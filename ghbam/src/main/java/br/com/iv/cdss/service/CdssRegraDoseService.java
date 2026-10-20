package br.com.iv.cdss.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.com.iv.cdss.entity.CdssRegraDose;
import br.com.iv.cdss.entity.CdssRegraDoseHistorico;
import br.com.iv.cdss.entity.CdssRegraDoseSugestao;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Medicamento;
import br.com.iv.security.AuthenticatedUser;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Limites de dose do CDSS editaveis e validaveis no sistema. O arquivo
 * farmacia/base-conhecimento-farmaceutico.json e a base; o valor alterado fica em
 * cdss_regra_dose e e aplicado em memoria (vale para prescricao e dispensacao).
 * Editar/validar exige a permissao pregrasdose (ou kadm); toda acao vai para o
 * historico. Os limites valem para todas as unidades. Medicamentos novos podem
 * ser incluidos pela tela (origem MANUAL) e qualquer um pode ser excluido
 * (exclusao logica, restauravel). Excluir um medicamento do arquivo tira so o
 * limite de dose: alergia cruzada e interacoes continuam valendo.
 */
@ApplicationScoped
public class CdssRegraDoseService {
    public static final String PERMISSAO = "pregrasdose";
    static final String MANUAL = "MANUAL";
    static final String MENSAGEM_EXCLUIDO = "Limite de dose excluido na gestao de limites do GHBAM.";
    /** Valor em vigor de um medicamento do arquivo cujo limite foi excluido. */
    static final Valores SEM_LIMITE = new Valores(null, null, null, MENSAGEM_EXCLUIDO, null);

    @Inject
    BaseConhecimentoFarmaceutico base;

    /** Valores do arquivo, antes de qualquer alteracao, por nome. */
    private final Map<String, Valores> originais = new HashMap<>();

    record Valores(Double doseMaximaDiaMg, Double doseMaximaMgKgDia, Double pediatriaMgKgDia, String semTetoFixo,
            Double idadeMinimaAnos) {
        static Valores de(Medicamento m) {
            return new Valores(m.doseMaximaDiaMg, m.doseMaximaMgKgDia, m.pediatriaMgKgDia,
                    m.semTetoFixo == null || m.semTetoFixo.isBlank() ? null : m.semTetoFixo, m.idadeMinimaAnos);
        }

        void aplicar(Medicamento m) {
            m.doseMaximaDiaMg = doseMaximaDiaMg;
            m.doseMaximaMgKgDia = doseMaximaMgKgDia;
            m.pediatriaMgKgDia = pediatriaMgKgDia;
            m.semTetoFixo = semTetoFixo == null ? "" : semTetoFixo;
            m.idadeMinimaAnos = idadeMinimaAnos;
        }
    }

    void aoSubir(@Observes StartupEvent evento) {
        try {
            aplicarAlteracoesSalvas();
        } catch (RuntimeException e) {
            System.err.println("[CDSS] Limites de dose do banco nao aplicados (tabela V201 ausente?): " + e.getMessage());
        }
    }

    @Transactional
    void aplicarAlteracoesSalvas() {
        guardarOriginais();
        Map<String, Medicamento> porNome = porNome();
        // sem linha validada = pendente (alerta de dose avisa sem bloquear)
        porNome.values().forEach(m -> m.limitePendente = true);
        for (CdssRegraDose r : CdssRegraDose.<CdssRegraDose>listAll()) {
            if (MANUAL.equals(r.getOrigem())) {
                if (!r.isExcluido()) base.adicionar(medicamentoDe(r));
                continue;
            }
            Medicamento m = porNome.get(r.getNome());
            if (m == null) continue;
            if (r.isExcluido()) {
                SEM_LIMITE.aplicar(m);
            } else if (r.isAlterado()) {
                valoresDa(r).aplicar(m);
            }
            m.limitePendente = r.isExcluido() || !r.isValidado();
        }
    }

    public List<Map<String, Object>> listar() {
        guardarOriginais();
        Map<String, CdssRegraDose> estado = new HashMap<>();
        try {
            for (CdssRegraDose r : CdssRegraDose.<CdssRegraDose>listAll()) estado.put(r.getNome(), r);
        } catch (RuntimeException e) {
            // tabela ausente: mostra so a base, tudo pendente
        }
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Medicamento m : base.getMedicamentos()) {
            CdssRegraDose r = estado.get(m.nome);
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("nome", m.nome);
            linha.put("sinonimos", new ArrayList<>(m.termos));
            linha.put("vigente", mapa(Valores.de(m)));
            linha.put("base", mapa(originais.getOrDefault(m.nome, Valores.de(m))));
            linha.put("alterado", r != null && r.isAlterado());
            linha.put("validado", r != null && r.isValidado());
            linha.put("validadoPor", r == null ? null : r.getValidadoPor());
            linha.put("validadoRegistro", r == null ? null : r.getValidadoRegistro());
            linha.put("validadoEm", r == null ? null : r.getValidadoEm());
            linha.put("validadoUnidade", r == null ? null : r.getValidadoUnidade());
            linha.put("origem", r == null ? "BASE" : r.getOrigem());
            linha.put("excluido", r != null && r.isExcluido());
            if (r != null && r.isExcluido()) {
                // excluido mostra o ultimo valor salvo (ou o do arquivo), nao o "sem limite" em vigor
                Valores guardado = r.isAlterado() ? valoresDa(r) : originais.getOrDefault(m.nome, Valores.de(m));
                linha.put("vigente", mapa(guardado));
            }
            lista.add(linha);
            estado.remove(m.nome);
        }
        // incluidos pela tela e excluidos nao estao mais na base em memoria
        for (CdssRegraDose r : estado.values()) {
            if (!MANUAL.equals(r.getOrigem()) || !r.isExcluido()) continue;
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("nome", r.getNome());
            linha.put("sinonimos", sinonimosDe(r));
            linha.put("vigente", mapa(valoresDa(r)));
            linha.put("base", mapa(valoresDa(r)));
            linha.put("alterado", true);
            linha.put("validado", false);
            linha.put("validadoPor", null);
            linha.put("validadoRegistro", null);
            linha.put("validadoEm", null);
            linha.put("validadoUnidade", null);
            linha.put("origem", MANUAL);
            linha.put("excluido", true);
            lista.add(linha);
        }
        lista.sort(Comparator.comparing(l -> String.valueOf(l.get("nome"))));
        return lista;
    }

    public List<CdssRegraDoseHistorico> historico(String nome) {
        return CdssRegraDoseHistorico.list("nome = ?1 order by em desc", nome == null ? "" : nome.trim());
    }

    /**
     * Altera e/ou valida o limite de um medicamento. Validar exige registro
     * profissional (CRF/CRM). Alterar sem validar deixa o item pendente.
     */
    @Transactional
    public Map<String, Object> salvar(Map<String, Object> corpo, AuthenticatedUser usuario) {
        exigirPermissao(usuario);
        String nome = texto(corpo.get("nome"));
        Medicamento m = porNome().get(nome);
        if (m == null) {
            throw new IllegalArgumentException("Medicamento nao encontrado na base.");
        }
        boolean validar = Boolean.TRUE.equals(corpo.get("validar"));
        String registro = texto(corpo.get("registroProfissional"));
        if (validar && registro.length() < 4) {
            throw new IllegalArgumentException("Informe o CRF ou CRM para validar.");
        }
        String observacao = texto(corpo.get("observacao"));

        guardarOriginais();
        Valores atual = Valores.de(m);
        Valores novo = new Valores(
                numero(corpo, "doseMaximaDiaMg", atual.doseMaximaDiaMg()),
                numero(corpo, "doseMaximaMgKgDia", atual.doseMaximaMgKgDia()),
                numero(corpo, "pediatriaMgKgDia", atual.pediatriaMgKgDia()),
                corpo.containsKey("semTetoFixo") ? vazioParaNulo(texto(corpo.get("semTetoFixo"))) : atual.semTetoFixo(),
                numero(corpo, "idadeMinimaAnos", atual.idadeMinimaAnos()));
        String mudancas = diferencas(atual, novo);
        if (mudancas.isEmpty() && !validar) {
            throw new IllegalArgumentException("Nenhum valor alterado.");
        }

        LocalDateTime agora = LocalDateTime.now();
        CdssRegraDose r = CdssRegraDose.findById(nome);
        if (r == null) {
            r = new CdssRegraDose();
            r.setNome(nome);
        }
        if (r.isExcluido()) {
            throw new IllegalArgumentException("Medicamento excluido: restaure antes de alterar ou validar.");
        }
        if (!mudancas.isEmpty()) {
            Valores original = originais.getOrDefault(nome, atual);
            r.setAlterado(MANUAL.equals(r.getOrigem()) || !novo.equals(original));
            r.setDoseMaximaDiaMg(bd(novo.doseMaximaDiaMg()));
            r.setDoseMaximaMgKgDia(bd(novo.doseMaximaMgKgDia()));
            r.setPediatriaMgKgDia(bd(novo.pediatriaMgKgDia()));
            r.setSemTetoFixo(novo.semTetoFixo());
            r.setIdadeMinimaAnos(bd(novo.idadeMinimaAnos()));
            r.setAtualizadoPor(usuario.nome());
            r.setAtualizadoPorMatricula(usuario.matricula());
            r.setAtualizadoEm(agora);
            r.setValidado(false); // valor mudou: precisa de (nova) validacao
            registrarHistorico(nome, "ALTERACAO", mudancas, observacao, usuario, validar ? registro : null);
        }
        if (validar) {
            r.setValidado(true);
            r.setValidadoPor(usuario.nome());
            r.setValidadoPorMatricula(usuario.matricula());
            r.setValidadoRegistro(registro);
            r.setValidadoEm(agora);
            r.setValidadoUnidade(usuario.unidade());
            registrarHistorico(nome, "VALIDACAO", "Validado: " + descrever(novo), observacao, usuario, registro);
        }
        r.persist();
        novo.aplicar(m); // passa a valer na hora, sem reiniciar
        m.limitePendente = !r.isValidado();
        return Map.of("nome", nome, "validado", r.isValidado(), "alterado", r.isAlterado());
    }

    /**
     * Qualquer clinico propoe novos valores com justificativa; nada muda ate a
     * farmacia clinica/medico designado aprovar. O catalogo de rotas restringe a
     * perfis clinicos.
     */
    @Transactional
    public Map<String, Object> sugerir(Map<String, Object> corpo, AuthenticatedUser usuario) {
        if (usuario == null || usuario.matricula() == null || usuario.matricula().isBlank()) {
            throw new SecurityException("Sessao sem profissional identificado.");
        }
        String nome = texto(corpo.get("nome"));
        Medicamento m = porNome().get(nome);
        if (m == null) {
            throw new IllegalArgumentException("Medicamento nao encontrado na base.");
        }
        String justificativa = texto(corpo.get("justificativa"));
        if (justificativa.length() < 15) {
            throw new IllegalArgumentException("Justifique a sugestao (minimo de 15 caracteres).");
        }
        Valores atual = Valores.de(m);
        Valores proposto = new Valores(
                numero(corpo, "doseMaximaDiaMg", atual.doseMaximaDiaMg()),
                numero(corpo, "doseMaximaMgKgDia", atual.doseMaximaMgKgDia()),
                numero(corpo, "pediatriaMgKgDia", atual.pediatriaMgKgDia()),
                corpo.containsKey("semTetoFixo") ? vazioParaNulo(texto(corpo.get("semTetoFixo"))) : atual.semTetoFixo(),
                numero(corpo, "idadeMinimaAnos", atual.idadeMinimaAnos()));
        String mudancas = diferencas(atual, proposto);
        if (mudancas.isEmpty()) {
            throw new IllegalArgumentException("A sugestao nao altera nenhum valor.");
        }
        CdssRegraDose estado = CdssRegraDose.findById(nome);
        if (estado != null && estado.isExcluido()) {
            throw new IllegalArgumentException("Medicamento excluido da gestao de limites.");
        }
        CdssRegraDoseSugestao s = new CdssRegraDoseSugestao();
        s.setNome(nome);
        s.setDoseMaximaDiaMg(bd(proposto.doseMaximaDiaMg()));
        s.setDoseMaximaMgKgDia(bd(proposto.doseMaximaMgKgDia()));
        s.setPediatriaMgKgDia(bd(proposto.pediatriaMgKgDia()));
        s.setSemTetoFixo(proposto.semTetoFixo());
        s.setIdadeMinimaAnos(bd(proposto.idadeMinimaAnos()));
        s.setMudancas(mudancas);
        s.setJustificativa(justificativa);
        s.setReferencia(vazioParaNulo(texto(corpo.get("referencia"))));
        s.setStatus("PENDENTE");
        s.setSugeridoPor(usuario.nome());
        s.setSugeridoPorMatricula(usuario.matricula());
        s.setSugeridoUnidade(usuario.unidade());
        s.persist();
        registrarHistorico(nome, "SUGESTAO", "Sugestao #" + s.getId() + ": " + mudancas, justificativa, usuario, null);
        return Map.of("id", s.getId(), "status", s.getStatus());
    }

    /**
     * Inclui um medicamento que nao esta no arquivo. Fica pendente ate validar
     * com CRF/CRM (pode validar no mesmo passo) e vale na hora.
     */
    @Transactional
    public Map<String, Object> incluir(Map<String, Object> corpo, AuthenticatedUser usuario) {
        exigirPermissao(usuario);
        String nome = BaseConhecimentoFarmaceutico.normalizar(texto(corpo.get("nome")));
        if (nome.length() < 3) {
            throw new IllegalArgumentException("Informe o nome do principio ativo (minimo de 3 letras).");
        }
        List<String> sinonimos = normalizarSinonimos(corpo.get("sinonimos"), nome);
        exigirTermosLivres(nome, sinonimos);
        Valores novo = new Valores(
                numero(corpo, "doseMaximaDiaMg", null),
                numero(corpo, "doseMaximaMgKgDia", null),
                numero(corpo, "pediatriaMgKgDia", null),
                vazioParaNulo(texto(corpo.get("semTetoFixo"))),
                numero(corpo, "idadeMinimaAnos", null));
        if (novo.doseMaximaDiaMg() == null && novo.doseMaximaMgKgDia() == null && novo.semTetoFixo() == null) {
            throw new IllegalArgumentException("Informe o limite adulto (mg/dia ou mg/kg/dia) ou o motivo de nao haver teto fixo.");
        }
        boolean validar = Boolean.TRUE.equals(corpo.get("validar"));
        String registro = texto(corpo.get("registroProfissional"));
        if (validar && registro.length() < 4) {
            throw new IllegalArgumentException("Informe o CRF ou CRM para validar.");
        }
        String observacao = texto(corpo.get("observacao"));

        CdssRegraDose r = CdssRegraDose.findById(nome);
        if (r != null && !(MANUAL.equals(r.getOrigem()) && r.isExcluido())) {
            throw new IllegalArgumentException("Medicamento ja cadastrado.");
        }
        if (r == null) {
            r = new CdssRegraDose();
            r.setNome(nome);
        }
        LocalDateTime agora = LocalDateTime.now();
        r.setOrigem(MANUAL);
        r.setExcluido(false);
        r.setAlterado(true);
        r.setSinonimos(String.join("; ", sinonimos));
        r.setDoseMaximaDiaMg(bd(novo.doseMaximaDiaMg()));
        r.setDoseMaximaMgKgDia(bd(novo.doseMaximaMgKgDia()));
        r.setPediatriaMgKgDia(bd(novo.pediatriaMgKgDia()));
        r.setSemTetoFixo(novo.semTetoFixo());
        r.setIdadeMinimaAnos(bd(novo.idadeMinimaAnos()));
        r.setAtualizadoPor(usuario.nome());
        r.setAtualizadoPorMatricula(usuario.matricula());
        r.setAtualizadoEm(agora);
        r.setValidado(validar);
        r.setValidadoPor(validar ? usuario.nome() : null);
        r.setValidadoPorMatricula(validar ? usuario.matricula() : null);
        r.setValidadoRegistro(validar ? registro : null);
        r.setValidadoEm(validar ? agora : null);
        r.setValidadoUnidade(validar ? usuario.unidade() : null);
        registrarHistorico(nome, "INCLUSAO", "Incluido: " + descrever(novo)
                + (sinonimos.isEmpty() ? "" : "; sinonimos: " + String.join(", ", sinonimos)), observacao, usuario, null);
        if (validar) {
            registrarHistorico(nome, "VALIDACAO", "Validado: " + descrever(novo), observacao, usuario, registro);
        }
        r.persist();
        base.adicionar(medicamentoDe(r));
        return Map.of("nome", nome, "validado", r.isValidado(), "origem", MANUAL);
    }

    /**
     * Exclusao logica com motivo. Incluido pela tela sai da base; do arquivo fica
     * sem limite de dose (alergia/interacao continuam). Restauravel.
     */
    @Transactional
    public Map<String, Object> excluir(Map<String, Object> corpo, AuthenticatedUser usuario) {
        exigirPermissao(usuario);
        String nome = texto(corpo.get("nome"));
        String motivo = texto(corpo.get("motivo"));
        if (motivo.length() < 10) {
            throw new IllegalArgumentException("Informe o motivo da exclusao (minimo de 10 caracteres).");
        }
        guardarOriginais();
        CdssRegraDose r = CdssRegraDose.findById(nome);
        Medicamento m = porNome().get(nome);
        if (m == null || (r != null && r.isExcluido())) {
            throw new IllegalArgumentException("Medicamento nao encontrado ou ja excluido.");
        }
        if (r == null) {
            r = new CdssRegraDose();
            r.setNome(nome);
        }
        String detalhe = "Excluido: " + descrever(Valores.de(m));
        r.setExcluido(true);
        r.setValidado(false);
        r.setAtualizadoPor(usuario.nome());
        r.setAtualizadoPorMatricula(usuario.matricula());
        r.setAtualizadoEm(LocalDateTime.now());
        registrarHistorico(nome, "EXCLUSAO", detalhe, motivo, usuario, null);
        r.persist();
        if (MANUAL.equals(r.getOrigem())) {
            base.remover(nome);
        } else {
            SEM_LIMITE.aplicar(m);
        }
        return Map.of("nome", nome, "excluido", true);
    }

    /** Desfaz a exclusao; volta pendente de validacao. */
    @Transactional
    public Map<String, Object> restaurar(Map<String, Object> corpo, AuthenticatedUser usuario) {
        exigirPermissao(usuario);
        String nome = texto(corpo.get("nome"));
        CdssRegraDose r = CdssRegraDose.findById(nome);
        if (r == null || !r.isExcluido()) {
            throw new IllegalArgumentException("Medicamento nao esta excluido.");
        }
        guardarOriginais();
        Valores restaurado;
        if (MANUAL.equals(r.getOrigem())) {
            restaurado = valoresDa(r);
        } else {
            Medicamento m = porNome().get(nome);
            if (m == null) {
                throw new IllegalArgumentException("Medicamento nao encontrado na base.");
            }
            restaurado = r.isAlterado() ? valoresDa(r) : originais.getOrDefault(nome, Valores.de(m));
        }
        r.setExcluido(false);
        r.setValidado(false);
        r.setAtualizadoPor(usuario.nome());
        r.setAtualizadoPorMatricula(usuario.matricula());
        r.setAtualizadoEm(LocalDateTime.now());
        registrarHistorico(nome, "RESTAURACAO", "Restaurado: " + descrever(restaurado), texto(corpo.get("observacao")),
                usuario, null);
        r.persist();
        if (MANUAL.equals(r.getOrigem())) {
            base.adicionar(medicamentoDe(r));
        } else {
            Medicamento m = porNome().get(nome);
            restaurado.aplicar(m);
            m.limitePendente = true;
        }
        return Map.of("nome", nome, "excluido", false);
    }

    /** Sinonimos normalizados, sem repetir o nome e sem termos curtos (casariam errado). */
    static List<String> normalizarSinonimos(Object bruto, String nome) {
        List<String> partes = new ArrayList<>();
        if (bruto instanceof List<?> lista) {
            for (Object o : lista) partes.add(String.valueOf(o));
        } else if (bruto != null) {
            partes.addAll(List.of(String.valueOf(bruto).split("[;,\\n]")));
        }
        List<String> saida = new ArrayList<>();
        for (String p : partes) {
            String t = BaseConhecimentoFarmaceutico.normalizar(p);
            if (t.length() >= 4 && !t.equals(nome) && !saida.contains(t)) saida.add(t);
        }
        return saida;
    }

    /** Nenhum termo novo pode ja identificar outra substancia da base. */
    private void exigirTermosLivres(String nome, List<String> sinonimos) {
        List<String> termos = new ArrayList<>(sinonimos);
        termos.add(0, nome);
        for (Medicamento m : base.getMedicamentos()) {
            for (String t : termos) {
                if (m.termos.contains(t)) {
                    throw new IllegalArgumentException("\"" + t + "\" ja pertence a " + m.nome + " na base; use Editar nele.");
                }
            }
        }
    }

    static Medicamento medicamentoDe(CdssRegraDose r) {
        Medicamento m = new Medicamento();
        m.nome = r.getNome();
        m.termos.add(r.getNome());
        m.termos.addAll(sinonimosDe(r));
        valoresDa(r).aplicar(m);
        m.limitePendente = !r.isValidado();
        return m;
    }

    static List<String> sinonimosDe(CdssRegraDose r) {
        List<String> lista = new ArrayList<>();
        if (r.getSinonimos() == null) return lista;
        for (String s : r.getSinonimos().split(";")) {
            if (!s.isBlank()) lista.add(s.trim());
        }
        return lista;
    }

    public List<CdssRegraDoseSugestao> sugestoes(String status) {
        String st = texto(status).toUpperCase();
        return st.isEmpty()
                ? CdssRegraDoseSugestao.list("order by sugeridoEm desc")
                : CdssRegraDoseSugestao.list("status = ?1 order by sugeridoEm desc", st);
    }

    /** Aprovar aplica e valida os valores propostos (exige CRF/CRM); rejeitar exige motivo. */
    @Transactional
    public Map<String, Object> decidirSugestao(Map<String, Object> corpo, AuthenticatedUser usuario) {
        exigirPermissao(usuario);
        Object idBruto = corpo.get("id");
        CdssRegraDoseSugestao s = idBruto == null ? null
                : CdssRegraDoseSugestao.findById(Long.valueOf(String.valueOf(idBruto)));
        if (s == null) {
            throw new IllegalArgumentException("Sugestao nao encontrada.");
        }
        if (!"PENDENTE".equals(s.getStatus())) {
            throw new IllegalArgumentException("Sugestao ja decidida.");
        }
        boolean aprovar = Boolean.TRUE.equals(corpo.get("aprovar"));
        String motivo = texto(corpo.get("motivo"));
        String registro = texto(corpo.get("registroProfissional"));
        if (aprovar) {
            Map<String, Object> valores = new HashMap<>();
            valores.put("nome", s.getNome());
            valores.put("doseMaximaDiaMg", dbl(s.getDoseMaximaDiaMg()));
            valores.put("doseMaximaMgKgDia", dbl(s.getDoseMaximaMgKgDia()));
            valores.put("pediatriaMgKgDia", dbl(s.getPediatriaMgKgDia()));
            valores.put("semTetoFixo", s.getSemTetoFixo());
            valores.put("idadeMinimaAnos", dbl(s.getIdadeMinimaAnos()));
            valores.put("validar", Boolean.TRUE);
            valores.put("registroProfissional", registro);
            valores.put("observacao", "Sugestao #" + s.getId() + " de " + s.getSugeridoPor() + ": " + s.getJustificativa()
                    + (motivo.isEmpty() ? "" : " | Aprovacao: " + motivo));
            salvar(valores, usuario);
        } else if (motivo.length() < 10) {
            throw new IllegalArgumentException("Informe o motivo da rejeicao (minimo de 10 caracteres).");
        } else {
            registrarHistorico(s.getNome(), "REJEICAO", "Sugestao #" + s.getId() + " rejeitada", motivo, usuario, registro.isEmpty() ? null : registro);
        }
        s.setStatus(aprovar ? "APROVADA" : "REJEITADA");
        s.setDecididoPor(usuario.nome());
        s.setDecididoPorMatricula(usuario.matricula());
        s.setDecididoRegistro(registro.isEmpty() ? null : registro);
        s.setDecididoEm(LocalDateTime.now());
        s.setMotivoDecisao(motivo.isEmpty() ? null : motivo);
        return Map.of("id", s.getId(), "status", s.getStatus());
    }

    static void exigirPermissao(AuthenticatedUser usuario) {
        if (usuario == null || usuario.matricula() == null || usuario.matricula().isBlank()) {
            throw new SecurityException("Sessao sem profissional identificado.");
        }
        if (!usuario.hasPermission(PERMISSAO) && !usuario.hasPermission("kadm")) {
            throw new SecurityException("Sem permissao para alterar ou validar limites de dose.");
        }
    }

    static String diferencas(Valores a, Valores b) {
        List<String> d = new ArrayList<>();
        comparar(d, "Adulto mg/dia", a.doseMaximaDiaMg(), b.doseMaximaDiaMg());
        comparar(d, "Adulto mg/kg/dia", a.doseMaximaMgKgDia(), b.doseMaximaMgKgDia());
        comparar(d, "Pediatria mg/kg/dia", a.pediatriaMgKgDia(), b.pediatriaMgKgDia());
        comparar(d, "Sem teto fixo", a.semTetoFixo(), b.semTetoFixo());
        comparar(d, "Idade minima (anos)", a.idadeMinimaAnos(), b.idadeMinimaAnos());
        return String.join("; ", d);
    }

    private static void comparar(List<String> d, String rotulo, Object antes, Object depois) {
        if (!Objects.equals(antes, depois)) {
            d.add(rotulo + ": " + (antes == null ? "-" : antes) + " -> " + (depois == null ? "-" : depois));
        }
    }

    private static String descrever(Valores v) {
        return "adulto " + (v.doseMaximaDiaMg() == null ? "-" : v.doseMaximaDiaMg()) + " mg/dia, "
                + (v.doseMaximaMgKgDia() == null ? "-" : v.doseMaximaMgKgDia()) + " mg/kg/dia; pediatria "
                + (v.pediatriaMgKgDia() == null ? "-" : v.pediatriaMgKgDia()) + " mg/kg/dia"
                + (v.semTetoFixo() == null ? "" : "; sem teto fixo");
    }

    private void registrarHistorico(String nome, String acao, String detalhe, String observacao,
            AuthenticatedUser usuario, String registro) {
        CdssRegraDoseHistorico h = new CdssRegraDoseHistorico();
        h.setNome(nome);
        h.setAcao(acao);
        h.setDetalhe(detalhe);
        h.setObservacao(observacao.isBlank() ? null : observacao);
        h.setPor(usuario.nome());
        h.setPorMatricula(usuario.matricula());
        h.setRegistroProfissional(registro);
        h.setPorUnidade(usuario.unidade());
        h.persist();
    }

    private synchronized void guardarOriginais() {
        if (!originais.isEmpty()) return;
        for (Medicamento m : base.getMedicamentos()) {
            originais.put(m.nome, Valores.de(m));
        }
    }

    private Map<String, Medicamento> porNome() {
        Map<String, Medicamento> mapa = new HashMap<>();
        for (Medicamento m : base.getMedicamentos()) mapa.put(m.nome, m);
        return mapa;
    }

    private static Valores valoresDa(CdssRegraDose r) {
        return new Valores(dbl(r.getDoseMaximaDiaMg()), dbl(r.getDoseMaximaMgKgDia()), dbl(r.getPediatriaMgKgDia()),
                vazioParaNulo(r.getSemTetoFixo()), dbl(r.getIdadeMinimaAnos()));
    }

    private static Map<String, Object> mapa(Valores v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("doseMaximaDiaMg", v.doseMaximaDiaMg());
        m.put("doseMaximaMgKgDia", v.doseMaximaMgKgDia());
        m.put("pediatriaMgKgDia", v.pediatriaMgKgDia());
        m.put("semTetoFixo", v.semTetoFixo());
        m.put("idadeMinimaAnos", v.idadeMinimaAnos());
        return m;
    }

    /** Campo ausente mantem o atual; vazio/null limpa; numero precisa ser >= 0. */
    static Double numero(Map<String, Object> corpo, String campo, Double atual) {
        if (!corpo.containsKey(campo)) return atual;
        Object v = corpo.get(campo);
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            double d = Double.parseDouble(String.valueOf(v).trim().replace(',', '.'));
            if (d < 0 || Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException();
            return d;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor invalido em " + campo + ".");
        }
    }

    private static String texto(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String vazioParaNulo(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static BigDecimal bd(Double v) {
        return v == null ? null : BigDecimal.valueOf(v);
    }

    private static Double dbl(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}

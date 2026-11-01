package br.com.iv.cme;

import br.com.iv.dto.LogDto;
import br.com.iv.service.LogService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rastreabilidade da CME: ciclo -> pacote etiquetado -> paciente. Toda consulta
 * e escrita e restrita a unidade recebida do controller (a da sessao).
 */
@ApplicationScoped
public class CmeService {

    private static final int VALIDADE_PADRAO_DIAS = 30;
    private static final int MAX_PACOTES_POR_INCLUSAO = 50;

    @Inject
    LogService logService;

    public List<CmeCiclo> listarCiclos(String unidade, String status, String dia) {
        StringBuilder jpql = new StringBuilder("unidade = :unidade");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("unidade", unidade);
        String s = CmeRules.normalizar(status);
        if (!s.isEmpty()) {
            jpql.append(" and status = :status");
            params.put("status", s);
        }
        LocalDate data = data(Map.<String, Object>of("dia", dia == null ? "" : dia), "dia");
        if (data != null) {
            jpql.append(" and dataReferencia = :dia");
            params.put("dia", data);
        }
        return CmeCiclo.<CmeCiclo>find(jpql + " order by dataRegistro desc", params).page(0, 200).list();
    }

    public Map<String, Object> detalheCiclo(String unidade, Long idciclo) {
        CmeCiclo ciclo = cicloDaUnidade(unidade, idciclo);
        List<CmePacote> pacotes = pacotesDoCiclo(idciclo);
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("ciclo", ciclo);
        resposta.put("pacotes", pacotes);
        resposta.put("eventos", CmeEvento.<CmeEvento>find("idciclo = ?1 order by dataEvento asc", idciclo).list());
        // Pacotes que ja chegaram ao paciente antes da reprovacao: a CCIH precisa
        // avaliar esses pacientes, nao basta recolher o que sobrou no arsenal.
        resposta.put("pacientesExpostos", CmeRules.CICLO_REPROVADO.equals(ciclo.status)
                ? pacotes.stream().filter(p -> CmeRules.PACOTE_UTILIZADO.equals(p.status)).toList()
                : List.of());
        return resposta;
    }

    @Transactional
    public CmeCiclo criarCiclo(String unidade, CmeCiclo dados, String usuario) {
        if (dados == null || vazio(dados.equipamento)) {
            throw new IllegalArgumentException("Informe o equipamento.");
        }
        String equipamento = dados.equipamento.trim().toUpperCase();
        // Numero sempre gerado aqui, reiniciando a cada dia por equipamento na
        // unidade; o valor vindo do cliente e ignorado para ninguem furar a sequencia.
        LocalDate hoje = LocalDate.now();
        String numero = CmeRules.proximoNumeroCiclo(CmeCiclo.<CmeCiclo>find(
                "unidade = ?1 and equipamento = ?2 and dataReferencia = ?3", unidade, equipamento, hoje)
                .stream().map(c -> c.numeroCiclo).toList());
        CmeCiclo ciclo = new CmeCiclo();
        ciclo.unidade = unidade;
        ciclo.equipamento = equipamento;
        ciclo.numeroCiclo = numero;
        ciclo.dataReferencia = hoje;
        ciclo.metodo = CmeRules.metodo(dados.metodo);
        ciclo.dataInicio = dados.dataInicio == null ? LocalDateTime.now() : dados.dataInicio;
        ciclo.dataFim = dados.dataFim;
        ciclo.temperatura = dados.temperatura;
        ciclo.pressao = dados.pressao;
        ciclo.tempoExposicaoMin = dados.tempoExposicaoMin;
        ciclo.indicadorFisico = CmeRules.PENDENTE;
        ciclo.indicadorQuimico = CmeRules.PENDENTE;
        ciclo.indicadorBiologico = CmeRules.IB_NAO_REALIZADO;
        ciclo.status = CmeRules.CICLO_EM_ANALISE;
        ciclo.observacao = dados.observacao;
        ciclo.usuarioRegistro = usuario;
        ciclo.persist();
        evento(ciclo.idciclo, null, "CICLO_ABERTO", null, ciclo.status,
                ciclo.equipamento + " ciclo " + ciclo.numeroCiclo + " (" + ciclo.metodo + ")", usuario);
        return ciclo;
    }

    @Transactional
    public List<CmePacote> adicionarPacotes(String unidade, Long idciclo, Map<String, Object> payload, String usuario) {
        CmeCiclo ciclo = cicloDaUnidade(unidade, idciclo);
        if (!CmeRules.CICLO_EM_ANALISE.equals(ciclo.status)) {
            throw new IllegalStateException("Pacotes so podem ser incluidos enquanto o ciclo esta em analise.");
        }
        if (payload != null && payload.get("preparos") instanceof List<?> ids && !ids.isEmpty()) {
            return carregarPreparados(unidade, ciclo, ids, data(payload, "validade"), usuario);
        }
        String descricao = texto(payload, "descricao");
        if (vazio(descricao)) {
            throw new IllegalArgumentException("Informe a caixa, bandeja ou kit.");
        }
        int copias = inteiro(payload, "copias", 1);
        if (copias < 1 || copias > MAX_PACOTES_POR_INCLUSAO) {
            throw new IllegalArgumentException("Quantidade de etiquetas deve ficar entre 1 e " + MAX_PACOTES_POR_INCLUSAO + ".");
        }
        LocalDate validade = data(payload, "validade");
        if (validade == null) {
            validade = LocalDate.now().plusDays(inteiro(payload, "diasValidade", VALIDADE_PADRAO_DIAS));
        }
        if (validade.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Validade nao pode estar no passado.");
        }
        boolean implante = Boolean.TRUE.equals(payload == null ? null : payload.get("contemImplante"))
                || "true".equalsIgnoreCase(texto(payload, "contemImplante"));
        int itens = Math.max(1, inteiro(payload, "quantidade", 1));

        List<CmePacote> criados = new ArrayList<>();
        for (int i = 0; i < copias; i++) {
            CmePacote pacote = new CmePacote();
            pacote.idciclo = idciclo;
            pacote.unidade = unidade;
            pacote.descricao = descricao.trim().toUpperCase();
            pacote.quantidade = itens;
            pacote.contemImplante = implante;
            pacote.validade = validade;
            pacote.status = CmeRules.PACOTE_ESTERILIZADO;
            pacote.usuarioRegistro = usuario;
            pacote.persist();
            pacote.codigo = CmeRules.codigoPacote(unidade, pacote.idpacote);
            evento(idciclo, pacote.idpacote, "PACOTE_ETIQUETADO", null, pacote.status,
                    pacote.descricao + " validade " + validade, usuario);
            criados.add(pacote);
        }
        ciclo.dataAtualizacao = LocalDateTime.now();
        ciclo.usuarioAtualizacao = usuario;
        return criados;
    }

    /** Cada caixa PRONTA do preparo vira um pacote etiquetado nesta carga. */
    private List<CmePacote> carregarPreparados(String unidade, CmeCiclo ciclo, List<?> ids, LocalDate validadeInformada,
            String usuario) {
        LocalDate validade = validadeInformada == null ? LocalDate.now().plusDays(VALIDADE_PADRAO_DIAS) : validadeInformada;
        if (validade.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Validade nao pode estar no passado.");
        }
        List<CmePacote> criados = new ArrayList<>();
        for (Object id : ids) {
            Long idpreparo;
            try {
                idpreparo = Long.valueOf(String.valueOf(id));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Caixa invalida.");
            }
            CmePreparo preparo = preparoDaUnidade(unidade, idpreparo);
            exigirEtapa(preparo, "CARGA");
            CmePacote pacote = new CmePacote();
            pacote.idciclo = ciclo.idciclo;
            pacote.idpreparo = preparo.idpreparo;
            pacote.unidade = unidade;
            pacote.descricao = preparo.descricao;
            pacote.quantidade = 1;
            pacote.contemImplante = Boolean.TRUE.equals(preparo.contemImplante);
            pacote.validade = validade;
            pacote.status = CmeRules.PACOTE_ESTERILIZADO;
            pacote.usuarioRegistro = usuario;
            pacote.persist();
            pacote.codigo = CmeRules.codigoPacote(unidade, pacote.idpacote);
            preparo.status = CmeRules.PREPARO_NA_CARGA;
            preparo.idpacote = pacote.idpacote;
            preparo.dataCarga = LocalDateTime.now();
            evento(ciclo.idciclo, pacote.idpacote, "PACOTE_ETIQUETADO", null, pacote.status,
                    pacote.descricao + " (limpeza " + preparo.metodoLimpeza + ", embalagem " + preparo.embalagem
                            + (preparo.codigoAnterior == null ? "" : ", reprocessamento de " + preparo.codigoAnterior)
                            + ") validade " + validade, usuario);
            criados.add(pacote);
        }
        ciclo.dataAtualizacao = LocalDateTime.now();
        ciclo.usuarioAtualizacao = usuario;
        return criados;
    }

    @Transactional
    public CmeCiclo registrarIndicadores(String unidade, Long idciclo, Map<String, Object> payload, String usuario) {
        CmeCiclo ciclo = cicloDaUnidade(unidade, idciclo);
        if (CmeRules.CICLO_REPROVADO.equals(ciclo.status) || CmeRules.CICLO_CANCELADO.equals(ciclo.status)) {
            throw new IllegalStateException("Ciclo " + ciclo.status.toLowerCase() + " nao aceita novos resultados.");
        }
        String fisico = payload != null && payload.containsKey("indicadorFisico")
                ? CmeRules.indicador(texto(payload, "indicadorFisico")) : ciclo.indicadorFisico;
        String quimico = payload != null && payload.containsKey("indicadorQuimico")
                ? CmeRules.indicador(texto(payload, "indicadorQuimico")) : ciclo.indicadorQuimico;
        String biologico = payload != null && payload.containsKey("indicadorBiologico")
                ? CmeRules.biologico(texto(payload, "indicadorBiologico")) : ciclo.indicadorBiologico;
        // Depois de liberado so o biologico (leitura tardia, 24-48h) pode mudar.
        if (CmeRules.CICLO_LIBERADO.equals(ciclo.status)
                && (!fisico.equals(ciclo.indicadorFisico) || !quimico.equals(ciclo.indicadorQuimico))) {
            throw new IllegalStateException("Ciclo liberado: apenas o indicador biologico pode ser atualizado.");
        }
        String detalhe = "fisico=" + fisico + " quimico=" + quimico + " biologico=" + biologico;
        ciclo.indicadorFisico = fisico;
        ciclo.indicadorQuimico = quimico;
        ciclo.indicadorBiologico = biologico;
        String lote = texto(payload, "loteIndicadorBiologico");
        if (!vazio(lote)) {
            ciclo.loteIndicadorBiologico = lote.trim();
        }
        ciclo.dataAtualizacao = LocalDateTime.now();
        ciclo.usuarioAtualizacao = usuario;
        evento(idciclo, null, "INDICADORES", ciclo.status, ciclo.status, detalhe, usuario);

        if (CmeRules.exigeReprovacao(fisico, quimico, biologico)) {
            reprovarInterno(ciclo, "Indicador nao conforme (" + detalhe + ")", usuario);
        }
        return ciclo;
    }

    @Transactional
    public CmeCiclo liberar(String unidade, Long idciclo, String usuario) {
        CmeCiclo ciclo = cicloDaUnidade(unidade, idciclo);
        List<CmePacote> pacotes = pacotesDoCiclo(idciclo);
        boolean implante = pacotes.stream().anyMatch(p -> Boolean.TRUE.equals(p.contemImplante));
        String bloqueio = CmeRules.bloqueioLiberacao(ciclo.status, ciclo.indicadorFisico, ciclo.indicadorQuimico,
                ciclo.indicadorBiologico, implante, pacotes.size());
        if (bloqueio != null) {
            throw new IllegalStateException(bloqueio);
        }
        ciclo.status = CmeRules.CICLO_LIBERADO;
        ciclo.dataFim = ciclo.dataFim == null ? LocalDateTime.now() : ciclo.dataFim;
        ciclo.dataLiberacao = LocalDateTime.now();
        ciclo.usuarioLiberacao = usuario;
        ciclo.dataAtualizacao = ciclo.dataLiberacao;
        ciclo.usuarioAtualizacao = usuario;
        evento(idciclo, null, "CICLO_LIBERADO", CmeRules.CICLO_EM_ANALISE, ciclo.status,
                pacotes.size() + " pacote(s) liberado(s)", usuario);
        return ciclo;
    }

    @Transactional
    public Map<String, Object> reprovar(String unidade, Long idciclo, String motivo, String usuario) {
        if (vazio(motivo)) {
            throw new IllegalArgumentException("Informe o motivo da reprovacao.");
        }
        CmeCiclo ciclo = cicloDaUnidade(unidade, idciclo);
        if (CmeRules.CICLO_REPROVADO.equals(ciclo.status) || CmeRules.CICLO_CANCELADO.equals(ciclo.status)) {
            throw new IllegalStateException("Ciclo ja esta " + ciclo.status.toLowerCase() + ".");
        }
        reprovarInterno(ciclo, motivo.trim(), usuario);
        return detalheCiclo(unidade, idciclo);
    }

    /**
     * Exclusao fisica so de ciclo em analise sem nenhuma etiqueta: nao ha
     * material nem paciente ligado. Com etiqueta, o caminho e cancelar.
     */
    @Transactional
    public void excluir(String unidade, Long idciclo, String usuario) {
        CmeCiclo ciclo = cicloDaUnidade(unidade, idciclo);
        if (!CmeRules.CICLO_EM_ANALISE.equals(ciclo.status)) {
            throw new IllegalStateException("So ciclo em analise pode ser excluido. Ciclo liberado ou reprovado fica no historico.");
        }
        if (CmePacote.count("idciclo", idciclo) > 0) {
            throw new IllegalStateException("O ciclo ja tem caixas etiquetadas: use Cancelar ciclo (fica no historico).");
        }
        CmeEvento.delete("idciclo", idciclo);
        ciclo.delete();
        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        log.setUnidade(unidade);
        log.setEvento("CME ciclo " + ciclo.numeroCiclo + " (" + ciclo.equipamento + ") excluido sem etiquetas"
                + (vazio(usuario) ? "" : " - por matricula " + usuario));
        logService.salvarLogAsync(log);
    }

    /** Ciclo aberto por engano: fica CANCELADO no historico e os pacotes etiquetados sao baixados. */
    @Transactional
    public Map<String, Object> cancelar(String unidade, Long idciclo, String motivo, String usuario) {
        if (vazio(motivo)) {
            throw new IllegalArgumentException("Informe o motivo do cancelamento.");
        }
        CmeCiclo ciclo = cicloDaUnidade(unidade, idciclo);
        String bloqueio = CmeRules.bloqueioCancelamento(ciclo.status);
        if (bloqueio != null) {
            throw new IllegalStateException(bloqueio);
        }
        String anterior = ciclo.status;
        ciclo.status = CmeRules.CICLO_CANCELADO;
        ciclo.motivoReprovacao = motivo.trim();
        ciclo.dataAtualizacao = LocalDateTime.now();
        ciclo.usuarioAtualizacao = usuario;
        evento(idciclo, null, "CICLO_CANCELADO", anterior, ciclo.status, motivo.trim(), usuario);
        for (CmePacote pacote : pacotesDoCiclo(idciclo)) {
            if (CmeRules.podeBaixar(pacote.status)) {
                String antes = pacote.status;
                pacote.status = CmeRules.PACOTE_BAIXADO;
                tocar(pacote, usuario);
                evento(idciclo, pacote.idpacote, "BAIXADO", antes, pacote.status, "Ciclo cancelado", usuario);
            }
        }
        return detalheCiclo(unidade, idciclo);
    }

    /** Corrige metodo e parametros fisicos enquanto o ciclo esta em analise; cada mudanca vai para o historico. */
    @Transactional
    public CmeCiclo alterarParametros(String unidade, Long idciclo, Map<String, Object> payload, String usuario) {
        CmeCiclo ciclo = cicloDaUnidade(unidade, idciclo);
        if (!CmeRules.CICLO_EM_ANALISE.equals(ciclo.status)) {
            throw new IllegalStateException("So ciclo em analise pode ser alterado.");
        }
        List<String> mudancas = new ArrayList<>();
        if (payload != null && payload.containsKey("metodo")) {
            String metodo = CmeRules.metodo(texto(payload, "metodo"));
            if (!metodo.equals(ciclo.metodo)) mudancas.add("metodo " + ciclo.metodo + " -> " + metodo);
            ciclo.metodo = metodo;
        }
        if (payload != null && payload.containsKey("temperatura")) {
            BigDecimal t = decimal(payload, "temperatura");
            if (!iguais(t, ciclo.temperatura)) mudancas.add("temperatura " + ciclo.temperatura + " -> " + t);
            ciclo.temperatura = t;
        }
        if (payload != null && payload.containsKey("pressao")) {
            BigDecimal p = decimal(payload, "pressao");
            if (!iguais(p, ciclo.pressao)) mudancas.add("pressao " + ciclo.pressao + " -> " + p);
            ciclo.pressao = p;
        }
        if (payload != null && payload.containsKey("tempoExposicaoMin")) {
            Integer m = vazio(texto(payload, "tempoExposicaoMin")) ? null : inteiro(payload, "tempoExposicaoMin", 0);
            if (!java.util.Objects.equals(m, ciclo.tempoExposicaoMin)) mudancas.add("tempo " + ciclo.tempoExposicaoMin + " -> " + m);
            ciclo.tempoExposicaoMin = m;
        }
        if (mudancas.isEmpty()) {
            return ciclo;
        }
        ciclo.dataAtualizacao = LocalDateTime.now();
        ciclo.usuarioAtualizacao = usuario;
        evento(idciclo, null, "CICLO_ALTERADO", ciclo.status, ciclo.status, String.join("; ", mudancas), usuario);
        return ciclo;
    }

    private static boolean iguais(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    public List<CmePacote> listarPacotes(String unidade, String status) {
        String s = CmeRules.normalizar(status);
        if (s.isEmpty()) {
            return CmePacote.<CmePacote>find("unidade = ?1 order by dataRegistro desc", unidade).page(0, 300).list();
        }
        return CmePacote.<CmePacote>find("unidade = ?1 and status = ?2 order by dataRegistro desc", unidade, s)
                .page(0, 300).list();
    }

    public List<CmePacote> pacotesDoPaciente(String unidade, Integer idpaciente) {
        return CmePacote.<CmePacote>find("unidade = ?1 and idpaciente = ?2 order by dataUtilizacao desc", unidade, idpaciente)
                .list();
    }

    public Map<String, Object> rastrear(String unidade, String codigo) {
        CmePacote pacote = pacoteDaUnidade(unidade, codigo);
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("pacote", pacote);
        resposta.put("ciclo", CmeCiclo.findById(pacote.idciclo));
        resposta.put("preparo", pacote.idpreparo == null ? null : CmePreparo.findById(pacote.idpreparo));
        resposta.put("eventos", CmeEvento.<CmeEvento>find(
                "idpacote = ?1 or (idciclo = ?2 and idpacote is null) order by dataEvento asc",
                pacote.idpacote, pacote.idciclo).list());
        return resposta;
    }

    @Transactional
    public CmePacote distribuir(String unidade, String codigo, String setor, String usuario) {
        if (vazio(setor)) {
            throw new IllegalArgumentException("Informe o setor de destino.");
        }
        CmePacote pacote = pacoteDaUnidade(unidade, codigo);
        exigirSaida(pacote, false);
        String anterior = pacote.status;
        pacote.status = CmeRules.PACOTE_DISTRIBUIDO;
        pacote.setorDestino = setor.trim().toUpperCase();
        tocar(pacote, usuario);
        evento(pacote.idciclo, pacote.idpacote, "DISTRIBUIDO", anterior, pacote.status, "Setor " + pacote.setorDestino, usuario);
        return pacote;
    }

    @Transactional
    public CmePacote utilizar(String unidade, String codigo, Map<String, Object> payload, String usuario) {
        Integer idpaciente = payload == null || payload.get("idpaciente") == null ? null : inteiro(payload, "idpaciente", 0);
        String nome = texto(payload, "nomepaciente");
        if (idpaciente == null || idpaciente <= 0 || vazio(nome)) {
            throw new IllegalArgumentException("Selecione o paciente que recebeu o material.");
        }
        CmePacote pacote = pacoteDaUnidade(unidade, codigo);
        exigirSaida(pacote, true);
        String anterior = pacote.status;
        pacote.status = CmeRules.PACOTE_UTILIZADO;
        pacote.idpaciente = idpaciente;
        pacote.nomepaciente = nome.trim().toUpperCase();
        pacote.prontuario = texto(payload, "prontuario");
        pacote.referenciaUso = texto(payload, "referencia");
        pacote.dataUtilizacao = LocalDateTime.now();
        tocar(pacote, usuario);
        evento(pacote.idciclo, pacote.idpacote, "UTILIZADO", anterior, pacote.status,
                "Paciente " + idpaciente + (vazio(pacote.referenciaUso) ? "" : " - " + pacote.referenciaUso), usuario);
        return pacote;
    }

    @Transactional
    public CmePacote descartar(String unidade, String codigo, String motivo, String usuario) {
        if (vazio(motivo)) {
            throw new IllegalArgumentException("Informe o motivo (reprocessamento, dano, vencimento...).");
        }
        CmePacote pacote = pacoteDaUnidade(unidade, codigo);
        if (!CmeRules.podeBaixar(pacote.status)) {
            throw new IllegalStateException("Pacote " + pacote.status + " nao pode ser baixado.");
        }
        String anterior = pacote.status;
        pacote.status = CmeRules.PACOTE_BAIXADO;
        tocar(pacote, usuario);
        evento(pacote.idciclo, pacote.idpacote, "BAIXADO", anterior, pacote.status, motivo.trim(), usuario);
        return pacote;
    }

    /**
     * Pacote recolhido (ou vencido no arsenal) volta ao expurgo: precisa ser
     * lavado e preparado de novo antes de outra carga. A etiqueta antiga e baixada.
     */
    @Transactional
    public CmePreparo reprocessar(String unidade, String codigo, String usuario) {
        CmePacote antigo = pacoteDaUnidade(unidade, codigo);
        if (!CmeRules.reprocessavel(antigo.status, antigo.validade, LocalDate.now())) {
            throw new IllegalStateException("So pacote recolhido ou vencido no arsenal pode ser reprocessado.");
        }
        return enviarAoExpurgo(antigo, usuario);
    }

    /** Ciclo reprovado: todas as caixas recolhidas voltam ao expurgo de uma vez. */
    @Transactional
    public Map<String, Object> reprocessarCarga(String unidade, Long idciclo, String usuario) {
        CmeCiclo origem = cicloDaUnidade(unidade, idciclo);
        if (!CmeRules.CICLO_REPROVADO.equals(origem.status)) {
            throw new IllegalStateException("So a carga de ciclo reprovado e reprocessada de uma vez.");
        }
        List<CmePacote> recolhidos = pacotesDoCiclo(idciclo).stream()
                .filter(p -> CmeRules.PACOTE_RECOLHIDO.equals(p.status)).toList();
        if (recolhidos.isEmpty()) {
            throw new IllegalStateException("Nao ha caixas recolhidas para reprocessar neste ciclo.");
        }
        for (CmePacote antigo : recolhidos) {
            enviarAoExpurgo(antigo, usuario);
        }
        Map<String, Object> resposta = detalheCiclo(unidade, idciclo);
        resposta.put("enviadasAoExpurgo", recolhidos.size());
        return resposta;
    }

    private CmePreparo enviarAoExpurgo(CmePacote antigo, String usuario) {
        CmePreparo preparo = new CmePreparo();
        preparo.unidade = antigo.unidade;
        preparo.descricao = antigo.descricao;
        preparo.contemImplante = Boolean.TRUE.equals(antigo.contemImplante);
        preparo.origem = "REPROCESSAMENTO";
        preparo.codigoAnterior = antigo.codigo;
        preparo.status = CmeRules.PREPARO_RECEBIDO;
        preparo.usuarioRecebimento = usuario;
        preparo.persist();

        String anterior = antigo.status;
        antigo.status = CmeRules.PACOTE_BAIXADO;
        tocar(antigo, usuario);
        evento(antigo.idciclo, antigo.idpacote, "REPROCESSADO", anterior, antigo.status,
                "Devolvido ao expurgo para nova limpeza e preparo", usuario);
        return preparo;
    }

    // ---- Expurgo, limpeza e preparo (antes da carga) ----

    public List<CmePreparo> listarPreparo(String unidade) {
        return CmePreparo.<CmePreparo>find("unidade = ?1 and status in ?2 order by dataRecebimento asc", unidade,
                List.of(CmeRules.PREPARO_RECEBIDO, CmeRules.PREPARO_LIMPO, CmeRules.PREPARO_PRONTO)).list();
    }

    @Transactional
    public List<CmePreparo> receber(String unidade, Map<String, Object> payload, String usuario) {
        String descricao = texto(payload, "descricao");
        if (vazio(descricao)) {
            throw new IllegalArgumentException("Informe a caixa, bandeja ou kit recebido.");
        }
        int copias = inteiro(payload, "copias", 1);
        if (copias < 1 || copias > MAX_PACOTES_POR_INCLUSAO) {
            throw new IllegalArgumentException("Quantidade deve ficar entre 1 e " + MAX_PACOTES_POR_INCLUSAO + ".");
        }
        boolean implante = Boolean.TRUE.equals(payload == null ? null : payload.get("contemImplante"))
                || "true".equalsIgnoreCase(texto(payload, "contemImplante"));
        String origem = texto(payload, "origem");
        List<CmePreparo> criados = new ArrayList<>();
        for (int i = 0; i < copias; i++) {
            CmePreparo preparo = new CmePreparo();
            preparo.unidade = unidade;
            preparo.descricao = descricao.trim().toUpperCase();
            preparo.contemImplante = implante;
            preparo.origem = vazio(origem) ? null : origem.trim().toUpperCase();
            preparo.status = CmeRules.PREPARO_RECEBIDO;
            preparo.usuarioRecebimento = usuario;
            preparo.persist();
            criados.add(preparo);
        }
        return criados;
    }

    @Transactional
    public CmePreparo limpar(String unidade, Long idpreparo, Map<String, Object> payload, String usuario) {
        CmePreparo preparo = preparoDaUnidade(unidade, idpreparo);
        exigirEtapa(preparo, "LIMPAR");
        preparo.metodoLimpeza = CmeRules.metodoLimpeza(texto(payload, "metodo"));
        preparo.dataLimpeza = LocalDateTime.now();
        preparo.usuarioLimpeza = usuario;
        preparo.status = CmeRules.PREPARO_LIMPO;
        return preparo;
    }

    /** Inspecao + embalagem. Inspecao reprovada (dano, sujidade, falta de peca) descarta a caixa. */
    @Transactional
    public CmePreparo preparar(String unidade, Long idpreparo, Map<String, Object> payload, String usuario) {
        CmePreparo preparo = preparoDaUnidade(unidade, idpreparo);
        exigirEtapa(preparo, "PREPARAR");
        boolean aprovada = !"REPROVADA".equals(CmeRules.normalizar(texto(payload, "inspecao")));
        if (!aprovada && vazio(texto(payload, "observacao"))) {
            throw new IllegalArgumentException("Informe o problema encontrado na inspecao.");
        }
        preparo.inspecao = aprovada ? "APROVADA" : "REPROVADA";
        preparo.dataPreparo = LocalDateTime.now();
        preparo.usuarioPreparo = usuario;
        if (!aprovada) {
            preparo.observacao = texto(payload, "observacao").trim();
            preparo.status = CmeRules.PREPARO_DESCARTADO;
            return preparo;
        }
        preparo.embalagem = CmeRules.embalagem(texto(payload, "embalagem"));
        preparo.status = CmeRules.PREPARO_PRONTO;
        return preparo;
    }

    private void exigirEtapa(CmePreparo preparo, String etapa) {
        String bloqueio = CmeRules.bloqueioEtapaPreparo(preparo.status, etapa);
        if (bloqueio != null) {
            throw new IllegalStateException(bloqueio);
        }
    }

    private CmePreparo preparoDaUnidade(String unidade, Long idpreparo) {
        CmePreparo preparo = idpreparo == null ? null
                : CmePreparo.<CmePreparo>find("idpreparo = ?1 and unidade = ?2", idpreparo, unidade).firstResult();
        if (preparo == null) {
            throw new NotFoundException("Caixa nao encontrada no preparo desta unidade.");
        }
        return preparo;
    }

    private void reprovarInterno(CmeCiclo ciclo, String motivo, String usuario) {
        String anterior = ciclo.status;
        ciclo.status = CmeRules.CICLO_REPROVADO;
        ciclo.motivoReprovacao = motivo;
        ciclo.dataAtualizacao = LocalDateTime.now();
        ciclo.usuarioAtualizacao = usuario;
        evento(ciclo.idciclo, null, "CICLO_REPROVADO", anterior, ciclo.status, motivo, usuario);

        int recolhidos = 0;
        int expostos = 0;
        for (CmePacote pacote : pacotesDoCiclo(ciclo.idciclo)) {
            if (CmeRules.recolhivel(pacote.status)) {
                String antes = pacote.status;
                pacote.status = CmeRules.PACOTE_RECOLHIDO;
                tocar(pacote, usuario);
                evento(ciclo.idciclo, pacote.idpacote, "RECOLHIDO", antes, pacote.status, "Ciclo reprovado", usuario);
                recolhidos++;
            } else if (CmeRules.PACOTE_UTILIZADO.equals(pacote.status)) {
                expostos++;
            }
        }
        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        log.setUnidade(ciclo.unidade);
        log.setEvento("CME ciclo " + ciclo.numeroCiclo + " (" + ciclo.equipamento + ") reprovado - "
                + recolhidos + " pacote(s) recolhido(s), " + expostos + " ja utilizado(s) em paciente"
                + (vazio(usuario) ? "" : " - por matricula " + usuario));
        logService.salvarLogAsync(log);
    }

    private void exigirSaida(CmePacote pacote, boolean paraUso) {
        CmeCiclo ciclo = CmeCiclo.findById(pacote.idciclo);
        String bloqueio = CmeRules.bloqueioSaida(ciclo == null ? null : ciclo.status, pacote.status, pacote.validade,
                LocalDate.now(), paraUso);
        if (bloqueio != null) {
            throw new IllegalStateException(bloqueio);
        }
    }

    private CmeCiclo cicloDaUnidade(String unidade, Long idciclo) {
        CmeCiclo ciclo = idciclo == null ? null
                : CmeCiclo.<CmeCiclo>find("idciclo = ?1 and unidade = ?2", idciclo, unidade).firstResult();
        if (ciclo == null) {
            throw new NotFoundException("Ciclo nao encontrado nesta unidade.");
        }
        return ciclo;
    }

    private CmePacote pacoteDaUnidade(String unidade, String codigo) {
        String c = CmeRules.normalizar(codigo);
        CmePacote pacote = c.isEmpty() ? null
                : CmePacote.<CmePacote>find("codigo = ?1 and unidade = ?2", c, unidade).firstResult();
        if (pacote == null) {
            throw new NotFoundException("Etiqueta nao encontrada nesta unidade.");
        }
        return pacote;
    }

    private List<CmePacote> pacotesDoCiclo(Long idciclo) {
        return CmePacote.<CmePacote>find("idciclo = ?1 order by idpacote asc", idciclo).list();
    }

    private void tocar(CmePacote pacote, String usuario) {
        pacote.dataAtualizacao = LocalDateTime.now();
        pacote.usuarioAtualizacao = usuario;
    }

    private void evento(Long idciclo, Long idpacote, String tipo, String anterior, String novo, String detalhe, String usuario) {
        CmeEvento e = new CmeEvento();
        e.idciclo = idciclo;
        e.idpacote = idpacote;
        e.evento = tipo;
        e.statusAnterior = anterior;
        e.statusNovo = novo;
        e.detalhe = detalhe;
        e.usuario = usuario;
        e.persist();
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    private static String texto(Map<String, Object> payload, String chave) {
        Object v = payload == null ? null : payload.get(chave);
        return v == null ? null : String.valueOf(v);
    }

    private static int inteiro(Map<String, Object> payload, String chave, int padrao) {
        String v = texto(payload, chave);
        if (vazio(v)) return padrao;
        try {
            return new BigDecimal(v.trim()).intValue();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numerico invalido em " + chave + ".");
        }
    }

    private static BigDecimal decimal(Map<String, Object> payload, String chave) {
        String v = texto(payload, chave);
        if (vazio(v)) return null;
        try {
            return new BigDecimal(v.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numerico invalido em " + chave + ".");
        }
    }

    private static LocalDate data(Map<String, Object> payload, String chave) {
        String v = texto(payload, chave);
        if (vazio(v)) return null;
        try {
            return LocalDate.parse(v.trim().substring(0, Math.min(10, v.trim().length())));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Data invalida em " + chave + " (use aaaa-MM-dd).");
        }
    }
}

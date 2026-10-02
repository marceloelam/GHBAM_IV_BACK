package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.iv.entity.ConfiguracaoConformidadeSla;
import br.com.iv.entity.ConfiguracaoConformidadeSlaHistorico;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConfiguracaoConformidadeSlaService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    EntityManager entityManager;

    @Transactional
    public List<Map<String, Object>> listar(String unidade, boolean apenasAtivos) {
        garantirTabela();
        String unidadeEfetiva = unidadeNormalizada(unidade);
        List<ConfiguracaoConformidadeSla> salvas = ConfiguracaoConformidadeSla
                .list("(?1 = 'TODAS' or upper(unidade) = upper(?1) or upper(unidade) = 'TODAS') "
                        + (apenasAtivos ? "and ativo = 'S' " : "")
                        + "order by categoria, unidade", unidadeEfetiva);
        if (!salvas.isEmpty()) {
            return salvas.stream().map(this::toMap).toList();
        }
        return regrasPadrao().stream().map(this::toMap).toList();
    }

    @Transactional
    public Map<String, Object> regraPara(String unidade, String categoria) {
        garantirTabela();
        String unidadeEfetiva = unidadeNormalizada(unidade);
        String categoriaEfetiva = categoriaNormalizada(categoria);

        Optional<ConfiguracaoConformidadeSla> unidadeRegra = ConfiguracaoConformidadeSla.find(
                "upper(unidade) = upper(?1) and upper(categoria) = upper(?2) and ativo = 'S'",
                unidadeEfetiva,
                categoriaEfetiva).firstResultOptional();
        if (unidadeRegra.isPresent()) return toMap(unidadeRegra.get());

        Optional<ConfiguracaoConformidadeSla> globalRegra = ConfiguracaoConformidadeSla.find(
                "upper(unidade) = 'TODAS' and upper(categoria) = upper(?1) and ativo = 'S'",
                categoriaEfetiva).firstResultOptional();
        if (globalRegra.isPresent()) return toMap(globalRegra.get());

        return toMap(regraPadrao(categoriaEfetiva));
    }

    @Transactional
    public List<Map<String, Object>> listarHistorico(String unidade) {
        garantirTabela();
        String unidadeEfetiva = unidadeNormalizada(unidade);
        List<ConfiguracaoConformidadeSlaHistorico> historicos = ConfiguracaoConformidadeSlaHistorico
                .list("(?1 = 'TODAS' or upper(unidade) = upper(?1)) order by criadoEm desc", unidadeEfetiva);
        return historicos.stream()
                .limit(100)
                .map(this::historicoToMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> salvar(Map<String, Object> dto) {
        garantirTabela();
        ConfiguracaoConformidadeSla regra = new ConfiguracaoConformidadeSla();
        aplicar(regra, dto);
        regra.setCriadoEm(LocalDateTime.now());
        regra.persist();
        registrarHistorico("CRIACAO", usuario(dto), null, regra);
        return toMap(regra);
    }

    @Transactional
    public Map<String, Object> atualizar(int id, Map<String, Object> dto) {
        garantirTabela();
        ConfiguracaoConformidadeSla regra = buscar(id);
        String anterior = resumo(regra);
        aplicar(regra, dto);
        regra.setAtualizadoEm(LocalDateTime.now());
        registrarHistorico("EDICAO", usuario(dto), anterior, regra);
        return toMap(regra);
    }

    @Transactional
    public Map<String, Object> alterarAtivo(int id, Map<String, Object> dto) {
        garantirTabela();
        ConfiguracaoConformidadeSla regra = buscar(id);
        String anterior = resumo(regra);
        regra.setAtivo("N".equalsIgnoreCase(texto(dto == null ? null : dto.get("ativo"))) ? "N" : "S");
        regra.setAtualizadoEm(LocalDateTime.now());
        registrarHistorico("S".equalsIgnoreCase(regra.getAtivo()) ? "ATIVACAO" : "INATIVACAO", usuario(dto), anterior, regra);
        return toMap(regra);
    }

    @Transactional
    public void excluir(int id, String usuario) {
        garantirTabela();
        ConfiguracaoConformidadeSla regra = buscar(id);
        registrarHistorico("EXCLUSAO", usuarioOuPadrao(usuario), resumo(regra), null, regra);
        regra.delete();
    }

    public Map<String, Object> toMap(ConfiguracaoConformidadeSla regra) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idconfiguracaoconformidadesla", regra.getIdconfiguracaoconformidadesla());
        item.put("unidade", regra.getUnidade());
        item.put("categoria", regra.getCategoria());
        item.put("responsavel", regra.getResponsavel());
        item.put("slaMinutos", regra.getSlaMinutos());
        item.put("orientacao", regra.getOrientacao());
        item.put("ativo", regra.getAtivo());
        item.put("criadoEm", regra.getCriadoEm());
        item.put("atualizadoEm", regra.getAtualizadoEm());
        return item;
    }

    public Map<String, Object> historicoToMap(ConfiguracaoConformidadeSlaHistorico historico) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idconfiguracaoconformidadeslahistorico", historico.getIdconfiguracaoconformidadeslahistorico());
        item.put("idconfiguracaoconformidadesla", historico.getIdconfiguracaoconformidadesla());
        item.put("unidade", historico.getUnidade());
        item.put("categoria", historico.getCategoria());
        item.put("acao", historico.getAcao());
        item.put("usuario", historico.getUsuario());
        item.put("valorAnterior", historico.getValorAnterior());
        item.put("valorNovo", historico.getValorNovo());
        item.put("criadoEm", historico.getCriadoEm());
        return item;
    }

    private ConfiguracaoConformidadeSla buscar(int id) {
        Optional<ConfiguracaoConformidadeSla> regra = ConfiguracaoConformidadeSla.findByIdOptional(id);
        if (regra.isEmpty()) {
            throw new IllegalArgumentException("Configuracao de SLA nao encontrada.");
        }
        return regra.get();
    }

    private void garantirTabela() {
        entityManager.createNativeQuery("""
                create sequence if not exists configuracao_conformidade_sla_idconfiguracaoconformidadesla_seq
                """).executeUpdate();
        entityManager.createNativeQuery("""
                create table if not exists configuracao_conformidade_sla (
                    idconfiguracaoconformidadesla integer primary key default nextval('configuracao_conformidade_sla_idconfiguracaoconformidadesla_seq'),
                    unidade varchar(30),
                    categoria varchar(50),
                    responsavel varchar(120),
                    sla_minutos integer,
                    orientacao text,
                    ativo varchar(1),
                    criado_em timestamp,
                    atualizado_em timestamp
                )
                """).executeUpdate();
        entityManager.createNativeQuery("""
                create sequence if not exists configuracao_conformidade_sla_historico_idconfiguracaoconformidadeslahistorico_seq
                """).executeUpdate();
        entityManager.createNativeQuery("""
                create table if not exists configuracao_conformidade_sla_historico (
                    idconfiguracaoconformidadeslahistorico integer primary key default nextval('configuracao_conformidade_sla_historico_idconfiguracaoconformidadeslahistorico_seq'),
                    idconfiguracaoconformidadesla integer,
                    unidade varchar(30),
                    categoria varchar(50),
                    acao varchar(30),
                    usuario varchar(120),
                    valor_anterior text,
                    valor_novo text,
                    criado_em timestamp
                )
                """).executeUpdate();
    }

    private void aplicar(ConfiguracaoConformidadeSla regra, Map<String, Object> dto) {
        regra.setUnidade(unidadeNormalizada(texto(dto == null ? null : dto.get("unidade"))));
        regra.setCategoria(categoriaNormalizada(texto(dto == null ? null : dto.get("categoria"))));
        regra.setResponsavel(textoOuPadrao(dto == null ? null : dto.get("responsavel"), "Coordenacao PA"));
        regra.setSlaMinutos(Math.max(5, numero(dto == null ? null : dto.get("slaMinutos"), 120)));
        regra.setOrientacao(textoOuPadrao(dto == null ? null : dto.get("orientacao"), "Direcionar pendencia ao setor responsavel."));
        regra.setAtivo("N".equalsIgnoreCase(texto(dto == null ? null : dto.get("ativo"))) ? "N" : "S");
    }

    private List<ConfiguracaoConformidadeSla> regrasPadrao() {
        List<ConfiguracaoConformidadeSla> regras = new ArrayList<>();
        for (String categoria : List.of("Assinatura", "Medicacao", "Farmacia", "Faturamento", "Pedidos", "Triagem", "Operacional")) {
            regras.add(regraPadrao(categoria));
        }
        return regras;
    }

    private ConfiguracaoConformidadeSla regraPadrao(String categoria) {
        ConfiguracaoConformidadeSla regra = new ConfiguracaoConformidadeSla();
        regra.setUnidade("TODAS");
        regra.setCategoria(categoriaNormalizada(categoria));
        regra.setAtivo("S");
        switch (regra.getCategoria()) {
            case "Assinatura" -> preencher(regra, "Medico/Enfermagem", 120, "Regularizar assinatura/documento clinico.");
            case "Medicacao" -> preencher(regra, "Enfermagem", 30, "Verificar checagem, recebimento ou administracao pendente.");
            case "Farmacia" -> preencher(regra, "Farmacia", 45, "Priorizar dispensacao ou retorno da farmacia.");
            case "Faturamento" -> preencher(regra, "Faturamento", 480, "Vincular procedimento real antes do fechamento do plantao.");
            case "Pedidos" -> preencher(regra, "Setor executor", 120, "Concluir pedido, exame ou retorno operacional.");
            case "Triagem" -> preencher(regra, "Triagem", 30, "Regularizar risco/classificacao do atendimento.");
            default -> preencher(regra, "Coordenacao PA", 120, "Direcionar pendencia operacional ao setor responsavel.");
        }
        return regra;
    }

    private void preencher(ConfiguracaoConformidadeSla regra, String responsavel, int slaMinutos, String orientacao) {
        regra.setResponsavel(responsavel);
        regra.setSlaMinutos(slaMinutos);
        regra.setOrientacao(orientacao);
    }

    private String categoriaNormalizada(String categoria) {
        String value = textoOuPadrao(categoria, "Operacional").trim();
        for (String item : List.of("Assinatura", "Medicacao", "Farmacia", "Faturamento", "Pedidos", "Triagem", "Operacional")) {
            if (item.equalsIgnoreCase(value)) return item;
        }
        return "Operacional";
    }

    private String unidadeNormalizada(String unidade) {
        String value = textoOuPadrao(unidade, "TODAS").split(":")[0].trim().toUpperCase();
        return value.isBlank() ? "TODAS" : value;
    }

    private String textoOuPadrao(Object value, String padrao) {
        String result = texto(value);
        return result.isBlank() ? padrao : result;
    }

    private String texto(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String usuario(Map<String, Object> dto) {
        return usuarioOuPadrao(texto(dto == null ? null : dto.get("usuario")));
    }

    private String usuarioOuPadrao(String usuario) {
        String value = texto(usuario);
        return value.isBlank() ? "Sistema" : value;
    }

    private String resumo(ConfiguracaoConformidadeSla regra) {
        if (regra == null) return null;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("unidade", regra.getUnidade());
        item.put("categoria", regra.getCategoria());
        item.put("responsavel", regra.getResponsavel());
        item.put("slaMinutos", regra.getSlaMinutos());
        item.put("orientacao", regra.getOrientacao());
        item.put("ativo", regra.getAtivo());
        return item.toString();
    }

    private void registrarHistorico(String acao, String usuario, String anterior, ConfiguracaoConformidadeSla regra) {
        registrarHistorico(acao, usuario, anterior, regra == null ? null : resumo(regra), regra);
    }

    private void registrarHistorico(String acao, String usuario, String anterior, String novo, ConfiguracaoConformidadeSla regra) {
        ConfiguracaoConformidadeSlaHistorico historico = new ConfiguracaoConformidadeSlaHistorico();
        historico.setIdconfiguracaoconformidadesla(regra == null ? null : regra.getIdconfiguracaoconformidadesla());
        historico.setUnidade(regra == null ? null : regra.getUnidade());
        historico.setCategoria(regra == null ? null : regra.getCategoria());
        historico.setAcao(acao);
        historico.setUsuario(usuarioOuPadrao(usuario));
        historico.setValorAnterior(anterior);
        historico.setValorNovo(novo);
        historico.setCriadoEm(LocalDateTime.now());
        historico.persist();
    }

    private int numero(Object value, int padrao) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return padrao;
        }
    }
}

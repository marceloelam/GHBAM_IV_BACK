package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.iv.entity.OcorrenciaAlertaConfig;
import br.com.iv.entity.OcorrenciaSistema;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OcorrenciaSistemaService implements Serializable {
    private static final long serialVersionUID = 1L;
    private volatile boolean tabelaGarantida;
    private volatile boolean tabelaAlertaConfigGarantida;

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public List<Map<String, Object>> listar(String unidade, String status, String tipo, String busca) {
        garantirTabela();

        String unidadeEfetiva = unidadeNormalizada(unidade);
        String statusEfetivo = texto(status).toLowerCase();
        String tipoEfetivo = texto(tipo).toLowerCase();
        String buscaTexto = texto(busca).toLowerCase();
        String buscaEfetiva = "%" + buscaTexto + "%";

        String query = "(?1 = 'TODAS' or upper(unidade) = upper(?1)) "
                + "and (?2 = '' or ?2 = 'todos' or (?2 = 'ativas' and status <> 'resolvida') or lower(status) = ?2) "
                + "and (?3 = '' or ?3 = 'todos' or lower(tipo) = ?3) "
                + "and (?4 = '' or lower(titulo) like ?5 or lower(descricao) like ?5 or lower(modulo) like ?5 "
                + "or lower(paciente) like ?5 or lower(bam) like ?5 or lower(responsavel) like ?5) "
                + "order by case when status = 'resolvida' then 1 else 0 end, criadoEm desc";

        List<OcorrenciaSistema> lista = OcorrenciaSistema.list(query, unidadeEfetiva, statusEfetivo, tipoEfetivo, buscaTexto, buscaEfetiva);
        return lista.stream().limit(300).map(this::toMap).toList();
    }

    @Transactional
    public Map<String, Object> salvar(Map<String, Object> dto) {
        garantirTabela();

        Optional<OcorrenciaSistema> automaticaAberta = buscarAutomaticaAberta(dto);
        if (automaticaAberta.isPresent()) {
            OcorrenciaSistema ocorrencia = automaticaAberta.get();
            aplicar(ocorrencia, dto);
            ocorrencia.setAtualizadoEm(LocalDateTime.now());
            registrarHistoricoAgrupamentoAutomatico(
                    ocorrencia,
                    textoOuPadrao(dto.get("criadoPor"), "Sistema"));
            return toMap(ocorrencia);
        }

        OcorrenciaSistema ocorrencia = new OcorrenciaSistema();
        aplicar(ocorrencia, dto);
        ocorrencia.setCriadoEm(LocalDateTime.now());
        ocorrencia.setAtualizadoEm(LocalDateTime.now());
        registrarHistorico(ocorrencia, "criada", textoOuPadrao(dto.get("criadoPor"), "Sistema"), "Ocorrencia registrada.");
        ocorrencia.persist();
        return toMap(ocorrencia);
    }

    @Transactional
    public Map<String, Object> atualizar(int id, Map<String, Object> dto) {
        garantirTabela();
        OcorrenciaSistema ocorrencia = buscar(id);
        String responsavelAnterior = texto(ocorrencia.getResponsavel());
        aplicar(ocorrencia, dto);
        ocorrencia.setAtualizadoEm(LocalDateTime.now());
        String usuario = usuarioAcao(dto);
        String responsavelNovo = texto(ocorrencia.getResponsavel());
        if (!responsavelNovo.equalsIgnoreCase(responsavelAnterior)) {
            registrarHistorico(ocorrencia, "assumida", usuario, "Responsavel alterado para " + responsavelNovo + ".");
        } else {
            registrarHistorico(ocorrencia, "atualizada", usuario, textoOuPadrao(dto == null ? null : dto.get("observacao"), "Ocorrencia atualizada."));
        }
        return toMap(ocorrencia);
    }

    @Transactional
    public Map<String, Object> alterarStatus(int id, Map<String, Object> dto) {
        garantirTabela();
        OcorrenciaSistema ocorrencia = buscar(id);
        String status = statusNormalizado(texto(dto == null ? null : dto.get("status")));
        ocorrencia.setStatus(status);
        ocorrencia.setAtualizadoEm(LocalDateTime.now());
        registrarHistorico(ocorrencia, acaoPorStatus(status), usuarioAcao(dto), texto(dto == null ? null : dto.get("observacao")));
        return toMap(ocorrencia);
    }

    @Transactional
    public void excluir(int id) {
        garantirTabela();
        // Exclusao idempotente: cliques/requisicoes concorrentes para o mesmo
        // registro devem terminar com o recurso ausente, sem OptimisticLockException.
        entityManager.createNativeQuery("""
                delete from ocorrencia_sistema
                 where idocorrenciasistema = ?1
                """)
                .setParameter(1, id)
                .executeUpdate();
    }

    @Transactional
    public Map<String, Object> obterAlertaConfig(String unidade) {
        garantirTabelaAlertaConfig();
        String unidadeEfetiva = unidadeNormalizada(unidade);
        Optional<OcorrenciaAlertaConfig> existente = OcorrenciaAlertaConfig
                .find("upper(unidade) = upper(?1)", unidadeEfetiva)
                .firstResultOptional();
        boolean ativo = existente.map(item -> !"N".equalsIgnoreCase(texto(item.getAtivo()))).orElse(true);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("unidade", unidadeEfetiva);
        map.put("ativo", ativo);
        map.put("atualizadoPor", existente.map(OcorrenciaAlertaConfig::getAtualizadoPor).orElse(null));
        map.put("atualizadoEm", existente.map(OcorrenciaAlertaConfig::getAtualizadoEm).orElse(null));
        return map;
    }

    @Transactional
    public Map<String, Object> salvarAlertaConfig(Map<String, Object> dto) {
        garantirTabelaAlertaConfig();
        String unidadeEfetiva = unidadeNormalizada(texto(dto == null ? null : dto.get("unidade")));
        Object valorAtivo = dto == null ? null : dto.get("ativo");
        boolean ativo = valorAtivo == null
                || Boolean.TRUE.equals(valorAtivo)
                || "true".equalsIgnoreCase(String.valueOf(valorAtivo))
                || "s".equalsIgnoreCase(String.valueOf(valorAtivo));

        OcorrenciaAlertaConfig registro = OcorrenciaAlertaConfig
                .<OcorrenciaAlertaConfig>find("upper(unidade) = upper(?1)", unidadeEfetiva)
                .firstResultOptional()
                .orElseGet(() -> {
                    OcorrenciaAlertaConfig novo = new OcorrenciaAlertaConfig();
                    novo.setUnidade(unidadeEfetiva);
                    return novo;
                });
        registro.setAtivo(ativo ? "S" : "N");
        registro.setAtualizadoPor(limitar(textoOuPadrao(dto == null ? null : dto.get("usuario"), "Sistema"), 120));
        registro.setAtualizadoEm(LocalDateTime.now());
        if (registro.getIdocorrenciaalertaconfig() == null) {
            registro.persist();
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("unidade", registro.getUnidade());
        map.put("ativo", ativo);
        map.put("atualizadoPor", registro.getAtualizadoPor());
        map.put("atualizadoEm", registro.getAtualizadoEm());
        return map;
    }

    public Map<String, Object> toMap(OcorrenciaSistema item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getIdocorrenciasistema());
        map.put("idocorrenciasistema", item.getIdocorrenciasistema());
        map.put("unidade", item.getUnidade());
        map.put("tipo", item.getTipo());
        map.put("gravidade", item.getGravidade());
        map.put("status", item.getStatus());
        map.put("modulo", item.getModulo());
        map.put("responsavel", item.getResponsavel());
        map.put("paciente", item.getPaciente());
        map.put("bam", item.getBam());
        map.put("titulo", item.getTitulo());
        map.put("descricao", item.getDescricao());
        map.put("origem", item.getOrigem());
        map.put("path", item.getPath());
        map.put("criadoPor", item.getCriadoPor());
        map.put("criadaEm", item.getCriadoEm());
        map.put("criadoEm", item.getCriadoEm());
        map.put("atualizadaEm", item.getAtualizadoEm());
        map.put("atualizadoEm", item.getAtualizadoEm());
        map.put("historico", historico(item));
        return map;
    }

    private OcorrenciaSistema buscar(int id) {
        Optional<OcorrenciaSistema> item = OcorrenciaSistema.findByIdOptional(id);
        if (item.isEmpty()) {
            throw new IllegalArgumentException("Ocorrencia nao encontrada.");
        }
        return item.get();
    }

    private Optional<OcorrenciaSistema> buscarAutomaticaAberta(Map<String, Object> dto) {
        String origem = texto(dto == null ? null : dto.get("origem"));
        String tipo = tipoNormalizado(texto(dto == null ? null : dto.get("tipo")));
        if (!"automatica".equalsIgnoreCase(origem)) {
            return Optional.empty();
        }

        String unidade = unidadeNormalizada(texto(dto == null ? null : dto.get("unidade")));
        String titulo = textoOuPadrao(dto == null ? null : dto.get("titulo"), "Ocorrencia sem titulo");
        if ("epidemiologia".equals(tipo)) {
            return OcorrenciaSistema.<OcorrenciaSistema>find(
                    "upper(unidade) = upper(?1) and lower(tipo) = ?2 and lower(titulo) = lower(?3) "
                            + "and lower(modulo) = lower(?4) and status <> 'resolvida' and lower(origem) = 'automatica' order by criadoEm desc",
                    unidade,
                    tipo,
                    titulo,
                    textoOuPadrao(dto == null ? null : dto.get("modulo"), "Vigilancia epidemiologica"))
                    .firstResultOptional();
        }
        if ("backend".equals(tipo)) {
            return OcorrenciaSistema.<OcorrenciaSistema>find(
                    "upper(unidade) = upper(?1) and lower(tipo) = ?2 and lower(titulo) = lower(?3) "
                            + "and status <> 'resolvida' and lower(origem) = 'automatica' order by criadoEm desc",
                    unidade,
                    tipo,
                    titulo)
                    .firstResultOptional();
        }

        String descricao = texto(dto == null ? null : dto.get("descricao"));
        String path = texto(dto == null ? null : dto.get("path"));
        if ("frontend".equalsIgnoreCase(texto(dto == null ? null : dto.get("modulo")))) {
            // Erro de JavaScript e do codigo, nao da tela: o mesmo erro em varias rotas vira uma ocorrencia so.
            return OcorrenciaSistema.<OcorrenciaSistema>find(
                    "upper(unidade) = upper(?1) and lower(tipo) = ?2 and lower(titulo) = lower(?3) "
                            + "and lower(descricao) = lower(?4) and lower(modulo) = 'frontend' "
                            + "and status <> 'resolvida' and lower(origem) = 'automatica' order by criadoEm desc",
                    unidade,
                    tipo,
                    titulo,
                    descricao)
                    .firstResultOptional();
        }
        return OcorrenciaSistema.<OcorrenciaSistema>find(
                "upper(unidade) = upper(?1) and lower(tipo) = ?2 and lower(titulo) = lower(?3) "
                        + "and lower(descricao) = lower(?4) and lower(path) = lower(?5) "
                        + "and status <> 'resolvida' and lower(origem) = 'automatica' order by criadoEm desc",
                unidade,
                tipo,
                titulo,
                descricao,
                path)
                .firstResultOptional();
    }

    private void aplicar(OcorrenciaSistema item, Map<String, Object> dto) {
        item.setUnidade(unidadeNormalizada(texto(dto == null ? null : dto.get("unidade"))));
        item.setTipo(tipoNormalizado(texto(dto == null ? null : dto.get("tipo"))));
        item.setGravidade(gravidadeNormalizada(texto(dto == null ? null : dto.get("gravidade"))));
        item.setStatus(statusNormalizado(textoOuPadrao(dto == null ? null : dto.get("status"), item.getStatus())));
        item.setModulo(limitar(textoOuPadrao(dto == null ? null : dto.get("modulo"), "Sistema"), 80));
        item.setResponsavel(limitar(textoOuPadrao(dto == null ? null : dto.get("responsavel"), "Administrativo"), 120));
        item.setPaciente(limitar(texto(dto == null ? null : dto.get("paciente")), 160));
        item.setBam(limitar(texto(dto == null ? null : dto.get("bam")), 40));
        item.setTitulo(limitar(textoOuPadrao(dto == null ? null : dto.get("titulo"), "Ocorrencia sem titulo"), 180));
        item.setDescricao(texto(dto == null ? null : dto.get("descricao")));
        item.setOrigem(limitar(textoOuPadrao(dto == null ? null : dto.get("origem"), "Manual"), 40));
        item.setPath(limitar(texto(dto == null ? null : dto.get("path")), 250));
        item.setCriadoPor(limitar(textoOuPadrao(dto == null ? null : dto.get("criadoPor"), "Sistema"), 120));
    }

    private synchronized void garantirTabela() {
        if (tabelaGarantida) {
            return;
        }

        if (!sequenciaExiste("ocorrencia_sistema_idocorrenciasistema_seq")) {
            entityManager.createNativeQuery("""
                    create sequence if not exists ocorrencia_sistema_idocorrenciasistema_seq
                    """).executeUpdate();
        }

        if (!tabelaExiste("ocorrencia_sistema")) {
            entityManager.createNativeQuery("""
                    create table if not exists ocorrencia_sistema (
                        idocorrenciasistema integer primary key default nextval('ocorrencia_sistema_idocorrenciasistema_seq'),
                        unidade varchar(30),
                        tipo varchar(40),
                        gravidade varchar(20),
                        status varchar(20),
                        modulo varchar(80),
                        responsavel varchar(120),
                        paciente varchar(160),
                        bam varchar(40),
                        titulo varchar(180),
                        descricao text,
                        origem varchar(40),
                        path varchar(250),
                        criado_por varchar(120),
                        criado_em timestamp,
                        atualizado_em timestamp,
                        historico text
                    )
                    """).executeUpdate();
        } else if (!colunaExiste("ocorrencia_sistema", "historico")) {
            entityManager.createNativeQuery("""
                    alter table ocorrencia_sistema add column historico text
                    """).executeUpdate();
        }

        if (!indiceExiste("idx_ocorrencia_sistema_unidade_status")) {
            entityManager.createNativeQuery("""
                    create index if not exists idx_ocorrencia_sistema_unidade_status on ocorrencia_sistema (unidade, status)
                    """).executeUpdate();
        }
        if (!indiceExiste("idx_ocorrencia_sistema_criado_em")) {
            entityManager.createNativeQuery("""
                    create index if not exists idx_ocorrencia_sistema_criado_em on ocorrencia_sistema (criado_em desc)
                    """).executeUpdate();
        }

        tabelaGarantida = true;
    }

    private synchronized void garantirTabelaAlertaConfig() {
        if (tabelaAlertaConfigGarantida) {
            return;
        }

        if (!sequenciaExiste("ocorrencia_alerta_config_idocorrenciaalertaconfig_seq")) {
            entityManager.createNativeQuery("""
                    create sequence if not exists ocorrencia_alerta_config_idocorrenciaalertaconfig_seq
                    """).executeUpdate();
        }
        if (!tabelaExiste("ocorrencia_alerta_config")) {
            entityManager.createNativeQuery("""
                    create table if not exists ocorrencia_alerta_config (
                        idocorrenciaalertaconfig integer primary key default nextval('ocorrencia_alerta_config_idocorrenciaalertaconfig_seq'),
                        unidade varchar(30) unique,
                        ativo varchar(1) default 'S',
                        atualizado_por varchar(120),
                        atualizado_em timestamp
                    )
                    """).executeUpdate();
        }

        tabelaAlertaConfigGarantida = true;
    }

    private boolean tabelaExiste(String tabela) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*)
                  from information_schema.tables
                 where table_schema = current_schema()
                   and table_name = ?1
                """).setParameter(1, tabela).getSingleResult();
        return count.longValue() > 0;
    }

    private boolean colunaExiste(String tabela, String coluna) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*)
                  from information_schema.columns
                 where table_schema = current_schema()
                   and table_name = ?1
                   and column_name = ?2
                """).setParameter(1, tabela).setParameter(2, coluna).getSingleResult();
        return count.longValue() > 0;
    }

    private boolean indiceExiste(String indice) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*)
                  from pg_indexes
                 where schemaname = current_schema()
                   and indexname = ?1
                """).setParameter(1, indice).getSingleResult();
        return count.longValue() > 0;
    }

    private boolean sequenciaExiste(String sequencia) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*)
                  from information_schema.sequences
                 where sequence_schema = current_schema()
                   and sequence_name = ?1
                """).setParameter(1, sequencia).getSingleResult();
        return count.longValue() > 0;
    }

    private String unidadeNormalizada(String unidade) {
        String value = textoOuPadrao(unidade, "TODAS").split(":")[0].trim().toUpperCase();
        return value.isBlank() ? "TODAS" : limitar(value, 30);
    }

    private String tipoNormalizado(String value) {
        String normalized = textoOuPadrao(value, "operacional").toLowerCase();
        return List.of("conformidade", "sessao", "backend", "impressao", "carregamento", "operacional", "epidemiologia").contains(normalized)
                ? normalized
                : "operacional";
    }

    private String gravidadeNormalizada(String value) {
        String normalized = textoOuPadrao(value, "media").toLowerCase();
        return List.of("critica", "alta", "media", "baixa").contains(normalized) ? normalized : "media";
    }

    private String statusNormalizado(String value) {
        String normalized = textoOuPadrao(value, "aberta").toLowerCase();
        return List.of("aberta", "analise", "resolvida").contains(normalized) ? normalized : "aberta";
    }

    private String acaoPorStatus(String status) {
        if ("analise".equals(status)) {
            return "em_analise";
        }
        if ("resolvida".equals(status)) {
            return "resolvida";
        }
        return "reaberta";
    }

    private String usuarioAcao(Map<String, Object> dto) {
        return limitar(textoOuPadrao(dto == null ? null : dto.get("usuario"), textoOuPadrao(dto == null ? null : dto.get("criadoPor"), "Sistema")), 120);
    }

    private List<Map<String, Object>> historico(OcorrenciaSistema item) {
        String raw = texto(item.getHistorico());
        if (raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void registrarHistoricoAgrupamentoAutomatico(OcorrenciaSistema item, String usuario) {
        List<Map<String, Object>> historico = historico(item);
        if (!historico.isEmpty()) {
            Map<String, Object> ultimo = historico.get(0);
            String observacao = texto(ultimo.get("observacao"));
            String dataHora = texto(ultimo.get("dataHora"));
            if ("Ocorrencia automatica agrupada para evitar duplicidade.".equals(observacao)) {
                try {
                    LocalDateTime ultimaAtualizacao = LocalDateTime.parse(dataHora);
                    if (ultimaAtualizacao.isAfter(LocalDateTime.now().minusHours(1))) {
                        return;
                    }
                } catch (Exception ignored) {
                    // Historico legado/invalido: registra novo evento para preservar rastreabilidade.
                }
            }
        }
        registrarHistorico(item, "atualizada", usuario,
                "Ocorrencia automatica agrupada para evitar duplicidade.");
    }
    private void registrarHistorico(OcorrenciaSistema item, String acao, String usuario, String observacao) {
        List<Map<String, Object>> historico = historico(item);
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("acao", limitar(textoOuPadrao(acao, "atualizada"), 40));
        evento.put("usuario", limitar(textoOuPadrao(usuario, "Sistema"), 120));
        evento.put("observacao", limitar(texto(observacao), 500));
        evento.put("dataHora", LocalDateTime.now());
        historico.add(0, evento);
        try {
            item.setHistorico(objectMapper.writeValueAsString(historico.stream().limit(50).toList()));
        } catch (Exception e) {
            item.setHistorico("[]");
        }
    }

    private String textoOuPadrao(Object value, String padrao) {
        String result = texto(value);
        return result.isBlank() ? padrao : result;
    }

    private String texto(Object value) {
        return value == null ? "" : String.valueOf(value).replaceAll("[\\r\\n]+", " ").trim();
    }

    private String limitar(String value, int max) {
        String text = texto(value);
        return text.length() <= max ? text : text.substring(0, max);
    }
}

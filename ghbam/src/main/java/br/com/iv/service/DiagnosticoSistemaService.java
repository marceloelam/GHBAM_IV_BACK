package br.com.iv.service;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.hibernate.Session;

import br.com.iv.entity.DiagnosticoSistema;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DiagnosticoSistemaService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    EntityManager entityManager;

    @Transactional
    public void onStart(@Observes StartupEvent ev) {
        garantirTabela();
    }

    @Transactional
    public List<Map<String, Object>> listar(String unidade, String tipo, String modulo, String severidade, String busca,
            String inicio, String fim) {
        String unidadeEfetiva = unidadeNormalizada(unidade);
        String tipoEfetivo = texto(tipo).toLowerCase();
        String moduloEfetivo = texto(modulo).toLowerCase();
        String severidadeEfetiva = texto(severidade).toLowerCase();
        String buscaTexto = texto(busca).toLowerCase();
        String buscaEfetiva = "%" + buscaTexto + "%";
        LocalDateTime inicioEfetivo = parseData(inicio, LocalDate.now().minusDays(1).atStartOfDay());
        LocalDateTime fimEfetivo = parseData(fim, LocalDateTime.now().plusDays(1));

        String query = "(?1 = 'TODAS' or upper(unidade) = upper(?1)) "
                + "and (?2 = '' or ?2 = 'todos' or lower(tipo) = ?2) "
                + "and (?3 = '' or ?3 = 'todos' or lower(modulo) = ?3) "
                + "and (?4 = '' or ?4 = 'todos' or lower(severidade) = ?4) "
                + "and criadoEm between ?5 and ?6 "
                + "and (?7 = '' or lower(endpoint) like ?8 or lower(rota) like ?8 or lower(motivo) like ?8 "
                + "or lower(detalhes) like ?8 or lower(usuario) like ?8 or lower(matricula) like ?8) "
                + "order by criadoEm desc";

        List<DiagnosticoSistema> lista = DiagnosticoSistema.list(query, unidadeEfetiva, tipoEfetivo, moduloEfetivo,
                severidadeEfetiva, inicioEfetivo, fimEfetivo, buscaTexto, buscaEfetiva);
        return lista.stream().limit(1000).map(this::toMap).toList();
    }

    public Map<String, Object> salvar(Map<String, Object> dto) {
        return retryDeadlock(() -> salvarTx(dto));
    }

    @Transactional
    protected Map<String, Object> salvarTx(Map<String, Object> dto) {
        DiagnosticoSistema item = new DiagnosticoSistema();
        aplicar(item, dto);
        item.setCriadoEm(LocalDateTime.now());
        item.persist();
        return toMap(item);
    }

    public int salvarLote(List<Map<String, Object>> itens) {
        return retryDeadlock(() -> salvarLoteTx(itens));
    }

    @Transactional
    protected int salvarLoteTx(List<Map<String, Object>> itens) {
        if (itens == null || itens.isEmpty()) return 0;
        var lista = itens.stream().limit(100).toList();
        var session = entityManager.unwrap(Session.class);
        session.doWork((Connection conn) -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "insert into diagnostico_sistema (unidade, usuario, matricula, tipo, modulo, severidade, "
                    + "metodo, endpoint, rota, status_http, duracao_ms, motivo, detalhes, criado_em) "
                    + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Map<String, Object> dto : lista) {
                    DiagnosticoSistema item = new DiagnosticoSistema();
                    aplicar(item, dto);
                    ps.setString(1, item.getUnidade());
                    ps.setString(2, item.getUsuario());
                    ps.setString(3, item.getMatricula());
                    ps.setString(4, item.getTipo());
                    ps.setString(5, item.getModulo());
                    ps.setString(6, item.getSeveridade());
                    ps.setString(7, item.getMetodo());
                    ps.setString(8, item.getEndpoint());
                    ps.setString(9, item.getRota());
                    if (item.getStatusHttp() != null) ps.setInt(10, item.getStatusHttp()); else ps.setNull(10, Types.INTEGER);
                    if (item.getDuracaoMs() != null) ps.setInt(11, item.getDuracaoMs()); else ps.setNull(11, Types.INTEGER);
                    ps.setString(12, item.getMotivo());
                    ps.setString(13, item.getDetalhes());
                    ps.setObject(14, LocalDateTime.now());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
        return lista.size();
    }

    private <T> T retryDeadlock(Supplier<T> fn) {
        RuntimeException last = null;
        for (int i = 0; i < 3; i++) {
            try {
                return fn.get();
            } catch (RuntimeException e) {
                last = e;
                if (isDeadlock(e) && i < 2) {
                    try { Thread.sleep(50L * (1L << i)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
                } else {
                    throw e;
                }
            }
        }
        throw last;
    }

    private boolean isDeadlock(RuntimeException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof OptimisticLockException) return true;
            String msg = cause.getMessage();
            if (msg != null && msg.contains("deadlock detected")) return true;
            cause = cause.getCause();
        }
        return false;
    }

    public Map<String, Object> toMap(DiagnosticoSistema item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getIddiagnosticosistema());
        map.put("iddiagnosticosistema", item.getIddiagnosticosistema());
        map.put("unidade", item.getUnidade());
        map.put("usuario", item.getUsuario());
        map.put("matricula", item.getMatricula());
        map.put("tipo", item.getTipo());
        map.put("modulo", item.getModulo());
        map.put("severidade", item.getSeveridade());
        map.put("metodo", item.getMetodo());
        map.put("endpoint", item.getEndpoint());
        map.put("rota", item.getRota());
        map.put("statusHttp", item.getStatusHttp());
        map.put("duracaoMs", item.getDuracaoMs());
        map.put("motivo", item.getMotivo());
        map.put("detalhes", item.getDetalhes());
        map.put("dataHora", item.getCriadoEm());
        map.put("criadoEm", item.getCriadoEm());
        return map;
    }

    private void aplicar(DiagnosticoSistema item, Map<String, Object> dto) {
        item.setUnidade(unidadeNormalizada(texto(valor(dto, "unidade"))));
        item.setUsuario(limitar(textoOuPadrao(valor(dto, "usuario"), textoOuPadrao(valor(dto, "autor"), "Sistema")), 120));
        item.setMatricula(limitar(texto(valor(dto, "matricula")), 40));
        item.setTipo(limitar(textoOuPadrao(valor(dto, "tipo"), "evento"), 40));
        item.setModulo(limitar(textoOuPadrao(valor(dto, "modulo"), "Sistema"), 80));
        item.setSeveridade(limitar(textoOuPadrao(valor(dto, "severidade"), "info"), 40));
        item.setMetodo(limitar(texto(valor(dto, "metodo")), 12));
        item.setEndpoint(limitar(texto(valor(dto, "endpoint")), 260));
        item.setRota(limitar(textoOuPadrao(valor(dto, "rota"), texto(valor(dto, "route"))), 260));
        item.setStatusHttp(inteiro(valor(dto, "statusHttp"), inteiro(valor(dto, "status"), null)));
        item.setDuracaoMs(inteiro(valor(dto, "duracaoMs"), inteiro(valor(dto, "durationMs"), null)));
        item.setMotivo(limitar(textoOuPadrao(valor(dto, "motivo"), texto(valor(dto, "reason"))), 180));
        item.setDetalhes(limitar(textoOuPadrao(valor(dto, "detalhes"), texto(valor(dto, "details"))), 4000));
    }

    private void garantirTabela() {
        entityManager.createNativeQuery("""
                create sequence if not exists diagnostico_sistema_iddiagnosticosistema_seq
                """).executeUpdate();
        entityManager.createNativeQuery("""
                create table if not exists diagnostico_sistema (
                    iddiagnosticosistema integer primary key default nextval('diagnostico_sistema_iddiagnosticosistema_seq'),
                    unidade varchar(30),
                    usuario varchar(120),
                    matricula varchar(40),
                    tipo varchar(40),
                    modulo varchar(80),
                    severidade varchar(40),
                    metodo varchar(12),
                    endpoint varchar(260),
                    rota varchar(260),
                    status_http integer,
                    duracao_ms integer,
                    motivo varchar(180),
                    detalhes text,
                    criado_em timestamp
                )
                """).executeUpdate();
        entityManager.createNativeQuery("""
                create index if not exists idx_diagnostico_sistema_unidade_tipo on diagnostico_sistema (unidade, tipo)
                """).executeUpdate();
        entityManager.createNativeQuery("""
                create index if not exists idx_diagnostico_sistema_modulo_criado on diagnostico_sistema (modulo, criado_em desc)
                """).executeUpdate();
        entityManager.createNativeQuery("""
                create index if not exists idx_diagnostico_sistema_criado_em on diagnostico_sistema (criado_em desc)
                """).executeUpdate();
    }

    private Object valor(Map<String, Object> dto, String key) {
        return dto == null ? null : dto.get(key);
    }

    private LocalDateTime parseData(String value, LocalDateTime fallback) {
        String text = texto(value);
        if (text.isBlank()) return fallback;
        try {
            if (text.length() == 10) return LocalDate.parse(text).atStartOfDay();
            return LocalDateTime.parse(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    private Integer inteiro(Object value, Integer fallback) {
        if (value == null || texto(value).isBlank()) return fallback;
        try {
            return (int) Math.round(Double.parseDouble(texto(value)));
        } catch (Exception e) {
            return fallback;
        }
    }

    private String unidadeNormalizada(String unidade) {
        String value = textoOuPadrao(unidade, "TODAS").split(":")[0].trim().toUpperCase();
        return value.isBlank() ? "TODAS" : limitar(value, 30);
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

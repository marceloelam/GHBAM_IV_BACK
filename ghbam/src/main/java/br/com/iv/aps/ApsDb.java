package br.com.iv.aps;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Acesso JDBC do modulo APS. O modulo nao usa @Entity de proposito: em dev o
 * Hibernate roda com database.generation=update e criaria/alteraria tabelas por
 * fora do Flyway. Aqui o schema e so o das migrations V171+.
 *
 * Parametros nomeados (":nome") viram "?" com tipo JDBC explicito; "::jsonb"
 * e casts do Postgres nao sao confundidos com parametro. Colunas json/jsonb
 * voltam como Map/List; datas como texto ISO (yyyy-MM-dd / yyyy-MM-ddTHH:mm:ss).
 */
@ApplicationScoped
public class ApsDb {

    private static final Pattern PARAM = Pattern.compile("(?<![:\\w]):([a-zA-Z_][a-zA-Z0-9_]*)");

    @Inject
    br.com.iv.plataforma.municipio.MunicipioRoteamento municipioRoteamento; // banco do municipio da requisicao

    @Inject
    ObjectMapper mapper;

    /** Valor a ser gravado em coluna jsonb (usar com CAST(:p AS jsonb)). */
    public record Json(Object value) {
    }

    public List<Map<String, Object>> list(String sql, Map<String, ?> params) {
        Parsed parsed = parse(sql);
        try (Connection c = municipioRoteamento.dataSourceAtual().getConnection();
                PreparedStatement ps = c.prepareStatement(parsed.sql())) {
            bind(ps, parsed.names(), params);
            try (ResultSet rs = ps.executeQuery()) {
                return read(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha de banco no modulo APS: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> list(String sql) {
        return list(sql, Map.of());
    }

    public Map<String, Object> one(String sql, Map<String, ?> params) {
        List<Map<String, Object>> rows = list(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int exec(String sql, Map<String, ?> params) {
        Parsed parsed = parse(sql);
        try (Connection c = municipioRoteamento.dataSourceAtual().getConnection();
                PreparedStatement ps = c.prepareStatement(parsed.sql())) {
            bind(ps, parsed.names(), params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha de banco no modulo APS: " + e.getMessage(), e);
        }
    }

    /** INSERT ... RETURNING id. */
    public Long insert(String sql, Map<String, ?> params) {
        Map<String, Object> row = one(sql, params);
        if (row == null || row.get("id") == null) {
            throw new IllegalStateException("INSERT sem RETURNING id no modulo APS.");
        }
        return ((Number) row.get("id")).longValue();
    }

    public Long count(String sql, Map<String, ?> params) {
        Map<String, Object> row = one(sql, params);
        if (row == null || row.isEmpty()) {
            return 0L;
        }
        Object v = row.values().iterator().next();
        return v == null ? 0L : ((Number) v).longValue();
    }

    private record Parsed(String sql, List<String> names) {
    }

    private Parsed parse(String sql) {
        List<String> names = new ArrayList<>();
        Matcher m = PARAM.matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            names.add(m.group(1));
            m.appendReplacement(sb, "?");
        }
        m.appendTail(sb);
        return new Parsed(sb.toString(), names);
    }

    private void bind(PreparedStatement ps, List<String> names, Map<String, ?> params) throws SQLException {
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!params.containsKey(name)) {
                throw new IllegalArgumentException("Parametro SQL ausente: " + name);
            }
            Object v = params.get(name);
            int idx = i + 1;
            if (v == null) {
                ps.setNull(idx, Types.NULL);
            } else if (v instanceof Json json) {
                ps.setString(idx, toJson(json.value()));
            } else if (v instanceof String s) {
                ps.setString(idx, s);
            } else if (v instanceof Integer n) {
                ps.setInt(idx, n);
            } else if (v instanceof Long n) {
                ps.setLong(idx, n);
            } else if (v instanceof Double n) {
                ps.setDouble(idx, n);
            } else if (v instanceof BigDecimal n) {
                ps.setBigDecimal(idx, n);
            } else if (v instanceof Boolean b) {
                ps.setBoolean(idx, b);
            } else if (v instanceof LocalDate d) {
                ps.setObject(idx, d);
            } else if (v instanceof LocalDateTime d) {
                ps.setObject(idx, d);
            } else if (v instanceof LocalTime t) {
                ps.setObject(idx, t);
            } else if (v instanceof Number n) {
                ps.setObject(idx, n);
            } else {
                ps.setString(idx, String.valueOf(v));
            }
        }
    }

    private List<Map<String, Object>> read(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        List<Map<String, Object>> out = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                String label = md.getColumnLabel(i);
                String typeName = md.getColumnTypeName(i);
                Object v = rs.getObject(i);
                row.put(label, normalize(v, typeName));
            }
            out.add(row);
        }
        return out;
    }

    private Object normalize(Object v, String typeName) {
        if (v == null) {
            return null;
        }
        if ("json".equalsIgnoreCase(typeName) || "jsonb".equalsIgnoreCase(typeName)) {
            return fromJson(v.toString());
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof Timestamp t) {
            return t.toLocalDateTime().withNano(0).toString();
        }
        if (v instanceof java.sql.Time t) {
            return t.toLocalTime().toString();
        }
        if (v instanceof java.sql.Array arr) {
            try {
                Object[] items = (Object[]) arr.getArray();
                return List.of(items);
            } catch (SQLException e) {
                return null;
            }
        }
        return v;
    }

    public String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON invalido no modulo APS.", e);
        }
    }

    public Object fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    public Map<String, Object> mapFromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON invalido no modulo APS.", e);
        }
    }

    /** Monta mapa de parametros aceitando valores nulos (Map.of nao aceita). */
    public static Map<String, Object> p(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}

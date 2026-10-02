package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class AssinaturaPendenciaService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    EntityManager entityManager;

    public Map<String, Object> minhasPendencias(AuthenticatedUser user, int dias) {
        List<Map<String, Object>> todas = listarPendencias(user == null ? "" : user.unidade(), dias);
        List<Map<String, Object>> minhas = todas.stream()
                .filter(item -> pertenceAoUsuario(item, user))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("escopo", "MINHAS");
        result.put("matricula", safe(user.matricula()));
        result.put("profissional", safe(user.nome()));
        result.put("total", minhas.size());
        result.put("pendencias", minhas);
        return result;
    }

    public Map<String, Object> relatorioAdministrativo(AuthenticatedUser user, int dias) {
        String unidade = user == null ? "" : user.unidade();
        List<Map<String, Object>> pendencias = listarPendencias(unidade, dias);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("escopo", "ADMINISTRATIVO");
        result.put("unidade", safe(unidade));
        result.put("dias", normalizarDias(dias));
        result.put("total", pendencias.size());
        result.put("pendencias", pendencias);
        result.put("porProfissional", consolidarPorProfissional(pendencias));
        return result;
    }

    private List<Map<String, Object>> listarPendencias(String unidade, int dias) {
        LocalDate corte = LocalDate.now().minusDays(normalizarDias(dias));
        List<Map<String, Object>> result = new ArrayList<>();

        result.addAll(documentos("""
                SELECT 'EVOLUCAO_MEDICA', 'Evolucao medica', e.idevolucao, e.nbam, e.prontuario,
                  e.data, e.horaev, e.medico, e.crm, e.unidade, e.relato, 'MEDICO'
                FROM evolucaopaciente e
                WHERE (e.inativo = false OR e.inativo IS NULL)
                  AND e.data >= :corte
                  AND (:unidade = '' OR upper(coalesce(e.unidade, '')) = upper(:unidade))
                  AND NOT EXISTS (
                      SELECT 1 FROM assinatura_clinica a
                      WHERE upper(a.tipo_documento) = 'EVOLUCAO_MEDICA'
                        AND a.iddocumento = e.idevolucao
                        AND upper(a.status) = 'ASSINADO'
                  )
                ORDER BY e.data DESC, e.horaev DESC
                """, corte, unidade));

        result.addAll(documentos("""
                SELECT 'EVOLUCAO_ENF', 'Evolucao de enfermagem', e.idevolucao, e.nbam, e.prontuario,
                  e.data, e.hora, coalesce(e.enfermeiro, e.medico), coalesce(e.crm, ''), e.unidade, e.relato,
                  coalesce(nullif(upper(e.quem), ''), 'ENFERMEIRO')
                FROM evolucaopacienteenf e
                WHERE e.data >= :corte
                  AND (:unidade = '' OR upper(coalesce(e.unidade, '')) = upper(:unidade))
                  AND (e.quem = 'ENFERMEIRO' OR e.quem = 'TECNICO' OR e.quem IS NULL)
                  AND NOT EXISTS (
                      SELECT 1 FROM assinatura_clinica a
                      WHERE upper(a.tipo_documento) = 'EVOLUCAO_ENF'
                        AND a.iddocumento = e.idevolucao
                        AND upper(a.status) = 'ASSINADO'
                  )
                ORDER BY e.data DESC, e.hora DESC
                """, corte, unidade));

        result.addAll(documentos("""
                SELECT 'EVOLUCAO_MULTIDISCIPLINAR', 'Evolucao multidisciplinar', e.idevolucao, e.nbam, e.prontuario,
                  e.data, e.hora, coalesce(e.enfermeiro, e.medico), coalesce(e.crm, ''), e.unidade, e.relato,
                  coalesce(nullif(upper(e.quem), ''), 'MULTIDISCIPLINAR')
                FROM evolucaopacienteenf e
                WHERE e.data >= :corte
                  AND (:unidade = '' OR upper(coalesce(e.unidade, '')) = upper(:unidade))
                  AND (e.quem != 'ENFERMEIRO' AND e.quem != 'TECNICO' AND e.quem IS NOT NULL)
                  AND NOT EXISTS (
                      SELECT 1 FROM assinatura_clinica a
                      WHERE upper(a.tipo_documento) = 'EVOLUCAO_MULTIDISCIPLINAR'
                        AND a.iddocumento = e.idevolucao
                        AND upper(a.status) = 'ASSINADO'
                  )
                ORDER BY e.data DESC, e.hora DESC
                """, corte, unidade));

        result.addAll(documentos("""
                SELECT 'PRESCRICAO_MEDICA', 'Prescricao medica', base.idprescricao, base.bam, '',
                  base.data_prescricao, base.hora, base.medicoq, base.mediccrm, base.localsolicitante, base.prescricao, 'MEDICO'
                FROM (
                    SELECT p.*,
                           CASE
                               WHEN p.data ~ '^\\d{4}-\\d{2}-\\d{2}' THEN cast(p.data as date)
                               WHEN p.data ~ '^\\d{2}/\\d{2}/\\d{4}' THEN to_date(p.data, 'DD/MM/YYYY')
                               ELSE NULL
                           END AS data_prescricao
                    FROM prescricao p
                ) base
                WHERE (base.inativo = false OR base.inativo IS NULL)
                  AND base.data_prescricao >= :corte
                                    AND (:unidade = '' OR upper(coalesce(base.localsolicitante, '')) = upper(:unidade))
                  AND NOT EXISTS (
                      SELECT 1 FROM assinatura_clinica a
                      WHERE upper(a.tipo_documento) = 'PRESCRICAO_MEDICA'
                        AND a.iddocumento = base.idprescricao
                        AND upper(a.status) = 'ASSINADO'
                  )
                ORDER BY base.data_prescricao DESC, base.hora DESC
                                """, corte, unidade));

        result.addAll(documentos("""
                SELECT 'RECEITUARIO_MEDICO', 'Receituario medico', r.idevolucaoreceituario, r.nbam, '',
                  r.data, r.horaev, r.medicocrm, r.medicocrm, r.unidade, r.relato, 'MEDICO'
                FROM evolucaopacientereceituario r
                WHERE r.data >= :corte
                  AND (:unidade = '' OR upper(coalesce(r.unidade, '')) = upper(:unidade))
                  AND NOT EXISTS (
                      SELECT 1 FROM assinatura_clinica a
                      WHERE upper(a.tipo_documento) = 'RECEITUARIO_MEDICO'
                        AND a.iddocumento = r.idevolucaoreceituario
                        AND upper(a.status) = 'ASSINADO'
                  )
                ORDER BY r.data DESC, r.horaev DESC
                """, corte, unidade));

        result.addAll(documentos("""
                SELECT 'RESUMO_ALTA', 'Resumo de alta', r.idresumoalta,
                  CASE WHEN r.bam ~ '^\\d+$' THEN cast(r.bam as integer) ELSE null END,
                  r.prontuario, r.dataalta, '', r.nome, '', coalesce(b.unidade, ''), r.relato, 'MEDICO'
                FROM resumodealta r
                LEFT JOIN bam b ON b.idbam = CASE WHEN r.bam ~ '^\\d+$' THEN cast(r.bam as integer) ELSE null END
                WHERE r.dataalta >= :corte
                  AND COALESCE(upper(r.status), 'ATIVO') <> 'CANCELADO'
                  AND (:unidade = '' OR upper(coalesce(b.unidade, '')) = upper(:unidade))
                  AND NOT EXISTS (
                      SELECT 1 FROM assinatura_clinica a
                      WHERE upper(a.tipo_documento) = 'RESUMO_ALTA'
                        AND a.iddocumento = r.idresumoalta
                        AND upper(a.status) = 'ASSINADO'
                  )
                ORDER BY r.dataalta DESC, r.idresumoalta DESC
                """, corte, unidade));

        result.addAll(documentos("""
                SELECT 'PARECER_ESPECIALISTA', 'Parecer especialista', p.idparecerespecialista, p.nbam, p.prontuario,
                  coalesce(p.dataresp, p.data), '', coalesce(p.medicorecebedor, p.medicorequisitante), coalesce(p.crmrecebedor, p.crm),
                  p.unidade, concat(coalesce(p.desc1, ''), ' ', coalesce(p.desc2, '')), 'MEDICO'
                FROM parecerespecialista p
                WHERE coalesce(p.dataresp, p.data) >= :corte
                  AND (:unidade = '' OR upper(coalesce(p.unidade, '')) = upper(:unidade))
                  AND NOT EXISTS (
                      SELECT 1 FROM assinatura_clinica a
                      WHERE upper(a.tipo_documento) = 'PARECER_ESPECIALISTA'
                        AND a.iddocumento = p.idparecerespecialista
                        AND upper(a.status) = 'ASSINADO'
                  )
                ORDER BY coalesce(p.dataresp, p.data) DESC, p.idparecerespecialista DESC
                """, corte, unidade));

        return result;
    }

    private List<Map<String, Object>> documentos(String sql, LocalDate corte, String unidade) {
        var query = entityManager.createNativeQuery(sql)
                .setParameter("corte", corte);

        if (sql.contains(":unidade")) {
            query.setParameter("unidade", safe(unidade));
        }

        List<?> rows = query.getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows) {
            Object[] col = (Object[]) row;
            LocalDate data = toLocalDate(col[5]);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tipoDocumento", col[0]);
            item.put("titulo", col[1]);
            item.put("iddocumento", col[2]);
            item.put("idbam", col[3]);
            item.put("prontuario", col[4]);
            item.put("data", data);
            item.put("hora", col[6]);
            item.put("autorNome", safeObj(col[7]));
            item.put("autorRegistro", safeObj(col[8]));
            item.put("unidade", safeObj(col[9]));
            item.put("descricao", resumo(col[10]));
            item.put("categoriaProfissional", categoriaProfissional(col[11]));
            item.put("diasPendente", data == null ? null : ChronoUnit.DAYS.between(data, LocalDate.now()));
            result.add(item);
        }
        return result;
    }

    private boolean pertenceAoUsuario(Map<String, Object> item, AuthenticatedUser user) {
        String autorRegistro = norm(item.get("autorRegistro"));
        String autorNome = norm(item.get("autorNome"));
        return (!safe(user.crm()).isBlank() && autorRegistro.equals(norm(user.crm())))
                || (!safe(user.corem()).isBlank() && autorRegistro.equals(norm(user.corem())))
                || (!safe(user.nome()).isBlank() && autorNome.equals(norm(user.nome())));
    }

    private List<Map<String, Object>> consolidarPorProfissional(List<Map<String, Object>> pendencias) {
        Map<String, Map<String, Object>> grupos = new LinkedHashMap<>();
        for (Map<String, Object> item : pendencias) {
            String chave = safeObj(item.get("autorNome")) + "|" + safeObj(item.get("autorRegistro"));
            Map<String, Object> grupo = grupos.computeIfAbsent(chave, ignored -> {
                Map<String, Object> novo = new LinkedHashMap<>();
                novo.put("profissional", item.get("autorNome"));
                novo.put("registro", item.get("autorRegistro"));
                novo.put("categoriaProfissional", item.get("categoriaProfissional"));
                novo.put("total", 0);
                return novo;
            });
            grupo.put("total", ((Integer) grupo.get("total")) + 1);
        }
        return new ArrayList<>(grupos.values());
    }

    private String categoriaProfissional(Object value) {
        String categoria = norm(value);
        if (categoria.contains("MED")) {
            return "MEDICO";
        }
        if (categoria.contains("TEC")) {
            return "TECNICO";
        }
        if (categoria.contains("ENFER")) {
            return "ENFERMEIRO";
        }
        return safeObj(value).isBlank() ? "NAO INFORMADO" : safeObj(value).toUpperCase(Locale.ROOT);
    }

    private int normalizarDias(int dias) {
        return Math.max(1, Math.min(dias <= 0 ? 30 : dias, 365));
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return value == null ? null : LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private String resumo(Object value) {
        String texto = safeObj(value)
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&ensp;", " ")
            .replace("&emsp;", " ")
            .replaceAll("<[^>]+>", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return texto.substring(0, Math.min(500, texto.length()));
    }

    private String norm(Object value) {
        return safeObj(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeObj(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

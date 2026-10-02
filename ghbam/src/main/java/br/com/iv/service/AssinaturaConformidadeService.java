package br.com.iv.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import br.com.iv.entity.AssinaturaClinica;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class AssinaturaConformidadeService implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String PRESCRICAO_ESTRUTURADA_BAM = "PRESCRICAO_ESTRUTURADA_BAM";

    @Inject
    EntityManager entityManager;

    @Inject
    AssinaturaClinicaService assinaturaClinicaService;

    public Map<String, Object> conformidadePorBam(Integer idbam) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idbam", idbam);

        if (idbam == null || idbam <= 0) {
            result.put("totalDocumentosIndividuais", 0);
            result.put("documentosAssinados", 0);
            result.put("documentosPendentes", 0);
            result.put("pendentes", List.of());
            result.put("prescricaoEstruturada", Map.of("existente", false, "assinada", false));
            return result;
        }

        List<Map<String, Object>> documentos = new ArrayList<>();
        documentos.addAll(documentos("EVOLUCAO_MEDICA", "Evolucao medica", """
                SELECT idevolucao, data, horaev, medico, 'MEDICO'
                FROM evolucaopaciente
                WHERE nbam = :bam AND (inativo = false OR inativo IS NULL)
                ORDER BY data DESC, horaev DESC
                """, idbam));
        documentos.addAll(documentos("EVOLUCAO_ENF", "Evolucao de enfermagem", """
                SELECT idevolucao, data, hora, coalesce(enfermeiro, medico), coalesce(nullif(upper(quem), ''), 'ENFERMEIRO')
                FROM evolucaopacienteenf
                WHERE nbam = :bam AND (quem = 'ENFERMEIRO' OR quem = 'TECNICO' OR quem IS NULL)
                ORDER BY data DESC, hora DESC
                """, idbam));
        documentos.addAll(documentos("EVOLUCAO_MULTIDISCIPLINAR", "Evolucao multidisciplinar", """
                SELECT idevolucao, data, hora, coalesce(enfermeiro, medico), coalesce(nullif(upper(quem), ''), 'MULTIDISCIPLINAR')
                FROM evolucaopacienteenf
                WHERE nbam = :bam AND (quem != 'ENFERMEIRO' AND quem != 'TECNICO' AND quem IS NOT NULL)
                ORDER BY data DESC, hora DESC
                """, idbam));
        documentos.addAll(documentosJpql("ANAMNESE_INTERNACAO", "Anamnese de internacao", """
                SELECT a.idanamneseinternacao, a.data, ''
                FROM AnamneseInternacao a
                WHERE a.nbam = :bam
                ORDER BY a.data DESC, a.idanamneseinternacao DESC
                """, "bam", idbam));
        documentos.addAll(documentosJpql("RESUMO_ALTA", "Resumo de alta", """
                SELECT r.idResumoalta, r.dataalta, ''
                FROM ResumoDeAlta r
                WHERE r.bam = :bam
                ORDER BY r.dataalta DESC, r.idResumoalta DESC
                """, "bam", String.valueOf(idbam)));
        documentos.addAll(documentosJpql("LAUDO_AUTORIZACAO", "Laudo de autorizacao", """
                SELECT l.idlaudo, l.datasolicitacao, ''
                FROM LaudoAutorizacao l
                WHERE l.bam = :bam
                ORDER BY l.datasolicitacao DESC, l.idlaudo DESC
                """, "bam", idbam));
        documentos.addAll(documentosJpql("FICHA_ANESTESICA", "Ficha anestesica", """
                SELECT f.idlistaanestesica, f.data, ''
                FROM listaanestesica f
                WHERE f.bam = :bam
                ORDER BY f.idlistaanestesica DESC
                """, "bam", idbam));

        List<AssinaturaClinica> assinaturas = assinaturaClinicaService.listarPorBam(idbam);
        Set<String> assinadas = assinaturas.stream()
                .filter(a -> "ASSINADO".equalsIgnoreCase(a.getStatus()))
                .map(this::chave)
                .collect(Collectors.toSet());

        List<Map<String, Object>> pendentes = documentos.stream()
                .filter(documento -> !assinadas.contains(chaveDocumento(documento)))
                .toList();

        boolean prescricaoExiste = existePrescricaoEstruturada(idbam);
        boolean prescricaoAssinada = assinaturas.stream()
                .anyMatch(a -> PRESCRICAO_ESTRUTURADA_BAM.equalsIgnoreCase(a.getTipoDocumento())
                        && "ASSINADO".equalsIgnoreCase(a.getStatus()));
        List<Map<String, Object>> pendencias = new ArrayList<>(pendentes);
        if (prescricaoExiste && !prescricaoAssinada) {
            Map<String, Object> prescricaoPendente = new LinkedHashMap<>();
            prescricaoPendente.put("tipo", PRESCRICAO_ESTRUTURADA_BAM);
            prescricaoPendente.put("titulo", "Prescricao estruturada");
            prescricaoPendente.put("id", idbam);
            prescricaoPendente.put("escopo", "BAM");
            pendencias.add(prescricaoPendente);
        }

        result.put("totalDocumentosIndividuais", documentos.size());
        result.put("documentosAssinados", documentos.size() - pendentes.size());
        result.put("documentosPendentes", pendentes.size());
        result.put("pendentes", pendentes);
        result.put("prescricaoEstruturada", Map.of("existente", prescricaoExiste, "assinada", prescricaoAssinada));
        result.put("pendencias", pendencias);
        result.put("totalDocumentosClinicos", documentos.size() + (prescricaoExiste ? 1 : 0));
        result.put("documentosClinicosAssinados", documentos.size() - pendentes.size() + (prescricaoAssinada ? 1 : 0));
        result.put("documentosClinicosPendentes", pendencias.size());
        result.put("completo", pendencias.isEmpty());
        return result;
    }

    private List<Map<String, Object>> documentos(String tipo, String titulo, String sql, Integer idbam) {
        List<?> rows = entityManager.createNativeQuery(sql)
                .setParameter("bam", idbam)
                .getResultList();

        return rowsToDocumentos(tipo, titulo, rows);
    }

    private List<Map<String, Object>> documentosJpql(String tipo, String titulo, String jpql, String parametro, Object valor) {
        List<?> rows = entityManager.createQuery(jpql)
                .setParameter(parametro, valor)
                .getResultList();

        return rowsToDocumentos(tipo, titulo, rows);
    }

    private List<Map<String, Object>> rowsToDocumentos(String tipo, String titulo, List<?> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("tipo", tipo);
            doc.put("titulo", titulo);
            doc.put("id", cols.length > 0 ? cols[0] : null);
            doc.put("data", cols.length > 1 ? cols[1] : null);
            doc.put("hora", cols.length > 2 ? cols[2] : null);
            doc.put("autorNome", cols.length > 3 ? cols[3] : null);
            doc.put("categoriaProfissional", cols.length > 4 ? cols[4] : null);
            result.add(doc);
        }
        return result;
    }

    private boolean existePrescricaoEstruturada(Integer idbam) {
        Object count = entityManager.createNativeQuery(
                "SELECT count(*) FROM prescricao_item_estruturado WHERE idbam = :bam")
                .setParameter("bam", idbam)
                .getSingleResult();
        return count instanceof Number number && number.longValue() > 0;
    }

    private String chave(AssinaturaClinica assinatura) {
        if (assinatura.getIddocumento() != null) {
            return assinatura.getTipoDocumento() + ":" + assinatura.getIddocumento();
        }
        return assinatura.getTipoDocumento() + ":BAM:" + assinatura.getIdbam();
    }

    private String chaveDocumento(Map<String, Object> documento) {
        return documento.get("tipo") + ":" + documento.get("id");
    }
}

package br.com.iv.service;

import java.util.List;
import java.util.Map;

import br.com.iv.entity.Bam;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Orquestra a exportação laboratorial local sem expor uma rota por conta própria.
 * O controller futuro deve delegar integralmente a este serviço.
 */
@ApplicationScoped
public class FhirResultadoLaboratorioExportService {

    @Inject
    FhirResultadoLaboratorioAuthorizationService authorizationService;

    @Inject
    FhirResultadoLaboratorioAuditService auditService;

    @Inject
    FhirResultadoLaboratorioMapperService mapperService;

    @Inject
    FhirR4PayloadValidator payloadValidator;

    public Map<String, Object> exportar(Integer idbam, AuthenticatedUser usuario) {
        Bam bam = bamAutorizado(idbam, usuario);
        Map<String, Object> bundle = mapperService.montarBundle(idbam);
        auditService.registrarConsulta(usuario, bam, "SUCESSO");
        return bundle;
    }

    /**
     * Retorna somente o checklist de homologacao, sem devolver dados clinicos.
     * Avalia so o que entra na fila RNDS: laudo antigo (so PDF, descricao livre como
     * "** Resultado liberado **") nao vai para a RNDS e gerava pendencias falsas.
     */
    public Map<String, Object> avaliarHomologacao(Integer idbam, AuthenticatedUser usuario) {
        Bam bam = bamAutorizado(idbam, usuario);
        Map<String, Object> bundle = mapperService.montarBundlePreparadoRnds(idbam);
        auditService.registrarConsulta(usuario, bam, "SUCESSO");
        var pendencias = bundle == null
                ? List.of("Nenhum resultado preparado para RNDS")
                : payloadValidator.avaliarResultadoLaboratorial(bundle);
        return Map.of(
                "pronto", pendencias.isEmpty(),
                "pendencias", pendencias,
                "tipo", "RESULTADO_EXAME_LABORATORIAL");
    }

    private Bam bamAutorizado(Integer idbam, AuthenticatedUser usuario) {
        Bam bam = idbam == null || idbam <= 0 ? null : Bam.<Bam>findById(idbam);
        if (bam == null) {
            auditService.registrarConsulta(usuario, null, "NAO_ENCONTRADO");
            throw new FhirResultadoLaboratorioAccessException("BAM nao encontrado.", true);
        }
        if (!authorizationService.podeExportar(usuario, bam)) {
            auditService.registrarConsulta(usuario, bam, "NEGADO");
            throw new FhirResultadoLaboratorioAccessException("Usuario sem autorizacao para este BAM.", false);
        }
        return bam;
    }

    public static class FhirResultadoLaboratorioAccessException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final boolean notFound;

        public FhirResultadoLaboratorioAccessException(String message, boolean notFound) {
            super(message);
            this.notFound = notFound;
        }

        public boolean isNotFound() {
            return notFound;
        }
    }
}

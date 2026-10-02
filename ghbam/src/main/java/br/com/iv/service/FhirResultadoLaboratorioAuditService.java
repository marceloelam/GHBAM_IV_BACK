package br.com.iv.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import br.com.iv.dto.LogDto;
import br.com.iv.entity.Bam;
import br.com.iv.entity.IntegracaoSusAuditoria;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Registra a tentativa de acesso ao Bundle laboratorial na trilha de auditoria
 * SUS já existente, sem criar uma segunda tabela de auditoria.
 */
@ApplicationScoped
public class FhirResultadoLaboratorioAuditService {

    @Inject
    LogService logService;

    @Transactional
    public void registrarConsulta(AuthenticatedUser usuario, Bam bam, String status) {
        String unidade = normalizar(bam == null ? null : bam.getUnidade());
        String acao = "FHIR_LABORATORIO_CONSULTA";
        String resultado = normalizar(status);
        String detalhes = "BAM=" + (bam == null ? "-" : bam.getIdbam())
                + "; resultado=" + (resultado.isBlank() ? "NAO_INFORMADO" : resultado);

        IntegracaoSusAuditoria auditoria = new IntegracaoSusAuditoria();
        auditoria.setUnidade(limit(unidade, 25));
        auditoria.setAmbiente("NAO_APLICAVEL");
        auditoria.setAcao(acao);
        auditoria.setStatus(limit(resultado.isBlank() ? "REGISTRADO" : resultado, 20));
        auditoria.setUsuario(limit(usuario == null ? "" : usuario.nome(), 80));
        auditoria.setMatricula(limit(usuario == null ? "" : usuario.matricula(), 80));
        auditoria.setDetalhes(limit(detalhes, 500));
        auditoria.setCriadoEm(LocalDateTime.now());
        auditoria.persist();

        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        log.setUnidade(unidade);
        log.setEvento(acao + " " + auditoria.getStatus() + " - " + detalhes);
        logService.salvarLogAsync(log);
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase();
    }

    private String limit(String valor, int maximo) {
        if (valor == null) return "";
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}

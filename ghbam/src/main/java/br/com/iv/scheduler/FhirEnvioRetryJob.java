package br.com.iv.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.jboss.logging.Logger;

import br.com.iv.entity.FhirEnvioFila;
import br.com.iv.service.FhirDispatchService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

// Rede de seguranca do envio FHIR: cobre o "primeiro tiro" perdido (crash
// entre o commit da assinatura e o evento pos-commit) e os retries com
// backoff das falhas de rede registradas por FhirDispatchService.
@ApplicationScoped
public class FhirEnvioRetryJob {

    private static final Logger LOG = Logger.getLogger(FhirEnvioRetryJob.class);

    @Inject
    FhirDispatchService dispatchService;

    @Scheduled(every = "{ghbam.fhir.dispatch.retry.cron:1m}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reprocessarPendentesAgendado() {
        // Em cada municipio (sem requisicao o banco seria sempre o padrao).
        br.com.iv.plataforma.municipio.MunicipioRoteamento.emCadaMunicipio(FhirEnvioRetryJob.class, "reprocessarPendentes", b -> b.reprocessarPendentes());
    }

    @Transactional
    void reprocessarPendentes() {
        List<FhirEnvioFila> elegiveis = FhirEnvioFila.list(
                "status in ('PENDENTE','ERRO') and (proximaTentativaEm is null or proximaTentativaEm <= ?1)",
                LocalDateTime.now());
        for (FhirEnvioFila fila : elegiveis) {
            try {
                dispatchService.processarEnvio(fila.getIdfhirenviofila());
            } catch (RuntimeException e) {
                LOG.warnf("[FHIR-RETRY] falha ao reprocessar idFila=%s: %s", fila.getIdfhirenviofila(), e.getMessage());
            }
        }
    }
}

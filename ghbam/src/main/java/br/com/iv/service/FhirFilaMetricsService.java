package br.com.iv.service;

import br.com.iv.entity.FhirEnvioFila;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

// Contador isolado num bean proprio de proposito: o Gauge do Micrometer chama
// este metodo fora de qualquer transacao/requisicao HTTP normal (no momento do
// scrape de /q/metrics), entao o @Transactional so tem efeito por ser uma
// invocacao via proxy CDI de outro bean (FhirDispatchService) - self-invocation
// dentro da mesma classe ignoraria o interceptor.
@ApplicationScoped
public class FhirFilaMetricsService {

    @Transactional
    public long contarPorStatus(String status) {
        return FhirEnvioFila.count("status = ?1", status);
    }
}

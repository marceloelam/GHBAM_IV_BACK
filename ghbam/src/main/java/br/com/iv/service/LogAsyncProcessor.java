package br.com.iv.service;

import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import br.com.iv.dto.LogDto;

@ApplicationScoped
public class LogAsyncProcessor {

    @Inject
    LogPersistencia logPersistencia;

    public void persistLog(LogDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("LogDto não pode ser nulo");
        }
        int attempts = 0;
        int maxAttempts = 3;
        long backoffMillis = 200L;

        while (true) {
            try {
                logPersistencia.persistir(dto);
                return;
            } catch (Exception e) {
                attempts++;
                if (attempts >= maxAttempts) {
                    throw new RuntimeException("persistLog failed after retries", e);
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("persistLog interrupted during backoff", ie);
                }
                backoffMillis *= 2;
            }
        }
    }

}

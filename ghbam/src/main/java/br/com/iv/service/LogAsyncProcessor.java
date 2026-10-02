package br.com.iv.service;

import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import br.com.iv.dto.LogDto;
import br.com.iv.entity.Log;

@ApplicationScoped
public class LogAsyncProcessor {

    @Transactional(TxType.REQUIRES_NEW)
    public void persistLog(LogDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("LogDto não pode ser nulo");
        }
        int attempts = 0;
        int maxAttempts = 3;
        long backoffMillis = 200L;

        while (true) {
            try {
                Log log = new Log();
                log.setData(dto.getData());
                log.setHora(dto.getHora());
                log.setEvento(dto.getEvento());
                log.setDados(dto.getDados());
                log.setUnidade(dto.getUnidade());
                log.persist();
                return;
            } catch (Exception e) {
                attempts++;
                if (attempts >= maxAttempts) {
                    throw new RuntimeException("persistLog failed after retries: " + e.getMessage(), e);
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

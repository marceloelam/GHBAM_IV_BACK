package br.com.iv.service;

import br.com.iv.dto.LogDto;
import br.com.iv.entity.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

@ApplicationScoped
public class LogPersistencia {

    @Transactional(TxType.REQUIRES_NEW)
    public void persistir(LogDto dto) {
        Log log = new Log();
        log.setData(dto.getData());
        log.setHora(dto.getHora());
        log.setEvento(dto.getEvento());
        log.setDados(dto.getDados());
        log.setUnidade(dto.getUnidade());
        log.persist();
    }
}

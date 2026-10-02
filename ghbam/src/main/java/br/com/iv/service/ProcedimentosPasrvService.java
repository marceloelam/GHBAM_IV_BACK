package br.com.iv.service;

import java.io.Serializable;

import br.com.iv.entity.ProcedimentosPasrv;
import br.com.iv.repository.ProcedimentosPasrvRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProcedimentosPasrvService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    ProcedimentosPasrvRepository procedimentosPasrvRepository;

    public Object procedimentosPasrvProcesso(String procedimento) {
        // tenta primeiro no PASRV
        ProcedimentosPasrv pasrv = procedimentosPasrvRepository.buscarNoPasrv(procedimento);
        if (pasrv != null) {
            return pasrv;
        }

        // se não achar, cai pro RX
        return procedimentosPasrvRepository.buscarNoRxProcedimentos(procedimento);
    }
}


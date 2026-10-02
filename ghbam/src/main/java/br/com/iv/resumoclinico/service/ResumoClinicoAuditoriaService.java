package br.com.iv.resumoclinico.service;

import br.com.iv.resumoclinico.entity.ResumoClinicoAuditoria;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ResumoClinicoAuditoriaService {
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void registrar(ResumoClinicoAuditoria auditoria) {
        auditoria.persist();
    }
}

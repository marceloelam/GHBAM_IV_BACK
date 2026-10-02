package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.ClassificacaoGhbamAuditoria;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClassificacaoGhbamAuditoriaRepository implements PanacheRepositoryBase<ClassificacaoGhbamAuditoria, Long> {
    public List<ClassificacaoGhbamAuditoria> porItem(String itemUuid) {
        return find("itemUuid = ?1 order by ocorridoEm desc, id desc", itemUuid).list();
    }
}

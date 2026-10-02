package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.ClassificacaoGhbamItem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClassificacaoGhbamItemRepository implements PanacheRepositoryBase<ClassificacaoGhbamItem, Long> {
    public ClassificacaoGhbamItem porUuid(String uuid) {
        return uuid == null || uuid.isBlank() ? null : find("uuid", uuid.trim()).firstResult();
    }

    public ClassificacaoGhbamItem porCodigoVersao(String codigo, String versao) {
        return find("codigo = ?1 and versao = ?2", codigo, versao).firstResult();
    }

    public boolean existeCodigo(String codigo) {
        return count("codigo", codigo) > 0;
    }

    public List<String> codigosComPrefixo(String prefixo) {
        return getEntityManager()
                .createQuery("select distinct i.codigo from ClassificacaoGhbamItem i where i.codigo like :prefixo", String.class)
                .setParameter("prefixo", prefixo + "%")
                .getResultList();
    }

    public List<ClassificacaoGhbamItem> aprovadosPorCodigo(String codigo) {
        return find("codigo = ?1 and status = 'APROVADO'", codigo).list();
    }

    public List<ClassificacaoGhbamItem> listar(String status) {
        if (status == null || status.isBlank()) return find("order by tipo, codigo").list();
        return find("status = ?1 order by tipo, codigo", status.trim().toUpperCase()).list();
    }
}

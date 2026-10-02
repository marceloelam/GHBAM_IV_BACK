package br.com.iv.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;

import br.com.iv.entity.ProcedimentosPasrv;

@ApplicationScoped
public class ProcedimentosPasrvRepository {

    @Inject
    EntityManager entityManager;

    public ProcedimentosPasrv buscarNoPasrv(String procedimento) {
        List<ProcedimentosPasrv> result = entityManager.createQuery(
                "SELECT p FROM ProcedimentosPasrv p WHERE p.pasrv_pa = :codigo",
                ProcedimentosPasrv.class)
            .setParameter("codigo", procedimento)
            .setMaxResults(1)
            .getResultList();

        return result.isEmpty() ? null : result.get(0);
    }

    public Object buscarNoRxProcedimentos(String procedimento) {
        @SuppressWarnings("unchecked")
		List<Object[]> result = entityManager.createNativeQuery(
                "SELECT * FROM rxprocedimentos WHERE codprocedimento = :codigo")
            .setParameter("codigo", procedimento)
            .setMaxResults(1)
            .getResultList();

        return result.isEmpty() ? null : result.get(0);
    }
}

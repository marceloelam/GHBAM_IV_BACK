package br.com.iv.repository;

import java.util.Collection;
import java.util.List;

import br.com.iv.entity.TissContaItem;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TissContaItemRepository implements PanacheRepository<TissContaItem> {
    private static final int ORIGEM_IDS_CHUNK_SIZE = 10000;

    public List<TissContaItem> listarPorConta(int idtissconta) {
        return find("idtissconta = ?1 order by idtisscontaitem", idtissconta).list();
    }
    public boolean existeOrigem(String origem, String origemId) {
        return count("origem = ?1 and origemId = ?2", origem, origemId) > 0;
    }

    public List<String> listarOrigemIds(String origem, Collection<String> origemIds) {
        if (origemIds == null || origemIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = List.copyOf(origemIds);
        List<String> encontrados = new java.util.ArrayList<>();
        for (int inicio = 0; inicio < ids.size(); inicio += ORIGEM_IDS_CHUNK_SIZE) {
            int fim = Math.min(inicio + ORIGEM_IDS_CHUNK_SIZE, ids.size());
            encontrados.addAll(getEntityManager()
                    .createQuery("select item.origemId from TissContaItem item where item.origem = :origem and item.origemId in :origemIds", String.class)
                    .setParameter("origem", origem)
                    .setParameter("origemIds", ids.subList(inicio, fim))
                    .getResultList());
        }
        return encontrados;
    }
}

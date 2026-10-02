package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.ListaAnestesica;

@ApplicationScoped
public class ListaAnestesicaRepository implements PanacheRepository<ListaAnestesica> {
    @Inject
    EntityManager entityManager;
    
    public List<ListaAnestesica> listarPorBam(int bam) {
        return find("bam = ?1 ORDER BY idlistaanestesica DESC", bam).list();
    }
    
    public List<ListaAnestesica> listarPorBamEData(int bam, String data) {
        return find("bam = ?1 AND data = ?2 ORDER BY idlistaanestesica DESC", bam, data).list();
    }
}

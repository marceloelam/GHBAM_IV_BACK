package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.ReceitaMedicamentos;

@ApplicationScoped
public class ReceitaMedicamentosRepository implements PanacheRepository<ReceitaMedicamentos> {
	@Inject
	EntityManager entityManager;
	
	public List<ReceitaMedicamentos> receitaMedica(int query) {
		return find("nbam = ?1", +query).list();
	}
	
	 public void atualizarObservacaoReceita(int bam, String obs) {
	        String sql = "UPDATE receitamedicamentos SET observacao = ?1 WHERE nbam = ?2";
	        entityManager.createNativeQuery(sql)
	                     .setParameter(1, obs)
	                     .setParameter(2, bam)
	                     .executeUpdate();
	    }

}

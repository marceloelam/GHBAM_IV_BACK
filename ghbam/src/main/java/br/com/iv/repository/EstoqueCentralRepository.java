package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.EstoqueCentral;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;


@ApplicationScoped
public class EstoqueCentralRepository implements PanacheRepository<EstoqueCentral> {
	
	@SuppressWarnings("unchecked")
	@Transactional
	public List<EstoqueCentral> estoquecentral(String material, String unidade) {
		
		String sql = "SELECT * from estoquecentral where material like :material and unidade = :unidade order by material " ;

		return getEntityManager().createNativeQuery(sql, EstoqueCentral.class)
				.setParameter("material", material + "%")
				.setParameter("unidade", unidade)
				.getResultList();
	}

	@SuppressWarnings("unchecked")
	public List<EstoqueCentral> estoquecentralLote(String lote, String unidade) {
		String sql = "SELECT * from estoquecentral where lote = :lote and unidade = :unidade order by material " ;
		return getEntityManager().createNativeQuery(sql, EstoqueCentral.class)
				.setParameter("lote", lote)
				.setParameter("unidade", unidade)
				.getResultList();
	}


}

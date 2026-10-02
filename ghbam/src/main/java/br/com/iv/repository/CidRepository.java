package br.com.iv.repository;

import java.util.List;

import jakarta.inject.Inject;

import br.com.iv.entity.Cid1;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CidRepository implements PanacheRepository<Cid1> {
	@Inject
	EntityManager entityManager;

	public List<Cid1> filtarPorCid(String query) {
		return find("upper(titulo) like ?1", "%" + query.toUpperCase() + "%").list();
	}

	public List<Cid1> filtarPorCidCodigo(String query) {
		return find("upper(codigo) like ?1", "%" + query.toUpperCase() + "%").list();
	}

	@SuppressWarnings("unchecked")
	@Transactional
	public List<Cid1> locaisDeCid(String insumo) {
//		String sql = "SELECT distinct on (cd_descr) cd_descr, * FROM   s_cid   where upper(cd_descr) like upper('%"
//				+ insumo + "%')  order by cd_descr limit 20";
		String sql = "SELECT DISTINCT ON (cd_descr) cd_descr AS unique_cd_descr, s_cid.* FROM s_cid	WHERE upper(cd_descr) LIKE upper('%" + insumo +"%')		ORDER BY cd_descr 	LIMIT 20";

		return getEntityManager().createNativeQuery(sql, Cid1.class).getResultList();
	}

}

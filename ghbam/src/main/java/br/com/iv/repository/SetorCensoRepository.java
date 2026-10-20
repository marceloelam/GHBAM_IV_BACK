package br.com.iv.repository;

import java.util.List;

import jakarta.inject.Inject;

import br.com.iv.entity.SetorCenso;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SetorCensoRepository implements PanacheRepository<SetorCenso> {
	@Inject
	EntityManager entityManager;

	public List<SetorCenso> filtarPorSetor(String query, String unidade) {
		return find("upper(setor) like ?1 and unidade = ?2", "%" + query.toUpperCase() + "%", unidade).list();
	}

	public List<SetorCenso> filtarPorCodigo(String query, String unidade) {
		return find("upper(codigo) like ?1 and unidade = ?2", "%" + query.toUpperCase() + "%", unidade).list();
	}

	public List<SetorCenso> filtarPorUnidadeSigla(String query, String opc) {
		if (opc.equals("tudo"))
			return find("unidade like ?1 order by setor", query).list();
		else
			return find("unidade like ?1 and excluido = false order by setor", query).list();
	}
	
	public List<SetorCenso> filtarPorUnidadeSigla2(String query) {
			return find("unidade like ?1 order by setor", query).list();
		
	}

	public List<SetorCenso> filtarPorUnidadeSiglaObs(String query, String opc) {
		if (opc.equals("tudo"))
			return find("unidade like ?1 and (setor = 'OBSERVACAO' OR setor = 'OBSERVAÇÃO') order by setor", query).list();
		else
			return find("unidade like ?1  and (setor = 'OBSERVACAO' OR setor = 'OBSERVAÇÃO') and excluido = false order by setor", query).list();
	}

	public List<String> listarUnidadesDistintosDoSetor(String unidade) {
		return entityManager.createQuery(
				"SELECT DISTINCT s.unidade FROM SetorCenso s where unidade = '" + unidade + "' ORDER BY s.unidade",
				String.class).getResultList();
	}

	public List<String> listarSetoresDistintosDoSetor(String unidade) {
		return entityManager.createQuery(
				"SELECT DISTINCT s.setor FROM SetorCenso s where s.unidade = '" + unidade + "' ORDER BY s.setor",
				String.class).getResultList();
	}

	// Atualizar senha do servidor
	public void alterarExclusao(int id, boolean excluido) {
		String sql = "UPDATE setorcenso set excluido = :excluir where idsetorcenso = :id";
		entityManager.createNativeQuery(sql).setParameter("id", id).setParameter("excluir", excluido).executeUpdate();
	}

	@Transactional
	public long contagem(String unidade) {
		String sql = "";
		sql = "SELECT COUNT(*) FROM setorcenso WHERE unidade = :unidade and excluido = false";
		return ((Number) entityManager.createNativeQuery(sql).setParameter("unidade", unidade).getSingleResult())
				.longValue();
	}

}

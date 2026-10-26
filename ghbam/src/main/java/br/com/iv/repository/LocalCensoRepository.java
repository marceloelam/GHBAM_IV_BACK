package br.com.iv.repository;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import br.com.iv.dto.LocalCensoDto;
import br.com.iv.entity.LocalCenso;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class LocalCensoRepository implements PanacheRepository<LocalCenso> {
	@Inject
	EntityManager entityManager;

	public List<LocalCenso> filtarPorSetor(String query, String unidade) {
		return find("upper(local) like ?1 and unidade = ?2", "%" + query.toUpperCase() + "%", unidade).list();
	}

	public List<LocalCenso> filtarPorCodigo(String query, String unidade) {
		return find("upper(codigo) like ?1 and unidade = ?2", "%" + query.toUpperCase() + "%", unidade).list();
	}

	/*
	 * public List<LocalCenso> filtarPorUnidadeSigla(String query) { return
	 * find("unidade like ?1  order by local", query).list(); }
	 */

	public List<LocalCenso> filtarPorUnidadeSigla(String query) {
		return find(
				"SELECT l FROM LocalCenso l INNER JOIN l.setorCenso s WHERE l.unidade LIKE ?1 ORDER BY s.setor, l.local",
				query).list();
	}

	public List<String> listarUnidadesDistintosDoSetor(String unidade) {
		return entityManager.createQuery(
				"SELECT DISTINCT s.unidade FROM LocalCenso s where unidade = :unidade ORDER BY s.unidade",
				String.class).setParameter("unidade", unidade).getResultList();
	}

	public List<LocalCensoDto> listarUnidadesDistintosDoLocalPorSetor(String unidade, int setor) {
	    List<Object[]> results = entityManager.createQuery(
	        "SELECT DISTINCT f.idsetorlocalcenso, f.local, f.unidade, f.disponivel, f.excluido " +
	        "FROM LocalCenso f " +
	        "JOIN f.setorCenso s " +
	        "WHERE s.idsetorcenso = :setor " +
	        "AND f.disponivel = TRUE " +
	        "AND f.excluido = FALSE " +
	        "AND s.excluido = FALSE " +
	        "AND f.idsetorlocalcenso NOT IN (" +
	        "  SELECT n.local.idsetorlocalcenso FROM NovoInternado n WHERE n.alta != TRUE AND n.unidade = :unidade" +
	        ") " +
	        "ORDER BY f.local ASC", Object[].class)
	        .setParameter("setor", setor)
	        .setParameter("unidade", unidade)
	        .getResultList();

	    List<LocalCensoDto> locais = new ArrayList<>();
	    for (Object[] result : results) {
	        int idsetorlocalcenso = ((Number) result[0]).intValue();  // Conversão segura para Integer
	        String local = (String) result[1];
	        String unidades = (String) result[2];
	        boolean disponivel = (Boolean) result[3];
	        boolean excluido = (Boolean) result[4];

	        locais.add(new LocalCensoDto(idsetorlocalcenso, local, unidades, disponivel, excluido));
	    }

	    return locais;
	}

	// Agregado por unidade inteira (todos os setores), sem exigir um setor
	// especifico. Usado por telas que mostram "leitos desocupados da unidade"
	// sem selecao de setor (NirRestaurarInternacao, CcihResumo). Mesma logica
	// da consulta por setor, apenas sem o predicado s.idsetorcenso = :setor.
	public List<LocalCensoDto> listarUnidadesDistintosDoLocalPorUnidade(String unidade) {
	    List<Object[]> results = entityManager.createQuery(
	        "SELECT DISTINCT f.idsetorlocalcenso, f.local, f.unidade, f.disponivel, f.excluido " +
	        "FROM LocalCenso f " +
	        "JOIN f.setorCenso s " +
	        "WHERE f.disponivel = TRUE " +
	        "AND f.excluido = FALSE " +
	        "AND s.excluido = FALSE " +
	        "AND f.unidade = :unidade " +
	        "AND f.idsetorlocalcenso NOT IN (" +
	        "  SELECT n.local.idsetorlocalcenso FROM NovoInternado n WHERE n.alta != TRUE AND n.unidade = :unidade" +
	        ") " +
	        "ORDER BY f.local ASC", Object[].class)
	        .setParameter("unidade", unidade)
	        .getResultList();

	    List<LocalCensoDto> locais = new ArrayList<>();
	    for (Object[] result : results) {
	        int idsetorlocalcenso = ((Number) result[0]).intValue();
	        String local = (String) result[1];
	        String unidades = (String) result[2];
	        boolean disponivel = (Boolean) result[3];
	        boolean excluido = (Boolean) result[4];

	        locais.add(new LocalCensoDto(idsetorlocalcenso, local, unidades, disponivel, excluido));
	    }

	    return locais;
	}
	public List<LocalCensoDto> listarUnidadesDistintosDoLocalPorSetorObservacao(String unidade, int setor) {
	    List<Object[]> results = entityManager.createQuery(
	        "SELECT DISTINCT f.idsetorlocalcenso, f.local, f.unidade, f.disponivel, f.excluido " +
	        "FROM LocalCenso f " +
	        "JOIN f.setorCenso s " +
	        "WHERE s.idsetorcenso = :setor " +
	        "AND f.disponivel = TRUE " +
	        "AND f.excluido = FALSE " +
	        "AND s.excluido = FALSE " +
	        "AND (s.setor = 'OBSERVACAO' OR s.setor = 'OBSERVAÇÃO')" +
	        "AND f.idsetorlocalcenso NOT IN (" +
	        "  SELECT n.local.idsetorlocalcenso FROM NovoInternado n WHERE n.alta != TRUE AND n.unidade = :unidade" +
	        ") " +
	        "ORDER BY f.local ASC", Object[].class)
	        .setParameter("setor", setor)
	        .setParameter("unidade", unidade)
	        .getResultList();

	    List<LocalCensoDto> locais = new ArrayList<>();
	    for (Object[] result : results) {
	        int idsetorlocalcenso = ((Number) result[0]).intValue();  // Conversão segura para Integer
	        String local = (String) result[1];
	        String unidades = (String) result[2];
	        boolean disponivel = (Boolean) result[3];
	        boolean excluido = (Boolean) result[4];

	        locais.add(new LocalCensoDto(idsetorlocalcenso, local, unidades, disponivel, excluido));
	    }

	    return locais;
	}

	// Atualizar senha do servidor
	public void alterarExclusao(int id, boolean excluido) {
		String sql = "UPDATE LocalCenso set excluido = :excluir where idlocalcenso = :id";
		entityManager.createNativeQuery(sql).setParameter("id", id).setParameter("excluir", excluido).executeUpdate();
	}

	@Transactional
	public long contagem(String unidade) {
		String sql = "";

		sql = "SELECT COUNT(*) FROM localcenso WHERE unidade = :unidade and excluido = false";
		return ((Number) entityManager.createNativeQuery(sql).setParameter("unidade", unidade).getSingleResult())
				.longValue();
	}

}

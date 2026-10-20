package br.com.iv.repository;

import java.util.List;
import java.util.Locale;

import br.com.iv.entity.ChamadaPaciente;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ChamadaRepository implements PanacheRepository<ChamadaPaciente> {
	@Inject
	EntityManager entityManager;

	boolean pp1 = false;
	boolean pp2 = false;
	boolean pp3 = false;
	boolean pp4 = false;
	boolean pp5 = false;
	boolean pp6 = false;
	boolean pp7 = false;
	boolean pp8 = false;
	boolean pp9 = false;
	boolean pp10 = false;

	@SuppressWarnings("unchecked")
	public List<ChamadaPaciente> filtarseisultimos(String unidade, String tv) {
		
		System.out.println("Unidade: " + unidade);
		System.out.println("TV: " + tv);
		
		//atualizarChamada(unidade, tv);

		String filtroTv = "";
		if (tv.matches("\\d+")) { // se for número
			filtroTv = " AND p" + tv + " = true";
		}

		String sql = "SELECT * FROM chamadapaciente " + "WHERE upper(unidade) LIKE :unidade " + filtroTv
				+ " ORDER BY CAST(DATA AS date) DESC, CAST(HORA AS time) DESC";

		List<ChamadaPaciente> resultado = entityManager.createNativeQuery(sql, ChamadaPaciente.class)
	            .setParameter("unidade", "%" + escaparLike(unidade.toUpperCase()) + "%")
	            .setMaxResults(8)
	            .getResultList();

	    // Atualiza os dados depois da consulta
	  //  atualizarChamada(unidade, tv);

	    return resultado;
	}

	public void atualizarChamada(String unidade, String tv) {
		
		String sql = "";

		if (tv.equals("1")) {
			sql = "upper(unidade) like upper(:unidade) and p1 = true where p||" + tv + "=false ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc";
		}

		if (tv.equals("2")) {
			sql = "upper(unidade) like upper(:unidade) and p2 = true  where p||" + tv + "=false  ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc";
		}

		if (tv.equals("3")) {
			sql = "upper(unidade) like upper(:unidade) and p3 = true  where p||" + tv + "=false  ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc";
		}
		if (tv.equals("4")) {
			sql = "upper(unidade) like upper(:unidade) and p4 = true  where p||" + tv + "=false  ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc";
		}

		if (tv.equals("5")) {
			sql = "upper(unidade) like upper(:unidade) and p5 = true  where p||" + tv + "=false  ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc";
		}

		if (tv.equals("6")) {
			sql = "upper(unidade) like upper(:unidade) and p6 = true  where p||" + tv + "=false ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc";
		}

		if (tv.equals("7")) {
			sql = "upper(unidade) like upper(:unidade) and p7 = true  where p||" + tv + "=false  ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc";
		}

		if (tv.equals("8")) {
			sql = "upper(unidade) like upper(:unidade) and p8 = true  where p||" + tv + "=false  ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc";
		}

		if (tv.equals("9")) {
			sql = "upper(unidade) like upper(:unidade) and p9 = true  where p||" + tv + "=false  ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc";
		}

		if (tv.equals("10")) {
			sql = "upper(unidade) like upper(:unidade) and p10 = true  where p||" + tv + "=false  ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc";
		}

		
		
		entityManager.createNativeQuery(sql).setParameter("unidade", unidade).executeUpdate();

	}

	public List<ChamadaPaciente> filtarultimoDireita(String unidade, String tv) {
		String filtroTv = "";
		if (tv.matches("\\d+")) {
			filtroTv = " AND p" + tv + " = true";
		}
		String sql = "SELECT * FROM chamadapaciente WHERE upper(unidade) LIKE :unidade" + filtroTv + " ORDER BY CAST(DATA AS date) DESC, CAST(HORA AS time) DESC";
		List<ChamadaPaciente> resultado = entityManager.createNativeQuery(sql, ChamadaPaciente.class)
			.setParameter("unidade", "%" + escaparLike(unidade.toUpperCase()) + "%")
			.setMaxResults(1)
			.getResultList();
		return resultado;
	}

	/**
	 * Rotas públicas do painel usam LIKE '%sigla%'. Sem escapar, a unidade "%" traria as
	 * chamadas (com nome de paciente) de todas as unidades. No PostgreSQL o escape padrão é '\'.
	 */
	static String escaparLike(String valor) {
		return String.valueOf(valor == null ? "" : valor)
				.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	@SuppressWarnings("unchecked")
	@Transactional
	public List<ChamadaPaciente> filtarultimo(String unidade, String tv) {
		unidade = unidade == null ? "" : unidade.trim().toUpperCase(Locale.ROOT);
		// Rota pública do painel (kiosk): a unidade vai como parâmetro, nunca concatenada no SQL.
		// A TV só entra no nome da coluna depois de validada (1 a 10).
		if (tv == null || !tv.matches("10|[1-9]")) {
			return List.of();
		}
		String sql = "select * from chamadapaciente where upper(unidade) = :unidade and p" + tv
				+ "=true and idchamada = (select idchamada from chamadapaciente where upper(unidade) = :unidade and p" + tv
				+ " = true and chamoup" + tv
				+ " = false ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc limit 1)"
				+ " ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc limit 1";
		return getEntityManager().createNativeQuery(sql, ChamadaPaciente.class)
				.setParameter("unidade", unidade)
				.getResultList();
	}

	public void removerOprimeiro(String unidade, String tv) {

		String sql = "";
		if (tv.equals("1")) {
			sql = "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  and p1 = true) ORDER BY idchamada asc  LIMIT 1)";
		}

		if (tv.equals("2")) {
			sql = "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  and p2 = true) ORDER BY idchamada asc  LIMIT 1)";
		}

		if (tv.equals("3")) {
			sql = "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  and p3 = true) ORDER BY idchamada asc  LIMIT 1)";
		}
		if (tv.equals("4")) {
			sql = "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  and p4 = true) ORDER BY idchamada asc  LIMIT 1)";
		}

		if (tv.equals("5")) {
			sql = "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  and p5 = true) ORDER BY idchamada asc  LIMIT 1)";
		}

		if (tv.equals("6")) {
			sql = "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  and p6 = true) ORDER BY idchamada asc  LIMIT 1)";
		}

		if (tv.equals("7")) {
			sql = "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  and p7 = true) ORDER BY idchamada asc  LIMIT 1)";
		}

		if (tv.equals("8")) {
			sql = "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  and p8 = true) ORDER BY idchamada asc  LIMIT 1)";
		}

		if (tv.equals("9")) {
			sql = "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  and p9 = true) ORDER BY idchamada asc  LIMIT 1)";
		}

		if (tv.equals("10")) {
			sql = "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  and p10 = true) ORDER BY idchamada asc  LIMIT 1)";
		}

		entityManager.createNativeQuery(sql).setParameter("unidade", unidade).executeUpdate();
	}

	/*
	 * public List<ChamadaPaciente> filtarseisultimos(String unidade, String tv) {
	 * return
	 * find("upper(unidade) like ?1 ORDER BY cast(DATA as date) desc, CAST(HORA as time) desc"
	 * , "%" + unidade.toUpperCase() + "%") .page(Page.ofSize(6)).list(); }
	 * 
	 * public List<ChamadaPaciente> filtarultimo(String unidade, String tv) { return
	 * find("upper(unidade) like ?1 ORDER BY CAST(HORA as time) desc", "%" +
	 * unidade.toUpperCase() + "%") .page(Page.ofSize(1)).list(); }
	 * 
	 * public void removerOprimeiro(String unidade, String tv) { String sql =
	 * "DELETE FROM chamadapaciente WHERE idchamada = (  SELECT idchamada   FROM chamadapaciente where unidade = :1  ORDER BY idchamada  LIMIT 1)"
	 * ; entityManager.createNativeQuery(sql).setParameter("unidade",
	 * unidade).executeUpdate(); }
	 * 
	 */
	public void atualizarChamada(int idchamada, String tv) {
		String sql = "";
		if (tv.equals("1"))
			sql = "UPDATE chamadapaciente set chamoup1 = true where idchamada = :id";
		if (tv.equals("2"))
			sql = "UPDATE chamadapaciente set chamoup2 = true where idchamada = :id";
		if (tv.equals("3"))
			sql = "UPDATE chamadapaciente set chamoup3 = true where idchamada = :id";
		if (tv.equals("4"))
			sql = "UPDATE chamadapaciente set chamoup4 = true where idchamada = :id";
		if (tv.equals("5"))
			sql = "UPDATE chamadapaciente set chamoup5 = true where idchamada = :id";
		if (tv.equals("6"))
			sql = "UPDATE chamadapaciente set chamoup6 = true where idchamada = :id";
		if (tv.equals("7"))
			sql = "UPDATE chamadapaciente set chamoup7 = true where idchamada = :id";
		if (tv.equals("8"))
			sql = "UPDATE chamadapaciente set chamoup8 = true where idchamada = :id";
		if (tv.equals("9"))
			sql = "UPDATE chamadapaciente set chamoup9 = true where idchamada = :id";
		if (tv.equals("10"))
			sql = "UPDATE chamadapaciente set chamoup10 = true where idchamada = :id";

		entityManager.createNativeQuery(sql).setParameter("id", idchamada).executeUpdate();
	}

}

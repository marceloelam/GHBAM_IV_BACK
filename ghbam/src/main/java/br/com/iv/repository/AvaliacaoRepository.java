package br.com.iv.repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import br.com.iv.entity.Avaliacao;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AvaliacaoRepository implements PanacheRepository<Avaliacao> {
	@Inject
	EntityManager entityManager;

	public String datas;

	public Avaliacao filtrarPorId(int id) {
		return find("idavaliacao = ?1", id).firstResult();
	}

	public Avaliacao filtrarPorControle(String controle) {
		return find("controle = ?1", controle).firstResult();
	}

	public Avaliacao ultimaPorPaciente(int idpaciente) {
		return find("idpaciente = ?1 order by data desc, hora desc, idavaliacao desc", idpaciente).firstResult();
	}

	@SuppressWarnings("unchecked")
	public Avaliacao ultimaPorBam(int idbam) {
		String sql = "SELECT a.* FROM avaliacao a INNER JOIN bam b ON b.avaliacao_idavaliacao = a.idavaliacao WHERE b.idbam = :idbam ORDER BY a.data DESC, a.hora DESC, a.idavaliacao DESC";
		List<Avaliacao> avaliacoes = entityManager.createNativeQuery(sql, Avaliacao.class)
				.setParameter("idbam", idbam)
				.getResultList();
		return avaliacoes.isEmpty() ? null : avaliacoes.get(0);
	}

	public List<Avaliacao> filtarPorControle(String query) {
		return find("controle = ?1", query).list();
	}

	public void alterarAvaliado(int idavaliacao, int avaliacao) {
		String sql = "UPDATE avaliacao set avaliado = " + avaliacao + " where idavaliacao = :id";
		entityManager.createNativeQuery(sql).setParameter("id", idavaliacao).executeUpdate();
		// atualizarTratamento(idavaliacao);
		// atualizarBam(idavaliacao);
	}

	// TRATAMENTO - ZERANDO CAMPO TRATADO
	public void atualizarTratamento(int idavaliacao) {
		// String sql = "UPDATE TRATAMENTO set tratado = 0 where idtratamento in (select
		// idtratamento from tratamento inner join bam on tratamento.idtratamento =
		// bam.tratamento_idtratamento inner join registro on registro.idregistro =
		// bam.registro_idregistro where registro.idregistro =:id)";
		String sql = "UPDATE TRATAMENTO set tratado = 0  where idtratamento in  (select idtratamento from tratamento inner join bam on tratamento.idtratamento = bam.tratamento_idtratamento inner join avaliacao on avaliacao.idavaliacao = bam.avaliacao_idavaliacao where avaliacao.idavaliacao =:id)";

		entityManager.createNativeQuery(sql).setParameter("id", idavaliacao).executeUpdate();
	}

	// BAM - retirar do atendimento sem apagar o marcador de transferencia
	public void atualizarBam(int idavaliacao) {
		// String sql = "UPDATE BAM set transferido = false, ematendimento = false where
		// idbam in (select idbam from bam inner join registro on registro.idregistro =
		// bam.registro_idregistro where registro.idregistro =:id)";
		String sql = "UPDATE BAM set ematendimento = false  where idbam in  (select idbam from bam  inner join avaliacao on avaliacao.idavaliacao = bam.avaliacao_idavaliacao where avaliacao.idavaliacao =:id)";

		entityManager.createNativeQuery(sql).setParameter("id", idavaliacao).executeUpdate();
	}

	public void removerEsperaMedico(int idavaliacao, String num) {
		int op = 0;
		if (num.equals("1"))
			op = 1;

		String sql = "UPDATE avaliacao set esperando = " + op + " where idavaliacao = :id";
		entityManager.createNativeQuery(sql).setParameter("id", idavaliacao).executeUpdate();
		System.out.println(sql);
	}

	public void atualizarCor(int idavaliacao, int ncor, String cor) {

		String sql = "UPDATE avaliacao set cor = :idcor, risco = :risco where idavaliacao = :id";
		entityManager.createNativeQuery(sql).setParameter("id", idavaliacao).setParameter("idcor", ncor)
				.setParameter("risco", cor).executeUpdate();
		System.out.println(sql);
	}

	public void atualizarAnamnese(int idavaliacao, String texto) {

		String sql = "UPDATE avaliacao set anamnese = '" + texto + "' where idavaliacao = :id";
		entityManager.createNativeQuery(sql).setParameter("id", idavaliacao).executeUpdate();
		System.out.println(sql);
	}

	@SuppressWarnings({ "unchecked", "unused" })
	@Transactional
	public List<Avaliacao> classificacaoDeRisco(String unidade, String dataRequerida) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		String datas = data.format(formatador).toString();

		String sql = "select * from avaliacao where cast(data as date) = cast('" + dataRequerida + "' as date) and unidade = '" + unidade + "'";

			return getEntityManager().createNativeQuery(sql, Avaliacao.class).getResultList();
	}

	
	
	@SuppressWarnings({ "unchecked", "unused" })
	@Transactional
	public Map<String, Map<String, Long>> listarClassificacaoDeRiscoCompleta(String unidade, String dataRequerida) {
	    try {
	        // Validar parâmetros de entrada
	        if (unidade == null || unidade.trim().isEmpty()) {
	            throw new IllegalArgumentException("Unidade não pode ser nula ou vazia");
	        }

	        if (dataRequerida == null || dataRequerida.trim().isEmpty()) {
	            throw new IllegalArgumentException("Data requerida não pode ser nula ou vazia");
	        }

	        // Converter e validar a data
	        LocalDate data;
	        try {
	            data = LocalDate.parse(dataRequerida);
	        } catch (DateTimeParseException e) {
	            throw new IllegalArgumentException("Formato de data inválido. Use o formato yyyy-MM-dd", e);
	        }

	        Map<String, Map<String, Long>> resultado = new LinkedHashMap<>();

	        // Consulta para o dia
	        resultado.put("dia", consultarClassificacao(unidade,
	            "cast(data as date) = cast(:data as date)",
	            Map.of("data", data.toString())
	        ));

	        // Consulta para a semana
	        LocalDate inicioSemana = data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	        LocalDate fimSemana = inicioSemana.plusDays(6);

	        resultado.put("semana", consultarClassificacao(unidade,
	            "cast(data as date) between cast(:inicioSemana as date) and cast(:fimSemana as date)",
	            Map.of("inicioSemana", inicioSemana.toString(), "fimSemana", fimSemana.toString())
	        ));

	        // Consulta para o mês
	        int mes = data.getMonthValue();
	        int ano = data.getYear();

	        resultado.put("mes", consultarClassificacao(unidade,
	            "extract(month from cast(data as date)) = cast(:mes as int) and extract(year from cast(data as date)) = cast(:ano as int)",
	            Map.of("mes", mes, "ano", ano)
	        ));

	        // Consulta para o ano
	        resultado.put("ano", consultarClassificacao(unidade,
	            "extract(year from cast(data as date)) = cast(:ano as int)",
	            Map.of("ano", ano)
	        ));

	        return resultado;

	    } catch (Exception e) {
	        String errorMsg = String.format("Erro ao listar classificação de risco para unidade %s e data %s: %s",
	            unidade, dataRequerida, e.getMessage());
	        System.out.println(errorMsg);
	        throw new PersistenceException(errorMsg, e);
	    }
	}

	private Map<String, Long> consultarClassificacao(String unidade, String whereClause, Map<String, Object> parametros) {
	    try {
	        String sql = "select risco, count(*) as quantidade from avaliacao " +
	                     "where " + whereClause + " and unidade = :unidade " +
	                     "group by risco";

	        Query query = getEntityManager().createNativeQuery(sql);
	        query.setParameter("unidade", unidade);

	        parametros.forEach(query::setParameter);

	        List<Object[]> resultados = query.getResultList();
	        Map<String, Long> contagem = new HashMap<>();

	        for (Object[] resultado : resultados) {
	            if (resultado[0] != null && resultado[1] != null) {
	                contagem.put(resultado[0].toString(), ((Number) resultado[1]).longValue());
	            }
	        }

	        return contagem;

	    } catch (Exception e) {
	        String errorMsg = String.format("Erro na consulta de classificação: %s", e.getMessage());
	        System.out.println(errorMsg);
	        throw new PersistenceException(errorMsg, e);
	    }
	}


	@Transactional
	public long contagemDeAtendimentos(String unidade, String data) {
		String sql = "";

		sql = "SELECT COUNT(*) FROM avaliacao WHERE unidade = :unidade and to_date(data, 'YYYY/MM/DD') between (cast(:dt as date) - interval '30 day') and cast("
				+ ":dt as date)";
		return ((Number) entityManager.createNativeQuery(sql).setParameter("unidade", unidade).setParameter("dt", data).getSingleResult())
				.longValue();
	}

	@Transactional
	public long contagemDeAtendimentosHoje(String unidade, String data) {
		String sql = "";

		sql = "SELECT COUNT(*) FROM avaliacao WHERE unidade = :unidade and to_date(data, 'YYYY/MM/DD') = cast("
				+ ":dt as date)";
		return ((Number) entityManager.createNativeQuery(sql).setParameter("unidade", unidade).setParameter("dt", data).getSingleResult())
				.longValue();
	}

	@Transactional
	public List<Map<String, Object>> contagemDeAtendimentosHojePorSetor(String unidade, String data) {
		String sql = "SELECT TRIM(r.setor) AS setor, COUNT(*) AS total "
				+ "FROM avaliacao a "
				+ "INNER JOIN bam b ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "INNER JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "WHERE a.unidade = :unidade "
				+ "AND to_date(a.data, 'YYYY/MM/DD') = cast(:dt as date) "
				+ "AND NULLIF(TRIM(r.setor), '') IS NOT NULL "
				+ "AND UPPER(TRIM(r.setor)) <> 'SEM SETOR' "
				+ "GROUP BY TRIM(r.setor) "
				+ "ORDER BY total DESC, setor ASC";
		return listarAtendimentosPorSetor(sql, unidade, data);
	}

	@Transactional
	public List<Map<String, Object>> contagemDeAtendimentosSemanaPorSetor(String unidade, String data) {
		String sql = "SELECT TRIM(r.setor) AS setor, COUNT(*) AS total "
				+ "FROM avaliacao a "
				+ "INNER JOIN bam b ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "INNER JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "WHERE a.unidade = :unidade "
				+ "AND to_date(a.data, 'YYYY/MM/DD') BETWEEN cast(:dt as date) - interval '6 day' "
				+ "AND cast(:dt as date) "
				+ "AND NULLIF(TRIM(r.setor), '') IS NOT NULL "
				+ "AND UPPER(TRIM(r.setor)) <> 'SEM SETOR' "
				+ "GROUP BY TRIM(r.setor) "
				+ "ORDER BY total DESC, setor ASC";
		return listarAtendimentosPorSetor(sql, unidade, data);
	}

	@Transactional
	public List<Map<String, Object>> contagemDeAtendimentosMesPorSetor(String unidade, String data) {
		String sql = "SELECT TRIM(r.setor) AS setor, COUNT(*) AS total "
				+ "FROM avaliacao a "
				+ "INNER JOIN bam b ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "INNER JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "WHERE a.unidade = :unidade "
				+ "AND to_date(a.data, 'YYYY/MM/DD') >= date_trunc('month', cast(:dt as date))::date "
				+ "AND to_date(a.data, 'YYYY/MM/DD') < (date_trunc('month', cast(:dt as date))::date + interval '1 month') "
				+ "AND NULLIF(TRIM(r.setor), '') IS NOT NULL "
				+ "AND UPPER(TRIM(r.setor)) <> 'SEM SETOR' "
				+ "GROUP BY TRIM(r.setor) "
				+ "ORDER BY total DESC, setor ASC";
		return listarAtendimentosPorSetor(sql, unidade, data);
	}

	@Transactional
	public List<Map<String, Object>> contagemDeAtendimentosAnoPorSetor(String unidade, String data) {
		String sql = "SELECT TRIM(r.setor) AS setor, COUNT(*) AS total "
				+ "FROM avaliacao a "
				+ "INNER JOIN bam b ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "INNER JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "WHERE a.unidade = :unidade "
				+ "AND to_date(a.data, 'YYYY/MM/DD') >= date_trunc('year', cast(:dt as date))::date "
				+ "AND to_date(a.data, 'YYYY/MM/DD') < (date_trunc('year', cast(:dt as date))::date + interval '1 year') "
				+ "AND NULLIF(TRIM(r.setor), '') IS NOT NULL "
				+ "AND UPPER(TRIM(r.setor)) <> 'SEM SETOR' "
				+ "GROUP BY TRIM(r.setor) "
				+ "ORDER BY total DESC, setor ASC";
		return listarAtendimentosPorSetor(sql, unidade, data);
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> listarAtendimentosPorSetor(String sql, String unidade, String data) {
		List<Object[]> resultados = entityManager.createNativeQuery(sql)
				.setParameter("unidade", unidade)
				.setParameter("dt", data)
				.getResultList();

		return resultados.stream()
				.map(resultado -> {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("setor", resultado[0] == null ? "" : resultado[0].toString());
					item.put("total", resultado[1] == null ? 0L : ((Number) resultado[1]).longValue());
					return item;
				})
				.toList();
	}
	
	
}


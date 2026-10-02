package br.com.iv.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import br.com.iv.dto.BiExecutivoDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;

@ApplicationScoped
public class BiExecutivoService {
	@Inject
	EntityManager entityManager;
	@Inject
	ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

	public Map<String, Object> montarPainelMunicipal(LocalDate inicio, LocalDate fim, String unidadeUsuario) {
		Set<String> permitidas = unidadesDoMunicipio(unidadeUsuario);
		String municipio = configuracaoUnidadeRepository.buscarMunicipioPorSigla(unidadeUsuario);
		List<Map<String, Object>> unidades = filtrarPorUnidade(agregadosPorUnidade(inicio, fim), permitidas);
		Map<String, Object> painel = new LinkedHashMap<>();
		painel.put("inicio", inicio.toString());
		painel.put("fim", fim.toString());
		painel.put("municipio", municipio == null || municipio.isBlank() ? unidadeUsuario : municipio);
		painel.put("unidades", unidades);
		painel.put("especialidades", filtrarPorUnidade(demandaPorEspecialidade(inicio, fim), permitidas));
		painel.put("territorios", filtrarPorUnidade(demandaPorTerritorio(inicio, fim), permitidas));
		painel.put("totais", totaisDasUnidades(unidades));
		return painel;
	}

	private Set<String> unidadesDoMunicipio(String unidadeUsuario) {
		String municipio = configuracaoUnidadeRepository.buscarMunicipioPorSigla(unidadeUsuario);
		Set<String> permitidas = new HashSet<>();
		configuracaoUnidadeRepository.listAll().stream()
				.filter(u -> municipio != null && municipio.equalsIgnoreCase(u.getMunicipioPadrao()))
				.map(u -> normalizar(u.getSigla())).filter(s -> !s.isBlank()).forEach(permitidas::add);
		if (permitidas.isEmpty()) permitidas.add(normalizar(unidadeUsuario));
		return permitidas;
	}

	private List<Map<String, Object>> filtrarPorUnidade(List<Map<String, Object>> itens, Set<String> permitidas) {
		return itens.stream().filter(item -> permitidas.contains(normalizar(String.valueOf(item.get("unidade"))))).toList();
	}

	private String normalizar(String valor) { return valor == null ? "" : valor.trim().toUpperCase(); }

	private Map<String, Object> totaisDasUnidades(List<Map<String, Object>> unidades) {
		Map<String, Object> totais = new LinkedHashMap<>();
		for (String campo : List.of("atendimentos", "pacientes", "internados", "examesPendentes")) {
			long soma = unidades.stream().mapToLong(u -> ((Number) u.getOrDefault(campo, 0)).longValue()).sum();
			totais.put(campo, soma);
		}
		return totais;
	}

	private List<Map<String, Object>> demandaPorTerritorio(LocalDate inicio, LocalDate fim) {
		String sql = "SELECT COALESCE(b.unidade, 'SEM UNIDADE'), COALESCE(NULLIF(p.bairro, ''), 'NAO INFORMADO'), "
				+ "COALESCE(NULLIF(p.municipio, ''), 'NAO INFORMADO'), COUNT(b.idbam), COUNT(DISTINCT p.idpaciente) "
				+ "FROM bam b JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente "
				+ "WHERE b.data BETWEEN :inicio AND :fim GROUP BY b.unidade, p.bairro, p.municipio "
				+ "ORDER BY b.unidade, COUNT(DISTINCT p.idpaciente) DESC, COUNT(b.idbam) DESC";
		List<Map<String, Object>> resultado = new java.util.ArrayList<>();
		try {
			@SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery(sql)
					.setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
			for (Object[] row : rows) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("unidade", texto(row[0]));
				item.put("bairro", texto(row[1]));
				item.put("municipio", texto(row[2]));
				item.put("atendimentos", numero(row[3]));
				item.put("pacientes", numero(row[4]));
				resultado.add(item);
			}
		} catch (Exception ignored) { /* endereco legado incompleto nao bloqueia o painel */ }
		return resultado;
	}

	private List<Map<String, Object>> demandaPorEspecialidade(LocalDate inicio, LocalDate fim) {
		String sql = "SELECT COALESCE(b.unidade, 'SEM UNIDADE'), COALESCE(NULLIF(r.especialidade, ''), NULLIF(r.setor, ''), 'NAO INFORMADA'), "
				+ "COUNT(b.idbam), COUNT(DISTINCT b.paciente_idpaciente) "
				+ "FROM bam b JOIN registro r ON r.idregistro = b.registro_idregistro "
				+ "WHERE b.data BETWEEN :inicio AND :fim GROUP BY b.unidade, r.especialidade, r.setor "
				+ "ORDER BY b.unidade, COUNT(b.idbam) DESC, r.especialidade, r.setor";
		List<Map<String, Object>> resultado = new java.util.ArrayList<>();
		try {
			@SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery(sql)
					.setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
			for (Object[] row : rows) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("unidade", texto(row[0]));
				item.put("especialidade", texto(row[1]));
				item.put("atendimentos", numero(row[2]));
				item.put("pacientes", numero(row[3]));
				resultado.add(item);
			}
		} catch (Exception ignored) { /* registros legados sem especialidade nao bloqueiam o painel */ }
		return resultado;
	}

	private List<Map<String, Object>> agregadosPorUnidade(LocalDate inicio, LocalDate fim) {
		String sql = "SELECT b.unidade, COUNT(b.idbam), COUNT(DISTINCT b.paciente_idpaciente), "
				+ "COUNT(*) FILTER (WHERE r.temlabo = true AND COALESCE(r.resultadolabo, false) = false), "
				+ "COALESCE((SELECT COUNT(*) FROM novointernado n WHERE n.unidade = b.unidade AND n.alta = false), 0) "
				+ "FROM bam b LEFT JOIN registro r ON r.idregistro = b.registro_idregistro "
				+ "WHERE CAST(b.data AS DATE) BETWEEN :inicio AND :fim "
				+ "GROUP BY b.unidade ORDER BY COUNT(b.idbam) DESC, b.unidade";
		List<Map<String, Object>> resultado = linhas(sql, inicio, fim);
		Map<String, Map<String, Object>> porUnidade = new LinkedHashMap<>();
		for (Map<String, Object> item : resultado) {
			porUnidade.put(String.valueOf(item.get("unidade")), item);
		}
		adicionarFaturamento(porUnidade, inicio, fim);
		adicionarEstoque(porUnidade);
		adicionarVigilancia(porUnidade, inicio, fim);
		adicionarOperacao(porUnidade, inicio, fim);
		adicionarAgendamento(porUnidade, inicio, fim);
		return new java.util.ArrayList<>(porUnidade.values());
	}

	private void adicionarAgendamento(Map<String, Map<String, Object>> porUnidade, LocalDate inicio, LocalDate fim) {
		String sql = "SELECT COALESCE(unidade, 'SEM UNIDADE'), COUNT(*), "
				+ "COUNT(*) FILTER (WHERE status = 'FALTOU'), "
				+ "COUNT(*) FILTER (WHERE status = 'CANCELADO'), "
				+ "COUNT(*) FILTER (WHERE status = 'FINALIZADO') "
				+ "FROM consultorioagendastatus WHERE dataatendimento BETWEEN :inicio AND :fim GROUP BY unidade";
		try {
			@SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery(sql)
					.setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
			for (Object[] row : rows) {
				Map<String, Object> item = porUnidade.computeIfAbsent(texto(row[0]), this::novaUnidade);
				item.put("agendamentos", numero(row[1]));
				item.put("faltas", numero(row[2]));
				item.put("cancelamentos", numero(row[3]));
				item.put("agendamentosFinalizados", numero(row[4]));
			}
		} catch (Exception ignored) { /* agenda pode nao estar habilitada no municipio */ }
	}

	private void adicionarOperacao(Map<String, Map<String, Object>> porUnidade, LocalDate inicio, LocalDate fim) {
		String sql = "SELECT COALESCE(unidade, 'SEM UNIDADE'), "
				+ "COUNT(*) FILTER (WHERE ematendimento = true), "
				+ "COUNT(*) FILTER (WHERE internar = true), "
				+ "COUNT(*) FILTER (WHERE dar_alta = true) "
				+ "FROM bam WHERE data BETWEEN :inicio AND :fim GROUP BY unidade";
		try {
			@SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery(sql)
					.setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
			for (Object[] row : rows) {
				Map<String, Object> item = porUnidade.computeIfAbsent(texto(row[0]), this::novaUnidade);
				item.put("emAtendimento", numero(row[1]));
				item.put("solicitacoesInternacao", numero(row[2]));
				item.put("altas", numero(row[3]));
			}
		} catch (Exception ignored) { /* estrutura operacional legada pode variar */ }
	}

	private void adicionarVigilancia(Map<String, Map<String, Object>> porUnidade, LocalDate inicio, LocalDate fim) {
		String sql = "SELECT COALESCE(unidade, 'SEM UNIDADE'), COUNT(*), "
				+ "COUNT(*) FILTER (WHERE status IN ('RASCUNHO', 'PRONTO', 'ENCAMINHADO')) "
				+ "FROM notificacao_sinan WHERE CAST(data_notificacao AS DATE) BETWEEN :inicio AND :fim GROUP BY unidade";
		try {
			@SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery(sql)
					.setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
			for (Object[] row : rows) {
				Map<String, Object> item = porUnidade.computeIfAbsent(texto(row[0]), this::novaUnidade);
				item.put("notificacoesSinan", numero(row[1]));
				item.put("pendenciasVigilancia", numero(row[2]));
			}
		} catch (Exception ignored) { /* instalacoes anteriores podem nao ter SINAN */ }
	}

	private void adicionarFaturamento(Map<String, Map<String, Object>> porUnidade, LocalDate inicio, LocalDate fim) {
		String sql = "SELECT COALESCE(unidade, 'SEM UNIDADE'), COUNT(*), "
				+ "COUNT(*) FILTER (WHERE status IN ('PRODUCAO_COM_CRITICA', 'PENDENTE_CLASSIFICACAO', 'REVISAR')), "
				+ "COALESCE(SUM(CASE WHEN status IN ('PRODUCAO_COM_CRITICA', 'PENDENTE_CLASSIFICACAO', 'REVISAR') "
				+ "THEN COALESCE(pa_sa, 0) + COALESCE(pa_sp, 0) + COALESCE(pa_sh, 0) ELSE 0 END), 0) "
				+ "FROM afaturar WHERE CAST(COALESCE(dtatendimento, data) AS DATE) BETWEEN :inicio AND :fim "
				+ "GROUP BY unidade";
		try {
			@SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery(sql)
					.setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
			for (Object[] row : rows) {
				Map<String, Object> item = porUnidade.computeIfAbsent(texto(row[0]), this::novaUnidade);
				item.put("producaoSus", numero(row[1]));
				item.put("pendenciasSus", numero(row[2]));
				item.put("valorRetidoSus", numero(row[3]));
			}
		} catch (Exception ignored) { /* base legada sem a coluna ou formato esperado */ }
	}

	private void adicionarEstoque(Map<String, Map<String, Object>> porUnidade) {
		String sql = "SELECT COALESCE(e.unidade_sigla, 'SEM UNIDADE'), "
				+ "COUNT(*) FILTER (WHERE e.ativo = true), "
				+ "COUNT(*) FILTER (WHERE e.ativo = true AND e.quantidade <= COALESCE(e.estoque_minimo, 0)), "
				+ "COUNT(*) FILTER (WHERE e.ativo = true AND l.data_validade < CURRENT_DATE) "
				+ "FROM pharmacia_estoque e JOIN pharmacia_lote l ON l.id = e.lote_id "
				+ "GROUP BY e.unidade_sigla";
		try {
			@SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
			for (Object[] row : rows) {
				Map<String, Object> item = porUnidade.computeIfAbsent(texto(row[0]), this::novaUnidade);
				item.put("itensEstoque", numero(row[1]));
				item.put("estoqueCritico", numero(row[2]));
				item.put("lotesVencidos", numero(row[3]));
			}
		} catch (Exception ignored) { /* modulo de farmacia pode nao estar instalado na unidade */ }
	}

	private Map<String, Object> novaUnidade(String unidade) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("unidade", unidade);
		item.put("atendimentos", 0);
		item.put("pacientes", 0);
		item.put("examesPendentes", 0);
		item.put("internados", 0);
		item.put("notificacoesSinan", 0);
		item.put("pendenciasVigilancia", 0);
		item.put("emAtendimento", 0);
		item.put("solicitacoesInternacao", 0);
		item.put("altas", 0);
		item.put("agendamentos", 0);
		item.put("faltas", 0);
		item.put("cancelamentos", 0);
		item.put("agendamentosFinalizados", 0);
		return item;
	}

	private Map<String, Object> totaisMunicipais(LocalDate inicio, LocalDate fim) {
		String sql = "SELECT COUNT(b.idbam), COUNT(DISTINCT b.paciente_idpaciente), "
				+ "COUNT(*) FILTER (WHERE r.temlabo = true AND COALESCE(r.resultadolabo, false) = false), "
				+ "(SELECT COUNT(*) FROM novointernado n WHERE n.alta = false) "
				+ "FROM bam b LEFT JOIN registro r ON r.idregistro = b.registro_idregistro "
				+ "WHERE CAST(b.data AS DATE) BETWEEN :inicio AND :fim";
		@SuppressWarnings("unchecked")
		List<Object[]> rows = entityManager.createNativeQuery(sql).setParameter("inicio", inicio)
				.setParameter("fim", fim).getResultList();
		Object[] row = rows.isEmpty() ? new Object[] { 0, 0, 0, 0 } : rows.get(0);
		Map<String, Object> totais = new LinkedHashMap<>();
		totais.put("atendimentos", numero(row[0]));
		totais.put("pacientes", numero(row[1]));
		totais.put("examesPendentes", numero(row[2]));
		totais.put("internados", numero(row[3]));
		return totais;
	}

	private List<Map<String, Object>> linhas(String sql, LocalDate inicio, LocalDate fim) {
		@SuppressWarnings("unchecked")
		List<Object[]> rows = entityManager.createNativeQuery(sql).setParameter("inicio", inicio)
				.setParameter("fim", fim).getResultList();
		List<Map<String, Object>> resultado = new java.util.ArrayList<>();
		for (Object[] row : rows) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("unidade", texto(row[0]));
			item.put("atendimentos", numero(row[1]));
			item.put("pacientes", numero(row[2]));
			item.put("examesPendentes", numero(row[3]));
			item.put("internados", numero(row[4]));
			resultado.add(item);
		}
		return resultado;
	}

	public BiExecutivoDto montarPainel(String unidade, LocalDate inicio, LocalDate fim) {
		BiExecutivoDto painel = new BiExecutivoDto();
		painel.setUnidade(unidade);
		painel.setInicio(inicio.toString());
		painel.setFim(fim.toString());

		BigDecimal leitos = scalar("SELECT COUNT(*) FROM localcenso WHERE unidade = :unidade AND excluido = false",
				unidade, inicio, fim);
		BigDecimal internados = scalar(
				"SELECT COUNT(*) FROM novointernado WHERE unidade = :unidade AND alta = false", unidade, inicio, fim);
		BigDecimal altas = scalar(
				"SELECT COUNT(*) FROM novointernado WHERE unidade = :unidade AND alta = true AND CAST(datadaacao AS DATE) BETWEEN :inicio AND :fim",
				unidade, inicio, fim);
		BigDecimal admissoes = scalar(
				"SELECT COUNT(*) FROM novointernado WHERE unidade = :unidade AND CAST(diainternacao AS DATE) BETWEEN :inicio AND :fim",
				unidade, inicio, fim);
		BigDecimal permanencia = scalar(
				"SELECT AVG(CURRENT_DATE - CAST(diainternacao AS DATE)) FROM novointernado WHERE unidade = :unidade AND alta = false AND diainternacao IS NOT NULL",
				unidade, inicio, fim);
		BigDecimal portaMedico = scalar(
				"SELECT AVG(EXTRACT(EPOCH FROM ((CAST(a.data AS DATE) + CAST(a.hora AS TIME)) - (CAST(b.data AS DATE) + CAST(b.hora AS TIME)))) / 60) "
						+ "FROM atendimento a JOIN bam b ON b.idbam = a.bam WHERE b.unidade = :unidade "
						+ "AND CAST(b.data AS DATE) BETWEEN :inicio AND :fim AND a.hora IS NOT NULL AND b.hora IS NOT NULL",
				unidade, inicio, fim);
		BigDecimal taxaRetorno = scalar(
				"SELECT CASE WHEN COUNT(*) = 0 THEN 0 ELSE ROUND((COUNT(*) FILTER (WHERE total_paciente > 1)::numeric / COUNT(*)) * 100, 2) END "
						+ "FROM (SELECT b.paciente_idpaciente, COUNT(*) OVER (PARTITION BY b.paciente_idpaciente) total_paciente "
						+ "FROM bam b WHERE b.unidade = :unidade AND CAST(b.data AS DATE) BETWEEN :inicio AND :fim) retornos",
				unidade, inicio, fim);
		BigDecimal examesPendentes = scalar(
				"SELECT COUNT(*) FROM bam b JOIN registro r ON b.registro_idregistro = r.idregistro "
						+ "WHERE b.unidade = :unidade AND CAST(b.data AS DATE) BETWEEN :inicio AND :fim "
						+ "AND r.temlabo = true AND COALESCE(r.resultadolabo, false) = false",
				unidade, inicio, fim);
		BigDecimal examesSla = scalar(
				"SELECT AVG(EXTRACT(EPOCH FROM ((p.data_exame + CAST(p.hr AS TIME)) - (CAST(b.data AS DATE) + CAST(b.hora AS TIME)))) / 60) "
						+ "FROM (SELECT pr.*, CASE "
						+ "WHEN pr.dt ~ '^\\d{4}/\\d{2}/\\d{2}$' THEN TO_DATE(pr.dt, 'YYYY/MM/DD') "
						+ "WHEN pr.dt ~ '^\\d{2}/\\d{2}/\\d{4}$' THEN TO_DATE(pr.dt, 'DD/MM/YYYY') "
						+ "ELSE NULL END AS data_exame FROM pedidoresultadolaboratorio pr) p "
						+ "JOIN bam b ON b.idbam = p.bam WHERE p.unidade = :unidade "
						+ "AND p.data_exame BETWEEN :inicio AND :fim AND p.hr ~ '^\\d{2}:\\d{2}$' AND b.hora ~ '^\\d{2}:\\d{2}$'",
				unidade, inicio, fim);
		BigDecimal consumoPorPaciente = scalar(
				"SELECT AVG(total_paciente) FROM (SELECT paciente_id, SUM(quantidade) total_paciente FROM pharmacia_dispensacao "
						+ "WHERE unidade_sigla = :unidade AND ativo = true AND CAST(data_hora AS DATE) BETWEEN :inicio AND :fim GROUP BY paciente_id) consumo",
				unidade, inicio, fim);
		BigDecimal infeccaoCcih = scalar(
				"SELECT COUNT(*) FROM resumodealta r JOIN bam b ON r.bam ~ '^[0-9]+$' AND CAST(r.bam AS INTEGER) = b.idbam "
						+ "WHERE r.infeccao = true AND b.unidade = :unidade AND CAST(r.dataalta AS DATE) BETWEEN :inicio AND :fim",
				unidade, inicio, fim);

		painel.getIndicadores().add(indicador("ocupacao", "Ocupacao", porcentagem(internados, leitos), "%",
				statusMaiorMelhor(porcentagem(internados, leitos), 85, 95), detalhe(internados, "internados de", leitos, "leitos")));
		painel.getIndicadores().add(indicador("giro_leito", "Giro de leito", dividir(altas, leitos), "altas/leito",
				"info", "Altas no periodo dividido pelos leitos cadastrados"));
		painel.getIndicadores().add(indicador("permanencia_media", "Permanencia media", permanencia, "dias",
				statusMenorMelhor(permanencia, 5, 8), "Pacientes atualmente internados"));
		painel.getIndicadores().add(indicador("porta_medico", "Tempo porta-medico (BAM-medico)", portaMedico, "min",
				statusMenorMelhor(portaMedico, 30, 60), "Media entre abertura do BAM e primeiro atendimento medico"));
		painel.getIndicadores().add(indicador("taxa_retorno", "Taxa de retorno", taxaRetorno, "%",
				statusMenorMelhor(taxaRetorno, 10, 20), "Pacientes com mais de uma passagem no periodo"));
		painel.getIndicadores().add(indicador("consumo_paciente", "Consumo por paciente", consumoPorPaciente, "itens",
				"info", "Media de itens dispensados pela farmacia por paciente"));
		painel.getIndicadores().add(indicador("sla_exames", "SLA medio exames (BAM-resultado)", examesSla, "min",
				statusMenorMelhor(examesSla, 120, 240), "Media entre BAM e resultado laboratorial"));
		painel.getIndicadores().add(indicador("ccih_infeccao", "CCIH infeccao", infeccaoCcih, "casos",
				statusMenorMelhor(infeccaoCcih, 1, 3), "Resumo de alta com infeccao marcada"));
		painel.getIndicadores().add(indicador("nir_admissoes", "Admissoes NIR", admissoes, "pacientes", "info",
				"Novas internacoes no periodo"));
		painel.getIndicadores().add(indicador("exames_pendentes", "Exames pendentes", examesPendentes, "pacientes",
				statusMenorMelhor(examesPendentes, 5, 15), "BAMs com laboratorio solicitado e sem resultado"));

		painel.setOcupacaoPorSetor(series(
				"SELECT COALESCE(s.setor, 'SEM SETOR') label, COUNT(n.idninternado) ocupados, COUNT(l.idsetorlocalcenso) leitos "
						+ "FROM setorcenso s LEFT JOIN localcenso l ON l.setorcenso_idsetorcenso = s.idsetorcenso AND l.excluido = false "
						+ "LEFT JOIN novointernado n ON n.local_idsetorlocalcenso = l.idsetorlocalcenso AND n.alta = false AND n.unidade = :unidade "
						+ "WHERE s.unidade = :unidade AND s.excluido = false GROUP BY s.setor ORDER BY ocupados DESC, s.setor",
				unidade, inicio, fim, true));
		painel.setProdutividadePorSetor(series(
				"SELECT COALESCE(r.setor, 'SEM SETOR') label, COUNT(b.idbam) valor, 0 referencia "
						+ "FROM bam b JOIN registro r ON b.registro_idregistro = r.idregistro WHERE b.unidade = :unidade "
						+ "AND CAST(b.data AS DATE) BETWEEN :inicio AND :fim GROUP BY r.setor ORDER BY valor DESC LIMIT 12",
				unidade, inicio, fim, false));
		painel.setTendenciaAtendimentos(series(
				"SELECT TO_CHAR(CAST(b.data AS DATE), 'DD/MM') label, COUNT(b.idbam) valor, 0 referencia "
						+ "FROM bam b WHERE b.unidade = :unidade AND CAST(b.data AS DATE) BETWEEN :inicio AND :fim "
						+ "GROUP BY CAST(b.data AS DATE) ORDER BY CAST(b.data AS DATE)",
				unidade, inicio, fim, false));
		painel.setDetalhesCcih(detalhesCcih(unidade, inicio, fim));
		painel.setDetalhesIndicadores(detalhesIndicadores(unidade, inicio, fim));

		montarAlertas(painel);
		return painel;
	}

	private BiExecutivoDto.Indicador indicador(String chave, String titulo, BigDecimal valor, String unidadeMedida,
			String status, String detalhe) {
		if (valor == null) {
			return BiExecutivoDto.Indicador.indisponivel(chave, titulo, detalhe);
		}
		return new BiExecutivoDto.Indicador(chave, titulo, valor.setScale(1, RoundingMode.HALF_UP), unidadeMedida, status,
				detalhe);
	}

	private List<BiExecutivoDto.Serie> series(String sql, String unidade, LocalDate inicio, LocalDate fim,
			boolean calcularPercentual) {
		List<BiExecutivoDto.Serie> lista = new java.util.ArrayList<>();
		try {
			Query query = entityManager.createNativeQuery(sql);
			aplicarParametros(query, sql, unidade, inicio, fim);
			@SuppressWarnings("unchecked")
			List<Object[]> rows = query.getResultList();
			for (Object[] row : rows) {
				String label = String.valueOf(row[0]);
				BigDecimal valor = numero(row[1]);
				BigDecimal referencia = row.length > 2 ? numero(row[2]) : BigDecimal.ZERO;
				BigDecimal exibicao = calcularPercentual ? porcentagem(valor, referencia) : valor;
				lista.add(new BiExecutivoDto.Serie(label, exibicao, referencia,
						calcularPercentual ? detalhe(valor, "ocupados de", referencia, "leitos") : ""));
			}
		} catch (Exception e) {
			lista.add(new BiExecutivoDto.Serie("Dados indisponiveis", null, null, "Nao foi possivel calcular esta serie."));
		}
		return lista;
	}

	private BigDecimal scalar(String sql, String unidade, LocalDate inicio, LocalDate fim) {
		try {
			Query query = entityManager.createNativeQuery(sql);
			aplicarParametros(query, sql, unidade, inicio, fim);
			return numero(query.getSingleResult());
		} catch (Exception e) {
			return null;
		}
	}

	private List<BiExecutivoDto.DetalheCcih> detalhesCcih(String unidade, LocalDate inicio, LocalDate fim) {
		List<BiExecutivoDto.DetalheCcih> lista = new java.util.ArrayList<>();
		String sql = "SELECT r.bam, COALESCE(p.nome, r.nome), TO_CHAR(CAST(r.dataalta AS DATE), 'DD/MM/YYYY'), "
				+ "COALESCE(r.cid2, r.cid, ''), COALESCE(r.diagfinal, r.diaginicial, ''), "
				+ "COALESCE(r.germesiso, ''), COALESCE(r.antibioticoutil, '') "
				+ "FROM resumodealta r JOIN bam b ON r.bam ~ '^[0-9]+$' AND CAST(r.bam AS INTEGER) = b.idbam "
				+ "LEFT JOIN pacientes p ON b.paciente_idpaciente = p.idpaciente "
				+ "WHERE r.infeccao = true AND b.unidade = :unidade AND CAST(r.dataalta AS DATE) BETWEEN :inicio AND :fim "
				+ "ORDER BY CAST(r.dataalta AS DATE) DESC, r.bam DESC";
		try {
			Query query = entityManager.createNativeQuery(sql);
			aplicarParametros(query, sql, unidade, inicio, fim);
			@SuppressWarnings("unchecked")
			List<Object[]> rows = query.getResultList();
			for (Object[] row : rows) {
				lista.add(new BiExecutivoDto.DetalheCcih(texto(row[0]), texto(row[1]), texto(row[2]), texto(row[3]),
						texto(row[4]), texto(row[5]), texto(row[6])));
			}
		} catch (Exception e) {
			return lista;
		}
		return lista;
	}

	private List<BiExecutivoDto.DetalheIndicador> detalhesIndicadores(String unidade, LocalDate inicio, LocalDate fim) {
		List<BiExecutivoDto.DetalheIndicador> lista = new java.util.ArrayList<>();
		lista.addAll(detalhesGenericos(
				"Ocupacao",
				"SELECT n.nbam, p.nome, COALESCE(s.setor, ''), TO_CHAR(CAST(n.diainternacao AS DATE), 'DD/MM/YYYY'), "
						+ "CAST((CURRENT_DATE - CAST(n.diainternacao AS DATE)) AS TEXT) || ' dia(s)', COALESCE(l.local, '') "
						+ "FROM novointernado n JOIN pacientes p ON n.pacientes_idpaciente = p.idpaciente "
						+ "LEFT JOIN setorcenso s ON n.setor_idsetorcenso = s.idsetorcenso "
						+ "LEFT JOIN localcenso l ON n.local_idsetorlocalcenso = l.idsetorlocalcenso "
						+ "WHERE n.unidade = :unidade AND n.alta = false ORDER BY s.setor, l.local, p.nome",
				unidade, inicio, fim));
		lista.addAll(detalhesGenericos(
				"Giro de leito",
				"SELECT n.nbam, p.nome, COALESCE(s.setor, ''), TO_CHAR(CAST(n.datadaacao AS DATE), 'DD/MM/YYYY'), "
						+ "COALESCE(n.motivosaida, ''), COALESCE(l.local, '') "
						+ "FROM novointernado n JOIN pacientes p ON n.pacientes_idpaciente = p.idpaciente "
						+ "LEFT JOIN setorcenso s ON n.setor_idsetorcenso = s.idsetorcenso "
						+ "LEFT JOIN localcenso l ON n.local_idsetorlocalcenso = l.idsetorlocalcenso "
						+ "WHERE n.unidade = :unidade AND n.alta = true AND CAST(n.datadaacao AS DATE) BETWEEN :inicio AND :fim "
						+ "ORDER BY CAST(n.datadaacao AS DATE) DESC, p.nome",
				unidade, inicio, fim));
		lista.addAll(detalhesGenericos(
				"Admissoes NIR",
				"SELECT n.nbam, p.nome, COALESCE(s.setor, ''), TO_CHAR(CAST(n.diainternacao AS DATE), 'DD/MM/YYYY'), "
						+ "COALESCE(n.tipo_alocacao, ''), COALESCE(l.local, '') "
						+ "FROM novointernado n JOIN pacientes p ON n.pacientes_idpaciente = p.idpaciente "
						+ "LEFT JOIN setorcenso s ON n.setor_idsetorcenso = s.idsetorcenso "
						+ "LEFT JOIN localcenso l ON n.local_idsetorlocalcenso = l.idsetorlocalcenso "
						+ "WHERE n.unidade = :unidade AND CAST(n.diainternacao AS DATE) BETWEEN :inicio AND :fim "
						+ "ORDER BY CAST(n.diainternacao AS DATE) DESC, p.nome",
				unidade, inicio, fim));
		lista.addAll(detalhesGenericos(
				"Tempo porta-medico (BAM-medico)",
				"SELECT b.idbam, p.nome, COALESCE(r.setor, ''), TO_CHAR(CAST(b.data AS DATE), 'DD/MM/YYYY'), "
						+ "ROUND(EXTRACT(EPOCH FROM ((CAST(a.data AS DATE) + CAST(a.hora AS TIME)) - (CAST(b.data AS DATE) + CAST(b.hora AS TIME)))) / 60)::TEXT || ' min', "
						+ "'BAM ' || COALESCE(b.hora, '') || ' / medico ' || COALESCE(a.hora, '') "
						+ "FROM atendimento a JOIN bam b ON b.idbam = a.bam JOIN pacientes p ON b.paciente_idpaciente = p.idpaciente "
						+ "LEFT JOIN registro r ON b.registro_idregistro = r.idregistro "
						+ "WHERE b.unidade = :unidade AND CAST(b.data AS DATE) BETWEEN :inicio AND :fim AND a.hora IS NOT NULL AND b.hora IS NOT NULL "
						+ "ORDER BY CAST(b.data AS DATE) DESC, b.hora DESC LIMIT 200",
				unidade, inicio, fim));
		lista.addAll(detalhesGenericos(
				"Taxa de retorno",
				"SELECT STRING_AGG(CAST(b.idbam AS TEXT), ', ' ORDER BY b.data), p.nome, '', '', COUNT(b.idbam)::TEXT || ' passagens', "
						+ "MIN(TO_CHAR(CAST(b.data AS DATE), 'DD/MM/YYYY')) || ' a ' || MAX(TO_CHAR(CAST(b.data AS DATE), 'DD/MM/YYYY')) "
						+ "FROM bam b JOIN pacientes p ON b.paciente_idpaciente = p.idpaciente "
						+ "WHERE b.unidade = :unidade AND CAST(b.data AS DATE) BETWEEN :inicio AND :fim "
						+ "GROUP BY p.idpaciente, p.nome HAVING COUNT(b.idbam) > 1 ORDER BY COUNT(b.idbam) DESC, p.nome",
				unidade, inicio, fim));
		lista.addAll(detalhesGenericos(
				"Consumo por paciente",
				"SELECT '', COALESCE(paciente_nome, 'Paciente nao informado'), '', '', SUM(quantidade)::TEXT || ' item(ns)', "
						+ "STRING_AGG(DISTINCT produto_nome, ', ') "
						+ "FROM pharmacia_dispensacao WHERE unidade_sigla = :unidade AND ativo = true AND CAST(data_hora AS DATE) BETWEEN :inicio AND :fim "
						+ "GROUP BY paciente_id, paciente_nome ORDER BY SUM(quantidade) DESC LIMIT 200",
				unidade, inicio, fim));
		lista.addAll(detalhesGenericos(
				"SLA medio exames (BAM-resultado)",
				"SELECT b.idbam, p.nome, COALESCE(r.setor, ''), TO_CHAR(pr.data_exame, 'DD/MM/YYYY'), "
						+ "ROUND(EXTRACT(EPOCH FROM ((pr.data_exame + CAST(pr.hr AS TIME)) - (CAST(b.data AS DATE) + CAST(b.hora AS TIME)))) / 60)::TEXT || ' min', "
						+ "COALESCE(pr.descricao, '') "
						+ "FROM (SELECT lab.*, CASE "
						+ "WHEN lab.dt ~ '^\\d{4}/\\d{2}/\\d{2}$' THEN TO_DATE(lab.dt, 'YYYY/MM/DD') "
						+ "WHEN lab.dt ~ '^\\d{2}/\\d{2}/\\d{4}$' THEN TO_DATE(lab.dt, 'DD/MM/YYYY') "
						+ "ELSE NULL END AS data_exame FROM pedidoresultadolaboratorio lab) pr "
						+ "JOIN bam b ON b.idbam = pr.bam JOIN pacientes p ON b.paciente_idpaciente = p.idpaciente "
						+ "LEFT JOIN registro r ON b.registro_idregistro = r.idregistro "
						+ "WHERE pr.unidade = :unidade AND pr.data_exame BETWEEN :inicio AND :fim "
						+ "AND pr.hr ~ '^\\d{2}:\\d{2}$' AND b.hora ~ '^\\d{2}:\\d{2}$' "
						+ "ORDER BY pr.data_exame DESC, pr.hr DESC LIMIT 200",
				unidade, inicio, fim));
		lista.addAll(detalhesGenericos(
				"Exames pendentes",
				"SELECT b.idbam, p.nome, COALESCE(r.setor, ''), TO_CHAR(CAST(b.data AS DATE), 'DD/MM/YYYY'), "
						+ "COALESCE(b.hora, ''), 'Laboratorio solicitado sem resultado' "
						+ "FROM bam b JOIN registro r ON b.registro_idregistro = r.idregistro JOIN pacientes p ON b.paciente_idpaciente = p.idpaciente "
						+ "WHERE b.unidade = :unidade AND CAST(b.data AS DATE) BETWEEN :inicio AND :fim AND r.temlabo = true AND COALESCE(r.resultadolabo, false) = false "
						+ "ORDER BY CAST(b.data AS DATE) DESC, b.hora DESC",
				unidade, inicio, fim));
		return lista;
	}

	private List<BiExecutivoDto.DetalheIndicador> detalhesGenericos(String indicador, String sql, String unidade,
			LocalDate inicio, LocalDate fim) {
		List<BiExecutivoDto.DetalheIndicador> lista = new java.util.ArrayList<>();
		try {
			Query query = entityManager.createNativeQuery(sql);
			aplicarParametros(query, sql, unidade, inicio, fim);
			@SuppressWarnings("unchecked")
			List<Object[]> rows = query.getResultList();
			for (Object[] row : rows) {
				lista.add(new BiExecutivoDto.DetalheIndicador(indicador, texto(row[0]), texto(row[1]), texto(row[2]),
						texto(row[3]), texto(row[4]), texto(row[5])));
			}
		} catch (Exception e) {
			return lista;
		}
		return lista;
	}

	private void aplicarParametros(Query query, String sql, String unidade, LocalDate inicio, LocalDate fim) {
		if (sql.contains(":unidade")) {
			query.setParameter("unidade", unidade);
		}
		if (sql.contains(":inicio")) {
			query.setParameter("inicio", inicio);
		}
		if (sql.contains(":fim")) {
			query.setParameter("fim", fim);
		}
	}

	private BigDecimal numero(Object valor) {
		if (valor == null) {
			return null;
		}
		if (valor instanceof BigDecimal) {
			return (BigDecimal) valor;
		}
		if (valor instanceof Number) {
			return BigDecimal.valueOf(((Number) valor).doubleValue());
		}
		return new BigDecimal(String.valueOf(valor));
	}

	private String texto(Object valor) {
		return valor == null ? "" : String.valueOf(valor);
	}

	private BigDecimal porcentagem(BigDecimal parte, BigDecimal total) {
		if (parte == null || total == null || total.compareTo(BigDecimal.ZERO) == 0) {
			return null;
		}
		return parte.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
	}

	private BigDecimal dividir(BigDecimal parte, BigDecimal total) {
		if (parte == null || total == null || total.compareTo(BigDecimal.ZERO) == 0) {
			return null;
		}
		return parte.divide(total, 2, RoundingMode.HALF_UP);
	}

	private String statusMaiorMelhor(BigDecimal valor, int atencao, int critico) {
		if (valor == null) return "indisponivel";
		if (valor.compareTo(BigDecimal.valueOf(critico)) >= 0) return "critico";
		if (valor.compareTo(BigDecimal.valueOf(atencao)) >= 0) return "atencao";
		return "bom";
	}

	private String statusMenorMelhor(BigDecimal valor, int atencao, int critico) {
		if (valor == null) return "indisponivel";
		if (valor.compareTo(BigDecimal.valueOf(critico)) >= 0) return "critico";
		if (valor.compareTo(BigDecimal.valueOf(atencao)) >= 0) return "atencao";
		return "bom";
	}

	private String detalhe(BigDecimal a, String meio, BigDecimal b, String fim) {
		return (a == null ? "-" : a.setScale(0, RoundingMode.HALF_UP)) + " " + meio + " "
				+ (b == null ? "-" : b.setScale(0, RoundingMode.HALF_UP)) + " " + fim;
	}

	private void montarAlertas(BiExecutivoDto painel) {
		for (BiExecutivoDto.Indicador indicador : painel.getIndicadores()) {
			if ("critico".equals(indicador.getStatus())) {
				painel.getAlertas().add(new BiExecutivoDto.Alerta("critico", indicador.getTitulo(), indicador.getDetalhe()));
			} else if ("atencao".equals(indicador.getStatus())) {
				painel.getAlertas().add(new BiExecutivoDto.Alerta("atencao", indicador.getTitulo(), indicador.getDetalhe()));
			}
		}
		if (painel.getAlertas().isEmpty()) {
			painel.getAlertas().add(new BiExecutivoDto.Alerta("bom", "Sem alertas criticos",
					"Os indicadores disponiveis estao dentro das faixas configuradas."));
		}
	}
}

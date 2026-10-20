package br.com.iv.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import br.com.iv.dto.AtendMedicoDTO;
import br.com.iv.dto.PacienteDto;
import br.com.iv.dto.PacienteResultadoExameDto;
import br.com.iv.dto.PacientesAtendimentoDto;
import br.com.iv.dto.PacientesBamDto;
import br.com.iv.dto.PacientesNirDto;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PacientesRepository implements PanacheRepository<Pacientes> {
	@Inject
	EntityManager entityManager;

	static Date toLegacyDate(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof LocalDate localDate) {
			return java.sql.Date.valueOf(localDate);
		}
		if (value instanceof Date date) {
			return date;
		}
		throw new IllegalArgumentException("Tipo de data nao suportado: " + value.getClass().getName());
	}

	public List<Pacientes> filtrarPacientes(String nome) {
		return find("nome", nome).list();
	}

	public Pacientes filtrarPorUnicoPacienteId(int query) {
		return find("idpaciente = ?1", query).firstResult();
	}

	public List<Pacientes> filtarPorPaciente(String query) {
		String n_nome = query.replaceAll("\\s", "");
		return find("replace(nome, ' ', '') like ?1", n_nome + "%").page(Page.ofSize(100)).list();
	}

	@SuppressWarnings("unchecked")
	public List<Pacientes> filtrarPacientesComBamRecente(String nome, String unidade) {
		String nomeCompacto = nome == null ? "" : nome.replaceAll("\\s", "").toUpperCase();
		String sql = """
				SELECT DISTINCT p.*
				FROM pacientes p
				JOIN bam b ON b.paciente_idpaciente = p.idpaciente
				WHERE replace(upper(p.nome), ' ', '') LIKE :nome
				  AND upper(trim(b.unidade)) = :unidade
				  AND cast(b.data as date) BETWEEN (CURRENT_DATE - INTERVAL '5 days') AND CURRENT_DATE
				ORDER BY p.nome
				LIMIT 100
				""";
		return entityManager.createNativeQuery(sql, Pacientes.class)
				.setParameter("nome", nomeCompacto + "%")
				.setParameter("unidade", unidade)
				.getResultList();
	}

	/**
	 * Palavras funcionais de pergunta que nunca fazem parte de um nome de paciente.
	 * Comparadas sem acento e em caixa alta.
	 */
	private static final List<String> PALAVRAS_NAO_NOME = List.of(
			"COMO", "ESTA", "ESTAO", "QUEM", "QUAL", "QUAIS", "QUANDO", "ONDE", "PORQUE", "POR", "QUE",
			"QUANTO", "QUANTOS", "QUANTAS", "SITUACAO", "ESTADO", "EVOLUCAO", "DIAGNOSTICO", "CONDUTA",
			"EXAME", "EXAMES", "LEITO", "HOJE", "AGORA", "ONTEM", "FOI", "TEVE", "TEM", "SEU", "SUA",
			"MEU", "MINHA", "SOBRE", "COM", "SEM", "EM", "NO", "NA", "NOS", "NAS", "AO", "AOS", "SE",
			"FAVOR", "DIGA", "FALE", "EXPLIQUE", "INFORME", "INFORMACOES", "DADOS", "RESUMO", "STATUS",
			"PRONTUARIO", "PACIENTE", "INTERNADO", "INTERNADA", "ATENDIMENTO", "GERAR", "GERE", "EMITIR",
			"MONTAR", "FAZER", "CRIAR", "IMPRIMIR", "MOSTRAR", "MOSTRE", "TRAZER", "LISTE", "LISTAR",
			"RESUMIR", "RESUMA", "CONSULTAR", "CONSULTE", "BUSCAR", "BUSQUE");

	private static String semAcentos(String texto) {
		return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	/**
	 * Busca pacientes a partir de uma frase livre (ex.: "como esta o paciente marcelo couto da silva").
	 * Descarta as palavras funcionais da pergunta e nao exige que TODOS os tokens casem: pontua cada
	 * nome pela quantidade de tokens encontrados e devolve os mais aderentes primeiro.
	 */
	@SuppressWarnings("unchecked")
	public List<Pacientes> buscarPorNomeEmFrase(String frase, int limite) {
		if (frase == null || frase.isBlank()) {
			return Collections.emptyList();
		}

		List<String> tokens = new ArrayList<>();
		for (String bruto : frase.trim().toUpperCase().split("\\s+")) {
			String comparavel = semAcentos(bruto).replaceAll("[^A-Z0-9]", "");
			if (comparavel.length() < 3 || PALAVRAS_NAO_NOME.contains(comparavel)) {
				continue;
			}
			// mantem o token cru (sem acentos removidos) para casar tanto "JOSE" quanto "JOSÉ"
			String token = bruto.replaceAll("[^A-Z0-9]", "");
			if (token.length() >= 3 && !tokens.contains(token)) {
				tokens.add(token);
			}
		}
		if (tokens.isEmpty()) {
			return Collections.emptyList();
		}

		String nomeNormalizadoSql = "upper(coalesce(p.nome, ''))";
		StringBuilder score = new StringBuilder();
		for (int i = 0; i < tokens.size(); i++) {
			if (score.length() > 0) {
				score.append(" + ");
			}
			score.append("(case when ").append(nomeNormalizadoSql).append(" like :token").append(i)
					.append(" then 1 else 0 end)");
		}

		int minimoTokens = Math.min(tokens.size(), 2);
		String sql = "SELECT p.* FROM pacientes p WHERE (" + score + ") >= " + minimoTokens
				+ " ORDER BY (" + score + ") DESC, p.nome"
				+ " LIMIT :limite";

		var query = entityManager.createNativeQuery(sql, Pacientes.class)
				.setParameter("limite", Math.max(1, Math.min(limite, 100)));
		for (int i = 0; i < tokens.size(); i++) {
			query.setParameter("token" + i, "%" + tokens.get(i) + "%");
		}

		return query.getResultList();
	}

	@SuppressWarnings("unchecked")
	public List<Pacientes> buscarPorNomeInteligente(String termo, int limite) {
		if (termo == null || termo.isBlank()) {
			return Collections.emptyList();
		}

		List<String> tokens = Arrays.stream(termo.trim().toUpperCase().split("\\s+"))
				.map(t -> t.replaceAll("[^A-Z0-9]", ""))
				.filter(t -> t.length() >= 3)
				.filter(t -> !List.of("DE", "DA", "DO", "DAS", "DOS", "E").contains(t))
				.distinct()
				.toList();

		String compacto = termo.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
		if (tokens.isEmpty() && compacto.length() < 3) {
			return Collections.emptyList();
		}

		String nomeNormalizadoSql = "upper(coalesce(p.nome, ''))";

		StringBuilder todosTokens = new StringBuilder();
		for (int i = 0; i < tokens.size(); i++) {
			if (todosTokens.length() > 0) {
				todosTokens.append(" and ");
			}
			todosTokens.append(nomeNormalizadoSql).append(" like :token").append(i);
		}

		StringBuilder where = new StringBuilder();
		if (compacto.length() >= 3) {
			where.append("replace(").append(nomeNormalizadoSql).append(", ' ', '') like :compacto ");
		}
		if (todosTokens.length() > 0) {
			if (where.length() > 0) {
				where.append(" or ");
			}
			where.append("(").append(todosTokens).append(")");
		}

		String sql = "SELECT p.* FROM pacientes p WHERE " + where
				+ " ORDER BY CASE WHEN replace(" + nomeNormalizadoSql + ", ' ', '') like :compactoOrdem THEN 0 ELSE 1 END, p.nome"
				+ " LIMIT :limite";

		var query = entityManager.createNativeQuery(sql, Pacientes.class)
				.setParameter("compactoOrdem", compacto.length() >= 3 ? compacto + "%" : "%NAO_LOCALIZAR%")
				.setParameter("limite", Math.max(1, Math.min(limite, 100)));

		if (compacto.length() >= 3) {
			query.setParameter("compacto", compacto + "%");
		}
		for (int i = 0; i < tokens.size(); i++) {
			query.setParameter("token" + i, "%" + tokens.get(i) + "%");
		}

		return query.getResultList();
	}
	
	public Pacientes filtarPorPacientecpf(String query) {
		return find("cpf = ?1", query ).firstResult();
	}

	public Pacientes buscarPorCpfNormalizado(String cpf) {
		String digitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
		if (digitos.length() != 11 || digitos.chars().distinct().count() == 1) {
			return null;
		}

		String sql = "SELECT p.* FROM pacientes p "
				+ "WHERE regexp_replace(coalesce(p.cpf, ''), '[^0-9]', '', 'g') = :digitos "
				+ "ORDER BY p.idpaciente LIMIT 1";

		return (Pacientes) entityManager.createNativeQuery(sql, Pacientes.class)
				.setParameter("digitos", digitos)
				.getResultStream()
				.findFirst()
				.orElse(null);
	}

	public Pacientes buscarPorCnsNormalizado(String cns) {
		String digitos = cns == null ? "" : cns.replaceAll("\\D", "");
		if (digitos.length() != 15 || digitos.chars().distinct().count() == 1) {
			return null;
		}

		String sql = "SELECT p.* FROM pacientes p "
				+ "WHERE regexp_replace(coalesce(p.cns, ''), '[^0-9]', '', 'g') = :digitos "
				+ "ORDER BY p.idpaciente LIMIT 1";

		return (Pacientes) entityManager.createNativeQuery(sql, Pacientes.class)
				.setParameter("digitos", digitos)
				.getResultStream()
				.findFirst()
				.orElse(null);
	}

	/**
	 * Existe cadastro deste CPF marcado como OBITO?
	 *
	 * O campo cpf da tabela e texto livre e convive com os dois formatos que a
	 * recepcao ja gravou ao longo do tempo (mascarado e so digitos), por isso a
	 * comparacao normaliza a coluna em vez de confiar num deles.
	 */
	public boolean existeObitoPorCpf(String cpf) {
		String digitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
		if (digitos.length() != 11) {
			return false;
		}

		String sql = "SELECT COUNT(*) FROM pacientes p "
				+ " WHERE upper(coalesce(p.pacienteespecial, '')) = 'OBITO' "
				+ " AND regexp_replace(coalesce(p.cpf, ''), '[^0-9]', '', 'g') = :digitos";

		Number total = (Number) entityManager.createNativeQuery(sql)
				.setParameter("digitos", digitos)
				.getSingleResult();

		return total != null && total.longValue() > 0;
	}

	@SuppressWarnings("unchecked")
	public List<Pacientes> filtarPorPacienteRn(String query) {
		String sql = "";
		String n_nome = query.replaceAll("\\s", "");

		if (n_nome.equals("")) {
			sql = "SELECT p.*  FROM pacientes  where s.nome = 'SOU GRATO A TI ABA'";
			return getEntityManager().createNativeQuery(sql, Pacientes.class).getResultList();
		}
		String setoresRnSql = "regexp_replace(translate(upper(coalesce(s.setor, '')), "
				+ "'\u00c1\u00c0\u00c2\u00c3\u00c4\u00c9\u00c8\u00ca\u00cb\u00cd\u00cc\u00ce\u00cf\u00d3\u00d2\u00d4\u00d5\u00d6\u00da\u00d9\u00db\u00dc\u00c7', "
				+ "'AAAAAEEEEIIIIOOOOOUUUUC'), '[^A-Z0-9]', '', 'g') like any (array['MATERNIDADE%', 'PREPARTO%', 'BERCARIO%'])";

		if ((n_nome.equals("todos")) || (n_nome.equals("TODOS"))) {
			sql = "SELECT p.*  FROM pacientes p LEFT JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
					+ "LEFT JOIN novointernado n ON n.nbam = b.idbam "
					+ "LEFT JOIN setorcenso s on s.idsetorcenso = n.setor_idsetorcenso "
					+ "WHERE " + setoresRnSql + " and n.alta = false and SUBSTRING (p.nome, 1, 2) != 'RN' order by p.nome";
		} else {
			sql = "SELECT p.*  FROM pacientes p LEFT JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
					+ "LEFT JOIN novointernado n ON n.nbam = b.idbam "
					+ "LEFT JOIN setorcenso s on s.idsetorcenso = n.setor_idsetorcenso "
					+ "WHERE  SUBSTRING (p.nome, 1, 2) != 'RN' and " + setoresRnSql + " and n.alta = false and replace(p.nome, ' ', '') like  replace('"
					+ n_nome + "%', ' ', '')  order by p.nome";
		}
		return getEntityManager().createNativeQuery(sql, Pacientes.class).getResultList();

		// return find("replace(nome, ' ', '') like ?1", n_nome +
		// "%").page(Page.ofSize(10)).list();
	}

	@SuppressWarnings("unchecked")
	public List<Pacientes> filtarPorPacienteRnVerificar(String query) {
		String sql = "";
		String n_nome = query.replaceAll("\\s", "");

		sql = "SELECT p.*  FROM pacientes p LEFT JOIN bam b ON b.paciente_idpaciente = p.idpaciente LEFT JOIN novointernado n ON n.nbam = b.idbam "
				+ "	 where n.alta = false and replace(p.mae, ' ', '') =  replace('" + n_nome
				+ "', ' ','') order by p.nome";

		return getEntityManager().createNativeQuery(sql, Pacientes.class).getResultList();

		// return find("replace(nome, ' ', '') like ?1", n_nome +
		// "%").page(Page.ofSize(10)).list();
	}

	public List<Pacientes> filtrarPorPacienteNir(String query) {
		String n_nome = query.replaceAll("\\s", "");
		return find("replace(nome, ' ', '') like ?1 AND prontuario = '0'", n_nome + "%").page(Page.ofSize(10)).list();
	}

	public List<Pacientes> filtarPorPacienteEMae(String query, String query2) {
		String n_nome = query.replaceAll("\\s", "");
		String n_mae = query2.replaceAll("\\s", "");
		// return find("nome like ?1 and mae like ?2", query + "%", query2 +
		// "%").list();
		return find("replace(nome, ' ', '') like ?1 and replace(mae, ' ', '') like ?2", n_nome + "%", n_mae + "%")
				.page(Page.ofSize(18)).list();
	}

	public List<Pacientes> filtrarPorMae(String query) {
		String n_mae = query.replaceAll("\\s", "");
		return find("replace(mae, ' ', '') like ?1", n_mae + "%").page(Page.ofSize(18)).list();
	}

	public List<Pacientes> xlistarPacientesNome(String query) {
		String n_nome = query.replaceAll("\\s", "");

		// return find("nome like ?1 and mae like ?2", query + "%", query2 +
		// "%").list();
		return find("replace(nome, ' ', '') like ?1", n_nome + "%").page(Page.ofSize(18)).list();
	}

	public List<Pacientes> filtarPorCPF(String query) {
		return find("cpf = ?1", query).list();
	}

	public List<Pacientes> filtarPorControle(String query) {
		return find("tempcod = ?1", query).list();
	}

	@Transactional
	public void fecharTodosBamAbertoDoPaciente(int idPaciente) {

	    String sql = "";

	    // 1. Fechar todos os tratamentos do paciente
	    sql = "UPDATE tratamento t " +
	          "SET tratado = 1 " +
	          "FROM bam b " +
	          "WHERE b.tratamento_idtratamento = t.idtratamento " +
	          "AND t.tratado = 0 " +
	          "AND b.paciente_idpaciente = :id_do_paciente";

	    entityManager.createNativeQuery(sql)
	        .setParameter("id_do_paciente", idPaciente)
	        .executeUpdate();

	    // 2. Pegar o último tratamento (APENAS 1 coluna)
	    sql = "select tratamento_idtratamento " +
	          "from bam " +
	          "where paciente_idpaciente = :id_do_paciente " +
	          "order by idbam desc limit 1";

		    @SuppressWarnings("unchecked")
		    java.util.List<Object> resultList = entityManager
			    .createNativeQuery(sql)
			    .setParameter("id_do_paciente", idPaciente)
			    .getResultList();

		    if (resultList == null || resultList.isEmpty()) {
			// nenhum tratamento encontrado para esse paciente
			return;
		    }

		    Integer tratamentoId = ((Number) resultList.get(0)).intValue();

	    // 3. Reabrir o último tratamento (SEM JOIN)
	    sql = "UPDATE tratamento " +
	          "SET tratado = 0 " +
	          "WHERE idtratamento = :id_do_tratamento";

	    entityManager.createNativeQuery(sql)
	        .setParameter("id_do_tratamento", tratamentoId)
	        .executeUpdate();
	}

	// Filtro de nascimento válido (YYYY/MM/DD) ciente do calendário: além do formato,
	// rejeita dias inexistentes (ex.: 2006/11/31, 2007/02/29 em ano não bissexto) que
	// fariam o to_date() do Postgres estourar e abortar o UPDATE do batch inteiro.
	// Ramos: meses de 31 dias | meses de 30 dias | fevereiro 01-28 | 29/02 em ano bissexto.
	private static String nascimentoValidoSql(String coluna) {
		return coluna + " ~ '^(\\d{4}/((0[13578]|1[02])/(0[1-9]|[12]\\d|3[01])"
				+ "|(0[469]|11)/(0[1-9]|[12]\\d|30)"
				+ "|02/(0[1-9]|1\\d|2[0-8]))"
				+ "|(\\d{2}(0[48]|[2468][048]|[13579][26])|([02468][048]|[13579][26])00)/02/29)$' "
				+ "AND " + coluna + " NOT LIKE '0000/%'";
	}

	// Atualizar idade através do nome do paciente
	// REQUIRES_NEW garante que cada tentativa de retry executa em transação independente.
	// ORDER BY idpaciente + Collections.sort() garantem que os locks sejam
	// sempre adquiridos na mesma ordem, prevenindo deadlocks entre transações
	// concorrentes com padrões LIKE sobrepostos.
	@Transactional(Transactional.TxType.REQUIRES_NEW)
	public void alterarAIdade(String nomePaciente) {
		String nascimentoValidoSql = nascimentoValidoSql("nascimento");

		// 1) Seleciona os ids correspondentes sem FOR UPDATE para evitar locks longos
		String selectIds = "SELECT idpaciente FROM pacientes "
				+ "WHERE nome LIKE :nome_do_paciente "
				+ "AND " + nascimentoValidoSql + " "
				+ "ORDER BY idpaciente";

		@SuppressWarnings("unchecked")
		List<Object> idResults = entityManager.createNativeQuery(selectIds)
				.setParameter("nome_do_paciente", nomePaciente + '%')
				.getResultList();

		if (idResults == null || idResults.isEmpty()) {
			return;
		}

		// Convert to Integer ids
		List<Integer> ids = new ArrayList<>();
		for (Object o : idResults) {
			if (o instanceof Number) {
				ids.add(((Number) o).intValue());
			}
		}

		// Ordena os IDs para garantir locks sempre na mesma ordem entre transações concorrentes
		Collections.sort(ids);

		// 2) Atualiza em batches para evitar queries muito grandes e reduzir chance de timeout
		final int BATCH = 500;
		for (int i = 0; i < ids.size(); i += BATCH) {
			int end = Math.min(i + BATCH, ids.size());
			List<Integer> batch = ids.subList(i, end);

			// Construir lista de ids segura (são inteiros vindos do DB)
			StringBuilder inBuilder = new StringBuilder();
			for (int j = 0; j < batch.size(); j++) {
				if (j > 0) inBuilder.append(',');
				inBuilder.append(batch.get(j));
			}

			String updateSql = "UPDATE pacientes SET idade = DATE_PART('YEAR', age(CURRENT_DATE, to_date(nascimento, 'YYYY/MM/DD'))) "
					+ "WHERE idpaciente IN (" + inBuilder.toString() + ") "
					+ "AND " + nascimentoValidoSql;

			entityManager.createNativeQuery(updateSql).executeUpdate();
		}
	}

	// Atualizar prontuarioatravés do id do paciente
	public void alterarProntuario(int idpaciente, String prontuario) {
		String sql = "UPDATE pacientes set prontuario = :prontuario  where idpaciente = :id ";
		entityManager.createNativeQuery(sql).setParameter("id", idpaciente).setParameter("prontuario", prontuario)
				.executeUpdate();
	}

	// Atualizar idade através do dia atual de passagem na triagem
	// REQUIRES_NEW garante que cada tentativa de retry executa em transação independente.
	// ORDER BY idpaciente + Collections.sort() garantem locks sempre na mesma ordem.
	@Transactional(Transactional.TxType.REQUIRES_NEW)
	public void alterarAIdadeNaTriagem(String unidade) {
		String nascimentoValidoSql = nascimentoValidoSql("p.nascimento");

		String selectIds = "SELECT DISTINCT p.idpaciente FROM pacientes p "
				+ "JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "WHERE a.avaliado = 0 AND r.eletivo = false "
				+ "AND " + nascimentoValidoSql + " "
				+ "AND to_date(a.data, 'YYYY/MM/DD') = CURRENT_DATE "
				+ "AND r.setor IN (select distinct registro.setor from setoresunidade "
				+ "inner join registro on registro.setor = setoresunidade.setor "
				+ "where setoresunidade.triar = true and setoresunidade.siglaunidade = :unidade) "
				+ "AND b.unidade = :unidade "
				+ "ORDER BY p.idpaciente";

		@SuppressWarnings("unchecked")
		List<Object> idResults = entityManager.createNativeQuery(selectIds)
				.setParameter("unidade", unidade)
				.getResultList();

		if (idResults == null || idResults.isEmpty()) {
			System.out.println("Não há pacientes para triagem...");
			return;
		}

		// Convert to Integer ids
		List<Integer> ids = new ArrayList<>();
		for (Object o : idResults) {
			if (o instanceof Number) {
				ids.add(((Number) o).intValue());
			}
		}

		// Ordena os IDs para garantir locks sempre na mesma ordem entre transações concorrentes
		Collections.sort(ids);

		// Atualiza em batches
		final int BATCH = 500;
		for (int i = 0; i < ids.size(); i += BATCH) {
			int end = Math.min(i + BATCH, ids.size());
			List<Integer> batch = ids.subList(i, end);

			StringBuilder inBuilder = new StringBuilder();
			for (int j = 0; j < batch.size(); j++) {
				if (j > 0) inBuilder.append(',');
				inBuilder.append(batch.get(j));
			}

			String updateSql = "UPDATE pacientes SET idade = DATE_PART('YEAR', age(CURRENT_DATE, to_date(nascimento, 'YYYY/MM/DD'))) "
					+ "WHERE idpaciente IN (" + inBuilder.toString() + ") "
					+ "AND " + nascimentoValidoSql("nascimento");

			entityManager.createNativeQuery(updateSql).executeUpdate();
		}
	}

	@SuppressWarnings("unchecked")
	@Transactional
	public List<Pacientes> locaisDeTriagem(String unidade) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		String subConsulta = "(select distinct registro.setor from setoresunidade inner join registro on registro.setor = setoresunidade.setor where setoresunidade.triar = true and setoresunidade.siglaunidade = '"
				+ unidade + "')";

		String sql = "SELECT DISTINCT b.idbam as ibam, p.*, a.*, b.*, r.* FROM pacientes p "
				+ "JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "WHERE a.avaliado = 0 AND r.eletivo = false AND to_date(a.data, 'YYYY/MM/DD') = '" + datas
				+ "' AND r.setor IN " + subConsulta + " AND b.unidade = '" + unidade
				+ "' ORDER BY b.idbam, b.prioridade DESC, b.hora";

		return getEntityManager().createNativeQuery(sql, Pacientes.class).getResultList();
	}

	@SuppressWarnings("unchecked")
	@Transactional
	public List<Pacientes> localizarPeloBam(int bam) {

		// SELECT p.* (nao "SELECT *"): pacientes e bam tem colunas com o mesmo nome (ex.: "data"),
		// e "SELECT *" nesse join faz o Hibernate falhar na auto-descoberta por alias duplicado.
		String sql = "SELECT p.*  FROM pacientes p JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "WHERE b.idbam  = " + bam;

		return getEntityManager().createNativeQuery(sql, Pacientes.class).getResultList();
	}

	// Busca aditiva para o RAG2 Prontuário: localizar paciente pelo número de prontuário.
	public List<Pacientes> findByProntuario(String prontuario) {
		if (prontuario == null || prontuario.isBlank() || prontuario.trim().equals("0")) {
			return Collections.emptyList();
		}
		return find("prontuario = ?1", prontuario.trim()).page(Page.ofSize(50)).list();
	}

	@SuppressWarnings("unchecked")
	@Transactional
	public List<PacienteDto> locaisDeTriagem2(String unidade, String ksetor) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		String subConsulta = "(select distinct registro.setor from setoresunidade inner join registro on registro.setor = setoresunidade.setor where setoresunidade.triar = true and setoresunidade.siglaunidade = '"
				+ unidade + "')";

		String sql = "SELECT DISTINCT p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.mae, p.pai, p.nascimento, a.idavaliacao, a.data, a.avaliado, "
				+ "b.idbam, b.prioridade, a.hora, r.idregistro, r.eletivo, r.setor, t.idtratamento, t.tratado  FROM pacientes p "
				+ "JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "JOIN tratamento t ON b.tratamento_idtratamento = t.idtratamento "
				+ "WHERE a.avaliado = 0 AND r.eletivo = false AND to_date(a.data, 'YYYY/MM/DD') = '" + datas
				+ "' AND r.setor IN " + subConsulta + " AND b.unidade = '" + unidade + "' AND r.setor like '" + ksetor
				+ "%' AND t.tratado = 0  ORDER BY r.setor, b.idbam, b.prioridade DESC, a.hora";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();
		List<PacienteDto> pacientes = new ArrayList<>();

		for (Object[] result : results) {
			PacienteDto dto = new PacienteDto();
			dto.setIdPaciente((int) result[0]);
			dto.setNome((String) result[1]);
			dto.setIdade((Integer) result[2]);
			dto.setPacienteespecial((String) result[3]);
			dto.setMae((String) result[4]);
			dto.setPai((String) result[5]);
			dto.setNascimento((String) result[6]);
			dto.setIdAvaliacao((int) result[7]);
			dto.setDataav((String) result[8]);
			dto.setAvaliado((Integer) result[9]);
			dto.setIdBam((int) result[10]);
			dto.setPrioridade((int) result[11]);
			dto.setHora((String) result[12]);
			dto.setIdRegistro((Integer) result[13]);
			dto.setEletivo((boolean) result[14]);
			dto.setSetor((String) result[15]);
			dto.setIdTratamento((int) result[16]);
			dto.setTratado((Integer) result[17]);
			pacientes.add(dto);
		}

		return pacientes;
	}

	// -------------------------------- NIR --------------------------------------
	@SuppressWarnings("unchecked")
	public List<PacientesNirDto> filtragemParaNir(String nome, String unidade, String tipo) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		List<PacientesNirDto> pacientesNir = new ArrayList<>();

		if (nome.equals("NADA"))
			nome = "%";

		if (tipo.equals("novo")) {
			/*
			 * String sql = String n_nome = query.replaceAll("\\s", ""); if
			 * (n_nome.equals("")) { sql =
			 * "SELECT p.* FROM pacientes p WHERE p.nome = 'SOU GRATO A TI ABA'"; return
			 * getEntityManager().createNativeQuery(sql, Pacientes.class).getResultList(); }
			 * if (n_nome.equals("todos") || n_nome.equals("TODOS")) { sql =
			 * "SELECT p.* FROM pacientes p LEFT JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
			 * + "LEFT JOIN novointernado n ON n.nbam = b.idbam " +
			 * "LEFT JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso " +
			 * "WHERE (s.setor = 'MATERNIDADE' OR s.setor = 'PRÉ PARTO' OR s.setor = 'BERÇÁRIO') AND n.alta = false AND SUBSTRING(p.nome, 1, 2) != 'RN' ORDER BY p.nome"
			 * ; } else { sql =
			 * "SELECT p.* FROM pacientes p LEFT JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
			 * + "LEFT JOIN novointernado n ON n.nbam = b.idbam " +
			 * "LEFT JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso " +
			 * "WHERE SUBSTRING(p.nome, 1, 2) != 'RN' AND (s.setor = 'MATERNIDADE' OR s.setor = 'PRÉ PARTO' OR s.setor = 'BERÇÁRIO') AND n.alta = false AND REPLACE(p.nome, ' ', '') LIKE REPLACE('"
			 * + n_nome + "%', ' ', '') ORDER BY p.nome"; +
			 * " LEFT JOIN  tratamento t ON t.idtratamento = b.tratamento_idtratamento " +
			 * " LEFT JOIN  laudoautorizacao l ON l.bam = b.idbam " +
			 * " LEFT JOIN internacao i ON i.bam = cast(b.idbam as text)  WHERE  p.nome LIKE '"
			 * + nome + "%' AND b.unidade = '" + unidade + "'" +
			 * " AND p.pacienteespecial != 'OBITO' AND b.transferido = false  AND i.bam is null"
			 * +
			 * " AND (((t.tipo_alta != 'obs' AND t.tipo_alta = 'internacao') AND t.tratado = 1 ) ) "
			 * +
			 * " GROUP BY p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,  t.tipo_alta, b.data,  l.bam"
			 * + " ORDER BY   p.prontuario, cast(b.data as date) desc, p.nome, l.bam ";
			 */
			// Uma linha por paciente: o pedido de internação do BAM mais recente.
			// Os demais BAMs com pedido e um BAM posterior ao pedido vão como aviso,
			// para o NIR conferir pedidos feitos no BAM errado (nada é ocultado).
			String sql = "WITH pedidos AS ( "
					+ " SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam, cast(b.data as date) AS data_bam, "
					+ " regexp_replace(coalesce(p.cpf, ''), '\\D', '', 'g') AS cpf_num, p.nascimento AS nasc, "
					+ " CASE WHEN l.idlaudo IS NOT NULL THEN 'internacao' ELSE t.tipo_alta END AS tipo_alta, l.bam as bamlaudo, "
					+ " ROW_NUMBER() OVER (PARTITION BY p.idpaciente ORDER BY b.idbam DESC, l.idlaudo DESC NULLS LAST) AS ordem "
					+ " FROM pacientes p LEFT JOIN bam b ON p.idpaciente = b.paciente_idpaciente "
					+ " LEFT JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento "
					+ " LEFT JOIN laudoautorizacao l ON l.bam = b.idbam "
					+ " LEFT JOIN internacao i ON i.bam = cast(b.idbam as text) "
					+ " WHERE p.nome LIKE :nome AND b.unidade = :unidade AND p.pacienteespecial != 'OBITO' "
					+ " AND b.transferido = false " + " AND i.bam is null"
					+ " AND (l.idlaudo IS NOT NULL OR (t.tipo_alta = 'internacao' AND t.tratado = 1)) "
					+ " AND cast(b.data as date) BETWEEN (cast(CURRENT_DATE as date) - INTERVAL '5 days') AND cast(CURRENT_DATE as date) "
					+ " ) "
					+ " SELECT * FROM ( "
					+ " SELECT pd.idpaciente, pd.nome, pd.idade, pd.prontuario, pd.pacienteespecial, pd.idbam, pd.data_bam, pd.tipo_alta, pd.bamlaudo, "
					+ " (SELECT string_agg(cast(x.idbam as text), ', ' ORDER BY x.idbam DESC) "
					+ "    FROM (SELECT DISTINCT o.idbam FROM pedidos o WHERE o.idpaciente = pd.idpaciente AND o.idbam <> pd.idbam) x) AS outrosbamspedido, "
					+ " (SELECT MAX(b2.idbam) FROM bam b2 "
					+ "    WHERE b2.paciente_idpaciente = pd.idpaciente AND b2.unidade = :unidade AND b2.idbam > pd.idbam) AS bamposterior, "
					// Internação aberta recente em outro BAM: o pedido pode ser duplicado.
					// Também procura pelo CPF, porque há pacientes com cadastro duplicado.
					+ " (SELECT MAX(cast(i2.bam as integer)) FROM internacao i2 "
					+ "    JOIN bam b3 ON b3.idbam = cast(i2.bam as integer) "
					+ "    JOIN pacientes p3 ON p3.idpaciente = b3.paciente_idpaciente "
					+ "    WHERE (b3.paciente_idpaciente = pd.idpaciente "
					+ "      OR (length(pd.cpf_num) = 11 AND regexp_replace(coalesce(p3.cpf, ''), '\\D', '', 'g') = pd.cpf_num) "
					// RN e cadastros sem CPF: o mesmo prontuário também identifica a pessoa.
					// Exige formato válido e mesmo nascimento: há prontuários-lixo ('-', '10') repetidos.
					+ "      OR (pd.prontuario ~ '^[0-9]+-[0-9]+$' AND p3.prontuario = pd.prontuario AND p3.nascimento = pd.nasc)) "
					+ "    AND b3.unidade = :unidade "
					+ "    AND cast(i2.datasaida as date) IS NULL AND i2.motivodaalta = '-' "
					+ "    AND cast(i2.datainternacao as date) >= CURRENT_DATE - 30) AS baminternado "
					+ " FROM pedidos pd WHERE pd.ordem = 1 "
					+ " ) r "
					// Internado num BAM mais novo que o pedido: o pedido já foi atendido (duplicado).
					+ " WHERE coalesce(r.baminternado, 0) < r.idbam "
					+ " ORDER BY r.prontuario, r.data_bam desc, r.nome, r.bamlaudo ";

			List<Object[]> results = getEntityManager().createNativeQuery(sql)
					.setParameter("nome", nome + "%")
					.setParameter("unidade", unidade)
					.getResultList();

			for (Object[] result : results) {
				PacientesNirDto dto = new PacientesNirDto();

				dto.setIdpaciente((Integer) result[0]);
				dto.setNome((String) result[1]);
				dto.setIdade((Integer) result[2]);
				dto.setProntuario((String) result[3]);
				dto.setPacienteespecial((String) result[4]);
				dto.setBam((Integer) result[5]);
				dto.setData(toLegacyDate(result[6]));
				dto.setTipo_alta((String) result[7]);
				dto.setBamlaudo(result[8] != null ? (Integer) result[8] : 0);
				dto.setOutrosbamspedido(result[9] != null ? result[9].toString() : "");
				dto.setBamposterior(result[10] != null ? ((Number) result[10]).intValue() : 0);
				dto.setBaminternado(result[11] != null ? ((Number) result[11]).intValue() : 0);
				// dto.setDatasaida((Date) result[6]);
				// dto.setAlta((Boolean) result[7]);
				// dto.setDatainternacao((Date) result[8]);
				// dto.setDiainternacao((Date) result[9]);
				// dto.setSetor((String) result[10]);
				// dto.setLocal((String) result[11]);

				pacientesNir.add(dto);

			}
		}

		if (tipo.equals("novoint")) {

			String sql = "SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, MAX(b.idbam) AS idbam,  t.tipo_alta, casT(i.datainternacao as date) "
					+ " FROM pacientes p LEFT JOIN bam b ON p.idpaciente = b.paciente_idpaciente "
					+ " LEFT JOIN internacao i ON cast(i.bam as integer) = b.idbam "
					+ " LEFT JOIN  tratamento t ON t.idtratamento = b.tratamento_idtratamento "
					+ " WHERE  p.nome LIKE '" + nome + "%' AND b.unidade = '" + unidade + "'"
					+ " AND p.pacienteespecial != 'OBITO' AND b.transferido = false "
					+ " AND   cast(i.datasaida as date)  is null   AND t.tipo_alta = 'internacao' AND p.prontuario != '0' AND i.motivodaalta = '-' "
					+ " GROUP BY p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,  t.tipo_alta, i.datasaida, i.datainternacao"
					+ " ORDER BY p.nome, p.prontuario";

			List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();

			for (Object[] result : results) {
				PacientesNirDto dto = new PacientesNirDto();

				dto.setIdpaciente((Integer) result[0]);
				dto.setNome((String) result[1]);
				dto.setIdade((Integer) result[2]);
				dto.setProntuario((String) result[3]);
				dto.setPacienteespecial((String) result[4]);
				dto.setBam((Integer) result[5]);
				dto.setDatainternacao(toLegacyDate(result[7]));

				// dto.setDatasaida((Date) result[6]);
				// dto.setAlta((Boolean) result[7]);
				// dto.setDatainternacao((Date) result[8]);
				// dto.setDiainternacao((Date) result[9]);
				// dto.setSetor((String) result[10]);
				// dto.setLocal((String) result[11]);

				pacientesNir.add(dto);

			}
		}

		if (tipo.equals("antigo")) {
			/*
			 * String sql =
			 * "SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,   n.alta,  "
			 * +
			 * "n.diainternacao,  s.setor, l.local, t.tipo_alta FROM  pacientes  p, bam  b, novointernado n, localcenso l, "
			 * + "setorcenso s, tratamento t  where   p.nome LIKE '" + nome +
			 * "%' AND  n.unidade = '" + unidade +
			 * "' and t.idtratamento = b.tratamento_idtratamento and n.setor_idsetorcenso = s.idsetorcenso and n.local_idsetorlocalcenso = l.idsetorlocalcenso "
			 * +
			 * "and b.paciente_idpaciente = p.idpaciente and n.pacientes_idpaciente=p.idpaciente   and (n.motivosaida = '-' or n.motivosaida = 'ALTA ADM') "
			 * +
			 * "and  cast(b.idbam as integer) = cast(n.nbam as integer) and n.alta = false "
			 * + "order by  n.setor_idsetorcenso, n.local_idsetorlocalcenso, p.nome";
			 */
			String sql = "select  p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,   n.alta,  "
					+ " n.diainternacao,  s.setor, l.local, t.tipo_alta from pacientes p "
					+ " left join bam b on b.paciente_idpaciente = p.idpaciente "
					+ " left join novointernado n on n.nbam= b.idbam "
					+ " left join tratamento t on  t.idtratamento = b.tratamento_idtratamento "
					+ " left join localcenso l on n.local_idsetorlocalcenso = l.idsetorlocalcenso "
					+ " left join setorcenso s on  n.setor_idsetorcenso = s.idsetorcenso "
					+ " where (n.motivosaida = '-' or n.motivosaida = 'ALTA ADM')  and n.alta = false "
					+ " and  p.nome LIKE '" + nome + "%' " + " and n.unidade  = '" + unidade + "' "
					+ " order by  t.tipo_alta desc,  n.setor_idsetorcenso, n.local_idsetorlocalcenso, p.nome,p.prontuario ";

			List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();

			for (Object[] result : results) {
				PacientesNirDto dto = new PacientesNirDto();
				dto.setIdpaciente((Integer) result[0]);
				dto.setNome((String) result[1]);
				dto.setIdade((Integer) result[2]);
				dto.setProntuario((String) result[3]);
				dto.setPacienteespecial((String) result[4]);
				dto.setBam((Integer) result[5]);
				dto.setAlta((Boolean) result[6]);
				dto.setDiainternacao(toLegacyDate(result[7]));
				dto.setSetor((String) result[8]);
				dto.setLocal((String) result[9]);
				dto.setTipo_alta((String) result[10]);
				pacientesNir.add(dto);
			}
		}
		return pacientesNir;
	}

	@SuppressWarnings("unchecked")
	public List<PacientesNirDto> filtragemParaNirPainel(String unidade) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		List<PacientesNirDto> pacientesNir = new ArrayList<>();

		// Mesmos critérios da aba "Novas internações" do /nir (filtragemParaNir tipo "novo"):
		// laudo de autorização entra mesmo sem tratamento finalizado e a janela é de 5 dias.
		// O painel continua mostrando também os pacientes em observação.
		String sql = "SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, MAX(b.idbam) AS idbam, cast(b.data as date), "
				+ " CASE WHEN l.idlaudo IS NOT NULL THEN 'internacao' ELSE t.tipo_alta END AS tipo_alta, l.bam as bamlaudo "
				+ " FROM pacientes p LEFT JOIN bam b ON p.idpaciente = b.paciente_idpaciente "
				+ " LEFT JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento "
				+ " LEFT JOIN laudoautorizacao l ON l.bam = b.idbam "
				+ " LEFT JOIN internacao i ON i.bam = cast(b.idbam as text) " + " WHERE  b.unidade = :unidade "
				+ " AND p.pacienteespecial != 'OBITO' " + " AND b.transferido = false " + " AND i.bam is null"
				+ " AND (l.idlaudo IS NOT NULL OR ((t.tipo_alta = 'obs' OR t.tipo_alta = 'internacao') AND t.tratado = 1)) "
				+ " AND cast(b.data as date) BETWEEN (cast(CURRENT_DATE as date) - INTERVAL '5 days') AND cast(CURRENT_DATE as date) "
				+ " GROUP BY p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam, t.tipo_alta, b.data, l.idlaudo, l.bam"
				+ " ORDER BY p.prontuario, cast(b.data as date) desc, p.nome, l.bam ";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).setParameter("unidade", unidade).getResultList();

		for (Object[] result : results) {
			PacientesNirDto dto = new PacientesNirDto();

			dto.setIdpaciente((Integer) result[0]);
			dto.setNome((String) result[1]);
			dto.setIdade((Integer) result[2]);
			dto.setProntuario((String) result[3]);
			dto.setPacienteespecial((String) result[4]);
			dto.setBam((Integer) result[5]);
			dto.setData(toLegacyDate(result[6]));
			dto.setTipo_alta((String) result[7]);
			dto.setBamlaudo(result[8] != null ? (Integer) result[8] : 0);
			// dto.setDatasaida((Date) result[6]);
			// dto.setAlta((Boolean) result[7]);
			// dto.setDatainternacao((Date) result[8]);
			// dto.setDiainternacao((Date) result[9]);
			// dto.setSetor((String) result[10]);
			// dto.setLocal((String) result[11]);

			pacientesNir.add(dto);

		}
		return pacientesNir;
	}

	@SuppressWarnings("unchecked")
	public List<PacientesNirDto> filtragemParaNirResturarInternacao(String nome, String unidade, String tipo) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		List<PacientesNirDto> pacientesNir = new ArrayList<>();

		if (nome.equals("NADA"))
			nome = "%";

		if (tipo.equals("internacao")) {
			/*
			 * String sql =
			 * "SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, MAX(b.idbam) AS idbam, cast(b.data as date),  t.tipo_alta "
			 * +
			 * " FROM pacientes p LEFT JOIN bam b ON p.idpaciente = b.paciente_idpaciente "
			 * + " LEFT JOIN  tratamento t ON t.idtratamento = b.tratamento_idtratamento " +
			 * " LEFT JOIN  laudoautorizacao l ON l.bam = b.idbam " +
			 * " LEFT JOIN internacao i ON i.bam = cast(b.idbam as text) " +
			 * " WHERE  p.nome LIKE '" + nome + "%' AND b.unidade = '" + unidade + "'" +
			 * " AND p.pacienteespecial != 'OBITO' AND b.transferido = false " +
			 * " AND i.bam is null" + " AND cast(b.data as date) between  (cast('" + datas +
			 * "' as date) - INTERVAL '10 day' ) and cast('" + datas + "' as date)" +
			 * " AND (((t.tipo_alta = 'obs' OR t.tipo_alta = 'internacao') AND t.tratado = 0 ) ) "
			 * +
			 * " GROUP BY p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,  t.tipo_alta, b.data"
			 * + " ORDER BY   cast(b.data as date) desc, p.nome,  p.prontuario ";
			 */
			String sql = "SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, MAX(b.idbam) AS idbam, cast(b.data as date),  t.tipo_alta, l.bam as bamlaudo, i.datainternacao, "
					+ "	i.datasaida, i.motivodaalta, i.medicodeualta, i.medicoqueinternou  "
					+ " FROM pacientes p LEFT JOIN bam b ON p.idpaciente = b.paciente_idpaciente "
					+ " LEFT JOIN  tratamento t ON t.idtratamento = b.tratamento_idtratamento "
					+ " LEFT JOIN  laudoautorizacao l ON l.bam = b.idbam "
					+ " LEFT JOIN internacao i ON i.bam = cast(b.idbam as text)  WHERE  p.nome LIKE '" + nome
					+ "%' AND b.unidade = '" + unidade
					+ "' and (i.datasaida is not null and (cast(i.datasaida as date) between  (cast('" + datas
					+ "' as date) - INTERVAL '5 day') and cast('" + datas + "' as date))) "
					+ " AND p.pacienteespecial != 'OBITO'   AND i.bam is not null " + " AND LEFT(p.nome, 3) != 'RN '"
					+ " GROUP BY p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,  t.tipo_alta, b.data,  l.bam, i.datainternacao, "
					+ "	    i.datasaida, i.motivodaalta, i.medicodeualta, i.medicoqueinternou"
					+ " ORDER BY  cast(i.datasaida as date), p.nome,  cast(b.data as date) desc,    p.prontuario, l.bam, i.datainternacao, "
					+ "	     i.medicodeualta, i.medicoqueinternou ";

			List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();

			for (Object[] result : results) {
				PacientesNirDto dto = new PacientesNirDto();

				dto.setIdpaciente((Integer) result[0]);
				dto.setNome((String) result[1]);
				dto.setIdade((Integer) result[2]);
				dto.setProntuario((String) result[3]);
				dto.setPacienteespecial((String) result[4]);
				dto.setBam((Integer) result[5]);
				dto.setData(toLegacyDate(result[6]));
				dto.setTipo_alta((String) result[7]);
				dto.setBamlaudo(result[8] != null ? (Integer) result[8] : 0);
				dto.setDatainternacao(toLegacyDate(result[9]));
				dto.setDatasaida(toLegacyDate(result[10]));
				dto.setMotivodaalta((String) result[11]);
				dto.setMedicodeualta((String) result[12]);
				dto.setMedicoqueinternou((String) result[13]);

				pacientesNir.add(dto);

			}
		}

		if (tipo.equals("resumoalta")) {

			String sql = "SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, MAX(b.idbam) AS idbam,   r.dataalta, r.diagfinal"
					+ " FROM pacientes p LEFT JOIN bam b ON p.idpaciente = b.paciente_idpaciente "
					+ " LEFT JOIN resumodealta r ON cast(r.bam as integer) = b.idbam  WHERE  p.nome LIKE '" + nome
					+ "%' AND b.unidade = '" + unidade + "'"
					+ " AND (p.pacienteespecial != 'OBITO' and (p.nome != '' or p.nome != ' ' or p.nome != '   ' or p.nome != '    ') and p.prontuario != '0') and ( r.dataalta is not null and cast(r.dataalta as date) <= CURRENT_DATE) "
					+ " AND COALESCE(upper(r.status), 'ATIVO') <> 'CANCELADO' "
					+ " GROUP BY p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,   r.datainternacao, r.dataalta, r.diagfinal"
					+ " ORDER BY  cast(r.dataalta as date) desc,  p.nome, b.idbam desc";

			List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();

			for (Object[] result : results) {
				PacientesNirDto dto = new PacientesNirDto();

				dto.setIdpaciente((Integer) result[0]);
				dto.setNome((String) result[1]);
				dto.setIdade((Integer) result[2]);
				dto.setProntuario((String) result[3]);
				dto.setPacienteespecial((String) result[4]);
				dto.setBam((Integer) result[5]);
				dto.setDataalta(toLegacyDate(result[6]));
				dto.setDiagfinal((String) result[7]);

				pacientesNir.add(dto);

			}
		}

		if (tipo.equals("censo")) {
			String sql = "SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, "
					+ "n.nbam, n.idninternado, n.diainternacao, n.datadaacao, n.diagnostico "
					+ "FROM novointernado n "
					+ "INNER JOIN pacientes p ON p.idpaciente = n.pacientes_idpaciente "
					+ "WHERE n.alta = true AND n.unidade = :unidade AND UPPER(p.nome) LIKE :nome "
					+ "AND n.datadaacao BETWEEN CURRENT_DATE - INTERVAL '5 day' AND CURRENT_DATE "
					+ "ORDER BY n.datadaacao DESC, p.nome";

			List<Object[]> results = getEntityManager().createNativeQuery(sql)
					.setParameter("unidade", unidade)
					.setParameter("nome", nome.toUpperCase() + "%")
					.getResultList();

			for (Object[] result : results) {
				PacientesNirDto dto = new PacientesNirDto();
				dto.setIdpaciente((Integer) result[0]);
				dto.setNome((String) result[1]);
				dto.setIdade((Integer) result[2]);
				dto.setProntuario((String) result[3]);
				dto.setPacienteespecial((String) result[4]);
				dto.setBam(((Number) result[5]).intValue());
				dto.setNovointernado(((Number) result[6]).intValue());
				dto.setDiainternacao(toLegacyDate(result[7]));
				dto.setDataacao(toLegacyDate(result[8]));
				dto.setDiagnostico((String) result[9]);
				pacientesNir.add(dto);
			}
		}

		if (tipo.equals("antigo")) {
			/*
			 * String sql =
			 * "SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,   n.alta,  "
			 * +
			 * "n.diainternacao,  s.setor, l.local, t.tipo_alta FROM  pacientes  p, bam  b, novointernado n, localcenso l, "
			 * + "setorcenso s, tratamento t  where   p.nome LIKE '" + nome +
			 * "%' AND  n.unidade = '" + unidade +
			 * "' and t.idtratamento = b.tratamento_idtratamento and n.setor_idsetorcenso = s.idsetorcenso and n.local_idsetorlocalcenso = l.idsetorlocalcenso "
			 * +
			 * "and b.paciente_idpaciente = p.idpaciente and n.pacientes_idpaciente=p.idpaciente   and (n.motivosaida = '-' or n.motivosaida = 'ALTA ADM') "
			 * +
			 * "and  cast(b.idbam as integer) = cast(n.nbam as integer) and n.alta = false "
			 * + "order by  n.setor_idsetorcenso, n.local_idsetorlocalcenso, p.nome";
			 */
			String sql = "select  p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,   n.alta,  "
					+ " n.diainternacao,  s.setor, l.local, t.tipo_alta from pacientes p "
					+ " left join bam b on b.paciente_idpaciente = p.idpaciente "
					+ " left join novointernado n on n.nbam= b.idbam "
					+ " left join tratamento t on  t.idtratamento = b.tratamento_idtratamento "
					+ " left join localcenso l on n.local_idsetorlocalcenso = l.idsetorlocalcenso "
					+ " left join setorcenso s on  n.setor_idsetorcenso = s.idsetorcenso "
					+ " where (n.motivosaida = '-' or n.motivosaida = 'ALTA ADM')  and n.alta = false "
					+ " and  p.nome LIKE '" + nome + "%' " + " and n.unidade  = '" + unidade + "' "
					+ " order by  n.setor_idsetorcenso, n.local_idsetorlocalcenso, p.nome ";

			List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();

			for (Object[] result : results) {
				PacientesNirDto dto = new PacientesNirDto();
				dto.setIdpaciente((Integer) result[0]);
				dto.setNome((String) result[1]);
				dto.setIdade((Integer) result[2]);
				dto.setProntuario((String) result[3]);
				dto.setPacienteespecial((String) result[4]);
				dto.setBam((Integer) result[5]);
				dto.setAlta((Boolean) result[6]);
				dto.setDiainternacao(toLegacyDate(result[7]));
				dto.setSetor((String) result[8]);
				dto.setLocal((String) result[9]);
				dto.setTipo_alta((String) result[10]);
				pacientesNir.add(dto);
			}
		}
		return pacientesNir;
	}

	@SuppressWarnings("unchecked")
	public List<PacientesNirDto> filtragemParaNir2(String nome, String unidade, String local) {
		if (nome.equals("NADA"))
			nome = "%";
		String sql = "SELECT DISTINCT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, "
				+ " b.idbam, n.alta, n.diainternacao, s.setor, lo.local, t.tipo_alta "
				+ " FROM novointernado n "
				+ " INNER JOIN bam b ON CAST(b.idbam AS TEXT) = CAST(n.nbam AS TEXT) "
				+ " INNER JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente "
				+ " LEFT JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso "
				+ " LEFT JOIN localcenso lo ON lo.idsetorlocalcenso = n.local_idsetorlocalcenso "
				+ " LEFT JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento "
				+ " WHERE UPPER(p.nome) LIKE UPPER(:nome) AND s.setor = :local "
				+ " AND b.unidade = :unidade AND n.alta = FALSE "
				+ " ORDER BY p.nome, s.setor, lo.local, n.alta";

		List<Object[]> results = getEntityManager().createNativeQuery(sql)
				.setParameter("nome", nome.equals("%") ? nome : nome + "%")
				.setParameter("local", local)
				.setParameter("unidade", unidade)
				.getResultList();
		List<PacientesNirDto> pacientesNir = new ArrayList<>();

		for (Object[] result : results) {
			PacientesNirDto dto = new PacientesNirDto();
			dto.setIdpaciente((Integer) result[0]);
			dto.setNome((String) result[1]);
			dto.setIdade((Integer) result[2]);
			dto.setProntuario((String) result[3]);
			dto.setPacienteespecial((String) result[4]);
			dto.setBam(result[5] == null ? null : ((Number) result[5]).intValue());
			dto.setAlta((Boolean) result[6]);
			// dto.setDatasaida((Date) result[7]);
			// dto.setDatainternacao((Date) result[7]);
			dto.setDiainternacao(toLegacyDate(result[7]));
			dto.setSetor((String) result[8]);
			dto.setLocal((String) result[9]);
			dto.setTipo_alta((String) result[10]);

			pacientesNir.add(dto);
		}

		return pacientesNir;
	}

	@SuppressWarnings("unchecked")
	public List<PacientesNirDto> filtragemParaNirInternado(String nome, String unidade) {
		if (nome.equals("NADA"))
			nome = "%";
		/*
		 * String sql =
		 * "SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,   n.alta,  "
		 * + "n.diainternacao,  s.setor, l.local, t.tipo_alta FROM  pacientes  p " +
		 * " LEFT JOIN bam b ON p.idpaciente = b.paciente_idpaciente " +
		 * " LEFT JOIN  novointernado n ON CAST(b.idbam AS TEXT) = CAST(n.nbam AS TEXT) "
		 * + " LEFT JOIN  setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso " +
		 * " LEFT JOIN  localcenso l ON l.idsetorlocalcenso = n.local_idsetorlocalcenso "
		 * + " LEFT JOIN  tratamento t ON t.idtratamento = b.tratamento_idtratamento " +
		 * "where   p.nome LIKE '" + nome + "%' AND  n.unidade = '" + unidade +
		 * "' and t.idtratamento = b.tratamento_idtratamento and n.setor_idsetorcenso = s.idsetorcenso and n.local_idsetorlocalcenso = l.idsetorlocalcenso "
		 * +
		 * "and b.paciente_idpaciente = p.idpaciente and n.pacientes_idpaciente=p.idpaciente   "
		 * +
		 * "and  cast(b.idbam as integer) = cast(n.nbam as integer) and n.alta = false "
		 * + "order by  n.setor_idsetorcenso, n.local_idsetorlocalcenso, p.nome";
		 */
		String sql = "select  p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, b.idbam,   n.alta,  "
				+ " n.diainternacao,  s.setor, l.local, t.tipo_alta from pacientes p "
				+ " left join bam b on b.paciente_idpaciente = p.idpaciente "
				+ " left join novointernado n on cast(n.nbam as text) = cast(b.idbam as text) "
				+ " left join tratamento t on  t.idtratamento = b.tratamento_idtratamento "
				+ " left join localcenso l on n.local_idsetorlocalcenso = l.idsetorlocalcenso "
				+ " left join setorcenso s on  n.setor_idsetorcenso = s.idsetorcenso "
				+ " where (n.motivosaida = '-' or n.motivosaida = 'ALTA ADM')  and n.alta = false "
				+ " and (p.nome LIKE :nomePrefixo or cast(b.idbam as text) = :identificador "
				+ " or p.prontuario = :identificador) and n.unidade = :unidade "
				+ " order by  n.setor_idsetorcenso, n.local_idsetorlocalcenso, p.nome ";

		var query = getEntityManager().createNativeQuery(sql);
		query.setParameter("nomePrefixo", nome + "%");
		query.setParameter("identificador", nome);
		query.setParameter("unidade", unidade);
		List<Object[]> results = query.getResultList();
		List<PacientesNirDto> pacientesNir = new ArrayList<>();

		for (Object[] result : results) {
			PacientesNirDto dto = new PacientesNirDto();
			dto.setIdpaciente((Integer) result[0]);
			dto.setNome((String) result[1]);
			dto.setIdade((Integer) result[2]);
			dto.setProntuario((String) result[3]);
			dto.setPacienteespecial((String) result[4]);
			dto.setBam(result[5] == null ? null : ((Number) result[5]).intValue());
			dto.setAlta((Boolean) result[6]);
			dto.setDiainternacao(toLegacyDate(result[7]));
			dto.setSetor((String) result[8]);
			dto.setLocal((String) result[9]);
			dto.setTipo_alta((String) result[10]);
			pacientesNir.add(dto);
		}

		return pacientesNir;
	}

	@SuppressWarnings("unchecked")
	public List<PacientesNirDto> filtragemParaNirInternadoTodos(String nome, String unidade) {
		if (nome.equals("NADA"))
			nome = "%";
		String sql = "select  distinct on (b.idbam) b.idbam, p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial,    n.alta,  "
				+ " n.diainternacao,  s.setor, l.local, t.tipo_alta from pacientes p "
				+ " left join bam b on b.paciente_idpaciente = p.idpaciente "
				+ " left join novointernado n on n.nbam= b.idbam "
				+ " left join tratamento t on  t.idtratamento = b.tratamento_idtratamento "
				+ " left join localcenso l on n.local_idsetorlocalcenso = l.idsetorlocalcenso "
				+ " left join setorcenso s on  n.setor_idsetorcenso = s.idsetorcenso " + " INNER JOIN visitantes v ON "
				+ "cast(b.idbam as integer) = cast(v.nbam as integer)" + " where p.nome LIKE '" + nome + "%' "
				+ " and n.unidade  = '" + unidade + "' "
				+ " order by b.idbam, n.setor_idsetorcenso, n.local_idsetorlocalcenso, p.nome ";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();
		List<PacientesNirDto> pacientesNir = new ArrayList<>();

		for (Object[] result : results) {
			PacientesNirDto dto = new PacientesNirDto();
			dto.setBam((Integer) result[0]);
			dto.setIdpaciente((Integer) result[1]);
			dto.setNome((String) result[2]);
			dto.setIdade((Integer) result[3]);
			dto.setProntuario((String) result[4]);
			dto.setPacienteespecial((String) result[5]);
			dto.setAlta((Boolean) result[6]);
			dto.setDiainternacao(toLegacyDate(result[7]));
			dto.setSetor((String) result[8]);
			dto.setLocal((String) result[9]);
			dto.setTipo_alta((String) result[10]);
			pacientesNir.add(dto);
		}

		return pacientesNir;
	}

	@SuppressWarnings("unchecked")
	public List<PacientesNirDto> filtragemParaNirPorId(int id, String unidade) {

		String sql = "SELECT  p.idpaciente, p.nome, p.idade,  p.prontuario, p.pacienteespecial, MAX(b.idbam) as idbam, i.datasaida, "
				+ " n.alta,  i.datainternacao,   n.diainternacao,   s.setor,  lo.local FROM   pacientes p JOIN    bam b    ON p.idpaciente = b.paciente_idpaciente "
				+ " LEFT JOIN  internacao i ON cast(b.idbam as text) = cast(i.bam as text) LEFT JOIN    novointernado n  ON cast(b.idbam as text) = cast(n.nbam as text) "
				+ " LEFT JOIN   laudoautorizacao la ON b.idbam = la.bam LEFT JOIN    setorcenso s  ON s.idsetorcenso = n.setor_idsetorcenso "
				+ " LEFT JOIN   localcenso lo  ON lo.idsetorlocalcenso = n.local_idsetorlocalcenso 	WHERE ((  p.prontuario != '0' "
				+ " AND p.prontuario != '-' AND (n.alta != true OR (i.datasaida IS NULL AND i.iddopaciente IS NOT NULL)) OR la.bam IS NOT NULL "
				+ " ) AND p.idpaciente = " + id + " AND b.unidade = '" + unidade
				+ "'  )	GROUP BY   p.idpaciente,  p.nome, p.idade, "
				+ "  p.prontuario,  p.pacienteespecial,  i.datasaida,  n.alta,  i.datainternacao,   n.diainternacao, "
				+ " s.setor, lo.local ORDER BY  s.setor, lo.local,  p.nome,  n.alta";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();
		List<PacientesNirDto> pacientesNir = new ArrayList<>();

		for (Object[] result : results) {
			PacientesNirDto dto = new PacientesNirDto();
			dto.setIdpaciente((Integer) result[0]);
			dto.setNome((String) result[1]);
			dto.setIdade((Integer) result[2]);
			dto.setProntuario((String) result[3]);
			dto.setPacienteespecial((String) result[4]);
			dto.setBam((Integer) result[5]);
			dto.setDatasaida(toLegacyDate(result[6]));
			dto.setAlta((Boolean) result[7]);
			dto.setDatainternacao(toLegacyDate(result[8]));
			dto.setDiainternacao(toLegacyDate(result[9]));
			dto.setSetor((String) result[10]);
			dto.setLocal((String) result[11]);
			pacientesNir.add(dto);
		}

		return pacientesNir;
	}

	@SuppressWarnings("unchecked")
	@Transactional
	public List<PacientesAtendimentoDto> ordem_atendimento_do_medico(String nome, String unidade) {

		String sql = "";
		String filtroSetor = "AND r.setor NOT IN ('ODONTOLOGIA', 'DENTISTA', 'BUCOMAXILO', 'RAIO X', 'TOMOGRAFIA', 'ENFERMAGEM', 'ENFERMARIA', 'ULTRASSONOGRAFIA', 'ULTRASSOM') ";

		if ("NADA".equals(nome)) {

			sql = "SELECT p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.mae, p.pai, p.nascimento, a.idavaliacao, a.data, a.avaliado, "
					+ " b.idbam, b.prioridade, a.hora, r.idregistro, r.atendidopor, r.eletivo, r.setor, t.idtratamento, t.tratado  from pacientes as p, bam as b, registro as r , avaliacao as a, tratamento as t , escalapaciente e where   b.idbam = e.agbam and e.agenda = false  and  b.paciente_idpaciente=p.idpaciente "
					+ " and b.registro_idregistro=r.idregistro  and b.tratamento_idtratamento=t.idtratamento"
					+ " and b.unidade = '" + unidade
					+ "' and b.avaliacao_idavaliacao=a.idavaliacao and (r.atendidopor != '.' and r.atendidopor != '')  and t.tratado=0 and t.tipo_alta='-' "
					+ filtroSetor
					+ " order by  cast(b.data as date) desc, cast(b.hora as time),r.setor, r.atendidopor";
		} else {
			sql = "SELECT p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.mae, p.pai, p.nascimento, a.idavaliacao, a.data, a.avaliado, "
					+ " b.idbam, b.prioridade, a.hora, r.idregistro , r.atendidopor, r.eletivo, r.setor, t.idtratamento, t.tratado  from pacientes as p, bam as b, registro as r , avaliacao as a, tratamento as t , escalapaciente e where   b.idbam = e.agbam and e.agenda = false  and  b.paciente_idpaciente=p.idpaciente "
					+ " and b.registro_idregistro=r.idregistro  and b.tratamento_idtratamento=t.idtratamento"
					+ " and b.unidade = '" + unidade + "' and ( p.nome like '" + nome + "%' or r.atendidopor like '"
					+ nome
					+ "%' )  and b.avaliacao_idavaliacao=a.idavaliacao  and (r.atendidopor != '.' and r.atendidopor != '')  and t.tratado=0  and t.tipo_alta='-' "
					+ filtroSetor
					+ "  order by  cast(b.data as date) desc, cast(b.hora as time),r.setor, r.atendidopor";
		}
		List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();
		List<PacientesAtendimentoDto> pacientes = new ArrayList<>();

		for (Object[] result : results) {
			PacientesAtendimentoDto dto = new PacientesAtendimentoDto();
			dto.setIdPaciente((int) result[0]);
			dto.setNome((String) result[1]);
			dto.setIdade((Integer) result[2]);
			dto.setPacienteespecial((String) result[3]);
			dto.setMae((String) result[4]);
			dto.setPai((String) result[5]);
			dto.setNascimento((String) result[6]);
			dto.setIdAvaliacao((int) result[7]);
			dto.setDataav((String) result[8]);
			dto.setAvaliado((Integer) result[9]);
			dto.setIdBam((int) result[10]);
			dto.setPrioridade((int) result[11]);
			dto.setHora((String) result[12]);
			dto.setIdRegistro((Integer) result[13]);
			dto.setAtendidopor((String) result[14]);
			dto.setEletivo((boolean) result[15]);
			dto.setSetor((String) result[16]);
			dto.setIdTratamento((int) result[17]);
			dto.setTratado((Integer) result[18]);
			pacientes.add(dto);
		}

		return pacientes;
	}

	@SuppressWarnings("unchecked")
	@Transactional
	public List<PacientesAtendimentoDto> ordem_aguardando_do_medico_bkp(String unidade) {

		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		String sql = "";

		/*
		 * sql =
		 * "SELECT p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.mae, p.pai, p.nascimento, a.idavaliacao, a.data, a.avaliado, "
		 * +
		 * " b.idbam,  b.prioridade, a.hora, r.idregistro, r.atendidopor, r.eletivo, r.setor, t.idtratamento, t.tratado, b.cor  from pacientes as p"
		 * + "inner join  bam as b on b.paciente_idpaciente=p.idpaciente " +
		 * "inner join  registro as r on b.registro_idregistro=r.idregistro  " +
		 * "inner join avaliacao as a on b.avaliacao_idavaliacao=a.idavaliacao " +
		 * "inner join tratamento as t on b.tratamento_idtratamento=t.idtratamento " +
		 * "inner join escalapaciente e on b.idbam = e.agbam " +
		 * "inner join pedidomedico2 p2 on p2.bam is null where e.agenda = false  " +
		 * " and b.unidade = '" + unidade + "' and cast(b.data as date) = cast('"+ datas
		 * + "' as date)  " + "and t.tratado=0 and t.tipo_alta='-'  " +
		 * "order by  b.cor desc, cast(b.data as date) desc, cast(b.hora as time)";
		

		sql = "	SELECT " + " p.idpaciente," + "  p.nome, " + "  p.idade, " + "  p.pacienteespecial, " + "  p.mae, "
				+ "  p.pai, " + "  p.nascimento, " + "  a.idavaliacao, " + "  a.data, " + "  a.avaliado, "
				+ "  b.idbam, " + "  b.prioridade, " + "  a.hora, " + "  r.idregistro, " + "  r.atendidopor, "
				+ "  r.eletivo, " + "  r.setor, " + "  t.idtratamento, " + "  t.tratado, " + "  b.cor " + "FROM "
				+ "  pacientes AS p " + "JOIN" + "  bam AS b ON b.paciente_idpaciente = p.idpaciente " + "INNER JOIN "
				+ "  registro AS r ON b.registro_idregistro = r.idregistro " + "INNER JOIN "
				+ "  avaliacao AS a ON b.avaliacao_idavaliacao = a.idavaliacao " + "INNER JOIN "
				+ "  tratamento AS t ON b.tratamento_idtratamento = t.idtratamento " + "INNER JOIN "
				+ " escalapaciente e ON b.idbam = e.agbam " + "LEFT JOIN " + "  pedidomedico2 p2 ON p2.bam = b.idbam "
				+ "WHERE " + "  e.agenda = FALSE and b.unidade = " + "'" + unidade
				+ "' and cast(b.data as date) = cast('" + datas + "' as date)  " + "  AND t.tratado = 0 "
				+ " AND p2.bam IS NULL" + "  AND t.tipo_alta = '-' " + " ORDER BY " + "  b.cor DESC, "
				+ "  CAST(b.data AS date) DESC, " + "  CAST(b.hora AS time)";
 */
		sql = "	SELECT  p.idpaciente,  p.nome,   p.idade,   p.pacienteespecial,   p.mae, "
				+ "  p.pai,   p.nascimento,   a.idavaliacao,   a.data,   a.avaliado, "
				+ "  b.idbam,   b.prioridade,   a.hora,   r.idregistro,   r.atendidopor, "
				+ "  r.eletivo,   r.setor,   t.idtratamento,   t.tratado,   b.cor FROM "
				+ "  pacientes AS p JOIN  bam AS b ON b.paciente_idpaciente = p.idpaciente INNER JOIN "
				+ "  registro AS r ON b.registro_idregistro = r.idregistro INNER JOIN "
				+ "  avaliacao AS a ON b.avaliacao_idavaliacao = a.idavaliacao INNER JOIN "
				+ "  tratamento AS t ON b.tratamento_idtratamento = t.idtratamento INNER JOIN "
				+ " escalapaciente e ON b.idbam = e.agbam LEFT JOIN   pedidomedico2 p2 ON p2.bam = b.idbam "
				+ "WHERE   e.agenda = FALSE and b.unidade = '" + unidade
				+ "' and cast(b.data as date) = cast('" + datas + "' as date) AND t.tratado = 0 "
				+ " AND p2.bam IS NULL  AND t.tipo_alta = '-'  ORDER BY   b.cor DESC, "
				+ "  CAST(b.data AS date) DESC,   CAST(b.hora AS time)";
		
		
		List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();
		List<PacientesAtendimentoDto> pacientes = new ArrayList<>();

		for (Object[] result : results) {
			PacientesAtendimentoDto dto = new PacientesAtendimentoDto();
			dto.setIdPaciente((int) result[0]);
			dto.setNome((String) result[1]);
			dto.setIdade((Integer) result[2]);
			dto.setPacienteespecial((String) result[3]);
			dto.setMae((String) result[4]);
			dto.setPai((String) result[5]);
			dto.setNascimento((String) result[6]);
			dto.setIdAvaliacao((int) result[7]);
			dto.setDataav((String) result[8]);
			dto.setAvaliado((Integer) result[9]);
			dto.setIdBam((int) result[10]);
			dto.setPrioridade((int) result[11]);
			dto.setHora((String) result[12]);
			dto.setIdRegistro((Integer) result[13]);
			dto.setAtendidopor((String) result[14]);
			dto.setEletivo((boolean) result[15]);
			dto.setSetor((String) result[16]);
			dto.setIdTratamento((int) result[17]);
			dto.setTratado((Integer) result[18]);
			dto.setCor((Integer) result[19]);
			pacientes.add(dto);
		}

		return pacientes;
	}

	
	
	@SuppressWarnings("unchecked")
	public List<AtendMedicoDTO> ordem_aguardando_do_medico(String unidade) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		String datas = data.format(formatador).replaceAll("/", "-");

		// Consulta otimizada com uso de parâmetros para maior segurança
		String subConsulta = "(SELECT DISTINCT ON (setoresunidade.setor) setoresunidade.setor FROM setoresunidade "
				+ "INNER JOIN registro ON registro.setor = setoresunidade.setor "
				+ "WHERE setoresunidade.triar = true AND setoresunidade.siglaunidade = '" + unidade + "')";

		String sql = "SELECT DISTINCT b.idbam, b.cor, CAST(b.data AS DATE), b.hora, b.ematendimento, "
				+ "p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.pai, p.mae, p.nascimento, "
				+ "a.idAvaliacao, a.avaliado, r.idregistro, r.setor, r.resultadorx, r.resultadotomo, "
				+ "r.resultadomed, r.resultadoexame, r.resultadolabo, r.resultadoenfe, t.idtratamento, t.tratado, r.atendidopor "
				+ "FROM tratamento t, avaliacao a, pacientes p, bam b, registro r, escalapaciente e "
				+ "WHERE b.idbam = e.agbam AND e.agenda = false AND b.registro_idregistro = r.idregistro AND b.tratamento_idtratamento = t.idtratamento "
				+ "AND b.paciente_idpaciente = p.idpaciente AND b.avaliacao_idavaliacao = a.idavaliacao "
				+ "AND t.tratado = 0 AND t.tipo_alta NOT IN ('internacao', 'obs') AND r.eletivo = false "
				+ "AND a.anamnese = '.'"
				+ "AND a.esperando = 0 AND CASE WHEN (r.setor IN " + subConsulta
				+ ") THEN a.avaliado = 1 ELSE a.avaliado = 0 END AND CAST(b.data AS DATE) = CAST('" + datas + "' AS DATE) AND b.unidade = '" + unidade
				+ "' AND r.setor NOT IN ('ODONTOLOGIA', 'DENTISTA', 'BUCOMAXILO', 'RAIO X', 'TOMOGRAFIA', 'ENFERMAGEM', 'ENFERMARIA', 'ULTRASSONOGRAFIA', 'ULTRASSOM')"
				+ " ORDER BY  b.cor desc, CAST(b.data AS DATE) DESC, b.hora";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();
		List<AtendMedicoDTO> atendimento = new ArrayList<>();

		// Loop otimizado para conversão de tipos evitando exceções
		for (Object[] result : results) {
			AtendMedicoDTO dto = new AtendMedicoDTO();
			dto.setIdbam(result[0] != null ? (Integer) result[0] : 0);
			dto.setCor(result[1] != null ? (Integer) result[1] : 0);
			dto.setData(toLegacyDate(result[2]));
			dto.setHora(result[3] != null ? (String) result[3] : "");
			dto.setEmatendimento(result[4] != null ? (Boolean) result[4] : false);
			dto.setIdpaciente(result[5] != null ? (Integer) result[5] : 0);
			dto.setNome(result[6] != null ? (String) result[6] : "Desconhecido");
			dto.setIdade(result[7] != null ? (Integer) result[7] : 0);
			dto.setPacienteespecial(result[8] != null ? (String) result[8] : "Não especificado");
			dto.setPai(result[9] != null ? (String) result[9] : "Não informado");
			dto.setMae(result[10] != null ? (String) result[10] : "Não informado");
			dto.setNascimento(result[11] != null ? (String) result[11] : "");
			dto.setIdavaliacao(result[12] != null ? (Integer) result[12] : 0);
			dto.setAvaliado(result[13] != null ? (Integer) result[13] : 0);
			dto.setIdRegistro(result[14] != null ? (Integer) result[14] : 0);
			dto.setSetor(result[15] != null ? (String) result[15] : "");
			dto.setResultadorx(result[16] != null ? (Boolean) result[16] : false);
			dto.setResultadotomo(result[17] != null ? (Boolean) result[17] : false);
			dto.setResultadomed(result[18] != null ? (Boolean) result[18] : false);
			dto.setResultadoexame(result[19] != null ? (Boolean) result[19] : false);
			dto.setResultadolabo(result[20] != null ? (Boolean) result[20] : false);
			dto.setResultadoenfe(result[21] != null ? (Boolean) result[21] : false);
			dto.setIdTratamento(result[22] != null ? (Integer) result[22] : 0);
			dto.setTratado(result[23] != null ? (Integer) result[23] : 0);
			dto.setAtendidopor(result[24] != null ? (String) result[24] : "** AGUARDANDO **");

			atendimento.add(dto);
		}

		return atendimento;
	}

	
	
	
	
	
	
	@SuppressWarnings("unchecked")
	@Transactional
	public List<PacienteResultadoExameDto> listaResultadoExamesLaboratorio(String nome, String local, String unidade) {

		String sql = "SELECT distinct on (p.nome) p.nome as xnome, idpaciente, idade, pacienteespecial, "
				+ "nascimento, idBam,  idpedidomedico, efetuado, confirmado, coletar FROM pacientes p, bam b, "
				+ "pedidomedico2 pe, pedidoresultadolaboratorio pm2 where b.idbam = pe.bam "
				+ "and b.paciente_idpaciente = p.idpaciente and pm2.bam = pe.bam  and nome like '" + nome
				+ "%' and pe.efetuado=true and pe.confirmado=true  and pe.coletar = false  "
				+ "and  pe.efetuado = true and pe.unidade = '" + unidade + "' limit 20";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();
		List<PacienteResultadoExameDto> pacientes = new ArrayList<>();

		for (Object[] result : results) {
			PacienteResultadoExameDto dto = new PacienteResultadoExameDto();

			dto.setNome((String) result[0]);
			dto.setIdPaciente((int) result[1]);
			dto.setIdade((Integer) result[2]);
			dto.setPacienteespecial((String) result[3]);
			dto.setNascimento((String) result[4]);
			dto.setIdBam((int) result[5]);
			dto.setIdpedidomedico((int) result[6]);
			dto.setEfetuado((boolean) result[7]);
			dto.setConfirmado((boolean) result[8]);
			dto.setColetar((boolean) result[9]);
			pacientes.add(dto);
		}

		return pacientes;
	}

	@SuppressWarnings("unchecked")
	public List<Object[]> filtarPorPacienteMunicipio(String sigla, String d1, String d2) {
		// Validações permanecem
		if (sigla == null || sigla.equals("null")) {
			sigla = "";
		}

		// Converta as strings para LocalDate
		LocalDate dataInicial;
		LocalDate dataFinal;

		try {
			dataInicial = LocalDate.parse(d1);
			dataFinal = LocalDate.parse(d2);
		} catch (Exception e) {
			dataInicial = LocalDate.now().minusDays(30);
			dataFinal = LocalDate.now();
		}

		// Use CAST explícito para garantir a conversão correta
		/*
		 * String sql = "SELECT p.municipio, count(*) AS total " + "FROM bam b " +
		 * "JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao " +
		 * "JOIN pacientes p ON b.paciente_idpaciente = p.idpaciente " +
		 * "WHERE b.unidade = :sigla " +
		 * "AND b.data BETWEEN CAST(:dataInicial AS timestamp) AND CAST(:dataFinal AS timestamp) "
		 * + "GROUP BY p.municipio " + "ORDER BY total DESC " + "LIMIT 5";
		 * 
		 * 
		 * String sql =
		 * " SELECT    upper(unaccent(trim(p.municipio))) AS municipio_normalizado, " +
		 * " count(*) AS total 	FROM    bam b " +
		 * "	JOIN    avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao " +
		 * "	JOIN    pacientes p ON b.paciente_idpaciente = p.idpaciente	WHERE    b.unidade = :sigla "
		 * + "	AND    CAST(b.data AS DATE) BETWEEN :dataInicial AND :dataFinal " +
		 * "	GROUP BY    municipio_normalizado " +
		 * "	ORDER BY    municipio_normalizado LIMIT 5";
		 */
		String sql = "SELECT p.municipio, count(*) AS total FROM bam b "
				+ "JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "JOIN pacientes p ON b.paciente_idpaciente = p.idpaciente " + "WHERE b.unidade = :sigla "
				+ "AND b.data BETWEEN CAST(:dataInicial AS timestamp) AND CAST(:dataFinal AS timestamp) "
				+ "GROUP BY p.municipio ORDER BY total DESC";

		return getEntityManager().createNativeQuery(sql).setParameter("sigla", sigla)
				.setParameter("dataInicial", dataInicial).setParameter("dataFinal", dataFinal).getResultList();
	}

	// -------------------------------- correcao de bam ---------------------------

	// BAM 1
	@SuppressWarnings("unchecked")
	public List<PacientesNirDto> corrigirbam(String bam, String unidade) {
		int nb = Integer.parseInt(bam);
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		String sql = "SELECT p.idpaciente, p.nome, p.idade, p.prontuario, p.pacienteespecial, "
				+ "b.idbam, cast(b.data as date) as data_bam, t.tipo_alta, l.bam as bamlaudo " + "FROM pacientes p "
				+ "LEFT JOIN bam b ON p.idpaciente = b.paciente_idpaciente "
				+ "LEFT JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento "
				+ "LEFT JOIN laudoautorizacao l ON l.bam = b.idbam " + "WHERE b.idbam = :bam AND b.unidade = :unidade "
				+ "ORDER BY p.idpaciente desc LIMIT 1";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).setParameter("bam", nb)
				.setParameter("unidade", unidade).getResultList();

		List<PacientesNirDto> pacientesNir = new ArrayList<>();

		for (Object[] result : results) {
			PacientesNirDto dto = new PacientesNirDto();

			// Usar Number para segurança contra tipos (BigInteger, Integer, Long)
			dto.setIdpaciente(result[0] != null ? ((Number) result[0]).intValue() : 0);
			dto.setNome(result[1] != null ? (String) result[1] : null);
			dto.setIdade(result[2] != null ? ((Number) result[2]).intValue() : 0);
			dto.setProntuario(result[3] != null ? (String) result[3] : null);
			dto.setPacienteespecial(result[4] != null ? (String) result[4] : null);
			dto.setBam(result[5] != null ? ((Number) result[5]).intValue() : 0);
			dto.setData(toLegacyDate(result[6]));
			dto.setTipo_alta(result[7] != null ? (String) result[7] : null);
			dto.setBamlaudo(result[8] != null ? ((Number) result[8]).intValue() : 0);

			pacientesNir.add(dto);
		}

		return pacientesNir;
	}

/*	
	@Transactional
	public void ajustarDatasDeNascimentoDigitadosErrado( {
		String sql = "WITH pacientes_validos AS ("
				+ "    SELECT"
				+ "        row_number() OVER () AS rn,"
				+ "        nascimento,"
				+ "        (regexp_matches(nascimento, '^(\\d{4})/(\\d{1,2})/(\\d{1,2})$'))[1]::int AS ano,"
				+ "        (regexp_matches(nascimento, '^(\\d{4})/(\\d{1,2})/(\\d{1,2})$'))[2]::int AS mes,"
				+ "        (regexp_matches(nascimento, '^(\\d{4})/(\\d{1,2})/(\\d{1,2})$'))[3]::int AS dia"
				+ "    FROM pacientes"
				+ "    WHERE nascimento ~ '^\\d{4}/\\d{1,2}/\\d{1,2}$'"
				+ "      AND (substring(nascimento from 1 for 4)::int > 0) "
				+ ") "
				+ "UPDATE pacientes p "
				+ "SET nascimento = to_char( "
				+ "    make_date( "
				+ "        t.ano, "
				+ "        1, "
				+ "        1 "
				+ "    )"
				+ "    + ((CASE WHEN t.mes > 12 THEN 12 ELSE t.mes END - 1) * INTERVAL '1 month')"
				+ "    + ((CASE"
				+ "            WHEN t.mes = 2 AND t.dia = 29 AND (t.ano % 4 <> 0 OR (t.ano % 100 = 0 AND t.ano % 400 <> 0)) THEN 28"
				+ "            WHEN t.mes IN (4,6,9,11) AND t.dia = 31 THEN 30"
				+ "            ELSE t.dia"
				+ "        END - 1) * INTERVAL '1 day'),"
				+ "    'YYYY/MM/DD'  -- aqui garante que mês e dia sempre terão 2 dígitos"
				+ ")"
				+ " FROM pacientes_validos t"
				+ " WHERE p.nascimento = t.nascimento;" 
				
				
				
		entityManager.createNativeQuery(sql).executeUpdate();

	}
	
	*/
	
	// BAM 2

	@SuppressWarnings("unchecked")
	public List<PacientesBamDto> corrigirbam2(int id, String bam, String unidade) {
		int nb;
		try {
			nb = Integer.parseInt(bam);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("O valor de BAM deve ser numérico: " + bam, e);
		}

		String sql = """
				SELECT
				    p.idpaciente,
				    p.nome,
				    p.idade,
				    p.prontuario,
				    p.pacienteespecial,
				    b.idbam,
				    b.data,
				    b.controle,
				    b.registro_idregistro,
				    b.tratamento_idtratamento,
				    p.prontuario
				FROM bam b
				JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
				WHERE b.paciente_idpaciente = :id
				  AND b.unidade = :unidade
				  AND b.idbam = :bam
				ORDER BY b.idbam DESC
				LIMIT 1
				""";

		List<Object[]> results;
		try {
			results = getEntityManager().createNativeQuery(sql).setParameter("id", id).setParameter("unidade", unidade)
					.setParameter("bam", nb).getResultList();
		} catch (Exception e) {
			throw new RuntimeException("Erro ao executar consulta corrigirbam2: " + e.getMessage(), e);
		}

		List<PacientesBamDto> pacientesBam = new ArrayList<>();

		for (Object[] result : results) {
			PacientesBamDto dto = new PacientesBamDto();

			dto.setIdPaciente(result[0] != null ? ((Number) result[0]).intValue() : 0);
			dto.setNome(result[1] != null ? result[1].toString() : null);
			dto.setIdade(result[2] != null ? ((Number) result[2]).intValue() : null);
			dto.setPacienteespecial(result[4] != null ? result[4].toString() : null);
			dto.setIdBam(result[5] != null ? ((Number) result[5]).intValue() : 0);

			// 🔹 Conversão segura da data (b.data)
			Object dataObj = result[6];
			if (dataObj instanceof java.util.Date) {
				dto.setDataav(dataObj.toString());
			} else if (dataObj instanceof java.sql.Timestamp) {
				dto.setDataav(dataObj.toString());
			} else if (dataObj instanceof java.time.LocalDateTime) {
				dto.setDataav(dataObj.toString());
			} else {
				dto.setDataav(null);
			}

			dto.setControle(result[7] != null ? result[7].toString() : null);
			dto.setIdRegistro(result[8] != null ? ((Number) result[8]).intValue() : 0);
			dto.setIdTratamento(result[9] != null ? ((Number) result[9]).intValue() : 0);
			dto.setProntuario(result[10] != null ? result[10].toString() : null);

			pacientesBam.add(dto);
		}

		return pacientesBam;
	}

	@Transactional
	public void opcao1(int bamgravado, int idpaciente1, int bamultimo, String prontuario, int idregistro, int idtratado,
			String controle, String unidade) {
		// Mesmas atualizacoes de antes, agora com parametros: prontuario, controle e
		// unidade chegam pela URL e eram concatenados direto no SQL (injecao).
		// As antigas sql9/sql10 (afaturar/afaturar2 "set idpaciente = X where
		// idpaciente = X") nao alteravam nada e foram retiradas.
		String bamNovoTxt = String.valueOf(bamultimo);
		String bamAntigoTxt = String.valueOf(bamgravado);

		entityManager.createNativeQuery("update novointernado set nbam = :novo, bam_idbam = :novo, "
				+ "pacientes_idpaciente = :idp, prontuario = :pront where unidade = :unidade and bam_idbam = :antigo")
				.setParameter("novo", bamultimo).setParameter("idp", idpaciente1).setParameter("pront", prontuario)
				.setParameter("unidade", unidade).setParameter("antigo", bamgravado).executeUpdate();

		entityManager.createNativeQuery("update internacao set bam = :novo, iddopaciente = :idp, prontuario = :pront "
				+ "where unidade = :unidade and bam = :antigo")
				.setParameter("novo", bamNovoTxt).setParameter("idp", idpaciente1).setParameter("pront", prontuario)
				.setParameter("unidade", unidade).setParameter("antigo", bamAntigoTxt).executeUpdate();

		for (String tabela : List.of("evolucaopaciente", "evolucaopacienteenf")) {
			entityManager.createNativeQuery("update " + tabela + " set nbam = :novo, prontuario = :pront "
					+ "where unidade = :unidade and nbam = :antigo")
					.setParameter("novo", bamultimo).setParameter("pront", prontuario)
					.setParameter("unidade", unidade).setParameter("antigo", bamgravado).executeUpdate();
		}

		// Tabela/coluna fixas no codigo (nunca vindas da requisicao).
		String[][] colunasNumericas = { { "prescricao", "bam" }, { "pedidomedico2", "bam" },
				{ "pedidoresultadolaboratorio", "bam" }, { "prescricaocont", "bam" },
				{ "anamneseinternacao", "nbam" }, { "laudoautorizacao", "bam" },
				{ "examescomplementares", "nbam" } };
		for (String[] tc : colunasNumericas) {
			atualizarBamNumerico(tc[0], tc[1], bamultimo, bamgravado);
		}

		entityManager.createNativeQuery("update atendimento set bam = :novo, idpaciente = :idp where bam = :antigo")
				.setParameter("novo", bamultimo).setParameter("idp", idpaciente1).setParameter("antigo", bamgravado)
				.executeUpdate();

		entityManager.createNativeQuery("update imagens set idbam = :novo, idpacientes = :idp, idregistro = :reg, "
				+ "idtratamento = :trat, controleid = :controle where idbam = :antigo")
				.setParameter("novo", bamultimo).setParameter("idp", idpaciente1).setParameter("reg", idregistro)
				.setParameter("trat", idtratado).setParameter("controle", controle)
				.setParameter("antigo", bamgravado).executeUpdate();

		entityManager.createNativeQuery("update infocomplemetares set bam = :novo where bam = :antigo")
				.setParameter("novo", bamNovoTxt).setParameter("antigo", bamAntigoTxt).executeUpdate();

		entityManager.createNativeQuery("update laboratorio set controleid = :controle, idbam = :novo, "
				+ "idpacientes = :idp, idregistro = :reg, idtratamento = :trat where idbam = :antigo")
				.setParameter("controle", controle).setParameter("novo", bamultimo).setParameter("idp", idpaciente1)
				.setParameter("reg", idregistro).setParameter("trat", idtratado).setParameter("antigo", bamgravado)
				.executeUpdate();

		String[][] colunasNumericas2 = { { "laudorecurso", "nbam" }, { "laudosolautoproambu", "nbam" },
				{ "parecerespecialista", "nbam" }, { "prescricaoassist", "bam" },
				{ "protocolosinan", "numerodobam" }, { "riscocirurgico", "nbam" } };
		for (String[] tc : colunasNumericas2) {
			atualizarBamNumerico(tc[0], tc[1], bamultimo, bamgravado);
		}

		entityManager.createNativeQuery("update visitantes set nbam = :novo where nbam = :antigo")
				.setParameter("novo", bamNovoTxt).setParameter("antigo", bamAntigoTxt).executeUpdate();

		atualizarBamNumerico("receitamedicamentos", "nbam", bamultimo, bamgravado);

		entityManager.createNativeQuery("delete from bam where idbam = :antigo")
				.setParameter("antigo", bamgravado).executeUpdate();

	}

	private void atualizarBamNumerico(String tabela, String coluna, int bamNovo, int bamAntigo) {
		entityManager.createNativeQuery("update " + tabela + " set " + coluna + " = :novo where " + coluna + " = :antigo")
				.setParameter("novo", bamNovo).setParameter("antigo", bamAntigo).executeUpdate();
	}

	@Transactional
	public void opcao2(int bamultimo) {
		String sql = "update pedidomedico2 set coletar = false where  bam = " + bamultimo;
		entityManager.createNativeQuery(sql).executeUpdate();

	}

	@Transactional
	public void opcao3(int bamultimo) {
		String sql = "update pedidomedico2 set efetuado = true where  bam = " + bamultimo;
		entityManager.createNativeQuery(sql).executeUpdate();

	}

	@Transactional
	public void opcao4(int bamultimo) {
		// Reabre a internação (ex.: paciente transferido que voltou com o mesmo BAM).
		// O motivo volta a '-': o sistema só considera aberta a internação com motivo '-'.
		String sql = "update internacao set datasaida = null, motivodaalta = '-', medicodeualta = null where bam = :bam";
		entityManager.createNativeQuery(sql).setParameter("bam", String.valueOf(bamultimo)).executeUpdate();

	}

	@SuppressWarnings("unchecked")
	@Transactional
	public void opcao5(int bamultimo, int idpac, String unidade) {
	    try {
	        // 1️⃣ Buscar paciente pelo ID usando JPA
	        Pacientes paciente = entityManager.find(Pacientes.class, idpac);
	        
	        if (paciente == null) {
	            return;
	        }

	        String nome = paciente.getNome();
	        String mae = paciente.getMae();

	        // Sem nome ou sem mae o LIKE vira '%%' (CONCAT ignora NULL no Postgres)
	        // e casaria qualquer paciente: o de menor id seria fundido e excluido.
	        if (nome == null || nome.isBlank() || mae == null || mae.isBlank()) {
	            throw new IllegalArgumentException("Paciente sem nome ou nome da mae: duplicidade nao pode ser verificada com seguranca.");
	        }

	        // 2️⃣ Buscar pacientes duplicados usando Native Query
	        String sql = "SELECT p.* FROM pacientes p WHERE " +
	                    "REPLACE(p.nome, ' ', '') LIKE CONCAT('%', REPLACE(:nome, ' ', ''), '%') " +
	                    "AND REPLACE(p.mae, ' ', '') LIKE CONCAT('%', REPLACE(:mae, ' ', ''), '%') " +
	                    "ORDER BY p.idpaciente ASC";
	        
	        List<Pacientes> pacientesDuplicados = entityManager.createNativeQuery(sql, Pacientes.class)
	                .setParameter("nome", nome)
	                .setParameter("mae", mae)
	                .getResultList();

	        if (pacientesDuplicados.isEmpty()) {
	            return;
	        }

	        // Restante do código permanece igual...
	        Pacientes pacienteOriginal = pacientesDuplicados.get(0);
	        int idDoPacienteMenor = pacienteOriginal.getIdpaciente();
	        String prontuario1 = "0";

	        for (Pacientes p : pacientesDuplicados) {
	            if (!"0".equals(p.getProntuario())) {
	                prontuario1 = p.getProntuario();
	                break;
	            }
	        }

	        // 3️⃣ Atualizar todas as referências para o paciente atual
	        if (idDoPacienteMenor < idpac) {
	            // Lista de queries de atualização (usando Native Query)
	            List<String> updateQueries = Arrays.asList(
	                "UPDATE bam SET paciente_idpaciente = :novoId WHERE paciente_idpaciente = :antigoId",
	                "UPDATE novointernado SET pacientes_idpaciente = :novoId WHERE pacientes_idpaciente = :antigoId", 
	                "UPDATE internacao SET iddopaciente = :novoId WHERE iddopaciente = :antigoId",
	                "UPDATE afaturar SET idpaciente = :novoId WHERE idpaciente = :antigoId",
	                "UPDATE afaturar2 SET idpaciente = :novoId WHERE idpaciente = :antigoId",
	                "UPDATE escalapaciente SET paciente_idpaciente = :novoId WHERE paciente_idpaciente = :antigoId"
	            );

	            // Executar todas as atualizações
	            for (String query : updateQueries) {
	                entityManager.createNativeQuery(query)
	                    .setParameter("novoId", idpac)
	                    .setParameter("antigoId", idDoPacienteMenor)
	                    .executeUpdate();
	            }

	            // Atualizar prontuário se necessário
	            if (!"0".equals(prontuario1)) {
	                entityManager.createNativeQuery("UPDATE pacientes SET prontuario = :prontuario WHERE idpaciente = :id")
	                    .setParameter("prontuario", prontuario1)
	                    .setParameter("id", idpac)
	                    .executeUpdate();
	            }

	            // Excluir paciente antigo (apenas se não for o paciente atual)
	            if (idDoPacienteMenor != idpac) {
	                Pacientes pacienteParaExcluir = entityManager.find(Pacientes.class, idDoPacienteMenor);
	                if (pacienteParaExcluir != null) {
	                    entityManager.remove(pacienteParaExcluir);
	                }
	            }
	        }

	    } catch (Exception e) {
	        throw new RuntimeException("Erro ao consolidar pacientes duplicados", e);
	    }
	}
	@Transactional
	public void opcao7(int bamultimo) {
		 // 1️⃣ Buscar paciente pelo ID usando JPA
        Bam bam = entityManager.find(Bam.class, bamultimo);
        
        if (bam == null || bam.getTratamento() == null) {
            return;
        }

        int idt  = bam.getTratamento().getIdtratamento();
		entityManager.createNativeQuery("update tratamento set tratado = 0 where idtratamento = :idt")
				.setParameter("idt", idt).executeUpdate();

	}

	// Recusa de regra (422) com texto legivel: a tela de Correcao de BAM mostra o
	// corpo da resposta no alerta de erro.
	private static jakarta.ws.rs.WebApplicationException recusarCorrecao(String motivo) {
		return new jakarta.ws.rs.WebApplicationException(jakarta.ws.rs.core.Response.status(422)
				.entity(motivo).type("text/plain; charset=UTF-8").build());
	}

	private Bam bamParaCorrecao(int idbam, String unidade) {
		Bam bam = entityManager.find(Bam.class, idbam);
		if (bam == null || unidade == null || !unidade.equals(bam.getUnidade())) {
			throw recusarCorrecao("BAM " + idbam + " não encontrado nesta unidade.");
		}
		if (bam.getTratamento() == null || bam.getAvaliacao() == null || bam.getRegistro() == null) {
			throw recusarCorrecao("BAM " + idbam + " sem registro, triagem ou tratamento vinculados.");
		}
		return bam;
	}

	private boolean setorPassaPelaTriagem(String setor, String unidade) {
		Number total = (Number) entityManager.createNativeQuery("select count(*) from setoresunidade "
				+ "where triar = true and setor = :setor and siglaunidade = :unidade")
				.setParameter("setor", setor).setParameter("unidade", unidade).getSingleResult();
		return total != null && total.longValue() > 0;
	}

	// Opcao 8 - Devolver o paciente a fila da triagem. Mesmos criterios da fila
	// (avaliado = 0, avaliacao do dia, setor que tria). Os dados ja digitados na
	// triagem sao preservados; sao sobrescritos quando a triagem for salva de novo.
	@Transactional
	public void opcao8(int idbam, String unidade) {
		Bam bam = bamParaCorrecao(idbam, unidade);
		if (bam.getTratamento().getTratado() != 0) {
			throw recusarCorrecao("O atendimento deste BAM já foi finalizado. Use antes "
					+ "\"Cancelar finalização do atendimento\".");
		}
		if (!setorPassaPelaTriagem(bam.getRegistro().getSetor(), unidade)) {
			throw recusarCorrecao("O setor \"" + bam.getRegistro().getSetor() + "\" não passa pela triagem nesta unidade.");
		}
		String hoje = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
		String dataTriagem = bam.getAvaliacao().getData();
		if (dataTriagem == null || !dataTriagem.trim().startsWith(hoje)) {
			throw recusarCorrecao("A fila da triagem mostra apenas BAMs de hoje; este BAM é de " + dataTriagem + ".");
		}
		entityManager.createNativeQuery("update avaliacao set avaliado = 0, esperando = 0 where idavaliacao = :id")
				.setParameter("id", bam.getAvaliacao().getIdavaliacao()).executeUpdate();
		entityManager.createNativeQuery("update bam set ematendimento = false where idbam = :idbam")
				.setParameter("idbam", idbam).executeUpdate();
	}

	// Opcao 9 - Devolver o paciente a fila do medico: libera o "em atendimento"
	// travado e tira da lista de reavaliacao (esperando = 1). Nao reabre alta nem
	// mexe em internacao/observacao (fluxo do NIR).
	@Transactional
	public void opcao9(int idbam, String unidade) {
		Bam bam = bamParaCorrecao(idbam, unidade);
		if (bam.getTratamento().getTratado() != 0) {
			throw recusarCorrecao("O atendimento deste BAM já foi finalizado. Use "
					+ "\"Cancelar finalização do atendimento\".");
		}
		String tipoAlta = bam.getTratamento().getTipo_alta();
		if ("internacao".equalsIgnoreCase(tipoAlta) || "obs".equalsIgnoreCase(tipoAlta)) {
			throw recusarCorrecao("Paciente encaminhado para internação/observação; a correção deve ser feita pelo NIR.");
		}
		if (setorPassaPelaTriagem(bam.getRegistro().getSetor(), unidade) && bam.getAvaliacao().getAvaliado() != 1) {
			throw recusarCorrecao("Paciente ainda não foi triado; ele vai para a fila do médico após a triagem.");
		}
		entityManager.createNativeQuery("update avaliacao set esperando = 0 where idavaliacao = :id")
				.setParameter("id", bam.getAvaliacao().getIdavaliacao()).executeUpdate();
		entityManager.createNativeQuery("update bam set ematendimento = false where idbam = :idbam")
				.setParameter("idbam", idbam).executeUpdate();
	}

	// Opcao 10 - Devolver a coleta os exames de laboratorio marcados como
	// coletados por engano. So os que ainda nao tem resultado (efetuado = false):
	// exame com resultado nunca volta para a coleta.
	@Transactional
	public int opcao10(int idbam, String unidade) {
		bamParaCorrecao(idbam, unidade);
		int alterados = entityManager.createNativeQuery("update pedidomedico2 set coletar = true "
				+ "where bam = :bam and local = 'LABORATORIO' and efetuado = false and coletar = false")
				.setParameter("bam", idbam).executeUpdate();
		if (alterados == 0) {
			throw recusarCorrecao("Nenhum exame de laboratório coletado e sem resultado neste BAM.");
		}
		return alterados;
	}
	
	@Transactional
	public void corrigirAntigo() {
		String sql =  "UPDATE PACIENTES set municipio = 'ITAGUAI', naturalidade = 'RJ', "
				+ "cns = '000000000000000', estado = 'RJ', data=cast('2025-01-20' as date), "
				+ "ecivil = 'SOLTEIRO', hora = '00:00', numero='0', prontuario = '0', rg='0', "
				+ "sangue = 'NF',sexo='M', telefone = '0', pacienteespecial='TIPICO',profissao='NF', "
				+ "ENDERECO = 'NÃO INFORMADO', bairro='CENTRO', etnia = 'PARDO' "
				+ "WHERE  municipio is null";
		entityManager.createNativeQuery(sql).executeUpdate();

	}

}

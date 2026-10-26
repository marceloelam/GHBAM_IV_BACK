package br.com.iv.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import br.com.iv.dto.AtendMedicoDTO;
import br.com.iv.entity.AtendimentoMedico;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class AtendimentoMedicoRepository implements PanacheRepository<AtendimentoMedico> {

	private static Date toLegacyDate(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof LocalDate localDate) {
			return java.sql.Date.valueOf(localDate);
		}
		if (value instanceof Date date) {
			return date;
		}
		throw new IllegalArgumentException("Tipo de data não suportado: " + value.getClass().getName());
	}
	@Inject
	EntityManager entityManager;

	public List<AtendimentoMedico> filtrarPorId(int idPaciente) {
		return find("idpaciente = ?1 order by cast(data as date) desc, hora", idPaciente).list();
	}

	public List<AtendimentoMedico> filtarPorControle(int idpaciente) {
		return find("idpaciente = ?1", idpaciente).list();
	}

	@SuppressWarnings("unchecked")
	public List<AtendMedicoDTO> medicoAtenderHoje(String atenderHoje, String unidade, String atendidopor) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		String datas = data.format(formatador).replaceAll("/", "-");

		String subConsulta = "(SELECT DISTINCT ON (setoresunidade.setor) setoresunidade.setor FROM setoresunidade "
				+ "INNER JOIN registro ON registro.setor = setoresunidade.setor "
				+ "WHERE setoresunidade.triar = true AND setoresunidade.siglaunidade = :unidade)";

		// Odontologia e os setores multidisciplinares tem fila propria (AtendimentoOdontologico /
		// AtendimentoMultidisciplinar) — nao devem aparecer tambem na fila generica do medico.
		// Comparacao por PREFIXO (nao igualdade exata): nomes de setor sao texto livre por
		// unidade e ja houve erro de digitacao real ("FONODIAULOGIA" em vez de "FONOAUDIOLOGIA"),
		// que "NOT IN" com palavra inteira nao pega. Mesmos prefixos usados no frontend
		// (SETOR_POR_CATEGORIA em AtendimentoMultidisciplinar.jsx) — manter em sincronia.
		String filtroExcluirOdonto = " AND UPPER(r.setor) NOT LIKE 'ODONTOLOGIA%' AND UPPER(r.setor) NOT LIKE 'DENTISTA%' "
				+ "AND UPPER(r.setor) NOT LIKE 'BUCOMAXILO%' AND UPPER(r.setor) NOT LIKE 'PSICOLOG%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FISIOTERAP%' AND UPPER(r.setor) NOT LIKE 'NUTRI%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FONO%' AND UPPER(r.setor) NOT LIKE 'SERVICO SOCIAL%' "
				+ "AND UPPER(r.setor) NOT LIKE 'SERVIÇO SOCIAL%' AND UPPER(r.setor) NOT LIKE 'ASSIST%SOCIAL%' "
				+ "AND UPPER(r.setor) NOT LIKE 'TERAPIA OCUP%' AND UPPER(r.setor) NOT LIKE 'FARMACIA CLIN%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FARMÁCIA CLÍN%' AND UPPER(r.setor) NOT LIKE 'ENFERMAGEM ESPEC%' "
				+ "AND UPPER(r.setor) NOT LIKE 'RAIO X%' AND UPPER(r.setor) NOT LIKE 'TOMOGRAFIA%' "
				+ "AND UPPER(r.setor) NOT LIKE 'ENFERMA%' AND UPPER(r.setor) NOT LIKE 'ULTRA%' ";

		String sql = "SELECT DISTINCT b.idbam, b.cor, CAST(b.data AS DATE), b.hora, b.ematendimento, "
				+ "p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.pai, p.mae, p.nascimento, "
				+ "a.idAvaliacao, a.avaliado, r.idregistro, r.setor, r.resultadorx, r.resultadotomo, "
				+ "r.resultadomed, r.resultadoexame, r.resultadolabo, r.resultadoenfe, t.idtratamento, t.tratado, "
				+ "e.idescalapaciente, e.medico, r.atendidopor, "
				+ "r.temlabo, r.temrx, r.temtomo, r.temmed, r.temenfe, (b.transferido OR NULLIF(TRIM(COALESCE(b.transorigem, '')), '') IS NOT NULL) AS transferido "
				+ "FROM tratamento t, avaliacao a, pacientes p, bam b, registro r, escalapaciente e "
				+ "WHERE b.idbam = e.agbam AND e.agenda = false AND CASE WHEN r.atendidopor = :atendidopor"
				+ " THEN r.resultadoexamec19 = 3 ELSE b.ematendimento = false END "
				+ "AND b.registro_idregistro = r.idregistro AND b.tratamento_idtratamento = t.idtratamento "
				+ "AND b.paciente_idpaciente = p.idpaciente AND b.avaliacao_idavaliacao = a.idavaliacao "
				+ "AND t.tratado = 0 AND t.tipo_alta NOT IN ('internacao', 'obs') AND r.eletivo = false "
				+ "AND a.esperando = 0 AND CASE WHEN (r.setor IN " + subConsulta
				+ ") THEN a.avaliado = 1 ELSE a.avaliado = 0 END AND r.setor = :atenderHoje"
				+ " AND CAST(b.data AS DATE) BETWEEN (CAST('" + datas + "' AS DATE) - INTERVAL '1 day') AND CAST('"
				+ datas + "' AS DATE) " + "AND b.unidade = :unidade" + filtroExcluirOdonto
				+ "ORDER BY  b.cor desc, CAST(b.data AS DATE) DESC, b.hora";

		List<Object[]> results = getEntityManager().createNativeQuery(sql)
				.setParameter("atendidopor", atendidopor)
				.setParameter("atenderHoje", atenderHoje)
				.setParameter("unidade", unidade)
				.getResultList();
		List<AtendMedicoDTO> atendimento = new ArrayList<>();

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
			dto.setIdescalapaciente(result[24] != null ? (Integer) result[24] : 0);
			dto.setMedico(result[25] != null ? (String) result[25] : "");
			dto.setAtendidopor(result[26] != null ? (String) result[26] : "");
			dto.setTemlabo(result[27] != null ? (Boolean) result[27] : false);
			dto.setTemrx(result[28] != null ? (Boolean) result[28] : false);
			dto.setTemtomo(result[29] != null ? (Boolean) result[29] : false);
			dto.setTemmed(result[30] != null ? (Boolean) result[30] : false);
			dto.setTemenfe(result[31] != null ? (Boolean) result[31] : false);
			dto.setTransferido(result[32] != null ? (Boolean) result[32] : false);

			atendimento.add(dto);
		}

		return atendimento;
	}

		@SuppressWarnings("unchecked")
	public List<AtendMedicoDTO> medicoAtenderEmEspera(String atenderHoje, String unidade, String setorPadrao) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		String subConsulta = "(select distinct on (registro.setor) registro.setor from setoresunidade inner join registro on registro.setor = setoresunidade.setor  where setoresunidade.triar = true  and setoresunidade.siglaunidade = :unidade)";

		// Odontologia e os setores multidisciplinares tem fila propria (AtendimentoOdontologico /
		// AtendimentoMultidisciplinar) — nao devem aparecer tambem na fila generica do medico.
		// Comparacao por PREFIXO (nao igualdade exata): nomes de setor sao texto livre por
		// unidade e ja houve erro de digitacao real ("FONODIAULOGIA" em vez de "FONOAUDIOLOGIA"),
		// que "NOT IN" com palavra inteira nao pega. Mesmos prefixos usados no frontend
		// (SETOR_POR_CATEGORIA em AtendimentoMultidisciplinar.jsx) — manter em sincronia.
		String filtroExcluirOdonto = " AND UPPER(r.setor) NOT LIKE 'ODONTOLOGIA%' AND UPPER(r.setor) NOT LIKE 'DENTISTA%' "
				+ "AND UPPER(r.setor) NOT LIKE 'BUCOMAXILO%' AND UPPER(r.setor) NOT LIKE 'PSICOLOG%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FISIOTERAP%' AND UPPER(r.setor) NOT LIKE 'NUTRI%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FONO%' AND UPPER(r.setor) NOT LIKE 'SERVICO SOCIAL%' "
				+ "AND UPPER(r.setor) NOT LIKE 'SERVIÇO SOCIAL%' AND UPPER(r.setor) NOT LIKE 'ASSIST%SOCIAL%' "
				+ "AND UPPER(r.setor) NOT LIKE 'TERAPIA OCUP%' AND UPPER(r.setor) NOT LIKE 'FARMACIA CLIN%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FARMÁCIA CLÍN%' AND UPPER(r.setor) NOT LIKE 'ENFERMAGEM ESPEC%' "
				+ "AND UPPER(r.setor) NOT LIKE 'RAIO X%' AND UPPER(r.setor) NOT LIKE 'TOMOGRAFIA%' "
				+ "AND UPPER(r.setor) NOT LIKE 'ENFERMA%' AND UPPER(r.setor) NOT LIKE 'ULTRA%' ";

		String sql = "";
		if (atenderHoje.equals(setorPadrao)) {
			sql = "SELECT DISTINCT b.idbam, b.cor, CAST(b.data AS DATE), b.hora, b.ematendimento, p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.pai, p.mae, p.nascimento, a.idAvaliacao, a.avaliado, r.idregistro, r.setor, r.resultadorx, r.resultadotomo, r.resultadomed, r.resultadoexame, r.resultadolabo, r.resultadoenfe, t.idtratamento, t.tratado, e.idescalapaciente, e.medico, r.atendidopor, r.temlabo, r.temrx, r.temtomo, r.temmed, r.temenfe, (b.transferido OR NULLIF(TRIM(COALESCE(b.transorigem, '')), '') IS NOT NULL) AS transferido "
					+ "FROM tratamento as t, avaliacao as a, pacientes as p, bam as b, registro as r , escalapaciente e "
					+ "WHERE b.idbam = e.agbam and e.agenda = false and  b.tratamento_idtratamento=t.idtratamento and b.registro_idregistro=r.idregistro and b.paciente_idpaciente=p.idpaciente and "
					+ "b.avaliacao_idavaliacao=a.idavaliacao and t.tratado=0  and ( t.tipo_alta NOT IN ('internacao', 'obs'))  and a.esperando=1  and r.eletivo=false and (r.setor = :atenderHoje"
					+ "  or r.setor = :setorPadrao"
					+ " )  and (cast(b.data as date ) between (cast('" + datas
					+ "' as date) - INTERVAL '1 day' ) and cast('" + datas + "' as date) ) and b.unidade = :unidade"
					+ filtroExcluirOdonto
					+ "order by cast(b.data as date) desc, b.hora, r.resultadorx desc, r.resultadoexame desc, b.cor desc";

			List<Object[]> results = getEntityManager().createNativeQuery(sql)
					.setParameter("atenderHoje", atenderHoje)
					.setParameter("setorPadrao", setorPadrao)
					.setParameter("unidade", unidade)
					.getResultList();
			List<AtendMedicoDTO> atendimento = new ArrayList<>();

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
				dto.setIdescalapaciente(result[24] != null ? (Integer) result[24] : 0);
				dto.setMedico(result[25] != null ? (String) result[25] : "");
				dto.setAtendidopor(result[26] != null ? (String) result[26] : "");
				dto.setTemlabo(result[27] != null ? (Boolean) result[27] : false);
				dto.setTemrx(result[28] != null ? (Boolean) result[28] : false);
				dto.setTemtomo(result[29] != null ? (Boolean) result[29] : false);
				dto.setTemmed(result[30] != null ? (Boolean) result[30] : false);
				dto.setTemenfe(result[31] != null ? (Boolean) result[31] : false);
				dto.setTransferido(result[32] != null ? (Boolean) result[32] : false);

				atendimento.add(dto);
			}

			return atendimento;

		} else {

			if ((!atenderHoje.equals(setorPadrao) && (!atenderHoje.equals("MATERNIDADE"))
					&& (!atenderHoje.equals("PEDIATRIA")))) {

				sql = "SELECT DISTINCT b.idbam, b.cor, CAST(b.data AS DATE), b.hora, b.ematendimento, p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.pai, p.mae, p.nascimento, a.idAvaliacao, a.avaliado, r.idregistro, r.setor, r.resultadorx, r.resultadotomo, r.resultadomed, r.resultadoexame, r.resultadolabo, r.resultadoenfe, t.idtratamento, t.tratado, e.idescalapaciente, e.medico, r.atendidopor, r.temlabo, r.temrx, r.temtomo, r.temmed, r.temenfe, (b.transferido OR NULLIF(TRIM(COALESCE(b.transorigem, '')), '') IS NOT NULL) AS transferido "
						+ "FROM tratamento as t, avaliacao as a, pacientes as p, bam as b, registro as r, escalapaciente e "
						+ "WHERE b.idbam = e.agbam and e.agenda = false and  b.tratamento_idtratamento=t.idtratamento and b.registro_idregistro=r.idregistro and b.paciente_idpaciente=p.idpaciente and "
						+ "b.avaliacao_idavaliacao=a.idavaliacao and t.tratado=0 and r.eletivo=false and a.esperando=1 and r.setor = :atenderHoje"
						+ " and b.unidade = :unidade"
						+ " and (cast(b.data as date ) between (cast('" + datas
						+ "' as date) - INTERVAL '1 day' ) and cast('" + datas
						+ "' as date) )" + filtroExcluirOdonto
						+ "order by cast(b.data as date), b.hora, b.cor desc";
			} else {
				sql = "SELECT DISTINCT b.idbam, b.cor, CAST(b.data AS DATE), b.hora, b.ematendimento, p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.pai, p.mae, p.nascimento, a.idAvaliacao, a.avaliado, r.idregistro, r.setor, r.resultadorx, r.resultadotomo, r.resultadomed, r.resultadoexame, r.resultadolabo, r.resultadoenfe, t.idtratamento, t.tratado, e.idescalapaciente, e.medico, r.atendidopor, r.temlabo, r.temrx, r.temtomo, r.temmed, r.temenfe, (b.transferido OR NULLIF(TRIM(COALESCE(b.transorigem, '')), '') IS NOT NULL) AS transferido "
						+ "FROM tratamento as t, avaliacao as a, pacientes as p, bam as b, registro as r , escalapaciente e "
						+ "WHERE b.idbam = e.agbam and e.agenda = false and  b.tratamento_idtratamento=t.idtratamento and b.registro_idregistro=r.idregistro and b.paciente_idpaciente=p.idpaciente and "
						+ "b.avaliacao_idavaliacao=a.idavaliacao and t.tratado=0 and a.esperando=1 and case when (r.setor in "
						+ subConsulta
						+ ") then a.avaliado=1 ELSE a.avaliado=0 END and r.eletivo=false and r.setor = :atenderHoje"
						+ " and b.unidade = :unidade"
						+ " and (cast(b.data as date ) between (cast('" + datas
						+ "' as date) - INTERVAL '1 day' ) and cast('" + datas
						+ "' as date) )" + filtroExcluirOdonto
						+ "order by cast(b.data as date), b.hora, b.cor desc";
			}
			List<Object[]> results = getEntityManager().createNativeQuery(sql)
					.setParameter("atenderHoje", atenderHoje)
					.setParameter("unidade", unidade)
					.getResultList();
			List<AtendMedicoDTO> atendimento = new ArrayList<>();

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
				dto.setIdescalapaciente(result[24] != null ? (Integer) result[24] : 0);
				dto.setMedico(result[25] != null ? (String) result[25] : "");
				dto.setAtendidopor(result[26] != null ? (String) result[26] : "");
				dto.setTemlabo(result[27] != null ? (Boolean) result[27] : false);
				dto.setTemrx(result[28] != null ? (Boolean) result[28] : false);
				dto.setTemtomo(result[29] != null ? (Boolean) result[29] : false);
				dto.setTemmed(result[30] != null ? (Boolean) result[30] : false);
				dto.setTemenfe(result[31] != null ? (Boolean) result[31] : false);
				dto.setTransferido(result[32] != null ? (Boolean) result[32] : false);

				atendimento.add(dto);
			}

			return atendimento;
		}
	}

	@SuppressWarnings("unchecked")
	public List<AtendMedicoDTO> medicoAtenderHojeAgenda(String atenderHoje, String unidade, String atendidopor) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		String datas = data.format(formatador).replaceAll("/", "-");

		String subConsulta = "(SELECT DISTINCT ON (setoresunidade.setor) setoresunidade.setor FROM setoresunidade "
				+ "INNER JOIN registro ON registro.setor = setoresunidade.setor "
				+ "WHERE setoresunidade.triar = true AND setoresunidade.siglaunidade = :unidade)";

		// Odontologia e os setores multidisciplinares tem fila propria (AtendimentoOdontologico /
		// AtendimentoMultidisciplinar) — nao devem aparecer tambem na fila generica do medico.
		// Comparacao por PREFIXO (nao igualdade exata): nomes de setor sao texto livre por
		// unidade e ja houve erro de digitacao real ("FONODIAULOGIA" em vez de "FONOAUDIOLOGIA"),
		// que "NOT IN" com palavra inteira nao pega. Mesmos prefixos usados no frontend
		// (SETOR_POR_CATEGORIA em AtendimentoMultidisciplinar.jsx) — manter em sincronia.
		String filtroExcluirOdonto = " AND UPPER(r.setor) NOT LIKE 'ODONTOLOGIA%' AND UPPER(r.setor) NOT LIKE 'DENTISTA%' "
				+ "AND UPPER(r.setor) NOT LIKE 'BUCOMAXILO%' AND UPPER(r.setor) NOT LIKE 'PSICOLOG%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FISIOTERAP%' AND UPPER(r.setor) NOT LIKE 'NUTRI%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FONO%' AND UPPER(r.setor) NOT LIKE 'SERVICO SOCIAL%' "
				+ "AND UPPER(r.setor) NOT LIKE 'SERVIÇO SOCIAL%' AND UPPER(r.setor) NOT LIKE 'ASSIST%SOCIAL%' "
				+ "AND UPPER(r.setor) NOT LIKE 'TERAPIA OCUP%' AND UPPER(r.setor) NOT LIKE 'FARMACIA CLIN%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FARMÁCIA CLÍN%' AND UPPER(r.setor) NOT LIKE 'ENFERMAGEM ESPEC%' "
				+ "AND UPPER(r.setor) NOT LIKE 'RAIO X%' AND UPPER(r.setor) NOT LIKE 'TOMOGRAFIA%' "
				+ "AND UPPER(r.setor) NOT LIKE 'ENFERMA%' AND UPPER(r.setor) NOT LIKE 'ULTRA%' ";

		String sql = "SELECT DISTINCT b.idbam, b.cor, CAST(b.data AS DATE), b.hora, b.ematendimento, "
				+ "p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.pai, p.mae, p.nascimento, "
				+ "a.idAvaliacao, a.avaliado, r.idregistro, r.setor, r.resultadorx, r.resultadotomo, "
				+ "r.resultadomed, r.resultadoexame, r.resultadolabo, r.resultadoenfe, t.idtratamento, t.tratado, "
				+ "e.idescalapaciente, e.medico, r.atendidopor, "
				+ "r.temlabo, r.temrx, r.temtomo, r.temmed, r.temenfe, (b.transferido OR NULLIF(TRIM(COALESCE(b.transorigem, '')), '') IS NOT NULL) AS transferido "
				+ "FROM tratamento t, avaliacao a, pacientes p, bam b, registro r, escalapaciente e "
				+ "WHERE b.idbam = e.agbam AND e.agenda = true and e.chegou = true AND CASE WHEN r.atendidopor = :atendidopor"
				+ " THEN r.resultadoexamec19 = 3 ELSE b.ematendimento = false END "
				+ "AND b.registro_idregistro = r.idregistro AND b.tratamento_idtratamento = t.idtratamento "
				+ "AND b.paciente_idpaciente = p.idpaciente AND b.avaliacao_idavaliacao = a.idavaliacao "
				+ "AND t.tratado = 0 AND t.tipo_alta NOT IN ('internacao', 'obs') AND r.eletivo = false "
				+ "AND a.esperando = 0 AND CASE WHEN (r.setor IN " + subConsulta
				+ ") THEN a.avaliado = 1 ELSE a.avaliado = 0 END AND r.setor = :atenderHoje"
				+ " AND e.inicioagenda = CAST('" + datas + "' AS DATE) AND b.unidade = :unidade"
				+ filtroExcluirOdonto
				+ "ORDER BY  b.cor desc, CAST(b.data AS DATE) DESC, b.hora";

		List<Object[]> results = getEntityManager().createNativeQuery(sql)
				.setParameter("atendidopor", atendidopor)
				.setParameter("atenderHoje", atenderHoje)
				.setParameter("unidade", unidade)
				.getResultList();
		List<AtendMedicoDTO> atendimento = new ArrayList<>();

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
			dto.setIdescalapaciente(result[24] != null ? (Integer) result[24] : 0);
			dto.setMedico(result[25] != null ? (String) result[25] : "");
			dto.setAtendidopor(result[26] != null ? (String) result[26] : "");
			dto.setTemlabo(result[27] != null ? (Boolean) result[27] : false);
			dto.setTemrx(result[28] != null ? (Boolean) result[28] : false);
			dto.setTemtomo(result[29] != null ? (Boolean) result[29] : false);
			dto.setTemmed(result[30] != null ? (Boolean) result[30] : false);
			dto.setTemenfe(result[31] != null ? (Boolean) result[31] : false);
			dto.setTransferido(result[32] != null ? (Boolean) result[32] : false);

			atendimento.add(dto);
		}

		return atendimento;
	}

	@SuppressWarnings("unchecked")
	public List<AtendMedicoDTO> medicoAtenderEmEsperaAgenda(String atenderHoje, String unidade, String setorPadrao) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		String subConsulta = "(select distinct on (registro.setor) registro.setor from setoresunidade inner join registro on registro.setor = setoresunidade.setor  where setoresunidade.triar = true  and setoresunidade.siglaunidade = :unidade)";
				

		String sql = "";
		// Odontologia e os setores multidisciplinares tem fila propria (AtendimentoOdontologico /
		// AtendimentoMultidisciplinar) — nao devem aparecer tambem na fila generica do medico.
		// Comparacao por PREFIXO (nao igualdade exata): nomes de setor sao texto livre por
		// unidade e ja houve erro de digitacao real ("FONODIAULOGIA" em vez de "FONOAUDIOLOGIA"),
		// que "NOT IN" com palavra inteira nao pega. Mesmos prefixos usados no frontend
		// (SETOR_POR_CATEGORIA em AtendimentoMultidisciplinar.jsx) — manter em sincronia.
		String filtroExcluirOdonto = " AND UPPER(r.setor) NOT LIKE 'ODONTOLOGIA%' AND UPPER(r.setor) NOT LIKE 'DENTISTA%' "
				+ "AND UPPER(r.setor) NOT LIKE 'BUCOMAXILO%' AND UPPER(r.setor) NOT LIKE 'PSICOLOG%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FISIOTERAP%' AND UPPER(r.setor) NOT LIKE 'NUTRI%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FONO%' AND UPPER(r.setor) NOT LIKE 'SERVICO SOCIAL%' "
				+ "AND UPPER(r.setor) NOT LIKE 'SERVIÇO SOCIAL%' AND UPPER(r.setor) NOT LIKE 'ASSIST%SOCIAL%' "
				+ "AND UPPER(r.setor) NOT LIKE 'TERAPIA OCUP%' AND UPPER(r.setor) NOT LIKE 'FARMACIA CLIN%' "
				+ "AND UPPER(r.setor) NOT LIKE 'FARMÁCIA CLÍN%' AND UPPER(r.setor) NOT LIKE 'ENFERMAGEM ESPEC%' "
				+ "AND UPPER(r.setor) NOT LIKE 'RAIO X%' AND UPPER(r.setor) NOT LIKE 'TOMOGRAFIA%' "
				+ "AND UPPER(r.setor) NOT LIKE 'ENFERMA%' AND UPPER(r.setor) NOT LIKE 'ULTRA%' ";

		if (atenderHoje.equals(setorPadrao)) {
			sql = "SELECT DISTINCT b.idbam, b.cor, CAST(b.data AS DATE), b.hora, b.ematendimento, p.idpaciente, p.nome, p.idade, p.pacienteespecial, "
					+ "p.pai, p.mae, p.nascimento, a.idAvaliacao, a.avaliado, r.idregistro, r.setor, r.resultadorx, r.resultadotomo, r.resultadomed, "
					+ "r.resultadoexame, r.resultadolabo, r.resultadoenfe, t.idtratamento, t.tratado, e.idescalapaciente, e.medico, r.atendidopor, "
					+ "r.temlabo, r.temrx, r.temtomo, r.temmed, r.temenfe, (b.transferido OR NULLIF(TRIM(COALESCE(b.transorigem, '')), '') IS NOT NULL) AS transferido "
					+ "FROM tratamento as t, avaliacao as a, pacientes as p, bam as b, registro as r , escalapaciente e "
					+ "WHERE b.idbam = e.agbam and e.agenda = true and e.chegou=true "
					+ "and b.tratamento_idtratamento=t.idtratamento and b.registro_idregistro=r.idregistro and b.paciente_idpaciente=p.idpaciente and "
					+ "b.avaliacao_idavaliacao=a.idavaliacao and t.tratado=0 and (t.tipo_alta NOT IN ('internacao', 'obs')) and a.esperando=1 "
					+ "and r.eletivo=false and (r.setor = :atenderHoje or r.setor = :setorPadrao"
					+ ") and e.inicioagenda = cast('" + datas + "' as date) and b.unidade = :unidade"
					+ filtroExcluirOdonto
					+ "order by cast(b.data as date) desc, b.hora, r.resultadorx desc, r.resultadoexame desc, b.cor desc";

			List<Object[]> results = getEntityManager().createNativeQuery(sql)
					.setParameter("atenderHoje", atenderHoje)
					.setParameter("setorPadrao", setorPadrao)
					.setParameter("unidade", unidade)
					.getResultList();
			List<AtendMedicoDTO> atendimento = new ArrayList<>();

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
				dto.setIdescalapaciente(result[24] != null ? (Integer) result[24] : 0);
				dto.setMedico(result[25] != null ? (String) result[25] : "");
				dto.setAtendidopor(result[26] != null ? (String) result[26] : "");
				dto.setTemlabo(result[27] != null ? (Boolean) result[27] : false);
				dto.setTemrx(result[28] != null ? (Boolean) result[28] : false);
				dto.setTemtomo(result[29] != null ? (Boolean) result[29] : false);
				dto.setTemmed(result[30] != null ? (Boolean) result[30] : false);
				dto.setTemenfe(result[31] != null ? (Boolean) result[31] : false);
				dto.setTransferido(result[32] != null ? (Boolean) result[32] : false);

				atendimento.add(dto);
			}

			return atendimento;

		} else {

			if ((!atenderHoje.equals(setorPadrao) && (!atenderHoje.equals("MATERNIDADE"))
					&& (!atenderHoje.equals("PEDIATRIA")))) {

				sql = "SELECT DISTINCT b.idbam, b.cor, CAST(b.data AS DATE), b.hora, b.ematendimento, p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.pai, p.mae, p.nascimento, a.idAvaliacao, a.avaliado, r.idregistro, r.setor, r.resultadorx, r.resultadotomo, r.resultadomed, r.resultadoexame, r.resultadolabo, r.resultadoenfe, t.idtratamento, t.tratado, e.idescalapaciente, e.medico, r.atendidopor, r.temlabo, r.temrx, r.temtomo, r.temmed, r.temenfe, (b.transferido OR NULLIF(TRIM(COALESCE(b.transorigem, '')), '') IS NOT NULL) AS transferido "
						+ "FROM tratamento as t, avaliacao as a, pacientes as p, bam as b, registro as r, escalapaciente e "
						+ "WHERE b.idbam = e.agbam and e.agenda = true and e.chegou = true and b.tratamento_idtratamento=t.idtratamento and b.registro_idregistro=r.idregistro and b.paciente_idpaciente=p.idpaciente and "
						+ "b.avaliacao_idavaliacao=a.idavaliacao and t.tratado=0 and r.eletivo=false and a.esperando=1 and r.setor = :atenderHoje"
						+ " and b.unidade = :unidade"
						+ " and e.inicioagenda = cast('" + datas + "' as date)"
						+ filtroExcluirOdonto
						+ "order by cast(b.data as date), b.hora, b.cor desc";
			} else {
				sql = "SELECT DISTINCT b.idbam, b.cor, CAST(b.data AS DATE), b.hora, b.ematendimento, p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.pai, p.mae, p.nascimento, a.idAvaliacao, a.avaliado, r.idregistro, r.setor, r.resultadorx, r.resultadotomo, r.resultadomed, r.resultadoexame, r.resultadolabo, r.resultadoenfe, t.idtratamento, t.tratado, e.idescalapaciente, e.medico, r.atendidopor, r.temlabo, r.temrx, r.temtomo, r.temmed, r.temenfe, (b.transferido OR NULLIF(TRIM(COALESCE(b.transorigem, '')), '') IS NOT NULL) AS transferido "
						+ "FROM tratamento as t, avaliacao as a, pacientes as p, bam as b, registro as r , escalapaciente e "
						+ "WHERE b.idbam = e.agbam and e.agenda = true and e.chegou = true and b.tratamento_idtratamento=t.idtratamento and b.registro_idregistro=r.idregistro and b.paciente_idpaciente=p.idpaciente and "
						+ "b.avaliacao_idavaliacao=a.idavaliacao and t.tratado=0 and a.esperando=1 and case when (r.setor in "
						+ subConsulta
						+ ") then a.avaliado=1 ELSE a.avaliado=0 END and r.eletivo=false and r.setor = :atenderHoje"
						+ " and b.unidade = :unidade"
						+ " and e.inicioagenda = cast('" + datas + "' as date)"
						+ filtroExcluirOdonto
						+ "order by cast(b.data as date), b.hora, b.cor desc";
			}
			List<Object[]> results = getEntityManager().createNativeQuery(sql)
					.setParameter("atenderHoje", atenderHoje)
					.setParameter("unidade", unidade)
					.getResultList();
			List<AtendMedicoDTO> atendimento = new ArrayList<>();

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
				dto.setIdescalapaciente(result[24] != null ? (Integer) result[24] : 0);
				dto.setMedico(result[25] != null ? (String) result[25] : "");
				dto.setAtendidopor(result[26] != null ? (String) result[26] : "");
				dto.setTemlabo(result[27] != null ? (Boolean) result[27] : false);
				dto.setTemrx(result[28] != null ? (Boolean) result[28] : false);
				dto.setTemtomo(result[29] != null ? (Boolean) result[29] : false);
				dto.setTemmed(result[30] != null ? (Boolean) result[30] : false);
				dto.setTemenfe(result[31] != null ? (Boolean) result[31] : false);
				dto.setTransferido(result[32] != null ? (Boolean) result[32] : false);

				atendimento.add(dto);
			}

			return atendimento;
		}
	}
}

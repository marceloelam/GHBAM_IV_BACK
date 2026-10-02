package br.com.iv.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import br.com.iv.dto.EscalaResumoDTO;
import br.com.iv.dto.PacienteDto;
import br.com.iv.entity.EscalaPaciente;
import br.com.iv.entity.Pacientes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class EscalaPacientesRepository implements PanacheRepository<EscalaPaciente> {
	@Inject
	EntityManager entityManager;

	public List<EscalaPaciente> filtrarPacientes(String nome) {
		return find("nome", nome).list();
	}

	public EscalaPaciente filtrarPorUnicoPacienteId(int query) {
		return find("paciente.idpaciente = ?1", query).firstResult();
	}

	// public EscalaPaciente filtrarPorUnicoPacienteId2(int query, String unidade,
	// boolean presenca) {
	// return find("paciente.idpaciente = ?1 and unidade = ?2", query,
	// unidade).firstResult();
	// }

	@Transactional
	public void filtrarPorUnicoPacienteId2(int idescalapaciente, String unidade, boolean presenca) {
		String sql = "UPDATE escalaPaciente set chegou = :presenca where idescalapaciente = :idescalapaciente and unidade = :unidade";
		entityManager.createNativeQuery(sql).setParameter("idescalapaciente", idescalapaciente)
				.setParameter("presenca", presenca).setParameter("unidade", unidade).executeUpdate();
	}

	public List<EscalaPaciente> filtarPorPaciente(String query) {
		String n_nome = query.replaceAll("\\s", "");
		return find("replace(nome, ' ', '') like ?1", n_nome + "%").page(Page.ofSize(10)).list();
	}

	public List<EscalaPaciente> filtarPorPacienteEMae(String query, String query2) {
		String n_nome = query.replaceAll("\\s", "");
		String n_mae = query2.replaceAll("\\s", "");
		// return find("nome like ?1 and mae like ?2", query + "%", query2 +
		// "%").list();
		return find("replace(nome, ' ', '') like ?1 and replace(mae, ' ', '') like ?2", n_nome + "%", n_mae + "%")
				.page(Page.ofSize(18)).list();
	}

	public List<EscalaPaciente> filtarPorCPF(String query) {
		return find("cpf = ?1", query).list();
	}

	public List<EscalaPaciente> filtarPorControle(String query) {
		return find("tempcod = ?1", query).list();
	}

	public void fecharTodosBamAbertoDoPaciente(int idPaciente) {
		String sql = "UPDATE tratamento set tratado = 1 where idtratamento in (select tratamento_idtratamento from bam where paciente_idpaciente = :id_do_paciente)";
		entityManager.createNativeQuery(sql).setParameter("id_do_paciente", idPaciente).executeUpdate();
	}

	// Atualizar idade através do nome do paciente
	public void alterarAIdade(String nomePaciente) {
		String sql = "UPDATE pacientes set idade = DATE_PART('YEAR', age(CURRENT_DATE, cast(nascimento as date))) where nome like :nome_do_paciente || '%')";
		entityManager.createNativeQuery(sql).setParameter("nome_do_paciente", nomePaciente).executeUpdate();
		System.out.println(sql);
	}

	// Atualizar idade atráves do dia atual de passagem na triagem
	public void alterarAIdadeNaTriagem(String unidade) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		String subConsulta = "(select distinct registro.setor from setoresunidade inner join registro on registro.setor = setoresunidade.setor where setoresunidade.triar = true and setoresunidade.siglaunidade = :unidade)";

		String subconsultaLocal = "SELECT DISTINCT p.nome FROM pacientes p "
				+ "JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "WHERE a.avaliado = 0 AND r.eletivo = false AND to_date(a.data, 'YYYY/MM/DD') = :datas "
				+ "AND r.setor IN " + subConsulta + " AND b.unidade = :unidade";

		String sql = "UPDATE pacientes set idade = DATE_PART('YEAR', age(CURRENT_DATE, cast(nascimento as date))) where nome in ("
				+ subconsultaLocal + ")";
		entityManager.createNativeQuery(sql)
				.setParameter("unidade", unidade)
				.setParameter("datas", datas)
				.executeUpdate();
		System.out.println(sql);
	}

	@SuppressWarnings("unchecked")
	@Transactional
	public List<Pacientes> locaisDeTriagem(String unidade) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		String subConsulta = "(select distinct registro.setor from setoresunidade inner join registro on registro.setor = setoresunidade.setor where setoresunidade.triar = true and setoresunidade.siglaunidade = :unidade)";

		String sql = "SELECT DISTINCT b.idbam, p.*, a.*, b.*, r.* FROM pacientes p "
				+ "JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "WHERE a.avaliado = 0 AND r.eletivo = false AND to_date(a.data, 'YYYY/MM/DD') = :datas "
				+ "AND r.setor IN " + subConsulta + " AND b.unidade = :unidade "
				+ "ORDER BY b.idbam, b.prioridade DESC, b.hora";

		return getEntityManager().createNativeQuery(sql, Pacientes.class)
				.setParameter("unidade", unidade)
				.setParameter("datas", datas)
				.getResultList();
	}

	@SuppressWarnings("unchecked")
	@Transactional
	public List<PacienteDto> locaisDeTriagem2(String unidade, String ksetor) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		String subConsulta = "(select distinct registro.setor from setoresunidade inner join registro on registro.setor = setoresunidade.setor where setoresunidade.triar = true and setoresunidade.siglaunidade = :unidade)";

		String sql = "SELECT DISTINCT p.idpaciente, p.nome, p.idade, p.pacienteespecial, p.mae, p.pai, p.nascimento, a.idavaliacao, a.data, a.avaliado, "
				+ "b.idbam, b.prioridade, a.hora, r.idregistro, r.eletivo, r.setor, t.idtratamento, t.tratado  FROM pacientes p "
				+ "JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "JOIN tratamento t ON b.tratamento_idtratamento = t.idtratamento "
				+ "WHERE a.avaliado = 0 AND r.eletivo = false AND to_date(a.data, 'YYYY/MM/DD') = :datas "
				+ "AND r.setor IN " + subConsulta + " AND b.unidade = :unidade AND r.setor like :ksetor "
				+ "AND t.tratado = 0  ORDER BY r.setor, b.idbam, b.prioridade DESC, a.hora";

		List<Object[]> results = getEntityManager().createNativeQuery(sql)
				.setParameter("unidade", unidade)
				.setParameter("datas", datas)
				.setParameter("ksetor", ksetor + "%")
				.getResultList();
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

	@SuppressWarnings("unchecked")
	public List<EscalaPaciente> filtarPorEspecialidade(String especialidade, String unidade) {
		String n_nome = especialidade.replaceAll("\\s", "");

		if (n_nome.equals("")) {
			String sql = "SELECT p.*  FROM pacientes  where s.nome = 'SOU GRATO A TI ABA'";
			return getEntityManager().createNativeQuery(sql, EscalaPaciente.class).getResultList();
		}
		String sql = "SELECT *  FROM EscalaPaciente WHERE n.alta = false and replace(p.nome, ' ', '') like :nome order by p.nome";
		return getEntityManager().createNativeQuery(sql, Pacientes.class)
				.setParameter("nome", n_nome + "%")
				.getResultList();

	}

	@SuppressWarnings("unchecked")
	public List<EscalaResumoDTO> filtarPorEspecialidadeHora(String especialidade, String unidade, String data,
			String medico) {
		String n_nome = especialidade.replaceAll("\\s", "");

		// Converter a data para LocalDate e usar Panache `find` para evitar SQL nativo
		LocalDate dt = LocalDate.parse(data);

		List<EscalaPaciente> lista = find(
				"cast(inicioagenda as date) = ?1 AND setor = ?2 AND medico = ?3 AND unidade = ?4 AND atendido = false order by cast(horamarcada as time)",
				dt, n_nome, medico, unidade).list();

		return lista.stream().map(e -> {
			String horamarcada = e.getHoramarcada();
			String unidadeRes = e.getUnidade();
			String medicoRes = e.getMedico();
			Date inicioAgenda = null;
			if (e.getInicioagenda() != null) {
				inicioAgenda = java.sql.Date.valueOf(e.getInicioagenda());
			}
			return new EscalaResumoDTO(horamarcada, unidadeRes, medicoRes, inicioAgenda);
		}).collect(Collectors.toList());
	}

	public List<EscalaPaciente> filtarPorDataUnidade(String data, String unidade) {
		LocalDate dt = LocalDate.parse(data);

		return find("FROM EscalaPaciente e " + "JOIN Bam b ON b.idbam = e.agbam JOIN Tratamento t ON t = b.tratamento "
				+ "WHERE cast(e.inicioagenda as date) = ?1 AND e.unidade = ?2 AND e.atendido = false "
				+ "AND t.tratado = 0 ORDER BY cast(e.horamarcada as time)", dt, unidade).list();
	}

	public List<EscalaPaciente> filtarPorCpf(String cpf) {
		return find(
				"FROM EscalaPaciente e JOIN Bam b ON b.idbam = e.agbam JOIN Tratamento t ON t = b.tratamento JOIN Pacientes p ON p.idpaciente = b.paciente.idpaciente"
						+ " WHERE p.cpf = ?1 and e.atendido = false AND t.tratado = 0 ORDER BY cast(b.data as date), cast(e.horamarcada as time)",
				cpf)
				.page(Page.ofSize(10)).list();
	}

	public void cancelaragendamentonodestino(int idbam) {
		String sql = "";
		sql = "UPDATE escalapaciente set agenda = false where agbam = :idbam";
		entityManager.createNativeQuery(sql).setParameter("idbam", idbam).executeUpdate();
	}

	@Transactional
	public boolean checkinByTempcod(String tempcod) {
		int updated = entityManager.createNativeQuery(
				"UPDATE escalapaciente SET chegou = true WHERE paciente_idpaciente IN " +
				"(SELECT idpaciente FROM pacientes WHERE tempcod = :tempcod) AND chegou = false")
				.setParameter("tempcod", tempcod)
				.executeUpdate();
		return updated > 0;
	}

	/*
	 * public List<EscalaPaciente> filtarPorDataUnidade(String data, String unidade)
	 * { LocalDate dt = LocalDate.parse(data); // converte "2024-05-10" // int mes =
	 * dt.getMonthValue(); // int ano = dt.getYear();
	 * 
	 * return
	 * find("cast(inicioagenda as date) = ?1  AND unidade = ?2 order by cast(horamarcada as time)"
	 * , dt, unidade) .page(Page.ofSize(10)).list();
	 * 
	 * // return find("MONTH(cast(inicioagenda as date)) = ?1 AND //
	 * YEAR(cast(inicioagenda as date)) = ?2 AND unidade = ?3", // mes, ano,
	 * unidade) // .page(Page.ofSize(10)) // .list(); }
	 */
}

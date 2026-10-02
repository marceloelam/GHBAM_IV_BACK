package br.com.iv.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import br.com.iv.dto.PedidoMedico2Dto;
import br.com.iv.dto.PedidoMedico2xDto;
import br.com.iv.dto.PedidoMedico2yDto;
import br.com.iv.entity.PedidoMedico2;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

@ApplicationScoped
public class PedidoMedico2Repository implements PanacheRepository<PedidoMedico2> {
	@Inject
	EntityManager entityManager;

	public String datas;

	public List<PedidoMedico2> filtarBam(int query1) {
		return find("bam = ?1  order by pedido", query1).list();
	}

	// PS coleta
	public List<PedidoMedico2> filtarBamColetarPs(int query1) {
		return find("bam = ?1 and efetuado = false and emergencia = false and internado=false and coletar=true ",
				query1).list();
	}

	// INTERNADOS coleta
	public List<PedidoMedico2> filtarBamColetarInternados(int query1) {
		return find("bam = ?1 and efetuado = false and emergencia = false and internado=true and coletar=true ", query1)
				.list();
	}

	// EMERGÊNCIA coleta
	public List<PedidoMedico2> filtarBamColetarEmergencia(int query1) {
		return find("bam = ?1 and efetuado = false and emergencia = true and internado=true and coletar=true ", query1)
				.list();
	}

	// ---------------------INTERNADO COLETA EMERGÊNCIA -----------------------
	@SuppressWarnings("unchecked")
	@Transactional(TxType.SUPPORTS)
	public List<PedidoMedico2xDto> coletaInternadoEmergencia(String unidade, String local) {

		LocalDate hoje = LocalDate.now();

		String sql = "SELECT DISTINCT ON (p.nome) "
				+ "p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, p.pai, p.mae, " + "b.idbam, b.cor, "
				+ "pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor, "
				+ "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, "
				+ "pe.efetuado, pe.coletar, pe.emergencia, pe.internado, pe.confirmado, "
				+ "pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade, "
				+ "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico, "
				+ "s.setor AS nome_setor, l.local AS nome_local " + "FROM pacientes p "
				+ "INNER JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "INNER JOIN novointernado n ON n.nbam = b.idbam AND n.unidade = :unidade AND n.alta = FALSE "
				+ "INNER JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso "
				+ "INNER JOIN localcenso l ON l.idsetorlocalcenso = n.local_idsetorlocalcenso "
				+ "INNER JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento "
				+ "INNER JOIN pedidomedico2 pe ON pe.bam = b.idbam " + "WHERE pe.efetuado = FALSE "
				+ "AND pe.local = :local " + "AND pe.emergencia = TRUE " + "AND pe.internado = TRUE "
				+ "AND pe.confirmado = TRUE " + "AND pe.medicoviu = FALSE " + "AND pe.coletar = TRUE "
				+ "AND pe.unidade = :unidade "
				+ "AND :hoje BETWEEN CAST(pe.dt AS DATE) AND (CAST(pe.dt AS DATE) + INTERVAL '2 day')";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).setParameter("local", local)
				.setParameter("unidade", unidade).setParameter("hoje", java.sql.Date.valueOf(hoje)).getResultList();

		List<PedidoMedico2xDto> lista = new ArrayList<>();

		for (Object[] r : results) {
			PedidoMedico2xDto dto = new PedidoMedico2xDto();

			dto.setNome((String) r[0]);
			dto.setIdPaciente(((Number) r[1]).intValue());
			dto.setIdade((r[2] != null) ? ((Number) r[2]).intValue() : null);
			dto.setPacienteespecial((String) r[3]);
			dto.setNascimento((String) r[4]);
			dto.setPai((String) r[5]);
			dto.setMae((String) r[6]);
			dto.setIdBam(((Number) r[7]).intValue());
			dto.setCor((r[8] != null) ? ((Number) r[8]).intValue() : null);
			dto.setIdpedidomedico(((Number) r[9]).intValue());
			dto.setPedido((String) r[10]);
			dto.setLocal((String) r[11]);
			dto.setReal((String) r[12]);
			dto.setMedico((String) r[13]);
			dto.setCrm((String) r[14]);
			dto.setSetor((String) r[15]);
			dto.setCod((String) r[16]);
			dto.setPosicao((r[17] != null) ? ((Number) r[17]).intValue() : 0);
			dto.setEntrada((String) r[18]);
			dto.setObs((String) r[19]);
			dto.setObs2((String) r[20]);
			dto.setObs3((String) r[21]);
			dto.setArquivo((String) r[22]);
			dto.setEfetuado((Boolean) r[23]);
			dto.setColetar((Boolean) r[24]);
			dto.setEmergencia((Boolean) r[25]);
			dto.setInternado((Boolean) r[26]);
			dto.setConfirmado((Boolean) r[27]);
			dto.setMedicoviu((Boolean) r[28]);
			dto.setEas((Boolean) r[29]);
			dto.setDt((r[30] != null) ? r[30].toString() : null);
			dto.setHr((String) r[31]);
			dto.setUnidade((String) r[32]);
			dto.setLiberoucoleta((String) r[33]);
			dto.setLiberouexame((String) r[34]);
			dto.setRestaurou((String) r[35]);
			dto.setIdunico((String) r[36]);

			// novos campos
			dto.setNomeSetor((String) r[37]);
			dto.setNomeLocal((String) r[38]);

			lista.add(dto);
		}

		return lista;
	}

	public void finalizarColetaEmergencia(int bam) {
		String sql = "UPDATE pedidomedico2 set coletar = false where efetuado = false and emergencia = true and internado=true and coletar=true  and bam = :bam and local = 'LABORATORIO'";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();

	}

	// ---------------------INTERNADO COLETA -----------------------
	@SuppressWarnings("unchecked")
	@Transactional(TxType.SUPPORTS)
	public List<PedidoMedico2xDto> coletaInternado(String unidade, String local) {

		LocalDate hoje = LocalDate.now();

		String sql = "SELECT DISTINCT ON (p.nome) "
				+ "p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, p.pai, p.mae, " + "b.idbam, b.cor, "
				+ "pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor, "
				+ "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, "
				+ "pe.efetuado, pe.coletar, pe.emergencia, pe.internado, pe.confirmado, "
				+ "pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade, "
				+ "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico, "
				+ "s.setor AS nome_setor, l.local AS nome_local " + "FROM pacientes p "
				+ "INNER JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "INNER JOIN novointernado n ON n.nbam = b.idbam AND n.unidade = :unidade AND n.alta = FALSE "
				+ "INNER JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso "
				+ "INNER JOIN localcenso l ON l.idsetorlocalcenso = n.local_idsetorlocalcenso "
				+ "INNER JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento "
				+ "INNER JOIN pedidomedico2 pe ON pe.bam = b.idbam " + "WHERE pe.efetuado = FALSE "
				+ "AND pe.local = :local " + "AND pe.emergencia = FALSE " + "AND pe.internado = TRUE "
				+ "AND pe.confirmado = TRUE " + "AND pe.medicoviu = FALSE " + "AND pe.coletar = TRUE "
				+ "AND pe.unidade = :unidade "
				+ "AND :hoje BETWEEN CAST(pe.dt AS DATE) AND (CAST(pe.dt AS DATE) + INTERVAL '2 day')";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).setParameter("local", local)
				.setParameter("unidade", unidade).setParameter("hoje", java.sql.Date.valueOf(hoje)).getResultList();

		List<PedidoMedico2xDto> lista = new ArrayList<>();

		for (Object[] r : results) {
			PedidoMedico2xDto dto = new PedidoMedico2xDto();

			dto.setNome((String) r[0]);
			dto.setIdPaciente(((Number) r[1]).intValue());
			dto.setIdade((r[2] != null) ? ((Number) r[2]).intValue() : null);
			dto.setPacienteespecial((String) r[3]);
			dto.setNascimento((String) r[4]);
			dto.setPai((String) r[5]);
			dto.setMae((String) r[6]);
			dto.setIdBam(((Number) r[7]).intValue());
			dto.setCor((r[8] != null) ? ((Number) r[8]).intValue() : null);
			dto.setIdpedidomedico(((Number) r[9]).intValue());
			dto.setPedido((String) r[10]);
			dto.setLocal((String) r[11]);
			dto.setReal((String) r[12]);
			dto.setMedico((String) r[13]);
			dto.setCrm((String) r[14]);
			dto.setSetor((String) r[15]);
			dto.setCod((String) r[16]);
			dto.setPosicao((r[17] != null) ? ((Number) r[17]).intValue() : 0);
			dto.setEntrada((String) r[18]);
			dto.setObs((String) r[19]);
			dto.setObs2((String) r[20]);
			dto.setObs3((String) r[21]);
			dto.setArquivo((String) r[22]);
			dto.setEfetuado((Boolean) r[23]);
			dto.setColetar((Boolean) r[24]);
			dto.setEmergencia((Boolean) r[25]);
			dto.setInternado((Boolean) r[26]);
			dto.setConfirmado((Boolean) r[27]);
			dto.setMedicoviu((Boolean) r[28]);
			dto.setEas((Boolean) r[29]);
			dto.setDt((r[30] != null) ? r[30].toString() : null);
			dto.setHr((String) r[31]);
			dto.setUnidade((String) r[32]);
			dto.setLiberoucoleta((String) r[33]);
			dto.setLiberouexame((String) r[34]);
			dto.setRestaurou((String) r[35]);
			dto.setIdunico((String) r[36]);

			// novos campos
			dto.setNomeSetor((String) r[37]);
			dto.setNomeLocal((String) r[38]);

			lista.add(dto);
		}

		return lista;
	}

	public void finalizarColetaInternado(int bam) {
		String sql = "UPDATE pedidomedico2 set coletar = false where efetuado = false and emergencia = false and internado=true and coletar=true  and bam = :bam and local = 'LABORATORIO'";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
	}

	// --------------------- COLETA ------------------------------
	/*
	 * @SuppressWarnings("unchecked")
	 * 
	 * @Transactional(TxType.SUPPORTS) public List<PedidoMedico2xDto>
	 * coletaPs(String unidade, String local) { LocalDate hoje = LocalDate.now();
	 * 
	 * String sql = "SELECT DISTINCT ON (p.nome) " +
	 * "p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, p.pai, p.mae, "
	 * + "b.idbam, b.cor, " +
	 * "pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor, "
	 * + "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, " +
	 * "pe.efetuado, pe.coletar, pe.emergencia, pe.internado, pe.confirmado, " +
	 * "pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade, " +
	 * "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico " +
	 * "FROM pacientes p " +
	 * "INNER JOIN bam b ON b.paciente_idpaciente = p.idpaciente " +
	 * "INNER JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento " +
	 * "INNER JOIN pedidomedico2 pe ON pe.bam = b.idbam " +
	 * "WHERE pe.efetuado = FALSE " + " AND t.tratado = 0" +
	 * "AND pe.local = :local " + "AND pe.confirmado = TRUE " +
	 * "AND pe.medicoviu = FALSE " + "AND pe.coletar = TRUE " +
	 * "AND pe.unidade = :unidade " +
	 * "AND :hoje BETWEEN CAST(pe.dt AS DATE) AND (CAST(pe.dt AS DATE) + INTERVAL '2 day')"
	 * ;
	 * 
	 * List<Object[]> results = getEntityManager() .createNativeQuery(sql)
	 * .setParameter("local", local) .setParameter("unidade", unidade)
	 * .setParameter("hoje", java.sql.Date.valueOf(hoje)) .getResultList();
	 * 
	 * List<PedidoMedico2xDto> lista = new ArrayList<>();
	 * 
	 * for (Object[] r : results) { PedidoMedico2xDto dto = new PedidoMedico2xDto();
	 * 
	 * dto.setNome((String) r[0]); dto.setIdPaciente(((Number) r[1]).intValue());
	 * dto.setIdade((r[2] != null) ? ((Number) r[2]).intValue() : null);
	 * dto.setPacienteespecial((String) r[3]); dto.setNascimento((String) r[4]);
	 * dto.setPai((String) r[5]); dto.setMae((String) r[6]); dto.setIdBam(((Number)
	 * r[7]).intValue()); dto.setCor((r[8] != null) ? ((Number) r[8]).intValue() :
	 * null); dto.setIdpedidomedico(((Number) r[9]).intValue());
	 * dto.setPedido((String) r[10]); dto.setLocal((String) r[11]);
	 * dto.setReal((String) r[12]); dto.setMedico((String) r[13]);
	 * dto.setCrm((String) r[14]); dto.setSetor((String) r[15]); dto.setCod((String)
	 * r[16]); dto.setPosicao((r[17] != null) ? ((Number) r[17]).intValue() : 0);
	 * dto.setEntrada((String) r[18]); dto.setObs((String) r[19]);
	 * dto.setObs2((String) r[20]); dto.setObs3((String) r[21]);
	 * dto.setArquivo((String) r[22]); dto.setEfetuado((Boolean) r[23]);
	 * dto.setColetar((Boolean) r[24]); dto.setEmergencia((Boolean) r[25]);
	 * dto.setInternado((Boolean) r[26]); dto.setConfirmado((Boolean) r[27]);
	 * dto.setMedicoviu((Boolean) r[28]); dto.setEas((Boolean) r[29]);
	 * dto.setDt((r[30] != null) ? r[30].toString() : null); dto.setHr((String)
	 * r[31]); dto.setUnidade((String) r[32]); dto.setLiberoucoleta((String) r[33]);
	 * dto.setLiberouexame((String) r[34]); dto.setRestaurou((String) r[35]);
	 * dto.setIdunico((String) r[36]);
	 * 
	 * // novos campos dto.setNomeSetor((String) r[37]); dto.setNomeLocal((String)
	 * r[38]);
	 * 
	 * lista.add(dto); }
	 * 
	 * return lista; }
	 */

	@SuppressWarnings("unchecked")
	@Transactional
	public List<PedidoMedico2xDto> coletaPs(String unidade, String local) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");
		/*
		 * String sql =
		 * "select distinct on (p.nome) p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, b.idbam"
		 * +
		 * ", b.cor, pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor,"
		 * +
		 * "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, pe.efetuado, pe.coletar,"
		 * +
		 * "pe.emergencia, pe.internado, pe.confirmado, pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade,"
		 * +
		 * "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico FROM pacientes p, bam b, pedidomedico2 pe, novointernado n, tratamento t "
		 * +
		 * "where n.pacientes_idpaciente = b.paciente_idpaciente and b.paciente_idpaciente = p.idpaciente and b.idbam = pe.bam and b.tratamento_idtratamento = t.idtratamento and t.tratado = 0 and pe.efetuado = false  and pe.local = '"
		 * + local +
		 * "' and pe.emergencia = false and pe.internado = false and pe.confirmado=true and pe.medicoviu = false and pe.coletar=true and pe.unidade = '"
		 * + unidade + "'    and cast('" + datas +
		 * "' as date) between cast(pe.dt as date ) and (cast(pe.dt as date ) + INTERVAL '2 day' )"
		 * ;
		 * 
		 */

		String sql = "select distinct on (p.nome) p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, b.idbam"
				+ ", b.cor, pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor,"
				+ "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, pe.efetuado, pe.coletar,"
				+ "pe.emergencia, pe.internado, pe.confirmado, pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade,"
				+ "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico FROM pacientes p, bam b, pedidomedico2 pe,  tratamento t "
				+ "where b.paciente_idpaciente = p.idpaciente and b.idbam = pe.bam and b.tratamento_idtratamento = t.idtratamento and t.tratado = 0 and pe.efetuado = false  and pe.local = :local "
				+ "and pe.emergencia = false and pe.internado = false and pe.confirmado=true and pe.medicoviu = false and pe.coletar=true and pe.unidade = :unidade "
				+ "and cast(:datas as date) between cast(pe.dt as date ) and (cast(pe.dt as date ) + INTERVAL '2 day' )";

		List<Object[]> results = getEntityManager().createNativeQuery(sql)
				.setParameter("local", local)
				.setParameter("unidade", unidade)
				.setParameter("datas", datas)
				.getResultList();
		List<PedidoMedico2xDto> pedidoMedico2 = new ArrayList<>();

		for (Object[] result : results) {

			PedidoMedico2xDto dto = new PedidoMedico2xDto();
			dto.setNome((String) result[0]);
			dto.setIdPaciente(((Number) result[1]).intValue());
			dto.setIdade(result[2] != null ? ((Number) result[2]).intValue() : null);
			dto.setPacienteespecial((String) result[3]);
			dto.setNascimento((String) result[4]);
			dto.setIdBam(((Number) result[5]).intValue());
			dto.setCor(result[6] != null ? ((Number) result[6]).intValue() : null);
			dto.setIdpedidomedico(((Number) result[7]).intValue());
			dto.setPedido((String) result[8]);
			dto.setLocal((String) result[9]);
			dto.setReal((String) result[10]);
			dto.setMedico((String) result[11]);
			dto.setCrm((String) result[12]);
			dto.setSetor((String) result[13]);
			dto.setCod((String) result[14]);
			dto.setPosicao(result[15] != null ? ((Number) result[15]).intValue() : 0);
			dto.setEntrada((String) result[16]);
			dto.setObs((String) result[17]);
			dto.setObs2((String) result[18]);
			dto.setObs3((String) result[19]);
			dto.setArquivo((String) result[20]);
			dto.setEfetuado((Boolean) result[21]);
			dto.setColetar((Boolean) result[22]);
			dto.setEmergencia((Boolean) result[23]);
			dto.setInternado((Boolean) result[24]);
			dto.setConfirmado((Boolean) result[25]);
			dto.setMedicoviu((Boolean) result[26]);
			dto.setEas((Boolean) result[27]);
			dto.setDt(result[28] != null ? result[28].toString() : null);
			dto.setHr((String) result[29]);
			dto.setUnidade((String) result[30]);
			dto.setLiberoucoleta((String) result[31]);
			dto.setLiberouexame((String) result[32]);
			dto.setRestaurou((String) result[33]);
			dto.setIdunico((String) result[34]);

			pedidoMedico2.add(dto);

		}

		return pedidoMedico2;

	}

	public void finalizarColetaPs(int bam) {
		String sql = "UPDATE pedidomedico2 set coletar = false where efetuado = false and emergencia = false and internado=false and coletar=true  and bam = :bam  and local = 'LABORATORIO'";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();

	}

//================================================ LABORATORIO ======================================================	

	// PS lab
	public List<PedidoMedico2> filtarBamLabPs(int query1) {
		return find(
				"bam = ?1 and local = 'LABORATORIO' and efetuado = false and emergencia = false and internado=false and coletar=false ",
				query1).list();
	}

	// INTERNADOS lab
	public List<PedidoMedico2> filtarBamLabInternados(int query1) {
		return find(
				"bam = ?1  and local = 'LABORATORIO' and efetuado = false and emergencia = false and internado=true and coletar=false ",
				query1).list();
	}

	// EMERGÊNCIA lab
	public List<PedidoMedico2> filtarBamLabEmergencia(int query1) {
		return find(
				"bam = ?1  and local = 'LABORATORIO' and efetuado = false and emergencia = true and internado=true and coletar=false ",
				query1).list();
	}

	// ---------------------INTERNADO EMERGÊNCIA -----------------------
	@SuppressWarnings("unchecked")
	@Transactional(TxType.SUPPORTS)
	public List<PedidoMedico2xDto> labInternadoEmergencia(String unidade, String local) {

		LocalDate hoje = LocalDate.now();

		String sql = "SELECT DISTINCT ON (p.nome) "
				+ "p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, p.pai, p.mae, " + "b.idbam, b.cor, "
				+ "pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor, "
				+ "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, "
				+ "pe.efetuado, pe.coletar, pe.emergencia, pe.internado, pe.confirmado, "
				+ "pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade, "
				+ "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico, "
				+ "s.setor AS nome_setor, l.local AS nome_local " + "FROM pacientes p "
				+ "INNER JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "INNER JOIN novointernado n ON n.nbam = b.idbam AND n.unidade = :unidade AND n.alta = FALSE "
				+ "INNER JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso "
				+ "INNER JOIN localcenso l ON l.idsetorlocalcenso = n.local_idsetorlocalcenso "
				+ "INNER JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento "
				+ "INNER JOIN pedidomedico2 pe ON pe.bam = b.idbam " + "WHERE pe.efetuado = FALSE "
				+ "AND pe.local = :local " + "AND pe.emergencia = TRUE " + "AND pe.internado = TRUE "
				+ "AND pe.confirmado = TRUE " + "AND pe.medicoviu = FALSE " + "AND pe.coletar = FALSE "
				+ "AND pe.unidade = :unidade "
				+ "AND REPLACE(TRIM(pe.dt), '/', '-') BETWEEN "
				+ "TO_CHAR(CAST(:hoje AS DATE) - INTERVAL '2 day', 'YYYY-MM-DD') "
				+ "AND TO_CHAR(CAST(:hoje AS DATE), 'YYYY-MM-DD')";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).setParameter("local", local)
				.setParameter("unidade", unidade).setParameter("hoje", java.sql.Date.valueOf(hoje)).getResultList();

		List<PedidoMedico2xDto> lista = new ArrayList<>();

		for (Object[] r : results) {
			PedidoMedico2xDto dto = new PedidoMedico2xDto();

			dto.setNome((String) r[0]);
			dto.setIdPaciente(((Number) r[1]).intValue());
			dto.setIdade((r[2] != null) ? ((Number) r[2]).intValue() : null);
			dto.setPacienteespecial((String) r[3]);
			dto.setNascimento((String) r[4]);
			dto.setPai((String) r[5]);
			dto.setMae((String) r[6]);
			dto.setIdBam(((Number) r[7]).intValue());
			dto.setCor((r[8] != null) ? ((Number) r[8]).intValue() : null);
			dto.setIdpedidomedico(((Number) r[9]).intValue());
			dto.setPedido((String) r[10]);
			dto.setLocal((String) r[11]);
			dto.setReal((String) r[12]);
			dto.setMedico((String) r[13]);
			dto.setCrm((String) r[14]);
			dto.setSetor((String) r[15]);
			dto.setCod((String) r[16]);
			dto.setPosicao((r[17] != null) ? ((Number) r[17]).intValue() : 0);
			dto.setEntrada((String) r[18]);
			dto.setObs((String) r[19]);
			dto.setObs2((String) r[20]);
			dto.setObs3((String) r[21]);
			dto.setArquivo((String) r[22]);
			dto.setEfetuado((Boolean) r[23]);
			dto.setColetar((Boolean) r[24]);
			dto.setEmergencia((Boolean) r[25]);
			dto.setInternado((Boolean) r[26]);
			dto.setConfirmado((Boolean) r[27]);
			dto.setMedicoviu((Boolean) r[28]);
			dto.setEas((Boolean) r[29]);
			dto.setDt((r[30] != null) ? r[30].toString() : null);
			dto.setHr((String) r[31]);
			dto.setUnidade((String) r[32]);
			dto.setLiberoucoleta((String) r[33]);
			dto.setLiberouexame((String) r[34]);
			dto.setRestaurou((String) r[35]);
			dto.setIdunico((String) r[36]);

			// novos campos
			dto.setNomeSetor((String) r[37]);
			dto.setNomeLocal((String) r[38]);

			lista.add(dto);
		}

		return lista;
	}

	public void finalizarLabEmergencia(int bam) {
		String sql = "UPDATE pedidomedico2 set efetuado = true, coletar = false where efetuado = false and emergencia = true and internado=true and coletar=false  and bam = :bam  and local = 'LABORATORIO'";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		informarRegistroEfetuadoExameLabInternadoEmergencia(bam);

	}

	public void informarRegistroEfetuadoExameLabInternadoEmergencia(int bam) {
		String sql = "UPDATE registro set resultadoexame = true, resultadolabo= true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();

	}

	// ---------------------INTERNADO -----------------------
	@SuppressWarnings("unchecked")
	@Transactional(TxType.SUPPORTS)
	public List<PedidoMedico2xDto> labInternado(String unidade, String local) {

		LocalDate hoje = LocalDate.now();

		String sql = "SELECT DISTINCT ON (p.nome) "
				+ "p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, p.pai, p.mae, " + "b.idbam, b.cor, "
				+ "pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor, "
				+ "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, "
				+ "pe.efetuado, pe.coletar, pe.emergencia, pe.internado, pe.confirmado, "
				+ "pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade, "
				+ "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico, "
				+ "s.setor AS nome_setor, l.local AS nome_local " + "FROM pacientes p "
				+ "INNER JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "INNER JOIN novointernado n ON n.nbam = b.idbam AND n.unidade = :unidade AND n.alta = FALSE "
				+ "INNER JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso "
				+ "INNER JOIN localcenso l ON l.idsetorlocalcenso = n.local_idsetorlocalcenso "
				+ "INNER JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento "
				+ "INNER JOIN pedidomedico2 pe ON pe.bam = b.idbam " + "WHERE pe.efetuado = FALSE "
				+ "AND pe.local = :local " + "AND pe.emergencia = FALSE " + "AND pe.internado = TRUE "
				+ "AND pe.confirmado = TRUE " + "AND pe.medicoviu = FALSE " + "AND pe.coletar = FALSE "
				+ "AND pe.unidade = :unidade "
				+ "AND :hoje BETWEEN CAST(pe.dt AS DATE) AND (CAST(pe.dt AS DATE) + INTERVAL '2 day')";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).setParameter("local", local)
				.setParameter("unidade", unidade).setParameter("hoje", java.sql.Date.valueOf(hoje)).getResultList();

		List<PedidoMedico2xDto> lista = new ArrayList<>();

		for (Object[] r : results) {
			PedidoMedico2xDto dto = new PedidoMedico2xDto();

			dto.setNome((String) r[0]);
			dto.setIdPaciente(((Number) r[1]).intValue());
			dto.setIdade((r[2] != null) ? ((Number) r[2]).intValue() : null);
			dto.setPacienteespecial((String) r[3]);
			dto.setNascimento((String) r[4]);
			dto.setPai((String) r[5]);
			dto.setMae((String) r[6]);
			dto.setIdBam(((Number) r[7]).intValue());
			dto.setCor((r[8] != null) ? ((Number) r[8]).intValue() : null);
			dto.setIdpedidomedico(((Number) r[9]).intValue());
			dto.setPedido((String) r[10]);
			dto.setLocal((String) r[11]);
			dto.setReal((String) r[12]);
			dto.setMedico((String) r[13]);
			dto.setCrm((String) r[14]);
			dto.setSetor((String) r[15]);
			dto.setCod((String) r[16]);
			dto.setPosicao((r[17] != null) ? ((Number) r[17]).intValue() : 0);
			dto.setEntrada((String) r[18]);
			dto.setObs((String) r[19]);
			dto.setObs2((String) r[20]);
			dto.setObs3((String) r[21]);
			dto.setArquivo((String) r[22]);
			dto.setEfetuado((Boolean) r[23]);
			dto.setColetar((Boolean) r[24]);
			dto.setEmergencia((Boolean) r[25]);
			dto.setInternado((Boolean) r[26]);
			dto.setConfirmado((Boolean) r[27]);
			dto.setMedicoviu((Boolean) r[28]);
			dto.setEas((Boolean) r[29]);
			dto.setDt((r[30] != null) ? r[30].toString() : null);
			dto.setHr((String) r[31]);
			dto.setUnidade((String) r[32]);
			dto.setLiberoucoleta((String) r[33]);
			dto.setLiberouexame((String) r[34]);
			dto.setRestaurou((String) r[35]);
			dto.setIdunico((String) r[36]);

			// novos campos
			dto.setNomeSetor((String) r[37]);
			dto.setNomeLocal((String) r[38]);
			lista.add(dto);
		}

		return lista;
	}

	public void finalizarLabInternado(int bam) {
		String sql = "UPDATE pedidomedico2 set efetuado = true, coletar=false where efetuado = false and emergencia = false and internado=true and coletar=false  and bam = :bam  and local = 'LABORATORIO'";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		informarRegistroEfetuadoExameLabInternado(bam);
	}

	public void informarRegistroEfetuadoExameLabInternado(int bam) {
		String sql = "UPDATE registro set resultadoexame = true, resultadolabo= true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();

	}

	// --------------------- amb ------------------------------
	@SuppressWarnings("unchecked")
	@Transactional(TxType.SUPPORTS)
	public List<PedidoMedico2xDto> labPs(String unidade, String local) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");
		/*
		 * String sql =
		 * "select distinct on (p.nome) p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, b.idbam"
		 * +
		 * ", b.cor, pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor,"
		 * +
		 * "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, pe.efetuado, pe.coletar,"
		 * +
		 * "pe.emergencia, pe.internado, pe.confirmado, pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade,"
		 * +
		 * "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico FROM pacientes p, bam b, pedidomedico2 pe, novointernado n, tratamento t "
		 * +
		 * "where n.pacientes_idpaciente = b.paciente_idpaciente and b.paciente_idpaciente = p.idpaciente and b.idbam = pe.bam and b.tratamento_idtratamento = t.idtratamento and t.tratado = 0 and pe.efetuado = false  and pe.local = '"
		 * + local +
		 * "' and pe.emergencia = false and pe.internado = false and pe.confirmado=true and pe.medicoviu = false and pe.coletar=false and pe.unidade = '"
		 * + unidade + "'    and cast('" + datas +
		 * "' as date) between cast(pe.dt as date ) and (cast(pe.dt as date ) + INTERVAL '2 day' )"
		 * ;
		 */

		String sql = "select distinct on (p.nome) p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, b.idbam"
				+ ", b.cor, pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor,"
				+ "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, pe.efetuado, pe.coletar,"
				+ "pe.emergencia, pe.internado, pe.confirmado, pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade,"
				+ "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico FROM pacientes p, bam b, pedidomedico2 pe,  tratamento t "
				+ "where  b.paciente_idpaciente = p.idpaciente and b.idbam = pe.bam and b.tratamento_idtratamento = t.idtratamento and t.tratado = 0 and pe.efetuado = false  and pe.local = :local "
				+ "and pe.emergencia = false and pe.internado = false and pe.confirmado=true and pe.medicoviu = false and pe.coletar=false and pe.unidade = :unidade "
				+ "and replace(trim(pe.dt), '/', '-') between "
				+ "to_char(cast(:datas as date) - interval '2 day', 'YYYY-MM-DD') "
				+ "and to_char(cast(:datas as date), 'YYYY-MM-DD')";

		List<Object[]> results = getEntityManager().createNativeQuery(sql)
				.setParameter("local", local)
				.setParameter("unidade", unidade)
				.setParameter("datas", datas)
				.getResultList();
		List<PedidoMedico2xDto> pedidoMedico2 = new ArrayList<>();

		for (Object[] result : results) {

			PedidoMedico2xDto dto = new PedidoMedico2xDto();
			dto.setNome((String) result[0]);
			dto.setIdPaciente(((Number) result[1]).intValue());
			dto.setIdade(result[2] != null ? ((Number) result[2]).intValue() : null);
			dto.setPacienteespecial((String) result[3]);
			dto.setNascimento((String) result[4]);
			dto.setIdBam(((Number) result[5]).intValue());
			dto.setCor(result[6] != null ? ((Number) result[6]).intValue() : null);
			dto.setIdpedidomedico(((Number) result[7]).intValue());
			dto.setPedido((String) result[8]);
			dto.setLocal((String) result[9]);
			dto.setReal((String) result[10]);
			dto.setMedico((String) result[11]);
			dto.setCrm((String) result[12]);
			dto.setSetor((String) result[13]);
			dto.setCod((String) result[14]);
			dto.setPosicao(result[15] != null ? ((Number) result[15]).intValue() : 0);
			dto.setEntrada((String) result[16]);
			dto.setObs((String) result[17]);
			dto.setObs2((String) result[18]);
			dto.setObs3((String) result[19]);
			dto.setArquivo((String) result[20]);
			dto.setEfetuado((Boolean) result[21]);
			dto.setColetar((Boolean) result[22]);
			dto.setEmergencia((Boolean) result[23]);
			dto.setInternado((Boolean) result[24]);
			dto.setConfirmado((Boolean) result[25]);
			dto.setMedicoviu((Boolean) result[26]);
			dto.setEas((Boolean) result[27]);
			dto.setDt(result[28] != null ? result[28].toString() : null);
			dto.setHr((String) result[29]);
			dto.setUnidade((String) result[30]);
			dto.setLiberoucoleta((String) result[31]);
			dto.setLiberouexame((String) result[32]);
			dto.setRestaurou((String) result[33]);
			dto.setIdunico((String) result[34]);

			pedidoMedico2.add(dto);

		}

		return pedidoMedico2;

	}
	/*
	 * @SuppressWarnings("unchecked")
	 * 
	 * @Transactional public List<PedidoMedico2xDto> pedido2Ps(String unidade,
	 * String local) { LocalDate data = LocalDate.now(); DateTimeFormatter
	 * formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd"); String datas =
	 * data.format(formatador); // Correção: removido .toString()
	 * 
	 * String sql =
	 * "SELECT DISTINCT ON (p.nome) p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, b.idbam, b.cor, pe.idpedidomedico, pe.pedido, pe.local, "
	 * +
	 * " pe.real, pe.medico, pe.crm, pe.setor, pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, pe.efetuado, pe.coletar, pe.emergencia, "
	 * +
	 * " pe.internado, pe.confirmado, pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade, pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico, p.pai, "
	 * + " p.mae FROM pacientes p " +
	 * " INNER JOIN bam b ON b.paciente_idpaciente = p.idpaciente " +
	 * " INNER JOIN pedidomedico2 pe ON b.idbam = pe.bam " +
	 * " LEFT JOIN novointernado n ON n.pacientes_idpaciente = b.paciente_idpaciente "
	 * + " INNER JOIN tratamento t ON b.tratamento_idtratamento = t.idtratamento " +
	 * " WHERE t.tratado = 0 AND pe.efetuado = FALSE AND pe.local = :medicacao " +
	 * " AND pe.confirmado = TRUE AND pe.medicoviu = FALSE AND pe.unidade = :unidade "
	 * +
	 * " AND CAST(:data AS DATE) BETWEEN CAST(pe.dt AS DATE) AND (CAST(pe.dt AS DATE) + INTERVAL '2 day')"
	 * ;
	 * 
	 * List<PedidoMedico2xDto> pedidoMedico2 = new ArrayList<>();
	 * 
	 * try { Query query = getEntityManager().createNativeQuery(sql);
	 * query.setParameter("unidade", unidade); query.setParameter("medicacao",
	 * local); query.setParameter("data", datas); // Correção: ajustado nome do
	 * parâmetro
	 * 
	 * List<Object[]> results = query.getResultList();
	 * 
	 * for (Object[] result : results) { PedidoMedico2xDto dto = new
	 * PedidoMedico2xDto(); dto.setNome((String) result[0]); dto.setIdPaciente((int)
	 * result[1]); dto.setIdade((Integer) result[2]);
	 * dto.setPacienteespecial((String) result[3]); dto.setNascimento((String)
	 * result[4]); dto.setIdBam((int) result[5]); dto.setCor((Integer) result[6]);
	 * dto.setIdpedidomedico((int) result[7]); dto.setPedido((String) result[8]);
	 * dto.setLocal((String) result[9]); dto.setReal((String) result[10]);
	 * dto.setMedico((String) result[11]); dto.setCrm((String) result[12]);
	 * dto.setSetor((String) result[13]); dto.setCod((String) result[14]);
	 * dto.setPosicao((Integer) result[15]); dto.setEntrada((String) result[16]);
	 * dto.setObs((String) result[17]); dto.setObs2((String) result[18]);
	 * dto.setObs3((String) result[19]); dto.setArquivo((String) result[20]);
	 * dto.setEfetuado((Boolean) result[21]); dto.setColetar((Boolean) result[22]);
	 * dto.setEmergencia((Boolean) result[23]); dto.setInternado((Boolean)
	 * result[24]); dto.setConfirmado((Boolean) result[25]);
	 * dto.setMedicoviu((Boolean) result[26]); dto.setEas((Boolean) result[27]);
	 * dto.setDt((String) result[28]); dto.setHr((String) result[29]);
	 * dto.setUnidade((String) result[30]); dto.setLiberoucoleta((String)
	 * result[31]); dto.setLiberouexame((String) result[32]);
	 * dto.setRestaurou((String) result[33]); dto.setIdunico((String) result[34]);
	 * dto.setPai((String) result[35]); dto.setMae((String) result[36]);
	 * 
	 * pedidoMedico2.add(dto); } } catch (Exception e) {
	 * System.err.println("Erro ao executar a query: " + e.getMessage()); }
	 * 
	 * return pedidoMedico2; }
	 * 
	 */

	@SuppressWarnings("unchecked")
	@Transactional(TxType.SUPPORTS)
	public List<PedidoMedico2xDto> pedido2Ps(String unidade, String local) {

		LocalDate hoje = LocalDate.now();

		String sql = "";
		List<Object[]> results;
		if (local.equals("MEDICACAO") ) {
			sql = "SELECT DISTINCT ON (p.nome) "
					+ "p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, p.pai, p.mae, "
					+ "b.idbam, b.cor, "
					+ "pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor, "
					+ "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, "
					+ "pe.efetuado, pe.coletar, pe.emergencia, pe.internado, pe.confirmado, "
					+ "pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade, "
					+ "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico, "
					+ "s.setor AS nome_setor, l.local AS nome_local, "
					+ "EXISTS (SELECT 1 FROM pedidomedico2 pe_lab WHERE pe_lab.bam = b.idbam AND pe_lab.local = 'LABORATORIO') AS tem_pedido_laboratorio "
					+ "FROM pacientes p "
					+ "INNER JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
					+ "LEFT JOIN novointernado n ON n.nbam = b.idbam and n.unidade = :unidade and n.alta = FALSE "
					+ "LEFT JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso "
					+ "LEFT JOIN localcenso l ON l.idsetorlocalcenso = n.local_idsetorlocalcenso "
					+ "INNER JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento "
					+ "INNER JOIN pedidomedico2 pe ON pe.bam = b.idbam WHERE  pe.efetuado = FALSE "
					+ "AND (pe.local = 'MEDICACAO' OR pe.local = 'MEDICAMENTO') " + "AND pe.unidade = :unidade "
					+ "ORDER BY p.nome, b.idbam DESC, pe.idpedidomedico DESC";

			try {
				results = getEntityManager().createNativeQuery(sql).setParameter("unidade", unidade).getResultList();
			} catch (Exception e) {
				System.err.println("Erro ao executar pedido2Ps: " + e.getMessage());
				e.printStackTrace();
				throw e;
			}

		} else {

			sql = "SELECT DISTINCT ON (p.nome) "
					+ "p.nome, p.idpaciente, p.idade, p.pacienteespecial, p.nascimento, p.pai, p.mae, "
					+ "b.idbam, b.cor, "
					+ "pe.idpedidomedico, pe.pedido, pe.local, pe.real, pe.medico, pe.crm, pe.setor, "
					+ "pe.cod, pe.posicao, pe.entrada, pe.obs, pe.obs2, pe.obs3, pe.arquivo, "
					+ "pe.efetuado, pe.coletar, pe.emergencia, pe.internado, pe.confirmado, "
					+ "pe.medicoviu, pe.eas, pe.dt, pe.hr, pe.unidade, "
					+ "pe.liberoucoleta, pe.liberouexame, pe.restaurou, pe.idunico, "
					+ "s.setor AS nome_setor, l.local AS nome_local, "
					+ "EXISTS (SELECT 1 FROM pedidomedico2 pe_lab WHERE pe_lab.bam = b.idbam AND pe_lab.local = 'LABORATORIO') AS tem_pedido_laboratorio "
					+ "FROM pacientes p "
					+ "INNER JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
					+ "LEFT JOIN novointernado n ON n.nbam = b.idbam and n.unidade = :unidade and n.alta = FALSE "
					+ "LEFT JOIN setorcenso s ON s.idsetorcenso = n.setor_idsetorcenso "
					+ "LEFT JOIN localcenso l ON l.idsetorlocalcenso = n.local_idsetorlocalcenso "
					+ "INNER JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento "
					+ "INNER JOIN pedidomedico2 pe ON pe.bam = b.idbam " + "WHERE " + " pe.efetuado = FALSE "
					+ "AND pe.local = :local " + "AND pe.unidade = :unidade "
					+ "ORDER BY p.nome, b.idbam DESC, pe.idpedidomedico DESC";

			try {
				results = getEntityManager().createNativeQuery(sql).setParameter("local", local)
						.setParameter("unidade", unidade).getResultList();
			} catch (Exception e) {
				System.err.println("Erro ao executar pedido2Ps: " + e.getMessage());
				e.printStackTrace();
				throw e;
			}

		}

		System.out.println("Resultados encontrados: " + results.size() + " - " + sql);
		List<PedidoMedico2xDto> lista = new ArrayList<>();

		for (Object[] r : results) {
			PedidoMedico2xDto dto = new PedidoMedico2xDto();

			dto.setNome((String) r[0]);
			dto.setIdPaciente(((Number) r[1]).intValue());
			dto.setIdade((r[2] != null) ? ((Number) r[2]).intValue() : null);
			dto.setPacienteespecial((String) r[3]);
			dto.setNascimento((String) r[4]);
			dto.setPai((String) r[5]);
			dto.setMae((String) r[6]);
			dto.setIdBam(((Number) r[7]).intValue());
			dto.setCor((r[8] != null) ? ((Number) r[8]).intValue() : null);
			dto.setIdpedidomedico(((Number) r[9]).intValue());
			dto.setPedido((String) r[10]);
			dto.setLocal((String) r[11]);
			dto.setReal((String) r[12]);
			dto.setMedico((String) r[13]);
			dto.setCrm((String) r[14]);
			dto.setSetor((String) r[15]);
			dto.setCod((String) r[16]);
			dto.setPosicao((r[17] != null) ? ((Number) r[17]).intValue() : 0);
			dto.setEntrada((String) r[18]);
			dto.setObs((String) r[19]);
			dto.setObs2((String) r[20]);
			dto.setObs3((String) r[21]);
			dto.setArquivo((String) r[22]);
			dto.setEfetuado((Boolean) r[23]);
			dto.setColetar((Boolean) r[24]);
			dto.setEmergencia((Boolean) r[25]);
			dto.setInternado((Boolean) r[26]);
			dto.setConfirmado((Boolean) r[27]);
			dto.setMedicoviu((Boolean) r[28]);
			dto.setEas((Boolean) r[29]);
			dto.setDt((r[30] != null) ? r[30].toString() : null);
			dto.setHr((String) r[31]);
			dto.setUnidade((String) r[32]);
			dto.setLiberoucoleta((String) r[33]);
			dto.setLiberouexame((String) r[34]);
			dto.setRestaurou((String) r[35]);
			dto.setIdunico((String) r[36]);

			// novos campos
			dto.setNomeSetor((String) r[37]);
			dto.setNomeLocal((String) r[38]);
			dto.setTemPedidoLaboratorio((Boolean) r[39]);

			lista.add(dto);
		}

		return lista;
	}

	// Pacientes cadastrados pela recepcao direto no setor (ex.: RAIO X, TOMOGRAFIA),
	// sem pedido medico (pedidomedico2) associado ainda. Respeita o mesmo gate de
	// triagem usado no restante do sistema: se o setor exige triagem (triar=true),
	// so aparece depois de avaliado=1; senao, aparece direto.
	@SuppressWarnings("unchecked")
	public List<PedidoMedico2xDto> pacientesDiretoSetor(String unidade, String setor) {
		String subConsultaTriar = "(select distinct on (registro.setor) registro.setor from setoresunidade "
				+ "inner join registro on registro.setor = setoresunidade.setor "
				+ "where setoresunidade.triar = true and setoresunidade.siglaunidade = :unidade)";

		String sql = "SELECT DISTINCT ON (p.nome) p.nome, p.idade, p.pacienteespecial, b.idbam, b.cor, "
				+ "CAST(b.data AS TEXT) as dt, r.setor "
				+ "FROM pacientes p "
				+ "JOIN bam b ON b.paciente_idpaciente = p.idpaciente "
				+ "JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "JOIN tratamento t ON b.tratamento_idtratamento = t.idtratamento "
				+ "WHERE UPPER(r.setor) LIKE UPPER(:setor) || '%' AND b.unidade = :unidade AND t.tratado = 0 AND r.eletivo = false "
				+ "AND CASE WHEN (r.setor IN " + subConsultaTriar + ") THEN a.avaliado = 1 ELSE TRUE END "
				+ "AND NOT EXISTS (SELECT 1 FROM pedidomedico2 pe WHERE pe.bam = b.idbam AND UPPER(pe.local) LIKE UPPER(:setor) || '%' AND pe.efetuado = false) "
				+ "ORDER BY p.nome";

		List<Object[]> results = getEntityManager().createNativeQuery(sql)
				.setParameter("unidade", unidade)
				.setParameter("setor", setor)
				.getResultList();

		List<PedidoMedico2xDto> lista = new ArrayList<>();
		for (Object[] r : results) {
			PedidoMedico2xDto dto = new PedidoMedico2xDto();
			dto.setNome((String) r[0]);
			dto.setIdade(r[1] != null ? ((Number) r[1]).intValue() : null);
			dto.setPacienteespecial((String) r[2]);
			dto.setIdBam(((Number) r[3]).intValue());
			dto.setCor(r[4] != null ? ((Number) r[4]).intValue() : null);
			dto.setDt((String) r[5]);
			dto.setSetor((String) r[6]);
			dto.setLocal((String) r[6]);
			lista.add(dto);
		}
		return lista;
	}

	// Finaliza (fecha) o atendimento de um paciente que veio direto da recepcao
	// para o setor (RAIO X/TOMOGRAFIA), sem pedido medico. Fecha somente o
	// tratamento deste BAM especifico (nao o paciente inteiro, que pode ter
	// outros BAMs/visitas em aberto).
	@Transactional
	public void finalizarExameDiretoSetor(int idbam) {
		String sql = "UPDATE tratamento SET tratado = 1 WHERE idtratamento = "
				+ "(SELECT tratamento_idtratamento FROM bam WHERE idbam = :idbam)";
		entityManager.createNativeQuery(sql).setParameter("idbam", idbam).executeUpdate();
	}

	public void finalizarLabPs(int bam, String user) {
		String sql = "UPDATE pedidomedico2 set efetuado = true, coletar = false, liberouexame = :user where efetuado = false and emergencia = false and internado=false and coletar=false  and bam = :bam  and local = 'LABORATORIO'";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).setParameter("user", user).executeUpdate();
		informarRegistroEfetuadoExameLab(bam);
	}

	public void informarRegistroEfetuadoExameLab(int bam) {
		String sql = "UPDATE registro set resultadoexame = true, resultadolabo=true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();

	}

	public List<PedidoMedico2> filtarBamExamePs(int query1, String local) {
		if (local.equals("MEDICACAO") || local.equals("MEDICAMENTO")) {
			if (local.equals("MEDICACAO") || local.equals("MEDICAMENTO")) {
				return find("bam = ?1 and (local = 'MEDICACAO' or local = 'MEDICAMENTO') and efetuado = false ", query1)
						.list();
			}
		}

		if (local.equals("MEDICACAO2") || local.equals("MEDICAMENTO2")) {
			if (local.equals("MEDICACAO2") || local.equals("MEDICAMENTO2")) {
				return find("bam = ?1 and (local = 'MEDICACAO2' or local = 'MEDICAMENTO2') and efetuado = false ",
						query1).list();
			}
		}

		return find("bam = ?1 and local = ?2 and efetuado = false ", query1, local).list();

	}

	public void atualizarAoMedicoRespostaExames(int bam, String resposta, String local) {
		if(local.equals("MEDICACAO") || local.equals("MEDICAMENTO")) {
			String sql = "UPDATE pedidomedico2 set efetuado=true, obs2 = :resposta "
					+ "from bam where efetuado = false and bam = :bam "
					+ "and local in ('MEDICACAO', 'MEDICAMENTO')";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).setParameter("resposta", resposta)
					.executeUpdate();
			informarRegistroEfetuadoExameLocal(bam, "MEDICACAO");
			return;
		}
		if(local.equals("MEDICACAO2") || local.equals("MEDICAMENTO2")) {
			String sql = "UPDATE pedidomedico2 set efetuado=true, obs2 = :resposta "
					+ "from bam where efetuado = false and bam = :bam "
					+ "and local in ('MEDICACAO2', 'MEDICAMENTO2')";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).setParameter("resposta", resposta)
					.executeUpdate();
			informarRegistroEfetuadoExameLocal(bam, "MEDICACAO2");
			return;
		}
		
		
		String sql = "UPDATE pedidomedico2 set efetuado=true,  obs2 = :resposta from bam where efetuado = false and bam = :bam and local = :local";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).setParameter("resposta", resposta)
				.setParameter("local", local).executeUpdate();
		informarRegistroEfetuadoExameLocal(bam, local);
	}

	public void informarRegistroEfetuadoExameLocal(int bam, String local) {
		String sql = "";
		if (local.equals("LABORATORIO")) {
			sql = "UPDATE registro set resultadoexame = true, resultadolabo=true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		}
		if (local.equals("RAIO X")) {
			sql = "UPDATE registro set resultadorx = true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		}
		if (local.equals("TOMOGRAFIA")) {
			sql = "UPDATE registro set resultadotomo = true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		}
		if (local.equals("MEDICACAO")) {
			sql = "UPDATE registro set resultadomed = true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		}
		if (local.equals("MEDICACAO2")) {
			sql = "UPDATE registro set resultadomed = true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		}
		if (local.equals("MEDICACAO3")) {
			sql = "UPDATE registro set resultadomed = true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		}
		if (local.equals("MEDICAMENTO")) {
			sql = "UPDATE registro set resultadomed = true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		}
		if (local.equals("MEDICAMENTO2")) {
			sql = "UPDATE registro set resultadomed = true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		}
		if (local.equals("ENFERMAGEM")) {
			sql = "UPDATE registro set resultadoenfe = true from bam where bam.registro_idregistro = registro.idregistro and bam.idbam = :bam";
			entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
		}
	}

	// Resposta RX, TOMO, MED e ENFE
	// valores distintos
	public List<PedidoMedico2> filtrarBamPedidoMedico2(int query1, String onde) {
		List<PedidoMedico2> resultados;

		if ("MEDICACAO".equalsIgnoreCase(onde)) {
			resultados = find("bam = ?1 and (local = 'MEDICACAO' or local = 'MEDICACAO2') "
					+ "order by cast(dt as date) desc, cast(hr as time) desc", query1).list();
		} else {
			resultados = find("bam = ?1 and local = ?2 order by cast(dt as date) desc, cast(hr as time) desc", query1,
					onde).list();
		}

		Set<String> seen = new HashSet<>();
		return resultados.stream().filter(p -> seen.add(p.getBam() + p.getDt() + p.getHr()))
				.collect(Collectors.toList());
	}
	/*
	 * @SuppressWarnings("unchecked") public List<PedidoMedico2>
	 * filtarBamListaLocal(int bam, String obs, String local) { String
	 * obsNormalizado = obs.replaceAll("\\s+", "").trim();
	 * 
	 * if (local.equals("MEDICACAO") || local.equals("MEDICACAO2")) { return
	 * getEntityManager() .createNativeQuery( "SELECT * FROM pedidomedico2 " +
	 * "WHERE bam = :bam " +
	 * "AND REPLACE(REGEXP_REPLACE(TRIM(obs2), '\\s+', ' ', 'g'), ' ', '') = :obs "
	 * + "AND (local = 'MEDICACAO' OR local = 'MEDICACAO2') " +
	 * "AND efetuado = true", PedidoMedico2.class) .setParameter("bam",
	 * bam).setParameter("obs", obsNormalizado).getResultList(); } else { return
	 * getEntityManager() .createNativeQuery("SELECT * FROM pedidomedico2 " +
	 * "WHERE bam = :bam " +
	 * "AND REPLACE(REGEXP_REPLACE(TRIM(obs2), '\\s+', ' ', 'g'), ' ', '') = :obs "
	 * + "AND local = :local " + "AND efetuado = true", PedidoMedico2.class)
	 * .setParameter("bam", bam).setParameter("obs",
	 * obsNormalizado).setParameter("local", local) .getResultList(); } }
	 */

	@SuppressWarnings("unchecked")
	public List<PedidoMedico2> filtarBamListaLocal(PedidoMedico2yDto dto) {
		String obsNormalizado = dto.getObs().replaceAll("\\s+", "").trim();
		String local = dto.getLocal();
		int bam = dto.getBam();
		String data = dto.getData();
		String hora = dto.getHora();

		if (local.equals("MEDICACAO") || local.equals("MEDICACAO2")) {
			return getEntityManager()
					.createNativeQuery("SELECT * FROM pedidomedico2 " + "WHERE bam = :bam "
							+ " and cast(dt as date) = cast(:data as date) and cast(hr as time) = cast(:hora as time) "
							+ "AND (local = 'MEDICACAO' OR local = 'MEDICACAO2') " + "AND efetuado = true",
							PedidoMedico2.class)
					.setParameter("bam", bam).setParameter("data", data).setParameter("hora", hora).getResultList();
		} else {
			return getEntityManager()
					.createNativeQuery("SELECT * FROM pedidomedico2 " + "WHERE bam = :bam "
							+ " and cast(dt as date) = cast(:data as date) and cast(hr as time) = cast(:hora as time) "
							+ "AND local = :local " + "AND efetuado = true", PedidoMedico2.class)
					.setParameter("bam", bam).setParameter("data", data).setParameter("hora", hora)
					.setParameter("local", local).getResultList();
		}
	}

}

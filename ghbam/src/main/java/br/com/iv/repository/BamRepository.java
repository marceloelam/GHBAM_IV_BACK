package br.com.iv.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.iv.entity.Bam;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class BamRepository implements PanacheRepository<Bam> {
	@Inject
	EntityManager entityManager;

	public Bam filtrarPorUnicoBam(int query) {
		return find("idbam = ?1", query).firstResult();
	}

	public Bam filtrarPorUnicoRegistro(int query) {
		return find("registro.idregistro = ?1", query).firstResult();
	}

	public Bam filtrarPorUnicoBamControle(String query) {
		return find("controle = ?1", query).firstResult();
	}

	public List<Bam> filtrarBamPelaUnidade(String query1, LocalDate d1, LocalDate d2) {
		LocalDate data1 = d1;
		LocalDate data2 = d2;
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		String dat1 = data1.format(formatador).toString();
		dat1 = dat1.replaceAll("/", "-");
		String dat2 = data2.format(formatador).toString();
		dat2 = dat2.replaceAll("/", "-");

		return find("unidade = ?1 and cast(data as date) BETWEEN cast(?2 as date) and cast(?3 as date) order by idbam",
				query1, dat1, dat2).list();
	}

	public List<Bam> filtrarBamEUnidade(int query1, String query2) {
		return find("idbam = ?1 and unidade = ?2  order by idbam", query1, query2).list();
	}

	public List<Bam> filtrarPorIdDoPciente(int query1) {
		return find("paciente.idpaciente = ?1 order by idbam desc, unidade", query1).list();
	}

	public Bam filtrarPorIdDoPcienteUltimoBam(int query1, String unidade) {
		return find("paciente.idpaciente = ?1 and unidade = ?2 order by idbam desc, unidade", query1, unidade)
				.firstResult();
	}

	public void atualizarCorNoBam(int idbam, int cor, int idservidor) {
		String sql = "";
		// String sql = "UPDATE bam set cor = :cor, servidor_idservidor = :idservidor
		// where idbam = :idbam";
		if (idservidor == 0)
			sql = "UPDATE bam set cor = :cor, transferido = true where idbam = :idbam";
		else
			sql = "UPDATE bam set cor = :cor where idbam = :idbam";

		entityManager.createNativeQuery(sql).setParameter("cor", cor).setParameter("idbam", idbam).executeUpdate();
		// .setParameter("idservidor", idservidor)
	}

	public void atualizarEmAtendimento(int idbam, boolean ematendimento) {

		String sql = "UPDATE bam set ematendimento = :ematendimento where idbam = :idbam";
		entityManager.createNativeQuery(sql).setParameter("ematendimento", ematendimento).setParameter("idbam", idbam)
				.executeUpdate();
		if (ematendimento) {
			// Primeira abertura pelo medico (V216): base do BI da PA, nao e sobrescrita.
			entityManager.createNativeQuery("UPDATE bam set atendimento_medico_em = COALESCE(atendimento_medico_em, :agora) where idbam = :idbam")
					.setParameter("agora", java.time.LocalDateTime.now()).setParameter("idbam", idbam)
					.executeUpdate();
		}
	}

	public void atualizarOrigemDestinoTransferencia(int idbam, String origem, String destino) {
		String sql = "UPDATE bam set transorigem = :origem, unidade = :destino, transferido = true where idbam = :idbam";
		entityManager.createNativeQuery(sql).setParameter("origem", origem).setParameter("destino", destino)
				.setParameter("idbam", idbam).executeUpdate();
		atualizarUnidadeAvaliacao(idbam, destino);
		atualizarPedidosPendentesUnidade(idbam, destino);
	}

    
	//Mudar unidade do bam
	public void atualizarUnidadeAvaliacao(int idbam, String destino) {
		String sql = "UPDATE avaliacao set unidade = :destino where idavaliacao in (select avaliacao_idavaliacao from bam where idbam = :idbam )";
		entityManager.createNativeQuery(sql).setParameter("destino", destino).setParameter("idbam", idbam)
				.executeUpdate();
	}

	/**
	 * A fila operacional de exames e medicações é filtrada pela unidade do pedido.
	 * Somente itens ainda não executados acompanham o BAM transferido; os já
	 * realizados permanecem identificados na unidade onde ocorreram.
	 */
	void atualizarPedidosPendentesUnidade(int idbam, String destino) {
		String sql = "UPDATE pedidomedico2 set unidade = :destino where bam = :idbam and efetuado = false";
		entityManager.createNativeQuery(sql).setParameter("destino", destino).setParameter("idbam", idbam)
				.executeUpdate();
	}

	public void atualizarMarcadorTransferencia(int idbam, boolean transferido) {
		String sql = "UPDATE bam set transferido = :transferido where idbam = :idbam";
		entityManager.createNativeQuery(sql).setParameter("transferido", transferido).setParameter("idbam", idbam)
				.executeUpdate();
	}
	
	@SuppressWarnings("unchecked")
	public List<Bam> filtarMapaGrafico(LocalDate d1, LocalDate d2, String unidade) {
		String sql = "SELECT DISTINCT (p.nome), b.* FROM bam b "
				+ "INNER JOIN pacientes p ON b.paciente_idpaciente = p.idpaciente "
				+ "INNER JOIN avaliacao a ON b.avaliacao_idavaliacao = a.idavaliacao "
				+ "INNER JOIN registro r ON b.registro_idregistro = r.idregistro "
				+ "WHERE to_date(a.data, 'YYYY/MM/DD') BETWEEN :dataInicio AND :dataFim "
				+ "AND b.unidade = :unidade";
		return getEntityManager().createNativeQuery(sql, Bam.class)
				.setParameter("dataInicio", d1)
				.setParameter("dataFim", d2)
				.setParameter("unidade", unidade)
				.getResultList();

		// return find("replace(nome, ' ', '') like ?1", n_nome +
		// "%").page(Page.ofSize(10)).list();
	}


}

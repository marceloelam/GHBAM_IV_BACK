package br.com.iv.repository;

import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;

import br.com.iv.entity.NovoInternado;
import br.com.iv.service.AltaadmnirService;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class NovoInternadoRepository implements PanacheRepository<NovoInternado> {
	@Inject
	EntityManager entityManager;

	public List<NovoInternado> filtarPorCbo(String query) {
		return find("upper(titulo) like ?1", "%" + query.toUpperCase() + "%").list();
	}

	public List<NovoInternado> listarTodosOsRegistrosAtivos(String unidade) {
		return find("alta = false and unidade = ?1", unidade).list();
	}

	public NovoInternado filtrarPorUnico(int query) {
		return find("nbam = ?1", query).firstResult();
	}

	public void alterarLeito(int idbam, int idsetor, int idleito, String quem) {
		String sql = "UPDATE novointernado set setor_idsetorcenso = :idsetor, local_idsetorlocalcenso = :idleito, quemleitoalterou= :quem where nbam = :idbam";
		entityManager.createNativeQuery(sql).setParameter("idbam", idbam).setParameter("idsetor", idsetor)
				.setParameter("idleito", idleito).setParameter("quem", quem).executeUpdate();
	}

	public void alterarAltaDoPaciente(int idbam, LocalDate acao, boolean alta, String quem, String motivo, int idnovo) {
		String sql = "";
		quem = quem.replace(":", "-");
		if (motivo.equals("ALTA ADM")) {
			// Cada alta ADM é um registro próprio (uma por data). Antes o id era o do leito:
			// a segunda alta dava 500 e, depois, sobrescrevia a primeira.
			// Mesma data no mesmo leito (ex.: clique duplo) só atualiza.
			int atualizadas = entityManager.createNativeQuery(
					"UPDATE novointernadoadm SET quemalocou = :quem WHERE nbam = :idbam "
							+ "AND motivosaida = 'ALTA ADM' AND cast(datadaacao as date) = :acao")
					.setParameter("quem", quem).setParameter("idbam", idbam).setParameter("acao", acao)
					.executeUpdate();
			if (atualizadas == 0) {
				// A coluna não tem sequência: serializa a geração do id dentro da transação.
				entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('novointernadoadm'))) trava").getSingleResult();
				sql = "	INSERT INTO novointernadoadm (idninternadoadm, agente, alta, datadaacao, datainsercaoser, diagnostico, diainternacao, especialidade, internado, "
						+ "leitoocupado, motivosaida, nbam, pendencia, prontuario, quemalocou, quemdeualta, quemexcluiu, quemleitoalterou, tempoDias, tipo, tipo_alocacao, "
						+ "unidade, bam_idbam, local_idsetorlocalcenso, pacientes_idpaciente, setor_idsetorcenso) "
						+ " SELECT (SELECT coalesce(max(idninternadoadm), 0) + 1 FROM novointernadoadm), agente, :alta, :acao, datainsercaoser, diagnostico, diainternacao, especialidade, internado, leitoocupado, :motivo, :idbam, pendencia, prontuario, :quem, quemdeualta, quemexcluiu, quemleitoalterou, tempoDias, tipo, tipo_alocacao, unidade, :idbam, local_idsetorlocalcenso, pacientes_idpaciente, setor_idsetorcenso "
						+ " FROM novointernado 	WHERE novointernado.idninternado = :idnovo";
				entityManager.createNativeQuery(sql).setParameter("idbam", idbam).setParameter("alta", alta)
						.setParameter("acao", acao).setParameter("quem", quem).setParameter("motivo", motivo)
						.setParameter("idnovo", idnovo).executeUpdate();
			}
		} else {
			sql = "UPDATE novointernado set nbam = :idbam, alta = :alta, datadaacao = :acao, quemdeualta= :quem, motivosaida = :motivo where idninternado = :idnovo";
			entityManager.createNativeQuery(sql).setParameter("idbam", idbam).setParameter("alta", alta)
					.setParameter("acao", acao).setParameter("quem", quem).setParameter("motivo", motivo)
					.setParameter("idnovo", idnovo).executeUpdate();
		}

		// Alta final do leito fecha também a internação aberta do mesmo BAM (antes ficava aberta:
		// ~3.400 casos, inclusive óbitos). ALTA ADM não fecha: o paciente segue internado.
		if (alta && !motivo.equals("ALTA ADM") && !motivo.equals("-")) {
			entityManager.createNativeQuery(
					"UPDATE internacao SET datasaida = :acao, motivodaalta = :motivo, medicodeualta = :quem "
							+ "WHERE bam = :bam AND datasaida IS NULL AND motivodaalta = '-'")
					.setParameter("acao", acao).setParameter("motivo", motivo).setParameter("quem", quem)
					.setParameter("bam", String.valueOf(idbam))
					.executeUpdate();
		}

		if (motivo.equals("ALTA ADM")) {
			AltaadmnirService altaadmnirService = new AltaadmnirService();
			
			altaadmnirService.salvarAltaadmnir(idbam, quem);

		}

	}

	// Alta de todas as internações ativas de um leito que foi bloqueado ou excluído.
	public long contarAtivosPorLeito(int idleito) {
		Number total = (Number) entityManager.createNativeQuery(
				"SELECT COUNT(*) FROM novointernado WHERE local_idsetorlocalcenso = :idleito AND alta = false")
				.setParameter("idleito", idleito).getSingleResult();
		return total == null ? 0 : total.longValue();
	}

	// Devolve os BAMs que receberam alta, para o log de auditoria.
	@SuppressWarnings("unchecked")
	public List<Integer> darAltaPorLeito(int idleito, LocalDate acao, String quem, String motivo) {
		List<Number> bams = entityManager.createNativeQuery(
				"SELECT nbam FROM novointernado WHERE local_idsetorlocalcenso = :idleito AND alta = false")
				.setParameter("idleito", idleito).getResultList();
		if (bams.isEmpty()) {
			return List.of();
		}
		String sql = "UPDATE novointernado SET alta = true, datadaacao = :acao, quemdeualta = :quem, motivosaida = :motivo "
				+ "WHERE local_idsetorlocalcenso = :idleito AND alta = false";
		entityManager.createNativeQuery(sql).setParameter("acao", acao)
				.setParameter("quem", quem.replace(":", "-")).setParameter("motivo", motivo)
				.setParameter("idleito", idleito).executeUpdate();
		return bams.stream().map(Number::intValue).toList();
	}

	public void deletarAlocacao(int idbam) {
		String sql = "DELETE from  novointernado  where nbam = :idbam";
		entityManager.createNativeQuery(sql).setParameter("idbam", idbam).executeUpdate();
	}

	public int cancelarAltaNoCenso(int idbam) {
		String sql = "UPDATE novointernado SET datadaacao = NULL, alta = false, motivosaida = '-' "
				+ "WHERE nbam = :idbam AND alta = true";
		return entityManager.createNativeQuery(sql)
				.setParameter("idbam", idbam)
				.executeUpdate();
	}

	/*
	 * public void alterarNovoInternado(int id, NovoInternadoDto dto) { String sql =
	 * "UPDATE internacao set datasaida = :dtsaida, motivodaalta = :motivoalta, origem = :origem, medicodeualta = :medicodeualta, obs = :obs where idninternado = :id"
	 * ; entityManager.createNativeQuery(sql).setParameter("dtsaida",
	 * dto.getDatasaida()) .setParameter("motivoalta",
	 * dto.getMotivodaalta()).setParameter("origem", dto.getOrigem())
	 * .setParameter("medicodeualta", dto.getMedicodeualta()).setParameter("obs",
	 * dto.getObs()) .setParameter("id", id).executeUpdate();
	 * 
	 * }
	 */
	@Transactional
	public long contagem(String unidade, String quem) {
		String sql = "";
		if (quem.equals("internados"))
			sql = "SELECT COUNT(*) FROM novointernado WHERE alta = false and unidade = :unidade";

		if (quem.equals("internados2"))
			sql = "SELECT COUNT(*) FROM  pacientes  p, bam  b, novointernado n, localcenso l, setorcenso s  "
					+ "where n.unidade = :unidade and n.setor_idsetorcenso = s.idsetorcenso "
					+ "and n.local_idsetorlocalcenso = l.idsetorlocalcenso and b.paciente_idpaciente = p.idpaciente "
					+ "and n.pacientes_idpaciente=p.idpaciente   and  cast(b.idbam as integer) = cast(n.nbam as integer) "
					+ "and n.alta = false order by  n.setor_idsetorcenso, n.local_idsetorlocalcenso";

		/*
		 * if (quem.equals("semana")) sql =
		 * "SELECT COUNT(*) FROM novointernado INNER JOIN bam on bam.novointernado_idnovointernado = novointernado.idnovointernado WHERE atendidopor = :servidor AND bam.unidade = :unidade AND bam.data >= date_trunc('week', CURRENT_DATE) + INTERVAL '1 day' AND bam.data < date_trunc('week', CURRENT_DATE) + INTERVAL '7 days' AND EXTRACT(DOW FROM bam.data) BETWEEN 1 AND 7 GROUP BY atendidopor"
		 * ;
		 * 
		 * if (quem.equals("mes")) sql =
		 * "SELECT  count(atendidopor) FROM novointernado	 INNER JOIN bam on bam.novointernado_idnovointernado = novointernado.idnovointernado WHERE atendidopor = :servidor AND bam.unidade = :unidade  AND date_trunc('month', bam.data) = date_trunc('month', CURRENT_DATE) 	GROUP BY atendidopor"
		 * ;
		 * 
		 * if (quem.equals("ano")) sql =
		 * "SELECT  count(atendidopor) FROM novointernado	 INNER JOIN bam on bam.novointernado_idnovointernado = novointernado.idnovointernado WHERE atendidopor = :servidor AND bam.unidade = :unidade  AND date_trunc('year', bam.data) = date_trunc('year', CURRENT_DATE) 	GROUP BY atendidopor"
		 * ;
		 */
		return ((Number) entityManager.createNativeQuery(sql).setParameter("unidade", unidade).getSingleResult())
				.longValue();
	}

	public List<NovoInternado> listarBamUnidade(String bam, String unidade) {
		 // Return a list (up to 1 element) ordered by nbam desc. Use Panache pagination to limit results
        return find("nbam = ?1 and unidade = ?2 order by nbam desc", bam, unidade)
                .page(io.quarkus.panache.common.Page.of(0, 1)).list();
    }

}

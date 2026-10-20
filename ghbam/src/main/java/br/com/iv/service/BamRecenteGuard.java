package br.com.iv.service;

import java.util.Map;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Internar ou alocar no censo por um BAM antigo divide prontuario e AIH.
 * Quando o paciente tem BAM mais recente na mesma unidade, exige confirmacao
 * explicita (parametro confirmarBamAntigo=true) e registra a decisao.
 */
@ApplicationScoped
public class BamRecenteGuard {

	@Inject
	EntityManager entityManager;

	public void exigirConfirmacao(Object bam, boolean confirmado, String acao) {
		int idbam = paraInt(bam);
		if (idbam <= 0) {
			return;
		}
		Object recente = entityManager.createNativeQuery(
				"SELECT MAX(x.idbam) FROM bam b JOIN bam x ON x.paciente_idpaciente = b.paciente_idpaciente "
						+ "AND x.unidade = b.unidade AND x.idbam > b.idbam WHERE b.idbam = :bam")
				.setParameter("bam", idbam)
				.getSingleResult();
		if (recente == null) {
			return;
		}
		int bamRecente = ((Number) recente).intValue();
		if (confirmado) {
			Log.infof("%s confirmada pelo BAM %d existindo BAM mais recente %d", acao, idbam, bamRecente);
			return;
		}
		throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
				.entity(Map.of(
						"erro", String.format(
								"O paciente tem o BAM n. %d, mais recente que o BAM n. %d. Confirme antes de prosseguir.",
								bamRecente, idbam),
						"campo", "BAM",
						"bamRecente", bamRecente))
				.build());
	}

	/**
	 * Paciente (ou cadastro com o mesmo CPF) já internado em outro BAM nos últimos 30 dias:
	 * uma nova internação duplicaria prontuário e AIH. O caminho certo é migrar.
	 */
	public void recusarSeJaInternado(Object bam) {
		int idbam = paraInt(bam);
		if (idbam <= 0) {
			return;
		}
		Object aberta = entityManager.createNativeQuery(
				"SELECT MAX(cast(i.bam as integer)) FROM bam b "
						+ "JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente "
						+ "JOIN bam b2 ON b2.unidade = b.unidade "
						+ "JOIN pacientes p2 ON p2.idpaciente = b2.paciente_idpaciente "
						+ "JOIN internacao i ON i.bam = cast(b2.idbam as text) "
						+ "WHERE b.idbam = :bam AND b2.idbam <> b.idbam "
						+ "AND (b2.paciente_idpaciente = b.paciente_idpaciente "
						+ "  OR (length(regexp_replace(coalesce(p.cpf, ''), '[^0-9]', '', 'g')) = 11 "
						+ "      AND regexp_replace(coalesce(p2.cpf, ''), '[^0-9]', '', 'g') = regexp_replace(coalesce(p.cpf, ''), '[^0-9]', '', 'g')) "
						// RN e cadastros sem CPF: o mesmo prontuário também identifica a pessoa.
						// Exige formato válido e mesmo nascimento: há prontuários-lixo ('-', '10') repetidos.
						+ "  OR (p.prontuario ~ '^[0-9]+-[0-9]+$' AND p2.prontuario = p.prontuario AND p2.nascimento = p.nascimento)) "
						+ "AND i.datasaida IS NULL AND i.motivodaalta = '-' "
						+ "AND cast(i.datainternacao as date) >= CURRENT_DATE - 30")
				.setParameter("bam", idbam)
				.getSingleResult();
		if (aberta == null) {
			return;
		}
		throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
				.entity(Map.of(
						"erro", "Paciente já internado no BAM n. " + aberta
								+ ". Não é possível abrir outra internação: use Migrar para o BAM mais recente.",
						"campo", "INTERNACAO",
						"bamInternado", ((Number) aberta).intValue()))
				.build());
	}

	private static int paraInt(Object valor) {
		if (valor instanceof Number n) {
			return n.intValue();
		}
		try {
			return valor == null ? 0 : Integer.parseInt(valor.toString().trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}

package br.com.iv.service;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Paciente internado nao fica com prontuario pendente ('0') nem com o temporario do RN
 * ('1' + prontuario da mae, gravado na Admissao do RN). Mesma regra do "Gerar prontuario"
 * do NIR (ano-idpaciente), aplicada aqui para valer em qualquer porta de entrada.
 */
@ApplicationScoped
public class ProntuarioDefinitivoService {

	// Definitivo tem 4 digitos antes do hifen (ano); o do RN tem 5 (ordem do RN + ano da mae).
	private static final Pattern FORMATO_RN = Pattern.compile("[1-9][0-9]{4}-[0-9]+");

	@Inject
	EntityManager entityManager;

	static boolean pendente(String prontuario) {
		String p = prontuario == null ? "" : prontuario.trim();
		return p.isEmpty() || "0".equals(p);
	}

	static boolean formatoRn(String prontuario) {
		return prontuario != null && FORMATO_RN.matcher(prontuario.trim()).matches();
	}

	static String gerar(int idpaciente, int ano) {
		return ano + "-" + idpaciente;
	}

	// So e temporario de RN se o prontuario sem o primeiro digito for o de outro paciente (a mae).
	// Assim nao depende do nome "RN n DE", que muda quando entra o nome civil.
	boolean temporario(String prontuario, int idpaciente) {
		if (pendente(prontuario)) {
			return true;
		}
		if (!formatoRn(prontuario)) {
			return false;
		}
		Number mae = (Number) entityManager.createNativeQuery(
				"SELECT count(*) FROM pacientes WHERE trim(prontuario) = :p AND idpaciente <> :id")
				.setParameter("p", prontuario.trim().substring(1)).setParameter("id", idpaciente)
				.getSingleResult();
		return mae.longValue() > 0;
	}

	/** Devolve o prontuario definitivo do paciente, gerando-o se ainda for pendente ou temporario. */
	@Transactional
	public String garantir(int idpaciente, String responsavel) {
		if (idpaciente <= 0) {
			return null;
		}
		@SuppressWarnings("unchecked")
		List<Object> linhas = entityManager.createNativeQuery(
				"SELECT prontuario FROM pacientes WHERE idpaciente = :id FOR UPDATE")
				.setParameter("id", idpaciente).getResultList();
		if (linhas.isEmpty()) {
			return null;
		}
		String atual = (String) linhas.get(0);
		if (!temporario(atual, idpaciente)) {
			return atual;
		}
		String novo = gerar(idpaciente, LocalDate.now().getYear());
		entityManager.createNativeQuery("UPDATE pacientes SET prontuario = :novo WHERE idpaciente = :id")
				.setParameter("novo", novo).setParameter("id", idpaciente).executeUpdate();
		int[] n = propagar(idpaciente, novo);
		Log.infof("Prontuario definitivo gerado: paciente %d, de '%s' para '%s' (internacao=%d, censo=%d) por %s",
				idpaciente, atual, novo, n[0], n[1], responsavel);
		return novo;
	}

	/**
	 * Leva o prontuario atual do paciente para a internacao e o leito abertos. Internacoes
	 * encerradas ficam com o numero da epoca (historico). Liga pelo BAM, que nao falha
	 * quando a internacao antiga nao tem iddopaciente.
	 */
	@Transactional
	public int[] propagar(int idpaciente, String prontuario) {
		int internacao = entityManager.createNativeQuery(
				"UPDATE internacao SET prontuario = :p WHERE datasaida IS NULL AND motivodaalta = '-' "
						+ "AND coalesce(prontuario, '') <> :p "
						+ "AND bam IN (SELECT cast(idbam AS text) FROM bam WHERE paciente_idpaciente = :id)")
				.setParameter("p", prontuario).setParameter("id", idpaciente).executeUpdate();
		int censo = entityManager.createNativeQuery(
				"UPDATE novointernado SET prontuario = :p WHERE alta = false "
						+ "AND coalesce(prontuario, '') <> :p "
						+ "AND nbam IN (SELECT idbam FROM bam WHERE paciente_idpaciente = :id)")
				.setParameter("p", prontuario).setParameter("id", idpaciente).executeUpdate();
		return new int[] { internacao, censo };
	}
}

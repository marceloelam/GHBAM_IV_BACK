package br.com.iv.service;

import br.com.iv.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.core.CurrentRequestManager;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;

/**
 * Grava na tabela logs (a mesma do "Logs do sistema") eventos gerados no backend, inclusive
 * por rotinas agendadas, onde não há requisição para o LogService.anexarUsuario.
 * Mesmo formato dos demais: "usuario=NOME | matricula=M | mensagem".
 */
public final class LogSistemaRegistro {

	private static final Logger LOG = Logger.getLogger(LogSistemaRegistro.class);

	private LogSistemaRegistro() {
	}

	/** Usuário da requisição atual (mesma técnica do LogService); null na rotina agendada. */
	private static AuthenticatedUser usuarioDaRequisicao() {
		try {
			ResteasyReactiveRequestContext req = CurrentRequestManager.get();
			Object u = req == null ? null : req.getContainerRequestContext().getProperty("ghbam.user");
			return u instanceof AuthenticatedUser user ? user : null;
		} catch (Exception e) {
			return null;
		}
	}

	/** Autor = usuário da requisição; sem requisição (rotina), "SISTEMA". */
	public static void registrar(EntityManager em, String unidade, String mensagem) {
		AuthenticatedUser u = usuarioDaRequisicao();
		registrar(em, unidade, u == null ? null : u.nome(), u == null ? null : u.matricula(), mensagem);
	}

	public static void registrar(EntityManager em, String unidade, String autorNome, String autorMatricula, String mensagem) {
		String evento = "usuario=" + (autorNome == null || autorNome.isBlank() ? "SISTEMA (rotina automática)" : autorNome.trim())
				+ " | matricula=" + (autorMatricula == null || autorMatricula.isBlank() ? "-" : autorMatricula.trim())
				+ " | " + mensagem;
		try {
			em.createNativeQuery("INSERT INTO logs (data, hora, evento, unidade, dados) "
					+ "VALUES (current_date, to_char(now(), 'HH24:MI'), :evento, :unidade, '')")
				.setParameter("evento", evento)
				.setParameter("unidade", unidade == null ? "" : unidade)
				.executeUpdate();
		} catch (RuntimeException e) {
			// Log nunca derruba a operação principal; fica ao menos no log do servidor.
			LOG.warnf("Falha ao gravar log do sistema (%s): %s", evento, e.getMessage());
		}
	}
}

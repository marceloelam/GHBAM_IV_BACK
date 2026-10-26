package br.com.iv.controller;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;

import org.jboss.logging.Logger;

import br.com.iv.security.PortalPacienteRateLimitService;
import br.com.iv.service.PortalPacienteService;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// Portal publico de exames do paciente (sem login GHBAM; liberado no RbacRequestFilter).
// CPF e nascimento vem no corpo, nunca na URL, para nao irem parar em log de acesso/proxy.
@Path("portal-paciente")
@Consumes(MediaType.APPLICATION_JSON)
public class PortalPacienteController {

	private static final Logger LOG = Logger.getLogger(PortalPacienteController.class);
	// A mesma resposta para CPF inexistente, nascimento errado ou sem laudo liberado.
	private static final String NAO_ENCONTRADO = "Não encontramos exames liberados para o CPF e a data de nascimento informados.";

	@Inject
	PortalPacienteService portal;

	@Inject
	PortalPacienteRateLimitService limite;

	@POST
	@Path("/exames")
	@Produces(MediaType.APPLICATION_JSON)
	public Response exames(Map<String, Object> corpo, @Context RoutingContext contexto) {
		String ip = ip(contexto);
		String cpf = corpo == null ? "" : String.valueOf(corpo.getOrDefault("cpf", "")).replaceAll("\\D", "");
		long bloqueado = limite.segundosBloqueado(ip, cpf);
		if (bloqueado > 0) {
			LOG.warnf("Portal paciente: consulta bloqueada ip=%s cpf=%s", ip, mascarar(cpf));
			return erro(Response.Status.TOO_MANY_REQUESTS,
					"Muitas tentativas. Tente novamente em " + Math.max(1, (bloqueado + 59) / 60) + " minuto(s).");
		}
		limite.registrarConsultaIp(ip);

		LocalDate nascimento = nascimento(corpo == null ? null : corpo.get("nascimento"));
		if (cpf.length() != 11 || nascimento == null) {
			return erro(Response.Status.BAD_REQUEST, "Informe o CPF (11 números) e a data de nascimento.");
		}
		PortalPacienteService.Consulta consulta = portal.consultar(cpf, nascimento);
		if (consulta == null) {
			limite.registrarFalhaCpf(cpf);
			LOG.infof("Portal paciente: consulta sem resultado ip=%s cpf=%s", ip, mascarar(cpf));
			return erro(Response.Status.NOT_FOUND, NAO_ENCONTRADO);
		}
		LOG.infof("Portal paciente: consulta ip=%s cpf=%s exames=%d", ip, mascarar(cpf), consulta.exames().size());
		return Response.ok(consulta).header("Cache-Control", "no-store").build();
	}

	@POST
	@Path("/laudo")
	@Produces({ "application/pdf", "image/jpeg", "image/png", MediaType.APPLICATION_JSON })
	public Response laudo(Map<String, Object> corpo, @Context RoutingContext contexto) {
		String ip = ip(contexto);
		String token = corpo == null ? null : String.valueOf(corpo.getOrDefault("token", ""));
		int id;
		try {
			id = Integer.parseInt(String.valueOf(corpo == null ? "" : corpo.getOrDefault("id", "")));
		} catch (NumberFormatException invalido) {
			return erro(Response.Status.BAD_REQUEST, "Exame inválido.");
		}
		PortalPacienteService.Laudo laudo;
		try {
			laudo = portal.laudo(token, id);
		} catch (RuntimeException falha) {
			LOG.errorf("Portal paciente: falha ao ler laudo do pedido %d: %s", id, falha.getMessage());
			return erro(Response.Status.SERVICE_UNAVAILABLE, "Não foi possível abrir o exame agora. Tente novamente mais tarde.");
		}
		if (laudo == null) {
			LOG.warnf("Portal paciente: laudo negado pedido=%d ip=%s", id, ip);
			return erro(Response.Status.FORBIDDEN, "Sua consulta expirou. Informe o CPF e a data de nascimento novamente.");
		}
		LOG.infof("Portal paciente: laudo entregue pedido=%d ip=%s", id, ip);
		String extensao = laudo.tipo().endsWith("pdf") ? ".pdf" : laudo.tipo().endsWith("png") ? ".png" : ".jpg";
		return Response.ok(laudo.conteudo(), laudo.tipo())
				.header("Content-Disposition", "inline; filename=\"exame-" + id + extensao + "\"")
				.header("Cache-Control", "no-store")
				.build();
	}

	// Aceita aaaa-MM-dd (input date do navegador) ou dd/MM/aaaa.
	private static LocalDate nascimento(Object valor) {
		String texto = valor == null ? "" : valor.toString().trim();
		try {
			if (texto.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(texto);
			if (texto.matches("\\d{2}/\\d{2}/\\d{4}")) {
				String[] p = texto.split("/");
				return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
			}
		} catch (DateTimeException invalida) {
			return null;
		}
		return null;
	}

	private static Response erro(Response.Status status, String mensagem) {
		return Response.status(status).type(MediaType.APPLICATION_JSON).entity(Map.of("erro", mensagem)).build();
	}

	// So os dois ultimos digitos no log.
	private static String mascarar(String cpf) {
		return cpf.length() < 2 ? "-" : "*********" + cpf.substring(cpf.length() - 2);
	}

	// Rota publica: X-Real-IP/X-Forwarded-For crus podem ser forjados pelo cliente e
	// burlariam o limite por IP. O Quarkus so troca o endereco da conexao pelo do
	// header quando a requisicao vem de um proxy confiavel (quarkus.http.proxy.trusted-proxies).
	private static String ip(RoutingContext contexto) {
		if (contexto == null || contexto.request() == null || contexto.request().remoteAddress() == null) return "-";
		String host = contexto.request().remoteAddress().host();
		return host == null || host.isBlank() ? "-" : host;
	}
}

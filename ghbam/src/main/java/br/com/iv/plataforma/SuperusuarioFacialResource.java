package br.com.iv.plataforma;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import br.com.iv.entity.Servidor;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.ProvaVidaFacialService;
import br.com.iv.service.ReconhecimentoFacialService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Confirmacao facial (prova de vida) de quem concede Super usuario ou designa quem concede.
 * O rosto conferido e sempre o do usuario logado contra a foto do proprio cadastro.
 */
@Path("/seguranca/superusuario-facial")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SuperusuarioFacialResource {

    public static final String CABECALHO = "X-Confirmacao-Superusuario";
    private static final Logger LOG = Logger.getLogger(SuperusuarioFacialResource.class);

    @Inject
    SuperusuarioConcessaoService concessao;

    @Inject
    ProvaVidaFacialService provaVida;

    @Inject
    ReconhecimentoFacialService reconhecimento;

    @Inject
    ServidorRepository servidorRepository;

    public static class Pedido {
        public String finalidade;
        public String alvo;
        public String desafioId;
        public List<String> imagens;
        public String matricula;
        public String comprovante;
        public boolean podeDesignar;
    }

    /** tentativa=true: a tela avisa que alguem sem autorizacao tentou mexer no Super usuario. */
    @GET
    @Path("/status")
    public Response status(@QueryParam("tentativa") boolean tentativa, @QueryParam("alvo") String alvo,
            @Context ContainerRequestContext ctx) {
        String m = matricula(ctx);
        boolean pode = concessao.podeConceder(m);
        if (tentativa && !pode) {
            LOG.warnf("[SUPERUSUARIO] tentativa sem autorizacao matricula=%s alvo=%s", m,
                    alvo == null ? "" : alvo.trim());
        }
        return Response.ok(Map.of("podeConceder", pode, "podeDesignar", concessao.podeDesignar(m),
                "responsavel", concessao.ehResponsavel(m))).build();
    }

    @POST
    @Path("/desafio")
    public Response desafio(Pedido p, @Context ContainerRequestContext ctx) {
        String m = matricula(ctx);
        try {
            concessao.exigirPapel(m, p == null ? null : p.finalidade);
            if (!reconhecimento.configurado()) {
                return erro(Response.Status.SERVICE_UNAVAILABLE, "Reconhecimento facial nao configurado no servidor.");
            }
            ProvaVidaFacialService.Desafio d = provaVida.criar(m);
            return Response.ok(Map.of("desafioId", d.id(), "etapas", d.etapas(),
                    "validadeSegundos", d.validadeSegundos())).build();
        } catch (SuperusuarioConcessaoService.NegadoException e) {
            LOG.warnf("[SUPERUSUARIO] tentativa sem autorizacao matricula=%s", m);
            return erro(Response.Status.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return erro(Response.Status.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    @POST
    @Path("/confirmar")
    public Response confirmar(Pedido p, @Context ContainerRequestContext ctx) {
        String m = matricula(ctx);
        if (p == null || p.desafioId == null || p.imagens == null || p.imagens.isEmpty()) {
            return erro(Response.Status.BAD_REQUEST, "Conclua todas as etapas da camera.");
        }
        try {
            concessao.exigirPapel(m, p.finalidade);
            Servidor s = servidorRepository.localizarMatricula(m);
            if (s == null || s.getFoto() == null || s.getFoto().isBlank()) {
                return erro(Response.Status.BAD_REQUEST, "Seu cadastro nao tem foto para a validacao facial.");
            }
            ReconhecimentoFacialService.ResultadoValidacao r = provaVida.validar(p.desafioId, m,
                    new ArrayList<>(p.imagens), s.getFoto());
            if (!r.isAprovado()) {
                LOG.warnf("[SUPERUSUARIO] rosto nao reconhecido matricula=%s", m);
                return erro(Response.Status.FORBIDDEN, r.getMensagem());
            }
            String comprovante = concessao.emitir(m, p.finalidade, p.alvo);
            return Response.ok(Map.of("aprovado", true, "comprovante", comprovante,
                    "validadeSegundos", SuperusuarioConcessaoService.VALIDADE_SEGUNDOS)).build();
        } catch (SuperusuarioConcessaoService.NegadoException e) {
            return erro(Response.Status.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            LOG.warnf("[SUPERUSUARIO] prova de vida reprovada matricula=%s", m);
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return erro(Response.Status.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    @GET
    @Path("/designados")
    public Response designados(@Context ContainerRequestContext ctx) {
        if (!concessao.podeDesignar(matricula(ctx))) {
            return erro(Response.Status.FORBIDDEN, "Somente o responsavel pela plataforma, ou quem ele autorizar.");
        }
        return Response.ok(concessao.designados()).build();
    }

    @POST
    @Path("/designados")
    public Response designar(Pedido p, @Context ContainerRequestContext ctx) {
        try {
            concessao.designar(usuario(ctx), p == null ? null : p.comprovante, p == null ? null : p.matricula,
                    p != null && p.podeDesignar);
            return Response.ok(Map.of("ok", true)).build();
        } catch (SuperusuarioConcessaoService.NegadoException e) {
            return erro(Response.Status.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return erro(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }

    @DELETE
    @Path("/designados/{matricula}")
    public Response cancelar(@PathParam("matricula") String alvo, @HeaderParam(CABECALHO) String comprovante,
            @Context ContainerRequestContext ctx) {
        try {
            concessao.cancelar(usuario(ctx), comprovante, alvo);
            return Response.ok(Map.of("ok", true)).build();
        } catch (SuperusuarioConcessaoService.NegadoException e) {
            return erro(Response.Status.FORBIDDEN, e.getMessage());
        }
    }

    private static Response erro(Response.Status status, String mensagem) {
        return Response.status(status).entity(Map.of("erro", mensagem == null ? "Operacao nao permitida." : mensagem))
                .build();
    }

    static AuthenticatedUser usuario(ContainerRequestContext ctx) {
        Object u = ctx == null ? null : ctx.getProperty("ghbam.user");
        return u instanceof AuthenticatedUser a ? a : AuthenticatedUser.empty();
    }

    private static String matricula(ContainerRequestContext ctx) {
        String m = usuario(ctx).matricula();
        return m == null ? "" : m.trim();
    }
}

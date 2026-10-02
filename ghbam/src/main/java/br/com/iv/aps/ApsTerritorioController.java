package br.com.iv.aps;

import static br.com.iv.aps.ApsHttp.ACS;
import static br.com.iv.aps.ApsHttp.APS;
import static br.com.iv.aps.ApsHttp.GESTAO;
import static br.com.iv.aps.ApsHttp.VACINA;
import static br.com.iv.aps.ApsHttp.executar;
import static br.com.iv.aps.ApsHttp.pode;
import static br.com.iv.aps.ApsHttp.proibido;

import java.util.List;
import java.util.Map;

import br.com.iv.dto.CadsusPdqConsultaDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.CadsusPdqClienteService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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
 * GHBAM APS - cidadao (cadastro individual), domicilio/familia (cadastro
 * domiciliar e territorial), gestao de cadastros e app offline do ACS.
 */
@Path("api/aps/territorio")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ApsTerritorioController {

    @Inject
    SecurityPermissionService sps;
    @Inject
    ApsContextoService contexto;
    @Inject
    ApsCidadaoService cidadaos;
    @Inject
    CadsusPdqClienteService cadsusPdq;
    @Inject
    ApsDomicilioService domicilios;
    @Inject
    ApsCdsService cds;

    @Context
    ContainerRequestContext ctx;

    private AuthenticatedUser u() {
        return ApsHttp.usuario(ctx, sps);
    }

    private String unidade(String informada) {
        return ApsHttp.unidade(ctx, u(), informada);
    }

    private ApsContextoService.Lotacao lot(AuthenticatedUser user) {
        return contexto.lotacao(user, unidade(null), ApsHttp.lotacao(ctx));
    }

    // ------------------------------------------------------------------ cidadao

    @GET
    @Path("/cidadaos")
    public Response buscar(@QueryParam("termo") String termo, @QueryParam("unidade") String unidade,
            @QueryParam("idEquipe") Long idEquipe, @QueryParam("microarea") String microarea,
            @QueryParam("hospital") boolean hospital, @QueryParam("todasUnidades") boolean todas,
            @QueryParam("limite") Integer limite) {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        // G-PEC e por unidade logada: todasUnidades nao amplia mais a busca.
        return executar(() -> cidadaos.buscar(termo, unidade(unidade), idEquipe, microarea, hospital,
                limite == null ? 50 : limite));
    }

    /**
     * CNS do cidadao pelo CADSUS (mesma consulta PDQ da Recepcao), na unidade logada.
     * Rota propria do G-PEC para nao exigir a permissao administrativa de integracao-sus.
     */
    @POST
    @Path("/cidadaos/cadsus")
    public Response cadsus(CadsusPdqConsultaDto filtro) {
        if (!pode(u(), APS, ACS, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> cadsusPdq.consultar(unidade(null), filtro));
    }

    @GET
    @Path("/cidadaos/{id}")
    public Response obter(@PathParam("id") Long id) {
        if (!pode(u(), APS, ACS, VACINA, GESTAO)) {
            return proibido("Atencao Primaria");
        }
        return executar(() -> cidadaos.obter(id));
    }

    @POST
    @Path("/cidadaos/importar/{idPaciente}")
    public Response importar(@PathParam("idPaciente") Integer idPaciente) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, ACS, VACINA)) {
            return proibido("Cadastro do cidadao");
        }
        return executar(() -> cidadaos.importarPaciente(idPaciente, unidade(null), user.matricula()));
    }

    @POST
    @Path("/cidadaos/cadastro-individual")
    public Response cadastroIndividual(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, ACS)) {
            return proibido("Cadastro individual");
        }
        return executar(() -> cidadaos.salvarCadastroIndividual(body, lot(user)));
    }

    @POST
    @Path("/cidadaos/{id}/saida")
    public Response saida(@PathParam("id") Long id, Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, ACS)) {
            return proibido("Cadastro individual");
        }
        return executar(() -> cidadaos.registrarSaida(id, body, lot(user)));
    }

    @GET
    @Path("/cidadaos/{id}/folha-rosto")
    public Response folhaRosto(@PathParam("id") Long id) {
        if (!pode(u(), APS, VACINA, GESTAO)) {
            return proibido("Prontuario");
        }
        return executar(() -> cidadaos.folhaDeRosto(id));
    }

    @GET
    @Path("/cidadaos/{id}/historico")
    public Response historico(@PathParam("id") Long id) {
        if (!pode(u(), APS, GESTAO)) {
            return proibido("Prontuario");
        }
        return executar(() -> cidadaos.historico(id));
    }

    @POST
    @Path("/cidadaos/unificar")
    public Response unificar(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, GESTAO)) {
            return proibido("Gestao de cadastros");
        }
        return executar(() -> cidadaos.unificar(ApsConfiguracaoService.num(body.get("idManter")),
                ApsConfiguracaoService.num(body.get("idRemover")), user.matricula()));
    }

    @POST
    @Path("/cidadaos/reterritorializar")
    @SuppressWarnings("unchecked")
    public Response reterritorializar(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, GESTAO)) {
            return proibido("Gestao de cadastros");
        }
        return executar(() -> {
            List<Long> ids = ((List<Object>) body.getOrDefault("ids", List.of())).stream()
                    .map(o -> ((Number) o).longValue()).toList();
            int n = cidadaos.reterritorializar(ids, ApsConfiguracaoService.num(body.get("idEquipe")),
                    ApsConfiguracaoService.str(body.get("microarea")), Boolean.TRUE.equals(body.get("foraArea")), user.matricula());
            return Map.of("atualizados", n);
        });
    }

    @DELETE
    @Path("/cidadaos/{id}/familia")
    public Response desvincular(@PathParam("id") Long id) {
        if (!pode(u(), APS, ACS)) {
            return proibido("Cadastro domiciliar");
        }
        return executar(() -> {
            domicilios.desvincularMembro(id);
            return Map.of("ok", true);
        });
    }

    // ------------------------------------------------------------------ domicilio / familia

    @GET
    @Path("/domicilios")
    public Response domicilios(@QueryParam("unidade") String unidade, @QueryParam("idEquipe") Long idEquipe,
            @QueryParam("microarea") String microarea, @QueryParam("termo") String termo, @QueryParam("limite") Integer limite) {
        if (!pode(u(), APS, ACS, GESTAO)) {
            return proibido("Territorio");
        }
        return executar(() -> domicilios.listar(unidade(unidade), idEquipe, microarea, termo, limite == null ? 200 : limite));
    }

    @GET
    @Path("/domicilios/{id}")
    public Response domicilio(@PathParam("id") Long id) {
        if (!pode(u(), APS, ACS, GESTAO)) {
            return proibido("Territorio");
        }
        return executar(() -> domicilios.obter(id));
    }

    @POST
    @Path("/domicilios")
    public Response salvarDomicilio(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, APS, ACS)) {
            return proibido("Cadastro domiciliar");
        }
        return executar(() -> domicilios.salvar(body, lot(user)));
    }

    @POST
    @Path("/familias/{id}/membros")
    public Response vincular(@PathParam("id") Long idFamilia, Map<String, Object> body) {
        if (!pode(u(), APS, ACS)) {
            return proibido("Cadastro domiciliar");
        }
        return executar(() -> domicilios.vincularMembro(idFamilia, ApsConfiguracaoService.num(body.get("idCidadao")),
                Boolean.TRUE.equals(body.get("responsavel"))));
    }

    // ------------------------------------------------------------------ app do ACS

    @GET
    @Path("/acs/pacote")
    public Response pacote(@QueryParam("idEquipe") Long idEquipe, @QueryParam("microarea") String microarea) {
        AuthenticatedUser user = u();
        if (!pode(user, ACS, GESTAO)) {
            return proibido("App do ACS");
        }
        Long eq = pode(user, GESTAO) ? idEquipe : null;
        return executar(() -> cds.pacoteAcs(user.matricula(), unidade(null), eq, microarea));
    }

    @POST
    @Path("/acs/sincronizar")
    public Response sincronizar(Map<String, Object> body) {
        AuthenticatedUser user = u();
        if (!pode(user, ACS)) {
            return proibido("App do ACS");
        }
        return executar(() -> cds.sincronizar(body, lot(user)));
    }
}

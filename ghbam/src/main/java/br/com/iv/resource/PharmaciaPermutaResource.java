package br.com.iv.resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.entity.PharmaciaPermuta;
import br.com.iv.entity.PharmaciaPermutaMensagem;
import br.com.iv.dto.PharmaciaPermutaFinalizarRequest;
import br.com.iv.service.PharmaciaPermutaService;

@Path("/pharmacia/permutas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaPermutaResource {

    @Inject
    PharmaciaPermutaService service;

    @GET
    public List<PharmaciaPermuta> listAll(@QueryParam("status") String status, @QueryParam("unidadeId") String sigla) {
        List<PharmaciaPermuta> result;
        if (sigla != null && !sigla.isEmpty()) {
            List<PharmaciaPermuta> origem = service.findByUnidadeOrigemSigla(sigla);
            List<PharmaciaPermuta> destino = service.findByUnidadeDestinoSigla(sigla);
            Set<PharmaciaPermuta> combined = new HashSet<>(origem);
            combined.addAll(destino);
            result = new ArrayList<>(combined);
        } else {
            result = service.listAll();
        }
        if (status != null) {
            try {
                PharmaciaPermuta.StatusPermuta statusEnum = PharmaciaPermuta.StatusPermuta.valueOf(status);
                result = result.stream().filter(p -> p.getStatus() == statusEnum).collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // status inválido, ignora filtro
            }
        }
        return result;
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        PharmaciaPermuta permuta = service.findById(id);
        if (permuta == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(permuta).build();
    }

    @POST
    @Path("/solicitar")
    public Response solicitar(PharmaciaPermuta permuta) {
        try {
            PharmaciaPermuta saved = service.solicitar(permuta);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}/aprovar")
    public Response aprovar(
            @PathParam("id") Long id,
            @QueryParam("aprovador") String aprovadorMatricula,
            @QueryParam("unidadeExecutora") String unidadeExecutora,
            Map<String, Integer> quantidadesAprovadas) {
        try {
            PharmaciaPermuta permuta = service.aprovar(id, aprovadorMatricula, quantidadesAprovadas, unidadeExecutora);
            return Response.ok(permuta).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}/recusar")
    public Response recusar(
            @PathParam("id") Long id,
            @QueryParam("aprovador") String aprovadorMatricula,
            @QueryParam("motivo") String motivo,
            @QueryParam("unidadeExecutora") String unidadeExecutora) {
        try {
            PharmaciaPermuta permuta = service.recusar(id, motivo, aprovadorMatricula, unidadeExecutora);
            return Response.ok(permuta).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}/iniciar-transporte")
    public Response iniciarTransporte(
            @PathParam("id") Long id,
            @QueryParam("matricula") String transporteMatricula,
            @QueryParam("unidadeExecutora") String unidadeExecutora) {
        try {
            PharmaciaPermuta permuta = service.iniciarTransporte(id, transporteMatricula, unidadeExecutora);
            return Response.ok(permuta).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}/finalizar")
    public Response finalizar(
            @PathParam("id") Long id,
            @QueryParam("matricula") String recebedorMatricula,
            @QueryParam("unidadeExecutora") String unidadeExecutora,
            PharmaciaPermutaFinalizarRequest request) {
        try {
            PharmaciaPermuta permuta = service.finalizar(id, recebedorMatricula, request, unidadeExecutora);
            return Response.ok(permuta).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id, @QueryParam("unidadeExecutora") String unidadeExecutora) {
        try {
            service.delete(id, unidadeExecutora);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/unidade-origem/{sigla}")
    public List<PharmaciaPermuta> findByUnidadeOrigem(@PathParam("sigla") String sigla) {
        return service.findByUnidadeOrigemSigla(sigla);
    }

    @GET
    @Path("/unidade-destino/{sigla}")
    public List<PharmaciaPermuta> findByUnidadeDestino(@PathParam("sigla") String sigla) {
        return service.findByUnidadeDestinoSigla(sigla);
    }

    @GET
    @Path("/lote/{loteId}")
    public List<PharmaciaPermuta> findByLote(@PathParam("loteId") Long loteId) {
        return service.findByLoteId(loteId);
    }

    @GET
    @Path("/{id}/mensagens")
    public List<PharmaciaPermutaMensagem> listarMensagens(@PathParam("id") Long id) {
        return service.listarMensagens(id);
    }

    @POST
    @Path("/{id}/mensagens")
    public Response enviarMensagem(@PathParam("id") Long id, PharmaciaPermutaMensagem mensagem) {
        try {
            PharmaciaPermutaMensagem salva = service.enviarMensagem(id, mensagem);
            return Response.status(Response.Status.CREATED).entity(salva).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}

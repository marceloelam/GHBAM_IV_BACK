package br.com.iv.resource;

import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import br.com.iv.entity.PharmaciaDispensacao;
import br.com.iv.service.PharmaciaDispensacaoService;

@Path("/pharmacia/dispensacao")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PharmaciaDispensacaoResource {

    @Inject
    PharmaciaDispensacaoService service;

    @GET
    public List<PharmaciaDispensacao> listAll() {
        return service.listAll();
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        PharmaciaDispensacao d = service.findById(id);
        if (d == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(d).build();
    }

    @GET
    @Path("/paciente/{pacienteId}")
    public List<PharmaciaDispensacao> findByPaciente(@PathParam("pacienteId") Long pacienteId) {
        return service.findByPacienteId(pacienteId);
    }

    @POST
    public Response save(PharmaciaDispensacao dispensacao) {
        try {
            PharmaciaDispensacao saved = service.save(dispensacao);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/devolver/{id}")
    public Response devolver(@PathParam("id") Long id, Map<String, Object> body) {
        try {
            Long loteId = body.get("loteId") != null ? Long.valueOf(body.get("loteId").toString()) : null;
            Integer quantidade = body.get("quantidade") != null ? Integer.valueOf(body.get("quantidade").toString()) : null;
            String unidadeSigla = body.get("unidadeSigla") != null ? body.get("unidadeSigla").toString() : null;
            service.devolver(id, loteId, quantidade, unidadeSigla);
            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        try {
            service.delete(id);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}

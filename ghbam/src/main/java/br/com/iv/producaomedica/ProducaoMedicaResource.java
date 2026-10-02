package br.com.iv.producaomedica;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/producao-medica") @Produces(MediaType.APPLICATION_JSON)
public class ProducaoMedicaResource {
    @Inject ProducaoMedicaService service;
    @Inject SecurityPermissionService security;
    @Context ContainerRequestContext request;
    @GET public Response gerar(@QueryParam("inicio")String inicio,@QueryParam("fim")String fim,
            @QueryParam("unidade")String unidade,@QueryParam("matricula")String matricula){
        AuthenticatedUser usuario=usuario();
        if(!autorizado(usuario))return proibido();
        String unidadeEfetiva=unidadeEfetiva(usuario);
        if(unidadeEfetiva==null)return proibido();
        return Response.ok(service.gerar(data(inicio,"inicio"),data(fim,"fim"),unidadeEfetiva,matricula)).build();
    }
    @GET @Path("/medicos") public Response medicos(@QueryParam("inicio")String inicio,@QueryParam("fim")String fim,
            @QueryParam("unidade")String unidade){
        AuthenticatedUser usuario=usuario();
        if(!autorizado(usuario))return proibido();
        String unidadeEfetiva=unidadeEfetiva(usuario);
        if(unidadeEfetiva==null)return proibido();
        return Response.ok(service.listarMedicos(data(inicio,"inicio"),data(fim,"fim"),unidadeEfetiva)).build();
    }
    private AuthenticatedUser usuario(){AuthenticatedUser u=(AuthenticatedUser)request.getProperty("ghbam.user");return u!=null?u:security.resolve(request);}
    private static boolean autorizado(AuthenticatedUser u){return u!=null&&(u.hasPermission("pproducaomedica")||u.hasPermission("kadm"));}
    private static String unidadeEfetiva(AuthenticatedUser u){return limpa(u.unidade());}
    private static String limpa(String valor){return valor==null||valor.isBlank()?null:valor.trim();}
    private static Response proibido(){return Response.status(Response.Status.FORBIDDEN).entity("{\"erro\":\"Acesso restrito a Producao Medica.\"}").build();}
    private static LocalDate data(String valor,String nome){if(valor==null||valor.isBlank())throw new BadRequestException(nome+" e obrigatorio (yyyy-MM-dd)");try{return LocalDate.parse(valor);}catch(DateTimeParseException e){throw new BadRequestException(nome+" invalido; use yyyy-MM-dd");}}
}

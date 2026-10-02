package br.com.iv.util;

import java.io.InputStream;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("/pontos")
public class PontosResources {

    @GET
    @Path("/marcas.xml")
    @Produces("application/xml")
    public Response getMarcasXml() {
        // Use o ClassLoader para localizar o arquivo no classpath
        InputStream fileStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("pontos/marcas.xml");
        if (fileStream == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Arquivo marcas.xml não encontrado!").build();
        }
        return Response.ok(fileStream).build();
    }
}

package br.com.iv.controller;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Path("/upload")
public class FileUploadController {

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(@RestForm FileUpload file) {
        String uploadDir = "src/main/resources/img/";
        try {
        	if((file != null) || !file.equals(""))
            Files.copy(file.uploadedFile(), Paths.get(uploadDir + file.fileName()));
            
            return Response.status(201).entity("Arquivo enviado com sucesso").build();
        } catch (IOException e) {
            e.printStackTrace();
            return Response.status(500).entity("Erro arquivo não foi enviado").build();
        }
    }
}
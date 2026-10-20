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
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Path("/upload")
public class FileUploadController {
    private static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(@RestForm FileUpload file) {
        if (file == null || file.uploadedFile() == null || file.fileName() == null || file.fileName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Selecione um arquivo de imagem."))
                    .build();
        }
        try {
            long tamanho = Files.size(file.uploadedFile());
            if (tamanho == 0 || tamanho > TAMANHO_MAXIMO) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("message", "A imagem deve ter entre 1 byte e 10 MB."))
                        .build();
            }
            String nomeRecebido = file.fileName().replace('\\', '/');
            String nome = nomeRecebido.substring(nomeRecebido.lastIndexOf('/') + 1);
            String extensao = nome.contains(".") ? nome.substring(nome.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
            if (nome.isBlank() || !nome.matches("[A-Za-z0-9._-]{1,180}")
                    || !Set.of("png", "jpg", "jpeg", "gif", "webp").contains(extensao)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("message", "Formato invalido. Envie uma imagem PNG, JPG, GIF ou WebP."))
                        .build();
            }
            java.nio.file.Path diretorio = java.nio.file.Path.of("src", "main", "resources", "img").toAbsolutePath().normalize();
            Files.createDirectories(diretorio);
            java.nio.file.Path destino = diretorio.resolve(nome).normalize();
            if (!destino.startsWith(diretorio)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("message", "Nome de arquivo invalido."))
                        .build();
            }
            Files.copy(file.uploadedFile(), destino, StandardCopyOption.REPLACE_EXISTING);
            return Response.status(201)
                    .entity(Map.of("message", "Arquivo enviado com sucesso.", "nome", nome))
                    .build();
        } catch (IOException e) {
            return Response.serverError().entity(Map.of("message", "Nao foi possivel salvar o arquivo.")).build();
        }
    }
}

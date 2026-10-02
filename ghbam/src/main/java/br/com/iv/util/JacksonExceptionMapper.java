package br.com.iv.util;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

/**
 * Captura erros de desserialização Jackson e retorna um JSON mais descritivo.
 * Também loga o stack trace completo para diagnóstico.
 */
@Provider
public class JacksonExceptionMapper implements ExceptionMapper<JsonMappingException> {

    private static final Logger LOG = Logger.getLogger(JacksonExceptionMapper.class);

    @Override
    public Response toResponse(JsonMappingException e) {
        String campo = "desconhecido";
        long coluna = -1;
        long linha = -1;

        if (e.getPath() != null && !e.getPath().isEmpty()) {
            JsonMappingException.Reference ref = e.getPath().get(e.getPath().size() - 1);
            if (ref.getFieldName() != null) {
                campo = ref.getFieldName();
            }
        }
        if (e.getLocation() != null) {
            coluna = e.getLocation().getColumnNr();
            linha  = e.getLocation().getLineNr();
        }

        LOG.errorf(e,
            "❌ Erro Jackson ao desserializar corpo da requisição — campo='%s' linha=%d coluna=%d | msg: %s",
            campo, linha, coluna, e.getMessage());

        String body = String.format(
            "{\"erro\":\"Falha ao interpretar JSON\",\"campo\":\"%s\",\"linha\":%d,\"coluna\":%d,\"detalhe\":\"%s\"}",
            campo, linha, coluna,
            (e.getOriginalMessage() != null ? e.getOriginalMessage().replace("\"", "'") : "null")
        );

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(body)
                .type("application/json")
                .build();
    }
}

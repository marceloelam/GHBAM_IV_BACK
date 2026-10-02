package br.com.iv.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

@Path("/api/pacs/proxy")
@ApplicationScoped
public class PacsProxyResource {

    private static final int MAX_REDIRECTS = 5;
    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final Pattern ACCESSION_PATTERN = Pattern.compile("\"accession_number\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TIPO_ACESSO_PATTERN = Pattern.compile("\"tipo_acesso\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TOKEN_ACESSO_PATTERN = Pattern.compile("\"token_acesso\"\\s*:\\s*\"([^\"]+)\"");

    @GET
    public Response proxy(
            @QueryParam("url") String url,
            @QueryParam("unidade") String unidade,
            @QueryParam("tipo") String tipo,
            @Context HttpHeaders headers) {
        if (url == null || url.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"Parâmetro 'url' é obrigatório\"}")
                    .build();
        }

        try {
            String tokenPacs = buscarTokenPacs(unidade, tipo);
            List<String> acceptHeaders = headers.getRequestHeader("Accept");
            List<String> originHeaders = headers.getRequestHeader("Origin");

            URI targetUri = URI.create(url);
            int redirectCount = 0;

            while (redirectCount <= MAX_REDIRECTS) {
                var requestBuilder = HttpRequest.newBuilder()
                        .uri(targetUri)
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .GET();

                if (tokenPacs != null && !tokenPacs.isBlank()) {
                    requestBuilder.header("Authorization", normalizarBearer(tokenPacs));
                }
                if (acceptHeaders != null && !acceptHeaders.isEmpty()) {
                    requestBuilder.header("Accept", acceptHeaders.get(0));
                }
                if (originHeaders != null && !originHeaders.isEmpty()) {
                    requestBuilder.header("Origin", originHeaders.get(0));
                }

                HttpResponse<byte[]> response = httpClient.send(
                        requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

                int status = response.statusCode();

                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("Location").orElse(null);
                    if (location == null || location.isBlank()) break;
                    targetUri = targetUri.resolve(location);
                    redirectCount++;
                    continue;
                }

                var responseBuilder = Response.status(status);
                response.headers().firstValue("Content-Type")
                        .ifPresent(ct -> responseBuilder.header("Content-Type", ct));
                response.headers().firstValue("Content-Disposition")
                        .ifPresent(cd -> responseBuilder.header("Content-Disposition", cd));

                return responseBuilder.entity(response.body()).build();
            }

            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("{\"erro\":\"Muitos redirecionamentos ao acessar PACS\"}")
                    .build();

        } catch (Exception e) {
            String detail = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'")
                    : e.getClass().getSimpleName();
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("{\"erro\":\"Falha ao conectar com PACS: " + detail + "\"}")
                    .build();
        }
    }

    @GET
    @Path("/config")
    public Response config(@QueryParam("unidade") String unidade) {
        ConfiguracaoUnidade config = buscarConfiguracaoUnidade(unidade);
        if (config == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"erro\":\"Configuracao PACS da unidade nao encontrada\"}")
                    .build();
        }

        String json = "{"
                + "\"urlconexaoimagempacs\":\"" + escapeJson(config.getUrlconexaoimagempacs()) + "\","
                + "\"urlconexaolaudopacs\":\"" + escapeJson(config.getUrlconexaolaudopacs()) + "\""
                + "}";

        return Response.ok(json)
                .header("Content-Type", "application/json")
                .build();
    }

    @GET
    @Path("/imagem/disponibilidade")
    public Response disponibilidadeImagem(
            @QueryParam("id") String idImagem,
            @QueryParam("unidade") String unidade) {
        if (idImagem == null || idImagem.isBlank()) {
            return jsonOk("{\"disponivel\":false,\"erro\":\"Exame sem identificador\"}");
        }

        ConfiguracaoUnidade config = buscarConfiguracaoUnidade(unidade);
        if (config == null) {
            return jsonOk("{\"disponivel\":false,\"erro\":\"Configuracao PACS da unidade nao encontrada\"}");
        }

        String baseImagem = config.getUrlconexaoimagempacs();
        if (baseImagem == null || baseImagem.isBlank()) {
            return jsonOk("{\"disponivel\":false,\"erro\":\"URL de imagem PACS nao configurada\"}");
        }

        try {
            String tokenImagem = config.getTokenimagempacs();
            String urlGeracao = montarUrlGeracaoImagem(baseImagem, idImagem);
            HttpResponse<byte[]> geracao = chamarPacs(urlGeracao, tokenImagem, "application/json");
            String body = new String(geracao.body(), StandardCharsets.UTF_8);

            if (geracao.statusCode() < 200 || geracao.statusCode() >= 300) {
                return jsonOk("{\"disponivel\":false,\"erro\":\"PACS retornou " + geracao.statusCode() + "\"}");
            }
            if (isRespostaImagemIndisponivel(body)) {
                return jsonOk("{\"disponivel\":false,\"erro\":\"" + escapeJson(resumoErroPacs(body)) + "\"}");
            }

            String accession = extrairJson(body, ACCESSION_PATTERN);
            String tipoAcesso = extrairJson(body, TIPO_ACESSO_PATTERN);
            String tokenAcesso = extrairJson(body, TOKEN_ACESSO_PATTERN);

            if (accession.isBlank() || tipoAcesso.isBlank() || tokenAcesso.isBlank()) {
                return jsonOk("{\"disponivel\":false,\"erro\":\"Resposta de imagem PACS incompleta\"}");
            }

            String viewerUrl = montarUrl(baseImagem, tipoAcesso, accession, tokenAcesso);
            HttpResponse<byte[]> viewer = chamarPacs(viewerUrl, tokenImagem, "text/html,application/xhtml+xml,text/plain,*/*");
            String viewerBody = new String(viewer.body(), StandardCharsets.UTF_8);

            if (viewer.statusCode() < 200 || viewer.statusCode() >= 300 || isRespostaImagemIndisponivel(viewerBody)) {
                return jsonOk("{\"disponivel\":false,\"erro\":\"Exame nao foi encontrado no viewer PACS\"}");
            }

            return jsonOk("{\"disponivel\":true,\"erro\":\"\"}");
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return jsonOk("{\"disponivel\":false,\"erro\":\"Falha ao verificar imagem PACS: " + escapeJson(detail) + "\"}");
        }
    }

    private String buscarTokenPacs(String unidade, String tipo) {
        ConfiguracaoUnidade config = buscarConfiguracaoUnidade(unidade);
        if (config == null) return "";

        String tipoNormalizado = tipo == null ? "" : tipo.trim().toLowerCase();
        if ("laudo".equals(tipoNormalizado)) {
            return config.getTokenlaudopacs();
        }

        return config.getTokenimagempacs();
    }

    private ConfiguracaoUnidade buscarConfiguracaoUnidade(String unidade) {
        if (unidade == null || unidade.isBlank()) return null;
        List<ConfiguracaoUnidade> configs = configuracaoUnidadeRepository.filtarPorUnidadeSigla(unidade.trim());
        return configs.isEmpty() ? null : configs.get(0);
    }

    private String normalizarBearer(String token) {
        String valor = token == null ? "" : token.trim();
        if (valor.toLowerCase().startsWith("bearer ")) return valor;
        return "Bearer " + valor;
    }

    private HttpResponse<byte[]> chamarPacs(String url, String tokenPacs, String accept)
            throws Exception {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", accept)
                .GET();

        if (tokenPacs != null && !tokenPacs.isBlank()) {
            requestBuilder.header("Authorization", normalizarBearer(tokenPacs));
        }

        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private String montarUrlGeracaoImagem(String baseImagem, String idImagem) {
        String base = baseImagem == null ? "" : baseImagem.trim();
        if (base.toLowerCase().contains("/viewer_ext/estudo")) {
            URI uri = URI.create(base);
            return uri.getScheme() + "://" + uri.getAuthority() + "/api/gerar_acesso_viewer/" + encodePath(idImagem);
        }

        return montarUrl(base, idImagem);
    }

    private String montarUrl(String baseUrl, String... partes) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        StringBuilder caminho = new StringBuilder(base);
        for (String parte : partes) {
            if (parte == null || parte.isBlank()) continue;
            caminho.append('/').append(encodePath(parte));
        }
        return caminho.toString();
    }

    private String encodePath(String valor) {
        return URI.create("x://x/" + valor.trim().replace(" ", "%20")).getRawPath().substring(1);
    }

    private String extrairJson(String json, Pattern pattern) {
        Matcher matcher = pattern.matcher(json == null ? "" : json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean isRespostaImagemIndisponivel(String valor) {
        String texto = normalizarTexto(valor);
        return texto.contains("\"codigo\":\"3\"")
                || texto.contains("\"codigo\":3")
                || texto.contains("exame nao foi encontrado")
                || texto.contains("exame nao encontrado")
                || texto.contains("acesso expirado")
                || texto.contains("token de acesso")
                || texto.contains("registre um novo token")
                || texto.contains("argumentcounterror")
                || texto.contains("too few arguments")
                || texto.contains("uncaught exception");
    }

    private String resumoErroPacs(String valor) {
        String texto = normalizarTexto(valor);
        if (texto.contains("exame")) return "Exame nao foi encontrado";
        if (texto.contains("token")) return "Token de acesso PACS invalido ou expirado";
        if (texto.contains("codigo")) return "Imagem ainda indisponivel no PACS";
        return "Imagem ainda indisponivel no PACS";
    }

    private String normalizarTexto(String valor) {
        if (valor == null) return "";
        return java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private Response jsonOk(String json) {
        return Response.ok(json)
                .header("Content-Type", "application/json")
                .build();
    }

    private String escapeJson(String valor) {
        return valor == null ? "" : valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

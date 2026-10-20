package br.com.iv.util;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.jboss.logging.Logger;

import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
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

    private static final Logger LOG = Logger.getLogger(PacsProxyResource.class);
    private static final String INTERMEDIARIOS_PEM = "/certs/letsencrypt-intermediarios.pem";
    private static final int MAX_REDIRECTS = 5;
    private static final Set<String> HOSTS_PACS_PADRAO = Set.of("ris.itaguai.rj.gov.br", "pacs.itaguai.rj.gov.br");
    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    private final HttpClient httpClient = criarHttpClient();

    // O PACS devolve accession_number e tipo_acesso como número ("accession_number":147779), não como texto.
    private static final Pattern ACCESSION_PATTERN = Pattern.compile("\"accession_number\"\\s*:\\s*\"?([^\",}\\s]+)\"?");
    private static final Pattern TIPO_ACESSO_PATTERN = Pattern.compile("\"tipo_acesso\"\\s*:\\s*\"?([^\",}\\s]+)\"?");
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
            ConfiguracaoUnidade config = buscarConfiguracaoUnidade(unidade);
            String tokenPacs = buscarTokenPacs(config, tipo);
            List<String> acceptHeaders = headers.getRequestHeader("Accept");
            List<String> originHeaders = headers.getRequestHeader("Origin");

            URI targetUri = URI.create(normalizarUrlPacs(url));
            Set<String> hostsPermitidos = hostsPacsPermitidos(config);
            int redirectCount = 0;

            while (redirectCount <= MAX_REDIRECTS) {
                // O proxy envia o token do PACS/RIS: só pode chamar os hosts configurados.
                if (!hostPermitido(targetUri, hostsPermitidos)) {
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("{\"erro\":\"Destino nao permitido para o proxy PACS\"}")
                            .header("Content-Type", "application/json")
                            .build();
                }

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
                + "\"urlconexaoimagempacs\":\"" + escapeJson(normalizarUrlPacs(config.getUrlconexaoimagempacs())) + "\","
                + "\"urlconexaolaudopacs\":\"" + escapeJson(normalizarUrlPacs(config.getUrlconexaolaudopacs())) + "\""
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

        String baseImagem = normalizarUrlPacs(config.getUrlconexaoimagempacs());
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

    // O PACS/RIS de Itaguaí saiu da porta 9596 e passou a responder só em HTTPS (443).
    // Normaliza as URLs antigas gravadas na configuração das unidades.
    // 10.200.200.43:9596 (configurado em unidades antigas) responde "Unknown method" para laudopdf.
    static String normalizarUrlPacs(String url) {
        if (url == null || url.isBlank()) return url;
        return url.trim()
                .replaceFirst("(?i)^https?://((?:ris|pacs)\\.itaguai\\.rj\\.gov\\.br):9596(?=/|$)", "https://$1")
                .replaceFirst("(?i)^https?://10\\.200\\.200\\.43:9596(?=/api/laudopdf)", "https://ris.itaguai.rj.gov.br");
    }

    // O servidor do RIS/PACS (revproxy90) não envia o certificado intermediário da Let's Encrypt.
    // Com AIA ligado, o Java busca o intermediário pela URL do próprio certificado, sem desligar a validação.
    // Precisa valer antes da primeira conexão TLS; só tem efeito após reiniciar a JVM.
    void habilitarAiaCertificados(@Observes StartupEvent ev) {
        System.setProperty("com.sun.security.enableAIAcaIssuers", "true");
    }

    // O AIA sozinho falha quando o servidor não alcança *.i.lencr.org (cadeia: folha -> YE1 -> Root YE -> ISRG Root X2).
    // Os intermediários Let's Encrypt (geração Y) vão embutidos só para montar a cadeia;
    // as âncoras de confiança continuam sendo as do JDK e a validação segue ligada.
    private static HttpClient criarHttpClient() {
        var builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NEVER);
        try {
            builder.sslContext(sslContextComIntermediarios());
        } catch (Exception e) {
            LOG.warnf("Proxy PACS sem intermediarios embutidos, usando TLS padrao: %s", e.getMessage());
        }
        return builder.build();
    }

    static SSLContext sslContextComIntermediarios() throws Exception {
        Collection<? extends Certificate> intermediarios;
        try (InputStream in = PacsProxyResource.class.getResourceAsStream(INTERMEDIARIOS_PEM)) {
            if (in == null) throw new IllegalStateException("recurso " + INTERMEDIARIOS_PEM + " ausente");
            intermediarios = CertificateFactory.getInstance("X.509").generateCertificates(in);
        }

        TrustManagerFactory padrao = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        padrao.init((KeyStore) null);
        Set<TrustAnchor> ancoras = new HashSet<>();
        for (TrustManager tm : padrao.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                for (X509Certificate ca : x509.getAcceptedIssuers()) ancoras.add(new TrustAnchor(ca, null));
            }
        }

        PKIXBuilderParameters params = new PKIXBuilderParameters(ancoras, new X509CertSelector());
        params.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(intermediarios)));
        params.setRevocationEnabled(false);

        TrustManagerFactory pkix = TrustManagerFactory.getInstance("PKIX");
        pkix.init(new CertPathTrustManagerParameters(params));
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, pkix.getTrustManagers(), null);
        return ctx;
    }

    private Set<String> hostsPacsPermitidos(ConfiguracaoUnidade config) {
        Set<String> hosts = new HashSet<>(HOSTS_PACS_PADRAO);
        if (config != null) {
            adicionarHost(hosts, config.getUrlconexaoimagempacs());
            adicionarHost(hosts, config.getUrlconexaolaudopacs());
        }
        return hosts;
    }

    private void adicionarHost(Set<String> hosts, String url) {
        try {
            String host = URI.create(normalizarUrlPacs(url)).getHost();
            if (host != null) hosts.add(host.toLowerCase());
        } catch (Exception ignored) {
            // URL de configuração inválida: não libera host algum
        }
    }

    private boolean hostPermitido(URI uri, Set<String> hosts) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        return ("http".equals(scheme) || "https".equals(scheme)) && hosts.contains(host);
    }

    private String buscarTokenPacs(ConfiguracaoUnidade config, String tipo) {
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

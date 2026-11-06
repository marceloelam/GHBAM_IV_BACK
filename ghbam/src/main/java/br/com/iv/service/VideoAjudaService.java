package br.com.iv.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Videos de ajuda das telas, guardados em bucket proprio (nunca no de documentos de pacientes).
 *
 * O backend so confere o login e assina um link de curta duracao: quem baixa o video e o
 * navegador, direto do MinIO, com avancar e voltar nativos e sem ocupar o backend.
 * Desligado ou com falha, a lista vem vazia e o botao de video simplesmente nao aparece.
 */
@ApplicationScoped
public class VideoAjudaService {

    private static final Logger LOG = Logger.getLogger(VideoAjudaService.class);
    private static final DateTimeFormatter AMZ_DATA_HORA = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter AMZ_DATA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int VALIDADE_LINK_SEGUNDOS = 900;
    private static final long VALIDADE_LISTA_MS = 300_000;
    private static final String LISTA_VAZIA = "[]";

    @ConfigProperty(name = "ghbam.videos.s3.habilitado", defaultValue = "false")
    boolean habilitado;

    /** Como o backend alcanca o MinIO. */
    @ConfigProperty(name = "ghbam.videos.s3.endpoint", defaultValue = "http://127.0.0.1:9010")
    String endpoint;

    /** Como o navegador alcanca o MinIO; em producao, HTTPS. Vazio = o mesmo do backend. */
    @ConfigProperty(name = "ghbam.videos.s3.endpoint-publico")
    Optional<String> endpointPublico;

    @ConfigProperty(name = "ghbam.videos.s3.bucket", defaultValue = "ghbam-videos")
    String bucket;

    @ConfigProperty(name = "ghbam.videos.s3.regiao", defaultValue = "us-east-1")
    String regiao;

    @ConfigProperty(name = "ghbam.videos.s3.access-key")
    Optional<String> accessKey;

    @ConfigProperty(name = "ghbam.videos.s3.secret-key")
    Optional<String> secretKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile String listaEmCache;
    private volatile long listaLidaEm;

    public boolean habilitado() {
        return habilitado && accessKey.filter(v -> !v.isBlank()).isPresent()
                && secretKey.filter(v -> !v.isBlank()).isPresent();
    }

    /** Conteudo de videos.json. Em falha, devolve a ultima lista lida ou uma lista vazia. */
    public String lista() {
        if (!habilitado()) {
            return LISTA_VAZIA;
        }
        String atual = listaEmCache;
        if (atual != null && System.currentTimeMillis() - listaLidaEm < VALIDADE_LISTA_MS) {
            return atual;
        }
        try {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(assinar(endpoint, "videos.json", ZonedDateTime.now(ZoneOffset.UTC))))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resposta = httpClient.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resposta.statusCode() != 200) {
                LOG.warnf("Lista de videos de ajuda indisponivel: HTTP %d", resposta.statusCode());
                return atual != null ? atual : LISTA_VAZIA;
            }
            listaEmCache = resposta.body();
            listaLidaEm = System.currentTimeMillis();
            return listaEmCache;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return atual != null ? atual : LISTA_VAZIA;
        } catch (Exception e) {
            LOG.warnf("Lista de videos de ajuda indisponivel: %s", e.getMessage());
            return atual != null ? atual : LISTA_VAZIA;
        }
    }

    /** Link temporario do video. O codigo ja chega validado como MM.SS pelo controller. */
    public String link(String codigo) {
        String publico = endpointPublico.filter(v -> !v.isBlank()).orElse(endpoint);
        return assinar(publico, "videos/" + codigo + ".mp4", ZonedDateTime.now(ZoneOffset.UTC));
    }

    /** URL pre-assinada (SigV4 por query string). O prefixo do endpoint nao entra na assinatura. */
    String assinar(String base, String chave, ZonedDateTime agora) {
        try {
            String semBarra = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            URI uri = URI.create(semBarra);
            String host = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
            String dataHora = AMZ_DATA_HORA.format(agora);
            String data = AMZ_DATA.format(agora);
            String escopo = data + "/" + regiao + "/s3/aws4_request";
            String caminho = "/" + bucket + "/" + chave;

            String consulta = "X-Amz-Algorithm=AWS4-HMAC-SHA256"
                    + "&X-Amz-Credential=" + codificar(accessKey.orElse("") + "/" + escopo)
                    + "&X-Amz-Date=" + dataHora
                    + "&X-Amz-Expires=" + VALIDADE_LINK_SEGUNDOS
                    + "&X-Amz-SignedHeaders=host";

            String canonica = "GET\n" + caminho + "\n" + consulta + "\nhost:" + host + "\n\nhost\nUNSIGNED-PAYLOAD";
            String textoAssinar = "AWS4-HMAC-SHA256\n" + dataHora + "\n" + escopo + "\n"
                    + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonica.getBytes(StandardCharsets.UTF_8)));

            byte[] k = hmac(("AWS4" + secretKey.orElse("")).getBytes(StandardCharsets.UTF_8), data);
            k = hmac(k, regiao);
            k = hmac(k, "s3");
            k = hmac(k, "aws4_request");
            String assinatura = HexFormat.of().formatHex(hmac(k, textoAssinar));

            return semBarra + caminho + "?" + consulta + "&X-Amz-Signature=" + assinatura;
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel assinar o link do video", e);
        }
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static byte[] hmac(byte[] chave, String dados) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return mac.doFinal(dados.getBytes(StandardCharsets.UTF_8));
    }
}

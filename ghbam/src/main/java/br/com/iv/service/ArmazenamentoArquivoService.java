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
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.hibernate.orm.panache.Panache;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Guarda arquivos (PDF, imagens) em object storage compativel com S3 (MinIO)
 * em vez de Base64 dentro do banco. O banco fica so com a chave do objeto.
 *
 * Desligado por padrao: com ghbam.armazenamento.s3.habilitado=false os modulos
 * continuam gravando no banco como antes. Fala S3 direto pelo HttpClient do JDK
 * (assinatura AWS SigV4, path-style), sem dependencia nova no pom.
 */
@ApplicationScoped
public class ArmazenamentoArquivoService {

    private static final Logger LOG = Logger.getLogger(ArmazenamentoArquivoService.class);
    private static final DateTimeFormatter AMZ_DATA_HORA = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter AMZ_DATA = DateTimeFormatter.ofPattern("yyyyMMdd");

    @ConfigProperty(name = "ghbam.armazenamento.s3.habilitado", defaultValue = "false")
    boolean habilitado;

    @ConfigProperty(name = "ghbam.armazenamento.s3.endpoint", defaultValue = "http://127.0.0.1:9000")
    String endpoint;

    @ConfigProperty(name = "ghbam.armazenamento.s3.regiao", defaultValue = "us-east-1")
    String regiao;

    @ConfigProperty(name = "ghbam.armazenamento.s3.bucket", defaultValue = "ghbam-documentos")
    String bucket;

    @ConfigProperty(name = "ghbam.armazenamento.s3.access-key")
    Optional<String> accessKey;

    @ConfigProperty(name = "ghbam.armazenamento.s3.secret-key")
    Optional<String> secretKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile boolean bucketVerificado;

    /** Ligado e com credenciais: so entao os modulos gravam fora do banco. */
    public boolean habilitado() {
        return habilitado && accessKey.filter(s -> !s.isBlank()).isPresent()
                && secretKey.filter(s -> !s.isBlank()).isPresent();
    }

    /**
     * Pasta da unidade (sigla) dentro do modulo, para achar arquivos no console do MinIO.
     * So letras, numeros, hifen e sublinhado; sem unidade vai para SEM-UNIDADE.
     */
    public static String pastaUnidade(String unidade) {
        String sigla = paraPasta(unidade, 40);
        return sigla.isEmpty() ? "SEM-UNIDADE" : sigla;
    }

    /**
     * Pasta do paciente, a pedido do Marcelo: nome seguido do identificador
     * (id do paciente, senao prontuario, senao BAM), ex. "MARLON-MARTINS_pac-123/".
     * Vazio quando nao ha paciente. O nome fica visivel no console: acesso restrito.
     */
    public static String pastaPaciente(String nome, Integer idpaciente, String prontuario, Integer idbam) {
        String ident = idpaciente != null && idpaciente > 0 ? "pac-" + idpaciente
                : !paraPasta(prontuario, 40).isEmpty() ? "pront-" + paraPasta(prontuario, 40)
                : idbam != null && idbam > 0 ? "bam-" + idbam : "";
        String nomePasta = paraPasta(nome, 60);
        if (nomePasta.isEmpty() && ident.isEmpty()) return "";
        return (nomePasta.isEmpty() ? ident : ident.isEmpty() ? nomePasta : nomePasta + "_" + ident) + "/";
    }

    /**
     * Pasta do paciente pelo BAM (nome e id do paciente), a mesma usada pelo
     * laboratorio e pelos visitantes. Sem BAM valido devolve vazio.
     */
    public String pastaPacienteDoBam(int bam) {
        if (bam <= 0) return "";
        try {
            List<Object[]> r = Panache.getEntityManager()
                    .createQuery("select b.paciente.nome, b.paciente.idpaciente from Bam b where b.idbam = :bam", Object[].class)
                    .setParameter("bam", bam).setMaxResults(1).getResultList();
            if (r.isEmpty()) return pastaPaciente(null, null, null, bam);
            return pastaPaciente((String) r.get(0)[0], (Integer) r.get(0)[1], null, bam);
        } catch (RuntimeException falha) {
            LOG.warnf("Armazenamento: paciente do BAM %d nao encontrado para a pasta do MinIO: %s", bam, falha.getMessage());
            return pastaPaciente(null, null, null, bam);
        }
    }

    /** Maiusculas sem acento; so letras, numeros, hifen e sublinhado. */
    private static String paraPasta(String texto, int maximo) {
        String s = texto == null ? "" : java.text.Normalizer.normalize(texto.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase().replaceAll("[^A-Z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        return s.length() > maximo ? s.substring(0, maximo).replaceAll("-+$", "") : s;
    }

    /** Grava o arquivo e devolve a chave do objeto, que vai para o banco. */
    public String salvar(String chave, byte[] conteudo, String tipoMime) {
        garantirBucket();
        String mime = tipoMime == null || tipoMime.isBlank() ? "application/octet-stream" : tipoMime;
        HttpResponse<byte[]> r = enviar("PUT", "/" + bucket + "/" + chave, conteudo, mime);
        if (r.statusCode() == 404) {
            // Bucket apagado com o backend no ar (ex.: pelo console): recria e tenta de novo.
            LOG.warnf("Armazenamento: bucket %s sumiu, recriando", bucket);
            bucketVerificado = false;
            garantirBucket();
            r = enviar("PUT", "/" + bucket + "/" + chave, conteudo, mime);
        }
        if (r.statusCode() != 200) {
            throw new IllegalStateException("Armazenamento recusou o arquivo (HTTP " + r.statusCode() + ")");
        }
        return chave;
    }

    /** Confere endpoint, credenciais e bucket; lanca IllegalStateException com o motivo. */
    public void verificar() {
        bucketVerificado = false;
        garantirBucket();
    }

    public String endpoint() {
        return endpoint;
    }

    public byte[] ler(String chave) {
        HttpResponse<byte[]> r = enviar("GET", "/" + bucket + "/" + chave, new byte[0], null);
        if (r.statusCode() != 200) {
            throw new IllegalStateException((r.statusCode() == 404 ? "Arquivo nao encontrado no armazenamento" : "Armazenamento recusou a leitura")
                    + " (HTTP " + r.statusCode() + ")");
        }
        return r.body();
    }

    /**
     * Copia para a nova chave e so depois apaga a original: se algo falhar no meio,
     * o arquivo continua existindo em pelo menos um dos dois lugares.
     */
    public void mover(String origem, String destino, String tipoMime) {
        salvar(destino, ler(origem), tipoMime);
        int status = enviar("DELETE", "/" + bucket + "/" + origem, new byte[0], null).statusCode();
        if (status != 204 && status != 200 && status != 404) {
            LOG.warnf("Armazenamento: copia %s criada, mas a original %s nao foi apagada (HTTP %d)", destino, origem, status);
        }
    }

    private void garantirBucket() {
        if (bucketVerificado) return;
        synchronized (this) {
            if (bucketVerificado) return;
            HttpResponse<byte[]> head = enviar("HEAD", "/" + bucket, new byte[0], null);
            // O console do MinIO (9001) responde 200 a tudo; so a API S3 manda x-amz-request-id.
            if (head.headers().firstValue("x-amz-request-id").isEmpty()) {
                throw new IllegalStateException("Endpoint " + endpoint + " nao e a API S3 do MinIO"
                        + " (porta do console? use a porta da API em GHBAM_S3_ENDPOINT)");
            }
            int status = head.statusCode();
            if (status == 404) {
                int criado = enviar("PUT", "/" + bucket, new byte[0], null).statusCode();
                if (criado != 200) throw new IllegalStateException("Nao foi possivel criar o bucket " + bucket + " (HTTP " + criado + ")");
                LOG.infof("Armazenamento: bucket %s criado", bucket);
            } else if (status != 200) {
                throw new IllegalStateException("Bucket " + bucket + " inacessivel (HTTP " + status + ")");
            }
            bucketVerificado = true;
        }
    }

    private HttpResponse<byte[]> enviar(String metodo, String caminho, byte[] corpo, String tipoMime) {
        try {
            URI base = URI.create(endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint);
            String caminhoCodificado = codificarCaminho(caminho);
            String host = base.getPort() > 0 ? base.getHost() + ":" + base.getPort() : base.getHost();
            ZonedDateTime agora = ZonedDateTime.now(ZoneOffset.UTC);
            String dataHora = AMZ_DATA_HORA.format(agora);
            String data = AMZ_DATA.format(agora);
            String hashCorpo = hex(sha256(corpo));

            String headersCanonicos = "host:" + host + "\n" + "x-amz-content-sha256:" + hashCorpo + "\n" + "x-amz-date:" + dataHora + "\n";
            String headersAssinados = "host;x-amz-content-sha256;x-amz-date";
            String requisicaoCanonica = metodo + "\n" + caminhoCodificado + "\n\n" + headersCanonicos + "\n" + headersAssinados + "\n" + hashCorpo;
            String escopo = data + "/" + regiao + "/s3/aws4_request";
            String textoAssinar = "AWS4-HMAC-SHA256\n" + dataHora + "\n" + escopo + "\n" + hex(sha256(requisicaoCanonica.getBytes(StandardCharsets.UTF_8)));

            byte[] chave = hmac(("AWS4" + secretKey.orElse("")).getBytes(StandardCharsets.UTF_8), data);
            chave = hmac(chave, regiao);
            chave = hmac(chave, "s3");
            chave = hmac(chave, "aws4_request");
            String assinatura = hex(hmac(chave, textoAssinar));

            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base.getScheme() + "://" + host + caminhoCodificado))
                    .timeout(Duration.ofSeconds(60))
                    .header("x-amz-date", dataHora)
                    .header("x-amz-content-sha256", hashCorpo)
                    .header("Authorization", "AWS4-HMAC-SHA256 Credential=" + accessKey.orElse("") + "/" + escopo
                            + ", SignedHeaders=" + headersAssinados + ", Signature=" + assinatura);
            if (tipoMime != null) b.header("Content-Type", tipoMime);
            if ("PUT".equals(metodo)) b.PUT(HttpRequest.BodyPublishers.ofByteArray(corpo));
            else b.method(metodo, HttpRequest.BodyPublishers.noBody());
            return httpClient.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Armazenamento interrompido", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Armazenamento indisponivel: " + e.getMessage(), e);
        }
    }

    /** Caminho sempre comeca com "/"; cada segmento e codificado como o S3 espera. */
    private static String codificarCaminho(String caminho) {
        StringBuilder sb = new StringBuilder();
        for (String parte : caminho.substring(1).split("/", -1)) {
            sb.append('/').append(URLEncoder.encode(parte, StandardCharsets.UTF_8).replace("+", "%20").replace("%7E", "~"));
        }
        return sb.toString();
    }

    private static byte[] sha256(byte[] dados) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(dados);
    }

    private static byte[] hmac(byte[] chave, String dados) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return mac.doFinal(dados.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] dados) {
        return HexFormat.of().formatHex(dados);
    }
}

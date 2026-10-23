package br.com.iv.imagemia.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Baixa o DICOM de um exame a partir do Orthanc INTERNO do GHBAM (nao do PACS do
 * fornecedor). A chave e o AccessionNumber, que no GHBAM e o idimagens (ver view
 * worklist_v: accessionnumber = imagens.idimagens). Usa a REST API do Orthanc
 * (/tools/find + /instances/{id}/file), que devolve o DICOM bruto - sem parsing
 * de multipart. A URL configurada na unidade pode trazer credenciais embutidas
 * (http://usuario:senha@host:porta/...), usadas como Basic Auth.
 */
@ApplicationScoped
public class OrthancDicomService {

    private static final Logger LOG = Logger.getLogger(OrthancDicomService.class);

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Base REST do Orthanc a partir da URL configurada (tira /dicom-web e credenciais). */
    static String baseRest(String orthancUrl) {
        if (orthancUrl == null) return null;
        String u = orthancUrl.trim();
        if (u.isEmpty()) return null;
        u = u.replaceAll("/+$", "");
        u = u.replaceAll("(?i)/dicom-web$", "");
        return u;
    }

    /** Basic Auth a partir do userinfo da URL (usuario:senha@), se houver. */
    static String basicAuth(String orthancUrl) {
        try {
            URI uri = URI.create(orthancUrl.trim());
            String info = uri.getUserInfo();
            if (info == null || info.isBlank()) return null;
            return "Basic " + Base64.getEncoder().encodeToString(info.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /** Remove o userinfo da URL para montar os endpoints (HttpClient nao usa userinfo). */
    static String semCredenciais(String url) {
        return url.replaceFirst("://[^@/]+@", "://");
    }

    /** DICOM baixado + PatientID gravado nele (para conferir o paciente). */
    public record Estudo(byte[] dicom, String patientId) {}

    /**
     * Compara o PatientID do DICOM com o idpaciente do GHBAM (a worklist envia
     * pacienteid = idpaciente). Ignora espacos e zeros a esquerda. Sem PatientID
     * nao confere: na duvida, nao analisa.
     */
    static boolean mesmoPaciente(String patientIdDicom, Integer idPaciente) {
        if (patientIdDicom == null || idPaciente == null) return false;
        String p = patientIdDicom.trim().replaceFirst("^0+(?=\\d)", "");
        return !p.isEmpty() && p.equals(String.valueOf(idPaciente));
    }

    /**
     * Procura o exame no Orthanc pelo AccessionNumber (= idimagens) e devolve o
     * DICOM de uma instancia com o PatientID. null se nao configurado ou nao encontrado.
     */
    public Estudo baixarPorAccession(String orthancUrl, String accessionNumber) {
        String base = baseRest(orthancUrl);
        if (base == null || accessionNumber == null || accessionNumber.isBlank()) {
            return null;
        }
        String auth = basicAuth(orthancUrl);
        base = semCredenciais(base);
        if (!ehOrthanc(base, auth)) {
            // PACS de outro fornecedor: DICOMweb padrao na URL como foi configurada.
            return baixarPorDicomWeb(semCredenciais(orthancUrl.trim().replaceAll("/+$", "")), auth, accessionNumber);
        }
        try {
            // 1) /tools/find -> id(s) de instancia com aquele AccessionNumber
            String corpo = objectMapper.writeValueAsString(java.util.Map.of(
                    "Level", "Instance",
                    "Query", java.util.Map.of("AccessionNumber", accessionNumber),
                    "Limit", 1));
            HttpResponse<String> find = enviar(HttpRequest.newBuilder()
                    .uri(URI.create(base + "/tools/find"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo)), auth, HttpResponse.BodyHandlers.ofString());
            recusarSemAutenticacao(find.statusCode(), auth);
            if (find.statusCode() / 100 != 2) {
                LOG.warnf("Orthanc /tools/find HTTP %d", find.statusCode());
                return null;
            }
            JsonNode ids = objectMapper.readTree(find.body());
            if (!ids.isArray() || ids.isEmpty()) {
                return null; // exame nao esta no Orthanc
            }
            String instanceId = ids.get(0).asText();

            // 2) /instances/{id}/simplified-tags -> PatientID, para conferir o paciente
            HttpResponse<String> tags = enviar(HttpRequest.newBuilder()
                    .uri(URI.create(base + "/instances/" + instanceId + "/simplified-tags"))
                    .GET(), auth, HttpResponse.BodyHandlers.ofString());
            String patientId = null;
            if (tags.statusCode() / 100 == 2) {
                JsonNode pid = objectMapper.readTree(tags.body()).get("PatientID");
                patientId = pid == null || pid.isNull() ? null : pid.asText();
            } else {
                LOG.warnf("Orthanc /instances/simplified-tags HTTP %d", tags.statusCode());
            }

            // 3) /instances/{id}/file -> DICOM bruto
            HttpResponse<byte[]> file = enviar(HttpRequest.newBuilder()
                    .uri(URI.create(base + "/instances/" + instanceId + "/file"))
                    .GET(), auth, HttpResponse.BodyHandlers.ofByteArray());
            if (file.statusCode() / 100 != 2) {
                LOG.warnf("Orthanc /instances/file HTTP %d", file.statusCode());
                return null;
            }
            return new Estudo(file.body(), patientId);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            LOG.warnf("Falha ao buscar DICOM no Orthanc: %s", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // DICOMweb padrao (PS3.18): PACS de fornecedor que libere QIDO-RS/WADO-RS.
    // O Orthanc segue pela REST propria (acima), que ja esta validada.
    // ------------------------------------------------------------------

    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> tipoServidor =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Orthanc responde GET /system com JSON que tem "DicomAet"; outro PACS nao. Guardado por URL. */
    private boolean ehOrthanc(String base, String auth) {
        return tipoServidor.computeIfAbsent(base, b -> {
            try {
                HttpResponse<String> r = enviar(HttpRequest.newBuilder()
                        .uri(URI.create(b + "/system")).GET(), auth, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 401 || r.statusCode() == 403) {
                    return true; // credencial errada: deixa o fluxo do Orthanc dar a mensagem clara
                }
                return r.statusCode() / 100 == 2 && r.body().contains("\"DicomAet\"");
            } catch (Exception e) {
                return true; // fora do ar: o fluxo atual registra a falha como antes
            }
        });
    }

    /** QIDO (estudo -> 1a instancia) + WADO-RS (multipart/related com o DICOM). */
    Estudo baixarPorDicomWeb(String dw, String auth, String accessionNumber) {
        try {
            HttpResponse<String> estudos = enviar(HttpRequest.newBuilder()
                    .uri(URI.create(dw + "/studies?AccessionNumber="
                            + java.net.URLEncoder.encode(accessionNumber, StandardCharsets.UTF_8) + "&limit=1"))
                    .header("Accept", "application/dicom+json").GET(), auth, HttpResponse.BodyHandlers.ofString());
            recusarSemAutenticacao(estudos.statusCode(), auth);
            if (estudos.statusCode() == 204 || estudos.statusCode() / 100 != 2 || estudos.body().isBlank()) {
                return null; // exame nao esta no PACS
            }
            JsonNode lista = objectMapper.readTree(estudos.body());
            if (!lista.isArray() || lista.isEmpty()) return null;
            String studyUid = valorTag(lista.get(0), "0020000D");
            if (studyUid == null) return null;

            HttpResponse<String> inst = enviar(HttpRequest.newBuilder()
                    .uri(URI.create(dw + "/studies/" + studyUid + "/instances?limit=1&includefield=00100020"))
                    .header("Accept", "application/dicom+json").GET(), auth, HttpResponse.BodyHandlers.ofString());
            if (inst.statusCode() / 100 != 2 || inst.body().isBlank()) return null;
            JsonNode i0 = objectMapper.readTree(inst.body()).path(0);
            String serieUid = valorTag(i0, "0020000E");
            String sopUid = valorTag(i0, "00080018");
            String patientId = valorTag(i0, "00100020");
            if (serieUid == null || sopUid == null) return null;

            HttpResponse<byte[]> wado = enviar(HttpRequest.newBuilder()
                    .uri(URI.create(dw + "/studies/" + studyUid + "/series/" + serieUid + "/instances/" + sopUid))
                    .header("Accept", "multipart/related; type=\"application/dicom\"; transfer-syntax=*")
                    .GET(), auth, HttpResponse.BodyHandlers.ofByteArray());
            if (wado.statusCode() / 100 != 2) {
                LOG.warnf("DICOMweb WADO-RS HTTP %d", wado.statusCode());
                return null;
            }
            byte[] dicom = primeiraParteMultipart(wado.body(), wado.headers().firstValue("Content-Type").orElse(""));
            return dicom == null ? null : new Estudo(dicom, patientId);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            LOG.warnf("Falha ao buscar DICOM via DICOMweb: %s", e.getMessage());
            return null;
        }
    }

    /** Primeiro valor de uma tag no JSON DICOM (PS3.18 F.2): {"0020000D":{"vr":"UI","Value":["1.2..."]}}. */
    static String valorTag(JsonNode item, String tag) {
        JsonNode v = item == null ? null : item.path(tag).path("Value").path(0);
        return v == null || v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    /**
     * Corpo da primeira parte de um multipart/related (WADO-RS). Sem boundary no
     * Content-Type, devolve o corpo inteiro (servidor que respondeu application/dicom).
     */
    static byte[] primeiraParteMultipart(byte[] corpo, String contentType) {
        if (corpo == null || corpo.length == 0) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)boundary=\"?([^\";]+)\"?").matcher(contentType == null ? "" : contentType);
        if (!m.find()) return corpo;
        byte[] marca = ("--" + m.group(1).trim()).getBytes(StandardCharsets.ISO_8859_1);
        int inicio = indexOf(corpo, marca, 0);
        if (inicio < 0) return null;
        byte[] fimCabecalho = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        int dados = indexOf(corpo, fimCabecalho, inicio + marca.length);
        if (dados < 0) return null;
        dados += fimCabecalho.length;
        byte[] proxima = ("\r\n--" + m.group(1).trim()).getBytes(StandardCharsets.ISO_8859_1);
        int fim = indexOf(corpo, proxima, dados);
        if (fim < 0) fim = corpo.length;
        return java.util.Arrays.copyOfRange(corpo, dados, fim);
    }

    private static int indexOf(byte[] dados, byte[] alvo, int de) {
        outer:
        for (int i = Math.max(0, de); i <= dados.length - alvo.length; i++) {
            for (int j = 0; j < alvo.length; j++) {
                if (dados[i + j] != alvo[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Tomografia: o modelo precisa da SERIE inteira (volume), nao de 1 corte.
    // ------------------------------------------------------------------

    /** Serie de TC escolhida para a IA + PatientID (do 1o corte) e quantidade de cortes. */
    public record SerieTc(String id, String descricao, int cortes, String patientId) {}

    /** Limites da serie: abaixo nao e volume diagnostico; acima pesa demais em CPU. */
    static final int MIN_CORTES = 20;
    static final int MAX_CORTES = 600;

    private static final java.util.regex.Pattern DESCARTAR = java.util.regex.Pattern.compile(
            "(?i)scout|localiz|topogra|surview|smart ?prep|bolus|monitor|dose|\\bmip\\b|\\b3d\\b|\\bvr\\b"
            + "|\\bcor\\b|coronal|\\bsag\\b|sagit|osso|bone|pulm|lung");
    private static final java.util.regex.Pattern PREFERIR = java.util.regex.Pattern.compile(
            "(?i)cerebr|encef|brain|cranio|crânio|head|partes moles|soft|\\bstd\\b|standard|nativ|natyve|sem contraste");

    /**
     * Escolhe a serie axial de partes moles de um estudo de TC. Descarta scout,
     * reconstrucoes coronais/sagitais, MIP/3D, janela ossea/pulmonar e series
     * curtas ou longas demais. Entre as que sobram, prefere a descricao de
     * cranio/partes moles e, empatando, a de MENOS cortes (corte mais espesso,
     * mais proximo do treino do modelo e mais leve em CPU). null se nao houver.
     */
    static JsonNode escolherSerie(JsonNode series) {
        JsonNode melhor = null;
        boolean melhorPreferida = false;
        int melhorCortes = Integer.MAX_VALUE;
        if (series == null || !series.isArray()) return null;
        for (JsonNode s : series) {
            JsonNode tags = s.path("MainDicomTags");
            if (!"CT".equalsIgnoreCase(tags.path("Modality").asText(""))) continue;
            String desc = tags.path("SeriesDescription").asText("");
            int cortes = s.path("Instances").size();
            if (cortes < MIN_CORTES || cortes > MAX_CORTES || DESCARTAR.matcher(desc).find()) continue;
            boolean preferida = PREFERIR.matcher(desc).find();
            if (melhor == null || (preferida && !melhorPreferida)
                    || (preferida == melhorPreferida && cortes < melhorCortes)) {
                melhor = s;
                melhorPreferida = preferida;
                melhorCortes = cortes;
            }
        }
        return melhor;
    }

    /** true se o estudo do AccessionNumber tem alguma serie de TC (modalidade CT). */
    static boolean temTc(JsonNode series) {
        if (series == null || !series.isArray()) return false;
        for (JsonNode s : series) {
            if ("CT".equalsIgnoreCase(s.path("MainDicomTags").path("Modality").asText(""))) return true;
        }
        return false;
    }

    /** Series (expandidas) do exame no Orthanc; array vazio se nao achar. */
    public JsonNode seriesPorAccession(String orthancUrl, String accessionNumber) throws Exception {
        String base = baseRest(orthancUrl);
        if (base == null || accessionNumber == null || accessionNumber.isBlank()) {
            return objectMapper.createArrayNode();
        }
        String auth = basicAuth(orthancUrl);
        base = semCredenciais(base);
        String corpo = objectMapper.writeValueAsString(java.util.Map.of(
                "Level", "Series",
                "Query", java.util.Map.of("AccessionNumber", accessionNumber),
                "Expand", true));
        HttpResponse<String> find = enviar(HttpRequest.newBuilder()
                .uri(URI.create(base + "/tools/find"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)), auth, HttpResponse.BodyHandlers.ofString());
        recusarSemAutenticacao(find.statusCode(), auth);
        if (find.statusCode() / 100 != 2) {
            LOG.warnf("Orthanc /tools/find (series) HTTP %d", find.statusCode());
            return objectMapper.createArrayNode();
        }
        return objectMapper.readTree(find.body());
    }

    /** Monta a SerieTc (com PatientID do 1o corte) a partir da serie escolhida. */
    public SerieTc descreverSerie(String orthancUrl, JsonNode serie) throws Exception {
        String auth = basicAuth(orthancUrl);
        String base = semCredenciais(baseRest(orthancUrl));
        String primeira = serie.path("Instances").path(0).asText("");
        String patientId = null;
        if (!primeira.isBlank()) {
            HttpResponse<String> tags = enviar(HttpRequest.newBuilder()
                    .uri(URI.create(base + "/instances/" + primeira + "/simplified-tags"))
                    .GET(), auth, HttpResponse.BodyHandlers.ofString());
            if (tags.statusCode() / 100 == 2) {
                JsonNode pid = objectMapper.readTree(tags.body()).get("PatientID");
                patientId = pid == null || pid.isNull() ? null : pid.asText();
            } else {
                LOG.warnf("Orthanc /instances/simplified-tags HTTP %d", tags.statusCode());
            }
        }
        return new SerieTc(serie.path("ID").asText(),
                serie.path("MainDicomTags").path("SeriesDescription").asText(""),
                serie.path("Instances").size(), patientId);
    }

    /** Baixa a serie inteira como ZIP de DICOMs (GET /series/{id}/archive). */
    public byte[] baixarSerieZip(String orthancUrl, String serieId) throws Exception {
        String auth = basicAuth(orthancUrl);
        String base = semCredenciais(baseRest(orthancUrl));
        HttpResponse<byte[]> zip = httpClient.send(comAuth(HttpRequest.newBuilder()
                .uri(URI.create(base + "/series/" + serieId + "/archive"))
                .GET(), auth).timeout(Duration.ofMinutes(5)).build(), HttpResponse.BodyHandlers.ofByteArray());
        recusarSemAutenticacao(zip.statusCode(), auth);
        if (zip.statusCode() / 100 != 2) {
            throw new IllegalStateException("Orthanc nao entregou a serie (HTTP " + zip.statusCode() + ").");
        }
        return zip.body();
    }

    /** Teste de conexao (GET /system): codigo HTTP; 401/403 = credencial ausente/errada. */
    public int testar(String orthancUrl) throws Exception {
        String base = baseRest(orthancUrl);
        if (base == null) return 0;
        String auth = basicAuth(orthancUrl);
        HttpResponse<Void> r = httpClient.send(comAuth(HttpRequest.newBuilder()
                .uri(URI.create(semCredenciais(base) + "/system"))
                .GET(), auth).timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.discarding());
        return r.statusCode();
    }

    private static void recusarSemAutenticacao(int status, String auth) {
        if (status == 401 || status == 403) {
            LOG.warnf("Orthanc HTTP %d (%s)", status,
                    auth == null ? "URL da unidade sem usuario:senha" : "usuario/senha recusados pelo Orthanc");
            throw new IllegalStateException(auth == null
                    ? "Orthanc exige autenticacao: informe usuario:senha na URL do Orthanc da unidade."
                    : "Orthanc recusou o usuario/senha configurados na unidade.");
        }
    }

    private static HttpRequest.Builder comAuth(HttpRequest.Builder b, String auth) {
        if (auth != null) b.header("Authorization", auth);
        return b;
    }

    private <T> HttpResponse<T> enviar(HttpRequest.Builder b, String auth, HttpResponse.BodyHandler<T> handler)
            throws Exception {
        return httpClient.send(comAuth(b, auth).timeout(Duration.ofSeconds(60)).build(), handler);
    }
}

package br.com.iv.aps;

import static br.com.iv.aps.ApsDb.p;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.iv.aps.ApsLediSerializer.Envelope;
import br.com.iv.aps.ApsLediSerializer.Instalacao;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Transmissao LEDI para o PEC e-SUS APS em modo Centralizador (o SIAPS so
 * recebe dados de uma instalacao PEC - manual 3.7.4.4).
 *
 * API oficial (LEDI > API de transmissao):
 *   POST {url}/api/recebimento/login        multipart: usuario, senha  -> cookie JSESSIONID
 *   POST {url}/api/v1/recebimento/ficha     multipart: ficha=<uuid>.esus (DadoTransporteThrift binario)
 *
 * Credencial gerada no PEC em Integracao > Credenciais de integracao; aqui vem
 * SO de variavel de ambiente (GHBAM_APS_CENTRALIZADOR_URL/_USUARIO/_SENHA),
 * nunca do banco nem do frontend.
 */
@ApplicationScoped
public class ApsTransmissaoService {

    private static final Logger LOG = Logger.getLogger(ApsTransmissaoService.class);

    @Inject
    ApsDb db;

    @Inject
    ApsLediSerializer serializer;

    @Inject
    ApsFichaService fichas;

    @Inject
    ApsContextoService contexto;

    @ConfigProperty(name = "ghbam.aps.centralizador.url")
    Optional<String> url;

    @ConfigProperty(name = "ghbam.aps.centralizador.usuario")
    Optional<String> usuario;

    @ConfigProperty(name = "ghbam.aps.centralizador.senha")
    Optional<String> senha;

    /** Instalacoes PEC municipais costumam usar certificado autoassinado. */
    @ConfigProperty(name = "ghbam.aps.centralizador.aceitar-certificado-autoassinado", defaultValue = "false")
    boolean aceitarAutoassinado;

    private volatile boolean emExecucao;

    @Inject
    ApsIntegracaoService integracoes;

    /** Configuracao salva pela tela (Configuracao > Integracoes) tem prioridade sobre as variaveis do servidor. */
    private ApsIntegracaoService.Config tela() {
        ApsIntegracaoService.Config c = integracoes.config(ApsIntegracaoService.CENTRALIZADOR);
        return c != null && c.url() != null ? c : null;
    }

    private String cfgUrl() {
        ApsIntegracaoService.Config c = tela();
        return c != null ? c.url() : url.orElse("");
    }

    private String cfgUsuario() {
        ApsIntegracaoService.Config c = tela();
        return c != null ? (c.usuario() == null ? "" : c.usuario()) : usuario.orElse("");
    }

    private String cfgSenha() {
        ApsIntegracaoService.Config c = tela();
        return c != null ? (c.senha() == null ? "" : c.senha()) : senha.orElse("");
    }

    private boolean cfgAutoassinado() {
        ApsIntegracaoService.Config c = tela();
        return c != null ? c.aceitarAutoassinado() : aceitarAutoassinado;
    }

    public record Resultado(int enviadas, int rejeitadas, int erros, Long lote, String mensagem) {
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configurado", configurado());
        m.put("url", cfgUrl());
        m.put("usuarioConfigurado", !cfgUsuario().isBlank());
        m.put("aceitarCertificadoAutoassinado", cfgAutoassinado());
        m.put("origemConfiguracao", tela() != null ? "TELA" : "SERVIDOR");
        m.put("emExecucao", emExecucao);
        Map<String, Object> par = contexto.parametros();
        m.put("envioAutomatico", par != null && Boolean.TRUE.equals(par.get("envio_automatico")));
        m.put("pendentes", db.count("SELECT COUNT(*) FROM aps_ficha WHERE status = 'PRONTA'", Map.of()));
        m.put("rejeitadas", db.count("SELECT COUNT(*) FROM aps_ficha WHERE status = 'REJEITADA'", Map.of()));
        m.put("enviadasHoje", db.count("SELECT COUNT(*) FROM aps_ficha WHERE status = 'ENVIADA' AND enviado_em >= CURRENT_DATE", Map.of()));
        return m;
    }

    public boolean configurado() {
        return !cfgUrl().isBlank() && !cfgUsuario().isBlank()
                && !cfgSenha().isBlank();
    }

    @Scheduled(every = "15m", delayed = "3m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void envioAutomaticoAgendado() {
        // Em cada municipio (sem requisicao o banco seria sempre o padrao).
        br.com.iv.plataforma.municipio.MunicipioRoteamento.emCadaMunicipio(ApsTransmissaoService.class, "envioAutomatico", b -> b.envioAutomatico());
    }

    void envioAutomatico() {
        try {
            Map<String, Object> par = contexto.parametros();
            if (par == null || !Boolean.TRUE.equals(par.get("envio_automatico")) || !configurado()) {
                return;
            }
            Resultado r = enviarPendentes(null, 500, "AUTOMATICO");
            if (r.enviadas() + r.rejeitadas() + r.erros() > 0) {
                LOG.infof("APS/LEDI envio automatico: %d enviadas, %d rejeitadas, %d erros (lote %d)",
                        r.enviadas(), r.rejeitadas(), r.erros(), r.lote());
            }
        } catch (RuntimeException e) {
            LOG.warn("APS/LEDI envio automatico falhou: " + e.getMessage());
        }
    }

    /** Envia registros PRONTA (opcionalmente so de uma unidade). */
    public synchronized Resultado enviarPendentes(String unidade, int limite, String matricula) {
        if (!configurado()) {
            throw new IllegalStateException("PEC Centralizador nao configurado. Preencha em Configuracao > Integracoes (ou defina GHBAM_APS_CENTRALIZADOR_URL, "
                    + "GHBAM_APS_CENTRALIZADOR_USUARIO e GHBAM_APS_CENTRALIZADOR_SENHA no servidor do backend).");
        }
        List<Map<String, Object>> pendentes = db.list("""
                SELECT id FROM aps_ficha
                WHERE status = 'PRONTA'
                  AND (CAST(:unidade AS varchar) IS NULL OR UPPER(unidade) = UPPER(CAST(:unidade AS varchar)))
                ORDER BY CASE WHEN tipo IN (2, 3) THEN 0 ELSE 1 END, criado_em
                LIMIT :limite
                """, p("unidade", ApsFichaService.vazioParaNulo(unidade), "limite", Math.max(1, Math.min(limite, 2000))));
        if (pendentes.isEmpty()) {
            return new Resultado(0, 0, 0, null, "Nenhum registro pendente.");
        }
        emExecucao = true;
        long lote = System.currentTimeMillis() / 1000L;
        int ok = 0;
        int rej = 0;
        int err = 0;
        try {
            HttpClient client = client();
            login(client);
            for (Map<String, Object> row : pendentes) {
                Long id = ((Number) row.get("id")).longValue();
                try {
                    Map<String, Object> ficha = fichas.detalhe(id);
                    byte[] conteudo = conteudo(ficha, lote);
                    HttpResponse<String> resp = enviarArquivo(client, (String) ficha.get("uuid"), conteudo);
                    if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                        login(client);
                        resp = enviarArquivo(client, (String) ficha.get("uuid"), conteudo);
                    }
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        db.exec("""
                                UPDATE aps_ficha SET status = 'ENVIADA', enviado_em = CURRENT_TIMESTAMP, lote = :lote,
                                       tentativas = tentativas + 1, ultimo_erro = NULL, atualizado_em = CURRENT_TIMESTAMP
                                WHERE id = :id
                                """, p("lote", lote, "id", id));
                        fichas.registrarLog(id, true, resp.statusCode(), "Aceito pelo Centralizador", lote, matricula);
                        ok++;
                    } else {
                        String msg = limitar(resp.body());
                        boolean validacao = resp.statusCode() == 400;
                        db.exec("""
                                UPDATE aps_ficha SET status = :status, tentativas = tentativas + 1, ultimo_erro = :msg,
                                       lote = :lote, atualizado_em = CURRENT_TIMESTAMP
                                WHERE id = :id
                                """, p("status", validacao ? "REJEITADA" : "PRONTA", "msg", "HTTP " + resp.statusCode() + ": " + msg,
                                "lote", lote, "id", id));
                        fichas.registrarLog(id, false, resp.statusCode(), msg, lote, matricula);
                        if (validacao) {
                            rej++;
                        } else {
                            err++;
                        }
                    }
                } catch (IllegalArgumentException e) {
                    db.exec("UPDATE aps_ficha SET status = 'REJEITADA', ultimo_erro = :msg, atualizado_em = CURRENT_TIMESTAMP WHERE id = :id",
                            p("msg", "Validacao local: " + e.getMessage(), "id", id));
                    fichas.registrarLog(id, false, null, "Validacao local: " + e.getMessage(), lote, matricula);
                    rej++;
                } catch (IOException e) {
                    fichas.registrarLog(id, false, null, "Falha de rede: " + e.getMessage(), lote, matricula);
                    err++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel conectar ao PEC Centralizador: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio interrompido.", e);
        } finally {
            emExecucao = false;
        }
        return new Resultado(ok, rej, err, lote, ok + " enviado(s), " + rej + " rejeitado(s), " + err + " com erro de comunicacao.");
    }

    /** Testa a credencial sem enviar nada. */
    public Map<String, Object> testarConexao() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!configurado()) {
            m.put("ok", false);
            m.put("naoConfigurado", true);
            m.put("mensagem", "Teste não realizado: o Centralizador ainda não foi configurado. Preencha em Configuração → Integrações.");
            return m;
        }
        try {
            login(client());
            m.put("ok", true);
            m.put("mensagem", "Login no PEC Centralizador realizado (JSESSIONID recebido).");
        } catch (IOException | IllegalStateException e) {
            m.put("ok", false);
            m.put("mensagem", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            m.put("ok", false);
            m.put("mensagem", "Interrompido.");
        }
        if (tela() != null) {
            integracoes.registrarTeste(ApsIntegracaoService.CENTRALIZADOR, Boolean.TRUE.equals(m.get("ok")), (String) m.get("mensagem"));
        }
        return m;
    }

    /** Pacote .zip de arquivos .esus para importacao manual no PEC (contingencia sem API). */
    public byte[] exportarZip(String unidade, LocalDate de, LocalDate ate, boolean incluirEnviadas) {
        List<Map<String, Object>> rows = db.list("""
                SELECT id FROM aps_ficha
                WHERE status IN ('PRONTA', 'ENVIADA')
                  AND (CAST(:todas AS boolean) OR status = 'PRONTA')
                  AND (CAST(:unidade AS varchar) IS NULL OR UPPER(unidade) = UPPER(CAST(:unidade AS varchar)))
                  AND (CAST(:de AS date) IS NULL OR data_atendimento >= CAST(:de AS date))
                  AND (CAST(:ate AS date) IS NULL OR data_atendimento <= CAST(:ate AS date))
                ORDER BY CASE WHEN tipo IN (2, 3) THEN 0 ELSE 1 END, criado_em
                LIMIT 20000
                """, p("todas", incluirEnviadas, "unidade", ApsFichaService.vazioParaNulo(unidade), "de", de, "ate", ate));
        // Um .zip vazio parecia sucesso: sem registros, ou todos recusados, a tela recebe o motivo.
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Nenhum registro pronto para exportar neste periodo.");
        }
        long lote = System.currentTimeMillis() / 1000L;
        int gravados = 0;
        String primeiroErro = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> ficha = fichas.detalhe(((Number) row.get("id")).longValue());
                try {
                    byte[] conteudo = conteudo(ficha, lote);
                    zip.putNextEntry(new ZipEntry(ficha.get("uuid") + ".esus"));
                    zip.write(conteudo);
                    zip.closeEntry();
                    gravados++;
                } catch (IllegalArgumentException e) {
                    LOG.warnf("Registro APS %s fora do zip: %s", ficha.get("uuid"), e.getMessage());
                    if (primeiroErro == null) {
                        primeiroErro = e.getMessage();
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar pacote LEDI: " + e.getMessage(), e);
        }
        if (gravados == 0) {
            throw new IllegalArgumentException("Nenhum dos " + rows.size() + " registro(s) pode ser exportado: " + primeiroErro);
        }
        return bos.toByteArray();
    }

    public void marcarComoEnviadas(List<Long> ids, String matricula) {
        for (Long id : ids) {
            int n = db.exec("""
                    UPDATE aps_ficha SET status = 'ENVIADA', enviado_em = CURRENT_TIMESTAMP, ultimo_erro = NULL,
                           atualizado_em = CURRENT_TIMESTAMP
                    WHERE id = :id AND status = 'PRONTA'
                    """, p("id", id));
            if (n > 0) {
                fichas.registrarLog(id, true, null, "Marcado como enviado (importacao manual no PEC) por " + matricula, null, matricula);
            }
        }
    }

    @SuppressWarnings("unchecked")
    byte[] conteudo(Map<String, Object> ficha, long lote) {
        Map<String, Object> par = contexto.parametros();
        String cpfCnpj = par == null ? null : (String) par.get("cpf_cnpj");
        String nome = par == null ? null : (String) par.get("nome_razao_social");
        if (cpfCnpj == null || cpfCnpj.isBlank() || nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Preencha em APS > Configuracao > Instalacao o CPF/CNPJ e o nome do responsavel "
                    + "(remetente obrigatorio do LEDI).");
        }
        Instalacao inst = new Instalacao((String) par.get("contra_chave"), (String) par.get("uuid_instalacao"),
                cpfCnpj.replaceAll("\\D", ""), nome, (String) par.get("fone"), (String) par.get("email"));
        Envelope env = new Envelope((String) ficha.get("uuid"), ((Number) ficha.get("tipo")).intValue(),
                (String) ficha.get("cnes"), (String) ficha.get("cod_ibge"), (String) ficha.get("ine"), lote);
        return serializer.serializarTransporte(env, (Map<String, Object>) ficha.get("dados"), inst);
    }

    private HttpClient client() {
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (cfgAutoassinado()) {
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[] { new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {
                    }

                    public void checkServerTrusted(X509Certificate[] c, String a) {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                } }, new SecureRandom());
                b.sslContext(ctx);
            } catch (Exception e) {
                throw new IllegalStateException("Falha configurando TLS: " + e.getMessage(), e);
            }
        }
        return b.build();
    }

    private String base() {
        String u = cfgUrl().trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private void login(HttpClient client) throws IOException, InterruptedException {
        Multipart mp = new Multipart();
        mp.campo("usuario", cfgUsuario());
        mp.campo("senha", cfgSenha());
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/api/recebimento/login"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", mp.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(mp.fechar()))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        boolean temSessao = resp.headers().allValues("set-cookie").stream().anyMatch(c -> c.startsWith("JSESSIONID"));
        if (resp.statusCode() >= 300 || !temSessao) {
            throw new IllegalStateException("Login no PEC Centralizador recusado (HTTP " + resp.statusCode() + "): " + limitar(resp.body()));
        }
    }

    private HttpResponse<String> enviarArquivo(HttpClient client, String uuid, byte[] conteudo)
            throws IOException, InterruptedException {
        Multipart mp = new Multipart();
        mp.arquivo("ficha", uuid + ".esus", conteudo);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/api/v1/recebimento/ficha"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", mp.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(mp.fechar()))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String limitar(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 1500 ? t.substring(0, 1500) + "..." : t;
    }

    /** multipart/form-data minimo (campos texto e um arquivo binario). */
    private static final class Multipart {
        private final String boundary = "----ghbamaps" + UUID.randomUUID().toString().replace("-", "");
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void campo(String nome, String valor) {
            escrever("--" + boundary + "\r\n");
            escrever("Content-Disposition: form-data; name=\"" + nome + "\"\r\n\r\n");
            escrever(valor + "\r\n");
        }

        void arquivo(String nome, String arquivo, byte[] conteudo) {
            escrever("--" + boundary + "\r\n");
            escrever("Content-Disposition: form-data; name=\"" + nome + "\"; filename=\"" + arquivo + "\"\r\n");
            escrever("Content-Type: application/octet-stream\r\n\r\n");
            out.writeBytes(conteudo);
            escrever("\r\n");
        }

        byte[] fechar() {
            escrever("--" + boundary + "--\r\n");
            return out.toByteArray();
        }

        String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

        private void escrever(String s) {
            out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}

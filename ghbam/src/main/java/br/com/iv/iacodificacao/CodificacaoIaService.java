package br.com.iv.iacodificacao;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.iv.iaauditoria.IaAuditoriaService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Sugestao de CID principal e procedimento SIGTAP para o laudo de AIH, por um modelo
 * treinado com os laudos do proprio hospital (tools/ia-codificacao). Roda dentro do
 * backend, sem chamada externa. Desligado por padrao. Apenas sugere: o medico escolhe.
 *
 * O modelo vem do classpath; para um municipio com modelo proprio, apontar
 * ghbam.ia.codificacao.modelo para o arquivo .json.gz treinado com a base dele.
 */
@ApplicationScoped
public class CodificacaoIaService {

    static final String RECURSO = "ia/codificacao-aih.json.gz";
    private static final Logger LOG = Logger.getLogger(CodificacaoIaService.class);
    private static final int TEXTO_MAX = 8000;

    @ConfigProperty(name = "ghbam.ia.codificacao.habilitado", defaultValue = "false")
    boolean habilitado;

    @ConfigProperty(name = "ghbam.ia.codificacao.modelo")
    Optional<String> caminhoModelo;

    @Inject
    IaAuditoriaService auditoria;

    private volatile ModeloCodificacao modelo;
    private volatile boolean falhouCarga;

    public boolean habilitado() {
        return habilitado && modelo() != null;
    }

    ModeloCodificacao modelo() {
        if (modelo == null && !falhouCarga) {
            synchronized (this) {
                if (modelo == null && !falhouCarga) {
                    try (InputStream in = new GZIPInputStream(abrir())) {
                        modelo = new ModeloCodificacao(in);
                        LOG.infof("Modelo de codificacao AIH carregado (gerado em %s)", modelo.geradoEm());
                    } catch (Exception e) {
                        falhouCarga = true;
                        LOG.errorf("Modelo de codificacao AIH indisponivel: %s", e.getMessage());
                    }
                }
            }
        }
        return modelo;
    }

    private InputStream abrir() throws java.io.IOException {
        if (caminhoModelo.isPresent() && !caminhoModelo.get().isBlank()) {
            return Files.newInputStream(Path.of(caminhoModelo.get().trim()));
        }
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RECURSO);
        if (in == null) throw new java.io.FileNotFoundException(RECURSO);
        return in;
    }

    public Map<String, Object> status() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("habilitado", habilitado());
        ModeloCodificacao m = habilitado ? modelo() : null;
        if (m != null) {
            r.put("geradoEm", m.geradoEm());
            r.put("periodo", m.periodo());
            r.put("cid", m.metricas("cid"));
            r.put("procedimento", m.metricas("procedimento"));
        }
        return r;
    }

    /**
     * @param texto sinais e sintomas, justificativa, diagnostico inicial e provas
     *              diagnosticas do laudo, concatenados.
     */
    public Map<String, Object> sugerir(String texto, Integer idbam) {
        if (!habilitado()) throw new IllegalStateException("Sugestão de codificação desligada nesta instalação.");
        String t = ModeloCodificacao.semHtml(texto).strip();
        if (t.length() > TEXTO_MAX) t = t.substring(0, TEXTO_MAX);
        if (t.split("\\s+").length < 3) {
            throw new IllegalArgumentException("Descreva pelo menos sinais, sintomas ou diagnóstico inicial.");
        }
        long inicio = System.nanoTime();
        ModeloCodificacao m = modelo();
        List<ModeloCodificacao.Sugestao> cids = m.sugerir("cid", t, 5);
        List<ModeloCodificacao.Sugestao> procs = m.sugerir("procedimento", t, 5);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cid", cids);
        r.put("procedimento", procs);
        r.put("modelo", status());
        registrarAuditoria(t, cids, procs, idbam, inicio);
        return r;
    }

    private void registrarAuditoria(String texto, List<ModeloCodificacao.Sugestao> cids,
                                    List<ModeloCodificacao.Sugestao> procs, Integer idbam, long inicio) {
        try {
            String resposta = "cid=" + codigos(cids) + "; procedimento=" + codigos(procs)
                    + (idbam != null ? "; bam=" + idbam : "");
            auditoria.registrar(new IaAuditoriaService.Chamada(
                    List.of(Map.of("role", "user", "content", texto)),
                    "local", "codificacao-aih " + modelo.geradoEm(), resposta, null, inicio, null, null,
                    CodificacaoIaService.class.getSimpleName()));
        } catch (Exception ignore) {
            // auditoria nunca derruba o fluxo clinico
        }
    }

    private static String codigos(List<ModeloCodificacao.Sugestao> s) {
        return s.stream().map(ModeloCodificacao.Sugestao::codigo).collect(Collectors.joining(","));
    }
}

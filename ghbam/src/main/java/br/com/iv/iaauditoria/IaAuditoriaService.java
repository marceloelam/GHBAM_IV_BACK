package br.com.iv.iaauditoria;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.core.CurrentRequestManager;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;

import br.com.iv.security.AuthenticatedUser;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Grava uma linha em ia_chamada_auditoria por chamada a LLM e publica a metrica
 * ghbam_ia_chamadas (latencia por provedor/status). Nunca derruba o fluxo clinico:
 * qualquer falha aqui vira apenas log de erro.
 */
@ApplicationScoped
public class IaAuditoriaService {

    private static final Logger LOG = Logger.getLogger(IaAuditoriaService.class);

    @Inject IaAuditoriaContexto contexto;
    @Inject MeterRegistry meterRegistry;

    /** Dados tecnicos da chamada, montados pelo AcessoIa. */
    public record Chamada(List<Map<String, String>> mensagens, String provedor, String modelo,
                          String resposta, Throwable erro, long inicioNanos,
                          Integer tokensEntrada, Integer tokensSaida, String classeChamadora) {
    }

    public void registrar(Chamada c) {
        try {
            IaChamadaAuditoria a = montar(c);
            meterRegistry.timer("ghbam.ia.chamadas",
                    "provedor", a.provedor != null ? a.provedor : "desconhecido",
                    "status", a.status)
                .record(Duration.ofMillis(a.tempoMs));
            QuarkusTransaction.requiringNew().run(a::persist);
        } catch (Exception e) {
            LOG.errorf("Falha ao gravar auditoria de IA: %s", e.getMessage());
        }
    }

    IaChamadaAuditoria montar(Chamada c) {
        IaChamadaAuditoria a = new IaChamadaAuditoria();
        a.criadoEm = LocalDateTime.now();
        a.tempoMs = Math.max(0, (System.nanoTime() - c.inicioNanos()) / 1_000_000);
        a.provedor = limitar(c.provedor(), 20);
        a.modelo = limitar(c.modelo(), 120);
        a.tokensEntrada = c.tokensEntrada();
        a.tokensSaida = c.tokensSaida();
        a.qtdMensagens = c.mensagens() != null ? c.mensagens().size() : null;
        a.hashContexto = sha256(serializar(c.mensagens()));
        a.status = status(c.resposta(), c.erro());
        if (c.erro() != null) {
            a.erro = limitar(c.erro().getClass().getSimpleName() + ": " + c.erro().getMessage(), 240);
        } else {
            a.resposta = c.resposta();
            a.hashResposta = sha256(c.resposta());
        }

        IaAuditoriaContexto ctx = contextoAtivo();
        a.origem = limitar(ctx != null && ctx.origem != null ? ctx.origem : c.classeChamadora(), 80);
        if (a.origem == null) a.origem = "desconhecida";
        if (ctx != null) {
            a.pacienteId = ctx.pacienteId;
            a.bam = limitar(ctx.bam, 20);
            a.pergunta = ctx.pergunta;
        }

        ResteasyReactiveRequestContext req = requisicaoAtual();
        if (req != null) {
            Object u = req.getContainerRequestContext().getProperty("ghbam.user");
            if (u instanceof AuthenticatedUser usuario) {
                a.matricula = limitar(usuario.matricula(), 40);
                a.usuarioNome = limitar(usuario.nome(), 120);
                a.unidade = limitar(usuario.unidade(), 80);
            }
            a.requestId = limitar(req.getContainerRequestContext().getHeaderString("X-GHBAM-REQUEST-ID"), 80);
        }
        return a;
    }

    static String status(String resposta, Throwable erro) {
        if (erro != null) return "ERRO";
        if (resposta == null || resposta.startsWith("Erro ao processar resposta da API")
                || resposta.startsWith("Resposta nao encontrada na resposta da API")) {
            return "RESPOSTA_INVALIDA";
        }
        return "OK";
    }

    private IaAuditoriaContexto contextoAtivo() {
        try {
            return Arc.container().requestContext().isActive() ? contexto : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ResteasyReactiveRequestContext requisicaoAtual() {
        try {
            return CurrentRequestManager.get();
        } catch (Exception e) {
            return null;
        }
    }

    static String serializar(List<Map<String, String>> mensagens) {
        if (mensagens == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> m : mensagens) {
            sb.append(m.get("role")).append('\u0000').append(m.get("content")).append('\u0001');
        }
        return sb.toString();
    }

    static String sha256(String texto) {
        if (texto == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    static String limitar(String v, int max) {
        if (v == null) return null;
        String s = v.replaceAll("[\\r\\n]+", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}

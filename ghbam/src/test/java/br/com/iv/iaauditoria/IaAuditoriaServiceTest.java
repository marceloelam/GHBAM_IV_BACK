package br.com.iv.iaauditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class IaAuditoriaServiceTest {

    private final IaAuditoriaService service = new IaAuditoriaService();

    private static List<Map<String, String>> msgs(String conteudo) {
        return List.of(Map.of("role", "system", "content", "regras"), Map.of("role", "user", "content", conteudo));
    }

    @Test
    void chamadaBemSucedidaGravaRespostaEHashes() {
        IaChamadaAuditoria a = service.montar(new IaAuditoriaService.Chamada(
                msgs("contexto do prontuario"), "groq", "llama-3.3-70b-versatile",
                "Resposta clinica", null, System.nanoTime(), 120, 40, "RagService"));

        assertEquals("OK", a.status);
        assertEquals("Resposta clinica", a.resposta);
        assertEquals(64, a.hashContexto.length());
        assertEquals(64, a.hashResposta.length());
        assertEquals(2, a.qtdMensagens);
        assertEquals("groq", a.provedor);
        assertEquals(120, a.tokensEntrada);
        assertNull(a.erro);
    }

    @Test
    void semContextoDeRequisicaoUsaClasseChamadoraComoOrigemENaoIdentificaUsuario() {
        IaChamadaAuditoria a = service.montar(new IaAuditoriaService.Chamada(
                msgs("x"), "groq", "m", "ok", null, System.nanoTime(), null, null, "GeradorResumoClinicoIa"));

        assertEquals("GeradorResumoClinicoIa", a.origem);
        assertNull(a.matricula);
        assertNull(a.pacienteId);
    }

    @Test
    void contextoClinicoEnviadoAoModeloNuncaViraPerguntaGravada() {
        IaChamadaAuditoria a = service.montar(new IaAuditoriaService.Chamada(
                msgs("Paciente FULANO, CPF 000.000.000-00"), "groq", "m", "ok", null, System.nanoTime(), null, null, "X"));

        assertNull(a.pergunta, "pergunta so vem do IaAuditoriaContexto, nunca do conteudo enviado ao modelo");
        assertEquals(64, a.hashContexto.length());
    }

    @Test
    void falhaDoProvedorGravaErroSemResposta() {
        IaChamadaAuditoria a = service.montar(new IaAuditoriaService.Chamada(
                msgs("x"), "huggingface", "m", null, new RuntimeException("HTTP 503\r\ncorpo"),
                System.nanoTime(), null, null, "X"));

        assertEquals("ERRO", a.status);
        assertNull(a.resposta);
        assertNull(a.hashResposta);
        assertEquals("RuntimeException: HTTP 503 corpo", a.erro);
    }

    @Test
    void respostaQueOParserNaoEntendeuNaoContaComoOk() {
        assertEquals("RESPOSTA_INVALIDA", IaAuditoriaService.status("Resposta nao encontrada na resposta da API", null));
        assertEquals("RESPOSTA_INVALIDA", IaAuditoriaService.status("Erro ao processar resposta da API: x", null));
        assertEquals("RESPOSTA_INVALIDA", IaAuditoriaService.status(null, null));
        assertEquals("OK", IaAuditoriaService.status("texto", null));
    }

    @Test
    void hashDependeDoPapelEDaOrdemDasMensagens() {
        String a = IaAuditoriaService.sha256(IaAuditoriaService.serializar(
                List.of(Map.of("role", "user", "content", "a"), Map.of("role", "assistant", "content", "b"))));
        String b = IaAuditoriaService.sha256(IaAuditoriaService.serializar(
                List.of(Map.of("role", "assistant", "content", "b"), Map.of("role", "user", "content", "a"))));
        assertNotEquals(a, b);
        assertEquals(a, IaAuditoriaService.sha256(IaAuditoriaService.serializar(
                List.of(Map.of("role", "user", "content", "a"), Map.of("role", "assistant", "content", "b")))));
    }

    @Test
    void camposSaoTruncadosAoTamanhoDaColuna() {
        IaChamadaAuditoria a = service.montar(new IaAuditoriaService.Chamada(
                msgs("x"), "provedor-com-nome-longo-demais", "m".repeat(300), "ok", null, System.nanoTime(), null, null, "X"));
        assertEquals(120, a.modelo.length());
        assertEquals(20, a.provedor.length());
    }

    @Test
    void contextoNormalizaPacienteEBamVazios() {
        IaAuditoriaContexto ctx = new IaAuditoriaContexto();
        ctx.definir("RAG", 0, 0, "pergunta");
        assertNull(ctx.pacienteId);
        assertNull(ctx.bam);

        ctx.definir("RAG", 42, " 1234 ", "pergunta");
        assertEquals(42, ctx.pacienteId);
        assertEquals("1234", ctx.bam);
    }

    @Test
    void registrarContextoForaDeRequisicaoNaoLancaExcecao() {
        // Sem container CDI (stream/SSE, jobs): nao pode interromper a chamada clinica.
        IaAuditoriaContexto.registrar("RAG", 1, 2, "p");
    }
}

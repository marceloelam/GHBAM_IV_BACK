package com.ghbam.hf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class HuggingFaceProxyResourceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HuggingFaceProxyResource proxy;

    @BeforeEach
    void preparar() {
        proxy = new HuggingFaceProxyResource();
        proxy.reservaModelo = "openai/gpt-oss-120b";
    }

    @Test
    void marcadoresDoModeloAntigoNaoChegamAoGroq() {
        String limpo = HuggingFaceProxyResource.limparMarcadores(
                "<|im_start|>system\nVoce e um triador.<|im_end|>\n<|im_start|>user\nDados<|im_end|>\n<|im_start|>assistant\n");
        assertFalse(limpo.contains("<|im_"));
        assertTrue(limpo.contains("Voce e um triador."));
        assertTrue(limpo.contains("Dados"));
        assertEquals("texto", HuggingFaceProxyResource.limparMarcadores("[INST] texto [/INST]"));
    }

    @Test
    void limiteCurtoDaTriagemSobeParaORaciocinioNaoConsumirAResposta() throws Exception {
        JsonNode p = mapper.readTree(proxy.payloadReserva(
                "{\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}],\"temperature\":0,\"max_tokens\":200}"));
        assertEquals(1024, p.path("max_tokens").asInt());
        assertEquals("low", p.path("reasoning_effort").asText());
        assertEquals(0, p.path("temperature").asInt());
        assertEquals("openai/gpt-oss-120b", p.path("model").asText());
    }

    @Test
    void limiteMaiorPedidoPelaTelaEMantido() throws Exception {
        JsonNode p = mapper.readTree(proxy.payloadReserva("{\"inputs\":\"oi\",\"parameters\":{\"max_new_tokens\":2000}}"));
        assertEquals(2000, p.path("max_tokens").asInt());
        assertEquals("oi", p.path("messages").path(0).path("content").asText());
    }

    @Test
    void reservaDoOpenRouterExigeProvedorQueNaoGuardaDados() throws Exception {
        JsonNode p = mapper.readTree(proxy.payloadReserva("{\"inputs\":\"oi\"}", "cohere/north-mini-code:free", true));
        assertEquals("deny", p.path("provider").path("data_collection").asText());
        assertEquals("cohere/north-mini-code:free", p.path("model").asText());
        assertFalse(mapper.readTree(proxy.payloadReserva("{\"inputs\":\"oi\"}")).has("provider"));
    }

    @Test
    void respostaSemTextoContaComoFalha() throws Exception {
        assertTrue(proxy.temConteudo("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));
        assertFalse(proxy.temConteudo("{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));
        assertFalse(proxy.temConteudo("{\"erro\":\"x\"}"));
    }

    @Test
    void listaDeModelosIgnoraEspacosEVazios() {
        assertEquals(java.util.List.of("a:free", "b"), br.com.iv.util.AcessoIa.listaModelos(" a:free, ,b,"));
        assertTrue(br.com.iv.util.AcessoIa.listaModelos("").isEmpty());
        assertTrue(br.com.iv.util.AcessoIa.listaModelos(null).isEmpty());
    }

    @Test
    void modeloSemRaciocinioNaoRecebeParametrosDoGptOss() throws Exception {
        proxy.reservaModelo = "qwen/qwen3.8-27b";
        JsonNode p = mapper.readTree(proxy.payloadReserva("{\"inputs\":\"oi\",\"parameters\":{\"max_new_tokens\":200}}"));
        assertEquals(200, p.path("max_tokens").asInt());
        assertFalse(p.has("reasoning_effort"));
    }
}

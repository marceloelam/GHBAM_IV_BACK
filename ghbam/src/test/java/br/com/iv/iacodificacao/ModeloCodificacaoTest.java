package br.com.iv.iacodificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Garante que a inferencia em Java reproduz o scikit-learn: o script de treino grava
 * frases inventadas com o top-3 esperado e as probabilidades.
 */
class ModeloCodificacaoTest {

    private static ModeloCodificacao modelo;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = new GZIPInputStream(
                ModeloCodificacaoTest.class.getResourceAsStream("/" + CodificacaoIaService.RECURSO))) {
            modelo = new ModeloCodificacao(in);
        }
    }

    @Test
    void reproduzOScikitLearnNasAmostras() {
        for (String nome : List.of("cid", "procedimento")) {
            JsonNode amostras = modelo.amostras(nome);
            assertNotNull(amostras, nome);
            assertTrue(amostras.size() > 0, nome);
            for (JsonNode a : amostras) {
                List<ModeloCodificacao.Sugestao> s = modelo.sugerir(nome, a.path("texto").asText(), 3);
                JsonNode esperado = a.path("esperado");
                for (int i = 0; i < esperado.size(); i++) {
                    assertEquals(esperado.get(i).get(0).asText(), s.get(i).codigo(), nome + ": " + a.path("texto"));
                    assertEquals(esperado.get(i).get(1).asDouble(), s.get(i).probabilidade(), 1e-3,
                            nome + ": " + a.path("texto"));
                }
            }
        }
    }

    @Test
    void textoSemTermoConhecidoNaoSugereNada() {
        assertTrue(modelo.sugerir("cid", "xyzw qwert", 5).isEmpty());
        assertTrue(modelo.sugerir("cid", "", 5).isEmpty());
        assertTrue(modelo.sugerir("inexistente", "pneumonia", 5).isEmpty());
    }

    @Test
    void textoDoEditorRicoDaAltaDaOMesmoResultadoQueTextoPuro() {
        String html = "<p>Pneumonia&nbsp;comunitária</p><p>febre e <strong>tosse</strong></p>";
        assertEquals(modelo.sugerir("cid", "Pneumonia comunitária febre e tosse", 3),
                modelo.sugerir("cid", ModeloCodificacao.semHtml(html), 3));
    }

    @Test
    void metricasVemPorOrigem() {
        var m = modelo.metricas("cid");
        assertTrue(m.containsKey("laudo"), m.toString());
        assertTrue(m.containsKey("alta"), m.toString());
    }

    @Test
    void normalizaAcentoNumeroEPontuacao() {
        assertEquals(List.of("pneumonia", "comunitaria", "pneumonia comunitaria"),
                ModeloCodificacao.ngramas(ModeloCodificacao.normalizar("Pneumonia, COMUNITÁRIA 39")));
    }
}

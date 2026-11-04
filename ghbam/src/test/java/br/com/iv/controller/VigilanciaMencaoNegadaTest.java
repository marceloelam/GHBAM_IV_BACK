package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class VigilanciaMencaoNegadaTest {

    private static final String TERMOS = "mpox, monkeypox, febre";

    /** Mesmo teste da SQL: o termo continua no texto depois de retirar as mencoes negadas? */
    private static boolean conta(String texto) {
        String limpo = (" " + texto + " ").replaceAll(VigilanciaEpidemiologicaController.regexMencaoNegada(TERMOS), " ");
        return VigilanciaEpidemiologicaController.termosNormalizados(TERMOS).stream()
                .anyMatch(termo -> limpo.contains(" " + termo + " "));
    }

    @Test
    void mencaoNegadaNaoConta() {
        for (String texto : List.of("paciente nega febre", "sem febre ha dois dias", "ausencia de febre",
                "sem sinais de mpox", "mpox descartado", "mpox negativo", "descartado mpox", "afastada suspeita de mpox")) {
            assertFalse(conta(texto), texto);
        }
    }

    @Test
    void suspeitaRealContinuaContando() {
        for (String texto : List.of("febre ha tres dias", "suspeita de mpox", "mpox nao descartado", "nao descartado mpox",
                "nao se descarta mpox", "nao foi afastado mpox", "para descartar mpox", "nao nega febre",
                "sem melhora da febre", "nega tosse e febre")) {
            assertTrue(conta(texto), texto);
        }
    }

    @Test
    void mencaoAfirmadaJuntoComNegadaContinuaContando() {
        assertTrue(conta("nega febre na admissao evoluiu com febre"));
    }

    @Test
    void normalizaTermosComoOTextoDoAtendimento() {
        assertEquals(List.of("variola dos macacos", "m pox"),
                VigilanciaEpidemiologicaController.termosNormalizados("Varíola dos Macacos, m-pox, ,M-POX"));
    }

    @Test
    void semTermoNaoRemoveNada() {
        assertEquals("^$", VigilanciaEpidemiologicaController.regexMencaoNegada(" , "));
    }

    @Test
    void osMesmosCamposValemEmTodasAsContagens() {
        for (String campo : List.of("r.queixaprincipal", "r.obsentrada", "a.anamnese", "a.comentario", "a.hpp", "a.risco",
                "t.diagnostico_provisorio", "t.evolucao_prescricao", "t.obs_enfermagem", "ai.frente", "ai.verso")) {
            assertTrue(VigilanciaEpidemiologicaController.TEXTO_BAM_SQL.contains(campo), campo);
        }
    }
}

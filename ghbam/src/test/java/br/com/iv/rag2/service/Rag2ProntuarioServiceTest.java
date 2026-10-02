package br.com.iv.rag2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.iv.rag2.dto.Rag2FonteDto;

/**
 * Trava as garantias de governanca de IA do RAG de prontuario: se alguem
 * editar o prompt de sistema e remover uma dessas clausulas sem perceber,
 * este teste quebra antes de ir para producao.
 */
class Rag2ProntuarioServiceTest {

    @Test
    void promptDeSistemaProibeInventarInformacao() {
        String prompt = Rag2ProntuarioService.obterSystemPrompt();

        assertTrue(prompt.toLowerCase().contains("nao invente"),
                "Prompt precisa proibir explicitamente invencao de informacao (guarda anti-alucinacao).");
    }

    @Test
    void promptDeSistemaExigeRespostaLiteralQuandoSemEvidencia() {
        String prompt = Rag2ProntuarioService.obterSystemPrompt();

        assertTrue(prompt.contains("Nao encontrei essa informacao no prontuario do paciente"),
                "Sem evidencia no contexto, o modelo precisa recusar com a frase literal, nunca inferir.");
    }

    @Test
    void promptDeSistemaProibeDiagnosticoNovoENaoSubstituiProfissional() {
        String prompt = Rag2ProntuarioService.obterSystemPrompt();
        String normalizado = prompt.toLowerCase();

        assertTrue(normalizado.contains("nao forneca diagnostico"),
                "IA nao pode gerar diagnostico novo - so relatar o que ja esta registrado.");
        assertTrue(normalizado.contains("nao substitua a avaliacao do profissional"),
                "Separacao explicita entre sugestao da IA e decisao clinica humana.");
    }

    @Test
    void extraiTrechoRealDaSecaoNaoSoONomeDela() {
        String contexto = "TRIAGEM:\n"
            + "  Classificacao: amarelo\n"
            + "  Prioridade: 2\n"
            + "SINAIS VITAIS:\n"
            + "  PA: 130x80\n";

        List<Rag2FonteDto> fontes = Rag2ProntuarioService.extrairFontes(contexto);

        assertEquals(2, fontes.size());
        assertEquals("TRIAGEM", fontes.get(0).getTipo());
        assertTrue(fontes.get(0).getTrecho().contains("Classificacao: amarelo"));
        assertTrue(fontes.get(0).getTrecho().contains("Prioridade: 2"));
        assertEquals("SINAIS VITAIS", fontes.get(1).getTipo());
        assertTrue(fontes.get(1).getTrecho().contains("PA: 130x80"));
    }

    @Test
    void naoDuplicaSecaoRepetidaEPreservaOPrimeiroTrecho() {
        String contexto = "EVOLUCOES MEDICAS:\n"
            + "  Primeira evolucao registrada\n"
            + "EVOLUCOES MEDICAS:\n"
            + "  Segunda ocorrencia da mesma secao\n";

        List<Rag2FonteDto> fontes = Rag2ProntuarioService.extrairFontes(contexto);

        assertEquals(1, fontes.size());
        assertTrue(fontes.get(0).getTrecho().contains("Primeira evolucao registrada"));
    }

    @Test
    void contextoVazioNaoGeraFonteNemQuebra() {
        assertTrue(Rag2ProntuarioService.extrairFontes(null).isEmpty());
        assertTrue(Rag2ProntuarioService.extrairFontes("   ").isEmpty());
    }
}

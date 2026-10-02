package br.com.iv.eval;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.util.AcessoIa;

/**
 * Usa o proprio AcessoIa como "juiz": recebe pergunta + criterio esperado + resposta
 * gerada, e devolve uma nota 0-1 com justificativa. Nao falha o processo se o juiz
 * responder em formato inesperado - grava o erro no ResultadoCaso.
 */
public class JuizAvaliador {

    private static final String SYSTEM_PROMPT_JUIZ = """
        Voce e um avaliador tecnico de respostas de um assistente medico hospitalar.
        Voce recebe: a pergunta feita, o criterio esperado de uma boa resposta, e a
        resposta que foi gerada. De uma nota de 0.0 a 1.0 para o quanto a resposta
        atende ao criterio esperado (1.0 = atende completamente, 0.0 = nao atende
        ou contradiz o criterio).

        Responda SOMENTE com um JSON no formato exato, sem texto antes ou depois:
        {"nota": 0.0, "justificativa": "..."}
        """;

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private final AcessoIa acessoIa;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JuizAvaliador(AcessoIa acessoIa) {
        this.acessoIa = acessoIa;
    }

    public static class Nota {
        public final Double nota;
        public final String justificativa;
        public final String erro;

        private Nota(Double nota, String justificativa, String erro) {
            this.nota = nota;
            this.justificativa = justificativa;
            this.erro = erro;
        }

        static Nota ok(double nota, String justificativa) {
            return new Nota(nota, justificativa, null);
        }

        static Nota falha(String erro) {
            return new Nota(null, null, erro);
        }
    }

    public Nota avaliar(String pergunta, String criterioEsperado, String respostaGerada) {
        String prompt = "PERGUNTA:\n" + pergunta
                + "\n\nCRITERIO ESPERADO:\n" + criterioEsperado
                + "\n\nRESPOSTA GERADA:\n" + respostaGerada;

        List<Map<String, String>> mensagens = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT_JUIZ),
                Map.of("role", "user", "content", prompt));

        try {
            AcessoIa.RespostaIa resposta = acessoIa.enviarMensagensComMetadados(mensagens, null, 0.0, 500);
            return parseNota(resposta.conteudo());
        } catch (Exception e) {
            return Nota.falha("Erro ao chamar juiz: " + e.getMessage());
        }
    }

    private Nota parseNota(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) {
            return Nota.falha("Juiz devolveu resposta vazia");
        }
        Matcher matcher = JSON_PATTERN.matcher(conteudo);
        if (!matcher.find()) {
            return Nota.falha("Nao foi possivel localizar JSON na resposta do juiz: " + conteudo);
        }
        try {
            JsonNode node = objectMapper.readTree(matcher.group());
            if (!node.has("nota")) {
                return Nota.falha("JSON do juiz sem campo 'nota': " + conteudo);
            }
            double nota = node.get("nota").asDouble();
            String justificativa = node.has("justificativa") ? node.get("justificativa").asText() : "";
            return Nota.ok(nota, justificativa);
        } catch (Exception e) {
            return Nota.falha("Erro ao parsear JSON do juiz: " + e.getMessage());
        }
    }
}

package br.com.iv.eval;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import br.com.iv.rag2.service.Rag2ProntuarioService;
import br.com.iv.service.RagService;
import br.com.iv.util.AcessoIa;

/**
 * Roda o conjunto de casos de teste (src/test/resources/eval/casos-teste.json) contra
 * os prompts reais de producao, nota cada resposta com JuizAvaliador, e compara com
 * o baseline salvo em eval-results/baseline.json para detectar regressoes.
 *
 * Uso: mvn compile exec:java -Dexec.mainClass=br.com.iv.eval.PromptEvaluationRunner -Dexec.classpathScope=test
 * Para promover o resultado atual a novo baseline: passe "--promote" como argumento.
 */
public class PromptEvaluationRunner {

    private static final double LIMIAR_ACERTO = 0.7;
    private static final double TOLERANCIA_REGRESSAO = 0.15;
    private static final File PASTA_RESULTADOS = new File("eval-results");
    private static final File ARQUIVO_BASELINE = new File(PASTA_RESULTADOS, "baseline.json");

    public static void main(String[] args) throws Exception {
        boolean promover = args.length > 0 && "--promote".equals(args[0]);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        List<CasoTeste> casos = carregarCasos(objectMapper);
        if (casos.isEmpty()) {
            System.out.println("Nenhum caso de teste encontrado em eval/casos-teste.json");
            return;
        }

        AcessoIa acessoIa = new AcessoIa();
        JuizAvaliador juiz = new JuizAvaliador(acessoIa);

        List<ResultadoCaso> resultados = new ArrayList<>();
        for (CasoTeste caso : casos) {
            resultados.add(rodarCaso(acessoIa, juiz, caso));
        }

        PASTA_RESULTADOS.mkdirs();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        File arquivoAtual = new File(PASTA_RESULTADOS, timestamp + ".json");
        objectMapper.writeValue(arquivoAtual, resultados);
        System.out.println("Resultados salvos em " + arquivoAtual.getPath());

        imprimirResumo(resultados);

        if (ARQUIVO_BASELINE.exists()) {
            List<ResultadoCaso> baseline = List.of(objectMapper.readValue(ARQUIVO_BASELINE, ResultadoCaso[].class));
            imprimirComparacaoComBaseline(resultados, baseline);
        } else {
            System.out.println("\nNenhum baseline encontrado (eval-results/baseline.json). Rode com --promote para criar um.");
        }

        if (promover) {
            Files.copy(arquivoAtual.toPath(), ARQUIVO_BASELINE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("\nResultado atual promovido a novo baseline.");
        }
    }

    private static List<CasoTeste> carregarCasos(ObjectMapper objectMapper) throws Exception {
        try (InputStream is = PromptEvaluationRunner.class.getResourceAsStream("/eval/casos-teste.json")) {
            if (is == null) {
                return List.of();
            }
            CasoTeste[] casos = objectMapper.readValue(is, CasoTeste[].class);
            List<CasoTeste> lista = new ArrayList<>();
            for (CasoTeste c : casos) {
                lista.add(c);
            }
            return lista;
        }
    }

    private static ResultadoCaso rodarCaso(AcessoIa acessoIa, JuizAvaliador juiz, CasoTeste caso) {
        ResultadoCaso resultado = new ResultadoCaso();
        resultado.setId(caso.getId());
        resultado.setCategoria(caso.getCategoria());

        String systemPrompt = resolverSystemPrompt(caso.getSystemPromptRef());
        String userContent = (caso.getContexto() != null ? "CONTEXTO:\n" + caso.getContexto() + "\n\n" : "") + caso.getPergunta();

        List<Map<String, String>> mensagens = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent));

        long inicio = System.nanoTime();
        try {
            AcessoIa.RespostaIa resposta = acessoIa.enviarMensagensComMetadados(mensagens, null, 0.2, 1500);
            long tempoMs = (System.nanoTime() - inicio) / 1_000_000;
            resultado.setTempoMs(tempoMs);
            resultado.setTokensPrompt(resposta.tokensPrompt());
            resultado.setTokensCompletion(resposta.tokensCompletion());
            resultado.setTokensTotal(resposta.tokensTotal());
            resultado.setRespostaBruta(resposta.conteudo());

            JuizAvaliador.Nota nota = juiz.avaliar(caso.getPergunta(), caso.getCriterioEsperado(), resposta.conteudo());
            if (nota.erro != null) {
                resultado.setErro(nota.erro);
            } else {
                resultado.setNota(nota.nota);
                resultado.setJustificativaJuiz(nota.justificativa);
            }
        } catch (Exception e) {
            resultado.setTempoMs((System.nanoTime() - inicio) / 1_000_000);
            resultado.setErro("Erro ao chamar IA: " + e.getMessage());
        }
        return resultado;
    }

    private static String resolverSystemPrompt(String ref) {
        if ("RAG_PRONTUARIO".equals(ref)) {
            return Rag2ProntuarioService.obterSystemPrompt();
        }
        return RagService.obterSystemPrompt();
    }

    private static void imprimirResumo(List<ResultadoCaso> resultados) {
        long total = resultados.size();
        long comErro = resultados.stream().filter(r -> r.getErro() != null).count();
        long corretos = resultados.stream().filter(r -> r.getNota() != null && r.getNota() >= LIMIAR_ACERTO).count();
        double tempoMedio = resultados.stream().mapToLong(ResultadoCaso::getTempoMs).average().orElse(0);
        double tokensMedio = resultados.stream()
                .filter(r -> r.getTokensTotal() != null)
                .mapToInt(ResultadoCaso::getTokensTotal)
                .average().orElse(0);

        System.out.println("\n=== Resumo ===");
        System.out.println("Total de casos: " + total);
        System.out.println("Corretos (nota >= " + LIMIAR_ACERTO + "): " + corretos + " (" + pct(corretos, total) + "%)");
        System.out.println("Com erro (juiz ou chamada de IA falhou): " + comErro);
        System.out.println("Tempo medio: " + String.format("%.0f", tempoMedio) + " ms");
        System.out.println("Tokens medios (quando disponivel): " + String.format("%.0f", tokensMedio));
    }

    private static void imprimirComparacaoComBaseline(List<ResultadoCaso> atuais, List<ResultadoCaso> baseline) {
        Map<String, ResultadoCaso> baselinePorId = new HashMap<>();
        for (ResultadoCaso r : baseline) {
            baselinePorId.put(r.getId(), r);
        }

        System.out.println("\n=== Comparacao com baseline ===");
        boolean algumaRegressao = false;
        for (ResultadoCaso atual : atuais) {
            ResultadoCaso anterior = baselinePorId.get(atual.getId());
            if (anterior == null || anterior.getNota() == null || atual.getNota() == null) {
                continue;
            }
            double diferenca = atual.getNota() - anterior.getNota();
            if (diferenca < -TOLERANCIA_REGRESSAO) {
                algumaRegressao = true;
                System.out.println("REGRESSAO em " + atual.getId() + ": nota caiu de " + anterior.getNota() + " para " + atual.getNota());
            }
        }
        if (!algumaRegressao) {
            System.out.println("Nenhuma regressao detectada (tolerancia: " + TOLERANCIA_REGRESSAO + ")");
        }
    }

    private static long pct(long parte, long total) {
        return total == 0 ? 0 : Math.round(parte * 100.0 / total);
    }
}

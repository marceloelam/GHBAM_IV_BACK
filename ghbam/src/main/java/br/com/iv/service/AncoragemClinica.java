package br.com.iv.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Filtro entre a interpretacao clinica gerada por IA e o prontuario. O prompt pede
// para nao inventar, mas pedido em prompt e probabilistico: mais cedo ou mais tarde
// escapa um "melhora com gelo" que ninguem disse. Aqui a regra e deterministica -
// cada oracao so sobrevive se todo termo clinico dela tiver apoio lexico na fala
// transcrita. O filtro e conservador de proposito: prefere apagar uma frase verdadeira
// porem parafraseada a deixar entrar um dado que nao foi dito.
public final class AncoragemClinica {

    private static final String SEM_APOIO = "Nao informado.";

    // Palavras de redacao, saudacao e estrutura de campo. Nao carregam achado clinico,
    // entao nao precisam de apoio na transcricao.
    private static final Set<String> VOCABULARIO_ESTRUTURAL = Set.of(
            "pacie", "relat", "refer", "apres", "infor", "nega", "queix", "princ",
            "histo", "doenc", "atual", "sinto", "assoc", "acomp", "antec", "condu",
            "orien", "discu", "dia", "hora", "seman", "mes", "ano", "desde", "aprox",
            "cerca", "tambe", "ainda", "alem", "disso", "duran", "perio", "quadr",
            "evolu", "inici", "nao", "sim", "mais", "menos", "pouco", "muito", "sendo",
            "esta", "estao", "tem", "tenho", "teve", "foi", "era", "seu", "sua",
            "entre", "sobre", "bom", "boa", "noite", "tarde", "douto", "senho",
            "obrig", "ele", "ela", "voce", "como", "quand", "onde", "qual", "entao",
            "apena", "confo", "estes", "esses", "essa", "esse", "isso");

    // A transcricao costuma trazer numero por extenso e a interpretacao em digito.
    // Sem esta ponte, "ha 2 dias" nao ancoraria em "ha dois dias".
    private static final Map<String, String> NUMEROS_POR_EXTENSO = Map.ofEntries(
            Map.entry("dois", "2"), Map.entry("duas", "2"), Map.entry("tres", "3"),
            Map.entry("quatro", "4"), Map.entry("cinco", "5"), Map.entry("seis", "6"),
            Map.entry("sete", "7"), Map.entry("oito", "8"), Map.entry("nove", "9"),
            Map.entry("dez", "10"), Map.entry("doze", "12"), Map.entry("quinze", "15"),
            Map.entry("vinte", "20"), Map.entry("trinta", "30"));

    // Termo tecnico que o medico escreve e o paciente falou em linguagem comum.
    // Chave e valor sao radicais de cinco letras, iguais aos produzidos por radical().
    private static final Map<String, List<String>> SINONIMOS = Map.ofEntries(
            Map.entry("cefal", List.of("cabec")),
            Map.entry("cabec", List.of("cefal")),
            Map.entry("emese", List.of("vomit")),
            Map.entry("pirex", List.of("febre")),
            Map.entry("verti", List.of("tontu")),
            Map.entry("tontu", List.of("verti")),
            Map.entry("nause", List.of("enjoo")),
            Map.entry("enjoo", List.of("nause")),
            Map.entry("inape", List.of("apeti", "comid", "fome", "alime")),
            Map.entry("hipor", List.of("apeti", "comid", "fome")),
            Map.entry("inges", List.of("comid", "alime", "apeti")),
            Map.entry("alime", List.of("comid", "apeti")),
            Map.entry("dispn", List.of("respi", "falta")),
            Map.entry("odino", List.of("garga")),
            Map.entry("mialg", List.of("corpo", "muscu")),
            Map.entry("asten", List.of("fraqu", "cansa")),
            Map.entry("evacu", List.of("diarr", "fezes", "intes")));

    private AncoragemClinica() {
    }

    /**
     * Devolve a interpretacao sem os trechos que nao aparecem na transcricao.
     * Campo que perde todo o conteudo vira "Nao informado."; se nenhum campo
     * sobreviver, devolve vazio - nesse caso o registro fica so com a fala literal.
     */
    public static String filtrar(String interpretacao, String transcricao) {
        if (interpretacao == null || interpretacao.isBlank()) {
            return "";
        }

        Set<String> ancoras = radicaisDe(transcricao);
        List<String> linhas = new ArrayList<>();
        boolean algumCampoSobreviveu = false;

        for (String linha : interpretacao.split("\\R")) {
            String bruta = linha.strip();
            if (bruta.isEmpty()) {
                continue;
            }

            int separador = bruta.indexOf(':');
            if (separador < 0) {
                String mantido = manterOracoesAncoradas(bruta, ancoras);
                if (!mantido.isBlank()) {
                    linhas.add(mantido);
                    algumCampoSobreviveu = true;
                }
                continue;
            }

            String rotulo = bruta.substring(0, separador + 1);
            String mantido = manterOracoesAncoradas(bruta.substring(separador + 1), ancoras);
            if (mantido.isBlank()) {
                linhas.add(rotulo + " " + SEM_APOIO);
            } else {
                linhas.add(rotulo + " " + mantido);
                algumCampoSobreviveu = true;
            }
        }

        return algumCampoSobreviveu ? String.join("\n", linhas) : "";
    }

    private static String manterOracoesAncoradas(String valor, Set<String> ancoras) {
        String texto = valor == null ? "" : valor.strip();
        if (texto.isEmpty() || normalizar(texto).startsWith("nao informado")) {
            return "";
        }

        List<String> mantidas = new ArrayList<>();
        for (String oracao : texto.split("[,;.]")) {
            String trecho = oracao.strip();
            if (!trecho.isEmpty() && oracaoAncorada(trecho, ancoras)) {
                mantidas.add(trecho);
            }
        }

        return mantidas.isEmpty() ? "" : String.join(", ", mantidas) + ".";
    }

    private static boolean oracaoAncorada(String oracao, Set<String> ancoras) {
        for (String token : normalizar(oracao).split("[^a-z0-9]+")) {
            if (token.length() <= 2) {
                continue;
            }
            String radical = radical(token);
            if (radical.isEmpty() || VOCABULARIO_ESTRUTURAL.contains(radical) || ancoras.contains(radical)) {
                continue;
            }
            if (!sinonimoAncorado(radical, ancoras)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sinonimoAncorado(String radical, Set<String> ancoras) {
        for (String equivalente : SINONIMOS.getOrDefault(radical, List.of())) {
            if (ancoras.contains(equivalente)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> radicaisDe(String texto) {
        Set<String> radicais = new HashSet<>();
        for (String token : normalizar(texto == null ? "" : texto).split("[^a-z0-9]+")) {
            String radical = radical(token);
            if (!radical.isEmpty()) {
                radicais.add(radical);
            }
        }
        return radicais;
    }

    // Radical tosco de cinco letras, com plural removido antes do corte, para que
    // "vomitos" e "vomito" cheguem ao mesmo "vomit".
    private static String radical(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }

        String palavra = NUMEROS_POR_EXTENSO.getOrDefault(token, token);
        if (palavra.length() > 4 && palavra.endsWith("es")) {
            palavra = palavra.substring(0, palavra.length() - 2);
        } else if (palavra.length() > 3 && palavra.endsWith("s")) {
            palavra = palavra.substring(0, palavra.length() - 1);
        }
        return palavra.length() > 5 ? palavra.substring(0, 5) : palavra;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
    }
}

package br.com.iv.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Criterio para um atendimento contar como caso SUSPEITO no mapa e na vigilancia.
 * Texto clinico nunca confirma doenca: confirmacao depende de laboratorio ou investigacao.
 *
 * Doenca especifica exige hipotese registrada (o nome da doenca no atendimento) E o conjunto
 * de sintomas da definicao de caso; sintomas sozinhos nao bastam, pois febre + mialgia +
 * cefaleia descrevem quase qualquer virose. Sindrome conta pelos sintomas, sem hipotese.
 * Base: Guia de Vigilancia em Saude/MS, 6a ed. e Nota Tecnica 4/2025 CIMVAC/SAPS/MS.
 */
public final class DefinicaoCasoEpidemiologico {

    private DefinicaoCasoEpidemiologico() {}

    private static final List<String> FEBRE = List.of("febre", "febril", "pirexia", "hipertermia");
    private static final List<String> CEFALEIA = List.of("cefaleia", "dor de cabeca");
    private static final List<String> VOMITO = List.of("nausea", "nauseas", "vomito", "vomitos", "vomitando");
    private static final List<String> EXANTEMA = List.of("exantema", "rash", "exantematico", "maculopapular", "manchas vermelhas");
    private static final List<String> CONJUNTIVITE = List.of("conjuntivite", "hiperemia conjuntival");
    private static final List<String> ARTRALGIA = List.of("artralgia", "poliartralgia", "artrite", "dor articular", "dor nas articulacoes");
    private static final List<String> TOSSE = List.of("tosse");
    private static final List<String> CORIZA = List.of("coriza", "corrimento nasal");

    /**
     * @param chaves termos que ligam uma regra a esta definicao
     * @param hipotese termos que registram a hipotese; vazio = sindrome, conta pelos sintomas
     * @param obrigatorios grupos que precisam estar todos presentes (basta um termo de cada)
     * @param adicionais grupos dos quais se exigem ao menos minimoAdicionais
     */
    private record Definicao(List<String> chaves, List<String> hipotese, List<List<String>> obrigatorios,
                             List<List<String>> adicionais, int minimoAdicionais, String descricao) {}

    private static final List<String> TOSSE_OU_GARGANTA = List.of("tosse", "dor de garganta", "odinofagia");

    private static final List<List<String>> GRAVIDADE_SRAG = List.of(
            List.of("dispneia", "falta de ar", "desconforto respiratorio", "pressao no torax", "aperto no torax",
                    "saturacao baixa", "baixa oxigenacao no sangue", "hipoxemia", "cianose"));

    private static final List<Definicao> DEFINICOES = List.of(
        new Definicao(List.of("dengue"), List.of("dengue"), List.of(FEBRE),
            List.of(VOMITO, EXANTEMA, List.of("mialgia", "dor no corpo", "dores no corpo", "artralgia", "dor articular"),
                    List.of("cefaleia", "dor de cabeca", "dor retro orbitaria", "dor retroorbitaria"),
                    List.of("petequias", "prova do laco positiva"), List.of("leucopenia")), 2,
            "hipótese de dengue + febre + ao menos 2 entre náusea/vômito, exantema, mialgia/artralgia, cefaleia/dor retro-orbitária, petéquias/prova do laço, leucopenia"),
        // Sindrome conta pelos sintomas. Regra antiga "Sindrome Febril / Suspeita de Dengue" casa com as duas definicoes e basta uma.
        new Definicao(List.of("sindrome febril"), List.of(), List.of(FEBRE),
            List.of(VOMITO, EXANTEMA, List.of("mialgia", "dor no corpo", "dores no corpo", "artralgia", "dor articular"),
                    List.of("cefaleia", "dor de cabeca", "dor retro orbitaria", "dor retroorbitaria"),
                    List.of("petequias", "prova do laco positiva"), List.of("leucopenia")), 2,
            "febre + ao menos 2 entre náusea/vômito, exantema, mialgia/artralgia, cefaleia/dor retro-orbitária, petéquias/prova do laço, leucopenia"),
        new Definicao(List.of("chikungunya", "chicungunha"), List.of("chikungunya", "chicungunha"), List.of(FEBRE, ARTRALGIA), List.of(), 0,
            "hipótese de chikungunya + febre + artralgia intensa ou artrite"),
        new Definicao(List.of("zika"), List.of("zika"), List.of(EXANTEMA),
            List.of(FEBRE, CONJUNTIVITE, ARTRALGIA, List.of("edema periarticular")), 1,
            "hipótese de zika + exantema + ao menos 1 entre febre, conjuntivite, artralgia, edema periarticular"),
        new Definicao(List.of("srag", "sindrome respiratoria aguda grave"), List.of(), List.of(FEBRE, TOSSE_OU_GARGANTA), GRAVIDADE_SRAG, 1,
            "febre + tosse ou dor de garganta + sinal de gravidade (dispneia, desconforto respiratório, saturação baixa, cianose)"),
        new Definicao(List.of("sindrome gripal", "gripe", "influenza"), List.of(), List.of(FEBRE, TOSSE_OU_GARGANTA), List.of(), 0,
            "febre + tosse ou dor de garganta"),
        new Definicao(List.of("diarreia", "diarreica", "gastroenterite", "gastroenterocolite"),
            List.of("diarreia", "diarreica", "gastroenterite", "gastroenterocolite", "evacuacoes liquidas"), List.of(), List.of(), 0,
            "diarreia ou gastroenterite registrada"),
        new Definicao(List.of("sarampo"), List.of("sarampo"), List.of(FEBRE, EXANTEMA), List.of(TOSSE, CORIZA, CONJUNTIVITE), 1,
            "hipótese de sarampo + febre + exantema + tosse, coriza ou conjuntivite"),
        new Definicao(List.of("rubeola"), List.of("rubeola"), List.of(FEBRE, EXANTEMA),
            List.of(List.of("linfadenopatia", "linfonodomegalia", "adenomegalia", "ingua")), 1,
            "hipótese de rubéola + febre + exantema + linfadenopatia"),
        new Definicao(List.of("leptospirose"), List.of("leptospirose"), List.of(FEBRE),
            List.of(List.of("mialgia", "dor nas panturrilhas", "dor no corpo", "dores no corpo"), CEFALEIA, List.of("ictericia")), 2,
            "hipótese de leptospirose + febre + ao menos 2 entre mialgia, cefaleia, icterícia"),
        new Definicao(List.of("meningite", "meningococ"), List.of("meningite", "meningococo", "meningococcemia", "meningococica"), List.of(FEBRE),
            List.of(CEFALEIA, VOMITO, List.of("rigidez de nuca", "sinais meningeos", "abaulamento de fontanela")), 1,
            "hipótese de meningite + febre + cefaleia, vômito ou rigidez de nuca"),
        new Definicao(List.of("tuberculose"), List.of("tuberculose"), List.of(),
            List.of(List.of("tosse", "tosse persistente"), List.of("hemoptise"), List.of("emagrecimento", "perda de peso"),
                    List.of("sudorese noturna"), List.of("febre vespertina"), List.of("baciloscopia positiva", "teste molecular detectavel")), 1,
            "hipótese de tuberculose + tosse, hemoptise, emagrecimento, sudorese noturna, febre vespertina ou exame positivo"),
        new Definicao(List.of("hantavirose", "hantavirus", "hanta virus"), List.of("hantavirose", "hantavirus", "hanta virus"), List.of(FEBRE),
            List.of(List.of("mialgia", "mialgias", "dor no corpo", "dores no corpo", "dores musculares")), 1,
            "hipótese de hantavirose + febre + mialgia"),
        new Definicao(List.of("mpox", "monkeypox"), List.of("mpox", "monkeypox"),
            List.of(List.of("lesao", "lesoes", "erupcao", "erupcoes", "pustula", "pustulas", "vesicula", "vesiculas", "exantema")), List.of(), 0,
            "hipótese de mpox + lesão de pele ou erupção")
    );

    /** Sintomas que, em regra sem definicao, nao contam sozinhos. Diarreia fica de fora: define a DDA. */
    private static final Set<String> SINTOMAS_INESPECIFICOS = Set.of(
            "febre", "febril", "tosse", "tosse persistente", "coriza", "corrimento nasal", "espirros", "congestao nasal",
            "cefaleia", "cefaleia intensa", "dor de cabeca", "dor", "dor no corpo", "dores no corpo", "dores musculares", "mialgia",
            "mialgia intensa", "artralgia", "dor articular", "dor de garganta", "odinofagia", "mal estar", "fraqueza", "astenia",
            "adinamia", "cansaco", "fadiga", "calafrios", "nausea", "nauseas", "vomito", "vomitos", "inapetencia", "tontura",
            "dispneia", "falta de ar", "desconforto respiratorio", "dor abdominal", "dor retro orbitaria", "exantema", "rash",
            "manchas", "manchas vermelhas", "lesao", "lesoes", "prurido", "coceira", "ictericia", "fotofobia", "hemoptise",
            "plaquetopenia", "hipoxemia", "saturacao baixa", "dor nas panturrilhas");

    public static List<String> normalizar(String termos) {
        String semAcento = Normalizer.normalize(termos == null ? "" : termos, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        List<String> out = new ArrayList<>();
        for (String parte : semAcento.toLowerCase(Locale.ROOT).split(",")) {
            String normalizado = parte.replaceAll("[^a-z0-9]+", " ").trim();
            if (!normalizado.isBlank() && !out.contains(normalizado)) out.add(normalizado);
        }
        return out;
    }

    private static String textoBusca(String valor) {
        return " " + String.join(" ", normalizar(valor == null ? "" : valor.replace(",", " "))) + " ";
    }

    private static boolean citaChave(Definicao d, String texto) {
        return d.chaves().stream().anyMatch(c -> texto.contains(" " + c + " ") || ("meningococ".equals(c) && texto.contains(" " + c)));
    }

    /** O nome da regra manda; os termos so decidem se o nome nao indicar doenca (ex.: "diarreia" dentro de Sindrome gripal). */
    private static List<Definicao> definicoesDa(String nomeRegra, String termosRegra) {
        String nome = textoBusca(nomeRegra);
        List<Definicao> porNome = DEFINICOES.stream().filter(d -> citaChave(d, nome)).toList();
        if (!porNome.isEmpty()) return porNome;
        String termos = " " + String.join(" ", normalizar(termosRegra)) + " ";
        return DEFINICOES.stream().filter(d -> citaChave(d, termos)).toList();
    }

    /** Texto ja normalizado e sem mencoes negadas, com espaco nas pontas. */
    public static boolean atende(String texto, String nomeRegra, String termosRegra) {
        return compilar(nomeRegra, termosRegra).test(texto);
    }

    /** Criterio da regra pronto para testar muitos atendimentos, sem resolver a definicao a cada texto. */
    public static Predicate<String> compilar(String nomeRegra, String termosRegra) {
        List<Definicao> definicoes = definicoesDa(nomeRegra, termosRegra);
        if (!definicoes.isEmpty()) {
            return texto -> {
                Predicate<String> tem = termo -> texto.contains(" " + termo + " ");
                return definicoes.stream().anyMatch(d -> atende(d, tem));
            };
        }
        List<String> termos = normalizar(termosRegra);
        List<String> especificos = termos.stream().filter(t -> !SINTOMAS_INESPECIFICOS.contains(t)).toList();
        List<String> sintomas = termos.stream().filter(SINTOMAS_INESPECIFICOS::contains).toList();
        return texto -> {
            Predicate<String> tem = termo -> texto.contains(" " + termo + " ");
            if (especificos.stream().anyMatch(tem)) return true;
            return !sintomas.isEmpty() && sintomas.stream().filter(tem).count() >= Math.min(2, sintomas.size());
        };
    }

    private static boolean atende(Definicao d, Predicate<String> tem) {
        if (!d.hipotese().isEmpty() && d.hipotese().stream().noneMatch(tem)) return false;
        if (!d.obrigatorios().stream().allMatch(g -> g.stream().anyMatch(tem))) return false;
        return d.adicionais().stream().filter(g -> g.stream().anyMatch(tem)).count() >= d.minimoAdicionais();
    }

    /** Condicao SQL equivalente a atende(). Os literais so tem [a-z0-9 ], garantido por normalizar() e pelas constantes. */
    public static String predicadoSql(String coluna, String nomeRegra, String termosRegra) {
        List<Definicao> definicoes = definicoesDa(nomeRegra, termosRegra);
        if (!definicoes.isEmpty()) {
            return definicoes.stream().map(d -> predicadoSql(coluna, d)).collect(Collectors.joining(" OR ", "(", ")"));
        }
        List<String> termos = normalizar(termosRegra);
        List<String> especificos = termos.stream().filter(t -> !SINTOMAS_INESPECIFICOS.contains(t)).toList();
        List<String> sintomas = termos.stream().filter(SINTOMAS_INESPECIFICOS::contains).toList();
        List<String> partes = new ArrayList<>();
        if (!especificos.isEmpty()) partes.add(algum(coluna, especificos));
        if (!sintomas.isEmpty()) partes.add(pelosMenos(coluna, sintomas.stream().map(List::of).toList(), Math.min(2, sintomas.size())));
        return partes.isEmpty() ? "FALSE" : "(" + String.join(" OR ", partes) + ")";
    }

    private static String predicadoSql(String coluna, Definicao d) {
        List<String> partes = new ArrayList<>();
        if (!d.hipotese().isEmpty()) partes.add(algum(coluna, d.hipotese()));
        d.obrigatorios().forEach(g -> partes.add(algum(coluna, g)));
        if (d.minimoAdicionais() > 0) partes.add(pelosMenos(coluna, d.adicionais(), d.minimoAdicionais()));
        return partes.isEmpty() ? "FALSE" : "(" + String.join(" AND ", partes) + ")";
    }

    private static String algum(String coluna, List<String> termos) {
        return termos.stream().map(t -> coluna + " LIKE '% " + t + " %'").collect(Collectors.joining(" OR ", "(", ")"));
    }

    private static String pelosMenos(String coluna, List<List<String>> grupos, int minimo) {
        return grupos.stream().map(g -> "(CASE WHEN " + algum(coluna, g) + " THEN 1 ELSE 0 END)")
                .collect(Collectors.joining(" + ", "(", ") >= " + minimo));
    }

    /** Termos cuja citacao coloca o atendimento na conferencia e cuja negacao precisa ser retirada do texto. */
    public static List<String> termosCitados(String nomeRegra, String termosRegra) {
        Set<String> todos = new LinkedHashSet<>(normalizar(termosRegra));
        for (Definicao d : definicoesDa(nomeRegra, termosRegra)) {
            todos.addAll(d.hipotese());
            d.obrigatorios().forEach(todos::addAll);
            d.adicionais().forEach(todos::addAll);
        }
        return new ArrayList<>(todos);
    }

    /** G-PEC: problema estruturado (CID/CIAP/descricao) registrado pelo profissional; sintoma avulso nao conta. */
    public static List<String> termosEstruturados(String nomeRegra, String termosRegra) {
        Set<String> out = new LinkedHashSet<>();
        for (Definicao d : definicoesDa(nomeRegra, termosRegra)) out.addAll(d.hipotese().isEmpty() ? d.chaves() : d.hipotese());
        normalizar(termosRegra).stream().filter(t -> !SINTOMAS_INESPECIFICOS.contains(t)).forEach(out::add);
        return new ArrayList<>(out);
    }

    /** O diagnostico escrito cita a doenca desta regra? So entao o descarte dela retira o BAM do grupo de acumulos. */
    public static boolean diagnosticoCitaRegra(String diagnostico, String nomeRegra, String termosRegra) {
        String texto = textoBusca(diagnostico);
        return termosEstruturados(nomeRegra, termosRegra).stream().anyMatch(termo -> texto.contains(" " + termo + " "));
    }

    /** Agravos raros ou graves: um unico caso ja justifica investigacao. Lista a validar com a vigilancia. */
    private static final List<String> CASO_UNICO_IMPORTA = List.of(
            "meningite", "meningococica", "sarampo", "rubeola", "febre amarela", "mpox", "monkeypox", "malaria",
            "hantavirose", "hantavirus", "hanta virus");

    public static boolean casoUnicoImporta(String nomeRegra, String termosRegra) {
        // Cada termo e testado separado: "febre, amarela" nao pode virar "febre amarela".
        List<String> textos = new ArrayList<>(normalizar(termosRegra));
        textos.add(textoBusca(nomeRegra).trim());
        return textos.stream().anyMatch(texto -> CASO_UNICO_IMPORTA.stream().anyMatch(termo -> (" " + texto + " ").contains(" " + termo + " ")));
    }

    /** Texto para a tela de configuracao. */
    public static String descricao(String nomeRegra, String termosRegra) {
        List<Definicao> definicoes = definicoesDa(nomeRegra, termosRegra);
        if (!definicoes.isEmpty()) return definicoes.stream().map(Definicao::descricao).collect(Collectors.joining("; ou "));
        return "Sem definição de caso cadastrada: conta o nome do agravo ou ao menos 2 sintomas da lista; 1 sintoma não conta.";
    }
}

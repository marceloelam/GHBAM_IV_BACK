package br.com.iv.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Vigilancia sindromica: agrupa os atendimentos por sindrome (o que o texto clinico sustenta) e lista as
 * doencas compativeis como hipoteses diferenciais, com o que reforca ou enfraquece cada uma.
 *
 * Sintomas se repetem em muitas doencas (febre + mialgia + cefaleia serve para dengue, influenza,
 * leptospirose, covid...). Por isso o sistema nunca escolhe a doenca: mostra a sindrome, as hipoteses
 * possiveis e o grau de sustentacao de cada caso. Confirmacao so por exame ou investigacao do especialista.
 *
 * Base: Guia de Vigilancia em Saude/MS (6a ed.), definicoes de sindrome gripal/SRAG do MS e
 * Lista Nacional de Notificacao Compulsoria (Portaria GM/MS 217/2023). Limites a calibrar com a vigilancia.
 */
public final class SindromeEpidemiologica {

    private SindromeEpidemiologica() {}

    /** Do menos para o mais sustentado. Nenhum grau confirma doenca. */
    public enum Grau { SINDROMICO, COMPATIVEL, HIPOTESE_REGISTRADA, COM_VINCULO }

    /** Do menor para o maior alcance do evento. Todos sao sinais para analise, nunca declaracao. */
    public enum Escala { NENHUMA, ELEVACAO, CASO_GRAVE, AGLOMERADO, POSSIVEL_SURTO, POSSIVEL_EPIDEMIA }

    record Pista(String rotulo, List<String> termos) {}

    record Hipotese(String nome, List<String> registro, List<Pista> aFavor, List<Pista> contra, String exames,
                    boolean notificacaoImediata, Pattern vinculo, Pattern contexto, Pattern vacina) {
        Hipotese(String nome, List<String> registro, List<Pista> aFavor, List<Pista> contra, String exames, boolean imediata) {
            this(nome, registro, aFavor, contra, exames, imediata, regexVinculo(registro), regexContexto(registro), regexVacina(registro));
        }
    }

    /**
     * @param obrigatorios grupos que precisam estar todos presentes (basta um termo de cada)
     * @param adicionais grupos dos quais se exigem ao menos minimoAdicionais
     * @param excluiSe codigos de sindromes que, presentes, explicam melhor o quadro
     * @param casoUnico sindrome grave: um caso ja justifica investigar
     */
    record Sindrome(String codigo, String nome, List<List<String>> obrigatorios, List<List<String>> adicionais,
                    int minimoAdicionais, List<String> excluiSe, String criterio, int minimoSinal, boolean casoUnico,
                    List<Hipotese> hipoteses) {}

    /** Um atendimento: texto ja normalizado ([a-z0-9 ]) e sem mencoes negadas. */
    public record Caso(String paciente, LocalDate data, String bairro, String texto) {}

    record AvaliacaoHipotese(boolean registrada, boolean vinculo, boolean vacina, List<String> aFavor, List<String> contra) {
        boolean compativel() {
            return registrada || vinculo || !aFavor.isEmpty();
        }
    }

    // ---- sintomas -------------------------------------------------------------------------------

    private static final List<String> FEBRE = List.of("febre", "febril", "pirexia", "hipertermia", "febre referida");
    private static final List<String> MIALGIA = List.of("mialgia", "mialgias", "dor no corpo", "dores no corpo", "dores musculares", "dor muscular");
    private static final List<String> CEFALEIA = List.of("cefaleia", "dor de cabeca");
    private static final List<String> RETRO = List.of("dor retro orbitaria", "dor retroorbitaria", "dor atras dos olhos");
    private static final List<String> ARTRALGIA = List.of("artralgia", "poliartralgia", "artrite", "dor articular", "dor nas articulacoes", "dor nas juntas");
    private static final List<String> VOMITO = List.of("nausea", "nauseas", "vomito", "vomitos", "vomitando", "emese");
    private static final List<String> EXANTEMA = List.of("exantema", "rash", "exantematico", "maculopapular", "manchas vermelhas", "manchas no corpo", "erupcao cutanea");
    private static final List<String> PROSTRACAO = List.of("prostracao", "prostrado", "prostrada", "astenia", "adinamia");
    private static final List<String> RESPIRATORIO = List.of("tosse", "dor de garganta", "odinofagia", "coriza", "corrimento nasal", "congestao nasal");
    private static final List<String> CALAFRIO = List.of("calafrio", "calafrios");
    private static final List<String> OLFATO = List.of("anosmia", "ageusia", "perda de olfato", "perda do olfato", "perda de paladar", "perda do paladar");
    private static final List<String> DISPNEIA = List.of("dispneia", "falta de ar", "desconforto respiratorio", "saturacao baixa", "hipoxemia",
            "cianose", "esforco respiratorio", "taquidispneia", "pressao no torax", "aperto no torax");
    private static final List<String> DIARREIA = List.of("diarreia", "diarreica", "evacuacoes liquidas", "fezes liquidas", "fezes amolecidas",
            "gastroenterite", "gastroenterocolite", "disenteria");
    private static final List<String> ICTERICIA = List.of("ictericia", "icterico", "icterica", "pele amarelada", "olhos amarelados");
    private static final List<String> SANGRAMENTO = List.of("sangramento", "hemorragia", "hemorragico", "petequias", "gengivorragia", "epistaxe",
            "hematemese", "melena", "hematuria", "prova do laco positiva", "equimoses");
    private static final List<String> NEUROLOGICO = List.of("rigidez de nuca", "sinais meningeos", "sinal de kernig", "sinal de brudzinski",
            "abaulamento de fontanela", "confusao mental", "rebaixamento do nivel de consciencia", "rebaixamento de consciencia",
            "convulsao", "convulsoes", "crise convulsiva", "desorientado", "desorientada");
    private static final List<String> LINFONODO = List.of("linfadenopatia", "linfonodomegalia", "adenomegalia", "ingua");

    // ---- pistas e hipoteses compartilhadas -------------------------------------------------------

    private static final Pista ENCHENTE = p("Exposição a enchente, lama ou esgoto", "enchente", "alagamento", "lama", "esgoto", "valao");
    private static final Pista ROEDOR = p("Contato com roedores", "rato", "ratos", "roedor", "roedores", "urina de rato");
    private static final Pista PANTURRILHA = p("Dor nas panturrilhas", "dor nas panturrilhas", "dor na panturrilha", "panturrilhas");
    private static final Pista VIAGEM = p("Viagem recente", "viagem", "viajou", "viajante", "garimpo", "amazonia");
    private static final Pista CARRAPATO = p("Contato com carrapato ou capivara", "carrapato", "carrapatos", "capivara", "capivaras");
    private static final Pista MATA = p("Área de mata ou rural", "mata", "zona rural", "area rural", "trilha", "sitio");
    private static final Pista PLAQUETAS = p("Plaquetopenia ou leucopenia", "plaquetopenia", "leucopenia", "plaquetas baixas");
    private static final Pista SIBILANCIA = p("Sibilância ou bronquiolite", "sibilancia", "sibilos", "chiado", "bronquiolite", "lactente");
    private static final Pista PETEQUIAS_TOXEMIA = p("Petéquias, púrpura ou toxemia", "petequias", "purpura", "sufusoes hemorragicas", "toxemiado", "toxemiada", "choque");
    private static final Pista VACINA_EM_DIA = p("Vacinação em dia citada", "vacinacao em dia", "cartao vacinal em dia", "vacinas em dia");

    private static final Hipotese LEPTOSPIROSE = new Hipotese("Leptospirose", List.of("leptospirose"),
            List.of(ENCHENTE, ROEDOR, PANTURRILHA, p("Sufusão conjuntival", "sufusao conjuntival")), List.of(),
            "Sorologia (ELISA IgM) para leptospirose; função renal e hepática", false);
    private static final Hipotese FEBRE_AMARELA = new Hipotese("Febre amarela", List.of("febre amarela"),
            List.of(MATA, p("Sem vacina contra febre amarela", "nao vacinado", "nao vacinada", "sem vacina")), List.of(),
            "RT-PCR ou sorologia para febre amarela; transaminases", true);
    private static final Hipotese MACULOSA = new Hipotese("Febre maculosa", List.of("febre maculosa", "maculosa", "riquetsiose"),
            List.of(CARRAPATO), List.of(), "Sorologia pareada para Rickettsia; não aguardar o exame para tratar", true);
    private static final Hipotese MALARIA = new Hipotese("Malária", List.of("malaria"),
            List.of(VIAGEM, p("Febre com calafrios", CALAFRIO)), List.of(), "Gota espessa ou teste rápido", true);
    private static final Hipotese HANTAVIROSE = new Hipotese("Hantavirose", List.of("hantavirose", "hantavirus", "hanta virus"),
            List.of(ROEDOR, p("Galpão, paiol ou celeiro", "galpao", "paiol", "celeiro")), List.of(), "Sorologia IgM para hantavírus", true);
    private static final Hipotese MENINGOCOCICA = new Hipotese("Doença meningocócica",
            List.of("meningococo", "meningococica", "meningococcemia", "doenca meningococica"),
            List.of(PETEQUIAS_TOXEMIA), List.of(),
            "URGÊNCIA: hemocultura e líquor; quimioprofilaxia dos contatos próximos", true);
    private static final Hipotese INFLUENZA = new Hipotese("Influenza", List.of("influenza", "gripe", "h1n1", "h3n2"),
            List.of(p("Início súbito com mialgia intensa", "inicio subito", "mialgia intensa")), List.of(),
            "Painel viral respiratório (RT-PCR) ou teste rápido", false);
    private static final Hipotese COVID = new Hipotese("Covid-19", List.of("covid", "covid 19", "sars cov 2", "coronavirus"),
            List.of(p("Perda de olfato ou paladar", OLFATO)), List.of(), "Teste de antígeno ou RT-PCR para SARS-CoV-2", false);
    private static final Hipotese VSR = new Hipotese("Vírus sincicial respiratório", List.of("vsr", "virus sincicial", "bronquiolite"),
            List.of(SIBILANCIA), List.of(), "Painel viral respiratório (VSR)", false);
    private static final Hipotese DENGUE = new Hipotese("Dengue", List.of("dengue"),
            List.of(p("Dor retro-orbitária", RETRO), p("Exantema", EXANTEMA), PLAQUETAS, p("Prova do laço positiva", "prova do laco positiva")),
            List.of(), "NS1 ou RT-PCR até o 5º dia; IgM depois; hemograma com plaquetas", false);
    private static final Hipotese CHIKUNGUNYA = new Hipotese("Chikungunya", List.of("chikungunya", "chicungunha", "chikungunha"),
            List.of(p("Artralgia intensa ou edema articular", "artralgia intensa", "poliartralgia", "artrite", "edema articular", "edema periarticular")),
            List.of(), "RT-PCR até o 5º dia; IgM depois", false);
    private static final Hipotese ZIKA = new Hipotese("Zika", List.of("zika"),
            List.of(p("Exantema com prurido", "prurido", "coceira"), p("Conjuntivite não purulenta", "conjuntivite", "hiperemia conjuntival")),
            List.of(), "RT-PCR em sangue ou urina; prioridade em gestantes", false);

    // ---- sindromes ------------------------------------------------------------------------------

    private static final List<Sindrome> SINDROMES = List.of(
        new Sindrome("NEUROLOGICA_FEBRIL", "Síndrome neurológica febril", List.of(FEBRE, NEUROLOGICO), List.of(), 0, List.of(),
            "febre + rigidez de nuca, sinais meníngeos, confusão mental, rebaixamento de consciência ou convulsão", 1, true,
            List.of(MENINGOCOCICA,
                new Hipotese("Meningite bacteriana", List.of("meningite bacteriana", "meningite"),
                    List.of(p("Vômitos em jato", "vomito em jato", "vomitos em jato"), p("Rigidez de nuca", "rigidez de nuca", "sinais meningeos")),
                    List.of(), "Líquor com Gram, cultura e látex; hemocultura", true),
                new Hipotese("Meningite viral", List.of("meningite viral", "meningite asseptica"),
                    List.of(p("Precedida de quadro viral", "virose", "quadro viral")), List.of(), "Líquor (perfil viral) e PCR", true),
                new Hipotese("Encefalite", List.of("encefalite", "meningoencefalite"),
                    List.of(p("Convulsão ou alteração de comportamento", "convulsao", "convulsoes", "crise convulsiva", "alteracao de comportamento")),
                    List.of(), "Líquor com PCR (herpes, arbovírus) e imagem", false),
                MALARIA)),
        new Sindrome("FEBRIL_ICTERO_HEMORRAGICA", "Síndrome febril íctero-hemorrágica", List.of(FEBRE), List.of(ICTERICIA, SANGRAMENTO), 1, List.of(),
            "febre + icterícia ou sangramento (petéquias, gengivorragia, epistaxe, hematêmese, melena)", 1, true,
            List.of(LEPTOSPIROSE,
                new Hipotese("Dengue com sinais de alarme ou grave", List.of("dengue"),
                    List.of(PLAQUETAS, p("Dor abdominal intensa", "dor abdominal intensa"), p("Hemoconcentração", "hemoconcentracao", "hematocrito elevado")),
                    List.of(), "NS1 ou IgM; hemograma seriado", true),
                FEBRE_AMARELA,
                new Hipotese("Hepatites virais", List.of("hepatite"),
                    List.of(p("Colúria ou acolia", "coluria", "acolia", "urina escura", "fezes claras"),
                            p("Transaminases elevadas", "transaminases elevadas", "tgo elevada", "tgp elevada")),
                    List.of(), "Marcadores de hepatites A, B e C", false),
                MACULOSA, MALARIA, HANTAVIROSE)),
        new Sindrome("EXANTEMATICA_FEBRIL", "Síndrome febril exantemática", List.of(FEBRE, EXANTEMA), List.of(), 0, List.of(),
            "febre + exantema", 3, false,
            List.of(
                new Hipotese("Sarampo", List.of("sarampo"),
                    List.of(p("Tosse, coriza ou conjuntivite", "tosse", "coriza", "conjuntivite"), p("Manchas de Koplik", "koplik"), VIAGEM),
                    List.of(VACINA_EM_DIA), "Sorologia IgM/IgG e RT-PCR (swab e urina) já no primeiro atendimento", true),
                new Hipotese("Rubéola", List.of("rubeola"), List.of(p("Linfadenopatia", LINFONODO)),
                    List.of(VACINA_EM_DIA), "Sorologia IgM e RT-PCR", true),
                new Hipotese("Dengue, zika ou chikungunya", List.of("dengue", "zika", "chikungunya", "chicungunha", "arbovirose"),
                    List.of(p("Dor retro-orbitária", RETRO), p("Artralgia", ARTRALGIA), p("Prurido", "prurido", "coceira")),
                    List.of(), "NS1/RT-PCR até o 5º dia; IgM depois", false),
                new Hipotese("Varicela", List.of("varicela", "catapora"),
                    List.of(p("Vesículas ou crostas", "vesiculas", "vesicula", "bolhas", "crostas")), List.of(),
                    "Diagnóstico clínico; PCR da lesão se atípico", false),
                new Hipotese("Mpox", List.of("mpox", "monkeypox"),
                    List.of(p("Lesões pustulosas, umbilicadas ou genitais", "pustulas", "pustula", "umbilicada", "lesao genital", "lesoes genitais", "lesao anal"),
                            p("Linfadenopatia", LINFONODO)),
                    List.of(), "PCR de swab da lesão", true),
                new Hipotese("Escarlatina", List.of("escarlatina"),
                    List.of(p("Faringite ou amigdalite", "faringite", "amigdalite", "placas na garganta"),
                            p("Língua em framboesa ou pele áspera", "lingua em framboesa", "pele aspera")),
                    List.of(), "Teste rápido ou cultura para estreptococo", false),
                MENINGOCOCICA)),
        new Sindrome("SRAG", "Síndrome respiratória aguda grave (SRAG)", List.of(RESPIRATORIO, DISPNEIA), List.of(FEBRE, CALAFRIO, CEFALEIA, OLFATO), 1, List.of(),
            "tosse, dor de garganta ou coriza + outro sintoma gripal + dispneia, desconforto respiratório ou saturação baixa", 3, false,
            List.of(INFLUENZA, COVID, VSR,
                new Hipotese("Pneumonia bacteriana", List.of("pneumonia", "pnm", "broncopneumonia"),
                    List.of(p("Expectoração purulenta", "expectoracao purulenta", "escarro purulento", "secrecao purulenta"),
                            p("Crepitações ou consolidação", "crepitacoes", "estertores", "consolidacao")),
                    List.of(), "Raio-X de tórax, hemograma; hemocultura se grave", false),
                HANTAVIROSE)),
        new Sindrome("GRIPAL", "Síndrome gripal", List.of(RESPIRATORIO), List.of(FEBRE, CALAFRIO, CEFALEIA, OLFATO), 1, List.of(),
            "tosse, dor de garganta ou coriza + febre, calafrios, cefaleia ou alteração de olfato/paladar", 5, false,
            List.of(INFLUENZA, COVID, VSR,
                new Hipotese("Outros vírus respiratórios", List.of("resfriado", "ivas", "rinofaringite", "nasofaringite", "virose"),
                    List.of(p("Quadro leve de vias aéreas superiores", "espirros", "congestao nasal")), List.of(),
                    "Painel viral apenas se surto ou caso grave", false))),
        new Sindrome("DIARREICA_AGUDA", "Doença diarreica aguda", List.of(DIARREIA), List.of(), 0, List.of(),
            "diarreia, evacuações líquidas ou gastroenterite registrada", 5, false,
            List.of(
                new Hipotese("Viral (rotavírus, norovírus)", List.of("rotavirus", "norovirus", "gastroenterite viral", "virose"),
                    List.of(p("Vômitos predominantes", VOMITO)), List.of(), "Pesquisa de rotavírus e norovírus nas fezes, se surto", false),
                new Hipotese("Bacteriana ou disenteria", List.of("shigella", "shiguelose", "salmonella", "salmonelose", "diarreia bacteriana", "disenteria"),
                    List.of(p("Sangue ou muco nas fezes", "sangue nas fezes", "fezes com sangue", "muco nas fezes", "diarreia sanguinolenta", "disenteria"),
                            p("Febre alta", "febre alta")),
                    List.of(), "Coprocultura", false),
                new Hipotese("Doença transmitida por alimento (DTA)", List.of("intoxicacao alimentar", "toxinfeccao alimentar", "dta"),
                    List.of(p("Refeição suspeita ou comum", "comeu", "ingeriu", "ingestao de", "festa", "restaurante", "marmita", "refeicao", "mesma refeicao"),
                            p("Início horas após refeição", "apos almoco", "apos jantar", "apos refeicao", "apos comer")),
                    List.of(), "Coprocultura; guardar amostra do alimento. Surto de DTA (2 ou mais pessoas da mesma fonte) é notificação imediata", true),
                new Hipotese("Cólera", List.of("colera"),
                    List.of(p("Diarreia aquosa abundante (água de arroz)", "agua de arroz", "diarreia aquosa abundante"),
                            p("Desidratação grave", "desidratacao grave")),
                    List.of(), "Coprocultura para Vibrio cholerae", true))),
        new Sindrome("FEBRIL_AGUDA", "Síndrome febril aguda indiferenciada", List.of(FEBRE),
            List.of(MIALGIA, Stream.concat(CEFALEIA.stream(), RETRO.stream()).toList(), ARTRALGIA, VOMITO, EXANTEMA, PROSTRACAO), 2,
            List.of("GRIPAL", "SRAG", "DIARREICA_AGUDA", "NEUROLOGICA_FEBRIL", "FEBRIL_ICTERO_HEMORRAGICA"),
            "febre + ao menos 2 entre mialgia, cefaleia/dor retro-orbitária, artralgia, náusea/vômito, exantema, prostração; "
                + "sem quadro respiratório, diarreico, neurológico ou íctero-hemorrágico que explique melhor", 5, false,
            List.of(DENGUE, CHIKUNGUNYA, ZIKA, LEPTOSPIROSE, MALARIA, MACULOSA, FEBRE_AMARELA))
    );

    /** Relato de varias pessoas com o mesmo quadro: vinculo para a sindrome, sem precisar citar a doenca. */
    private static final Pattern AGRUPAMENTO = Pattern.compile(
            " (?:outros casos|outras pessoas|varios casos|varias pessoas|mais pessoas|familiares tambem|familia toda|toda a familia"
            + "|toda familia|mesmos sintomas|mesmo quadro|mesma refeicao|surto)(?= )");

    // ---- regex de contexto ----------------------------------------------------------------------

    private static String alternativas(Collection<String> termos) {
        return termos.stream().sorted((a, b) -> b.length() - a.length()).map(Pattern::quote).collect(Collectors.joining("|"));
    }

    /** "contato com caso de dengue", "filho com dengue": vinculo epidemiologico, nao doenca deste paciente. */
    static Pattern regexVinculo(List<String> registro) {
        String t = alternativas(registro);
        return Pattern.compile(" (?:contato|contatos|contactante|comunicante|convive|conviveu|convivio|exposicao|exposto|exposta)"
                + "(?: [a-z0-9]+){0,2} (?:caso|casos|pessoa|pessoas|paciente|familiar|familiares|parente|crianca|criancas|colega|colegas|vizinho|vizinhos|alguem)"
                + "(?: [a-z0-9]+){0,3} (?:" + t + ")(?= )"
                + "| (?:familiar|familiares|mae|pai|filho|filha|filhos|irmao|irma|irmaos|esposa|esposo|marido|vizinho|vizinha|vizinhos"
                + "|colega|colegas|avo|tio|tia|primo|prima)(?: [a-z0-9]+){0,3} (?:" + t + ")(?= )");
    }

    /** "teve dengue", "historia de dengue", "dengue em 2019": antecedente, nao doenca atual. */
    static Pattern regexContexto(List<String> registro) {
        String t = alternativas(registro);
        return Pattern.compile(" (?:teve|tivera)(?: [a-z0-9]+){0,1} (?:" + t + ")(?= )"
                + "| (?:historia|historico|antecedente|antecedentes|passado)(?: [a-z0-9]+){0,2} (?:" + t + ")(?= )"
                + "| (?:" + t + ") (?:previa|previo|pregressa|pregresso|anterior|antiga|ha [0-9]+ (?:anos|meses)|em [0-9]{4}"
                + "|na infancia|quando crianca|tratada|tratado|curada|curado)(?= )");
    }

    /** "vacinado contra febre amarela": nao e caso, e enfraquece a hipotese. */
    static Pattern regexVacina(List<String> registro) {
        return Pattern.compile(" (?:vacina|vacinas|vacinado|vacinada|vacinacao|imunizado|imunizada|imunizacao)(?: [a-z0-9]+){0,3} (?:"
                + alternativas(registro) + ")(?= )");
    }

    // ---- avaliacao por atendimento --------------------------------------------------------------

    private static String comEspacos(String texto) {
        return " " + (texto == null ? "" : texto.trim()) + " ";
    }

    private static boolean cita(String texto, List<String> termos) {
        for (String termo : termos) {
            if (texto.contains(" " + termo + " ")) return true;
        }
        return false;
    }

    private static boolean cumpre(Sindrome s, String texto) {
        if (!s.obrigatorios().stream().allMatch(grupo -> cita(texto, grupo))) return false;
        return s.adicionais().stream().filter(grupo -> cita(texto, grupo)).count() >= s.minimoAdicionais();
    }

    /** Codigos das sindromes que o texto cumpre, ja retirando as que outra sindrome explica melhor. */
    public static List<String> sindromesDoTexto(String textoNormalizado) {
        String texto = comEspacos(textoNormalizado);
        List<Sindrome> cumpridas = SINDROMES.stream().filter(s -> cumpre(s, texto)).toList();
        Set<String> codigos = cumpridas.stream().map(Sindrome::codigo).collect(Collectors.toSet());
        return cumpridas.stream().filter(s -> s.excluiSe().stream().noneMatch(codigos::contains)).map(Sindrome::codigo).toList();
    }

    static AvaliacaoHipotese avaliar(Hipotese h, String textoNormalizado) {
        String texto = comEspacos(textoNormalizado);
        boolean vinculo = h.vinculo().matcher(texto).find();
        boolean vacina = h.vacina().matcher(texto).find();
        String semContexto = h.contexto().matcher(h.vinculo().matcher(h.vacina().matcher(texto).replaceAll(" ")).replaceAll(" ")).replaceAll(" ");
        boolean registrada = cita(semContexto, h.registro());
        List<String> aFavor = h.aFavor().stream().filter(pista -> cita(texto, pista.termos())).map(Pista::rotulo).toList();
        List<String> contra = new ArrayList<>(h.contra().stream().filter(pista -> cita(texto, pista.termos())).map(Pista::rotulo).toList());
        if (vacina) contra.add("Vacinação citada");
        return new AvaliacaoHipotese(registrada, vinculo, vacina, aFavor, contra);
    }

    static Grau grau(Sindrome s, String textoNormalizado) {
        String texto = comEspacos(textoNormalizado);
        Grau grau = Grau.SINDROMICO;
        boolean vinculo = AGRUPAMENTO.matcher(texto).find();
        for (Hipotese h : s.hipoteses()) {
            AvaliacaoHipotese a = avaliar(h, texto);
            if (a.compativel() && grau.ordinal() < Grau.COMPATIVEL.ordinal()) grau = Grau.COMPATIVEL;
            if (a.registrada() && grau.ordinal() < Grau.HIPOTESE_REGISTRADA.ordinal()) grau = Grau.HIPOTESE_REGISTRADA;
            vinculo |= a.vinculo();
        }
        return vinculo ? Grau.COM_VINCULO : grau;
    }

    static Sindrome sindrome(String codigo) {
        return SINDROMES.stream().filter(s -> s.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    /** Termos para o pre-filtro do banco: toda sindrome exige o primeiro grupo obrigatorio. */
    public static List<String> termosDeBusca() {
        return SINDROMES.stream().flatMap(s -> s.obrigatorios().get(0).stream()).distinct().toList();
    }

    /** Termos cuja negacao ("nega febre", "dengue descartada") precisa sair do texto antes da avaliacao. */
    public static List<String> termosNegaveis() {
        Set<String> termos = new LinkedHashSet<>();
        for (Sindrome s : SINDROMES) {
            s.obrigatorios().forEach(termos::addAll);
            s.adicionais().forEach(termos::addAll);
            for (Hipotese h : s.hipoteses()) {
                termos.addAll(h.registro());
                h.aFavor().forEach(pista -> termos.addAll(pista.termos()));
            }
        }
        return new ArrayList<>(termos);
    }

    // ---- painel agregado ------------------------------------------------------------------------

    private static final int JANELAS = 5; // 4 anteriores de mesmo tamanho + a atual

    static final class Acumulado {
        final List<Set<String>> janelas = new ArrayList<>();
        final Map<String, Set<String>> bairroAtual = new HashMap<>();
        final Map<String, Set<String>> bairroBase = new HashMap<>();
        final Map<String, Grau> grauPorPaciente = new HashMap<>();
        final Set<String> comDiferencial = new HashSet<>();
        final Map<String, Map<String, Set<String>>> hipoteses = new LinkedHashMap<>();

        Acumulado() {
            for (int i = 0; i < JANELAS; i++) janelas.add(new HashSet<>());
        }

        Set<String> conjunto(String hipotese, String chave) {
            return hipoteses.computeIfAbsent(hipotese, k -> new LinkedHashMap<>()).computeIfAbsent(chave, k -> new HashSet<>());
        }
    }

    /**
     * Painel do periodo [inicio, fim] contra as 4 janelas anteriores de mesmo tamanho. Conta paciente, nunca
     * atendimento, e nao devolve nome nem identificador de paciente.
     *
     * @param casos atendimentos de inicio - 4 periodos ate fim, texto ja sem mencoes negadas
     * @param ajusteAtendimentos crescimento do movimento da unidade (1x a 2x), para nao alarmar so pelo movimento
     */
    public static Map<String, Object> painel(List<Caso> casos, LocalDate inicio, LocalDate fim, double ajusteAtendimentos) {
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        LocalDate inicioBase = inicio.minusDays(dias * 4);
        Map<String, Acumulado> porSindrome = new LinkedHashMap<>();
        for (Caso caso : casos) {
            if (caso == null || caso.data() == null || caso.paciente() == null) continue;
            if (caso.data().isBefore(inicioBase) || caso.data().isAfter(fim)) continue;
            String texto = comEspacos(caso.texto());
            List<String> codigos = sindromesDoTexto(texto);
            if (codigos.isEmpty()) continue;
            int janela = (int) Math.min(JANELAS - 1, ChronoUnit.DAYS.between(inicioBase, caso.data()) / dias);
            boolean atual = janela == JANELAS - 1;
            String bairro = caso.bairro() == null || caso.bairro().isBlank() ? "" : caso.bairro().trim().toUpperCase(Locale.ROOT);
            for (String codigo : codigos) {
                Sindrome s = sindrome(codigo);
                Acumulado acc = porSindrome.computeIfAbsent(codigo, k -> new Acumulado());
                acc.janelas.get(janela).add(caso.paciente());
                if (!bairro.isEmpty()) {
                    (atual ? acc.bairroAtual : acc.bairroBase).computeIfAbsent(bairro, k -> new HashSet<>()).add(caso.paciente());
                }
                if (!atual) continue;
                acc.grauPorPaciente.merge(caso.paciente(), grau(s, texto), (a, b) -> a.ordinal() >= b.ordinal() ? a : b);
                for (Hipotese h : s.hipoteses()) {
                    AvaliacaoHipotese a = avaliar(h, texto);
                    if (a.compativel()) {
                        acc.comDiferencial.add(caso.paciente());
                        acc.conjunto(h.nome(), "#compativel").add(caso.paciente());
                    }
                    if (a.registrada()) acc.conjunto(h.nome(), "#registrada").add(caso.paciente());
                    if (a.vinculo()) acc.conjunto(h.nome(), "#vinculo").add(caso.paciente());
                    a.aFavor().forEach(rotulo -> acc.conjunto(h.nome(), "+" + rotulo).add(caso.paciente()));
                    a.contra().forEach(rotulo -> acc.conjunto(h.nome(), "-" + rotulo).add(caso.paciente()));
                }
            }
        }

        List<Map<String, Object>> sindromes = new ArrayList<>();
        for (Map.Entry<String, Acumulado> entrada : porSindrome.entrySet()) {
            Map<String, Object> item = montarSindrome(sindrome(entrada.getKey()), entrada.getValue(), inicioBase, dias, ajusteAtendimentos);
            if (((Number) item.get("casos")).longValue() > 0) sindromes.add(item);
        }
        sindromes.sort((a, b) -> {
            int porEscala = Integer.compare(Escala.valueOf((String) b.get("escala")).ordinal(), Escala.valueOf((String) a.get("escala")).ordinal());
            return porEscala != 0 ? porEscala : Long.compare(((Number) b.get("casos")).longValue(), ((Number) a.get("casos")).longValue());
        });

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("inicio", inicio.toString());
        resposta.put("fim", fim.toString());
        resposta.put("dias", dias);
        resposta.put("aviso", "Apoio ao especialista. O sistema agrupa os atendimentos por síndrome e lista as doenças compatíveis a partir do "
                + "texto clínico. Sintomas se repetem em muitas doenças: nenhuma hipótese é diagnóstico, e a confirmação depende de exame "
                + "ou investigação.");
        resposta.put("sindromes", sindromes);
        return resposta;
    }

    private static Map<String, Object> montarSindrome(Sindrome s, Acumulado acc, LocalDate inicioBase, long dias, double ajuste) {
        long atual = acc.janelas.get(JANELAS - 1).size();
        Set<String> anteriores = new HashSet<>();
        for (int i = 0; i < JANELAS - 1; i++) anteriores.addAll(acc.janelas.get(i));
        double mediaAnterior = anteriores.size() / 4d;
        AvaliacaoSinalEpidemiologico.Resultado sinal = AvaliacaoSinalEpidemiologico.avaliar(new AvaliacaoSinalEpidemiologico.Entrada(
                atual, mediaAnterior, null, ajuste, 1d, s.minimoSinal(), 2d, s.casoUnico(), false, false));

        List<Map<String, Object>> serie = new ArrayList<>();
        long[] contagens = new long[JANELAS];
        for (int i = 0; i < JANELAS; i++) {
            contagens[i] = acc.janelas.get(i).size();
            Map<String, Object> ponto = new LinkedHashMap<>();
            ponto.put("inicio", inicioBase.plusDays(dias * i).toString());
            ponto.put("fim", inicioBase.plusDays(dias * (i + 1) - 1).toString());
            ponto.put("casos", contagens[i]);
            serie.add(ponto);
        }

        List<Map<String, Object>> bairros = new ArrayList<>();
        for (Map.Entry<String, Set<String>> b : acc.bairroAtual.entrySet()) {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("bairro", b.getKey());
            linha.put("casos", (long) b.getValue().size());
            linha.put("mediaAnterior", arredondar(acc.bairroBase.getOrDefault(b.getKey(), Set.of()).size() / 4d));
            bairros.add(linha);
        }
        bairros.sort((a, b) -> Long.compare((Long) b.get("casos"), (Long) a.get("casos")));

        Map<String, Long> graus = new LinkedHashMap<>();
        for (Grau g : Grau.values()) graus.put(g.name(), 0L);
        acc.grauPorPaciente.values().forEach(g -> graus.merge(g.name(), 1L, Long::sum));
        long comVinculo = graus.get(Grau.COM_VINCULO.name());

        List<Map<String, Object>> hipoteses = new ArrayList<>();
        for (Hipotese h : s.hipoteses()) {
            Map<String, Set<String>> dados = acc.hipoteses.getOrDefault(h.nome(), Map.of());
            long compativeis = dados.getOrDefault("#compativel", Set.of()).size();
            if (compativeis == 0) continue;
            Map<String, Object> hip = new LinkedHashMap<>();
            hip.put("nome", h.nome());
            hip.put("compativeis", compativeis);
            hip.put("registradas", (long) dados.getOrDefault("#registrada", Set.of()).size());
            hip.put("comVinculo", (long) dados.getOrDefault("#vinculo", Set.of()).size());
            hip.put("aFavor", pistas(dados, "+"));
            hip.put("contra", pistas(dados, "-"));
            hip.put("exames", h.exames());
            hip.put("notificacaoImediata", h.notificacaoImediata());
            hipoteses.add(hip);
        }
        hipoteses.sort((a, b) -> Long.compare(((Number) b.get("compativeis")).longValue(), ((Number) a.get("compativeis")).longValue()));

        EscalaAvaliada escala = escala(s, sinal, contagens, acc, comVinculo);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("codigo", s.codigo());
        item.put("nome", s.nome());
        item.put("criterio", s.criterio());
        item.put("casos", atual);
        item.put("mediaAnterior", arredondar(mediaAnterior));
        item.put("esperado", arredondar(sinal.esperadoRecente()));
        item.put("estado", sinal.estado().name());
        item.put("escala", escala.escala().name());
        item.put("motivos", escala.motivos());
        item.put("orientacao", orientacao(escala.escala()));
        item.put("serie", serie);
        item.put("bairros", bairros.size() > 8 ? new ArrayList<>(bairros.subList(0, 8)) : bairros);
        item.put("graus", graus);
        item.put("semDiferencial", atual - acc.comDiferencial.size());
        item.put("hipoteses", hipoteses);
        item.put("notificacaoImediata", hipoteses.stream().anyMatch(h -> Boolean.TRUE.equals(h.get("notificacaoImediata"))
                && ((Number) h.get("registradas")).longValue() + ((Number) h.get("comVinculo")).longValue() > 0));
        return item;
    }

    private static List<Map<String, Object>> pistas(Map<String, Set<String>> dados, String prefixo) {
        List<Map<String, Object>> out = new ArrayList<>();
        dados.forEach((chave, pacientes) -> {
            if (!chave.startsWith(prefixo)) return;
            Map<String, Object> pista = new LinkedHashMap<>();
            pista.put("rotulo", chave.substring(1));
            pista.put("casos", (long) pacientes.size());
            out.add(pista);
        });
        out.sort((a, b) -> Long.compare((Long) b.get("casos"), (Long) a.get("casos")));
        return out;
    }

    record EscalaAvaliada(Escala escala, List<String> motivos) {}

    /**
     * Aglomerado: bairro com 3 ou mais casos e ao menos o dobro da sua media anterior.
     * Possivel surto: aglomerado (ou sindrome grave) com 2 ou mais casos ligados por vinculo citado no texto.
     * Possivel epidemia: alta na unidade nos 2 ultimos periodos seguidos e em 3 ou mais bairros.
     */
    static EscalaAvaliada escala(Sindrome s, AvaliacaoSinalEpidemiologico.Resultado sinal, long[] contagens, Acumulado acc, long comVinculo) {
        List<String> motivos = new ArrayList<>();
        String aglomerado = null;
        long casosAglomerado = 0;
        double mediaAglomerado = 0;
        long bairrosAfetados = 0;
        for (Map.Entry<String, Set<String>> b : acc.bairroAtual.entrySet()) {
            long n = b.getValue().size();
            if (n >= 2) bairrosAfetados++;
            double media = acc.bairroBase.getOrDefault(b.getKey(), Set.of()).size() / 4d;
            if (n >= 3 && n >= media * 2 && n > casosAglomerado) {
                aglomerado = b.getKey();
                casosAglomerado = n;
                mediaAglomerado = media;
            }
        }
        if (aglomerado != null) {
            motivos.add(casosAglomerado + " casos em " + aglomerado + " (média anterior " + formatar(mediaAglomerado) + ")");
        }

        double referencia = (contagens[0] + contagens[1] + contagens[2]) / 3d;
        boolean sustentado = contagens[3] >= s.minimoSinal() && contagens[4] >= s.minimoSinal()
                && contagens[3] > referencia * 1.5 && contagens[4] > referencia * 1.5;

        if (sinal.alerta() && !s.casoUnico() && sustentado && bairrosAfetados >= 3) {
            motivos.add(0, "Acima do esperado na unidade por 2 períodos seguidos (" + contagens[3] + " e " + contagens[4]
                    + " casos; referência " + formatar(referencia) + ")");
            motivos.add("Casos espalhados por " + bairrosAfetados + " bairros");
            return new EscalaAvaliada(Escala.POSSIVEL_EPIDEMIA, motivos);
        }
        if ((aglomerado != null || s.casoUnico()) && comVinculo >= 2) {
            motivos.add(comVinculo + " casos com vínculo citado no atendimento (contato com caso, familiares ou mesma fonte)");
            return new EscalaAvaliada(Escala.POSSIVEL_SURTO, motivos);
        }
        if (aglomerado != null) return new EscalaAvaliada(Escala.AGLOMERADO, motivos);
        if (s.casoUnico() && contagens[4] > 0) {
            motivos.add("Síndrome grave: cada caso merece avaliação individual");
            return new EscalaAvaliada(Escala.CASO_GRAVE, motivos);
        }
        if (sinal.alerta()) {
            motivos.add("Acima do esperado na unidade (" + contagens[4] + " casos; esperado " + formatar(sinal.esperadoRecente())
                    + "), sem concentração em bairro nem vínculo citado");
            return new EscalaAvaliada(Escala.ELEVACAO, motivos);
        }
        return new EscalaAvaliada(Escala.NENHUMA, motivos);
    }

    static String orientacao(Escala escala) {
        return switch (escala) {
            case POSSIVEL_EPIDEMIA -> "Comparar com a série do município e com o laboratório; comunicar a vigilância municipal. "
                    + "Coletar exame de uma amostra dos casos para identificar o agente.";
            case POSSIVEL_SURTO -> "Investigar os casos com vínculo: fonte comum, contatos, data de início dos sintomas. "
                    + "Coletar exames dos primeiros casos antes de tratar como surto.";
            case AGLOMERADO -> "Verificar se os casos do bairro têm ligação (mesmo endereço, escola, trabalho, evento) e se a hipótese é a mesma.";
            case CASO_GRAVE -> "Avaliar cada caso: síndromes graves exigem investigação e, conforme a hipótese, notificação imediata.";
            case ELEVACAO -> "Aumento geral sem concentração: conferir mudança no movimento da unidade ou na forma de registro antes de concluir.";
            case NENHUMA -> "Dentro do esperado para a unidade.";
        };
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 10) / 10d;
    }

    private static String formatar(double valor) {
        return String.format(Locale.ROOT, "%.1f", valor).replace('.', ',');
    }

    private static Pista p(String rotulo, String... termos) {
        return new Pista(rotulo, List.of(termos));
    }

    private static Pista p(String rotulo, List<String> termos) {
        return new Pista(rotulo, termos);
    }
}

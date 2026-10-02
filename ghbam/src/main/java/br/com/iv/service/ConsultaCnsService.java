package br.com.iv.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.iv.dto.CadsusPdqConsultaDto;
import br.com.iv.dto.CadsusPdqRespostaDto;
import br.com.iv.dto.ConsultaCnsDto;
import br.com.iv.repository.PacientesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Consulta o CNS no cadastro publico do CNES a partir de CPF + data de nascimento.
 *
 * Por que fica no backend e nao no navegador: cnesadm.datasus.gov.br nao manda
 * cabecalho CORS, entao um fetch direto da /recepcao morre em "Failed to fetch".
 * Quem fala com o DATASUS aqui e o servidor.
 *
 * O endpoint tambem nao e um JSON: e o mesmo formulario HTML do cadastro publico,
 * em tres etapas obrigatorias -
 *   1. GET  na pagina para pegar o token _csrf e o cookie de sessao;
 *   2. POST com cpf, dataNascimento e o _csrf da MESMA sessao;
 *   3. quando o CPF e localizado a resposta e 302, e so a pagina do redirect traz
 *      CNS e Nome preenchidos (o Location volta em http, por isso forcamos https).
 *
 * Os campos uteis chegam como input readonly: e o jeito que o CNES tem de dizer
 * "isto aqui foi resolvido por mim". CPF nao localizado volta como li dentro de
 * ul class="text-red" - nao e erro de integracao, e resposta negativa, e por isso
 * sobe como encontrado=false com a mensagem original, nao como excecao.
 *
 * Raspagem de HTML de terceiro quebra quando o DATASUS mexer no layout: por isso a
 * consulta e best-effort e a /recepcao continua aceitando o CNS digitado a mao.
 */
@ApplicationScoped
public class ConsultaCnsService {

    private static final Logger LOG = Logger.getLogger(ConsultaCnsService.class);

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final Pattern BLOCO_ERRO = Pattern.compile("<ul class=\"text-red\">([\\s\\S]*?)</ul>");
    private static final Pattern ITEM_ERRO = Pattern.compile("<li[^>]*>([\\s\\S]*?)</li>");
    private static final Pattern INPUT = Pattern.compile("<input\\b([^>]*)>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ATTR_NAME = Pattern.compile("\\bname=\"([^\"]+)\"");
    private static final Pattern ATTR_VALUE = Pattern.compile("\\bvalue=\"([^\"]*)\"");
    private static final Pattern READONLY = Pattern.compile("\\breadonly\\b");
    private static final Pattern DATA_ANO_PRIMEIRO = Pattern.compile("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$");
    private static final Pattern DATA_DIA_PRIMEIRO = Pattern.compile("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})$");

    @ConfigProperty(name = "ghbam.cns.consulta.url",
            defaultValue = "https://cnesadm.datasus.gov.br/cnesadm/publico/usuarios/cadastro")
    String urlCnes;

    @ConfigProperty(name = "ghbam.cns.consulta.timeout.seconds", defaultValue = "15")
    int timeoutSegundos;

    @ConfigProperty(name = "ghbam.cns.consulta.cache.minutos", defaultValue = "60")
    int cacheMinutos;

    @ConfigProperty(name = "ghbam.cns.consulta.habilitado", defaultValue = "true")
    boolean habilitado;

    @Inject
    PacientesRepository pacientesRepository;

    @Inject
    CadsusPdqClienteService cadsusPdqClienteService;

    // Toggle global (nao por unidade), controlado pela tela /base2/integracao-sus - sem
    // precisar de variavel de ambiente nem reiniciar o servidor. Enquanto desligado (estado
    // inicial), o comportamento desta classe fica identico ao de antes: so scraping do CNES.
    @Inject
    ConfiguracaoCnsPdqService configuracaoCnsPdqService;

    // A recepcao repete a mesma consulta varias vezes por atendimento (o campo
    // dispara no blur). Cache curto em memoria para nao bater no DATASUS a cada
    // clique - e para nao virar um scanner de CPF vindo daqui.
    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();

    private record Entrada(ConsultaCnsDto valor, Instant expiraEm) {
    }

    /** Mantido para os chamadores existentes que nao tem unidade a mao (ex.: FaturamentoProducaoService). */
    public ConsultaCnsDto consultar(String cpfBruto, String nascimentoBruto) {
        return consultar(cpfBruto, nascimentoBruto, "");
    }

    public ConsultaCnsDto consultar(String cpfBruto, String nascimentoBruto, String unidade) {
        return consultar(cpfBruto, nascimentoBruto, "", unidade);
    }

    public ConsultaCnsDto consultar(String cpfBruto, String nascimentoBruto, String nomeBruto, String unidade) {
        return consultar(cpfBruto, nascimentoBruto, nomeBruto, "", unidade);
    }

    /**
     * @param nomeBruto    usado como ULTIMO recurso quando nao ha CPF - emergencia sem
     *                     documento, paciente que so consegue informar o proprio nome. O
     *                     scraping do CNES exige CPF; sem ele, so a API oficial do PDQ
     *                     ajuda (Nome + Nascimento e/ou Nome da mae), e so quando o
     *                     toggle global esta ligado (ConfiguracaoCnsPdqService).
     * @param nomeMaeBruto opcional, reforca a busca por nome (e pode, sozinho, bastar
     *                     junto do nome quando nao ha data de nascimento confiavel).
     */
    public ConsultaCnsDto consultar(String cpfBruto, String nascimentoBruto, String nomeBruto, String nomeMaeBruto, String unidade) {
        if (!habilitado) {
            return ConsultaCnsDto.naoEncontrado(List.of("Consulta ao CNES desabilitada nesta instalacao."));
        }

        String cpf = formatarCpf(cpfBruto);
        if (cpf == null) {
            String nome = nomeBruto == null ? "" : nomeBruto.trim();
            String nomeMae = nomeMaeBruto == null ? "" : nomeMaeBruto.trim();
            String nascimentoEmergencia = formatarData(nascimentoBruto);
            if (!nome.isBlank() && nascimentoEmergencia != null && unidade != null && !unidade.isBlank()
                    && configuracaoCnsPdqService.ativo(unidade)) {
                return consultarPorNome(nome, nascimentoEmergencia, nomeMae, unidade);
            }
            boolean cpfInformado = cpfBruto != null && !cpfBruto.isBlank();
            return ConsultaCnsDto.naoEncontrado(List.of(cpfInformado
                    ? "CPF invalido - informe os 11 digitos."
                    : "Sem CPF: informe Nome completo e data de nascimento para tentar o CADSUS oficial."));
        }

        String nascimento = formatarData(nascimentoBruto);
        if (nascimento == null) {
            return ConsultaCnsDto.naoEncontrado(List.of("Data de nascimento invalida - use dd/mm/aaaa."));
        }

        // Paciente falecido nao volta a ser atendido: puxar o CNS dele so serve para
        // reabrir um cadastro que a recepcao ja bloqueia em tela. Fica antes do cache
        // de proposito - resultado positivo guardado antes do obito nao pode escapar.
        if (obitoRegistrado(cpf)) {
            return ConsultaCnsDto.naoEncontrado(
                    List.of("Paciente consta como falecido no cadastro - consulta ao CNES nao realizada."));
        }

        String chave = cpf + "|" + nascimento;
        Entrada emCache = cache.get(chave);
        if (emCache != null && emCache.expiraEm().isAfter(Instant.now())) {
            return emCache.valor();
        }
        cache.entrySet().removeIf(e -> e.getValue().expiraEm().isBefore(Instant.now()));

        // Caminho novo (toggle global via ConfiguracaoCnsPdqService, desligado por padrao):
        // tenta a API oficial do PDQ primeiro, que fala com o CADSUS de verdade em vez de
        // raspar HTML. So usa o resultado se vier um CNS que passa no modulo 11 - qualquer
        // coisa fora disso (sem credencial, erro de rede, resposta sem CNS reconhecivel) cai
        // no scraping de sempre, sem exceção subir e sem o operador perceber a tentativa.
        if (unidade != null && !unidade.isBlank() && configuracaoCnsPdqService.ativo(unidade)) {
            ConsultaCnsDto viaApiOficial = tentarApiOficial(cpf, unidade);
            if (viaApiOficial != null) {
                cache.put(chave, new Entrada(viaApiOficial, Instant.now().plus(Duration.ofMinutes(cacheMinutos))));
                return viaApiOficial;
            }
        }

        try {
            ConsultaCnsDto resultado = executar(cpf, nascimento);
            // So o resultado positivo entra no cache: negativa pode ser cadastro
            // recem-criado no CNES, e o operador precisa poder tentar de novo.
            if (resultado.encontrado()) {
                cache.put(chave, new Entrada(resultado, Instant.now().plus(Duration.ofMinutes(cacheMinutos))));
            }
            return resultado;
        } catch (Exception e) {
            LOG.warn("Consulta de CNS no CNES falhou (cpf " + cpf.substring(0, 3) + "***): " + e.getMessage());
            throw new IllegalStateException("Nao foi possivel consultar o CNES agora: " + e.getMessage(), e);
        }
    }

    /** Sobrecarga sem nome da mae - usada pelo FaturamentoProducaoService (RN nao tem esse dado cadastrado). */
    public ConsultaCnsDto consultarPorNome(String nomePaciente, String nascimentoFormatado, String unidade) {
        return consultarPorNome(nomePaciente, nascimentoFormatado, "", unidade);
    }

    /**
     * Busca o CNS via API oficial do PDQ usando Nome + Data de nascimento (e Nome da mae,
     * quando disponivel) - o unico caminho quando nao ha CPF (emergencia sem documento,
     * recem-nascido sem CPF proprio). O scraping do CNES nao aceita busca por nome, entao
     * nao ha fallback aqui: sem credencial ativa ou sem bater com o cadastro do CADSUS, o
     * resultado e "nao encontrado" mesmo, sem excecao subir.
     *
     * O CPF do retorno vem de um bonus best-effort: se o CADSUS devolver o CPF vinculado
     * ao mesmo registro (patientPerson/asOtherIDs), extraimos e devolvemos preenchido -
     * mas isso AINDA NAO foi confirmado com um resultado positivo real (so testamos ate
     * agora "nao encontrado"). Se nao vier, o campo fica vazio e so o CNS e usado, sem
     * quebrar nada - o operador continua tendo que digitar o CPF.
     *
     * @param nascimentoFormatado dd/mm/aaaa (mesmo formato usado no resto da classe)
     */
    public ConsultaCnsDto consultarPorNome(String nomePaciente, String nascimentoFormatado, String nomeMae, String unidade) {
        try {
            String[] partes = nascimentoFormatado.split("/");
            if (partes.length != 3) {
                return ConsultaCnsDto.naoEncontrado(List.of("Data de nascimento em formato inesperado."));
            }
            String dataAaaammdd = partes[2] + partes[1] + partes[0];
            String nomeMaeConsulta = nomeMae == null || nomeMae.isBlank() ? null : nomeMae.trim();

            CadsusPdqRespostaDto resposta = cadsusPdqClienteService.consultar(unidade,
                    new CadsusPdqConsultaDto(null, null, nomePaciente, nomeMaeConsulta, dataAaaammdd, null));
            if (!resposta.sucesso() || resposta.pacientes().isEmpty()) {
                return ConsultaCnsDto.naoEncontrado(List.of(resposta.mensagem()));
            }
            String cns = CadsusPdqClienteService.extrairCnsDoCorpoBruto(resposta.corpoBruto()).orElse(null);
            if (cns == null || !CnsUtils.valido(cns)) {
                return ConsultaCnsDto.naoEncontrado(List.of("CADSUS respondeu, mas sem um CNS reconhecivel."));
            }
            // Best-effort: se o CPF vier junto no mesmo registro, aproveita para auto-preencher.
            String cpfEncontrado = CadsusPdqClienteService.extrairCpfDoCorpoBruto(resposta.corpoBruto())
                    .filter(CpfUtils::valido)
                    .orElse("");
            return new ConsultaCnsDto(true, cns, nomePaciente, cpfEncontrado, nascimentoFormatado, List.of());
        } catch (Exception e) {
            return ConsultaCnsDto.naoEncontrado(List.of("Falha ao consultar o CADSUS oficial: " + e.getMessage()));
        }
    }

    /**
     * Tenta a consulta oficial via CPF (livingSubjectId, OID 2.16.840.1.113883.13.237 -
     * confirmado em 19/09/2026 contra o CADSUS real). CPF sozinho e uma busca valida por la
     * (diferente de Nome, que exige um segundo dado junto).
     *
     * @return o DTO pronto se achou um CNS valido; null para qualquer outro caso (cai no
     *         scraping). Nunca lanca excecao - erro aqui e so mais um motivo de fallback.
     */
    private ConsultaCnsDto tentarApiOficial(String cpf, String unidade) {
        try {
            CadsusPdqRespostaDto resposta = cadsusPdqClienteService.consultar(unidade,
                    new CadsusPdqConsultaDto(null, cpf.replaceAll("\\D", ""), null, null, null, null));
            if (!resposta.sucesso() || resposta.pacientes().isEmpty()) {
                return null;
            }
            // Schema do bloco de sucesso (patientPerson) ainda nao foi validado com um
            // resultado positivo real - por isso a extracao do CNS e feita direto no corpo
            // bruto, procurando o identificador pelo OID do CNS no CADSUS, em vez de confiar
            // no parser best-effort de CadsusPdqClienteService#parsearPacientesXml.
            String cns = CadsusPdqClienteService.extrairCnsDoCorpoBruto(resposta.corpoBruto()).orElse(null);
            if (cns == null || !CnsUtils.valido(cns)) {
                return null;
            }
            Map<String, Object> paciente = resposta.pacientes().get(0);
            String nome = String.valueOf(paciente.getOrDefault("given", "")).trim();
            return new ConsultaCnsDto(true, cns, nome, cpf, "", List.of());
        } catch (Exception e) {
            LOG.warn("Consulta oficial PDQ falhou, caindo para o scraping do CNES: " + e.getMessage());
            return null;
        }
    }

    /**
     * O CPF pertence a alguem marcado como OBITO na tabela pacientes?
     *
     * Erro aqui nao derruba a consulta: a /recepcao tambem barra o preenchimento
     * pelo pacienteespecial que ja carregou em tela, e travar o auto-preenchimento
     * de todo mundo por causa de uma query quebrada seria pior que o que se evita.
     */
    private boolean obitoRegistrado(String cpf) {
        try {
            return pacientesRepository.existeObitoPorCpf(cpf);
        } catch (Exception e) {
            LOG.warn("Nao foi possivel verificar obito antes da consulta de CNS: " + e.getMessage());
            return false;
        }
    }

    private ConsultaCnsDto executar(String cpf, String nascimento) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSegundos))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        Map<String, String> cookies = new LinkedHashMap<>();

        // Etapa 1 - formulario inicial: token _csrf + cookie de sessao.
        HttpResponse<String> inicial = client.send(
                HttpRequest.newBuilder(URI.create(urlCnes))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept", "text/html")
                        .timeout(Duration.ofSeconds(timeoutSegundos))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        coletarCookies(inicial, cookies);

        Matcher csrf = CSRF.matcher(inicial.body());
        if (!csrf.find()) {
            throw new IllegalStateException("Token _csrf nao encontrado na pagina do CNES.");
        }

        // Etapa 2 - POST real, com o token e o cookie da mesma sessao.
        String corpo = "cpf=" + URLEncoder.encode(cpf, StandardCharsets.UTF_8)
                + "&dataNascimento=" + URLEncoder.encode(nascimento, StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(csrf.group(1), StandardCharsets.UTF_8);

        HttpResponse<String> resposta = client.send(
                HttpRequest.newBuilder(URI.create(urlCnes))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "text/html")
                        .header("Cookie", serializar(cookies))
                        .header("Referer", urlCnes)
                        .timeout(Duration.ofSeconds(timeoutSegundos))
                        .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        coletarCookies(resposta, cookies);

        String destino = resposta.headers().firstValue("location").orElse(null);
        String html = resposta.body();

        // Etapa 3 - POST-redirect-GET: so a pagina seguinte traz CNS e Nome.
        if (destino != null && !destino.isBlank()) {
            URI alvo = URI.create(urlCnes).resolve(destino);
            if ("http".equalsIgnoreCase(alvo.getScheme())) {
                alvo = URI.create("https://" + alvo.getRawAuthority()
                        + alvo.getRawPath()
                        + (alvo.getRawQuery() == null ? "" : "?" + alvo.getRawQuery()));
            }

            HttpResponse<String> etapa = client.send(
                    HttpRequest.newBuilder(alvo)
                            .header("User-Agent", "Mozilla/5.0")
                            .header("Accept", "text/html")
                            .header("Cookie", serializar(cookies))
                            .header("Referer", urlCnes)
                            .timeout(Duration.ofSeconds(timeoutSegundos))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            coletarCookies(etapa, cookies);
            html = etapa.body();
        }

        List<String> mensagens = extrairMensagens(html);
        Map<String, String> dados = extrairCamposTravados(html);

        String cns = CnsUtils.somenteDigitos(dados.getOrDefault("cns", ""));
        boolean encontrado = destino != null && mensagens.isEmpty() && !cns.isBlank();

        if (!encontrado) {
            return ConsultaCnsDto.naoEncontrado(mensagens.isEmpty()
                    ? List.of("CPF nao localizado no cadastro do CNES para esta data de nascimento.")
                    : mensagens);
        }

        // O CNS vem do proprio DATASUS, mas ainda passa pelo modulo 11: se o numero
        // nao fecha, e sinal de que o layout mudou e o campo lido nao era o CNS.
        if (!CnsUtils.valido(cns)) {
            LOG.warn("CNES devolveu um CNS que nao passa na validacao: " + CnsUtils.motivoInvalido(cns));
            return ConsultaCnsDto.naoEncontrado(
                    List.of("Retorno do CNES nao reconhecido - informe o CNS manualmente."));
        }

        return new ConsultaCnsDto(
                true,
                cns,
                dados.getOrDefault("nome", "").trim(),
                dados.getOrDefault("cpf", cpf),
                dados.getOrDefault("dataNascimento", nascimento),
                List.of());
    }

    private static void coletarCookies(HttpResponse<?> resposta, Map<String, String> jar) {
        for (String bruto : resposta.headers().allValues("set-cookie")) {
            if (bruto == null || bruto.isBlank()) {
                continue;
            }
            String par = bruto.split(";", 2)[0];
            int idx = par.indexOf('=');
            if (idx > 0) {
                jar.put(par.substring(0, idx).trim(), par.substring(idx + 1).trim());
            }
        }
    }

    private static String serializar(Map<String, String> jar) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : jar.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    static List<String> extrairMensagens(String html) {
        List<String> mensagens = new ArrayList<>();
        Matcher bloco = BLOCO_ERRO.matcher(html);
        if (!bloco.find()) {
            return mensagens;
        }
        Matcher item = ITEM_ERRO.matcher(bloco.group(1));
        while (item.find()) {
            String texto = TAG.matcher(item.group(1)).replaceAll("").trim();
            if (!texto.isEmpty()) {
                mensagens.add(texto);
            }
        }
        return mensagens;
    }

    /** Os input readonly sao o retorno da consulta: cpf, dataNascimento, cns, nome. */
    static Map<String, String> extrairCamposTravados(String html) {
        Map<String, String> campos = new LinkedHashMap<>();
        Matcher input = INPUT.matcher(html);
        while (input.find()) {
            String attrs = input.group(1);
            if (!READONLY.matcher(attrs).find()) {
                continue;
            }
            Matcher nome = ATTR_NAME.matcher(attrs);
            if (!nome.find()) {
                continue;
            }
            Matcher valor = ATTR_VALUE.matcher(attrs);
            campos.put(nome.group(1), valor.find() ? valor.group(1) : "");
        }
        return campos;
    }

    /** O formulario do CNES espera o CPF mascarado, como o operador digita. */
    static String formatarCpf(String bruto) {
        String d = bruto == null ? "" : bruto.replaceAll("\\D", "");
        if (d.length() != 11) {
            return null;
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    /**
     * Normaliza para dd/mm/aaaa as tres formas de data que circulam no sistema.
     *
     * dd/mm/aaaa e o que a /recepcao digita; aaaa-mm-dd e o que vem de campo ISO; e
     * aaaa/mm/dd e o que a propria /recepcao GRAVA em pacientes.nascimento
     * (Cad_pacientes formata para yyyy/MM/dd antes de enviar). Faltava justamente o
     * terceiro - e e o unico que o faturamento le, porque ele pega a data do cadastro
     * em vez da tela.
     *
     * O estrago era silencioso: "1968/03/13" era lido como dia 19 / mes 68 e a data
     * caia fora (o paciente contava como "nascimento invalido" e nem chegava a ser
     * consultado), enquanto "2005/06/12" virava 20/05/0612 - data que existe, porem de
     * outra pessoa - e o CADSUS respondia "nao localizado". Nos dois casos o paciente
     * voltava para a lista de CNS pendente com CPF e nascimento certos em tela, que e
     * exatamente o "na recepcao acha e aqui nao" relatado pelo operador.
     */
    static String formatarData(String bruto) {
        String valor = bruto == null ? "" : bruto.trim();
        if (valor.isEmpty()) {
            return null;
        }
        // Timestamp de banco ("1968-03-13 00:00:00", com T ou com espaco): so a data importa.
        valor = valor.split("[T ]", 2)[0];

        Matcher anoPrimeiro = DATA_ANO_PRIMEIRO.matcher(valor);
        if (anoPrimeiro.matches()) {
            return montarData(anoPrimeiro.group(3), anoPrimeiro.group(2), anoPrimeiro.group(1));
        }
        Matcher diaPrimeiro = DATA_DIA_PRIMEIRO.matcher(valor);
        if (diaPrimeiro.matches()) {
            return montarData(diaPrimeiro.group(1), diaPrimeiro.group(2), diaPrimeiro.group(3));
        }

        // Sem separador sobra a ambiguidade, e ela se resolve sozinha: nao existe mes
        // 68, entao "19680313" so pode ser aaaammdd. Dia primeiro tem preferencia por
        // ser o formato que o operador digita.
        String d = valor.replaceAll("\\D", "");
        if (d.length() != 8) {
            return null;
        }
        String comoDiaPrimeiro = montarData(d.substring(0, 2), d.substring(2, 4), d.substring(4));
        return comoDiaPrimeiro != null
                ? comoDiaPrimeiro
                : montarData(d.substring(6), d.substring(4, 6), d.substring(0, 4));
    }

    /** Monta dd/mm/aaaa apenas quando os tres numeros formam uma data que existe. */
    private static String montarData(String dia, String mes, String ano) {
        try {
            LocalDate data = LocalDate.of(Integer.parseInt(ano), Integer.parseInt(mes), Integer.parseInt(dia));
            return String.format("%02d/%02d/%04d", data.getDayOfMonth(), data.getMonthValue(), data.getYear());
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }
}

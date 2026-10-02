package br.com.iv.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.dto.CadsusPdqConsultaDto;
import br.com.iv.dto.CadsusPdqRespostaDto;
import br.com.iv.entity.IntegracaoSusCredencial;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Cliente da API oficial PDQ CADSUS/CNS do DATASUS (Manual de Integracao PDQ CNS v2.1).
 *
 * Reaproveita o certificado digital ICP-Brasil (PFX/A1) e a senha ja salvos - e
 * criptografados - em {@link IntegracaoSusCredencial} pela tela Integracao SUS. Nao
 * existe segredo novo aqui: e a chamada real que hoje falta nessa infraestrutura
 * (ver o comentario "Chamada externa ao DATASUS/RNDS ainda nao ativada" em
 * IntegracaoSusCredencialService#testar).
 *
 * Fluxo (secoes 3.3 e 5 do manual):
 *   1. Autenticar com o certificado via Two-way SSL no EHR-Auth -> token JWT (30 min);
 *   2. Reusar o token em todas as chamadas seguintes ao CADSUS Service ate expirar;
 *   3. So autenticar de novo quando o token expirar (cache em memoria por unidade+ambiente).
 *
 * Nota sobre o formato da consulta: o manual documenta o GET de token com exemplo
 * completo, mas NAO traz o schema do corpo de POST/resposta do PDQSupplierJWT - so
 * os nomes dos parametros aceitos (CNS, CPF, Nome, Nome da mae, NIS). O corpo do
 * POST abaixo usa esses mesmos nomes; se o DATASUS exigir um envelope diferente em
 * homologacao, o corpoBruto devolvido em CadsusPdqRespostaDto (inclusive em erro)
 * mostra a mensagem exata para ajuste, sem exigir instrumentacao nova.
 */
@ApplicationScoped
public class CadsusPdqClienteService {

    private static final Logger LOG = Logger.getLogger(CadsusPdqClienteService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Schema do bloco de sucesso (patientPerson) do PRPA_IN201306UV02 ainda nao foi
    // validado com um resultado positivo real - por isso o CNS e extraido direto do corpo
    // bruto, procurando o identificador pelo OID do CNS no CADSUS, em vez de depender do
    // parser best-effort de #parsearPacientesXml. Compartilhado entre ConsultaCnsService
    // (recepcao/faturamento via CPF) e FaturamentoProducaoService (RN via nome+nascimento).
    private static final java.util.regex.Pattern ID_CNS_CADSUS = java.util.regex.Pattern
            .compile("<id root=\"2\\.16\\.840\\.1\\.113883\\.13\\.236\" extension=\"(\\d{15})\"");

    public static java.util.Optional<String> extrairCnsDoCorpoBruto(String corpoBruto) {
        if (corpoBruto == null) {
            return java.util.Optional.empty();
        }
        var matcher = ID_CNS_CADSUS.matcher(corpoBruto);
        return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }

    // Mesmo raciocinio do CNS, trocando o OID: se o CADSUS devolver o CPF vinculado ao
    // registro (patientPerson/asOtherIDs), esse identificador aparece com o OID
    // 2.16.840.1.113883.13.237 - o mesmo usado como criterio de busca "Query By CPF" no
    // kit oficial do DATASUS. AINDA NAO CONFIRMADO com um resultado positivo real: so
    // testamos ate agora com "nao encontrado" (queryResponseCode NF). Por isso o
    // auto-preenchimento do CPF e best-effort - se o CADSUS nao devolver, o campo
    // simplesmente fica vazio, sem quebrar o preenchimento do CNS.
    private static final java.util.regex.Pattern ID_CPF_CADSUS = java.util.regex.Pattern
            .compile("<id root=\"2\\.16\\.840\\.1\\.113883\\.13\\.237\" extension=\"(\\d{11})\"");

    public static java.util.Optional<String> extrairCpfDoCorpoBruto(String corpoBruto) {
        if (corpoBruto == null) {
            return java.util.Optional.empty();
        }
        var matcher = ID_CPF_CADSUS.matcher(corpoBruto);
        return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }

    @Inject
    IntegracaoSusCredencialService credencialService;

    @Inject
    IntegracaoSusCryptoService cryptoService;

    @ConfigProperty(name = "ghbam.cadsus.pdq.token-url.homologacao")
    String tokenUrlHomologacao;

    @ConfigProperty(name = "ghbam.cadsus.pdq.token-url.producao")
    String tokenUrlProducao;

    @ConfigProperty(name = "ghbam.rnds.token-url.homologacao")
    String rndsTokenUrlHomologacao;

    @ConfigProperty(name = "ghbam.rnds.token-url.producao")
    String rndsTokenUrlProducao;

    @ConfigProperty(name = "ghbam.cadsus.pdq.consulta-url.homologacao")
    String consultaUrlHomologacao;

    @ConfigProperty(name = "ghbam.cadsus.pdq.consulta-url.producao")
    String consultaUrlProducao;

    @ConfigProperty(name = "ghbam.cadsus.pdq.timeout.seconds", defaultValue = "20")
    int timeoutSegundos;

    @ConfigProperty(name = "ghbam.cadsus.pdq.token.margem-seguranca.minutos", defaultValue = "2")
    int margemSegurancaMinutos;

    // Token e o "ticket" recomendado pelo manual: uma entrada por unidade+ambiente,
    // reutilizada ate 2 min antes dos 30 min de validade oficial.
    private final Map<String, TokenCache> cacheToken = new ConcurrentHashMap<>();

    private record TokenCache(String token, Instant expiraEm) {
    }

    /** Usado pelo botao "Testar conexao" da tela Integracao SUS - gera um token real. */
    public Map<String, Object> testarConexao(String unidade) {
        try {
            IntegracaoSusCredencial credencial = credencialObrigatoria(unidade);
            obterToken(credencial, true);
            return Map.of(
                    "ok", true,
                    "status", "CONECTADO",
                    "mensagem", "Certificado valido - token gerado com sucesso junto ao EHR-Auth do DATASUS.");
        } catch (IllegalStateException e) {
            return Map.of("ok", false, "status", "FALHA_CONEXAO", "mensagem", e.getMessage());
        }
    }

    /** Consulta um paciente no CADSUS pela API oficial PDQ, usando a credencial da unidade. */
    public CadsusPdqRespostaDto consultar(String unidade, CadsusPdqConsultaDto filtro) {
        if (filtro == null || !filtro.possuiAoMenosUmParametro()) {
            return CadsusPdqRespostaDto.falha("Informe ao menos um parametro de busca (CNS, CPF, Nome, Nome da mae ou NIS).");
        }
        // Regra de negocio real do DATASUS (nao documentada no manual, confirmada em teste):
        // buscar so por nome e recusado - precisa vir com Nome da mae ou Data de nascimento junto.
        if (filtro.buscaPorNomeIncompleta()) {
            return CadsusPdqRespostaDto.falha(
                    "Para buscar por Nome, informe tambem a Data de nascimento ou o Nome da mae (exigencia do DATASUS).");
        }

        IntegracaoSusCredencial credencial;
        try {
            credencial = credencialObrigatoria(unidade);
        } catch (IllegalStateException e) {
            return CadsusPdqRespostaDto.falha(e.getMessage());
        }

        String token;
        try {
            token = obterToken(credencial, false);
        } catch (IllegalStateException e) {
            return CadsusPdqRespostaDto.falha("Falha ao autenticar no DATASUS: " + e.getMessage());
        }

        try {
            return executarConsulta(credencial, token, filtro);
        } catch (Exception e) {
            LOG.warn("Falha na consulta PDQ CADSUS (unidade " + unidade + "): " + e.getMessage());
            return CadsusPdqRespostaDto.falha("Nao foi possivel consultar o CADSUS agora: " + e.getMessage());
        }
    }

    /** Token + contexto TLS do certificado da unidade, para o envio FHIR a RNDS (FhirDispatchService). */
    public record AcessoRnds(String token, SSLContext sslContext) {
    }

    // Mesmo certificado da tela Integracao SUS, mas o token da RNDS sai de outro path do
    // EHR-Auth (o do CADSUS e o do barramento OSB). O ambiente vem do destino FHIR, nao
    // da credencial, para o token casar com o endpoint EHR configurado na tela.
    public AcessoRnds acessoRnds(String unidade, boolean producao) {
        IntegracaoSusCredencial credencial = credencialObrigatoria(unidade);
        String urlToken = producao ? rndsTokenUrlProducao : rndsTokenUrlHomologacao;
        return new AcessoRnds(obterToken(credencial, urlToken, false), montarSslContext(credencial));
    }

    private IntegracaoSusCredencial credencialObrigatoria(String unidade) {
        IntegracaoSusCredencial credencial = credencialService.buscarEntidadeAtiva(unidade)
                .orElseThrow(() -> new IllegalStateException("Nenhuma credencial SUS ativa configurada para esta unidade."));
        if (vazio(credencial.getCertificadoPfxCriptografado())) {
            throw new IllegalStateException(
                    "Certificado digital (PFX/A1) nao cadastrado para esta unidade - a API do CNS exige certificado ICP-Brasil.");
        }
        if (vazio(credencial.getCertificadoSenhaCriptografada())) {
            throw new IllegalStateException("Senha do certificado digital nao cadastrada para esta unidade.");
        }
        return credencial;
    }

    private String obterToken(IntegracaoSusCredencial credencial, boolean forcarRenovacao) {
        return obterToken(credencial, ehProducao(credencial) ? tokenUrlProducao : tokenUrlHomologacao, forcarRenovacao);
    }

    private String obterToken(IntegracaoSusCredencial credencial, String urlToken, boolean forcarRenovacao) {
        String chave = credencial.getUnidade() + "|" + urlToken;
        if (!forcarRenovacao) {
            TokenCache emCache = cacheToken.get(chave);
            if (emCache != null && emCache.expiraEm().isAfter(Instant.now())) {
                return emCache.token();
            }
        }
        String tokenNovo = gerarTokenNovo(credencial, urlToken);
        int validadeMinutos = Math.max(1, 30 - margemSegurancaMinutos);
        cacheToken.put(chave, new TokenCache(tokenNovo, Instant.now().plus(Duration.ofMinutes(validadeMinutos))));
        return tokenNovo;
    }

    private String gerarTokenNovo(IntegracaoSusCredencial credencial, String urlToken) {
        SSLContext sslContext = montarSslContext(credencial);

        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(timeoutSegundos))
                .build();

        try {
            HttpResponse<String> resposta = client.send(
                    HttpRequest.newBuilder(URI.create(urlToken))
                            .header("Accept", "application/json")
                            .timeout(Duration.ofSeconds(timeoutSegundos))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resposta.statusCode() != 200) {
                throw new IllegalStateException("EHR-Auth respondeu HTTP " + resposta.statusCode() + ": " + resumir(resposta.body()));
            }

            JsonNode raiz = OBJECT_MAPPER.readTree(resposta.body());
            JsonNode accessToken = raiz.get("access_token");
            if (accessToken == null || accessToken.asText().isBlank()) {
                throw new IllegalStateException("Resposta do EHR-Auth nao trouxe access_token.");
            }
            return accessToken.asText();
        } catch (IOException e) {
            throw new IllegalStateException("Falha de rede ao gerar token no EHR-Auth: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Geracao de token interrompida.", e);
        }
    }

    private CadsusPdqRespostaDto executarConsulta(IntegracaoSusCredencial credencial, String token, CadsusPdqConsultaDto filtro)
            throws IOException, InterruptedException {
        SSLContext sslContext = montarSslContext(credencial);
        String urlConsulta = ehProducao(credencial) ? consultaUrlProducao : consultaUrlHomologacao;

        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(timeoutSegundos))
                .build();

        // CONFIRMADO em teste real (homologacao, 19/09/2026), em duas etapas:
        //
        // 1) O servico roda atras de um Oracle Service Bus e e SOAP 1.2 puro -
        //    "Unsupported Content-Type: application/json. Supported ones are:
        //    [application/soap+xml]". O manual chama o servico de "RESTful" mas isso nao
        //    se sustenta na pratica.
        //
        // 2) O WSDL publico (?wsdl) responde 403 Access Denied, mas a estrutura real foi
        //    reconstituida cruzando duas fontes publicas: um client Java de exemplo do
        //    proprio CADSUS (github.com/flaviociaware/cwEnsaiosWeb, PDQClientExample.java,
        //    que usa PDQSupplierPortType/PDQSupplierService do namespace
        //    ihe.iti.pdqv3._2007) e o WSDL de referencia do perfil IHE ITI-47/PDQ v3
        //    (projeto IPF/openhealthhub). Isso e o padrao internacional "Patient
        //    Demographics Query HL7 V3": operacao PRPA_IN201305UV02 (Patient Registry Find
        //    Candidates Query), resposta PRPA_IN201306UV02, SOAPAction
        //    "urn:hl7-org:v3:PRPA_IN201305UV02". Testado e CONFIRMADO que o Oracle Service
        //    Bus reconhece essa operacao: o erro evoluiu de "OSB-386103: The request could
        //    not be mapped to one of the operations exposed by the service" (chute anterior)
        //    para uma mensagem de validacao de negocio em portugues sobre o campo Nome - ou
        //    seja, a estrutura do envelope e a operacao estao corretas.
        //
        // 3) RESOLVIDO (19/09/2026): o formato exato do campo Nome foi confirmado baixando o
        //    kit oficial de exemplos do DATASUS - ihe-client-example-files.zip e IHE-MS.zip
        //    (projeto SoapUI com requests prontos), ambos publicados publicamente em
        //    datasus.saude.gov.br/wp-content/uploads/2019/12/ e referenciados no documento
        //    oficial "Especificacao Tecnica para Integracao PDQ com o Cartao Nacional de
        //    Saude v5" (mesma pasta). O nome vai INTEIRO dentro de <given> (sem separar
        //    family), com o atributo use="L" - o oposto do padrao IHE generico (given+family
        //    separados) que era o meu chute anterior e que o DATASUS sempre recusava com
        //    "Preencha o campo Nome do Paciente...". Alem disso, a regra de negocio real do
        //    DATASUS exige nome + (nomeMae OU dataNascimento) - nome sozinho e recusado com
        //    outra mensagem ("Ao informar o Nome do Paciente, e obrigatorio informar pelo
        //    menos mais um dos seguintes dados..."). CPF entra como livingSubjectId com OID
        //    2.16.840.1.113883.13.237 (tambem confirmado no request "Query By CPF" do
        //    IHE-MS.zip). Teste real (nome + data de nascimento fictícia) devolveu HTTP 200
        //    com PRPA_IN201306UV02 e queryResponseCode "NF" (not found) - ou seja, a consulta
        //    E ACEITA de ponta a ponta; falta so testar com dados que batem de verdade.
        String corpoSoap = montarEnvelopePrpaIn201305(filtro);

        HttpResponse<String> resposta = client.send(
                HttpRequest.newBuilder(URI.create(urlConsulta))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/soap+xml; charset=UTF-8; action=\"urn:hl7-org:v3:PRPA_IN201305UV02\"")
                        .timeout(Duration.ofSeconds(timeoutSegundos))
                        .POST(HttpRequest.BodyPublishers.ofString(corpoSoap, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        String body = resposta.body();
        if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) {
            String mensagemFalha = extrairMensagemSoapFault(body)
                    .map(texto -> "CADSUS respondeu HTTP " + resposta.statusCode() + ": " + texto)
                    .orElse("CADSUS respondeu HTTP " + resposta.statusCode() + ".");
            return CadsusPdqRespostaDto.falhaHttp(resposta.statusCode(), mensagemFalha, body);
        }

        return interpretarRespostaPrpaIn201306(resposta.statusCode(), body);
    }

    /**
     * Resposta de sucesso e a mensagem HL7v3 PRPA_IN201306UV02 (XML) - confirmado em teste
     * real (19/09/2026): queryAck/queryResponseCode "NF" = nao encontrado (sem
     * subject/registrationEvent no corpo), "OK"/"AA" = encontrado. O schema completo do
     * bloco subject/registrationEvent/subject1/patient/patientPerson (nome, CNS, data de
     * nascimento etc devolvidos pelo CADSUS) ainda nao foi validado com um resultado real -
     * so testamos ate agora com dados que nao batem (queryResponseCode "NF"). Por isso o
     * parser abaixo e propositalmente tolerante: extrai o que reconhece e sempre devolve o
     * corpoBruto, para ajuste assim que um resultado positivo real chegar.
     */
    private CadsusPdqRespostaDto interpretarRespostaPrpaIn201306(int statusHttp, String body) {
        if (vazio(body)) {
            return CadsusPdqRespostaDto.falha("CADSUS respondeu vazio.");
        }

        String queryResponseCode = extrairAtributo(body, "queryResponseCode", "code");
        String totalTexto = extrairAtributo(body, "resultTotalQuantity", "value");
        int total = totalTexto != null && totalTexto.matches("\\d+") ? Integer.parseInt(totalTexto) : -1;

        if ("NF".equalsIgnoreCase(queryResponseCode) || total == 0) {
            return new CadsusPdqRespostaDto(true, statusHttp,
                    "Nenhum paciente encontrado para os parametros informados.", body, List.of());
        }

        List<Map<String, Object>> pacientes = parsearPacientesXml(body);
        String mensagem = pacientes.isEmpty()
                // Resposta nao veio vazia (queryResponseCode != NF) mas tambem nao reconhecemos
                // nenhum patientPerson - schema do bloco de sucesso ainda nao mapeado.
                ? "CADSUS retornou resultado, mas o formato ainda nao foi totalmente mapeado - confira o corpo bruto."
                : "Consulta realizada com sucesso.";
        return new CadsusPdqRespostaDto(true, statusHttp, mensagem, body, pacientes);
    }

    /** Primeiro valor de um atributo dentro de uma tag - parsing tolerante via regex, sem exigir DOM. */
    private String extrairAtributo(String xml, String tag, String atributo) {
        var matcher = java.util.regex.Pattern
                .compile("<[^:>\\s]*:?" + tag + "\\b[^>]*\\b" + atributo + "=\"([^\"]*)\"").matcher(xml);
        return matcher.find() ? matcher.group(1) : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parsearPacientesXml(String body) {
        // Melhor esforco: procura por patientPerson (padrao HL7v3) e devolve os campos como
        // texto bruto por tag. Ainda nao validado contra um resultado real do DATASUS.
        List<Map<String, Object>> pacientes = new java.util.ArrayList<>();
        var blocoMatcher = java.util.regex.Pattern
                .compile("<[^:>\\s]*:?patientPerson\\b[^>]*>(.*?)</[^:>\\s]*:?patientPerson>",
                        java.util.regex.Pattern.DOTALL)
                .matcher(body);
        while (blocoMatcher.find()) {
            String bloco = blocoMatcher.group(1);
            Map<String, Object> paciente = new java.util.LinkedHashMap<>();
            for (String tag : List.of("given", "family", "birthTime", "administrativeGenderCode", "mothersMaidenName")) {
                var valorMatcher = java.util.regex.Pattern
                        .compile("<[^:>\\s]*:?" + tag + "\\b[^>]*>([^<]*)<").matcher(bloco);
                if (valorMatcher.find() && !valorMatcher.group(1).isBlank()) {
                    paciente.put(tag, valorMatcher.group(1).trim());
                }
                var atributoMatcher = java.util.regex.Pattern
                        .compile("<[^:>\\s]*:?" + tag + "\\b[^>]*\\bvalue=\"([^\"]*)\"").matcher(bloco);
                if (atributoMatcher.find()) {
                    paciente.putIfAbsent(tag, atributoMatcher.group(1));
                }
            }
            // CNS/CPF do MESMO bloco (nao do corpo inteiro): com mais de um resultado na
            // resposta, extrair do body todo misturaria o identificador de uma pessoa com os
            // dados demograficos de outra.
            var cnsBloco = ID_CNS_CADSUS.matcher(bloco);
            if (cnsBloco.find()) {
                paciente.put("cns", cnsBloco.group(1));
            }
            var cpfBloco = ID_CPF_CADSUS.matcher(bloco);
            if (cpfBloco.find()) {
                paciente.put("cpf", cpfBloco.group(1));
            }
            if (!paciente.isEmpty()) {
                pacientes.add(paciente);
            }
        }
        return pacientes;
    }

    /**
     * Monta o envelope SOAP 1.2 com a mensagem HL7v3 PRPA_IN201305UV02 (padrao IHE PDQ v3 /
     * ITI-47) - ver o comentario extenso em {@link #executarConsulta} para as fontes e o que
     * ja foi confirmado contra o ambiente real do DATASUS.
     */
    private String montarEnvelopePrpaIn201305(CadsusPdqConsultaDto filtro) {
        String agora = java.time.ZonedDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String idMensagem = java.util.UUID.randomUUID().toString();
        String queryId = java.util.UUID.randomUUID().toString();

        // OID do proprio GHBAM como "sender" do sistema de origem (nao e um identificador
        // oficial cadastrado no DATASUS - ainda nao ha confirmacao de que precisa ser um OID
        // registrado; o exemplo publico do CADSUS usa qualquer OID de teste aqui).
        String oidRemetente = "1.2.76.114001.1";
        // OID do CADSUS como "receiver" - mesmo valor usado no exemplo publico do CADSUS
        // (PDQClientExample.java) para o servidor do DATASUS.
        String oidCadsus = "2.16.840.1.113883.13.240";
        // OIDs de identificador dentro do CADSUS, confirmados em 19/09/2026 contra o
        // ambiente real de homologacao usando o projeto SoapUI oficial do DATASUS
        // (arquivo IHE-MS.zip, publicado em datasus.saude.gov.br/wp-content/uploads/2019/12/):
        // requests "Query By CNS" e "Query By CPF" prontos, ambos testados com sucesso (HTTP
        // 200, resposta PRPA_IN201306UV02).
        String oidCns = "2.16.840.1.113883.13.236";
        String oidCpf = "2.16.840.1.113883.13.237";

        StringBuilder parametros = new StringBuilder();
        // Nome: CONFIRMADO que vai inteiro dentro de <given>, sem separar family - o
        // <value> precisa do atributo use="L" (Legal name). Isso e o oposto do padrao IHE
        // generico (given+family separados) que eu tinha chutado antes e que o DATASUS
        // sempre recusava com "Preencha o campo Nome do Paciente...". Fonte: mesmo
        // IHE-MS.zip, request "Query By Nome".
        if (!vazio(filtro.nome())) {
            parametros.append("<livingSubjectName>")
                    .append("<value use=\"L\"><given>").append(escaparXml(filtro.nome().trim())).append("</given></value>")
                    .append("<semanticsText>LivingSubject.Given</semanticsText>")
                    .append("</livingSubjectName>");
        }
        if (!vazio(filtro.cns())) {
            parametros.append("<livingSubjectId>")
                    .append("<value root=\"").append(oidCns).append("\" extension=\"")
                    .append(escaparXml(filtro.cns().trim())).append("\"/>")
                    .append("<semanticsText>LivingSubject.id</semanticsText>")
                    .append("</livingSubjectId>");
        }
        if (!vazio(filtro.cpf())) {
            parametros.append("<livingSubjectId>")
                    .append("<value root=\"").append(oidCpf).append("\" extension=\"")
                    .append(escaparXml(filtro.cpf().trim())).append("\"/>")
                    .append("<semanticsText>LivingSubject.id</semanticsText>")
                    .append("</livingSubjectId>");
        }
        // Nome da mae: elemento proprio "mothersMaidenName", texto direto no value (sem
        // sub-elemento given/family) - confirmado no mesmo request de exemplo oficial.
        if (!vazio(filtro.nomeMae())) {
            parametros.append("<mothersMaidenName>")
                    .append("<value use=\"L\">").append(escaparXml(filtro.nomeMae().trim())).append("</value>")
                    .append("<semanticsText>mothersMaidenName</semanticsText>")
                    .append("</mothersMaidenName>");
        }
        // Data de nascimento no formato AAAAMMDD, como no exemplo oficial - confirmado
        // funcionando junto com o nome (HTTP 200, resposta PRPA_IN201306UV02).
        if (!vazio(filtro.dataNascimento())) {
            parametros.append("<livingSubjectBirthTime>")
                    .append("<value value=\"").append(escaparXml(filtro.dataNascimento().trim())).append("\"/>")
                    .append("<semanticsText>LivingSubject.birthTime</semanticsText>")
                    .append("</livingSubjectBirthTime>");
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap:Header/>"
                + "<soap:Body>"
                + "<PRPA_IN201305UV02 xmlns=\"urn:hl7-org:v3\" ITSVersion=\"XML_1.0\">"
                + "<id root=\"" + oidRemetente + "\" extension=\"" + idMensagem + "\"/>"
                + "<creationTime value=\"" + agora + "\"/>"
                + "<interactionId root=\"2.16.840.1.113883.1.6\" extension=\"PRPA_IN201305UV02\"/>"
                // "T" = teste, usado no exemplo de referencia do IPF/IHE. Confirmado que o OSB
                // aceita a mensagem com esse valor (nao rejeitou por processingCode).
                + "<processingCode code=\"T\"/>"
                + "<processingModeCode code=\"T\"/>"
                + "<acceptAckCode code=\"AL\"/>"
                + "<receiver typeCode=\"RCV\"><device classCode=\"DEV\" determinerCode=\"INSTANCE\">"
                + "<id root=\"" + oidCadsus + "\"/></device></receiver>"
                + "<sender typeCode=\"SND\"><device classCode=\"DEV\" determinerCode=\"INSTANCE\">"
                + "<id root=\"" + oidRemetente + "\"/></device></sender>"
                + "<controlActProcess classCode=\"CACT\" moodCode=\"EVN\">"
                + "<code code=\"PRPA_TE201305UV02\" codeSystem=\"2.16.840.1.113883.1.6\"/>"
                + "<queryByParameter>"
                + "<queryId root=\"" + oidRemetente + "\" extension=\"" + queryId + "\"/>"
                + "<statusCode code=\"new\"/>"
                + "<responseModalityCode code=\"R\"/>"
                + "<responsePriorityCode code=\"I\"/>"
                + "<parameterList>" + parametros + "</parameterList>"
                + "</queryByParameter>"
                + "</controlActProcess>"
                + "</PRPA_IN201305UV02>"
                + "</soap:Body>"
                + "</soap:Envelope>";
    }

    private String escaparXml(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /** SOAP Fault vem como XML, nunca como o JSON que OBJECT_MAPPER entende - parsing simples por regex. */
    private java.util.Optional<String> extrairMensagemSoapFault(String body) {
        if (vazio(body)) {
            return java.util.Optional.empty();
        }
        var matcher = java.util.regex.Pattern.compile("<[^:>]*:?Text[^>]*>([^<]+)</[^:>]*:?Text>").matcher(body);
        return matcher.find() ? java.util.Optional.of(matcher.group(1).trim()) : java.util.Optional.empty();
    }

    private SSLContext montarSslContext(IntegracaoSusCredencial credencial) {
        try {
            byte[] pfxBytes = Base64.getDecoder().decode(cryptoService.decrypt(credencial.getCertificadoPfxCriptografado()));
            char[] senha = cryptoService.decrypt(credencial.getCertificadoSenhaCriptografada()).toCharArray();

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(pfxBytes), senha);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, senha);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new IllegalStateException("Certificado digital invalido ou senha incorreta: " + e.getMessage(), e);
        }
    }

    private boolean ehProducao(IntegracaoSusCredencial credencial) {
        return "PRODUCAO".equalsIgnoreCase(credencial.getAmbiente());
    }

    private boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String resumir(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.length() > 300 ? texto.substring(0, 300) + "..." : texto;
    }
}

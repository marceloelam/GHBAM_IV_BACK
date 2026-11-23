package br.com.iv.aps;

import static br.com.iv.aps.ApsConfiguracaoService.num;
import static br.com.iv.aps.ApsConfiguracaoService.str;
import static br.com.iv.aps.ApsDb.p;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.util.AcessoIa;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Apoio de IA do G-PEC. Regras da casa (iguais as do GHBAM):
 * - a IA nunca diagnostica nem grava/transmite ficha sozinha: so sugere, o profissional decide;
 * - nada de identificador do cidadao no prompt (sequencias longas de digitos sao mascaradas);
 * - todo codigo sugerido e conferido contra o catalogo (CIAP-2 do LEDI e s_cid) antes de aparecer;
 * - toda chamada passa pelo AcessoIa, que ja registra em ia_chamada_auditoria (chamador = ApsIaService).
 * Rejeicoes conhecidas do Centralizador sao explicadas por regra fixa, sem IA.
 */
@ApplicationScoped
public class ApsIaService {

    private static final Logger LOG = Logger.getLogger(ApsIaService.class);
    private static final String CADASTRO_CIDADAO = "CADASTRO_CIDADAO";

    @Inject
    ApsDb db;
    @Inject
    ApsFichaService fichas;
    @Inject
    ApsReferenciaService refs;
    @Inject
    ApsCidadaoService cidadaos;
    @Inject
    AcessoIa ia;
    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "ghbam.aps.ia.habilitada", defaultValue = "true")
    boolean habilitada;

    public Map<String, Object> status() {
        return Map.of("habilitada", habilitada);
    }

    // ------------------------------------------------------------------ rejeicao do Centralizador

    private record Regra(Pattern padrao, String titulo, String explicacao, String comoCorrigir, String acao) {
    }

    private static Regra regra(String regex, String titulo, String explicacao, String comoCorrigir, String acao) {
        return new Regra(Pattern.compile(regex), titulo, explicacao, comoCorrigir, acao);
    }

    /** Casadas contra o texto sem acento e em minusculas. A primeira regra que casar com cada trecho vence. */
    private static final List<Regra> REGRAS = List.of(
            // Antes da regra de CPF: "Preencha ... o CPF/CNPJ e o nome do responsavel (remetente ...)" nao e do cidadao.
            regra("remetente|responsavel|configuracao\\s*>\\s*instalacao|cpf/cnpj", "Dados da instalação incompletos",
                    "Falta preencher quem envia os dados ao Ministério (CPF/CNPJ e nome do responsável pela instalação do G-PEC). "
                            + "Nenhum registro sai enquanto isso estiver em branco; não é problema deste registro.",
                    "Peça à coordenação para preencher em Configuração → Instalação. Depois abra este registro e clique "
                            + "em \"Salvar correção\" sem alterar nada: ele volta para a fila de envio.", null),
            regra("cpf.*profissional|profissional.*cpf", "CPF do profissional",
                    "O CPF do profissional que assinou o registro foi recusado.",
                    "Confira o CPF no cadastro do servidor (RH) e depois use \"Salvar correção\" ou registre de novo.", null),
            regra("cpf", "CPF do cidadão inválido",
                    "O CPF que estava no cadastro do cidadão quando o registro foi feito não passa na conta do dígito verificador. "
                            + "Por causa disso o Ministério recusa o registro inteiro.",
                    "Abra o cadastro do cidadão e corrija o CPF (se ele não tiver CPF, apague o campo e informe o CNS). "
                            + "Depois volte aqui e clique em \"Atualizar com o cadastro\".",
                    CADASTRO_CIDADAO),
            regra("cns.*profissional|profissional.*cns", "CNS do profissional",
                    "O CNS do profissional foi recusado.",
                    "Corrija o CNS no cadastro do servidor e reenvie.", null),
            regra("cns", "CNS do cidadão inválido",
                    "O Cartão Nacional de Saúde do cidadão não é válido.",
                    "Abra o cadastro do cidadão, confira o CNS (15 dígitos) ou informe o CPF. "
                            + "Depois volte aqui e clique em \"Atualizar com o cadastro\".",
                    CADASTRO_CIDADAO),
            regra("duplicad|ja (foi )?recebid|ja existe|uuid", "Registro já recebido",
                    "O Centralizador já tem um registro com este mesmo identificador; normalmente é um envio repetido.",
                    "Confira no PEC se o registro chegou. Se chegou, cancele este aqui com o motivo \"já recebido\".", null),
            regra("cbo", "Ocupação (CBO) não permitida",
                    "A ocupação do profissional não pode registrar este tipo de ficha no e-SUS.",
                    "Confira a lotação usada (Configuração → Lotações). Se estiver errada, cancele e registre de novo "
                            + "com a lotação certa.", null),
            regra("\\bine\\b|equipe", "Equipe (INE)",
                    "O código da equipe (INE) foi recusado ou não pertence a esta unidade.",
                    "Confira o INE da lotação em Configuração → Equipes.", null),
            regra("cnes|estabelecimento", "Unidade (CNES)",
                    "O CNES da unidade foi recusado.",
                    "Confira o CNES em Configuração → Unidade.", null),
            regra("nascimento", "Data de nascimento",
                    "A data de nascimento do cidadão está ausente, no futuro ou incompatível com o registro.",
                    "Corrija a data de nascimento no cadastro do cidadão, cancele este registro e registre de novo.", null),
            regra("credencia|\\b401\\b|\\b403\\b|autentica|senha", "Credencial do Centralizador",
                    "O Centralizador recusou o usuário/senha da integração.",
                    "Gere ou confira a credencial no PEC Centralizador e atualize em Configuração → Integrações.", null),
            regra("contrachave|instalacao", "Instalação do PEC",
                    "A contra-chave ou o identificador da instalação não confere com o Centralizador.",
                    "Confira a contra-chave em Configuração → Integrações.", null),
            regra("obrigatori|nao informad|ausente|required", "Campo obrigatório faltando",
                    "Falta um campo que o e-SUS exige neste tipo de registro.",
                    "Abra o registro, preencha o campo indicado e use \"Salvar correção\".", null));

    private static final Pattern CAMINHO = Pattern.compile("[A-Za-z]+(?:\\[\\d+\\])?(?:\\.[A-Za-z]+(?:\\[\\d+\\])?)+");

    public Map<String, Object> explicarRejeicao(Long idFicha, String unidade) {
        Map<String, Object> f = fichaDaUnidade(idFicha, unidade);
        if (f == null) {
            return null;
        }
        String erro = str(f.get("ultimo_erro"));
        int tipo = ((Number) f.get("tipo")).intValue();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("idFicha", idFicha);
        out.put("registro", ApsReferenciaService.nomeTipo(tipo));
        out.put("status", f.get("status"));
        out.put("erroOriginal", erro);
        Map<String, Object> cidadao = cidadao(num(f.get("id_cidadao")));
        if (cidadao != null) {
            out.put("idCidadao", cidadao.get("id"));
            out.put("cidadao", cidadao.get("nome"));
        }
        List<Map<String, Object>> itens = new ArrayList<>();
        out.put("fonte", "REGRA");
        if (erro != null) {
            Set<String> titulos = new LinkedHashSet<>();
            for (String trecho : trechos(erro)) {
                String t = semAcento(trecho);
                for (Regra r : REGRAS) {
                    if (!r.padrao().matcher(t).find()) {
                        continue;
                    }
                    if (titulos.add(r.titulo())) {
                        Map<String, Object> it = new LinkedHashMap<>();
                        it.put("titulo", r.titulo());
                        it.put("explicacao", r.explicacao());
                        it.put("comoCorrigir", r.comoCorrigir());
                        Matcher m = CAMINHO.matcher(trecho);
                        if (m.find()) {
                            it.put("campo", m.group());
                        }
                        if (r.acao() != null && cidadao != null) {
                            it.put("acao", r.acao());
                            complementarComCadastro(it, r, cidadao);
                        }
                        itens.add(it);
                    }
                    break;
                }
            }
            if (itens.isEmpty() && habilitada) {
                Map<String, Object> viaIa = explicarComIa(erro, tipo);
                if (viaIa != null) {
                    itens.add(viaIa);
                    out.put("fonte", "IA");
                }
            }
            if (itens.isEmpty()) {
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("titulo", "Rejeição sem explicação automática");
                it.put("explicacao", "Ainda não reconhecemos esta mensagem do Centralizador.");
                it.put("comoCorrigir", "Abra o conteúdo do registro, procure o campo citado na mensagem, corrija e use "
                        + "\"Salvar correção\". Se não for possível, cancele e registre de novo.");
                itens.add(it);
            }
        }
        out.put("itens", itens);
        out.put("podeAtualizarComCadastro", cidadao != null
                && itens.stream().anyMatch(i -> CADASTRO_CIDADAO.equals(i.get("acao")))
                && ("REJEITADA".equals(f.get("status")) || "PRONTA".equals(f.get("status"))));
        return out;
    }

    /** O que esta no cadastro AGORA: diz se o problema ja foi corrigido ou se e cidadao de teste. */
    private static void complementarComCadastro(Map<String, Object> it, Regra r, Map<String, Object> c) {
        if (!r.titulo().startsWith("CPF")) {
            return;
        }
        String cpf = ApsValidacao.digitos(str(c.get("cpf")));
        String cns = c.get("cns") == null ? "" : String.valueOf(c.get("cns")).replaceAll("\\D", "");
        if ((cpf == null || cpf.isEmpty()) && cns.isEmpty()) {
            it.put("situacaoCadastro", "O cadastro está sem CPF e sem CNS: informe um dos dois no cadastro do cidadão.");
        } else if (cpf == null || cpf.isEmpty()) {
            it.put("situacaoCadastro", "O cadastro agora está sem CPF; o registro pode ser atualizado com o CNS.");
            it.put("cadastroCorrigido", true);
        } else if (ApsValidacao.cpfValido(cpf)) {
            it.put("situacaoCadastro", "O CPF do cadastro já foi corrigido. Falta só atualizar este registro.");
            it.put("cadastroCorrigido", true);
        } else if (cpf.chars().distinct().count() == 1) {
            it.put("situacaoCadastro", "O cadastro tem um CPF de números repetidos: parece cidadão fictício/de teste. "
                    + "Se for teste, cancele este registro.");
        } else {
            it.put("situacaoCadastro", "O CPF do cadastro continua inválido.");
        }
    }

    private static List<String> trechos(String erro) {
        String corpo = erro.replaceFirst("(?i)^HTTP \\d{3}:\\s*", "").replaceFirst("(?i)^campos inv\\S*lidos:\\s*", "");
        List<String> out = new ArrayList<>();
        for (String s : corpo.split("[;\\n]|,\\s+(?=[a-zA-Z]+(?:\\[\\d+\\])?\\.)")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        if (out.isEmpty()) {
            out.add(erro);
        }
        return out;
    }

    private Map<String, Object> explicarComIa(String erro, int tipo) {
        String sistema = """
                Voce ajuda servidores de uma UBS a entender por que o PEC e-SUS APS Centralizador recusou um registro LEDI.
                Explique em portugues do Brasil simples, sem jargao tecnico, para quem nao e de TI. Nao invente regras do
                e-SUS: se a mensagem nao permitir saber a causa, diga isso. Responda SOMENTE com JSON no formato
                {"titulo":"...","explicacao":"...","comoCorrigir":"..."} (no maximo 2 frases em cada campo).""";
        String usuario = "Tipo de registro: " + ApsReferenciaService.nomeTipo(tipo) + "\nMensagem do Centralizador: " + mascarar(erro);
        try {
            JsonNode n = json(ia.enviarMensagens(mensagens(sistema, usuario)));
            if (n == null || !n.hasNonNull("explicacao")) {
                return null;
            }
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("titulo", n.path("titulo").asText("Rejeição do Centralizador"));
            it.put("explicacao", n.path("explicacao").asText());
            it.put("comoCorrigir", n.path("comoCorrigir").asText(""));
            it.put("geradoPorIa", true);
            return it;
        } catch (Exception e) {
            LOG.warn("APS IA: falha ao explicar rejeicao: " + e.getMessage());
            return null;
        }
    }

    /**
     * Depois de corrigido o cadastro, reescreve CPF/CNS invalidos do cidadao no registro (mantendo UUID e
     * header) e o devolve a fila. Nao usa IA.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> atualizarComCadastro(Long idFicha, String unidade, String matricula) {
        Map<String, Object> f = fichaDaUnidade(idFicha, unidade);
        if (f == null) {
            return null;
        }
        Map<String, Object> c = cidadao(num(f.get("id_cidadao")));
        if (c == null) {
            throw new IllegalArgumentException("Este registro não está ligado a um cidadão cadastrado.");
        }
        ApsCdsService.exigirDocumentoValido(c);
        String cpf = ApsValidacao.digitos(str(c.get("cpf")));
        String cns = c.get("cns") == null ? null : String.valueOf(c.get("cns")).replaceAll("\\D", "");
        cpf = cpf == null || cpf.isEmpty() ? null : cpf;
        cns = cns == null || cns.isEmpty() ? null : cns;
        if (cpf == null && cns == null) {
            throw new IllegalArgumentException("O cadastro de " + c.get("nome") + " está sem CPF e sem CNS. Informe um dos dois.");
        }
        if (!(f.get("dados") instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Registro sem conteúdo para atualizar.");
        }
        Map<String, Object> dados = mapper.convertValue(f.get("dados"), Map.class);
        int trocas = reescreverIdentificacao(dados, cpf, cns);
        if (trocas == 0) {
            throw new IllegalArgumentException("Não encontrei CPF/CNS inválido do cidadão neste registro. "
                    + "Use \"Salvar correção\" no conteúdo do registro.");
        }
        return fichas.corrigir(idFicha, dados, matricula);
    }

    /** Completa CPF/CNS do cidadao deste registro pelo CADSUS (sem IA; ver ApsCidadaoService.completarDoCadsus). */
    public Map<String, Object> buscarNoCadsus(Long idFicha, String unidade) {
        Map<String, Object> f = fichaDaUnidade(idFicha, unidade);
        if (f == null) {
            return null;
        }
        Long idCidadao = num(f.get("id_cidadao"));
        if (idCidadao == null) {
            throw new IllegalArgumentException("Este registro não está ligado a um cidadão cadastrado.");
        }
        return cidadaos.completarDoCadsus(idCidadao, unidade);
    }

    /** Troca so documento invalido do cidadao (cpfCidadao/cnsCidadao/cns) em qualquer nivel do registro. */
    @SuppressWarnings("unchecked")
    private static int reescreverIdentificacao(Object no, String cpf, String cns) {
        int trocas = 0;
        if (no instanceof Map<?, ?> mm) {
            Map<String, Object> m = (Map<String, Object>) mm;
            Object atualCpf = m.get("cpfCidadao");
            if (atualCpf != null && !ApsValidacao.cpfValido(String.valueOf(atualCpf))) {
                m.remove("cpfCidadao");
                if (cpf != null) {
                    m.put("cpfCidadao", cpf);
                } else if (!m.containsKey("cns") && !m.containsKey("cnsCidadao")) {
                    m.put("cnsCidadao", cns);
                }
                trocas++;
            }
            for (String k : List.of("cnsCidadao", "cns")) {
                Object v = m.get(k);
                if (v != null && !ApsValidacao.cnsValido(String.valueOf(v))) {
                    if (cns != null) {
                        m.put(k, cns);
                    } else {
                        m.remove(k);
                        m.putIfAbsent("cpfCidadao", cpf);
                    }
                    trocas++;
                }
            }
            for (Object v : new ArrayList<>(m.values())) {
                trocas += reescreverIdentificacao(v, cpf, cns);
            }
        } else if (no instanceof List<?> l) {
            for (Object v : l) {
                trocas += reescreverIdentificacao(v, cpf, cns);
            }
        }
        return trocas;
    }

    // ------------------------------------------------------------------ sugestao de CIAP-2 / CID-10

    /**
     * body: { subjetivo, objetivo, avaliacao, plano, sexo, idade }. Devolve ate 5 sugestoes ja conferidas
     * no catalogo. Codifica o que esta escrito; nao propoe diagnostico novo.
     */
    public Map<String, Object> sugerirCodigos(Map<String, Object> body) {
        if (!habilitada) {
            throw new IllegalStateException("Apoio de IA do G-PEC desligado (ghbam.aps.ia.habilitada=false).");
        }
        StringBuilder texto = new StringBuilder();
        for (String[] b : new String[][] { { "subjetivo", "Subjetivo" }, { "objetivo", "Objetivo" },
                { "avaliacao", "Avaliacao" }, { "plano", "Plano" } }) {
            String v = str(body.get(b[0]));
            if (v != null && !v.isBlank()) {
                texto.append(b[1]).append(": ").append(v.trim()).append('\n');
            }
        }
        if (texto.toString().replaceAll("\\s", "").length() < 25) {
            throw new IllegalArgumentException("Escreva o relato ou a avaliação antes de pedir sugestões de código.");
        }
        String contexto = "";
        if (str(body.get("sexo")) != null) {
            contexto += "Sexo: " + str(body.get("sexo")) + ". ";
        }
        if (num(body.get("idade")) != null) {
            contexto += "Idade: " + num(body.get("idade")) + " anos.";
        }
        // A IA escolhe so entre candidatos do catalogo pre-selecionados pelas palavras do proprio texto:
        // pedir o codigo "de memoria" trazia codigo existente mas errado (cefaleia tensional -> N89 enxaqueca).
        List<Candidato> candidatos = candidatos(texto.toString());
        if (candidatos.isEmpty()) {
            return Map.of("sugestoes", List.of(), "descartadas", 0);
        }
        StringBuilder lista = new StringBuilder();
        for (Candidato cd : candidatos) {
            lista.append(cd.ciap()).append(" | ").append(cd.descricao());
            for (String[] cid : cd.cids()) {
                lista.append(" | ").append(cid[0]).append(' ').append(cid[1]);
            }
            lista.append('\n');
        }
        String sistema = """
                Voce apoia a codificacao de atendimentos da Atencao Primaria (e-SUS APS). Recebe o registro SOAP e uma
                LISTA DE CANDIDATOS (CIAP-2 | descricao | CIDs relacionados). Indique ate 5 problemas/condicoes JA
                ESCRITOS no texto, escolhendo o CIAP-2 SOMENTE da lista e o CID-10 SOMENTE entre os CIDs daquela linha
                (ou vazio). Regras: a descricao do codigo tem de corresponder ao que esta escrito; nao faca diagnostico
                novo nem sugira hipotese que o profissional nao escreveu; sintoma isolado recebe o codigo do sintoma;
                se nenhum candidato servir para um problema, deixe-o de fora. Responda SOMENTE com JSON:
                {"sugestoes":[{"ciap":"K86","cid10":"I10","trecho":"palavras do texto que justificam"}]}.""";
        String usuario = contexto + "\nREGISTRO:\n" + mascarar(texto.toString()) + "\nCANDIDATOS:\n" + lista;
        JsonNode n;
        try {
            n = json(ia.enviarMensagens(mensagens(sistema, usuario)));
        } catch (Exception e) {
            LOG.warn("APS IA: falha ao sugerir codigos: " + e.getMessage());
            throw new IllegalStateException("A IA não respondeu agora. Tente de novo em instantes ou codifique pela busca.");
        }
        Map<String, Candidato> porCiap = new HashMap<>();
        candidatos.forEach(cd -> porCiap.put(cd.ciap(), cd));
        List<Map<String, Object>> sugestoes = new ArrayList<>();
        int descartadas = 0;
        Set<String> vistos = new LinkedHashSet<>();
        if (n != null && n.path("sugestoes").isArray()) {
            for (JsonNode s : n.path("sugestoes")) {
                String ciap = s.path("ciap").asText("").trim().toUpperCase();
                String cid = s.path("cid10").asText("").replace(".", "").trim().toUpperCase();
                Candidato cd = porCiap.get(ciap);
                if (cd == null) {
                    descartadas++;
                    continue;
                }
                String[] cidEscolhido = null;
                for (String[] c : cd.cids()) {
                    if (c[0].equalsIgnoreCase(cid)) {
                        cidEscolhido = c;
                    }
                }
                if (!cid.isEmpty() && cidEscolhido == null) {
                    descartadas++; // CID fora da linha do CIAP: fica so o CIAP
                }
                if (sugestoes.size() >= 5 || !vistos.add(ciap)) {
                    continue;
                }
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("ciap", ciap);
                it.put("ciapDescricao", cd.descricao());
                if (cidEscolhido != null) {
                    it.put("cid10", cidEscolhido[0]);
                    it.put("cidDescricao", cidEscolhido[1]);
                }
                it.put("trecho", s.path("trecho").asText(""));
                sugestoes.add(it);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sugestoes", sugestoes);
        out.put("descartadas", descartadas);
        return out;
    }

    /** Uma linha CIAP-2 do catalogo LEDI com os CIDs que o proprio catalogo relaciona a ela. */
    record Candidato(String ciap, String descricao, List<String[]> cids, Set<String> radicais) {
    }

    private static final Set<String> PALAVRAS_VAZIAS = Set.of("paciente", "refere", "relata", "queixa", "anos", "dias",
            "semana", "semanas", "meses", "para", "como", "pela", "pelo", "mais", "menos", "sem", "com", "desde", "esta",
            "este", "essa", "esse", "muito", "pouco", "uso", "usa", "faz", "fazer", "retorno", "orientacoes",
            "orientado", "nega", "outros", "outras", "especificad", "classificad", "parte", "provavel");

    private volatile List<Candidato> catalogo;

    @SuppressWarnings("unchecked")
    private List<Candidato> catalogo() {
        if (catalogo == null) {
            Map<String, String> desc = new LinkedHashMap<>();
            Map<String, List<String[]>> cids = new HashMap<>();
            if (refs.referencias().get("ciapCid") instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> r && r.get("ciap") != null) {
                        String c = String.valueOf(r.get("ciap")).trim().toUpperCase();
                        desc.putIfAbsent(c, String.valueOf(r.get("ciapDescricao")).trim());
                        Object cid = r.get("cid");
                        if (cid != null && !String.valueOf(cid).isBlank()) {
                            List<String[]> lc = cids.computeIfAbsent(c, k -> new ArrayList<>());
                            if (lc.size() < 4) {
                                lc.add(new String[] { String.valueOf(cid).trim().toUpperCase(),
                                        String.valueOf(r.get("cidDescricao")).trim() });
                            }
                        }
                    }
                }
            }
            List<Candidato> out = new ArrayList<>();
            desc.forEach((c, d) -> {
                List<String[]> lc = cids.getOrDefault(c, List.of());
                StringBuilder palavras = new StringBuilder(d);
                lc.forEach(x -> palavras.append(' ').append(x[1]));
                out.add(new Candidato(c, d, lc, radicais(palavras.toString())));
            });
            catalogo = out;
        }
        return catalogo;
    }

    /** Ate 40 linhas do catalogo que dividem radicais com o texto, as mais parecidas primeiro. */
    List<Candidato> candidatos(String texto) {
        Set<String> doTexto = radicais(texto);
        List<Map.Entry<Candidato, Integer>> pontuados = new ArrayList<>();
        for (Candidato cd : catalogo()) {
            int pontos = 0;
            for (String r : cd.radicais()) {
                if (doTexto.contains(r)) {
                    pontos++;
                }
            }
            if (pontos > 0) {
                pontuados.add(Map.entry(cd, pontos));
            }
        }
        pontuados.sort((a, b) -> b.getValue() - a.getValue());
        return pontuados.stream().limit(40).map(Map.Entry::getKey).toList();
    }

    /** Radical = 5 primeiras letras de cada palavra de 4+ letras (cefaleia/cefaleias, hipertensao/hipertenso). */
    static Set<String> radicais(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (String w : semAcento(s).split("[^a-z]+")) {
            if (w.length() >= 4 && !PALAVRAS_VAZIAS.contains(w)) {
                out.add(w.length() > 5 ? w.substring(0, 5) : w);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ apoio

    private Map<String, Object> fichaDaUnidade(Long id, String unidade) {
        Map<String, Object> f = fichas.detalhe(id);
        if (f == null) {
            return null;
        }
        String uf = str(f.get("unidade"));
        if (unidade != null && uf != null && !uf.equalsIgnoreCase(unidade)) {
            return null; // fora da unidade da sessao: mesmo tratamento de "nao encontrado"
        }
        return f;
    }

    private Map<String, Object> cidadao(Long id) {
        return id == null ? null : db.one("SELECT id, nome, cpf, cns FROM aps_cidadao WHERE id = :id", p("id", id));
    }

    private static List<Map<String, String>> mensagens(String sistema, String usuario) {
        List<Map<String, String>> l = new ArrayList<>();
        l.add(Map.of("role", "system", "content", sistema));
        l.add(Map.of("role", "user", "content", usuario));
        return l;
    }

    private JsonNode json(String resposta) {
        if (resposta == null) {
            return null;
        }
        int i = resposta.indexOf('{');
        int j = resposta.lastIndexOf('}');
        if (i < 0 || j <= i) {
            return null;
        }
        try {
            return mapper.readTree(resposta.substring(i, j + 1));
        } catch (Exception e) {
            LOG.debug("APS IA: resposta fora do formato JSON");
            return null;
        }
    }

    /** CPF, CNS, telefone e qualquer sequencia longa de digitos nao vao para o provedor de IA. */
    static String mascarar(String s) {
        return s == null ? null : s.replaceAll("\\d[\\d.\\-/ ]{8,}\\d", "[documento]");
    }

    private static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }
}

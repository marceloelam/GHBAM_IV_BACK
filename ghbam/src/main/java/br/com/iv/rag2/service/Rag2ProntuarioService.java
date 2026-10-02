package br.com.iv.rag2.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import br.com.iv.dto.LogDto;
import br.com.iv.entity.Bam;
import br.com.iv.entity.NovoInternado;
import br.com.iv.entity.Pacientes;
import br.com.iv.rag2.Rag2IaException;
import br.com.iv.rag2.Rag2NaoEncontradoException;
import br.com.iv.rag2.Rag2PermissaoException;
import br.com.iv.rag2.Rag2ValidacaoException;
import br.com.iv.rag2.dto.Rag2FonteDto;
import br.com.iv.rag2.dto.Rag2PacienteBuscaDto;
import br.com.iv.rag2.dto.Rag2PacienteBuscaResultadoDto;
import br.com.iv.rag2.dto.Rag2PerguntaDto;
import br.com.iv.rag2.dto.Rag2RespostaDto;
import br.com.iv.repository.BamRepository;
import br.com.iv.repository.NovoInternadoRepository;
import br.com.iv.repository.PacientesRepository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.LogService;
import br.com.iv.service.RagPacienteService;
import br.com.iv.util.AcessoIa;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * RAG2 PRONTUARIO: responde perguntas sobre o prontuario de um paciente especifico.
 * Namespace "rag2" para nao colidir com o RAG generico ja existente
 * (br.com.iv.util.RagResource / br.com.iv.service.RagService).
 */
@ApplicationScoped
public class Rag2ProntuarioService {

    private static final String AVISO_SEGURANCA =
        "As respostas s\u00e3o geradas com base nos dados registrados no prontu\u00e1rio e n\u00e3o substituem a avalia\u00e7\u00e3o do profissional de sa\u00fade.";

    private static final String MENSAGEM_NAO_ENCONTROU =
        "N\u00e3o encontrei essa informa\u00e7\u00e3o no prontu\u00e1rio do paciente.";

    private static final List<String> SECOES_PERMITIDAS_ADMINISTRATIVO =
        List.of("IDENTIFICACAO:", "TRIAGEM:", "REGISTRO CLINICO:");

    private static final Pattern PADRAO_BAM = Pattern.compile(
        "\\bbam\\s*(?:n(?:umero|[o.]*)\\s*)?[:#-]?\\s*(\\d{2,})",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PADRAO_PRONTUARIO = Pattern.compile(
        "\\bprontuario\\s*(?:n(?:umero|[o.]*)\\s*)?[:#-]?\\s*(\\d{2,})",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PADRAO_NUMERO_SOLTO = Pattern.compile("\\b(\\d{2,})\\b");

    private static final String SYSTEM_PROMPT = """
        Voce e o assistente de consulta ao prontuario eletronico do GHBAM IV.
        Responda somente com base no contexto do prontuario fornecido, priorizando o ultimo BAM ou o BAM explicitamente selecionado.

        Regras obrigatorias:
        - Nao invente informacoes que nao estejam no contexto.
        - Para situacao atual do paciente, use somente o ultimo BAM/BAM selecionado; historico antigo so pode ser citado como historico.
        - Nao altere classificacao de risco, sinais vitais, anamnese, evolucao, exames, leito ou condutas se o contexto nao registrar isso.
        - Nao forneca diagnostico novo; apenas relate o que ja esta registrado.
        - Nao substitua a avaliacao do profissional de saude responsavel.
        - Indique, sempre que possivel, de qual secao do prontuario veio a informacao.
        - Se o contexto nao tiver a informacao pedida, responda literalmente:
          "Nao encontrei essa informacao no prontuario do paciente."
        - Responda em portugues brasileiro, de forma direta e tecnica.
        """;

    public static String obterSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Inject
    PacientesRepository pacientesRepository;

    @Inject
    BamRepository bamRepository;

    @Inject
    NovoInternadoRepository novoInternadoRepository;

    @Inject
    RagPacienteService ragPacienteService;

    @Inject
    AcessoIa acessoIa;

    @Inject
    LogService logService;

    public Rag2PacienteBuscaResultadoDto buscarPacientes(String termo, String tipoBusca, int pagina, int limite) {
        if (termo == null || termo.isBlank()) {
            throw new Rag2ValidacaoException("Informe um termo de busca.");
        }
        String tipo = tipoBusca == null ? "" : tipoBusca.trim().toUpperCase();
        if (!tipo.equals("NOME") && !tipo.equals("BAM") && !tipo.equals("PRONTUARIO")) {
            throw new Rag2ValidacaoException("tipoBusca deve ser NOME, BAM ou PRONTUARIO.");
        }

        int paginaSegura = Math.max(pagina, 0);
        int limiteSeguro = limite <= 0 ? 20 : Math.min(limite, 100);

        List<Pacientes> encontrados;
        switch (tipo) {
            case "BAM": {
                Integer bam = parseInteiro(termo);
                encontrados = bam == null ? List.of() : pacientesRepository.localizarPeloBam(bam);
                break;
            }
            case "PRONTUARIO":
                encontrados = pacientesRepository.findByProntuario(termo.trim());
                break;
            default: {
                encontrados = pacientesRepository.buscarPorNomeInteligente(termo, 200);
                if (encontrados.isEmpty()) {
                    // o termo pode vir como frase ("como esta o paciente fulano"): tenta a busca tolerante
                    encontrados = pacientesRepository.buscarPorNomeEmFrase(termo, 200);
                }
            }
        }

        int total = encontrados.size();
        int inicio = Math.min(paginaSegura * limiteSeguro, total);
        int fim = Math.min(inicio + limiteSeguro, total);
        List<Rag2PacienteBuscaDto> itens = encontrados.subList(inicio, fim).stream()
            .map(this::montarPacienteBuscaDto)
            .collect(Collectors.toList());

        Rag2PacienteBuscaResultadoDto resultado = new Rag2PacienteBuscaResultadoDto();
        resultado.setItens(itens);
        resultado.setTotal(total);
        resultado.setPagina(paginaSegura);
        resultado.setLimite(limiteSeguro);
        return resultado;
    }

    public Rag2RespostaDto perguntar(Rag2PerguntaDto dto, AuthenticatedUser usuario) {
        if (dto.getPergunta() == null || dto.getPergunta().isBlank()) {
            throw new Rag2ValidacaoException("A pergunta nao pode ser vazia.");
        }
        String pergunta = dto.getPergunta().trim();
        Pacientes paciente = resolverPaciente(dto);

        if (paciente == null) {
            paciente = resolverPacientePelaPergunta(pergunta);
        }
        if (paciente == null) {
            registrarAuditoria(usuario, dto, null, "paciente-nao-encontrado");
            throw new Rag2NaoEncontradoException("Paciente nao encontrado.");
        }
        int idPaciente = paciente.getIdpaciente();

        if (!temPermissao(usuario)) {
            registrarAuditoria(usuario, dto, idPaciente, "negado");
            throw new Rag2PermissaoException("Perfil sem permissao para consultar o RAG Prontuario.");
        }

        boolean acessoClinico = temAcessoClinico(usuario);
        Integer bamSolicitado = dto.getBam() != null && dto.getBam() > 0 ? dto.getBam() : null;
        String contexto;
        try {
            contexto = ragPacienteService.buscarDadosPaciente(idPaciente, bamSolicitado, usuario.unidade());
        } catch (Exception e) {
            contexto = montarContextoBasicoPaciente(paciente);
            registrarAuditoria(usuario, dto, idPaciente, "contexto-parcial:" + e.getClass().getSimpleName());
        }
        if (!acessoClinico) {
            contexto = restringirContextoParaAdministrativo(contexto);
        }

        Rag2RespostaDto resposta = new Rag2RespostaDto();
        resposta.setDataHoraConsulta(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        resposta.setAvisos(List.of(AVISO_SEGURANCA));
        resposta.setPaciente(montarPacienteBuscaDto(paciente));

        if (contexto == null || contexto.isBlank()) {
            resposta.setResposta(MENSAGEM_NAO_ENCONTROU);
            resposta.setFontes(List.of());
            resposta.setModo("IA");
            registrarAuditoria(usuario, dto, idPaciente, "sem-dados");
            return resposta;
        }

        if (detectarModoCompleto(pergunta)) {
            resposta.setResposta(gerarRelatorioConsolidadoProntuario(contexto, paciente, pergunta, usuario, dto, idPaciente, bamSolicitado));
            resposta.setFontes(extrairFontes(contexto));
            resposta.setModo("RELATORIO_CONSOLIDADO_PRONTUARIO");
            return resposta;
        }

        List<Map<String, String>> mensagens = new ArrayList<>();
        mensagens.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        StringBuilder userContent = new StringBuilder();
        userContent.append("## CONTEXTO DO PRONTUARIO\n\n").append(contexto).append("\n\n");
        userContent.append("## PERGUNTA\n\n").append(pergunta);
        mensagens.add(Map.of("role", "user", "content", userContent.toString()));

        String textoResposta;
        try {
            br.com.iv.iaauditoria.IaAuditoriaContexto.registrar("RAG2_PRONTUARIO", idPaciente, bamSolicitado, pergunta);
            textoResposta = acessoIa.enviarMensagens(mensagens);
        } catch (Exception e) {
            registrarAuditoria(usuario, dto, idPaciente, "erro-ia:" + e.getMessage());
            throw new Rag2IaException("Falha ao consultar a IA: " + e.getMessage(), e);
        }

        resposta.setResposta(textoResposta);
        resposta.setFontes(extrairFontes(contexto));
        resposta.setModo("IA");

        registrarAuditoria(usuario, dto, idPaciente, "ok");
        return resposta;
    }

    private String gerarRelatorioConsolidadoProntuario(
        String contexto,
        Pacientes paciente,
        String pergunta,
        AuthenticatedUser usuario,
        Rag2PerguntaDto dto,
        Integer idPaciente,
        Integer bamSolicitado
    ) {
        String registros = formatarRegistrosOriginais(contexto);
        String emitidoEm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        String emissor = usuario != null && usuario.nome() != null && !usuario.nome().isBlank()
            ? usuario.nome()
            : safe(dto.getNomeUsuario());
        String matricula = usuario != null && usuario.matricula() != null && !usuario.matricula().isBlank()
            ? usuario.matricula()
            : safe(dto.getMatriculaUsuario());
        String unidade = usuario != null && usuario.unidade() != null ? usuario.unidade() : "";

        StringBuilder relatorio = new StringBuilder();
        relatorio.append("PRONTUARIO DO PACIENTE\n");
        relatorio.append("Finalidade: consolidacao documental dos registros localizados para o BAM selecionado.\n");
        relatorio.append("Regra de busca: por nome ou numero de prontuario, usa o ultimo BAM do paciente; por BAM, usa o BAM informado.\n");
        relatorio.append("Aviso: este documento nao substitui o prontuario original, as assinaturas dos registros nem a validacao institucional quando exigida.\n\n");

        relatorio.append("DADOS DE EMISSAO\n");
        relatorio.append(descricaoSecao("DADOS DE EMISSAO")).append("\n");
        relatorio.append("- Emitido em: ").append(emitidoEm).append("\n");
        relatorio.append("- Emitido por: ").append(safe(emissor)).append("\n");
        relatorio.append("- Matricula: ").append(safe(matricula)).append("\n");
        relatorio.append("- Unidade: ").append(safe(unidade)).append("\n");
        relatorio.append("- Escopo: ").append(bamSolicitado != null ? "BAM especifico " + bamSolicitado : "ultimo BAM do paciente").append("\n");
        relatorio.append("- Pergunta/origem da solicitacao: ").append(safe(pergunta)).append("\n\n");

        relatorio.append("DADOS DO PACIENTE\n");
        relatorio.append(descricaoSecao("DADOS DO PACIENTE")).append("\n");
        relatorio.append("- Nome: ").append(paciente != null ? safe(paciente.getNome()) : "").append("\n");
        relatorio.append("- ID paciente: ").append(idPaciente != null ? idPaciente : 0).append("\n");
        relatorio.append("- Prontuario: ").append(paciente != null ? safe(paciente.getProntuario()) : "").append("\n");
        relatorio.append("- Nascimento: ").append(paciente != null ? safe(paciente.getNascimento()) : "").append("\n");
        relatorio.append("- Sexo: ").append(paciente != null ? safe(paciente.getSexo()) : "").append("\n\n");

        relatorio.append("FONTES CONSOLIDADAS\n");
        relatorio.append(descricaoSecao("FONTES CONSOLIDADAS")).append("\n");
        List<Rag2FonteDto> fontes = extrairFontes(contexto);
        if (fontes.isEmpty()) {
            relatorio.append("- Nenhuma fonte estruturada localizada.\n");
        } else {
            for (Rag2FonteDto fonte : fontes) {
                relatorio.append("- ").append(fonte.getTipo()).append(" - ").append(fonte.getReferencia());
                if (fonte.getTrecho() != null && !fonte.getTrecho().isBlank()) {
                    relatorio.append(" | \"").append(fonte.getTrecho()).append("\"");
                }
                relatorio.append("\n");
            }
        }
        relatorio.append("\nREGISTROS CONSOLIDADOS\n");
        relatorio.append(descricaoSecao("REGISTROS CONSOLIDADOS")).append("\n");
        relatorio.append(registros).append("\n\n");

        String conteudoSemHash = relatorio.toString().trim();
        String hash = sha256(conteudoSemHash);
        registrarAuditoria(usuario, dto, idPaciente, "ok-relatorio-consolidado:" + hash);

        return conteudoSemHash
            + "\n\nCONTROLE DO DOCUMENTO\n"
            + descricaoSecao("CONTROLE DO DOCUMENTO") + "\n"
            + "- Codigo de verificacao SHA-256: " + hash + "\n"
            + "- Para uso externo, conferir este relatorio com os registros originais e assinaturas existentes no sistema.\n\n"
            + gerarAvaliacaoDocumental(contexto);
    }

    private String formatarRegistrosOriginais(String contexto) {
        String texto = contexto == null ? "" : contexto.trim();
        if (texto.isBlank()) {
            return MENSAGEM_NAO_ENCONTROU;
        }
        return texto
            .replace("--- DADOS DO PACIENTE ---", "IDENTIFICACAO ORIGINAL")
            .replaceAll("(?m)^([A-Z0-9 ()]+):\\s*$", "\n$1\n")
            .replaceAll("(?m)^  ", "- ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim()
            .lines()
            .map(linha -> {
                String trim = linha.trim();
                if (ehTituloSecao(trim)) {
                    String descricao = descricaoSecao(trim);
                    return descricao.isBlank() ? trim : trim + "\n" + descricao;
                }
                return linha;
            })
            .collect(Collectors.joining("\n"));
    }

    private boolean ehTituloSecao(String linha) {
        return linha != null
            && !linha.isBlank()
            && linha.equals(linha.toUpperCase())
            && linha.matches("[A-Z0-9 /()\\-]+");
    }

    private String descricaoSecao(String secao) {
        String s = secao == null ? "" : normalizarTexto(secao);
        if (s.contains("identificacao")) {
            return "Explicacao: dados cadastrais usados para confirmar quem e o paciente e evitar troca de prontuario.";
        }
        if (s.contains("dados de emissao")) {
            return "Explicacao: informa quem gerou este documento, quando foi gerado e qual foi a pergunta original.";
        }
        if (s.contains("dados do paciente")) {
            return "Explicacao: resume os principais dados de identificacao do paciente no cadastro.";
        }
        if (s.contains("fontes")) {
            return "Explicacao: lista as partes do sistema que forneceram informacoes para este prontuario.";
        }
        if (s.contains("registros consolidados")) {
            return "Explicacao: reune os registros clinicos e operacionais encontrados, mantendo a origem por tipo de documento e BAM.";
        }
        if (s.contains("triagem")) {
            return "Explicacao: mostra a classificacao inicial, prioridade, setor e dados de entrada do atendimento.";
        }
        if (s.contains("periodo do atendimento")) {
            return "Explicacao: informa o BAM consultado, a data de entrada e a data de encerramento ou alta quando ela estiver registrada. As datas aparecem no formato dia-mes-ano.";
        }
        if (s.contains("profissionais envolvidos")) {
            return "Explicacao: identifica os profissionais localizados nos registros do BAM, incluindo nome e CRM quando essas informacoes estiverem gravadas no sistema.";
        }
        if (s.contains("diagnostico")) {
            return "Explicacao: mostra diagnosticos ou hipoteses registradas no atendimento, sem criar conclusoes novas.";
        }
        if (s.contains("anamnese")) {
            return "Explicacao: relato inicial da queixa, historia do problema atual e informacoes importantes contadas pelo paciente ou acompanhante.";
        }
        if (s.contains("comentario da avaliacao")) {
            return "Explicacao: observacoes complementares registradas durante a avaliacao do paciente. Ajuda a entender detalhes que nao ficaram nos campos padronizados.";
        }
        if (s.contains("exame fisico")) {
            return "Explicacao: achados observados pelo profissional no exame do paciente, como estado geral, sinais neurologicos, respiratorios, hidratacao e outros dados clinicos.";
        }
        if (s.contains("prescrico")) {
            return "Explicacao: medicamentos, cuidados, exames, orientacoes ou condutas prescritas pelo profissional no BAM indicado. Quando o item for medicamento, o sistema tenta explicar em linguagem clara usando o bulario interno, sem substituir a orientacao medica.";
        }
        if (s.contains("evolucoes medicas")) {
            return "Explicacao: acompanhamento medico ao longo do atendimento, com relato da evolucao do quadro, condutas tomadas e reavaliacoes registradas.";
        }
        if (s.contains("evolucao de enfermagem")) {
            return "Explicacao: acompanhamento feito pela enfermagem, incluindo observacoes assistenciais, intercorrencias, cuidados prestados e estado do paciente.";
        }
        if (s.contains("evolucoes de receituario")) {
            return "Explicacao: registros vinculados ao receituario ou a orientacoes de medicacao, quando foram documentados separadamente da evolucao principal.";
        }
        if (s.contains("evoluco")) {
            return "Explicacao: registros de acompanhamento feitos pelos profissionais durante a permanencia do paciente, em ordem conforme foram encontrados no sistema.";
        }
        if (s.contains("sinais vitais")) {
            return "Explicacao: medidas objetivas do estado do paciente, como pressao arterial, frequencia cardiaca, respiracao, temperatura, saturacao de oxigenio e peso.";
        }
        if (s.contains("exames laboratoriais")) {
            return "Explicacao: exames de laboratorio solicitados ou resultados encontrados para o BAM indicado. Quando houver resultado, conferir tambem o laudo original no modulo de laboratorio.";
        }
        if (s.contains("exames")) {
            return "Explicacao: exames solicitados ou resultados localizados no prontuario. Esta secao ajuda a confirmar quais investigacoes foram registradas no atendimento.";
        }
        if (s.contains("registro clinico")) {
            return "Explicacao: registros clinicos adicionais cadastrados em campos estruturados do prontuario, usados para complementar a linha do tempo do atendimento.";
        }
        if (s.contains("controle do documento")) {
            return "Explicacao: dados de conferencia do documento gerado, incluindo codigo de verificacao. Serve para rastrear o conteudo emitido e comparar com os registros originais.";
        }
        if (s.contains("avaliacao")) {
            return "Explicacao: leitura auxiliar sobre a completude documental; nao e laudo, pericia nem julgamento clinico.";
        }
        return "";
    }

    private String gerarAvaliacaoDocumental(String contexto) {
        String normalizado = normalizarTexto(contexto);
        List<String> ausencias = new ArrayList<>();
        if (!normalizado.contains("triagem")) ausencias.add("triagem/classificacao de risco");
        if (!normalizado.contains("exame fisico")) ausencias.add("exame fisico");
        if (!normalizado.contains("sinais vitais")) ausencias.add("sinais vitais recentes");
        if (!normalizado.contains("evoluco")) ausencias.add("evolucoes clinicas");
        if (!normalizado.contains("prescrico")) ausencias.add("prescricoes");
        if (!normalizado.contains("exames")) ausencias.add("exames/resultados");

        StringBuilder avaliacao = new StringBuilder();
        avaliacao.append("AVALIACAO DO ASSISTENTE - AUXILIAR E NAO PERICIAL\n");
        avaliacao.append(descricaoSecao("AVALIACAO DO ASSISTENTE")).append("\n");
        avaliacao.append("- O relatorio acima consolida informacoes localizadas no prontuario eletronico para facilitar leitura e conferencia.\n");
        avaliacao.append("- Para finalidade juridica, devem prevalecer os registros originais, suas datas, autores, assinaturas e trilhas de auditoria.\n");
        if (ausencias.isEmpty()) {
            avaliacao.append("- As principais secoes clinicas esperadas foram localizadas no material consolidado.\n");
        } else {
            avaliacao.append("- Nao foram localizadas, neste extrato, as seguintes informacoes que merecem conferencia: ")
                .append(String.join(", ", ausencias)).append(".\n");
        }
        avaliacao.append("- Esta avaliacao nao estabelece nexo causal, culpa, prognostico ou conduta medica; apenas aponta consistencia documental aparente.\n");
        return avaliacao.toString().trim();
    }

    private String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((valor == null ? "" : valor).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "hash-indisponivel";
        }
    }


    private Pacientes resolverPaciente(Rag2PerguntaDto dto) {
        if (dto.getIdPaciente() != null && dto.getIdPaciente() > 0) {
            return pacientesRepository.filtrarPorUnicoPacienteId(dto.getIdPaciente());
        }
        if (dto.getBam() == null || dto.getBam() <= 0) {
            return null;
        }
        List<Pacientes> encontrados = pacientesRepository.localizarPeloBam(dto.getBam());
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    private String montarContextoBasicoPaciente(Pacientes paciente) {
        if (paciente == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("--- DADOS DO PACIENTE ---\n\n");
        sb.append("IDENTIFICACAO:\n");
        sb.append("  Nome: ").append(safe(paciente.getNome())).append("\n");
        sb.append("  Idade: ").append(paciente.getIdade()).append(" anos\n");
        sb.append("  Sexo: ").append(safe(paciente.getSexo())).append("\n");
        sb.append("  Nascimento: ").append(safe(paciente.getNascimento())).append("\n");
        sb.append("  Mae: ").append(safe(paciente.getMae())).append("\n");
        sb.append("  Prontuario: ").append(safe(paciente.getProntuario())).append("\n\n");

        List<Bam> bams = bamRepository.filtrarPorIdDoPciente(paciente.getIdpaciente());
        if (!bams.isEmpty()) {
            sb.append("ATENDIMENTOS/BAMS LOCALIZADOS:\n");
            for (int i = 0; i < Math.min(bams.size(), 10); i++) {
                Bam bam = bams.get(i);
                sb.append("- BAM ").append(bam.getIdbam())
                    .append(" | Data: ").append(bam.getData() != null ? String.valueOf(bam.getData()) : "")
                    .append(" | Unidade: ").append(bam.getUnidade() != null ? bam.getUnidade() : "")
                    .append("\n");
            }
            sb.append("\n");
        }
        sb.append("OBSERVACAO:\n");
        sb.append("  Parte dos detalhes clinicos nao foi carregada por falha em uma fonte interna. ")
            .append("Os dados acima foram preservados para identificacao do paciente e auditoria.\n\n");
        return sb.toString();
    }

    private Pacientes resolverPacientePelaPergunta(String pergunta) {
        String normalizado = normalizarTexto(pergunta);

        Integer bam = extrairNumero(PADRAO_BAM, normalizado);
        if (bam != null) {
            List<Pacientes> porBam = pacientesRepository.localizarPeloBam(bam);
            if (!porBam.isEmpty()) {
                return porBam.get(0);
            }
        }

        Integer prontuario = extrairNumero(PADRAO_PRONTUARIO, normalizado);
        if (prontuario != null) {
            List<Pacientes> porProntuario = pacientesRepository.findByProntuario(String.valueOf(prontuario));
            if (!porProntuario.isEmpty()) {
                return porProntuario.get(0);
            }
        }

        Integer numeroSolto = extrairNumero(PADRAO_NUMERO_SOLTO, normalizado);
        if (numeroSolto != null) {
            Pacientes porNumero = resolverPacientePorNumero(String.valueOf(numeroSolto));
            if (porNumero != null) {
                return porNumero;
            }
        }

        String nomeCandidato = extrairNomeCandidato(pergunta);
        if (nomeCandidato.isBlank()) {
            return null;
        }

        List<Pacientes> porNome = buscarPacientesPorNome(nomeCandidato, 6);
        if (porNome.size() == 1) {
            return porNome.get(0);
        }
        if (porNome.size() > 1) {
            throw new Rag2ValidacaoException(
                "Encontrei mais de um paciente com esse nome. Selecione o paciente na lista ou informe BAM/prontuario.");
        }
        return null;
    }

    private Pacientes resolverPacientePorNumero(String numero) {
        try {
            int bamNum = Integer.parseInt(numero);
            List<Pacientes> porBam = pacientesRepository.localizarPeloBam(bamNum);
            if (!porBam.isEmpty()) {
                return porBam.get(0);
            }
        } catch (NumberFormatException ignorado) {
            // segue para a tentativa por numero de prontuario
        }
        List<Pacientes> porProntuario = pacientesRepository.findByProntuario(numero);
        return porProntuario.isEmpty() ? null : porProntuario.get(0);
    }

    private boolean detectarModoCompleto(String pergunta) {
        String normalizado = normalizarTexto(pergunta);
        if (normalizado.matches("\\d{2,}")) {
            return true;
        }
        boolean mencionaProntuario = normalizado.contains("prontuario");
        boolean mencionaCompleto = normalizado.contains("completo")
            || normalizado.contains("inteiro")
            || normalizado.contains("todo")
            || normalizado.contains("tudo sobre")
            || normalizado.contains("tudo do paciente")
            || normalizado.contains("historico completo");
        boolean pedeGeracao = normalizado.matches(".*\\b(gerar|gere|emitir|montar|fazer|criar|imprimir|mostrar|mostre|trazer|liste|listar)\\b.*");
        boolean mencionaBam = normalizado.matches(".*\\bbam\\b.*");
        boolean mencionaIdentificador = mencionaBam || PADRAO_PRONTUARIO.matcher(normalizado).find();
        return mencionaProntuario && (mencionaCompleto || pedeGeracao || mencionaIdentificador);
    }

    private String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcentos = java.text.Normalizer.normalize(texto.toLowerCase(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return semAcentos.trim();
    }

    private Integer extrairNumero(Pattern pattern, String texto) {
        Matcher matcher = pattern.matcher(texto == null ? "" : texto);
        if (!matcher.find()) {
            return null;
        }
        return parseInteiro(matcher.group(1));
    }

    private String extrairNomeCandidato(String pergunta) {
        if (pergunta == null || pergunta.isBlank()) {
            return "";
        }
        String nome = normalizarTexto(pergunta)
            .replaceAll("\\d+", " ")
            .replaceAll("(?iu)\\b(gerar|gere|emitir|montar|fazer|criar|imprimir|mostrar|mostre|trazer|liste|listar|resumir|resuma|consultar|consulte|buscar|busque)\\b", " ")
            .replaceAll("(?iu)\\b(prontuario|historico|paciente|nome|bam|numero|no|n|do|da|de|das|dos|para|sobre|completo|inteiro|todo|tudo|o|a|um|uma)\\b", " ")
            .replaceAll("[^\\p{L}\\s']", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return nome.split("\\s+").length >= 2 ? nome : "";
    }

    private List<Pacientes> buscarPacientesPorNome(String termo, int limite) {
        if (termo == null || termo.isBlank()) {
            return List.of();
        }
        String semEspacos = termo.trim().replaceAll("\\s+", "");
        if (semEspacos.length() < 3) {
            return List.of();
        }
        int limiteSeguro = Math.max(1, limite);
        List<Pacientes> encontrados = pacientesRepository.buscarPorNomeInteligente(termo, limiteSeguro);
        if (encontrados.isEmpty()) {
            // o nome candidato pode ainda conter palavras da pergunta: tenta a busca tolerante
            encontrados = pacientesRepository.buscarPorNomeEmFrase(termo, limiteSeguro);
        }
        return encontrados;
    }

    private boolean temAcessoClinico(AuthenticatedUser usuario) {
        return usuario != null
            && usuario.hasPermission("pragprontuario");
    }

    private boolean temPermissao(AuthenticatedUser usuario) {
        if (usuario == null || usuario.matricula() == null || usuario.matricula().isBlank()) {
            return false;
        }
        return usuario.hasPermission("pragprontuario");
    }

    private String restringirContextoParaAdministrativo(String contexto) {
        if (contexto == null || contexto.isBlank()) {
            return contexto;
        }
        String[] linhas = contexto.split("\n", -1);
        StringBuilder resultado = new StringBuilder();
        boolean manterSecaoAtual = true;
        for (String linha : linhas) {
            String semEspacos = linha.trim();
            boolean pareceCabecalho = !semEspacos.isEmpty()
                && semEspacos.endsWith(":")
                && semEspacos.equals(semEspacos.toUpperCase())
                && !semEspacos.startsWith("[");
            if (pareceCabecalho) {
                manterSecaoAtual = SECOES_PERMITIDAS_ADMINISTRATIVO.stream().anyMatch(semEspacos::startsWith);
            }
            if (linha.startsWith("---") || manterSecaoAtual) {
                resultado.append(linha).append('\n');
            }
        }
        return resultado.toString();
    }

    /**
     * Sem o trecho preenchido, "fontes" era so uma lista de nomes de secao - o
     * profissional nao tinha como conferir rapido qual frase do prontuario
     * embasou a resposta da IA sem reabrir o prontuario inteiro.
     */
    static List<Rag2FonteDto> extrairFontes(String contexto) {
        List<Rag2FonteDto> fontes = new ArrayList<>();
        if (contexto == null || contexto.isBlank()) {
            return fontes;
        }
        String[] linhas = contexto.split("\n");
        Rag2FonteDto secaoAtual = null;
        StringBuilder trechoAtual = new StringBuilder();
        for (String linha : linhas) {
            String semEspacos = linha.trim();
            boolean pareceCabecalho = !semEspacos.isEmpty()
                && semEspacos.endsWith(":")
                && semEspacos.equals(semEspacos.toUpperCase())
                && !semEspacos.startsWith("[")
                && !semEspacos.startsWith("---");
            if (pareceCabecalho) {
                finalizarTrecho(secaoAtual, trechoAtual);
                trechoAtual = new StringBuilder();
                String secao = semEspacos.substring(0, semEspacos.length() - 1);
                boolean jaExiste = fontes.stream().anyMatch(f -> f.getTipo().equals(secao));
                if (jaExiste) {
                    secaoAtual = null; // nao reabre secao duplicada, preserva o primeiro trecho
                } else {
                    Rag2FonteDto fonte = new Rag2FonteDto();
                    fonte.setTipo(secao);
                    fonte.setReferencia("Prontuario do paciente");
                    fonte.setTrecho("");
                    fontes.add(fonte);
                    secaoAtual = fonte;
                }
            } else if (secaoAtual != null && !semEspacos.isEmpty() && !linha.startsWith("---")) {
                if (trechoAtual.length() > 0) {
                    trechoAtual.append(' ');
                }
                trechoAtual.append(semEspacos);
            }
        }
        finalizarTrecho(secaoAtual, trechoAtual);
        return fontes;
    }

    private static void finalizarTrecho(Rag2FonteDto fonte, StringBuilder trecho) {
        if (fonte == null || trecho.length() == 0) {
            return;
        }
        String texto = trecho.toString().trim();
        fonte.setTrecho(texto.length() > 240 ? texto.substring(0, 240) + "..." : texto);
    }

    private Rag2PacienteBuscaDto montarPacienteBuscaDto(Pacientes p) {
        Rag2PacienteBuscaDto dto = new Rag2PacienteBuscaDto();
        dto.setIdPaciente(p.getIdpaciente());
        dto.setNome(p.getNome());
        dto.setProntuario(p.getProntuario());
        dto.setDataNascimento(p.getNascimento());
        dto.setIdade(p.getIdade());
        dto.setSexo(p.getSexo());

        List<Bam> bams = bamRepository.filtrarPorIdDoPciente(p.getIdpaciente());
        if (!bams.isEmpty()) {
            Bam ultimo = bams.get(0);
            dto.setBam(ultimo.getIdbam());
            dto.setUnidade(ultimo.getUnidade());
            dto.setSetor(ultimo.getRegistro() != null ? ultimo.getRegistro().getSetor() : null);
            dto.setSituacao(derivarSituacao(ultimo));

            NovoInternado internado = novoInternadoRepository.filtrarPorUnico(ultimo.getIdbam());
            dto.setLeito(internado != null ? internado.getLeitoocupado() : null);
        }
        return dto;
    }

    private String derivarSituacao(Bam bam) {
        if (bam.isTransferido()) {
            return "TRANSFERIDO";
        }
        if (bam.isDarAlta()) {
            return "ALTA";
        }
        String tipoAlta = bam.getTratamento() != null ? bam.getTratamento().getTipo_alta() : null;
        if ("obs".equalsIgnoreCase(tipoAlta)) {
            return "OBSERVACAO";
        }
        if ("internacao".equalsIgnoreCase(tipoAlta)) {
            return "INTERNADO";
        }
        return "EM ATENDIMENTO";
    }

    private void registrarAuditoria(AuthenticatedUser usuario, Rag2PerguntaDto dto, Integer idPaciente, String status) {
        try {
            LogDto log = new LogDto();
            log.setData(LocalDate.now());
            log.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            String matricula = usuario != null ? usuario.matricula() : safe(dto.getMatriculaUsuario());
            String nome = usuario != null && usuario.nome() != null && !usuario.nome().isBlank()
                ? usuario.nome() : safe(dto.getNomeUsuario());
            String evento = "RAG2 PRONTUARIO: matricula=" + matricula + " nome=" + nome
                + " idPaciente=" + (idPaciente != null ? idPaciente : "0")
                + " perfil=" + safe(dto.getPerfilUsuario())
                + " pergunta='" + truncate(dto.getPergunta(), 200) + "'"
                + " status=" + status;
            log.setEvento(truncate(evento, 590));
            if (usuario != null && usuario.unidade() != null) {
                log.setUnidade(truncate(usuario.unidade(), 20));
            }
            logService.salvarLogAsync(log);
        } catch (Exception e) {
            System.out.println("[Rag2ProntuarioService] Falha ao registrar auditoria: " + e.getMessage());
        }
    }

    private Integer parseInteiro(String valor) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String safe(String valor) {
        return valor == null ? "" : valor;
    }

    private String truncate(String valor, int max) {
        if (valor == null) {
            return "";
        }
        return valor.length() <= max ? valor : valor.substring(0, max) + "...";
    }
}


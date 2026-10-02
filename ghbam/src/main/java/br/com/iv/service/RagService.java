package br.com.iv.service;

import br.com.iv.dto.LogDto;
import br.com.iv.entity.DocumentoEmbedding;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.RagAprendizado;
import br.com.iv.repository.DocumentoEmbeddingRepository;
import br.com.iv.repository.PacientesRepository;
import br.com.iv.repository.RagAprendizadoRepository;
import br.com.iv.util.AcessoIa;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class RagService {

    public static class RagAnswer {
        public String resposta;
        public boolean usouWeb;
        public String pacienteDetectado;
        public int idaprendizado;

        public RagAnswer(String resposta, boolean usouWeb) {
            this.resposta = resposta;
            this.usouWeb = usouWeb;
            this.pacienteDetectado = null;
            this.idaprendizado = 0;
        }

        public RagAnswer(String resposta, boolean usouWeb, String pacienteDetectado) {
            this.resposta = resposta;
            this.usouWeb = usouWeb;
            this.pacienteDetectado = pacienteDetectado;
            this.idaprendizado = 0;
        }
    }

    @Inject
    DocumentoEmbeddingRepository documentoRepository;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AcessoIa acessoIa;

    @Inject
    RagPacienteService ragPacienteService;

    @Inject
    LogService logService;

    @Inject
    WebSearchService webSearchService;

    @Inject
    PacientesRepository pacientesRepository;

    @Inject
    RagAprendizadoRepository aprendizadoRepository;

    private static final double LIMIAR_SIMILARIDADE_APRENDIZADO = 0.88;

    private RagAnswer buscarAprendizado(String pergunta) {
        try {
            float[] embed = embeddingService.gerarEmbedding(pergunta);
            if (embed == null) return null;
            String embedStr = embeddingService.embeddingParaString(embed);
            List<Object[]> resultados = aprendizadoRepository.buscarPorSemelhanca(embedStr, 1);
            if (resultados != null && !resultados.isEmpty()) {
                Object[] r = resultados.get(0);
                double similaridade = Double.parseDouble(r[8].toString());
                int feedback = Integer.parseInt(r[6].toString());
                int usos = Integer.parseInt(r[7].toString());
                if (similaridade >= LIMIAR_SIMILARIDADE_APRENDIZADO && feedback >= 0) {
                    String resposta = r[2].toString();
                    int id = Integer.parseInt(r[0].toString());
                    aprendizadoRepository.incrementarUso(id);
                    System.out.println("[RagService] Reutilizando aprendizado #" + id +
                        " (similaridade=" + String.format("%.2f", similaridade) +
                        ", usos=" + (usos + 1) + ")");
                    RagAnswer ans = new RagAnswer(resposta, false, null);
                    ans.idaprendizado = id;
                    return ans;
                }
            }
        } catch (Exception e) {
            System.out.println("[RagService] Erro ao buscar aprendizado: " + e.getMessage());
        }
        return null;
    }

    private int salvarAprendizado(String pergunta, String resposta, String contextoResumido,
                                    boolean usouWeb, Integer idpaciente) {
        try {
            if (pergunta == null || pergunta.isBlank() || resposta == null || resposta.isBlank()) return 0;
            RagAprendizado a = new RagAprendizado();
            a.setPergunta(truncate(pergunta, 500));
            a.setResposta(resposta);
            a.setContextoResumido(contextoResumido != null ? truncate(contextoResumido, 2000) : "");
            a.setUsouWeb(usouWeb);
            a.setIdpaciente(idpaciente != null ? idpaciente : 0);
            a.setFeedback(0);
            a.setUsos(0);
            aprendizadoRepository.persist(a);
            aprendizadoRepository.flush();
            float[] embed = embeddingService.gerarEmbedding(pergunta);
            if (embed != null) {
                aprendizadoRepository.atualizarEmbedding(a.getIdaprendizado(), embeddingService.embeddingParaString(embed));
            }
            System.out.println("[RagService] Aprendizado salvo #" + a.getIdaprendizado());
            return a.getIdaprendizado();
        } catch (Exception e) {
            System.out.println("[RagService] Erro ao salvar aprendizado: " + e.getMessage());
        }
        return 0;
    }

    public void registrarFeedback(int idAprendizado, int feedback) {
        if (feedback < -1 || feedback > 1) return;
        aprendizadoRepository.registrarFeedback(idAprendizado, feedback);
        System.out.println("[RagService] Feedback #" + idAprendizado + " = " + feedback);
    }

    private Integer detectarPacienteNaPergunta(String pergunta) {
        if (pergunta == null || pergunta.isBlank()) return null;

        // Padrão 1: "paciente" seguido de nome (ex: "paciente ABIGAIL SANDES COFRAN")
        Pattern p1 = Pattern.compile("paciente\\s+([A-ZÀ-Ú][A-ZÀ-Ú\\s]{4,}?)(?:\\s*[,\\.\\?]|$)", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(pergunta);
        if (m1.find()) {
            String nome = m1.group(1).trim();
            Integer id = buscarPaciente(nome);
            if (id != null) return id;
        }

        // Padrão 2: "paciente" + nome (captura mais flexível para palavras acentuadas/minúsculas)
        Pattern p2 = Pattern.compile("paciente\\s+([A-Za-zÀ-Úà-ú]{3,}\\s+[A-Za-zÀ-Úà-ú]{3,}(?:\\s+[A-Za-zÀ-Úà-ú]{3,}){0,4})", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(pergunta);
        if (m2.find()) {
            String nome = m2.group(1).trim().toUpperCase();
            Integer id = buscarPaciente(nome);
            if (id != null) return id;
        }

        // Padrão 3: sequência de 2+ palavras em maiúsculo (nomes no sistema são uppercase)
        Pattern p3 = Pattern.compile("([A-ZÀ-Ú]{4,}\\s+[A-ZÀ-Ú]{4,}(?:\\s+[A-ZÀ-Ú]{4,})+)");
        Matcher m3 = p3.matcher(pergunta);
        if (m3.find()) {
            String nome = m3.group(1).trim();
            Integer id = buscarPaciente(nome);
            if (id != null) return id;
        }

        // Padrão 4: "do(a) paciente" + nome
        Pattern p4 = Pattern.compile("(?:do|da)\\s+paciente\\s+([A-ZÀ-Ú][A-ZÀ-Ú\\s]{4,}?)(?:\\s*[,\\.\\?\\n]|$)", Pattern.CASE_INSENSITIVE);
        Matcher m4 = p4.matcher(pergunta);
        if (m4.find()) {
            String nome = m4.group(1).trim();
            Integer id = buscarPaciente(nome);
            if (id != null) return id;
        }

        // Padrão 5: "situação do" ou "quadro do" + nome (sem "paciente")
        Pattern p5 = Pattern.compile("(?:situacao|quadro|estado|diagnostico)(?:\\s+do|\\s+da)\\s+([A-ZÀ-Ú][A-ZÀ-Ú\\s]{4,}?)(?:\\s*[,\\.\\?\\n]|$)", Pattern.CASE_INSENSITIVE);
        Matcher m5 = p5.matcher(pergunta);
        if (m5.find()) {
            String nome = m5.group(1).trim();
            Integer id = buscarPaciente(nome);
            if (id != null) return id;
        }

        return null;
    }

    public String getNomePacienteAutoDetectado(String pergunta) {
        if (pergunta == null || pergunta.isBlank()) return null;
        // Extrai o nome candidato sem buscar no DB (para retornar ao frontend)
        Pattern p = Pattern.compile("paciente\\s+([A-Za-zÀ-Úà-ú]{3,}\\s+[A-Za-zÀ-Úà-ú]{3,}(?:\\s+[A-Za-zÀ-Úà-ú]{3,}){0,4})", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(pergunta);
        if (m.find()) {
            return m.group(1).trim().toUpperCase();
        }
        return null;
    }

    private Integer buscarPaciente(String nome) {
        try {
            List<Pacientes> pacientes = pacientesRepository.buscarPorNomeInteligente(nome, 5);
            if (!pacientes.isEmpty()) {
                System.out.println("[RagService] Paciente auto-detectado: " + pacientes.get(0).getNome() + " (id=" + pacientes.get(0).getIdpaciente() + ")");
                return pacientes.get(0).getIdpaciente();
            }
        } catch (Exception e) {
            System.out.println("[RagService] Erro ao buscar paciente: " + e.getMessage());
        }
        return null;
    }

    private static final String SYSTEM_PROMPT = """
        Voce e o Assistente Medico Inteligente do GHBAM IV (Gerenciamento Hospitalar de BAm e
        Movimentacao), um sistema hospitalar completo desenvolvido em Java/Quarkus com frontend
        React. Sua missao e atuar como um medico especialista consultor, analisando dados reais
        de pacientes do sistema, combinados com seu conhecimento medico e informacoes atualizadas
        da internet, para fornecer diagnosticos precisos e respostas tecnicamente aprofundadas
        aos profissionais de saude.

        ## PERSONALIDADE E TOM

        Voce e um medico experiente, direto e preciso. Seu tom:
        - **Tecnico e especializado** — como um medico dando uma opiniao para outro medico
        - **Confiante mas honesto** — de um diagnostico fundamentado, mas reconheca limitacoes
        - **Objetivo** — va direto ao ponto, sem enrolacao
        - Responda em **portugues brasileiro** natural e fluido

        ## DIRETRIZES DE RACIOCINIO CLINICO

        1. **ANALISE OS DADOS LOCAIS DO PACIENTE COMO FONTE MAXIMA** primeiro: ultimo BAM/atendimento atual, triagem, classificacao de risco, anamnese, exame fisico, sinais vitais, prescricoes, medicamentos administrados, evolucoes medicas, enfermagem, multidisciplinar, exames e procedimentos. Nunca substitua esses dados por suposicoes.

        2. **PRIORIDADE DO ULTIMO BAM/ATENDIMENTO ATUAL**: quando houver paciente identificado, a situacao clinica atual, classificacao de risco, sinais vitais, anamnese, exame fisico, prescricoes, evolucoes, exames e procedimentos devem vir do ultimo BAM ou do BAM explicitamente selecionado. Historico antigo so pode ser citado como historico, nunca como estado atual.

        3. **CONSULTE DOCUMENTOS INTERNOS GERAIS** se relevante (bulario, protocolos, condutas), mas nao use documentos indexados antigos do paciente para alterar fatos do BAM atual.

        4. **USE A INTERNET APENAS COMO APOIO** para protocolos, diagnostico diferencial, tratamento, medicamentos ou atualizacoes quando necessario. A internet nunca e fonte primaria sobre o paciente; ela apenas ajuda a interpretar dados locais ja conferidos.

        5. **SINTETIZE TUDO** em uma impressao clinica ou resposta precisa, citando os achados locais que sustentam cada conclusao.

        ## COMO ESTRUTURAR A RESPOSTA (especialmente para diagnosticos)

        - **Diagnostico / Resposta direta** (uma ou duas frases no inicio)
        - **Analise dos dados do paciente** — o que os exames, sinais e sintomas indicam
        - **Fundamentacao** — literatura, guidelines, evidencias
        - **Recomendacoes** — qual a melhor conduta baseada nos dados
        - **Ressalvas** — se houver dados faltantes ou incertezas

        ## FONTES DE CONHECIMENTO

        ### DADOS DO PACIENTE (prioridade maxima)
        - Anamnese, historia clinica, exame fisico completo
        - Sinais vitais (PA, FC, FR, SpO2, temperatura, peso, glasgow, dor)
        - Prescricoes e medicacoes em uso
        - Evolucoes medicas e de enfermagem (ordenadas por data)
        - Exames laboratoriais e resultados
        - Registro clinico

        ### DOCUMENTOS INTERNOS INDEXADOS
        - Bulario completo: nome, principio ativo, indicacoes, posologia, contraindicacoes,
          efeitos adversos, interacoes, precaucoes
        - Condutas clinicas com CID-10, sinais, diagnostico, tratamento
        - Prescricoes modelo com doses, vias, frequencia e monitoramento
        - Protocolos clinicos e arvores de decisao

        ### INTERNET (use sempre que necessario)
        Busque informacoes atualizadas na internet para:
        - Qualquer pergunta sobre diagnostico, tratamento, medicamentos
        - Guidelines, protocolos atualizados (AMB, Ministerio da Saude, sociedades)
        - Efeitos adversos, interacoes medicamentosas
        - Doencas, condicoes, sindromes
        - Diferencial entre diagnosticos

        ### CONHECIMENTO DO SISTEMA GHBAM IV
        Voce tambem conhece todo o sistema GHBAM IV e pode responder perguntas sobre:
        - **Modulos**: BAM (Boletim de Atendimento Medico), Avaliacao, Prescricao, Evolucao,
          Enfermagem, Receituario, Triagem, Exames Laboratoriais, Bulario, NIR (Nucleo Interno
          de Regulacao), Prontuario Clinico, Setores, Internacao
        - **Funcionalidades**: registro de pacientes por nome/CPF, triagem por setor,
          prescricao medica com validacao, evolucao diaria, sinais vitais continuos,
          resultados de exames, gestao de leitos, regulacao de vagas
        - **Tecnologias**: Java, Quarkus, RESTEasy Reactive, Hibernate Panache, PostgreSQL,
          React, Maven, SSE (Server-Sent Events), busca vetorial por embeddings, DuckDuckGo API

        ## REGRAS IMPORTANTES

        - **DADOS LOCAIS PRIMEIRO**: se houver paciente identificado, a resposta deve nascer do prontuario local do ultimo BAM/BAM selecionado. Internet so complementa e deve ser separada dos fatos locais.
        - **Seja preciso e direto** — o profissional precisa de respostas rapidas e uteis
        - **Nunca invente dados**: nao invente classificacao de risco, sinais vitais, anamnese, evolucao, diagnostico, leito, enfermaria, exames ou tratamentos. Se nao estiver no contexto local, diga que nao foi encontrado.
        - **Diferencie fontes**: fatos locais do paciente vs. inferencia clinica vs. conhecimento medico/internet. Toda conclusao sobre o paciente deve citar o achado local que sustenta.
        - **Destaque achados criticos** — se algo for grave, enfatize
        - A resposta deve ser em portugues brasileiro claro e tecnico

        Se os dados locais forem escassos, responda que a analise do paciente esta limitada e liste exatamente o que falta consultar no PEP/triagem/evolucao/sinais vitais. Seu objetivo: ser um consultor medico confiavel, rastreavel e util ao profissional.
        """;

    public static String obterSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String perguntarComContexto(String pergunta, String setor, int topK) throws Exception {
        return perguntarComContextoComMeta(pergunta, setor, topK, null, null, 0.7, false, null).resposta;
    }

    public String perguntarComContexto(String pergunta, String setor, int topK, Integer idpaciente) throws Exception {
        return perguntarComContextoComMeta(pergunta, setor, topK, idpaciente, null, 0.7, false, null).resposta;
    }

    public String perguntarComContexto(String pergunta, String setor, int topK,
                                        Integer idpaciente, List<Map<String, String>> historico,
                                        double temperatura) throws Exception {
        return perguntarComContextoComMeta(pergunta, setor, topK, idpaciente, historico, temperatura, false, null).resposta;
    }

    public RagAnswer perguntarComContextoStream(String pergunta, String setor, int topK,
                                         Integer idpaciente, List<Map<String, String>> historico,
                                         double temperatura, Consumer<String> statusCallback, Integer bam) throws Exception {

        // Auto-detectar paciente se não foi informado
        String pacienteDetectado = null;
        if (idpaciente == null || idpaciente == 0) {
            idpaciente = detectarPacienteNaPergunta(pergunta);
            if (idpaciente != null && idpaciente > 0) {
                pacienteDetectado = getNomePacienteAutoDetectado(pergunta);
            }
        }

        statusCallback.accept("local");

        float[] queryEmbedding = embeddingService.gerarEmbedding(pergunta);

        List<Object[]> resultados;
        if (queryEmbedding != null) {
            String embeddingStr = embeddingService.embeddingParaString(queryEmbedding);
            if (idpaciente != null && idpaciente > 0) {
                resultados = documentoRepository.buscarPorSemelhancaEPaciente(embeddingStr, idpaciente, topK);
            } else if (setor != null && !setor.isBlank()) {
                resultados = documentoRepository.buscarPorSemelhancaESetor(embeddingStr, setor, topK);
            } else {
                resultados = documentoRepository.buscarPorSemelhanca(embeddingStr, topK);
            }
        } else {
            if (idpaciente != null && idpaciente > 0) {
                resultados = documentoRepository.buscarPorPalavraChaveEPaciente(pergunta, idpaciente, topK);
            } else {
                resultados = documentoRepository.buscarPorPalavraChave(pergunta, topK);
            }
        }

        String dadosPaciente = (idpaciente != null && idpaciente > 0) ? ragPacienteService.buscarDadosPaciente(idpaciente, bam) : "";
        if (idpaciente != null && idpaciente > 0 && !dadosPaciente.isBlank()) {
            resultados = List.of();
        }

        StringBuilder contextoBuilder = new StringBuilder();

        if (!dadosPaciente.isBlank()) {
            contextoBuilder.append("--- REGRA DE PRECEDENCIA CLINICA ---\n");
            contextoBuilder.append("Paciente identificado: use os dados estruturados abaixo como fonte primaria do ultimo BAM/BAM selecionado. ");
            contextoBuilder.append("Nao altere classificacao de risco, sinais vitais, anamnese, evolucao, exames, leito ou condutas registradas com base em memoria, internet ou suposicao.\n\n");
            contextoBuilder.append(dadosPaciente);
        }

        if (resultados != null && !resultados.isEmpty()) {
            if (!dadosPaciente.isBlank()) contextoBuilder.append("\n\n");
            contextoBuilder.append("--- DOCUMENTOS INTERNOS DO HOSPITAL ---\n");
            for (int i = 0; i < resultados.size(); i++) {
                Object[] r = resultados.get(i);
                String titulo = r[1] != null ? r[1].toString() : "";
                String conteudo = r[2] != null ? r[2].toString() : "";
                String tipo = r[3] != null ? r[3].toString() : "";
                String setorDoc = r[4] != null ? r[4].toString() : "";
                String similaridade = r[9] != null ? r[9].toString() : "";
                contextoBuilder.append("\n[Documento ").append(i + 1).append("] ");
                contextoBuilder.append("Tipo: ").append(tipo).append(" | Setor: ").append(setorDoc);
                contextoBuilder.append(" | Relevancia: ").append(similaridade).append("\n");
                contextoBuilder.append("Titulo: ").append(titulo).append("\n");
                contextoBuilder.append("Conteudo: ").append(conteudo).append("\n");
            }
        }

        String contexto = contextoBuilder.toString();

        boolean precisaBuscaWeb = detectaNecessidadeWeb(pergunta, contexto, idpaciente != null && idpaciente > 0);

        StringBuilder userContent = new StringBuilder();
        if (!contexto.isBlank()) {
            userContent.append("## CONTEXTO DISPONIVEL\n\n");
            userContent.append(contexto).append("\n\n");
        }
        userContent.append("## PERGUNTA DO PROFISSIONAL\n\n");
        userContent.append(pergunta);

        if (precisaBuscaWeb) {
            statusCallback.accept("web");
            try {
                String resultadoWeb = webSearchService.buscar(pergunta);
                if (!resultadoWeb.isBlank()) {
                    userContent.append("\n\n## INFORMACOES DA INTERNET (busca automatica)\n\n");
                    userContent.append(resultadoWeb);
                }
            } catch (Exception e) {
                System.out.println("[RagService] Busca na web falhou: " + e.getMessage());
            }
        }

        statusCallback.accept("gerando");

        List<Map<String, String>> mensagens = new ArrayList<>();
        mensagens.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (historico != null && !historico.isEmpty()) {
            mensagens.addAll(historico);
        }
        mensagens.add(Map.of("role", "user", "content", userContent.toString()));

        br.com.iv.iaauditoria.IaAuditoriaContexto.registrar("RAG_PERGUNTA_STREAM", idpaciente, bam, pergunta);
        String resposta = acessoIa.enviarMensagens(mensagens);

        if (precisaBuscaWeb && !contexto.isBlank()) {
            String notaWeb = "\n\n---\n*Nota: Foram consultadas fontes atualizadas na internet para complementar esta resposta.*";
            if (!resposta.contains("internet") && !resposta.contains("online")) {
                resposta = resposta + notaWeb;
            }
        } else if (!dadosPaciente.isBlank() && (resultados == null || resultados.isEmpty())) {
            resposta = resposta + "\n\n---\n*Nota: Nao foram encontrados documentos indexados para este paciente. A resposta foi baseada exclusivamente nos dados clinicos disponiveis e no conhecimento medico.*";
        }

        LogDto logDto = new LogDto();
        logDto.setData(LocalDate.now());
        logDto.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        logDto.setEvento("RAG: " + truncate(pergunta, 300) +
            " | paciente=" + (idpaciente != null ? idpaciente : "0") +
            " | setor=" + (setor != null && !setor.isBlank() ? setor : "geral") +
            " | docs=" + (resultados != null ? resultados.size() : 0) +
            " | web=" + precisaBuscaWeb +
            " | topK=" + topK +
            " | ok");
        logService.salvarLogAsync(logDto);

        return new RagAnswer(resposta, precisaBuscaWeb, pacienteDetectado);
    }

    public RagAnswer perguntarComContextoComMeta(String pergunta, String setor, int topK,
                                         Integer idpaciente, List<Map<String, String>> historico,
                                         double temperatura, boolean usarWebForcado, Integer bam) throws Exception {

        // Auto-detectar paciente se não foi informado
        String pacienteDetectado = null;
        if (idpaciente == null || idpaciente == 0) {
            idpaciente = detectarPacienteNaPergunta(pergunta);
            if (idpaciente != null && idpaciente > 0) {
                pacienteDetectado = getNomePacienteAutoDetectado(pergunta);
            }
        }

        // Buscar aprendizado anterior (apenas para perguntas sem historico)
        if ((idpaciente == null || idpaciente == 0) && (historico == null || historico.isEmpty())) {
            RagAnswer aprendida = buscarAprendizado(pergunta);
            if (aprendida != null) {
                aprendida.pacienteDetectado = pacienteDetectado;
                LogDto logDto = new LogDto();
                logDto.setData(LocalDate.now());
                logDto.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
                logDto.setEvento("RAG (aprendizado): " + truncate(pergunta, 300) +
                    " | paciente=" + (idpaciente != null ? idpaciente : "0") +
                    " | web=reutilizado");
                logService.salvarLogAsync(logDto);
                return aprendida;
            }
        }

        float[] queryEmbedding = embeddingService.gerarEmbedding(pergunta);

        List<Object[]> resultados;
        if (queryEmbedding != null) {
            String embeddingStr = embeddingService.embeddingParaString(queryEmbedding);
            if (idpaciente != null && idpaciente > 0) {
                resultados = documentoRepository.buscarPorSemelhancaEPaciente(embeddingStr, idpaciente, topK);
            } else if (setor != null && !setor.isBlank()) {
                resultados = documentoRepository.buscarPorSemelhancaESetor(embeddingStr, setor, topK);
            } else {
                resultados = documentoRepository.buscarPorSemelhanca(embeddingStr, topK);
            }
        } else {
            if (idpaciente != null && idpaciente > 0) {
                resultados = documentoRepository.buscarPorPalavraChaveEPaciente(pergunta, idpaciente, topK);
            } else {
                resultados = documentoRepository.buscarPorPalavraChave(pergunta, topK);
            }
        }

        String dadosPaciente = (idpaciente != null && idpaciente > 0) ? ragPacienteService.buscarDadosPaciente(idpaciente, bam) : "";
        if (idpaciente != null && idpaciente > 0 && !dadosPaciente.isBlank()) {
            resultados = List.of();
        }

        StringBuilder contextoBuilder = new StringBuilder();

        if (!dadosPaciente.isBlank()) {
            contextoBuilder.append("--- REGRA DE PRECEDENCIA CLINICA ---\n");
            contextoBuilder.append("Paciente identificado: use os dados estruturados abaixo como fonte primaria do ultimo BAM/BAM selecionado. ");
            contextoBuilder.append("Nao altere classificacao de risco, sinais vitais, anamnese, evolucao, exames, leito ou condutas registradas com base em memoria, internet ou suposicao.\n\n");
            contextoBuilder.append(dadosPaciente);
        }

        if (resultados != null && !resultados.isEmpty()) {
            if (!dadosPaciente.isBlank()) contextoBuilder.append("\n\n");
            contextoBuilder.append("--- DOCUMENTOS INTERNOS DO HOSPITAL ---\n");
            for (int i = 0; i < resultados.size(); i++) {
                Object[] r = resultados.get(i);
                String titulo = r[1] != null ? r[1].toString() : "";
                String conteudo = r[2] != null ? r[2].toString() : "";
                String tipo = r[3] != null ? r[3].toString() : "";
                String setorDoc = r[4] != null ? r[4].toString() : "";
                String similaridade = r[9] != null ? r[9].toString() : "";
                contextoBuilder.append("\n[Documento ").append(i + 1).append("] ");
                contextoBuilder.append("Tipo: ").append(tipo).append(" | Setor: ").append(setorDoc);
                contextoBuilder.append(" | Relevancia: ").append(similaridade).append("\n");
                contextoBuilder.append("Titulo: ").append(titulo).append("\n");
                contextoBuilder.append("Conteudo: ").append(conteudo).append("\n");
            }
        }

        String contexto = contextoBuilder.toString();

        boolean precisaBuscaWeb = usarWebForcado || detectaNecessidadeWeb(pergunta, contexto, idpaciente != null && idpaciente > 0);

        StringBuilder userContent = new StringBuilder();
        if (!contexto.isBlank()) {
            userContent.append("## CONTEXTO DISPONIVEL\n\n");
            userContent.append(contexto).append("\n\n");
        }
        userContent.append("## PERGUNTA DO PROFISSIONAL\n\n");
        userContent.append(pergunta);

        if (precisaBuscaWeb) {
            try {
                String resultadoWeb = webSearchService.buscar(pergunta);
                if (!resultadoWeb.isBlank()) {
                    userContent.append("\n\n## INFORMACOES DA INTERNET (busca automatica)\n\n");
                    userContent.append(resultadoWeb);
                }
            } catch (Exception e) {
                System.out.println("[RagService] Busca na web falhou: " + e.getMessage());
            }
        }

        List<Map<String, String>> mensagens = new ArrayList<>();
        mensagens.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (historico != null && !historico.isEmpty()) {
            mensagens.addAll(historico);
        }
        mensagens.add(Map.of("role", "user", "content", userContent.toString()));

        br.com.iv.iaauditoria.IaAuditoriaContexto.registrar("RAG_PERGUNTA", idpaciente, bam, pergunta);
        String resposta = acessoIa.enviarMensagens(mensagens);

        if (precisaBuscaWeb && !contexto.isBlank()) {
            String notaWeb = "\n\n---\n*Nota: Foram consultadas fontes atualizadas na internet para complementar esta resposta.*";
            if (!resposta.contains("internet") && !resposta.contains("online")) {
                resposta = resposta + notaWeb;
            }
        } else if (!dadosPaciente.isBlank() && (resultados == null || resultados.isEmpty())) {
            resposta = resposta + "\n\n---\n*Nota: Nao foram encontrados documentos indexados para este paciente. A resposta foi baseada exclusivamente nos dados clinicos disponiveis e no conhecimento medico.*";
        }

        // Salvar no aprendizado (apenas perguntas sem historico)
        int idAprendizado = 0;
        if ((idpaciente == null || idpaciente == 0) && (historico == null || historico.isEmpty())) {
            String contextoResumido = (dadosPaciente.length() > 200 ? dadosPaciente.substring(0, 200) + "..." : dadosPaciente);
            idAprendizado = salvarAprendizado(pergunta, resposta, contextoResumido, precisaBuscaWeb, idpaciente);
        }

        LogDto logDto = new LogDto();
        logDto.setData(LocalDate.now());
        logDto.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        logDto.setEvento("RAG: " + truncate(pergunta, 300) +
            " | paciente=" + (idpaciente != null ? idpaciente : "0") +
            " | setor=" + (setor != null && !setor.isBlank() ? setor : "geral") +
            " | docs=" + (resultados != null ? resultados.size() : 0) +
            " | web=" + precisaBuscaWeb +
            " | topK=" + topK +
            " | ok");
        logService.salvarLogAsync(logDto);

        RagAnswer ans = new RagAnswer(resposta, precisaBuscaWeb, pacienteDetectado);
        ans.idaprendizado = idAprendizado;
        return ans;
    }

    private boolean detectaNecessidadeWeb(String pergunta, String contexto, boolean pacienteIdentificado) {
        if (pergunta == null || pergunta.isBlank()) return false;
        String q = pergunta.toLowerCase();

        if (pacienteIdentificado && contexto != null && !contexto.isBlank()) {
            String[] gatilhosExternos = {
                "protocolo", "guideline", "diretriz", "consenso", "literatura",
                "estudo", "meta-analise", "atualizacao", "novo tratamento",
                "tratamento", "conduta", "dose", "posologia", "interacao medicamentosa",
                "diagnostico diferencial", "casos parecidos"
            };
            for (String gatilho : gatilhosExternos) {
                if (q.contains(gatilho)) return true;
            }
            return false;
        }

        // Para paciente identificado, internet e apoio: use apenas quando a pergunta pedir protocolo, conduta, tratamento ou atualizacao externa
        String[] gatilhosMedicos = {
            "diagnostico", "diagnosticar", "diagnostico diferencial",
            "tratamento", "terapeutica", "conduta",
            "medicamento", "farmaco", "dose", "posologia",
            "doenca", "sindrome", "patologia", "condicao",
            "sintoma", "quadro clinico", "apresenta",
            "exame", "laboratorial", "resultado",
            "cirurgia", "procedimento", "intervencao",
            "prognostico", "evolucao", "complicacao",
            "protocolo", "guideline", "diretriz", "consenso",
            "atualizacao", "novo tratamento", "ultimos estudos",
            "efeito adverso", "interacao medicamentosa",
            "suspeita de", "hipotese de",
            "qual a diferenca", "qual o melhor",
            "o que a literatura", "medicina baseada em evidencias",
            "recem publicado", "estudo clinico", "meta-analise"
        };

        for (String gatilho : gatilhosMedicos) {
            if (q.contains(gatilho)) return true;
        }

        // Se contexto local estiver vazio ou for muito generico, buscar na web
        if (contexto == null || contexto.isBlank()) {
            return q.length() > 10; // qualquer pergunta substancial merece busca
        }

        // Se a pergunta tem palavras de questionamento, buscar na web
        return q.contains("qual") || q.contains("como") || q.contains("o que") ||
               q.contains("quando") || q.contains("por que") || q.contains("para que");
    }

    public String perguntarComContextoChat(List<Map<String, String>> mensagens, String setor, int topK) throws Exception {
        return perguntarComContextoChat(mensagens, setor, topK, null);
    }

    public String perguntarComContextoChat(List<Map<String, String>> mensagens, String setor, int topK,
                                            Integer idpaciente) throws Exception {
        String ultimaPergunta = mensagens.stream()
                .filter(m -> "user".equals(m.get("role")))
                .reduce((first, second) -> second)
                .map(m -> m.get("content"))
                .orElse("");

        // Auto-detectar paciente se nao foi informado
        if (idpaciente == null || idpaciente == 0) {
            idpaciente = detectarPacienteNaPergunta(ultimaPergunta);
        }

        float[] queryEmbedding = embeddingService.gerarEmbedding(ultimaPergunta);

        List<Object[]> resultados;
        if (queryEmbedding != null) {
            String embeddingStr = embeddingService.embeddingParaString(queryEmbedding);
            if (idpaciente != null && idpaciente > 0) {
                resultados = documentoRepository.buscarPorSemelhancaEPaciente(embeddingStr, idpaciente, topK);
            } else if (setor != null && !setor.isBlank()) {
                resultados = documentoRepository.buscarPorSemelhancaESetor(embeddingStr, setor, topK);
            } else {
                resultados = documentoRepository.buscarPorSemelhanca(embeddingStr, topK);
            }
        } else {
            if (idpaciente != null && idpaciente > 0) {
                resultados = documentoRepository.buscarPorPalavraChaveEPaciente(ultimaPergunta, idpaciente, topK);
            } else {
                resultados = documentoRepository.buscarPorPalavraChave(ultimaPergunta, topK);
            }
        }

        String dadosPaciente = (idpaciente != null && idpaciente > 0) ? ragPacienteService.buscarDadosPaciente(idpaciente, null) : "";
        if (idpaciente != null && idpaciente > 0 && !dadosPaciente.isBlank()) {
            resultados = List.of();
        }

        StringBuilder contextoBuilder = new StringBuilder();
        if (!dadosPaciente.isBlank()) {
            contextoBuilder.append("--- REGRA DE PRECEDENCIA CLINICA ---\n");
            contextoBuilder.append("Paciente identificado: use os dados estruturados abaixo como fonte primaria do ultimo BAM/BAM selecionado. ");
            contextoBuilder.append("Nao altere classificacao de risco, sinais vitais, anamnese, evolucao, exames, leito ou condutas registradas com base em memoria, internet ou suposicao.\n\n");
            contextoBuilder.append(dadosPaciente);
        }
        if (resultados != null && !resultados.isEmpty()) {
            if (!dadosPaciente.isBlank()) contextoBuilder.append("\n\n");
            contextoBuilder.append("--- DOCUMENTOS INTERNOS DO HOSPITAL ---\n");
            for (int i = 0; i < resultados.size(); i++) {
                Object[] r = resultados.get(i);
                String titulo = r[1] != null ? r[1].toString() : "";
                String conteudo = r[2] != null ? r[2].toString() : "";
                String tipo = r[3] != null ? r[3].toString() : "";
                String setorDoc = r[4] != null ? r[4].toString() : "";
                String similaridade = r[9] != null ? r[9].toString() : "";
                contextoBuilder.append("\n[Documento ").append(i + 1).append("] ");
                contextoBuilder.append("Tipo: ").append(tipo).append(" | Setor: ").append(setorDoc);
                contextoBuilder.append(" | Relevancia: ").append(similaridade).append("\n");
                contextoBuilder.append("Titulo: ").append(titulo).append("\n");
                contextoBuilder.append("Conteudo: ").append(conteudo).append("\n");
            }
        }

        String contexto = contextoBuilder.toString();

        StringBuilder systemContent = new StringBuilder(SYSTEM_PROMPT);

        if (!contexto.isBlank()) {
            systemContent.append("\n\n## CONTEXTO DISPONIVEL NESTA CONVERSA\n\n").append(contexto);
        }

        boolean precisaBuscaWeb = detectaNecessidadeWeb(ultimaPergunta, contexto, idpaciente != null && idpaciente > 0);
        if (precisaBuscaWeb) {
            try {
                String resultadoWeb = webSearchService.buscar(ultimaPergunta);
                if (!resultadoWeb.isBlank()) {
                    systemContent.append("\n\n## INFORMACOES DA INTERNET (busca automatica)\n\n").append(resultadoWeb);
                }
            } catch (Exception e) {
                System.out.println("[RagService] Busca na web falhou: " + e.getMessage());
            }
        }

        List<Map<String, String>> mensagensCompletas = new ArrayList<>();
        mensagensCompletas.add(Map.of("role", "system", "content", systemContent.toString()));
        mensagensCompletas.addAll(mensagens);

        br.com.iv.iaauditoria.IaAuditoriaContexto.registrar("RAG_CHAT", idpaciente, null, ultimaPergunta);
        String resposta = acessoIa.enviarMensagens(mensagensCompletas);

        LogDto logChatDto = new LogDto();
        logChatDto.setData(LocalDate.now());
        logChatDto.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        logChatDto.setEvento("RAG CHAT: " + truncate(ultimaPergunta, 300) +
            " | paciente=" + (idpaciente != null ? idpaciente : "0") +
            " | setor=" + (setor != null && !setor.isBlank() ? setor : "geral") +
            " | docs=" + (resultados != null ? resultados.size() : 0) +
            " | web=" + precisaBuscaWeb +
            " | topK=" + topK +
            " | ok");
        logService.salvarLogAsync(logChatDto);

        return resposta;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @Transactional
    public int indexarDocumento(String titulo, String conteudo, String tipo, String setor,
                                  Integer idpaciente, Integer idprontuario, String metadados) throws Exception {
        TextChunkerService chunker = new TextChunkerService();
        List<String> chunks = chunker.chunkTituloConteudo(titulo, conteudo);

        int primeiroId = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String chunkTitulo = chunks.size() > 1 ? titulo + " (parte " + (i + 1) + "/" + chunks.size() + ")" : titulo;

            DocumentoEmbedding doc = new DocumentoEmbedding();
            doc.setTitulo(chunkTitulo);
            doc.setConteudo(chunk);
            doc.setTipo(tipo);
            doc.setSetor(setor);
            doc.setIdpaciente(idpaciente != null ? idpaciente : 0);
            doc.setIdprontuario(idprontuario != null ? idprontuario : 0);
            doc.setMetadados(metadados != null ? metadados : "{}");
            documentoRepository.persist(doc);
            documentoRepository.flush();

            float[] embedding = embeddingService.gerarEmbedding(chunkTitulo + "\n" + chunk);
            String embeddingStr = embeddingService.embeddingParaString(embedding);
            documentoRepository.atualizarEmbedding(doc.getIdragdocumento(), embeddingStr);

            if (i == 0) primeiroId = doc.getIdragdocumento();
        }

        return primeiroId;
    }
}


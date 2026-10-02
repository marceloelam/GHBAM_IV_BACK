package br.com.iv.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.com.iv.dto.NspAcaoDto;
import br.com.iv.dto.NspEventoDto;
import br.com.iv.entity.NspEvento;
import br.com.iv.entity.NspEventoAcao;
import br.com.iv.repository.NspEventoAcaoRepository;
import br.com.iv.repository.NspEventoRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class NspEventoService {

    public static final Set<String> TIPOS = Set.of(
            "QUEDA", "ERRO_MEDICACAO", "LESAO_POR_PRESSAO", "IDENTIFICACAO_INCORRETA", "FLEBITE",
            "PERDA_DISPOSITIVO", "EXTUBACAO_ACIDENTAL", "INFECCAO_RELACIONADA_ASSISTENCIA",
            "REACAO_TRANSFUSIONAL", "ERRO_PROCEDIMENTO_CIRURGICO", "FALHA_EQUIPAMENTO",
            "ERRO_LABORATORIO_EXAME", "ATRASO_ASSISTENCIA", "ERRO_DIETA", "EVASAO_PACIENTE",
            "AGRESSAO_VIOLENCIA", "TENTATIVA_SUICIDIO", "REACAO_ADVERSA_MEDICAMENTO", "OUTROS");

    public static final Set<String> CLASSIFICACOES = Set.of(
            "CIRCUNSTANCIA_NOTIFICAVEL", "NEAR_MISS", "INCIDENTE_SEM_DANO", "EVENTO_ADVERSO", "NEVER_EVENT");

    public static final Set<String> GRAUS_DANO = Set.of("NENHUM", "LEVE", "MODERADO", "GRAVE", "OBITO");

    private static final Set<String> STATUS = Set.of("NOTIFICADO", "EM_ANALISE", "PLANO_ACAO", "ENCERRADO", "CANCELADO");

    private static final Map<String, Set<String>> TRANSICOES = Map.of(
            "NOTIFICADO", Set.of("EM_ANALISE", "CANCELADO"),
            "EM_ANALISE", Set.of("PLANO_ACAO", "ENCERRADO", "CANCELADO"),
            "PLANO_ACAO", Set.of("EM_ANALISE", "ENCERRADO", "CANCELADO"),
            "ENCERRADO", Set.of(),
            "CANCELADO", Set.of());

    private static final Set<String> TIPOS_ACAO = Set.of("ACAO_CORRETIVA", "ACAO_PREVENTIVA", "COMENTARIO");

    private static final Set<String> CLASSIFICACOES_COM_DANO = Set.of("EVENTO_ADVERSO", "NEVER_EVENT");

    /** Tipos de incidente que envolvem medicamento e por isso exigem rastreabilidade estruturada. */
    public static final Set<String> TIPOS_MEDICAMENTO = Set.of("ERRO_MEDICACAO", "REACAO_ADVERSA_MEDICAMENTO");

    /** Escala de causalidade adaptada do algoritmo de Naranjo / OMS-UMC. */
    public static final Set<String> CAUSALIDADES = Set.of(
            "DEFINIDA", "PROVAVEL", "POSSIVEL", "IMPROVAVEL", "CONDICIONAL", "NAO_CLASSIFICADA");

    @Inject
    NspEventoRepository repository;

    @Inject
    NspEventoAcaoRepository acaoRepository;

    // ------------------------------------------------------------------ leitura

    public Map<String, Object> listar(String unidade, String status, String classificacao,
            LocalDateTime inicio, LocalDateTime fim, boolean gestao) {
        String sigla = exigirUnidade(unidade);
        List<NspEvento> eventos = repository.listar(sigla, status, classificacao, inicio, fim);
        List<Map<String, Object>> registros = new ArrayList<>();
        for (NspEvento evento : eventos) {
            registros.add(resumo(evento, gestao));
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("unidade", sigla);
        resposta.put("total", registros.size());
        resposta.put("abertos", repository.contarAbertos(sigla));
        resposta.put("atrasados", repository.contarAtrasados(sigla, LocalDateTime.now()));
        resposta.put("registros", registros);
        return resposta;
    }

    public Map<String, Object> detalhar(Long idevento, String unidade, boolean gestao) {
        NspEvento evento = exigirEvento(idevento, unidade);
        Map<String, Object> corpo = resumo(evento, gestao);
        corpo.put("descricao", evento.getDescricao());
        corpo.put("acaoImediata", evento.getAcaoImediata());
        corpo.put("causaRaiz", evento.getCausaRaiz());
        corpo.put("medicamento", evento.getMedicamento());
        corpo.put("doseAdministrada", evento.getDoseAdministrada());
        corpo.put("viaAdministracao", evento.getViaAdministracao());
        corpo.put("idprescricao", evento.getIdprescricao());
        corpo.put("causalidade", evento.getCausalidade());
        corpo.put("conclusao", evento.getConclusao());
        corpo.put("evitavel", evento.getEvitavel());
        corpo.put("notivisaNotificado", evento.getNotivisaNotificado());
        corpo.put("notivisaProtocolo", evento.getNotivisaProtocolo());
        corpo.put("encerradoEm", evento.getEncerradoEm());
        corpo.put("encerradoPor", evento.getEncerradoPor());
        List<Map<String, Object>> acoes = new ArrayList<>();
        for (NspEventoAcao acao : acaoRepository.listarDoEvento(evento.getIdevento())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("idacao", acao.getIdacao());
            item.put("tipo", acao.getTipo());
            item.put("descricao", acao.getDescricao());
            item.put("responsavel", acao.getResponsavel());
            item.put("prazo", acao.getPrazo());
            item.put("status", acao.getStatus());
            item.put("concluidaEm", acao.getConcluidaEm());
            item.put("criadoEm", acao.getCriadoEm());
            item.put("criadoPor", acao.getCriadoPor());
            acoes.add(item);
        }
        corpo.put("acoes", acoes);
        return corpo;
    }

    /**
     * Painel do nucleo: contagens por status, tipo, classificacao e grau de dano.
     * Sao contagens absolutas - o GHBAM ainda nao tem pacientes-dia consolidado
     * por unidade para calcular taxa por 1000 pacientes-dia.
     */
    public Map<String, Object> indicadores(String unidade, LocalDateTime inicio, LocalDateTime fim) {
        String sigla = exigirUnidade(unidade);
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("unidade", sigla);
        corpo.put("inicio", inicio);
        corpo.put("fim", fim);
        corpo.put("porStatus", agrupar("status", sigla, inicio, fim));
        corpo.put("porTipo", agrupar("tipoIncidente", sigla, inicio, fim));
        corpo.put("porClassificacao", agrupar("classificacao", sigla, inicio, fim));
        corpo.put("porGrauDano", agrupar("grauDano", sigla, inicio, fim));
        corpo.put("abertos", repository.contarAbertos(sigla));
        corpo.put("atrasados", repository.contarAtrasados(sigla, LocalDateTime.now()));
        return corpo;
    }

    private List<Map<String, Object>> agrupar(String campo, String unidade, LocalDateTime inicio, LocalDateTime fim) {
        List<Map<String, Object>> linhas = new ArrayList<>();
        for (Object[] linha : repository.agrupar(campo, unidade, inicio, fim)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chave", linha[0]);
            item.put("total", linha[1]);
            linhas.add(item);
        }
        return linhas;
    }

    // ------------------------------------------------------------------ escrita

    @Transactional
    public NspEvento notificar(NspEventoDto dto, AuthenticatedUser usuario) {
        NspEvento evento = new NspEvento();
        evento.setUnidade(exigirUnidade(dto.getUnidade()));
        evento.setTipoIncidente(exigirValor(dto.getTipoIncidente(), TIPOS, "Tipo de incidente"));
        if (vazio(dto.getDescricao())) {
            throw new IllegalArgumentException("Descreva o que aconteceu.");
        }
        validarCamposMedicamento(evento.getTipoIncidente(), dto);
        evento.setDescricao(dto.getDescricao().trim());
        evento.setAcaoImediata(texto(dto.getAcaoImediata()));
        evento.setMedicamento(texto(dto.getMedicamento()));
        evento.setDoseAdministrada(texto(dto.getDoseAdministrada()));
        evento.setViaAdministracao(texto(dto.getViaAdministracao()));
        evento.setIdprescricao(dto.getIdprescricao());
        evento.setDataOcorrencia(dto.getDataOcorrencia() != null ? dto.getDataOcorrencia() : LocalDateTime.now());
        if (evento.getDataOcorrencia().isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new IllegalArgumentException("A data da ocorrencia nao pode estar no futuro.");
        }
        evento.setLocalOcorrencia(texto(dto.getLocalOcorrencia()));
        evento.setTurno(texto(dto.getTurno()));
        evento.setIdpaciente(dto.getIdpaciente());
        evento.setIdbam(dto.getIdbam());
        evento.setProntuario(texto(dto.getProntuario()));
        evento.setNomePaciente(texto(dto.getNomePaciente()));
        if (dto.getGrauDano() != null && !dto.getGrauDano().isBlank()) {
            evento.setGrauDano(exigirValor(dto.getGrauDano(), GRAUS_DANO, "Grau do dano"));
        }
        evento.setStatus("NOTIFICADO");
        evento.setProtocolo(gerarProtocolo(evento.getUnidade()));
        evento.setNotivisaNotificado(Boolean.FALSE);

        // Notificacao nao punitiva: no anonimo nem o notificante nem o usuario de
        // registro sao gravados. Sem isso o anonimato seria so um rotulo na tela.
        boolean anonimo = Boolean.TRUE.equals(dto.getAnonimo());
        evento.setAnonimo(anonimo);
        if (!anonimo && usuario != null) {
            evento.setNotificanteNome(usuario.nome());
            evento.setNotificanteMatricula(usuario.matricula());
            evento.setNotificanteCargo(usuario.cargo());
            evento.setUsuarioRegistro(usuario.matricula());
        } else {
            evento.setUsuarioRegistro("ANONIMO");
        }

        // Prazo inicial de triagem do nucleo; e recalculado quando o caso e classificado.
        evento.setPrazoAnalise(LocalDateTime.now().plusDays(7));
        repository.persist(evento);
        registrarTrilha(evento, "MUDANCA_STATUS",
                "Notificacao registrada com protocolo " + evento.getProtocolo() + ".",
                anonimo ? "ANONIMO" : matricula(usuario));
        return evento;
    }

    @Transactional
    public Map<String, Object> classificar(Long idevento, String unidade, NspEventoDto dto, AuthenticatedUser usuario) {
        NspEvento evento = exigirEvento(idevento, unidade);
        exigirEmAndamento(evento);
        String classificacao = exigirValor(dto.getClassificacao(), CLASSIFICACOES, "Classificacao");
        String grauDano = exigirValor(dto.getGrauDano(), GRAUS_DANO, "Grau do dano");
        if (CLASSIFICACOES_COM_DANO.contains(classificacao) && "NENHUM".equals(grauDano)) {
            throw new IllegalArgumentException("Evento adverso pressupoe dano: informe o grau do dano.");
        }
        if (!CLASSIFICACOES_COM_DANO.contains(classificacao) && !"NENHUM".equals(grauDano)) {
            throw new IllegalArgumentException("Com dano ao paciente o caso deve ser classificado como evento adverso.");
        }
        if (TIPOS_MEDICAMENTO.contains(evento.getTipoIncidente())) {
            evento.setCausalidade(exigirValor(dto.getCausalidade(), CAUSALIDADES, "Causalidade"));
        }
        evento.setClassificacao(classificacao);
        evento.setGrauDano(grauDano);
        evento.setEvitavel(dto.getEvitavel());
        evento.setCausaRaiz(texto(dto.getCausaRaiz()));
        evento.setPrazoAnalise(prazoPorGravidade(evento));
        if ("NOTIFICADO".equals(evento.getStatus())) {
            evento.setStatus("EM_ANALISE");
        }
        evento.setUsuarioAtualizacao(matricula(usuario));
        registrarTrilha(evento, "MUDANCA_STATUS",
                "Caso classificado como " + classificacao + " (dano " + grauDano + ").", matricula(usuario));
        return detalhar(evento.getIdevento(), unidade, true);
    }

    @Transactional
    public Map<String, Object> atualizarStatus(Long idevento, String unidade, String novoStatus,
            String observacao, AuthenticatedUser usuario) {
        NspEvento evento = exigirEvento(idevento, unidade);
        String destino = novoStatus == null ? "" : novoStatus.trim().toUpperCase();
        if (!STATUS.contains(destino)) {
            throw new IllegalArgumentException("Situacao invalida.");
        }
        if ("ENCERRADO".equals(destino)) {
            throw new IllegalArgumentException("Use o encerramento do caso para concluir a analise.");
        }
        Set<String> permitidos = TRANSICOES.getOrDefault(evento.getStatus(), Set.of());
        if (!permitidos.contains(destino)) {
            throw new IllegalStateException("Nao e possivel mudar de " + evento.getStatus() + " para " + destino + ".");
        }
        if ("PLANO_ACAO".equals(destino) && vazio(evento.getClassificacao())) {
            throw new IllegalArgumentException("Classifique o caso antes de abrir o plano de acao.");
        }
        String anterior = evento.getStatus();
        evento.setStatus(destino);
        evento.setUsuarioAtualizacao(matricula(usuario));
        String detalhe = "Situacao alterada de " + anterior + " para " + destino + ".";
        if (!vazio(observacao)) {
            detalhe = detalhe + " " + observacao.trim();
        }
        registrarTrilha(evento, "MUDANCA_STATUS", detalhe, matricula(usuario));
        return detalhar(evento.getIdevento(), unidade, true);
    }

    @Transactional
    public Map<String, Object> encerrar(Long idevento, String unidade, NspEventoDto dto, AuthenticatedUser usuario) {
        NspEvento evento = exigirEvento(idevento, unidade);
        exigirEmAndamento(evento);
        if (vazio(evento.getClassificacao())) {
            throw new IllegalArgumentException("Classifique o caso antes de encerrar.");
        }
        String conclusao = texto(dto.getConclusao());
        if (vazio(conclusao)) {
            throw new IllegalArgumentException("Informe a conclusao da analise.");
        }
        String causaRaiz = vazio(dto.getCausaRaiz()) ? evento.getCausaRaiz() : texto(dto.getCausaRaiz());
        if (CLASSIFICACOES_COM_DANO.contains(evento.getClassificacao()) && vazio(causaRaiz)) {
            throw new IllegalArgumentException("Evento adverso so encerra com causa raiz registrada.");
        }
        long pendentes = acaoRepository.contarPendentes(evento.getIdevento());
        if (pendentes > 0) {
            throw new IllegalStateException("Ainda ha " + pendentes + " acao(oes) pendente(s) no plano de acao.");
        }
        if (CLASSIFICACOES_COM_DANO.contains(evento.getClassificacao())
                && acaoRepository.contarAcoes(evento.getIdevento()) == 0) {
            throw new IllegalStateException("Evento adverso exige ao menos uma acao corretiva ou preventiva.");
        }
        evento.setCausaRaiz(causaRaiz);
        evento.setConclusao(conclusao);
        if (dto.getEvitavel() != null) {
            evento.setEvitavel(dto.getEvitavel());
        }
        if (dto.getNotivisaNotificado() != null) {
            evento.setNotivisaNotificado(dto.getNotivisaNotificado());
        }
        if (!vazio(dto.getNotivisaProtocolo())) {
            evento.setNotivisaProtocolo(texto(dto.getNotivisaProtocolo()));
        }
        evento.setStatus("ENCERRADO");
        evento.setEncerradoEm(LocalDateTime.now());
        evento.setEncerradoPor(usuario == null ? null : usuario.nome());
        evento.setUsuarioAtualizacao(matricula(usuario));
        registrarTrilha(evento, "MUDANCA_STATUS", "Caso encerrado.", matricula(usuario));
        return detalhar(evento.getIdevento(), unidade, true);
    }

    @Transactional
    public Map<String, Object> adicionarAcao(Long idevento, String unidade, NspAcaoDto dto, AuthenticatedUser usuario) {
        NspEvento evento = exigirEvento(idevento, unidade);
        exigirEmAndamento(evento);
        String tipo = exigirValor(dto.getTipo(), TIPOS_ACAO, "Tipo da acao");
        if (vazio(dto.getDescricao())) {
            throw new IllegalArgumentException("Descreva a acao.");
        }
        NspEventoAcao acao = new NspEventoAcao();
        acao.setIdevento(evento.getIdevento());
        acao.setTipo(tipo);
        acao.setDescricao(dto.getDescricao().trim());
        acao.setResponsavel(texto(dto.getResponsavel()));
        acao.setPrazo(dto.getPrazo());
        acao.setStatus("COMENTARIO".equals(tipo) ? "CONCLUIDA" : "PENDENTE");
        if ("COMENTARIO".equals(tipo)) {
            acao.setConcluidaEm(LocalDateTime.now());
        } else if (acao.getPrazo() == null) {
            throw new IllegalArgumentException("Informe o prazo da acao.");
        } else if (acao.getPrazo().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("O prazo da acao nao pode estar no passado.");
        }
        acao.setCriadoPor(matricula(usuario));
        acaoRepository.persist(acao);
        if (!"COMENTARIO".equals(tipo) && "EM_ANALISE".equals(evento.getStatus())) {
            evento.setStatus("PLANO_ACAO");
        }
        evento.setUsuarioAtualizacao(matricula(usuario));
        return detalhar(evento.getIdevento(), unidade, true);
    }

    @Transactional
    public Map<String, Object> concluirAcao(Long idevento, Long idacao, String unidade, AuthenticatedUser usuario) {
        NspEvento evento = exigirEvento(idevento, unidade);
        NspEventoAcao acao = acaoRepository.findById(idacao);
        if (acao == null || !evento.getIdevento().equals(acao.getIdevento())) {
            throw new IllegalArgumentException("Acao nao encontrada neste caso.");
        }
        if (!"PENDENTE".equals(acao.getStatus())) {
            throw new IllegalStateException("Esta acao ja foi concluida ou cancelada.");
        }
        acao.setStatus("CONCLUIDA");
        acao.setConcluidaEm(LocalDateTime.now());
        evento.setUsuarioAtualizacao(matricula(usuario));
        return detalhar(evento.getIdevento(), unidade, true);
    }

    // ------------------------------------------------------------------ apoio

    private Map<String, Object> resumo(NspEvento evento, boolean gestao) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idevento", evento.getIdevento());
        item.put("protocolo", evento.getProtocolo());
        item.put("unidade", evento.getUnidade());
        item.put("tipoIncidente", evento.getTipoIncidente());
        item.put("classificacao", evento.getClassificacao());
        item.put("grauDano", evento.getGrauDano());
        item.put("status", evento.getStatus());
        item.put("dataOcorrencia", evento.getDataOcorrencia());
        item.put("localOcorrencia", evento.getLocalOcorrencia());
        item.put("turno", evento.getTurno());
        item.put("idpaciente", evento.getIdpaciente());
        item.put("prontuario", evento.getProntuario());
        item.put("nomePaciente", evento.getNomePaciente());
        item.put("dataRegistro", evento.getDataRegistro());
        item.put("prazoAnalise", evento.getPrazoAnalise());
        item.put("atrasado", atrasado(evento));
        item.put("anonimo", Boolean.TRUE.equals(evento.getAnonimo()));
        // O nome de quem notificou so aparece para o nucleo, e nunca no anonimo:
        // fila aberta com o nome do notificante e o que faz a equipe parar de notificar.
        if (gestao && !Boolean.TRUE.equals(evento.getAnonimo())) {
            item.put("notificanteNome", evento.getNotificanteNome());
            item.put("notificanteMatricula", evento.getNotificanteMatricula());
            item.put("notificanteCargo", evento.getNotificanteCargo());
        }
        return item;
    }

    private boolean atrasado(NspEvento evento) {
        return evento.getPrazoAnalise() != null
                && evento.getPrazoAnalise().isBefore(LocalDateTime.now())
                && !"ENCERRADO".equals(evento.getStatus())
                && !"CANCELADO".equals(evento.getStatus());
    }

    /** Grave, obito e never event vao para 48h; moderado 7 dias; o resto 30 dias. */
    private LocalDateTime prazoPorGravidade(NspEvento evento) {
        LocalDateTime base = evento.getDataRegistro() != null ? evento.getDataRegistro() : LocalDateTime.now();
        String grau = evento.getGrauDano() == null ? "" : evento.getGrauDano();
        if ("NEVER_EVENT".equals(evento.getClassificacao()) || "GRAVE".equals(grau) || "OBITO".equals(grau)) {
            return base.plusHours(48);
        }
        if ("MODERADO".equals(grau)) {
            return base.plusDays(7);
        }
        return base.plusDays(30);
    }

    private void registrarTrilha(NspEvento evento, String tipo, String descricao, String usuario) {
        NspEventoAcao trilha = new NspEventoAcao();
        trilha.setIdevento(evento.getIdevento());
        trilha.setTipo(tipo);
        trilha.setDescricao(descricao);
        trilha.setStatus("CONCLUIDA");
        trilha.setConcluidaEm(LocalDateTime.now());
        trilha.setCriadoPor(usuario);
        acaoRepository.persist(trilha);
    }

    private String gerarProtocolo(String unidade) {
        String data = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int tentativa = 0; tentativa < 5; tentativa++) {
            String protocolo = "NSP-" + unidade.toUpperCase() + "-" + data + "-"
                    + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
            if (!repository.protocoloExiste(protocolo)) {
                return protocolo;
            }
        }
        throw new IllegalStateException("Nao foi possivel gerar o protocolo do caso. Tente novamente.");
    }

    private NspEvento exigirEvento(Long idevento, String unidade) {
        NspEvento evento = repository.buscarNaUnidade(idevento, exigirUnidade(unidade));
        if (evento == null) {
            throw new IllegalArgumentException("Caso nao encontrado nesta unidade.");
        }
        return evento;
    }

    private void exigirEmAndamento(NspEvento evento) {
        if ("ENCERRADO".equals(evento.getStatus()) || "CANCELADO".equals(evento.getStatus())) {
            throw new IllegalStateException("Caso " + evento.getStatus().toLowerCase() + ": nao aceita mais alteracao.");
        }
    }

    /**
     * Sem isso, um erro de medicacao ou reacao adversa vira so texto livre em
     * "descricao" - nunca daria pra cruzar com o CDSS nem gerar indicador de
     * farmacovigilancia por principio ativo.
     */
    static void validarCamposMedicamento(String tipoIncidente, NspEventoDto dto) {
        if (!TIPOS_MEDICAMENTO.contains(tipoIncidente)) {
            return;
        }
        if (dto.getMedicamento() == null || dto.getMedicamento().isBlank()) {
            throw new IllegalArgumentException("Informe o medicamento envolvido.");
        }
    }

    private String exigirUnidade(String unidade) {
        String sigla = unidade == null ? "" : unidade.split(":", 2)[0].trim();
        if (sigla.isEmpty() || "TODAS".equalsIgnoreCase(sigla)) {
            throw new IllegalArgumentException("Unidade e obrigatoria para o Nucleo de Seguranca do Paciente.");
        }
        return sigla;
    }

    private String exigirValor(String valor, Set<String> permitidos, String rotulo) {
        String normalizado = valor == null ? "" : valor.trim().toUpperCase();
        if (!permitidos.contains(normalizado)) {
            throw new IllegalArgumentException(rotulo + " invalido.");
        }
        return normalizado;
    }

    private String matricula(AuthenticatedUser usuario) {
        return usuario == null ? null : usuario.matricula();
    }

    private String texto(String valor) {
        return valor == null ? null : valor.trim();
    }

    private boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}

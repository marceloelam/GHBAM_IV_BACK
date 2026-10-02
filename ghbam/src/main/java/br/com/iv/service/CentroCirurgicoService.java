package br.com.iv.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.CirurgiaAgendamentoDto;
import br.com.iv.dto.LogDto;
import br.com.iv.dto.CirurgiaEquipeDto;
import br.com.iv.dto.CirurgiaProcedimentoDto;
import br.com.iv.dto.CirurgiaSuspensaoDto;
import br.com.iv.entity.CirurgiaAgendamento;
import br.com.iv.entity.CirurgiaChecklist;
import br.com.iv.entity.CirurgiaContagem;
import br.com.iv.entity.CirurgiaDescricao;
import br.com.iv.entity.CirurgiaEquipe;
import br.com.iv.entity.CirurgiaFichaAnestesica;
import br.com.iv.entity.CirurgiaListaEspera;
import br.com.iv.entity.CirurgiaOpme;
import br.com.iv.entity.CirurgiaProcedimento;
import br.com.iv.entity.CirurgiaSala;
import br.com.iv.entity.CirurgiaSuspensao;
import br.com.iv.entity.CirurgiaTempo;
import br.com.iv.enums.CirurgiaStatus;
import br.com.iv.repository.CirurgiaAgendamentoRepository;
import br.com.iv.repository.CirurgiaSalaRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class CentroCirurgicoService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Inject
    CirurgiaAgendamentoRepository agendamentoRepository;

    @Inject
    CirurgiaSalaRepository salaRepository;

    @Inject
    CirurgiaFaturamentoService cirurgiaFaturamentoService;

    @Inject
    LogService logService;

    @Inject
    FluxoCirurgicoAssistencial fluxoCirurgicoAssistencial;

    /**
     * Registra o evento no log da unidade.
     *
     * Assincrono de proposito: o log nao pode segurar nem derrubar a operacao
     * clinica que o originou.
     */
    void registrarLog(String evento, String unidade, AuthenticatedUser user) {
        LogDto dto = new LogDto();
        dto.setData(LocalDate.now());
        dto.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        dto.setEvento(evento + (user == null ? "" : " - por " + texto(user.nome()) + " (matricula " + texto(user.matricula()) + ")"));
        dto.setUnidade(texto(unidade).isEmpty() ? "GERAL" : texto(unidade));
        logService.salvarLogAsync(dto);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private LocalDateTime parseData(String valor) {
        String v = texto(valor);
        if (v.isBlank()) {
            return null;
        }
        try {
            if (v.length() == 16) {
                return LocalDateTime.parse(v, ISO);
            }
            return LocalDateTime.parse(v.length() > 19 ? v.substring(0, 19) : v);
        } catch (Exception e) {
            throw new IllegalArgumentException("Data invalida: " + valor);
        }
    }

    private String gerarNumero(String unidade) {
        String prefixo = "CIR-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long sequencial = agendamentoRepository.count("numero like ?1", prefixo + "%") + 1;
        String candidato = String.format("%s-%05d", prefixo, sequencial);
        while (agendamentoRepository.count("numero", candidato) > 0) {
            sequencial++;
            candidato = String.format("%s-%05d", prefixo, sequencial);
        }
        return candidato;
    }

    private void validarSalaDisponivel(Integer idsala, LocalDateTime inicio, int duracaoMin, Integer ignorar) {
        if (idsala == null) {
            return;
        }
        CirurgiaSala sala = salaRepository.findById(idsala.longValue());
        if (sala == null) {
            throw new IllegalArgumentException("Sala cirurgica nao encontrada.");
        }
        if (!sala.isAtiva()) {
            throw new IllegalArgumentException("Sala cirurgica esta inativa.");
        }
        LocalDateTime fim = inicio.plusMinutes(duracaoMin + sala.getTempoLimpezaMin());
        List<CirurgiaAgendamento> conflitos = agendamentoRepository.conflitosDeSala(idsala, inicio, fim, ignorar);
        if (!conflitos.isEmpty()) {
            CirurgiaAgendamento c = conflitos.get(0);
            throw new IllegalArgumentException("Sala ocupada no horario pela cirurgia " + c.getNumero()
                    + " (" + c.getDataPrevista().format(ISO) + ").");
        }
    }

    private void gravarProcedimentos(int idcirurgia, List<CirurgiaProcedimentoDto> itens) {
        CirurgiaProcedimento.delete("idcirurgia", idcirurgia);
        if (itens == null || itens.isEmpty()) {
            return;
        }
        boolean temPrincipal = itens.stream().anyMatch(i -> Boolean.TRUE.equals(i.getPrincipal()));
        for (int i = 0; i < itens.size(); i++) {
            CirurgiaProcedimentoDto dto = itens.get(i);
            if (dto == null || texto(dto.getDescricao()).isBlank()) {
                continue;
            }
            CirurgiaProcedimento p = new CirurgiaProcedimento();
            p.setIdcirurgia(idcirurgia);
            p.setCodigo(texto(dto.getCodigo()));
            p.setDescricao(texto(dto.getDescricao()));
            p.setTabela(texto(dto.getTabela()).isBlank() ? "SIGTAP" : texto(dto.getTabela()));
            p.setPrincipal(Boolean.TRUE.equals(dto.getPrincipal()) || (!temPrincipal && i == 0));
            p.setQuantidade(dto.getQuantidade() == null || dto.getQuantidade() < 1 ? 1 : dto.getQuantidade());
            p.setLateralidade(texto(dto.getLateralidade()));
            p.setRealizado(Boolean.TRUE.equals(dto.getRealizado()));
            p.persist();
        }
    }

    private void gravarEquipe(int idcirurgia, List<CirurgiaEquipeDto> itens, AuthenticatedUser user) {
        CirurgiaEquipe.delete("idcirurgia", idcirurgia);
        if (itens == null || itens.isEmpty()) {
            return;
        }
        for (CirurgiaEquipeDto dto : itens) {
            if (dto == null || texto(dto.getNome()).isBlank()) {
                continue;
            }
            CirurgiaEquipe e = new CirurgiaEquipe();
            e.setIdcirurgia(idcirurgia);
            e.setIdservidor(dto.getIdservidor());
            e.setMatricula(texto(dto.getMatricula()));
            e.setNome(texto(dto.getNome()));
            e.setFuncao(texto(dto.getFuncao()).isBlank() ? "OUTRO" : texto(dto.getFuncao()).toUpperCase());
            e.setConselho(texto(dto.getConselho()));
            e.setCbo(texto(dto.getCbo()));
            e.setPresente(Boolean.TRUE.equals(dto.getPresente()));
            e.setUsuarioCriador(user == null ? null : user.matricula());
            e.persist();
        }
    }

    @Transactional
    public CirurgiaAgendamento criar(CirurgiaAgendamentoDto dto, AuthenticatedUser user) {
        if (dto == null || dto.getIdpaciente() <= 0) {
            throw new IllegalArgumentException("Paciente e obrigatorio.");
        }
        LocalDateTime dataPrevista = parseData(dto.getDataPrevista());
        if (dataPrevista == null) {
            throw new IllegalArgumentException("Data prevista e obrigatoria.");
        }
        String unidade = texto(dto.getUnidade());
        if (unidade.isBlank() && user != null) {
            unidade = texto(user.unidade());
        }
        if (unidade.isBlank() || "TODAS".equalsIgnoreCase(unidade)) {
            throw new IllegalArgumentException("Unidade e obrigatoria para agendar cirurgia.");
        }
        // Sem sala a cirurgia caia no bloco "sem sala" do mapa e a checagem de
        // conflito de horario nao tinha o que comparar: duas cirurgias podiam ser
        // marcadas para a mesma hora sem ninguem ser avisado.
        if (dto.getIdsala() == null || dto.getIdsala() <= 0) {
            throw new IllegalArgumentException("Sala cirurgica e obrigatoria para agendar.");
        }
        // CID ausente so aparece na glosa do faturamento, com a competencia ja
        // fechada.
        if (texto(dto.getCid()).isBlank()) {
            throw new IllegalArgumentException("CID e obrigatorio para agendar cirurgia.");
        }
        int duracao = dto.getDuracaoPrevistaMin() == null || dto.getDuracaoPrevistaMin() < 15
                ? 60
                : dto.getDuracaoPrevistaMin();
        validarSalaDisponivel(dto.getIdsala(), dataPrevista, duracao, null);

        CirurgiaAgendamento c = new CirurgiaAgendamento();
        c.setNumero(gerarNumero(unidade));
        c.setIdpaciente(dto.getIdpaciente());
        c.setNomepaciente(texto(dto.getNomepaciente()));
        c.setCnspaciente(texto(dto.getCnspaciente()));
        c.setIdbam(dto.getIdbam());
        c.setIdinternacao(dto.getIdinternacao());
        c.setUnidade(unidade);
        c.setCnes(texto(dto.getCnes()));
        c.setIdsala(dto.getIdsala());
        if (dto.getIdsala() != null) {
            CirurgiaSala sala = salaRepository.findById(dto.getIdsala().longValue());
            c.setNomesala(sala == null ? null : sala.getNome());
        }
        c.setDataPrevista(dataPrevista);
        c.setDuracaoPrevistaMin(duracao);
        c.setCarater(texto(dto.getCarater()).isBlank() ? "ELETIVA" : texto(dto.getCarater()).toUpperCase());
        c.setPorte(texto(dto.getPorte()));
        c.setEspecialidade(texto(dto.getEspecialidade()));
        c.setIdcirurgiao(dto.getIdcirurgiao());
        c.setNomecirurgiao(texto(dto.getNomecirurgiao()));
        c.setTipoAnestesia(texto(dto.getTipoAnestesia()));
        c.setLateralidade(texto(dto.getLateralidade()));
        c.setCid(texto(dto.getCid()));
        c.setDiagnostico(texto(dto.getDiagnostico()));
        c.setStatus(CirurgiaStatus.AGENDADA.name());
        c.setNecessitaUti(Boolean.TRUE.equals(dto.getNecessitaUti()));
        c.setNecessitaHemocomponente(Boolean.TRUE.equals(dto.getNecessitaHemocomponente()));
        c.setReservaSangue(texto(dto.getReservaSangue()));
        c.setObservacao(texto(dto.getObservacao()));
        c.setUsuarioCriador(user == null ? null : user.matricula());
        c.persist();

        gravarProcedimentos(c.getIdcirurgia(), dto.getProcedimentos());
        gravarEquipe(c.getIdcirurgia(), dto.getEquipe(), user);
        fluxoCirurgicoAssistencial.sincronizarDemandas(c, user);

        CirurgiaTempo tempo = new CirurgiaTempo();
        tempo.setIdcirurgia(c.getIdcirurgia());
        tempo.persist();

        if (dto.getIdespera() != null) {
            CirurgiaListaEspera espera = CirurgiaListaEspera.findById(dto.getIdespera().longValue());
            if (espera != null) {
                espera.setStatus("AGENDADA");
                espera.setIdcirurgia(c.getIdcirurgia());
                espera.setDataPrevista(dataPrevista);
                espera.setAtualizadoEm(LocalDateTime.now());
                registrarLog("LISTA DE ESPERA CIRURGICA AGENDADA - item " + espera.getIdespera() + " ("
                        + texto(espera.getNomepaciente()) + ") vinculado a cirurgia " + texto(c.getNumero()),
                        c.getUnidade(), user);
            }
        }
        return c;
    }

    @Transactional
    public CirurgiaAgendamento atualizar(int idcirurgia, CirurgiaAgendamentoDto dto, AuthenticatedUser user) {
        CirurgiaAgendamento c = agendamentoRepository.findById((long) idcirurgia);
        if (c == null) {
            throw new IllegalArgumentException("Cirurgia nao encontrada.");
        }
        if (CirurgiaStatus.ENCERRADA.name().equals(c.getStatus())
                || CirurgiaStatus.CANCELADA.name().equals(c.getStatus())) {
            throw new IllegalArgumentException("Cirurgia encerrada ou cancelada nao pode ser alterada.");
        }
        if (dto.getDataPrevista() != null) {
            LocalDateTime nova = parseData(dto.getDataPrevista());
            if (nova != null) {
                c.setDataPrevista(nova);
            }
        }
        if (dto.getDuracaoPrevistaMin() != null && dto.getDuracaoPrevistaMin() >= 15) {
            c.setDuracaoPrevistaMin(dto.getDuracaoPrevistaMin());
        }
        if (dto.getIdsala() != null) {
            CirurgiaSala sala = salaRepository.findById(dto.getIdsala().longValue());
            c.setIdsala(dto.getIdsala());
            c.setNomesala(sala == null ? null : sala.getNome());
        }
        validarSalaDisponivel(c.getIdsala(), c.getDataPrevista(), c.getDuracaoPrevistaMin(), c.getIdcirurgia());

        if (dto.getCarater() != null) {
            c.setCarater(texto(dto.getCarater()).toUpperCase());
        }
        if (dto.getPorte() != null) {
            c.setPorte(texto(dto.getPorte()));
        }
        if (dto.getEspecialidade() != null) {
            c.setEspecialidade(texto(dto.getEspecialidade()));
        }
        if (dto.getIdcirurgiao() != null) {
            c.setIdcirurgiao(dto.getIdcirurgiao());
        }
        if (dto.getNomecirurgiao() != null) {
            c.setNomecirurgiao(texto(dto.getNomecirurgiao()));
        }
        if (dto.getTipoAnestesia() != null) {
            c.setTipoAnestesia(texto(dto.getTipoAnestesia()));
        }
        if (dto.getLateralidade() != null) {
            c.setLateralidade(texto(dto.getLateralidade()));
        }
        if (dto.getCid() != null) {
            c.setCid(texto(dto.getCid()));
        }
        if (dto.getDiagnostico() != null) {
            c.setDiagnostico(texto(dto.getDiagnostico()));
        }
        if (dto.getNecessitaUti() != null) {
            c.setNecessitaUti(dto.getNecessitaUti());
        }
        if (dto.getNecessitaHemocomponente() != null) {
            c.setNecessitaHemocomponente(dto.getNecessitaHemocomponente());
        }
        if (dto.getReservaSangue() != null) {
            c.setReservaSangue(texto(dto.getReservaSangue()));
        }
        if (dto.getObservacao() != null) {
            c.setObservacao(texto(dto.getObservacao()));
        }
        if (dto.getProcedimentos() != null) {
            gravarProcedimentos(c.getIdcirurgia(), dto.getProcedimentos());
        }
        if (dto.getEquipe() != null) {
            gravarEquipe(c.getIdcirurgia(), dto.getEquipe(), user);
        }
        fluxoCirurgicoAssistencial.sincronizarDemandas(c, user);
        c.setAtualizadoEm(LocalDateTime.now());
        c.setUsuarioAtualizacao(user == null ? null : user.matricula());
        return c;
    }

    public List<CirurgiaAgendamento> listar(String unidade, String status, String especialidade, String busca,
            int pagina, int tamanho) {
        int tam = tamanho <= 0 || tamanho > 200 ? 50 : tamanho;
        return agendamentoRepository.listarFiltrado(unidade, status, especialidade, busca, Math.max(pagina, 0), tam);
    }

    public Map<String, Object> mapaCirurgico(String unidade, String data, Integer idsala) {
        LocalDateTime referencia = parseData(data);
        if (referencia == null) {
            referencia = LocalDateTime.now();
        }
        LocalDateTime inicio = referencia.toLocalDate().atStartOfDay();
        LocalDateTime fim = inicio.plusDays(1).minusSeconds(1);
        List<CirurgiaAgendamento> cirurgias = agendamentoRepository.mapaDoDia(unidade, inicio, fim, idsala);
        List<CirurgiaSala> salas = salaRepository.listarPorUnidade(unidade, true);

        List<Map<String, Object>> blocos = new ArrayList<>();
        for (CirurgiaSala sala : salas) {
            List<Map<String, Object>> agenda = new ArrayList<>();
            int minutosOcupados = 0;
            for (CirurgiaAgendamento c : cirurgias) {
                if (sala.getIdsala().equals(c.getIdsala())) {
                    agenda.add(resumo(c));
                    if (!CirurgiaStatus.SUSPENSA.name().equals(c.getStatus())
                            && !CirurgiaStatus.CANCELADA.name().equals(c.getStatus())) {
                        minutosOcupados += c.getDuracaoPrevistaMin();
                    }
                }
            }
            Map<String, Object> bloco = new LinkedHashMap<>();
            bloco.put("idsala", sala.getIdsala());
            bloco.put("nome", sala.getNome());
            bloco.put("horaInicioExpediente", sala.getHoraInicioExpediente());
            bloco.put("horaFimExpediente", sala.getHoraFimExpediente());
            bloco.put("tempoLimpezaMin", sala.getTempoLimpezaMin());
            bloco.put("minutosOcupados", minutosOcupados);
            bloco.put("cirurgias", agenda);
            blocos.add(bloco);
        }

        List<Map<String, Object>> semSala = new ArrayList<>();
        for (CirurgiaAgendamento c : cirurgias) {
            if (c.getIdsala() == null) {
                semSala.add(resumo(c));
            }
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("data", inicio.toLocalDate().toString());
        resposta.put("unidade", unidade);
        resposta.put("salas", blocos);
        resposta.put("semSala", semSala);
        resposta.put("total", cirurgias.size());
        return resposta;
    }

    private Map<String, Object> resumo(CirurgiaAgendamento c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idcirurgia", c.getIdcirurgia());
        m.put("numero", c.getNumero());
        m.put("idpaciente", c.getIdpaciente());
        m.put("nomepaciente", c.getNomepaciente());
        m.put("dataPrevista", c.getDataPrevista());
        m.put("duracaoPrevistaMin", c.getDuracaoPrevistaMin());
        m.put("status", c.getStatus());
        m.put("carater", c.getCarater());
        m.put("especialidade", c.getEspecialidade());
        m.put("nomecirurgiao", c.getNomecirurgiao());
        m.put("tipoAnestesia", c.getTipoAnestesia());
        m.put("necessitaUti", c.isNecessitaUti());
        m.put("necessitaHemocomponente", c.isNecessitaHemocomponente());
        m.put("procedimentos", CirurgiaProcedimento.list("idcirurgia", c.getIdcirurgia()));
        m.put("demandasAssistenciais", fluxoCirurgicoAssistencial.listarDemandas(c));
        return m;
    }

    public Map<String, Object> detalhe(int idcirurgia) {
        CirurgiaAgendamento c = agendamentoRepository.findById((long) idcirurgia);
        if (c == null) {
            throw new IllegalArgumentException("Cirurgia nao encontrada.");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cirurgia", c);
        m.put("procedimentos", CirurgiaProcedimento.list("idcirurgia", idcirurgia));
        m.put("equipe", CirurgiaEquipe.list("idcirurgia", idcirurgia));
        m.put("tempos", CirurgiaTempo.find("idcirurgia", idcirurgia).firstResult());
        m.put("checklist", CirurgiaChecklist.list("idcirurgia", idcirurgia));
        m.put("opme", CirurgiaOpme.list("idcirurgia", idcirurgia));
        m.put("contagens", CirurgiaContagem.list("idcirurgia", idcirurgia));
        m.put("descricao", CirurgiaDescricao.find("idcirurgia", idcirurgia).firstResult());
        m.put("fichaAnestesica", CirurgiaFichaAnestesica.find("idcirurgia", idcirurgia).firstResult());
        m.put("demandasAssistenciais", fluxoCirurgicoAssistencial.listarDemandas(c));
        m.put("suspensoes", CirurgiaSuspensao.list("idcirurgia", idcirurgia));
        // Fecha o prontuario cirurgico impresso: sem isto a folha nao mostra se o
        // ato chegou ao faturamento, que e a pergunta do fim do processo.
        m.put("producao", cirurgiaFaturamentoService.producaoDaCirurgia(idcirurgia));
        return m;
    }

    @Transactional
    public CirurgiaAgendamento confirmar(int idcirurgia, AuthenticatedUser user) {
        CirurgiaAgendamento c = agendamentoRepository.findById((long) idcirurgia);
        if (c == null) {
            throw new IllegalArgumentException("Cirurgia nao encontrada.");
        }
        if (!CirurgiaStatus.AGENDADA.name().equals(c.getStatus())) {
            throw new IllegalArgumentException("Somente cirurgia agendada pode ser confirmada.");
        }
        if (c.getIdsala() == null) {
            throw new IllegalArgumentException("Defina a sala cirurgica antes de confirmar.");
        }
        long procedimentos = CirurgiaProcedimento.count("idcirurgia", idcirurgia);
        if (procedimentos == 0) {
            throw new IllegalArgumentException("Informe ao menos um procedimento antes de confirmar.");
        }
        long cirurgioes = CirurgiaEquipe.count("idcirurgia = ?1 and funcao = ?2", idcirurgia, "CIRURGIAO");
        if (cirurgioes == 0) {
            throw new IllegalArgumentException("Informe o cirurgiao responsavel antes de confirmar.");
        }
        c.setStatus(CirurgiaStatus.CONFIRMADA.name());
        c.setAtualizadoEm(LocalDateTime.now());
        c.setUsuarioAtualizacao(user == null ? null : user.matricula());
        return c;
    }

    @Transactional
    public CirurgiaSuspensao suspender(int idcirurgia, CirurgiaSuspensaoDto dto, AuthenticatedUser user) {
        CirurgiaAgendamento c = agendamentoRepository.findById((long) idcirurgia);
        if (c == null) {
            throw new IllegalArgumentException("Cirurgia nao encontrada.");
        }
        if (CirurgiaStatus.ENCERRADA.name().equals(c.getStatus())) {
            throw new IllegalArgumentException("Cirurgia encerrada nao pode ser suspensa.");
        }
        if (dto == null || texto(dto.getCategoria()).isBlank() || texto(dto.getMotivo()).isBlank()) {
            throw new IllegalArgumentException("Categoria e motivo da suspensao sao obrigatorios.");
        }
        CirurgiaSuspensao s = new CirurgiaSuspensao();
        s.setIdcirurgia(idcirurgia);
        s.setCategoria(texto(dto.getCategoria()).toUpperCase());
        s.setMotivo(texto(dto.getMotivo()));
        s.setResponsavel(user == null ? null : user.nome());
        s.setUsuarioRegistro(user == null ? null : user.matricula());
        s.persist();

        c.setStatus(CirurgiaStatus.SUSPENSA.name());
        c.setAtualizadoEm(LocalDateTime.now());
        c.setUsuarioAtualizacao(user == null ? null : user.matricula());
        fluxoCirurgicoAssistencial.cancelarDemandasAbertas(c, "Cirurgia suspensa: " + texto(dto.getMotivo()), user);

        if (Boolean.TRUE.equals(dto.getDevolverParaListaEspera())) {
            CirurgiaListaEspera espera = CirurgiaListaEspera.find("idcirurgia", idcirurgia).firstResult();
            if (espera != null) {
                espera.setStatus("AGUARDANDO");
                espera.setIdcirurgia(null);
                espera.setAtualizadoEm(LocalDateTime.now());
            }
        }
        return s;
    }

    @Transactional
    public CirurgiaAgendamento cancelar(int idcirurgia, String motivo, AuthenticatedUser user) {
        CirurgiaAgendamento c = agendamentoRepository.findById((long) idcirurgia);
        if (c == null) {
            throw new IllegalArgumentException("Cirurgia nao encontrada.");
        }
        if (CirurgiaStatus.ENCERRADA.name().equals(c.getStatus())) {
            throw new IllegalArgumentException("Cirurgia encerrada nao pode ser cancelada.");
        }
        c.setStatus(CirurgiaStatus.CANCELADA.name());
        c.setObservacao(texto(c.getObservacao()) + "\nCancelada: " + texto(motivo));
        c.setAtualizadoEm(LocalDateTime.now());
        c.setUsuarioAtualizacao(user == null ? null : user.matricula());
        fluxoCirurgicoAssistencial.cancelarDemandasAbertas(c, "Cirurgia cancelada: " + texto(motivo), user);
        return c;
    }

    /**
     * Exclui um agendamento que ainda nao virou ato.
     *
     * So vale enquanto ninguem entrou em sala: a partir dai existe registro
     * assistencial (checklist, tempos, contagem) e apagar destruiria prontuario -
     * nesses casos o caminho e suspender ou cancelar, que preservam o rastro.
     * Chamado pelo cancelamento da lista de espera, que desfaz o agendamento que
     * ele mesmo originou.
     */
    @Transactional
    public boolean excluirAgendamentoNaoIniciado(int idcirurgia, AuthenticatedUser user) {
        CirurgiaAgendamento c = agendamentoRepository.findById((long) idcirurgia);
        if (c == null) {
            return false;
        }
        String status = texto(c.getStatus());
        // Cirurgia ja cancelada ou suspensa nao tem ato a preservar nem agendamento
        // a desfazer: o vinculo apenas se solta e o item da fila segue para
        // CANCELADA. Recusar aqui deixava o item preso em AGENDADA para sempre.
        if (CirurgiaStatus.CANCELADA.name().equals(status) || CirurgiaStatus.SUSPENSA.name().equals(status)) {
            return false;
        }
        // A partir da entrada em sala existe registro assistencial: apagar seria
        // destruir prontuario.
        if (!CirurgiaStatus.AGENDADA.name().equals(status) && !CirurgiaStatus.CONFIRMADA.name().equals(status)) {
            throw new IllegalArgumentException(
                    "A cirurgia " + texto(c.getNumero()) + " ja esta em execucao ou encerrada (" + status
                            + ") e nao pode ser excluida. Suspenda ou cancele a cirurgia.");
        }
        CirurgiaProcedimento.delete("idcirurgia", idcirurgia);
        CirurgiaEquipe.delete("idcirurgia", idcirurgia);
        CirurgiaTempo.delete("idcirurgia", idcirurgia);
        CirurgiaChecklist.delete("idcirurgia", idcirurgia);
        CirurgiaOpme.delete("idcirurgia", idcirurgia);
        CirurgiaContagem.delete("idcirurgia", idcirurgia);
        CirurgiaDescricao.delete("idcirurgia", idcirurgia);
        CirurgiaFichaAnestesica.delete("idcirurgia", idcirurgia);
        CirurgiaSuspensao.delete("idcirurgia", idcirurgia);
        String numero = texto(c.getNumero());
        String unidade = c.getUnidade();
        agendamentoRepository.delete(c);
        registrarLog("CIRURGIA EXCLUIDA - " + numero + " (id " + idcirurgia
                + ") removida do mapa pelo cancelamento na lista de espera", unidade, user);
        return true;
    }

    public Map<String, Object> indicadores(String unidade, String dataInicio, String dataFim) {
        LocalDateTime inicio = parseData(dataInicio);
        LocalDateTime fim = parseData(dataFim);
        if (inicio == null) {
            inicio = LocalDateTime.now().minusDays(30).toLocalDate().atStartOfDay();
        }
        if (fim == null) {
            fim = LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay();
        }
        List<CirurgiaAgendamento> periodo = agendamentoRepository.mapaDoDia(unidade, inicio, fim, null);

        Map<String, Integer> porStatus = new LinkedHashMap<>();
        Map<String, Integer> motivosSuspensao = new LinkedHashMap<>();
        long somaTempoSala = 0;
        int contagemTempoSala = 0;
        for (CirurgiaAgendamento c : periodo) {
            porStatus.merge(c.getStatus(), 1, Integer::sum);
            if (CirurgiaStatus.SUSPENSA.name().equals(c.getStatus())) {
                List<CirurgiaSuspensao> suspensoes = CirurgiaSuspensao.list("idcirurgia", c.getIdcirurgia());
                for (CirurgiaSuspensao s : suspensoes) {
                    motivosSuspensao.merge(s.getCategoria(), 1, Integer::sum);
                }
            }
            CirurgiaTempo t = CirurgiaTempo.find("idcirurgia", c.getIdcirurgia()).firstResult();
            if (t != null && t.getEntradaSala() != null && t.getSaidaSala() != null) {
                somaTempoSala += java.time.temporal.ChronoUnit.MINUTES.between(t.getEntradaSala(), t.getSaidaSala());
                contagemTempoSala++;
            }
        }
        int total = periodo.size();
        int suspensas = porStatus.getOrDefault(CirurgiaStatus.SUSPENSA.name(), 0);
        int encerradas = porStatus.getOrDefault(CirurgiaStatus.ENCERRADA.name(), 0);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unidade", unidade);
        m.put("periodoInicio", inicio.toLocalDate().toString());
        m.put("periodoFim", fim.toLocalDate().toString());
        m.put("total", total);
        m.put("porStatus", porStatus);
        m.put("encerradas", encerradas);
        m.put("suspensas", suspensas);
        m.put("taxaSuspensao", total == 0 ? 0.0 : Math.round((suspensas * 10000.0) / total) / 100.0);
        m.put("motivosSuspensao", motivosSuspensao);
        m.put("tempoMedioSalaMin", contagemTempoSala == 0 ? 0 : somaTempoSala / contagemTempoSala);
        m.put("cirurgiasComTempoRegistrado", contagemTempoSala);
        return m;
    }

    public List<CirurgiaAgendamento> porPaciente(int idpaciente) {
        return agendamentoRepository.porPaciente(idpaciente);
    }
}

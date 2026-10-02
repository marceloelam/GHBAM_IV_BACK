package br.com.iv.service;

import br.com.iv.entity.CirurgiaAgendamento;
import br.com.iv.entity.EvidenciaAssistencial;
import br.com.iv.dto.LogDto;
import br.com.iv.repository.EvidenciaAssistencialRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FluxoCirurgicoAssistencial {

    static final String ORIGEM_CENTRO_CIRURGICO = "CENTRO_CIRURGICO";
    private static final String ABERTO = "ABERTO";
    private static final String CONCLUIDO = "CONCLUIDO";
    private static final String CANCELADO = "CANCELADO";

    @Inject
    EvidenciaAssistencialRepository evidencias;

    @Inject
    LogService logService;

    record Demanda(String modulo, String tipo) {}

    static List<Demanda> demandasParaCirurgia(int idcirurgia, String numeroCirurgia, boolean necessitaHemocomponente) {
        Demanda cme = new Demanda("CME", "SOLICITACAO_CIRURGICA");
        return necessitaHemocomponente
                ? List.of(cme, new Demanda("HEMOTERAPIA", "RESERVA_CIRURGICA"))
                : List.of(cme);
    }

    static boolean podeIniciar(String statusCme, String statusHemoterapia, boolean necessitaHemocomponente) {
        return CONCLUIDO.equals(statusCme) && (!necessitaHemocomponente || CONCLUIDO.equals(statusHemoterapia));
    }

    public void sincronizarDemandas(CirurgiaAgendamento cirurgia, AuthenticatedUser user) {
        for (Demanda demanda : demandasParaCirurgia(cirurgia.getIdcirurgia(), cirurgia.getNumero(), cirurgia.isNecessitaHemocomponente())) {
            sincronizarDemanda(cirurgia, demanda, user);
        }
        if (!cirurgia.isNecessitaHemocomponente()) {
            cancelarAberta(cirurgia, "HEMOTERAPIA", "Necessidade de hemocomponente retirada do agendamento.", user);
        }
    }

    public void cancelarDemandasAbertas(CirurgiaAgendamento cirurgia, String motivo, AuthenticatedUser user) {
        for (EvidenciaAssistencial evidencia : evidencias.listarPorOrigem(ORIGEM_CENTRO_CIRURGICO, String.valueOf(cirurgia.getIdcirurgia()))) {
            if (ABERTO.equals(evidencia.status)) {
                evidencia.status = CANCELADO;
                evidencia.descricao = texto(evidencia.descricao) + " Cancelada: " + texto(motivo);
                evidencia.dataAtualizacao = LocalDateTime.now();
                evidencia.usuarioAtualizacao = user == null ? null : user.matricula();
                registrarLog("DEMANDA " + evidencia.modulo + " CANCELADA - cirurgia " + cirurgia.getNumero(), cirurgia, user);
            }
        }
    }

    public void garantirLiberacoesParaInicio(CirurgiaAgendamento cirurgia) {
        Map<String, EvidenciaAssistencial> porModulo = demandasDaCirurgia(cirurgia);
        EvidenciaAssistencial cme = porModulo.get("CME");
        EvidenciaAssistencial hemoterapia = porModulo.get("HEMOTERAPIA");
        if (!podeIniciar(status(cme), status(hemoterapia), cirurgia.isNecessitaHemocomponente())) {
            StringBuilder pendencias = new StringBuilder();
            if (!CONCLUIDO.equals(status(cme))) pendencias.append("CME/esterilizacao");
            if (cirurgia.isNecessitaHemocomponente() && !CONCLUIDO.equals(status(hemoterapia))) {
                if (!pendencias.isEmpty()) pendencias.append(" e ");
                pendencias.append("hemoterapia");
            }
            throw new IllegalArgumentException("A cirurgia nao pode iniciar sem liberacao de " + pendencias + ".");
        }
    }

    public List<EvidenciaAssistencial> listarDemandas(CirurgiaAgendamento cirurgia) {
        return evidencias.listarPorOrigem(ORIGEM_CENTRO_CIRURGICO, String.valueOf(cirurgia.getIdcirurgia()));
    }

    public void enfileirarDocumentoRnds(CirurgiaAgendamento cirurgia, AuthenticatedUser user) {
        String origem = "DOCUMENTO_CIRURGICO";
        EvidenciaAssistencial existente = evidencias.buscarPorOrigemEModulo(origem,
                String.valueOf(cirurgia.getIdcirurgia()), "RNDS");
        if (existente != null) return;
        EvidenciaAssistencial evidencia = new EvidenciaAssistencial();
        evidencia.modulo = "RNDS";
        evidencia.tipo = "PENDENCIA_ENVIO";
        evidencia.status = ABERTO;
        evidencia.unidade = cirurgia.getUnidade();
        evidencia.idpaciente = cirurgia.getIdpaciente();
        evidencia.nomepaciente = cirurgia.getNomepaciente();
        evidencia.referencia = "Descricao cirurgica assinada - " + cirurgia.getNumero();
        evidencia.origemModulo = origem;
        evidencia.origemId = String.valueOf(cirurgia.getIdcirurgia());
        evidencia.origemDescricao = "Documento cirurgico " + cirurgia.getNumero();
        evidencia.responsavel = "RNDS";
        evidencia.descricao = "Documento cirurgico assinado aguardando envio ou reprocessamento RNDS/FHIR.";
        evidencia.dataRegistro = LocalDateTime.now();
        evidencia.usuarioRegistro = user == null ? null : user.matricula();
        evidencias.persist(evidencia);
        registrarLog("PENDENCIA RNDS CRIADA - cirurgia " + cirurgia.getNumero(), cirurgia, user);
    }

    private void sincronizarDemanda(CirurgiaAgendamento cirurgia, Demanda demanda, AuthenticatedUser user) {
        EvidenciaAssistencial evidencia = evidencias.buscarPorOrigemEModulo(
                ORIGEM_CENTRO_CIRURGICO, String.valueOf(cirurgia.getIdcirurgia()), demanda.modulo());
        String referencia = "Cirurgia " + cirurgia.getNumero() + " / " + texto(cirurgia.getNomesala());
        String descricao = demanda.modulo().equals("CME")
                ? "Demanda cirurgica para preparo e liberacao de material esterilizado."
                : "Solicitacao cirurgica de avaliacao e reserva de hemocomponente: " + texto(cirurgia.getReservaSangue());
        boolean mudou = evidencia != null && (!texto(evidencia.referencia).equals(referencia) || !texto(evidencia.descricao).equals(descricao));
        if (evidencia == null) {
            evidencia = new EvidenciaAssistencial();
            evidencia.modulo = demanda.modulo();
            evidencia.tipo = demanda.tipo();
            evidencia.status = ABERTO;
            evidencia.dataRegistro = LocalDateTime.now();
            evidencia.usuarioRegistro = user == null ? null : user.matricula();
            evidencias.persist(evidencia);
            registrarLog("DEMANDA " + demanda.modulo() + " CRIADA - cirurgia " + cirurgia.getNumero(), cirurgia, user);
        } else if (mudou && CONCLUIDO.equals(evidencia.status)) {
            evidencia.status = ABERTO;
            registrarLog("DEMANDA " + demanda.modulo() + " REABERTA - cirurgia " + cirurgia.getNumero(), cirurgia, user);
        }
        evidencia.unidade = cirurgia.getUnidade();
        evidencia.idpaciente = cirurgia.getIdpaciente();
        evidencia.nomepaciente = cirurgia.getNomepaciente();
        evidencia.referencia = referencia;
        evidencia.origemModulo = ORIGEM_CENTRO_CIRURGICO;
        evidencia.origemId = String.valueOf(cirurgia.getIdcirurgia());
        evidencia.origemDescricao = "Cirurgia " + cirurgia.getNumero();
        evidencia.responsavel = demanda.modulo().equals("CME") ? "CME" : "Hemoterapia";
        evidencia.descricao = descricao;
        evidencia.dataAtualizacao = LocalDateTime.now();
        evidencia.usuarioAtualizacao = user == null ? null : user.matricula();
    }

    private void cancelarAberta(CirurgiaAgendamento cirurgia, String modulo, String motivo, AuthenticatedUser user) {
        EvidenciaAssistencial evidencia = evidencias.buscarPorOrigemEModulo(ORIGEM_CENTRO_CIRURGICO,
                String.valueOf(cirurgia.getIdcirurgia()), modulo);
        if (evidencia != null && ABERTO.equals(evidencia.status)) {
            evidencia.status = CANCELADO;
            evidencia.descricao = texto(evidencia.descricao) + " Cancelada: " + motivo;
            evidencia.dataAtualizacao = LocalDateTime.now();
            evidencia.usuarioAtualizacao = user == null ? null : user.matricula();
            registrarLog("DEMANDA " + modulo + " CANCELADA - cirurgia " + cirurgia.getNumero(), cirurgia, user);
        }
    }

    private Map<String, EvidenciaAssistencial> demandasDaCirurgia(CirurgiaAgendamento cirurgia) {
        return listarDemandas(cirurgia).stream().collect(java.util.stream.Collectors.toMap(
                evidencia -> evidencia.modulo, evidencia -> evidencia, (primeira, segunda) -> primeira));
    }

    private String status(EvidenciaAssistencial evidencia) {
        return evidencia == null ? null : evidencia.status;
    }

    private void registrarLog(String evento, CirurgiaAgendamento cirurgia, AuthenticatedUser user) {
        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        log.setUnidade(texto(cirurgia.getUnidade()).isEmpty() ? "GERAL" : texto(cirurgia.getUnidade()));
        log.setEvento(evento + " - paciente " + texto(cirurgia.getNomepaciente())
                + (user == null ? "" : " - por " + texto(user.nome()) + " (matricula " + texto(user.matricula()) + ")"));
        logService.salvarLogAsync(log);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}

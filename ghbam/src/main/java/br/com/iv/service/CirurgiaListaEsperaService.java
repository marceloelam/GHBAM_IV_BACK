package br.com.iv.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.CirurgiaListaEsperaDto;
import br.com.iv.entity.CirurgiaListaEspera;
import br.com.iv.entity.Pacientes;
import br.com.iv.enums.PrioridadeListaEsperaCirurgica;
import br.com.iv.repository.CirurgiaListaEsperaRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class CirurgiaListaEsperaService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Inject
    CirurgiaListaEsperaRepository esperaRepository;

    @Inject
    CentroCirurgicoService centroCirurgicoService;

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private LocalDateTime parseData(String valor) {
        String v = texto(valor);
        if (v.isBlank()) {
            return null;
        }
        try {
            return v.length() == 16 ? LocalDateTime.parse(v, ISO)
                    : LocalDateTime.parse(v.length() > 19 ? v.substring(0, 19) : v);
        } catch (Exception e) {
            throw new IllegalArgumentException("Data invalida: " + valor);
        }
    }

    private String validarPrioridade(String prioridade) {
        String p = texto(prioridade).toUpperCase();
        if (p.isBlank()) {
            return PrioridadeListaEsperaCirurgica.ELETIVA.name();
        }
        try {
            return PrioridadeListaEsperaCirurgica.valueOf(p).name();
        } catch (Exception e) {
            throw new IllegalArgumentException("Prioridade invalida. Use EMERGENCIA, URGENCIA, PRIORIDADE ou ELETIVA.");
        }
    }

    /**
     * Fila com os dados do cadastro anexados.
     *
     * A fila guarda so nome e CNS do momento da inclusao. Prontuario, CPF e
     * nascimento vem do cadastro na leitura para o agendamento preencher o
     * prontuario e consultar o CNS no CNES sem uma segunda ida ao servidor.
     */
    public List<CirurgiaListaEspera> fila(String unidade, String status, String especialidade) {
        List<CirurgiaListaEspera> itens = esperaRepository.fila(unidade, status, especialidade);
        for (CirurgiaListaEspera item : itens) {
            Pacientes paciente = Pacientes.findById(item.getIdpaciente());
            if (paciente == null) {
                continue;
            }
            item.setProntuario(paciente.getProntuario());
            item.setCpf(paciente.getCpf());
            item.setNascimento(paciente.getNascimento());
            // O CNS do cadastro vence o gravado na inclusao: o cadastro pode ter sido
            // corrigido depois que o paciente entrou na fila.
            if (texto(paciente.getCns()).length() > texto(item.getCnspaciente()).length()) {
                item.setCnspaciente(paciente.getCns());
            }
        }
        return itens;
    }

    @Transactional
    public CirurgiaListaEspera incluir(CirurgiaListaEsperaDto dto, AuthenticatedUser user) {
        if (dto == null || dto.getIdpaciente() <= 0) {
            throw new IllegalArgumentException("Paciente e obrigatorio.");
        }
        if (texto(dto.getProcedimentoDescricao()).isBlank()) {
            throw new IllegalArgumentException("Procedimento e obrigatorio.");
        }
        String unidade = texto(dto.getUnidade());
        if (unidade.isBlank() && user != null) {
            unidade = texto(user.unidade());
        }
        if (unidade.isBlank() || "TODAS".equalsIgnoreCase(unidade)) {
            throw new IllegalArgumentException("Unidade e obrigatoria.");
        }
        String prioridade = validarPrioridade(dto.getPrioridade());
        if (!PrioridadeListaEsperaCirurgica.ELETIVA.name().equals(prioridade)
                && texto(dto.getJustificativaPrioridade()).isBlank()) {
            throw new IllegalArgumentException("Justifique a prioridade informada.");
        }
        long duplicada = CirurgiaListaEspera.count(
                "idpaciente = ?1 and procedimentoDescricao = ?2 and status = ?3",
                dto.getIdpaciente(), texto(dto.getProcedimentoDescricao()), "AGUARDANDO");
        if (duplicada > 0) {
            throw new IllegalArgumentException("Paciente ja esta na fila para este procedimento.");
        }

        CirurgiaListaEspera e = new CirurgiaListaEspera();
        e.setIdpaciente(dto.getIdpaciente());
        e.setNomepaciente(texto(dto.getNomepaciente()));
        e.setCnspaciente(texto(dto.getCnspaciente()));
        e.setUnidade(unidade);
        e.setEspecialidade(texto(dto.getEspecialidade()));
        e.setProcedimentoCodigo(texto(dto.getProcedimentoCodigo()));
        e.setProcedimentoDescricao(texto(dto.getProcedimentoDescricao()));
        e.setPrioridade(prioridade);
        e.setJustificativaPrioridade(texto(dto.getJustificativaPrioridade()));
        e.setDataPrevista(parseData(dto.getDataPrevista()));
        e.setObservacao(texto(dto.getObservacao()));
        e.setUsuarioCriador(user == null ? null : user.matricula());
        e.persist();
        return e;
    }

    @Transactional
    public CirurgiaListaEspera atualizarPrioridade(int idespera, String prioridade, String justificativa) {
        CirurgiaListaEspera e = CirurgiaListaEspera.findById((long) idespera);
        if (e == null) {
            throw new IllegalArgumentException("Item da lista de espera nao encontrado.");
        }
        String nova = validarPrioridade(prioridade);
        if (!PrioridadeListaEsperaCirurgica.ELETIVA.name().equals(nova) && texto(justificativa).isBlank()) {
            throw new IllegalArgumentException("Justifique a prioridade informada.");
        }
        e.setPrioridade(nova);
        e.setJustificativaPrioridade(texto(justificativa));
        e.setAtualizadoEm(LocalDateTime.now());
        return e;
    }

    /**
     * Cancela o item da fila e desfaz o agendamento que ele originou.
     *
     * Antes o cancelamento era barrado quando o item ja estava agendado, o que
     * obrigava a desfazer as duas pontas a mao e deixava fila e mapa divergentes.
     * Agora o cancelamento e a operacao unica: o agendamento ainda nao iniciado e
     * excluido do mapa junto. Cirurgia ja em execucao ou encerrada continua
     * protegida - excluirAgendamentoNaoIniciado recusa e o cancelamento nao passa.
     */
    @Transactional
    public CirurgiaListaEspera cancelar(int idespera, String motivo, AuthenticatedUser user) {
        CirurgiaListaEspera e = CirurgiaListaEspera.findById((long) idespera);
        if (e == null) {
            throw new IllegalArgumentException("Item da lista de espera nao encontrado.");
        }
        if ("CANCELADA".equals(e.getStatus())) {
            throw new IllegalArgumentException("Item ja cancelado.");
        }
        if ("REALIZADA".equals(e.getStatus())) {
            throw new IllegalArgumentException("Cirurgia ja realizada: o item nao pode ser cancelado.");
        }
        Integer idcirurgia = e.getIdcirurgia();
        boolean agendamentoExcluido = false;
        if (idcirurgia != null) {
            agendamentoExcluido = centroCirurgicoService.excluirAgendamentoNaoIniciado(idcirurgia, user);
            e.setIdcirurgia(null);
            e.setDataPrevista(null);
        }
        e.setStatus("CANCELADA");
        e.setObservacao(texto(e.getObservacao()) + " | Cancelado: " + texto(motivo));
        e.setAtualizadoEm(LocalDateTime.now());
        centroCirurgicoService.registrarLog(
                "LISTA DE ESPERA CIRURGICA CANCELADA - item " + idespera + " (" + texto(e.getNomepaciente())
                        + ") motivo: " + (texto(motivo).isEmpty() ? "nao informado" : texto(motivo))
                        + (agendamentoExcluido ? " - agendamento " + idcirurgia + " excluido do mapa" : ""),
                e.getUnidade(), user);
        return e;
    }

    public long contarAguardando(String unidade) {
        return esperaRepository.contarAguardando(unidade);
    }
}

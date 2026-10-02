package br.com.iv.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ConsultorioAgendaStatusDto;
import br.com.iv.entity.ConfiguracaoAgendaConsultorio;
import br.com.iv.entity.ConsultorioAgendaStatus;
import br.com.iv.repository.ConfiguracaoAgendaConsultorioRepository;
import br.com.iv.repository.ConsultorioAgendaStatusRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConsultorioAgendaStatusService {

    @Inject
    ConsultorioAgendaStatusRepository repository;

    @Inject
    ConfiguracaoAgendaConsultorioRepository configuracaoAgendaRepository;

    public List<ConsultorioAgendaStatus> listarPorDataUnidade(String data, String unidade) {
        return repository.listarPorDataUnidade(parseData(data, LocalDate.now()), unidade);
    }

    public Optional<ConsultorioAgendaStatus> buscarPorAgendamento(int idescalaPaciente, String unidade) {
        return repository.buscarPorAgendamento(idescalaPaciente, unidade);
    }

    @Transactional
    public ConsultorioAgendaStatus salvarStatus(ConsultorioAgendaStatusDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Dados de status ausentes");
        }
        int idescalaPaciente = inteiro(dto.getIdescalaPaciente());
        if (idescalaPaciente <= 0) {
            throw new IllegalArgumentException("Agendamento ausente");
        }

        validarLimiteEncaixe(dto, idescalaPaciente);

        Optional<ConsultorioAgendaStatus> existente = repository.buscarPorAgendamento(idescalaPaciente, dto.getUnidade());
        ConsultorioAgendaStatus status = existente.orElseGet(ConsultorioAgendaStatus::new);
        aplicar(status, dto);
        if (existente.isEmpty()) {
            status.setCriadoEm(LocalDateTime.now());
            status.setCriadoPor(valorOuPadrao(dto.getUsuario(), "-"));
            repository.persist(status);
        }
        return status;
    }

    private void validarLimiteEncaixe(ConsultorioAgendaStatusDto dto, int idescalaPaciente) {
        String status = normalizarStatus(dto.getStatus());
        if (!"ENCAIXE".equals(status)) {
            return;
        }

        LocalDate data = dto.getDataAtendimento() == null ? LocalDate.now() : dto.getDataAtendimento();
        String profissional = valorOuPadrao(dto.getProfissional(), "-").toUpperCase();
        Optional<ConfiguracaoAgendaConsultorio> regra = configuracaoAgendaRepository.buscarRegraAtiva(
                dto.getUnidade(), profissional, data.getDayOfWeek());

        if (regra.isEmpty()) {
            return;
        }

        ConfiguracaoAgendaConsultorio config = regra.get();
        if ("N".equalsIgnoreCase(config.getPermiteEncaixe())) {
            throw new IllegalArgumentException("Este profissional nao permite encaixe nesta data");
        }

        int limite = Math.max(config.getLimiteEncaixes(), 0);
        long encaixesDoDia = repository.contarEncaixes(data, dto.getUnidade(), profissional, idescalaPaciente);
        if (encaixesDoDia >= limite) {
            throw new IllegalArgumentException("Limite de encaixes atingido para este profissional nesta data");
        }
    }

    private void aplicar(ConsultorioAgendaStatus status, ConsultorioAgendaStatusDto dto) {
        status.setUnidade(valorOuPadrao(dto.getUnidade(), "-").toUpperCase());
        status.setIdescalaPaciente(inteiro(dto.getIdescalaPaciente()));
        status.setDataAtendimento(dto.getDataAtendimento() == null ? LocalDate.now() : dto.getDataAtendimento());
        status.setPaciente(valorOuPadrao(dto.getPaciente(), "-").toUpperCase());
        status.setProfissional(valorOuPadrao(dto.getProfissional(), "-").toUpperCase());
        status.setEspecialidade(valorOuPadrao(dto.getEspecialidade(), "-").toUpperCase());
        status.setStatus(normalizarStatus(dto.getStatus()));
        status.setObservacao(valorOuPadrao(dto.getObservacao(), "-"));
        status.setAtualizadoEm(LocalDateTime.now());
        status.setAtualizadoPor(valorOuPadrao(dto.getUsuario(), "-"));
    }

    private LocalDate parseData(String valor, LocalDate padrao) {
        if (valor == null || valor.trim().isEmpty()) {
            return padrao;
        }
        return LocalDate.parse(valor);
    }

    private int inteiro(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private String valorOuPadrao(String valor, String padrao) {
        return valor == null || valor.trim().isEmpty() ? padrao : valor.trim();
    }

    private String normalizarStatus(String valor) {
        String status = valorOuPadrao(valor, "AGENDADO").toUpperCase();
        if (status.equals("FALTOU") || status.equals("RETORNO") || status.equals("ENCAIXE")
                || status.equals("REMARCADO") || status.equals("FINALIZADO") || status.equals("CANCELADO")) {
            return status;
        }
        return "AGENDADO";
    }
}

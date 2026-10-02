package br.com.iv.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.FinanceiroConsultorioDto;
import br.com.iv.entity.FinanceiroConsultorio;
import br.com.iv.repository.FinanceiroConsultorioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FinanceiroConsultorioService {

    @Inject
    FinanceiroConsultorioRepository repository;

    public List<FinanceiroConsultorio> listarPorPeriodoUnidade(String dataInicio, String dataFim, String unidade) {
        LocalDate inicio = parseData(dataInicio, LocalDate.now());
        LocalDate fim = parseData(dataFim, inicio);
        return repository.listarPorPeriodoUnidade(inicio, fim, unidade);
    }

    public List<FinanceiroConsultorio> listarPorPaciente(int idpaciente, String unidade) {
        return repository.listarPorPaciente(idpaciente, unidade);
    }

    public Optional<FinanceiroConsultorio> buscarPorAgendamento(int idescalaPaciente, String unidade) {
        return repository.buscarPorAgendamento(idescalaPaciente, unidade);
    }

    @Transactional
    public FinanceiroConsultorio salvar(FinanceiroConsultorioDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Dados financeiros ausentes");
        }
        int idescalaPaciente = inteiro(dto.getIdescalaPaciente());
        if (idescalaPaciente > 0) {
            Optional<FinanceiroConsultorio> existente = repository.buscarPorAgendamento(idescalaPaciente, dto.getUnidade());
            if (existente.isPresent()) {
                FinanceiroConsultorio financeiro = existente.get();
                aplicar(financeiro, dto);
                financeiro.setAtualizadoEm(LocalDateTime.now());
                financeiro.setAtualizadoPor(valorOuPadrao(dto.getUsuario(), "-"));
                return financeiro;
            }
        }
        FinanceiroConsultorio financeiro = new FinanceiroConsultorio();
        aplicar(financeiro, dto);
        financeiro.setCriadoEm(LocalDateTime.now());
        financeiro.setCriadoPor(valorOuPadrao(dto.getUsuario(), "-"));
        repository.persist(financeiro);
        return financeiro;
    }

    @Transactional
    public FinanceiroConsultorio atualizar(int id, FinanceiroConsultorioDto dto) {
        FinanceiroConsultorio financeiro = buscar(id);
        aplicar(financeiro, dto);
        financeiro.setAtualizadoEm(LocalDateTime.now());
        financeiro.setAtualizadoPor(valorOuPadrao(dto.getUsuario(), "-"));
        return financeiro;
    }

    @Transactional
    public FinanceiroConsultorio atualizarStatus(int id, FinanceiroConsultorioDto dto) {
        FinanceiroConsultorio financeiro = buscar(id);
        financeiro.setStatus(normalizarStatus(dto == null ? null : dto.getStatus()));
        financeiro.setFormaPagamento(normalizarFormaPagamento(dto == null ? null : dto.getFormaPagamento()));
        financeiro.setValorRecebido(valor(dto == null ? null : dto.getValorRecebido()));
        financeiro.setReciboNumero(valorOuPadrao(dto == null ? null : dto.getReciboNumero(), financeiro.getReciboNumero()));
        limparRepasseSePacienteNaoPagou(financeiro);
        financeiro.setAtualizadoEm(LocalDateTime.now());
        financeiro.setAtualizadoPor(valorOuPadrao(dto == null ? null : dto.getUsuario(), "-"));
        return financeiro;
    }

    @Transactional
    public List<FinanceiroConsultorio> marcarRepasseRecebido(FinanceiroConsultorioDto dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new IllegalArgumentException("Nenhum repasse selecionado");
        }
        LocalDate dataPagamento = dto.getDataRepasseRecebido() == null ? LocalDate.now() : dto.getDataRepasseRecebido();
        String usuario = valorOuPadrao(dto.getUsuario(), "-");
        List<FinanceiroConsultorio> repasses = dto.getIds().stream()
                .map(this::buscar)
                .filter(financeiro -> "PAGO".equalsIgnoreCase(valorOuPadrao(financeiro.getStatus(), "ABERTO")))
                .peek(financeiro -> {
                    financeiro.setStatusRepasseMedico("PAGO");
                    financeiro.setValorRepasseRecebido(valor(financeiro.getValorRepasseMedico()));
                    financeiro.setDataRepasseRecebido(dataPagamento);
                    financeiro.setAtualizadoEm(LocalDateTime.now());
                    financeiro.setAtualizadoPor(usuario);
                })
                .toList();
        if (repasses.isEmpty()) {
            throw new IllegalArgumentException("Nao ha repasse liberado. O paciente precisa estar com pagamento confirmado.");
        }
        return repasses;
    }

    @Transactional
    public FinanceiroConsultorio cancelar(int id, FinanceiroConsultorioDto dto) {
        FinanceiroConsultorio financeiro = buscar(id);
        financeiro.setStatus("CANCELADO");
        financeiro.setObservacao(valorOuPadrao(dto == null ? null : dto.getObservacao(), financeiro.getObservacao()));
        limparRepasseSePacienteNaoPagou(financeiro);
        financeiro.setAtualizadoEm(LocalDateTime.now());
        financeiro.setAtualizadoPor(valorOuPadrao(dto == null ? null : dto.getUsuario(), "-"));
        return financeiro;
    }

    private FinanceiroConsultorio buscar(int id) {
        Optional<FinanceiroConsultorio> financeiro = FinanceiroConsultorio.findByIdOptional(id);
        if (financeiro.isEmpty()) {
            throw new IllegalArgumentException("Registro financeiro nao encontrado para o id: " + id);
        }
        return financeiro.get();
    }

    private void aplicar(FinanceiroConsultorio financeiro, FinanceiroConsultorioDto dto) {
        financeiro.setUnidade(valorOuPadrao(dto.getUnidade(), "-").toUpperCase());
        financeiro.setIdpaciente(inteiro(dto.getIdpaciente()));
        financeiro.setIdescalaPaciente(inteiro(dto.getIdescalaPaciente()));
        financeiro.setIdatendimentoMedicoAgenda(inteiro(dto.getIdatendimentoMedicoAgenda()));
        financeiro.setIdprocedimentoConsultorio(inteiro(dto.getIdprocedimentoConsultorio()));
        financeiro.setPaciente(valorOuPadrao(dto.getPaciente(), "-").toUpperCase());
        financeiro.setProfissional(valorOuPadrao(dto.getProfissional(), "-").toUpperCase());
        financeiro.setEspecialidade(valorOuPadrao(dto.getEspecialidade(), "-").toUpperCase());
        financeiro.setProcedimento(valorOuPadrao(dto.getProcedimento(), "-").toUpperCase());
        financeiro.setDataAtendimento(dto.getDataAtendimento() == null ? LocalDate.now() : dto.getDataAtendimento());
        financeiro.setTipoCobranca(normalizarTipoCobranca(dto.getTipoCobranca()));
        financeiro.setConvenio(valorOuPadrao(dto.getConvenio(), "-").toUpperCase());
        financeiro.setPlano(valorOuPadrao(dto.getPlano(), "-").toUpperCase());
        financeiro.setNumeroCarteira(valorOuPadrao(dto.getNumeroCarteira(), "-"));
        financeiro.setGuiaAutorizacao(valorOuPadrao(dto.getGuiaAutorizacao(), "-"));
        financeiro.setValorConsulta(valor(dto.getValorConsulta()));
        financeiro.setValorCoparticipacao(valor(dto.getValorCoparticipacao()));
        financeiro.setValorDesconto(valor(dto.getValorDesconto()));
        financeiro.setValorRecebido(valor(dto.getValorRecebido()));
        financeiro.setValorRepasseMedico(valor(dto.getValorRepasseMedico()));
        if (dto.getValorRepasseRecebido() != null || financeiro.getValorRepasseRecebido() == null) {
            financeiro.setValorRepasseRecebido(valor(dto.getValorRepasseRecebido()));
        }
        if (dto.getStatusRepasseMedico() != null || financeiro.getStatusRepasseMedico() == null) {
            financeiro.setStatusRepasseMedico(normalizarStatusRepasse(dto.getStatusRepasseMedico()));
        }
        if (dto.getDataRepasseRecebido() != null) {
            financeiro.setDataRepasseRecebido(dto.getDataRepasseRecebido());
        }
        financeiro.setFormaPagamento(normalizarFormaPagamento(dto.getFormaPagamento()));
        financeiro.setStatus(normalizarStatus(dto.getStatus()));
        financeiro.setReciboNumero(valorOuPadrao(dto.getReciboNumero(), "-"));
        financeiro.setObservacao(valorOuPadrao(dto.getObservacao(), "-"));
        limparRepasseSePacienteNaoPagou(financeiro);
        financeiro.setAtualizadoEm(LocalDateTime.now());
        financeiro.setAtualizadoPor(valorOuPadrao(dto.getUsuario(), "-"));
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

    private BigDecimal valor(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private String valorOuPadrao(String valor, String padrao) {
        return valor == null || valor.trim().isEmpty() ? padrao : valor.trim();
    }

    private String normalizarTipoCobranca(String valor) {
        String tipo = valorOuPadrao(valor, "PARTICULAR").toUpperCase();
        if (tipo.equals("CONVENIO") || tipo.equals("COPARTICIPACAO") || tipo.equals("CORTESIA")) {
            return tipo;
        }
        return "PARTICULAR";
    }

    private String normalizarFormaPagamento(String valor) {
        String forma = valorOuPadrao(valor, "NAO_INFORMADO").toUpperCase();
        if (forma.equals("DINHEIRO") || forma.equals("PIX") || forma.equals("CARTAO_CREDITO")
                || forma.equals("CARTAO_DEBITO") || forma.equals("BOLETO") || forma.equals("CONVENIO")) {
            return forma;
        }
        return "NAO_INFORMADO";
    }

    private String normalizarStatus(String valor) {
        String status = valorOuPadrao(valor, "ABERTO").toUpperCase();
        if (status.equals("PAGO") || status.equals("PARCIAL") || status.equals("CANCELADO") || status.equals("GLOSADO")) {
            return status;
        }
        return "ABERTO";
    }

    private String normalizarStatusRepasse(String valor) {
        String status = valorOuPadrao(valor, "ABERTO").toUpperCase();
        if (status.equals("PAGO")) {
            return status;
        }
        return "ABERTO";
    }

    private void limparRepasseSePacienteNaoPagou(FinanceiroConsultorio financeiro) {
        if (!"PAGO".equalsIgnoreCase(valorOuPadrao(financeiro.getStatus(), "ABERTO"))) {
            financeiro.setStatusRepasseMedico("ABERTO");
            financeiro.setValorRepasseRecebido(BigDecimal.ZERO);
            financeiro.setDataRepasseRecebido(null);
        }
    }
}

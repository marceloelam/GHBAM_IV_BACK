package br.com.iv.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class FinanceiroConsultorioDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String unidade;
    private Integer idpaciente;
    private Integer idescalaPaciente;
    private Integer idatendimentoMedicoAgenda;
    private Integer idprocedimentoConsultorio;
    private String paciente;
    private String profissional;
    private String especialidade;
    private String procedimento;
    private LocalDate dataAtendimento;
    private String tipoCobranca;
    private String convenio;
    private String plano;
    private String numeroCarteira;
    private String guiaAutorizacao;
    private BigDecimal valorConsulta;
    private BigDecimal valorCoparticipacao;
    private BigDecimal valorDesconto;
    private BigDecimal valorRecebido;
    private BigDecimal valorRepasseMedico;
    private BigDecimal valorRepasseRecebido;
    private String statusRepasseMedico;
    private LocalDate dataRepasseRecebido;
    private String formaPagamento;
    private String status;
    private String reciboNumero;
    private String observacao;
    private String usuario;
    private List<Integer> ids;
}

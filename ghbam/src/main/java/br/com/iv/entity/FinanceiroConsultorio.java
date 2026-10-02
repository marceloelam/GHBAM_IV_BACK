package br.com.iv.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "financeiroconsultorio")
public class FinanceiroConsultorio extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private int idfinanceiroconsultorio;

    @Column(length = 25)
    private String unidade;

    @Column(columnDefinition = "integer default 0")
    private int idpaciente;

    @Column(name = "idescalapaciente", columnDefinition = "integer default 0")
    private int idescalaPaciente;

    @Column(name = "idatendimentomedicoagenda", columnDefinition = "integer default 0")
    private int idatendimentoMedicoAgenda;

    @Column(name = "idprocedimentoconsultorio", columnDefinition = "integer default 0")
    private int idprocedimentoConsultorio;

    @Column(length = 100)
    private String paciente;

    @Column(length = 80)
    private String profissional;

    @Column(length = 80)
    private String especialidade;

    @Column(length = 120)
    private String procedimento;

    @Column(name = "dataatendimento")
    private LocalDate dataAtendimento;

    @Column(name = "tipocobranca", length = 20)
    private String tipoCobranca;

    @Column(length = 80)
    private String convenio;

    @Column(length = 80)
    private String plano;

    @Column(name = "numerocarteira", length = 60)
    private String numeroCarteira;

    @Column(name = "guiaautorizacao", length = 60)
    private String guiaAutorizacao;

    @Column(name = "valorconsulta", precision = 12, scale = 2)
    private BigDecimal valorConsulta;

    @Column(name = "valorcoparticipacao", precision = 12, scale = 2)
    private BigDecimal valorCoparticipacao;

    @Column(name = "valordesconto", precision = 12, scale = 2)
    private BigDecimal valorDesconto;

    @Column(name = "valorrecebido", precision = 12, scale = 2)
    private BigDecimal valorRecebido;

    @Column(name = "valorrepassemedico", precision = 12, scale = 2)
    private BigDecimal valorRepasseMedico;

    @Column(name = "valorrepasserecebido", precision = 12, scale = 2)
    private BigDecimal valorRepasseRecebido;

    @Column(name = "statusrepassemedico", length = 20)
    private String statusRepasseMedico;

    @Column(name = "datarepasserecebido")
    private LocalDate dataRepasseRecebido;

    @Column(name = "formapagamento", length = 20)
    private String formaPagamento;

    @Column(length = 20)
    private String status;

    @Column(name = "recibonumero", length = 40)
    private String reciboNumero;

    @Column(length = 500)
    private String observacao;

    @Column(name = "criadoem")
    private LocalDateTime criadoEm;

    @Column(name = "criadopor", length = 80)
    private String criadoPor;

    @Column(name = "atualizadoem")
    private LocalDateTime atualizadoEm;

    @Column(name = "atualizadopor", length = 80)
    private String atualizadoPor;
}

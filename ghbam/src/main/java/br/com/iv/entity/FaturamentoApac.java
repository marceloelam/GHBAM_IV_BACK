package br.com.iv.entity;

import java.io.Serializable;
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
@Table(name = "faturamento_apac")
public class FaturamentoApac extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idapac;

    @Column(length = 30, nullable = false, unique = true)
    private String numero;

    @Column(nullable = false)
    private int idpaciente;

    @Column(length = 80)
    private String nomepaciente;

    @Column(length = 19)
    private String cnspaciente;

    @Column(length = 14)
    private String cpfpaciente;

    @Column(length = 25)
    private String unidade;

    @Column(length = 15)
    private String cnes;

    @Column(length = 160)
    private String nomeestabelecimento;

    @Column(length = 30)
    private String prontuario;

    @Column(length = 30)
    private String bam;

    @Column(length = 20)
    private String datanascimento;

    @Column(length = 20)
    private String sexo;

    @Column(length = 30)
    private String racacor;

    @Column(length = 60)
    private String etnia;

    private Boolean situacaorua;

    @Column(length = 120)
    private String nomemae;

    @Column(length = 120)
    private String nomeresponsavel;

    @Column(length = 25)
    private String telefonecelular;

    @Column(length = 25)
    private String telefonecontato;

    @Column(length = 100)
    private String municipioresidencia;

    @Column(length = 2)
    private String uf;

    @Column(length = 10)
    private String codigoibge;

    @Column(length = 10)
    private String cep;

    @Column(length = 180)
    private String endereco;

    @Column(length = 80)
    private String complemento;

    private Integer idprofissional;

    @Column(length = 15)
    private String matriculaprofissional;

    @Column(length = 120)
    private String nomeprofissional;

    @Column(length = 16)
    private String cnsprofissional;

    @Column(length = 14)
    private String cpfprofissional;

    @Column(length = 20)
    private String conselhoprofissional;

    @Column(length = 30)
    private String registroprofissional;

    @Column(length = 10)
    private String procedimentoprincipal;

    @Column(length = 255)
    private String descricaoprocedimentoprincipal;

    @Column(length = 200)
    private String procedimentossecundarios;

    @Column(columnDefinition = "TEXT")
    private String descricaoprocedimentossecundarios;

    @Column(length = 10)
    private String cid;

    @Column(length = 10)
    private String cidsecundario;

    @Column(columnDefinition = "TEXT")
    private String diagnosticoobservacaoclinica;

    @Column(columnDefinition = "TEXT")
    private String justificativaclinica;

    @Column(columnDefinition = "TEXT")
    private String examescomplementares;

    @Column(length = 30)
    private String numeroautorizacao;

    @Column(length = 160)
    private String nomeestabelecimentoexecutante;

    @Column(length = 15)
    private String cnesexecutante;

    @Column(length = 120)
    private String orgaoautorizador;

    @Column(length = 30)
    private String codorgaoemissor;

    @Column(length = 120)
    private String profissionalautorizador;

    @Column(length = 16)
    private String cnsprofissionalautorizador;

    @Column(length = 20)
    private String validadeinicio;

    @Column(length = 20)
    private String validadefim;

    private Integer idcompetencia;

    private int quantidade;

    @Column(nullable = false)
    private Boolean exportado = false;

    @Column(name = "data_exportacao")
    private LocalDateTime dataExportacao;

    @Column(name = "numero_remessa", length = 40)
    private String numeroRemessa;

    @Column(length = 30, nullable = false)
    private String status;

    @Column(name = "data_solicitacao")
    private LocalDateTime dataSolicitacao;

    @Column(name = "data_autorizacao")
    private LocalDateTime dataAutorizacao;

    @Column(name = "usuario_autorizacao", length = 40)
    private String usuarioAutorizacao;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "usuario_criador", length = 40)
    private String usuarioCriador;
}

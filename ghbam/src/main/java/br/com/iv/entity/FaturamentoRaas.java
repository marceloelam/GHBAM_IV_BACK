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
@Table(name = "faturamento_raas")
public class FaturamentoRaas extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idraas;

    @Column(length = 30, nullable = false, unique = true)
    private String numero;

    @Column(nullable = false)
    private int idpaciente;

    @Column(length = 80)
    private String nomepaciente;

    @Column(length = 19)
    private String cnspaciente;

    @Column(length = 25)
    private String unidade;

    @Column(length = 15)
    private String cnes;

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
    private String procedimento;

    @Column(length = 10)
    private String cid;

    @Column(length = 120)
    private String nomeestabelecimento;

    @Column(length = 30)
    private String prontuario;

    @Column(length = 1)
    private String sexo;

    @Column(length = 10)
    private String datanascimento;

    @Column(length = 30)
    private String nacionalidade;

    @Column(length = 30)
    private String racacor;

    @Column(length = 80)
    private String etniaindigena;

    @Column(length = 120)
    private String nomemae;

    @Column(length = 120)
    private String nomeresponsavel;

    @Column(length = 80)
    private String municipioresidencia;

    @Column(length = 2)
    private String uf;

    @Column(length = 7)
    private String codigoibge;

    @Column(length = 8)
    private String cep;

    @Column(length = 160)
    private String endereco;

    @Column(length = 120)
    private String complemento;

    @Column(length = 20)
    private String telefonecelular;

    @Column(length = 20)
    private String telefonecontato;

    @Column(length = 10)
    private String dataadmissao;

    @Column(length = 7)
    private String mesatendimento;

    @Column(length = 30)
    private String numeroautorizacao;

    private Boolean usuarioalcooldrogas;

    private Boolean drogaalcool;

    private Boolean drogacrack;

    private Boolean drogaoutras;

    @Column(length = 40)
    private String origempaciente;

    @Column(length = 10)
    private String cidcausasassociadas;

    @Column(length = 180)
    private String diagnosticoprincipal;

    @Column(length = 180)
    private String diagnosticocausasassociadas;

    private Boolean coberturaesf;

    @Column(length = 15)
    private String cnesesf;

    @Column(length = 80)
    private String encaminhamento;

    @Column(length = 80)
    private String conclusao;

    @Column(columnDefinition = "TEXT")
    private String acoesrealizadas;

    private Integer idcompetencia;

    @Column(nullable = false)
    private Boolean exportado = false;

    @Column(name = "data_exportacao")
    private LocalDateTime dataExportacao;

    @Column(name = "numero_remessa", length = 40)
    private String numeroRemessa;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "usuario_criador", length = 40)
    private String usuarioCriador;
}

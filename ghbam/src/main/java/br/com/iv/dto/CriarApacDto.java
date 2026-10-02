package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class CriarApacDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int idpaciente;
    private String nomepaciente;
    private String cnspaciente;
    private String cpfpaciente;
    private String unidade;
    private String cnes;
    private String nomeestabelecimento;
    private String prontuario;
    private String bam;
    private String datanascimento;
    private String sexo;
    private String racacor;
    private String etnia;
    private Boolean situacaorua;
    private String nomemae;
    private String nomeresponsavel;
    private String telefonecelular;
    private String telefonecontato;
    private String municipioresidencia;
    private String uf;
    private String codigoibge;
    private String cep;
    private String endereco;
    private String complemento;
    private Integer idprofissional;
    private String matriculaprofissional;
    private String nomeprofissional;
    private String cnsprofissional;
    private String cpfprofissional;
    private String conselhoprofissional;
    private String registroprofissional;
    private String procedimentoprincipal;
    private String descricaoprocedimentoprincipal;
    private String procedimentossecundarios;
    private String descricaoprocedimentossecundarios;
    private String cid;
    private String cidsecundario;
    private String diagnosticoobservacaoclinica;
    private String justificativaclinica;
    private String examescomplementares;
    private String nomeestabelecimentoexecutante;
    private String cnesexecutante;
    private String orgaoautorizador;
    private String codorgaoemissor;
    private String profissionalautorizador;
    private String cnsprofissionalautorizador;
    private String numeroautorizacao;
    private String validadeinicio;
    private String validadefim;
    private int quantidade;
    private Integer idcompetencia;
}

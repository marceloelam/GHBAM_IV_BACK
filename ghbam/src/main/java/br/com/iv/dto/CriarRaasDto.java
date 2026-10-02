package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class CriarRaasDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int idpaciente;
    private String nomepaciente;
    private String cnspaciente;
    private String unidade;
    private String cnes;
    private Integer idprofissional;
    private String matriculaprofissional;
    private String nomeprofissional;
    private String cnsprofissional;
    private String cpfprofissional;
    private String conselhoprofissional;
    private String registroprofissional;
    private String procedimento;
    private String cid;
    private String nomeestabelecimento;
    private String prontuario;
    private String sexo;
    private String datanascimento;
    private String nacionalidade;
    private String racacor;
    private String etniaindigena;
    private String nomemae;
    private String nomeresponsavel;
    private String municipioresidencia;
    private String uf;
    private String codigoibge;
    private String cep;
    private String endereco;
    private String complemento;
    private String telefonecelular;
    private String telefonecontato;
    private String dataadmissao;
    private String mesatendimento;
    private String numeroautorizacao;
    private Boolean usuarioalcooldrogas;
    private Boolean drogaalcool;
    private Boolean drogacrack;
    private Boolean drogaoutras;
    private String origempaciente;
    private String cidcausasassociadas;
    private String diagnosticoprincipal;
    private String diagnosticocausasassociadas;
    private Boolean coberturaesf;
    private String cnesesf;
    private String encaminhamento;
    private String conclusao;
    private String acoesrealizadas;
    private Integer idcompetencia;
}

package br.com.iv.rag2.dto;

import lombok.Data;

@Data
public class Rag2PacienteBuscaDto {
    private Integer idPaciente;
    private String nome;
    private String prontuario;
    private Integer bam;
    private String dataNascimento;
    private Integer idade;
    private String sexo;
    private String unidade;
    private String setor;
    private String leito;
    private String situacao;
}

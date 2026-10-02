package br.com.iv.rag2.dto;

import lombok.Data;

@Data
public class Rag2PerguntaDto {
    private Integer idPaciente;
    private Integer bam;
    private String pergunta;
    private String perfilUsuario;
    private String matriculaUsuario;
    private String nomeUsuario;
}

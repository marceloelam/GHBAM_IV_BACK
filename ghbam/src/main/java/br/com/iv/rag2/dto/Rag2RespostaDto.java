package br.com.iv.rag2.dto;

import java.util.List;

import lombok.Data;

@Data
public class Rag2RespostaDto {
    private String resposta;
    private List<Rag2FonteDto> fontes;
    private List<String> avisos;
    private String dataHoraConsulta;
    /** "IA" (resposta gerada pelo modelo) ou "PRONTUARIO_COMPLETO" (dados brutos do prontuário, sem passar pela IA). */
    private String modo;
    private Rag2PacienteBuscaDto paciente;
}

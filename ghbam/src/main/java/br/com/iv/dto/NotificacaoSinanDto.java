package br.com.iv.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class NotificacaoSinanDto {
    private Integer idpaciente;
    private Integer idbam;
    private String tipo;
    private String unidade;
    private String nomePaciente;
    private LocalDateTime dataNotificacao;
    private String dados;
    private String criadoPor;
    private String criadoPorMatricula;
    private String status;
    private String periodicidade;
}

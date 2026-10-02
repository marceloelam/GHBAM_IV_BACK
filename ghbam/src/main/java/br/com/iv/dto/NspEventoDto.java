package br.com.iv.dto;

import java.time.LocalDateTime;
import lombok.Data;

/** Corpo da notificacao e das etapas de analise do NSP. */
@Data
public class NspEventoDto {
    private String unidade;
    private String tipoIncidente;
    private String classificacao;
    private String status;
    private String grauDano;
    private LocalDateTime dataOcorrencia;
    private String localOcorrencia;
    private String turno;
    private Integer idpaciente;
    private Integer idbam;
    private String prontuario;
    private String nomePaciente;
    private String descricao;
    private String acaoImediata;
    private Boolean anonimo;
    private Boolean evitavel;
    private String causaRaiz;
    private String medicamento;
    private String doseAdministrada;
    private String viaAdministracao;
    private Integer idprescricao;
    private String causalidade;
    private String conclusao;
    private String observacao;
    private Boolean notivisaNotificado;
    private String notivisaProtocolo;
}

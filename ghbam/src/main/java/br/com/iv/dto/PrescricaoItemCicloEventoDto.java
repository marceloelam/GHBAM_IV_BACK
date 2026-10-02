package br.com.iv.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class PrescricaoItemCicloEventoDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int idevento;
    private int iditem;
    private int idbam;
    private Integer idpaciente;
    private Integer idprescricao;
    private String unidade;
    private String tipoEvento;
    private String statusEvento;
    private LocalDateTime dataHoraPrevista;
    private LocalDateTime dataHoraRealizada;
    private BigDecimal quantidade;
    private String doseAdministrada;
    private String responsavel;
    private String responsavelMatricula;
    private String observacao;
}

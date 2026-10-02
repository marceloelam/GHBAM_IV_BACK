package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

@Data
public class SaeEnfermagemDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String uuid;
    private String status;
    private String tipoSae;
    private Integer versao;
    private String uuidOrigem;
    private String motivoRetificacao;
    private LocalDate data;
    private JsonNode paciente;
    private JsonNode historico;
    private JsonNode diagnosticos;
    private JsonNode cuidados;
    private JsonNode prescricoes;
    private JsonNode evolucoes;
    private JsonNode criadoPor;
    private JsonNode atualizadoPor;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private boolean gravado;
}

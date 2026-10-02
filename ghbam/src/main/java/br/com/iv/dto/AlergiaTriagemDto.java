package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * Promocao da alergia digitada na triagem (texto livre, gravado no campo
 * `cabeca` da avaliacao daquele atendimento) para item ALERGIA do prontuario
 * clinico do paciente. Sem isso a alergia morre junto com o BAM: o mesmo
 * paciente volta em outro atendimento e o CDSS nao tem o que checar.
 */
@Data
public class AlergiaTriagemDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int idpaciente;
    private Integer idbam;
    private String unidade;
    private String descricao;
    private String criadoPor;
    private String criadoPorMatricula;
}

package br.com.iv.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ZamedSolicitacaoDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String status;
    private String criadoEm;
    private String unidade;
    private String usuario;
    private String usuarioMatricula;
    private String bam;
    private String pacienteId;
    private String pacienteNome;
    private String prontuario;
    private String setor;
    private String leito;
    private ZamedPeriodoDto periodo;
    private List<ZamedItemSolicitacaoDto> itens = new ArrayList<>();
}

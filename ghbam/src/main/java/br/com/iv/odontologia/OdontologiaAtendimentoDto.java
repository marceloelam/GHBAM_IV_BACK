package br.com.iv.odontologia;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class OdontologiaAtendimentoDto {
    private Integer idatendimento;
    private Integer idpaciente;
    private Integer idbam;
    private String status;
    private String tipoOdontograma;
    private String denticao;
    private String queixaPrincipal;
    private String historiaDoencaAtual;
    private String anamneseJson;
    private String exameGeral;
    private String exameExtraoral;
    private String exameIntraoral;
    private String observacoes;
    private String evolucoesJson;
    private String periodontaisJson;
    private String conteudoHash;
    private String conteudoResumo;
    private List<RegistroDto> registros = new ArrayList<>();

    @Data
    public static class RegistroDto {
        private Integer numeroDente;
        private String face;
        private String condicao;
        private String descricao;
    }
}

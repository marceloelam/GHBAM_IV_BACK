package br.com.iv.analiserisco.dto;

public class AnaliseRiscoRespostaDto {

    private String analise;
    private String aviso;
    private String dataHoraAnalise;
    private boolean semDados;

    public String getAnalise() {
        return analise;
    }

    public void setAnalise(String analise) {
        this.analise = analise;
    }

    public String getAviso() {
        return aviso;
    }

    public void setAviso(String aviso) {
        this.aviso = aviso;
    }

    public String getDataHoraAnalise() {
        return dataHoraAnalise;
    }

    public void setDataHoraAnalise(String dataHoraAnalise) {
        this.dataHoraAnalise = dataHoraAnalise;
    }

    public boolean isSemDados() {
        return semDados;
    }

    public void setSemDados(boolean semDados) {
        this.semDados = semDados;
    }
}

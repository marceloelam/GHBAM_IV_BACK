package br.com.iv.cdss.dto;

import java.util.ArrayList;
import java.util.List;

public class CdssAvaliacaoResponse {
    private boolean possuiAlertaCritico;
    private boolean bloqueante;
    private List<CdssAlertDto> alertas = new ArrayList<>();

    public boolean isPossuiAlertaCritico() { return possuiAlertaCritico; }
    public void setPossuiAlertaCritico(boolean possuiAlertaCritico) { this.possuiAlertaCritico = possuiAlertaCritico; }
    public boolean isBloqueante() { return bloqueante; }
    public void setBloqueante(boolean bloqueante) { this.bloqueante = bloqueante; }
    public List<CdssAlertDto> getAlertas() { return alertas; }
    public void setAlertas(List<CdssAlertDto> alertas) { this.alertas = alertas == null ? new ArrayList<>() : alertas; }
}

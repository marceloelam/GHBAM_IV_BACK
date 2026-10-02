package br.com.iv.cdss.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class News2Response {
    private int total;
    private String risco;
    private String orientacao;
    private Map<String, Integer> componentes = new LinkedHashMap<>();

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public String getRisco() { return risco; }
    public void setRisco(String risco) { this.risco = risco; }
    public String getOrientacao() { return orientacao; }
    public void setOrientacao(String orientacao) { this.orientacao = orientacao; }
    public Map<String, Integer> getComponentes() { return componentes; }
    public void setComponentes(Map<String, Integer> componentes) { this.componentes = componentes == null ? new LinkedHashMap<>() : componentes; }
}

package br.com.iv.dto;

import java.util.Date;

public class EscalaResumoDTO {
    private String horamarcada;
    private String unidade;
    private String medico;
    private Date inicioagenda;
    
    // Construtor
    public EscalaResumoDTO(String horamarcada, String unidade, String medico, Date inicioagenda) {
        this.horamarcada = horamarcada;
        this.unidade = unidade;
        this.medico = medico;
        this.inicioagenda = inicioagenda;
    }
    
    // Getters e Setters
    public String getHoramarcada() {
        return horamarcada;
    }
    
    public void setHoramarcada(String horamarcada) {
        this.horamarcada = horamarcada;
    }
    
    public String getUnidade() {
        return unidade;
    }
    
    public void setUnidade(String unidade) {
        this.unidade = unidade;
    }
    
    public String getMedico() {
        return medico;
    }
    
    public void setMedico(String medico) {
        this.medico = medico;
    }
    
    public Date getInicioagenda() {
        return inicioagenda;
    }
    
    public void setInicioagenda(Date inicioagenda) {
        this.inicioagenda = inicioagenda;
    }
}
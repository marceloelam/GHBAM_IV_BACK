package br.com.iv.cdss.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

public class CdssAvaliacaoPrescricaoRequest {
    private Integer idpaciente;
    private Integer idbam;
    private String unidade;
    private String medicamento;
    private String dose;
    private String via;
    private String frequencia;
    private Double pesoKg;
    private Integer idadeAnos;
    private Double egfr;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> medicamentosAtuais = new ArrayList<>();
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> alergias = new ArrayList<>();
    private CdssBularioMedicamentoDto bulario;

    public Integer getIdpaciente() { return idpaciente; }
    public void setIdpaciente(Integer idpaciente) { this.idpaciente = idpaciente; }
    public Integer getIdbam() { return idbam; }
    public void setIdbam(Integer idbam) { this.idbam = idbam; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public String getMedicamento() { return medicamento; }
    public void setMedicamento(String medicamento) { this.medicamento = medicamento; }
    public String getDose() { return dose; }
    public void setDose(String dose) { this.dose = dose; }
    public String getVia() { return via; }
    public void setVia(String via) { this.via = via; }
    public String getFrequencia() { return frequencia; }
    public void setFrequencia(String frequencia) { this.frequencia = frequencia; }
    public Double getPesoKg() { return pesoKg; }
    public void setPesoKg(Double pesoKg) { this.pesoKg = pesoKg; }
    public Integer getIdadeAnos() { return idadeAnos; }
    public void setIdadeAnos(Integer idadeAnos) { this.idadeAnos = idadeAnos; }
    public Double getEgfr() { return egfr; }
    public void setEgfr(Double egfr) { this.egfr = egfr; }
    public List<String> getMedicamentosAtuais() { return medicamentosAtuais; }
    public void setMedicamentosAtuais(List<String> medicamentosAtuais) { this.medicamentosAtuais = medicamentosAtuais == null ? new ArrayList<>() : medicamentosAtuais; }
    public List<String> getAlergias() { return alergias; }
    public void setAlergias(List<String> alergias) { this.alergias = alergias == null ? new ArrayList<>() : alergias; }
    public CdssBularioMedicamentoDto getBulario() { return bulario; }
    public void setBulario(CdssBularioMedicamentoDto bulario) { this.bulario = bulario; }
}

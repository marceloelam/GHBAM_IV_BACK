package br.com.iv.pharmacia.validacao.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Tudo de que a validacao farmaceutica precisa para avaliar uma dispensacao.
 *
 * Os campos clinicos (peso, TFG, gestante) sao opcionais: quando ausentes, o
 * motor nao inventa valor - ele avisa que a checagem correspondente nao pode
 * ser feita, o que ja e uma informacao util na bancada.
 */
public class ValidacaoDispensacaoRequest {

    private Integer idpaciente;
    private String pacienteNome;
    private Integer idbam;
    private String unidade;

    private Integer idadeAnos;
    /** Usado para menores de 1 ano, onde a idade em anos perde resolucao. */
    private Integer idadeMeses;
    private String sexo;
    private Double pesoKg;
    /** Taxa de filtracao glomerular estimada, em mL/min/1,73m2. */
    private Double tfg;
    private Boolean gestante;
    private Boolean lactante;

    /** Itens que a farmacia vai entregar agora. */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<ItemDispensacaoDto> itens = new ArrayList<>();

    /** Alergias registradas no prontuario do paciente (texto livre). */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> alergias = new ArrayList<>();

    /** Medicamentos em uso que nao estao sendo dispensados agora: prescricao ativa, uso domiciliar, dispensacoes recentes. */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> medicamentosEmUso = new ArrayList<>();

    public Integer getIdpaciente() { return idpaciente; }
    public void setIdpaciente(Integer idpaciente) { this.idpaciente = idpaciente; }
    public String getPacienteNome() { return pacienteNome; }
    public void setPacienteNome(String pacienteNome) { this.pacienteNome = pacienteNome; }
    public Integer getIdbam() { return idbam; }
    public void setIdbam(Integer idbam) { this.idbam = idbam; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public Integer getIdadeAnos() { return idadeAnos; }
    public void setIdadeAnos(Integer idadeAnos) { this.idadeAnos = idadeAnos; }
    public Integer getIdadeMeses() { return idadeMeses; }
    public void setIdadeMeses(Integer idadeMeses) { this.idadeMeses = idadeMeses; }
    public String getSexo() { return sexo; }
    public void setSexo(String sexo) { this.sexo = sexo; }
    public Double getPesoKg() { return pesoKg; }
    public void setPesoKg(Double pesoKg) { this.pesoKg = pesoKg; }
    public Double getTfg() { return tfg; }
    public void setTfg(Double tfg) { this.tfg = tfg; }
    public Boolean getGestante() { return gestante; }
    public void setGestante(Boolean gestante) { this.gestante = gestante; }
    public Boolean getLactante() { return lactante; }
    public void setLactante(Boolean lactante) { this.lactante = lactante; }
    public List<ItemDispensacaoDto> getItens() { return itens; }
    public void setItens(List<ItemDispensacaoDto> itens) { this.itens = itens == null ? new ArrayList<>() : itens; }
    public List<String> getAlergias() { return alergias; }
    public void setAlergias(List<String> alergias) { this.alergias = alergias == null ? new ArrayList<>() : alergias; }
    public List<String> getMedicamentosEmUso() { return medicamentosEmUso; }
    public void setMedicamentosEmUso(List<String> medicamentosEmUso) { this.medicamentosEmUso = medicamentosEmUso == null ? new ArrayList<>() : medicamentosEmUso; }
}

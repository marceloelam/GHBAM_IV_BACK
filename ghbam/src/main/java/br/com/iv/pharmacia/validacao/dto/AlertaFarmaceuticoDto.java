package br.com.iv.pharmacia.validacao.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Alerta produzido pela validacao farmaceutica da dispensacao.
 *
 * Espelha a forma do CdssAlertDto (usado na prescricao medica) para que o
 * frontend trate os dois com o mesmo componente, mas acrescenta o que so faz
 * sentido na bancada da farmacia: o item que gerou o alerta e a conduta
 * farmaceutica sugerida.
 */
public class AlertaFarmaceuticoDto {

    /** Codigo estavel da regra, ex.: FARM_ALERGIA_DIRETA. Usado em relatorio e auditoria. */
    private String codigo;
    /** CRITICAL (exige justificativa), MAJOR, MINOR. */
    private String severidade;
    /** ALERGIA, INTERACAO, DUPLICIDADE, DOSE, RENAL, GESTACAO, PEDIATRIA, IDOSO, ALTA_VIGILANCIA, CONTROLE_ESPECIAL, ANTIMICROBIANO, ORIENTACAO. */
    private String categoria;
    private String titulo;
    private String mensagem;
    /** O que o farmaceutico deve fazer. */
    private String conduta;
    /** Nome do medicamento que disparou o alerta, como veio da dispensacao. */
    private String medicamento;
    /** Medicamento/alergia do outro lado do par, quando houver. */
    private String relacionadoA;
    /** true quando a confirmacao exige justificativa registrada. */
    private boolean exigeJustificativa;
    private List<String> fatores = new ArrayList<>();

    public AlertaFarmaceuticoDto() {
    }

    public AlertaFarmaceuticoDto(String codigo, String severidade, String categoria, String titulo, String mensagem,
            String conduta, String medicamento, String relacionadoA, boolean exigeJustificativa, List<String> fatores) {
        this.codigo = codigo;
        this.severidade = severidade;
        this.categoria = categoria;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.conduta = conduta;
        this.medicamento = medicamento;
        this.relacionadoA = relacionadoA;
        this.exigeJustificativa = exigeJustificativa;
        this.fatores = fatores == null ? new ArrayList<>() : new ArrayList<>(fatores);
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getSeveridade() { return severidade; }
    public void setSeveridade(String severidade) { this.severidade = severidade; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
    public String getConduta() { return conduta; }
    public void setConduta(String conduta) { this.conduta = conduta; }
    public String getMedicamento() { return medicamento; }
    public void setMedicamento(String medicamento) { this.medicamento = medicamento; }
    public String getRelacionadoA() { return relacionadoA; }
    public void setRelacionadoA(String relacionadoA) { this.relacionadoA = relacionadoA; }
    public boolean isExigeJustificativa() { return exigeJustificativa; }
    public void setExigeJustificativa(boolean exigeJustificativa) { this.exigeJustificativa = exigeJustificativa; }
    public List<String> getFatores() { return fatores; }
    public void setFatores(List<String> fatores) { this.fatores = fatores == null ? new ArrayList<>() : fatores; }
}

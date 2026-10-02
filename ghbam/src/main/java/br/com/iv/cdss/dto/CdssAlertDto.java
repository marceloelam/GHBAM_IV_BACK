package br.com.iv.cdss.dto;

import java.util.ArrayList;
import java.util.List;

public class CdssAlertDto {
    private String codigo;
    private String severidade;
    private String categoria;
    private String titulo;
    private String mensagem;
    private String recomendacao;
    private boolean bloqueante;
    private List<String> fatores = new ArrayList<>();

    public CdssAlertDto() {
    }

    public CdssAlertDto(String codigo, String severidade, String categoria, String titulo, String mensagem,
            String recomendacao, boolean bloqueante, List<String> fatores) {
        this.codigo = codigo;
        this.severidade = severidade;
        this.categoria = categoria;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.recomendacao = recomendacao;
        this.bloqueante = bloqueante;
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
    public String getRecomendacao() { return recomendacao; }
    public void setRecomendacao(String recomendacao) { this.recomendacao = recomendacao; }
    public boolean isBloqueante() { return bloqueante; }
    public void setBloqueante(boolean bloqueante) { this.bloqueante = bloqueante; }
    public List<String> getFatores() { return fatores; }
    public void setFatores(List<String> fatores) { this.fatores = fatores == null ? new ArrayList<>() : fatores; }
}

package br.com.iv.cdss.dto;

public class CdssOverrideRequest {
    private Integer idpaciente;
    private Integer idbam;
    private String unidade;
    private String alertaCodigo;
    private String alertaSeveridade;
    private String justificativa;
    private String moduloOrigem;
    private String usuarioNome;
    private String usuarioMatricula;

    public Integer getIdpaciente() { return idpaciente; }
    public void setIdpaciente(Integer idpaciente) { this.idpaciente = idpaciente; }
    public Integer getIdbam() { return idbam; }
    public void setIdbam(Integer idbam) { this.idbam = idbam; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public String getAlertaCodigo() { return alertaCodigo; }
    public void setAlertaCodigo(String alertaCodigo) { this.alertaCodigo = alertaCodigo; }
    public String getAlertaSeveridade() { return alertaSeveridade; }
    public void setAlertaSeveridade(String alertaSeveridade) { this.alertaSeveridade = alertaSeveridade; }
    public String getJustificativa() { return justificativa; }
    public void setJustificativa(String justificativa) { this.justificativa = justificativa; }
    public String getModuloOrigem() { return moduloOrigem; }
    public void setModuloOrigem(String moduloOrigem) { this.moduloOrigem = moduloOrigem; }
    public String getUsuarioNome() { return usuarioNome; }
    public void setUsuarioNome(String usuarioNome) { this.usuarioNome = usuarioNome; }
    public String getUsuarioMatricula() { return usuarioMatricula; }
    public void setUsuarioMatricula(String usuarioMatricula) { this.usuarioMatricula = usuarioMatricula; }
}

package br.com.iv.dto;

import java.io.Serializable;

public class PacientesImagensPacsDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int idimagens;
    private int idbam;
    private String descricao;
    private String data;
    private String local;
    private String atendidopor;
    private String nomePaciente;
    private String link;
    private String laudo;

    public int getIdimagens() { return idimagens; }
    public void setIdimagens(int idimagens) { this.idimagens = idimagens; }

    public int getIdbam() { return idbam; }
    public void setIdbam(int idbam) { this.idbam = idbam; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getLocal() { return local; }
    public void setLocal(String local) { this.local = local; }

    public String getAtendidopor() { return atendidopor; }
    public void setAtendidopor(String atendidopor) { this.atendidopor = atendidopor; }

    public String getNomePaciente() { return nomePaciente; }
    public void setNomePaciente(String nomePaciente) { this.nomePaciente = nomePaciente; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getLaudo() { return laudo; }
    public void setLaudo(String laudo) { this.laudo = laudo; }
}

package br.com.iv.imagemia.dto;

/** Ciencia do profissional sobre um achado de IA, com a conduta tomada. */
public class CienciaImagemIaRequest {
    private Long id;
    private Integer idbam;
    private String conduta;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getIdbam() { return idbam; }
    public void setIdbam(Integer idbam) { this.idbam = idbam; }

    public String getConduta() { return conduta; }
    public void setConduta(String conduta) { this.conduta = conduta; }
}

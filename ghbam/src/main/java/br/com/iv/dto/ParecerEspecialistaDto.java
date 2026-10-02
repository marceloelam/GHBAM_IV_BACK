package br.com.iv.dto;

import java.util.Date;

public class ParecerEspecialistaDto {
    private int idparecerespecialista;
    private String desc1;
    private Date data;
    private Date dataresp;
    private String desc2;
    private String prontuario;
    private String medicorequisitante;
    private String crm;
    private String medicorecebedor;
    private String crmrecebedor;
    private String especialidadeorigem;
    private String especialidadedestino;
    private String unidade;
    private int nbam;

    public ParecerEspecialistaDto() {}

    public ParecerEspecialistaDto(int idparecerespecialista, String desc1, Date data, Date dataresp, String desc2, String prontuario, String medicorequisitante, String crm, String medicorecebedor, String crmrecebedor, String especialidadeorigem, String especialidadedestino, String unidade, int nbam) {
        this.idparecerespecialista = idparecerespecialista;
        this.desc1 = desc1;
        this.data = data;
        this.dataresp = dataresp;
        this.desc2 = desc2;
        this.prontuario = prontuario;
        this.medicorequisitante = medicorequisitante;
        this.crm = crm;
        this.medicorecebedor = medicorecebedor;
        this.crmrecebedor = crmrecebedor;
        this.especialidadeorigem = especialidadeorigem;
        this.especialidadedestino = especialidadedestino;
        this.unidade = unidade;
        this.nbam = nbam;
    }

    public int getIdparecerespecialista() { return idparecerespecialista; }
    public void setIdparecerespecialista(int idparecerespecialista) { this.idparecerespecialista = idparecerespecialista; }
    public String getDesc1() { return desc1; }
    public void setDesc1(String desc1) { this.desc1 = desc1; }
    public Date getData() { return data; }
    public void setData(Date data) { this.data = data; }
    public Date getDataresp() { return dataresp; }
    public void setDataresp(Date dataresp) { this.dataresp = dataresp; }
    public String getDesc2() { return desc2; }
    public void setDesc2(String desc2) { this.desc2 = desc2; }
    public String getProntuario() { return prontuario; }
    public void setProntuario(String prontuario) { this.prontuario = prontuario; }
    public String getMedicorequisitante() { return medicorequisitante; }
    public void setMedicorequisitante(String medicorequisitante) { this.medicorequisitante = medicorequisitante; }
    public String getCrm() { return crm; }
    public void setCrm(String crm) { this.crm = crm; }
    public String getMedicorecebedor() { return medicorecebedor; }
    public void setMedicorecebedor(String medicorecebedor) { this.medicorecebedor = medicorecebedor; }
    public String getCrmrecebedor() { return crmrecebedor; }
    public void setCrmrecebedor(String crmrecebedor) { this.crmrecebedor = crmrecebedor; }
    public String getEspecialidadeorigem() { return especialidadeorigem; }
    public void setEspecialidadeorigem(String especialidadeorigem) { this.especialidadeorigem = especialidadeorigem; }
    public String getEspecialidadedestino() { return especialidadedestino; }
    public void setEspecialidadedestino(String especialidadedestino) { this.especialidadedestino = especialidadedestino; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public int getNbam() { return nbam; }
    public void setNbam(int nbam) { this.nbam = nbam; }
}

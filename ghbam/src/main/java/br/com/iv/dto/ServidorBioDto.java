package br.com.iv.dto;

import java.io.Serializable;

public class ServidorBioDto  implements Serializable  {
	private static final long serialVersionUID = 1L;

    private int idservidor;
    private String matricula;
    private String nome;
    private String pwd;

    // Adicione aqui apenas os campos que realmente precisa transferir via JSON

    public int getIdservidor() {
        return idservidor;
    }

    public void setIdservidor(int idservidor) {
        this.idservidor = idservidor;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }
}

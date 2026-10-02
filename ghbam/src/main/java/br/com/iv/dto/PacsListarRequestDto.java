package br.com.iv.dto;

import java.io.Serializable;

public class PacsListarRequestDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String nome;
    private int first;
    private int pageSize;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public int getFirst() { return first; }
    public void setFirst(int first) { this.first = first; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}

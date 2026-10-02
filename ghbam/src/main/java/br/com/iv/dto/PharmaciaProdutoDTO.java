package br.com.iv.dto;

import java.io.Serializable;

public class PharmaciaProdutoDTO implements Serializable {
    private Long id;
    private String nomeComercial;
    private String nomeGenerico;
    private String codigoBarras;
    private String formaFarmaceutica;
    private Long categoriaId;
    private String unidadeMedida;
    private String fabricante;
    private String registroAnvisa;
    private Long unidadeId;
    private String unidadeSigla;
    private Boolean ativo;
    // Campos clinicos (V158): habilitam a validacao farmaceutica da dispensacao.
    private String principioAtivo;
    private String concentracao;
    private String viaAdministracao;
    private String codigoAtc;
    private Boolean altaVigilancia;
    private Boolean antimicrobiano;
    private String listaControlada;
    private Boolean exigeReceita;

    public PharmaciaProdutoDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNomeComercial() { return nomeComercial; }
    public void setNomeComercial(String nomeComercial) { this.nomeComercial = nomeComercial; }

    public String getNomeGenerico() { return nomeGenerico; }
    public void setNomeGenerico(String nomeGenerico) { this.nomeGenerico = nomeGenerico; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public String getFormaFarmaceutica() { return formaFarmaceutica; }
    public void setFormaFarmaceutica(String formaFarmaceutica) { this.formaFarmaceutica = formaFarmaceutica; }

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }

    public String getUnidadeMedida() { return unidadeMedida; }
    public void setUnidadeMedida(String unidadeMedida) { this.unidadeMedida = unidadeMedida; }

    public String getFabricante() { return fabricante; }
    public void setFabricante(String fabricante) { this.fabricante = fabricante; }

    public String getRegistroAnvisa() { return registroAnvisa; }
    public void setRegistroAnvisa(String registroAnvisa) { this.registroAnvisa = registroAnvisa; }

    public Long getUnidadeId() { return unidadeId; }
    public void setUnidadeId(Long unidadeId) { this.unidadeId = unidadeId; }

    public String getUnidadeSigla() { return unidadeSigla; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public String getPrincipioAtivo() { return principioAtivo; }
    public void setPrincipioAtivo(String principioAtivo) { this.principioAtivo = principioAtivo; }

    public String getConcentracao() { return concentracao; }
    public void setConcentracao(String concentracao) { this.concentracao = concentracao; }

    public String getViaAdministracao() { return viaAdministracao; }
    public void setViaAdministracao(String viaAdministracao) { this.viaAdministracao = viaAdministracao; }

    public String getCodigoAtc() { return codigoAtc; }
    public void setCodigoAtc(String codigoAtc) { this.codigoAtc = codigoAtc; }

    public Boolean getAltaVigilancia() { return altaVigilancia; }
    public void setAltaVigilancia(Boolean altaVigilancia) { this.altaVigilancia = altaVigilancia; }

    public Boolean getAntimicrobiano() { return antimicrobiano; }
    public void setAntimicrobiano(Boolean antimicrobiano) { this.antimicrobiano = antimicrobiano; }

    public String getListaControlada() { return listaControlada; }
    public void setListaControlada(String listaControlada) { this.listaControlada = listaControlada; }

    public Boolean getExigeReceita() { return exigeReceita; }
    public void setExigeReceita(Boolean exigeReceita) { this.exigeReceita = exigeReceita; }
}

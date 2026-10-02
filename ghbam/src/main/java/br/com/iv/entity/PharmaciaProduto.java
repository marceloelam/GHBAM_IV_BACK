package br.com.iv.entity;

import java.io.Serializable;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "pharmacia_produto", uniqueConstraints = @UniqueConstraint(columnNames = {"nomeComercial", "unidade_sigla"}))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PharmaciaProduto extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nomeComercial;

    @Column(nullable = false, length = 150)
    private String nomeGenerico;

    @Column(unique = true, length = 50)
    private String codigoBarras;

    @Column(nullable = false, length = 50)
    private String formaFarmaceutica;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id", nullable = false)
    private PharmaciaCategoria categoria;

    @Column(nullable = false, length = 20)
    private String unidadeMedida;

    @Column(length = 100)
    private String fabricante;

    @Column(length = 50)
    private String registroAnvisa;

    @Column(name = "unidade_sigla", length = 20)
    private String unidadeSigla;

    @Column(nullable = false)
    private Boolean ativo = true;

    // --- Campos clinicos (V158) ---------------------------------------
    // Sem principio ativo o sistema so conhece o nome comercial, e nenhuma
    // checagem de alergia, interacao ou duplicidade consegue funcionar.

    /** Denominacao comum brasileira do principio ativo. */
    @Column(name = "principio_ativo", length = 200)
    private String principioAtivo;

    /** Ex.: 500 mg, 5 mg/mL. */
    @Column(name = "concentracao", length = 60)
    private String concentracao;

    @Column(name = "via_administracao", length = 60)
    private String viaAdministracao;

    @Column(name = "codigo_atc", length = 20)
    private String codigoAtc;

    /** Medicamento potencialmente perigoso (ISMP): exige dupla checagem na dispensacao. */
    @Column(name = "alta_vigilancia", nullable = false)
    private Boolean altaVigilancia = false;

    /** Sujeito ao controle da RDC ANVISA 20/2011. */
    @Column(name = "antimicrobiano", nullable = false)
    private Boolean antimicrobiano = false;

    /** Lista da Portaria SVS/MS 344/1998: A1, A2, A3, B1, B2, C1 a C5. */
    @Column(name = "lista_controlada", length = 10)
    private String listaControlada;

    /** Exige retencao de receituario na dispensacao. */
    @Column(name = "exige_receita", nullable = false)
    private Boolean exigeReceita = false;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNomeComercial() { return nomeComercial; }
    public void setNomeComercial(String nomeComercial) { this.nomeComercial = nomeComercial; }

    public String getNomeGenerico() { return nomeGenerico; }
    public void setNomeGenerico(String nomeGenerico) { this.nomeGenerico = nomeGenerico; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = (codigoBarras != null && !codigoBarras.trim().isEmpty()) ? codigoBarras.trim() : null;
    }

    public String getFormaFarmaceutica() { return formaFarmaceutica; }
    public void setFormaFarmaceutica(String formaFarmaceutica) { this.formaFarmaceutica = formaFarmaceutica; }

    public PharmaciaCategoria getCategoria() { return categoria; }
    public void setCategoria(PharmaciaCategoria categoria) { this.categoria = categoria; }

    public String getUnidadeMedida() { return unidadeMedida; }
    public void setUnidadeMedida(String unidadeMedida) { this.unidadeMedida = unidadeMedida; }

    public String getFabricante() { return fabricante; }
    public void setFabricante(String fabricante) { this.fabricante = fabricante; }

    public String getRegistroAnvisa() { return registroAnvisa; }
    public void setRegistroAnvisa(String registroAnvisa) { this.registroAnvisa = registroAnvisa; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public String getUnidadeSigla() { return unidadeSigla; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }

    public String getPrincipioAtivo() { return principioAtivo; }
    public void setPrincipioAtivo(String principioAtivo) { this.principioAtivo = principioAtivo; }

    public String getConcentracao() { return concentracao; }
    public void setConcentracao(String concentracao) { this.concentracao = concentracao; }

    public String getViaAdministracao() { return viaAdministracao; }
    public void setViaAdministracao(String viaAdministracao) { this.viaAdministracao = viaAdministracao; }

    public String getCodigoAtc() { return codigoAtc; }
    public void setCodigoAtc(String codigoAtc) { this.codigoAtc = codigoAtc; }

    public Boolean getAltaVigilancia() { return altaVigilancia; }
    public void setAltaVigilancia(Boolean altaVigilancia) { this.altaVigilancia = altaVigilancia != null && altaVigilancia; }

    public Boolean getAntimicrobiano() { return antimicrobiano; }
    public void setAntimicrobiano(Boolean antimicrobiano) { this.antimicrobiano = antimicrobiano != null && antimicrobiano; }

    public String getListaControlada() { return listaControlada; }
    public void setListaControlada(String listaControlada) {
        this.listaControlada = (listaControlada != null && !listaControlada.trim().isEmpty()) ? listaControlada.trim().toUpperCase() : null;
    }

    public Boolean getExigeReceita() { return exigeReceita; }
    public void setExigeReceita(Boolean exigeReceita) { this.exigeReceita = exigeReceita != null && exigeReceita; }
}
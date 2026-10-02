package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "pharmacia_permuta")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PharmaciaPermuta extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum StatusPermuta {
        PENDENTE, APROVADO, RECUSADO, EM_TRANSPORTE, FINALIZADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unidade_origem_sigla", length = 20)
    private String unidadeOrigemSigla;

    @Column(name = "unidade_destino_sigla", length = 20)
    private String unidadeDestinoSigla;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPermuta status = StatusPermuta.PENDENTE;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime dataSolicitacao;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dataAprovacao;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dataFinalizacao;

    @Column(length = 500)
    private String observacao;

    @Column(length = 500)
    private String motivoRecusa;

    @Column(nullable = false)
    private String solicitanteMatricula;

    private String aprovadorMatricula;

    // Rastro de auditoria completo do ciclo de vida — quem fez cada etapa e
    // quando, alem de solicitante/aprovador, ja existentes. Necessario pra
    // nao ter duvida de responsabilidade em caso de divergencia de estoque.
    private String transporteMatricula;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dataTransporte;

    private String recebedorMatricula;

    @JsonManagedReference
    @OneToMany(mappedBy = "permuta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PharmaciaPermutaItem> itens;

    @Column(nullable = false)
    private Boolean ativo = true;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUnidadeOrigemSigla() { return unidadeOrigemSigla; }
    public void setUnidadeOrigemSigla(String unidadeOrigemSigla) { this.unidadeOrigemSigla = unidadeOrigemSigla; }

    public String getUnidadeDestinoSigla() { return unidadeDestinoSigla; }
    public void setUnidadeDestinoSigla(String unidadeDestinoSigla) { this.unidadeDestinoSigla = unidadeDestinoSigla; }

    public StatusPermuta getStatus() { return status; }
    public void setStatus(StatusPermuta status) { this.status = status; }

    public LocalDateTime getDataSolicitacao() { return dataSolicitacao; }
    public void setDataSolicitacao(LocalDateTime dataSolicitacao) { this.dataSolicitacao = dataSolicitacao; }

    public LocalDateTime getDataAprovacao() { return dataAprovacao; }
    public void setDataAprovacao(LocalDateTime dataAprovacao) { this.dataAprovacao = dataAprovacao; }

    public LocalDateTime getDataFinalizacao() { return dataFinalizacao; }
    public void setDataFinalizacao(LocalDateTime dataFinalizacao) { this.dataFinalizacao = dataFinalizacao; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public String getMotivoRecusa() { return motivoRecusa; }
    public void setMotivoRecusa(String motivoRecusa) { this.motivoRecusa = motivoRecusa; }

    public String getSolicitanteMatricula() { return solicitanteMatricula; }
    public void setSolicitanteMatricula(String solicitanteMatricula) { this.solicitanteMatricula = solicitanteMatricula; }

    public String getAprovadorMatricula() { return aprovadorMatricula; }
    public void setAprovadorMatricula(String aprovadorMatricula) { this.aprovadorMatricula = aprovadorMatricula; }

    public String getTransporteMatricula() { return transporteMatricula; }
    public void setTransporteMatricula(String transporteMatricula) { this.transporteMatricula = transporteMatricula; }

    public LocalDateTime getDataTransporte() { return dataTransporte; }
    public void setDataTransporte(LocalDateTime dataTransporte) { this.dataTransporte = dataTransporte; }

    public String getRecebedorMatricula() { return recebedorMatricula; }
    public void setRecebedorMatricula(String recebedorMatricula) { this.recebedorMatricula = recebedorMatricula; }

    public List<PharmaciaPermutaItem> getItens() { return itens; }
    public void setItens(List<PharmaciaPermutaItem> itens) { this.itens = itens; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}

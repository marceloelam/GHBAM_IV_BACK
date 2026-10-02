package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "pharmacia_dispensacao")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PharmaciaDispensacao extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(name = "paciente_nome", length = 200)
    private String pacienteNome;

    @Column(name = "paciente_cpf", length = 20)
    private String pacienteCpf;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lote_id", nullable = false)
    @JsonIgnoreProperties({"produto", "numeroLote", "dataFabricacao", "dataValidade",
            "quantidadeInicial", "quantidadeAtual", "bloqueado", "unidadeSigla",
            "ativo", "hibernateLazyInitializer", "handler"})
    private PharmaciaLote lote;

    @Column(name = "produto_nome", length = 200)
    private String produtoNome;

    @Column(nullable = false)
    private Integer quantidade;

    @Column
    private Integer duracao;

    @Column(length = 500)
    private String observacao;

    @Column(name = "medico_nome", length = 150)
    private String medicoNome;

    @Column(name = "medico_crm", length = 30)
    private String medicoCRM;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(name = "unidade_sigla", length = 20)
    private String unidadeSigla;

    @Column(name = "usuario_matricula", nullable = false)
    private String usuarioMatricula;

    @Column(nullable = false)
    private Boolean ativo = true;

    /** Expose loteId in JSON response for frontend devolver call */
    @Transient
    public Long getLoteId() {
        return lote != null ? lote.getId() : null;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }

    public String getPacienteNome() { return pacienteNome; }
    public void setPacienteNome(String pacienteNome) { this.pacienteNome = pacienteNome; }

    public String getPacienteCpf() { return pacienteCpf; }
    public void setPacienteCpf(String pacienteCpf) { this.pacienteCpf = pacienteCpf; }

    public PharmaciaLote getLote() { return lote; }
    public void setLote(PharmaciaLote lote) { this.lote = lote; }

    public String getProdutoNome() { return produtoNome; }
    public void setProdutoNome(String produtoNome) { this.produtoNome = produtoNome; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public Integer getDuracao() { return duracao; }
    public void setDuracao(Integer duracao) { this.duracao = duracao; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public String getMedicoNome() { return medicoNome; }
    public void setMedicoNome(String medicoNome) { this.medicoNome = medicoNome; }

    public String getMedicoCRM() { return medicoCRM; }
    public void setMedicoCRM(String medicoCRM) { this.medicoCRM = medicoCRM; }

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }

    public String getUnidadeSigla() { return unidadeSigla; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }

    public String getUsuarioMatricula() { return usuarioMatricula; }
    public void setUsuarioMatricula(String usuarioMatricula) { this.usuarioMatricula = usuarioMatricula; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}

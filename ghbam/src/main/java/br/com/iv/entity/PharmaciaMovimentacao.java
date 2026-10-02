package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "pharmacia_movimentacao")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PharmaciaMovimentacao extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum TipoMovimentacao {
        ENTRADA, SAIDA, TRANSFERENCIA, AJUSTE, DISPENSACAO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimentacao tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private PharmaciaLote lote;

    @Column(name = "unidade_origem_sigla", length = 20)
    private String unidadeOrigemSigla;

    @Column(name = "unidade_destino_sigla", length = 20)
    private String unidadeDestinoSigla;

    @Column(nullable = false)
    private Integer quantidade;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime dataHora;

    @Column(length = 255)
    private String justificativa;

    @Column(length = 100)
    private String pacienteNome;

    @Column(length = 20)
    private String pacienteCpf;

    @Column(nullable = false)
    private String usuarioMatricula;

    @Column(nullable = false)
    private Boolean ativo = true;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TipoMovimentacao getTipo() { return tipo; }
    public void setTipo(TipoMovimentacao tipo) { this.tipo = tipo; }

    public PharmaciaLote getLote() { return lote; }
    public void setLote(PharmaciaLote lote) { this.lote = lote; }

    public String getUnidadeOrigemSigla() { return unidadeOrigemSigla; }
    public void setUnidadeOrigemSigla(String unidadeOrigemSigla) { this.unidadeOrigemSigla = unidadeOrigemSigla; }

    public String getUnidadeDestinoSigla() { return unidadeDestinoSigla; }
    public void setUnidadeDestinoSigla(String unidadeDestinoSigla) { this.unidadeDestinoSigla = unidadeDestinoSigla; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }

    public String getJustificativa() { return justificativa; }
    public void setJustificativa(String justificativa) { this.justificativa = justificativa; }

    public String getPacienteNome() { return pacienteNome; }
    public void setPacienteNome(String pacienteNome) { this.pacienteNome = pacienteNome; }

    public String getPacienteCpf() { return pacienteCpf; }
    public void setPacienteCpf(String pacienteCpf) { this.pacienteCpf = pacienteCpf; }

    public String getUsuarioMatricula() { return usuarioMatricula; }
    public void setUsuarioMatricula(String usuarioMatricula) { this.usuarioMatricula = usuarioMatricula; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}

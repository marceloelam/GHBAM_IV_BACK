package br.com.iv.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "evidencia_assistencial")
public class EvidenciaAssistencial extends PanacheEntityBase implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long idevidencia;

    @Column(nullable = false, length = 40)
    public String modulo;

    @Column(nullable = false, length = 80)
    public String tipo;

    @Column(length = 40)
    public String status;

    @Column(length = 20)
    public String unidade;

    public Integer idpaciente;

    @Column(length = 40)
    public String prontuario;

    @Column(length = 160)
    public String nomepaciente;

    @Column(length = 80)
    public String referencia;

    @Column(length = 160)
    public String responsavel;

    @Column(length = 80)
    public String origemModulo;

    @Column(length = 80)
    public String origemId;

    @Column(length = 220)
    public String origemDescricao;

    @Column(columnDefinition = "text")
    public String descricao;

    @Column(columnDefinition = "text")
    public String detalhesJson;

    public LocalDateTime dataRegistro;

    @Column(length = 80)
    public String usuarioRegistro;

    public LocalDateTime dataAtualizacao;

    @Column(length = 80)
    public String usuarioAtualizacao;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) {
            dataRegistro = LocalDateTime.now();
        }
        if (dataAtualizacao == null) {
            dataAtualizacao = dataRegistro;
        }
    }
}

package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "controle_assistencial_registro")
public class ControleAssistencialRegistro extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true, length = 80)
    private String uuid;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 30)
    private String unidade;

    @Column(length = 120)
    private String setor;

    @Column(name = "data_registro", nullable = false)
    private LocalDate dataRegistro;

    @Column(length = 20)
    private String turno;

    @Column(length = 120)
    private String responsavel;

    @Column(name = "responsavel_matricula", length = 40)
    private String responsavelMatricula;

    @Column(name = "responsavel_cargo", length = 80)
    private String responsavelCargo;

    @Column(name = "conselho", length = 20)
    private String conselho;

    @Column(name = "numero_conselho", length = 40)
    private String numeroConselho;

    @Column(name = "idbam")
    private Integer idbam;

    @Column(name = "idpaciente")
    private Integer idpaciente;

    @Column(name = "paciente", length = 180)
    private String paciente;

    @Column(name = "dados_json", columnDefinition = "TEXT")
    private String dadosJson;

    @Column(name = "observacao", columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
        if (status == null || status.isBlank()) {
            status = "REGISTRADO";
        }
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}

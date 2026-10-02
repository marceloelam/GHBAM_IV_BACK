package br.com.iv.entity;

import java.io.Serializable;
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
@Table(name = "chat_conversas_limpas")
public class ChatConversaLimpa extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int idchatconversalimpa;

    @Column(length = 20, nullable = false)
    private String tipo;

    @Column(length = 40)
    private String sala;

    @Column(length = 50, nullable = false)
    private String unidade;

    @Column(name = "usuario_matricula", length = 20, nullable = false)
    private String usuarioMatricula;

    @Column(name = "outro_matricula", length = 20)
    private String outroMatricula;

    @Column(name = "ultimo_id_limpo")
    private Integer ultimoIdLimpo;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        LocalDateTime agora = LocalDateTime.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        atualizadoEm = agora;
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}

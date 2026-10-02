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
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "chat_mensagens")
public class ChatMensagem extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int idchatmensagem;

    @Column(length = 20, nullable = false)
    private String tipo;

    @Column(length = 40)
    private String sala;

    @Column(length = 50, nullable = false)
    private String unidade;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "remetente_matricula", length = 20, nullable = false)
    private String remetenteMatricula;

    @Column(name = "remetente_nome", length = 120)
    private String remetenteNome;

    @Column(name = "remetente_cargo", length = 80)
    private String remetenteCargo;

    @Column(name = "remetente_foto", columnDefinition = "TEXT")
    private String remetenteFoto;

    @Column(name = "destinatario_matricula", length = 20)
    private String destinatarioMatricula;

    @Column(name = "destinatario_nome", length = 120)
    private String destinatarioNome;

    @Column(name = "destinatario_foto", columnDefinition = "TEXT")
    private String destinatarioFoto;

    @Column(name = "anexo_nome", length = 255)
    private String anexoNome;

    @Column(name = "anexo_tipo", length = 120)
    private String anexoTipo;

    @Column(name = "anexo_tamanho")
    private Long anexoTamanho;

    @Column(name = "anexo_conteudo", columnDefinition = "TEXT")
    private String anexoConteudo;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "visualizada_em")
    private LocalDateTime visualizadaEm;

    @PrePersist
    protected void onCreate() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }
}

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
@Table(name = "assinatura_clinica")
public class AssinaturaClinica extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idassinatura;

    @Column(name = "tipo_documento", length = 60)
    private String tipoDocumento;

    private Integer iddocumento;

    private Integer idbam;

    private Integer idpaciente;

    @Column(length = 20)
    private String unidade;

    @Column(length = 20)
    private String status;

    @Column(name = "conteudo_hash", length = 64)
    private String conteudoHash;

    @Column(name = "conteudo_resumo", columnDefinition = "TEXT")
    private String conteudoResumo;

    @Column(name = "assinado_por", length = 120)
    private String assinadoPor;

    @Column(name = "assinado_por_matricula", length = 40)
    private String assinadoPorMatricula;

    @Column(name = "assinado_em")
    private LocalDateTime assinadoEm;

    @Column(name = "adendo_texto", columnDefinition = "TEXT")
    private String adendoTexto;

    @Column(name = "adendo_por", length = 120)
    private String adendoPor;

    @Column(name = "adendo_por_matricula", length = 40)
    private String adendoPorMatricula;

    @Column(name = "adendo_em")
    private LocalDateTime adendoEm;

    @Column(name = "assinatura_digital", columnDefinition = "TEXT")
    private String assinaturaDigital;

    @Column(name = "algoritmo_assinatura", length = 40)
    private String algoritmoAssinatura;

    @Column(name = "chave_publica", columnDefinition = "TEXT")
    private String chavePublica;

    @Column(name = "assinatura_origem_id")
    private Integer assinaturaOrigemId;

    @PrePersist
    public void prePersist() {
        if (status == null || status.isBlank()) {
            status = "ASSINADO";
        }
        if (assinadoEm == null && !"ADENDO".equalsIgnoreCase(status)) {
            assinadoEm = LocalDateTime.now();
        }
        if (adendoEm == null && "ADENDO".equalsIgnoreCase(status)) {
            adendoEm = LocalDateTime.now();
        }
    }
}

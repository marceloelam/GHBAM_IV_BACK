package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "diagnostico_sistema")
public class DiagnosticoSistema extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddiagnosticosistema")
    private Integer iddiagnosticosistema;

    @Column(length = 30)
    private String unidade;

    @Column(length = 120)
    private String usuario;

    @Column(length = 40)
    private String matricula;

    @Column(length = 40)
    private String tipo;

    @Column(length = 80)
    private String modulo;

    @Column(length = 40)
    private String severidade;

    @Column(length = 12)
    private String metodo;

    @Column(length = 260)
    private String endpoint;

    @Column(length = 260)
    private String rota;

    @Column(name = "status_http")
    private Integer statusHttp;

    @Column(name = "duracao_ms")
    private Integer duracaoMs;

    @Column(length = 180)
    private String motivo;

    @Column(columnDefinition = "TEXT")
    private String detalhes;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;
}

package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

// Vinculo servidor <-> unidade (ate 3 por servidor, limite validado na
// aplicacao). servidor.unidade continua sendo a unidade "principal", espelhada
// aqui com principal=true - ver V72__create_servidor_unidade.sql.
@Data
@Entity(name = "servidorUnidade")
@Table(name = "servidor_unidade")
public class ServidorUnidade extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(name = "idservidor", nullable = false)
    private Integer idservidor;

    @Column(length = 30, nullable = false)
    private String unidade;

    @Column(nullable = false)
    private Boolean principal = false;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}

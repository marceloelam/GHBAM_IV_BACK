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
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "consultorioagendastatus")
public class ConsultorioAgendaStatus extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private int idconsultorioagendastatus;

    @Column(length = 25)
    private String unidade;

    @Column(name = "idescalapaciente", columnDefinition = "integer default 0")
    private int idescalaPaciente;

    @Column(name = "dataatendimento")
    private LocalDate dataAtendimento;

    @Column(length = 100)
    private String paciente;

    @Column(length = 80)
    private String profissional;

    @Column(length = 80)
    private String especialidade;

    @Column(length = 20)
    private String status;

    @Column(length = 500)
    private String observacao;

    @Column(name = "criadoem")
    private LocalDateTime criadoEm;

    @Column(name = "criadopor", length = 80)
    private String criadoPor;

    @Column(name = "atualizadoem")
    private LocalDateTime atualizadoEm;

    @Column(name = "atualizadopor", length = 80)
    private String atualizadoPor;
}

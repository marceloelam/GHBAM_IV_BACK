package br.com.iv.odontologia;

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
@Table(name = "odontograma_registro")
public class OdontogramaRegistro extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idregistro;

    @Column(name = "idatendimento", nullable = false)
    private Integer idatendimento;

    @Column(name = "numero_dente", nullable = false)
    private Integer numeroDente;

    @Column(length = 24)
    private String face;

    @Column(length = 30)
    private String condicao;

    @Column(columnDefinition = "TEXT")
    private String descricao;
}

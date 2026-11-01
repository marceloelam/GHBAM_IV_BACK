package br.com.iv.almoxarifado;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "almox_compra")
public class AlmoxCompra extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idcompra")
    public Long idcompra;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "idfornecedor")
    public Long idfornecedor;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "observacao", columnDefinition = "text")
    public String observacao;

    @Column(name = "notafiscal", length = 60)
    public String notaFiscal;

    @Column(name = "dataregistro")
    public LocalDateTime dataRegistro;

    @Column(name = "usuarioregistro", length = 80)
    public String usuarioRegistro;

    @Column(name = "dataenvio")
    public LocalDateTime dataEnvio;

    @Column(name = "datarecebimento")
    public LocalDateTime dataRecebimento;

    @Column(name = "usuariorecebimento", length = 80)
    public String usuarioRecebimento;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) dataRegistro = LocalDateTime.now();
    }
}

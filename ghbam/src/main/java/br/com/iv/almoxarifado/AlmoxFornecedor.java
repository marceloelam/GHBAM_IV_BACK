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
@Table(name = "almox_fornecedor")
public class AlmoxFornecedor extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idfornecedor")
    public Long idfornecedor;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "nome", nullable = false, length = 160)
    public String nome;

    @Column(name = "cnpj", length = 20)
    public String cnpj;

    @Column(name = "telefone", length = 40)
    public String telefone;

    @Column(name = "email", length = 120)
    public String email;

    @Column(name = "ativo", nullable = false)
    public Boolean ativo;

    @Column(name = "dataregistro")
    public LocalDateTime dataRegistro;

    @Column(name = "usuarioregistro", length = 80)
    public String usuarioRegistro;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) dataRegistro = LocalDateTime.now();
        if (ativo == null) ativo = Boolean.TRUE;
    }
}

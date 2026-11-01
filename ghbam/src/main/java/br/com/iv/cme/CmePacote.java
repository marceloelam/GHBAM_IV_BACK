package br.com.iv.cme;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cme_pacote")
public class CmePacote extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idpacote")
    public Long idpacote;

    @Column(name = "codigo", length = 40)
    public String codigo;

    @Column(name = "idciclo", nullable = false)
    public Long idciclo;

    /** Limpeza/preparo de origem; nulo quando a caixa foi digitada sem preparo registrado. */
    @Column(name = "idpreparo")
    public Long idpreparo;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "descricao", nullable = false, length = 160)
    public String descricao;

    @Column(name = "quantidade", nullable = false)
    public Integer quantidade;

    @Column(name = "contemimplante", nullable = false)
    public Boolean contemImplante;

    @Column(name = "validade", nullable = false)
    public LocalDate validade;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "setordestino", length = 80)
    public String setorDestino;

    @Column(name = "idpaciente")
    public Integer idpaciente;

    @Column(name = "prontuario", length = 40)
    public String prontuario;

    @Column(name = "nomepaciente", length = 160)
    public String nomepaciente;

    @Column(name = "referenciauso", length = 80)
    public String referenciaUso;

    @Column(name = "datautilizacao")
    public LocalDateTime dataUtilizacao;

    @Column(name = "dataregistro")
    public LocalDateTime dataRegistro;

    @Column(name = "usuarioregistro", length = 80)
    public String usuarioRegistro;

    @Column(name = "dataatualizacao")
    public LocalDateTime dataAtualizacao;

    @Column(name = "usuarioatualizacao", length = 80)
    public String usuarioAtualizacao;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) {
            dataRegistro = LocalDateTime.now();
        }
        if (dataAtualizacao == null) {
            dataAtualizacao = dataRegistro;
        }
    }
}

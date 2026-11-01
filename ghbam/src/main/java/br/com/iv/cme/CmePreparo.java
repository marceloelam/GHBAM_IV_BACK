package br.com.iv.cme;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Caixa no caminho expurgo -> limpeza -> preparo, antes de entrar numa carga. */
@Entity
@Table(name = "cme_preparo")
public class CmePreparo extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idpreparo")
    public Long idpreparo;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "descricao", nullable = false, length = 160)
    public String descricao;

    @Column(name = "contemimplante", nullable = false)
    public Boolean contemImplante;

    @Column(name = "origem", length = 80)
    public String origem;

    @Column(name = "codigoanterior", length = 40)
    public String codigoAnterior;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "datarecebimento")
    public LocalDateTime dataRecebimento;

    @Column(name = "usuariorecebimento", length = 80)
    public String usuarioRecebimento;

    @Column(name = "metodolimpeza", length = 30)
    public String metodoLimpeza;

    @Column(name = "datalimpeza")
    public LocalDateTime dataLimpeza;

    @Column(name = "usuariolimpeza", length = 80)
    public String usuarioLimpeza;

    @Column(name = "inspecao", length = 20)
    public String inspecao;

    @Column(name = "embalagem", length = 40)
    public String embalagem;

    @Column(name = "datapreparo")
    public LocalDateTime dataPreparo;

    @Column(name = "usuariopreparo", length = 80)
    public String usuarioPreparo;

    @Column(name = "observacao", columnDefinition = "text")
    public String observacao;

    @Column(name = "idpacote")
    public Long idpacote;

    @Column(name = "datacarga")
    public LocalDateTime dataCarga;

    @PrePersist
    public void prePersist() {
        if (dataRecebimento == null) {
            dataRecebimento = LocalDateTime.now();
        }
    }
}

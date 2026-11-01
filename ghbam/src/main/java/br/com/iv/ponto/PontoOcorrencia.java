package br.com.iv.ponto;

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

/** Ocorrencia de frequencia (ferias, atestado, licenca, abono, falta...); ver PontoApuracao.TipoOcorrencia. */
@Entity
@Table(name = "rh_ponto_ocorrencia")
public class PontoOcorrencia extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idocorrencia")
    public Long idocorrencia;

    @Column(name = "unidade", nullable = false, length = 20)
    public String unidade;

    @Column(name = "matricula", nullable = false, length = 40)
    public String matricula;

    @Column(name = "nome", length = 160)
    public String nome;

    @Column(name = "tipo", nullable = false, length = 30)
    public String tipo;

    @Column(name = "datainicio", nullable = false)
    public LocalDate dataInicio;

    @Column(name = "datafim", nullable = false)
    public LocalDate dataFim;

    /** Abono parcial do dia (ex.: saiu 2h antes para consulta); nulo = dia(s) inteiro(s). */
    @Column(name = "minutos")
    public Integer minutos;

    @Column(name = "periodoaquisitivo", length = 20)
    public String periodoAquisitivo;

    @Column(name = "documento", length = 80)
    public String documento;

    @Column(name = "observacao", columnDefinition = "text")
    public String observacao;

    @Column(name = "usuario", length = 80)
    public String usuario;

    @Column(name = "anulada", nullable = false)
    public Boolean anulada;

    @Column(name = "motivoanulacao", columnDefinition = "text")
    public String motivoAnulacao;

    @Column(name = "usuarioanulacao", length = 80)
    public String usuarioAnulacao;

    @Column(name = "dataregistro")
    public LocalDateTime dataRegistro;

    @PrePersist
    public void prePersist() {
        if (dataRegistro == null) dataRegistro = LocalDateTime.now();
        if (anulada == null) anulada = Boolean.FALSE;
    }
}

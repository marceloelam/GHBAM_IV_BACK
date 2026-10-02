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

/**
 * Uma linha por unidade, ligando/desligando a consulta oficial do CNS/PDQ do DATASUS
 * naquela unidade - ver {@link br.com.iv.service.ConsultaCnsService}. Controlado pela
 * tela /base2/integracao-sus; sem linha para a unidade (ou ativo=false), o sistema usa
 * so o scraping do CNES. Por unidade porque a credencial/certificado tambem e por
 * unidade (integracao_sus_credencial) - nao ha uso real sem as duas coisas juntas.
 */
@Data
@Entity
@Table(name = "configuracao_cns_pdq")
public class ConfiguracaoCnsPdq extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idconfiguracaocnspdq;

    @Column(length = 30, nullable = false, unique = true)
    private String unidade;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(length = 80)
    private String atualizadopor;

    private LocalDateTime atualizadoem;
}

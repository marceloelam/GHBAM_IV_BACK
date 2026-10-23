package br.com.iv.imagemia.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * Uma analise de imagem por IA (MONAI) sobre um exame de um BAM. Ver
 * V206__imagem_ia_analise.sql. A IA apenas sugere; a ciencia e a conduta do
 * profissional ficam nos campos ciencia_*.
 */
@Entity
@Table(name = "imagem_ia_analise")
public class ImagemIaAnalise extends PanacheEntityBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Integer idbam;
    @Column(name = "paciente_id") public Integer pacienteId;
    @Column(length = 40) public String idimagens;
    @Column(name = "descricao_exame", length = 240) public String descricaoExame;
    @Column(length = 120) public String modelo;
    @Column(name = "versao_modelo", length = 40) public String versaoModelo;
    @Column(columnDefinition = "TEXT") public String achado;
    @Column(nullable = false, length = 20) public String severidade = "INFORMATIVO";
    @Column(precision = 5, scale = 4) public BigDecimal score;
    @Column(name = "detalhes_json", columnDefinition = "TEXT") public String detalhesJson;
    @Column(nullable = false, length = 20) public String status = "PENDENTE";
    @Column(length = 240) public String erro;
    @Column(length = 80) public String unidade;
    @Column(name = "solicitado_por_matricula", length = 40) public String solicitadoPorMatricula;
    @Column(name = "solicitado_por_nome", length = 120) public String solicitadoPorNome;
    @Column(name = "criado_em", nullable = false) public LocalDateTime criadoEm = LocalDateTime.now();
    @Column(name = "ciencia_em") public LocalDateTime cienciaEm;
    @Column(name = "ciencia_matricula", length = 40) public String cienciaMatricula;
    @Column(name = "ciencia_nome", length = 120) public String cienciaNome;
    @Column(name = "ciencia_conduta", columnDefinition = "TEXT") public String cienciaConduta;
}

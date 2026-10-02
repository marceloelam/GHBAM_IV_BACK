package br.com.iv.entity;

import java.time.LocalDateTime;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Data;

/**
 * Item do hospital entregue a alguem sob responsabilidade, com obrigacao de devolver.
 * Ver V128__create_cautela.sql para o porque de cada campo.
 */
@Data
@Entity
@Table(name = "cautela")
public class Cautela extends PanacheEntityBase {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Integer id;
 @Column(nullable = false, unique = true, length = 80) public String uuid;
 @Column(nullable = false, length = 40) public String categoria;
 @Column(nullable = false, length = 255) public String item;
 @Column(length = 80) public String identificador;
 @Column(nullable = false) public Integer quantidade;
 @Column(name = "responsavel_nome", nullable = false, length = 180) public String responsavelNome;
 @Column(name = "responsavel_matricula", length = 40) public String responsavelMatricula;
 // true = quem recebeu nao e servidor (fornecedor, acompanhante). Nesse caso
 // responsavelMatricula guarda documento ou empresa, nao matricula.
 @Column(name = "responsavel_externo", nullable = false) public Boolean responsavelExterno;
 @Column(length = 120) public String setor;
 @Column(length = 30) public String unidade;
 @Column(columnDefinition = "TEXT") public String finalidade;
 @Column(columnDefinition = "TEXT") public String observacao;
 @Column(nullable = false, length = 20) public String situacao;
 @Column(name = "data_saida", nullable = false) public LocalDateTime dataSaida;
 @Column(name = "data_prevista") public LocalDateTime dataPrevista;
 @Column(name = "data_devolucao") public LocalDateTime dataDevolucao;
 @Column(name = "estado_devolucao", length = 30) public String estadoDevolucao;
 @Column(name = "observacao_devolucao", columnDefinition = "TEXT") public String observacaoDevolucao;
 @Column(name = "motivo_cancelamento", columnDefinition = "TEXT") public String motivoCancelamento;
 @Column(name = "criado_por", length = 120) public String criadoPor;
 @Column(name = "criado_por_matricula", length = 40) public String criadoPorMatricula;
 @Column(name = "devolvido_por", length = 120) public String devolvidoPor;
 @Column(name = "devolvido_por_matricula", length = 40) public String devolvidoPorMatricula;
 @Column(name = "cancelado_por", length = 120) public String canceladoPor;
 @Column(name = "cancelado_por_matricula", length = 40) public String canceladoPorMatricula;
 @Column(name = "criado_em") public LocalDateTime criadoEm;
 @Column(name = "atualizado_em") public LocalDateTime atualizadoEm;
 @PrePersist void insert(){var now=LocalDateTime.now();criadoEm=now;atualizadoEm=now;if(situacao==null||situacao.isBlank())situacao="EM_CAUTELA";if(quantidade==null||quantidade<1)quantidade=1;if(responsavelExterno==null)responsavelExterno=false;if(dataSaida==null)dataSaida=now;}
 @PreUpdate void update(){atualizadoEm=LocalDateTime.now();}
}

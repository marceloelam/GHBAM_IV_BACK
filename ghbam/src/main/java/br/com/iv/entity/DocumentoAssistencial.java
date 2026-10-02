package br.com.iv.entity;
import java.time.LocalDateTime;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Data;
@Data @Entity @Table(name="documento_assistencial")
public class DocumentoAssistencial extends PanacheEntityBase {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public int id;
 @Column(nullable=false,unique=true,length=80) public String uuid;
 @Column(nullable=false,length=40) public String tipo;
 @Column(nullable=false,length=30) public String status;
 public int idpaciente; public Integer idbam; @Column(length=40) public String prontuario; @Column(length=180) public String nomepaciente; @Column(length=30) public String unidade;
 @Column(nullable=false,length=180) public String titulo; @Column(name="conteudo_json",columnDefinition="TEXT",nullable=false) public String conteudoJson;
 @Column(name="criado_por",length=120) public String criadoPor; @Column(name="criado_por_matricula",length=40) public String criadoPorMatricula;
 @Column(name="concluido_por",length=120) public String concluidoPor; @Column(name="concluido_por_matricula",length=40) public String concluidoPorMatricula;
 @Column(name="cancelado_por",length=120) public String canceladoPor; @Column(name="cancelado_por_matricula",length=40) public String canceladoPorMatricula;
 @Column(name="motivo_cancelamento",columnDefinition="TEXT") public String motivoCancelamento;
 @Column(name="criado_em") public LocalDateTime criadoEm; @Column(name="atualizado_em") public LocalDateTime atualizadoEm; @Column(name="concluido_em") public LocalDateTime concluidoEm; @Column(name="cancelado_em") public LocalDateTime canceladoEm;
 @PrePersist void insert(){var now=LocalDateTime.now(); criadoEm=now; atualizadoEm=now;} @PreUpdate void update(){atualizadoEm=LocalDateTime.now();}
}

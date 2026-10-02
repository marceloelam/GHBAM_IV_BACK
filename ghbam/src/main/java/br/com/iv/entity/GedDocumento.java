package br.com.iv.entity;

import java.time.LocalDateTime;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "ged_documento")
public class GedDocumento extends PanacheEntityBase {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Integer id;
 @Column(nullable = false, unique = true, length = 80) public String uuid;
 @Column(nullable = false, length = 80) public String tipo;
 @Column(nullable = false, length = 30) public String status;
 public Integer idpaciente;
 public Integer idbam;
 @Column(length = 40) public String prontuario;
 @Column(length = 180) public String nomePaciente;
 @Column(length = 30) public String unidade;
 @Column(nullable = false, length = 255) public String nomeArquivo;
 @Column(nullable = false, length = 120) public String tipoMime;
 public Long tamanhoBytes;
 @Column(columnDefinition = "TEXT") public String descricao;
 @Column(nullable = false, columnDefinition = "TEXT") public String conteudoBase64;
 @Column(name = "criado_por", length = 120) public String criadoPor;
 @Column(name = "criado_por_matricula", length = 40) public String criadoPorMatricula;
 @Column(name = "removido_por", length = 120) public String removidoPor;
 @Column(name = "removido_por_matricula", length = 40) public String removidoPorMatricula;
 @Column(name = "motivo_remocao", columnDefinition = "TEXT") public String motivoRemocao;
 public LocalDateTime criadoEm;
 public LocalDateTime atualizadoEm;
 public LocalDateTime removidoEm;
 @PrePersist void insert(){var now=LocalDateTime.now();criadoEm=now;atualizadoEm=now;if(status==null||status.isBlank())status="ATIVO";}
 @PreUpdate void update(){atualizadoEm=LocalDateTime.now();}
}

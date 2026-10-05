package br.com.iv.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pharmacia_confirmacao_chave", uniqueConstraints = @UniqueConstraint(columnNames = {"unidade", "chave"}))
public class PharmaciaConfirmacaoChave {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 20) public String unidade;
    @Column(nullable = false, length = 80) public String chave;
    @Column(nullable = false, length = 64) public String hash;
    @Column(nullable = false) public Long paciente;
    @Column(nullable = false, length = 40) public String matricula;
    @Column(name = "criado_em", nullable = false) public LocalDateTime criadoEm;
    @Column(name = "resposta_json", nullable = false, columnDefinition = "TEXT") public String respostaJson;
}

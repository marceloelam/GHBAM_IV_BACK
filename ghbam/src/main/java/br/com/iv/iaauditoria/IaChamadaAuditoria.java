package br.com.iv.iaauditoria;

import java.time.LocalDateTime;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/** Uma linha por chamada a LLM. Ver V187__create_ia_chamada_auditoria.sql. */
@Entity
@Table(name = "ia_chamada_auditoria")
public class IaChamadaAuditoria extends PanacheEntityBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "criado_em", nullable = false) public LocalDateTime criadoEm;
    @Column(nullable = false, length = 80) public String origem;
    @Column(length = 40) public String matricula;
    @Column(name = "usuario_nome", length = 120) public String usuarioNome;
    @Column(length = 80) public String unidade;
    @Column(name = "request_id", length = 80) public String requestId;
    @Column(name = "paciente_id") public Integer pacienteId;
    @Column(length = 20) public String bam;
    @Column(length = 20) public String provedor;
    @Column(length = 120) public String modelo;
    @Column(nullable = false, length = 20) public String status;
    @Column(name = "tempo_ms", nullable = false) public long tempoMs;
    @Column(name = "tokens_entrada") public Integer tokensEntrada;
    @Column(name = "tokens_saida") public Integer tokensSaida;
    @Column(name = "qtd_mensagens") public Integer qtdMensagens;
    @Column(name = "hash_contexto", length = 64) public String hashContexto;
    @Column(name = "hash_resposta", length = 64) public String hashResposta;
    @Column(columnDefinition = "TEXT") public String pergunta;
    @Column(columnDefinition = "TEXT") public String resposta;
    @Column(length = 240) public String erro;
}

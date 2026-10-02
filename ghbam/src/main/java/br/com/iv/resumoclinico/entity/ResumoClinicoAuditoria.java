package br.com.iv.resumoclinico.entity;
import java.time.LocalDateTime;
import br.com.iv.resumoclinico.dto.PeriodoResumoClinico;
import br.com.iv.resumoclinico.dto.TipoResumoClinico;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
@Entity
@Table(name = "resumo_clinico_auditoria")
public class ResumoClinicoAuditoria extends PanacheEntityBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "paciente_id", nullable = false) public int pacienteId;
    @Column(nullable = false) public int bam;
    @Column(nullable = false, length = 40) public String usuario;
    @Column(name = "usuario_nome", length = 120) public String usuarioNome;
    @Enumerated(EnumType.STRING) @Column(name = "tipo_resumo", nullable = false, length = 20) public TipoResumoClinico tipoResumo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) public PeriodoResumoClinico periodo;
    @Column(name = "gerado_em", nullable = false) public LocalDateTime geradoEm;
    @Column(length = 80) public String modelo;
    @Column(length = 20, nullable = false) public String modo;
    @Column(length = 20, nullable = false) public String status;
    @Column(name = "tempo_processamento_ms") public long tempoProcessamentoMs;
    @Column(name = "tokens_entrada") public Integer tokensEntrada;
    @Column(name = "tokens_saida") public Integer tokensSaida;
    @Column(name = "hash_contexto", length = 64) public String hashContexto;
    @Column(name = "fontes_consultadas", columnDefinition = "TEXT") public String fontesConsultadas;
    @Column(length = 240) public String erro;
    @Column(name = "ip_usuario", length = 64) public String ipUsuario;
}

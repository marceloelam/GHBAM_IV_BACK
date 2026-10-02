package br.com.iv.pharmacia.validacao.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Registro da validacao farmaceutica de uma dispensacao.
 *
 * Existe por tres motivos: a analise da prescricao e atividade privativa do
 * farmaceutico e precisa ficar documentada; alerta critico confirmado exige
 * justificativa rastreavel; e o conjunto desses registros e a materia-prima dos
 * indicadores de intervencao farmaceutica.
 */
@Entity
@Table(name = "pharmacia_validacao")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PharmaciaValidacao extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idpaciente")
    private Integer idpaciente;

    @Column(name = "paciente_nome", length = 200)
    private String pacienteNome;

    @Column(name = "idbam")
    private Integer idbam;

    @Column(name = "unidade_sigla", length = 20)
    private String unidadeSigla;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    @Column(name = "usuario_matricula", length = 30)
    private String usuarioMatricula;

    @Column(name = "usuario_nome", length = 150)
    private String usuarioNome;

    /** Versao da base de conhecimento vigente na avaliacao - permite reler o alerta no contexto da epoca. */
    @Column(name = "versao_base", length = 30)
    private String versaoBase;

    @Column(name = "total_alertas")
    private Integer totalAlertas = 0;

    @Column(name = "alertas_criticos")
    private Integer alertasCriticos = 0;

    /** Alertas serializados como vieram do motor, para consulta e auditoria. */
    @Column(name = "alertas_json", columnDefinition = "TEXT")
    private String alertasJson;

    /** Itens avaliados, serializados. */
    @Column(name = "itens_json", columnDefinition = "TEXT")
    private String itensJson;

    /** Justificativa do farmaceutico quando confirma apesar de alerta critico. */
    @Column(name = "justificativa", length = 2000)
    private String justificativa;

    /** Matricula do segundo profissional na dupla checagem de medicamento potencialmente perigoso. */
    @Column(name = "dupla_checagem_matricula", length = 30)
    private String duplaChecagemMatricula;

    /** DISPENSADO, DISPENSADO_COM_JUSTIFICATIVA, SUSPENSO, ALTERADO, APENAS_CONSULTA. */
    @Column(name = "desfecho", length = 40)
    private String desfecho;

    /** Preenchido quando a validacao resultou em contato com o prescritor. */
    @Column(name = "intervencao", length = 1000)
    private String intervencao;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getIdpaciente() { return idpaciente; }
    public void setIdpaciente(Integer idpaciente) { this.idpaciente = idpaciente; }
    public String getPacienteNome() { return pacienteNome; }
    public void setPacienteNome(String pacienteNome) { this.pacienteNome = pacienteNome; }
    public Integer getIdbam() { return idbam; }
    public void setIdbam(Integer idbam) { this.idbam = idbam; }
    public String getUnidadeSigla() { return unidadeSigla; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }
    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }
    public String getUsuarioMatricula() { return usuarioMatricula; }
    public void setUsuarioMatricula(String usuarioMatricula) { this.usuarioMatricula = usuarioMatricula; }
    public String getUsuarioNome() { return usuarioNome; }
    public void setUsuarioNome(String usuarioNome) { this.usuarioNome = usuarioNome; }
    public String getVersaoBase() { return versaoBase; }
    public void setVersaoBase(String versaoBase) { this.versaoBase = versaoBase; }
    public Integer getTotalAlertas() { return totalAlertas; }
    public void setTotalAlertas(Integer totalAlertas) { this.totalAlertas = totalAlertas; }
    public Integer getAlertasCriticos() { return alertasCriticos; }
    public void setAlertasCriticos(Integer alertasCriticos) { this.alertasCriticos = alertasCriticos; }
    public String getAlertasJson() { return alertasJson; }
    public void setAlertasJson(String alertasJson) { this.alertasJson = alertasJson; }
    public String getItensJson() { return itensJson; }
    public void setItensJson(String itensJson) { this.itensJson = itensJson; }
    public String getJustificativa() { return justificativa; }
    public void setJustificativa(String justificativa) { this.justificativa = justificativa; }
    public String getDuplaChecagemMatricula() { return duplaChecagemMatricula; }
    public void setDuplaChecagemMatricula(String duplaChecagemMatricula) { this.duplaChecagemMatricula = duplaChecagemMatricula; }
    public String getDesfecho() { return desfecho; }
    public void setDesfecho(String desfecho) { this.desfecho = desfecho; }
    public String getIntervencao() { return intervencao; }
    public void setIntervencao(String intervencao) { this.intervencao = intervencao; }
}

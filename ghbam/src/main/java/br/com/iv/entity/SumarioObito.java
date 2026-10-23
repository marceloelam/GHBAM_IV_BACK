package br.com.iv.entity;

import java.time.LocalDate;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Data;

@Data @Entity(name = "SumarioObito")
public class SumarioObito extends PanacheEntityBase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    private Integer idResumoAlta;
    @Column(length=80, nullable=false) private String unidade;
    @Column(length=15) private String bam;
    @Column(length=120) private String nomePaciente;
    @Column(length=30) private String prontuario;
    @Column(length=20) private String dataInternacao, dataNascimento;
    @Column(length=10) private String idade;
    @Column(length=20) private String sexo;
    private LocalDate dataObito;
    @Column(length=10) private String horaObito;
    @Column(length=30) private String numeroDO;
    @Column(length=80) private String setor;
    @Column(length=30) private String permanenciaHospitalar;
    @Column(length=80) private String periodoMulher, causaExterna;
    @Column(length=120) private String estatura, corIdadePresumida;
    @Column(columnDefinition="TEXT") private String sinaisAparentes, vestuario;
    @Column(length=255) private String causaTerminal, causaIntermediaria1, causaIntermediaria2, causaBasica;
    @Column(length=30) private String tempoTerminal, tempoIntermediaria1, tempoIntermediaria2, tempoBasica;
    @Column(columnDefinition="TEXT") private String condicoesContribuintes, sumarioAdmissao;
    @Column(length=30) private String agravoNotificacao;
    @Column(length=255) private String agravoEspecificar;
    @Column(length=120) private String medicoAssistente, revisor;
    @Column(length=30) private String crmAssistente, crmRevisor;
    @Column(length=20) private String dataAssinaturaAssistente, dataAssinaturaRevisor;
    // Cancelamento preserva o registro; o documento passa a imprimir com marca d'água "CANCELADO".
    @Column(length=20, nullable=false) private String status = "ATIVO";
    private java.time.LocalDateTime canceladoEm;
    @Column(length=120) private String canceladoPor;
    @Column(columnDefinition = "TEXT") private String motivoCancelamento;
}

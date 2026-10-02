package br.com.iv.entity;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity(name = "listaanestesica")
public class ListaAnestesica extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idlistaanestesica;

    // Dados principais
    private int bam;
    private String data;
    
    // Dados do paciente
    private String nomePaciente;
    private String prontuario;
    private String dataNascimento;
    private String nomeMae;
    private String enfLeito;
    
    // Dados da anestesia
    private String numeroSala;
    private String asa;
    private String efeitoPreMedicacao;
    
    // Agentes anestésicos
    private String o2;
    private String n2o;
    private String propofol;
    private String remifentanil;
    private String cristaloides;
    private String hemoderivados;
    
    // Monitorização
    private String pvc;
    private String pa;
    private String bis;
    private String resp;
    private String temp;
    private String spo2;
    private String etco2;
    
    // Procedimento - Intubação
    private String intubacao;
    private boolean intubacaoDificil;
    private boolean intubacaoOral;
    private boolean intubacaoNasal;
    private boolean comBalonete;
    private String canulaNumero;
    
    // Procedimento - Ventilação
    private String ventilacao;
    private boolean ventilacaoEspontanea;
    private boolean ventilacaoControlada;
    private boolean ventilacaoManual;
    private boolean ventilacaoMecanica;
    
    // Ventilador
    private String ventiladorModo;
    private boolean ventiladorPcv;
    private boolean ventiladorVcv;
    private String vc;
    private String freq;
    private String pres;
    private String peep;
    private String relacaoIE;
    private boolean comReabsorcao;
    private boolean semReabsorcao;
    private boolean circComValv;
    
    // Outros dados
    private boolean intercorrencias;
    private String descricaoIntercorrencias;
    private String cirurgioes;
    private String anestesistas;
    private String procedimento;
    private String horaInicio;
    private String horaFim;
    private String horaHemoderivado;
    private String horaAntibiotico;
    
    private String supraglotico;
    private boolean mMacial;
    private boolean mLaringea;
    
    private String observacoes;

    public ListaAnestesica() {
        super();
        this.bam = 0;
        this.data = "";
        this.nomePaciente = "";
        this.prontuario = "";
        this.dataNascimento = "";
        this.nomeMae = "";
        this.enfLeito = "";
        this.numeroSala = "";
        this.asa = "";
        this.efeitoPreMedicacao = "";
        this.o2 = "";
        this.n2o = "";
        this.propofol = "";
        this.remifentanil = "";
        this.cristaloides = "";
        this.hemoderivados = "";
        this.pvc = "";
        this.pa = "";
        this.bis = "";
        this.resp = "";
        this.temp = "";
        this.spo2 = "";
        this.etco2 = "";
        this.intubacao = "";
        this.intubacaoDificil = false;
        this.intubacaoOral = false;
        this.intubacaoNasal = false;
        this.comBalonete = false;
        this.canulaNumero = "";
        this.ventilacao = "";
        this.ventilacaoEspontanea = false;
        this.ventilacaoControlada = false;
        this.ventilacaoManual = false;
        this.ventilacaoMecanica = false;
        this.ventiladorModo = "";
        this.ventiladorPcv = false;
        this.ventiladorVcv = false;
        this.vc = "";
        this.freq = "";
        this.pres = "";
        this.peep = "";
        this.relacaoIE = "";
        this.comReabsorcao = false;
        this.semReabsorcao = false;
        this.circComValv = false;
        this.intercorrencias = false;
        this.descricaoIntercorrencias = "";
        this.cirurgioes = "";
        this.anestesistas = "";
        this.procedimento = "";
        this.horaInicio = "";
        this.horaFim = "";
        this.horaHemoderivado = "";
        this.horaAntibiotico = "";
        this.supraglotico = "";
        this.mMacial = false;
        this.mLaringea = false;
        this.observacoes = "";
    }
}

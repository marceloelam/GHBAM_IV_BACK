package br.com.iv.dto;

import java.io.Serializable;
import lombok.Data;

@Data
public class ListaAnestesicaDto implements Serializable {
    private static final long serialVersionUID = 1L;

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
}

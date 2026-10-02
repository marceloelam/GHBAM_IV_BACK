package br.com.iv.entity;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "balanco_hidrico_entradas")
public class BalancoHidricoEntrada extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;
	@Id
	@Column(name = "uuid", unique = true, nullable = false, length = 36)
	private String uuid; // UUID para controle frontend
	private String hora; // Formato HH:mm
	private String tipo; // ORAL, PARENTERAL, ENTERAL
	private double volumeMl;
	@Column(name = "observacao", length = 500)
	private String observacao;
	private String bam; // Referência para sincronização
	private String dataRegistro;
	private String dataAtualizacao;
	private String subtipo;
	@Column(name = "profissional", length = 180)
	private String profissional;
	@Column(name = "turno", length = 30)
	private String turno;

	
	// Lifecycle callbacks
	@PrePersist
	protected void onCreate() {
		dataRegistro = converteData();
		dataAtualizacao = converteTime();
	}

	@PreUpdate
	protected void onUpdate() {
		dataAtualizacao =  converteData();;
	}


	protected String converteData() {
		Date dataAtual = new Date();
		SimpleDateFormat formatador = new SimpleDateFormat("yyyy/MM/dd");
		String dataFormatada = formatador.format(dataAtual);

		return dataFormatada;
	}

	protected String converteTime() {
		Date horaAtual = new Date();
		SimpleDateFormat formatador = new SimpleDateFormat("HH:mm");
		String timeFormatada = formatador.format(horaAtual);

		return timeFormatada;
	}
	
	

}

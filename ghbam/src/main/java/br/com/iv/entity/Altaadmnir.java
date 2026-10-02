package br.com.iv.entity;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "altaadmnir")
public class Altaadmnir extends PanacheEntityBase implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idalta;
    
    private int bam;
    private String data;
    
    @Column(length = 5)
    private String hora;
    
    
    @Column(length = 150)
    private String servidormatricula;

	public Altaadmnir() {
		data = converteData();
		hora = converteTime();

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

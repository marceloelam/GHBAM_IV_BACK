package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity
@Table(name = "logs")
public class Log  extends PanacheEntityBase  implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final int TAMANHO_HORA = 5;
	private static final int TAMANHO_UNIDADE = 20;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column()
	private int idlog;
	private LocalDate data;
	@Column(length = TAMANHO_HORA)
	private String hora;
	@Column(columnDefinition = "TEXT")
	private String evento;
	@Column(columnDefinition = "TEXT")
	private String dados;
	@Column(length = TAMANHO_UNIDADE)
	private String unidade;

	/**
	 * Um log jamais pode derrubar a operação que o originou. Clientes enviam a
	 * unidade em formatos variados ("SIGLA", "SIGLA:Nome do Hospital", nome por
	 * extenso), então normalizamos para a sigla e cortamos no limite da coluna.
	 */
	@PrePersist
	@PreUpdate
	void normalizarCampos() {
		this.unidade = truncar(extrairSigla(this.unidade), TAMANHO_UNIDADE);
		this.hora = truncar(this.hora, TAMANHO_HORA);
	}

	private static String extrairSigla(String valor) {
		if (valor == null) {
			return null;
		}
		int separador = valor.indexOf(':');
		return (separador >= 0 ? valor.substring(0, separador) : valor).trim();
	}

	private static String truncar(String valor, int limite) {
		if (valor == null || valor.length() <= limite) {
			return valor;
		}
		return valor.substring(0, limite);
	}

}

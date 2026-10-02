package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class LogDto implements Serializable {
	private static final long serialVersionUID = 1L;

	private int idlog;
	private LocalDate data;
	@Column(length = 5)
	private String hora;
	@Column(length = 600)
	private String evento;
	@Column(columnDefinition = "TEXT")
	private String dados;
	@Column(length = 20)
	private String unidade;

	// Preenchidos so pelo AuditRequestFilter; o nome do paciente e resolvido na
	// thread assincrona do log. Ignorados no JSON para o cliente nao forjar.
	@JsonIgnore
	private String pacienteTipo;
	@JsonIgnore
	private String pacienteValor;
}

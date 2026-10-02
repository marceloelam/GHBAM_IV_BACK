package br.com.iv.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.Data;

@Data
@Entity
@Table(name = "cirurgia_tempo")
public class CirurgiaTempo extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer idtempo;

	@Column(nullable = false, unique = true)
	private int idcirurgia;

	@Column(name = "entrada_sala")
	private LocalDateTime entradaSala;

	@Column(name = "inicio_anestesia")
	private LocalDateTime inicioAnestesia;

	@Column(name = "fim_anestesia")
	private LocalDateTime fimAnestesia;

	@Column(name = "inicio_cirurgia")
	private LocalDateTime inicioCirurgia;

	@Column(name = "fim_cirurgia")
	private LocalDateTime fimCirurgia;

	@Column(name = "saida_sala")
	private LocalDateTime saidaSala;

	@Column(name = "inicio_limpeza")
	private LocalDateTime inicioLimpeza;

	@Column(name = "fim_limpeza")
	private LocalDateTime fimLimpeza;

	@Column(name = "atualizado_em")
	private LocalDateTime atualizadoEm;

	@Column(name = "usuario_registro", length = 40)
	private String usuarioRegistro;

	public CirurgiaTempo() {
		super();
	}
}

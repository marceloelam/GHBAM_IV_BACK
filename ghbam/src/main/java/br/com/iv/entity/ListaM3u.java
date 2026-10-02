package br.com.iv.entity;

import java.io.Serializable;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity(name = "lista_m3u")
@Table(
	name = "lista_m3u",
	uniqueConstraints = @UniqueConstraint(name = "uk_lista_m3u_nome", columnNames = "nome")
)
public class ListaM3u extends PanacheEntityBase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 160)
	private String nome;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String url;
}

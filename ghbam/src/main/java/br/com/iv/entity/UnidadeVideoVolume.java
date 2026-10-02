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
@Entity(name = "unidade_video_volume")
@Table(
	name = "unidade_video_volume",
	uniqueConstraints = @UniqueConstraint(name = "uk_unidade_video_volume_sigla_tv", columnNames = { "sigla", "tv" })
)
public class UnidadeVideoVolume extends PanacheEntityBase implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 15)
	private String sigla;

	@Column(nullable = false)
	private int tv;

	@Column(nullable = false)
	private boolean mudo;

	@Column(nullable = false)
	private int volume;

	@Column(name = "canal_id")
	private Long canalId;

	public UnidadeVideoVolume() {
		this.mudo = false;
		this.volume = 80;
	}
}

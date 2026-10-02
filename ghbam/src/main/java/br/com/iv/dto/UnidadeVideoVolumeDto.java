package br.com.iv.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnidadeVideoVolumeDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private boolean mudo;
	private int volume;
	private Long canalId;
	private String canalNome;
	private String canalDescricao;
	private String canalLogo;
	private String canalUrl;
}

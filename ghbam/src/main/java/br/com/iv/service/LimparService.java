package br.com.iv.service;

import java.io.Serializable;

import br.com.iv.repository.LimparPainelRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LimparService implements Serializable {

	private static final long serialVersionUID = 1L;

	@Inject
	LimparPainelRepository limparPainelRepository;


	public void limparPainel(String unidade) {
		limparPainelRepository.limparPainel(unidade);
	}
}

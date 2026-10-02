package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.ProntuarioDto;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Prontuario;
import br.com.iv.repository.ProntuarioRepository;

@ApplicationScoped
public class ProntuarioService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	ProntuarioRepository prontuarioRepository;

	
	@Transactional
	// Salvar os setores
	public Prontuario salvarProntuario(ProntuarioDto dto) {
		Prontuario prontuario = new Prontuario();
		
		prontuario.setProntuario(dto.getProntuario());
		prontuario.setControle(dto.getControle());
		
		prontuario.persist();
		return prontuario;
	}

	public Prontuario listarPorControle(String controle) {
		return prontuarioRepository.filtrarCodPront(controle);
	}
	
	
	

}

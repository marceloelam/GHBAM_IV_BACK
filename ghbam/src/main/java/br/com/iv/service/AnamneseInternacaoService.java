package br.com.iv.service;

import java.io.Serializable;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.AnamneseInternacaoDto;
import br.com.iv.entity.AnamneseInternacao;
import br.com.iv.repository.AnamneseInternacaoRepository;

@ApplicationScoped
public class AnamneseInternacaoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	AnamneseInternacaoRepository anamneseInternacaoRepository;

	public AnamneseInternacao filtarPorUnicoAnamneseInternacao(int id) {
		return anamneseInternacaoRepository.filtrarPorUnicoAnamneseInternacao(id);
	}
	
	public AnamneseInternacao filtarPorUnicoAnamneseInternacaoPeloBam(int id) {
		return anamneseInternacaoRepository.filtrarPorUnicoAnamneseInternacaoPeloBam(id);
	}

	// Salvar bam
	@Transactional
	public AnamneseInternacao salvarAnamnese(AnamneseInternacaoDto dto) {
		AnamneseInternacao anamnese = new AnamneseInternacao();
		anamnese.setVerso(dto.getVerso());
		anamnese.setFrente(dto.getFrente());
		anamnese.setMedico(dto.getMedico());
		anamnese.setCrm(dto.getCrm());
		anamnese.setNbam(dto.getNbam());
		anamnese.setProntuario(dto.getProntuario());
		// anamnese.setData(Date.from(dto.getData().toInstant().plus(1,
		// ChronoUnit.DAYS))); // Data acrescida de 1 dia

		anamnese.persist();
		return anamnese;
	}

	// Alterar
	@Transactional
	public void atualizarAnamneseInternacao(int id, AnamneseInternacaoDto dto) {
		Optional<AnamneseInternacao> anamneseInternacaoOp = AnamneseInternacao.findByIdOptional(id);
		AnamneseInternacao anamneseInternacao = anamneseInternacaoOp.orElseThrow(() -> new NullPointerException("Anamnese não foi encontrado..."));

		anamneseInternacao.setVerso(dto.getVerso());
		anamneseInternacao.setFrente(dto.getFrente());
		anamneseInternacao.setMedico(dto.getMedico());
		anamneseInternacao.setCrm(dto.getCrm());
		anamneseInternacao.setNbam(dto.getNbam());
		anamneseInternacao.setProntuario(dto.getProntuario());
		anamneseInternacao.persist();
	}

}

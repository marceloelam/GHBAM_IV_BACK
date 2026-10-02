package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

import br.com.iv.entity.Internacao;
import br.com.iv.entity.ResumoFinalAtendimentoMedico;
import br.com.iv.repository.ResumoFinalAvaliacaoMedicoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ResumoFinalAvaliacaoMedicoService implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@Inject
	ResumoFinalAvaliacaoMedicoRepository resumoFinalAvaliacaoMedicoRepository;

	@Inject
	FhirDispatchService fhirDispatchService;
	
	@Transactional
	public List<ResumoFinalAtendimentoMedico> filtarPorResumoFinal(String data, String unidade) {

		List<ResumoFinalAtendimentoMedico> resumo = resumoFinalAvaliacaoMedicoRepository.listarResumoFinalMedico(data, unidade);
		if (resumo.isEmpty()) {
			System.out.println("Resumo de atendimento médico não foi encontrado...");
		}
		return resumo;
	}
	
	
	@Transactional
	public Response restaurarinternacao(String bam) {
		// Interrompe qualquer envio ainda nao concluido antes de remover o resumo.
		// Envios ja confirmados permanecem no historico para auditoria.
		fhirDispatchService.cancelarFilaPorBam(bam);
		resumoFinalAvaliacaoMedicoRepository.cancelarResumodealta(bam);
	return null;
	}
	
	
	
}

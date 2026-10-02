package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ReceitaMedicamentoDto;
import br.com.iv.entity.ReceitaMedicamentos;
import br.com.iv.repository.ReceitaMedicamentosRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ReceitaMedicamentoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	EntityManager entityManager;
	@Inject
	ReceitaMedicamentosRepository receitaMedicamentosRepository;

	public List<ReceitaMedicamentos> listarReceitaMedica(int bam) {
		return receitaMedicamentosRepository.receitaMedica(bam);
	}
	
	@Transactional
	public void atualizarObservacaoReceita(int bam, String obs) {
		receitaMedicamentosRepository.atualizarObservacaoReceita( bam,  obs);
	}
	
	// Salvar
	@Transactional
	public ReceitaMedicamentos salvarReceitaMedicamento(ReceitaMedicamentoDto dto) {
		ReceitaMedicamentos receitaMedicamentos = new ReceitaMedicamentos();
		
		receitaMedicamentos.setNbam(dto.getNbam());
		receitaMedicamentos.setRemedio(dto.getRemedio());
		receitaMedicamentos.setComplemento(dto.getComplemento());
		receitaMedicamentos.setQtde(dto.getQtde());
		receitaMedicamentos.setTipo(dto.getTipo());;
		receitaMedicamentos.setTempo(dto.getTempo());
		receitaMedicamentos.setDurante(dto.getDurante());
		receitaMedicamentos.setPeriodo(dto.getPeriodo());
		receitaMedicamentos.setObservacao(dto.getObservacao());
		receitaMedicamentos.setMedicocrm(dto.getMedicocrm());
		receitaMedicamentos.setDataehora(dto.getDataehora());
		
		entityManager.persist(receitaMedicamentos);
		entityManager.flush();
		return receitaMedicamentos;
	}

	@Transactional
	public void removeReceitaMedicamento(int id) {
	    Optional<ReceitaMedicamentos> receitaMedicamentoOP = ReceitaMedicamentos.findByIdOptional(id);
	    if (receitaMedicamentoOP.isEmpty()) {
	        System.out.println("Receita de Medicamento não foi encontrado...");
	    }

	    ReceitaMedicamentos receitaMedicamentos = receitaMedicamentoOP.get();
	    receitaMedicamentos.delete();
	}
	
}

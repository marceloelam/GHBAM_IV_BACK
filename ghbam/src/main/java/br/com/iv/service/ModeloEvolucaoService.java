package br.com.iv.service;

import java.io.Serializable;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ModeloEvolucaoDto;
import br.com.iv.entity.ModeloEvolucao;
import br.com.iv.repository.ModeloEvolucaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ModeloEvolucaoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	ModeloEvolucaoRepository modeloEvolucaoRepository;

	@Transactional
	public List<ModeloEvolucao> filtarPorModeloEvolucao(String nome, ModeloEvolucaoDto dto) {

		List<ModeloEvolucao> modeloEvolucaoOp = modeloEvolucaoRepository.filtarPorTituloModelo(nome);
		if (modeloEvolucaoOp.isEmpty()) {
			System.out.println("Esse Modelo de evolução não foi encontrado...");
		}
		return modeloEvolucaoOp;
	}

	// Salvar
	@Transactional
	public ModeloEvolucao salvarModeloEvolucao(ModeloEvolucaoDto dto) throws ParseException {
		ModeloEvolucao modeloEvolucao = new ModeloEvolucao();
		modeloEvolucao.setModelo(dto.getModelo());
		modeloEvolucao.setTitulo(dto.getTitulo());
		modeloEvolucao.setCriador(dto.getCriador());
		modeloEvolucao.setSigla(dto.getSigla());
		modeloEvolucao.setUnidade(dto.getUnidade());		
		modeloEvolucao.persist();
		return modeloEvolucao;

	}

	@Transactional
	public void removeModeloEvolucao(int id) {
	    Optional<ModeloEvolucao> modeloEvolucaoOP = ModeloEvolucao.findByIdOptional(id);
	    if (modeloEvolucaoOP.isEmpty()) {
	        System.out.println("Modelo de evolução não foi encontrado...");
	    }

	    ModeloEvolucao modeloDeEvolucao = modeloEvolucaoOP.get();
	    modeloDeEvolucao.delete();
	}

}

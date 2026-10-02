package br.com.iv.service;

import java.io.Serializable;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ModeloEvolucaoEnfDto;
import br.com.iv.entity.ModeloEvolucaoEnf;
import br.com.iv.repository.ModeloEvolucaoEnfRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ModeloEvolucaoEnfService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	ModeloEvolucaoEnfRepository modeloEvolucaoEnfRepository;

	@Transactional
	public List<ModeloEvolucaoEnf> filtarPorModeloEvolucao(String nome, ModeloEvolucaoEnfDto dto) {

		List<ModeloEvolucaoEnf> modeloEvolucaoEnfOp = modeloEvolucaoEnfRepository.filtarPorTituloModelo(nome);
		if (modeloEvolucaoEnfOp.isEmpty()) {
			System.out.println("Esse Modelo de evolução de enfermagem não foi encontrado...");
		}
		return modeloEvolucaoEnfOp;
	}

	// Salvar
	@Transactional
	public ModeloEvolucaoEnf salvarModeloEvolucao(ModeloEvolucaoEnfDto dto) throws ParseException {
		ModeloEvolucaoEnf modeloEvolucaoEnf = new ModeloEvolucaoEnf();
		modeloEvolucaoEnf.setModelo(dto.getModelo());
		modeloEvolucaoEnf.setTitulo(dto.getTitulo());
		modeloEvolucaoEnf.setCriador(dto.getCriador());
		modeloEvolucaoEnf.setSigla(dto.getSigla());
		modeloEvolucaoEnf.setUnidade(dto.getUnidade());
		modeloEvolucaoEnf.persist();
		return modeloEvolucaoEnf;

	}

	@Transactional
	public void removeModeloEvolucao(int id) {
	    Optional<ModeloEvolucaoEnf> modeloEvolucaoEnfOP = ModeloEvolucaoEnf.findByIdOptional(id);
	    if (modeloEvolucaoEnfOP.isEmpty()) {
	        System.out.println("Modelo de evolução de enfermagem não foi encontrado...");
	    }

	    ModeloEvolucaoEnf modeloDeEvolucao = modeloEvolucaoEnfOP.get();
	    modeloDeEvolucao.delete();
	}

}

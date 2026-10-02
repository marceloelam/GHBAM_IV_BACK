package br.com.iv.service;

import java.io.Serializable;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ModeloPrescricaoEnfDto;
import br.com.iv.entity.ModeloPrescricaoEnf;
import br.com.iv.repository.ModeloPrescricaoEnfRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ModeloPrescricaoEnfService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	ModeloPrescricaoEnfRepository modeloPrescricaoEnfRepository;

	@Transactional
	public List<ModeloPrescricaoEnf> filtarPorModeloPrescricao(String nome, ModeloPrescricaoEnfDto dto) {

		List<ModeloPrescricaoEnf> modeloPrescricaoEnfOp = modeloPrescricaoEnfRepository.filtarPorTituloModelo(nome);
		if (modeloPrescricaoEnfOp.isEmpty()) {
			System.out.println("Esse Modelo de prescrição de enfermagem não foi encontrado...");
		}
		return modeloPrescricaoEnfOp;
	}

	// Salvar
	@Transactional
	public ModeloPrescricaoEnf salvarModeloPrescricao(ModeloPrescricaoEnfDto dto) throws ParseException {
		ModeloPrescricaoEnf modeloPrescricaoEnf = new ModeloPrescricaoEnf();
		modeloPrescricaoEnf.setModelo(dto.getModelo());
		modeloPrescricaoEnf.setTitulo(dto.getTitulo());
		modeloPrescricaoEnf.setCriador(dto.getCriador());
		modeloPrescricaoEnf.setSigla(dto.getSigla());
		modeloPrescricaoEnf.setUnidade(dto.getUnidade());
		modeloPrescricaoEnf.persist();
		return modeloPrescricaoEnf;

	}

	@Transactional
	public void removeModeloPrescricao(int id) {
	    Optional<ModeloPrescricaoEnf> modeloPrescricaoEnfOP = ModeloPrescricaoEnf.findByIdOptional(id);
	    if (modeloPrescricaoEnfOP.isEmpty()) {
	        System.out.println("Modelo de prescrição de enfermagem não foi encontrado...");
	    }

	    ModeloPrescricaoEnf modeloDePrescricao = modeloPrescricaoEnfOP.get();
	    modeloDePrescricao.delete();
	}

}

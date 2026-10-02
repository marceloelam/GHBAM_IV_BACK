package br.com.iv.service;

import java.io.Serializable;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ModeloPrescricaoDto;
import br.com.iv.entity.ModeloPrescricao;
import br.com.iv.repository.ModeloPrescricaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ModeloPrescricaoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	ModeloPrescricaoRepository modeloPrescricaoRepository;

	@Transactional
	public List<ModeloPrescricao> filtarPorModeloPrescricao(String nome, ModeloPrescricaoDto dto) {

		List<ModeloPrescricao> modeloPrescricaoOp = modeloPrescricaoRepository.filtarPorTituloModelo(nome);
		if (modeloPrescricaoOp.isEmpty()) {
			System.out.println("Esse Modelo de evolução não foi encontrado...");
		}
		return modeloPrescricaoOp;
	}

	// Salvar
	@Transactional
	public ModeloPrescricao salvarModeloPrescricao(ModeloPrescricaoDto dto) throws ParseException {
		ModeloPrescricao modeloPrescricao = new ModeloPrescricao();
		modeloPrescricao.setModelo(dto.getModelo());
		modeloPrescricao.setTitulo(dto.getTitulo());
		modeloPrescricao.setCriador(dto.getCriador());
		modeloPrescricao.setSigla(dto.getSigla());
		modeloPrescricao.setUnidade(dto.getUnidade());		
		modeloPrescricao.persist();
		return modeloPrescricao;

	}

	@Transactional
	public void removeModeloPrescricao(int id) {
	    Optional<ModeloPrescricao> modeloPrescricaoOP = ModeloPrescricao.findByIdOptional(id);
	    if (modeloPrescricaoOP.isEmpty()) {
	        System.out.println("Modelo de evolução não foi encontrado...");
	    }

	    ModeloPrescricao modeloDePrescricao = modeloPrescricaoOP.get();
	    modeloDePrescricao.delete();
	}

}

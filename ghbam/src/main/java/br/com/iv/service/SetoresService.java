package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkus.panache.common.Sort;
import br.com.iv.dto.SetoresDto;
import br.com.iv.dto.SetoresUnidadesDto;
import br.com.iv.entity.Setores;
import br.com.iv.repository.SetoresRepository;

@ApplicationScoped
public class SetoresService implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@Inject
	SetoresRepository setoresRepository;

	// Listando todos os setores
	public List<Setores> listarTodosOsSetores() {
		return setoresRepository.listAll(Sort.by("setor"));
	}
	
	public List<SetoresUnidadesDto> listarTodosOsSetoresUnidades() {
		return setoresRepository.listAllUnidades();
	}

	// Filtar por setor e unidade
	@Transactional
	public List<Setores> filtarPorSetorEUnidade(String setor, String unidade, SetoresDto dto) {

	//	Setores setores = new Setores();
		List<Setores> setoresOp = setoresRepository.filtarPorSetorEUnidade(setor, unidade);
		if (setoresOp.isEmpty()) {
			System.out.println("Setor não encontrado...");
		}
		return setoresOp;
	}


	// Filtar por unidade o setor
	@Transactional
	public List<Setores> filtarPorUnidadeDoSetor(String unidade, SetoresDto dto) {

	//	Setores setores = new Setores();
		List<Setores> setoresOp = setoresRepository.filtarPorUnidadeDoSetor(unidade);
		if (setoresOp.isEmpty()) {
			System.out.println("Não há setores cadastrados nessa unidade...");
		}
		return setoresOp;
	}

	@Transactional
	public List<Setores> filtarPorUnidadeDoSetorTriar(String unidade, SetoresDto dto) {

	//	Setores setores = new Setores();
		List<Setores> setoresOp = setoresRepository.filtarPorUnidadeDoSetorTriar(unidade);
		if (setoresOp.isEmpty()) {
			System.out.println("Não há setores cadastrados nessa unidade...");
		}
		return setoresOp;
	}
	// Filtar por unidade o setor
	@Transactional
	public List<Setores> filtarUnidadesDistintasDoSetor(SetoresDto dto) {

	//	Setores setores = new Setores();
		List<Setores> setoresOp = setoresRepository.listarUnidadesDistintosDoSetor();
		if (setoresOp.isEmpty()) {
			System.out.println("Não há unidades cadastrados...");
		}
		return setoresOp;
	}
	
	@Transactional
	// Salvar os setores
	public Setores salvarSetores(SetoresDto dto) {
		Setores setores = new Setores();
		setores.setSetor(dto.getSetor());
		setores.setSiglaunidade(dto.getSiglaunidade());
		setores.setTriar(dto.isTriar());
		setores.persist();
		return setores;
	}
	
	// Alterar
	@Transactional
	public void atualizarSetor(int id, SetoresDto dto) {
	    Optional<Setores> setoresOp = Setores.findByIdOptional(id);
	    Setores setores = setoresOp.orElseThrow(() -> new NullPointerException("Setor não foi encontrado..."));

	    setores.setSetor(dto.getSetor());
	    setores.setSiglaunidade(dto.getSiglaunidade());
	    setores.setTriar(dto.isTriar());
	    setores.persist();
	}
	
	// Alterar sigla
	@Transactional
	public void atualizarSetorSigla(String siglaAntiga, String siglaNova, SetoresDto dto) {
		setoresRepository.alterarSiglasDasUnidadesNosSetores(siglaAntiga, siglaNova);
	  

	}
	
	
	@Transactional
	//deletar
	public void removeSetor(int id) {
		
		Optional<Setores> setoresOp = Setores.findByIdOptional(id);
		if (setoresOp.isEmpty()) {
			System.out.println("Setor não foi encontrado...");
		}

		Setores setores = setoresOp.get();
		setores.delete();

	}

}

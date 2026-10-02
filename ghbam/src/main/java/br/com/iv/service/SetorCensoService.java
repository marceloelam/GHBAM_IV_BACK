package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.SetorCensoDto;
import br.com.iv.entity.SetorCenso;
import br.com.iv.repository.SetorCensoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SetorCensoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	SetorCensoRepository setorCensoRepository;

	// Listando todos os setores
	public List<SetorCenso> listarTodosSetores() {
		return setorCensoRepository.listAll(Sort.by("setor"));
	}

	@Transactional
	public List<SetorCenso> filtarPorSetorCenso(String nome, String unidade) {

		List<SetorCenso> setorCensoOp = setorCensoRepository.filtarPorSetor(nome, unidade);
		if (setorCensoOp.isEmpty()) {
			System.out.println("Esse setor  não foi encontrado...");
		}
		return setorCensoOp;
	}

	@Transactional
	public List<SetorCenso> filtarPorSetorCensoCodigo(String codigo, String unidade) {

		List<SetorCenso> setorCensoOp = setorCensoRepository.filtarPorCodigo(codigo, unidade);
		if (setorCensoOp.isEmpty()) {
			System.out.println("Esse Setor não foi encontrado...");
		}
		return setorCensoOp;
	}

	@Transactional
	public List<SetorCenso> filtarPorSigladaUnidade(String sigla, String opc, SetorCensoDto dto) {

		// ConfiguracaoUnidade configuracaoUnidade = new ConfiguracaoUnidade();
		List<SetorCenso> setorcensoOp = setorCensoRepository.filtarPorUnidadeSigla(sigla, opc);
		if (setorcensoOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return setorcensoOp;
	}
	
	@Transactional
	public List<SetorCenso> filtarPorSigladaUnidade2(String sigla) {

		// ConfiguracaoUnidade configuracaoUnidade = new ConfiguracaoUnidade();
		List<SetorCenso> setorcensoOp = setorCensoRepository.filtarPorUnidadeSigla2(sigla);
		if (setorcensoOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return setorcensoOp;
	}
	
	
	
	@Transactional
	public List<SetorCenso> filtarPorSigladaUnidadeObs(String sigla, String opc, SetorCensoDto dto) {

		// ConfiguracaoUnidade configuracaoUnidade = new ConfiguracaoUnidade();
		List<SetorCenso> setorcensoOp = setorCensoRepository.filtarPorUnidadeSiglaObs(sigla, opc);
		if (setorcensoOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return setorcensoOp;
	}

	public List<String> filtrarPorSiglaUnidadeHospitalar(String unidade) {
		List<String> setorcensoUnidadeOp = setorCensoRepository.listarUnidadesDistintosDoSetor(unidade);
		if (setorcensoUnidadeOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return setorcensoUnidadeOp;
	}
	
	public List<String> filtrarPorSiglaSetorHospitalar(String unidade) {
		List<String> setorcensoUnidadeOp = setorCensoRepository.listarSetoresDistintosDoSetor(unidade);
		if (setorcensoUnidadeOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return setorcensoUnidadeOp;
	}
	

	public long contagem( String unidade) {
        return setorCensoRepository.contagem( unidade);
    }
	
	
	@Transactional
	public void alterarSeto(int id, boolean excluido) {
		setorCensoRepository.alterarExclusao(id, excluido);
	}

	@Transactional
	// Salvar os setores
	public SetorCenso salvarSetorCenso(SetorCensoDto dto) {
		SetorCenso setorCenso = new SetorCenso();

		setorCenso.setCodigo(dto.getCodigo());
		setorCenso.setSetor(dto.getSetor());
		setorCenso.setUnidade(dto.getUnidade());
		setorCenso.setExcluido(dto.isExcluido());
		setorCenso.persist();
		return setorCenso;
	}

	// Alterar
	@Transactional
	public void atualizarSetorCenso(int id, SetorCensoDto dto) {
		Optional<SetorCenso> setorCensoOp = SetorCenso.findByIdOptional(id);
		SetorCenso setorCenso = setorCensoOp
				.orElseThrow(() -> new NullPointerException("SetorCenso não foi encontrado..."));

		setorCenso.setCodigo(dto.getCodigo());
		setorCenso.setSetor(dto.getSetor());
		setorCenso.setUnidade(dto.getUnidade());
		setorCenso.setExcluido(dto.isExcluido());
		setorCenso.persist();
	}

}

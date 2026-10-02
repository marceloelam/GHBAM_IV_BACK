package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.LocalCensoDto;
import br.com.iv.entity.LocalCenso;
import br.com.iv.repository.LocalCensoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class LocalCensoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	LocalCensoRepository localCensoRepository;

	// Listando todos os setores
	public List<LocalCenso> listarTodosSetores() {
		return localCensoRepository.listAll(Sort.by("local"));
	}

	@Transactional
	public List<LocalCenso> filtarPorLocalCenso(String nome, String unidade) {

		List<LocalCenso> localCensoOp = localCensoRepository.filtarPorSetor(nome, unidade);
		if (localCensoOp.isEmpty()) {
			System.out.println("Esse local censo  não foi encontrado...");
		}
		return localCensoOp;
	}

	@Transactional
	public List<LocalCenso> filtarPorLocalCensoCodigo(String codigo, String unidade) {

		List<LocalCenso> localCensoOp = localCensoRepository.filtarPorCodigo(codigo, unidade);
		if (localCensoOp.isEmpty()) {
			System.out.println("Esse local censo não foi encontrado...");
		}
		return localCensoOp;
	}

	@Transactional
	public List<LocalCenso> filtarPorSigladaUnidade(String sigla, LocalCensoDto dto) {

		// ConfiguracaoUnidade configuracaoUnidade = new ConfiguracaoUnidade();
		List<LocalCenso> localCensoOp = localCensoRepository.filtarPorUnidadeSigla(sigla);
		if (localCensoOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return localCensoOp;
	}

	public List<String> filtrarPorSiglaUnidadeHospitalar(String unidade) {
		List<String> localCensoUnidadeOp = localCensoRepository.listarUnidadesDistintosDoSetor(unidade);
		if (localCensoUnidadeOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return localCensoUnidadeOp;
	}
	
	public List<LocalCensoDto> filtrarPorSiglaUnidadeHospitalarLocalUnidade(String unidade) {
		List<LocalCensoDto> localCensoUnidadeOp = localCensoRepository.listarUnidadesDistintosDoLocalPorUnidade(unidade);
		if (localCensoUnidadeOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return localCensoUnidadeOp;
	}
		public List<LocalCensoDto> filtrarPorSiglaUnidadeHospitalarLocalSetorCenso(String unidade, int idDoSetor) {
		List<LocalCensoDto> localCensoUnidadeOp = localCensoRepository.listarUnidadesDistintosDoLocalPorSetor(unidade, idDoSetor);
		if (localCensoUnidadeOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return localCensoUnidadeOp;
	}
	
	public List<LocalCensoDto> filtrarPorSiglaUnidadeHospitalarLocalSetorCensoObservacao(String unidade, int idDoSetor) {
		List<LocalCensoDto> localCensoUnidadeOp = localCensoRepository.listarUnidadesDistintosDoLocalPorSetorObservacao(unidade, idDoSetor);
		if (localCensoUnidadeOp.isEmpty()) {
			System.out.println("Unidade hospitalar não foi encontrada...");
		}
		return localCensoUnidadeOp;
	}

	public long contagem( String unidade) {
        return localCensoRepository.contagem( unidade);
    }
	
	
	@Transactional
	public void alterarSeto(int id, boolean excluido) {
		localCensoRepository.alterarExclusao(id, excluido);
	}

	@Transactional
	// Salvar os setores
	public LocalCenso salvarLocalCenso(LocalCensoDto dto) {
		LocalCenso localCenso = new LocalCenso();

		localCenso.setCodigo(dto.getCodigo());
		localCenso.setLocal(dto.getLocal());
		localCenso.setUnidade(dto.getUnidade());
		localCenso.setExcluido(dto.isExcluido());
		localCenso.setDisponivel(dto.isDisponivel());
		localCenso.setSetorCenso(dto.getSetorCenso());
		localCenso.persist();
		return localCenso;
	}

	// Alterar
	@Transactional
	public void atualizarLocalCenso(int id, LocalCensoDto dto) {
		Optional<LocalCenso> localCensoOp = LocalCenso.findByIdOptional(id);
		LocalCenso localCenso = localCensoOp
				.orElseThrow(() -> new NullPointerException("LocalCenso não foi encontrado..."));

		localCenso.setCodigo(dto.getCodigo());
		localCenso.setLocal(dto.getLocal());
		localCenso.setUnidade(dto.getUnidade());
		localCenso.setExcluido(dto.isExcluido());
		localCenso.setDisponivel(dto.isDisponivel());
		localCenso.setSetorCenso(dto.getSetorCenso());
		localCenso.persist();
	}

}

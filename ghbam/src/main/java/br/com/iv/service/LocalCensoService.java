package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import br.com.iv.dto.LocalCensoDto;
import br.com.iv.dto.LogDto;
import br.com.iv.entity.LocalCenso;
import br.com.iv.repository.LocalCensoRepository;
import br.com.iv.repository.NovoInternadoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class LocalCensoService implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(LocalCensoService.class);

	@Inject
	LocalCensoRepository localCensoRepository;

	@Inject
	NovoInternadoRepository novoInternadoRepository;

	@Inject
	LogService logService;

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
		alterarSeto(id, excluido, "SISTEMA");
	}

	@Transactional
	public void alterarSeto(int id, boolean excluido, String quem) {
		localCensoRepository.alterarExclusao(id, excluido);
		if (excluido) {
			darAltaNoLeito(id, "LEITO EXCLUIDO", quem);
		}
	}

	public long contarInternadosAtivos(int idleito) {
		return novoInternadoRepository.contarAtivosPorLeito(idleito);
	}

	// Leito bloqueado/excluído não pode manter paciente internado: todos recebem alta.
	private void darAltaNoLeito(int idleito, String motivo, String quem) {
		List<Integer> bams = novoInternadoRepository.darAltaPorLeito(idleito, LocalDate.now(), quem, motivo);
		if (bams.isEmpty()) {
			return;
		}
		LocalCenso leito = LocalCenso.<LocalCenso>findByIdOptional(idleito).orElse(null);
		String nomeLeito = leito != null && leito.getLocal() != null ? leito.getLocal() : String.valueOf(idleito);
		String unidade = leito != null && leito.getUnidade() != null && !leito.getUnidade().isBlank() ? leito.getUnidade() : "GERAL";

		LogDto log = new LogDto();
		log.setData(LocalDate.now());
		log.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
		log.setEvento("Alta automática por " + motivo + " (leito " + nomeLeito + ") dos BAMs " + bams
				+ " - por " + quem);
		log.setUnidade(unidade);
		logService.salvarLogAsync(log);
		LOG.infof("Leito %d (%s): %d internação(ões) receberam alta automática por %s", idleito, motivo, bams.size(), quem);
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
		atualizarLocalCenso(id, dto, "SISTEMA");
	}

	@Transactional
	public void atualizarLocalCenso(int id, LocalCensoDto dto, String quem) {
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

		if (dto.isExcluido()) {
			darAltaNoLeito(id, "LEITO EXCLUIDO", quem);
		} else if (!dto.isDisponivel()) {
			darAltaNoLeito(id, "LEITO BLOQUEADO", quem);
		}
	}

}

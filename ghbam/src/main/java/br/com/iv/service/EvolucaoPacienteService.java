package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.EvolucaoPacienteDto;
import br.com.iv.entity.EvolucaoPaciente;
import br.com.iv.repository.EvolucaoPacienteRepository;

@ApplicationScoped
public class EvolucaoPacienteService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	EvolucaoPacienteRepository evolucaoPacienteRepository;

	@Inject
	AssinaturaClinicaService assinaturaClinicaService;
	
	@Transactional
	public EvolucaoPaciente filtarPorId(int id) {
		return evolucaoPacienteRepository.findById(id); // filtarPorId(id);
	}

	@Transactional
	public List<EvolucaoPaciente> filtarPorBam(int bam, String inicio, String fim) {
		return evolucaoPacienteRepository.filtarPoBam(bam, inicio, fim);
	}
	@Transactional
	public List<EvolucaoPaciente> filtarPorBam2(int bam) {
		return evolucaoPacienteRepository.filtarPoBam2(bam);
	}
	
	@Transactional
	public List<EvolucaoPaciente> filtarUltimaEvolucao(int bam) {
		return evolucaoPacienteRepository.filtarUltimaEvolucao(bam);
	}
	
	@Transactional
	public EvolucaoPaciente filtarUltimaPorBam(int bam) {
		return evolucaoPacienteRepository.filtarUltimaPoBam(bam);
	}

	// Salvar
	@Transactional
	public EvolucaoPaciente salvarEvolucao(EvolucaoPacienteDto dto) {
	    EvolucaoPaciente evolucaoPaciente = new EvolucaoPaciente();
	    evolucaoPaciente.setAlta(dto.isAlta());
	    evolucaoPaciente.setInativo(dto.isInativo());
	    evolucaoPaciente.setCrm(dto.getCrm());
	    evolucaoPaciente.setData(dto.getData());
	    evolucaoPaciente.setUnidade(dto.getUnidade());
	    evolucaoPaciente.setEnfermaria(dto.getEnfermaria());
	    evolucaoPaciente.setHoraev(dto.getHoraev());
	    evolucaoPaciente.setLeito(dto.getLeito());
	    evolucaoPaciente.setLocal(dto.getLocal());
	    evolucaoPaciente.setMedico(dto.getMedico());
	    evolucaoPaciente.setNbam(dto.getNbam());
	    evolucaoPaciente.setPedido(dto.getPedido());
	    evolucaoPaciente.setPedidodomed(dto.getPedidodomed());
	    evolucaoPaciente.setPedidolimpo(dto.getPedidolimpo());
	    evolucaoPaciente.setProntuario(dto.getProntuario());
	    evolucaoPaciente.setQtde(dto.getQtde());
	    evolucaoPaciente.setRelato(dto.getRelato());
	    
	    // Ajuste para garantir a data correta
	    LocalDate dataAtual = LocalDate.now();
	    evolucaoPaciente.setData(dataAtual);

	    evolucaoPaciente.persist();
	    return evolucaoPaciente;
	}

	@Transactional
	public void inativar(int id) {
		if (assinaturaClinicaService.bloqueadoPorDocumento("EVOLUCAO_MEDICA", id)) {
			throw new IllegalStateException("Evolucao assinada nao pode ser inativada. Registre um adendo.");
		}
		evolucaoPacienteRepository.inativar(id);
	}

	
	
}

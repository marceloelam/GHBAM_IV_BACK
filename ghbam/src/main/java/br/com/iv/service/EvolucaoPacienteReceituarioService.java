package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.EvolucaoPacienteDto;
import br.com.iv.dto.EvolucaoPacienteReceituarioDto;
import br.com.iv.entity.EvolucaoPaciente;
import br.com.iv.entity.EvolucaoPacienteReceituario;
import br.com.iv.repository.EvolucaoPacienteReceituarioRepository;
import br.com.iv.repository.EvolucaoPacienteRepository;

@ApplicationScoped
public class EvolucaoPacienteReceituarioService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	EvolucaoPacienteReceituarioRepository evolucaoPacienteReceituarioRepository;

	@Transactional
	public EvolucaoPacienteReceituario filtarPorId(int id) {
		return evolucaoPacienteReceituarioRepository.findById(id); // filtarPorId(id);
	}

	@Transactional
	public List<EvolucaoPacienteReceituario> filtarPorBam(int bam) {
		return evolucaoPacienteReceituarioRepository.filtarPoBam(bam);
	}

	@Transactional
	public EvolucaoPacienteReceituario filtarUltimaPorBam(int bam) {
		return evolucaoPacienteReceituarioRepository.filtarUltimaPoBam(bam);
	}

	// Salvar
	@Transactional
	public EvolucaoPacienteReceituario salvarEvolucao(EvolucaoPacienteReceituarioDto dto) {
		EvolucaoPacienteReceituario evolucaoPaciente = new EvolucaoPacienteReceituario();

		evolucaoPaciente.setData(dto.getData());
		evolucaoPaciente.setHoraev(dto.getHoraev());
		evolucaoPaciente.setNbam(dto.getNbam());
		evolucaoPaciente.setRelato(dto.getRelato());
		evolucaoPaciente.setMedicocrm(dto.getMedicocrm());
		evolucaoPaciente.setUnidade(dto.getUnidade());
		// Ajuste para garantir a data correta
		LocalDate dataAtual = LocalDate.now();
		evolucaoPaciente.setData(dataAtual);

		evolucaoPaciente.persist();
		return evolucaoPaciente;
	}

}

package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

import br.com.iv.dto.AtendMedicoDTO;
import br.com.iv.dto.AtendimentoMedicoDto;
import br.com.iv.entity.AtendimentoMedico;
import br.com.iv.repository.AtendimentoMedicoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;


@ApplicationScoped
public class AtendimentoMedicoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	AtendimentoMedicoRepository atendimentoMedicoRepository;
	
	 @Inject
	    EntityManager entityManager;

	public List<AtendimentoMedico> listarTodosOsAtendimentosMedicos() {
		return atendimentoMedicoRepository.listAll();
	}

	public List<AtendimentoMedico> listarPorID(int id) {
		return atendimentoMedicoRepository.filtrarPorId(id);
	}
	
	
	public List<AtendMedicoDTO> medicoAtenderHoje(String atendimentoHoje, String unidade, String atendidoPor) {	
		List<AtendMedicoDTO> atendimentoOP = atendimentoMedicoRepository.medicoAtenderHoje(atendimentoHoje, unidade, atendidoPor);
		if (atendimentoOP.isEmpty()) {
			System.out.println("Não há pacientes para atendimento...");
		}
		return atendimentoOP;
	}
	
	public List<AtendMedicoDTO> medicoAtenderHojeAgenda(String atendimentoHoje, String unidade, String atendidoPor) {	
		List<AtendMedicoDTO> atendimentoOP = atendimentoMedicoRepository.medicoAtenderHojeAgenda(atendimentoHoje, unidade, atendidoPor);
		if (atendimentoOP.isEmpty()) {
			System.out.println("Não há pacientes para atendimento...");
		}
		return atendimentoOP;
	}

	public List<AtendMedicoDTO> medicoAtenderHojeEspera(String atendimentoHoje, String unidade, String setorPadrao) {	
		List<AtendMedicoDTO> atendimentoOP = atendimentoMedicoRepository.medicoAtenderEmEspera(atendimentoHoje, unidade, setorPadrao);
		if (atendimentoOP.isEmpty()) {
			System.out.println("Não há pacientes em espera...");
		}
		return atendimentoOP;
	}

	public List<AtendMedicoDTO> medicoAtenderHojeEsperaAgenda(String atendimentoHoje, String unidade, String setorPadrao) {	
		List<AtendMedicoDTO> atendimentoOP = atendimentoMedicoRepository.medicoAtenderEmEsperaAgenda(atendimentoHoje, unidade, setorPadrao);
		if (atendimentoOP.isEmpty()) {
			System.out.println("Não há pacientes em espera...");
		}
		return atendimentoOP;
	}

	// Salvar os atendimentos médicos
	@Transactional
	public AtendimentoMedico salvarAtendimentoMedico(AtendimentoMedicoDto dto) {
		AtendimentoMedico atendimentoMedico = new AtendimentoMedico();

		atendimentoMedico.setReceita(dto.getReceita());
		atendimentoMedico.setRelato(dto.getRelato());
		atendimentoMedico.setData(dto.getData());
		atendimentoMedico.setHora(dto.getHora());
		atendimentoMedico.setNomedomedico(dto.getNomedomedico());
		atendimentoMedico.setBam(dto.getBam());
		atendimentoMedico.setIdpaciente(dto.getIdpaciente());
		
		entityManager.persist(atendimentoMedico);
        entityManager.flush();
        return atendimentoMedico;
		
	}
	
	
	
	



}

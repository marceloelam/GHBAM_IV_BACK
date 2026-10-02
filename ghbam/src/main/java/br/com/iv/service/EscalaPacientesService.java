package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

import br.com.iv.dto.EscalaPacienteDto;
import br.com.iv.dto.SalvarEscalaPaciente2Dto;
import br.com.iv.entity.EscalaPaciente;
import br.com.iv.dto.EscalaResumoDTO;
import br.com.iv.repository.EscalaPacientesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class EscalaPacientesService implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    EscalaPacientesRepository escalaPacientesRepository;

    
    @Transactional
    public EscalaPaciente escalaPacientePorId(int idPaciente) {
        EscalaPaciente escala = escalaPacientesRepository.filtrarPorUnicoPacienteId(idPaciente);
        // Forçar inicialização do relacionamento lazy enquanto a Session/Tx está aberta
        if (escala != null && escala.getPaciente() != null) {
            // Acessar um campo do paciente para que o proxy seja inicializado
            escala.getPaciente().getNome();
            escala.getPaciente().getIdpaciente();
        }
        return escala;
    }

    
    @Transactional
    public void escalaPacientePorId2(int idag, String unidade, boolean presenca) {
        escalaPacientesRepository.filtrarPorUnicoPacienteId2(idag, unidade, presenca);
       
    }
    
    
    
    @Transactional
    public EscalaPaciente salvarEscalaPaciente(EscalaPacienteDto dto) {
        // Validação básica
        if (dto.getPaciente() == null) {
            throw new IllegalArgumentException("Paciente não pode ser nulo");
        }

        EscalaPaciente novoPaciente = new EscalaPaciente();
        novoPaciente.setPaciente(dto.getPaciente());
        novoPaciente.setAgbam(dto.getAgbam());
        novoPaciente.setAtendido(false);
        novoPaciente.setCadastrou("-");
        novoPaciente.setMedico("-");
        novoPaciente.setSetor("-");
        novoPaciente.setTitulo("");
        novoPaciente.setAgenda(false);
        novoPaciente.setChegou(false);
        novoPaciente.setComentario("");
        novoPaciente.setHoramarcada("08:00");
        novoPaciente.setIdDoMedico(0);
        novoPaciente.setObs("");
        novoPaciente.setObs2("");
        novoPaciente.setStatusevento(false);
        novoPaciente.setModalidade(dto.getModalidade() != null ? dto.getModalidade() : "PRESENCIAL");

        novoPaciente.persist();
        
        return novoPaciente;
        
    }

    @Transactional
    public EscalaPaciente salvarEscalaPaciente2(SalvarEscalaPaciente2Dto dto) {
        // Validação básica
        if (dto.getPaciente() == null) {
            throw new IllegalArgumentException("Paciente não pode ser nulo");
        }

        EscalaPaciente novoPaciente = new EscalaPaciente();
        novoPaciente.setTitulo(dto.getTitulo());
        novoPaciente.setInicioagenda(dto.getInicioagenda());
        novoPaciente.setFimagenda(dto.getFimagenda());
        novoPaciente.setDt_nasc(dto.getDt_nasc());
        novoPaciente.setHoramarcada(dto.getHoramarcada());
        novoPaciente.setHorasaida(dto.getHorasaida());
        novoPaciente.setSetor(dto.getSetor());
        novoPaciente.setPaciente(dto.getPaciente());
        novoPaciente.setMedico(dto.getMedico());
        novoPaciente.setCadastrou(dto.getCadastrou());
        novoPaciente.setUnidade(dto.getUnidade());
        novoPaciente.setObs(dto.getObs());
        novoPaciente.setObs2(dto.getObs2());
        novoPaciente.setStatusevento(dto.isStatusevento());
        novoPaciente.setChegou(dto.isChegou());
        novoPaciente.setAgbam(dto.getAgbam());
        novoPaciente.setAtendido(dto.isAtendido());
        novoPaciente.setAgenda(true);
        novoPaciente.setIdDoMedico(dto.getIdDoMedico());
        novoPaciente.setComentario(dto.getComentario());
        novoPaciente.setModalidade(dto.getModalidade() != null ? dto.getModalidade() : "PRESENCIAL");

        // persistir
        novoPaciente.persist();

        return novoPaciente;
    }



	public List<EscalaPaciente> xlocalizaEspecialidade(String especialidade, String unidade) {
		new EscalaPaciente();
		List<EscalaPaciente> escalaPacienteOp = escalaPacientesRepository.filtarPorEspecialidade(especialidade, unidade);
		if (escalaPacienteOp.isEmpty()) {
			System.out.println("Especialidade não foi encontrado...");
		}

		return escalaPacienteOp;
	}
	
    public List<EscalaResumoDTO> xlocalizaEspecialidadeHora(String especialidade, String unidade, String data, String medico) {
        new EscalaPaciente();
        List<EscalaResumoDTO> escalaPacienteOp = escalaPacientesRepository.filtarPorEspecialidadeHora(especialidade, unidade, data, medico);
        if (escalaPacienteOp.isEmpty()) {
            System.out.println("Especialidade não foi encontrado...");
        }

        return escalaPacienteOp;
    }

	public List<EscalaPaciente> xlocalizaDataUnidade(String data, String unidade) {
		new EscalaPaciente();
		List<EscalaPaciente> escalaPacienteOp = escalaPacientesRepository.filtarPorDataUnidade(data, unidade);
		if (escalaPacienteOp.isEmpty()) {
			System.out.println("Especialidade não foi encontrado...");
		}

		return escalaPacienteOp;
	}

@Transactional
	public void deletarAgendamento(int idp) {
        // Tentar deletar diretamente pelo repositório dentro da transação
        boolean deleted = escalaPacientesRepository.deleteById((long) idp);
        if (!deleted) {
            System.out.println("Agendamento do paciente não foi encontrado...");
        }
		
	}


public List<EscalaPaciente> xlocalizacpf(String cpf) {
	List<EscalaPaciente> escalaPacienteOp = escalaPacientesRepository.filtarPorCpf(cpf);
	if (escalaPacienteOp.isEmpty()) {
		System.out.println("Agendamento não foi encontrado...");
	}

	return escalaPacienteOp;
}

@Transactional
public void cancelaragendamentonodestino(int idbam) {
	escalaPacientesRepository.cancelaragendamentonodestino(idbam);
}

@Transactional
public boolean checkinByTempcod(String tempcod) {
	return escalaPacientesRepository.checkinByTempcod(tempcod);
}
	
	
}

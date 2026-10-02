package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import br.com.iv.dto.EscalaDto;
import br.com.iv.entity.Escala;
import br.com.iv.repository.EscalaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;

@ApplicationScoped
public class EscalaService implements Serializable {
	private static final long serialVersionUID = 1L;
	String destination;
	String nomeArquivo;

	@Context
	UriInfo uriInfo;

	@Inject
	EscalaRepository escalaRepository;


	public List<Escala> escalamedica(int mes, int ano, String yunidade) {
		// ConfiguracaoUnidade configuracaoUnidade = new ConfiguracaoUnidade();
				List<Escala> escalaOp = escalaRepository.filtarEscalaMesAnoUnidade(mes, ano, yunidade);
				if (escalaOp.isEmpty()) {
					System.out.println("Não encontrei esse agendamento...");
				}
				return escalaOp;
	}

	public List<Escala> escalamedicoindividual(int mes, int ano, String nomemedico, String yunidade) {
		List<Escala> escalaOp = escalaRepository.filtarEscalaMesAnoMedicoUnidade(mes, ano, nomemedico, yunidade);
		if (escalaOp.isEmpty()) {
			System.out.println("Não encontrei esse agendamento...");
		}
		return escalaOp;
	}
	
	public List<Escala> escalaEspecialidade(String especialidade, String yunidade) {
		List<Escala> escalaOp = escalaRepository.filtarEscalaEspecialidade(especialidade, yunidade);
		if (escalaOp.isEmpty()) {
			System.out.println("Não encontrei esse agendamento...");
		}
		return escalaOp;
	}

	@Transactional
	public Escala salvarEscalaMedica(EscalaDto dto) {
		
		Escala escala = new Escala();
		escala.setTitulo(dto.getTitulo());
		// Normaliza datas recebidas para startOfDay no timezone do servidor
		if (dto.getInicioagenda() != null) {
			Instant inst = dto.getInicioagenda().toInstant();
			// Interpretar o instante como UTC para extrair a data calendar (ano/mês/dia)
			// Isso evita que diferenças de fuso façam a data "cair" no dia anterior
			LocalDate ld = inst.atZone(ZoneOffset.UTC).toLocalDate();
			escala.setInicioagenda(java.util.Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant()));
		} else {
			escala.setInicioagenda(null);
		}
		if (dto.getFimagenda() != null) {
			Instant inst2 = dto.getFimagenda().toInstant();
			// Interpretar como UTC por mesma razão descrita acima
			LocalDate ld2 = inst2.atZone(ZoneOffset.UTC).toLocalDate();
			escala.setFimagenda(java.util.Date.from(ld2.atStartOfDay(ZoneId.systemDefault()).toInstant()));
		} else {
			escala.setFimagenda(null);
		}
		
		escala.setHoraescala(dto.getHoraescala());
		escala.setHorasaida(dto.getHorasaida());
		escala.setObs(dto.getObs());
		escala.setObs2(dto.getObs2());
		escala.setSetor(dto.getSetor());
		escala.setStatusevento(dto.isStatusevento());
		escala.setTitulo(dto.getTitulo());
		escala.setUnidade(dto.getUnidade());
		escala.setCadastrou(dto.getCadastrou());
		escala.setServidor(dto.getServidor());

		escala.persist();
		return escala;
	}

	@Transactional
	public void atualizarEscala(int id, EscalaDto dto) {
		Optional<Escala> escalaOp = Escala.findByIdOptional(id);
		if (escalaOp.isEmpty()) {
			throw new EntityNotFoundException("Escala foi encontrada...");
		}

		Escala escala = escalaOp.get();
		escala.setTitulo(dto.getTitulo());
		// Normaliza datas recebidas para startOfDay no timezone do servidor
		if (dto.getInicioagenda() != null) {
			Instant inst = dto.getInicioagenda().toInstant();
			LocalDate ld = inst.atZone(ZoneOffset.UTC).toLocalDate();
			escala.setInicioagenda(java.util.Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant()));
		} else {
			escala.setInicioagenda(null);
		}
		if (dto.getFimagenda() != null) {
			Instant inst2 = dto.getFimagenda().toInstant();
			LocalDate ld2 = inst2.atZone(ZoneOffset.UTC).toLocalDate();
			escala.setFimagenda(java.util.Date.from(ld2.atStartOfDay(ZoneId.systemDefault()).toInstant()));
		} else {
			escala.setFimagenda(null);
		}
		escala.setHoraescala(dto.getHoraescala());
		escala.setHorasaida(dto.getHorasaida());
		escala.setObs(dto.getObs());
		escala.setObs2(dto.getObs2());
		escala.setSetor(dto.getSetor());
		escala.setStatusevento(dto.isStatusevento());
		escala.setTitulo(dto.getTitulo());
		escala.setUnidade(dto.getUnidade());
		escala.setCadastrou(dto.getCadastrou());
		escala.setServidor(dto.getServidor());


		// Persiste as alterações
		escala.persist();
		
	}

	@Transactional
	public void removeEscalar(Long id) {
		Optional<Escala> escalaOp = Escala.findByIdOptional(id);
		if (escalaOp.isEmpty()) {
			System.out.println("Escala não foi encontrada...");
		}

		Escala escala = escalaOp.get();
		escala.delete();

		
	}

	@Transactional
	public void removeEscalarDiante(Long id, String data) {
		 escalaRepository.removerDiante(id, data);
	}


}

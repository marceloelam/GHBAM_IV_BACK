package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.VisitantesDto;
import br.com.iv.util.DateUtils;
import br.com.iv.entity.Visitantes;
import br.com.iv.repository.VisitantesRepository;

@ApplicationScoped
public class VisitantesService implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final int LIMITE_VISITANTES_POR_DIA = 4;
	private static final DateTimeFormatter FORMATO_DATA_VISITA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Inject
	VisitantesRepository visitantesRepository;

	public List<Visitantes> filtrarPelaUnidadeEDataVisita(String unidade, LocalDate data1, LocalDate data2) {
		List<Visitantes> visitantesOp = visitantesRepository.filtrarVisitantesPelaUnidadeEData(unidade, data1, data2);
		if (visitantesOp.isEmpty()) {
			System.out.println("Visitante(s) não encontrado(s)...");
		}
		return visitantesOp;
	}
	
	public List<Visitantes> filtrarPelobamVisita(String bam) {
		List<Visitantes> visitantesOp = visitantesRepository.filtarPorBam(bam);
		if (visitantesOp.isEmpty()) {
			System.out.println("Visitante(s) não encontrado(s)...");
		}
		return visitantesOp;
	}
	
	// Salvar
	@Transactional
	public Visitantes salvarLog(VisitantesDto dto) {
		if (dto == null || dto.getNbam() == null || dto.getNbam().isBlank()) {
			throw new IllegalArgumentException("O BAM do paciente é obrigatório.");
		}

		String dataVisita = LocalDate.now().format(FORMATO_DATA_VISITA);
		if (visitantesRepository.contarPorBamEData(dto.getNbam(), dataVisita) >= LIMITE_VISITANTES_POR_DIA) {
			throw new IllegalStateException(
					"Limite diário de 4 visitantes atingido para este paciente.");
		}

		Visitantes visitantes = new Visitantes();
		
		visitantes.setNomeVisitante(dto.getNomeVisitante());
		visitantes.setCpfVisitante(dto.getCpfVisitante());
		visitantes.setIdentidadeVisitante(dto.getIdentidadeVisitante());
		visitantes.setDataVisita(dataVisita);
		visitantes.setHoraVisitante(dto.getHoraVisitante());
		visitantes.setNascimentoVisitante(DateUtils.toYMD(dto.getNascimentoVisitante()));
		visitantes.setNbam(dto.getNbam());
		visitantes.setObsvisitante(dto.getObsvisitante());
		visitantes.setEndereco(dto.getEndereco());
		visitantes.setTelefone(dto.getTelefone());
		visitantes.setFotovisitante(dto.getFotovisitante());
		visitantes.setFotovisitantearquivo(dto.getFotovisitantearquivo());
		visitantes.setNunidade(dto.getNunidade());

		
		visitantes.persist();
		return visitantes;
	}

}

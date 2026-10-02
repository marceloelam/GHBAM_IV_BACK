package br.com.iv.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.EscalaDeEnfermagemDto;
import br.com.iv.entity.EscalaDeEnfermagem;
import br.com.iv.repository.EscalaDeEnfermagemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Column;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class EscalaDeEnfermagemService {
	@Inject
	EscalaDeEnfermagemRepository escalaDeEnfermagemRepository;;

	public List<EscalaDeEnfermagem> filtarescalaenfermagemPorBameData(int bam, String dt, String unidade) {
		List<EscalaDeEnfermagem> escalaenfermagemOp = escalaDeEnfermagemRepository.filtarPorBamEData(bam, dt, unidade);
		if (escalaenfermagemOp.isEmpty()) {
			System.out.println("Não tem escala de enfermagem ...");
		}
		return escalaenfermagemOp;
	}
	
	
	// Salvar escalaenfermagem
	@Transactional
	public EscalaDeEnfermagem salvarescalaenfermagem(EscalaDeEnfermagemDto dto) {
		EscalaDeEnfermagem escalaenfermagem = new EscalaDeEnfermagem();

		  // Campos principais
	    escalaenfermagem.setBam(dto.getBam());
	    escalaenfermagem.setPaciente(dto.getPaciente());
	    escalaenfermagem.setEnfermaria(dto.getEnfermaria());
	    escalaenfermagem.setLeito(dto.getLeito());
	    escalaenfermagem.setData(dto.getData());
	    escalaenfermagem.setServidor(dto.getServidor());
		
		
	    // Novos campos Fugulin
	    escalaenfermagem.setHigiene(dto.getHigiene());
	    escalaenfermagem.setAlimentacao(dto.getAlimentacao());
	    escalaenfermagem.setEliminacao(dto.getEliminacao());
	    escalaenfermagem.setVentilacao(dto.getVentilacao());
	    escalaenfermagem.setMobilidade(dto.getMobilidade());
	    escalaenfermagem.setAtividade(dto.getAtividade());
	    escalaenfermagem.setEstadoEmocional(dto.getEstadoEmocional());
	    escalaenfermagem.setComunicacao(dto.getComunicacao());
	    escalaenfermagem.setCuidados(dto.getCuidados());

	    // Morse
	    escalaenfermagem.setHistoriaQuedas(dto.getHistoriaQuedas());
	    escalaenfermagem.setDiagnosticoSecundario(dto.getDiagnosticoSecundario());
	    escalaenfermagem.setAuxilioDeambulacao(dto.getAuxilioDeambulacao());
	    escalaenfermagem.setDispositivoIv(dto.getDispositivoIv());
	    escalaenfermagem.setMarchaTransferencia(dto.getMarchaTransferencia());
	    escalaenfermagem.setEstadoMental(dto.getEstadoMental());

	    // Braden
	    escalaenfermagem.setSensorial(dto.getSensorial());
	    escalaenfermagem.setUmidade(dto.getUmidade());
	    escalaenfermagem.setAtividadeBraden(dto.getAtividadeBraden());
	    escalaenfermagem.setMobilidadeBraden(dto.getMobilidadeBraden());
	    escalaenfermagem.setNutricional(dto.getNutricional());
	    escalaenfermagem.setFriccao(dto.getFriccao());

	    // Totais / resultados
	    escalaenfermagem.setFugulinTotal(dto.getFugulinTotal());
	    escalaenfermagem.setFugulinGrau(dto.getFugulinGrau());
	    escalaenfermagem.setMorseTotal(dto.getMorseTotal());
	    escalaenfermagem.setMorseRisco(dto.getMorseRisco());
	    escalaenfermagem.setBradenTotal(dto.getBradenTotal());
	    escalaenfermagem.setBradenRisco(dto.getBradenRisco());

	    // Timestamps
	    escalaenfermagem.setCreatedAt(OffsetDateTime.now());
	    escalaenfermagem.setUpdatedAt(OffsetDateTime.now());

	    escalaenfermagem.persist();
	    return escalaenfermagem;
	}

	// Alterar
	@Transactional
	public void atualizarescalaenfermagem(int id, EscalaDeEnfermagemDto dto) {
	    Optional<EscalaDeEnfermagem> escalaenfermagemOp = escalaDeEnfermagemRepository.findByIdOptional(id);
	    EscalaDeEnfermagem escalaenfermagem = escalaenfermagemOp.orElseThrow(() -> new NullPointerException("Escala de enfermagem não foi encontrado..."));

	    // Novos campos Fugulin
	    escalaenfermagem.setHigiene(dto.getHigiene());
	    escalaenfermagem.setAlimentacao(dto.getAlimentacao());
	    escalaenfermagem.setEliminacao(dto.getEliminacao());
	    escalaenfermagem.setVentilacao(dto.getVentilacao());
	    escalaenfermagem.setMobilidade(dto.getMobilidade());
	    escalaenfermagem.setAtividade(dto.getAtividade());
	    escalaenfermagem.setEstadoEmocional(dto.getEstadoEmocional());
	    escalaenfermagem.setComunicacao(dto.getComunicacao());
	    escalaenfermagem.setCuidados(dto.getCuidados());
	    escalaenfermagem.setServidor(dto.getServidor());

	    // Morse
	    escalaenfermagem.setHistoriaQuedas(dto.getHistoriaQuedas());
	    escalaenfermagem.setDiagnosticoSecundario(dto.getDiagnosticoSecundario());
	    escalaenfermagem.setAuxilioDeambulacao(dto.getAuxilioDeambulacao());
	    escalaenfermagem.setDispositivoIv(dto.getDispositivoIv());
	    escalaenfermagem.setMarchaTransferencia(dto.getMarchaTransferencia());
	    escalaenfermagem.setEstadoMental(dto.getEstadoMental());

	    // Braden
	    escalaenfermagem.setSensorial(dto.getSensorial());
	    escalaenfermagem.setUmidade(dto.getUmidade());
	    escalaenfermagem.setAtividadeBraden(dto.getAtividadeBraden());
	    escalaenfermagem.setMobilidadeBraden(dto.getMobilidadeBraden());
	    escalaenfermagem.setNutricional(dto.getNutricional());
	    escalaenfermagem.setFriccao(dto.getFriccao());

	    // Totais / resultados
	    escalaenfermagem.setFugulinTotal(dto.getFugulinTotal());
	    escalaenfermagem.setFugulinGrau(dto.getFugulinGrau());
	    escalaenfermagem.setMorseTotal(dto.getMorseTotal());
	    escalaenfermagem.setMorseRisco(dto.getMorseRisco());
	    escalaenfermagem.setBradenTotal(dto.getBradenTotal());
	    escalaenfermagem.setBradenRisco(dto.getBradenRisco());

	    // Timestamp de atualização
	    escalaenfermagem.setUpdatedAt(OffsetDateTime.now());

	    escalaenfermagem.persist();
	}

	
}

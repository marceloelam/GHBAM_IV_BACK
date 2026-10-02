package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.BalancoHibricoSaidaDto;
import br.com.iv.entity.BalancoHidricoSaida;
import br.com.iv.repository.BalancoHidricoSaidaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BalancoHidricoSaidaService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	BalancoHidricoSaidaRepository balancoHidricoSaidaRepository;

	@Transactional
	public List<BalancoHidricoSaida> filtarPorBamData(String bam, String data) {

		List<BalancoHidricoSaida> balancohidrigoSaida = balancoHidricoSaidaRepository.filtrarPorBamData(bam, data);
		if (balancohidrigoSaida.isEmpty()) {
			System.out.println("Esse balanço de Saida não foi encontrado...");
		}
		return balancohidrigoSaida;
	}

	@Transactional
	public BalancoHidricoSaida salvarBalancoHidricoSaida(BalancoHibricoSaidaDto dto) {
		BalancoHidricoSaida balancoHidricoSaida = new BalancoHidricoSaida();

		balancoHidricoSaida.setUuid(dto.getUuid());
		balancoHidricoSaida.setHora(dto.getHora());
		balancoHidricoSaida.setVolumeMl(dto.getVolumeMl());
		balancoHidricoSaida.setObservacao(dto.getObservacao());
		balancoHidricoSaida.setBam(dto.getBam());
		balancoHidricoSaida.setTipo(dto.getTipo().toUpperCase());
		balancoHidricoSaida.setProfissional(dto.getProfissional());
		balancoHidricoSaida.setTurno(dto.getTurno());
		balancoHidricoSaida.persist();
		return balancoHidricoSaida;
	}

	// Alterar
	@Transactional
	public BalancoHidricoSaida alterarBalancoHidricoSaida(String id, BalancoHibricoSaidaDto dto) {
		BalancoHidricoSaida balancoHidricoSaida = balancoHidricoSaidaRepository.findByUuid(id);
		
		if (balancoHidricoSaida == null) {
			throw new IllegalArgumentException("Registro não encontrado para ID: " + id);
		}

		// Atualiza os campos simples
		balancoHidricoSaida.setUuid(dto.getUuid());
		balancoHidricoSaida.setHora(dto.getHora());
		balancoHidricoSaida.setVolumeMl(dto.getVolumeMl());
		balancoHidricoSaida.setObservacao(dto.getObservacao());
		balancoHidricoSaida.setBam(dto.getBam());
		balancoHidricoSaida.setTipo(dto.getTipo().toUpperCase());
		balancoHidricoSaida.setProfissional(dto.getProfissional());
		balancoHidricoSaida.setTurno(dto.getTurno());
	
		balancoHidricoSaida.persist();
		return balancoHidricoSaida;
		
	}
	
	//Deletar
	@Transactional
	public void deletarBalancoHidricoSaida(String id) {
		Optional<BalancoHidricoSaida> balancoHidricoSaidaOP = BalancoHidricoSaida.findByIdOptional(id);
		if (balancoHidricoSaidaOP.isEmpty()) {
			System.out.println("Balanço hídrico de Saida não foi encontrado...");
		}
		BalancoHidricoSaida balancoHidrocoDeSaida = balancoHidricoSaidaOP.get();
		balancoHidrocoDeSaida.delete();

	}

}

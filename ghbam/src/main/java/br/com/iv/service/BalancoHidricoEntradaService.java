package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.BalancoHibricoEntradaDto;
import br.com.iv.entity.BalancoHidricoEntrada;
import br.com.iv.repository.BalancoHidricoEntradaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BalancoHidricoEntradaService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	BalancoHidricoEntradaRepository balancoHidricoEntradaRepository;

	@Transactional
	public List<BalancoHidricoEntrada> filtarPorBamData(String bam, String data) {

		List<BalancoHidricoEntrada> balancohidrigoentrada = balancoHidricoEntradaRepository.filtrarPorBamData(bam, data);
		if (balancohidrigoentrada.isEmpty()) {
			System.out.println("Esse balanço de entrada não foi encontrado...");
		}
		return balancohidrigoentrada;
	}

	@Transactional
	public BalancoHidricoEntrada salvarBalancoHidricoEntrada(BalancoHibricoEntradaDto dto) {
		BalancoHidricoEntrada balancoHidricoEntrada = new BalancoHidricoEntrada();

		balancoHidricoEntrada.setUuid(dto.getUuid());
		balancoHidricoEntrada.setHora(dto.getHora());
		balancoHidricoEntrada.setVolumeMl(dto.getVolumeMl());
		balancoHidricoEntrada.setObservacao(dto.getObservacao());
		balancoHidricoEntrada.setBam(dto.getBam());
		balancoHidricoEntrada.setTipo(dto.getTipo().toUpperCase());
		balancoHidricoEntrada.setSubtipo(dto.getSubtipo());
		balancoHidricoEntrada.setProfissional(dto.getProfissional());
		balancoHidricoEntrada.setTurno(dto.getTurno());
		balancoHidricoEntrada.persist();
		return balancoHidricoEntrada;
	}

	// Alterar
	@Transactional
	public BalancoHidricoEntrada alterarBalancoHidricoEntrada(String id, BalancoHibricoEntradaDto dto) {
		BalancoHidricoEntrada balancoHidricoEntrada = balancoHidricoEntradaRepository.findByUuid(id);
		//BalancoHidricoEntrada balancoHidricoEntrada = new BalancoHidricoEntrada();
		//Optional<BalancoHidricoEntrada> balancohidrientOp = balancoHidricoEntradaRepository.findByUuid(id);
		//if (balancohidrientOp.isEmpty()) {
		//	System.out.println("Balanço hídrico não foi encontrado...");
		//}


		if (balancoHidricoEntrada == null) {
			throw new IllegalArgumentException("Registro não encontrado para ID: " + id);
		}

		// Atualiza os campos simples
		balancoHidricoEntrada.setUuid(dto.getUuid());
		balancoHidricoEntrada.setHora(dto.getHora());
		balancoHidricoEntrada.setVolumeMl(dto.getVolumeMl());
		balancoHidricoEntrada.setObservacao(dto.getObservacao());
		balancoHidricoEntrada.setBam(dto.getBam());
		balancoHidricoEntrada.setTipo(dto.getTipo().toUpperCase());
		balancoHidricoEntrada.setSubtipo(dto.getSubtipo());
		balancoHidricoEntrada.setProfissional(dto.getProfissional());
		balancoHidricoEntrada.setTurno(dto.getTurno());
	
		balancoHidricoEntrada.persist();
		return balancoHidricoEntrada;
		
	}
	
	//Deletar
	@Transactional
	public void deletarBalancoHidricoEntrada(String id) {
		Optional<BalancoHidricoEntrada> balancoHidricoEntradaOP = BalancoHidricoEntrada.findByIdOptional(id);
		if (balancoHidricoEntradaOP.isEmpty()) {
			System.out.println("Balanço hídrico de entrada não foi encontrado...");
		}
		BalancoHidricoEntrada balancoHidrocoDeEntrada = balancoHidricoEntradaOP.get();
		balancoHidrocoDeEntrada.delete();

	}

}

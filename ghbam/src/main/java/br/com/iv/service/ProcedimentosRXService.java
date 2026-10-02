package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkus.panache.common.Sort;
import br.com.iv.dto.ProcedimentosRXDto;
import br.com.iv.entity.ProcedimentosRX;
import br.com.iv.repository.ProcedimentosRXRepository;

@ApplicationScoped
public class ProcedimentosRXService implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final List<String> MODOS_VALIDOS = List.of("-", "ASSISTENTE", "ELETIVO", "EXAME", "FISIO",
			"FONO", "LABORATORIO", "MEDICACAO", "MEDICACAO2", "MEDICACAO3", "NUTRICAO", "PROCEDIMENTO",
			"PSICOLOGIA", "RAIO X", "TOMOGRAFIA", "TRIAGEM", "ULTRASSONOGRAFIA");
	private static final List<String> MODOS2_VALIDOS = List.of("-", "ENFERMAGEM", "MEDICACAO", "RAIO X",
			"TOMOGRAFIA");

	@Inject
	ProcedimentosRXRepository ProcedimentosRXRepository;

	// Listando todos os ProcedimentosRX
	public List<ProcedimentosRX> listarTodosOsProcedimentosRX() {
		return ProcedimentosRXRepository.listAll(Sort.by("descricao"));
	}

	// Filtar por procedimentose

	public List<ProcedimentosRX> filtarPorProcedimentoRXModos(String descricao, String modo, String modo2) {
		List<ProcedimentosRX> procedimentosRXOp = ProcedimentosRXRepository.filtarPorProcedimentosModos(descricao, modo,
				modo2);
		if (procedimentosRXOp.isEmpty()) {
			System.out.println("Procedimento não encontrado...");
		}
		return procedimentosRXOp;
	}

	

	public ProcedimentosRX  ProcedimentoRXId(String procedimento, String descricao) {
		return ProcedimentosRXRepository.filtrarPorUnicoProcedimentoRX(procedimento, descricao);
	}
	

	public List<ProcedimentosRX> filtarPorDescricaoProcedimentos(String descricao, ProcedimentosRXDto dto) {

		// ProcedimentosRX ProcedimentosRX = new ProcedimentosRX();
		List<ProcedimentosRX> procedimentosRXOp = ProcedimentosRXRepository.filtarPorProcedimentos(descricao);
		if (procedimentosRXOp.isEmpty()) {
			System.out.println("Não há Procedimentos cadastrados nessa unidade...");
		}
		return procedimentosRXOp;
	}

	// Filtar por unidade o setor

	public List<ProcedimentosRX> filtarModoDoProcedimento(String modo) {

		List<ProcedimentosRX> procedimentosRXOp = ProcedimentosRXRepository.filtarPorModo(modo);
		if (procedimentosRXOp.isEmpty()) {
			System.out.println("Não há procedimento cadastrados...");
		}
		return procedimentosRXOp;
	}

	
	public List<ProcedimentosRX> filtarModo2DoProcedimento(String modo2) {

		List<ProcedimentosRX> procedimentosRXOp = ProcedimentosRXRepository.filtarPorModo2(modo2);
		if (procedimentosRXOp.isEmpty()) {
			System.out.println("Não há procedimento cadastrados...");
		}
		return procedimentosRXOp;
	}

	@Transactional
	// Salvar os ProcedimentosRX
	public ProcedimentosRX salvarProcedimentosRX(ProcedimentosRXDto dto) {
		validar(dto);
		ProcedimentosRX procedimentosRX = new ProcedimentosRX();
		procedimentosRX.setDescricao(dto.getDescricao().trim());
		procedimentosRX.setModo(valorOuPadrao(dto.getModo(), "-"));
		procedimentosRX.setModo2(valorOuPadrao(dto.getModo2(), "-"));
		procedimentosRX.setCid(valorOuPadrao(dto.getCid(), "0000"));
		procedimentosRX.setCodprocedimento(dto.getCodprocedimento().trim());
		procedimentosRX.persist();
		return procedimentosRX;
	}

	// Alterar
	@Transactional
	public void atualizarProcedimentoRX(int id, ProcedimentosRXDto dto) {
		validar(dto);
		Optional<ProcedimentosRX> procedimentosRXOp = ProcedimentosRX.findByIdOptional(id);
		ProcedimentosRX procedimentosRX = procedimentosRXOp
				.orElseThrow(() -> new NullPointerException("Procedimentos não foi encontrado..."));

		procedimentosRX.setDescricao(dto.getDescricao().trim());
		procedimentosRX.setModo(valorOuPadrao(dto.getModo(), "-"));
		procedimentosRX.setModo2(valorOuPadrao(dto.getModo2(), "-"));
		procedimentosRX.setCid(valorOuPadrao(dto.getCid(), "0000"));
		procedimentosRX.setCodprocedimento(dto.getCodprocedimento().trim());
		procedimentosRX.persist();
	}

	@Transactional
	public void apagarProcedimentoRX(int id) {
		boolean apagado = ProcedimentosRXRepository.deleteById(Long.valueOf(id));
		if (!apagado) {
			throw new NullPointerException("Procedimento RX nao foi encontrado...");
		}
	}

	void validar(ProcedimentosRXDto dto) {
		if (dto == null || vazio(dto.getCodprocedimento()) || vazio(dto.getDescricao())) {
			throw new IllegalArgumentException("Codigo do procedimento e descricao sao obrigatorios.");
		}

		String modo = valorOuPadrao(dto.getModo(), "-");
		String modo2 = valorOuPadrao(dto.getModo2(), "-");

		if (!MODOS_VALIDOS.contains(modo)) {
			throw new IllegalArgumentException("Modo invalido.");
		}

		if (!MODOS2_VALIDOS.contains(modo2)) {
			throw new IllegalArgumentException("Modo 2 invalido.");
		}
	}

	private String valorOuPadrao(String valor, String padrao) {
		return vazio(valor) ? padrao : valor.trim().toUpperCase();
	}

	private boolean vazio(String valor) {
		return valor == null || valor.trim().isEmpty();
	}

	

}

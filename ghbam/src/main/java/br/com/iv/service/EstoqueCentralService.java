package br.com.iv.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.EstoqueCentralDto;
import br.com.iv.dto.PacientesDto;
import br.com.iv.entity.Cid1;
import br.com.iv.entity.EstoqueCentral;
import br.com.iv.entity.Pacientes;
import br.com.iv.repository.EstoqueCentralRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class EstoqueCentralService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	EstoqueCentralRepository estoqueCentralRepository;

	
	public List<EstoqueCentral> listarTudo() {
		return estoqueCentralRepository.listAll(Sort.by("material"));
	}
	
	public List<EstoqueCentral> listarTodosEstoque(String material, String unidade) {
		return estoqueCentralRepository.estoquecentral(material, unidade);
	}

	public List<EstoqueCentralDto> listarTodosEstoqueDto(String material, String unidade) {
		return listarTodosEstoque(material, unidade).stream().map(this::toDto).toList();
	}

	@jakarta.transaction.Transactional
	public EstoqueCentral salvar(EstoqueCentralDto dto) {
		EstoqueCentral entity = new EstoqueCentral();

	    entity.setMaterial(dto.getMaterial() != null ? dto.getMaterial().toUpperCase() : null);
	    entity.setDescricao(dto.getDescricao());
	    entity.setFornecedor(dto.getFornecedor());
	    entity.setNotafiscal(dto.getNotafiscal());
	    entity.setTipo(dto.getTipo());
	    entity.setQuantidade(dto.getQuantidade());
	    entity.setValor(dto.getValor());
		entity.setFabricacao(validarData(dto.getFabricacao()));
		entity.setValidade(validarData(dto.getValidade()));
		entity.setEntrada(validarData(dto.getEntrada()));
	    entity.setLote(dto.getLote());
	    entity.setCadastradopor(dto.getCadastradopor());
	    entity.setAtualizadopor(dto.getAtualizadopor());
	    entity.setCorrelato(dto.getCorrelato());
	    entity.setControlado(dto.getControlado());
	    entity.setMedicamento(dto.getMedicamento());
	    entity.setAltavigilancia(dto.getAltavigilancia());
	    entity.setCodbarra(dto.getCodbarra());
	    entity.setPermuta(dto.isPermuta());
	    entity.setDescpermuta(dto.getDescpermuta());
	    entity.setIdzamed(dto.getIdzamed());
	    entity.setUnidade(dto.getUnidade());

	    entity.persist();
		return entity;
	}

	private String validarData(String data) {
		if (data == null)
			return null;
		data = data.trim();
		// Se vier um datetime ISO (ex: 2025-10-10T00:00:00.000Z) ou com espaço, extrai
		// a parte da data
		if (data.contains("T")) {
			data = data.substring(0, data.indexOf('T'));
		} else if (data.contains(" ")) {
			data = data.split(" ")[0];
		}
		// Aceita yyyy-MM-dd e normaliza para yyyy/MM/dd
		if (data.matches("\\d{4}-\\d{2}-\\d{2}")) {
			data = data.replace('-', '/');
		}
		if (data.length() == 10 && data.matches("\\d{4}/\\d{2}/\\d{2}")) {
			return data;
		}
		throw new IllegalArgumentException("Data deve estar no formato yyyy/MM/dd e ter 10 caracteres: " + data);
	}

	// Alterar
	@SuppressWarnings("static-access")
	@Transactional
	public void atualizar(int id, EstoqueCentralDto dto) {
		EstoqueCentral entity = new EstoqueCentral();
		Optional<EstoqueCentral> estoqueCentralOp = entity.findByIdOptional(id);
		if (estoqueCentralOp.isEmpty()) {
			System.out.println("Insumo não foi encontrado...");
		}

		entity = estoqueCentralOp.get();
		entity.setMaterial(dto.getMaterial() != null ? dto.getMaterial().toUpperCase() : null);
		entity.setDescricao(dto.getDescricao());
		entity.setFornecedor(dto.getFornecedor());
		entity.setNotafiscal(dto.getNotafiscal());
		entity.setTipo(dto.getTipo());
		entity.setQuantidade(dto.getQuantidade());
		entity.setValor(dto.getValor());
		// Normaliza e valida datas assim como no salvar
		entity.setFabricacao(validarData(dto.getFabricacao()));
		entity.setValidade(validarData(dto.getValidade()));
		entity.setEntrada(validarData(dto.getEntrada()));
		entity.setLote(dto.getLote());
		entity.setCadastradopor(dto.getCadastradopor());
		entity.setAtualizadopor(dto.getAtualizadopor());
		entity.setCorrelato(dto.getCorrelato());
		entity.setControlado(dto.getControlado());
		entity.setMedicamento(dto.getMedicamento());
		entity.setAltavigilancia(dto.getAltavigilancia());
		entity.setCodbarra(dto.getCodbarra());
		entity.setPermuta(dto.isPermuta());
		entity.setDescpermuta(dto.getDescpermuta());
		entity.setIdzamed(dto.getIdzamed());
		entity.setUnidade(dto.getUnidade());
		entity.persist();
		
		
		
		
		
		
		
	}

	@jakarta.transaction.Transactional
	public boolean deletar(int id) {
		return estoqueCentralRepository.deleteById((long) id);
	}

	private EstoqueCentralDto toDto(EstoqueCentral entity) {
		EstoqueCentralDto dto = new EstoqueCentralDto();
		dto.setIdinsumo(entity.getIdinsumo());
		// material can be null in the DB; guard against NPE
		dto.setMaterial(entity.getMaterial() != null ? entity.getMaterial().toUpperCase() : null);
		dto.setDescricao(entity.getDescricao());
		dto.setFornecedor(entity.getFornecedor());
		dto.setNotafiscal(entity.getNotafiscal());
		dto.setTipo(entity.getTipo());
		dto.setQuantidade(entity.getQuantidade());
		dto.setValor(entity.getValor());
		dto.setFabricacao(entity.getFabricacao());
		dto.setValidade(entity.getValidade());
		dto.setEntrada(entity.getEntrada());
		dto.setLote(entity.getLote());
		dto.setCadastradopor(entity.getCadastradopor());
		dto.setAtualizadopor(entity.getAtualizadopor());
		dto.setCorrelato(entity.getCorrelato());
		dto.setControlado(entity.getControlado());
		dto.setMedicamento(entity.getMedicamento());
		dto.setAltavigilancia(entity.getAltavigilancia());
		dto.setCodbarra(entity.getCodbarra());
		dto.setPermuta(entity.isPermuta());
		dto.setDescpermuta(entity.getDescpermuta());
		dto.setIdzamed(entity.getIdzamed());
		// was accidentally setting entity from dto; fix to set DTO from entity
		dto.setUnidade(entity.getUnidade());
		return dto;
	}

	private EstoqueCentral toEntity(EstoqueCentralDto dto) {
		EstoqueCentral entity = new EstoqueCentral();
		entity.setMaterial(dto.getMaterial() != null ? dto.getMaterial().toUpperCase() : null);
		entity.setDescricao(dto.getDescricao());
		entity.setFornecedor(dto.getFornecedor());
		entity.setNotafiscal(dto.getNotafiscal());
		entity.setTipo(dto.getTipo());
		entity.setQuantidade(dto.getQuantidade());
		entity.setValor(dto.getValor());
		entity.setFabricacao(dto.getFabricacao());
		entity.setValidade(dto.getValidade());
		entity.setEntrada(dto.getEntrada());
		entity.setLote(dto.getLote());
		entity.setCadastradopor(dto.getCadastradopor());
		entity.setAtualizadopor(dto.getAtualizadopor());
		entity.setCorrelato(dto.getCorrelato());
		entity.setControlado(dto.getControlado());
		entity.setMedicamento(dto.getMedicamento());
		entity.setAltavigilancia(dto.getAltavigilancia());
		entity.setCodbarra(dto.getCodbarra());
		entity.setPermuta(dto.isPermuta());
		entity.setDescpermuta(dto.getDescpermuta());
		entity.setIdzamed(dto.getIdzamed());
		entity.setUnidade(dto.getUnidade());
		return entity;
	}

	public List<EstoqueCentralDto> listarTodosEstoqu2eDto(String lote, String unidade) {
		return listarTodosEstoqueLote(lote, unidade).stream().map(this::toDto).toList();
	}

	public List<EstoqueCentral> listarTodosEstoqueLote(String lote, String unidade) {
		return estoqueCentralRepository.estoquecentralLote(lote, unidade);
	}
	
}

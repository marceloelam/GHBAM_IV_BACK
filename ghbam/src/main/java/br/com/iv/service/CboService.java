package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

import br.com.iv.dto.CboDto;
import br.com.iv.entity.Cbo;
import br.com.iv.repository.CboRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CboService implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@Inject
	CboRepository cboRepository;

	// Listando todos os cbo
	public List<Cbo> listarTodosCBO() {
		return cboRepository.listAll(Sort.by("titulo"));
	}
	
	
	@Transactional
	public List<Cbo> filtarPorCBO(String nome, CboDto dto) {

		List<Cbo> cboOp = cboRepository.filtarPorCbo(nome);
		if (cboOp.isEmpty()) {
			System.out.println("Esse CBO não foi encontrado...");
		}
		return cboOp;
	}

	
	@Transactional
	public List<Cbo> filtarPorCBOCodigo(String codigo, CboDto dto) {

		List<Cbo> cboOp = cboRepository.filtarPorCboCodigo(codigo);
		if (cboOp.isEmpty()) {
			System.out.println("Esse CBO não foi encontrado...");
		}
		return cboOp;
	}
	
}

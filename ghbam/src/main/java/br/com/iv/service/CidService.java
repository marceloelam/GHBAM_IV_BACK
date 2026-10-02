package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkus.panache.common.Sort;
import br.com.iv.dto.CboDto;
import br.com.iv.dto.Cid1Dto;
import br.com.iv.entity.Cid1;
import br.com.iv.repository.CidRepository;

@ApplicationScoped
public class CidService implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@Inject
	CidRepository cidRepository;

	// Listando todos os cbo
	public List<Cid1> listarTodosCid() {
		return cidRepository.listAll(Sort.by("titulo"));
	}
	
	
	@Transactional
	public List<Cid1> filtarPorCid(String nome, Cid1Dto dto) {

		List<Cid1> cidOp = cidRepository.filtarPorCid(nome);
		if (cidOp.isEmpty()) {
			System.out.println("Esse CID não foi encontrado...");
		}
		return cidOp;
	}

	
	@Transactional
	public List<Cid1> filtarPorCidCodigo(String codigo, CboDto dto) {

		List<Cid1> cidOp = cidRepository.filtarPorCidCodigo(codigo);
		if (cidOp.isEmpty()) {
			System.out.println("Esse CID não foi encontrado...");
		}
		return cidOp;
	}
	
	@Transactional
	public List<Cid1> filtarPorCid(String nome, CboDto dto) {

		List<Cid1> cidOp = cidRepository.locaisDeCid(nome);
		if (cidOp.isEmpty()) {
			System.out.println("Esse CID não foi encontrado...");
		}
		return cidOp;
	}
	
}

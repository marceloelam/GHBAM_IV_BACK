package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.BalancoHibricoResumoDto;
import br.com.iv.dto.BalancoHibricoSaidaDto;
import br.com.iv.entity.BalancoHidricoEntrada;
import br.com.iv.entity.BalancoHidricoSaida;
import br.com.iv.entity.Bam;
import br.com.iv.entity.ResumoBalancoHidrico;
import br.com.iv.repository.BalancoHidricoResumoRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@ApplicationScoped
public class ResumoBalancoHidricoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	BalancoHidricoResumoRepository resumoBalancoHidricoRepository;


	@Transactional
	public List<ResumoBalancoHidrico> filtarPorBamData(String bam, String data) {

		List<ResumoBalancoHidrico> balancohidrigoentrada = resumoBalancoHidricoRepository.filtrarPorBamData(bam, data);
		if (balancohidrigoentrada.isEmpty()) {
			System.out.println("Esse balanço de entrada não foi encontrado...");
		}
		return balancohidrigoentrada;
	}


	@Transactional
	public ResumoBalancoHidrico salvarResumoBalancoHidrico(BalancoHibricoResumoDto dto) {
	    ResumoBalancoHidrico resumo = new ResumoBalancoHidrico();

	    resumo.setUuid(dto.getUuid());
	    resumo.setBam(dto.getBam());
	    resumo.setDataBalanco(dto.getDataBalanco());
	    resumo.setTotalEntradasMl(dto.getTotalEntradasMl());
	    resumo.setTotalSaidasMl(dto.getTotalSaidasMl());
	    resumo.setSaldoHidricoMl(dto.getSaldoHidricoMl());
	    resumo.setDiureseTotalMl(dto.getDiureseTotalMl());
	    resumo.setDiureseMlKgH(dto.getDiureseMlKgH());
	    resumo.setClassificacaoBalanco(dto.getClassificacaoBalanco());
	    resumo.setAlertasClinicos(dto.getAlertasClinicos());
	    resumo.setObservacoes(dto.getObservacoes());
	    resumo.setDataCriacao(dto.getDataCriacao());
	    resumo.setDataAtualizacao(dto.getDataAtualizacao());
	    //resumo.setServidor(dto.getServidor());

	    resumo.persist();
	    return resumo;
	}

	// Alterar
	@Transactional
	public void alterarResumoBalancoHidrico(String id, BalancoHibricoResumoDto dto) {
		
		ResumoBalancoHidrico resumo = resumoBalancoHidricoRepository.findByUuid(id);
		
		if (resumo == null) {
			throw new IllegalArgumentException("Registro não encontrado para ID: " + id);
		}

		// Atualiza os campos simples
		
	    resumo.setBam(dto.getBam());
	    resumo.setDataBalanco(dto.getDataBalanco());
	    resumo.setTotalEntradasMl(dto.getTotalEntradasMl());
	    resumo.setTotalSaidasMl(dto.getTotalSaidasMl());
	    resumo.setSaldoHidricoMl(dto.getSaldoHidricoMl());
	    resumo.setDiureseTotalMl(dto.getDiureseTotalMl());
	    resumo.setDiureseMlKgH(dto.getDiureseMlKgH());
	    resumo.setClassificacaoBalanco(dto.getClassificacaoBalanco());
	    resumo.setAlertasClinicos(dto.getAlertasClinicos());
	    resumo.setObservacoes(dto.getObservacoes());
	    resumo.setDataCriacao(dto.getDataCriacao());
	    resumo.setDataAtualizacao(dto.getDataAtualizacao());
	   // resumo.setServidor(dto.getServidor());
	
		resumo.persist();
		
	}
	
	

}

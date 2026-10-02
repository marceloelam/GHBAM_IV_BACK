package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.MultidisciplinarPtsDto;
import br.com.iv.entity.MultidisciplinarPts;
import br.com.iv.repository.MultidisciplinarPtsRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class MultidisciplinarPtsService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	MultidisciplinarPtsRepository repository;

	public List<MultidisciplinarPts> listarPorPaciente(int idpaciente) {
		return repository.listarPorPaciente(idpaciente);
	}

	@Transactional
	public MultidisciplinarPts salvar(MultidisciplinarPtsDto dto, AuthenticatedUser usuario) {
		if (dto.getProblema() == null || dto.getProblema().isBlank()) {
			throw new IllegalArgumentException("Descreva o problema do item do PTS.");
		}
		MultidisciplinarPts item = new MultidisciplinarPts();
		copiarDtoParaEntity(dto, item);
		item.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? "PLANEJADO" : dto.getStatus());
		item.setProfissional(usuario == null ? dto.getProfissional() : usuario.nome());
		item.setMatriculaResponsavel(usuario == null ? "" : usuario.matricula());
		item.setCriadoPorMatricula(usuario == null ? "" : usuario.matricula());
		item.persist();
		return item;
	}

	@Transactional
	public void atualizar(int id, MultidisciplinarPtsDto dto) {
		Optional<MultidisciplinarPts> op = MultidisciplinarPts.findByIdOptional(id);
		MultidisciplinarPts item = op.orElseThrow(() -> new NullPointerException("Item de PTS nao encontrado."));
		copiarDtoParaEntity(dto, item);
		item.persist();
	}

	private void copiarDtoParaEntity(MultidisciplinarPtsDto dto, MultidisciplinarPts item) {
		item.setIdpaciente(dto.getIdpaciente());
		item.setIdAtendimento(dto.getIdAtendimento());
		item.setProblema(dto.getProblema());
		item.setObjetivo(dto.getObjetivo());
		item.setAcao(dto.getAcao());
		item.setCategoriaProfissional(dto.getCategoriaProfissional());
		item.setPrazo(dto.getPrazo());
		if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
			item.setStatus(dto.getStatus());
		}
		item.setResultadoEsperado(dto.getResultadoEsperado());
		item.setResultadoAlcancado(dto.getResultadoAlcancado());
		item.setObservacao(dto.getObservacao());
		if (dto.getDataConclusao() != null && !dto.getDataConclusao().isBlank()) {
			item.setDataConclusao(java.sql.Date.valueOf(dto.getDataConclusao().substring(0, 10)));
		}
	}

}

package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.MultidisciplinarEncaminhamentoDto;
import br.com.iv.entity.MultidisciplinarEncaminhamento;
import br.com.iv.repository.MultidisciplinarEncaminhamentoRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class MultidisciplinarEncaminhamentoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	MultidisciplinarEncaminhamentoRepository repository;

	public List<MultidisciplinarEncaminhamento> listarPorPaciente(int idpaciente) {
		return repository.listarPorPaciente(idpaciente);
	}

	public List<MultidisciplinarEncaminhamento> listarPorCategoriaDestino(String categoriaDestino, String status) {
		return repository.listarPorCategoriaDestino(categoriaDestino, status);
	}

	@Transactional
	public MultidisciplinarEncaminhamento salvar(MultidisciplinarEncaminhamentoDto dto, AuthenticatedUser usuario) {
		if (dto.getCategoriaDestino() == null || dto.getCategoriaDestino().isBlank()) {
			throw new IllegalArgumentException("Categoria de destino e obrigatoria.");
		}
		MultidisciplinarEncaminhamento encaminhamento = new MultidisciplinarEncaminhamento();
		encaminhamento.setIdpaciente(dto.getIdpaciente());
		encaminhamento.setIdAtendimentoOrigem(dto.getIdAtendimentoOrigem());
		encaminhamento.setCategoriaOrigem(dto.getCategoriaOrigem());
		encaminhamento.setCategoriaDestino(dto.getCategoriaDestino());
		encaminhamento.setProfissionalDestino(dto.getProfissionalDestino());
		encaminhamento.setUnidade(dto.getUnidade());
		encaminhamento.setSetor(dto.getSetor());
		encaminhamento.setMotivo(dto.getMotivo());
		encaminhamento.setPrioridade(dto.getPrioridade());
		encaminhamento.setObservacao(dto.getObservacao());
		encaminhamento.setStatus("SOLICITADO");
		encaminhamento.setProfissionalSolicitante(usuario == null ? "" : usuario.nome());
		encaminhamento.setMatriculaSolicitante(usuario == null ? "" : usuario.matricula());
		encaminhamento.setCriadoPorMatricula(usuario == null ? "" : usuario.matricula());
		encaminhamento.persist();
		return encaminhamento;
	}

	@Transactional
	public void atualizarStatus(int id, String status) {
		Optional<MultidisciplinarEncaminhamento> op = MultidisciplinarEncaminhamento.findByIdOptional(id);
		MultidisciplinarEncaminhamento encaminhamento = op
				.orElseThrow(() -> new NullPointerException("Encaminhamento nao encontrado."));
		encaminhamento.setStatus(status);
		encaminhamento.persist();
	}

}

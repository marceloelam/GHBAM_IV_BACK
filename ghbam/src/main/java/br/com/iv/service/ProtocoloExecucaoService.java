package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;

import br.com.iv.entity.ProtocoloExecucao;
import br.com.iv.repository.ProtocoloExecucaoRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ProtocoloExecucaoService {

	@Inject
	ProtocoloExecucaoRepository repository;

	@Inject
	EntityManager entityManager;

	public List<ProtocoloExecucao> listarTodos() {
		return repository.listAll();
	}

	public ProtocoloExecucao buscarPorId(int id) {
		return repository.findById((long) id);
	}

	public List<ProtocoloExecucao> buscarPorUnidade(String unidade) {
		return repository.buscarPorUnidade(unidade);
	}

	public List<ProtocoloExecucao> buscarPorProtocoloId(int protocoloId) {
		return repository.buscarPorProtocoloId(protocoloId);
	}

	public List<ProtocoloExecucao> buscarPorServidorExecutor(String servidor) {
		return repository.buscarPorServidorExecutor(servidor);
	}

	@Transactional
	public ProtocoloExecucao salvar(ProtocoloExecucao execucao) {
		execucao.setDataExecucao(LocalDateTime.now());
		if (execucao.getStatus() == null) {
			execucao.setStatus("em_andamento");
		}
		repository.persist(execucao);
		return execucao;
	}

	@Transactional
	public ProtocoloExecucao atualizarResultados(int id, String resultadosJson, String acoesExecutadasJson,
			String alertasJson, String examesSolicitadosJson, String tarefasCriadasJson) {
		ProtocoloExecucao execucao = repository.findById((long) id);
		if (execucao == null) {
			return null;
		}
		if (resultadosJson != null) {
			execucao.setResultadosJson(resultadosJson);
		}
		if (acoesExecutadasJson != null) {
			execucao.setAcoesExecutadasJson(acoesExecutadasJson);
		}
		if (alertasJson != null) {
			execucao.setAlertasJson(alertasJson);
		}
		if (examesSolicitadosJson != null) {
			execucao.setExamesSolicitadosJson(examesSolicitadosJson);
		}
		if (tarefasCriadasJson != null) {
			execucao.setTarefasCriadasJson(tarefasCriadasJson);
		}
		return execucao;
	}

	@Transactional
	public ProtocoloExecucao atualizarStatus(int id, String status, String observacoes) {
		ProtocoloExecucao execucao = repository.findById((long) id);
		if (execucao == null) {
			return null;
		}
		if (status != null) {
			execucao.setStatus(status);
		}
		if (observacoes != null) {
			execucao.setObservacoes(observacoes);
		}
		return execucao;
	}

	@Transactional
	public boolean deletar(int id) {
		return repository.deleteById((long) id);
	}
}

package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.ProtocoloExecucao;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProtocoloExecucaoRepository implements PanacheRepository<ProtocoloExecucao> {

	public List<ProtocoloExecucao> buscarPorUnidade(String unidade) {
		return find("unidade = ?1 order by dataExecucao desc", unidade).list();
	}

	public List<ProtocoloExecucao> buscarPorProtocoloId(int protocoloId) {
		return find("protocoloId = ?1 order by dataExecucao desc", protocoloId).list();
	}

	public List<ProtocoloExecucao> buscarPorServidorExecutor(String servidor) {
		return find("servidorExecutor = ?1 order by dataExecucao desc", servidor).list();
	}
}

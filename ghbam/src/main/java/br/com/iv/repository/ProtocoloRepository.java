package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.Protocolo;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProtocoloRepository implements PanacheRepository<Protocolo> {

	public List<Protocolo> listarTodos() {
		return find("ativo = true order by nome").list();
	}

	public List<Protocolo> buscarPorEspecialidade(String especialidade) {
		return find("especialidade = ?1 and ativo = true order by nome", especialidade).list();
	}

	public Protocolo buscarPorNome(String nome) {
		return find("nome = ?1 and ativo = true", nome).firstResult();
	}
}

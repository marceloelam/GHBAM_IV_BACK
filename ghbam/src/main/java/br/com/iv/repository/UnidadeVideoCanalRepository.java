package br.com.iv.repository;

import java.util.List;
import java.util.Optional;

import br.com.iv.entity.UnidadeVideoCanal;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UnidadeVideoCanalRepository implements PanacheRepository<UnidadeVideoCanal> {
	public List<UnidadeVideoCanal> listarPorSigla(String sigla) {
		return list("sigla = ?1 order by ordem, id", sigla);
	}

	public Optional<UnidadeVideoCanal> buscarPorSiglaEUrl(String sigla, String url) {
		return find("sigla = ?1 and url = ?2", sigla, url).firstResultOptional();
	}

	public Optional<UnidadeVideoCanal> buscarPorIdESigla(Long id, String sigla) {
		return find("id = ?1 and sigla = ?2", id, sigla).firstResultOptional();
	}
}

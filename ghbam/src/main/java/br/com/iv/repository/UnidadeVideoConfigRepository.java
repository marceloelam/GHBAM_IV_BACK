package br.com.iv.repository;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.UnidadeVideoConfig;

@ApplicationScoped
public class UnidadeVideoConfigRepository implements PanacheRepository<UnidadeVideoConfig> {

	public Optional<UnidadeVideoConfig> buscarPorSigla(String sigla) {
		return find("sigla", sigla).firstResultOptional();
	}
}

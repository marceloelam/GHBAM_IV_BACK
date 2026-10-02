package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.UnidadeVideo;

@ApplicationScoped
public class UnidadeVideoRepository implements PanacheRepository<UnidadeVideo> {

	public List<UnidadeVideo> listarPorSigla(String sigla) {
		return find("sigla = ?1 order by ordem, id", sigla).list();
	}

	public List<UnidadeVideo> listarAtivosPorSigla(String sigla) {
		return find("sigla = ?1 and ativo = true order by ordem, id", sigla).list();
	}
}

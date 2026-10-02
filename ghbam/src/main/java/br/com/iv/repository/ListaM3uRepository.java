package br.com.iv.repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import br.com.iv.entity.ListaM3u;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ListaM3uRepository implements PanacheRepository<ListaM3u> {
	public List<ListaM3u> listarOrdenadas() {
		return listAll(Sort.by("nome").ascending());
	}

	public Optional<ListaM3u> buscarPorNome(String nome) {
		return find("lower(nome) = ?1", nome.toLowerCase(Locale.ROOT)).firstResultOptional();
	}

	public Optional<ListaM3u> buscarOutraPorNome(Long id, String nome) {
		return find("id <> ?1 and lower(nome) = ?2", id, nome.toLowerCase(Locale.ROOT)).firstResultOptional();
	}
}

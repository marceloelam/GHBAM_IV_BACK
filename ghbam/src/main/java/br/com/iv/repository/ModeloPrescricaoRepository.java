package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.ModeloPrescricao;

@ApplicationScoped
public class ModeloPrescricaoRepository implements PanacheRepository<ModeloPrescricao> {

	public List<ModeloPrescricao> filtarPorTituloModelo(String query) {
		if (query.equals("tudo")) {
			return find("upper(titulo) like ?1 order by titulo", "%").list();
		} else {
			return find("upper(titulo) like ?1", query.toUpperCase() + "%").list();
		}

	}
}

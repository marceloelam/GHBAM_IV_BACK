package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.ModeloPrescricaoEnf;

@ApplicationScoped
public class ModeloPrescricaoEnfRepository implements PanacheRepository<ModeloPrescricaoEnf> {

	public List<ModeloPrescricaoEnf> filtarPorTituloModelo(String query) {
		if (query.equals("tudo")) {
			return find("upper(titulo) like ?1 order by titulo", "%").list();
		} else {
			return find("upper(titulo) like ?1", query.toUpperCase() + "%").list();
		}

	}
}

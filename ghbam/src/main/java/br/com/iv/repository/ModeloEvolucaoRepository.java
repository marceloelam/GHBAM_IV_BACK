package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.ModeloEvolucao;

@ApplicationScoped
public class ModeloEvolucaoRepository implements PanacheRepository<ModeloEvolucao> {

	
	
	
	
	public List<ModeloEvolucao> filtarPorTituloModelo(String query) {
		if (query.equals("tudo")) {
			return find("upper(titulo) like ?1 order by titulo", "%").list();
		} else {
			return find("upper(titulo) like ?1", query.toUpperCase() + "%").list();
		}

	}
}

package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.Salas;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class SalasRepository implements PanacheRepository<Salas> {
	
	public List<Salas> filtarPorSala(String query, String local) {
		return find("upper(unidade) like ?1 and local = ?2", "%" + query.toUpperCase() + "%", local).list();
	}

	
	public List<Salas> filtarPorSala(String query) {
		return find("upper(unidade) like ?1 order by sala", "%" + query.toUpperCase() + "%").list();
	}

	public List<Salas> filtarPorSalaunidade(String query, String local) {
		return find("upper(unidade) like ?1 and sala = ?2", "%" + query.toUpperCase() + "%", local).list();
	}


}

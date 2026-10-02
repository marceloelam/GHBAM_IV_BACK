package br.com.iv.repository;

import java.util.List;

import jakarta.inject.Inject;

import br.com.iv.entity.NovoInternadoAdm;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class NovoInternadoAdmRepository implements PanacheRepository<NovoInternadoAdm> {
	@Inject
	EntityManager entityManager;

	public List<NovoInternadoAdm> filtarPorCbo(String query) {
		return find("upper(titulo) like ?1", "%" + query.toUpperCase() + "%").list();
	}

	public NovoInternadoAdm filtrarPorUnico(int query) {
		return find("nbam = ?1", query).firstResult();
	}

	


}

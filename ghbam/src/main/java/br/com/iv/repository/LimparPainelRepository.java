package br.com.iv.repository;

import br.com.iv.entity.Pacientes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class LimparPainelRepository implements PanacheRepository<Pacientes> {
	@Inject
	EntityManager entityManager;

	@Transactional
	public void limparPainel(String unidade) {
		String sql = "delete from chamadapaciente where unidade = :unidade";
		entityManager.createNativeQuery(sql).setParameter("unidade", unidade).executeUpdate();

	}
}
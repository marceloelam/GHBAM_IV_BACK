package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.Servidor;
import br.com.iv.entity.Servidor2;

@ApplicationScoped
public class Servidor2Repository implements PanacheRepository<Servidor2> {
	@Inject
	EntityManager entityManager;

	@PersistenceContext
	private EntityManager em;

	public List<Servidor2> findAllServidor2es() {
		return listAll();
	}

	public Servidor2 findByBiometria(String biometria) {
		return find("biometria", biometria).firstResult();
	}

	public List<Servidor2> filtarPorServidor2(String query, String query2) {
		return find("nome like ?1 and unidade = ?2", query + "%", query2).list();
	}

	public List<Servidor2> filtarPorUnidadeDoServidor2(String query) {
		return find("unidade like ?1", query + "%").list();
	}

	public Servidor2 filtrarPorID(int id) {
		return find("idServidor2", id).firstResult();
	}

	public List<Servidor2> filtarPorUnidadeDoServidor2Medico(String query) {
		return find("unidade like ?1 and cargo = 'MÉDICO' and ativo=true order by nome", query + "%").list();
	}

	public Servidor2 findByNomeDoMedico(String nome, String unidade) {
		return find("nome = ?1 and unidade = ?2", nome, unidade).firstResult();
	}

	
	
	public Servidor2 localizarMatricula(String matricula) {
		// Sem ORDER BY o Postgres pode devolver uma linha diferente a cada consulta
		// quando existe matricula duplicada: era isso que fazia as permissoes
		// "sumirem de um dia para o outro". Fixa no registro mais antigo.
		return find("matricula = ?1 order by idservidor2", matricula).firstResult();
	}

	public Servidor2 localizarMatriculaBiometria(String biometria) {
		System.out.println("*** Passei aqui ***");
		return find("biometria", biometria).firstResult();
	}

	// Atualizar senha do Servidor2
	public void alterarSenha(String matricula) {
		//String sql = "UPDATE Servidor2 set pwd = :pwd where matricula = :matricula";
		//entityManager.createNativeQuery(sql).setParameter("pwd", pwd).setParameter("matricula", matricula)
		//		.executeUpdate();
		System.out.println("");
	}
	
	// Atualizar senha do Servidor2
		public void alterarStatus(int id, boolean status) {
			String sql = "UPDATE Servidor2 set ativo = :ativo where idServidor2 = :idServidor2";
			entityManager.createNativeQuery(sql).setParameter("ativo", status).setParameter("idServidor2", id)
					.executeUpdate();
			System.out.println(sql);
		}

}

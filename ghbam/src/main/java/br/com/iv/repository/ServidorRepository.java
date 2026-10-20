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

@ApplicationScoped
public class ServidorRepository implements PanacheRepository<Servidor> {
	@Inject
	EntityManager entityManager;

	@PersistenceContext
	private EntityManager em;

	public List<Servidor> findAllServidores() {
		return listAll();
	}

	public Servidor findByBiometria(String biometria) {
		return find("biometria", biometria).firstResult();
	}

	public List<Servidor> filtarPorServidor(String query, String query2) {
		return find("nome like ?1 and unidade = ?2", query + "%", query2).list();
	}

	public List<Servidor> filtarPorUnidadeDoServidor(String query) {
		return find("unidade like ?1", query + "%").list();
	}

	public List<Servidor> filtarPorUnidadeDoServidorAtivo(String unidade) {
		return find("unidade = ?1 and ativo = true", unidade).list();
	}

	public Servidor filtrarPorID(int id) {
		return find("idservidor", id).firstResult();
	}

	public List<Servidor> filtarPorUnidadeDoServidorMedico(String query) {
		return find("unidade like ?1 and cargo = 'MÉDICO' and ativo=true order by nome", query + "%").list();
	}

	public Servidor findByNomeDoMedico(String nome, String unidade) {
		return find("nome = ?1 and unidade = ?2", nome, unidade).firstResult();
	}

	public Servidor localizarMatricula(String matricula) {
		//return find("matricula =?1 and ativo = true", matricula).firstResult();
		return find("matricula =?1", matricula).firstResult();
	}

	public Servidor localizarMatriculaBiometria(String biometria) {
		System.out.println("*** Passei aqui ***");
		return find("biometria", biometria).firstResult();
	}

	// Atualizar senha do servidor
	public void alterarSenha(String matricula, String pwd) {
		String sql = "UPDATE servidor set pwd = :pwd where matricula = :matricula";
		entityManager.createNativeQuery(sql).setParameter("pwd", pwd).setParameter("matricula", matricula)
				.executeUpdate();
	}

	// Atualizar senha do servidor
	public void alterarStatus(int id, boolean status) {
		String sql = "UPDATE servidor set ativo = :ativo where idservidor = :idservidor";
		entityManager.createNativeQuery(sql).setParameter("ativo", status).setParameter("idservidor", id)
				.executeUpdate();
	}

	public List<Servidor> especialistas(String unidade) {
		return find(
				"cargo = 'MÉDICO' or cargo = 'FONOAUDIÓLOGO' or cargo = 'NUTRICIONISTA'   or cargo = 'PSIC'   or cargo = 'FISIOTERAPIA'  or cargo = 'ASSISTENTE' and unidade = ?1 order by nome, cargo", unidade)
				.list();

	}

	/**
	 * Retorna todos os servidores que possuem template biométrico cadastrado.
	 * Considera o campo 'fir' como principal e 'biometria' como fallback,
	 * pois alguns registros podem ter sido gravados antes de o campo 'fir' existir.
	 */
	public List<Servidor> findAllComFir() {
		return find("(fir is not null and fir <> '') or (biometria is not null and biometria <> '')").list();
	}

	public List<Servidor> findAllComSourceafis() {
		return find("biometriaSourceafis is not null and biometriaSourceafis <> ''").list();
	}

	// Migracao automatica: so grava quando o servidor ainda nao tem modelo SourceAFIS.
	@jakarta.transaction.Transactional
	public int salvarSourceafisSeAusente(String matricula, String modelo) {
		return update("biometriaSourceafis = ?1 where matricula = ?2 and (biometriaSourceafis is null or biometriaSourceafis = '')",
				modelo, matricula);
	}

}

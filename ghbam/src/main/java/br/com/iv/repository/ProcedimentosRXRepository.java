package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.ProcedimentosRX;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class ProcedimentosRXRepository implements PanacheRepository<ProcedimentosRX> {
	@Inject
	EntityManager entityManager;

	public List<ProcedimentosRX> filtarPorProcedimentos(String query) {
		return find("descricao like ?1 order by descricao", "%" + query + "%").list();
	}

	public List<ProcedimentosRX> listarPorCodigos(List<String> codigos) {
		if (codigos == null || codigos.isEmpty()) {
			return List.of();
		}
		return find("codprocedimento in ?1 order by descricao", codigos).list();
	}

	public List<ProcedimentosRX> filtarPorModo(String query) {
		if (query.equals("MEDICACAO"))
			return find("modo like ?1 order by descricao", "%MEDICACAO").list();
		else
			return find("modo like ?1 order by descricao", "%" + query + "%").list();
	}

	public List<ProcedimentosRX> filtarPorModo2(String query) {
		return find("modo2 like ?1 order by descricao", "%" + query + "%").list();
	}

	public ProcedimentosRX filtrarPorUnicoProcedimentoRX(String procedimento, String descricao) {
		return find("codprocedimento = ?1 and descricao = ?2", procedimento, descricao).firstResult();
	}

	public List<ProcedimentosRX> filtarPorProcedimentosModos(String query, String query2, String query3) {
		return find("descricao like ?1 and modo like ?2 and modo2 like ?3   order by descricao", "%" + query + "%",
				'%' + query2 + "%", '%' + query3 + "%").list();
	}

	public void alterarProcedimentosRX(String descricao, String codprocedimento, String modo, String modo2, int id) {
		String sql = "UPDATE ProcedimentosRXunidade SET descricao = :descricao, codprocedimento = :codprocedimento  WHERE siglaunidade = :nomeAntigo";
		entityManager.createNativeQuery(sql).setParameter("descricao", descricao)
				.setParameter("codprocedimento", codprocedimento).setParameter("modo", modo)
				.setParameter("modo2", modo2).executeUpdate();
		System.out.println(sql);
	}

}

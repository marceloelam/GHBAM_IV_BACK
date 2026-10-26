package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.PedidoResultadoLaboratorio;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PedidoResultadoLaboratorioRepository implements PanacheRepository<PedidoResultadoLaboratorio> {
	@Inject
	EntityManager entityManager;

	public List<PedidoResultadoLaboratorio> filtrarPeloIDPaciente(int query1) {
		return find(
				"SELECT p FROM PedidoResultadoLaboratorio p INNER JOIN bam b on p.bam = b.idbam WHERE b.paciente_idpaciente = ?1 "
						+ "  and local = 'LABORATORIO' ORDER BY CAST(p.dt AS date) DESC",
				query1).list();
	}

	public List<PedidoResultadoLaboratorio> filtrarBamPedidoResultadoLaboratorio(int query1) {
		return find("bam = ?1 and local = 'LABORATORIO' order by cast(dt as date) desc, cast(hr as time) desc", query1)
				.list();
	}

	@Transactional
	public void pdfExame(int idpedido, String arquivo, String liberou) {
		String sql = "UPDATE pedidoresultadolaboratorio set arquivo = :arquivo, armazenamento_chave = null where idpedidoresultado = :idpedidoresultado";
		entityManager.createNativeQuery(sql).setParameter("idpedidoresultado", idpedido).setParameter("arquivo", arquivo).executeUpdate();
	}

	// Laudo guardado no MinIO: o banco fica so com a chave.
	public void pdfExameArmazenado(int idpedido, String chave) {
		String sql = "UPDATE pedidoresultadolaboratorio set arquivo = '', armazenamento_chave = :chave where idpedidoresultado = :idpedidoresultado";
		entityManager.createNativeQuery(sql).setParameter("idpedidoresultado", idpedido).setParameter("chave", chave).executeUpdate();
	}

	@SuppressWarnings("unchecked")
	public List<PedidoResultadoLaboratorio> lista_de_resultados_de_exames_pedidomedico2_lab_result(int idbam) {
	    String sql = """
	        SELECT DISTINCT ON (CAST(p2.dt AS DATE), p2.hr)
	               CAST(p2.dt AS DATE) AS data_resultado,
	               p2.*
	        FROM pedidoresultadolaboratorio p2
	        INNER JOIN bam b ON b.idbam = p2.bam
	        WHERE p2.bam = :idbam
	        ORDER BY CAST(p2.dt AS DATE) DESC, p2.hr
	        """;

	    return getEntityManager()
	            .createNativeQuery(sql, PedidoResultadoLaboratorio.class)
	            .setParameter("idbam", idbam)
	            .getResultList();
	}

}

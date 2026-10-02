package br.com.iv.repository;

import java.util.List;
import jakarta.persistence.Query;

import br.com.iv.entity.ResumoDeAlta;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ResumoDeAltaRepository implements PanacheRepository<ResumoDeAlta> {

	public List<ResumoDeAlta> filtarPorBam(String query) {
		return find("bam like ?1 and (status is null or status <> 'CANCELADO')", "%" + query.toUpperCase() + "%").list();
	}

	public ResumoDeAlta filtrarPorUnicoBam(int query) {
		return find("bam = ?1 and (status is null or status <> 'CANCELADO')", query).firstResult();
	}

	public List<ResumoDeAlta> listarAtivos() {
		return find("status is null or status <> 'CANCELADO' order by idResumoalta desc").list();
	}

	@SuppressWarnings("unchecked")
	public List<ResumoDeAlta> listarCanceladosPorUnidade(String unidade, String nome, String bam, String prontuario) {
		StringBuilder sql = new StringBuilder("SELECT r.* FROM resumodealta r JOIN bam b ON r.bam ~ '^[0-9]+$' AND CAST(r.bam AS INTEGER) = b.idbam "
				+ "WHERE COALESCE(upper(r.status), 'ATIVO') = 'CANCELADO' AND upper(b.unidade) = upper(:unidade)");
		if (nome != null && !nome.isBlank()) sql.append(" AND upper(r.nome) LIKE upper(:nome)");
		if (bam != null && !bam.isBlank()) sql.append(" AND r.bam LIKE :bam");
		if (prontuario != null && !prontuario.isBlank()) sql.append(" AND upper(r.prontuario) LIKE upper(:prontuario)");
		sql.append(" ORDER BY r.cancelado_em DESC NULLS LAST, r.idresumoalta DESC");
		Query query = getEntityManager().createNativeQuery(sql.toString(), ResumoDeAlta.class).setParameter("unidade", unidade);
		if (nome != null && !nome.isBlank()) query.setParameter("nome", "%" + nome.trim() + "%");
		if (bam != null && !bam.isBlank()) query.setParameter("bam", "%" + bam.trim() + "%");
		if (prontuario != null && !prontuario.isBlank()) query.setParameter("prontuario", "%" + prontuario.trim() + "%");
		return query.getResultList();
	}

}

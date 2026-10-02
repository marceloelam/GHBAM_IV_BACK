package br.com.iv.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.iv.entity.PrescricaoContinuacao;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PrescricaoContinuacaoRepository implements PanacheRepository<PrescricaoContinuacao> {
	@Inject
	EntityManager entityManager;

	public List<PrescricaoContinuacao> filtarPoBam(int bam) {
		return find("bam = ?1 order by cast(data as date) desc, cast(hora as time) desc", bam).list();
	}

	public PrescricaoContinuacao findByUuid(String id) {
		return find("uuid", id).firstResult();
	}

	@SuppressWarnings("unchecked")
	@Transactional
	public List<PrescricaoContinuacao> filtarPoBam2(int bam, String data) {
	    // Converte yyyy-MM-dd -> dd/MM/yyyy
	    LocalDate dt = LocalDate.parse(data, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
	    String dataFormatada = dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

	    String sql = "SELECT * FROM prescricaocont " +
	                 "WHERE bam = " + bam +
	                 " AND to_char(cast(data as date), 'DD/MM/YYYY') = '" + dataFormatada + "' " +
	                 "ORDER BY cast(data as date) desc, hora ";

	    return getEntityManager()
	            .createNativeQuery(sql, PrescricaoContinuacao.class)
	            .getResultList();
	}


	public PrescricaoContinuacao filtarUltimaPoBam(int bam) {
		return find("bam = ?1  order by idprescricaocontinuacao desc", bam).firstResult();
	}

}

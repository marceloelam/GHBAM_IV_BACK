package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.dto.InternacaoDto;
import br.com.iv.entity.Internacao;

@ApplicationScoped
public class InternacaoRepository implements PanacheRepository<Internacao> {
	@Inject
	EntityManager entityManager;

	public List<Internacao> filtarPorCbo(String query) {
		return find("upper(titulo) like ?1", "%" + query.toUpperCase() + "%").list();
	}

	public Internacao filtrarPorUnico(String query) {
		return find("bam = ?1", query).firstResult();
	}

	public List<Internacao> filtarPorCboCodigo(String query) {
		return find("upper(codigo) like ?1", "%" + query.toUpperCase() + "%").list();
	}

	public void alterarInternacao(int id, InternacaoDto dto) {
		String sql = "UPDATE internacao set datasaida = :dtsaida, motivodaalta = :motivoalta, origem = :origem, medicodeualta = :medicodeualta, obs = :obs where idninternado = :id";
		entityManager.createNativeQuery(sql).setParameter("dtsaida", dto.getDatasaida())
				.setParameter("motivoalta", dto.getMotivodaalta()).setParameter("origem", dto.getOrigem())
				.setParameter("medicodeualta", dto.getMedicodeualta()).setParameter("obs", dto.getObs())
				.setParameter("id", id).executeUpdate();
		
	}
	
	public void restaurarinternacao(String bam) {
		String sql = "UPDATE internacao set datasaida = null, motivodaalta = '-', medicodeualta = 'Não informado...' where bam = :bam";
		entityManager.createNativeQuery(sql).setParameter("bam", bam).executeUpdate();
	}

}

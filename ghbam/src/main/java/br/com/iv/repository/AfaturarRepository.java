package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.Afaturar;

@ApplicationScoped
public class AfaturarRepository implements PanacheRepository<Afaturar> {

	public List<Afaturar> filtarPorPeriodoEunidade(String query1, String query2, String unidade) {
		unidade = unidade.toUpperCase();

		return getEntityManager()
				.createNativeQuery("""
						select *
						  from afaturar
						 where unidade = ?3
						   and case
						         when data ~ '^\\d{4}-\\d{2}-\\d{2}' then cast(substring(data, 1, 10) as date)
						         when data ~ '^\\d{2}/\\d{2}/\\d{4}' then to_date(substring(data, 1, 10), 'DD/MM/YYYY')
						         else null
						       end between cast(?1 as date) and cast(?2 as date)
						 order by case
						         when data ~ '^\\d{4}-\\d{2}-\\d{2}' then cast(substring(data, 1, 10) as date)
						         when data ~ '^\\d{2}/\\d{2}/\\d{4}' then to_date(substring(data, 1, 10), 'DD/MM/YYYY')
						         else null
						       end, idafaturar
						""", Afaturar.class)
				.setParameter(1, query1)
				.setParameter(2, query2)
				.setParameter(3, unidade)
				.getResultList();
	}
	
	

}

package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.BalancoHidricoEntrada;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BalancoHidricoEntradaRepository implements PanacheRepository<BalancoHidricoEntrada> {

	public BalancoHidricoEntrada findByUuid(String id) {
		return find("uuid", id).firstResult();
	}

	@Transactional
	public List<BalancoHidricoEntrada> filtrarPorBamData(String bam, String data) {
	    //LocalDate date = LocalDate.parse(data); // converte "2025-09-02" para LocalDate
	    
	    List<BalancoHidricoEntrada> resultados = find(
	        "bam = ?1 AND cast(dataRegistro as date)  = cast(?2 as date) order by cast(dataRegistro as date), hora asc", 
	        bam, 
	        data
	    ).list();

	    if (resultados.isEmpty()) {
	    }
	    return resultados;
	}

}

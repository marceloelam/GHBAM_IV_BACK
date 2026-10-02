package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.ResumoBalancoHidrico;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BalancoHidricoResumoRepository implements PanacheRepository<ResumoBalancoHidrico> {

	
	public  ResumoBalancoHidrico findByUuid(String id) {
		return find("uuid", id).firstResult();
	}
	
	
	@Transactional
	public List<ResumoBalancoHidrico> filtrarPorBamData(String bam, String data) {
	    String dataFormatada = data.replace("-", "/");
	    
	    Query query = getEntityManager().createNativeQuery(
	        "SELECT * FROM resumo_balanco_hidrico WHERE bam = ? AND DATE(dataatualizacao) = DATE(?) limit 1",
	        ResumoBalancoHidrico.class
	    );
	    
	    query.setParameter(1, bam);
	    query.setParameter(2, dataFormatada);
	    
	    List<ResumoBalancoHidrico> resultados = query.getResultList();
	    
	    if (resultados.isEmpty()) {
	        System.out.println("Nenhum balanço hídrico de entrada encontrado para BAM " + bam + " na data " + data);
	    }
	    return resultados;
	    
	}
	


}

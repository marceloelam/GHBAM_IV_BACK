package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.EscalaDeEnfermagem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EscalaDeEnfermagemRepository implements  PanacheRepositoryBase<EscalaDeEnfermagem, Integer> {

	public List<EscalaDeEnfermagem> filtarPorBamEData(int bam, String dt, String unidade) {

		return find("cast(data as date) =  cast(?1 as date) and bam = ?2 order by id desc limit 1",dt, bam)
				.list();
	}

}

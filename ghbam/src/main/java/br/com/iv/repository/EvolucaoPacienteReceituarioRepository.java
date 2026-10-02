package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.EvolucaoPacienteReceituario;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EvolucaoPacienteReceituarioRepository implements PanacheRepositoryBase<EvolucaoPacienteReceituario, Integer> {

	
	public List<EvolucaoPacienteReceituario> filtarPorId(int id) {
		return find("nbam = ?1", id).list();
	}
	
	public List<EvolucaoPacienteReceituario> filtarPoBam(int bam) {
		return find("nbam = ?1 order by cast(data as date) desc, cast(horaev as time) desc", bam).list();
	}
	
	public EvolucaoPacienteReceituario filtarUltimaPoBam(int bam) {
		return find("nbam = ?1  order by idevolucao desc", bam).firstResult();
	}

}

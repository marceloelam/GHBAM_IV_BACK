package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.EvolucaoPaciente;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class EvolucaoPacienteRepository implements PanacheRepositoryBase<EvolucaoPaciente, Integer> {
	@Inject
	EntityManager entityManager;
	
	public List<EvolucaoPaciente> filtarPorId(int id) {
		return find("nbam = ?1", id).list();
	}
	
	public List<EvolucaoPaciente> filtarPoBam(int bam, String inicio, String fim) {
		return find("nbam = ?1 and cast(data as date) between cast(?2 as date) and cast(?3 as date) order by cast(data as date) desc, cast(horaev as time) desc", bam, inicio, fim).list();
	}
	
	public List<EvolucaoPaciente> filtarPoBam2(int bam) {
		return find("nbam = ?1 order by cast(data as date) desc, cast(horaev as time) desc", bam).list();
	}
	
	public EvolucaoPaciente filtarUltimaPoBam(int bam) {
		return find("nbam = ?1  order by idevolucao desc", bam).firstResult();
	}
	
	public List<EvolucaoPaciente>  filtarUltimaEvolucao(int bam) {
		return find("nbam = ?1 and data = (select max(e.data) from EvolucaoPaciente e where e.nbam = ?1) order by idevolucao", bam).list();
	}
	
	
	public void inativar(int id) {
		String sql = "UPDATE evolucaopaciente set inativo= true  where idevolucao = :id_da_evo";
		entityManager.createNativeQuery(sql).setParameter("id_da_evo", id).executeUpdate();
	}

}

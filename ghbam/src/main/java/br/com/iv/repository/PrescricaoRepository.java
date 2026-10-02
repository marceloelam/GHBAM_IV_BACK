package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.Prescricao;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class PrescricaoRepository implements PanacheRepositoryBase<Prescricao, Integer> {
	@Inject
	EntityManager entityManager;

	public List<Prescricao> filtarPorId(int id) {
		return find("idprescricao = ?1", id).list();
	}
	
	
	public List<Prescricao> filtarPoBam(int bam, String inicio, String fim) {
		return find("bam = ?1 and cast(data as date) between cast(?2 as date) and cast(?3 as date) order by cast(data as date) desc, cast(hora as time) desc", bam, inicio, fim).list();
		
	}

	public List<Prescricao> filtarPoBam2(int bam) {
		return find("bam = ?1 order by cast(data as date) desc, cast(hora as time) desc", bam).list();
	}

	public List<Prescricao> filtarPoBam(int bam, String inicio, String fim, String tipo) {
		if ("1".equals(tipo)) {
			return find(
					"bam = ?1 and cast(data as date) between cast(?2 as date) and cast(?3 as date) and (quem = 'ENFERMEIRO' or quem = 'TECNICO' or quem is null) order by cast(data as date) desc, cast(hora as time) desc",
					bam, inicio, fim).list();
		} else {
			return find(
					"bam = ?1 and cast(data as date) between cast(?2 as date) and cast(?3 as date) and (quem != 'ENFERMEIRO' and quem != 'TECNICO' and quem is not null) order by cast(data as date) desc, cast(hora as time) desc",
					bam, inicio, fim).list();
		}
	}
	
	public void inativar(int id) {
		String sql = "UPDATE prescricao set inativo= true  where idprescricao = :id_da_pre";
		entityManager.createNativeQuery(sql).setParameter("id_da_pre", id).executeUpdate();
	}
	
	public Prescricao filtarUltimaPoBam(int bam) {
		return find("bam = ?1  order by idprescricao desc", bam).firstResult();
	}

	// Somente as prescricoes do bam feitas pelo profissional autenticado (por
	// matricula; registros antigos sem matricula gravada caem no fallback por
	// nome do "medicoq").
	public List<Prescricao> listarMinhasPorBam(int bam, String matricula, String nome) {
		return find(
				"bam = ?1 and ((matriculaAutor is not null and matriculaAutor = ?2) or (matriculaAutor is null and medicoq = ?3)) order by cast(data as date) desc, cast(hora as time) desc",
				bam, matricula, nome).list();
	}

	public void prescricaoCont(int idprescricao, Prescricao dto) {
		boolean hor11 = dto.isHora11();
		boolean hor22 = dto.isHora22();
		boolean hor33 = dto.isHora33();
		boolean hor44 = dto.isHora44();
		boolean hor55 = dto.isHora55();
		boolean hor66 = dto.isHora66();
		boolean hor77 = dto.isHora77();
		boolean hor88 = dto.isHora88();

		String hor1 = dto.getHora1();
		String hor2 = dto.getHora2();
		String hor3 = dto.getHora3();
		String hor4 = dto.getHora4();
		String hor5 = dto.getHora5();
		String hor6 = dto.getHora6();
		String hor7 = dto.getHora7();
		String hor8 = dto.getHora8();
		String obs = dto.getObservacao();

		String sql = "UPDATE prescricao set hora11 = :hor11, hora22 = :hor22, hora33 = :hor33, hora44 = :hor44, hora55 = :hor55, hora66 = :hor66, hora77 = :hor77, hora88 = :hor88, hora1 = :hor1, hora2 = :hor2, hora3 = :hor3, hora4 = :hor4, hora5 = :hor5, hora6 = :hor6, hora7 = :hor7, hora8 = :hor8,observacao = :obs  where idprescricao = :idprescricao";
		entityManager.createNativeQuery(sql).setParameter("idprescricao", idprescricao).setParameter("hor11", hor11)
				.setParameter("hor22", hor22).setParameter("hor33", hor33).setParameter("hor44", hor44)
				.setParameter("hor55", hor55).setParameter("hor66", hor66).setParameter("hor77", hor77)
				.setParameter("hor88", hor88).setParameter("obs", obs).setParameter("hor1", hor1)
				.setParameter("hor2", hor2).setParameter("hor3", hor3).setParameter("hor4", hor4)
				.setParameter("hor5", hor5).setParameter("hor6", hor6).setParameter("hor7", hor7)
				.setParameter("hor8", hor8).executeUpdate();
	}

}

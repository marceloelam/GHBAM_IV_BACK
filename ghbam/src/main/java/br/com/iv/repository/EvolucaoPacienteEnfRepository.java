package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.EvolucaoPacienteEnf;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EvolucaoPacienteEnfRepository implements PanacheRepository<EvolucaoPacienteEnf> {

	public List<EvolucaoPacienteEnf> filtarPoBam(int bam, String inicio, String fim, String tipo) {
		if (tipo.equals("1")) {
			return find(
					"nbam = ?1 and cast(data as date) between cast(?2 as date) and cast(?3 as date) and (quem = 'ENFERMEIRO' or quem = 'TECNICO' or quem is null)   order by cast(data as date) asc, cast(hora as time) asc",
					bam, inicio, fim).list();
		} else {
			return find(
					"nbam = ?1 and cast(data as date) between cast(?2 as date) and cast(?3 as date) and (quem != 'ENFERMEIRO' and quem != 'TECNICO' and quem is not null) order by cast(data as date) desc, cast(hora as time) desc",
					bam, inicio, fim).list();
		}
	}

	// Bare, sem filtro de data/tipo: TODAS as evolucoes de enfermagem do bam.
	public List<EvolucaoPacienteEnf> filtarPoBamTodas(int bam) {
		return find("nbam = ?1 order by cast(data as date) desc, cast(hora as time) desc", bam).list();
	}

	// Bare, sem filtro de data, mas com filtro de tipo (enfermagem x
	// multidisciplinar): usado pela tela que carrega tudo do bam de uma vez,
	// deixando a ordenacao por conta do frontend.
	public List<EvolucaoPacienteEnf> filtarPoBamTodasPorTipo(int bam, String tipo) {
		if ("1".equals(tipo)) {
			return find(
					"nbam = ?1 and (quem = 'ENFERMEIRO' or quem = 'TECNICO' or quem is null) order by cast(data as date) desc, cast(hora as time) desc",
					bam).list();
		} else {
			return find(
					"nbam = ?1 and (quem != 'ENFERMEIRO' and quem != 'TECNICO' and quem is not null) order by cast(data as date) desc, cast(hora as time) desc",
					bam).list();
		}
	}
	public EvolucaoPacienteEnf filtarUltimaPoBam(int bam) {
		return find("nbam = ?1  order by idevolucao desc", bam).firstResult();
	}

	public EvolucaoPacienteEnf buscarPorId(int idevolucao) {
		return find("idevolucao = ?1", idevolucao).firstResult();
	}

	// Somente as evolucoes do bam feitas pelo profissional autenticado (por
	// matricula; registros antigos sem matricula gravada caem no fallback por
	// nome do "enfermeiro").
	public List<EvolucaoPacienteEnf> listarMinhasPorBam(int bam, String matricula, String nome) {
		return find(
				"nbam = ?1 and ((matriculaAutor is not null and matriculaAutor = ?2) or (matriculaAutor is null and enfermeiro = ?3)) order by cast(data as date) desc, cast(hora as time) desc",
				bam, matricula, nome).list();
	}

	// Ultima evolucao do bam feita pelo profissional autenticado (mesmo criterio
	// de autoria acima): a evolucao de um profissional nao pode ser importada
	// no editor de outro.
	public EvolucaoPacienteEnf buscarMinhaUltimaPorBam(int bam, String matricula, String nome) {
		return find(
				"nbam = ?1 and ((matriculaAutor is not null and matriculaAutor = ?2) or (matriculaAutor is null and enfermeiro = ?3)) order by idevolucao desc",
				bam, matricula, nome).firstResult();
	}

}

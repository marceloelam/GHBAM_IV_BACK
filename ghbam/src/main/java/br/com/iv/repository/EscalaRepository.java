package br.com.iv.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.iv.entity.Escala;
import br.com.iv.entity.EscalaPaciente;
import br.com.iv.entity.Pacientes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class EscalaRepository implements PanacheRepository<Escala> {
	@Inject
	EntityManager entityManager;

	public List<Escala> filtarEscalaMesAnoUnidade(int mes, int ano, String yunidade) {
		return find(
				"EXTRACT(MONTH FROM cast(inicioagenda as date)) = ?1 and EXTRACT(YEAR FROM cast(inicioagenda as date)) = ?2 and unidade = ?3 order by cast(inicioagenda as date), cast(horaescala as time) , setor",
				mes, ano, yunidade).list();
	}

	public List<Escala> filtarEscalaMesAnoMedicoUnidade(int mes, int ano, String nomemedico, String yunidade) {
		String nomeDoMedico = "%" + nomemedico.toUpperCase() + "%";
		return find(
				"EXTRACT(MONTH FROM cast(inicioagenda as date)) = ?1 and EXTRACT(YEAR FROM cast(inicioagenda as date)) = ?2 and unidade = ?3 and titulo like ?4 order by cast(inicioagenda as date), cast(horaescala as time) , setor",
				mes, ano, yunidade, nomeDoMedico).list();

	}

	@SuppressWarnings("unchecked")
	public List<Escala> filtarPorEspecialidade(String especialidade, String unidade) {
		String sql = "";
		String n_nome = especialidade.replaceAll("\\s", "");

		if (n_nome.equals("")) {
			sql = "SELECT p.*  FROM pacientes  where s.nome = 'SOU GRATO A TI ABA'";
			return getEntityManager().createNativeQuery(sql, EscalaPaciente.class).getResultList();
		} else {
			sql = "SELECT *  FROM escala WHERE setor like  '" + n_nome + "%' and unidade ='" + unidade
					+ "'  order by setor";
		}
		return getEntityManager().createNativeQuery(sql, Pacientes.class).getResultList();

	}

	@Transactional
	public void removerDiante(Long id, String data) {
		String sql = "delete from escala  where  servidor_idservidor = " + id
				+ " and Cast(inicioagenda as date) >= Cast('" + data + "' as date)";
		entityManager.createNativeQuery(sql).executeUpdate();

	}

	public List<Escala> filtarEscalaEspecialidade(String especialidade, String yunidade) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		String datas = data.format(formatador).replaceAll("/", "-");
		
		return find("setor like ?1 and unidade = ?2 and cast(inicioagenda as date) >= cast(?3 as date) order by setor", especialidade + "%", yunidade,datas ).list();
	}

}

package br.com.iv.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.Visitantes;

@ApplicationScoped
public class VisitantesRepository implements PanacheRepository<Visitantes> {
	public String datas;

	public long contarPorBamEData(String bam, String dataVisita) {
		return count("nbam = ?1 and dataVisita = ?2", bam, dataVisita);
	}
	
	public List<Visitantes> filtarPorBam(String bam) {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		return find("nbam = ?1 and  cast(to_char(to_date(dataVisita, 'DD/MM/YYYY'), 'YYYY-MM-DD') as date) = cast(?2 as date)  order by cast(dataVisita as date) desc", bam, datas).list();
	}

	public List<Visitantes> filtarPorPaciente(String query) {
		return find("upper(codigo) like ?1", "%" + query.toUpperCase() + "%").list();
	}

	public List<Visitantes> filtrarVisitantesPelaUnidadeEData(String query1, LocalDate d1, LocalDate d2) {
		LocalDate data1 = d1;
		LocalDate data2 = d2;
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");
		String dat1 = data1.format(formatador).toString();
		dat1 = dat1.replaceAll("/", "-");
		String dat2 = data2.format(formatador).toString();
		dat2 = dat2.replaceAll("/", "-");

		return find(
				"nunidade = ?1 and   cast(to_char(to_date(dataVisita, 'DD/MM/YYYY'), 'YYYY-MM-DD') as date) = cast(?2 as date) order by cast(dataVisita as date) desc",
				query1, dat1).list();
	}

}

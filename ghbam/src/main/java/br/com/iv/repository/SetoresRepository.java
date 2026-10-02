package br.com.iv.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import br.com.iv.dto.SetoresUnidadesDto;
import br.com.iv.entity.Setores;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;


@ApplicationScoped
public class SetoresRepository implements PanacheRepository<Setores> {
	@Inject
	EntityManager entityManager;
	
	
	@SuppressWarnings("unchecked")
	@Transactional
	public List<SetoresUnidadesDto> listAllUnidades() {
		LocalDate data = LocalDate.now();
		DateTimeFormatter formatador = DateTimeFormatter.ofPattern("yyyy/MM/dd");

		String datas = data.format(formatador).toString();
		datas = datas.replaceAll("-", "/");

		
		String sql = "SELECT DISTINCT  s.siglaunidade, s.setor, s.triar, u.hospital "
				+ "FROM setoresunidade s "
				+ "JOIN configurarunidade u ON s.siglaunidade = u.sigla "
				+ "ORDER BY s.siglaunidade, s.setor";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).getResultList();
		List<SetoresUnidadesDto> setoresUnidades = new ArrayList<>();

		for (Object[] result : results) {
			SetoresUnidadesDto dto = new SetoresUnidadesDto();
			dto.setSiglaunidade((String) result[0]);
			dto.setSetor((String) result[1]);	
			dto.setTriar((boolean) result[2]);
			dto.setHospital((String) result[3]);
			setoresUnidades.add(dto);
		}

		return setoresUnidades;
	}

	public List<Setores> filtarPorSetorEUnidade(String query, String query2){
		return find("setor like ?1 and siglaunidade = ?2  order by setor", query + "%", query2).list();
	}
	public List<Setores> filtarPorUnidadeDoSetor(String query){
		return find("siglaunidade like ?1 order by setor", query + "%").list();
	}
	
	public List<Setores> filtarPorUnidadeDoSetorTriar(String query){
		return find("siglaunidade like ?1 and  triar = true order by setor", query + "%").list();
	}
	
	public void alterarSiglasDasUnidadesNosSetores(String siglaUnidadeAntiga, String siglaUnidadeAlterada) {
		System.out.println(siglaUnidadeAntiga);
		System.out.println(siglaUnidadeAlterada);
		
		String sql = "UPDATE setoresunidade SET siglaunidade = :novoNome WHERE siglaunidade = :nomeAntigo";
		entityManager.createNativeQuery(sql)
                .setParameter("novoNome", siglaUnidadeAlterada)
                .setParameter("nomeAntigo", siglaUnidadeAntiga)
                .executeUpdate(); 
		System.out.println(sql);
	}
	
	
	public List<Setores> listarUnidadesDistintosDoSetor() {
	   // String sql = "SELECT DISTINCT on (siglaunidade) siglaunidade FROM setoresunidade";
	   // return entityManager.createNativeQuery(sql).getResultList();
		// String hql = "SELECT DISTINCT (u.siglaunidade) u.siglaunidade FROM Setores u";
		// return entityManager.createQuery(hql, Setores.class).getResultList();
		 return list("SELECT DISTINCT s.siglaunidade FROM Setores s order by s.siglaunidade");  
	}
	
}

package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.LocalCenso;
import br.com.iv.entity.Log;




@ApplicationScoped
public class LogRepository implements PanacheRepository<Log> {
	
	public List<Log> filtarPorCbo(String query) {
	return find("upper(titulo) like ?1", "%" +query.toUpperCase() + "%").list();
}

	public List<Log> filtarPorCboCodigo(String query) {
	return find("upper(codigo) like ?1", "%" +query.toUpperCase() + "%").list();
}
	
	public List<Log> filtarPorPesquisa(String query, String unidade) {
		if (query.equals("nda.")) {
			return find("unidade = ?1 order by data desc, cast(hora as time) desc limit 400", unidade).list();
		}
		return find("upper(evento) like ?1 and unidade = ?2 order by data desc, cast(hora as time) desc  limit 400", "%" + query.toUpperCase() + "%", unidade).list();
	}

	public List<Log> filtarPorData(String query, String unidade) {
		return find("cast(data as string) like ?1 and unidade = ?2 order by data desc, cast(hora as time) desc", "%" + query.toUpperCase() + "%", unidade).list();
	}
	
}

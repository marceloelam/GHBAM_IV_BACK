package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.Drive;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DriveRepository implements PanacheRepository<Drive> {

	public List<Drive> listarPorUnidade(String unidade) {
		return find("unidade", Sort.by("titulo"), unidade).list();
	}

	public List<Drive> listarPorMenuEUnidade(String menu, String unidade) {
		return find("menu = ?1 and unidade = ?2", Sort.by("titulo"), menu, unidade).list();
	}
}

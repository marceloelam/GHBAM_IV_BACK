package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.MultidisciplinarPts;

@ApplicationScoped
public class MultidisciplinarPtsRepository implements PanacheRepository<MultidisciplinarPts> {

	public List<MultidisciplinarPts> listarPorPaciente(int idpaciente) {
		return list("idpaciente = ?1 order by criadoEm desc", idpaciente);
	}

}

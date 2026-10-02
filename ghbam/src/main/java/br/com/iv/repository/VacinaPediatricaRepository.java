package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.VacinaPediatrica;

@ApplicationScoped
public class VacinaPediatricaRepository implements PanacheRepository<VacinaPediatrica> {

	public List<VacinaPediatrica> listarPorPaciente(int idpaciente) {
		return list("idpaciente = ?1 order by vacina, dataAplicacao", idpaciente);
	}

}

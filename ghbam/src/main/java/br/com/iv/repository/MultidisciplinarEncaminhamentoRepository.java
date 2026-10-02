package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.MultidisciplinarEncaminhamento;

@ApplicationScoped
public class MultidisciplinarEncaminhamentoRepository implements PanacheRepository<MultidisciplinarEncaminhamento> {

	public List<MultidisciplinarEncaminhamento> listarPorPaciente(int idpaciente) {
		return list("idpaciente = ?1 order by criadoEm desc", idpaciente);
	}

	public List<MultidisciplinarEncaminhamento> listarPorCategoriaDestino(String categoriaDestino, String status) {
		if (status != null && !status.isBlank()) {
			return list("categoriaDestino = ?1 and status = ?2 order by criadoEm desc", categoriaDestino, status);
		}
		return list("categoriaDestino = ?1 order by criadoEm desc", categoriaDestino);
	}

}

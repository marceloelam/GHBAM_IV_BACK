package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

import br.com.iv.dto.CboDto;
import br.com.iv.entity.Bulario;
import br.com.iv.entity.Cbo;
import br.com.iv.repository.BularioRepository;
import br.com.iv.repository.CboRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BularioService implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@Inject
	BularioRepository bularioRepository;

	
	public List<Bulario> filtarBulario(String ynome) {
		List<Bulario> bulario = bularioRepository.filtarBulario(ynome);
		if (bulario.isEmpty()) {
			System.out.println("Esse pedido não foi encontrado...");
		}
		return bulario;
	}
	
}

package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.SalasDto;
import br.com.iv.entity.Salas;
import br.com.iv.repository.SalasRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SalasService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	SalasRepository salasRepository;

	// Listando todas as salas
	public List<Salas> listarTodasSalas() {
		return salasRepository.listAll(Sort.by("sala"));
	}

	@Transactional
	public List<Salas> filtarPorSalas(String unidade, String local, SalasDto dto) {

		List<Salas> salaOp = salasRepository.filtarPorSala(unidade, local);
		if (salaOp.isEmpty()) {
			System.out.println("Essas salas não foram encontradas...");
		}
		return salaOp;
	}
	
	
	@Transactional
	public List<Salas> filtarPorSalaunidade(String unidade, String local, SalasDto dto) {

		List<Salas> salaOp = salasRepository.filtarPorSalaunidade(unidade, local);
		if (salaOp.isEmpty()) {
			System.out.println("Essas salas não foram encontradas...");
		}
		return salaOp;
	}
	
	
	

	@Transactional
	public List<Salas> filtarPorSalas(String unidade, SalasDto dto) {

		List<Salas> salaOp = salasRepository.filtarPorSala(unidade);
		if (salaOp.isEmpty()) {
			System.out.println("Essas salas não foram encontradas...");
		}
		return salaOp;
	}

	// Salvar
	@Transactional
	public Salas salvarSala(SalasDto dto) {
		Salas salas = new Salas();
		salas.setSala(dto.getSala());
		salas.setLocal(dto.getLocal());
		salas.setUnidade(dto.getUnidade());
		salas.setP1(dto.isP1());
		salas.setP2(dto.isP2());
		salas.setP3(dto.isP3());
		salas.setP4(dto.isP4());
		salas.setP5(dto.isP5());
		salas.setP6(dto.isP6());
		salas.setP7(dto.isP7());
		salas.setP8(dto.isP8());
		salas.setP9(dto.isP9());
		salas.setP10(dto.isP10());
		salas.persist();
		return salas;
	}

	@Transactional
	public void atualizarSala(int id, SalasDto dto) {
		Salas salas = Salas.findById(id);
		if (salas == null) {
			throw new jakarta.ws.rs.NotFoundException("Sala com id " + id + " não encontrada");
		}

		salas.setSala(dto.getSala());
		salas.setUnidade(dto.getUnidade());
		salas.setLocal(dto.getLocal());
		salas.setP1(dto.isP1());
		salas.setP2(dto.isP2());
		salas.setP3(dto.isP3());
		salas.setP4(dto.isP4());
		salas.setP5(dto.isP5());
		salas.setP6(dto.isP6());
		salas.setP7(dto.isP7());
		salas.setP8(dto.isP8());
		salas.setP9(dto.isP9());
		salas.setP10(dto.isP10());
	}

	@Transactional
	public void removeSala(int id) {
		Optional<Salas> salaOp = Salas.findByIdOptional(id);
		if (salaOp.isEmpty()) {
			throw new jakarta.ws.rs.NotFoundException("Sala com id " + id + " não encontrada");
		}

		try {
			salaOp.get().delete();
		} catch (org.hibernate.jdbc.BatchedTooManyRowsAffectedException e) {
			throw new RuntimeException("Erro de integridade no banco de dados: existem registros duplicados para a sala " + id + ". Verifique a tabela 'salas' no banco.", e);
		} catch (Exception e) {
			throw new RuntimeException("Não foi possível remover a sala " + id + ". Ela pode estar referenciada em outros registros.", e);
		}
	}

}

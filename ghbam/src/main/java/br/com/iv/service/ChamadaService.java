package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import br.com.iv.dto.ChamadaDto;
import br.com.iv.entity.ChamadaPaciente;
import br.com.iv.repository.ChamadaRepository;

@ApplicationScoped
public class ChamadaService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	EntityManager entityManager;

	@Inject
	ChamadaRepository chamadaRepository;

	public List<ChamadaPaciente> listarultimo(String unidade, String tv) {
		return chamadaRepository.filtarultimo(unidade, tv);
	}

	public List<ChamadaPaciente> listarseisultimos(String unidade, String tv) {
		return chamadaRepository.filtarseisultimos(unidade, tv);
	}
	
	public List<ChamadaPaciente> filtarultimoDireita(String unidade, String tv) {
		return chamadaRepository.filtarultimoDireita(unidade, tv);
	}

	// Salvar
	@Transactional
	public ChamadaPaciente salvarChamada(ChamadaDto dto) {
		ChamadaPaciente chamada = new ChamadaPaciente();
		chamada.setNome(dto.getNome());
		chamada.setLocal(dto.getLocal());
		chamada.setChamou(dto.isChamou());
		chamada.setChamou2(dto.isChamou2());
		chamada.setData(dto.getData());
		chamada.setHora(dto.getHora());
		chamada.setSetor(dto.getSetor());
		chamada.setUnidade(dto.getUnidade());
		chamada.setPacienteespecial(dto.getPacienteespecial());

		chamada.setP1(dto.isP1());
		chamada.setP2(dto.isP2());
		chamada.setP3(dto.isP3());
		chamada.setP4(dto.isP4());
		chamada.setP5(dto.isP5());
		chamada.setP6(dto.isP6());
		chamada.setP7(dto.isP7());
		chamada.setP8(dto.isP8());
		chamada.setP9(dto.isP9());
		chamada.setP10(dto.isP10());
		chamada.persist();
		//entityManager.persist(chamada);
		//entityManager.flush();
		return chamada;
	}

	@Transactional
	public void removeChamada(String unidade, String tv) {
		chamadaRepository.removerOprimeiro(unidade, tv);
	}

	@Transactional
	public void atualizarChamada(int idchamada, String tv) {
		chamadaRepository.atualizarChamada(idchamada, tv);
	}
	
}

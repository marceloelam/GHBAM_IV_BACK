package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.DriveDto;
import br.com.iv.entity.Drive;
import br.com.iv.repository.DriveRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class DriveService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	DriveRepository driveRepository;

	public List<Drive> listarTodos() {
		return driveRepository.listAll(Sort.by("titulo"));
	}

	public List<Drive> listarPorUnidade(String unidade) {
		return driveRepository.listarPorUnidade(unidade);
	}

	public List<Drive> listarPorMenuEUnidade(String menu, String unidade) {
		return driveRepository.listarPorMenuEUnidade(menu, unidade);
	}

	@Transactional
	public Drive salvar(DriveDto dto) {
		Drive drive = new Drive();
		drive.setTitulo(dto.getTitulo());
		drive.setDescricao(dto.getDescricao());
		drive.setUrl(dto.getUrl());
		drive.setMenu(dto.getMenu());
		drive.setUnidade(dto.getUnidade());
		drive.persist();
		return drive;
	}

	@Transactional
	public void atualizar(int id, DriveDto dto) {
		Drive drive = Drive.findById(id);
		if (drive == null) {
			throw new NotFoundException("Drive com id " + id + " não encontrado");
		}

		drive.setTitulo(dto.getTitulo());
		drive.setDescricao(dto.getDescricao());
		drive.setUrl(dto.getUrl());
		drive.setMenu(dto.getMenu());
		drive.setUnidade(dto.getUnidade() != null ? dto.getUnidade() : drive.getUnidade());
	}

	@Transactional
	public void remover(int id) {
		Optional<Drive> driveOp = Drive.findByIdOptional(id);
		if (driveOp.isEmpty()) {
			throw new NotFoundException("Drive com id " + id + " não encontrado");
		}

		try {
			driveOp.get().delete();
		} catch (Exception e) {
			throw new RuntimeException("Não foi possível remover o drive " + id + ".", e);
		}
	}
}

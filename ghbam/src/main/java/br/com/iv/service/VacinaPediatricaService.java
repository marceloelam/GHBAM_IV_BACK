package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;

import br.com.iv.dto.VacinaPediatricaDto;
import br.com.iv.entity.VacinaPediatrica;
import br.com.iv.repository.VacinaPediatricaRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class VacinaPediatricaService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	VacinaPediatricaRepository vacinaPediatricaRepository;

	public List<VacinaPediatrica> listarPorPaciente(int idpaciente) {
		return vacinaPediatricaRepository.listarPorPaciente(idpaciente);
	}

	@Transactional
	public VacinaPediatrica salvar(VacinaPediatricaDto dto, AuthenticatedUser usuario) {
		VacinaPediatrica vacina = new VacinaPediatrica();
		copiarDtoParaEntity(dto, vacina);
		vacina.setCriadoPorMatricula(usuario == null ? "" : usuario.matricula());
		vacina.persist();
		return vacina;
	}

	@Transactional
	public void atualizar(int id, VacinaPediatricaDto dto, AuthenticatedUser usuario) {
		Optional<VacinaPediatrica> vacinaOp = VacinaPediatrica.findByIdOptional(id);
		VacinaPediatrica vacina = vacinaOp.orElseThrow(() -> new NullPointerException("Registro de vacina não encontrado."));
		validarPermissaoAlteracao(vacina, usuario);
		copiarDtoParaEntity(dto, vacina);
		vacina.persist();
	}

	@Transactional
	public void excluir(int id, AuthenticatedUser usuario) {
		Optional<VacinaPediatrica> vacinaOp = VacinaPediatrica.findByIdOptional(id);
		VacinaPediatrica vacina = vacinaOp.orElseThrow(() -> new NullPointerException("Registro de vacina não encontrado."));
		validarPermissaoAlteracao(vacina, usuario);
		vacina.delete();
	}

	private void validarPermissaoAlteracao(VacinaPediatrica vacina, AuthenticatedUser usuario) {
		String matricula = usuario == null ? "" : usuario.matricula();
		String matriculaCriador = vacina.getCriadoPorMatricula();
		if (matricula.isBlank() || matriculaCriador == null || !matricula.equals(matriculaCriador)) {
			throw new ForbiddenException("Apenas quem registrou esta vacina pode altera-la ou exclui-la.");
		}
		LocalDate hoje = LocalDate.now();
		LocalDate diaCriacao = vacina.getCriadoEm() == null
				? null
				: vacina.getCriadoEm().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		if (diaCriacao == null || !diaCriacao.equals(hoje)) {
			throw new IllegalArgumentException("Este registro so pode ser alterado ou excluido no mesmo dia em que foi criado.");
		}
	}

	private void copiarDtoParaEntity(VacinaPediatricaDto dto, VacinaPediatrica vacina) {
		vacina.setIdpaciente(dto.getIdpaciente());
		vacina.setVacina(dto.getVacina());
		vacina.setDose(dto.getDose());
		vacina.setIdadeAplicacao(dto.getIdadeAplicacao());
		vacina.setObservacao(dto.getObservacao());
		vacina.setMedico(dto.getMedico());
		vacina.setCrm(dto.getCrm());
		if (dto.getDataAplicacao() != null && !dto.getDataAplicacao().isBlank()) {
			LocalDate data = LocalDate.parse(dto.getDataAplicacao().substring(0, 10));
			vacina.setDataAplicacao(Date.from(data.atStartOfDay(ZoneId.systemDefault()).toInstant()));
		} else {
			vacina.setDataAplicacao(null);
		}
	}

}

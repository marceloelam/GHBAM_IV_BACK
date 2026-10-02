package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

import br.com.iv.dto.PrescricaoDto;
import br.com.iv.entity.Prescricao;
import br.com.iv.repository.PrescricaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PrescricaoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	PrescricaoRepository prescricaoRepository;

	@Inject
	AssinaturaClinicaService assinaturaClinicaService;
	
	@Transactional
	public Prescricao filtarPorId(int id) {
	    return prescricaoRepository.findById(id);
	}


	@Transactional
	public List<Prescricao> filtarPorBam(int bam, String inicio, String fim) {
		return prescricaoRepository.filtarPoBam(bam, inicio, fim);
	}

	@Transactional
	public List<Prescricao> filtarPorBam(int bam, String inicio, String fim, String tipo) {
		return prescricaoRepository.filtarPoBam(bam, inicio, fim, tipo);
	}

	@Transactional
	public List<Prescricao> filtarPorBam2(int bam) {
		return prescricaoRepository.filtarPoBam2(bam);
	}
	
	@Transactional
	public void inativar(int id) {
		validarPrescricaoAberta(id);
		prescricaoRepository.inativar(id);
	}

	
	@Transactional
	public Prescricao filtarUltimaPorBam(int bam) {
		return prescricaoRepository.filtarUltimaPoBam(bam);
	}
	
	@Transactional
	public void prescricao(int id, Prescricao dto) {
		validarPrescricaoAberta(id);
		prescricaoRepository.prescricaoCont(id, dto);
	}

	private void validarPrescricaoAberta(int id) {
		Prescricao prescricao = prescricaoRepository.findById(id);
		if (prescricao == null) {
			throw new IllegalArgumentException("Prescricao nao localizada.");
		}
		if (assinaturaClinicaService.bloqueadoPorBam(
				AssinaturaClinicaService.PRESCRICAO_ESTRUTURADA_BAM,
				prescricao.getBam())) {
			throw new IllegalStateException("Prescricao assinada nao pode ser alterada. Registre um adendo.");
		}
		if (assinaturaClinicaService.bloqueadoPorDocumento(
				AssinaturaClinicaService.PRESCRICAO_MEDICA,
				id)) {
			throw new IllegalStateException("Prescricao assinada nao pode ser alterada. Registre um adendo.");
		}
	}


	// Salvar
	@Transactional
	public Prescricao salvarPrescricao(PrescricaoDto dto, br.com.iv.security.AuthenticatedUser usuario) {
		Prescricao prescricao = new Prescricao();
		prescricao.setMedicacao(dto.getMedicacao());
		prescricao.setPrescricao(dto.getPrescricao());
		prescricao.setObservacao2(dto.getObservacao2());
		prescricao.setTprpa(dto.getTprpa());
		prescricao.setCness(dto.getCness());
		prescricao.setMediccrm(dto.getMediccrm());
		prescricao.setMedicoq(dto.getMedicoq());
		prescricao.setBam(dto.getBam());
		prescricao.setData(dto.getData());
		prescricao.setHora(dto.getHora());
		prescricao.setLocalsolicitante(dto.getLocalsolicitante());
		prescricao.setFeito(dto.isFeito());
		// Aprazamento
		prescricao.setHora1(dto.getHora1());
		prescricao.setHora11(dto.isHora11());
		prescricao.setHora2(dto.getHora2());
		prescricao.setHora22(dto.isHora22());
		prescricao.setHora3(dto.getHora3());
		prescricao.setHora33(dto.isHora33());
		prescricao.setHora4(dto.getHora4());
		prescricao.setHora44(dto.isHora44());
		prescricao.setHora5(dto.getHora5());
		prescricao.setHora55(dto.isHora55());
		prescricao.setHora6(dto.getHora6());
		prescricao.setHora66(dto.isHora66());
		prescricao.setHora7(dto.getHora7());
		prescricao.setHora77(dto.isHora77());
		prescricao.setHora8(dto.getHora8());
		prescricao.setHora88(dto.isHora88());
		prescricao.setMedicamentopedido(dto.getMedicamentopedido());
		prescricao.setRetirada(dto.getRetirada());
		prescricao.setFarmfez(dto.isFarmfez());
		prescricao.setObservacao(dto.getObservacao());
		prescricao.setObservacao3(dto.getObservacao3());
		prescricao.setQuem(dto.getQuem());

		// Ajuste para garantir a data correta
		// LocalDate dataAtual = LocalDate.now();
		// Prescricao.setData(dataAtual);

		prescricao.setMatriculaAutor(usuario == null ? null : safe(usuario.matricula()));

		prescricao.persist();
		return prescricao;
	}

	public List<Prescricao> listarMinhasPorBam(int bam, br.com.iv.security.AuthenticatedUser usuario) {
		return prescricaoRepository.listarMinhasPorBam(bam, safe(usuario.matricula()), safe(usuario.nome()));
	}

	// Retificacao: gera uma NOVA linha com o conteudo corrigido e marca a
	// original como substituida. Mesmas regras da retificacao de evolucao:
	// - somente quem criou o registro original pode retifica-lo;
	// - so pode retificar dentro de 4h do horario gravado no registro original;
	// - um registro so pode ser retificado uma vez.
	@Transactional
	public Prescricao retificar(int idOrigem, PrescricaoDto dto, br.com.iv.security.AuthenticatedUser usuario) {
		Prescricao original = prescricaoRepository.findById(idOrigem);
		if (original == null) {
			throw new IllegalArgumentException("Prescricao original nao encontrada");
		}

		String matriculaUsuario = safe(usuario == null ? null : usuario.matricula());
		String matriculaOriginal = safe(original.getMatriculaAutor());
		boolean mesmoAutor = !matriculaOriginal.isBlank()
				? matriculaOriginal.equalsIgnoreCase(matriculaUsuario)
				: safe(original.getMedicoq()).equalsIgnoreCase(safe(usuario == null ? null : usuario.nome()));
		if (!mesmoAutor) {
			throw new SecurityException("Somente o profissional que registrou esta prescricao pode retifica-la");
		}
		if (original.getIdPrescricaoRetificacao() != null) {
			throw new IllegalStateException("Esta prescricao ja foi retificada anteriormente");
		}
		if (original.isInativo()) {
			throw new IllegalStateException("Prescricao inativada nao pode ser retificada");
		}
		if (!dentroDoPrazoDeRetificacao(original.getData(), original.getHora())) {
			throw new IllegalStateException("O prazo de " + PRAZO_RETIFICACAO_HORAS + " horas para retificar esta prescricao ja expirou");
		}
		String motivo = safe(dto == null ? null : dto.getMotivoRetificacao());
		if (motivo.isBlank()) {
			throw new IllegalArgumentException("Informe o motivo da retificacao");
		}

		Prescricao nova = new Prescricao();
		nova.setMedicacao(dto.getMedicacao());
		nova.setPrescricao(dto.getPrescricao());
		nova.setPrescricaopuro(dto.getPrescricaopuro());
		nova.setObservacao2(dto.getObservacao2());
		nova.setTprpa(dto.getTprpa());
		nova.setCness(dto.getCness());
		nova.setMediccrm(dto.getMediccrm());
		nova.setMedicoq(dto.getMedicoq());
		nova.setBam(dto.getBam());
		nova.setData(dto.getData());
		nova.setHora(dto.getHora());
		nova.setLocalsolicitante(dto.getLocalsolicitante());
		nova.setFeito(dto.isFeito());
		nova.setMedicamentopedido(dto.getMedicamentopedido());
		nova.setRetirada(dto.getRetirada());
		nova.setFarmfez(dto.isFarmfez());
		nova.setObservacao(dto.getObservacao());
		nova.setObservacao3(dto.getObservacao3());
		nova.setQuem(dto.getQuem());
		nova.setVersao((original.getVersao() == null ? 1 : original.getVersao()) + 1);
		nova.setIdPrescricaoOrigem(original.getIdprescricao());
		nova.setMotivoRetificacao(motivo);
		nova.setMatriculaAutor(matriculaUsuario);
		nova.persist();

		original.setIdPrescricaoRetificacao(nova.getIdprescricao());
		original.persist();

		return nova;
	}

	private static final int PRAZO_RETIFICACAO_HORAS = 4;

	private boolean dentroDoPrazoDeRetificacao(String data, String hora) {
		if (data == null || data.isBlank() || hora == null || hora.isBlank()) {
			return false;
		}
		try {
			java.time.LocalDateTime criadoEm = java.time.LocalDateTime.of(
					java.time.LocalDate.parse(data.length() > 10 ? data.substring(0, 10) : data),
					java.time.LocalTime.parse(hora));
			return java.time.Duration.between(criadoEm, java.time.LocalDateTime.now()).toMinutes() <= PRAZO_RETIFICACAO_HORAS * 60L;
		} catch (Exception e) {
			return false;
		}
	}

	private String safe(String valor) {
		return valor == null ? "" : valor.trim();
	}

}

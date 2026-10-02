package br.com.iv.service;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import jakarta.inject.Inject;

import br.com.iv.dto.EvolucaoPacienteEnfDto;
import br.com.iv.entity.EvolucaoPacienteEnf;
import br.com.iv.repository.EvolucaoPacienteEnfRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class EvolucaoPacienteEnfService implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final int PRAZO_RETIFICACAO_HORAS = 4;

	@Inject
	EvolucaoPacienteEnfRepository evolucaoPacienteEnfRepository;

	@Transactional
	public List<EvolucaoPacienteEnf> filtarPorBam(int bam, String inicio, String fim, String tipo) {
		return evolucaoPacienteEnfRepository.filtarPoBam(bam, inicio, fim, tipo);
	}

	@Transactional
	public List<EvolucaoPacienteEnf> filtarTodasPorBam(int bam) {
		return evolucaoPacienteEnfRepository.filtarPoBamTodas(bam);
	}

	@Transactional
	public List<EvolucaoPacienteEnf> filtarTodasPorBamETipo(int bam, String tipo) {
		return evolucaoPacienteEnfRepository.filtarPoBamTodasPorTipo(bam, tipo);
	}
	public EvolucaoPacienteEnf filtarUltimaPorBam(int bam) {
		return evolucaoPacienteEnfRepository.filtarUltimaPoBam(bam);
	}

	public EvolucaoPacienteEnf buscarMinhaUltimaPorBam(int bam, AuthenticatedUser usuario) {
		return evolucaoPacienteEnfRepository.buscarMinhaUltimaPorBam(bam, safe(usuario.matricula()), safe(usuario.nome()));
	}

	public List<EvolucaoPacienteEnf> listarMinhasPorBam(int bam, AuthenticatedUser usuario) {
		return evolucaoPacienteEnfRepository.listarMinhasPorBam(bam, safe(usuario.matricula()), safe(usuario.nome()));
	}

	// Salvar
	@Transactional
	public EvolucaoPacienteEnf salvarEvolucao(EvolucaoPacienteEnfDto dto, AuthenticatedUser usuario) {
	    EvolucaoPacienteEnf evolucaoPacienteEnf = new EvolucaoPacienteEnf();

	    evolucaoPacienteEnf.setLeito(dto.getLeito());
	    //evolucaoPacienteEnf.setData(dto.getData());
	    evolucaoPacienteEnf.setProntuario(dto.getProntuario());
	    evolucaoPacienteEnf.setRelato(dto.getRelato());
	    evolucaoPacienteEnf.setEnfermaria(dto.getEnfermaria());
	    evolucaoPacienteEnf.setEnfermeiro(dto.getEnfermeiro());
	    evolucaoPacienteEnf.setNbam(dto.getNbam());
	    evolucaoPacienteEnf.setHora(dto.getHora());
	    evolucaoPacienteEnf.setQuem(dto.getQuem());
	    evolucaoPacienteEnf.setPedido(dto.getPedido());
	    evolucaoPacienteEnf.setPedidolimpo(dto.getPedidolimpo());
	    evolucaoPacienteEnf.setLocal(dto.getLocal());
	    evolucaoPacienteEnf.setMedico(dto.getMedico());
	    evolucaoPacienteEnf.setCrm(dto.getCrm());
	    evolucaoPacienteEnf.setPedidodomed(dto.getPedidodomed());
	    evolucaoPacienteEnf.setUnidade(dto.getUnidade());
	    evolucaoPacienteEnf.setMatriculaAutor(usuario == null ? null : safe(usuario.matricula()));

	    // Ajuste para garantir a data correta
	    LocalDate dataAtual = LocalDate.now();
	    evolucaoPacienteEnf.setData(dataAtual);

	    evolucaoPacienteEnf.persist();
	    return evolucaoPacienteEnf;
	}

	// Retificacao: gera uma NOVA linha com o conteudo corrigido e marca a
	// original como substituida. Regras de negocio (nao apenas de UI):
	// - somente quem criou o registro original pode retifica-lo;
	// - so pode retificar dentro de 4h do horario gravado no registro original;
	// - um registro so pode ser retificado uma vez (a retificacao da
	//   retificacao e feita chamando este mesmo metodo tendo a retificacao
	//   anterior como "original").
	@Transactional
	public EvolucaoPacienteEnf retificar(int idOrigem, EvolucaoPacienteEnfDto dto, AuthenticatedUser usuario) {
		EvolucaoPacienteEnf original = evolucaoPacienteEnfRepository.buscarPorId(idOrigem);
		if (original == null) {
			throw new IllegalArgumentException("Evolucao original nao encontrada");
		}

		String matriculaUsuario = safe(usuario == null ? null : usuario.matricula());
		String matriculaOriginal = safe(original.getMatriculaAutor());
		boolean mesmoAutor = !matriculaOriginal.isBlank()
				? matriculaOriginal.equalsIgnoreCase(matriculaUsuario)
				: safe(original.getEnfermeiro()).equalsIgnoreCase(safe(usuario == null ? null : usuario.nome()));
		if (!mesmoAutor) {
			throw new SecurityException("Somente o profissional que registrou esta evolucao pode retifica-la");
		}
		if (original.getIdEvolucaoRetificacao() != null) {
			throw new IllegalStateException("Esta evolucao ja foi retificada anteriormente");
		}
		if (!dentroDoPrazoDeRetificacao(original.getData(), original.getHora())) {
			throw new IllegalStateException("O prazo de " + PRAZO_RETIFICACAO_HORAS + " horas para retificar esta evolucao ja expirou");
		}
		String motivo = safe(dto == null ? null : dto.getMotivoRetificacao());
		if (motivo.isBlank()) {
			throw new IllegalArgumentException("Informe o motivo da retificacao");
		}

		EvolucaoPacienteEnf nova = new EvolucaoPacienteEnf();
		nova.setLeito(dto.getLeito());
		nova.setProntuario(dto.getProntuario());
		nova.setRelato(dto.getRelato());
		nova.setEnfermaria(dto.getEnfermaria());
		nova.setEnfermeiro(dto.getEnfermeiro());
		nova.setNbam(dto.getNbam());
		nova.setHora(dto.getHora());
		nova.setQuem(dto.getQuem());
		nova.setPedido(dto.getPedido());
		nova.setPedidolimpo(dto.getPedidolimpo());
		nova.setLocal(dto.getLocal());
		nova.setMedico(dto.getMedico());
		nova.setCrm(dto.getCrm());
		nova.setPedidodomed(dto.getPedidodomed());
		nova.setUnidade(dto.getUnidade());
		nova.setData(LocalDate.now());
		nova.setVersao((original.getVersao() == null ? 1 : original.getVersao()) + 1);
		nova.setIdEvolucaoOrigem(original.getIdevolucao());
		nova.setMotivoRetificacao(motivo);
		nova.setMatriculaAutor(matriculaUsuario);
		nova.persist();

		original.setIdEvolucaoRetificacao(nova.getIdevolucao());
		original.persist();

		return nova;
	}

	private boolean dentroDoPrazoDeRetificacao(LocalDate data, String hora) {
		if (data == null || hora == null || hora.isBlank()) {
			return false;
		}
		try {
			LocalDateTime criadoEm = LocalDateTime.of(data, LocalTime.parse(hora));
			return Duration.between(criadoEm, LocalDateTime.now()).toMinutes() <= PRAZO_RETIFICACAO_HORAS * 60L;
		} catch (Exception e) {
			return false;
		}
	}

	private String safe(String valor) {
		return valor == null ? "" : valor.trim();
	}

}

package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.LogDto;
import br.com.iv.entity.LocalCenso;
import br.com.iv.entity.Log;
import br.com.iv.repository.LogRepository;
import br.com.iv.security.AuditPacienteResolver;
import br.com.iv.security.AuthenticatedUser;
import org.jboss.resteasy.reactive.server.core.CurrentRequestManager;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;

@ApplicationScoped
public class LogService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	LogRepository logRepository;

	@Inject
	LogAsyncProcessor logAsyncProcessor;

	@Inject
	AuditPacienteResolver auditPacienteResolver;

	private ExecutorService logExecutor;
	private final AtomicLong deadLetterSequence = new AtomicLong();

	@PostConstruct
	public void init() {
		// Limit queued audit events so a database outage cannot grow memory without bound.
		this.logExecutor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(2000), new ThreadPoolExecutor.CallerRunsPolicy());
	}

	@Transactional
	public List<Log> filtarPorLog(String pesquisa, String unidade) {

		List<Log> localCensoOp = logRepository.filtarPorPesquisa(pesquisa, unidade);
		if (localCensoOp.isEmpty()) {
			System.out.println("Esses logs não foram encontrados...");
		}
		return localCensoOp;
	}

	@Transactional
	public List<Log> filtarPorLogData(LocalDate data, String unidade) {

		List<Log> localCensoOp = logRepository.filtarPorData(data, unidade);
		if (localCensoOp.isEmpty()) {
			System.out.println("Esses logs não foram encontrados...");
		}
		return localCensoOp;
	}

	@PreDestroy
	public void shutdown() {
		if (this.logExecutor != null) {
			this.logExecutor.shutdown();
			try {
				this.logExecutor.awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	// Listando todos os log
	public List<Log> listarTodosLogs() {
		return logRepository.listAll();
	}

	// Salvar
	@Transactional
	public Log salvarLog(LogDto dto) {
		if (dto == null) {
			throw new IllegalArgumentException("LogDto não pode ser nulo");
		}
		anexarUsuario(dto);
		Log log = new Log();
		log.setData(dto.getData());
		log.setHora(dto.getHora());
		log.setEvento(dto.getEvento());
		log.setDados(dto.getDados());
		log.setUnidade(dto.getUnidade());
		log.persist();
		return log;
	}

	/**
	 * Enfileira gravação do log de forma assíncrona. Retorna imediatamente com um
	 * objeto parcial (sem id). Use este endpoint quando a latência do cliente for
	 * prioritária.
	 */
	public Log salvarLogAsync(final LogDto dto) {
		if (dto == null) {
			throw new IllegalArgumentException("LogDto não pode ser nulo");
		}
		// A requisicao so existe nesta thread; no executor ela ja se perdeu.
		anexarUsuario(dto);
		// build a Log object to return quickly (no id)
		Log log = new Log();
		log.setData(dto.getData());
		log.setHora(dto.getHora());
		log.setEvento(dto.getEvento());
		log.setDados(dto.getDados());
		log.setUnidade(dto.getUnidade());

		logExecutor.submit(() -> {
			anexarPaciente(dto);
			try {
				logAsyncProcessor.persistLog(dto);
			} catch (Exception e) {
				// Keep only operational metadata here; event text may contain patient PHI.
				try {
					Path dir = Paths.get("logs");
					if (!Files.exists(dir)) {
						Files.createDirectories(dir);
					}
					String line = String.format("falha=%d;tipo=%s;data=%s%n", deadLetterSequence.incrementAndGet(),
							e.getClass().getSimpleName(), dto.getData());
					Files.writeString(dir.resolve("deadletter-logs.txt"), line, StandardOpenOption.CREATE,
							StandardOpenOption.APPEND);
				} catch (Exception ex) {
					System.err.println("Failed to write dead-letter log: " + ex.getMessage());
				}
			}
		});

		return log;
	}

	// Servicos que gravam log direto (farmacia, CDSS, RAG, cirurgia...) muitas vezes
	// so poem a matricula ou nem isso. Quem fez a acao e o usuario autenticado da
	// requisicao (JWT), entao ele e anexado como "usuario=... | matricula=...", o
	// mesmo formato do POST /log. Sem requisicao (jobs) o evento fica como veio.
	void anexarUsuario(LogDto dto) {
		try {
			AuthenticatedUser user = usuarioDaRequisicao();
			if (user == null || user.matricula() == null || user.matricula().isBlank()) {
				return;
			}
			String evento = dto.getEvento() == null ? "" : dto.getEvento();
			String nome = user.nome() == null ? "" : user.nome().trim();
			if (jaIdentificaUsuario(evento, nome)) {
				return;
			}
			dto.setEvento(evento + " | usuario=" + (nome.isBlank() ? "Usuario autenticado" : nome)
					+ " | matricula=" + user.matricula().trim());
		} catch (Exception e) {
			// identificar o autor nunca impede a gravacao do log
		}
	}

	static boolean jaIdentificaUsuario(String evento, String nome) {
		String texto = evento.toLowerCase(java.util.Locale.ROOT);
		if (texto.matches("(?s)(^|.*\\|)\\s*usuario\\s*[=:].*")) {
			return true;
		}
		return !nome.isBlank() && texto.contains(nome.toLowerCase(java.util.Locale.ROOT));
	}

	private static AuthenticatedUser usuarioDaRequisicao() {
		try {
			ResteasyReactiveRequestContext req = CurrentRequestManager.get();
			Object u = req == null ? null : req.getContainerRequestContext().getProperty("ghbam.user");
			return u instanceof AuthenticatedUser user ? user : null;
		} catch (Exception e) {
			return null;
		}
	}

	// Falha na identificacao do paciente nunca impede a gravacao do log: fica ao
	// menos a referencia crua (bam/idpaciente/prontuario) que ja esta no path.
	private void anexarPaciente(LogDto dto) {
		if (dto.getPacienteTipo() == null || dto.getPacienteValor() == null) {
			return;
		}
		String sufixo;
		try {
			sufixo = auditPacienteResolver.descrever(dto.getPacienteTipo(), dto.getPacienteValor());
		} catch (Exception e) {
			sufixo = " | paciente=erro ao identificar (" + dto.getPacienteTipo() + "=" + dto.getPacienteValor() + ")";
		}
		dto.setEvento(dto.getEvento() + sufixo);
	}

}

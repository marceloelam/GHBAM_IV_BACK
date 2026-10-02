package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

	@PostConstruct
	public void init() {
		// executor with a small pool to serialize log inserts and avoid DB connection
		// spikes
		this.logExecutor = Executors.newFixedThreadPool(4);
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
	public List<Log> filtarPorLogData(String data, String unidade) {

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
				// Write to a dead-letter file so logs are not lost
				try {
					Path dir = Paths.get("logs");
					if (!Files.exists(dir)) {
						Files.createDirectories(dir);
					}
					String line = String.format("data=%s;hora=%s;evento=%s;unidade=%s;error=%s%n", dto.getData(),
							dto.getHora(), dto.getEvento(), dto.getUnidade(), e.getMessage());
					Files.writeString(dir.resolve("deadletter-logs.txt"), line, StandardOpenOption.CREATE,
							StandardOpenOption.APPEND);
				} catch (Exception ex) {
					System.err.println("Failed to write dead-letter log: " + ex.getMessage());
				}
			}
		});

		return log;
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

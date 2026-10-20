package br.com.iv.service;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.iv.entity.ChamadaPaciente;
import br.com.iv.entity.UnidadeVideoConfig;
import br.com.iv.repository.UnidadeVideoConfigRepository;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Locução da chamada de paciente gerada no servidor, com voz neural local
 * (Kokoro via sherpa-onnx, ou Piper). Nada sai da rede do hospital.
 *
 * O painel é kiosk público (sem login) e o sistema poderá ficar na internet.
 * Por isso o áudio da chamada NÃO aceita texto livre: só é montado a partir de
 * uma chamada real, da própria unidade/TV, entre as mais recentes.
 *
 * Desligado por padrão. Qualquer falha faz o painel voltar para a voz do
 * navegador. Os WAVs são temporários: apagados logo após a leitura; o cache
 * fica só em memória (limitado e com validade).
 */
@ApplicationScoped
public class PainelVozService {

	private static final Logger LOG = Logger.getLogger(PainelVozService.class);

	static final int CHAMADAS_RECENTES_PERMITIDAS = 10;
	private static final int MAX_ITENS_CACHE = 40;
	private static final long VALIDADE_CACHE_MS = 30L * 60 * 1000;
	private static final String PREFIXO_TEMP = "ghbam-painel-voz-";
	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
	private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
	private static final Set<String> PARTICULAS = Set.of("da", "de", "do", "das", "dos", "e");
	private static final Set<String> LOCAIS_FEMININOS = Set.of("sala", "triagem", "recepção", "recepcao",
			"enfermaria", "farmácia", "farmacia", "emergência", "emergencia", "classificação", "classificacao",
			"ortopedia", "pediatria", "odontologia", "radiologia", "observação", "observacao", "coleta",
			"vacina", "vacinação", "vacinacao", "mesa", "cabine", "unidade", "assistência", "assistente");

	static final String TEXTO_EXEMPLO_NOME = "Maria da Silva";
	static final String TEXTO_EXEMPLO_LOCAL = "Consultório 1";
	static final String TEXTO_EXEMPLO_SETOR = "Clínica Médica";

	/** Catálogo fixo: só aparece para escolha a voz cujos arquivos existem no servidor. */
	public record Voz(String id, String nome, String genero, String motor, String parametro, String descricao) {
	}

	static final List<Voz> CATALOGO = List.of(
			new Voz("kokoro-dora", "Dora", "feminina", "kokoro", "42", "Mais natural"),
			new Voz("kokoro-alex", "Alex", "masculina", "kokoro", "43", "Mais natural"),
			new Voz("kokoro-santa", "Santa", "masculina", "kokoro", "44", "Mais natural"),
			new Voz("piper-faber", "Faber", "masculina", "piper", "pt_BR-faber-medium.onnx", "Mais rápida"));

	@ConfigProperty(name = "ghbam.painel.voz.habilitado", defaultValue = "false")
	boolean habilitado;

	@ConfigProperty(name = "ghbam.painel.voz.unidades")
	Optional<List<String>> unidadesHabilitadas;

	@ConfigProperty(name = "ghbam.painel.voz.padrao", defaultValue = "kokoro-dora")
	String vozPadrao;

	@ConfigProperty(name = "ghbam.painel.voz.kokoro.executavel", defaultValue = "/opt/tts/sherpa-onnx/bin/sherpa-onnx-offline-tts")
	String kokoroExecutavel;

	@ConfigProperty(name = "ghbam.painel.voz.kokoro.modelo-dir", defaultValue = "/opt/tts/kokoro-multi-lang-v1_0")
	String kokoroModeloDir;

	@ConfigProperty(name = "ghbam.painel.voz.kokoro.threads", defaultValue = "4")
	int kokoroThreads;

	// Serviço de voz (tools/servidor-voz): modelo carregado uma vez, com pausas de respiração.
	@ConfigProperty(name = "ghbam.painel.voz.kokoro.servico-url")
	Optional<String> kokoroServicoUrl;

	private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
			.connectTimeout(java.time.Duration.ofSeconds(2))
			.build();

	@ConfigProperty(name = "ghbam.painel.voz.piper.executavel", defaultValue = "/opt/tts/piper/piper")
	String piperExecutavel;

	@ConfigProperty(name = "ghbam.painel.voz.piper.dir", defaultValue = "/opt/tts/piper-vozes")
	String piperDir;

	@ConfigProperty(name = "ghbam.painel.voz.timeout-segundos", defaultValue = "20")
	int timeoutSegundos;

	@ConfigProperty(name = "ghbam.painel.voz.fuso", defaultValue = "America/Sao_Paulo")
	String fuso;

	@Inject
	EntityManager entityManager;

	@Inject
	UnidadeVideoConfigRepository unidadeVideoConfigRepository;

	// No máximo 2 sínteses simultâneas e 8 na fila; o excedente é recusado (painel cai na voz do navegador).
	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(8), r -> {
				Thread t = new Thread(r, "painel-voz");
				t.setDaemon(true);
				return t;
			});

	private final Map<String, CompletableFuture<byte[]>> emAndamento = new ConcurrentHashMap<>();
	private volatile boolean temporariosLimpos;

	private final Map<String, ItemCache> cache = new LinkedHashMap<>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, ItemCache> eldest) {
			return size() > MAX_ITENS_CACHE;
		}
	};

	private record ItemCache(byte[] audio, long criadoEm) {
	}

	public static class VozIndisponivelException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public VozIndisponivelException(String mensagem) {
			super(mensagem);
		}
	}

	@PreDestroy
	void encerrar() {
		executor.shutdownNow();
	}

	// ---------------------------------------------------------------- consultas

	public boolean ativoPara(String sigla) {
		if (!habilitado || sigla == null || sigla.isBlank() || vozesDisponiveis().isEmpty()) {
			return false;
		}
		List<String> lista = unidadesHabilitadas.orElse(List.of()).stream()
				.map(s -> s.trim().toUpperCase(Locale.ROOT))
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
		return lista.isEmpty() || lista.contains(sigla.trim().toUpperCase(Locale.ROOT));
	}

	public List<Map<String, Object>> listarVozes() {
		List<Map<String, Object>> vozes = new ArrayList<>();
		for (Voz voz : CATALOGO) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", voz.id());
			item.put("nome", voz.nome());
			item.put("genero", voz.genero());
			item.put("descricao", voz.descricao());
			item.put("disponivel", habilitado && disponivel(voz));
			item.put("padrao", voz.id().equals(vozPadrao));
			vozes.add(item);
		}
		return vozes;
	}

	private List<Voz> vozesDisponiveis() {
		return CATALOGO.stream().filter(this::disponivel).toList();
	}

	private boolean disponivel(Voz voz) {
		if ("kokoro".equals(voz.motor())) {
			return servicoKokoroConfigurado() || kokoroLinhaDeComandoInstalado();
		}
		return Files.isRegularFile(Path.of(piperExecutavel))
				&& Files.isRegularFile(Path.of(piperDir, voz.parametro()));
	}

	private Optional<UnidadeVideoConfig> configDaUnidade(String sigla) {
		return unidadeVideoConfigRepository.buscarPorSigla(sigla);
	}

	private Voz resolverVoz(String id) {
		List<Voz> disponiveis = vozesDisponiveis();
		if (disponiveis.isEmpty()) {
			throw new VozIndisponivelException("Nenhuma voz instalada no servidor.");
		}
		return disponiveis.stream().filter(v -> v.id().equals(id)).findFirst()
				.or(() -> disponiveis.stream().filter(v -> v.id().equals(vozPadrao)).findFirst())
				.orElse(disponiveis.get(0));
	}

	// ---------------------------------------------------------------- áudio

	/**
	 * Devolve o WAV da locução ou vazio quando a chamada não pertence às
	 * chamadas recentes daquela unidade/TV.
	 */
	public Optional<byte[]> audioDaChamada(String sigla, int tv, int idchamada) {
		if (!ativoPara(sigla)) {
			throw new VozIndisponivelException("Voz do servidor desligada para a unidade.");
		}
		if (tv < 1 || tv > 10 || idchamada <= 0) {
			return Optional.empty();
		}

		ChamadaPaciente chamada = buscarChamadaRecente(sigla, tv, idchamada);
		if (chamada == null) {
			return Optional.empty();
		}
		Optional<UnidadeVideoConfig> config = configDaUnidade(sigla);
		Voz voz = resolverVoz(config.map(UnidadeVideoConfig::getVozPainel).orElse(null));
		String texto = textoDaChamada(chamada, config.map(UnidadeVideoConfig::getFraseChamada).orElse(null));
		return Optional.of(aguardar(sintetizarComCache(texto, voz), idchamada));
	}

	/**
	 * Exemplo para a tela de configuração (rota autenticada, kadm). Aceita a
	 * frase ainda não salva para o usuário ouvir antes de gravar.
	 */
	public byte[] exemplo(String vozId, String frase) {
		if (!habilitado) {
			throw new VozIndisponivelException("Voz do servidor desligada.");
		}
		Voz voz = CATALOGO.stream().filter(v -> v.id().equals(vozId) && disponivel(v)).findFirst()
				.orElseThrow(() -> new VozIndisponivelException("Voz não instalada no servidor."));
		String texto = montarTexto(validarModeloFrase(frase), saudacaoAgora(),
				TEXTO_EXEMPLO_NOME, TEXTO_EXEMPLO_LOCAL, TEXTO_EXEMPLO_SETOR);
		return aguardar(sintetizarComCache(texto, voz), 0);
	}

	/**
	 * Começa a gerar o áudio assim que a chamada é criada, para ele já estar
	 * pronto quando o painel pedir. Nunca lança exceção: é só otimização.
	 */
	public void preaquecer(ChamadaPaciente chamada) {
		try {
			if (chamada == null || !ativoPara(chamada.getUnidade())) {
				return;
			}
			Optional<UnidadeVideoConfig> config = configDaUnidade(chamada.getUnidade().trim());
			sintetizarComCache(
					textoDaChamada(chamada, config.map(UnidadeVideoConfig::getFraseChamada).orElse(null)),
					resolverVoz(config.map(UnidadeVideoConfig::getVozPainel).orElse(null)));
		} catch (Exception e) {
			LOG.debugf("Pré-geração da voz ignorada (idchamada=%d): %s",
					chamada == null ? 0 : chamada.getIdchamada(), e.getClass().getSimpleName());
		}
	}

	private ChamadaPaciente buscarChamadaRecente(String sigla, int tv, int idchamada) {
		// tv já foi validado entre 1 e 10, então o nome da coluna é seguro.
		@SuppressWarnings("unchecked")
		List<Number> recentes = entityManager.createNativeQuery(
				"select idchamada from chamadapaciente where upper(unidade) = :unidade and p" + tv
						+ " = true order by idchamada desc limit " + CHAMADAS_RECENTES_PERMITIDAS)
				.setParameter("unidade", sigla.trim().toUpperCase(Locale.ROOT))
				.getResultList();

		boolean recente = recentes.stream().anyMatch(n -> n.intValue() == idchamada);
		return recente ? entityManager.find(ChamadaPaciente.class, idchamada) : null;
	}

	private String textoDaChamada(ChamadaPaciente chamada, String modeloFrase) {
		return montarTexto(modeloFrase, saudacaoAgora(), chamada.getNome(), chamada.getLocal(), chamada.getSetor());
	}

	private String saudacaoAgora() {
		return saudacao(ZonedDateTime.now(ZoneId.of(fuso)).getHour());
	}

	private CompletableFuture<byte[]> sintetizarComCache(String texto, Voz voz) {
		String chave = hash(voz.id() + "|" + texto);
		long agora = System.currentTimeMillis();
		synchronized (cache) {
			ItemCache item = cache.get(chave);
			if (item != null && agora - item.criadoEm() < VALIDADE_CACHE_MS) {
				return CompletableFuture.completedFuture(item.audio());
			}
		}

		// Mesma frase pedida duas vezes (pré-geração + painel, ou duas TVs) gera um único áudio.
		CompletableFuture<byte[]> futuro = emAndamento.computeIfAbsent(chave, k -> iniciarSintese(k, texto, voz));
		return futuro != null ? futuro
				: CompletableFuture.failedFuture(new VozIndisponivelException("Fila de síntese cheia."));
	}

	/** Agenda a síntese; devolve null se a fila estiver cheia (nada é registrado em emAndamento). */
	private CompletableFuture<byte[]> iniciarSintese(String chave, String texto, Voz voz) {
		CompletableFuture<byte[]> futuro = new CompletableFuture<>();
		try {
			executor.execute(() -> {
				try {
					byte[] audio = sintetizar(texto, voz);
					synchronized (cache) {
						cache.put(chave, new ItemCache(audio, System.currentTimeMillis()));
					}
					futuro.complete(audio);
				} catch (Throwable t) {
					futuro.completeExceptionally(t);
				} finally {
					emAndamento.remove(chave);
				}
			});
			return futuro;
		} catch (RejectedExecutionException e) {
			return null;
		}
	}

	private byte[] aguardar(CompletableFuture<byte[]> futuro, int idchamada) {
		try {
			return futuro.get(timeoutSegundos, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			LOG.warnf("Voz do painel excedeu o tempo (idchamada=%d).", idchamada);
			throw new VozIndisponivelException("Tempo de síntese excedido.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new VozIndisponivelException("Síntese interrompida.");
		} catch (ExecutionException e) {
			// Nunca registrar o texto: contém o nome do paciente.
			LOG.warnf("Voz do painel indisponível (idchamada=%d): %s", idchamada,
					e.getCause() == null ? "?" : e.getCause().getMessage());
			throw new VozIndisponivelException("Falha na síntese.");
		}
	}

	private boolean servicoKokoroConfigurado() {
		return kokoroServicoUrl.map(u -> !u.isBlank()).orElse(false);
	}

	private boolean kokoroLinhaDeComandoInstalado() {
		return Files.isRegularFile(Path.of(kokoroExecutavel))
				&& Files.isRegularFile(Path.of(kokoroModeloDir, "model.onnx"));
	}

	private byte[] sintetizarViaServico(String texto, Voz voz) throws Exception {
		String base = kokoroServicoUrl.orElseThrow().replaceAll("/+$", "");
		java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(
				java.net.URI.create(base + "/falar?sid=" + voz.parametro()))
				.timeout(java.time.Duration.ofSeconds(timeoutSegundos))
				.header("Content-Type", "text/plain; charset=utf-8")
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString(texto, StandardCharsets.UTF_8))
				.build();
		java.net.http.HttpResponse<byte[]> resp = httpClient.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
		if (resp.statusCode() != 200 || resp.body().length < 100) {
			throw new IllegalStateException("serviço de voz respondeu " + resp.statusCode());
		}
		return resp.body();
	}

	private byte[] sintetizar(String texto, Voz voz) throws Exception {
		if ("kokoro".equals(voz.motor()) && servicoKokoroConfigurado()) {
			try {
				return sintetizarViaServico(texto, voz);
			} catch (Exception e) {
				// Sem o serviço, ainda dá para falar pelo modo linha de comando (sem as pausas longas).
				LOG.warnf("Serviço de voz indisponível (%s); usando linha de comando.", e.getClass().getSimpleName());
				if (!kokoroLinhaDeComandoInstalado()) {
					throw e;
				}
			}
		}
		limparTemporariosAntigos();
		Path saida = Files.createTempFile(PREFIXO_TEMP, ".wav");
		Process processo = null;
		try {
			ProcessBuilder pb;
			String entrada = null;
			if ("kokoro".equals(voz.motor())) {
				String dir = kokoroModeloDir.endsWith("/") ? kokoroModeloDir : kokoroModeloDir + "/";
				pb = new ProcessBuilder(kokoroExecutavel,
						"--num-threads=" + Math.max(1, kokoroThreads),
						"--kokoro-model=" + dir + "model.onnx",
						"--kokoro-voices=" + dir + "voices.bin",
						"--kokoro-tokens=" + dir + "tokens.txt",
						"--kokoro-data-dir=" + dir + "espeak-ng-data",
						"--kokoro-lang=pt-br",
						"--sid=" + voz.parametro(),
						"--output-filename=" + saida,
						// Kokoro pronuncia igual em minúsculas; evita letras que o Windows não converte.
						argumentoDeLinhaDeComando(texto.toLowerCase(PT_BR)));
			} else {
				pb = new ProcessBuilder(piperExecutavel,
						"--model", Path.of(piperDir, voz.parametro()).toString(),
						"--output_file", saida.toString(),
						"--length_scale", "1.05",
						"--sentence_silence", "0.15");
				entrada = texto;
			}

			processo = pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			try (OutputStream in = processo.getOutputStream()) {
				if (entrada != null) {
					in.write(entrada.getBytes(StandardCharsets.UTF_8));
				}
			}
			if (!processo.waitFor(timeoutSegundos, TimeUnit.SECONDS)) {
				throw new IllegalStateException(voz.motor() + " excedeu o tempo limite");
			}
			if (processo.exitValue() != 0 || Files.size(saida) < 100) {
				throw new IllegalStateException(voz.motor() + " terminou com código " + processo.exitValue());
			}
			return Files.readAllBytes(saida);
		} finally {
			if (processo != null && processo.isAlive()) {
				processo.destroyForcibly();
			}
			Files.deleteIfExists(saida);
		}
	}

	/**
	 * Remove WAVs que tenham sobrado de um processo interrompido (ex.: queda do
	 * servidor no meio de uma síntese). Roda uma vez por inicialização.
	 */
	private void limparTemporariosAntigos() {
		if (temporariosLimpos) {
			return;
		}
		temporariosLimpos = true;
		Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
		long limite = System.currentTimeMillis() - 10 * 60 * 1000;
		try (DirectoryStream<Path> sobras = Files.newDirectoryStream(tmp, PREFIXO_TEMP + "*.wav")) {
			for (Path arquivo : sobras) {
				if (Files.getLastModifiedTime(arquivo).toMillis() < limite) {
					Files.deleteIfExists(arquivo);
				}
			}
		} catch (Exception e) {
			LOG.debugf("Limpeza de temporários da voz ignorada: %s", e.getClass().getSimpleName());
		}
	}

	/**
	 * No Linux (servidor de produção) o texto vai como está, em UTF-8. No
	 * Windows com página ANSI cp1252, o programa nativo recebe o argumento
	 * convertido para cp1252; então entregamos uma string cuja conversão gera
	 * exatamente os bytes UTF-8 do texto. Sem essa troca, "João" vira "Jo?o".
	 */
	static String argumentoDeLinhaDeComando(String texto) {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		if (!windows || !"Cp1252".equalsIgnoreCase(System.getProperty("sun.jnu.encoding"))) {
			return texto;
		}
		return converterParaCp1252(texto);
	}

	static String converterParaCp1252(String texto) {
		byte[] utf8 = texto.getBytes(StandardCharsets.UTF_8);
		String convertido = new String(utf8, WINDOWS_1252);
		return Arrays.equals(convertido.getBytes(WINDOWS_1252), utf8) ? convertido : texto;
	}

	// ---------------------------------------------------------------- texto

	// ---------------------------------------------------------------- frase personalizada

	static final int TAMANHO_MAXIMO_FRASE = 300;
	private static final java.util.regex.Pattern MARCADOR = java.util.regex.Pattern.compile("\\[([^\\]]*)\\]");

	/** Nome do marcador sem acento/maiúsculas → chave interna. */
	private static String chaveMarcador(String marcador) {
		String sem = java.text.Normalizer.normalize(marcador.trim().toLowerCase(PT_BR), java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").replaceAll("\\s+", " ");
		return switch (sem) {
			case "saudacao" -> "saudacao";
			case "paciente", "nome" -> "paciente";
			case "ao local", "local" -> "local";
			case "no setor", "setor" -> "setor";
			default -> null;
		};
	}

	/**
	 * Valida a frase digitada na configuração da unidade. Devolve a frase limpa,
	 * ou null quando vazia (= frase padrão). Lança IllegalArgumentException com
	 * mensagem para o usuário quando há marcador desconhecido.
	 */
	public static String validarModeloFrase(String frase) {
		if (frase == null) {
			return null;
		}
		String limpa = frase.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
		if (limpa.isEmpty()) {
			return null;
		}
		if (limpa.length() > TAMANHO_MAXIMO_FRASE) {
			throw new IllegalArgumentException("A frase da chamada pode ter no máximo " + TAMANHO_MAXIMO_FRASE + " caracteres.");
		}
		java.util.regex.Matcher m = MARCADOR.matcher(limpa);
		while (m.find()) {
			if (chaveMarcador(m.group(1)) == null) {
				throw new IllegalArgumentException("Marcador desconhecido na frase: [" + m.group(1)
						+ "]. Use [saudação], [paciente], [ao local] ou [no setor].");
			}
		}
		return limpa;
	}

	/** Frase da unidade (ou a padrão) com os marcadores preenchidos e a concordância aplicada. */
	static String montarTexto(String modelo, String saudacao, String nome, String local, String setor) {
		if (modelo == null || modelo.isBlank()) {
			return montarTexto(saudacao, nome, local, setor);
		}
		String localEscrito = normalizarParaFala(local);
		String setorEscrito = normalizarParaFala(setor);
		Map<String, String> valores = Map.of(
				"saudacao", saudacao,
				"paciente", normalizarParaFala(nome),
				"local", localEscrito.isEmpty() ? "" : artigo(localEscrito) + " " + falarSiglas(localEscrito),
				"setor", setorEscrito.isEmpty() || setorEscrito.equalsIgnoreCase(localEscrito) ? ""
						: "no setor " + (setorComPreposicao(setorEscrito) ? "de " : "") + falarSiglas(setorEscrito));

		java.util.regex.Matcher m = MARCADOR.matcher(modelo);
		StringBuilder texto = new StringBuilder();
		while (m.find()) {
			String chave = chaveMarcador(m.group(1));
			m.appendReplacement(texto, java.util.regex.Matcher.quoteReplacement(
					chave == null ? "" : valores.getOrDefault(chave, "")));
		}
		m.appendTail(texto);

		// Marcador vazio (ex.: chamada sem setor) não pode deixar ", ." ou espaços sobrando.
		String resultado = texto.toString()
				.replaceAll("\\s+", " ")
				.replaceAll("\\s+([,.!?;:])", "$1")
				.replaceAll(",\\s*([.!?;:])", "$1")
				.replaceAll(",\\s*,", ",")
				.replaceAll("^[,\\s]+", "")
				.trim();
		return resultado.isEmpty() || resultado.matches(".*[.!?]$") ? resultado : resultado + ".";
	}

	/**
	 * Ex.: "Boa tarde, paciente Maria da Silva. Por favor, compareça ao Consultório 1, no setor de Clínica Médica."
	 * Concordância: "ao/à" pelo gênero do local; "no setor de Pediatria", mas "no setor Azul".
	 */
	static String montarTexto(String saudacao, String nome, String local, String setor) {
		String nomeFalado = normalizarParaFala(nome);
		String localEscrito = normalizarParaFala(local);
		String setorEscrito = normalizarParaFala(setor);

		StringBuilder texto = new StringBuilder(saudacao);
		if (!nomeFalado.isEmpty()) {
			texto.append(", paciente ").append(nomeFalado);
		}
		texto.append(". Por favor, compareça");
		if (!localEscrito.isEmpty()) {
			texto.append(' ').append(artigo(localEscrito)).append(' ').append(falarSiglas(localEscrito));
		}
		if (!setorEscrito.isEmpty() && !setorEscrito.equalsIgnoreCase(localEscrito)) {
			texto.append(", no setor ");
			if (setorComPreposicao(setorEscrito)) {
				texto.append("de ");
			}
			texto.append(falarSiglas(setorEscrito));
		}
		return texto.append('.').toString();
	}

	// Masculinos terminados em "a" que aparecem como local de atendimento.
	private static final Set<String> MASCULINOS_EM_A = Set.of("dia", "sistema", "programa", "mapa", "trauma",
			"plantonista", "pediatra", "obstetra", "psiquiatra", "anestesista", "dentista", "cardiologista");
	private static final Set<String> SIGLAS_FEMININAS = Set.of("uti", "utin", "ucin", "upa", "ubs", "usf");
	private static final Set<String> CORES_E_CODIGOS = Set.of("azul", "verde", "amarelo", "amarela", "laranja",
			"vermelho", "vermelha", "branco", "branca", "rosa", "roxo", "cinza");
	// Escritas como se falam: em minúsculas o motor leria "uti" como palavra.
	private static final Map<String, String> SIGLAS_FALADAS = Map.of(
			"Uti", "u tê i", "Utin", "u tê i neonatal", "Cti", "cê tê i", "Ubs", "u bê esse",
			"Usf", "u esse efe", "Rx", "raio x", "Ecg", "eletrocardiograma", "Sae", "esse a é");

	/** "ao Consultório 1", "à Sala 2", "à Triagem", "à UTI", "ao Eletrocardiograma", "à Hemodiálise". */
	static String artigo(String local) {
		String primeira = local.split(" ")[0].toLowerCase(PT_BR);
		if (LOCAIS_FEMININOS.contains(primeira) || SIGLAS_FEMININAS.contains(primeira)) {
			return "à";
		}
		if (MASCULINOS_EM_A.contains(primeira) || primeira.endsWith("grama")) {
			return "ao";
		}
		boolean terminacaoFeminina = primeira.endsWith("a") || primeira.endsWith("ção")
				|| primeira.endsWith("são") || primeira.endsWith("gem") || primeira.endsWith("dade")
				|| primeira.endsWith("ise");
		return terminacaoFeminina ? "à" : "ao";
	}

	/** Cor da classificação de risco, número ou letra vão sem "de": "no setor Azul", "no setor 2". */
	static boolean setorComPreposicao(String setor) {
		String primeira = setor.split(" ")[0].toLowerCase(PT_BR);
		return !(CORES_E_CODIGOS.contains(primeira) || primeira.matches("\\d+|[a-z]"));
	}

	/** Troca siglas conhecidas pela forma falada. */
	static String falarSiglas(String texto) {
		if (texto.isEmpty()) {
			return texto;
		}
		String[] palavras = texto.split(" ");
		for (int i = 0; i < palavras.length; i++) {
			palavras[i] = SIGLAS_FALADAS.getOrDefault(palavras[i], palavras[i]);
		}
		return String.join(" ", palavras);
	}

	static String saudacao(int hora) {
		return hora < 12 ? "Bom dia" : hora < 18 ? "Boa tarde" : "Boa noite";
	}

	/**
	 * Nomes chegam em CAIXA ALTA; converte para "Maria da Silva" e tira zeros à
	 * esquerda ("Consultório 03" → "Consultório 3", senão fala "zero três").
	 */
	static String normalizarParaFala(String valor) {
		if (valor == null) {
			return "";
		}
		String limpo = valor.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim()
				.replaceAll("\\b0+(\\d)", "$1");
		if (limpo.isEmpty()) {
			return "";
		}
		String[] palavras = limpo.toLowerCase(PT_BR).split(" ");
		for (int i = 0; i < palavras.length; i++) {
			String p = ACENTOS.getOrDefault(palavras[i], palavras[i]);
			palavras[i] = p;
			if (i > 0 && PARTICULAS.contains(p)) {
				continue;
			}
			palavras[i] = p.substring(0, 1).toUpperCase(PT_BR) + p.substring(1);
		}
		return String.join(" ", palavras);
	}

	/**
	 * O cadastro costuma gravar sem acento ("JOSE", "HEMODIALISE") e o motor
	 * pronuncia errado ("Jóse"). Restaura o acento de nomes e locais comuns.
	 */
	private static final Map<String, String> ACENTOS = mapaDePares(
			// nomes e sobrenomes
			"jose", "josé", "joao", "joão", "antonio", "antônio", "lucia", "lúcia", "marcia", "márcia",
			"patricia", "patrícia", "fabio", "fábio", "marcio", "márcio", "sergio", "sérgio", "vitoria", "vitória",
			"claudia", "cláudia", "monica", "mônica", "angela", "ângela", "rogerio", "rogério", "flavio", "flávio",
			"otavio", "otávio", "julio", "júlio", "cecilia", "cecília", "leticia", "letícia", "tania", "tânia",
			"vania", "vânia", "sonia", "sônia", "valeria", "valéria", "debora", "débora", "barbara", "bárbara",
			"jessica", "jéssica", "andre", "andré", "inacio", "inácio", "mauricio", "maurício", "emilia", "emília",
			"amelia", "amélia", "aurelio", "aurélio", "helio", "hélio", "nazare", "nazaré", "gloria", "glória",
			"rosangela", "rosângela", "conceicao", "conceição", "assuncao", "assunção", "sebastiao", "sebastião",
			"estevao", "estevão", "damiao", "damião", "simao", "simão", "simoes", "simões", "guimaraes", "guimarães",
			"magalhaes", "magalhães", "araujo", "araújo", "falcao", "falcão", "romao", "romão", "goncalves", "gonçalves",
			"vinicius", "vinícius", "lazaro", "lázaro", "luis", "luís", "ines", "inês",
			// locais e setores
			"consultorio", "consultório", "clinica", "clínica", "medica", "médica", "medico", "médico",
			"recepcao", "recepção", "medicacao", "medicação", "farmacia", "farmácia", "emergencia", "emergência",
			"observacao", "observação", "classificacao", "classificação", "vacinacao", "vacinação",
			"hemodialise", "hemodiálise", "guiche", "guichê", "cirurgica", "cirúrgica", "pediatrica", "pediátrica",
			"ginecologica", "ginecológica", "obstetrica", "obstétrica", "odontologico", "odontológico",
			"nutricao", "nutrição", "laboratorio", "laboratório", "ambulatorio", "ambulatório", "inalacao", "inalação",
			"reanimacao", "reanimação", "servico", "serviço", "nucleo", "núcleo", "psicologico", "psicológico");

	private static Map<String, String> mapaDePares(String... pares) {
		Map<String, String> mapa = new java.util.HashMap<>();
		for (int i = 0; i + 1 < pares.length; i += 2) {
			mapa.put(pares[i], pares[i + 1]);
		}
		return Map.copyOf(mapa);
	}

	private static String hash(String texto) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			return Integer.toHexString(texto.hashCode());
		}
	}
}

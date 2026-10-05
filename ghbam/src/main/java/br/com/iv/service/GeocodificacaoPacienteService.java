package br.com.iv.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.entity.Pacientes;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Latitude/longitude do endereco do paciente via Nominatim (OpenStreetMap).
 *
 * Roda DEPOIS do commit do cadastro/alteracao e fora da requisicao: a recepcao nunca espera,
 * e se a consulta falhar o cadastro continua gravado com a coordenada que ja tinha.
 * Envia SO o endereco (rua, numero, bairro, municipio, UF) - nunca nome, documento ou dado clinico.
 * Desligado enquanto ghbam.geocodificacao.url estiver vazio (nenhum endereco sai do servidor).
 */
@ApplicationScoped
public class GeocodificacaoPacienteService {

	private static final Logger LOG = Logger.getLogger(GeocodificacaoPacienteService.class);

	// Centro de Itaguai: valor gravado por padrao quando ninguem calculava a coordenada.
	static final double LATITUDE_PADRAO = -22.869417;
	static final double LONGITUDE_PADRAO = -43.777349;

	@ConfigProperty(name = "ghbam.geocodificacao.url")
	Optional<String> url;

	@ConfigProperty(name = "ghbam.geocodificacao.timeout.seconds", defaultValue = "5")
	int timeoutSegundos;

	// Politica do Nominatim publico: no maximo 1 requisicao por segundo.
	@ConfigProperty(name = "ghbam.geocodificacao.intervalo.ms", defaultValue = "1100")
	long intervaloMs;

	@Inject
	ManagedExecutor executor;

	@Inject
	ObjectMapper mapper;

	private final Object trava = new Object();
	private long ultimaConsulta;

	/** Endereco a geocodificar, capturado no momento da gravacao. */
	public record Pedido(int idpaciente, String endereco, String numero, String bairro,
			String municipio, String estado, boolean tinhaCoordenadaReal) {
	}

	record Tentativa(String precisao, Map<String, String> params) {
	}

	record Resultado(double latitude, double longitude, String precisao) {
	}

	public static Pedido pedidoDe(Pacientes p, boolean tinhaCoordenadaReal) {
		return new Pedido(p.getIdpaciente(), txt(p.getEndereco()), txt(p.getNumero()), txt(p.getBairro()),
				txt(p.getMunicipio()), txt(p.getEstado()), tinhaCoordenadaReal);
	}

	public static String chaveEndereco(Pacientes p) {
		return String.join("|", txt(p.getEndereco()), txt(p.getNumero()), txt(p.getBairro()),
				txt(p.getMunicipio()), txt(p.getEstado()), txt(p.getCep())).toUpperCase();
	}

	/** Coordenada dentro do Brasil e diferente do ponto padrao de Itaguai. */
	public static boolean coordenadaReal(double lat, double lon) {
		if (!dentroDoBrasil(lat, lon)) {
			return false;
		}
		return Math.abs(lat - LATITUDE_PADRAO) > 1e-6 || Math.abs(lon - LONGITUDE_PADRAO) > 1e-6;
	}

	static boolean dentroDoBrasil(double lat, double lon) {
		return lat >= -34 && lat <= 6 && lon >= -74 && lon <= -33;
	}

	public boolean habilitado() {
		return url.map(String::isBlank).map(b -> !b).orElse(false);
	}

	void aoConfirmarGravacao(@Observes(during = TransactionPhase.AFTER_SUCCESS) Pedido pedido) {
		if (!habilitado() || pedido == null || pedido.idpaciente() <= 0) {
			return;
		}
		executor.runAsync(() -> processar(pedido));
	}

	void processar(Pedido pedido) {
		try {
			Resultado r = geocodificar(pedido);
			if (r == null) {
				LOG.debugf("Geocodificacao sem resultado para paciente %d", pedido.idpaciente());
				return;
			}
			// So achou a cidade: nao troca uma coordenada real que ja existia.
			if ("municipio".equals(r.precisao()) && pedido.tinhaCoordenadaReal()) {
				return;
			}
			// So grava se o endereco ainda for o mesmo (outra alteracao pode ter chegado antes).
			int linhas = QuarkusTransaction.requiringNew().call(() -> Pacientes.update(
					"latitude = ?1, longitude = ?2 where idpaciente = ?3"
							+ " and coalesce(endereco, '') = ?4 and coalesce(numero, '') = ?5"
							+ " and coalesce(municipio, '') = ?6",
					r.latitude(), r.longitude(), pedido.idpaciente(),
					pedido.endereco(), pedido.numero(), pedido.municipio()));
			LOG.infof("Coordenada do paciente %d atualizada (precisao %s, linhas %d)",
					pedido.idpaciente(), r.precisao(), linhas);
		} catch (Exception e) {
			LOG.warnf("Falha ao geocodificar paciente %d: %s", pedido.idpaciente(), e.getMessage());
		}
	}

	/** Da mais precisa para a menos precisa. */
	static List<Tentativa> montarTentativas(Pedido p) {
		List<Tentativa> tentativas = new ArrayList<>();
		if (p.municipio().isBlank()) {
			return tentativas;
		}
		if (!p.endereco().isBlank()) {
			String numero = p.numero().replaceAll("\\D", "");
			Map<String, String> params = new LinkedHashMap<>();
			params.put("street", (numero.isEmpty() ? "" : numero + " ") + p.endereco());
			params.put("city", p.municipio());
			params.put("state", p.estado());
			tentativas.add(new Tentativa("endereco", params));
		}
		if (!p.bairro().isBlank()) {
			String q = String.join(", ", List.of(p.bairro(), p.municipio(), p.estado()).stream()
					.filter(s -> !s.isBlank()).toList());
			tentativas.add(new Tentativa("bairro", Map.of("q", q)));
		}
		Map<String, String> cidade = new LinkedHashMap<>();
		cidade.put("city", p.municipio());
		cidade.put("state", p.estado());
		tentativas.add(new Tentativa("municipio", cidade));
		return tentativas;
	}

	Resultado geocodificar(Pedido pedido) throws InterruptedException {
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(timeoutSegundos))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		for (Tentativa t : montarTentativas(pedido)) {
			Resultado r = consultar(client, t);
			if (r != null) {
				return r;
			}
		}
		return null;
	}

	private Resultado consultar(HttpClient client, Tentativa t) throws InterruptedException {
		StringBuilder qs = new StringBuilder("format=jsonv2&limit=1&countrycodes=br&accept-language=pt-BR");
		t.params().forEach((k, v) -> {
			if (v != null && !v.isBlank()) {
				qs.append('&').append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8));
			}
		});
		String base = url.orElse("").replaceAll("/+$", "");
		HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/search?" + qs))
				.timeout(Duration.ofSeconds(timeoutSegundos))
				.header("Accept", "application/json")
				.header("User-Agent", "GHBAM-IV/1.0 (geocodificacao do cadastro de pacientes)")
				.GET()
				.build();
		synchronized (trava) {
			long espera = ultimaConsulta + intervaloMs - System.currentTimeMillis();
			if (espera > 0) {
				Thread.sleep(espera);
			}
			try {
				HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
				if (resp.statusCode() != 200) {
					return null;
				}
				return interpretar(resp.body(), t.precisao());
			} catch (java.io.IOException e) {
				return null;
			} finally {
				ultimaConsulta = System.currentTimeMillis();
			}
		}
	}

	Resultado interpretar(String json, String precisao) {
		try {
			JsonNode lista = mapper.readTree(json);
			if (lista == null || !lista.isArray() || lista.isEmpty()) {
				return null;
			}
			JsonNode item = lista.get(0);
			double lat = Double.parseDouble(item.path("lat").asText("x"));
			double lon = Double.parseDouble(item.path("lon").asText("x"));
			return dentroDoBrasil(lat, lon) ? new Resultado(lat, lon, precisao) : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static String txt(String s) {
		return s == null ? "" : s.trim();
	}

	// ---------------------------------------------------------------------------------------------
	// Tabela local (sem internet): centro de cada bairro da regiao e de cada municipio do RJ,
	// extraida do OpenStreetMap em 2026-10-03 (resources/geocodificacao/bairros-municipios-rj.json).
	// ---------------------------------------------------------------------------------------------

	static String normalizar(String s) {
		return java.text.Normalizer.normalize(txt(s), java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").toUpperCase()
				.replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
	}

	private static String semEspaco(String s) {
		return s.replace(" ", "");
	}

	private static final class TabelaLocal {
		static final Map<String, double[]> MUNICIPIOS = new java.util.HashMap<>();
		// chave "MUNICIPIO|BAIRRO" sem espacos: "BRISAMAR" e "BRISA MAR" caem no mesmo bairro.
		static final Map<String, double[]> BAIRROS = new java.util.HashMap<>();
		// Sede de todos os municipios do Brasil, chave "UF|MUNICIPIO"; e nome do estado -> sigla.
		static final Map<String, double[]> MUNICIPIOS_BRASIL = new java.util.HashMap<>();
		static final Map<String, String> UFS = new java.util.HashMap<>();
		// Listas para os selects da recepcao (nome de exibicao, com acento), ja ordenadas.
		static final Map<String, List<String>> NOMES_MUNICIPIOS_POR_UF = new java.util.TreeMap<>();
		static final Map<String, List<String>> NOMES_BAIRROS_POR_MUNICIPIO = new java.util.HashMap<>();

		private static double[] ponto(JsonNode v) {
			return v.get(0).isNull() ? null : new double[] { v.get(0).asDouble(), v.get(1).asDouble() };
		}

		private static void listar(Map<String, List<String>> destino, String chave, String nome) {
			destino.computeIfAbsent(chave, k -> new ArrayList<>()).add(nome);
		}

		static {
			try (java.io.InputStream in = GeocodificacaoPacienteService.class
					.getResourceAsStream("/geocodificacao/bairros-municipios-rj.json")) {
				JsonNode raiz = new ObjectMapper().readTree(in);
				raiz.path("municipios").fields().forEachRemaining(e -> MUNICIPIOS.put(semEspaco(e.getKey()), ponto(e.getValue())));
				raiz.path("bairros").fields().forEachRemaining(e -> {
					double[] c = ponto(e.getValue());
					if (c != null) {
						BAIRROS.put(semEspaco(e.getKey()), c);
					}
					// Apelidos (alt_name do OSM) valem para a coordenada, mas nao aparecem na lista.
					if (e.getValue().path(3).asInt(0) == 0) {
						listar(NOMES_BAIRROS_POR_MUNICIPIO, semEspaco(e.getKey().split("\\|")[0]), e.getValue().path(2).asText());
					}
				});
				raiz.path("municipiosBrasil").fields().forEachRemaining(e -> {
					MUNICIPIOS_BRASIL.put(semEspaco(e.getKey()), ponto(e.getValue()));
					listar(NOMES_MUNICIPIOS_POR_UF, e.getKey().split("\\|")[0], e.getValue().path(2).asText());
				});
				raiz.path("ufs").fields().forEachRemaining(e -> UFS.put(e.getKey(), e.getValue().asText()));
				java.text.Collator ordem = java.text.Collator.getInstance(new java.util.Locale("pt", "BR"));
				NOMES_MUNICIPIOS_POR_UF.values().forEach(l -> l.sort(ordem));
				NOMES_BAIRROS_POR_MUNICIPIO.values().forEach(l -> l.sort(ordem));
			} catch (Exception e) {
				LOG.warnf("Tabela local de bairros indisponivel: %s", e.getMessage());
			}
		}
	}

	private static String siglaUf(String estado) {
		String uf = normalizar(estado);
		return uf.isEmpty() ? "RJ" : TabelaLocal.UFS.getOrDefault(uf, uf);
	}

	/** Municipios do estado (sigla ou nome), para o select da recepcao. */
	public static List<String> listarMunicipios(String estado) {
		return TabelaLocal.NOMES_MUNICIPIOS_POR_UF.getOrDefault(siglaUf(estado), List.of());
	}

	/** Bairros conhecidos do municipio (so ha bairros do RJ); vazio = campo livre. */
	public static List<String> listarBairros(String estado, String municipio) {
		if (!"RJ".equals(siglaUf(estado))) {
			return List.of();
		}
		return TabelaLocal.NOMES_BAIRROS_POR_MUNICIPIO.getOrDefault(semEspaco(normalizar(municipio)), List.of());
	}

	/** Centro do bairro (ou, sem bairro conhecido, do municipio). Null se o municipio nao for do RJ. */
	static Resultado coordenadaLocal(String bairro, String municipio, String estado) {
		String mun = semEspaco(normalizar(municipio));
		String uf = normalizar(estado);
		uf = uf.isEmpty() ? "RJ" : TabelaLocal.UFS.getOrDefault(uf, uf);
		if (mun.isEmpty()) {
			return null;
		}
		// Outro estado: so ha a sede do municipio (chave com a UF: "Bom Jesus/PI" nao cai no RJ).
		if (!uf.equals("RJ")) {
			double[] c = TabelaLocal.MUNICIPIOS_BRASIL.get(uf + "|" + mun);
			return c == null ? null : new Resultado(c[0], c[1], "municipio");
		}
		String b = normalizar(bairro).replaceFirst("^BAIRRO ", "");
		if (!b.isEmpty()) {
			double[] c = TabelaLocal.BAIRROS.get(mun + "|" + semEspaco(b));
			if (c != null) {
				return new Resultado(c[0], c[1], "bairro");
			}
		}
		double[] c = TabelaLocal.MUNICIPIOS.get(mun);
		if (c == null) {
			c = TabelaLocal.MUNICIPIOS_BRASIL.get("RJ|" + mun);
		}
		return c == null ? null : new Resultado(c[0], c[1], "municipio");
	}

	// ---------------------------------------------------------------------------------------------
	// Padrao da unidade da sessao (Cad_unidades): municipioPadrao e latitude/longitude da unidade.
	// ---------------------------------------------------------------------------------------------

	public record PadraoUnidade(String municipio, Double latitude, Double longitude) {
		static final PadraoUnidade VAZIO = new PadraoUnidade("", null, null);

		boolean temPonto() {
			return latitude != null && longitude != null && dentroDoBrasil(latitude, longitude);
		}

		boolean ehOPonto(double lat, double lon) {
			return temPonto() && Math.abs(lat - latitude) < 1e-6 && Math.abs(lon - longitude) < 1e-6;
		}
	}

	public static PadraoUnidade padraoDaUnidade(String sigla) {
		if (sigla == null || sigla.isBlank()) {
			return PadraoUnidade.VAZIO;
		}
		ConfiguracaoUnidade u = ConfiguracaoUnidade.find("upper(sigla) = upper(?1)", sigla.trim()).firstResult();
		return u == null ? PadraoUnidade.VAZIO
				: new PadraoUnidade(txt(u.getMunicipioPadrao()), u.getLatitude(), u.getLongitude());
	}

	/** Municipio em branco: assume o municipio da unidade onde o paciente esta sendo cadastrado. */
	public static void completarMunicipio(Pacientes p, PadraoUnidade unidade) {
		if (txt(p.getMunicipio()).isEmpty() && !unidade.municipio().isEmpty()) {
			p.setMunicipio(unidade.municipio().toUpperCase());
		}
	}

	/** Sem localizacao propria: nem coordenada real, nem so o ponto da unidade. */
	public static boolean semLocalizacaoPropria(Pacientes p, PadraoUnidade unidade) {
		return !coordenadaReal(p.getLatitude(), p.getLongitude()) || unidade.ehOPonto(p.getLatitude(), p.getLongitude());
	}

	/**
	 * Aplica na hora (antes de gravar) a coordenada da tabela local; sem bairro nem municipio
	 * conhecido, o paciente fica no ponto da unidade (e nao mais no centro fixo de Itaguai).
	 */
	public static void aplicarCoordenadaLocal(Pacientes p, PadraoUnidade unidade) {
		Resultado r = coordenadaLocal(p.getBairro(), p.getMunicipio(), p.getEstado());
		if (r == null) {
			if (unidade.temPonto() && semLocalizacaoPropria(p, unidade)) {
				p.setLatitude(unidade.latitude());
				p.setLongitude(unidade.longitude());
			}
			return;
		}
		if ("municipio".equals(r.precisao()) && !semLocalizacaoPropria(p, unidade)) {
			return;
		}
		p.setLatitude(r.latitude());
		p.setLongitude(r.longitude());
	}
}

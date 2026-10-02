package br.com.iv.service;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class VideoProxyService implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final Pattern ATRIBUTO_URI = Pattern.compile("URI=\"([^\"]+)\"");

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(6))
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.build();

	public String buscarEReescreverManifesto(String urlOriginal, String baseProxy) {
		URI uriOriginal = validarUrl(urlOriginal);
		String conteudo = buscarTexto(uriOriginal);
		return reescrever(conteudo, uriOriginal, baseProxy);
	}

	// Busca crua, sem reescrever nada — usada para carregar listas M3U inteiras
	// (que listam vários canais, não os segmentos de um único stream) contornando
	// CORS. Diferente de buscarEReescreverManifesto, que é para um único manifesto HLS.
	public String buscarTextoBruto(String urlOriginal) {
		URI uriOriginal = validarUrl(urlOriginal);
		return buscarTexto(uriOriginal);
	}

	public byte[] buscarSegmento(String urlOriginal) {
		URI uriOriginal = validarUrl(urlOriginal);
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(uriOriginal)
					.timeout(Duration.ofSeconds(15))
					.header("User-Agent", "Mozilla/5.0 (GHBAM painel de chamada)")
					.GET()
					.build();
			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() >= 400) {
				throw new NotFoundException("Segmento não encontrado na origem (" + response.statusCode() + ")");
			}
			return response.body();
		} catch (IOException | InterruptedException e) {
			throw new NotFoundException("Erro ao buscar segmento na origem: " + e.getMessage());
		}
	}

	public boolean verificarDisponibilidade(String urlOriginal) {
		URI uriOriginal = validarUrl(urlOriginal);
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(uriOriginal)
					.timeout(Duration.ofSeconds(8))
					.header("User-Agent", "Mozilla/5.0 (GHBAM painel de chamada)")
					.GET()
					.build();

			// O InputStream permite encerrar a conexão assim que os cabeçalhos chegam,
			// sem baixar o manifesto inteiro ou manter um stream de vídeo aberto.
			HttpResponse<java.io.InputStream> response = client.send(
					request,
					HttpResponse.BodyHandlers.ofInputStream());
			try (java.io.InputStream corpo = response.body()) {
				return response.statusCode() >= 200 && response.statusCode() < 400;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (IOException e) {
			return false;
		}
	}

	private String buscarTexto(URI uri) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(uri)
					// Listas M3U completas (ex.: index.m3u do iptv-org) podem ter vários MB.
					.timeout(Duration.ofSeconds(25))
					.header("User-Agent", "Mozilla/5.0 (GHBAM painel de chamada)")
					.GET()
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new NotFoundException("Manifesto não encontrado na origem (" + response.statusCode() + ")");
			}
			return response.body();
		} catch (IOException | InterruptedException e) {
			throw new NotFoundException("Erro ao buscar manifesto na origem: " + e.getMessage());
		}
	}

	private String reescrever(String manifesto, URI urlBase, String baseProxy) {
		StringBuilder saida = new StringBuilder();
		for (String linhaBruta : manifesto.split("\r?\n", -1)) {
			String linha = linhaBruta;
			if (linha.startsWith("#EXT-X-KEY") || linha.startsWith("#EXT-X-MEDIA") || linha.startsWith("#EXT-X-MAP")) {
				linha = reescreverAtributoUri(linha, urlBase, baseProxy);
			} else if (!linha.isBlank() && !linha.startsWith("#")) {
				URI absoluta = urlBase.resolve(linha.trim());
				linha = montarUrlProxy(absoluta, baseProxy);
			}
			saida.append(linha).append('\n');
		}
		return saida.toString();
	}

	private String reescreverAtributoUri(String linha, URI urlBase, String baseProxy) {
		Matcher matcher = ATRIBUTO_URI.matcher(linha);
		if (!matcher.find()) {
			return linha;
		}
		URI absoluta = urlBase.resolve(matcher.group(1));
		String proxiada = montarUrlProxy(absoluta, baseProxy);
		return matcher.replaceFirst(Matcher.quoteReplacement("URI=\"" + proxiada + "\""));
	}

	private String montarUrlProxy(URI absoluta, String baseProxy) {
		String codificada = URLEncoder.encode(absoluta.toString(), StandardCharsets.UTF_8);
		boolean ehManifesto = absoluta.getPath() != null && absoluta.getPath().toLowerCase().endsWith(".m3u8");
		String caminho = ehManifesto ? "videoProxy/manifest?url=" : "videoProxy/segmento?url=";
		return baseProxy + caminho + codificada;
	}

	private URI validarUrl(String url) {
		if (url == null || url.isBlank()) {
			throw new BadRequestException("URL não informada");
		}
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("URL inválida");
		}
		String esquema = uri.getScheme();
		if (esquema == null || !(esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))) {
			throw new BadRequestException("Somente URLs http/https são permitidas");
		}
		if (uri.getHost() == null || ehHostPrivado(uri.getHost())) {
			throw new BadRequestException("Endereço não permitido");
		}
		return uri;
	}

	private boolean ehHostPrivado(String host) {
		try {
			InetAddress endereco = InetAddress.getByName(host);
			return endereco.isLoopbackAddress()
					|| endereco.isLinkLocalAddress()
					|| endereco.isSiteLocalAddress()
					|| endereco.isAnyLocalAddress()
					|| endereco.isMulticastAddress();
		} catch (UnknownHostException e) {
			return true;
		}
	}
}

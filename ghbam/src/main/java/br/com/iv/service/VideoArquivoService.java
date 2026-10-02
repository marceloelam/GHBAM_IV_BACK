package br.com.iv.service;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.iv.entity.UnidadeVideo;
import br.com.iv.repository.UnidadeVideoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class VideoArquivoService implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final Set<String> EXTENSOES_PERMITIDAS = Set.of("mp4", "webm", "ogg", "mov", "m3u8");

	@ConfigProperty(name = "app.painel-videos.dir")
	String baseDir;

	@Inject
	UnidadeVideoRepository unidadeVideoRepository;

	public List<String> listarArquivos(String sigla) {
		Path pasta = resolverPastaSigla(sigla);
		List<String> arquivos = new ArrayList<>();
		if (!Files.isDirectory(pasta)) {
			return arquivos;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(pasta)) {
			for (Path caminho : stream) {
				if (Files.isRegularFile(caminho) && temExtensaoPermitida(caminho.getFileName().toString())) {
					arquivos.add(caminho.getFileName().toString());
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Erro ao listar arquivos da pasta da unidade: " + e.getMessage(), e);
		}
		arquivos.sort(String::compareToIgnoreCase);
		return arquivos;
	}

	public Path resolverArquivo(String sigla, String arquivo) {
		validarNomeSimples(arquivo);
		Path pasta = resolverPastaSigla(sigla);
		Path caminho = pasta.resolve(arquivo).normalize();
		if (!caminho.startsWith(pasta) || !Files.isRegularFile(caminho)) {
			throw new NotFoundException("Arquivo não encontrado na pasta da unidade");
		}
		return caminho;
	}

	@Transactional
	public List<UnidadeVideo> sincronizar(String sigla, String urlBase) {
		List<String> arquivos = listarArquivos(sigla);
		List<UnidadeVideo> existentes = unidadeVideoRepository.listarPorSigla(sigla);

		int maiorOrdem = existentes.stream().mapToInt(UnidadeVideo::getOrdem).max().orElse(-1);

		for (String arquivo : arquivos) {
			String url = urlBase + "video/" + sigla + "/" + arquivo;
			boolean jaExiste = existentes.stream().anyMatch(v -> url.equals(v.getUrl()));
			if (jaExiste) {
				continue;
			}
			UnidadeVideo novo = new UnidadeVideo();
			novo.setSigla(sigla);
			novo.setTitulo(removerExtensao(arquivo));
			novo.setUrl(url);
			maiorOrdem++;
			novo.setOrdem(maiorOrdem);
			novo.persist();
		}

		return unidadeVideoRepository.listarPorSigla(sigla);
	}

	public String tipoConteudo(String arquivo) {
		String extensao = extensaoDe(arquivo);
		switch (extensao) {
			case "mp4":
				return "video/mp4";
			case "webm":
				return "video/webm";
			case "ogg":
				return "video/ogg";
			case "mov":
				return "video/quicktime";
			case "m3u8":
				return "application/vnd.apple.mpegurl";
			default:
				return "application/octet-stream";
		}
	}

	private Path resolverPastaSigla(String sigla) {
		validarNomeSimples(sigla);
		return Paths.get(baseDir, sigla).normalize();
	}

	private void validarNomeSimples(String valor) {
		if (valor == null || valor.isBlank() || valor.contains("..") || valor.contains("/") || valor.contains("\\")) {
			throw new IllegalArgumentException("Nome inválido: " + valor);
		}
	}

	private boolean temExtensaoPermitida(String nomeArquivo) {
		return EXTENSOES_PERMITIDAS.contains(extensaoDe(nomeArquivo));
	}

	private String extensaoDe(String nomeArquivo) {
		int ponto = nomeArquivo.lastIndexOf('.');
		return ponto < 0 ? "" : nomeArquivo.substring(ponto + 1).toLowerCase();
	}

	private String removerExtensao(String nomeArquivo) {
		int ponto = nomeArquivo.lastIndexOf('.');
		return ponto > 0 ? nomeArquivo.substring(0, ponto) : nomeArquivo;
	}
}

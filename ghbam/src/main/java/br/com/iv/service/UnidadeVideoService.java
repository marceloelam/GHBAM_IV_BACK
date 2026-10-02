package br.com.iv.service;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.UnidadeVideoDto;
import br.com.iv.entity.UnidadeVideo;
import br.com.iv.repository.UnidadeVideoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UnidadeVideoService implements Serializable {
	private static final long serialVersionUID = 1L;

	@Inject
	UnidadeVideoRepository unidadeVideoRepository;

	public List<UnidadeVideo> listarPorSigla(String sigla) {
		return unidadeVideoRepository.listarPorSigla(sigla);
	}

	public List<UnidadeVideo> listarAtivosPorSigla(String sigla) {
		return unidadeVideoRepository.listarAtivosPorSigla(sigla);
	}

	@Transactional
	public UnidadeVideo salvar(UnidadeVideoDto dto) {
		UnidadeVideo unidadeVideo = new UnidadeVideo();
		unidadeVideo.setSigla(dto.getSigla());
		unidadeVideo.setTitulo(dto.getTitulo());
		unidadeVideo.setUrl(dto.getUrl());
		unidadeVideo.setAtivo(dto.isAtivo());

		int maiorOrdem = unidadeVideoRepository.listarPorSigla(dto.getSigla()).stream()
				.mapToInt(UnidadeVideo::getOrdem).max().orElse(-1);
		unidadeVideo.setOrdem(maiorOrdem + 1);

		unidadeVideo.persist();
		return unidadeVideo;
	}

	@Transactional
	public void atualizar(int id, UnidadeVideoDto dto) {
		Optional<UnidadeVideo> unidadeVideoOp = UnidadeVideo.findByIdOptional(id);
		if (unidadeVideoOp.isEmpty()) {
			throw new EntityNotFoundException("Vídeo do painel não foi encontrado...");
		}

		UnidadeVideo unidadeVideo = unidadeVideoOp.get();
		unidadeVideo.setTitulo(dto.getTitulo());
		unidadeVideo.setUrl(dto.getUrl());
		unidadeVideo.setAtivo(dto.isAtivo());
	}

	@Transactional
	public void remover(int id) {
		Optional<UnidadeVideo> unidadeVideoOp = UnidadeVideo.findByIdOptional(id);
		if (unidadeVideoOp.isEmpty()) {
			return;
		}
		unidadeVideoOp.get().delete();
	}

	@Transactional
	public void reordenar(String sigla, List<Integer> idsEmOrdem) {
		List<UnidadeVideo> videos = unidadeVideoRepository.listarPorSigla(sigla);
		for (int posicao = 0; posicao < idsEmOrdem.size(); posicao++) {
			int idAlvo = idsEmOrdem.get(posicao);
			int posicaoFinal = posicao;
			videos.stream()
					.filter(v -> v.getId() == idAlvo)
					.findFirst()
					.ifPresent(v -> v.setOrdem(posicaoFinal));
		}
	}

	@Transactional
	public void renomearSigla(String siglaAntiga, String siglaNova) {
		if (siglaAntiga == null || siglaNova == null || siglaAntiga.equals(siglaNova)) {
			return;
		}
		unidadeVideoRepository.listarPorSigla(siglaAntiga).forEach(v -> v.setSigla(siglaNova));
	}

	@Transactional
	public UnidadeVideo verificar(int id) {
		Optional<UnidadeVideo> unidadeVideoOp = UnidadeVideo.findByIdOptional(id);
		if (unidadeVideoOp.isEmpty()) {
			throw new EntityNotFoundException("Vídeo do painel não foi encontrado...");
		}

		UnidadeVideo unidadeVideo = unidadeVideoOp.get();
		unidadeVideo.setStatusVerificacao(testarUrl(unidadeVideo.getUrl()));
		unidadeVideo.setVerificadoEm(LocalDateTime.now());
		return unidadeVideo;
	}

	private String testarUrl(String url) {
		try {
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(5))
					.followRedirects(HttpClient.Redirect.ALWAYS)
					.build();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(6))
					.header("User-Agent", "Mozilla/5.0 (GHBAM painel de chamada)")
					.header("Origin", "https://ghbam.com.br")
					.GET()
					.build();
			HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() >= 400) {
				return "offline";
			}

			// O GHBAM roda em HTTPS em produção — o navegador bloqueia (ou tenta
			// "upgradar" e falha) qualquer link http:// puro carregado dentro de
			// uma página https://, mesmo que o servidor responda normalmente
			// aqui (essa checagem roda no backend, que não tem essa restrição).
			if (url.toLowerCase().startsWith("http://")) {
				return "http_inseguro";
			}

			// CORS só importa para links .m3u8 (o navegador busca o manifesto via
			// JavaScript/hls.js). Links normais de iframe (YouTube, Vimeo, StreamHub
			// etc.) e arquivos de vídeo direto não precisam de CORS para tocar — não
			// faz sentido marcá-los como bloqueados por causa disso.
			if (!ehStreamHls(url)) {
				return "online";
			}

			// O servidor respondeu, mas se não expõe Access-Control-Allow-Origin, o
			// navegador vai bloquear o hls.js de ler o manifesto (CORS) mesmo o link
			// estando no ar — sinalizamos esse caso separado de "online" de verdade.
			boolean temCors = response.headers().firstValue("access-control-allow-origin").isPresent();
			return temCors ? "online" : "sem_cors";
		} catch (Exception e) {
			return "offline";
		}
	}

	private boolean ehStreamHls(String url) {
		return url != null && url.toLowerCase().split("\\?")[0].endsWith(".m3u8");
	}
}

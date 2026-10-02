package br.com.iv.service;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import br.com.iv.dto.UnidadeVideoCanalDto;
import br.com.iv.dto.UnidadeVideoConfigDto;
import br.com.iv.dto.UnidadeVideoVolumeDto;
import br.com.iv.entity.UnidadeVideoCanal;
import br.com.iv.entity.UnidadeVideoConfig;
import br.com.iv.entity.UnidadeVideoVolume;
import br.com.iv.repository.UnidadeVideoCanalRepository;
import br.com.iv.repository.UnidadeVideoConfigRepository;
import br.com.iv.repository.UnidadeVideoVolumeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UnidadeVideoConfigService implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final int LIMITE_CANAIS = 5;

	@Inject
	UnidadeVideoConfigRepository unidadeVideoConfigRepository;

	@Inject
	UnidadeVideoVolumeRepository unidadeVideoVolumeRepository;

	@Inject
	UnidadeVideoCanalRepository unidadeVideoCanalRepository;

	public UnidadeVideoConfig buscar(String sigla) {
		return unidadeVideoConfigRepository.buscarPorSigla(sigla).orElseGet(() -> {
			UnidadeVideoConfig padrao = new UnidadeVideoConfig();
			padrao.setSigla(sigla);
			return padrao;
		});
	}

	@Transactional
	public UnidadeVideoConfig salvar(String sigla, UnidadeVideoConfigDto dto) {
		UnidadeVideoConfig config = unidadeVideoConfigRepository.buscarPorSigla(sigla).orElseGet(() -> {
			UnidadeVideoConfig novo = new UnidadeVideoConfig();
			novo.setSigla(sigla);
			return novo;
		});
		config.setTipoTransicao(dto.getTipoTransicao());
		config.setDuracaoTransicao(dto.getDuracaoTransicao());
		config.setMudo(dto.isMudo());
		config.setVolume(dto.getVolume());
		config.setModo(dto.getModo());
		config.setUrlM3u(dto.getUrlM3u());
		config.setTituloM3u(dto.getTituloM3u());

		if (config.isPersistent()) {
			return config;
		}
		config.persist();
		return config;
	}

	public UnidadeVideoVolumeDto buscarVolume(String sigla, int tv) {
		String siglaNormalizada = validarSiglaETv(sigla, tv);
		return unidadeVideoVolumeRepository.buscarPorSiglaETv(siglaNormalizada, tv)
				.map(this::paraVolumeDto)
				.orElseGet(() -> {
					UnidadeVideoConfig configUnidade = buscar(siglaNormalizada);
					return new UnidadeVideoVolumeDto(
							configUnidade.isMudo(), configUnidade.getVolume(), null,
							configUnidade.getTituloM3u(), null, null, configUnidade.getUrlM3u());
				});
	}

	@Transactional
	public UnidadeVideoVolumeDto salvarVolume(String sigla, int tv, UnidadeVideoVolumeDto dto) {
		String siglaNormalizada = validarSiglaETv(sigla, tv);
		if (dto == null) {
			throw new IllegalArgumentException("Os dados de volume são obrigatórios.");
		}
		if (dto.getVolume() < 0 || dto.getVolume() > 100) {
			throw new IllegalArgumentException("O volume deve estar entre 0 e 100.");
		}

		UnidadeVideoVolume volume = unidadeVideoVolumeRepository.buscarPorSiglaETv(siglaNormalizada, tv)
				.orElseGet(() -> criarVolumeComPadraoDaUnidade(siglaNormalizada, tv));
		volume.setMudo(dto.isMudo());
		volume.setVolume(dto.getVolume());
		if (!volume.isPersistent()) {
			volume.persist();
		}
		return paraVolumeDto(volume);
	}

	public List<UnidadeVideoCanalDto> listarCanais(String sigla) {
		String siglaNormalizada = validarSigla(sigla);
		return unidadeVideoCanalRepository.listarPorSigla(siglaNormalizada).stream()
				.map(this::paraCanalDto)
				.toList();
	}

	@Transactional
	public UnidadeVideoCanalDto adicionarCanal(String sigla, UnidadeVideoCanalDto dto) {
		String siglaNormalizada = validarSigla(sigla);
		validarCanal(dto);
		String url = dto.getUrl().trim();

		return unidadeVideoCanalRepository.buscarPorSiglaEUrl(siglaNormalizada, url)
				.map(this::paraCanalDto)
				.orElseGet(() -> {
					List<UnidadeVideoCanal> canais = unidadeVideoCanalRepository.listarPorSigla(siglaNormalizada);
					if (canais.size() >= LIMITE_CANAIS) {
						throw new IllegalStateException("A unidade já possui o limite de 5 canais adicionados.");
					}

					UnidadeVideoCanal canal = new UnidadeVideoCanal();
					canal.setSigla(siglaNormalizada);
					canal.setNome(dto.getNome().trim());
					canal.setDescricao(limparOpcional(dto.getDescricao()));
					canal.setLogo(limparOpcional(dto.getLogo()));
					canal.setUrl(url);
					canal.setOrdem(proximaOrdemDisponivel(canais));
					canal.persist();
					return paraCanalDto(canal);
				});
	}

	@Transactional
	public void removerCanal(String sigla, Long canalId) {
		String siglaNormalizada = validarSigla(sigla);
		if (canalId == null || canalId <= 0) {
			throw new IllegalArgumentException("O canal informado é inválido.");
		}
		UnidadeVideoCanal canal = unidadeVideoCanalRepository.buscarPorIdESigla(canalId, siglaNormalizada)
				.orElseThrow(() -> new IllegalArgumentException("Canal não encontrado para esta unidade."));
		canal.delete();
	}

	@Transactional
	public UnidadeVideoVolumeDto selecionarCanal(String sigla, int tv, Long canalId) {
		String siglaNormalizada = validarSiglaETv(sigla, tv);
		if (canalId == null || canalId <= 0) {
			throw new IllegalArgumentException("O canal informado é inválido.");
		}
		unidadeVideoCanalRepository.buscarPorIdESigla(canalId, siglaNormalizada)
				.orElseThrow(() -> new IllegalArgumentException("Canal não encontrado para esta unidade."));

		UnidadeVideoVolume volume = unidadeVideoVolumeRepository.buscarPorSiglaETv(siglaNormalizada, tv)
				.orElseGet(() -> criarVolumeComPadraoDaUnidade(siglaNormalizada, tv));
		volume.setCanalId(canalId);
		if (!volume.isPersistent()) {
			volume.persist();
		}
		return paraVolumeDto(volume);
	}

	private String validarSiglaETv(String sigla, int tv) {
		String siglaNormalizada = validarSigla(sigla);
		if (tv < 1 || tv > 10) {
			throw new IllegalArgumentException("A televisão deve estar entre 1 e 10.");
		}
		return siglaNormalizada;
	}

	private String validarSigla(String sigla) {
		String siglaNormalizada = sigla == null ? "" : sigla.trim().toUpperCase(Locale.ROOT);
		if (siglaNormalizada.isBlank() || siglaNormalizada.length() > 15) {
			throw new IllegalArgumentException("A sigla da unidade é inválida.");
		}
		return siglaNormalizada;
	}

	private void validarCanal(UnidadeVideoCanalDto dto) {
		if (dto == null || dto.getNome() == null || dto.getNome().isBlank()) {
			throw new IllegalArgumentException("O nome do canal é obrigatório.");
		}
		if (dto.getNome().trim().length() > 160) {
			throw new IllegalArgumentException("O nome do canal deve ter no máximo 160 caracteres.");
		}
		if (dto.getDescricao() != null && dto.getDescricao().trim().length() > 160) {
			throw new IllegalArgumentException("A descrição do canal deve ter no máximo 160 caracteres.");
		}
		if (dto.getLogo() != null && dto.getLogo().trim().length() > 8000) {
			throw new IllegalArgumentException("O endereço do logotipo é muito longo.");
		}
		if (dto.getUrl() == null || dto.getUrl().isBlank() || dto.getUrl().trim().length() > 8000) {
			throw new IllegalArgumentException("O endereço do canal é inválido.");
		}
		try {
			URI uri = new URI(dto.getUrl().trim());
			if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
				throw new IllegalArgumentException("O endereço do canal deve usar HTTP ou HTTPS.");
			}
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("O endereço do canal é inválido.");
		}
	}

	private UnidadeVideoVolume criarVolumeComPadraoDaUnidade(String sigla, int tv) {
		UnidadeVideoConfig config = buscar(sigla);
		UnidadeVideoVolume volume = new UnidadeVideoVolume();
		volume.setSigla(sigla);
		volume.setTv(tv);
		volume.setMudo(config.isMudo());
		volume.setVolume(config.getVolume());
		return volume;
	}

	private UnidadeVideoVolumeDto paraVolumeDto(UnidadeVideoVolume volume) {
		UnidadeVideoCanal canal = volume.getCanalId() == null
				? null
				: unidadeVideoCanalRepository.findById(volume.getCanalId());
		UnidadeVideoConfig configUnidade = canal == null ? buscar(volume.getSigla()) : null;
		return new UnidadeVideoVolumeDto(
				volume.isMudo(),
				volume.getVolume(),
				canal == null ? null : canal.getId(),
				canal == null ? configUnidade.getTituloM3u() : canal.getNome(),
				canal == null ? null : canal.getDescricao(),
				canal == null ? null : canal.getLogo(),
				canal == null ? configUnidade.getUrlM3u() : canal.getUrl());
	}

	private UnidadeVideoCanalDto paraCanalDto(UnidadeVideoCanal canal) {
		return new UnidadeVideoCanalDto(
				canal.getId(), canal.getNome(), canal.getDescricao(), canal.getLogo(), canal.getUrl(), canal.getOrdem());
	}

	private int proximaOrdemDisponivel(List<UnidadeVideoCanal> canais) {
		for (int ordem = 1; ordem <= LIMITE_CANAIS; ordem++) {
			final int candidata = ordem;
			if (canais.stream().noneMatch(canal -> canal.getOrdem() == candidata)) {
				return ordem;
			}
		}
		throw new IllegalStateException("A unidade já possui o limite de 5 canais adicionados.");
	}

	private String limparOpcional(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}
}

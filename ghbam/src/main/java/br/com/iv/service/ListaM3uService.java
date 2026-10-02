package br.com.iv.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.NoSuchElementException;

import br.com.iv.dto.ListaM3uDto;
import br.com.iv.entity.ListaM3u;
import br.com.iv.repository.ListaM3uRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ListaM3uService {
	@Inject
	ListaM3uRepository listaM3uRepository;

	public List<ListaM3uDto> listar() {
		return listaM3uRepository.listarOrdenadas().stream()
				.map(this::paraDto)
				.toList();
	}

	@Transactional
	public ListaM3uDto criar(ListaM3uDto dto) {
		DadosValidados dados = validar(dto);
		if (listaM3uRepository.buscarPorNome(dados.nome()).isPresent()) {
			throw new IllegalStateException("Já existe uma lista M3U com esse nome.");
		}

		ListaM3u lista = new ListaM3u();
		lista.setNome(dados.nome());
		lista.setUrl(dados.url());
		listaM3uRepository.persistAndFlush(lista);
		return paraDto(lista);
	}

	@Transactional
	public ListaM3uDto atualizar(Long id, ListaM3uDto dto) {
		validarId(id);
		DadosValidados dados = validar(dto);
		ListaM3u lista = listaM3uRepository.findByIdOptional(id)
				.orElseThrow(() -> new NoSuchElementException("Lista M3U não encontrada."));

		if (listaM3uRepository.buscarOutraPorNome(id, dados.nome()).isPresent()) {
			throw new IllegalStateException("Já existe uma lista M3U com esse nome.");
		}

		lista.setNome(dados.nome());
		lista.setUrl(dados.url());
		return paraDto(lista);
	}

	@Transactional
	public void excluir(Long id) {
		validarId(id);
		ListaM3u lista = listaM3uRepository.findByIdOptional(id)
				.orElseThrow(() -> new NoSuchElementException("Lista M3U não encontrada."));
		lista.delete();
	}

	private DadosValidados validar(ListaM3uDto dto) {
		if (dto == null || dto.getNome() == null || dto.getNome().isBlank()) {
			throw new IllegalArgumentException("O nome da lista M3U é obrigatório.");
		}
		String nome = dto.getNome().trim();
		if (nome.length() > 160) {
			throw new IllegalArgumentException("O nome da lista M3U deve ter no máximo 160 caracteres.");
		}

		if (dto.getUrl() == null || dto.getUrl().isBlank()) {
			throw new IllegalArgumentException("O link da lista M3U é obrigatório.");
		}
		String url = dto.getUrl().trim();
		if (url.length() > 8000) {
			throw new IllegalArgumentException("O link da lista M3U é muito longo.");
		}
		try {
			URI uri = new URI(url);
			if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
				throw new IllegalArgumentException("O link da lista M3U deve usar HTTP ou HTTPS.");
			}
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("O link da lista M3U é inválido.");
		}
		return new DadosValidados(nome, url);
	}

	private void validarId(Long id) {
		if (id == null || id <= 0) {
			throw new IllegalArgumentException("A lista M3U informada é inválida.");
		}
	}

	private ListaM3uDto paraDto(ListaM3u lista) {
		return new ListaM3uDto(lista.getId(), lista.getNome(), lista.getUrl());
	}

	private record DadosValidados(String nome, String url) {
	}
}

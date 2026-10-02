package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;

import br.com.iv.entity.Protocolo;
import br.com.iv.repository.ProtocoloRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ProtocoloService {

	@Inject
	ProtocoloRepository protocoloRepository;

	public List<Protocolo> listarTodos() {
		return protocoloRepository.listarTodos();
	}

	public Protocolo buscarPorId(int id) {
		return protocoloRepository.findById((long) id);
	}

	public List<Protocolo> buscarPorEspecialidade(String especialidade) {
		return protocoloRepository.buscarPorEspecialidade(especialidade);
	}

	public Protocolo buscarPorNome(String nome) {
		return protocoloRepository.buscarPorNome(nome);
	}

	@Transactional
	public Protocolo salvar(Protocolo protocolo) {
		protocolo.setDataAtualizacao(LocalDateTime.now());
		if (protocolo.getDataCriacao() == null) {
			protocolo.setDataCriacao(LocalDateTime.now());
		}
		protocoloRepository.persist(protocolo);
		return protocolo;
	}

	@Transactional
	public Protocolo atualizar(int id, Protocolo dados) {
		Protocolo protocolo = protocoloRepository.findById((long) id);
		if (protocolo == null) {
			return null;
		}
		protocolo.setNome(dados.getNome());
		protocolo.setEspecialidade(dados.getEspecialidade());
		protocolo.setDescricao(dados.getDescricao());
		protocolo.setVersao(dados.getVersao());
		protocolo.setPerguntasJson(dados.getPerguntasJson());
		protocolo.setCamposJson(dados.getCamposJson());
		protocolo.setRegrasJson(dados.getRegrasJson());
		protocolo.setAtualizadoPor(dados.getAtualizadoPor());
		protocolo.setDataAtualizacao(LocalDateTime.now());
		return protocolo;
	}

	@Transactional
	public boolean deletar(int id) {
		Protocolo protocolo = protocoloRepository.findById((long) id);
		if (protocolo == null) {
			return false;
		}
		protocolo.setAtivo(false);
		return true;
	}
}

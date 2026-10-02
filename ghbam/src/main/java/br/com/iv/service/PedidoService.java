package br.com.iv.service;

import java.util.List;

import br.com.iv.dto.PedidoDTO;
import br.com.iv.dto.PedidoItemDTO;
import br.com.iv.entity.Pedido;
import br.com.iv.entity.PedidoItem;
import br.com.iv.dto.PedidoItensDto;
import br.com.iv.repository.PedidoItemRepository;
import br.com.iv.repository.PedidoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PedidoService {

	@Inject
	PedidoRepository repository;
	@Inject
	PedidoItemRepository itemRepository;

	@Transactional
	public Pedido create(PedidoDTO dto) {
		Pedido p = new Pedido();
		p.setOrigem(dto.getOrigem());
		p.setUnidade(dto.getUnidade());
		p.setUnidadeSigla(dto.getUnidadeSigla());
		p.setComentario(dto.getComentario());

		repository.persist(p);

		if (dto.getItens() != null && !dto.getItens().isEmpty()) {
			for (PedidoItemDTO itemDto : dto.getItens()) {
				PedidoItem item = toEntity(itemDto);
				p.addItem(item);
			}
		}

		return p;
	}

	public List<Pedido> findAll() {
		return repository.listAll();
	}

	public List<br.com.iv.dto.PedidoItensDto> findByUnidade(String unidade, String data) {
		return repository.findByUnidade(unidade, data);
	}

	public Pedido findById(Long id) {
		return repository.findById(id);
	}

	public List<PedidoItensDto> findByIdItem(int id) {
		return itemRepository.findByIdItem(id);
	}

	@Transactional
	public void delete(Long id) {
		repository.deleteById(id);
	}

	private PedidoItem toEntity(PedidoItemDTO dto) {
		PedidoItem item = new PedidoItem();
		item.setMaterial(dto.getMaterial());
		item.setQuantidade(dto.getQuantidade());
		item.setObservacao(dto.getObservacao());
		item.setRecebido(dto.isRecebido());
		return item;
	}

	public void itemRecebido(int id) {
		repository.itemRecebido(id);
	}
}

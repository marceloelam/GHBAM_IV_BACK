package br.com.iv.repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import br.com.iv.dto.PedidoItensDto;
import br.com.iv.entity.Pedido;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class PedidoRepository implements PanacheRepository<Pedido> {
	@Inject
	EntityManager entityManager;

	@SuppressWarnings("unchecked")
	public List<PedidoItensDto> findByUnidade(String unidade, String data) {
		String sql = "select p.id as pedido_id, p.unidade, p.unidadesigla, p.comentario, p.criadoem, "
				+ "i.id as item_id, i.material, i.quantidade, i.observacao, "
				+ "i.quantidadeliberada, i.msgresposta, i.ci "
				+ "from pedido p join pedido_item i on i.pedido_id = p.id "
				+ "where p.origem = :unidade "
				+ "AND cast(p.criadoem as date) = cast(:data as date) order by i.pedido_id";

		List<Object[]> results = getEntityManager().createNativeQuery(sql).setParameter("unidade", unidade)
				.setParameter("data", data).getResultList();

		List<PedidoItensDto> pd = new ArrayList<>();

		for (Object[] row : results) {
			PedidoItensDto dto = new PedidoItensDto();

			// indices from select: 0=pedido_id,1=unidade,2=unidadesigla,3=comentario,4=criadoem,
			// 5=item_id,6=material,7=quantidade,8=observacao,9=quantidadeliberada,10=msgresposta,11=ci
			
			Long pedidoId = row[0] != null ? ((Number) row[0]).longValue() : null;
			if (pedidoId != null) {
				Pedido pedido = new Pedido();
				pedido.setId(pedidoId);
				dto.setPedido(pedido);
			}
			dto.setUnidade(row[1] != null ? row[1].toString() : null);
			dto.setUnidadeSigla(row[2] != null ? row[2].toString() : null);
			dto.setComentario(row[3] != null ? row[3].toString() : null);
			dto.setCriadoEm(row[4] != null ? ((Date) row[4]).toLocalDate() : null);
			dto.setId(row[5] != null ? ((Number) row[5]).longValue() : null);
			dto.setMaterial(row[6] != null ? row[6].toString() : null);
			if (row[7] != null) dto.setQuantidade(((Number) row[7]).intValue());
			dto.setObservacao(row[8] != null ? row[8].toString() : null);
			
			dto.setQuantidadeliberada(row[9] != null ? ((Number) row[9]).intValue() : 0);
			dto.setMsgresposta(row[10] != null ? row[10].toString() : null);
			dto.setCi(row[11] != null ? row[11].toString() : null);

			pd.add(dto);
		}

		return pd;

	}

	public void itemRecebido(int id) {
		String sql = "UPDATE pedido_item set recebido = true  where id = :id";
		entityManager.createNativeQuery(sql).setParameter("id", id).executeUpdate();
		
	}
}

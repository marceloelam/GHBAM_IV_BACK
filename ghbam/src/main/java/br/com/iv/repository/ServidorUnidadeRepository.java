package br.com.iv.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.ServidorUnidade;

@ApplicationScoped
public class ServidorUnidadeRepository implements PanacheRepository<ServidorUnidade> {

	// Limite de negocio: ate 6 unidades vinculadas por servidor (principal + 5 adicionais).
	public static final int LIMITE_UNIDADES_POR_SERVIDOR = 6;

	public List<ServidorUnidade> listarPorServidor(int idservidor) {
		return list("idservidor", idservidor);
	}

	public List<String> listarSiglasPorServidor(int idservidor) {
		return listarPorServidor(idservidor).stream().map(ServidorUnidade::getUnidade).toList();
	}

	public boolean pertenceAUnidade(int idservidor, String unidade) {
		if (unidade == null || unidade.isBlank()) return false;
		return count("idservidor = ?1 and upper(unidade) = ?2", idservidor, unidade.trim().toUpperCase()) > 0;
	}

	@Transactional
	public void substituirVinculos(int idservidor, List<String> unidades) {
		delete("idservidor", idservidor);
		if (unidades == null) return;
		for (int i = 0; i < unidades.size() && i < LIMITE_UNIDADES_POR_SERVIDOR; i++) {
			String sigla = unidades.get(i);
			if (sigla == null || sigla.isBlank()) continue;
			ServidorUnidade vinculo = new ServidorUnidade();
			vinculo.setIdservidor(idservidor);
			vinculo.setUnidade(sigla.trim().toUpperCase());
			vinculo.setPrincipal(i == 0);
			persist(vinculo);
		}
	}
}

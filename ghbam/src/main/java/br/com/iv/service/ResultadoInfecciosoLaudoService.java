package br.com.iv.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Resultado infeccioso lido do PDF, so para a vigilancia. Fica fora de pedidoresultadolaboratorio_item porque item sem
 * LOINC trava o laudo na fila RNDS. Transacao propria: uma falha aqui nunca pode impedir o laudo de ser arquivado.
 */
@ApplicationScoped
public class ResultadoInfecciosoLaudoService {

    private static final Set<String> RESULTADOS = Set.of("REAGENTE", "NAO_REAGENTE");
    private static final int MAXIMO_POR_LAUDO = 30;

    @Inject EntityManager em;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void gravar(int idPedido, int bam, List<Map<String, String>> resultados) {
        if (resultados == null) return;
        resultados.stream().limit(MAXIMO_POR_LAUDO).forEach(item -> {
            String nome = item == null ? "" : String.valueOf(item.getOrDefault("nomeExame", "")).trim();
            String resultado = item == null ? "" : String.valueOf(item.getOrDefault("resultado", "")).trim();
            if (nome.isEmpty() || !RESULTADOS.contains(resultado)) return;
            em.createNativeQuery("INSERT INTO laboratorio_resultado_infeccioso (idpedidoresultado, bam, nome_exame, resultado) VALUES (:p, :b, :n, :r)")
                    .setParameter("p", idPedido).setParameter("b", bam)
                    .setParameter("n", nome.length() > 120 ? nome.substring(0, 120) : nome).setParameter("r", resultado)
                    .executeUpdate();
        });
    }
}

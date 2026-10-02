package br.com.iv.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.CirurgiaListaEspera;

@ApplicationScoped
public class CirurgiaListaEsperaRepository implements PanacheRepository<CirurgiaListaEspera> {

    private static final Map<String, Integer> PESO_PRIORIDADE = Map.of(
            "EMERGENCIA", 0,
            "URGENCIA", 1,
            "PRIORIDADE", 2,
            "ELETIVA", 3);

    public List<CirurgiaListaEspera> fila(String unidade, String status, String especialidade) {
        StringBuilder jpql = new StringBuilder("1 = 1");
        List<Object> params = new ArrayList<>();
        if (unidade != null && !unidade.isBlank()) {
            jpql.append(" and unidade = ?").append(params.size() + 1);
            params.add(unidade);
        }
        if (status != null && !status.isBlank()) {
            jpql.append(" and status = ?").append(params.size() + 1);
            params.add(status);
        }
        if (especialidade != null && !especialidade.isBlank()) {
            jpql.append(" and especialidade = ?").append(params.size() + 1);
            params.add(especialidade);
        }
        List<CirurgiaListaEspera> itens = new ArrayList<>(list(jpql.toString(), params.toArray()));
        itens.sort(Comparator
                .comparingInt((CirurgiaListaEspera e) -> PESO_PRIORIDADE.getOrDefault(e.getPrioridade(), 9))
                .thenComparing(CirurgiaListaEspera::getDataInclusao));
        return itens;
    }

    public long contarAguardando(String unidade) {
        if (unidade == null || unidade.isBlank()) {
            return count("status", "AGUARDANDO");
        }
        return count("unidade = ?1 and status = ?2", unidade, "AGUARDANDO");
    }
}

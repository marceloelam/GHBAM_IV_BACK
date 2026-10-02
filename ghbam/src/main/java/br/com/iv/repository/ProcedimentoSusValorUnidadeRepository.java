package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.ProcedimentoSusValorUnidade;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcedimentoSusValorUnidadeRepository implements PanacheRepository<ProcedimentoSusValorUnidade> {

    public ProcedimentoSusValorUnidade buscar(String unidade, String paId, String paDv) {
        List<ProcedimentoSusValorUnidade> result = find(
                "unidade = ?1 and pa_id = ?2 and pa_dv = ?3",
                unidade,
                paId,
                paDv).list();
        return result.isEmpty() ? null : result.get(0);
    }
}

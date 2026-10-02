package br.com.iv.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.FaturamentoCompetencia;

@ApplicationScoped
public class FaturamentoCompetenciaRepository implements PanacheRepository<FaturamentoCompetencia> {

    public FaturamentoCompetencia buscarPorCompetencia(String competencia) {
        return find("competencia", competencia).firstResult();
    }

    public FaturamentoCompetencia buscarPorCompetenciaUnidade(String competencia, String unidade) {
        return find("competencia = ?1 and upper(unidade) = ?2", competencia, normalizarUnidade(unidade)).firstResult();
    }

    public List<FaturamentoCompetencia> listarTodas() {
        return listAll(Sort.by("competencia").descending());
    }

    public List<FaturamentoCompetencia> listarPorUnidade(String unidade) {
        return find("upper(unidade) = ?1", Sort.by("competencia").descending(), normalizarUnidade(unidade)).list();
    }

    private String normalizarUnidade(String unidade) {
        return unidade == null ? "" : unidade.split(":")[0].trim().toUpperCase();
    }
}

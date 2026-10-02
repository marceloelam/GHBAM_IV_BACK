package br.com.iv.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.FaturamentoSigtapImportacao;

@ApplicationScoped
public class FaturamentoSigtapImportacaoRepository implements PanacheRepository<FaturamentoSigtapImportacao> {

    public List<FaturamentoSigtapImportacao> listarTodas() {
        return listAll(Sort.by("dataImportacao").descending());
    }

    public List<FaturamentoSigtapImportacao> listarPorUnidade(String unidade) {
        return find("upper(unidade) = ?1", Sort.by("dataImportacao").descending(), normalizarUnidade(unidade)).list();
    }

    public FaturamentoSigtapImportacao buscarUltimaPorCompetencia(String competencia) {
        return find("competencia", Sort.by("dataImportacao").descending(), competencia).firstResult();
    }

    public FaturamentoSigtapImportacao buscarUltimaPorCompetenciaUnidade(String competencia, String unidade) {
        return find("competencia = ?1 and upper(unidade) = ?2", Sort.by("dataImportacao").descending(), competencia, normalizarUnidade(unidade)).firstResult();
    }

    public long contarPorCompetencia(String competencia) {
        return count("competencia", competencia);
    }

    private String normalizarUnidade(String unidade) {
        return unidade == null ? "" : unidade.split(":")[0].trim().toUpperCase();
    }
}

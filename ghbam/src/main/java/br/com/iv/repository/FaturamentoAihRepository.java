package br.com.iv.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.FaturamentoAih;

@ApplicationScoped
public class FaturamentoAihRepository implements PanacheRepository<FaturamentoAih> {

    public FaturamentoAih buscarPorInternacao(int idInternacao) {
        return find("idInternacao", idInternacao).firstResult();
    }

    public List<FaturamentoAih> listar(Integer idcompetencia) {
        return listar(idcompetencia, null);
    }

    public List<FaturamentoAih> listar(Integer idcompetencia, String unidade) {
        boolean filtraUnidade = unidade != null && !unidade.trim().isBlank() && !"TODAS".equalsIgnoreCase(unidade);
        if (idcompetencia == null) {
            if (filtraUnidade) {
                return list("upper(unidade) = ?1", Sort.by("criadoEm").descending(), unidade.trim().toUpperCase());
            }
            return listAll(Sort.by("criadoEm").descending());
        }
        if (filtraUnidade) {
            return list("idcompetencia = ?1 and upper(unidade) = ?2", Sort.by("criadoEm").descending(),
                    idcompetencia, unidade.trim().toUpperCase());
        }
        return list("idcompetencia", Sort.by("criadoEm").descending(), idcompetencia);
    }

    public long contarPorCompetenciaETipo(int idcompetencia) {
        return count("idcompetencia", idcompetencia);
    }
}

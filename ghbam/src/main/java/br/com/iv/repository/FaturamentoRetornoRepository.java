package br.com.iv.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.FaturamentoRetorno;

@ApplicationScoped
public class FaturamentoRetornoRepository implements PanacheRepository<FaturamentoRetorno> {

    public List<FaturamentoRetorno> listar() {
        return listAll(Sort.by("dataImportacao").descending());
    }

    public List<FaturamentoRetorno> listar(String unidade) {
        if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)) {
            return list("upper(unidade) = ?1", Sort.by("dataImportacao").descending(),
                    unidade.trim().toUpperCase());
        }
        return listar();
    }
}

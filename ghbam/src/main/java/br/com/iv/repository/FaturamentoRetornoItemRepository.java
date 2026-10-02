package br.com.iv.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.FaturamentoRetornoItem;

@ApplicationScoped
public class FaturamentoRetornoItemRepository implements PanacheRepository<FaturamentoRetornoItem> {

    public List<FaturamentoRetornoItem> listarPorRetorno(int idretorno) {
        return list("idretorno", idretorno);
    }
}

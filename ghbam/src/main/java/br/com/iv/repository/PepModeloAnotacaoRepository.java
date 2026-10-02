package br.com.iv.repository;

import java.util.List;

import br.com.iv.entity.PepModeloAnotacao;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PepModeloAnotacaoRepository implements PanacheRepository<PepModeloAnotacao> {

    public List<PepModeloAnotacao> listarPorUnidade(String unidade) {
        return list("unidade = ?1 order by atualizadoEm desc", unidade);
    }
}

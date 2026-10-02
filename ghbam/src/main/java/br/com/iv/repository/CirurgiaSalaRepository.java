package br.com.iv.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.CirurgiaSala;

@ApplicationScoped
public class CirurgiaSalaRepository implements PanacheRepository<CirurgiaSala> {

    public List<CirurgiaSala> listarPorUnidade(String unidade, boolean somenteAtivas) {
        if (unidade == null || unidade.isBlank()) {
            return somenteAtivas ? list("ativa", Sort.by("nome"), true) : listAll(Sort.by("nome"));
        }
        if (somenteAtivas) {
            return list("unidade = ?1 and ativa = ?2", Sort.by("nome"), unidade, true);
        }
        return list("unidade", Sort.by("nome"), unidade);
    }

    public boolean nomeDuplicado(String unidade, String nome, Integer idsalaIgnorar) {
        List<CirurgiaSala> encontradas = list("unidade = ?1 and lower(nome) = ?2", unidade, nome.toLowerCase());
        return encontradas.stream()
                .anyMatch(s -> idsalaIgnorar == null || !idsalaIgnorar.equals(s.getIdsala()));
    }
}

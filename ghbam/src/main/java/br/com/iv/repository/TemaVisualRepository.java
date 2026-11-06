package br.com.iv.repository;

import java.util.Optional;

import br.com.iv.entity.TemaVisual;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TemaVisualRepository implements PanacheRepository<TemaVisual> {

    public Optional<TemaVisual> buscarPorUnidade(String unidade) {
        return find("upper(unidade) = ?1", normalizarUnidade(unidade)).firstResultOptional();
    }

    public Optional<TemaVisual> buscarMaisRecente() {
        return find("ORDER BY atualizadoEm DESC NULLS LAST, idTemaVisual DESC").firstResultOptional();
    }

    public String normalizarUnidade(String unidade) {
        String texto = unidade == null ? "GLOBAL" : unidade.trim();
        int separador = texto.indexOf(':');
        if (separador >= 0) {
            texto = texto.substring(0, separador);
        }
        if (texto.isBlank()) {
            texto = "GLOBAL";
        }
        return texto.toUpperCase();
    }
}

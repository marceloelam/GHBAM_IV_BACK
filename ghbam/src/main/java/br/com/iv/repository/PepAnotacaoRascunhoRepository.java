package br.com.iv.repository;

import java.util.List;
import java.util.Optional;

import br.com.iv.entity.PepAnotacaoRascunho;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PepAnotacaoRascunhoRepository implements PanacheRepository<PepAnotacaoRascunho> {

    public List<PepAnotacaoRascunho> listarPorBam(Integer idbam) {
        return list("idbam = ?1 order by criadoEm desc", idbam);
    }

    public Optional<PepAnotacaoRascunho> buscarAtivoDoUsuario(Integer idbam, String matricula) {
        return find("idbam = ?1 and criadoPorMatricula = ?2 order by atualizadoEm desc", idbam, matricula)
                .firstResultOptional();
    }
}

package br.com.iv.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import br.com.iv.entity.SessaoAtiva;

@ApplicationScoped
public class SessaoAtivaRepository implements PanacheRepository<SessaoAtiva> {

    public Optional<SessaoAtiva> findByMatriculaAtiva(String matricula) {
        return find("trim(matricula) = ?1 and ativa = true", normalizarMatricula(matricula)).firstResultOptional();
    }

    public Optional<SessaoAtiva> findByTokenHash(String tokenHash) {
        return find("tokenHash = ?1", tokenHash).firstResultOptional();
    }

    public int desativarSessoesPorMatricula(String matricula) {
        return update("ativa = false where trim(matricula) = ?1 and ativa = true", normalizarMatricula(matricula));
    }

    public int desativarPorTokenHash(String tokenHash) {
        return update("ativa = false where tokenHash = ?1", tokenHash);
    }

    public int limparSessoesInativas(long minutosInatividade) {
        return update(
            "ativa = false where ativa = true and ultimaAtividade < ?1",
            LocalDateTime.now().minusMinutes(minutosInatividade)
        );
    }

    public int limparSessoesInativasSegundos(long segundosInatividade) {
        return update(
            "ativa = false where ativa = true and ultimaAtividade < ?1",
            LocalDateTime.now().minusSeconds(segundosInatividade)
        );
    }

    private String normalizarMatricula(String matricula) {
        return matricula == null ? "" : matricula.trim();
    }
}

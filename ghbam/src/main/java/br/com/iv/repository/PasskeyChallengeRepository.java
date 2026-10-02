package br.com.iv.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import br.com.iv.entity.PasskeyChallenge;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PasskeyChallengeRepository implements PanacheRepository<PasskeyChallenge> {
    public Optional<PasskeyChallenge> localizarValido(String requestId, String tipo) {
        return find("requestId = ?1 and tipo = ?2 and expiraEm > ?3", requestId, tipo, LocalDateTime.now())
            .firstResultOptional();
    }

    @Transactional
    public void limparExpirados() {
        delete("expiraEm <= ?1", LocalDateTime.now());
    }
}

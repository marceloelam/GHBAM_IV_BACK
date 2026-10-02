package br.com.iv.repository;

import java.util.List;
import java.util.Optional;

import br.com.iv.entity.PasskeyCredential;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasskeyCredentialRepository implements PanacheRepository<PasskeyCredential> {
    public List<PasskeyCredential> ativasPorMatricula(String matricula) {
        return list("matricula = ?1 and ativa = true", normalizar(matricula));
    }

    public Optional<PasskeyCredential> ativaPorCredentialId(String credentialId) {
        return find("credentialId = ?1 and ativa = true", credentialId).firstResultOptional();
    }

    public List<PasskeyCredential> ativasPorUserHandle(String userHandle) {
        return list("userHandle = ?1 and ativa = true", userHandle);
    }

    public boolean existeAtivaParaMatricula(String matricula) {
        return count("matricula = ?1 and ativa = true", normalizar(matricula)) > 0;
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}

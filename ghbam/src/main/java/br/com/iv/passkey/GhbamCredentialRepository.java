package br.com.iv.passkey;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;

import br.com.iv.entity.PasskeyCredential;
import br.com.iv.repository.PasskeyCredentialRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GhbamCredentialRepository implements CredentialRepository {
    @Inject
    PasskeyCredentialRepository passkeyRepository;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return passkeyRepository.ativasPorMatricula(username).stream()
            .map(credential -> PublicKeyCredentialDescriptor.builder()
                .id(decode(credential.getCredentialId()))
                .build())
            .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return passkeyRepository.ativasPorMatricula(username).stream()
            .findFirst()
            .map(credential -> decode(credential.getUserHandle()))
            .or(() -> Optional.of(handleParaMatricula(username)));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        String encoded = encode(userHandle);
        return passkeyRepository.ativasPorUserHandle(encoded).stream()
            .findFirst()
            .map(PasskeyCredential::getMatricula);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        String credentialIdEncoded = encode(credentialId);
        String userHandleEncoded = encode(userHandle);
        return passkeyRepository.ativaPorCredentialId(credentialIdEncoded)
            .filter(credential -> userHandleEncoded.equals(credential.getUserHandle()))
            .map(this::toRegisteredCredential);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        String credentialIdEncoded = encode(credentialId);
        return passkeyRepository.ativaPorCredentialId(credentialIdEncoded)
            .map(credential -> Set.of(toRegisteredCredential(credential)))
            .orElseGet(Set::of);
    }

    public ByteArray handleParaMatricula(String matricula) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new ByteArray(digest.digest(("ghbam:" + normalizar(matricula)).getBytes()));
        } catch (NoSuchAlgorithmException e) {
            return new ByteArray(normalizar(matricula).getBytes());
        }
    }

    public String encode(ByteArray value) {
        return value.getBase64Url();
    }

    public ByteArray decode(String value) {
        try {
            return ByteArray.fromBase64Url(value == null ? "" : value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Valor Base64Url invalido.", e);
        }
    }

    private RegisteredCredential toRegisteredCredential(PasskeyCredential credential) {
        return RegisteredCredential.builder()
            .credentialId(decode(credential.getCredentialId()))
            .userHandle(decode(credential.getUserHandle()))
            .publicKeyCose(decode(credential.getPublicKeyCose()))
            .signatureCount(credential.getSignatureCount() == null ? 0L : credential.getSignatureCount())
            .build();
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}

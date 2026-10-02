package br.com.iv.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;

@ApplicationScoped
public class TokenRevocationService {

    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();

    @Inject
    JWTParser jwtParser;

    public void revokeAuthorization(String authorization) {
        String token = bearerToken(authorization);
        if (token.isBlank()) {
            return;
        }
        revokedTokens.put(token, expirationEpochSeconds(token));
        cleanupExpired();
    }

    public boolean isAuthorizationRevoked(String authorization) {
        String token = bearerToken(authorization);
        if (token.isBlank()) {
            return false;
        }

        Long expiresAt = revokedTokens.get(token);
        if (expiresAt == null) {
            return false;
        }

        if (expiresAt > 0 && expiresAt <= Instant.now().getEpochSecond()) {
            revokedTokens.remove(token);
            return false;
        }
        return true;
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        return authorization.substring(7).trim();
    }

    private long expirationEpochSeconds(String token) {
        try {
            JsonWebToken jwt = jwtParser.parse(token);
            return jwt.getExpirationTime();
        } catch (ParseException | RuntimeException e) {
            return Instant.now().plusSeconds(3600).getEpochSecond();
        }
    }

    private void cleanupExpired() {
        long now = Instant.now().getEpochSecond();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue() > 0 && entry.getValue() <= now);
    }
}

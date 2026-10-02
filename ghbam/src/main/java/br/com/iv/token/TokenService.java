package br.com.iv.token;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TokenService {

    public String gerarToken(Usuario usuario) {
        Set<String> grupos = usuario.permissoes == null
                ? Set.of(usuario.role)
                : usuario.permissoes.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());
        if (grupos.isEmpty()) {
            grupos = Set.of("user");
        }

        return Jwt.issuer("ghbam-iv")
                .upn(usuario.username)
                .groups(grupos)
                .claim("idServidor", usuario.idServidor)
                .claim("matricula", usuario.matricula)
                .claim("nome", usuario.nome)
                .claim("cargo", usuario.cargo)
                .claim("unidade", usuario.unidade)
                .claim("senhaInicial", usuario.senhaInicial)
                .expiresIn(3600)
                .sign();
    }
}

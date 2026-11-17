package br.com.iv.token;

import br.com.iv.plataforma.municipio.MunicipioRoteamento;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TokenService {

    // Municipio do endereco em que a sessao foi aberta (MunicipioSessaoFilter confere a cada requisicao).
    @Inject
    MunicipioRoteamento municipioRoteamento;

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
                .claim(MunicipioRoteamento.CLAIM, municipioRoteamento.atual())
                .expiresIn(3600)
                .sign();
    }
}

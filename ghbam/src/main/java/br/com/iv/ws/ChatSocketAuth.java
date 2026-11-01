package br.com.iv.ws;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

/** Autentica a conexão do chat pelo mesmo Bearer das rotas REST (sessão ativa, não revogada). */
@ApplicationScoped
public class ChatSocketAuth {

    @Inject
    SecurityPermissionService securityPermissionService;

    public record Identidade(String matricula, String nome, String unidade, String cargo) { }

    @ActivateRequestContext
    public Identidade autenticar(String token) {
        if (token == null || token.isBlank()) return null;
        String authorization = "Bearer " + token.trim();
        AuthenticatedUser user = securityPermissionService.resolveFromAuthorization(authorization);
        if (user == null || user.matricula() == null || user.matricula().isBlank()) return null;
        if (!user.hasPermission("pchatprofissionais") && !user.hasPermission("kadm")) return null;
        String unidade = securityPermissionService.unidadeDoAuthorization(authorization);
        return new Identidade(user.matricula(), user.nome(), unidade == null || unidade.isBlank() ? user.unidade() : unidade, user.cargo());
    }
}

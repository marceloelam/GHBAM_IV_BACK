package br.com.iv.token;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;

@ApplicationScoped
public class AuthService {

    @Inject
    SecurityPermissionService securityPermissionService;

    public Usuario validar(String username, String senha) {
        return validar(username, senha, "");
    }

    public Usuario validar(String username, String senha, String senhaMontada) {
        AuthenticatedUser authenticated = securityPermissionService.authenticate(username, senha, senhaMontada);
        if (authenticated == null) {
            return null;
        }
        return fromAuthenticatedUser(authenticated);
    }

    // Reutilizado pelo login biometrico (requisitarCapturaDoBackPrincipal): a
    // digital ja confirma a identidade, entao nao ha senha para validar aqui,
    // so o mapeamento do usuario autenticado para o formato de sessao/token.
    public Usuario fromAuthenticatedUser(AuthenticatedUser authenticated) {
        Usuario user = new Usuario();
        user.username = authenticated.matricula();
        user.matricula = authenticated.matricula();
        user.idServidor = authenticated.idServidor();
        user.nome = authenticated.nome();
        user.cargo = authenticated.cargo();
        user.unidade = authenticated.unidade();
        user.cns = authenticated.cns();
        user.crm = authenticated.crm();
        user.corem = authenticated.corem();
        user.cbo = authenticated.cbo();
        user.especialidade = authenticated.especialidade();
        user.foto = authenticated.foto();
        user.usuarioad = authenticated.usuarioad();
        user.senhaInicial = authenticated.senhaInicial();
        user.role = authenticated.hasPermission("kadm") ? "admin" : "user";
        user.permissoes = authenticated.permissions();
        return user;
    }
}

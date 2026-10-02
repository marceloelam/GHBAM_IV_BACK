package br.com.iv.cdss.resource;

import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import br.com.iv.security.AuthenticatedUser;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;

class PreventivoResourceTest {
    private ContainerRequestContext context(Object user) {
        return (ContainerRequestContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ContainerRequestContext.class},
            (proxy, method, args) -> method.getName().equals("getProperty") ? user : null);
    }
    private AuthenticatedUser user(String unidade, Map<String, Boolean> permissions) {
        return new AuthenticatedUser("1", "TESTE", "Teste", "", unidade, "", "", "", "", "", "", "", false, permissions);
    }
    @Test void exigeAutenticacao() {
        assertThrows(NotAuthorizedException.class, () -> PreventivoResource.autorizar(context(null), false));
    }
    @Test void leitorNaoPodeEscrever() {
        var ctx = context(user("UNIDADE_TESTE", Map.of("pprontuariocompleto", true)));
        assertDoesNotThrow(() -> PreventivoResource.autorizar(ctx, false));
        assertThrows(ForbiddenException.class, () -> PreventivoResource.autorizar(ctx, true));
    }
    @Test void administradorPodeConsultarSemRegistrarAlerta() {
        var ctx = context(user("UNIDADE_TESTE", Map.of("kadm", true)));
        assertDoesNotThrow(() -> PreventivoResource.autorizar(ctx, false));
        assertThrows(ForbiddenException.class, () -> PreventivoResource.autorizar(ctx, true));
    }
    @Test void naoAceitaUnidadeGlobalNemPerfilSemPermissao() {
        assertThrows(ForbiddenException.class, () -> PreventivoResource.autorizar(context(user("TODAS", Map.of("kmedico", true))), true));
        assertThrows(ForbiddenException.class, () -> PreventivoResource.autorizar(context(user("UNIDADE_TESTE", Map.of())), false));
    }
}

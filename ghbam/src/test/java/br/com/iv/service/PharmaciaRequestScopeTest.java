package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.iv.security.*;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;

class PharmaciaRequestScopeTest {
    @Test void rejeitaUnidadeAdulterada() {
        PharmaciaRequestScope scope = new PharmaciaRequestScope(); scope.security = mock(SecurityPermissionService.class);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        when(user.hasPermission("kfarmaciacentral")).thenReturn(true);
        when(scope.security.resolve(request)).thenReturn(user);
        when(scope.security.unidadeDaSessao(request)).thenReturn("QA");
        assertSame(user, scope.exigirUnidade(request, "QA"));
        assertThrows(ForbiddenException.class, () -> scope.exigirUnidade(request, "OUTRA"));
    }
    @Test void rejeitaSemAutenticacao() {
        PharmaciaRequestScope scope = new PharmaciaRequestScope(); scope.security = mock(SecurityPermissionService.class);
        assertThrows(NotAuthorizedException.class, () -> scope.exigirUnidade(mock(ContainerRequestContext.class), "QA"));
    }
    @Test void rejeitaSemPermissao() {
        PharmaciaRequestScope scope = new PharmaciaRequestScope(); scope.security = mock(SecurityPermissionService.class);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(scope.security.resolve(request)).thenReturn(mock(AuthenticatedUser.class));
        assertThrows(ForbiddenException.class, () -> scope.exigirUnidade(request, "QA"));
    }
    static PharmaciaRequestScope escopoCom(ContainerRequestContext request, String... permissoes) {
        PharmaciaRequestScope scope = new PharmaciaRequestScope(); scope.security = mock(SecurityPermissionService.class);
        java.util.Map<String, Boolean> mapa = new java.util.HashMap<>();
        for (String p : permissoes) mapa.put(p, true);
        AuthenticatedUser user = new AuthenticatedUser("1", "M1", "TESTE", "", "QA", "", "", "", "", "", "", "", false, mapa);
        when(scope.security.resolve(request)).thenReturn(user);
        when(scope.security.unidadeDaSessao(request)).thenReturn("QA");
        return scope;
    }
    @Test void areaLiberadaPermiteSoAquelaArea() {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        PharmaciaRequestScope scope = escopoCom(request, PharmaciaRequestScope.DISPENSACAO);
        assertNotNull(scope.exigirUnidade(request, "QA"));
        assertNotNull(scope.exigirArea(request, "QA", PharmaciaRequestScope.DISPENSACAO));
        assertThrows(ForbiddenException.class, () -> scope.exigirArea(request, "QA", PharmaciaRequestScope.ESTOQUE));
        assertThrows(ForbiddenException.class, () -> scope.exigirArea(request, "QA", PharmaciaRequestScope.AVALIACAO));
        assertThrows(ForbiddenException.class, () -> scope.exigirArea(request, "OUTRA", PharmaciaRequestScope.DISPENSACAO));
    }
    @Test void legadoFarmhmsfxValeSoComoDispensacao() {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        PharmaciaRequestScope scope = escopoCom(request, "kfarmhmsfx");
        assertNotNull(scope.exigirArea(request, "QA", PharmaciaRequestScope.DISPENSACAO));
        assertThrows(ForbiddenException.class, () -> scope.exigirArea(request, "QA", PharmaciaRequestScope.PRODUTOS));
    }
    @Test void farmaciaAdmERemediosContinuamComAcessoTotal() {
        for (String perfil : new String[] { "kfarmaciacentral", "kremedios", "kadm" }) {
            ContainerRequestContext request = mock(ContainerRequestContext.class);
            PharmaciaRequestScope scope = escopoCom(request, perfil);
            for (String area : PharmaciaRequestScope.AREAS) assertNotNull(scope.exigirArea(request, "QA", area), perfil + " em " + area);
        }
    }
}

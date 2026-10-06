package br.com.iv.producaoassistencial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.RoutePermissionCatalog;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;

class ProducaoAssistencialResourceTest {
    private AuthenticatedUser usuario(boolean admin, String unidade) {
        return new AuthenticatedUser("1", "M1", "Ana", "MEDICO", unidade, "", "", "", "", "", "", "", false, Map.of("kadm", admin, "kmedico", true));
    }
    private ProducaoAssistencialResource resource(AuthenticatedUser user) {
        var resource = new ProducaoAssistencialResource();
        resource.service = mock(ProducaoAssistencialService.class);
        resource.request = mock(ContainerRequestContext.class);
        when(resource.request.getProperty("ghbam.user")).thenReturn(user);
        return resource;
    }
    @Test void perfilClinicoSemAdministrativoNaoAcessa() {
        var resource = resource(usuario(false, "U"));
        assertEquals(403, resource.gerar("2026-10-01", "2026-10-05", "MEDICO").getStatus());
        verifyNoInteractions(resource.service);
        assertEquals(List.of("kadm"), new RoutePermissionCatalog().requiredPermissionsFor("producao-assistencial").orElseThrow());
    }
    @Test void usaUnidadeDaIdentidadeAutenticada() {
        var resource = resource(usuario(true, " U "));
        var inicio = LocalDate.of(2026, 10, 1); var fim = LocalDate.of(2026, 10, 5);
        when(resource.service.gerar(inicio, fim, "U", "MEDICO")).thenReturn(new ProducaoAssistencialService.Relatorio(inicio, fim, "U", List.of("MEDICO"), 0, 0, 0, List.of()));
        assertEquals(200, resource.gerar("2026-10-01", "2026-10-05", "MEDICO").getStatus());
        verify(resource.service).gerar(inicio, fim, "U", "MEDICO");
    }
    @Test void rejeitaUnidadeAusenteEDataInvalida() {
        assertEquals(403, resource(usuario(true, "")).gerar("2026-10-01", "2026-10-05", "MEDICO").getStatus());
        var resource = resource(usuario(true, "U"));
        assertThrows(BadRequestException.class, () -> resource.gerar("2026-02-30", "2026-10-05", "MEDICO"));
        verifyNoInteractions(resource.service);
    }
}

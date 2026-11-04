package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.LeitoReservaService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;

class LeitoReservaControllerTest {

    private LeitoReservaController controller;
    private SecurityPermissionService security;
    private LeitoReservaService service;
    private AuthenticatedUser user;

    @BeforeEach
    void preparar() {
        controller = new LeitoReservaController();
        security = mock(SecurityPermissionService.class);
        service = mock(LeitoReservaService.class);
        user = mock(AuthenticatedUser.class);
        controller.security = security;
        controller.service = service;
        controller.context = mock(ContainerRequestContext.class);
        when(security.resolve(any())).thenReturn(user);
        when(security.unidadeDaSessao(any())).thenReturn("HMSFX");
        when(user.matricula()).thenReturn("1");
    }

    @Test
    void semLoginRecusaTudoSemTocarNoServico() {
        when(security.resolve(any())).thenReturn(null);
        assertThrows(NotAuthorizedException.class, () -> controller.listarAtivas());
        assertThrows(NotAuthorizedException.class, () -> controller.reservar(new LeitoReservaController.ReservaRequest(1, 1)));
        assertThrows(NotAuthorizedException.class, () -> controller.cancelar(1L, new LeitoReservaController.CancelamentoRequest("motivo")));
        verify(service, never()).reservar(anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void semPermissaoDoNirRecusaTudoSemTocarNoServico() {
        when(user.hasAnyPermission(any())).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> controller.listarAtivas());
        assertThrows(ForbiddenException.class, () -> controller.reservar(new LeitoReservaController.ReservaRequest(1, 1)));
        assertThrows(ForbiddenException.class, () -> controller.cancelar(1L, new LeitoReservaController.CancelamentoRequest("motivo")));
        verify(service, never()).listarAtivas(anyString());
        verify(service, never()).reservar(anyInt(), anyInt(), anyString(), anyString());
        verify(service, never()).cancelar(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void unidadeVemDaSessaoENuncaDoCliente() {
        when(user.hasAnyPermission(any())).thenReturn(true);
        when(service.listarAtivas("HMSFX")).thenReturn(List.of());
        assertEquals(200, controller.listarAtivas().getStatus());
        verify(service).listarAtivas("HMSFX");
    }

    @Test
    void sessaoSemUnidadeRecusa() {
        when(user.hasAnyPermission(any())).thenReturn(true);
        when(security.unidadeDaSessao(any())).thenReturn("");
        when(user.unidade()).thenReturn("");
        assertThrows(ForbiddenException.class, () -> controller.listarAtivas());
    }
}

package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import br.com.iv.dto.LogDto;
import br.com.iv.entity.Servidor;
import br.com.iv.entity.Servidor2;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.repository.Servidor2Repository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.WebApplicationException;

class ServidorMatriculaServiceTest {
    ServidorMatriculaService service;
    Servidor servidor;
    Servidor2 complementar;

    @BeforeEach
    void preparar() {
        service = new ServidorMatriculaService();
        service.entityManager = mock(EntityManager.class);
        service.servidorRepository = mock(ServidorRepository.class);
        service.servidor2Repository = mock(Servidor2Repository.class);
        service.securityPermissionService = mock(SecurityPermissionService.class);
        service.sessaoAtivaService = mock(SessaoAtivaService.class);
        service.logService = mock(LogService.class);
        when(service.entityManager.createNativeQuery(anyString())).thenReturn(mock(Query.class));
        servidor = new Servidor();
        servidor.setIdservidor(7);
        servidor.setMatricula("123");
        servidor.setUnidade("UBS");
        servidor.setAdministracao(true);
        complementar = new Servidor2();
        complementar.setIdservidor2(8);
        complementar.setMatricula("123");
        complementar.setCadusuarios(true);
        when(service.entityManager.find(Servidor.class, 7)).thenReturn(servidor);
        when(service.servidorRepository.count("matricula", "123")).thenReturn(1L);
        when(service.servidor2Repository.list("matricula", "123")).thenReturn(List.of(complementar));
    }

    AuthenticatedUser usuario(String unidade, Map<String, Boolean> permissoes) {
        return new AuthenticatedUser("9", "ADMIN", "Admin", "", unidade, "", "", "", "", "", "", "", false, permissoes);
    }

    void falha(int status, String anterior, String nova, String motivo, AuthenticatedUser usuario) {
        WebApplicationException error = assertThrows(WebApplicationException.class,
                () -> service.alterar(7, anterior, nova, motivo, usuario));
        assertEquals(status, error.getResponse().getStatus());
        assertEquals("123", servidor.getMatricula());
        assertEquals("123", complementar.getMatricula());
        verifyNoInteractions(service.logService, service.sessaoAtivaService);
    }

    @Test
    void alteraOsDoisCadastrosSemMudarIdsOuPermissoesEAudita() {
        Map<String, Object> resposta = service.alterar(7, "123", " 00456 ", "Troca pela prefeitura", usuario("UBS", Map.of("kadm", true)));
        assertEquals("00456", servidor.getMatricula());
        assertEquals("00456", complementar.getMatricula());
        assertEquals(7, servidor.getIdservidor());
        assertEquals(8, complementar.getIdservidor2());
        assertTrue(servidor.getAdministracao());
        assertTrue(complementar.getCadusuarios());
        assertEquals(true, resposta.get("cadastroComplementarAtualizado"));
        verify(service.sessaoAtivaService).encerrarSessaoPorMatricula("123");
        verify(service.securityPermissionService).invalidateUserCache("123");
        verify(service.securityPermissionService).invalidateUserCache("00456");
        ArgumentCaptor<LogDto> log = ArgumentCaptor.forClass(LogDto.class);
        verify(service.logService).salvarLog(log.capture());
        assertTrue(log.getValue().getEvento().contains("123 -> 00456"));
        assertTrue(log.getValue().getEvento().contains("ADMIN"));
        assertTrue(log.getValue().getEvento().contains("Troca pela prefeitura"));
        verify(service.entityManager).flush();
    }

    @Test void exigeAutenticacao() { falha(401, "123", "456", "Troca oficial", AuthenticatedUser.empty()); }
    @Test void exigeAdministracao() { falha(403, "123", "456", "Troca oficial", usuario("UBS", Map.of())); }
    @Test void protegeOutraUnidade() { falha(403, "123", "456", "Troca oficial", usuario("OUTRA", Map.of("kadm", true))); }
    @Test void bloqueiaEstadoObsoleto() { falha(409, "OLD", "456", "Troca oficial", usuario("UBS", Map.of("kadm", true))); }
    @Test void rejeitaFormatoInvalido() { falha(400, "123", "a/b", "Troca oficial", usuario("UBS", Map.of("kadm", true))); }
    @Test void rejeitaComprimentoInvalido() { falha(400, "123", "1234567890123456", "Troca oficial", usuario("UBS", Map.of("kadm", true))); }
    @Test void exigeMotivo() { falha(400, "123", "456", "", usuario("UBS", Map.of("kadm", true))); }
    @Test void rejeitaMesmaMatricula() { falha(400, "123", "123", "Troca oficial", usuario("UBS", Map.of("kadm", true))); }

    @Test void bloqueiaDuplicidadePrincipal() {
        when(service.servidorRepository.count("matricula", "456")).thenReturn(1L);
        falha(409, "123", "456", "Troca oficial", usuario("UBS", Map.of("kadm", true)));
    }
    @Test void bloqueiaDuplicidadeComplementar() {
        when(service.servidor2Repository.count("matricula", "456")).thenReturn(1L);
        falha(409, "123", "456", "Troca oficial", usuario("UBS", Map.of("kadm", true)));
    }
    @Test void bloqueiaPermissoesAntigasAmbiguas() {
        when(service.servidor2Repository.list("matricula", "123")).thenReturn(List.of(complementar, new Servidor2()));
        falha(409, "123", "456", "Troca oficial", usuario("UBS", Map.of("kadm", true)));
    }
    @Test void permiteCadastroSemComplementar() {
        when(service.servidor2Repository.list("matricula", "123")).thenReturn(List.of());
        assertEquals(false, service.alterar(7, "123", "456", "Troca oficial", usuario("UBS", Map.of("kadm", true))).get("cadastroComplementarAtualizado"));
    }
    @Test void superusuarioPodeAtuarEmOutraUnidade() {
        assertEquals("456", service.alterar(7, "123", "456", "Troca oficial", usuario("OUTRA", Map.of("ksuperusuario", true))).get("matricula"));
    }
    @Test void salvamentoComumNaoPodeDesfazerTrocaDeMatricula() {
        WebApplicationException error = assertThrows(WebApplicationException.class,
                () -> ServidorMatriculaService.validarMatriculaCadastro("456", "123"));
        assertEquals(409, error.getResponse().getStatus());
    }
    @Test void salvamentoComumAceitaMatriculaAtualOuCampoAusente() {
        assertDoesNotThrow(() -> ServidorMatriculaService.validarMatriculaCadastro("123", "123"));
        assertDoesNotThrow(() -> ServidorMatriculaService.validarMatriculaCadastro("123", null));
    }
}

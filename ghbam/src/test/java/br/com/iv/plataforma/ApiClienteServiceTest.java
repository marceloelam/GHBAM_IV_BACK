package br.com.iv.plataforma;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.iv.security.AuthenticatedUser;

/** Credencial de sistema integrador: permissoes limpas, nunca de plataforma, usuario no formato padrao. */
class ApiClienteServiceTest {

    @Test
    void permissoesSaoLimpasESemPoderDePlataforma() {
        assertEquals(List.of("kenfermagem", "prealizarexames"),
                ApiClienteService.normalizarPermissoes(" KENFERMAGEM, prealizarexames;kenfermagem superusuario ksuperusuario"));
        assertEquals(List.of(), ApiClienteService.normalizarPermissoes(""));
        assertEquals(List.of(), ApiClienteService.normalizarPermissoes("drop table;--"));
    }

    @Test
    void clienteViraUsuarioComUnidadeEPermissoes() {
        AuthenticatedUser u = ApiClienteService.paraUsuario(new ApiClienteService.Cliente("cli_ab", "Laboratorio X",
                "HPS", List.of("prealizarexames"), true));
        assertEquals("cliente:cli_ab", u.matricula());
        assertEquals("HPS", u.unidade());
        assertEquals(ApiClienteService.CARGO, u.cargo());
        assertTrue(u.hasPermission("prealizarexames"));
        assertFalse(u.hasPermission("superusuario"));
        assertFalse(u.hasPermission("kadm"));
    }

    @Test
    void segredoEIdSaoAleatorios() {
        String s1 = ApiClienteService.gerarSegredo();
        assertTrue(s1.length() >= 40);
        assertNotEquals(s1, ApiClienteService.gerarSegredo());
        assertTrue(ApiClienteService.gerarClientId().matches("cli_[0-9a-f]{20}"));
    }
}

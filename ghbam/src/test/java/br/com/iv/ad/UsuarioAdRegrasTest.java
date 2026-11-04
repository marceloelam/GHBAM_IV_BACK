package br.com.iv.ad;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UsuarioAdRegrasTest {

    @Test
    void hifenBloqueiaESemadNao() {
        assertTrue(UsuarioAdRegras.bloqueado("-"));
        assertTrue(UsuarioAdRegras.bloqueado(" - "));
        assertFalse(UsuarioAdRegras.bloqueado("semad"));
        assertFalse(UsuarioAdRegras.bloqueado("joao.silva"));
        assertFalse(UsuarioAdRegras.bloqueado(null));
    }

    @Test
    void ninguemGravaHifenNovo() {
        assertNotNull(UsuarioAdRegras.motivoRecusa("-", null, true));
        assertNotNull(UsuarioAdRegras.motivoRecusa("-", "joao.silva", true));
    }

    @Test
    void cadastroComHifenPodeSerEditadoERegularizado() {
        assertNull(UsuarioAdRegras.motivoRecusa("-", "-", false));
        assertNull(UsuarioAdRegras.motivoRecusa("joao.silva", "-", false));
    }

    @Test
    void somenteSuperusuarioConcedeIsencao() {
        assertNotNull(UsuarioAdRegras.motivoRecusa("semad", "joao.silva", false));
        assertNotNull(UsuarioAdRegras.motivoRecusa(" SEMAD ", null, false));
        assertNotNull(UsuarioAdRegras.motivoRecusa("admin", "-", false));
        assertNull(UsuarioAdRegras.motivoRecusa("semad", "joao.silva", true));
    }

    @Test
    void isencaoExistenteSobreviveAEdicaoPorNaoSuperusuario() {
        assertNull(UsuarioAdRegras.motivoRecusa("semad", "semad", false));
        assertNull(UsuarioAdRegras.motivoRecusa("admin", "admin", false));
    }

    @Test
    void usuarioComumNaoTemRestricao() {
        assertNull(UsuarioAdRegras.motivoRecusa("joao.silva", null, false));
        assertNull(UsuarioAdRegras.motivoRecusa("", "joao.silva", false));
        assertNull(UsuarioAdRegras.motivoRecusa(null, "joao.silva", false));
    }
}

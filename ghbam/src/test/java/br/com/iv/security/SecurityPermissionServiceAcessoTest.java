package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.Servidor;

class SecurityPermissionServiceAcessoTest {

    private final SecurityPermissionService service = new SecurityPermissionService();

    private static Servidor servidor(Boolean ativo, String usuarioAd) {
        Servidor s = new Servidor();
        s.setAtivo(ativo);
        s.setUsuarioad(usuarioAd);
        return s;
    }

    @Test
    void ativoComUsuarioOuSemAdAcessa() {
        assertTrue(service.acessoPermitido(servidor(true, "joao.silva")));
        assertTrue(service.acessoPermitido(servidor(true, "semad")));
        assertTrue(service.acessoPermitido(servidor(true, "")));
    }

    @Test
    void hifenNaoAcessaMesmoAtivo() {
        assertFalse(service.acessoPermitido(servidor(true, "-")));
        assertFalse(service.acessoPermitido(servidor(true, " - ")));
    }

    @Test
    void inativoOuInexistenteNaoAcessa() {
        assertFalse(service.acessoPermitido(servidor(false, "joao.silva")));
        assertFalse(service.acessoPermitido(servidor(null, "joao.silva")));
        assertFalse(service.acessoPermitido(null));
    }
}

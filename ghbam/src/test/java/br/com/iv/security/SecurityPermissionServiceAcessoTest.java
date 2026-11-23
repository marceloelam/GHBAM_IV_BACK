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

    /** Inatividade (V265): simula o serviço sem banco. */
    private static br.com.iv.service.InatividadeAcessoService inatividade(boolean suspensa) {
        return new br.com.iv.service.InatividadeAcessoService() {
            @Override
            public boolean suspensa(Servidor s) {
                return suspensa;
            }
        };
    }

    @Test
    void contaSuspensaPorInatividadeNaoAcessa() {
        SecurityPermissionService comControle = new SecurityPermissionService();
        comControle.inatividadeAcessoService = inatividade(true);
        assertFalse(comControle.acessoPermitido(servidor(true, "joao.silva")));
        assertFalse(comControle.acessoPermitido(servidor(true, "semad")));
    }

    @Test
    void contaNaoSuspensaAcessaNormalmente() {
        SecurityPermissionService comControle = new SecurityPermissionService();
        comControle.inatividadeAcessoService = inatividade(false);
        assertTrue(comControle.acessoPermitido(servidor(true, "joao.silva")));
        // Inatividade não libera quem já está bloqueado por outro motivo.
        assertFalse(comControle.acessoPermitido(servidor(false, "joao.silva")));
        assertFalse(comControle.acessoPermitido(servidor(true, "-")));
    }

    @Test
    void inativoOuInexistenteNaoAcessa() {
        assertFalse(service.acessoPermitido(servidor(false, "joao.silva")));
        assertFalse(service.acessoPermitido(servidor(null, "joao.silva")));
        assertFalse(service.acessoPermitido(null));
    }
}

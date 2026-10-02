package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.Bam;
import br.com.iv.security.AuthenticatedUser;

class FhirResultadoLaboratorioAuthorizationServiceTest {

    private final FhirResultadoLaboratorioAuthorizationService authorization =
            new FhirResultadoLaboratorioAuthorizationService();

    @Test
    void permiteProfissionalDoMesmoLocal() {
        assertTrue(authorization.podeExportar(usuario("LAB-A", "klaboratorio"), bam("lab-a")));
    }

    @Test
    void bloqueiaProfissionalDeOutraUnidade() {
        assertFalse(authorization.podeExportar(usuario("LAB-A", "klaboratorio"), bam("LAB-B")));
    }

    @Test
    void administradorPodeAcessarOutraUnidade() {
        assertTrue(authorization.podeExportar(usuario("ADM", "kadm"), bam("LAB-B")));
    }

    @Test
    void bloqueiaUsuarioSemPermissaoClinica() {
        assertFalse(authorization.podeExportar(usuario("LAB-A", "krecepcao"), bam("LAB-A")));
    }

    @Test
    void bloqueiaSessaoSemMatricula() {
        AuthenticatedUser semMatricula = new AuthenticatedUser("1", "", "", "", "LAB-A", "", "", "", "", "", "", "", false,
                Map.of("klaboratorio", true));
        assertFalse(authorization.podeExportar(semMatricula, bam("LAB-A")));
    }

    private AuthenticatedUser usuario(String unidade, String permissao) {
        return new AuthenticatedUser("1", "123", "Profissional", "", unidade, "", "", "", "", "", "", "", false,
                Map.of(permissao, true));
    }

    private Bam bam(String unidade) {
        Bam bam = new Bam();
        bam.setUnidade(unidade);
        return bam;
    }
}

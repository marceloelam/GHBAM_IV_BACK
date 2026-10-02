package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResetSenhaRateLimitServiceTest {

    @Test
    void naoBloqueiaMatriculaSemHistoricoDeSolicitacoes() {
        ResetSenhaRateLimitService service = new ResetSenhaRateLimitService();

        assertEquals(0, service.segundosBloqueadoMatricula("753951"));
    }

    @Test
    void bloqueiaMatriculaAposTresSolicitacoesNaJanela() {
        ResetSenhaRateLimitService service = new ResetSenhaRateLimitService();

        service.registrarSolicitacaoMatricula("753951");
        service.registrarSolicitacaoMatricula("753951");
        assertEquals(0, service.segundosBloqueadoMatricula("753951"));

        service.registrarSolicitacaoMatricula("753951");

        assertTrue(service.segundosBloqueadoMatricula("753951") > 0);
    }

    @Test
    void chaveDeMatriculaEhNormalizadaParaMaiusculaETrim() {
        ResetSenhaRateLimitService service = new ResetSenhaRateLimitService();

        service.registrarSolicitacaoMatricula(" abc123 ");
        service.registrarSolicitacaoMatricula("ABC123");
        service.registrarSolicitacaoMatricula("abc123");

        assertTrue(service.segundosBloqueadoMatricula("ABC123") > 0);
    }

    @Test
    void bloqueioDeMatriculaEIpSaoIndependentes() {
        ResetSenhaRateLimitService service = new ResetSenhaRateLimitService();

        service.registrarSolicitacaoMatricula("753951");
        service.registrarSolicitacaoMatricula("753951");
        service.registrarSolicitacaoMatricula("753951");

        assertTrue(service.segundosBloqueadoMatricula("753951") > 0);
        assertEquals(0, service.segundosBloqueadoIp("10.0.0.1"));
    }

    @Test
    void chaveVaziaOuNulaNuncaBloqueiaNemQuebra() {
        ResetSenhaRateLimitService service = new ResetSenhaRateLimitService();

        service.registrarSolicitacaoMatricula(null);
        service.registrarSolicitacaoMatricula("");
        service.registrarSolicitacaoIp(null);

        assertEquals(0, service.segundosBloqueadoMatricula(null));
        assertEquals(0, service.segundosBloqueadoMatricula(""));
        assertEquals(0, service.segundosBloqueadoIp(null));
    }
}

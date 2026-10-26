package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortalPacienteRateLimitServiceTest {

    private static final String CPF = "00000000191";

    @Test
    void bloqueiaIpAposDezConsultasNaJanela() {
        PortalPacienteRateLimitService service = new PortalPacienteRateLimitService();

        for (int i = 0; i < 9; i++) service.registrarConsultaIp("10.0.0.1");
        assertEquals(0, service.segundosBloqueado("10.0.0.1", CPF));

        service.registrarConsultaIp("10.0.0.1");

        assertTrue(service.segundosBloqueado("10.0.0.1", CPF) > 0);
    }

    @Test
    void ipsDiferentesNaoSeBloqueiamEntreSi() {
        PortalPacienteRateLimitService service = new PortalPacienteRateLimitService();

        for (int i = 0; i < 10; i++) service.registrarConsultaIp("10.0.0.1");

        assertEquals(0, service.segundosBloqueado("10.0.0.2", CPF));
    }

    @Test
    void bloqueiaCpfAposCincoFalhasEmQualquerIp() {
        PortalPacienteRateLimitService service = new PortalPacienteRateLimitService();

        for (int i = 0; i < 4; i++) service.registrarFalhaCpf(CPF);
        assertEquals(0, service.segundosBloqueado("10.0.0.1", CPF));

        service.registrarFalhaCpf(CPF);

        assertTrue(service.segundosBloqueado("10.0.0.9", CPF) > 0);
        assertEquals(0, service.segundosBloqueado("10.0.0.9", "00000000272"));
    }
}

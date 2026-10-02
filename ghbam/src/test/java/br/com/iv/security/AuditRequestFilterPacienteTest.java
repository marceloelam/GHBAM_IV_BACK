package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AuditRequestFilterPacienteTest {

    @Test
    void extraiBamDoSegmentoNbam() {
        assertArrayEquals(new String[] { "bam", "485014" },
                AuditRequestFilter.extrairReferenciaPaciente("anamneseinternacao/nbam/485014"));
    }

    @Test
    void extraiIdPacienteEProntuario() {
        assertArrayEquals(new String[] { "idpaciente", "77" },
                AuditRequestFilter.extrairReferenciaPaciente("historicopaciente/idpaciente/77"));
        assertArrayEquals(new String[] { "prontuario", "12345" },
                AuditRequestFilter.extrairReferenciaPaciente("pacientes/prontuario/12345"));
    }

    @Test
    void balancoHidricoTrataProntuarioComoBam() {
        assertArrayEquals(new String[] { "bam", "900" },
                AuditRequestFilter.extrairReferenciaPaciente("backend_balanco_hidrico_completo/prontuario/900/dt/2026-09-28"));
    }

    @Test
    void semMarcadorOuValorNaoNumericoNaoIdentifica() {
        assertNull(AuditRequestFilter.extrairReferenciaPaciente("pacientes/aguardando/unidade/hmsfx"));
        assertNull(AuditRequestFilter.extrairReferenciaPaciente("pacientes/bam/abc"));
        assertNull(AuditRequestFilter.extrairReferenciaPaciente(""));
    }

    @Test
    void heartbeatNuncaETratadoComoEventoAuditavel() {
        org.junit.jupiter.api.Assertions.assertTrue(
                AuditRequestFilter.isHeartbeat("/api/usuarios-conectados/heartbeat"));
        org.junit.jupiter.api.Assertions.assertTrue(
                AuditRequestFilter.isHeartbeat("api/usuarios-conectados//heartbeat"));
        org.junit.jupiter.api.Assertions.assertFalse(
                AuditRequestFilter.isHeartbeat("api/usuarios-conectados/logoff"));
    }
}

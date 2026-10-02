package br.com.iv.odontologia;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;

class OdontologiaProntuarioContractTest {
    @Test
    void pacienteDeveTerUmUnicoProntuarioOdontologicoVitalicio() throws Exception {
        Column coluna = OdontologiaProntuario.class
                .getDeclaredField("idpaciente")
                .getAnnotation(Column.class);

        assertTrue(coluna.unique());
        assertTrue(!coluna.nullable());
    }
}

package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

class PacientesRecentesContractTest {

    @Test
    void ofereceBuscaEspecificaDePacientesComBamDosUltimosCincoDias() throws Exception {
        Method method = PacientesController.class.getDeclaredMethod(
                "listarPacientesComBamRecente", String.class, String.class);

        assertNotNull(method.getAnnotation(GET.class));
        assertEquals("/recentes/{nome}/unidade/{unidade}", method.getAnnotation(Path.class).value());
    }
}

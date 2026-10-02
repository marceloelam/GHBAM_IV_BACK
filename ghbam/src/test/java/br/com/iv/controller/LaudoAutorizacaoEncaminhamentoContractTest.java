package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

class LaudoAutorizacaoEncaminhamentoContractTest {

    @Test
    void possuiAcaoSeparadaParaEncaminharLaudoAssinadoAoNir() throws Exception {
        Method method = LaudoAutorizacaoController.class
                .getDeclaredMethod("encaminharAoNir", int.class);

        assertNotNull(method.getAnnotation(POST.class));
        assertEquals("/{id}/encaminhar-nir", method.getAnnotation(Path.class).value());
    }
}

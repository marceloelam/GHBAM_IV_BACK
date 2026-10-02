package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.PathParam;

class SalaControllerTest {

    @Test
    void deveMapearParametroLocalDasRotasDeConsulta() {
        assertTrue(possuiPathParamLocal("xlistarSala", String.class, String.class));
        assertTrue(possuiPathParamLocal("filtarPorSalaunidade", String.class, String.class));
    }

    private boolean possuiPathParamLocal(String methodName, Class<?>... parameterTypes) {
        try {
            Method method = SalaController.class.getDeclaredMethod(methodName, parameterTypes);
            Annotation[][] annotations = method.getParameterAnnotations();
            return Arrays.stream(annotations[1])
                    .filter(PathParam.class::isInstance)
                    .map(PathParam.class::cast)
                    .anyMatch(annotation -> "local".equals(annotation.value()));
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}

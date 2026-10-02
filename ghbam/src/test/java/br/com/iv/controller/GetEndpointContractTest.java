package br.com.iv.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.GET;

class GetEndpointContractTest {

    @Test
    void endpointsGetNaoDeclaramCorpoDeRequisicao() throws Exception {
        List<String> violations = new ArrayList<>();

        for (Class<?> controller : controllerClasses()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (method.getAnnotation(GET.class) == null) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    if (!hasJaxRsBinding(parameter)) {
                        violations.add(controller.getSimpleName() + "#" + method.getName()
                                + "(" + parameter.getType().getSimpleName() + ")");
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                () -> "Endpoints GET nao podem depender de corpo de requisicao: "
                        + String.join(", ", violations));
    }

    private static boolean hasJaxRsBinding(Parameter parameter) {
        for (Annotation annotation : parameter.getAnnotations()) {
            Package annotationPackage = annotation.annotationType().getPackage();
            if (annotationPackage != null
                    && annotationPackage.getName().startsWith("jakarta.ws.rs")) {
                return true;
            }
        }
        return false;
    }

    private static List<Class<?>> controllerClasses() throws IOException, ClassNotFoundException {
        Path packageDir = Path.of("target", "classes", "br", "com", "iv", "controller");
        List<Class<?>> classes = new ArrayList<>();

        try (var files = Files.list(packageDir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Controller.class"))
                    .toList()) {
                String simpleName = file.getFileName().toString().replaceFirst("\\.class$", "");
                classes.add(Class.forName("br.com.iv.controller." + simpleName));
            }
        }
        return classes;
    }
}

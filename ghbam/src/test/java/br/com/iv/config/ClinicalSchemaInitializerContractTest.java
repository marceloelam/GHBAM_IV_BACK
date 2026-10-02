package br.com.iv.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

class ClinicalSchemaInitializerContractTest {

    @Test
    void permiteDesativarAlteracoesDeEsquemaNoStartup() throws Exception {
        Field field = ClinicalSchemaInitializer.class.getDeclaredField("enabled");
        ConfigProperty config = field.getAnnotation(ConfigProperty.class);

        assertNotNull(config);
        assertEquals("ghbam.clinical-schema-initializer.enabled", config.name());
        assertEquals("true", config.defaultValue());
    }
}

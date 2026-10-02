package br.com.iv.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class FlywayV2ConfigurationContractTest {

    @Test
    void corrigeSequenciaDeRxProcedimentosAposCargaLegada() throws IOException {
        Path migration = Path.of(
                "src", "main", "resources", "db", "migration-v2",
                "V86__sync_rxprocedimentos_idrx_sequence.sql");
        String sql = Files.readString(migration, StandardCharsets.UTF_8).toLowerCase();

        assertTrue(sql.contains("pg_get_serial_sequence('rxprocedimentos', 'idrx')"));
        assertTrue(sql.contains("max(idrx)"));
        assertTrue(sql.contains("setval"));
    }

    @Test
    void usaCatalogoIsoladoEMigracaoAutomaticaPorPadrao() throws IOException {
        Properties properties = new Properties();
        Path applicationProperties = Path.of(
                "src", "main", "resources", "application.properties");
        try (Reader reader = Files.newBufferedReader(applicationProperties, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        assertEquals("db/migration-v2", properties.getProperty("quarkus.flyway.locations"));
        assertEquals("ghbam_schema_history_v2", properties.getProperty("quarkus.flyway.table"));
        assertEquals("68", properties.getProperty("quarkus.flyway.baseline-version"));
        assertEquals("${GHBAM_FLYWAY_MIGRATE_AT_START:true}",
                properties.getProperty("quarkus.flyway.migrate-at-start"));
        assertEquals("${HIBERNATE_SCHEMA_GENERATION:validate}",
                properties.getProperty("%prod.quarkus.hibernate-orm.database.generation"));
        assertEquals("${GHBAM_CLINICAL_SCHEMA_INITIALIZER_ENABLED:false}",
                properties.getProperty("ghbam.clinical-schema-initializer.enabled"));
    }
}

package br.com.iv.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.StartupEvent;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import java.sql.Connection;
import java.sql.Statement;
import org.jboss.logging.Logger;

@ApplicationScoped
public class VectorExtensionInstaller {

    private static final Logger LOG = Logger.getLogger(VectorExtensionInstaller.class);

    @Inject
    AgroalDataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
            LOG.info("Extensao pgvector ativada com sucesso");
        } catch (Exception e) {
            LOG.warn("Nao foi possivel ativar pgvector: " + e.getMessage());
            LOG.warn("Execute manualmente no banco: CREATE EXTENSION vector;");
        }
    }
}

package br.com.iv.health;

import io.agroal.api.AgroalDataSource;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;

@ApplicationScoped
public class DbConnectionReaper {
    private static final Logger LOG = Logger.getLogger(DbConnectionReaper.class);

    @Inject
    AgroalDataSource dataSource;

    // Executa a cada 30 minutos (seguro para não sobrecarregar o banco)
    @Scheduled(every = "30m")
    void reapStuckConnections() {
        // Seleciona apenas sessões da aplicação que estão em 'idle in transaction' há mais de 5 minutos.
        // NÃO inclui sessões 'active' ou 'idle' — preservamos conexões em uso e simples conexões ociosas.
        String listSql = "SELECT pid, state, now() - state_change AS duration, usename, application_name, client_addr, query " +
                "FROM pg_stat_activity " +
                "WHERE application_name = 'ghbam-app' AND pid <> pg_backend_pid() " +
                "AND state = 'idle in transaction' AND now() - state_change > interval '5 minutes'";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(listSql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int pid = rs.getInt("pid");
                String state = rs.getString("state");
                String user = rs.getString("usename");
                String app = rs.getString("application_name");
                String client = rs.getString("client_addr");
                String query = rs.getString("query");
                String dur = rs.getString("duration");

                LOG.warnf("Detected candidate DB session pid=%d user=%s app=%s client=%s state=%s duration=%s query=%s",
                        pid, user, app, client, state, dur, query == null ? "<null>" : query.replaceAll("\n", " "));

                // Tentamos apenas cancelar a query. Não terminamos o backend para preservar segurança do banco.
                boolean cancelled = tryCancel(pid, c);
                if (!cancelled) {
                    LOG.warnf("pg_cancel_backend returned false for pid=%d (may require superuser or session owner). Manual DBA action may be needed.", pid);
                }
            }

        } catch (Exception e) {
            LOG.error("Error while scanning/cleaning stuck DB connections", e);
        }
    }

    private boolean tryCancel(int pid, Connection c) {
        String cancelSql = "SELECT pg_cancel_backend(?)";
        try (PreparedStatement ps = c.prepareStatement(cancelSql)) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean res = rs.getBoolean(1);
                    LOG.infof("pg_cancel_backend(%d) returned %b", pid, res);
                    return res;
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "pg_cancel_backend failed for pid=%d", pid);
        }
        return false;
    }

    private boolean tryTerminate(int pid, Connection c) {
        String termSql = "SELECT pg_terminate_backend(?)";
        try (PreparedStatement ps = c.prepareStatement(termSql)) {
            ps.setInt(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean res = rs.getBoolean(1);
                    LOG.infof("pg_terminate_backend(%d) returned %b", pid, res);
                    return res;
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "pg_terminate_backend failed for pid=%d (may require superuser)", pid);
        }
        return false;
    }
}

package br.com.iv.util;

import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint para monitorar o estado das conexões do pool de dados.
 * 
 * Acesse: http://localhost:8080/admin/pool-status
 * 
 * IMPORTANTE: Em produção, proteja este endpoint com autenticação!
 * Adicione @RolesAllowed("admin") se tiver controle de acesso configurado.
 */
@Path("/admin/pool-status")
public class ConnectionPoolMonitor {
    
    private static final Logger LOG = Logger.getLogger(ConnectionPoolMonitor.class);
    
    @Inject
    AgroalDataSource dataSource;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getPoolStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            var metrics = dataSource.getMetrics();
            
            // Informações do pool principal (Agroal)
            Map<String, Object> poolInfo = new HashMap<>();
            poolInfo.put("activeConnections", metrics.activeCount());
            poolInfo.put("availableConnections", metrics.availableCount());
            poolInfo.put("maxPoolSize", metrics.maxUsedCount());
            poolInfo.put("awaitingConnections", metrics.awaitingCount());
            poolInfo.put("totalCreated", metrics.creationCount());
            poolInfo.put("totalDestroyed", metrics.destroyCount());
            poolInfo.put("leakDetections", metrics.leakDetectionCount());
            poolInfo.put("invalidConnections", metrics.invalidCount());
            poolInfo.put("flushCount", metrics.flushCount());
            poolInfo.put("reapCount", metrics.reapCount());
            
            status.put("agroalPool", poolInfo);
            status.put("timestamp", System.currentTimeMillis());
            
            // Informações da classe Conexao customizada (se houver)
            try {
                Map<String, Object> customStats = br.com.iv.util.Conexao.getConnectionStats();
                status.put("customConnectionPool", customStats);
            } catch (Exception e) {
                status.put("customConnectionPool", "Não disponível");
            }
            
            // Status geral
            long active = metrics.activeCount();
            long max = metrics.maxUsedCount();
            double utilizacao = max > 0 ? (active * 100.0 / max) : 0;
            
            status.put("utilizacaoPercent", String.format("%.2f%%", utilizacao));
            
            // Alertas
            if (utilizacao > 80) {
                status.put("alerta", "ATENÇÃO: Pool de conexões com mais de 80% de utilização!");
            } else if (utilizacao > 60) {
                status.put("alerta", "AVISO: Pool de conexões com mais de 60% de utilização");
            } else {
                status.put("alerta", "Pool de conexões operando normalmente");
            }
            
            if (metrics.leakDetectionCount() > 0) {
                status.put("vazamento", "ALERTA: Detectado vazamento de conexões!");
            }
            
            LOG.infof("Pool status consultado - Ativas: %d, Disponíveis: %d, Utilização: %.2f%%", 
                     active, metrics.availableCount(), utilizacao);
            
        } catch (Exception e) {
            LOG.error("Erro ao obter status do pool", e);
            status.put("erro", e.getMessage());
        }
        
        return status;
    }
}

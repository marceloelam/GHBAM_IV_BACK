package br.com.iv.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import br.com.iv.util.UsuariosConectadosUtil;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Job agendado que remove usuários inativos a cada 3 minutos.
 * Remove usuários que não enviaram heartbeat há mais de 3,5 minutos.
 */
@ApplicationScoped
public class UsuariosOnlineCleanupJob {
    
    private static final Logger LOGGER = Logger.getLogger(UsuariosOnlineCleanupJob.class.getName());
    
    private static final long INACTIVITY_THRESHOLD_MS = 45 * 1000;
    
    /**
     * Executa a cada 3 minutos (180 segundos).
     * Remove usuários que não enviaram heartbeat há mais de 3,5 minutos.
     */
    @Scheduled(every = "15s")
    void cleanupInactiveUsers() {
        LOGGER.log(Level.FINE, "Iniciando limpeza de usuários inativos...");
        
        long cutoffTime = System.currentTimeMillis() - INACTIVITY_THRESHOLD_MS;
        int removed = UsuariosConectadosUtil.removeInactiveUsers(cutoffTime);
        
        if (removed > 0) {
            LOGGER.log(Level.INFO, "Removidos {0} usuários inativos", removed);
            System.out.println("✓ Removidos " + removed + " usuários inativos");
        } else {
            LOGGER.log(Level.FINE, "Nenhum usuário inativo para remover");
        }
    }
}

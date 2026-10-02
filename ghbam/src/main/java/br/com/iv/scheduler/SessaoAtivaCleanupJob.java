package br.com.iv.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import br.com.iv.repository.SessaoAtivaRepository;
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class SessaoAtivaCleanupJob {

    private static final Logger LOGGER = Logger.getLogger(SessaoAtivaCleanupJob.class.getName());
    // O heartbeat de presenca so e enviado com a aba visivel (document.visibilityState),
    // entao um usuario que apenas troca de aba/minimiza por alguns minutos (ex: olhar PACS,
    // atender ligacao) fica sem heartbeat mas continua com a sessao aberta. Um limite curto
    // aqui derruba sessoes legitimas com 401 SESSAO_ENCERRADA ao voltar para a aba.
    // Deve ficar bem acima do tempo tipico de uma aba em segundo plano e abaixo do TTL do
    // token (3600s, ver TokenService.expiresIn) para nao manter sessoes mortas por muito tempo.
    private static final long EXPIRACAO_SEGUNDOS = 1800;

    @Inject
    SessaoAtivaRepository sessaoAtivaRepository;

    @Scheduled(every = "60s")
    @Transactional
    void cleanupStaleSessions() {
        try {
            int removed = sessaoAtivaRepository.limparSessoesInativasSegundos(EXPIRACAO_SEGUNDOS);
            if (removed > 0) {
                LOGGER.log(Level.INFO, "[CLEANUP] {0} sessoes expiradas desativadas ({1}s sem atividade)",
                        new Object[]{removed, EXPIRACAO_SEGUNDOS});
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[CLEANUP] Erro ao limpar sessoes expiradas: {0}", e.getMessage());
        }
    }
}

package br.com.iv.scheduled;

// ...existing code...
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class LimpezaUsuariosInativosScheduler {

    // Executa a cada 5 minutos
    @Scheduled(every = "5m")
    void limparUsuariosInativos() {
    // Usuários inativos não são removidos automaticamente
    }
}

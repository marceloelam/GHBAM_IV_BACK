package br.com.iv.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

// Portal publico de exames (CPF + nascimento): mesmo padrao de ResetSenhaRateLimitService,
// sem tabela nova. Por IP conta toda consulta (freia quem varre CPFs); por CPF conta so as
// falhas (freia quem tenta adivinhar o nascimento de uma pessoa).
@ApplicationScoped
public class PortalPacienteRateLimitService {

    private static final int MAX_CONSULTAS_IP = 10;
    private static final long JANELA_IP_SEGUNDOS = 600; // 10 minutos
    private static final long BLOQUEIO_IP_SEGUNDOS = 900; // 15 minutos

    private static final int MAX_FALHAS_CPF = 5;
    private static final long JANELA_CPF_SEGUNDOS = 3600; // 1 hora
    private static final long BLOQUEIO_CPF_SEGUNDOS = 3600; // 1 hora

    private final Map<String, Registro> porIp = new ConcurrentHashMap<>();
    private final Map<String, Registro> porCpf = new ConcurrentHashMap<>();

    private static final class Registro {
        volatile int tentativas;
        volatile long primeiraEpoch;
        volatile long bloqueadoAteEpoch;
    }

    public long segundosBloqueado(String ip, String cpf) {
        return Math.max(segundosBloqueado(porIp, chave(ip)), segundosBloqueado(porCpf, chave(cpf)));
    }

    public void registrarConsultaIp(String ip) {
        registrar(porIp, chave(ip), MAX_CONSULTAS_IP, JANELA_IP_SEGUNDOS, BLOQUEIO_IP_SEGUNDOS);
    }

    public void registrarFalhaCpf(String cpf) {
        registrar(porCpf, chave(cpf), MAX_FALHAS_CPF, JANELA_CPF_SEGUNDOS, BLOQUEIO_CPF_SEGUNDOS);
    }

    private long segundosBloqueado(Map<String, Registro> mapa, String chave) {
        if (chave.isEmpty()) return 0;
        Registro r = mapa.get(chave);
        if (r == null) return 0;
        long agora = Instant.now().getEpochSecond();
        return r.bloqueadoAteEpoch > agora ? r.bloqueadoAteEpoch - agora : 0;
    }

    private void registrar(Map<String, Registro> mapa, String chave, int max, long janela, long bloqueio) {
        if (chave.isEmpty()) return;
        long agora = Instant.now().getEpochSecond();
        mapa.compute(chave, (k, r) -> {
            if (r == null || (agora - r.primeiraEpoch) > janela) {
                Registro novo = new Registro();
                novo.tentativas = 1;
                novo.primeiraEpoch = agora;
                return novo;
            }
            r.tentativas++;
            if (r.tentativas >= max) {
                r.bloqueadoAteEpoch = agora + bloqueio;
            }
            return r;
        });
        mapa.entrySet().removeIf(e -> {
            Registro r = e.getValue();
            return r.bloqueadoAteEpoch <= agora && (agora - r.primeiraEpoch) > Math.max(janela, bloqueio);
        });
    }

    private String chave(String valor) {
        return valor == null ? "" : valor.trim();
    }
}

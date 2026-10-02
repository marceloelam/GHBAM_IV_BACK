package br.com.iv.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

// Bloqueia tentativas repetidas de login em memoria (sem tabela nova, mesmo
// padrao de TokenRevocationService). Duas chaves independentes: por matricula
// (protege uma conta especifica de forca bruta, o risco real desde que o
// teclado virtual passou a rodar bcrypt) e por IP (protege contra alguem
// varrendo varias matriculas rapido da mesma origem). Zera automaticamente
// apos login bem-sucedido.
@ApplicationScoped
public class LoginRateLimitService {

    // Por matricula: protege UMA conta especifica de forca bruta, continua
    // apertado. Por IP: um IP real normalmente representa varios profissionais
    // atras do mesmo gateway/NAT da unidade, entao um limite igual ao da
    // matricula derrubava logins legitimos concorrentes junto com o alvo real
    // (alguem varrendo varias matriculas rapido da mesma origem).
    private static final int MAX_TENTATIVAS_MATRICULA = 3;
    private static final int MAX_TENTATIVAS_IP = 30;
    private static final long JANELA_SEGUNDOS = 120;
    private static final long BLOQUEIO_SEGUNDOS = 120;
    // Sem teto, bloqueiosConsecutivos crescia pra sempre a cada nova tentativa
    // (mesmo durante o bloqueio), fazendo o tempo de espera so aumentar e nunca
    // se resolver sozinho. Teto de 5 limita o bloqueio maximo a 10 minutos.
    private static final int MAX_BLOQUEIOS_CONSECUTIVOS = 5;

    private final Map<String, Registro> porMatricula = new ConcurrentHashMap<>();
    private final Map<String, Registro> porIp = new ConcurrentHashMap<>();

    private static final class Registro {
        volatile int tentativas;
        volatile int bloqueiosConsecutivos;
        volatile long primeiraTentativaEpoch;
        volatile long bloqueadoAteEpoch;
    }

    public long segundosBloqueadoMatricula(String matricula) {
        return segundosBloqueado(porMatricula, normalizarMatricula(matricula));
    }

    public long segundosBloqueadoIp(String ip) {
        return segundosBloqueado(porIp, normalizarIp(ip));
    }

    public void registrarFalhaMatricula(String matricula) {
        registrarFalha(porMatricula, normalizarMatricula(matricula), MAX_TENTATIVAS_MATRICULA);
    }

    public void registrarFalhaIp(String ip) {
        registrarFalha(porIp, normalizarIp(ip), MAX_TENTATIVAS_IP);
    }

    public void registrarSucessoMatricula(String matricula) {
        porMatricula.remove(normalizarMatricula(matricula));
    }

    public void registrarSucessoIp(String ip) {
        porIp.remove(normalizarIp(ip));
    }

    private long segundosBloqueado(Map<String, Registro> mapa, String chave) {
        if (chave.isEmpty()) return 0;
        Registro r = mapa.get(chave);
        if (r == null) return 0;
        long agora = Instant.now().getEpochSecond();
        return r.bloqueadoAteEpoch > agora ? r.bloqueadoAteEpoch - agora : 0;
    }

    private void registrarFalha(Map<String, Registro> mapa, String chave, int maxTentativas) {
        if (chave.isEmpty()) return;
        long agora = Instant.now().getEpochSecond();
        mapa.compute(chave, (k, r) -> {
            if (r == null || (agora - r.primeiraTentativaEpoch) > JANELA_SEGUNDOS) {
                Registro novo = new Registro();
                novo.tentativas = 1;
                novo.primeiraTentativaEpoch = agora;
                if (r != null && r.bloqueiosConsecutivos > 0) {
                    novo.bloqueiosConsecutivos = r.bloqueiosConsecutivos;
                }
                return novo;
            }
            r.tentativas++;
            if (r.tentativas >= maxTentativas) {
                r.bloqueiosConsecutivos = Math.min(r.bloqueiosConsecutivos + 1, MAX_BLOQUEIOS_CONSECUTIVOS);
                r.bloqueadoAteEpoch = agora + BLOQUEIO_SEGUNDOS * r.bloqueiosConsecutivos;
                r.tentativas = 0;
                r.primeiraTentativaEpoch = agora;
            }
            return r;
        });
        limpar(mapa);
    }

    private void limpar(Map<String, Registro> mapa) {
        long agora = Instant.now().getEpochSecond();
        mapa.entrySet().removeIf(e -> {
            Registro r = e.getValue();
            boolean semBloqueioAtivo = r.bloqueadoAteEpoch == 0 || r.bloqueadoAteEpoch <= agora;
            boolean janelaVelha = (agora - r.primeiraTentativaEpoch) > JANELA_SEGUNDOS;
            return semBloqueioAtivo && janelaVelha;
        });
    }

    private String normalizarMatricula(String matricula) {
        return matricula == null ? "" : matricula.trim().toUpperCase();
    }

    private String normalizarIp(String ip) {
        return ip == null ? "" : ip.trim();
    }
}

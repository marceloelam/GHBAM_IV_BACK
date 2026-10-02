package br.com.iv.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

// Bloqueia tentativas repetidas de criar solicitacoes de reset de senha (sem tabela nova,
// mesmo padrao de LoginRateLimitService). Duas chaves independentes: por matricula
// (protege uma conta de abuso) e por IP (protege contra alguem varrendo varias matriculas).
@ApplicationScoped
public class ResetSenhaRateLimitService {

    private static final int MAX_SOLICITACOES = 3;
    private static final long JANELA_SEGUNDOS = 600; // 10 minutos
    private static final long BLOQUEIO_SEGUNDOS = 300; // 5 minutos

    private final Map<String, Registro> porMatricula = new ConcurrentHashMap<>();
    private final Map<String, Registro> porIp = new ConcurrentHashMap<>();

    private static final class Registro {
        volatile int solicitacoes;
        volatile long primeiraSolicitacaoEpoch;
        volatile long bloqueadoAteEpoch;
    }

    public long segundosBloqueadoMatricula(String matricula) {
        return segundosBloqueado(porMatricula, normalizarMatricula(matricula));
    }

    public long segundosBloqueadoIp(String ip) {
        return segundosBloqueado(porIp, normalizarIp(ip));
    }

    public void registrarSolicitacaoMatricula(String matricula) {
        registrarSolicitacao(porMatricula, normalizarMatricula(matricula));
    }

    public void registrarSolicitacaoIp(String ip) {
        registrarSolicitacao(porIp, normalizarIp(ip));
    }

    private long segundosBloqueado(Map<String, Registro> mapa, String chave) {
        if (chave.isEmpty()) return 0;
        Registro r = mapa.get(chave);
        if (r == null) return 0;
        long agora = Instant.now().getEpochSecond();
        return r.bloqueadoAteEpoch > agora ? r.bloqueadoAteEpoch - agora : 0;
    }

    private void registrarSolicitacao(Map<String, Registro> mapa, String chave) {
        if (chave.isEmpty()) return;
        long agora = Instant.now().getEpochSecond();
        mapa.compute(chave, (k, r) -> {
            if (r == null || (agora - r.primeiraSolicitacaoEpoch) > JANELA_SEGUNDOS) {
                Registro novo = new Registro();
                novo.solicitacoes = 1;
                novo.primeiraSolicitacaoEpoch = agora;
                return novo;
            }
            r.solicitacoes++;
            if (r.solicitacoes >= MAX_SOLICITACOES) {
                r.bloqueadoAteEpoch = agora + BLOQUEIO_SEGUNDOS;
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
            boolean janelaVelha = (agora - r.primeiraSolicitacaoEpoch) > JANELA_SEGUNDOS;
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

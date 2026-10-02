package br.com.iv.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import br.com.iv.dto.LogDto;
import br.com.iv.entity.SessaoAtiva;
import br.com.iv.repository.SessaoAtivaRepository;
import br.com.iv.security.ExpiringRequestCache;

@ApplicationScoped
public class SessaoAtivaService {

    private static final Logger LOGGER = Logger.getLogger(SessaoAtivaService.class.getName());
    private static final long SESSAO_ORFA_SEGUNDOS = 5 * 60;
    private static final long TOKEN_ATIVO_CACHE_TTL_MS = 500;
    private static final int TOKEN_ATIVO_CACHE_MAX_ENTRIES = 8_192;
    private final ExpiringRequestCache<String, Boolean> tokenAtivoCache =
            new ExpiringRequestCache<>(TOKEN_ATIVO_CACHE_TTL_MS, TOKEN_ATIVO_CACHE_MAX_ENTRIES);

    @Inject
    SessaoAtivaRepository sessaoAtivaRepository;

    @Inject
    LogService logService;

    public Optional<SessaoAtiva> buscarSessaoAtiva(String matricula) {
        return sessaoAtivaRepository.findByMatriculaAtiva(normalizarMatricula(matricula));
    }

    private String hashToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return token;
        }
    }

    @Transactional
    public SessaoAtiva criarSessao(String matricula, String nome, String token, String ipAddress, String computador, String unidade) {
        String matriculaNormalizada = normalizarMatricula(matricula);
        sessaoAtivaRepository.desativarSessoesPorMatricula(matriculaNormalizada);

        SessaoAtiva sessao = new SessaoAtiva();
        sessao.setMatricula(limitar(matriculaNormalizada, 15));
        sessao.setNome(limitar(nome, 55));
        sessao.setTokenHash(hashToken(token));
        sessao.setIpAddress(limitar(ipAddress, 45));
        sessao.setComputador(limitar(computador, 100));
        sessao.setUnidade(limitar(unidade, 30));
        sessao.setDataLogin(LocalDateTime.now());
        sessao.setUltimaAtividade(LocalDateTime.now());
        sessao.setAtiva(true);
        sessao.persist();
        tokenAtivoCache.clear();
        return sessao;
    }

    @Transactional
    public SessaoAtiva criarSessaoSeDisponivel(
            String matricula, String nome, String token,
            String ipAddress, String computador, String unidade,
            boolean forcar) {

        String matriculaNormalizada = normalizarMatricula(matricula);
        Optional<SessaoAtiva> sessaoExistente = sessaoAtivaRepository.findByMatriculaAtiva(matriculaNormalizada);
        LOGGER.log(Level.INFO, "[SESSAO_ATIVA] verificar matricula={0} forcar={1} existente={2} computador={3}",
                new Object[]{matriculaNormalizada, forcar, sessaoExistente.isPresent(), computador});

        if (sessaoExistente.isPresent()) {
            SessaoAtiva existente = sessaoExistente.get();
            if (isSessaoCelularAnterior(existente, computador)) {
                int encerradas = sessaoAtivaRepository.desativarSessoesPorMatricula(matriculaNormalizada);
                LOGGER.log(Level.INFO, "[SESSAO_ATIVA] sessao celular anterior liberada matricula={0} sessoes_encerradas={1}",
                        new Object[]{matriculaNormalizada, encerradas});
            } else if (isSessaoOrfa(existente)) {
                int encerradas = sessaoAtivaRepository.desativarSessoesPorMatricula(matriculaNormalizada);
                LOGGER.log(Level.INFO, "[SESSAO_ATIVA] sessao orfa liberada matricula={0} sessoes_encerradas={1}",
                        new Object[]{matriculaNormalizada, encerradas});
            } else if (!forcar) {
                LOGGER.log(Level.INFO, "[SESSAO_ATIVA] bloqueando login duplicado matricula={0} sessao={1}",
                        new Object[]{matriculaNormalizada, existente.getIdsessao()});
                throw new SessaoJaAtivaException(existente);
            } else {
                int encerradas = sessaoAtivaRepository.desativarSessoesPorMatricula(matriculaNormalizada);
                LOGGER.log(Level.INFO, "[SESSAO_ATIVA] login forcado matricula={0} sessoes_encerradas={1}",
                        new Object[]{matriculaNormalizada, encerradas});
                LogDto logDto = new LogDto();
                logDto.setData(LocalDate.now());
                logDto.setHora(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                logDto.setEvento("SESSAO ANTERIOR ENCERRADA - " + nome + " (matricula " + matriculaNormalizada + ") forcou o login. Sessao anterior encerrada.");
                logDto.setUnidade(unidade != null ? unidade : "GERAL");
                logService.salvarLogAsync(logDto);
            }
        }

        SessaoAtiva sessao = new SessaoAtiva();
        sessao.setMatricula(limitar(matriculaNormalizada, 15));
        sessao.setNome(limitar(nome, 55));
        sessao.setTokenHash(hashToken(token));
        sessao.setIpAddress(limitar(ipAddress, 45));
        sessao.setComputador(limitar(computador, 100));
        sessao.setUnidade(limitar(unidade, 30));
        sessao.setDataLogin(LocalDateTime.now());
        sessao.setUltimaAtividade(LocalDateTime.now());
        sessao.setAtiva(true);

        try {
            // persistAndFlush forca o INSERT agora, disparando a violacao do indice unico
            // (idx_unique_active_session) dentro deste try/catch. Com persist() simples a
            // violacao so apareceria no commit, fora daqui, e o login duplicado passaria.
            sessao.persistAndFlush();
            tokenAtivoCache.clear();
            LOGGER.log(Level.INFO, "[SESSAO_ATIVA] nova sessao gravada matricula={0} id={1} computador={2}",
                    new Object[]{matriculaNormalizada, sessao.getIdsessao(), computador});
        } catch (PersistenceException ex) {
            if (!forcar) {
                SessaoAtiva ficticia = new SessaoAtiva();
                ficticia.setMatricula(limitar(matriculaNormalizada, 15));
                ficticia.setIpAddress(limitar(ipAddress, 45));
                ficticia.setComputador(limitar(computador, 100));
                throw new SessaoJaAtivaException(ficticia);
            }
            throw ex;
        }

        return sessao;
    }

    public Optional<SessaoAtiva> buscarPorToken(String token) {
        return sessaoAtivaRepository.findByTokenHash(hashToken(token));
    }

    public boolean tokenAtivo(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String tokenHash = hashToken(token);
        return tokenAtivoCache.get(tokenHash, hash -> sessaoAtivaRepository.findByTokenHash(hash)
                .map(sessao -> Boolean.TRUE.equals(sessao.getAtiva()))
                .orElse(false));
    }

    @Transactional
    public void encerrarSessaoPorToken(String token) {
        String tokenHash = hashToken(token);
        sessaoAtivaRepository.desativarPorTokenHash(tokenHash);
        tokenAtivoCache.invalidate(tokenHash);
    }

    @Transactional
    public void encerrarSessaoPorMatricula(String matricula) {
        sessaoAtivaRepository.desativarSessoesPorMatricula(normalizarMatricula(matricula));
        tokenAtivoCache.clear();
    }

    @Transactional
    public void atualizarAtividade(String matricula) {
        sessaoAtivaRepository.findByMatriculaAtiva(normalizarMatricula(matricula)).ifPresent(s -> {
            s.setUltimaAtividade(LocalDateTime.now());
            s.persist();
        });
    }

    private String normalizarMatricula(String matricula) {
        return matricula == null ? "" : matricula.trim();
    }

    private String limitar(String valor, int tamanhoMaximo) {
        if (valor == null) {
            return "";
        }
        String texto = valor.trim();
        return texto.length() <= tamanhoMaximo ? texto : texto.substring(0, tamanhoMaximo);
    }

    private boolean isSessaoOrfa(SessaoAtiva sessao) {
        return sessao == null
                || sessao.getUltimaAtividade() == null
                || sessao.getUltimaAtividade().plusSeconds(SESSAO_ORFA_SEGUNDOS).isBefore(LocalDateTime.now());
    }

    private boolean isSessaoCelularAnterior(SessaoAtiva sessao, String novoComputador) {
        return isComputadorCelular(sessao == null ? null : sessao.getComputador())
                && isComputadorCelular(novoComputador);
    }

    private boolean isComputadorCelular(String computador) {
        return computador != null && "celular".equalsIgnoreCase(computador.trim());
    }
}


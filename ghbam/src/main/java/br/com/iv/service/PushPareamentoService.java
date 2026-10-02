package br.com.iv.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import br.com.iv.entity.PushDispositivo;
import br.com.iv.entity.PushPareamentoToken;
import br.com.iv.entity.Servidor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PushPareamentoService {

    private static final int TOKEN_DURACAO_MINUTOS = 5;
    private static final int TOKEN_LENGTH_BYTES = 32;

    @Inject
    ServidorService servidorService;

    @Transactional
    public String gerarToken(String matricula) {
        validarMatricula(matricula);

        // Gerar token opaco aleatorio (URL-safe Base64)
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[TOKEN_LENGTH_BYTES];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Criar registro no banco
        PushPareamentoToken pareamento = new PushPareamentoToken();
        pareamento.setMatricula(matricula.trim());
        pareamento.setToken(token);
        pareamento.setCriadoEm(LocalDateTime.now());
        pareamento.setExpiraEm(LocalDateTime.now().plusMinutes(TOKEN_DURACAO_MINUTOS));
        pareamento.persist();

        return token;
    }

    public Optional<String> consultarToken(String token) {
        return PushPareamentoToken
            .<PushPareamentoToken>find("token = ?1 and expiraEm > ?2 and usadoEm is null",
                token, LocalDateTime.now())
            .firstResultOptional()
            .map(PushPareamentoToken::getMatricula);
    }

    @Transactional
    public void confirmarPareamento(String token, String endpoint, String p256dh, String authKey, String userAgent,
        String nomeDispositivo) {
        PushPareamentoToken pareamento = PushPareamentoToken
            .<PushPareamentoToken>find("token = ?1 and expiraEm > ?2 and usadoEm is null",
                token, LocalDateTime.now())
            .firstResultOptional()
            .orElseThrow(() -> new IllegalArgumentException("Token de pareamento invalido ou expirado."));

        String matricula = pareamento.getMatricula();

        // Regra de negocio: um unico dispositivo ativo por matricula. Para trocar,
        // o usuario precisa remover o dispositivo atual antes de parear outro
        // (tela "Meus dispositivos pareados") - nao ha substituicao automatica.
        Optional<PushDispositivo> dispositivoAtivo = PushDispositivo
            .<PushDispositivo>find("matricula = ?1 and ativo = true", matricula)
            .firstResultOptional();
        if (dispositivoAtivo.isPresent() && !endpoint.equals(dispositivoAtivo.get().getEndpoint())) {
            throw new IllegalArgumentException(
                "Ja existe um dispositivo pareado para esta matricula. Remova-o antes de parear outro.");
        }

        // Marcar token como usado
        pareamento.setUsadoEm(LocalDateTime.now());
        pareamento.persist();

        // Criar ou atualizar dispositivo
        PushDispositivo dispositivo = PushDispositivo
            .<PushDispositivo>find("matricula = ?1 and endpoint = ?2", matricula, endpoint)
            .firstResultOptional()
            .orElseGet(PushDispositivo::new);

        dispositivo.setMatricula(matricula);
        dispositivo.setEndpoint(endpoint);
        dispositivo.setP256dh(p256dh);
        dispositivo.setAuthKey(authKey);
        dispositivo.setAtivo(true);
        dispositivo.setUserAgent(userAgent);
        dispositivo.setNomeDispositivo(normalizarNomeDispositivo(nomeDispositivo));
        if (dispositivo.getCriadoEm() == null) {
            dispositivo.setCriadoEm(LocalDateTime.now());
        }
        dispositivo.persist();
        atualizarResumoNoServidor(matricula, dispositivo, true);
    }

    @Transactional
    public void sincronizarDispositivo(String matricula, String endpoint, String p256dh, String authKey,
        String userAgent, String nomeDispositivo) {
        validarMatricula(matricula);
        PushDispositivo dispositivo = PushDispositivo.<PushDispositivo>find("matricula = ?1", matricula)
            .firstResultOptional().orElseGet(PushDispositivo::new);
        dispositivo.setMatricula(matricula);
        dispositivo.setEndpoint(endpoint);
        dispositivo.setP256dh(p256dh);
        dispositivo.setAuthKey(authKey);
        dispositivo.setAtivo(true);
        dispositivo.setUserAgent(userAgent);
        dispositivo.setNomeDispositivo(normalizarNomeDispositivo(nomeDispositivo));
        if (dispositivo.getCriadoEm() == null) dispositivo.setCriadoEm(LocalDateTime.now());
        dispositivo.persist();
        atualizarResumoNoServidor(matricula, dispositivo, true);
    }

    @Transactional
    public void renovarDispositivoAprovador(String matricula, String endpoint, String p256dh, String authKey,
        String userAgent, String nomeDispositivo) {
        validarMatricula(matricula);

        // A resposta recebida no celular prova a posse do dispositivo pareado.
        // Renova a subscription para que o próximo reset não reutilize um
        // endpoint que o navegador acabou de invalidar.
        PushDispositivo.<PushDispositivo>find("matricula = ?1 and ativo = true and endpoint <> ?2", matricula, endpoint)
            .list()
            .forEach(item -> {
                PushDispositivo antigo = (PushDispositivo) item;
                antigo.setAtivo(false);
                antigo.persist();
            });

        PushDispositivo dispositivo = PushDispositivo
            .<PushDispositivo>find("matricula = ?1 and endpoint = ?2", matricula, endpoint)
            .firstResultOptional()
            .orElseGet(PushDispositivo::new);
        dispositivo.setMatricula(matricula);
        dispositivo.setEndpoint(endpoint);
        dispositivo.setP256dh(p256dh);
        dispositivo.setAuthKey(authKey);
        dispositivo.setAtivo(true);
        dispositivo.setUserAgent(userAgent);
        dispositivo.setNomeDispositivo(normalizarNomeDispositivo(nomeDispositivo));
        if (dispositivo.getCriadoEm() == null) dispositivo.setCriadoEm(LocalDateTime.now());
        dispositivo.persist();
        atualizarResumoNoServidor(matricula, dispositivo, true);
    }

    private String normalizarNomeDispositivo(String nomeDispositivo) {
        if (nomeDispositivo == null || nomeDispositivo.isBlank()) {
            return null;
        }
        String nome = nomeDispositivo.trim();
        return nome.length() > 120 ? nome.substring(0, 120) : nome;
    }

    public java.util.List<PushDispositivo> listarDispositivos(String matricula) {
        return PushDispositivo
            .<PushDispositivo>find("matricula = ?1 order by ativo desc, criadoEm desc", matricula)
            .list();
    }

    @Transactional
    public void removerDispositivo(String matricula, Long id) {
        PushDispositivo dispositivo = PushDispositivo
            .<PushDispositivo>find("id = ?1 and matricula = ?2", id, matricula)
            .firstResultOptional()
            .orElseThrow(() -> new IllegalArgumentException("Dispositivo nao encontrado."));

        // Exclusão é uma ação explícita do usuário: remove a assinatura e o
        // resumo da conta, permitindo gerar outro QR Code imediatamente.
        dispositivo.delete();
        atualizarResumoNoServidor(matricula, dispositivo, false);
    }

    private void atualizarResumoNoServidor(String matricula, PushDispositivo dispositivo, boolean ativo) {
        Servidor servidor = Servidor.<Servidor>find("matricula = ?1", matricula).firstResult();
        if (servidor == null) return;
        servidor.setCelularPareadoAtivo(ativo);
        servidor.setCelularPareadoNome(ativo ? dispositivo.getNomeDispositivo() : null);
        servidor.setCelularPareadoEm(ativo ? LocalDateTime.now() : null);
        servidor.persist();
    }

    private void validarMatricula(String matricula) {
        if (matricula == null || matricula.trim().isEmpty()) {
            throw new IllegalArgumentException("Matricula obrigatoria.");
        }
    }
}

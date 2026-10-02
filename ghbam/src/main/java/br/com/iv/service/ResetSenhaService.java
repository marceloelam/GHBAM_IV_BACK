package br.com.iv.service;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.iv.dto.LogDto;
import br.com.iv.entity.PushDispositivo;
import br.com.iv.entity.ResetSenhaSolicitacao;
import br.com.iv.entity.Servidor;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.security.ResetSenhaRateLimitService;
import br.gov.secure.AppSecrets;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ResetSenhaService {

    private static final Logger LOGGER = Logger.getLogger(ResetSenhaService.class.getName());
    private static final int DURACAO_SOLICITACAO_MINUTOS = 1;
    // Mantido apenas para encerrar de forma segura solicitacoes antigas iniciadas
    // antes do fluxo de redefinicao direta para a senha padrao.
    private static final int DURACAO_TOKEN_CONFIRMACAO_MINUTOS = 2;
    private static final String SENHA_PADRAO_PRIMEIRO_ACESSO = "123456";
    private static final int TOKEN_LENGTH_BYTES = 32;

    @Inject
    ResetSenhaRateLimitService rateLimitService;

    @Inject
    PushNotificationService pushNotificationService;

    @Inject
    PushPareamentoService pushPareamentoService;

    @Inject
    ServidorService servidorService;

    @Inject
    ServidorRepository servidorRepository;

    @Inject
    ReconhecimentoFacialService reconhecimentoFacialService;

    @Inject
    LogService logService;

    @Inject
    SessaoAtivaService sessaoAtivaService;

    @Transactional
    public void redefinirSenhaPadraoPorValidacaoFacial(String matricula, String imagemBase64, String ip,
        String computador) {
        matricula = matricula == null ? "" : matricula.trim();

        if (matricula.isBlank() || imagemBase64 == null || imagemBase64.isBlank()) {
            throw new IllegalArgumentException("Matricula e foto sao obrigatorias.");
        }

        if (!reconhecimentoFacialService.configurado()) {
            throw new IllegalStateException("Reconhecimento facial nao configurado no servidor.");
        }

        long bloqueioMatricula = rateLimitService.segundosBloqueadoMatricula(matricula);
        long bloqueioIp = rateLimitService.segundosBloqueadoIp(ip);
        if (bloqueioMatricula > 0 || bloqueioIp > 0) {
            salvarLog("RESET_SENHA_FACIAL_BLOQUEADO_RATE_LIMIT", matricula);
            throw new IllegalArgumentException("Muitas tentativas. Aguarde alguns minutos e tente novamente.");
        }

        rateLimitService.registrarSolicitacaoMatricula(matricula);
        rateLimitService.registrarSolicitacaoIp(ip);

        Servidor servidor = servidorRepository.localizarMatricula(matricula);
        if (servidor == null) {
            salvarLog("RESET_SENHA_FACIAL_MATRICULA_NAO_ENCONTRADA", matricula);
            throw new IllegalArgumentException("Matricula nao encontrada.");
        }

        if (servidor.getFoto() == null || servidor.getFoto().isBlank()) {
            salvarLog("RESET_SENHA_FACIAL_SEM_FOTO_CADASTRADA", matricula);
            throw new IllegalArgumentException("Servidor sem foto cadastrada para validacao facial.");
        }

        ReconhecimentoFacialService.ResultadoValidacao resultado =
            reconhecimentoFacialService.validar(servidor.getFoto(), imagemBase64);
        if (!resultado.isAprovado()) {
            salvarLog("RESET_SENHA_FACIAL_NAO_RECONHECIDA", matricula);
            throw new ValidacaoFacialException(
                resultado.getMensagem(),
                resultado.getSimilaridade(),
                reconhecimentoFacialService.getSimilarityThreshold()
            );
        }

        ResetSenhaSolicitacao solicitacao = new ResetSenhaSolicitacao();
        solicitacao.setMatricula(matricula);
        solicitacao.setIpSolicitante(ip);
        solicitacao.setComputador(computador);
        solicitacao.setCodigoConfirmacao(gerarCodigoAleatorio());
        solicitacao.setStatus("PENDENTE");
        solicitacao.setAprovarToken(gerarTokenOpaco());
        solicitacao.setConsultaToken(gerarTokenOpaco());
        solicitacao.setCriadoEm(LocalDateTime.now());
        solicitacao.setRespondidoEm(LocalDateTime.now());
        solicitacao.setExpiraEm(LocalDateTime.now().plusMinutes(DURACAO_SOLICITACAO_MINUTOS));

        redefinirParaSenhaPadrao(solicitacao);
        solicitacao.persist();
        salvarLog("RESET_SENHA_FACIAL_CONCLUIDO_SIMILARIDADE_" + resultado.getSimilaridade(), matricula);
    }

    @Transactional
    public String criarSolicitacao(String matricula, String ip, String computador) {
        matricula = matricula == null ? "" : matricula.trim();

        // Aplicar rate limit: so consultar aqui, sem registrar - registrar de novo
        // um bloqueio ja ativo so estenderia a janela sem proteger nada a mais.
        long bloqueioMatricula = rateLimitService.segundosBloqueadoMatricula(matricula);
        long bloqueioIp = rateLimitService.segundosBloqueadoIp(ip);
        boolean bloqueado = bloqueioMatricula > 0 || bloqueioIp > 0;

        // Sempre retornar um ID de solicitacao, mesmo bloqueado ou sem dispositivo pareado
        // (para nao vazar informacao sobre enumeracao de matricula nem sobre rate limit)
        ResetSenhaSolicitacao solicitacao = new ResetSenhaSolicitacao();
        solicitacao.setMatricula(matricula);
        solicitacao.setIpSolicitante(ip);
        solicitacao.setComputador(computador);
        solicitacao.setCodigoConfirmacao(gerarCodigoAleatorio());
        solicitacao.setStatus("PENDENTE");
        solicitacao.setAprovarToken(gerarTokenOpaco());
        solicitacao.setConsultaToken(gerarTokenOpaco());
        solicitacao.setCriadoEm(LocalDateTime.now());
        solicitacao.setExpiraEm(LocalDateTime.now().plusMinutes(DURACAO_SOLICITACAO_MINUTOS));
        solicitacao.persist();

        String consultaToken = solicitacao.getConsultaToken();

        if (bloqueado) {
            // Nao busca dispositivo nem envia push: e aqui que o rate limit de fato
            // protege contra spam de notificacao/varredura de matricula. O token
            // devolvido e identico ao do fluxo normal para nao vazar o bloqueio.
            LOGGER.info("[ResetSenha] Solicitacao bloqueada por rate limit para matricula '" + matricula + "'.");
            salvarLog("RESET_SENHA_BLOQUEADA_RATE_LIMIT", matricula);
            return consultaToken;
        }

        // Buscar dispositivos ativos da matrícula e enviar push
        List<PushDispositivo> dispositivos = PushDispositivo
            .<PushDispositivo>find("matricula = ?1 and ativo = true", matricula)
            .list();

        if (!dispositivos.isEmpty()) {
            LOGGER.info("[ResetSenha] " + dispositivos.size() + " dispositivo(s) ativo(s) encontrado(s) para matricula "
                + matricula + " - enviando push.");
            registrarRateLimitApenasSeOuve(matricula, ip);

            Map<String, Object> dados = new HashMap<>();
            dados.put("requestId", consultaToken);
            dados.put("aprovarToken", solicitacao.getAprovarToken());
            dados.put("codigo", solicitacao.getCodigoConfirmacao());
            dados.put("vapidPublicKey", AppSecrets.VAPID_PUBLIC_KEY);

            for (PushDispositivo dispositivo : dispositivos) {
                String resultadoEnvio = pushNotificationService.enviarNotificacao(
                    dispositivo,
                    "Recuperação de Senha",
                    "Uma solicitação de recuperação de senha foi recebida. Código: " + solicitacao.getCodigoConfirmacao(),
                    dados
                );
                // Auditoria de entrega sem expor endpoint, tokens ou codigo.
                salvarLog("RESET_SENHA_PUSH_" + resultadoEnvio + " (dispositivo " + dispositivo.getId() + ")", matricula);
            }

            salvarLog("RESET_SENHA_SOLICITADA", matricula);
        } else {
            // Nenhum dispositivo pareado - criar solicitacao fantasma que expirara
            LOGGER.info("[ResetSenha] Nenhum dispositivo ativo pareado para matricula '" + matricula + "'.");
            salvarLog("RESET_SENHA_SOLICITADA_SEM_DEVICE", matricula);
        }

        return consultaToken;
    }

    public Map<String, Object> consultarStatus(String consultaToken) {
        Optional<ResetSenhaSolicitacao> opt = ResetSenhaSolicitacao
            .<ResetSenhaSolicitacao>find("consultaToken = ?1", consultaToken)
            .firstResultOptional();

        Map<String, Object> resposta = new HashMap<>();
        if (opt.isEmpty()) {
            resposta.put("status", "NAO_ENCONTRADA");
            return resposta;
        }

        ResetSenhaSolicitacao solicitacao = opt.get();

        // Verificar se expirou
        if (LocalDateTime.now().isAfter(solicitacao.getExpiraEm())) {
            if (!"CONCLUIDO".equals(solicitacao.getStatus())) {
                solicitacao.setStatus("EXPIRADO");
                solicitacao.persist();
            }
        }

        resposta.put("status", solicitacao.getStatus());
        // O codigo de confirmacao NUNCA e devolvido aqui: e publico e sem autenticacao,
        // e o desktop ja recebeu o codigo na resposta de criacao da solicitacao.
        // Devolve-lo aqui permitiria a qualquer um "ler" o codigo por tentativa de token.

        return resposta;
    }

    @Transactional
    public void responder(String aprovarToken, boolean aprovado, String endpoint, String p256dh, String authKey,
        String userAgent, String nomeDispositivo) {
        ResetSenhaSolicitacao solicitacao = ResetSenhaSolicitacao
            .<ResetSenhaSolicitacao>find("aprovarToken = ?1", aprovarToken)
            .firstResultOptional()
            .orElseThrow(() -> new IllegalArgumentException("Token de aprovacao invalido."));

        // Validar expiracao
        if (LocalDateTime.now().isAfter(solicitacao.getExpiraEm())) {
            solicitacao.setStatus("EXPIRADO");
            solicitacao.setRespondidoEm(LocalDateTime.now());
            solicitacao.persist();
            throw new IllegalArgumentException("Solicitacao expirada.");
        }

        if (endpoint != null && !endpoint.isBlank() && p256dh != null && !p256dh.isBlank()
            && authKey != null && !authKey.isBlank()) {
            pushPareamentoService.renovarDispositivoAprovador(solicitacao.getMatricula(), endpoint, p256dh, authKey,
                userAgent, nomeDispositivo);
        }

        solicitacao.setRespondidoEm(LocalDateTime.now());
        if (aprovado) {
            redefinirParaSenhaPadrao(solicitacao);
        } else {
            solicitacao.setStatus("NEGADO");
            salvarLog("RESET_SENHA_NEGADO", solicitacao.getMatricula());
        }
        solicitacao.persist();
    }

    @Transactional
    public void renovarDispositivoAprovador(String aprovarToken, String endpoint, String p256dh, String authKey,
        String userAgent, String nomeDispositivo) {
        ResetSenhaSolicitacao solicitacao = ResetSenhaSolicitacao
            .<ResetSenhaSolicitacao>find("aprovarToken = ?1", aprovarToken)
            .firstResultOptional()
            .orElseThrow(() -> new IllegalArgumentException("Token de aprovacao invalido."));
        if (LocalDateTime.now().isAfter(solicitacao.getExpiraEm())) {
            throw new IllegalArgumentException("Solicitacao expirada.");
        }
        pushPareamentoService.renovarDispositivoAprovador(solicitacao.getMatricula(), endpoint, p256dh, authKey,
            userAgent, nomeDispositivo);
    }

    private void redefinirParaSenhaPadrao(ResetSenhaSolicitacao solicitacao) {
        try {
            String senhaPadraoBase64 = Base64.getEncoder().encodeToString(
                SENHA_PADRAO_PRIMEIRO_ACESSO.getBytes(StandardCharsets.UTF_8));
            servidorService.alterarSenha(solicitacao.getMatricula(), senhaPadraoBase64);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao redefinir senha para a matricula: " + solicitacao.getMatricula(), e);
            throw new RuntimeException("Erro ao redefinir senha.", e);
        }

        solicitacao.setStatus("RESETADO");
        encerrarSessoesAtivas(solicitacao.getMatricula());
        salvarLog("RESET_SENHA_PADRAO_APLICADA", solicitacao.getMatricula());
    }

    @Transactional
    public void confirmarNovaSenha(String tokenConfirmacao, String novaSenhaBase64) {
        ResetSenhaSolicitacao solicitacao = ResetSenhaSolicitacao
            .<ResetSenhaSolicitacao>find("tokenConfirmacao = ?1", tokenConfirmacao)
            .firstResultOptional()
            .orElseThrow(() -> new IllegalArgumentException("Token de confirmacao invalido."));

        // Validar status e expiracao
        if (!"APROVADO".equals(solicitacao.getStatus())) {
            throw new IllegalArgumentException("Solicitacao nao foi aprovada.");
        }

        if (solicitacao.getRespondidoEm() == null ||
            LocalDateTime.now().isAfter(solicitacao.getRespondidoEm().plusMinutes(DURACAO_TOKEN_CONFIRMACAO_MINUTOS))) {
            throw new IllegalArgumentException("Token de confirmacao expirado.");
        }

        // Alterar a senha usando o serviço existente
        try {
            servidorService.alterarSenha(solicitacao.getMatricula(), novaSenhaBase64);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao alterar senha para matricula: " + solicitacao.getMatricula(), e);
            throw new RuntimeException("Erro ao atualizar senha.", e);
        }

        // Marcar como concluido
        solicitacao.setStatus("CONCLUIDO");
        solicitacao.persist();

        // Invalidar todas as sessoes ativas da matrícula por segurança
        encerrarSessoesAtivas(solicitacao.getMatricula());

        salvarLog("RESET_SENHA_CONCLUIDO", solicitacao.getMatricula());
    }

    private void encerrarSessoesAtivas(String matricula) {
        try {
            sessaoAtivaService.encerrarSessaoPorMatricula(matricula);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao encerrar sessoes da matricula: " + matricula, e);
        }
    }

    private String gerarCodigoAleatorio() {
        SecureRandom random = new SecureRandom();
        return String.format("%04d", random.nextInt(10000));
    }

    private String gerarTokenOpaco() {
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[TOKEN_LENGTH_BYTES];
        random.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private void registrarRateLimitApenasSeOuve(String matricula, String ip) {
        // Registrar a solicitacao no rate limit apenas se havia dispositivo pareado
        rateLimitService.registrarSolicitacaoMatricula(matricula);
        rateLimitService.registrarSolicitacaoIp(ip);
    }

    private void salvarLog(String evento, String matricula) {
        try {
            LogDto dto = new LogDto();
            dto.setData(java.time.LocalDate.now());
            dto.setHora(String.format("%02d:%02d",
                LocalDateTime.now().getHour(),
                LocalDateTime.now().getMinute()));
            dto.setEvento(evento + " (matricula " + matricula + ")");
            dto.setUnidade("GERAL");
            logService.salvarLogAsync(dto);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[ResetSenhaService] Erro ao salvar log: {0}", e.getMessage());
        }
    }

    public static class ValidacaoFacialException extends IllegalArgumentException {
        private final double similaridade;
        private final double minimoExigido;

        public ValidacaoFacialException(String mensagem, double similaridade, double minimoExigido) {
            super(mensagem);
            this.similaridade = similaridade;
            this.minimoExigido = minimoExigido;
        }

        public double getSimilaridade() {
            return similaridade;
        }

        public double getMinimoExigido() {
            return minimoExigido;
        }
    }
}

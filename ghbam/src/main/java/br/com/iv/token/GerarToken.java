package br.com.iv.token;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.List;

import br.com.iv.dto.LogDto;
import br.com.iv.dto.ServidorDto;
import br.gov.secure.AppSecrets;
import br.com.iv.entity.SessaoAtiva;
import br.com.iv.repository.ServidorUnidadeRepository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.LoginRateLimitService;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.security.TokenRevocationService;
import br.com.iv.service.LogService;
import br.com.iv.service.ServidorService;
import br.com.iv.service.SessaoAtivaService;
import br.com.iv.service.SessaoJaAtivaException;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GerarToken {

    private static final Logger LOGGER = Logger.getLogger(GerarToken.class.getName());
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");

    @Inject
    AuthService authService;

    @Inject
    TokenService tokenService;

    @Inject
    TokenRevocationService tokenRevocationService;

    @Inject
    LoginRateLimitService loginRateLimitService;

    @Inject
    ServidorService servidorService;

    @Inject
    SessaoAtivaService sessaoAtivaService;

    @Inject
    LogService logService;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Inject
    ServidorUnidadeRepository servidorUnidadeRepository;

    @Inject
    MeterRegistry meterRegistry;

    /** Metrica ghbam_login_tentativas_total{resultado}: base p/ alerta de ataque de senha ou AD fora. */
    private void contarLogin(String resultado) {
        try {
            meterRegistry.counter("ghbam.login.tentativas", "resultado", resultado).increment();
        } catch (Exception ignored) {
            // metrica nunca bloqueia login
        }
    }

    @POST
    @Path("/login")
    public Response login(Usuario request, @Context HttpHeaders httpHeaders, @Context RoutingContext routingContext) {
        if (request == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("mensagem", "Payload de login ausente.");
            return Response.status(Response.Status.BAD_REQUEST).entity(body).build();
        }


        String username = !safe(request.username).isBlank() ? request.username : request.matricula;
        if (safe(username).isBlank()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("mensagem", "Matricula nao informada.");
            return Response.status(Response.Status.BAD_REQUEST).entity(body).build();
        }

        String ip = obterIPCliente(httpHeaders, routingContext);

        long bloqueio = Math.max(
            loginRateLimitService.segundosBloqueadoMatricula(username),
            loginRateLimitService.segundosBloqueadoIp(ip)
        );
        if (bloqueio > 0) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("mensagem", "Muitas tentativas de login. Aguarde " + bloqueio + " segundos e tente novamente.");
            contarLogin("bloqueado");
            return Response.status(429).header("Retry-After", String.valueOf(bloqueio)).entity(body).build();
        }

        Usuario user = authService.validar(username, request.senha, request.senhaMontada);

        // Mesmo endereco para todos os municipios: quem nao autentica no banco desta requisicao pode ser
        // servidor de outra prefeitura - tenta nos bancos em que a matricula existe.
        if (user == null && municipioRoteamento.ativa()) {
            Response outro = loginEmOutroMunicipio(username, request, ip);
            if (outro != null) {
                return outro;
            }
        }

        if (user == null) {
            loginRateLimitService.registrarFalhaMatricula(username);
            loginRateLimitService.registrarFalhaIp(ip);
            if (isAdminRecoveryConfigured()
                    && AppSecrets.ADMIN_RECOVERY_MATRICULA.equals(safe(username))
                    && isAdminPassword(request.senha, request.senhaMontada)) {
                criarAdminSeNaoExistir();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("mensagem", "Bem-vindo Mestre administrador, dados do acesso administrativo atualizados, tente novamente por favor");
                return Response.status(Response.Status.UNAUTHORIZED).entity(body).build();
            }
            if (securityPermissionService.senhaConfereMasSemAd(username, request.senha, request.senhaMontada)) {
                // 403: o Login mostra a mensagem real (401 fica generico de proposito).
                contarLogin("sem_ad");
                Map<String, Object> semAd = new LinkedHashMap<>();
                semAd.put("mensagem", "Usuário sem AD cadastrado. Procure a administração para cadastrar seu usuário de rede (formato nome.sobrenome).");
                return Response.status(Response.Status.FORBIDDEN).entity(semAd).build();
            }
            if (securityPermissionService.senhaConfereMasSuspensaInatividade(username, request.senha, request.senhaMontada)) {
                // 403 com motivo real; o registro no log é feito no SecurityPermissionService.
                contarLogin("suspensa_inatividade");
                Map<String, Object> suspensa = new LinkedHashMap<>();
                suspensa.put("mensagem", "Sua conta foi suspensa por ficar 60 dias sem acesso. Peça ao coordenador do seu setor ou à administração para reativar.");
                return Response.status(Response.Status.FORBIDDEN).entity(suspensa).build();
            }
            Map<String, Object> body = new LinkedHashMap<>();
            contarLogin("falha");
            body.put("mensagem", "Matricula, senha ou usuario ativo nao conferem.");
            return Response.status(Response.Status.UNAUTHORIZED).entity(body).build();
        }

        loginRateLimitService.registrarSucessoMatricula(username);
        loginRateLimitService.registrarSucessoIp(ip);

        String token = tokenService.gerarToken(user);

        try {
            sessaoAtivaService.criarSessaoSeDisponivel(
                user.matricula, user.nome, token, ip,
                request.computador, user.unidade,
                request.forcar
            );
        } catch (SessaoJaAtivaException e) {
            SessaoAtiva sessao = e.getSessao();
            String ipComputador = sessao.getIpAddress() != null ? sessao.getIpAddress() : "desconhecido";
            String computador = sessao.getComputador() != null ? sessao.getComputador() : ipComputador;

            String evento = String.format(
                "TENTATIVA DE ACESSO BLOQUEADA - %s (matricula %s) ja possui sessao ativa em outro computador (%s). Tentativa rejeitada.",
                user.nome, user.matricula, computador
            );
            salvarLog(evento, user.unidade);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("erro", "USUARIO_JA_LOGADO");
            body.put("mensagem", String.format(
                "A matricula %s ja esta em uso por %s em %s. Deseja encerrar a sessao anterior e entrar neste dispositivo?",
                user.matricula,
                user.nome,
                computador
            ));
            body.put("matricula", user.matricula);
            body.put("nome", user.nome);
            body.put("computador", computador);
            contarLogin("sessao_ativa");
            return Response.status(Response.Status.CONFLICT).entity(body).build();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("tipo", "Bearer");
        body.put("expiraEm", 3600);
        contarLogin("sucesso");
        body.put("usuario", montarUsuario(user));
        return Response.ok(body).build();
    }

    // Lista as unidades vinculadas ao servidor autenticado (servidor_unidade,
    // V72) - o front so oferece o seletor de troca em /usuarios-unidade quando
    // vier mais de 1. Fica em "auth" (prefixo publico no RbacRequestFilter) e
    // valida o Bearer manualmente, igual trocarUnidade() abaixo.
    @GET
    @Path("/minhas-unidades")
    public Response minhasUnidades(@Context HttpHeaders httpHeaders) {
        AuthenticatedUser atual = securityPermissionService.resolveFromHeaders(httpHeaders);
        if (atual.matricula().isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("mensagem", "Sessao invalida."))
                    .build();
        }

        List<String> unidades = servidorUnidadeRepository.listarSiglasPorServidor(Integer.parseInt(atual.idServidor()));
        if (unidades.isEmpty() && !atual.unidade().isBlank()) {
            unidades = List.of(atual.unidade());
        }
        // Banco por municipio: o superusuario tambem ve as unidades dos outros municipios (com banco proprio)
        // para trocar de municipio pela "Unidade ativa", no mesmo endereco.
        if (municipioRoteamento.ativa() && ehSuperusuario(atual)
                && municipioRoteamento.podeAcessarMunicipios(atual.matricula())) {
            java.util.LinkedHashSet<String> todas = new java.util.LinkedHashSet<>(unidades);
            Map<String, Boolean> acesso = new java.util.HashMap<>();
            for (String[] u : municipioRoteamento.unidadesDaPlataforma()) {
                // So municipios em que este superusuario ja foi ativado (cadastro dele existe naquele banco).
                if (acesso.computeIfAbsent(u[1], cod -> municipioRoteamento.temAcesso(cod, atual.matricula()))) {
                    todas.add(u[0].toUpperCase());
                }
            }
            unidades = new java.util.ArrayList<>(todas);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unidadeAtual", atual.unidade());
        body.put("unidades", unidades);
        return Response.ok(body).build();
    }

    // Troca a unidade da sessao atual sem pedir senha de novo: emite um token
    // novo com a unidade destino (o resto da identidade/permissoes continua o
    // mesmo), desde que o servidor esteja vinculado a ela em servidor_unidade
    // (ou seja a unidade principal do cadastro). Qualquer servidor com mais de
    // 1 unidade vinculada pode usar - nao e mais restrito a super usuario.
    @POST
    @Path("/trocar-unidade")
    public Response trocarUnidade(TrocarUnidadeRequest request,
                                   @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                                   @Context HttpHeaders httpHeaders,
                                   @Context RoutingContext routingContext) {
        String unidadeDestino = request == null ? "" : safe(request.unidade).toUpperCase();
        if (unidadeDestino.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("mensagem", "Unidade nao informada."))
                    .build();
        }

        AuthenticatedUser atual = securityPermissionService.resolveFromHeaders(httpHeaders);
        if (atual.matricula().isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("mensagem", "Sessao invalida."))
                    .build();
        }

        // Unidade de outro municipio (banco proprio): so o superusuario, com cadastro ja ativado la.
        String municipioDestino = municipioRoteamento.ativa() ? municipioRoteamento.municipioDaUnidade(unidadeDestino) : null;
        if (municipioDestino != null && !municipioDestino.equals(municipioRoteamento.atual())
                && (municipioRoteamento.ehPadrao(municipioDestino) || municipioRoteamento.temBancoProprio(municipioDestino))) {
            return trocarMunicipio(atual, unidadeDestino, municipioDestino, authorization, httpHeaders, routingContext);
        }

        int idservidor = Integer.parseInt(atual.idServidor());
        boolean vinculada = unidadeDestino.equalsIgnoreCase(atual.unidade())
                || servidorUnidadeRepository.pertenceAUnidade(idservidor, unidadeDestino);
        if (!vinculada) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("erro", "UNIDADE_NAO_VINCULADA");
            body.put("mensagem", "Voce nao esta vinculado a unidade " + unidadeDestino + ".");
            return Response.status(Response.Status.FORBIDDEN).entity(body).build();
        }

        Usuario user = authService.fromAuthenticatedUser(atual.comUnidade(unidadeDestino));
        String novoToken = tokenService.gerarToken(user);
        String ip = obterIPCliente(httpHeaders, routingContext);

        tokenRevocationService.revokeAuthorization(authorization);
        try {
            sessaoAtivaService.criarSessaoSeDisponivel(
                    user.matricula, user.nome, novoToken, ip, "trocaunidade", user.unidade, true);
        } catch (SessaoJaAtivaException e) {
            LOGGER.log(Level.WARNING, "[TROCA_UNIDADE] sessao anterior nao liberada matricula={0}: {1}",
                    new Object[]{user.matricula, e.getMessage()});
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", novoToken);
        body.put("tipo", "Bearer");
        body.put("expiraEm", 3600);
        body.put("usuario", montarUsuario(user));
        return Response.ok(body).build();
    }

    @Inject
    br.com.iv.plataforma.municipio.MunicipioRoteamento municipioRoteamento;

    /**
     * Login de servidor de outro municipio (banco proprio): autentica, emite o token e cria a sessao no
     * banco de la, numa thread propria (a sessao Hibernate desta requisicao ja esta no banco daqui).
     * null = a matricula nao existe ou a senha nao confere em nenhum outro municipio (segue o 401 normal).
     */
    private Response loginEmOutroMunicipio(String username, Usuario request, String ip) {
        String daqui = municipioRoteamento.atual();
        for (String cod : municipioRoteamento.municipios()) {
            if (cod.equals(daqui) || !municipioRoteamento.existeMatricula(cod, username)) {
                continue;
            }
            Object[] resultado = new Object[3]; // [0]=Usuario, [1]=token, [2]=SessaoJaAtivaException
            try {
                municipioRoteamento.executarEmNovaThread(cod, () -> {
                    Usuario u = authService.validar(username, request.senha, request.senhaMontada);
                    if (u == null) {
                        return;
                    }
                    String t = tokenService.gerarToken(u);
                    try {
                        sessaoAtivaService.criarSessaoSeDisponivel(u.matricula, u.nome, t, ip,
                                request.computador, u.unidade, request.forcar);
                    } catch (SessaoJaAtivaException e) {
                        resultado[2] = e;
                    }
                    resultado[0] = u;
                    resultado[1] = t;
                });
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "[LOGIN_MUNICIPIO] falha ao autenticar no municipio " + cod, e);
                continue;
            }
            if (resultado[0] == null) {
                continue;
            }
            Usuario u = (Usuario) resultado[0];
            loginRateLimitService.registrarSucessoMatricula(username);
            loginRateLimitService.registrarSucessoIp(ip);
            if (resultado[2] instanceof SessaoJaAtivaException e) {
                SessaoAtiva sessao = e.getSessao();
                String computador = sessao.getComputador() != null ? sessao.getComputador()
                        : (sessao.getIpAddress() != null ? sessao.getIpAddress() : "desconhecido");
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("erro", "USUARIO_JA_LOGADO");
                body.put("mensagem", String.format(
                        "A matricula %s ja esta em uso por %s em %s. Deseja encerrar a sessao anterior e entrar neste dispositivo?",
                        u.matricula, u.nome, computador));
                body.put("matricula", u.matricula);
                body.put("nome", u.nome);
                body.put("computador", computador);
                contarLogin("sessao_ativa");
                return Response.status(Response.Status.CONFLICT).entity(body).build();
            }
            LOGGER.log(Level.INFO, "[LOGIN_MUNICIPIO] matricula={0} municipio={1} unidade={2}",
                    new Object[] { u.matricula, cod, u.unidade });
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("token", resultado[1]);
            body.put("tipo", "Bearer");
            body.put("expiraEm", 3600);
            contarLogin("sucesso");
            body.put("usuario", montarUsuario(u));
            return Response.ok(body).build();
        }
        return null;
    }

    private static boolean ehSuperusuario(AuthenticatedUser u) {
        return u.hasPermission("ksuperusuario") || u.hasPermission("superusuario");
    }

    /**
     * Troca de municipio (mesmo endereco): o cadastro, as permissoes e a sessao vem do banco do municipio
     * destino - o idServidor de la, nunca o daqui. Exige superusuario nos DOIS bancos.
     */
    private Response trocarMunicipio(AuthenticatedUser atual, String unidadeDestino, String municipioDestino,
            String authorization, HttpHeaders httpHeaders, RoutingContext routingContext) {
        // Superusuario de uma prefeitura nao entra nas outras: so o responsavel ou quem ele designou.
        if (!ehSuperusuario(atual) || !municipioRoteamento.podeAcessarMunicipios(atual.matricula())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("erro", "UNIDADE_OUTRO_MUNICIPIO",
                            "mensagem", "A unidade " + unidadeDestino + " pertence a outro município."))
                    .build();
        }
        String ip = obterIPCliente(httpHeaders, routingContext);
        Usuario[] userLa = new Usuario[1];
        String[] tokenLa = new String[1];
        try {
            municipioRoteamento.executarEmNovaThread(municipioDestino, () -> {
                AuthenticatedUser la = securityPermissionService.findByMatricula(atual.matricula());
                if (la == null || la.matricula().isBlank() || !ehSuperusuario(la)) {
                    return;
                }
                Usuario u = authService.fromAuthenticatedUser(la.comUnidade(unidadeDestino));
                String t = tokenService.gerarToken(u);
                try {
                    sessaoAtivaService.criarSessaoSeDisponivel(u.matricula, u.nome, t, ip, "trocamunicipio",
                            u.unidade, true);
                } catch (SessaoJaAtivaException e) {
                    LOGGER.log(Level.WARNING, "[TROCA_MUNICIPIO] sessao anterior nao liberada matricula={0}: {1}",
                            new Object[] { u.matricula, e.getMessage() });
                }
                userLa[0] = u;
                tokenLa[0] = t;
            });
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "[TROCA_MUNICIPIO] falha ao entrar no municipio " + municipioDestino, e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("mensagem", "Não foi possível acessar o banco do município da unidade "
                            + unidadeDestino + "."))
                    .build();
        }
        if (tokenLa[0] == null) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("erro", "MUNICIPIO_NAO_ATIVADO",
                            "mensagem", "Seu acesso de superusuário ainda não existe no município da unidade "
                                    + unidadeDestino + ". Ative-o em Municípios."))
                    .build();
        }
        tokenRevocationService.revokeAuthorization(authorization);
        LOGGER.log(Level.INFO, "[TROCA_MUNICIPIO] matricula={0} municipio={1} unidade={2}",
                new Object[] { atual.matricula(), municipioDestino, unidadeDestino });
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", tokenLa[0]);
        body.put("tipo", "Bearer");
        body.put("expiraEm", 3600);
        body.put("usuario", montarUsuario(userLa[0]));
        return Response.ok(body).build();
    }

    private Map<String, Object> montarUsuario(Usuario user) {
        Map<String, Object> usuario = new LinkedHashMap<>();
        usuario.put("idServidor", user.idServidor);
        usuario.put("matricula", user.matricula);
        usuario.put("nome", user.nome);
        usuario.put("cargo", user.cargo);
        usuario.put("unidade", user.unidade);
        usuario.put("cns", user.cns);
        usuario.put("crm", user.crm);
        usuario.put("corem", user.corem);
        usuario.put("cbo", user.cbo);
        usuario.put("especialidade", user.especialidade);
        usuario.put("foto", user.foto);
        usuario.put("usuarioad", user.usuarioad);
        usuario.put("senhaInicial", user.senhaInicial);
        usuario.put("permissoes", user.permissoes);
        return usuario;
    }

    public static class TrocarUnidadeRequest {
        public String unidade;
    }

    @POST
    @Path("/logout")
    public Response logout(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        tokenRevocationService.revokeAuthorization(authorization);

        // Extrair e limpar sessao ativa do banco
        if (authorization != null && authorization.startsWith("Bearer ")) {
            sessaoAtivaService.encerrarSessaoPorToken(authorization.substring(7));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("mensagem", "Sessao encerrada");
        return Response.ok(body).build();
    }

    private boolean isSessaoOrfa(SessaoAtiva sessao) {
        try {
            long MINUTOS_ORFA = 5;
            LocalDateTime agora = LocalDateTime.now(FUSO_HORARIO);
            boolean nula = sessao.getUltimaAtividade() == null;
            boolean orfa = nula || sessao.getUltimaAtividade().plusMinutes(MINUTOS_ORFA).isBefore(agora);
            LOGGER.log(Level.INFO, "[LOGIN] isSessaoOrfa: matricula={0} ultima_atividade={1} agora={2} orfa={3}",
                new Object[]{sessao.getMatricula(), sessao.getUltimaAtividade(), agora, orfa});
            return orfa;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[LOGIN] isSessaoOrfa erro: {0}", e.getMessage());
            return true;
        }
    }

    // Sem X-Forwarded-For/X-Real-IP (proxy ausente ou mal configurado), cair
    // sempre no literal "desconhecido" juntava QUALQUER cliente direto num so
    // balde do LoginRateLimitService por IP - um pico de logins legitimos de
    // maquinas diferentes acabava lido como forca bruta de uma unica origem e
    // bloqueava todo mundo. O fallback via RoutingContext (mesmo padrao de
    // UsuarioLogadoResource.obterIPCliente) separa por endereco real da conexao.
    private String obterIPCliente(HttpHeaders httpHeaders, RoutingContext routingContext) {
        if (httpHeaders != null) {
            String xff = httpHeaders.getHeaderString("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            String realIp = httpHeaders.getHeaderString("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        if (routingContext != null && routingContext.request() != null
                && routingContext.request().remoteAddress() != null) {
            String remoto = routingContext.request().remoteAddress().host();
            if (remoto != null && !remoto.isBlank()) {
                return remoto;
            }
        }
        return "desconhecido";
    }

    private void salvarLog(String evento, String unidade) {
        try {
            LogDto dto = new LogDto();
            dto.setData(LocalDate.now(FUSO_HORARIO));
            dto.setHora(String.format("%02d:%02d",
                LocalDateTime.now(FUSO_HORARIO).getHour(),
                LocalDateTime.now(FUSO_HORARIO).getMinute()));
            dto.setEvento(evento);
            dto.setUnidade(unidade != null ? unidade : "GERAL");
            logService.salvarLogAsync(dto);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[GerarToken] Erro ao salvar log: {0}", e.getMessage());
        }
    }

    private boolean isAdminPassword(String senha, String senhaMontada) {
        if (!isAdminRecoveryConfigured()) {
            return false;
        }
        String esperado = AppSecrets.ADMIN_RECOVERY_PASSWORD;
        if (esperado.equals(safe(senha))) {
            return true;
        }
        if (senhaMontada != null && senhaMontada.length() >= 12) {
            String decoded = extractFromKeypad(esperado, senhaMontada);
            return esperado.equals(decoded);
        }
        return false;
    }

    private boolean isAdminRecoveryConfigured() {
        return !AppSecrets.ADMIN_RECOVERY_MATRICULA.isBlank()
                && !AppSecrets.ADMIN_RECOVERY_PASSWORD.isBlank();
    }

    private String extractFromKeypad(String expected, String keypad) {
        String kp = safe(keypad);
        if (kp.length() < 12) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int idx = i * 2;
            String exp = expected.substring(i, i + 1);
            String first = kp.substring(idx, idx + 1);
            String second = kp.substring(idx + 1, idx + 2);
            result.append(exp.equals(first) ? first : second);
        }
        return result.toString();
    }

    private void criarAdminSeNaoExistir() {
        if (!isAdminRecoveryConfigured()) {
            LOGGER.warning("[GerarToken] Recuperacao administrativa desabilitada: credenciais ausentes.");
            return;
        }
        LOGGER.log(Level.INFO, "[GerarToken] Verificando se admin de recuperacao precisa ser criado...");
        ServidorDto dto = new ServidorDto();
        dto.setMatricula(AppSecrets.ADMIN_RECOVERY_MATRICULA);
        dto.setNome("ADMINISTRADOR GERAL");
        dto.setPwd(Base64.getEncoder().encodeToString(AppSecrets.ADMIN_RECOVERY_PASSWORD.getBytes()));
        dto.setUnidade("GERAL");
        dto.setUsuarioad("admin");
        dto.setCargo("SERVIDOR");
        dto.setAtivo(true);
        dto.setAdministracao(true);
        dto.setSubadm(true);
        dto.setRecepcao(false);
        dto.setTriagem(false);
        dto.setMedico(false);
        dto.setDanfe(false);
        dto.setRx(false);
        dto.setLaboratorio(false);
        dto.setFarmacia(false);
        dto.setFaturamento(false);
        dto.setRemedios(false);
        dto.setFarmhmsfx(false);
        dto.setFarmcentral(false);
        dto.setMedicacao(false);
        dto.setEnfermagem(false);
        dto.setTcenfermagem(false);
        dto.setAssistencia(false);
        dto.setFisioterapia(false);
        dto.setNutricao(false);
        dto.setFonoaudiologia(false);
        dto.setPsicologia(false);
        dto.setCriaragenda(false);
        dto.setAgendarpaciente(false);
        dto.setConsultorioadmin(false);
        dto.setConsultoriorecepcao(false);
        dto.setConsultoriomedico(false);
        dto.setConsultoriofinanceiro(false);
        dto.setTomografia(false);
        try {
            servidorService.salvarServidor(dto);
            LOGGER.log(Level.INFO, "[GerarToken] Admin criado/atualizado com sucesso, matricula={0}", AppSecrets.ADMIN_RECOVERY_MATRICULA);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[GerarToken] ERRO ao criar admin: {0}", e.getMessage());
            e.printStackTrace();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}



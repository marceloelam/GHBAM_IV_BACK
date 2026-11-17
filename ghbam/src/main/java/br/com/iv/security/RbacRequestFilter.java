package br.com.iv.security;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class RbacRequestFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(RbacRequestFilter.class.getName());

    private static final Set<String> AUTHENTICATED_ONLY_PATHS = Set.of(
            "api/usuarios-conectados/heartbeat",
            // Favoritos do menu do proprio usuario (matricula vem do token); nao concede acesso a telas.
            "api/menu-favoritos",
            // Executado logo apÃƒÆ’Ã‚Â³s a emissÃƒÆ’Ã‚Â£o do token para confirmar a situaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o
            // da prÃƒÆ’Ã‚Â³pria conta no AD. NÃƒÆ’Ã‚Â£o lista nem altera usuÃƒÆ’Ã‚Â¡rios.
            "ad/verificar-status",
            // Gera o QR de pareamento do proprio celular do usuario logado
            // (recuperacao de senha via push); nao exige permissao especial.
            "push/pareamento/gerar",
            // Listar/remover os proprios dispositivos pareados (GET e DELETE);
            // a posse e' validada no service pela matricula do token, nao por
            // uma permissao especifica.
            // As rotas publicas do pareamento retornam antes deste controle;
            // as demais operacoes do recurso exigem sessao autenticada.
            "push/pareamento",
            "push/pareamento/dispositivos",
            // Espelho de ponto do proprio servidor; o controller usa a matricula do token.
            "rh/ponto/meu");

    // Criar uma ocorrencia de sistema (POST na raiz) e' liberado a qualquer
    // usuario autenticado: e' o mesmo endpoint usado tanto por um registro
    // manual quanto pelo monitor automatico de performance do frontend, que
    // dispara para qualquer papel clinico/tecnico. Sem isso, o POST caia em
    // 403 pra quem nao e' kadm/kfaturamento e o registro ficava preso so no
    // localStorage do navegador, invisivel pra quem gerencia ocorrencias.
    // Listar/editar/excluir (GET/PUT/DELETE) continuam exigindo kadm/kfaturamento
    // pelo catalogo normal.
    private static final Set<String> AUTHENTICATED_ONLY_POST_PATHS = Set.of(
            "ocorrencia-sistema",
            // Os modulos enviam eventos de auditoria para /log; a leitura e
            // restrita pelo catalogo RBAC a kadm/plogs.
            "log",
            // Reconfirma a senha do proprio usuario antes de operacoes sensiveis;
            // a matricula e a senha continuam sendo validadas no controller.
            "servidor/validar-credencial");

    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
            "tema-visual",
            // Nome do sistema exibido na tela de login (antes da sessao).
            "sistema/identidade",
            // CatÃƒÆ’Ã‚Â¡logos necessÃƒÆ’Ã‚Â¡rios para compor a tela de entrada e os
            // seletores de atendimento. SÃƒÆ’Ã‚Â£o exclusivamente leituras de
            // referÃƒÆ’Ã‚Âªncia; criaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o/ediÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o continua passando pelo RBAC.
            "setores/unidade",
            "setores/unidadetriar",
            "sala/unidade",
            "procedimentos/modo",
            "procedimentos/modo2",
            "servidor/medico",
            "servidor/medicos",
            "servidor/especialistas",
            "configuracaounidade");

    private static final Set<String> AUTHENTICATED_ONLY_GET_PATHS = Set.of(
            // Videos de ajuda das telas: material de treinamento, sem dado de paciente.
            "videos-ajuda",
            // Setores com coordenador cuja equipe nao inclui o usuario (fecha o menu do setor).
            "coordenacao-setor/meus-setores",
            // Modulos contratados da unidade: o menu de todos le para esconder o que nao foi contratado.
            "unidade-modulos",
            "sala/unidade",
            "setores/unidade",
            "setores/unidadetriar",
            "procedimentos/modo",
            "procedimentos/modo2",
            "servidor/medico",
            "servidor/medicos",
            "servidor/especialistas",
            "servidor/nome",
            "servidor/unidade",
            "servidor/idservidor",
            "servidor/certificado",
            "servidor2/medico",
            "servidor2/matricula",
            "servidor2/nome",
            "servidor2/unidade",
            "servidor2/idservidor2",
            "configuracaounidade",
            "localcenso/sigla",
            "localcenso/hospitaldistintos",
            "localcenso/localunidade",
            "localcenso/localunidadeobservacao",
            "localcenso/unidadenir",
            "setorcenso/sigla",
            "setorcenso/sigla2",
            "setorcenso/siglaobs",
            "setorcenso/hospitaldistintos",
            "setorcenso/hospitaldistintosbylocal",
            "setorcenso/unidadenir",
            "setorcenso/titulo");

    private static final Set<String> PUBLIC_EXACT_PATHS = Set.of(
            "ad/verificar");

    private static final Set<String> PUBLIC_PATH_PREFIXES = Set.of(
            "auth",
            "seguranca/contexto",
            "q",
            "health",
            "metrics",
            "swagger",
            "openapi",
            "img",
            "favicon",
            "api/version/example",
            "pontos",
            "hello",
            "escalapaciente/checkin",
            // Paineis de video por unidade ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â telas/TVs de sala de espera sem
            // login (kiosk), confirmado com o cliente em 2026-07-20.
            "video",
            "videoproxy",
            // Config/playlist do mesmo painel de video kiosk (sem login) ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â sem esses
            // dois, o enforce=true quebra o proprio painel que ja foi aprovado publico.
            "unidadevideoconfig",
            "unidadevideos",
            // Telemetria de diagnostico do frontend (POST em lote); dispara sem
            // usuario logado. Escopo estrito ao /lote, demais rotas de diagnostico
            // seguem sob RBAC.
            "diagnostico-sistema/lote",
            // Log de auditoria de acoes do usuario ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â o frontend envia via
            // LEGACY_AUTH_ONLY sem Bearer, entao o resolve() retorna empty.
            // Endpoint apenas registra eventos, nao expoe dados sensiveis.
            // Fluxo de pareamento de celular / recuperacao de senha via push,
            // acessado direto do navegador do celular sem sessao GHBAM (o
            // token opaco de curta duracao e' quem protege o endpoint).
            "push/pareamento/token",
            "push/pareamento/confirmar",
            "push/pareamento/vapid-public-key",
            // Consulta de paciente por sistema externo: sem sessao GHBAM, protegida
            // pela chave X-API-Key validada no proprio IntegracaoPacienteController.
            "integracao/pacientes",
            // Portal publico de exames do paciente (CPF + nascimento no corpo do POST),
            // protegido por PortalPacienteRateLimitService e token curto no download.
            "portal-paciente");

    @ConfigProperty(name = "ghbam.security.rbac.enforce", defaultValue = "true")
    boolean enforce;

    // Flag proprio (nao o "enforce" geral acima) para a nova checagem de
    // permissao OBRIGATORIA (E) do RoutePermissionCatalog.requiredAllPermissionsFor.
    // Comeca em false de proposito: mesmo depois de popular entradas nesse mapa,
    // nada bloqueia ninguem em producao ate esse flag ser ligado - so entao,
    // depois de rodar em shadow (RBAC-SHADOW-REQUIRED nos logs) e confirmar que
    // nao ha usuario legitimo sendo pego de surpresa. Mesmo raciocinio que ja
    // guiou o rollout do "enforce" geral (ver memoria do projeto).
    @ConfigProperty(name = "ghbam.security.rbac.enforceRequiredPermissions", defaultValue = "false")
    boolean enforceRequiredPermissions;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Inject
    RoutePermissionCatalog routePermissionCatalog;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = normalize(requestContext.getUriInfo().getPath());
        if (isPublic(path)
                || isPublicReadOnly(path, requestContext.getMethod())
                || isPublicKioskRequest(path, requestContext.getMethod())
                || isPublicBiometricLogin(path, requestContext.getMethod())
                || "OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }
        // Endpoint de troca da propria senha (servidor/{matricula}/senha, senha no corpo):
        // tem regra propria (dono da matricula OU kadm), nao o generico "servidor"
        // do catalogo (que exige kadm/plistausuarios). Sem isso, um usuario comum
        // nunca consegue trocar a propria senha inicial via RBAC ligado.
        // Tratado ANTES do bloqueio de sessao encerrada: o usuario e obrigado a
        // trocar a senha inicial, e um Bearer valido (nao revogado/nao expirado)
        // de uma sessao encerrada por novo login em outra aba/dispositivo nao deve
        // travar esse fluxo obrigatorio com 401 SESSAO_ENCERRADA.
        String matriculaTrocaSenha = matriculaDoPathTrocaSenha(path);
        if (matriculaTrocaSenha != null) {
            AuthenticatedUser user = securityPermissionService.resolveForPasswordChange(requestContext);
            requestContext.setProperty("ghbam.user", user);
            boolean donoOuAdmin = user.matricula().equalsIgnoreCase(matriculaTrocaSenha) || user.hasPermission("kadm");
            requestContext.setProperty("ghbam.rbac.decision", donoOuAdmin ? "allowed-self-senha" : "denied");
            LOGGER.warning("RBAC-SENHA decision=" + (donoOuAdmin ? "allowed" : "denied")
                    + " method=" + requestContext.getMethod()
                    + " path=" + path
                    + " auth=" + (requestContext.getHeaderString(HttpHeaders.AUTHORIZATION) != null)
                    + " userMatricula=" + user.matricula()
                    + " pathMatricula=" + matriculaTrocaSenha
                    + " admin=" + user.hasPermission("kadm"));
            if (!donoOuAdmin) {
                requestContext.abortWith(error(Response.Status.FORBIDDEN, "Permissao insuficiente"));
            }
            return;
        }

        if (!isPostLoginAdStatusCheck(path, requestContext.getMethod())
                && securityPermissionService.isAuthorizationRevokedOrInactive(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))) {
            // Aborta ANTES do catalogo/resolve(), entao nao passava pelo shadow log ate
            // agora ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â ficava invisivel nos logs mesmo bloqueando toda chamada da aba com
            // Bearer antigo apos um novo login (forcar=true) para a mesma matricula.
            logShadow(requestContext, "sessao-encerrada", path, null);
            requestContext.abortWith(error(Response.Status.UNAUTHORIZED, "SESSAO_ENCERRADA", "Sua sessao foi encerrada por um novo login."));
            return;
        }

        if (isPostLoginAdStatusCheck(path, requestContext.getMethod())) {
            AuthenticatedUser user = securityPermissionService.resolveForPasswordChange(requestContext);
            requestContext.setProperty("ghbam.user", user);
            requestContext.setProperty("ghbam.rbac.required", List.of("authenticated"));
            if (user.matricula().isBlank()) {
                requestContext.setProperty("ghbam.rbac.decision", "missing-user");
                logShadow(requestContext, "missing-user", path, "authenticated");
                requestContext.abortWith(error(Response.Status.UNAUTHORIZED, "Usuario nao autenticado"));
                return;
            }
            requestContext.setProperty("ghbam.rbac.decision", "allowed-post-login-ad-status");
            return;
        }

        boolean authenticatedOnly = requiresOnlyAuthenticatedUser(path, requestContext.getMethod());
        Optional<List<String>> required = routePermissionCatalog.requiredPermissionsFor(path);
        if (required.isEmpty() && !authenticatedOnly) {
            requestContext.setProperty("ghbam.rbac.mode", "unmapped");
            requestContext.setProperty("ghbam.rbac.decision", "unmapped");
            logShadow(requestContext, "unmapped", path, null);
            if (enforce) {
                requestContext.abortWith(error(Response.Status.FORBIDDEN, "Rota sem mapeamento RBAC"));
                return;
            }
            authenticatedOnly = true;
        }
        List<String> requiredPermissions = authenticatedOnly
                ? List.of("authenticated")
                : permissionsForRequest(path, requestContext.getMethod(), required.get());

        AuthenticatedUser user = securityPermissionService.resolve(requestContext);
        requestContext.setProperty("ghbam.user", user);
        requestContext.setProperty("ghbam.rbac.required", requiredPermissions);

        if (user.matricula().isBlank()) {
            requestContext.setProperty("ghbam.rbac.decision", "missing-user");
            logShadow(requestContext, "missing-user", path, String.join(",", requiredPermissions));
            requestContext.abortWith(error(Response.Status.UNAUTHORIZED, "Usuario nao autenticado"));
            return;
        }

        // Bloqueio incondicional (independe de "enforce"): enquanto a senha ainda
        // for a padrao de fabrica (123456), o usuario so pode chamar o endpoint
        // de troca de senha (tratado acima) ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â nada mais no sistema. Antes disso,
        // "senha inicial" era so uma sugestao de redirecionamento no frontend; o
        // token/sessao emitidos no login ja davam acesso completo a qualquer API.
        if (user.senhaInicial()) {
            requestContext.setProperty("ghbam.rbac.decision", "senha-inicial-pendente");
            requestContext.abortWith(error(Response.Status.FORBIDDEN, "SENHA_INICIAL_PENDENTE",
                    "E necessario alterar a senha inicial antes de continuar."));
            return;
        }

        if (user.hasPermission("ksuperusuario") || user.hasPermission("superusuario")) {
            requestContext.setProperty("ghbam.rbac.decision", "allowed-superusuario");
            return;
        }

        if (authenticatedOnly) {
            requestContext.setProperty("ghbam.rbac.decision", "allowed-authenticated");
            return;
        }

        if (!user.hasAnyPermission(requiredPermissions)) {
            requestContext.setProperty("ghbam.rbac.decision", "denied");
            logShadow(requestContext, "denied", path, String.join(",", requiredPermissions));
            if (enforce) {
                requestContext.abortWith(error(Response.Status.FORBIDDEN, "Permissao insuficiente"));
                return;
            }
            requestContext.setProperty("ghbam.rbac.decision", "allowed-legacy-permissions");
            return;
        }

        // kadm tem bypass proprio aqui (decisao explicita, 2026-09-17): ao contrario
        // do restante do filtro, onde kadm e' so mais um item nas listas OU por rota,
        // a exigencia E foi pensada para restringir usuarios comuns por acao fina -
        // travar um administrador que nao marcou uma flag especifica (ex.: pcadsalas)
        // do proprio cadastro dele seria surpresa demais. ksuperusuario/superusuario
        // ja bypassam tudo antes disso (linha ~259).
        List<String> requiredAllPermissions = routePermissionCatalog.requiredAllPermissionsFor(path);
        if (!requiredAllPermissions.isEmpty() && !user.hasPermission("kadm") && !user.hasAllPermissions(requiredAllPermissions)) {
            requestContext.setProperty("ghbam.rbac.decision", "denied-missing-required-permission");
            logShadowRequired(requestContext, path, requiredAllPermissions);
            if (enforceRequiredPermissions) {
                requestContext.abortWith(error(Response.Status.FORBIDDEN, "Permissao insuficiente"));
                return;
            }
            requestContext.setProperty("ghbam.rbac.decision", "allowed-required-permission-shadow");
            return;
        }

        requestContext.setProperty("ghbam.rbac.decision", "allowed");
    }

    static List<String> permissionsForRequest(String path, String method, List<String> catalogPermissions) {
        String normalizedPath = normalizePath(path);
        String[] pathParts = normalizedPath.split("/");
        String clinicalPath = normalizedPath.startsWith("api/")
                ? normalizedPath.substring("api/".length())
                : normalizedPath;
        String[] clinicalPathParts = clinicalPath.split("/");
        boolean leituraCatalogoMedicamentos = "GET".equalsIgnoreCase(method)
                && "pharmacia/produtos".equals(normalizedPath);
        boolean leituraPrescricaoEstruturada = "GET".equalsIgnoreCase(method)
                && ("prescricao-itens".equals(normalizedPath)
                        || normalizedPath.startsWith("prescricao-itens/"));
        boolean leituraHistoricoEnfermagem = "GET".equalsIgnoreCase(method)
                && (normalizedPath.startsWith("evolucaopacienteenf/bam/")
                        || normalizedPath.startsWith("anotacaoenfermagem/bam/"));
        // Decisao Marcelo 17/11/2026 (negados reais na producao): enfermagem LE a prescricao do BAM
        // e o medico LE o atendimento multidisciplinar. So GET; gravar continua com as listas do catalogo.
        boolean leituraPrescricaoPelaEnfermagem = "GET".equalsIgnoreCase(method)
                && normalizedPath.startsWith("prescricao/bam2/");
        boolean leituraMultidisciplinarPeloMedico = "GET".equalsIgnoreCase(method)
                && (normalizedPath.startsWith("atendimentomultidisciplinar/bam/")
                        || normalizedPath.startsWith("atendimentomultidisciplinar/paciente/"));
        boolean criacaoInicialRecepcao = "POST".equalsIgnoreCase(method)
                && ("avaliacao".equals(normalizedPath) || "prontuario".equals(normalizedPath));
        boolean recuperacaoIdCriadoPelaRecepcao = "GET".equalsIgnoreCase(method)
                && (normalizedPath.startsWith("avaliacao/controle/")
                        || normalizedPath.startsWith("prontuario/cod/"));
        boolean consultaPacienteParaImprimirBam = "GET".equalsIgnoreCase(method)
                && pathParts.length == 2
                && "pacientes".equals(pathParts[0])
                && !pathParts[1].isBlank();
        // Lista de BAMs do paciente na reimpressão (GET bam/idpaciente/{id}): leitura para quem imprime BAM.
        boolean consultaBamDoPacienteParaImprimir = "GET".equalsIgnoreCase(method)
                && pathParts.length == 3
                && "bam".equals(pathParts[0])
                && "idpaciente".equals(pathParts[1]);
        boolean consultaPacientePorNome = "GET".equalsIgnoreCase(method)
                && pathParts.length == 2
                && "pacientes".equals(pathParts[0])
                && !pathParts[1].isBlank();
        boolean atualizacaoAutomaticaIdadePaciente = "PATCH".equalsIgnoreCase(method)
                && pathParts.length == 4
                && "pacientes".equals(pathParts[0])
                && !pathParts[1].isBlank()
                && "idade".equals(pathParts[2])
                && "atualizacao".equals(pathParts[3]);
        boolean leituraFilaAguardando = "GET".equalsIgnoreCase(method)
                && pathParts.length == 4
                && "pacientes".equals(pathParts[0])
                && "aguardando".equals(pathParts[1])
                && "unidade".equals(pathParts[2])
                && !pathParts[3].isBlank();
        boolean leituraPacientesEmAtendimento = "GET".equalsIgnoreCase(method)
                && pathParts.length == 5
                && "pacientes".equals(pathParts[0])
                && "ematendimento".equals(pathParts[1])
                && !pathParts[2].isBlank()
                && "unidade".equals(pathParts[3])
                && !pathParts[4].isBlank();
        boolean leituraSetoresDaUnidadePelaRecepcao = "GET".equalsIgnoreCase(method)
                && pathParts.length == 3
                && "setores".equals(pathParts[0])
                && "unidade".equals(pathParts[1])
                && !pathParts[2].isBlank();
        boolean leituraCatalogoParaFaturamentoAssistencial = "GET".equalsIgnoreCase(method)
                && (normalizedPath.startsWith("procedimentos/procedimento/")
                        || normalizedPath.startsWith("procedimentospasrv/codigo/")
                        || normalizedPath.startsWith("procedimentossus/procedimentosus/")
                        || normalizedPath.startsWith("procedimentossus/procedi/"));
        boolean gravacaoFaturamentoAssistencial = "POST".equalsIgnoreCase(method)
                && "afaturar".equals(normalizedPath);
        boolean leituraFilaMedicacao = "GET".equalsIgnoreCase(method)
                && normalizedPath.startsWith("pedidomedico2/psunidadepedido/")
                && (normalizedPath.endsWith("/local/medicacao")
                        || normalizedPath.endsWith("/local/medicacao2")
                        || normalizedPath.endsWith("/local/medicacao3"));
        boolean leituraPedidosMedicacao = "GET".equalsIgnoreCase(method)
                && normalizedPath.startsWith("pedidomedico2/examesbamps/")
                && (normalizedPath.endsWith("/local/medicacao")
                        || normalizedPath.endsWith("/local/medicacao2")
                        || normalizedPath.endsWith("/local/medicacao3"));
        boolean conclusaoPedidoMedicacao = "PATCH".equalsIgnoreCase(method)
                && normalizedPath.startsWith("pedidomedico2/")
                && normalizedPath.contains("/resposta-exames/resposta/")
                && (normalizedPath.endsWith("/local/medicacao")
                        || normalizedPath.endsWith("/local/medicacao2")
                        || normalizedPath.endsWith("/local/medicacao3"));
        boolean leituraDriveDoMenu = "GET".equalsIgnoreCase(method)
                && pathParts.length == 5
                && "drive".equals(pathParts[0])
                && "menu".equals(pathParts[1])
                && "unidade".equals(pathParts[3])
                && !pathParts[4].isBlank();
        List<String> permissoesDoDrive = leituraDriveDoMenu
                ? switch (pathParts[2].toLowerCase(java.util.Locale.ROOT)) {
                    case "administrativo" -> List.of("kadm");
                    case "recepcao" -> List.of("krecepcao");
                    case "enfermagem" -> List.of("kenfermagem", "ktcenfermagem");
                    case "medico" -> List.of("kmedico");
                    case "multidisciplinar" -> List.of("kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia");
                    case "nir" -> List.of("kfaturamento");
                    case "laboratorio" -> List.of("klaboratorio");
                    case "imagem" -> List.of("krx", "ktomografia", "kultrassonografia");
                    case "spa" -> List.of("kmedicacao");
                    case "faturamento" -> List.of("kremedios");
                    case "farmacia" -> List.of("kfarmaciacentral", "kfarmhmsfx");
                    case "recursos-humanos" -> List.of("krh", "pmonitoramento", "pcontroleplantoes", "prhponto", "pprodutividaderh", "ptreinamentocertificacao");
                    default -> List.of();
                }
                : List.of();
        boolean leituraAssinaturaPorBam = "GET".equalsIgnoreCase(method)
                && clinicalPathParts.length == 5
                && "assinaturas-clinicas".equals(clinicalPathParts[0])
                && "tipo".equals(clinicalPathParts[1])
                && !clinicalPathParts[2].isBlank()
                && "bam".equals(clinicalPathParts[3])
                && !clinicalPathParts[4].isBlank();
        boolean leituraRegistroPorId = "GET".equalsIgnoreCase(method)
                && pathParts.length == 3
                && "registro".equals(pathParts[0])
                && "id".equals(pathParts[1])
                && !pathParts[2].isBlank();
        boolean leituraHistoricoProntuarioCompleto = isLeituraHistoricoProntuarioCompleto(normalizedPath, pathParts, method);
        boolean relatorioHistoricoProntuarioCompleto = isRelatorioHistoricoProntuarioCompleto(normalizedPath, method);

        java.util.stream.Stream<String> adicionais = java.util.stream.Stream.empty();
        if (criacaoInicialRecepcao || recuperacaoIdCriadoPelaRecepcao) {
            adicionais = java.util.stream.Stream.concat(adicionais, java.util.stream.Stream.of("krecepcao"));
        }
        if (leituraPrescricaoPelaEnfermagem) {
            adicionais = java.util.stream.Stream.concat(adicionais, java.util.stream.Stream.of("kenfermagem", "ktcenfermagem"));
        }
        if (leituraMultidisciplinarPeloMedico) {
            adicionais = java.util.stream.Stream.concat(adicionais, java.util.stream.Stream.of("kmedico"));
        }
        if (leituraCatalogoMedicamentos || leituraPrescricaoEstruturada || leituraHistoricoEnfermagem) {
            adicionais = java.util.stream.Stream.concat(adicionais, java.util.stream.Stream.of(
                    "kmedico",
                    "kenfermagem",
                    "ktriagem",
                    "kfisioterapia",
                    "kassistencia",
                    "knutricao",
                    "kfonoaudiologia",
                    "kpsicologia",
                    "pevolucaomultidisciplinar",
                    "presumopaciente",
                    "pcomoestaopaciente",
                    "pprontuariocompleto"));
        }
        // Técnico de enfermagem reimprime BAM (pedido de 2026-11-01): só leitura, nunca alteração.
        if (consultaPacienteParaImprimirBam || consultaBamDoPacienteParaImprimir) {
            adicionais = java.util.stream.Stream.concat(adicionais, java.util.stream.Stream.of("pimprimirbam", "ktcenfermagem"));
        }
        if (consultaPacientePorNome || atualizacaoAutomaticaIdadePaciente) {
            adicionais = java.util.stream.Stream.concat(adicionais, java.util.stream.Stream.of(
                    "presumopaciente",
                    "pcomoestaopaciente",
                    "pprontuariocompleto",
                    "penfermariaseleitos",
                    "pnir",
                    "pccih"));
        }
        if (leituraFilaAguardando) {
            adicionais = java.util.stream.Stream.concat(adicionais,
                    java.util.stream.Stream.of("paguardandoatendimento"));
        }
        if (leituraPacientesEmAtendimento) {
            adicionais = java.util.stream.Stream.concat(adicionais,
                    java.util.stream.Stream.of("ppacientesematendimento"));
        }
        if (leituraSetoresDaUnidadePelaRecepcao) {
            adicionais = java.util.stream.Stream.concat(adicionais,
                    java.util.stream.Stream.of("krecepcao"));
        }
        if (leituraCatalogoParaFaturamentoAssistencial || gravacaoFaturamentoAssistencial) {
            adicionais = java.util.stream.Stream.concat(adicionais, java.util.stream.Stream.of(
                    "krecepcao",
                    "ktriagem",
                    "kmedico",
                    "kenfermagem",
                    "klaboratorio",
                    "krx",
                    "ktomografia",
                    "kmedicacao",
                    "kconsultorio_medico"));
        }
        if (leituraFilaMedicacao || leituraPedidosMedicacao || conclusaoPedidoMedicacao) {
            adicionais = java.util.stream.Stream.concat(adicionais,
                    java.util.stream.Stream.of("kmedicacao"));
        }
        if (leituraAssinaturaPorBam) {
            adicionais = java.util.stream.Stream.concat(adicionais, java.util.stream.Stream.of(
                    "krecepcao",
                    "ktriagem",
                    "kmedico",
                    "kenfermagem",
                    "presumopaciente",
                    "pcomoestaopaciente",
                    "pprontuariocompleto",
                    "penfermariaseleitos",
                    "pnir",
                    "pccih",
                    "klaboratorio",
                    "krx",
                    "ktomografia",
                    "kmedicacao"));
        }
        if (leituraRegistroPorId || leituraHistoricoProntuarioCompleto || relatorioHistoricoProntuarioCompleto) {
            adicionais = java.util.stream.Stream.concat(adicionais, java.util.stream.Stream.of(
                    "krecepcao",
                    "ktriagem",
                    "kmedico",
                    "kenfermagem",
                    "presumopaciente",
                    "pcomoestaopaciente",
                    "pprontuariocompleto",
                    "penfermariaseleitos",
                    "pnir",
                    "pccih"));
        }
        if (!permissoesDoDrive.isEmpty()) {
            adicionais = java.util.stream.Stream.concat(adicionais, permissoesDoDrive.stream());
        }
        return java.util.stream.Stream.concat(catalogPermissions.stream(), adicionais)
                .distinct()
                .toList();
    }

    private static boolean isLeituraHistoricoProntuarioCompleto(String normalizedPath, String[] pathParts, String method) {
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        return (pathParts.length == 5
                && "novointernado".equals(pathParts[0])
                && "bam".equals(pathParts[1])
                && !pathParts[2].isBlank()
                && "unidade".equals(pathParts[3])
                && !pathParts[4].isBlank())
                || (pathParts.length == 3
                        && "prescricao".equals(pathParts[0])
                        && "bamultimo".equals(pathParts[1])
                        && !pathParts[2].isBlank())
                // Leitura das prescricoes do BAM (com ou sem filtro de tipo): usada pela
                // tela Enfermarias e Leitos, que ja e' liberada pra enfermagem via
                // penfermariaseleitos. Sem isto, kenfermagem/ktcenfermagem levava 403
                // ao tentar ver a prescricao pra administrar medicacao. Criar/inativar/
                // aprazar prescricao continuam exclusivos de kmedico/kadm pelo catalogo.
                || ((pathParts.length == 7 || pathParts.length == 9)
                        && "prescricao".equals(pathParts[0])
                        && "bam".equals(pathParts[1])
                        && !pathParts[2].isBlank()
                        && "inicio".equals(pathParts[3])
                        && "fim".equals(pathParts[5]))
                || (pathParts.length == 3
                        && "consultapediatrica".equals(pathParts[0])
                        && "paciente".equals(pathParts[1])
                        && !pathParts[2].isBlank())
                || (pathParts.length == 3
                        && "atendimentomultidisciplinar".equals(pathParts[0])
                        && "paciente".equals(pathParts[1])
                        && !pathParts[2].isBlank())
                || (pathParts.length == 4
                        && "cdss".equals(pathParts[0])
                        && "preventivo".equals(pathParts[1])
                        && "paciente".equals(pathParts[2])
                        && !pathParts[3].isBlank());
    }

    private static boolean isRelatorioHistoricoProntuarioCompleto(String normalizedPath, String method) {
        return "POST".equalsIgnoreCase(method)
                && ("report/historicopacienteatendido".equals(normalizedPath)
                        || "report/faturadopacientedetalhado".equals(normalizedPath));
    }

    static boolean requiresOnlyAuthenticatedUser(String path, String method) {
        String normalizedPath = normalizePath(path);
        boolean exactAuthenticatedPath = AUTHENTICATED_ONLY_PATHS.stream()
                .anyMatch(prefix -> normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/"));
        if (exactAuthenticatedPath) {
            return true;
        }

        if ("GET".equalsIgnoreCase(method)
                && AUTHENTICATED_ONLY_GET_PATHS.stream()
                        .anyMatch(prefix -> normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/"))) {
            return true;
        }

        return "POST".equalsIgnoreCase(method)
                && AUTHENTICATED_ONLY_POST_PATHS.stream()
                        .anyMatch(prefix -> normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/"));
    }

    private boolean isPostLoginAdStatusCheck(String path, String method) {
        return "POST".equalsIgnoreCase(method) && "ad/verificar-status".equals(normalizePath(path));
    }

    static boolean isPublicBiometricLogin(String path, String method) {
        return "POST".equalsIgnoreCase(method)
                && "api/fingerprint/verify".equals(normalizePath(path));
    }

    static boolean isPublicReadOnly(String path, String method) {
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        String normalizedPath = normalizePath(path);
        return PUBLIC_GET_PATHS.stream()
                .anyMatch(prefix -> normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/"));
    }

    static boolean isPublicKioskRequest(String path, String method) {
        String[] parts = normalizePath(path).split("/");

        if ("GET".equalsIgnoreCase(method)) {
            boolean chamadaPainel = parts.length == 5
                    && "chamada".equals(parts[0])
                    && !parts[2].isBlank()
                    && (("unidade1".equals(parts[1]) && "unico".equals(parts[3]))
                            || ("unidade2".equals(parts[1]) && "direita".equals(parts[3]))
                            || ("unidade3".equals(parts[1]) && "tv".equals(parts[3])))
                    && !parts[4].isBlank();
            boolean siglaUnidade = parts.length == 3
                    && "localcenso".equals(parts[0])
                    && "sigla".equals(parts[1])
                    && !parts[2].isBlank();
            // Voz do painel: status por sigla e áudio de uma chamada recente da
            // unidade/TV (o serviço recusa qualquer outra; não aceita texto livre).
            boolean vozStatus = parts.length == 3
                    && "painelvoz".equals(parts[0])
                    && "status".equals(parts[1])
                    && !parts[2].isBlank();
            boolean vozChamada = parts.length == 5
                    && "painelvoz".equals(parts[0])
                    && "chamada".equals(parts[1])
                    && !parts[2].isBlank()
                    && parts[3].matches("\\d{1,2}")
                    && parts[4].matches("\\d+");
            return chamadaPainel || siglaUnidade || vozStatus || vozChamada;
        }

        return "PATCH".equalsIgnoreCase(method)
                && parts.length == 4
                && "chamada".equals(parts[0])
                && parts[1].matches("\\d+")
                && "tv".equals(parts[2])
                && !parts[3].isBlank();
    }

    // servidor/{matricula}/senha ou servidor/{matricula}/senha/{pwd} (com ou sem prefixo api)
    // -> matricula; null se o path nao for esse endpoint.
    private String matriculaDoPathTrocaSenha(String path) {
        String[] partes = path.split("/");
        int offset = partes.length > 0 && "api".equals(partes[0]) ? 1 : 0;
        if (partes.length >= offset + 3
                && "servidor".equals(partes[offset])
                && "senha".equals(partes[offset + 2])
                && !partes[offset + 1].isBlank()) {
            return partes[offset + 1];
        }
        return null;
    }

    private boolean isPublic(String path) {
        String normalizedPath = normalizePath(path);
        return normalizedPath.isBlank()
                || PUBLIC_EXACT_PATHS.contains(normalizedPath)
                || PUBLIC_PATH_PREFIXES.stream()
                        .anyMatch(prefix -> normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/"));
    }

    // Modo shadow: registra as decisoes que BLOQUEARIAM a requisicao (unmapped/
    // missing-user/denied) mesmo quando enforce=false, para mapear em producao real
    // exatamente o que precisa ser ajustado ANTES de ligar o enforcement. Nunca
    // altera o fluxo da requisicao.
    private void logShadow(ContainerRequestContext requestContext, String decision, String path, String required) {
        try {
            AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
            String matricula = user != null && !user.matricula().isBlank()
                    ? user.matricula()
                    : String.valueOf(requestContext.getHeaderString("X-GHBAM-MATRICULA"));
            String cargo = user != null ? user.cargo() : "";
            LOGGER.warning("RBAC-SHADOW decision=" + decision + " enforce=" + enforce
                    + " method=" + requestContext.getMethod() + " path=" + path
                    + " matricula=" + matricula + " cargo=" + cargo
                    + " required=" + (required == null ? "" : required));
        } catch (RuntimeException ignored) {
            // Log de shadow nunca pode interferir na requisicao.
        }
    }

    // Shadow log dedicado a requiredAllPermissions (logica E), separado do
    // RBAC-SHADOW generico para poder gerar sozinho no log
    // ("grep RBAC-SHADOW-REQUIRED") antes de ligar enforceRequiredPermissions.
    private void logShadowRequired(ContainerRequestContext requestContext, String path, List<String> requiredAll) {
        try {
            AuthenticatedUser user = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
            String matricula = user != null ? user.matricula() : "";
            String cargo = user != null ? user.cargo() : "";
            LOGGER.warning("RBAC-SHADOW-REQUIRED decision=denied-missing-required-permission"
                    + " enforceRequiredPermissions=" + enforceRequiredPermissions
                    + " method=" + requestContext.getMethod() + " path=" + path
                    + " matricula=" + matricula + " cargo=" + cargo
                    + " requiredAll=" + String.join(",", requiredAll));
        } catch (RuntimeException ignored) {
            // Log de shadow nunca pode interferir na requisicao.
        }
    }

    private Response error(Response.Status status, String message) {
        return error(status, message, message);
    }

    private Response error(Response.Status status, String code, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"erro\":\"" + code + "\",\"mensagem\":\"" + message + "\"}")
                .build();
    }

    private String normalize(String value) {
        return normalizePath(value);
    }

    private static String normalizePath(String value) {
        return String.valueOf(value == null ? "" : value)
                .replaceAll("^/+", "")
                .replaceAll("/+$", "")
                .toLowerCase(Locale.ROOT);
    }
}





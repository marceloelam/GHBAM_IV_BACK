package br.com.iv.security;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.iv.entity.Servidor;
import br.com.iv.entity.Servidor2;
import br.com.iv.repository.Servidor2Repository;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.service.SessaoAtivaService;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.mindrot.jbcrypt.BCrypt;

@ApplicationScoped
public class SecurityPermissionService {

    // Uma tela abre varias APIs em paralelo. Durante essa rajada, usuario,
    // senha inicial e permissoes sao os mesmos. Dois segundos eliminam as
    // consultas/BCrypt duplicados sem manter autorizacao obsoleta por longo tempo.
    private static final long USER_CACHE_TTL_MS = 2_000;
    private static final int USER_CACHE_MAX_ENTRIES = 2_048;
    private final ExpiringRequestCache<String, AuthenticatedUser> userCache =
            new ExpiringRequestCache<>(USER_CACHE_TTL_MS, USER_CACHE_MAX_ENTRIES);

    @Inject
    ServidorRepository servidorRepository;

    @Inject
    Servidor2Repository servidor2Repository;

    @Inject
    JWTParser jwtParser;

    @Inject
    JsonWebToken currentToken;

    @Inject
    TokenRevocationService tokenRevocationService;

    @Inject
    SessaoAtivaService sessaoAtivaService;

    @ConfigProperty(name = "ghbam.security.legacy-header-auth.enabled", defaultValue = "false")
    boolean legacyHeaderAuthEnabled;

    public AuthenticatedUser authenticate(String matricula, String senha) {
        return authenticate(matricula, senha, "");
    }

    @Transactional
    public AuthenticatedUser authenticate(String matricula, String senha, String senhaMontada) {
        Servidor servidor = servidorRepository.localizarMatricula(safe(matricula));
        if (servidor == null || !Boolean.TRUE.equals(servidor.getAtivo())) {
            return null;
        }
        String storedPassword = safe(servidor.getPwd());
        String requestedPassword = safe(senha);
        if (requestedPassword.isBlank() && !safe(senhaMontada).isBlank()) {
            requestedPassword = resolvePasswordFromKeypad(storedPassword, senhaMontada);
        }
        if (!passwordMatches(storedPassword, requestedPassword)) {
            return null;
        }
        migrarSenhaParaBcryptSeNecessario(servidor, storedPassword, requestedPassword);
        return fromServidor(servidor, senhaAindaEhPadrao(servidor.getPwd()));
    }

    // Reutilizado tambem fora do login (RbacRequestFilter, a cada requisicao
    // autenticada): como e derivado so do hash salvo, da pra saber se a senha
    // "de fabrica" ainda esta em vigor sem precisar da senha em texto que o
    // usuario digitou no momento do login.
    private boolean senhaAindaEhPadrao(String storedPassword) {
        String stored = safe(storedPassword);
        if (isBcryptHash(stored)) {
            try {
                return BCrypt.checkpw("123456", stored);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return "123456".equals(stored) || "123456".equals(decodedPassword(stored));
    }

    // Migracao transparente: no primeiro login bem-sucedido de cada usuario
    // apos este deploy, a senha em texto/Base64 vira hash bcrypt. Ninguem
    // precisa trocar senha nem ser avisado - so acontece na hora que o
    // login ja deu certo pelo metodo antigo.
    private void migrarSenhaParaBcryptSeNecessario(Servidor servidor, String storedPassword, String resolvedPlaintext) {
        if (isBcryptHash(storedPassword) || resolvedPlaintext == null || resolvedPlaintext.isBlank()) {
            return;
        }
        servidor.setPwd(BCrypt.hashpw(resolvedPlaintext, BCrypt.gensalt()));
        servidor.persist();
    }

    public AuthenticatedUser resolve(ContainerRequestContext requestContext) {
        return resolveFromHeaderLookup(requestContext::getHeaderString);
    }

    // Mesma resolucao de resolve(ContainerRequestContext), pra uso fora do
    // RbacRequestFilter - GerarToken (troca de unidade, minhas-unidades) fica
    // no prefixo publico "auth" e por isso nunca passa pelo filtro, mas
    // precisa do mesmo fallback por header legado (X-GHBAM-MATRICULA) que o
    // resto do app usa quando a sessao Bearer esta orfa/inativa.
    public AuthenticatedUser resolveFromHeaders(jakarta.ws.rs.core.HttpHeaders httpHeaders) {
        return resolveFromHeaderLookup(httpHeaders::getHeaderString);
    }

    private AuthenticatedUser resolveFromHeaderLookup(java.util.function.Function<String, String> headerLookup) {
        String authorization = headerLookup.apply(HttpHeaders.AUTHORIZATION);
        String bearerToken = bearerToken(authorization);

        if (!bearerToken.isBlank()) {
            boolean bearerValido = !tokenRevocationService.isAuthorizationRevoked(authorization) && sessaoAtivaService.tokenAtivo(bearerToken);
            if (bearerValido) {
                TokenIdentity tokenIdentity = identityFromAuthorization(authorization);
                if (!tokenIdentity.matricula().isBlank()) {
                    return findServidorAtivo(tokenIdentity.matricula(), tokenIdentity.senhaInicial());
                }
            }
            // Bearer presente mas invalido/revogado/sem matricula: nao aceitar fallback por header em producao.
        }

        if (!legacyHeaderAuthEnabled) {
            return AuthenticatedUser.empty();
        }

        String matricula = sanitizarMatriculaHeader(headerLookup.apply("X-GHBAM-MATRICULA"));
        if (matricula.isBlank()) {
            return AuthenticatedUser.empty();
        }
        return findServidorAtivo(matricula);
    }

    // Quando o header X-GHBAM-MATRICULA chega duplicado na mesma requisicao
    // (dois interceptadores de fetch/axios no frontend setando o mesmo header
    // sem coordenacao), o RestEasy junta os valores repetidos com ", "
    // (ex.: "852852, 852852"), o que nunca bate com nenhuma matricula real no
    // banco e derruba a resolucao do usuario (missing-user) mesmo com sessao
    // valida. Matricula nunca contem virgula de verdade, entao pegar so o
    // primeiro token normaliza o caso duplicado sem afetar o caso normal.
    private String sanitizarMatriculaHeader(String value) {
        String raw = safe(value);
        if (raw.isBlank()) return "";
        int virgula = raw.indexOf(',');
        return virgula >= 0 ? safe(raw.substring(0, virgula)) : raw;
    }

    public boolean isAuthorizationRevokedOrInactive(String authorization) {
        String bearerToken = bearerToken(authorization);
        return !bearerToken.isBlank()
                && (tokenRevocationService.isAuthorizationRevoked(authorization) || !sessaoAtivaService.tokenAtivo(bearerToken));
    }

    // Variante usada apenas no endpoint de troca da propria senha
    // (servidor/{matricula}/senha/{pwd}). O usuario e obrigado a trocar a senha
    // inicial; se a sessao dele foi encerrada por um novo login em outra aba/
    // dispositivo, o Bearer ainda e' uma prova valida de autenticacao (nao
    // revogado, nao expirado) e nao deve travar o fluxo obrigatorio com 401
    // SESSAO_ENCERRADA. A checagem de sessao ativa continua valendo para todas
    // as demais rotas via isAuthorizationRevokedOrInactive().
    public AuthenticatedUser resolveForPasswordChange(ContainerRequestContext requestContext) {
        String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        String bearerToken = bearerToken(authorization);

        if (!bearerToken.isBlank() && !tokenRevocationService.isAuthorizationRevoked(authorization)) {
            TokenIdentity tokenIdentity = identityFromAuthorization(authorization);
            if (tokenIdentity.matricula().isBlank()) {
                tokenIdentity = identityFromToken(currentToken);
            }
            if (!tokenIdentity.matricula().isBlank()) {
                return findServidorAtivo(tokenIdentity.matricula(), tokenIdentity.senhaInicial());
            }

        }

        if (!legacyHeaderAuthEnabled) {
            return AuthenticatedUser.empty();
        }

        String matricula = sanitizarMatriculaHeader(requestContext.getHeaderString("X-GHBAM-MATRICULA"));
        if (matricula.isBlank()) {
            return AuthenticatedUser.empty();
        }
        return findServidorAtivo(matricula);
    }

    private AuthenticatedUser findServidorAtivo(String matricula) {
        return findServidorAtivo(matricula, null);
    }

    private AuthenticatedUser findServidorAtivo(String matricula, Boolean senhaInicialToken) {
        String normalized = safe(matricula);
        if (normalized.isBlank()) {
            return AuthenticatedUser.empty();
        }
        return userCache.get(normalized, ignored -> loadServidorAtivo(normalized, senhaInicialToken));
    }

    private AuthenticatedUser loadServidorAtivo(String matricula, Boolean senhaInicialToken) {
        Servidor servidor = servidorRepository.localizarMatricula(matricula);
        if (servidor == null || !Boolean.TRUE.equals(servidor.getAtivo())) {
            return AuthenticatedUser.empty();
        }
        return senhaInicialToken == null
                ? fromServidor(servidor)
                : fromServidor(servidor, senhaInicialToken);
    }

    public void invalidateUserCache(String matricula) {
        userCache.invalidate(safe(matricula));
    }

    public AuthenticatedUser findByMatricula(String matricula) {
        Servidor servidor = servidorRepository.localizarMatricula(safe(matricula));
        return servidor == null ? AuthenticatedUser.empty() : fromServidor(servidor);
    }

    public AuthenticatedUser fromServidor(Servidor servidor) {
        if (servidor == null) {
            return AuthenticatedUser.empty();
        }
        return fromServidor(servidor, senhaAindaEhPadrao(servidor.getPwd()));
    }

    public AuthenticatedUser fromServidor(Servidor servidor, boolean senhaInicial) {
        if (servidor == null) {
            return AuthenticatedUser.empty();
        }
        Servidor2 servidor2 = servidor2Repository.localizarMatricula(servidor.getMatricula());
        return new AuthenticatedUser(
                String.valueOf(servidor.getIdservidor()),
                safe(servidor.getMatricula()),
                safe(servidor.getNome()),
                safe(servidor.getCargo()),
                safe(servidor.getUnidade()),
                safe(servidor.getCns()),
                safe(servidor.getCrm()),
                safe(servidor.getCorem()),
                safe(servidor.getCbo()),
                safe(servidor.getEspecialidade()),
                safe(servidor.getFoto()),
                safe(servidor.getUsuarioad()),
                senhaInicial,
                permissions(servidor, servidor2));
    }

    private Map<String, Boolean> permissions(Servidor s, Servidor2 s2) {
        Map<String, Boolean> p = new LinkedHashMap<>();
        put(p, "kadm", s.getAdministracao());
        put(p, "ksubadm", s.getSubadm());
        put(p, "krecepcao", s.getRecepcao());
        put(p, "ktriagem", s.getTriagem());
        put(p, "kmedico", s.getMedico());
        put(p, "kdentista", s.getDentista());
        put(p, "kremedios", s.getRemedios());
        put(p, "kdanfe", s.getDanfe());
        put(p, "klaboratorio", s.getLaboratorio());
        put(p, "krx", s.getRx());
        put(p, "kmedicacao", s.getMedicacao());
        put(p, "ktomografia", s.getTomografia());
        put(p, "kultrassonografia", s.getUltrassonografia());
        put(p, "kenfermagem", s.getEnfermagem());
        put(p, "kfaturamento", s.getFaturamento());
        put(p, "kfarmacia", s.getFarmacia());
        put(p, "kfarmhmsfx", s.getFarmhmsfx());
        put(p, "kfarmcentral", s.getFarmcentral());
        put(p, "kfisioterapia", s.getFisioterapia());
        put(p, "kassistencia", s.getAssistencia());
        put(p, "knutricao", s.getNutricao());
        put(p, "kfonoaudiologia", s.getFonoaudiologia());
        put(p, "kpsicologia", s.getPsicologia());
        put(p, "kfarmaciacentral", s.getFarmcentral());
        put(p, "ktcenfermagem", s.getTcenfermagem());
        put(p, "kcriaragenda", s.getCriaragenda());
        put(p, "kagendarpaciente", s.getAgendarpaciente());
        put(p, "kconsultorio_admin", s.getConsultorioadmin());
        put(p, "kconsultorio_recepcao", s.getConsultoriorecepcao());
        put(p, "kconsultorio_medico", s.getConsultoriomedico());
        put(p, "kconsultorio_financeiro", s.getConsultoriofinanceiro());
        put(p, "krh", s.getRh());

        boolean superusuario = s2 != null && Boolean.TRUE.equals(s2.getSuperusuario());
        put(p, "ksuperusuario", superusuario);
        put(p, "superusuario", superusuario);
        put(p, "pcadsalas", s2 != null && Boolean.TRUE.equals(s2.getCadsalas()));
        put(p, "pcadsetores", s2 != null && Boolean.TRUE.equals(s2.getCadsetores()));
        put(p, "pcadusuarios", s2 != null && Boolean.TRUE.equals(s2.getCadusuarios()));
        put(p, "pcadleitos", s2 != null && Boolean.TRUE.equals(s2.getCadleitos()));
        put(p, "pcadsetor", s2 != null && Boolean.TRUE.equals(s2.getCadsetor()));
        put(p, "prestaurarpaciente", s2 != null && Boolean.TRUE.equals(s2.getRestaurarpaciente()));
        put(p, "pbiexecutivo", s2 != null && Boolean.TRUE.equals(s2.getBiexecutivo()));
        put(p, "pnirrestaurarinternacao", s2 != null && Boolean.TRUE.equals(s2.getNirrestaurarinternacao()));
        put(p, "pged", s2 != null && Boolean.TRUE.equals(s2.getGed()));
        put(p, "pgedremover", s2 != null && Boolean.TRUE.equals(s2.getGedremover()));
        put(p, "pgedconsulta", s2 != null && Boolean.TRUE.equals(s2.getGedconsulta()));
        put(p, "pcautela", s2 != null && Boolean.TRUE.equals(s2.getCautela()));
        put(p, "pfarmaciaprodutos", s2 != null && Boolean.TRUE.equals(s2.getFarmaciaprodutos()));
        put(p, "pfarmaciaestoque", s2 != null && Boolean.TRUE.equals(s2.getFarmaciaestoque()));
        put(p, "pfarmaciadispensacao", s2 != null && Boolean.TRUE.equals(s2.getFarmaciadispensacao()));
        put(p, "pfarmaciapermutas", s2 != null && Boolean.TRUE.equals(s2.getFarmaciapermutas()));
        put(p, "pfarmaciaoperacoes", s2 != null && Boolean.TRUE.equals(s2.getFarmaciaoperacoes()));
        put(p, "pfarmaciaavaliacao", s2 != null && Boolean.TRUE.equals(s2.getFarmaciaavaliacao()));
        put(p, "pfarmaciarelatorios", s2 != null && Boolean.TRUE.equals(s2.getFarmaciarelatorios()));
        put(p, "pnir", s2 != null && Boolean.TRUE.equals(s2.getNir()));
        put(p, "ppainelnir", s2 != null && Boolean.TRUE.equals(s2.getPainelnir()));
        put(p, "plocalcenso", s2 != null && Boolean.TRUE.equals(s2.getLocalcenso()));
        put(p, "ppesquisanir", s2 != null && Boolean.TRUE.equals(s2.getPesquisanir()));
        put(p, "pnirrelatorio", s2 != null && Boolean.TRUE.equals(s2.getNirrelatorio()));
        put(p, "pnirlaudointernacao", s2 != null && Boolean.TRUE.equals(s2.getNirlaudointernacao()));
        put(p, "pnirenfermariaseleitos", s2 != null && Boolean.TRUE.equals(s2.getNirenfermariaseleitos()));
        put(p, "pnirhistoricocompleto", s2 != null && Boolean.TRUE.equals(s2.getNirhistoricocompleto()));
        put(p, "psetorcenso", s2 != null && Boolean.TRUE.equals(s2.getSetorcenso()));
        put(p, "pinternadosvisitaimp", s2 != null && Boolean.TRUE.equals(s2.getInternadosvisitaimp()));
        put(p, "pcadunidades", s2 != null && Boolean.TRUE.equals(s2.getCadunidades()));
        put(p, "pauditoriaclinica", s2 != null && Boolean.TRUE.equals(s2.getAuditoriaclinica()));
        put(p, "pintegracaosus", s2 != null && Boolean.TRUE.equals(s2.getIntegracaosus()));
        put(p, "pmigracaodados", s2 != null && Boolean.TRUE.equals(s2.getMigracaodados()));
        put(p, "plistausuarios", s2 != null && Boolean.TRUE.equals(s2.getListausuarios()));
        put(p, "pmapapacientes", s2 != null && Boolean.TRUE.equals(s2.getMapapacientes()));
        put(p, "presumopaciente", s2 != null && Boolean.TRUE.equals(s2.getResumopaciente()));
        put(p, "pbalancohidrico", s2 != null && Boolean.TRUE.equals(s2.getBalancohidrico()));
        put(p, "pccih", s2 != null && Boolean.TRUE.equals(s2.getCcih()));
        put(p, "pdriveenfermagem", s2 != null && Boolean.TRUE.equals(s2.getDriveenfermagem()));
        put(p, "pcaddrives", s2 != null && Boolean.TRUE.equals(s2.getCaddrives()));
        put(p, "pescalaenfermagem", s2 != null && Boolean.TRUE.equals(s2.getEscalaenfermagem()));
        put(p, "pevolucaomultidisciplinar", s2 != null && Boolean.TRUE.equals(s2.getEvolucaomultidisciplinar()));
        put(p, "paguardandoatendimento", s2 != null && Boolean.TRUE.equals(s2.getAguardandoatendimento()));
        put(p, "patendimentoperiodo", s2 != null && Boolean.TRUE.equals(s2.getAtendimentoperiodo()));
        put(p, "pbiometria", s2 != null && Boolean.TRUE.equals(s2.getBiometria()));
        put(p, "pcomunicacaoespecialistas", s2 != null && Boolean.TRUE.equals(s2.getComunicacaoespecialistas()));
        put(p, "pcorrecaobam", s2 != null && Boolean.TRUE.equals(s2.getCorrecaobam()));
        // Item "Enfermarias e Leitos" do menu NIR usa as mesmas APIs da tela original.
        put(p, "penfermariaseleitos", s2 != null && (Boolean.TRUE.equals(s2.getEnfermariaseleitos()) || Boolean.TRUE.equals(s2.getNirenfermariaseleitos())));
        put(p, "pimprimirbam", s2 != null && Boolean.TRUE.equals(s2.getImprimirbam()));
        put(p, "plimparpainel", s2 != null && Boolean.TRUE.equals(s2.getLimparpainel()));
        put(p, "ppacientesematendimento", s2 != null && Boolean.TRUE.equals(s2.getPacientesematendimento()));
        put(p, "ppedidosexame", s2 != null && Boolean.TRUE.equals(s2.getPedidosexame()));
        put(p, "pavaliacaopacienteia_adm", s2 != null && Boolean.TRUE.equals(s2.getAvaliacaopacienteia_adm()));
        put(p, "ppendenciaassinatura", s2 != null && Boolean.TRUE.equals(s2.getPendenciaassinatura()));
        put(p, "phistoricocompleto_adm", s2 != null && Boolean.TRUE.equals(s2.getHistoricocompleto_adm()));
        put(p, "pprontuariocompleto", s2 != null && Boolean.TRUE.equals(s2.getProntuariocompleto()));
        put(p, "pexportardadoslgpd", s2 != null && Boolean.TRUE.equals(s2.getExportardadoslgpd()));
        put(p, "pcomoestaopaciente", s2 != null && Boolean.TRUE.equals(s2.getComoestaopaciente()));
        put(p, "pverificarassinatura", s2 != null && Boolean.TRUE.equals(s2.getVerificarassinatura()));
        put(p, "pragprontuario", s2 != null && Boolean.TRUE.equals(s2.getRagprontuario()));
        put(p, "presumoclinicoia", s2 != null && Boolean.TRUE.equals(s2.getResumoclinicoia()));
        put(p, "ppersonalizacaovisual", s2 != null && Boolean.TRUE.equals(s2.getPersonalizacaovisual()));
        put(p, "ppacs", s2 != null && Boolean.TRUE.equals(s2.getPacs()));
        put(p, "pcentrocirurgico", s2 != null && Boolean.TRUE.equals(s2.getPcentrocirurgico()));
        put(p, "pcirurgiaagendar", s2 != null && Boolean.TRUE.equals(s2.getPcirurgiaagendar()));
        put(p, "pcirurgiaexecutar", s2 != null && Boolean.TRUE.equals(s2.getPcirurgiaexecutar()));
        put(p, "pcirurgiadescrever", s2 != null && Boolean.TRUE.equals(s2.getPcirurgiadescrever()));
        put(p, "pcirurgiaanestesia", s2 != null && Boolean.TRUE.equals(s2.getPcirurgiaanestesia()));
        put(p, "pcirurgiasuspender", s2 != null && Boolean.TRUE.equals(s2.getPcirurgiasuspender()));
        put(p, "pcirurgiaopme", s2 != null && Boolean.TRUE.equals(s2.getPcirurgiaopme()));
        put(p, "pcirurgiasalas", s2 != null && Boolean.TRUE.equals(s2.getPcirurgiasalas()));
        put(p, "pcirurgialistaespera", s2 != null && Boolean.TRUE.equals(s2.getPcirurgialistaespera()));
        put(p, "papoioassistencial", s2 != null && Boolean.TRUE.equals(s2.getPapoioassistencial()));
        put(p, "pcme", s2 != null && Boolean.TRUE.equals(s2.getPcme()));
        put(p, "phemoterapia", s2 != null && Boolean.TRUE.equals(s2.getPhemoterapia()));
        put(p, "psnd", s2 != null && Boolean.TRUE.equals(s2.getPsnd()));
        put(p, "prnds", s2 != null && Boolean.TRUE.equals(s2.getPrnds()));
        put(p, "pnsp", s2 != null && Boolean.TRUE.equals(s2.getPnsp()));
        put(p, "pnspgestao", s2 != null && Boolean.TRUE.equals(s2.getPnspgestao()));
        put(p, "pnspclassificar", s2 != null && Boolean.TRUE.equals(s2.getPnspclassificar()));
        put(p, "pnspbloqueado", s2 != null && Boolean.TRUE.equals(s2.getPnspbloqueado()));
        put(p, "pfhirsumarioalta", s2 != null && Boolean.TRUE.equals(s2.getPfhirsumarioalta()));
        put(p, "plogs", s2 != null && Boolean.TRUE.equals(s2.getLogs()));
        put(p, "pminhasassinaturas", s2 != null && Boolean.TRUE.equals(s2.getMinhasassinaturas()));
        put(p, "pavaliacaopacienteia", s2 != null && Boolean.TRUE.equals(s2.getAvaliacaopacienteia()));
        put(p, "pbulario", s2 != null && Boolean.TRUE.equals(s2.getBulario()));
        put(p, "pciclomedicacao", s2 != null && Boolean.TRUE.equals(s2.getCiclomedicacao()));
        // Item "Histórico completo" do menu NIR usa as mesmas APIs da tela original.
        put(p, "phistoricocompleto", s2 != null && (Boolean.TRUE.equals(s2.getHistoricocompleto()) || Boolean.TRUE.equals(s2.getNirhistoricocompleto())));
        put(p, "psaeenfermagem", s2 != null && Boolean.TRUE.equals(s2.getSaeenfermagem()));
        put(p, "pclassificacaoghbam", s2 != null && Boolean.TRUE.equals(s2.getClassificacaoghbam()));
        put(p, "pclassificacaoghbam_revisar", s2 != null && Boolean.TRUE.equals(s2.getClassificacaoghbam_revisar()));
        put(p, "pclassificacaoghbam_aprovar", s2 != null && Boolean.TRUE.equals(s2.getClassificacaoghbam_aprovar()));
        put(p, "pclassificacaoghbam_admin", s2 != null && Boolean.TRUE.equals(s2.getClassificacaoghbam_admin()));
        put(p, "pclassificacaoghbam_auditar", s2 != null && Boolean.TRUE.equals(s2.getClassificacaoghbam_auditar()));
        put(p, "pcontroleassistencial", s2 != null && Boolean.TRUE.equals(s2.getControleassistencial()));
        put(p, "psinan", s2 != null && Boolean.TRUE.equals(s2.getSinan()));
        put(p, "pdocumentosassistenciais", s2 != null && Boolean.TRUE.equals(s2.getDocumentosassistenciais()));
        put(p, "panotacoesenfermagem", s2 != null && Boolean.TRUE.equals(s2.getAnotacoesenfermagem()));
        put(p, "pmonitoramento", s2 != null && Boolean.TRUE.equals(s2.getMonitoramento()));
        put(p, "pcontroleplantoes", s2 != null && Boolean.TRUE.equals(s2.getControleplantoes()));
        put(p, "pprodutividaderh", s2 != null && Boolean.TRUE.equals(s2.getProdutividaderh()));
        put(p, "ptreinamentocertificacao", s2 != null && Boolean.TRUE.equals(s2.getTreinamentocertificacao()));
        put(p, "pgerenciarcertificados", s2 != null && Boolean.TRUE.equals(s2.getGerenciarcertificados()));
        put(p, "pvigilanciaepidemiologica", s2 != null && Boolean.TRUE.equals(s2.getVigilanciaepidemiologica()));
        put(p, "pconfiguracaovigilanciaepidemiologica", s2 != null && Boolean.TRUE.equals(s2.getConfiguracaovigilanciaepidemiologica()));
        put(p, "pproducaomedica", s2 != null && Boolean.TRUE.equals(s2.getProducaomedica()));
        put(p, "ppainelenfermagem", s2 != null && Boolean.TRUE.equals(s2.getPainelenfermagem()));
        put(p, "ppainelspa", s2 != null && Boolean.TRUE.equals(s2.getPainelspa()));
        put(p, "ppainelmaternidade", s2 != null && Boolean.TRUE.equals(s2.getPainelmaternidade()));
        put(p, "ppainelpediatria", s2 != null && Boolean.TRUE.equals(s2.getPainelpediatria()));
        put(p, "pmodeloevolucaoenf", s2 != null && Boolean.TRUE.equals(s2.getModeloevolucaoenf()));
        put(p, "pmodeloprescricaoenf", s2 != null && Boolean.TRUE.equals(s2.getModeloprescricaoenf()));
        put(p, "presumopacientes", s2 != null && Boolean.TRUE.equals(s2.getResumopacientes()));
        put(p, "pchatprofissionais", s2 != null && Boolean.TRUE.equals(s2.getChatprofissionais()));
        put(p, "pfaturamentosuslancar", s2 != null && Boolean.TRUE.equals(s2.getFaturamentosuslancar()));
        put(p, "pfaturamentosuscorrigir", s2 != null && Boolean.TRUE.equals(s2.getFaturamentosuscorrigir()));
        put(p, "pfaturamentosusauditar", s2 != null && Boolean.TRUE.equals(s2.getFaturamentosusauditar()));
        put(p, "pfaturamentosusfechar", s2 != null && Boolean.TRUE.equals(s2.getFaturamentosusfechar()));
        put(p, "pfaturamentosusreabrir", s2 != null && Boolean.TRUE.equals(s2.getFaturamentosusreabrir()));
        put(p, "sigtapimportar", s2 != null && Boolean.TRUE.equals(s2.getSigtapimportar()));
        put(p, "bpaexportar", s2 != null && Boolean.TRUE.equals(s2.getBpaexportar()));
        put(p, "apacautorizar", s2 != null && Boolean.TRUE.equals(s2.getApacautorizar()));
        put(p, "aihauditar", s2 != null && Boolean.TRUE.equals(s2.getAihauditar()));
        put(p, "faturamentosusimportarretorno", s2 != null && Boolean.TRUE.equals(s2.getFaturamentosusimportarretorno()));
        // GHBAM APS (Atencao Primaria / substituto do PEC e-SUS nas UBS/ESF)
        // G-PEC: cada parte marcada no card de /servidores2 libera tambem o perfil
        // de API que ela usa (a checagem fina continua nos controllers APS).
        boolean gAtend = s2 != null && Boolean.TRUE.equals(s2.getGpecatendimentos());
        boolean gCid = s2 != null && Boolean.TRUE.equals(s2.getGpeccidadaos());
        boolean gAgenda = s2 != null && Boolean.TRUE.equals(s2.getGpecagenda());
        boolean gVac = s2 != null && Boolean.TRUE.equals(s2.getGpecvacinacao());
        boolean gTerr = s2 != null && Boolean.TRUE.equals(s2.getGpecterritorio());
        boolean gReg = s2 != null && Boolean.TRUE.equals(s2.getGpecregistros());
        boolean gAcomp = s2 != null && Boolean.TRUE.equals(s2.getGpecacompanhamento());
        boolean gRel = s2 != null && Boolean.TRUE.equals(s2.getGpecrelatorios());
        boolean gTrans = s2 != null && Boolean.TRUE.equals(s2.getGpectransmissao());
        boolean gConf = s2 != null && Boolean.TRUE.equals(s2.getGpecconfiguracao());
        boolean gAcs = s2 != null && Boolean.TRUE.equals(s2.getGpecappacs());
        put(p, "pgpecatendimentos", gAtend);
        put(p, "pgpeccidadaos", gCid);
        put(p, "pgpecagenda", gAgenda);
        put(p, "pgpecvacinacao", gVac);
        put(p, "pgpecterritorio", gTerr);
        put(p, "pgpecregistros", gReg);
        put(p, "pgpecacompanhamento", gAcomp);
        put(p, "pgpecrelatorios", gRel);
        put(p, "pgpectransmissao", gTrans);
        put(p, "pgpecconfiguracao", gConf);
        put(p, "pgpecappacs", gAcs);
        put(p, "paps", (s2 != null && Boolean.TRUE.equals(s2.getAps())) || gAtend || gCid || gAgenda || gReg || gAcomp);
        put(p, "papsacs", (s2 != null && Boolean.TRUE.equals(s2.getApsacs())) || gTerr || gAcs);
        put(p, "papsvacina", (s2 != null && Boolean.TRUE.equals(s2.getApsvacina())) || gVac);
        put(p, "papsgestao", (s2 != null && Boolean.TRUE.equals(s2.getApsgestao())) || gRel || gTrans || gConf);
        return p;
    }


    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        return authorization.substring(7).trim();
    }
    /**
     * Unidade em que a sessao foi aberta (claim "unidade" do JWT assinado, atualizada
     * na troca de unidade). Vazio quando nao ha Bearer valido (ex.: header legado em dev).
     */
    public String unidadeDaSessao(ContainerRequestContext requestContext) {
        String authorization = requestContext.getHeaderString("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        try {
            JsonWebToken token = jwtParser.parse(authorization.substring(7).trim());
            Object claim = token.getClaim("unidade");
            return claim == null ? "" : safe(claim.toString());
        } catch (ParseException | RuntimeException e) {
            return "";
        }
    }

    private TokenIdentity identityFromAuthorization(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return TokenIdentity.empty();
        }
        try {
            JsonWebToken token = jwtParser.parse(authorization.substring(7).trim());
            return identityFromToken(token);
        } catch (ParseException | RuntimeException e) {
            return TokenIdentity.empty();
        }
    }

    private TokenIdentity identityFromToken(JsonWebToken token) {
        if (token == null) {
            return TokenIdentity.empty();
        }
        try {
            String matricula = primeiroClaimTexto(token, "matricula", "username", "preferred_username", "upn", "matServidor", "sub");
            if (matricula.isBlank()) {
                matricula = token.getName() == null ? "" : token.getName();
            }

            Object senhaInicialClaim = token.getClaim("senhaInicial");
            Boolean senhaInicial = senhaInicialClaim instanceof Boolean value
                    ? value
                    : senhaInicialClaim == null ? null : Boolean.valueOf(senhaInicialClaim.toString());
            return new TokenIdentity(safe(matricula), senhaInicial);
        } catch (RuntimeException e) {
            return TokenIdentity.empty();
        }
    }

    private String primeiroClaimTexto(JsonWebToken token, String... nomes) {
        for (String nome : nomes) {
            Object value = token.getClaim(nome);
            String texto = safe(value == null ? "" : value.toString());
            if (!texto.isBlank()) {
                return texto;
            }
        }
        return "";
    }

    private record TokenIdentity(String matricula, Boolean senhaInicial) {
        private static TokenIdentity empty() {
            return new TokenIdentity("", null);
        }
    }

    private void put(Map<String, Boolean> permissions, String key, Boolean value) {
        permissions.put(key, Boolean.TRUE.equals(value));
    }

    private void put(Map<String, Boolean> permissions, String key, boolean value) {
        permissions.put(key, value);
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String decodeBase64(String value) {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String decodedPassword(String value) {
        String decoded = safe(decodeBase64(value));
        return decoded.matches("\\d{6}") ? decoded : "";
    }

    private static final java.util.regex.Pattern BCRYPT_PATTERN = java.util.regex.Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    private boolean isBcryptHash(String value) {
        return value != null && BCRYPT_PATTERN.matcher(value).matches();
    }

    private boolean passwordMatches(String storedPassword, String requestedPassword) {
        String requested = safe(requestedPassword);
        if (requested.isBlank()) {
            return false;
        }
        String stored = safe(storedPassword);
        if (isBcryptHash(stored)) {
            try {
                return BCrypt.checkpw(requested, stored);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (stored.equals(requested)) {
            return true;
        }
        String decoded = decodedPassword(stored);
        return !decoded.isBlank() && decoded.equals(requested);
    }

    private String resolvePasswordFromKeypad(String storedPassword, String senhaMontada) {
        String stored = safe(storedPassword);

        if (isBcryptHash(stored)) {
            return resolvePasswordFromKeypadBcrypt(stored, senhaMontada);
        }

        String decoded = decodedPassword(stored);

        String resolved = extractPasswordFromKeypad(stored, senhaMontada);
        if (passwordMatches(stored, resolved)) {
            return resolved;
        }

        if (!decoded.isBlank()) {
            resolved = extractPasswordFromKeypad(decoded, senhaMontada);
            if (passwordMatches(stored, resolved)) {
                return resolved;
            }
        }

        return "";
    }

    // O teclado virtual manda 6 pares de digito (12 chars): em cada posicao,
    // o usuario clicou em UM dos dois, mas o front deliberadamente nao sabe
    // qual e o certo (evita que quem grava a tela ou olha por cima do ombro
    // aprenda a senha real so vendo o clique). Antes, o backend descobria o
    // digito certo comparando caractere a caractere contra a senha em texto
    // guardada no banco. Bcrypt e um hash de mao unica - nao da pra "abrir"
    // pra comparar digito a digito. Em vez disso, testamos as combinacoes
    // possiveis (no maximo 2^6 = 64) contra o hash ate achar a unica que bate.
    private String resolvePasswordFromKeypadBcrypt(String storedHash, String senhaMontada) {
        String keypad = safe(senhaMontada);
        if (keypad.length() < 12) {
            return "";
        }
        char[][] opcoes = new char[6][2];
        for (int posicao = 0; posicao < 6; posicao++) {
            int indice = posicao * 2;
            opcoes[posicao][0] = keypad.charAt(indice);
            opcoes[posicao][1] = keypad.charAt(indice + 1);
        }
        return resolverCandidatoBcrypt(storedHash, opcoes, new char[6], 0);
    }

    private String resolverCandidatoBcrypt(String storedHash, char[][] opcoes, char[] candidato, int posicao) {
        if (posicao == opcoes.length) {
            String tentativa = new String(candidato);
            try {
                return BCrypt.checkpw(tentativa, storedHash) ? tentativa : "";
            } catch (IllegalArgumentException e) {
                return "";
            }
        }
        for (char opcao : opcoes[posicao]) {
            candidato[posicao] = opcao;
            String resultado = resolverCandidatoBcrypt(storedHash, opcoes, candidato, posicao + 1);
            if (!resultado.isEmpty()) {
                return resultado;
            }
        }
        return "";
    }

    private String extractPasswordFromKeypad(String storedPassword, String senhaMontada) {
        String keypad = safe(senhaMontada);
        String pwd = safe(storedPassword);
        if (pwd.length() < 6 || keypad.length() < 12) {
            return "";
        }

        StringBuilder resolved = new StringBuilder();
        for (int passwordIndex = 0; passwordIndex < 6; passwordIndex++) {
            int keypadIndex = passwordIndex * 2;
            String expected = pwd.substring(passwordIndex, passwordIndex + 1);
            String first = keypad.substring(keypadIndex, keypadIndex + 1);
            String second = keypad.substring(keypadIndex + 1, keypadIndex + 2);
            if (expected.equals(first)) {
                resolved.append(first);
            } else if (expected.equals(second)) {
                resolved.append(second);
            } else {
                return "";
            }
        }
        return resolved.toString();
    }
}


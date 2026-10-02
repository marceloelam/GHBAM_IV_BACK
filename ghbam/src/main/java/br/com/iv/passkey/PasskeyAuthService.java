package br.com.iv.passkey;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;

import br.com.iv.entity.PasskeyChallenge;
import br.com.iv.entity.PasskeyCredential;
import br.com.iv.entity.Servidor;
import br.com.iv.repository.PasskeyChallengeRepository;
import br.com.iv.repository.PasskeyCredentialRepository;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.SessaoAtivaService;
import br.com.iv.service.SessaoJaAtivaException;
import br.com.iv.token.TokenService;
import br.com.iv.token.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.HttpHeaders;

@ApplicationScoped
public class PasskeyAuthService {
    private static final String TIPO_REGISTRO = "REGISTRO";
    private static final String TIPO_LOGIN = "LOGIN";

    @Inject
    GhbamCredentialRepository credentialRepository;

    @Inject
    PasskeyCredentialRepository passkeyRepository;

    @Inject
    PasskeyChallengeRepository challengeRepository;

    @Inject
    ServidorRepository servidorRepository;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Inject
    TokenService tokenService;

    @Inject
    SessaoAtivaService sessaoAtivaService;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "ghbam.webauthn.rp-id", defaultValue = "ghbam.com.br")
    String rpId;

    @ConfigProperty(name = "ghbam.webauthn.rp-name", defaultValue = "GHBAM")
    String rpName;

    @ConfigProperty(name = "ghbam.webauthn.origins", defaultValue = "https://ghbam.com.br,http://localhost:3000,http://localhost:5173,http://127.0.0.1:5173")
    String origins;

    @Transactional
    public Map<String, Object> iniciarRegistro(AuthenticatedUser user, String origin) {
        if (user == null || user.matricula().isBlank()) {
            throw new NotAuthorizedException("Usuario nao autenticado.");
        }

        ByteArray userHandle = credentialRepository.handleParaMatricula(user.matricula());
        PublicKeyCredentialCreationOptions request = relyingParty(origin).startRegistration(
            StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                    .name(user.matricula())
                    .displayName(user.nome().isBlank() ? user.matricula() : user.nome())
                    .id(userHandle)
                    .build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                    .residentKey(ResidentKeyRequirement.REQUIRED)
                    .userVerification(UserVerificationRequirement.REQUIRED)
                    .build())
                .build());

        try {
            String requestId = salvarChallenge(TIPO_REGISTRO, user.matricula(), request.toJson());
            return respostaComRequestId(requestId, request.toCredentialsCreateJson());
        } catch (Exception e) {
            throw new BadRequestException("Nao foi possivel iniciar cadastro da biometria deste celular.", e);
        }
    }

    @Transactional
    public Map<String, Object> finalizarRegistro(AuthenticatedUser user, Map<String, Object> body, String origin) {
        if (user == null || user.matricula().isBlank()) {
            throw new NotAuthorizedException("Usuario nao autenticado.");
        }
        String requestId = texto(body.get("requestId"));
        PasskeyChallenge challenge = challengeRepository.localizarValido(requestId, TIPO_REGISTRO)
            .filter(item -> user.matricula().equals(item.getMatricula()))
            .orElseThrow(() -> new BadRequestException("Solicitacao de biometria expirada. Tente ativar novamente."));

        try {
            PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions.fromJson(challenge.getRequestJson());
            String credentialJson = mapper.writeValueAsString(body.get("credential"));
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credential =
                PublicKeyCredential.parseRegistrationResponseJson(credentialJson);

            RegistrationResult result = relyingParty(origin).finishRegistration(FinishRegistrationOptions.builder()
                .request(request)
                .response(credential)
                .build());

            PasskeyCredential entity = passkeyRepository.ativaPorCredentialId(credentialRepository.encode(result.getKeyId().getId()))
                .orElseGet(PasskeyCredential::new);
            entity.setMatricula(user.matricula());
            entity.setNomeDispositivo(texto(body.get("nomeDispositivo")));
            entity.setCredentialId(credentialRepository.encode(result.getKeyId().getId()));
            entity.setUserHandle(credentialRepository.encode(credentialRepository.handleParaMatricula(user.matricula())));
            entity.setPublicKeyCose(credentialRepository.encode(result.getPublicKeyCose()));
            entity.setSignatureCount(result.getSignatureCount());
            entity.setDiscoverable(result.isDiscoverable().orElse(false));
            entity.setBackupEligible(result.isBackupEligible());
            entity.setBackedUp(result.isBackedUp());
            entity.setAtiva(true);
            if (entity.getId() == null) entity.persist();

            challenge.delete();
            atualizarPareamentoServidor(user.matricula(), entity.getNomeDispositivo(), true);

            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("ativa", true);
            resposta.put("credentialId", entity.getCredentialId());
            resposta.put("matricula", entity.getMatricula());
            return resposta;
        } catch (Exception e) {
            throw new BadRequestException("Nao foi possivel validar a biometria deste celular: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> iniciarLogin(String origin, Map<String, Object> body) {
        String matricula = texto(body == null ? null : body.get("matricula"));
        StartAssertionOptions.StartAssertionOptionsBuilder options = StartAssertionOptions.builder()
            .userVerification(UserVerificationRequirement.REQUIRED);
        if (!matricula.isBlank() && passkeyRepository.existeAtivaParaMatricula(matricula)) {
            options.username(matricula);
        }
        AssertionRequest request = relyingParty(origin).startAssertion(options.build());
        try {
            String requestId = salvarChallenge(TIPO_LOGIN, "", request.toJson());
            return respostaComRequestId(requestId, request.toCredentialsGetJson());
        } catch (Exception e) {
            throw new BadRequestException("Nao foi possivel iniciar login por biometria do celular.", e);
        }
    }

    @Transactional
    public Map<String, Object> finalizarLogin(Map<String, Object> body, HttpHeaders headers) {
        String requestId = texto(body.get("requestId"));
        PasskeyChallenge challenge = challengeRepository.localizarValido(requestId, TIPO_LOGIN)
            .orElseThrow(() -> new BadRequestException("Solicitacao de login expirada. Tente novamente."));

        try {
            AssertionRequest request = AssertionRequest.fromJson(challenge.getRequestJson());
            String credentialJson = mapper.writeValueAsString(body.get("credential"));
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> credential =
                PublicKeyCredential.parseAssertionResponseJson(credentialJson);

            var result = relyingParty(headers == null ? "" : headers.getHeaderString("Origin")).finishAssertion(FinishAssertionOptions.builder()
                .request(request)
                .response(credential)
                .build());

            if (!result.isSuccess() || result.getUsername() == null || result.getUsername().isBlank()) {
                throw new BadRequestException("Biometria do celular nao reconhecida.");
            }

            String matricula = result.getUsername();
            Servidor servidor = servidorRepository.localizarMatricula(matricula);
            if (servidor == null || !Boolean.TRUE.equals(servidor.getAtivo())) {
                throw new NotAuthorizedException("Usuario inativo ou nao encontrado.");
            }

            passkeyRepository.ativaPorCredentialId(credentialRepository.encode(result.getCredential().getCredentialId()))
                .ifPresent(passkey -> {
                    passkey.setSignatureCount(result.getSignatureCount());
                    passkey.setUltimoUsoEm(LocalDateTime.now());
                    passkey.persist();
                });

            challenge.delete();
            return emitirLogin(securityPermissionService.fromServidor(servidor), headers);
        } catch (SessaoJaAtivaException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Nao foi possivel validar a biometria do celular: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> statusDispositivo(AuthenticatedUser user) {
        Map<String, Object> resposta = new LinkedHashMap<>();
        boolean ativa = user != null && passkeyRepository.existeAtivaParaMatricula(user.matricula());
        resposta.put("ativa", ativa);
        if (ativa) resposta.put("matricula", user.matricula());
        return resposta;
    }

    @Transactional
    public Map<String, Object> desativarDispositivo(AuthenticatedUser user) {
        if (user == null || user.matricula().isBlank()) {
            throw new NotAuthorizedException("Usuario nao autenticado.");
        }
        for (PasskeyCredential credential : passkeyRepository.ativasPorMatricula(user.matricula())) {
            credential.setAtiva(false);
            credential.persist();
        }
        atualizarPareamentoServidor(user.matricula(), "", false);
        return Map.of("ativa", false);
    }

    private Map<String, Object> emitirLogin(AuthenticatedUser authenticated, HttpHeaders headers) {
        Usuario user = new Usuario();
        user.username = authenticated.matricula();
        user.matricula = authenticated.matricula();
        user.idServidor = authenticated.idServidor();
        user.nome = authenticated.nome();
        user.cargo = authenticated.cargo();
        user.unidade = authenticated.unidade();
        user.cns = authenticated.cns();
        user.crm = authenticated.crm();
        user.corem = authenticated.corem();
        user.cbo = authenticated.cbo();
        user.especialidade = authenticated.especialidade();
        user.foto = authenticated.foto();
        user.usuarioad = authenticated.usuarioad();
        user.senhaInicial = authenticated.senhaInicial();
        user.role = authenticated.hasPermission("kadm") ? "admin" : "user";
        user.permissoes = authenticated.permissions();

        String token = tokenService.gerarToken(user);
        sessaoAtivaService.criarSessaoSeDisponivel(user.matricula, user.nome, token, obterIPCliente(headers), "celular", user.unidade, false);

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

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("token", token);
        resposta.put("tipo", "Bearer");
        resposta.put("expiraEm", 3600);
        resposta.put("usuario", usuario);
        return resposta;
    }

    private RelyingParty relyingParty(String origin) {
        String rpIdEfetivo = rpIdParaOrigem(origin);
        RelyingPartyIdentity identity = RelyingPartyIdentity.builder()
            .id(rpIdEfetivo)
            .name(rpName)
            .build();
        Set<String> allowedOrigins = allowedOrigins(origin);
        return RelyingParty.builder()
            .identity(identity)
            .credentialRepository(credentialRepository)
            .origins(allowedOrigins)
            .build();
    }

    private Set<String> allowedOrigins(String origin) {
        Set<String> allowedOrigins = Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        String origem = texto(origin);
        if (!origem.isBlank() && originPermitida(origem)) {
            allowedOrigins.add(origem);
        }
        return allowedOrigins;
    }

    private String rpIdParaOrigem(String origin) {
        String origem = texto(origin);
        if (origem.isBlank() || !originPermitida(origem)) return rpId;
        try {
            URI uri = URI.create(origem);
            String host = uri.getHost();
            return host == null || host.isBlank() ? rpId : host.toLowerCase();
        } catch (IllegalArgumentException e) {
            return rpId;
        }
    }

    private boolean originPermitida(String origin) {
        String origem = texto(origin);
        if (origem.isBlank()) return false;
        return Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .anyMatch(item -> item.equals("*") || item.equalsIgnoreCase(origem));
    }

    private String salvarChallenge(String tipo, String matricula, String requestJson) {
        challengeRepository.limparExpirados();
        PasskeyChallenge challenge = new PasskeyChallenge();
        challenge.setRequestId(UUID.randomUUID().toString());
        challenge.setTipo(tipo);
        challenge.setMatricula(texto(matricula));
        challenge.setRequestJson(requestJson);
        challenge.setExpiraEm(LocalDateTime.now().plusMinutes(5));
        challenge.persist();
        return challenge.getRequestId();
    }

    private Map<String, Object> respostaComRequestId(String requestId, String json) {
        try {
            Map<String, Object> resposta = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            resposta.put("requestId", requestId);
            return resposta;
        } catch (Exception e) {
            throw new BadRequestException("Nao foi possivel gerar desafio WebAuthn.", e);
        }
    }

    private void atualizarPareamentoServidor(String matricula, String nomeDispositivo, boolean ativo) {
        Servidor servidor = servidorRepository.localizarMatricula(matricula);
        if (servidor == null) return;
        servidor.setCelularPareadoAtivo(ativo);
        servidor.setCelularPareadoNome(ativo ? texto(nomeDispositivo) : null);
        servidor.setCelularPareadoEm(ativo ? LocalDateTime.now() : null);
        servidor.persist();
    }

    private String obterIPCliente(HttpHeaders httpHeaders) {
        if (httpHeaders == null) return "desconhecido";
        String xff = httpHeaders.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = httpHeaders.getHeaderString("X-Real-IP");
        return realIp == null || realIp.isBlank() ? "desconhecido" : realIp.trim();
    }

    private String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }
}

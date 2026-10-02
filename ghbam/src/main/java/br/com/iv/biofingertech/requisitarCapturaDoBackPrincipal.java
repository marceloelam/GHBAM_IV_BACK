package br.com.iv.biofingertech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.dto.ServidorBioDto;
import br.com.iv.entity.Servidor;
import br.com.iv.entity.SessaoAtiva;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.SessaoAtivaService;
import br.com.iv.service.SessaoJaAtivaException;
import br.com.iv.token.AuthService;
import br.com.iv.token.TokenService;
import br.com.iv.token.Usuario;

import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/fingerprint")
@Produces(MediaType.APPLICATION_JSON)
public class requisitarCapturaDoBackPrincipal {

    private static final Logger LOG = Logger.getLogger(requisitarCapturaDoBackPrincipal.class);
    private static final Set<String> connectedClients = ConcurrentHashMap.newKeySet();
    private static final Map<String, ServidorBioDto> verificationResults = new ConcurrentHashMap<>();
    private static final long VERIFICATION_TIMEOUT = 30000; // 30 segundos
    private static final Client sharedClient = ClientBuilder.newClient();
    private static final int DEFAULT_CLIENT_SERVICE_PORT = 9000; // fallback quando porta nÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© fornecida

    @Inject
    ServidorRepository servidorRepository;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Inject
    AuthService authService;

    @Inject
    TokenService tokenService;

    @Inject
    SessaoAtivaService sessaoAtivaService;

    // ===== CAPTURA =====
    @POST
    @Path("/capture")
    public Response requestFingerprintCapture(@Context RoutingContext context) {
        String clientIP = getClientIP(context);
        registrarIP(clientIP);
        LOG.infof("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€šÃ‚Â¡ Solicitando captura do cliente: %s", clientIP);
        Response clientResponse = forwardGet(clientIP, "/api/public/v1/captura/Capturar/1", MediaType.WILDCARD);
        if (clientResponse.getStatus() == Response.Status.OK.getStatusCode()) {
            String fir = clientResponse.readEntity(String.class);
            clientResponse.close(); // Fecha explicitamente apÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â³s ler a entidade
            LOG.infof("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€šÃ‚Â¥ Biometria recebida (Tamanho: %d)", fir.length());
            return Response.ok(fir).build();
        } else {
            String errorMsg = clientResponse.readEntity(String.class);
            clientResponse.close(); // Fecha explicitamente apÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â³s ler a entidade
            LOG.errorf("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Erro no cliente durante captura: %s", errorMsg);
            return Response.status(clientResponse.getStatus()).entity(errorMsg).build();
        }
    }

    // ===== ARMAZENAMENTO =====
    @POST
    @Path("/store")
    public Response storeFingerprint(String fir) {
        LOG.infof("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€šÃ‚Â¥ FIR recebida no backend principal: %s", fir);
        return Response.ok().build();
    }

    // ===== VERIFICAÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¡ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢O =====
    @POST
    @Path("/verify")
    public Response requestFingerprintVerification(Map<String, Object> payloadRecebido, @Context RoutingContext context) {
        String clientIP = getClientIP(context);
        registrarIP(clientIP);
        LOG.infof("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒâ€šÃ‚Â Iniciando verificaÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o biomÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©trica para cliente: %s", clientIP);

        try {
            // tenta carregar bibliotecas nativas, nÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o fatal
            try {
                NativeLibLoader.load();
                LOG.info("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒâ€¦Ã¢â‚¬â„¢ DLLs biomÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©tricas carregadas");
            } catch (Throwable ex) {
                LOG.warnf("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â NÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o foi possÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â­vel carregar DLLs nativas: %s", ex.getMessage());
            }

            String firCapturado = extrairFirRecebido(payloadRecebido);
            if (firCapturado == null || firCapturado.isBlank()) {
                // Compatibilidade com clientes antigos: se nao veio FIR no POST,
                // tenta capturar pela estacao cliente como era feito antes.
                Response captureResponse = forwardGet(clientIP, "/api/public/v1/captura/Capturar/1", MediaType.WILDCARD);
                if (captureResponse.getStatus() != Response.Status.OK.getStatusCode()) {
                    return handleClientError(captureResponse, "captura de FIR");
                }
                firCapturado = captureResponse.readEntity(String.class);
                captureResponse.close();
                LOG.infof("FIR capturada no cliente (tamanho: %d)", firCapturado.length());
            } else {
                LOG.infof("FIR recebida do login biometrico (tamanho: %d)", firCapturado.length());
            }

            // Busca templates no banco
            List<Servidor> servidores = servidorRepository.findAllComFir();
            LOG.infof("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¹ %d templates carregados do banco de dados", servidores.size());
            if (servidores.isEmpty()) {
                LOG.warn("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Nenhuma biometria cadastrada no banco de dados");
                return Response.status(Response.Status.NOT_FOUND).entity("Nenhuma biometria cadastrada").build();
            }

            List<BiometriaDTO> templates = new ArrayList<>();
            for (Servidor s : servidores) {
                BiometriaDTO dto = new BiometriaDTO();
                dto.matricula = s.getMatricula();
                String firServidor = firServidor(s);
                if (firServidor == null || firServidor.isBlank()) continue;
                dto.fir = firServidor;
                templates.add(dto);
            }

            // Tentativa local (heurÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â­stica)
            String matriculaEncontrada = BiometricMatcher.matchOneToMany(firCapturado, templates);
            boolean confirmadoPeloAgenteNitgen = false;

            // Se nÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o encontrou localmente, delega ao cliente para comparar 1:N
            if (matriculaEncontrada == null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("firToVerify", firCapturado);
                payload.put("templates", templates);

                Response compareResponse = forwardRequest(clientIP, "/api/public/v1/captura/Comparar1N", payload);
                if (compareResponse.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                    compareResponse.close();
                    LOG.warn("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Biometria nÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o identificada pelo dispositivo do cliente");
                    return Response.status(Response.Status.NOT_FOUND).entity("Biometria não identificada").build();
                }
                if (compareResponse.getStatus() != Response.Status.OK.getStatusCode()) {
                    return handleClientError(compareResponse, "comparaÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o biomÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©trica");
                }

                matriculaEncontrada = compareResponse.readEntity(String.class);
                compareResponse.close();
                confirmadoPeloAgenteNitgen = matriculaEncontrada != null && !matriculaEncontrada.isBlank();
                LOG.infof("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Biometria identificada pelo cliente: matrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â­cula=%s", matriculaEncontrada);
            }

            // Buscar servidor pelo resultado
            Servidor servidor = servidorRepository.localizarMatricula(matriculaEncontrada);
            if (servidor == null) {
                LOG.errorf("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Servidor nÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o encontrado para matrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â­cula: %s", matriculaEncontrada);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Servidor não encontrado para a matrícula: " + matriculaEncontrada).build();
            }

            // VerificaÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o adicional: confirmar que o FIR capturado confere com o FIR do banco
            boolean biometricConfirmed = confirmadoPeloAgenteNitgen;
            if (!biometricConfirmed) {
                try {
                    String firServidor = firServidor(servidor);
                    biometricConfirmed = NBioAdapter.matchFIRs(firCapturado, firServidor);
                    if (!biometricConfirmed) {
                        biometricConfirmed = BiometricMatcher.matchTwoFirStrings(firCapturado, firServidor);
                    }
                } catch (Throwable ex) {
                    LOG.warnf("Erro durante verificacao biometrica final para matricula=%s: %s", servidor.getMatricula(), ex.getMessage());
                    biometricConfirmed = false;
                }
            }

            if (!biometricConfirmed) {
                LOG.warnf("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Biometria capturada NÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢O confere com o cadastro para matrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â­cula=%s", servidor.getMatricula());
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("Biometria não confere com o cadastro para a matrícula informada").build();
            }

            // A digital ja confirma a identidade: nao ha por que devolver a
            // senha (hoje um hash bcrypt) pro frontend reenviar em /auth/login
            // como se fosse texto puro - isso nunca autentica. Em vez disso o
            // proprio /verify emite o token de sessao, igual ao GerarToken.login.
            LOG.infof("Verificacao biometrica concluida: matricula=%s, nome=%s",
                    servidor.getMatricula(), servidor.getNome());
            return Response.ok(emitirLoginBiometrico(servidor, clientIP)).build();

        } catch (Exception e) {
            return handleCommunicationError(getClientIP(context), e);
        }
    }

    private Map<String, Object> emitirLoginBiometrico(Servidor servidor, String clientIP) {
        AuthenticatedUser authenticated = securityPermissionService.fromServidor(servidor);
        Usuario user = authService.fromAuthenticatedUser(authenticated);
        String token = tokenService.gerarToken(user);

        // A leitura biometrica ja prova presenca fisica e identidade com mais
        // forca que uma senha; diferente do login por senha (que pede
        // confirmacao explicita do usuario pra forcar), aqui forcamos direto -
        // uma sessao anterior esquecida nao deve travar quem esta com o dedo
        // no leitor.
        sessaoAtivaService.criarSessaoSeDisponivel(
                user.matricula, user.nome, token, clientIP, "Biometria", user.unidade, true);

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

    // ===== RESPOSTA DO CLIENTE =====
    @POST
    @Path("/verify-response")
    public Response processVerificationResult(String jsonBruto, @Context RoutingContext context) {
        String clientIP = getClientIP(context);
        registrarIP(clientIP);

        try {
            if (jsonBruto == null || jsonBruto.trim().isEmpty()) {
                LOG.errorf("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ JSON vazio recebido do cliente: %s", clientIP);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Erro: JSON vazio ou nulo").build();
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            ServidorBioDto servidor = mapper.readValue(jsonBruto, ServidorBioDto.class);

            if (servidor.getPwd() == null || servidor.getPwd().isEmpty()) {
                LOG.errorf("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Campo 'pwd' ausente ou vazio do cliente: %s", clientIP);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Campo 'pwd' ausente ou vazio").build();
            }

            verificationResults.put(clientIP, servidor);
            LOG.infof("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Resultado processado: Matricula=%s | Nome=%s", 
                     servidor.getMatricula(), servidor.getNome());

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "VerificaÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o processada com sucesso");

            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.errorf("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Erro ao processar JSON do verify-response: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Erro ao processar JSON: " + e.getMessage()).build();
        }
    }

    // ===== CLIENTES CONECTADOS =====
    @GET
    @Path("/clients")
    public Response listConnectedClients() {
        LOG.info("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¹ Listando clientes conectados");
        return Response.ok(connectedClients).build();
    }

    private String firServidor(Servidor servidor) {
        if (servidor == null) return null;
        String fir = servidor.getFir();
        if (fir != null && !fir.isBlank()) return fir.trim();
        String biometria = servidor.getBiometria();
        return biometria != null ? biometria.trim() : null;
    }

    private String extrairFirRecebido(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return null;
        Object valor = payload.get("Digital");
        if (valor == null) valor = payload.get("digital");
        if (valor == null) valor = payload.get("fir");
        if (valor == null) valor = payload.get("FIR");
        String fir = valor == null ? null : String.valueOf(valor).trim();
        return fir != null && !fir.isBlank() ? fir : null;
    }
    // ===== MÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â°TODOS AUXILIARES =====
    private String getClientIP(RoutingContext context) {
        // First check common proxy headers (if behind a reverse proxy) to get the original host.
        // Nota: X-Forwarded-Port reflete a porta pela qual o navegador chegou no proxy (ex.: 443
        // do HTTPS) e nao tem nenhuma relacao com a porta do agente biometrico local no posto do
        // cliente - por isso NUNCA deve ser usado para decidir clientServicePort abaixo (era usado
        // antes e causava "Connection refused" tentando falar com a porta 443 do posto do cliente
        // em vez da porta 9000 do agente).
        String xForwardedFor = null;
        String xClientServicePort = null;
        String host = null;
        if (context != null && context.request() != null) {
            xForwardedFor = context.request().getHeader("X-Forwarded-For");
            // Optional header clients can send to tell backend which service port they serve on
            xClientServicePort = context.request().getHeader("X-Client-Service-Port");

            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // X-Forwarded-For can contain multiple IPs, take the first
                String[] parts = xForwardedFor.split(",");
                host = parts[0].trim();
            }
        }

        // Capture host (ignore source ephemeral port for client service discovery)
        int sourcePort = -1;
        if (host == null && context != null && context.request() != null && context.request().remoteAddress() != null) {
            host = context.request().remoteAddress().host();
            try {
                sourcePort = context.request().remoteAddress().port();
            } catch (Exception ignored) {
                // leave sourcePort as -1 if unavailable
            }
        }

        if (host == null || host.isEmpty()) {
            throw new WebApplicationException("NÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o foi possÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â­vel identificar o IP do cliente",
                    Response.Status.BAD_REQUEST);
        }

        // Determine which port to use to contact the client service.
        // Priority: explicit X-Client-Service-Port header > DEFAULT_CLIENT_SERVICE_PORT.
        int clientServicePort = DEFAULT_CLIENT_SERVICE_PORT;
        if (xClientServicePort != null && !xClientServicePort.isEmpty()) {
            try {
                clientServicePort = Integer.parseInt(xClientServicePort.trim());
            } catch (NumberFormatException ignored) {
            }
        } else if (sourcePort > 0 && sourcePort == DEFAULT_CLIENT_SERVICE_PORT) {
            // only use the source port when it exactly matches the expected service port
            clientServicePort = sourcePort;
        }

        return formatHostPort(host, Integer.toString(clientServicePort));
    }

    private String formatHostPort(String host, String portStr) {
        if (host == null) {
            throw new WebApplicationException("Host invÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡lido ao formatar IP/porta", Response.Status.BAD_REQUEST);
        }
        String hostTrim = host.trim();
        if (hostTrim.contains(":") && !hostTrim.startsWith("[")) {
            // IPv6 literal without brackets -> add brackets
            if (portStr == null || portStr.isEmpty()) {
                return "[" + hostTrim + "]";
            }
            return "[" + hostTrim + "]:" + portStr;
        }
        if (portStr == null || portStr.isEmpty()) {
            return hostTrim;
        }
        return hostTrim + ":" + portStr;
    }

    private void registrarIP(String ip) {
        if (connectedClients.add(ip)) {
            LOG.infof("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Novo cliente registrado: %s", ip);
        }
    }

    private Response forwardRequest(String clientIP, String path, Object entity) {
        try {
            // clientIP is expected in the form host:port (IPv6 properly bracketed when necessary)
            String target = "http://" + clientIP + path;
            return sharedClient.target(target)
                    .request(MediaType.WILDCARD)  // aceita qualquer tipo que o serviÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§o biomÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©trico retorne
                    .post(Entity.entity(entity, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            LOG.errorf("Erro ao encaminhar requisiÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o para %s: %s", clientIP, e.getMessage());
            throw new WebApplicationException("Erro na comunicaÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o com o cliente", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response forwardGet(String clientIP, String path, String acceptMediaType) {
        try {
            String target = "http://" + clientIP + path;
            if (acceptMediaType == null || acceptMediaType.isEmpty()) {
                acceptMediaType = MediaType.WILDCARD;
            }
            return sharedClient.target(target)
                    .request(acceptMediaType)
                    .get();
        } catch (Exception e) {
            LOG.errorf("Erro ao encaminhar requisiÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o GET para %s: %s", clientIP, e.getMessage());
            throw new WebApplicationException("Erro na comunicaÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o com o cliente", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response handleClientError(Response clientResponse, String operation) {
        try {
            String errorMsg = clientResponse.readEntity(String.class);
            LOG.errorf("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Erro no cliente durante %s: %s", operation, errorMsg);
            return Response.status(clientResponse.getStatus()).entity(errorMsg).build();
        } finally {
            clientResponse.close();
        }
    }

    private Response handleCommunicationError(String clientIP, Exception e) {
        LOG.errorf("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Erro na comunicaÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â£o com o cliente [%s]: %s", clientIP, e.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Erro na comunicação com o cliente: " + e.getMessage()).build();
    }
}



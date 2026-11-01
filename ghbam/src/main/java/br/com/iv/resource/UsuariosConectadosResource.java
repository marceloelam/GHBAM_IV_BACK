package br.com.iv.resource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.entity.Servidor;
import br.com.iv.repository.Servidor2Repository;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.service.SessaoAtivaService;
import br.com.iv.service.ConfiguracaoUnidadeService;
import br.com.iv.util.UsuarioConectado;
import br.com.iv.util.UsuariosConectadosUtil;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/usuarios-conectados")
public class UsuariosConectadosResource {
    private static final Logger LOGGER = Logger.getLogger(UsuariosConectadosResource.class.getName());

    private static final Map<String, ViaCepResult> CEP_CACHE = new ConcurrentHashMap<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    SecurityPermissionService securityPermissionService;

    @Inject
    Servidor2Repository servidor2Repository;

    @Inject
    ServidorRepository servidorRepository;

    @Inject
    SessaoAtivaService sessaoAtivaService;

    @Inject
    ConfiguracaoUnidadeService configuracaoUnidadeService;

    @POST
    @Path("/heartbeat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response heartbeat(HeartbeatRequest request, @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        if (request == null) {
            LOGGER.log(Level.WARNING, "[HEARTBEAT] Requisicao sem corpo JSON.");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"REQUEST_INVALIDO\",\"mensagem\":\"Corpo JSON obrigatorio.\"}")
                    .build();
        }

        if (request.unidade == null || request.unidade.isEmpty()
                || request.matricula == null || request.matricula.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"PARAMETROS_OBRIGATORIOS\",\"mensagem\":\"Parametros 'unidade' e 'matricula' obrigatorios.\"}")
                    .build();
        }

        String token = bearerToken(authorization);
        if (!token.isBlank()) {
            var sessaoToken = sessaoAtivaService.buscarPorToken(token);
            if (sessaoToken.isPresent() && !Boolean.TRUE.equals(sessaoToken.get().getAtiva())) {
                LOGGER.log(Level.INFO, "[HEARTBEAT] Sessao encerrada para matricula {0}", request.matricula);
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"erro\":\"SESSAO_ENCERRADA\",\"mensagem\":\"Sua sessao foi encerrada por um novo login.\"}")
                        .build();
            }
        }

        LOGGER.log(Level.FINE, "Heartbeat recebido: unidade={0}, matricula={1}, nome={2}",
                new Object[]{request.unidade, request.matricula, request.nome});

        String especialidade = request.especialidade;
        try {
            Servidor servidor = servidorRepository.localizarMatricula(request.matricula);
            if (servidor != null && servidor.getEspecialidade() != null && !servidor.getEspecialidade().isEmpty()) {
                especialidade = servidor.getEspecialidade();
                LOGGER.log(Level.FINE, "Especialidade encontrada no banco: {0} para matricula {1}",
                        new Object[]{especialidade, request.matricula});
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao buscar especialidade para matricula: {0}", request.matricula);
        }

        UsuariosConectadosUtil.adicionarUsuario(
                request.unidade,
                request.nome,
                request.matricula,
                request.cargo,
                especialidade,
                request.foto,
                chatLiberado(authorization, request.chatprofissionais),
                request.sala
        );

        try {
            sessaoAtivaService.atualizarAtividade(request.matricula);
            LOGGER.log(Level.INFO, "[HEARTBEAT] Atividade atualizada para matricula {0}", request.matricula);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[HEARTBEAT] Erro ao atualizar atividade da sessao: {0}", e.getMessage());
        }

        return Response.ok().entity("{\"status\":\"ok\"}").build();
    }

    @DELETE
    @Path("/logoff")
    public Response logoff(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                           @QueryParam("unidade") String unidade,
                           @QueryParam("matricula") String matricula,
                           @DefaultValue("true") @QueryParam("sessao") boolean encerrarSessao) {
        return finalizarLogoff(authorization, unidade, matricula, encerrarSessao);
    }

    @POST
    @Path("/logoff")
    public Response logoffPost(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                               @QueryParam("unidade") String unidade,
                               @QueryParam("matricula") String matricula,
                               @DefaultValue("true") @QueryParam("sessao") boolean encerrarSessao) {
        return finalizarLogoff(authorization, unidade, matricula, encerrarSessao);
    }

    private Response finalizarLogoff(String authorization, String unidade, String matricula, boolean encerrarSessao) {
        if (unidade == null || unidade.isEmpty() || matricula == null || matricula.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Parametros 'unidade' e 'matricula' obrigatorios.")
                    .build();
        }
        boolean removed = UsuariosConectadosUtil.removerUsuarioPorMatricula(unidade, matricula);
        if (encerrarSessao) {
            // Sem token nao e possivel identificar qual sessao encerrar com seguranca.
            // Logoff sem token (ex: heartbeat expirado navegando para /login) nao deve
            // matar sessoes de outros dispositivos do mesmo usuario.
            String token = bearerToken(authorization);
            if (!token.isBlank()) {
                try {
                    sessaoAtivaService.encerrarSessaoPorToken(token);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "[LOGOFF] Erro ao encerrar sessao da matricula {0}: {1}",
                            new Object[]{matricula, e.getMessage()});
                }
            }
        }
        return Response.ok("{\"status\":\"ok\",\"onlineRemovido\":" + removed + ",\"sessaoEncerrada\":" + encerrarSessao + "}").build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listarUsuarios(@QueryParam("unidade") String unidade,
                                   @DefaultValue("false") @QueryParam("chat") boolean apenasChat) {
        if (unidade == null || unidade.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Parametro 'unidade' obrigatorio.")
                    .build();
        }
        Set<UsuarioConectado> usuarios = UsuariosConectadosUtil.listarUsuarios(unidade, apenasChat);
        return Response.ok(usuarios).build();
    }

    @GET
    @Path("/disponibilidade-medica")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listarDisponibilidadeMedica() {
        List<UnidadeDisponivel> unidades = new ArrayList<>();
        for (var unidade : configuracaoUnidadeService.listarTodosHospitais()) {
            String sigla = texto(unidade.getSigla());
            if (sigla.isBlank()) continue;

            List<MedicoDisponivel> medicos = new ArrayList<>();

            UsuariosConectadosUtil.listarUsuarios(sigla).stream()
                    .map(this::medicoComDadosPublicaveis)
                    .filter(java.util.Objects::nonNull)
                    .forEach(medicos::add);

            servidorRepository.filtarPorUnidadeDoServidorAtivo(sigla).stream()
                    .filter(servidor -> cargoMedico(servidor.getCargo()))
                    .filter(servidor -> !texto(servidor.getEspecialidade()).isBlank())
                    .filter(servidor -> !texto(servidor.getFoto()).isBlank())
                    .map(servidor -> new MedicoDisponivel(
                            texto(servidor.getNome()),
                            texto(servidor.getMatricula()),
                            texto(servidor.getCrm()),
                            texto(servidor.getEspecialidade()),
                            texto(servidor.getFoto()),
                            false))
                    .forEach(cadastrado -> {
                        boolean jaOnline = medicos.stream().anyMatch(online ->
                                normalizar(online.nome()).equals(normalizar(cadastrado.nome()))
                                        && normalizar(online.especialidade()).equals(normalizar(cadastrado.especialidade())));
                        if (!jaOnline) medicos.add(cadastrado);
                    });

            medicos.sort((a, b) -> normalizar(a.nome()).compareTo(normalizar(b.nome())));

            if (!medicos.isEmpty()) {
                unidades.add(new UnidadeDisponivel(
                        sigla,
                        texto(unidade.getHospital()),
                        texto(unidade.getCep()),
                        texto(unidade.getBairroPadrao()),
                        texto(unidade.getMunicipioPadrao()),
                        texto(unidade.getComplemetos()),
                        montarEndereco(unidade),
                        unidade.getLatitude(),
                        unidade.getLongitude(),
                        medicos));
            }
        }
        List<String> especialidades = servidorRepository.findAllServidores().stream()
                .filter(servidor -> Boolean.TRUE.equals(servidor.getAtivo()))
                .filter(servidor -> cargoMedico(servidor.getCargo()))
                .filter(servidor -> !texto(servidor.getEspecialidade()).isBlank())
                .filter(servidor -> !texto(servidor.getFoto()).isBlank())
                .map(servidor -> texto(servidor.getEspecialidade()))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return Response.ok(new DisponibilidadeMedica(unidades, especialidades)).build();
    }

    private MedicoDisponivel medicoComDadosPublicaveis(UsuarioConectado usuario) {
        Servidor servidor = servidorRepository.localizarMatricula(texto(usuario.getMatricula()));
        if (servidor == null || !Boolean.TRUE.equals(servidor.getAtivo())) {
            return null;
        }
        String cargo = servidor.getCargo();
        String nome = servidor.getNome();
        String especialidade = servidor.getEspecialidade();
        String foto = servidor.getFoto();
        if (!cargoMedico(cargo) || texto(nome).isBlank() || texto(especialidade).isBlank() || texto(foto).isBlank()) {
            return null;
        }
        return new MedicoDisponivel(texto(nome), texto(servidor.getMatricula()), texto(servidor.getCrm()), texto(especialidade), texto(foto), true);
    }

    private boolean cargoMedico(String cargo) {
        String normalizado = normalizar(cargo);
        return normalizado.equals("MEDICO") || normalizado.equals("MEDICO(A)");
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String normalizar(String valor) {
        return java.text.Normalizer.normalize(texto(valor), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }

    public record MedicoDisponivel(String nome, String matricula, String crm, String especialidade, String foto, Boolean online) {}

    public record DisponibilidadeMedica(List<UnidadeDisponivel> unidades, List<String> especialidades) {}

    public record UnidadeDisponivel(
            String sigla,
            String nome,
            String cep,
            String bairro,
            String municipio,
            String complemento,
            String endereco,
            Double latitude,
            Double longitude,
            List<MedicoDisponivel> medicos) {}

    private String montarEndereco(ConfiguracaoUnidade unidade) {
        ViaCepResult viaCep = buscarCep(texto(unidade.getCep()));
        List<String> partes = new ArrayList<>();
        if (viaCep != null && !viaCep.logradouro().isBlank()) {
            partes.add(viaCep.logradouro());
            partes.add(viaCep.bairro());
            String localidade = viaCep.localidade();
            String uf = viaCep.uf();
            if (!localidade.isBlank()) {
                partes.add(localidade + (uf.isBlank() ? "" : "/" + uf));
            } else if (!uf.isBlank()) {
                partes.add(uf);
            }
        } else {
            partes.add(texto(unidade.getBairroPadrao()));
            partes.add(texto(unidade.getMunicipioPadrao()));
        }
        partes.add(texto(unidade.getComplemetos()));
        return partes.stream().filter(parte -> !parte.isBlank()).collect(Collectors.joining(" — "));
    }

    private ViaCepResult buscarCep(String cep) {
        String digitos = cep == null ? "" : cep.replaceAll("\\D", "");
        if (digitos.length() != 8) return null;
        return CEP_CACHE.computeIfAbsent(digitos, this::consultarViaCep);
    }

    private ViaCepResult consultarViaCep(String cep) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://viacep.com.br/ws/" + cep + "/json/"))
                    .header("User-Agent", "GHBAM-IV/1.0")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            JsonNode node = OBJECT_MAPPER.readTree(response.body());
            if (node.has("erro") && node.get("erro").asBoolean()) return null;
            return new ViaCepResult(
                    texto(node.path("logradouro").asText()),
                    texto(node.path("bairro").asText()),
                    texto(node.path("localidade").asText()),
                    texto(node.path("uf").asText()));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao consultar viaCEP para {0}: {1}",
                    new Object[]{cep, e.getMessage()});
            return null;
        }
    }

    private record ViaCepResult(String logradouro, String bairro, String localidade, String uf) {}

    /**
     * O chat liberado vem do cadastro (servidor2), não do navegador: quem entrou antes de uma
     * mudança de permissão ainda manda o valor antigo guardado no login e sumia da lista do chat.
     */
    private Boolean chatLiberado(String authorization, Boolean informado) {
        try {
            AuthenticatedUser user = securityPermissionService.resolveFromAuthorization(authorization);
            if (user != null && user.matricula() != null && !user.matricula().isBlank()) {
                return user.hasPermission("pchatprofissionais") || user.hasPermission("kadm");
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "[HEARTBEAT] Permissao de chat nao resolvida: {0}", e.getMessage());
        }
        return informado;
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        return authorization.substring(7).trim();
    }

    public static class HeartbeatRequest {
        public String unidade;
        public String matricula;
        public String nome;
        public String cargo;
        public String especialidade;
        public String foto;
        public Boolean chatprofissionais;
        public String sala;
    }
}

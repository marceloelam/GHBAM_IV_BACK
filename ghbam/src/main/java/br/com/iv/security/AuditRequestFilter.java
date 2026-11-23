package br.com.iv.security;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

import br.com.iv.dto.LogDto;
import br.com.iv.service.LogService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.runtime.BlockingOperationControl;

@Provider
@Priority(Priorities.USER)
public class AuditRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final DateTimeFormatter HORA_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> AUDITED_ACTION_TOKENS = Set.of(
            "imprimir",
            "impressao",
            "assinar",
            "assinatura",
            "cancelar",
            "baixar",
            "retificar",
            "exportar");
    private static final Set<String> LEGACY_GET_WRITE_TOKENS = Set.of(
            "alterar",
            "atualizar",
            "baixar",
            "cancelar",
            "deletar",
            "delete",
            "fechar",
            "finalizar",
            "inativar",
            "liberar",
            "remover",
            "transferir");
    private static final Set<String> CLINICAL_READ_PATHS = Set.of(
            "historicopaciente",
            "paciente",
            "pacientes",
            "bam",
            "atendimento",
            "evolucaopaciente",
            "evolucaopacienteenf",
            "sae-enfermagem",
            "modeloevolucao",
            "modeloprescricao",
            "prescricao",
            "prescricaocontinuacao",
            "prescricaoassinatura",
            "prescricaoaprazamentochecagem",
            "prescricaoitens",
            "prescricaoitemcicloevento",
            "prescricaoitemestruturado",
            "anamneseinternacao",
            "resumodealta",
            "resumomedico",
            "laudoautorizacao",
            "listaanestesica",
            "backendcontroleentrada",
            "backendcontrolesaida",
            "backendresumobalanco",
            "api/pedidos",
            "pedido",
            "pedidomedico2",
            "prontuarioclinico",
            "pedidoresultadolaboratorio",
            "avaliacao",
            "internacao",
            "novointernado",
            "localcenso",
            "setorcenso",
            "biexecutivo");
    private static final Set<String> NOISY_SUCCESSFUL_CLINICAL_READ_PREFIXES = Set.of(
            "avaliacao/unidadever/",
            "avaliacao/unidadeverhoje/",
            "avaliacao/unidadeversetorhoje/",
            "avaliacao/unidadeversetorsemana/",
            "avaliacao/unidadeversetormes/",
            "avaliacao/unidadeversetorano/",
            "pedidomedico2/examesbamps/");

    // Dedup de leituras clinicas (GET) repetidas em rajada: varios componentes da
    // mesma tela (ex.: AtMedico.jsx) buscam o mesmo BAM de forma independente ao
    // carregar, gerando uma linha por chamada em /logs para o que e', na pratica,
    // uma unica acao do usuario ("abriu o atendimento do paciente"). So se aplica
    // a clinicalRead (GET) - mutating/specialAction continuam sem dedup, porque
    // ali uma repeticao pode ser sinal de bug real (double-submit) que vale
    // aparecer no log. Mapa limitado por limpeza total ao passar de 5000 chaves,
    // em vez de LRU, por simplicidade - aceitavel para reducao de ruido de log.
    private static final long DEDUP_WINDOW_MS = 5000;

    private static final java.util.Map<String, String> PATH_MARCADORES_PACIENTE = java.util.Map.ofEntries(
            java.util.Map.entry("bam", AuditPacienteResolver.TIPO_BAM),
            java.util.Map.entry("nbam", AuditPacienteResolver.TIPO_BAM),
            java.util.Map.entry("idbam", AuditPacienteResolver.TIPO_BAM),
            java.util.Map.entry("numerobam", AuditPacienteResolver.TIPO_BAM),
            java.util.Map.entry("idpaciente", AuditPacienteResolver.TIPO_ID_PACIENTE),
            java.util.Map.entry("iddopaciente", AuditPacienteResolver.TIPO_ID_PACIENTE),
            java.util.Map.entry("prontuario", AuditPacienteResolver.TIPO_PRONTUARIO));
    private final java.util.concurrent.ConcurrentHashMap<String, Long> ultimoEventoLeituraClinica =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    LogService logService;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Inject
    AuditPacienteResolver auditPacienteResolver;

    private static final String PROP_REGISTRO = "audit.pacienteRegistro";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        requestContext.setProperty("audit.start", System.currentTimeMillis());
        // DELETE/inativar/alterar pelo id do proprio registro: descobre o paciente ANTES do recurso
        // executar, porque numa exclusao fisica depois nao ha mais registro para consultar.
        // So quando a thread permite consulta (nunca na IO thread do Vert.x); senao o log tenta depois.
        String method = requestContext.getMethod().toUpperCase(Locale.ROOT);
        if (!MUTATING_METHODS.contains(method) || !BlockingOperationControl.isBlockingAllowed()) return;
        try {
            AuditRegistroPaciente.Alvo alvo = AuditRegistroPaciente.localizar(normalize(requestContext.getUriInfo().getPath()));
            if (alvo == null) return;
            String[] ref = auditPacienteResolver.referenciaDoRegistro(alvo.entidade(), alvo.id());
            if (ref != null) requestContext.setProperty(PROP_REGISTRO, ref);
        } catch (RuntimeException ignorado) {
            // auditoria nunca atrapalha a requisicao
        }
    }

    // Este filtro roda depois de cada resposta e faz duas coisas que dependem do banco:
    // resolver quem e o usuario e gravar o log. A gravacao ja e assincrona; a resolucao
    // do usuario e condicional (ver BlockingOperationControl mais abaixo), porque em
    // resposta abortada o filtro executa na IO thread do Vert.x.
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        String path = normalize(requestContext.getUriInfo().getPath());
        if (shouldIgnore(path) || isHeartbeat(path)) {
            return;
        }

        String method = requestContext.getMethod().toUpperCase(Locale.ROOT);
        int status = responseContext.getStatus();
        boolean mutating = MUTATING_METHODS.contains(method);
        boolean legacyGetWrite = "GET".equals(method) && isLegacyGetWrite(path);
        boolean clinicalRead = "GET".equals(method) && !legacyGetWrite && isClinicalRead(path);
        boolean specialAction = isAuditedAction(path);
        if (legacyGetWrite) {
            addLegacyDeprecationHeaders(responseContext);
        }
        if (clinicalRead && status >= 200 && status < 300 && isNoisySuccessfulClinicalRead(path)) {
            return;
        }
        if (!mutating && !legacyGetWrite && !clinicalRead && !specialAction) {
            return;
        }

        AuthenticatedUser authenticatedUser = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
        // O resolve consulta o cadastro do servidor, e isso e bloqueante. Ele so entra
        // quando a thread permite: em resposta que nao passou pelo recurso (RBAC que
        // aborta, 401, 404) o filtro roda na IO thread do Vert.x e o Hibernate recusa a
        // consulta, transformando um 403 legitimo em tela de erro para o operador.
        //
        // Nesses casos a auditoria continua acontecendo pelos cabecalhos - que e o
        // fallback logo abaixo. Perde-se cargo e unidade vindos do cadastro, nao o
        // registro do acesso.
        if ((authenticatedUser == null || authenticatedUser.matricula().isBlank())
                && BlockingOperationControl.isBlockingAllowed()) {
            authenticatedUser = securityPermissionService.resolve(requestContext);
        }
        AuditUserContext user = authenticatedUser != null && !authenticatedUser.matricula().isBlank()
                ? AuditUserContext.fromAuthenticatedUser(
                        authenticatedUser,
                        requestContext.getHeaderString("X-GHBAM-REQUEST-ID"),
                        requestContext.getHeaderString(HttpHeaders.USER_AGENT))
                : AuditUserContext.fromHeaders(
                        requestContext.getHeaders(),
                        requestContext.getHeaderString(HttpHeaders.USER_AGENT));
        if (!user.hasUser()) {
            return;
        }

        if (clinicalRead && isDuplicadaEmRajada(user.matricula(), method, path, status)) {
            return;
        }

        String action = actionDescription(method, path, status, mutating, legacyGetWrite, clinicalRead, specialAction);
        LogDto dto = new LogDto();
        dto.setData(LocalDate.now());
        dto.setHora(LocalTime.now().format(HORA_FORMAT));
        dto.setEvento(buildEvent(action, method, path, status, user, clientIp(requestContext),
                status >= 400 ? motivoDaResposta(responseContext.getEntity()) : ""));
        dto.setUnidade(trimTo(user.unidade(), 20));
        String[] referenciaPaciente = extrairReferenciaPaciente(path);
        if (referenciaPaciente == null) {
            referenciaPaciente = extrairReferenciaPacienteDaQuery(requestContext.getUriInfo().getQueryParameters());
        }
        if (referenciaPaciente == null
                && requestContext.getProperty(AuditCorpoPacienteInterceptor.PROPRIEDADE) instanceof String[] doCorpo
                && doCorpo.length == 2) {
            referenciaPaciente = doCorpo;
        }
        if (referenciaPaciente == null && requestContext.getProperty(PROP_REGISTRO) instanceof String[] doRegistro
                && doRegistro.length == 2) {
            referenciaPaciente = doRegistro;
        }
        if (referenciaPaciente == null) {
            // Ex.: PUT /evolucaopaciente/123 (id da evolucao): o paciente vem no objeto devolvido.
            referenciaPaciente = AuditRespostaPaciente.extrair(responseContext.getEntity());
        }
        if (referenciaPaciente == null && mutating) {
            // Ultima tentativa, no log assincrono (vale para inativar/cancelar, que nao apagam).
            AuditRegistroPaciente.Alvo alvo = AuditRegistroPaciente.localizar(path);
            if (alvo != null) {
                referenciaPaciente = new String[] { AuditPacienteResolver.TIPO_REGISTRO, AuditRegistroPaciente.emTexto(alvo) };
            }
        }
        if (referenciaPaciente != null) {
            dto.setPacienteTipo(referenciaPaciente[0]);
            dto.setPacienteValor(referenciaPaciente[1]);
        }
        logService.salvarLogAsync(dto);
    }

    // O path so traz o numero (ex.: anamneseinternacao/nbam/485014); para o log dizer
    // de qual paciente e o prontuario acessado, pega o segmento numerico que vem logo
    // depois de um marcador conhecido. Rotas sem marcador ficam como antes.
    static String[] extrairReferenciaPaciente(String path) {
        String[] segmentos = String.valueOf(path == null ? "" : path).toLowerCase(Locale.ROOT).split("/");
        for (int i = 0; i < segmentos.length - 1; i++) {
            String tipo = PATH_MARCADORES_PACIENTE.get(segmentos[i]);
            String valor = segmentos[i + 1];
            if (tipo != null && valor.matches("\\d{1,12}")) {
                // Balanco hidrico usa "prontuario/{bam}": o numero ali e BAM.
                if (AuditPacienteResolver.TIPO_PRONTUARIO.equals(tipo)
                        && segmentos[0].equals("backend_balanco_hidrico_completo")) {
                    tipo = AuditPacienteResolver.TIPO_BAM;
                }
                return new String[] { tipo, valor };
            }
        }
        return null;
    }

    // Muitas telas mandam o paciente como parametro (?bam=123, ?idpaciente=45) e nao no path:
    // sem isto o log dizia "alterou dados" sem dizer de quem.
    static String[] extrairReferenciaPacienteDaQuery(java.util.Map<String, java.util.List<String>> query) {
        if (query == null || query.isEmpty()) return null;
        for (java.util.Map.Entry<String, java.util.List<String>> e : query.entrySet()) {
            String tipo = PATH_MARCADORES_PACIENTE.get(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT));
            if (tipo == null || e.getValue() == null) continue;
            for (String valor : e.getValue()) {
                String v = valor == null ? "" : valor.trim();
                if (v.matches("\\d{1,12}")) return new String[] { tipo, v };
            }
        }
        return null;
    }

    private boolean isDuplicadaEmRajada(String matricula, String method, String path, int status) {
        String chave = matricula + "|" + method + "|" + path + "|" + status;
        long agora = System.currentTimeMillis();
        Long ultimo = ultimoEventoLeituraClinica.put(chave, agora);
        if (ultimoEventoLeituraClinica.size() > 5000) {
            ultimoEventoLeituraClinica.clear();
        }
        return ultimo != null && (agora - ultimo) < DEDUP_WINDOW_MS;
    }

    private boolean shouldIgnore(String path) {
        return path.isBlank()
                || path.startsWith("log")
                || path.startsWith("q/")
                || path.startsWith("metrics")
                || path.startsWith("health")
                || path.startsWith("swagger")
                || path.startsWith("openapi")
                || path.startsWith("img/")
                || path.startsWith("favicon")
                || path.equals("api/usuarios-conectados/heartbeat");
    }

    /** Heartbeat is presence traffic, not an auditable user action. */
    static boolean isHeartbeat(String path) {
        String normalized = String.valueOf(path == null ? "" : path)
                .replaceAll("^/+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("/+", "/");
        return normalized.equals("api/usuarios-conectados/heartbeat")
                || normalized.endsWith("/api/usuarios-conectados/heartbeat");
    }

    private boolean isClinicalRead(String path) {
        String compact = path.replace("-", "").replace("_", "").replace("/", "").replace(" ", "");
        return CLINICAL_READ_PATHS.stream()
                .map(item -> item.replace("-", "").replace("_", "").replace("/", "").replace(" ", ""))
                .anyMatch(compact::startsWith);
    }

    private boolean isNoisySuccessfulClinicalRead(String path) {
        return NOISY_SUCCESSFUL_CLINICAL_READ_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix.replaceAll("/+$", "")) || path.startsWith(prefix));
    }

    private boolean isLegacyGetWrite(String path) {
        String compact = path.replace("-", "").replace("_", "").replace("/", "").replace(" ", "");
        return LEGACY_GET_WRITE_TOKENS.stream().anyMatch(compact::contains);
    }

    private boolean isAuditedAction(String path) {
        String compact = path.replace("-", "").replace("_", "").replace("/", "").replace(" ", "");
        return AUDITED_ACTION_TOKENS.stream().anyMatch(compact::contains);
    }

    private String actionDescription(
            String method,
            String path,
            int status,
            boolean mutating,
            boolean legacyGetWrite,
            boolean clinicalRead,
            boolean specialAction) {
        String resultado = status >= 200 && status < 300
                ? "SUCESSO"
                : (status == 401 || status == 403 ? "NEGADO" : "FALHA");
        String compact = path.replace("-", "").replace("_", "").replace("/", "").replace(" ", "");
        String acao;
        if (compact.contains("exportar")) {
            acao = "solicitou exportacao de dados (LGPD)";
        } else if (compact.contains("impressao") || compact.contains("imprimir")) {
            acao = "solicitou impressao";
        } else if (compact.contains("assinatura") || compact.contains("assinar")) {
            acao = "executou assinatura/validacao";
        } else if (compact.contains("cancelar") || "DELETE".equals(method)) {
            acao = "cancelou/inativou registro";
        } else if ("POST".equals(method)) {
            acao = "criou/enviou dados";
        } else if ("PUT".equals(method) || "PATCH".equals(method)) {
            acao = "alterou dados";
        } else if (legacyGetWrite) {
            acao = "alterou dados via API legada GET";
        } else if (clinicalRead) {
            acao = "visualizou prontuario/dado clinico";
        } else if (specialAction) {
            acao = "executou acao auditada";
        } else {
            acao = mutating ? "alterou dados" : "acessou recurso";
        }
        return resultado + " - " + acao;
    }

    private void addLegacyDeprecationHeaders(ContainerResponseContext responseContext) {
        responseContext.getHeaders().putSingle("Deprecation", "true");
        responseContext.getHeaders().putSingle("Warning",
                "299 GHBAM \"Endpoint legado GET com escrita; use o metodo HTTP novo\"");
        responseContext.getHeaders().putSingle("X-GHBAM-Legacy-Write-GET", "true");
    }

    private String normalize(String path) {
        return String.valueOf(path == null ? "" : path).replaceAll("^/+", "").toLowerCase(Locale.ROOT);
    }

    // Em falha, o log so dizia "status=409": sem o motivo nao dava para saber o que houve.
    // Pega a mensagem que o proprio endpoint devolveu ao operador (erro/message/mensagem/detail).
    static String motivoDaResposta(Object entity) {
        if (entity == null) return "";
        if (entity instanceof String s) return s.length() <= 300 ? s.trim() : s.substring(0, 300).trim();
        if (entity instanceof java.util.Map<?, ?> map) {
            for (String chave : new String[] { "erro", "error", "message", "mensagem", "detail", "detalhe", "motivo" }) {
                Object valor = map.get(chave);
                if (valor != null && !String.valueOf(valor).isBlank()) return String.valueOf(valor).trim();
            }
        }
        return "";
    }

    private String buildEvent(String action, String method, String path, int status, AuditUserContext user, String ip,
            String motivo) {
        StringBuilder sb = new StringBuilder();
        sb.append(user.nome()).append(" (matricula ").append(user.matricula()).append(") ");
        sb.append(action).append(": ").append(method).append(" /").append(path);
        sb.append(" | status=").append(status);
        if (motivo != null && !motivo.isBlank()) {
            sb.append(" | motivo=").append(trimTo(motivo.replaceAll("\\s+", " "), 300));
        }
        if (!user.cargo().isBlank()) {
            sb.append(" | cargo=").append(user.cargo());
        }
        if (!ip.isBlank()) {
            sb.append(" | ip=").append(ip);
        }
        if (!user.requestId().isBlank()) {
            sb.append(" | req=").append(user.requestId());
        }
        if (!user.userAgent().isBlank()) {
            sb.append(" | agent=").append(trimTo(user.userAgent(), 180));
        }
        return trimTo(sb.toString(), 2400);
    }

    private String clientIp(ContainerRequestContext requestContext) {
        String forwarded = requestContext.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = requestContext.getHeaderString("X-Real-IP");
        return realIp == null ? "" : realIp.trim();
    }

    private String trimTo(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}

package br.com.iv.usuariologado;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Recursos relacionados ao usuário logado na sessão atual.
 *
 * <p>Fluxo de funcionamento em produção (HTTPS/Nginx):
 * <ol>
 *   <li>O Electron/server.js na máquina cliente chama {@code GET /info} localmente</li>
 *   <li>Ao responder, server.js faz {@code POST /servidor/api/usuario-logado/info-quiosque}
 *       enviando usuario, hostname, ip e timestamp ao servidor</li>
 *   <li>Este backend armazena os dados em cache indexados pelo IP de origem</li>
 *   <li>Quando o frontend chama {@code GET /info-quiosque}, os dados em cache são retornados</li>
 * </ol>
 *
 * <p>Em desenvolvimento local (sem proxy), o GET ainda tenta conexão direta à porta 4567.
 */
@Path("/api/usuario-logado")
@Produces(MediaType.APPLICATION_JSON)
public class UsuarioLogadoResource {

    private static final Logger LOG = Logger.getLogger(UsuarioLogadoResource.class.getName());

    /** Porta em que o server.js (backend local do quiosque) escuta. */
    private static final int QUIOSQUE_PORT = 4567;

    /**
     * Tempo máximo (em segundos) sem receber POST do server.js antes de expirar a entrada.
     * Se o backend do cliente parar de enviar heartbeats, a entrada é removida após este intervalo.
     */
    private static final long TTL_SEGUNDOS = 60;

    /** Representa uma entrada no cache com o corpo recebido e o instante da última atualização. */
    private record CacheEntry(String corpo, Instant ultimaAtualizacao) {
        boolean expirado() {
            return Instant.now().isAfter(ultimaAtualizacao.plusSeconds(TTL_SEGUNDOS));
        }
    }

    /**
     * Cache: IP do cliente → última entrada recebida via POST pelo server.js.
     * A entrada expira automaticamente após {@value #TTL_SEGUNDOS}s sem receber novo POST.
     */
    private static final ConcurrentHashMap<String, CacheEntry> CACHE_QUIOSQUE = new ConcurrentHashMap<>();

    /** Limpeza periódica de entradas expiradas (executa a cada 30 s). */
    private static final ScheduledExecutorService LIMPADOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cache-quiosque-cleanup");
        t.setDaemon(true);
        return t;
    });

    static {
        LIMPADOR.scheduleAtFixedRate(() -> {
            int antes = CACHE_QUIOSQUE.size();
            CACHE_QUIOSQUE.entrySet().removeIf(e -> e.getValue().expirado());
            int removidos = antes - CACHE_QUIOSQUE.size();
            if (removidos > 0) {
                LOG.log(Level.INFO, "Limpeza do cache: {0} entrada(s) expirada(s) removida(s).", removidos);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private static final Client HTTP_CLIENT = ClientBuilder.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    // ------------------------------------------------------------------ //
    //  POST — recebe push do server.js (config.json → backendUrl)         //
    // ------------------------------------------------------------------ //

    /**
     * Recebe os dados enviados pelo server.js da máquina cliente (fire-and-forget do sendToBackend).
     * Armazena em cache indexado pelo IP de origem para ser consultado pelo GET.
     *
     * <p>O {@code config.json} do Electron deve ter:
     * <pre>
     * {
     *   "backendUrl": "https://ghbam-iv.itaguai.rj.gov.br/servidor/api/usuario-logado/info-quiosque"
     * }
     * </pre>
     */
    @POST
    @Path("/info-quiosque")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response receberInfoQuiosque(String corpo, @Context RoutingContext context) {
        String clientIP = obterIPCliente(context);
        CACHE_QUIOSQUE.put(clientIP, new CacheEntry(corpo, Instant.now()));
        LOG.log(Level.INFO, "Dados do quiosque recebidos e armazenados para IP [{0}]: {1}",
                new Object[]{clientIP, corpo});
        return Response.ok("{\"ok\":true}", MediaType.APPLICATION_JSON).build();
    }

    // ------------------------------------------------------------------ //
    //  GET — retorna dados do cache ou tenta conexão direta (dev local)   //
    // ------------------------------------------------------------------ //

    /**
     * Retorna as informações do quiosque para o IP do cliente que fez a requisição.
     *
     * <p>Estratégia:
     * <ol>
     *   <li>Verifica o cache populado pelo POST acima (funciona em produção)</li>
     *   <li>Se não houver cache, tenta conexão direta à porta 4567 (dev local)</li>
     * </ol>
     */
    @GET
    @Path("/info-quiosque")
    public Response infoQuiosque(@Context RoutingContext context) {
        String clientIP = obterIPCliente(context);

        // 1. Tenta retornar do cache (push recebido pelo server.js via POST)
        CacheEntry entry = CACHE_QUIOSQUE.get(clientIP);
        if (entry != null) {
            if (entry.expirado()) {
                CACHE_QUIOSQUE.remove(clientIP);
                LOG.log(Level.INFO, "Cache expirado para [{0}]: backend do cliente considerado offline.", clientIP);
            } else {
                LOG.log(Level.INFO, "Retornando dados do cache para [{0}]: {1}", new Object[]{clientIP, entry.corpo()});
                return Response.ok(entry.corpo(), MediaType.APPLICATION_JSON).build();
            }
        }

        // 2. Fallback: conexão direta (funciona em dev local sem proxy)
        LOG.log(Level.INFO, "Cache vazio para [{0}], tentando conexão direta em :{1}/info",
                new Object[]{clientIP, QUIOSQUE_PORT});

        try {
            Response quiosqueResp = HTTP_CLIENT
                    .target("http://" + clientIP + ":" + QUIOSQUE_PORT + "/info")
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            String corpo = quiosqueResp.readEntity(String.class);
            int status = quiosqueResp.getStatus();
            quiosqueResp.close();

            if (status == 200) {
                LOG.log(Level.INFO, "Conexão direta bem-sucedida [{0}]: {1}", new Object[]{clientIP, corpo});
                CACHE_QUIOSQUE.put(clientIP, new CacheEntry(corpo, Instant.now())); // armazena para próximas chamadas
                return Response.ok(corpo, MediaType.APPLICATION_JSON).build();
            }

            LOG.log(Level.WARNING, "Quiosque [{0}] retornou HTTP {1}", new Object[]{clientIP, status});
            return Response.status(status).entity(corpo).type(MediaType.APPLICATION_JSON).build();

        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Conexão direta ao quiosque [{0}] falhou (esperado em produção): {1}",
                    new Object[]{clientIP, e.getMessage()});
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("{\"erro\":\"Dados do quiosque ainda não recebidos para o IP "
                            + clientIP
                            + ". Aguarde o server.js enviar os dados ou verifique o backendUrl no config.json.\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    // ------------------------------------------------------------------ //
    //  Helper                                                             //
    // ------------------------------------------------------------------ //

    private String obterIPCliente(RoutingContext context) {
        String xff = context.request().getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String ip = xff.split(",")[0].trim();
            if (!ip.isEmpty()) {
                LOG.log(Level.INFO, "IP obtido via X-Forwarded-For: {0}", ip);
                return ip;
            }
        }

        String realIp = context.request().getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            LOG.log(Level.INFO, "IP obtido via X-Real-IP: {0}", realIp);
            return realIp.trim();
        }

        String ip = context.request().remoteAddress().host();
        if (ip == null || ip.isBlank()) {
            throw new WebApplicationException(
                    "Não foi possível identificar o IP do cliente",
                    Response.Status.BAD_REQUEST);
        }
        LOG.log(Level.WARNING, "IP obtido via remoteAddress (proxy nao detectado): {0}", ip);
        return ip;
    }
}

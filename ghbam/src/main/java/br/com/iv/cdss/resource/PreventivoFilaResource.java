package br.com.iv.cdss.resource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import br.com.iv.cdss.service.PreventivoService;
import br.com.iv.dto.LogDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.LogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("cdss/preventivo/fila")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PreventivoFilaResource {
    @Inject PreventivoService service;
    @Inject LogService logService;

    @GET
    public Map<String, Object> listar(@QueryParam("limite") int limite, @QueryParam("busca") String busca, @Context ContainerRequestContext context) {
        AuthenticatedUser user = PreventivoResource.autorizar(context, false);
        Map<String, Object> result = service.listarFila(user, limite, busca);
        auditar(user, limite, busca);
        return result;
    }

    // A lista sem busca e reaberta/atualizada o tempo todo e nao mostra um paciente
    // especifico: grava no maximo uma linha a cada 10 min por profissional. Busca
    // informada (procura por paciente) continua sempre auditada.
    private static final long JANELA_LOG_LISTA_MS = 10 * 60 * 1000L;
    private final ConcurrentHashMap<String, Long> ultimoLogLista = new ConcurrentHashMap<>();

    boolean deveAuditarLista(String chaveUsuario, boolean buscaInformada, long agora) {
        if (buscaInformada) return true;
        Long ultimo = ultimoLogLista.get(chaveUsuario);
        if (ultimo != null && agora - ultimo < JANELA_LOG_LISTA_MS) return false;
        if (ultimoLogLista.size() > 5000) ultimoLogLista.clear();
        ultimoLogLista.put(chaveUsuario, agora);
        return true;
    }

    private void auditar(AuthenticatedUser user, int limite, String busca) {
        boolean buscaInformada = busca != null && !busca.isBlank();
        if (!deveAuditarLista(user.matricula() + "|" + user.unidade(), buscaInformada, System.currentTimeMillis())) {
            return;
        }
        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalTime.now().toString().substring(0, 5));
        log.setUnidade(user.unidade());
        log.setEvento("CDSS_PREVENTIVO FILA limite=" + limite + " busca=" + (busca == null || busca.isBlank() ? "-" : "informada") + " profissional=" + user.matricula());
        logService.salvarLogAsync(log);
    }
}

package br.com.iv.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.ModuloRequestFilter;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Módulos contratados por unidade (docs/modulos-contratacao.md no frontend).
 * Sem linha na tabela = habilitado. NUCLEO nunca desliga. Dependências são conferidas ao salvar.
 */
@Path("unidade-modulos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UnidadeModuloController {

    /** Código -> módulos dos quais depende (precisam estar ligados). Manter igual a modulosSistema.js. */
    static final Map<String, List<String>> CATALOGO = new LinkedHashMap<>();
    static {
        CATALOGO.put("NUCLEO", List.of());
        CATALOGO.put("PA", List.of());
        CATALOGO.put("INTERNACAO", List.of("PA"));
        CATALOGO.put("MULTI", List.of("INTERNACAO"));
        CATALOGO.put("CIRURGIA", List.of("INTERNACAO"));
        CATALOGO.put("CME", List.of("CIRURGIA"));
        CATALOGO.put("APOIO", List.of("INTERNACAO"));
        CATALOGO.put("LAB", List.of("PA"));
        CATALOGO.put("IMAGEM", List.of("PA"));
        CATALOGO.put("FARMACIA", List.of());
        CATALOGO.put("ALMOX", List.of());
        CATALOGO.put("FATURAMENTO", List.of("PA"));
        CATALOGO.put("FATURAMENTO_SUS", List.of("PA"));
        CATALOGO.put("CONSULTORIO", List.of());
        CATALOGO.put("ODONTO", List.of());
        CATALOGO.put("APS", List.of());
        CATALOGO.put("VIGILANCIA", List.of());
        CATALOGO.put("QUALIDADE", List.of());
        CATALOGO.put("PROTOCOLOS", List.of());
        CATALOGO.put("RH", List.of());
        CATALOGO.put("GED", List.of());
        CATALOGO.put("BI", List.of());
        CATALOGO.put("IA", List.of());
        CATALOGO.put("INTEROP", List.of());
        CATALOGO.put("CHAT", List.of());
        CATALOGO.put("PAINEL_MIDIA", List.of("PA"));
        CATALOGO.put("PORTAL", List.of());
        CATALOGO.put("BIOMETRIA", List.of());
    }

    private static final Set<String> QUEM_ALTERA = Set.of("pcadunidades", "kadm");

    /** Sempre ligados, ninguém desliga (núcleo + exigências legais). Igual a `fixo: true` em modulosCatalogo.js. */
    public static final Set<String> FIXOS = Set.of("NUCLEO", "VIGILANCIA", "QUALIDADE", "INTEROP");

    @Inject EntityManager em;
    @Inject SecurityPermissionService securityPermissionService;
    @Context ContainerRequestContext requestContext;

    public static class ModulosDto {
        public Map<String, Boolean> modulos;
    }

    @GET
    @Path("/{sigla}")
    public Response buscar(@PathParam("sigla") String sigla) {
        String chave = normalizarSigla(sigla);
        if (chave.isEmpty()) return erro(Response.Status.BAD_REQUEST, "Informe a sigla da unidade.");
        return Response.ok(Map.of("sigla", chave, "modulos", estado(chave))).build();
    }

    @PUT
    @Path("/{sigla}")
    @Transactional
    public Response salvar(@PathParam("sigla") String sigla, ModulosDto dto) {
        AuthenticatedUser usuario = securityPermissionService.resolve(requestContext);
        if (usuario == null || !usuario.hasAnyPermission(QUEM_ALTERA)) {
            return erro(Response.Status.FORBIDDEN, "Somente quem cadastra unidades pode alterar os módulos.");
        }
        String chave = normalizarSigla(sigla);
        if (chave.isEmpty()) return erro(Response.Status.BAD_REQUEST, "Informe a sigla da unidade.");
        if (dto == null || dto.modulos == null) return erro(Response.Status.BAD_REQUEST, "Nenhum módulo enviado.");

        Map<String, Boolean> novo = estado(chave);
        for (Map.Entry<String, Boolean> item : dto.modulos.entrySet()) {
            String codigo = String.valueOf(item.getKey()).trim().toUpperCase();
            if (!CATALOGO.containsKey(codigo)) return erro(Response.Status.BAD_REQUEST, "Módulo desconhecido: " + codigo);
            novo.put(codigo, !Boolean.FALSE.equals(item.getValue()));
        }
        FIXOS.forEach(codigo -> novo.put(codigo, true));
        for (Map.Entry<String, Boolean> item : novo.entrySet()) {
            if (!item.getValue()) continue;
            for (String dep : CATALOGO.get(item.getKey())) {
                if (!novo.get(dep)) {
                    return erro(Response.Status.BAD_REQUEST,
                            "O módulo " + item.getKey() + " depende de " + dep + ". Ligue " + dep + " ou desligue " + item.getKey() + ".");
                }
            }
        }

        String matricula = usuario.matricula();
        for (Map.Entry<String, Boolean> item : novo.entrySet()) {
            em.createNativeQuery("""
                    INSERT INTO unidade_modulo (sigla, modulo, ativo, alterado_por, alterado_em)
                    VALUES (?1, ?2, ?3, ?4, NOW())
                    ON CONFLICT (sigla, modulo) DO UPDATE
                       SET ativo = EXCLUDED.ativo, alterado_por = EXCLUDED.alterado_por, alterado_em = NOW()
                     WHERE unidade_modulo.ativo IS DISTINCT FROM EXCLUDED.ativo
                    """)
                    .setParameter(1, chave)
                    .setParameter(2, item.getKey())
                    .setParameter(3, item.getValue())
                    .setParameter(4, matricula)
                    .executeUpdate();
        }
        ModuloRequestFilter.invalidar(chave);
        return Response.ok(Map.of("sigla", chave, "modulos", novo)).build();
    }

    /** Estado completo: todos os códigos do catálogo, ligados quando não há linha gravada. */
    private Map<String, Boolean> estado(String sigla) {
        Map<String, Boolean> mapa = new LinkedHashMap<>();
        CATALOGO.keySet().forEach(codigo -> mapa.put(codigo, true));
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("SELECT modulo, ativo FROM unidade_modulo WHERE sigla = ?1")
                .setParameter(1, sigla)
                .getResultList();
        for (Object[] linha : linhas) {
            String codigo = String.valueOf(linha[0]);
            if (mapa.containsKey(codigo) && !FIXOS.contains(codigo)) mapa.put(codigo, Boolean.TRUE.equals(linha[1]));
        }
        return mapa;
    }

    private static String normalizarSigla(String sigla) {
        String valor = sigla == null ? "" : sigla.split(":")[0].trim().toUpperCase();
        return valor.length() > 30 ? "" : valor;
    }

    private static Response erro(Response.Status status, String mensagem) {
        return Response.status(status).entity(Map.of("mensagem", mensagem)).build();
    }
}

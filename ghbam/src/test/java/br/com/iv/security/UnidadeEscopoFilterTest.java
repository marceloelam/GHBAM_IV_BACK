package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;

class UnidadeEscopoFilterTest {

    @Test
    void mesmaUnidadeDaSessaoNaoRegistra() {
        assertNull(UnidadeEscopoFilter.avaliar("HPS", "hps"));
        assertNull(UnidadeEscopoFilter.avaliar("HPS:Hospital", " HPS "));
    }

    @Test
    void unidadeVaziaOuDiferenteRegistra() {
        assertEquals("vazia", UnidadeEscopoFilter.avaliar("HPS", ""));
        assertEquals("vazia", UnidadeEscopoFilter.avaliar("HPS", null));
        assertEquals("divergente", UnidadeEscopoFilter.avaliar("HPS", "UPA1"));
        assertEquals("todas", UnidadeEscopoFilter.avaliar("HPS", "TODAS"));
        assertEquals("divergente", UnidadeEscopoFilter.avaliar("", "HPS"));
    }

    @Test
    void bloqueiaUnidadeDivergenteParaUsuarioComum() throws Exception {
        assertEquals("bloqueada", executar(usuario(Map.of("kenfermagem", true)), "UPA1", true));
        // Fora do historico do paciente, TODAS tambem e' outra unidade.
        assertEquals("bloqueada", executar(usuario(Map.of("kenfermagem", true)), "TODAS", true));
    }

    @Test
    void liberaMesmaUnidadeVaziaPerfilMultiunidadeEModoObservacao() throws Exception {
        AuthenticatedUser comum = usuario(Map.of("kenfermagem", true));
        assertNull(executar(comum, "hps", true));
        assertNull(executar(comum, "", true));
        assertNull(executar(usuario(Map.of("pnir", true)), "UPA1", true));
        assertNull(executar(usuario(Map.of("kadm", true)), "UPA1", true));
        assertNull(executar(comum, "UPA1", false));
    }

    private static AuthenticatedUser usuario(Map<String, Boolean> permissoes) {
        return new AuthenticatedUser("1", "123", "Teste", "ENFERMEIRO", "HPS", "", "", "", "", "", "", "",
                false, permissoes);
    }

    /** "bloqueada" quando o filtro decidiu abortar, ou null se a requisicao seguiu. */
    private static String executar(AuthenticatedUser user, String unidadePedida, boolean bloquear) throws Exception {
        MultivaluedHashMap<String, String> path = new MultivaluedHashMap<>();
        path.add("unidade", unidadePedida);
        UriInfo uriInfo = (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(),
                new Class<?>[] { UriInfo.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getPathParameters" -> path;
                    case "getQueryParameters" -> new MultivaluedHashMap<String, String>();
                    case "getPath" -> "teste/unidade";
                    default -> null;
                });
        AtomicReference<String> decisao = new AtomicReference<>();
        ContainerRequestContext ctx = (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(), new Class<?>[] { ContainerRequestContext.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getProperty" -> "ghbam.user".equals(args[0]) ? user : null;
                    case "getUriInfo" -> uriInfo;
                    case "getMethod" -> "GET";
                    case "setProperty" -> {
                        if ("ghbam.unidade.decision".equals(args[0])) {
                            decisao.set((String) args[1]);
                        }
                        yield null;
                    }
                    default -> null;
                });

        UnidadeEscopoFilter filtro = new UnidadeEscopoFilter();
        filtro.shadow = true;
        filtro.bloquear = bloquear;
        filtro.securityPermissionService = new SecurityPermissionService();
        filtro.filter(ctx);
        return decisao.get();
    }
}

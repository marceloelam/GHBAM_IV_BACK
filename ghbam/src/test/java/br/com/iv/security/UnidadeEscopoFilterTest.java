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

    @Test
    void parametroLegadoYunidadeTambemEAvaliado() throws Exception {
        // bam/unidade/{yunidade}/inicio/.../fim/... listava BAMs de qualquer unidade.
        AuthenticatedUser comum = usuario(Map.of("kenfermagem", true));
        assertEquals("bloqueada", executar(comum, "yunidade", "UPA1", true));
        assertNull(executar(comum, "yunidade", "HPS", true));
        assertNull(executar(usuario(Map.of("kfaturamento", true)), "yunidade", "UPA1", true));
    }

    @Test
    void consultarBamDeOutraUnidadeELiberadoParaOHistorico() throws Exception {
        AuthenticatedUser comum = usuario(Map.of("kenfermagem", true));
        assertNull(executarBam(comum, "idbam", "10", true, "GET", "xlistarPedidos"));
        assertNull(executarBam(comum, "bam", "10", true, "GET", "filtrarBamPedidoMedico2"));
    }

    @Test
    void alterarBamDeOutraUnidadeBloqueiaQuandoLigado() throws Exception {
        AuthenticatedUser comum = usuario(Map.of("kenfermagem", true));
        // Chave desligada: so loga, a requisicao segue.
        assertNull(executarBam(comum, "bam", "10", false, "POST", "salvar"));
        // Ligada: escrita por metodo HTTP ou por GET legado com nome de escrita bloqueia.
        assertEquals("bloqueada", executarBam(comum, "bam", "10", true, "POST", "executarItem"));
        assertEquals("bloqueada", executarBam(comum, "bam", "10", true, "PATCH", "x"));
        assertEquals("bloqueada", executarBam(comum, "idbam", "10", true, "DELETE", "remover"));
        assertEquals("bloqueada", executarBam(comum, "bam", "10", true, "GET", "alterarColetaPs"));
        assertEquals("bloqueada", executarBam(comum, "bam", "10", true, "GET", "corrigirbam"));
        // Propria unidade, inexistente, invalido e perfil multiunidade passam.
        assertNull(executarBam(comum, "bam", "20", true, "POST", "salvar"));
        assertNull(executarBam(comum, "bam", "99", true, "POST", "salvar"));
        assertNull(executarBam(comum, "bam", "abc", true, "POST", "salvar"));
        assertNull(executarBam(usuario(Map.of("pnir", true)), "bam", "10", true, "POST", "salvar"));
        // Falha na consulta nao derruba nem bloqueia.
        assertNull(executarBam(comum, "bam", "666", true, "POST", "salvar"));
    }

    @Test
    void gravarPeloIdDoRegistroDeOutraUnidadeBloqueia() throws Exception {
        AuthenticatedUser comum = usuario(Map.of("kenfermagem", true));
        // Registro 55 pertence ao BAM 10 (UPA1); registro 77 ao BAM 20 (HPS); 88 so tem paciente.
        assertEquals("bloqueada", executarRegistro(comum, "55", "POST"));
        assertNull(executarRegistro(comum, "77", "PUT"));
        assertNull(executarRegistro(comum, "88", "PUT"));
        // Consulta pelo id do registro continua liberada (nem consulta o BAM).
        assertNull(executarRegistro(comum, "55", "GET"));
        assertNull(executarRegistro(usuario(Map.of("pnir", true)), "55", "POST"));
    }

    @Test
    void rotaDeExecutarExameEReconhecidaComoRegistro() {
        AuditRegistroPaciente.Alvo alvo = AuditRegistroPaciente.localizar("pedidoMedico2/55/executar");
        assertEquals("PedidoMedico2", alvo.entidade().getSimpleName());
        assertEquals("55", alvo.id());
        assertNull(AuditRegistroPaciente.localizar("pedidoMedico2/bam/55"));
    }

    private static String executarRegistro(AuthenticatedUser user, String idRegistro, String metodoHttp)
            throws Exception {
        UnidadeEscopoFilter filtro = new UnidadeEscopoFilter() {
            @Override
            String unidadeDoBam(int id) {
                return id == 10 ? "UPA1" : id == 20 ? "HPS" : "";
            }

            @Override
            Integer bamDoRegistro(String path) {
                return path.endsWith("/55") ? Integer.valueOf(10) : path.endsWith("/77") ? Integer.valueOf(20) : null;
            }

            @Override
            boolean ehEscrita(ContainerRequestContext ctx) {
                return !"GET".equals(metodoHttp);
            }
        };
        filtro.bloquearBam = true;
        return executar(filtro, user, "id", idRegistro, true, "registro/" + idRegistro);
    }

    @SuppressWarnings("unused")
    static class RecursoFalso {
        public void alterarColetaPs() { }
        public void xlistarPedidos() { }
        public void inativar() { }
        public void listar(@jakarta.ws.rs.QueryParam("unidade") String unidade) { }
        @UnidadePadraoSessao
        public void listarComSessao(@jakarta.ws.rs.QueryParam("unidade") String unidade) { }
    }

    // Mesmo nome simples do recurso real: vazio la usa a unidade do token.
    @SuppressWarnings("unused")
    static class VigilanciaEpidemiologicaController {
        public void mapa(@jakarta.ws.rs.QueryParam("unidade") String unidade) { }
    }

    @Test
    void unidadeVaziaSoBloqueiaComAChaveLigada() throws Exception {
        AuthenticatedUser comum = usuario(Map.of("kenfermagem", true));
        UnidadeEscopoFilter ligado = new UnidadeEscopoFilter();
        ligado.bloquearVazia = true;
        assertEquals("bloqueada", executar(ligado, comum, "unidade", "", true));
        UnidadeEscopoFilter multi = new UnidadeEscopoFilter();
        multi.bloquearVazia = true;
        assertNull(executar(multi, usuario(Map.of("kfaturamento", true)), "unidade", "", true));
        assertNull(executar(comum, "", true)); // desligada: so registra
    }

    @Test
    void unidadeAusenteNoMetodoQueADeclaraBloqueiaComAChaveLigada() throws Exception {
        AuthenticatedUser comum = usuario(Map.of("kenfermagem", true));
        assertEquals("bloqueada", executarAusente(comum, RecursoFalso.class, "listar", true));
        assertNull(executarAusente(comum, RecursoFalso.class, "listar", false));
        // Metodo sem parametro de unidade: nada a avaliar.
        assertNull(executarAusente(comum, RecursoFalso.class, "xlistarPedidos", true));
        // Vigilancia usa a unidade do token quando vem vazia.
        assertNull(executarAusente(comum, VigilanciaEpidemiologicaController.class, "mapa", true));
        // Metodo que completa com a sessao (resumo de alta, documentos, APS).
        assertNull(executarAusente(comum, RecursoFalso.class, "listarComSessao", true));
    }

    @Test
    void sessaoSemUnidadeSoBloqueiaComAChaveLigada() throws Exception {
        AuthenticatedUser semUnidade = new AuthenticatedUser("1", "123", "Teste", "ENFERMEIRO", "", "", "", "", "", "",
                "", "", false, Map.of("kenfermagem", true));
        UnidadeEscopoFilter ligado = new UnidadeEscopoFilter();
        ligado.bloquearSemUnidade = true;
        assertEquals("bloqueada", executar(ligado, semUnidade, "id", "1", true));
        assertNull(executar(semUnidade, "id", "1", true)); // desligada: so registra
        // Troca de unidade precisa continuar acessivel.
        UnidadeEscopoFilter auth = new UnidadeEscopoFilter();
        auth.bloquearSemUnidade = true;
        assertNull(executar(auth, semUnidade, "id", "1", true, "auth/trocar-unidade"));
        // Perfil multiunidade nao depende de unidade de sessao.
        UnidadeEscopoFilter multi = new UnidadeEscopoFilter();
        multi.bloquearSemUnidade = true;
        AuthenticatedUser nir = new AuthenticatedUser("1", "123", "Teste", "ENFERMEIRO", "", "", "", "", "", "",
                "", "", false, Map.of("pnir", true));
        assertNull(executar(multi, nir, "id", "1", true));
    }

    /** HPS e UPA1 em Itaguai; OUTRA em Seropedica. */
    private static UnidadeEscopoFilter filtroMunicipios(boolean variosMunicipios) {
        UnidadeEscopoFilter f = new UnidadeEscopoFilter() {
            @Override
            String municipioDaUnidade(String unidade) {
                String u = unidade == null ? "" : unidade.trim().toUpperCase();
                return switch (u) {
                    case "HPS", "UPA1" -> "ITAGUAI";
                    case "OUTRA" -> "SEROPEDICA";
                    default -> "";
                };
            }

            @Override
            boolean variosMunicipios() {
                return variosMunicipios;
            }
        };
        f.bloquearMunicipio = true;
        return f;
    }

    @Test
    void perfilMultiunidadeSoCruzaUnidadesDoProprioMunicipio() throws Exception {
        AuthenticatedUser nir = usuario(Map.of("pnir", true));
        assertNull(executar(filtroMunicipios(true), nir, "unidade", "UPA1", true)); // mesmo municipio
        assertEquals("bloqueada", executar(filtroMunicipios(true), nir, "unidade", "OUTRA", true));
        assertEquals("bloqueada", executar(filtroMunicipios(true), usuario(Map.of("kfaturamento", true)), "unidade",
                "TODAS", true));
        // Um municipio so (hoje): TODAS continua como antes.
        assertNull(executar(filtroMunicipios(false), nir, "unidade", "TODAS", true));
        // Administrador da plataforma ve todos os municipios.
        assertNull(executar(filtroMunicipios(true), usuario(Map.of("superusuario", true)), "unidade", "OUTRA", true));
        // Chave desligada: so registra.
        UnidadeEscopoFilter desligado = filtroMunicipios(true);
        desligado.bloquearMunicipio = false;
        assertNull(executar(desligado, nir, "unidade", "OUTRA", true));
    }

    private static String executarAusente(AuthenticatedUser user, Class<?> recurso, String metodoJava,
            boolean bloquearVazia) throws Exception {
        java.lang.reflect.Method m = java.util.Arrays.stream(recurso.getMethods())
                .filter(x -> x.getName().equals(metodoJava)).findFirst().orElseThrow();
        UnidadeEscopoFilter filtro = new UnidadeEscopoFilter();
        filtro.bloquearVazia = bloquearVazia;
        filtro.resourceInfo = (jakarta.ws.rs.container.ResourceInfo) Proxy.newProxyInstance(
                UnidadeEscopoFilterTest.class.getClassLoader(),
                new Class<?>[] { jakarta.ws.rs.container.ResourceInfo.class },
                (p, method, args) -> switch (method.getName()) {
                    case "getResourceMethod" -> m;
                    case "getResourceClass" -> recurso;
                    default -> null;
                });
        // Path param neutro (nao e' de unidade): so a ausencia do query param conta.
        return executar(filtro, user, "id", "1", true);
    }

    @Test
    void ehEscritaRealPorMetodoHttpOuNomeLegado() throws Exception {
        assertEquals(true, ehEscritaReal("POST", "xlistarPedidos"));
        assertEquals(true, ehEscritaReal("DELETE", "xlistarPedidos"));
        assertEquals(true, ehEscritaReal("GET", "alterarColetaPs"));
        assertEquals(false, ehEscritaReal("GET", "xlistarPedidos"));
        // GET evolucaopaciente/inativar/{id} (legado) tambem altera.
        assertEquals(true, ehEscritaReal("GET", "inativar"));
    }

    private static boolean ehEscritaReal(String metodoHttp, String metodoJava) throws Exception {
        java.lang.reflect.Method m = RecursoFalso.class.getMethod(metodoJava);
        UnidadeEscopoFilter filtro = new UnidadeEscopoFilter();
        filtro.resourceInfo = (jakarta.ws.rs.container.ResourceInfo) Proxy.newProxyInstance(
                UnidadeEscopoFilterTest.class.getClassLoader(),
                new Class<?>[] { jakarta.ws.rs.container.ResourceInfo.class },
                (p, method, args) -> switch (method.getName()) {
                    case "getResourceMethod" -> m;
                    case "getResourceClass" -> RecursoFalso.class;
                    default -> null;
                });
        ContainerRequestContext ctx = (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(), new Class<?>[] { ContainerRequestContext.class },
                (p, method, args) -> "getMethod".equals(method.getName()) ? metodoHttp : null);
        return filtro.ehEscrita(ctx);
    }

    private static String executarBam(AuthenticatedUser user, String parametro, String idbam, boolean bloquearBam,
            String metodoHttp, String metodoJava) throws Exception {
        UnidadeEscopoFilter filtro = new UnidadeEscopoFilter() {
            @Override
            String unidadeDoBam(int id) {
                return switch (id) {
                    case 10 -> "UPA1";
                    case 20 -> "HPS";
                    case 666 -> throw new IllegalStateException("banco fora");
                    default -> "";
                };
            }

            @Override
            boolean ehEscrita(ContainerRequestContext ctx) {
                return !"GET".equals(metodoHttp)
                        || PREFIXOS_ESCRITA.stream().anyMatch(metodoJava.toLowerCase()::startsWith);
            }
        };
        filtro.bloquearBam = bloquearBam;
        return executar(filtro, user, parametro, idbam, true);
    }

    private static AuthenticatedUser usuario(Map<String, Boolean> permissoes) {
        return new AuthenticatedUser("1", "123", "Teste", "ENFERMEIRO", "HPS", "", "", "", "", "", "", "",
                false, permissoes);
    }

    /** "bloqueada" quando o filtro decidiu abortar, ou null se a requisicao seguiu. */
    private static String executar(AuthenticatedUser user, String unidadePedida, boolean bloquear) throws Exception {
        return executar(user, "unidade", unidadePedida, bloquear);
    }

    private static String executar(AuthenticatedUser user, String parametro, String unidadePedida, boolean bloquear)
            throws Exception {
        return executar(new UnidadeEscopoFilter(), user, parametro, unidadePedida, bloquear);
    }

    private static String executar(UnidadeEscopoFilter filtro, AuthenticatedUser user, String parametro,
            String unidadePedida, boolean bloquear) throws Exception {
        return executar(filtro, user, parametro, unidadePedida, bloquear, "teste/unidade");
    }

    private static String executar(UnidadeEscopoFilter filtro, AuthenticatedUser user, String parametro,
            String unidadePedida, boolean bloquear, String caminho) throws Exception {
        MultivaluedHashMap<String, String> path = new MultivaluedHashMap<>();
        path.add(parametro, unidadePedida);
        UriInfo uriInfo = (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(),
                new Class<?>[] { UriInfo.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getPathParameters" -> path;
                    case "getQueryParameters" -> new MultivaluedHashMap<String, String>();
                    case "getPath" -> caminho;
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

        filtro.shadow = true;
        filtro.bloquear = bloquear;
        if (filtro.bloquearBam == null) {
            filtro.bloquearBam = false; // fora do Quarkus nao ha Config para a leitura preguicosa
        }
        if (filtro.bloquearVazia == null) {
            filtro.bloquearVazia = false;
        }
        if (filtro.bloquearSemUnidade == null) {
            filtro.bloquearSemUnidade = false;
        }
        if (filtro.bloquearMunicipio == null) {
            filtro.bloquearMunicipio = false;
        }
        filtro.securityPermissionService = new SecurityPermissionService();
        filtro.filter(ctx);
        return decisao.get();
    }
}

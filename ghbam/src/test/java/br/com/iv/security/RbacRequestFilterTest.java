package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

class RbacRequestFilterTest {

    @Test
    void permiteRecepcaoSomenteNaCriacaoInicialDeAvaliacaoEProntuario() {
        List<String> avaliacao = List.of("ktriagem", "kenfermagem", "kmedico", "kadm");
        List<String> prontuario = List.of("kmedico", "kenfermagem", "kfaturamento", "presumopaciente", "kadm");

        assertTrue(RbacRequestFilter.permissionsForRequest("/avaliacao/", "POST", avaliacao).contains("krecepcao"));
        assertTrue(RbacRequestFilter.permissionsForRequest("/prontuario/", "POST", prontuario).contains("krecepcao"));
        assertTrue(RbacRequestFilter.permissionsForRequest("/avaliacao/controle/abc123", "GET", avaliacao).contains("krecepcao"));
        assertTrue(RbacRequestFilter.permissionsForRequest("/prontuario/cod/abc123", "GET", prontuario).contains("krecepcao"));
        assertFalse(RbacRequestFilter.permissionsForRequest("/avaliacao/123", "PATCH", avaliacao).contains("krecepcao"));
        assertFalse(RbacRequestFilter.permissionsForRequest("/avaliacao/123", "GET", avaliacao).contains("krecepcao"));
        assertFalse(RbacRequestFilter.permissionsForRequest("/prontuario/123", "GET", prontuario).contains("krecepcao"));
    }

    @Test
    void recepcaoPodeExecutarTodoOFluxoDePrimeiroAtendimento() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();

        for (String rota : List.of("registro", "tratamento", "avaliacao", "prontuario")) {
            List<String> permissoes = RbacRequestFilter.permissionsForRequest(
                    rota,
                    "POST",
                    catalog.requiredPermissionsFor(rota).orElseThrow());
            assertTrue(permissoes.contains("krecepcao"), "krecepcao ausente no POST /" + rota);
        }
    }

    @Test
    void triagemPodeConsultarPacientesPorUnidadeESetor() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        String rota = "/pacientes/unidade2/HMSFX/setor/TRIAGEM";

        List<String> permissoes = RbacRequestFilter.permissionsForRequest(
                rota,
                "GET",
                catalog.requiredPermissionsFor(rota).orElseThrow());

        assertTrue(permissoes.contains("ktriagem"),
                "ktriagem deve acessar GET " + rota);
    }

    @Test
    void setoresAssistenciaisPodemEnviarChamadaAoPainel() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> permissoes = catalog.requiredPermissionsFor("/chamada").orElseThrow();

        for (String perfil : List.of(
                "ktriagem",
                "kmedico",
                "kenfermagem",
                "klaboratorio",
                "krx",
                "ktomografia",
                "kmedicacao",
                "kadm")) {
            assertTrue(permissoes.contains(perfil), perfil + " deve acessar POST /chamada");
        }
    }

    @Test
    void perfisClinicosPodemUsarProxyDeIaSemLiberarOutrosPerfis() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();

        for (String rota : List.of(
                "/api/hf/health",
                "/api/hf/models/Qwen/Qwen2.5-1.5B-Instruct")) {
            List<String> permissoes = catalog.requiredPermissionsFor(rota).orElseThrow();

            assertTrue(permissoes.contains("ktriagem"), "ktriagem ausente em " + rota);
            assertTrue(permissoes.contains("kmedico"), "kmedico ausente em " + rota);
            assertTrue(permissoes.contains("kenfermagem"), "kenfermagem ausente em " + rota);
            assertTrue(permissoes.contains("kadm"), "kadm ausente em " + rota);
            assertFalse(permissoes.contains("krecepcao"), "krecepcao nao deve usar IA clinica em " + rota);
        }
    }

    @Test
    void equipeClinicaPodeLerCatalogoSemReceberPermissaoDeAlterarFarmacia() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> permissoesFarmacia = catalog.requiredPermissionsFor("/pharmacia/produtos").orElseThrow();

        List<String> leitura = RbacRequestFilter.permissionsForRequest(
                "/pharmacia/produtos", "GET", permissoesFarmacia);
        List<String> alteracao = RbacRequestFilter.permissionsForRequest(
                "/pharmacia/produtos", "POST", permissoesFarmacia);

        for (String perfil : List.of(
                "kmedico",
                "kenfermagem",
                "ktriagem",
                "kfisioterapia",
                "kassistencia",
                "knutricao",
                "kfonoaudiologia",
                "kpsicologia",
                "pevolucaomultidisciplinar")) {
            assertTrue(leitura.contains(perfil), perfil + " deve consultar o catalogo de medicamentos");
            assertFalse(alteracao.contains(perfil), perfil + " nao deve alterar a farmacia por essa excecao");
        }
    }

    @Test
    void perfisQueProduzemDocumentoClinicoPodemAssinar() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();

        for (String rota : List.of("/assinaturas-clinicas/assinar", "/api/assinaturas-clinicas/assinar")) {
            List<String> permissoes = catalog.requiredPermissionsFor(rota).orElseThrow();

            for (String perfil : List.of(
                    "kmedico",
                    "kconsultorio_medico",
                    "kenfermagem",
                    "ktcenfermagem",
                    "kdentista",
                    "kfisioterapia",
                    "kassistencia",
                    "knutricao",
                    "kfonoaudiologia",
                    "kpsicologia",
                    "pevolucaomultidisciplinar")) {
                assertTrue(permissoes.contains(perfil), perfil + " deve assinar documento clinico em " + rota);
            }
        }
    }
    @Test
    void equipeMultidisciplinarPodeLerPrescricaoSemGanharPermissaoDeAlteracao() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> permissoes = catalog.requiredPermissionsFor("/prescricao-itens/bam/123").orElseThrow();

        List<String> leitura = RbacRequestFilter.permissionsForRequest(
                "/prescricao-itens/bam/123", "GET", permissoes);
        List<String> alteracao = RbacRequestFilter.permissionsForRequest(
                "/prescricao-itens/123", "PUT", permissoes);

        for (String perfil : List.of(
                "kfisioterapia",
                "kassistencia",
                "knutricao",
                "kfonoaudiologia",
                "kpsicologia",
                "pevolucaomultidisciplinar")) {
            assertTrue(leitura.contains(perfil), perfil + " deve consultar a prescricao");
            assertFalse(alteracao.contains(perfil), perfil + " nao deve alterar a prescricao por essa excecao");
        }
    }

    @Test
    void equipeAssistencialPodeLerHistoricoEnfermagemSemGanharPermissaoDeEscrita() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();

        for (String rota : List.of(
                "/evolucaopacienteenf/bam/493258",
                "/anotacaoenfermagem/bam/493258")) {
            List<String> permissoes = catalog.requiredPermissionsFor(rota).orElseThrow();
            List<String> leitura = RbacRequestFilter.permissionsForRequest(rota, "GET", permissoes);
            List<String> alteracao = RbacRequestFilter.permissionsForRequest(rota, "PUT", permissoes);

            for (String perfil : List.of(
                    "kmedico",
                    "kenfermagem",
                    "ktriagem",
                    "kfisioterapia",
                    "kassistencia",
                    "knutricao",
                    "kfonoaudiologia",
                    "kpsicologia",
                    "pevolucaomultidisciplinar",
                    "presumopaciente",
                    "pcomoestaopaciente",
                    "pprontuariocompleto")) {
                assertTrue(leitura.contains(perfil), perfil + " deve visualizar " + rota);
            }

            for (String perfil : List.of(
                    "kmedico",
                    "ktriagem",
                    "presumopaciente",
                    "pcomoestaopaciente",
                    "pprontuariocompleto")) {
                assertFalse(alteracao.contains(perfil),
                        perfil + " nao deve alterar registro de enfermagem por essa excecao");
            }
        }
    }

    @Test
    void triagemNaoPodeAlterarPacientesPelaPermissaoDaFila() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();

        for (String rota : List.of(
                "/pacientes",
                "/pacientes/1",
                "/pacientes/1/fechamento-bam",
                "/pacientes/opcao1/bamdestino/2")) {
            List<String> permissoes = catalog.requiredPermissionsFor(rota).orElseThrow();
            assertFalse(permissoes.contains("ktriagem"),
                    "ktriagem nao deve receber permissao ampla em " + rota);
        }
    }

    @Test
    void imprimirBamPodePesquisarPacienteSemGanharPermissaoDeAlteracao() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> permissoes = catalog.requiredPermissionsFor("/pacientes/MARIA").orElseThrow();

        List<String> leitura = RbacRequestFilter.permissionsForRequest(
                "/pacientes/MARIA", "GET", permissoes);
        List<String> alteracaoIdade = RbacRequestFilter.permissionsForRequest(
                "/pacientes/MARIA/idade/atualizacao", "PATCH", permissoes);
        List<String> alteracaoPaciente = RbacRequestFilter.permissionsForRequest(
                "/pacientes/123", "PUT", permissoes);

        assertTrue(leitura.contains("pimprimirbam"));
        assertFalse(alteracaoIdade.contains("pimprimirbam"));
        assertFalse(alteracaoPaciente.contains("pimprimirbam"));
    }

    @Test
    void tecnicoDeEnfermagemLeBamEPacienteParaReimprimirSemGanharAlteracao() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> doPaciente = catalog.requiredPermissionsFor("/pacientes/MARIA").orElseThrow();
        List<String> doBam = catalog.requiredPermissionsFor("/bam/idpaciente/123").orElseThrow();

        assertTrue(RbacRequestFilter.permissionsForRequest("/pacientes/MARIA", "GET", doPaciente).contains("ktcenfermagem"));
        assertTrue(RbacRequestFilter.permissionsForRequest("/bam/idpaciente/123", "GET", doBam).contains("ktcenfermagem"));
        assertTrue(RbacRequestFilter.permissionsForRequest("/bam/idpaciente/123", "GET", doBam).contains("pimprimirbam"));
        assertFalse(RbacRequestFilter.permissionsForRequest("/pacientes/123", "PUT", doPaciente).contains("ktcenfermagem"));
        assertFalse(RbacRequestFilter.permissionsForRequest("/pacientes/MARIA/idade/atualizacao", "PATCH", doPaciente).contains("ktcenfermagem"));
    }

    @Test
    void triagemPodeAtualizarIdadeDaPropriaFilaSemAcessoAmploAPacientes() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        String rota = "/pacientes/triagem/vila_geny/idade/atualizacao";

        List<String> permissoes = catalog.requiredPermissionsFor(rota).orElseThrow();
        List<String> alteracao = RbacRequestFilter.permissionsForRequest(rota, "PATCH", permissoes);

        assertTrue(alteracao.contains("ktriagem"));
        assertFalse(catalog.requiredPermissionsFor("/pacientes/123").orElseThrow().contains("ktriagem"));
    }

    @Test
    void permissoesGranularesPodemConsultarAsFilasDePacientes() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();

        String aguardando = "/pacientes/aguardando/unidade/HMSFX";
        List<String> permissoesAguardando = RbacRequestFilter.permissionsForRequest(
                aguardando,
                "GET",
                catalog.requiredPermissionsFor(aguardando).orElseThrow());
        assertTrue(permissoesAguardando.contains("paguardandoatendimento"));

        String emAtendimento = "/pacientes/ematendimento/NADA/unidade/HMSFX";
        List<String> permissoesEmAtendimento = RbacRequestFilter.permissionsForRequest(
                emAtendimento,
                "GET",
                catalog.requiredPermissionsFor(emAtendimento).orElseThrow());
        assertTrue(permissoesEmAtendimento.contains("ppacientesematendimento"));

        assertFalse(RbacRequestFilter.permissionsForRequest(
                aguardando,
                "POST",
                catalog.requiredPermissionsFor(aguardando).orElseThrow())
                .contains("paguardandoatendimento"));
    }

    @Test
    void perfisSetoriaisPodemListarSomenteODriveCorrespondenteSemAdministrarCadastro() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        String driveMedico = "/drive/menu/medico/unidade/HMSFX";
        String driveRecepcao = "/drive/menu/recepcao/unidade/HMSFX";
        List<String> catalogadas = catalog.requiredPermissionsFor(driveMedico).orElseThrow();

        List<String> leituraMedico = RbacRequestFilter.permissionsForRequest(driveMedico, "GET", catalogadas);
        List<String> leituraRecepcao = RbacRequestFilter.permissionsForRequest(driveRecepcao, "GET", catalogadas);
        List<String> escrita = RbacRequestFilter.permissionsForRequest("/drive/10", "PUT", catalogadas);

        assertTrue(leituraMedico.contains("kmedico"));
        assertFalse(leituraMedico.contains("krecepcao"));
        assertTrue(leituraRecepcao.contains("krecepcao"));
        assertFalse(leituraRecepcao.contains("kmedico"));
        assertFalse(escrita.contains("kmedico"));
        assertFalse(escrita.contains("krecepcao"));
    }

    @Test
    void recepcaoPodeListarSetoresDaUnidadeSemAdministrarSetores() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        String listagem = "/setores/unidade/HMSFX";
        List<String> permissoesCatalogo = catalog.requiredPermissionsFor(listagem).orElseThrow();

        assertTrue(RbacRequestFilter.permissionsForRequest(
                listagem, "GET", permissoesCatalogo).contains("krecepcao"));
        assertFalse(RbacRequestFilter.permissionsForRequest(
                "/setores", "POST", permissoesCatalogo).contains("krecepcao"));
        assertFalse(RbacRequestFilter.permissionsForRequest(
                "/setores/atualizar/10", "PUT", permissoesCatalogo).contains("krecepcao"));
        assertFalse(RbacRequestFilter.permissionsForRequest(
                "/setores/remover/10", "DELETE", permissoesCatalogo).contains("krecepcao"));
    }

    @Test
    void equipeAssistencialPodeGerarFaturamentoAutomaticoSemAdministrarCatalogos() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> perfisAssistenciais = List.of(
                "krecepcao",
                "ktriagem",
                "kmedico",
                "kenfermagem",
                "klaboratorio",
                "krx",
                "ktomografia",
                "kmedicacao",
                "kconsultorio_medico");

        for (String rota : List.of(
                "/procedimentos/procedimento/0301060061/descricao/ATENDIMENTO",
                "/procedimentospasrv/codigo/030106006",
                "/procedimentossus/procedimentosus/0301060061",
                "/procedimentossus/procedimentosus/0301060061/unidade/HMSFX",
                "/procedimentossus/procedi/CA/cid/A001/cbo/225125")) {
            List<String> permissoesCatalogo = catalog.requiredPermissionsFor(rota).orElseThrow();
            List<String> leitura = RbacRequestFilter.permissionsForRequest(rota, "GET", permissoesCatalogo);
            List<String> alteracao = RbacRequestFilter.permissionsForRequest(rota, "PUT", permissoesCatalogo);

            for (String perfil : perfisAssistenciais) {
                assertTrue(leitura.contains(perfil), perfil + " deve consultar " + rota);
                if (!permissoesCatalogo.contains(perfil)) {
                    assertFalse(alteracao.contains(perfil),
                            perfil + " nao deve ganhar alteracao por esta excecao em " + rota);
                }
            }
        }

        List<String> permissoesAfaturar = catalog.requiredPermissionsFor("/afaturar").orElseThrow();
        List<String> gravacao = RbacRequestFilter.permissionsForRequest(
                "/afaturar", "POST", permissoesAfaturar);
        List<String> alteracao = RbacRequestFilter.permissionsForRequest(
                "/afaturar/10", "PUT", permissoesAfaturar);

        for (String perfil : perfisAssistenciais) {
            assertTrue(gravacao.contains(perfil), perfil + " deve gerar faturamento automatico");
            if (!"krecepcao".equals(perfil)) {
                assertFalse(alteracao.contains(perfil), perfil + " nao deve alterar faturamento existente");
            }
        }
    }

    @Test
    void permiteSomenteAsOperacoesPublicasNecessariasAoPainelKiosk() {
        assertTrue(RbacRequestFilter.isPublicKioskRequest("/chamada/unidade1/HMSFX/unico/1", "GET"));
        assertTrue(RbacRequestFilter.isPublicKioskRequest("/chamada/unidade2/HMSFX/direita/1", "GET"));
        assertTrue(RbacRequestFilter.isPublicKioskRequest("/chamada/unidade3/HMSFX/tv/1", "GET"));
        assertTrue(RbacRequestFilter.isPublicKioskRequest("/chamada/123/tv/1", "PATCH"));
        assertTrue(RbacRequestFilter.isPublicKioskRequest("/localcenso/sigla/HMSFX", "GET"));

        assertFalse(RbacRequestFilter.isPublicKioskRequest("/chamada", "POST"));
        assertFalse(RbacRequestFilter.isPublicKioskRequest("/chamada/unidade/HMSFX/tv/1", "DELETE"));
        assertFalse(RbacRequestFilter.isPublicKioskRequest("/chamada/abc/tv/1", "PATCH"));
        assertFalse(RbacRequestFilter.isPublicKioskRequest("/localcenso/sigla/HMSFX", "POST"));

        assertTrue(RbacRequestFilter.isPublicKioskRequest("/painelVoz/status/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.isPublicKioskRequest("/painelVoz/chamada/HMSFX/1/123", "GET"));
        assertFalse(RbacRequestFilter.isPublicKioskRequest("/painelVoz/chamada/HMSFX/1/abc", "GET"));
        assertFalse(RbacRequestFilter.isPublicKioskRequest("/painelVoz/chamada/HMSFX/1/123", "POST"));
        assertFalse(RbacRequestFilter.isPublicKioskRequest("/painelVoz/texto/qualquer", "GET"));
    }

    private static final Pattern CLASS_PATH_PATTERN = Pattern.compile("@Path\\(\\s*\"([^\"]+)\"\\s*\\)");

    private boolean isPublic(String path) throws Exception {
        Method method = RbacRequestFilter.class.getDeclaredMethod("isPublic", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(new RbacRequestFilter(), path);
    }

    @Test
    void deveManterRbacAtivoPorPadraoNoFiltro() throws Exception {
        Field enforce = RbacRequestFilter.class.getDeclaredField("enforce");
        ConfigProperty config = enforce.getAnnotation(ConfigProperty.class);

        assertEquals("ghbam.security.rbac.enforce", config.name());
        assertEquals("true", config.defaultValue());
    }

    @Test
    void todoControllerDeveTerCoberturaRbacOuSerPublico() throws Exception {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        Path controllersDir = Path.of("src/main/java/br/com/iv/controller");

        List<String> descobertasSemCobertura = Files.walk(controllersDir)
                .filter(path -> path.toString().endsWith("Controller.java"))
                .map(path -> {
                    try {
                        String source = Files.readString(path);
                        Matcher matcher = CLASS_PATH_PATTERN.matcher(source);
                        if (!matcher.find()) {
                            return null;
                        }
                        String route = matcher.group(1)
                                .replaceAll("^/+", "")
                                .replaceAll("/+$", "");
                        // EvidenciaAssistencialController recebe o modulo como parametro
                        // (CME, HEMOTERAPIA, SND ou RNDS). A autorizacao e deliberadamente
                        // resolvida dentro do controlador por modulo, em vez de permitir a
                        // uniao de permissoes para toda a raiz /assistencial no filtro.
                        boolean rbacInternoPorModulo = "assistencial/{modulo}".equals(route)
                                && source.contains("a.hasAnyPermission(permissoes)")
                                && source.contains("permissoesPorModulo.get(chave)");
                        boolean coberta = isPublic(route)
                                || RbacRequestFilter.isPublicReadOnly(route, "GET")
                                || RbacRequestFilter.requiresOnlyAuthenticatedUser(route, "GET")
                                || catalog.requiredPermissionsFor(route).isPresent()
                                || rbacInternoPorModulo;
                        return coberta ? null : path.getFileName() + " -> " + route;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(item -> item != null)
                .toList();

        assertTrue(descobertasSemCobertura.isEmpty(),
                "Controllers sem cobertura RBAC/public/authenticated-only: " + descobertasSemCobertura);
    }

    @Test
    void devePermitirHeartbeatParaQualquerUsuarioAutenticado() {
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser(
                "/api/usuarios-conectados/heartbeat", "POST"));
    }

    @Test
    void devePermitirVerificacaoDeStatusAdParaUsuarioAutenticado() {
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser(
                "/ad/verificar-status/", "POST"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser(
                "/ad/usuariosPorOu", "GET"));
    }

    @Test
    void devePermitirLeituraDoTemaSemLiberarAlteracao() {
        assertTrue(RbacRequestFilter.isPublicReadOnly("/tema-visual", "GET"));
        assertTrue(RbacRequestFilter.isPublicReadOnly("/tema-visual/", "GET"));
        assertFalse(RbacRequestFilter.isPublicReadOnly("/tema-visual", "POST"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/tema-visual", "GET"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/tema-visual", "PUT"));
    }

    @Test
    void devePermitirConsultaDeSalasDaUnidadeParaQualquerUsuarioAutenticado() {
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/sala/unidade/HMSFX/local/M", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/sala/unidade/HMSFX/sala/CONSULTORIO-1", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/sala/unidade/HMSFX", "GET"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/sala", "POST"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/sala/1", "PUT"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/sala/1", "DELETE"));
    }

    @Test
    void devePermitirConsultaDeSetoresDaUnidadeParaQualquerUsuarioAutenticado() {
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setores/unidade/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setores/unidadetriar/HMSFX", "GET"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setores", "POST"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setores/atualizar/1", "PUT"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setores/remover/1", "DELETE"));
    }

    @Test
    void devePermitirConsultaDeProcedimentosPorModoParaQualquerUsuarioAutenticado() {
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/procedimentos/modo/ELETIVO", "GET"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/procedimentos", "POST"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/procedimentos/1", "PUT"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/procedimentos/1", "DELETE"));
    }

    @Test
    void devePermitirConsultaDeMedicosDaUnidadeParaQualquerUsuarioAutenticado() {
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor/medico/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor/medico/DEV-UNITY", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor/especialistas/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor/nome/MARIA/unidade/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor/unidade/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor/idservidor/1", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor/certificado/852852", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor2/medico/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor2/matricula/852852", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor2/nome/MARIA/unidade/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor2/unidade/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor2/idServidor2/1", "GET"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor", "POST"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor/1", "PUT"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor/remover/1", "DELETE"));
    }

    @Test
    void devePermitirConsultasDeCadastroBaseParaQualquerUsuarioAutenticado() {
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/configuracaoUnidade", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/configuracaoUnidade/hospitalDistintos", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/configuracaoUnidade/sigla/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/configuracaoUnidade/hospital/HMSFX", "GET"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/configuracaoUnidade", "POST"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/configuracaoUnidade/atualizar/1", "PUT"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/configuracaoUnidade/remover/1", "DELETE"));
    }

    @Test
    void devePermitirConsultasDeCensoParaQualquerUsuarioAutenticado() {
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/localcenso/sigla/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/localcenso/hospitalDistintos/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/localcenso/localunidade/HMSFX/pontoidsetor/1", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/localcenso/localunidadeobservacao/HMSFX/pontoidsetor/1", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/localcenso/unidadenir/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setorcenso/sigla/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setorcenso/sigla2/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setorcenso/siglaobs/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setorcenso/hospitalDistintos/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setorcenso/hospitalDistintosbylocal/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setorcenso/unidadenir/HMSFX", "GET"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setorcenso/titulo/CLINICA/unidade/HMSFX", "GET"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/localcenso", "POST"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/localcenso/1", "PUT"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setorcenso", "POST"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/setorcenso/1", "PUT"));
    }

    @Test
    void naoDeveLiberarListagemDeUsuariosConectados() {
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/api/usuarios-conectados", "GET"));
    }

    @Test
    void multidisciplinarEEscalasDeEnfermagemPodemListarInternados() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> internadonomenir = catalog.requiredPermissionsFor("/pacientes/internadonomenir/joao/unidade/HMSFX").orElseThrow();
        List<String> novoInternado = catalog.requiredPermissionsFor("/novoInternado/unidadenir/HMSFX/quem/internados").orElseThrow();

        for (String perfil : List.of(
                "kfisioterapia", "kassistencia", "knutricao", "kfonoaudiologia", "kpsicologia",
                "pevolucaomultidisciplinar", "pescalaenfermagem")) {
            assertTrue(internadonomenir.contains(perfil), perfil + " deve listar pacientes/internadonomenir");
            assertTrue(novoInternado.contains(perfil), perfil + " deve listar novoInternado");
        }
    }

    @Test
    void farmaciaNoCicloDeMedicacaoPodeLerNovoInternadoEIdPaciente() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> novoInternado = catalog.requiredPermissionsFor("/novoInternado/bam/507604/unidade/HMSFX").orElseThrow();
        List<String> idPaciente = catalog.requiredPermissionsFor("/pacientes/idpaciente/123").orElseThrow();

        for (String perfil : List.of("kfarmaciacentral", "pciclomedicacao")) {
            assertTrue(novoInternado.contains(perfil), perfil + " deve ler novoInternado (ciclo de medicacao)");
            assertTrue(idPaciente.contains(perfil), perfil + " deve ler pacientes/idpaciente (ciclo de medicacao)");
        }
    }

    @Test
    void kultrassonografiaPodeAcessarPedidosEChamadaDeUltrassonografia() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> pedidos = catalog.requiredPermissionsFor("/pedidomedico2/psunidadepedido/HMSFX/local/ULTRASSONOGRAFIA").orElseThrow();
        List<String> chamada = catalog.requiredPermissionsFor("/chamada/1").orElseThrow();

        assertTrue(pedidos.contains("kultrassonografia"), "kultrassonografia deve acessar pedidomedico2");
        assertTrue(chamada.contains("kultrassonografia"), "kultrassonografia deve acessar chamada");
    }

    @Test
    void restauracaoDePacientesNirTemEntradaPropriaMaisEstreitaQueAGenerica() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> restaurar = catalog.requiredPermissionsFor(
                "/pacientes/nomenirrestaurarinternacao/joao/unidade/HMSFX/tipo/1").orElseThrow();

        assertTrue(restaurar.contains("prestaurarpaciente"), "prestaurarpaciente deve buscar pacientes pra restaurar");
        assertTrue(restaurar.contains("pnirrestaurarinternacao"));
        assertFalse(restaurar.contains("kfaturamento"), "nao deve herdar a lista generica de /pacientes");
    }

    @Test
    void equipeDeEnfermagemPodeLerPrescricaoDoBamSemGanharPermissaoDeAlteracao() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        List<String> permissoes = catalog.requiredPermissionsFor("/prescricao").orElseThrow();

        List<String> leituraSemTipo = RbacRequestFilter.permissionsForRequest(
                "/prescricao/bam/507604/inicio/2026-09-12/fim/2026-09-12", "GET", permissoes);
        List<String> leituraComTipo = RbacRequestFilter.permissionsForRequest(
                "/prescricao/bam/507604/inicio/2026-09-12/fim/2026-09-12/tipo/1", "GET", permissoes);
        List<String> criacao = RbacRequestFilter.permissionsForRequest("/prescricao", "POST", permissoes);
        List<String> inativacao = RbacRequestFilter.permissionsForRequest(
                "/prescricao/123/inativacao", "PATCH", permissoes);

        for (String perfil : List.of("kenfermagem", "ktriagem", "presumopaciente", "pprontuariocompleto")) {
            assertTrue(leituraSemTipo.contains(perfil), perfil + " deve ler prescricao/bam sem tipo");
            assertTrue(leituraComTipo.contains(perfil), perfil + " deve ler prescricao/bam com tipo");
            assertFalse(criacao.contains(perfil), perfil + " nao deve criar prescricao por essa excecao");
            assertFalse(inativacao.contains(perfil), perfil + " nao deve inativar prescricao por essa excecao");
        }
    }

    @Test
    void devePermitirCriarOcorrenciaSistemaParaQualquerUsuarioAutenticadoMasNaoGerenciar() {
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/ocorrencia-sistema", "POST"));
        assertTrue(RbacRequestFilter.requiresOnlyAuthenticatedUser("/servidor/validar-credencial", "POST"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/ocorrencia-sistema", "GET"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/ocorrencia-sistema/1", "PUT"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/ocorrencia-sistema/1/status", "PUT"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/ocorrencia-sistema/1", "DELETE"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/ocorrencia-sistema/alerta-config", "GET"));
        assertFalse(RbacRequestFilter.requiresOnlyAuthenticatedUser("/ocorrencia-sistema/alerta-config", "PUT"));
    }

    @Test
    void deveAutorizarListagemParaQuemPossuiPermissaoDeChat() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();

        assertTrue(catalog.requiredPermissionsFor("/api/usuarios-conectados")
                .orElseThrow()
                .contains("pchatprofissionais"));
    }

    @Test
    void naoDeveLiberarEstatisticasDeAtendimentoSemLogin() throws Exception {
        // Contagens por unidade exigem sessao: publicas, qualquer um contava atendimentos de qualquer unidade.
        assertFalse(isPublic("/avaliacao/unidadever/HOSPITAL/data/2026-07-22"));
        assertFalse(isPublic("/avaliacao/unidadeverhoje/HOSPITAL/data/2026-07-22"));
        assertFalse(isPublic("/avaliacao/unidadeversetorhoje/HOSPITAL/data/2026-07-22"));
        assertFalse(isPublic("/avaliacao/unidadeversetorsemana/HOSPITAL/data/2026-07-22"));
        assertFalse(isPublic("/avaliacao/unidadeversetormes/HOSPITAL/data/2026-07-22"));
        assertFalse(isPublic("/avaliacao/unidadeversetorano/HOSPITAL/data/2026-07-22"));

        assertFalse(isPublic("/avaliacao/avaliado/123/avaliacao/1"));
        assertFalse(isPublic("/avaliacao/123"));
        assertFalse(isPublic("/avaliacao/unidadever/HOSPITAL/data/2026-07-22/extra"));
        assertFalse(isPublic("/avaliacao/unidadever/HOSPITAL/periodo/2026-07-22"));
    }

    @Test
    void deveNormalizarRotasPublicasComCamelCase() throws Exception {
        assertTrue(isPublic("/videoProxy/status"));
        assertTrue(isPublic("/unidadeVideoConfig/HMSFX"));
        assertTrue(isPublic("/unidadeVideos/unidade/HMSFX"));
    }
}



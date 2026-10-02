package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.Servidor;
import br.com.iv.entity.Servidor2;
import br.com.iv.repository.Servidor2Repository;

class SecurityPermissionServiceTest {

    @Test
    void devePreservarPermissoesOperacionaisCadastradasNoServidor2() {
        Servidor servidor = new Servidor();
        servidor.setMatricula("852852");
        servidor.setNome("Profissional");
        servidor.setPwd("senha-nao-padrao");

        Servidor2 servidor2 = new Servidor2();
        servidor2.setMatricula("852852");
        servidor2.setCaddrives(true);
        servidor2.setAnotacoesenfermagem(true);
        servidor2.setMonitoramento(true);
        servidor2.setControleplantoes(true);
        servidor2.setProdutividaderh(true);
        servidor2.setTreinamentocertificacao(true);
        servidor2.setGerenciarcertificados(true);
        servidor2.setProducaomedica(true);
        servidor2.setGed(true);
        servidor2.setGedconsulta(true);
        servidor2.setGedremover(true);
        servidor2.setCautela(true);

        SecurityPermissionService service = new SecurityPermissionService();
        service.servidor2Repository = new Servidor2Repository() {
            @Override
            public Servidor2 localizarMatricula(String matricula) {
                return servidor2;
            }
        };

        AuthenticatedUser usuario = service.fromServidor(servidor, false);

        for (String permissao : new String[] {
                "pcaddrives",
                "panotacoesenfermagem",
                "pmonitoramento",
                "pcontroleplantoes",
                "pprodutividaderh",
                "ptreinamentocertificacao",
                "pgerenciarcertificados",
                "pproducaomedica",
                "pged",
                "pgedconsulta",
                "pgedremover",
                "pcautela"
        }) {
            assertTrue(usuario.hasPermission(permissao),
                    () -> "Permissao cadastrada em servidor2 foi perdida no RBAC: " + permissao);
        }
    }

    @Test
    void producaoMedicaDeveExigirPermissaoAdministrativaOuAdministrador() {
        assertEquals(
                Set.of("pproducaomedica", "kadm"),
                Set.copyOf(new RoutePermissionCatalog()
                        .requiredPermissionsFor("/producao-medica/relatorio")
                        .orElseThrow()));
    }

    @Test
    void dentistaDeveReceberPermissaoExclusivaDoModuloOdontologico() {
        Servidor servidor = new Servidor();
        servidor.setMatricula("CRO123");
        servidor.setNome("Cirurgiao dentista");
        servidor.setPwd("senha-nao-padrao");
        servidor.setDentista(true);

        SecurityPermissionService service = new SecurityPermissionService();
        service.servidor2Repository = new Servidor2Repository() {
            @Override
            public Servidor2 localizarMatricula(String matricula) {
                return null;
            }
        };

        assertTrue(service.fromServidor(servidor, false).hasPermission("kdentista"));
    }

    @Test
    void ultrassonografiaDeveSerIncluidaNasPermissoesDoLogin() {
        Servidor servidor = new Servidor();
        servidor.setMatricula("852852");
        servidor.setNome("Profissional");
        servidor.setPwd("senha-nao-padrao");
        servidor.setUltrassonografia(true);

        SecurityPermissionService service = new SecurityPermissionService();
        service.servidor2Repository = new Servidor2Repository() {
            @Override
            public Servidor2 localizarMatricula(String matricula) {
                return null;
            }
        };

        assertTrue(service.fromServidor(servidor, false).hasPermission("kultrassonografia"));
    }

    @Test
    void apiOdontologiaDeveEstarMapeadaNoCatalogoRbac() {
        assertEquals(
                Set.of("kdentista"),
                Set.copyOf(new RoutePermissionCatalog()
                        .requiredPermissionsFor("/api/odontologia/paciente/28433")
                        .orElseThrow()));
    }

    @Test
    void catalogoRbacNaoPodeUsarPermissaoAusenteDoResolvedor() throws IllegalAccessException {
        Servidor servidor = new Servidor();
        Servidor2 servidor2 = new Servidor2();
        habilitarTodosOsBooleanos(servidor);
        habilitarTodosOsBooleanos(servidor2);
        servidor.setMatricula("852852");
        servidor.setNome("Profissional");
        servidor.setPwd("senha-nao-padrao");
        servidor2.setMatricula("852852");

        SecurityPermissionService service = new SecurityPermissionService();
        service.servidor2Repository = new Servidor2Repository() {
            @Override
            public Servidor2 localizarMatricula(String matricula) {
                return servidor2;
            }
        };

        Set<String> permissoesResolvidas = service.fromServidor(servidor, false)
                .permissions()
                .keySet();
        Set<String> permissoesDoCatalogo = new RoutePermissionCatalog().all()
                .values()
                .stream()
                .flatMap(java.util.Collection::stream)
                .collect(Collectors.toSet());

        assertTrue(permissoesResolvidas.containsAll(permissoesDoCatalogo),
                () -> "Catalogo RBAC usa permissoes que o login nao resolve: "
                        + diferenca(permissoesDoCatalogo, permissoesResolvidas));
    }

    private static void habilitarTodosOsBooleanos(Object alvo) throws IllegalAccessException {
        for (Field field : alvo.getClass().getDeclaredFields()) {
            if (field.getType() == Boolean.class || field.getType() == boolean.class) {
                field.setAccessible(true);
                field.set(alvo, true);
            }
        }
    }

    private static Set<String> diferenca(Set<String> esperado, Set<String> atual) {
        return esperado.stream()
                .filter(item -> !atual.contains(item))
                .collect(Collectors.toSet());
    }
}

package br.com.iv.cdss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.iv.cdss.service.CdssRegraDoseService.Valores;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.RoutePermissionCatalog;

class CdssRegraDoseServiceTest {

    private AuthenticatedUser usuario(Map<String, Boolean> permissoes) {
        return new AuthenticatedUser("1", "777", "Teste", null, "HMSFX", null, "12345", null, null, null, null, null,
                false, permissoes);
    }

    @Test
    void soQuemTemPermissaoAlteraOuValida() {
        assertThrows(SecurityException.class, () -> CdssRegraDoseService.exigirPermissao(null));
        assertThrows(SecurityException.class, () -> CdssRegraDoseService.exigirPermissao(usuario(Map.of("kmedico", true))));
        CdssRegraDoseService.exigirPermissao(usuario(Map.of("pregrasdose", true)));
        CdssRegraDoseService.exigirPermissao(usuario(Map.of("kadm", true)));
    }

    @Test
    void medicoComumNaoEditaPelaRota() {
        RoutePermissionCatalog c = new RoutePermissionCatalog();
        List<String> editar = c.requiredPermissionsFor("cdss/regras-dose/editar").orElseThrow();
        assertEquals(List.of("pregrasdose", "kadm"), editar);
        assertTrue(c.requiredPermissionsFor("cdss/regras-dose/gestao").orElseThrow().contains("kmedico"));
    }

    @Test
    void tecnicoSugereMasSoAutorizadoDecide() {
        RoutePermissionCatalog c = new RoutePermissionCatalog();
        assertTrue(c.requiredPermissionsFor("cdss/regras-dose/sugerir").orElseThrow().contains("ktcenfermagem"));
        assertTrue(c.requiredPermissionsFor("cdss/regras-dose/gestao").orElseThrow().contains("ktcenfermagem"));
        assertEquals(List.of("pregrasdose", "kadm"),
                c.requiredPermissionsFor("cdss/regras-dose/sugestoes/decidir").orElseThrow());
        assertThrows(SecurityException.class,
                () -> new CdssRegraDoseService().decidirSugestao(Map.of("id", 1, "aprovar", true), usuario(Map.of("kmedico", true))));
    }

    @Test
    void sugestaoExigeJustificativaEMudanca() throws Exception {
        CdssRegraDoseService service = new CdssRegraDoseService();
        br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico base =
                new br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico();
        java.lang.reflect.Method carregar = base.getClass().getDeclaredMethod("carregar");
        carregar.setAccessible(true);
        carregar.invoke(base);
        service.base = base;
        AuthenticatedUser tecnico = usuario(Map.of("ktcenfermagem", true));

        Map<String, Object> curta = new HashMap<>(Map.of("nome", "paracetamol", "doseMaximaDiaMg", "3000", "justificativa", "curta"));
        assertThrows(IllegalArgumentException.class, () -> service.sugerir(curta, tecnico));

        Map<String, Object> igual = new HashMap<>(Map.of("nome", "paracetamol", "doseMaximaDiaMg", "4000",
                "justificativa", "Manter o limite conforme bula vigente."));
        assertThrows(IllegalArgumentException.class, () -> service.sugerir(igual, tecnico));

        assertThrows(SecurityException.class, () -> service.sugerir(curta, null));
    }

    private CdssRegraDoseService servicoComBase() throws Exception {
        CdssRegraDoseService service = new CdssRegraDoseService();
        br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico base =
                new br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico();
        java.lang.reflect.Method carregar = base.getClass().getDeclaredMethod("carregar");
        carregar.setAccessible(true);
        carregar.invoke(base);
        service.base = base;
        return service;
    }

    @Test
    void inclusaoExclusaoERestauracaoSoComPermissaoPelaRota() {
        RoutePermissionCatalog c = new RoutePermissionCatalog();
        for (String rota : List.of("cdss/regras-dose/incluir", "cdss/regras-dose/excluir", "cdss/regras-dose/restaurar")) {
            assertEquals(List.of("pregrasdose", "kadm"), c.requiredPermissionsFor(rota).orElseThrow(), rota);
        }
        CdssRegraDoseService service = new CdssRegraDoseService();
        AuthenticatedUser medico = usuario(Map.of("kmedico", true));
        assertThrows(SecurityException.class, () -> service.incluir(Map.of("nome", "cefepima"), medico));
        assertThrows(SecurityException.class, () -> service.excluir(Map.of("nome", "dipirona"), medico));
        assertThrows(SecurityException.class, () -> service.restaurar(Map.of("nome", "dipirona"), medico));
    }

    @Test
    void inclusaoRecusaTermoQueJaIdentificaOutraSubstanciaEFaltaDeLimite() throws Exception {
        CdssRegraDoseService service = servicoComBase();
        AuthenticatedUser farmacia = usuario(Map.of("pregrasdose", true));
        IllegalArgumentException repetido = assertThrows(IllegalArgumentException.class,
                () -> service.incluir(new HashMap<>(Map.of("nome", "Remedio X", "sinonimos", "Novalgina", "doseMaximaDiaMg", "100")), farmacia));
        assertTrue(repetido.getMessage().contains("dipirona"));
        assertThrows(IllegalArgumentException.class,
                () -> service.incluir(new HashMap<>(Map.of("nome", "Dipirona", "doseMaximaDiaMg", "100")), farmacia));
        assertThrows(IllegalArgumentException.class,
                () -> service.incluir(new HashMap<>(Map.of("nome", "substancia nova")), farmacia));
        assertThrows(IllegalArgumentException.class,
                () -> service.excluir(new HashMap<>(Map.of("nome", "dipirona", "motivo", "curto")), farmacia));
    }

    @Test
    void sinonimosNormalizadosSemRepetirNemTermoCurto() {
        assertEquals(List.of("maxcef", "cefepime"),
                CdssRegraDoseService.normalizarSinonimos("Maxcef; CEFEPIME, cefepima, ab", "cefepima"));
        assertEquals(List.of("maxcef"), CdssRegraDoseService.normalizarSinonimos(List.of("Maxcef"), "cefepima"));
    }

    @Test
    void baseIncluiERetiraSubstanciaEmTempoDeExecucao() throws Exception {
        CdssRegraDoseService service = servicoComBase();
        br.com.iv.cdss.entity.CdssRegraDose r = new br.com.iv.cdss.entity.CdssRegraDose();
        r.setNome("ficticiol");
        r.setSinonimos("marca teste ghbam");
        r.setDoseMaximaDiaMg(java.math.BigDecimal.valueOf(6000));
        int antes = service.base.getMedicamentos().size();
        service.base.adicionar(CdssRegraDoseService.medicamentoDe(r));
        assertEquals(antes + 1, service.base.getMedicamentos().size());
        assertEquals("ficticiol", service.base.identificar("MARCA TESTE GHBAM 2 G INJ").nome);
        assertEquals(6000d, service.base.identificar("ficticiol").doseMaximaDiaMg);
        assertEquals("dipirona", service.base.identificar("novalgina 1 g").nome);
        assertTrue(service.base.remover("ficticiol"));
        assertNull(service.base.identificar("marca teste ghbam"));
        assertEquals(antes, service.base.getMedicamentos().size());
    }

    @Test
    void exclusaoDeMedicamentoDoArquivoDeixaSemLimiteComAviso() {
        br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Medicamento m =
                new br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Medicamento();
        m.doseMaximaDiaMg = 4000d;
        m.pediatriaMgKgDia = 75d;
        CdssRegraDoseService.SEM_LIMITE.aplicar(m);
        assertNull(m.doseMaximaDiaMg);
        assertNull(m.pediatriaMgKgDia);
        assertEquals(CdssRegraDoseService.MENSAGEM_EXCLUIDO, m.semTetoFixo);
    }

    @Test
    void diferencasDescrevemCadaCampoAlterado() {
        Valores a = new Valores(4000d, null, 75d, null, null);
        Valores b = new Valores(3000d, null, 60d, null, 0.25);
        String d = CdssRegraDoseService.diferencas(a, b);
        assertTrue(d.contains("Adulto mg/dia: 4000.0 -> 3000.0"));
        assertTrue(d.contains("Pediatria mg/kg/dia: 75.0 -> 60.0"));
        assertTrue(d.contains("Idade minima (anos): - -> 0.25"));
        assertEquals("", CdssRegraDoseService.diferencas(a, a));
    }

    @Test
    void numeroAusenteMantemVazioLimpaInvalidoRecusa() {
        Map<String, Object> corpo = new HashMap<>();
        assertEquals(10d, CdssRegraDoseService.numero(corpo, "x", 10d));
        corpo.put("x", "");
        assertNull(CdssRegraDoseService.numero(corpo, "x", 10d));
        corpo.put("x", "7,5");
        assertEquals(7.5, CdssRegraDoseService.numero(corpo, "x", 10d));
        corpo.put("x", "-1");
        assertThrows(IllegalArgumentException.class, () -> CdssRegraDoseService.numero(corpo, "x", 10d));
        corpo.put("x", "abc");
        assertThrows(IllegalArgumentException.class, () -> CdssRegraDoseService.numero(corpo, "x", 10d));
        assertFalse(corpo.isEmpty());
    }
}

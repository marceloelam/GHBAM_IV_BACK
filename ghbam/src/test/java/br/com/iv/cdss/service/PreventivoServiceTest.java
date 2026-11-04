package br.com.iv.cdss.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import br.com.iv.security.AuthenticatedUser;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import br.com.iv.entity.ProntuarioClinicoItem;

class PreventivoServiceTest {
    private AuthenticatedUser usuario() {
        return new AuthenticatedUser("1", "TESTE", "Teste", "", "UNIDADE_A", "", "", "", "", "", "", "", false, Map.of("kmedico", true));
    }

    private AuthenticatedUser usuarioProntuarioCompleto() {
        return new AuthenticatedUser("1", "TESTE", "Teste", "", "UNIDADE_A", "", "", "", "", "", "", "", false, Map.of("kmedico", true, "pprontuariocompleto", true));
    }

    @Test void pacienteSemVinculoNaUnidadeNaoPodeSerConsultado() {
        PreventivoService service = new PreventivoService();
        Map<String, Object> parametros = new HashMap<>();
        Query query = (Query) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Query.class}, (p, m, args) -> {
            if (m.getName().equals("setParameter")) { parametros.put(args[0].toString(), args[1]); return p; }
            if (m.getName().equals("getSingleResult")) return 0L;
            return null;
        });
        service.em = (EntityManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{EntityManager.class}, (p, m, args) -> query);
        assertThrows(NotFoundException.class, () -> service.avaliar(12, usuario()));
        assertEquals("UNIDADE_A", parametros.get("unidade"));
        assertEquals(12, parametros.get("id"));
    }

    @Test void prontuarioCompletoPodeConsultarPacienteDeOutraUnidade() {
        PreventivoService service = new PreventivoService();
        Map<String, Object> parametros = new HashMap<>();
        Query query = (Query) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Query.class}, (p, m, args) -> {
            if (m.getName().equals("setParameter")) { parametros.put(args[0].toString(), args[1]); return p; }
            if (m.getName().equals("getSingleResult")) return 1L;
            return p;
        });
        service.em = (EntityManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{EntityManager.class}, (p, m, args) -> query);
        assertDoesNotThrow(() -> service.verificarPaciente(12, usuarioProntuarioCompleto()));
        assertEquals(12, parametros.get("id"));
        assertFalse(parametros.containsKey("unidade"));
    }

    @Test void naoEncerraAlertaDeOutraUnidade() {
        PreventivoService service = new PreventivoService();
        ProntuarioClinicoItem item = new ProntuarioClinicoItem();
        item.setIdpaciente(12); item.setTipo(PreventivoService.TIPO); item.setUnidade("UNIDADE_B"); item.setStatus("ATIVO");
        Query query = (Query) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Query.class}, (p, m, args) -> m.getName().equals("getSingleResult") ? 1L : p);
        service.em = (EntityManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{EntityManager.class}, (p, m, args) -> m.getName().equals("find") ? item : query);
        assertThrows(NotFoundException.class, () -> service.encerrar(12, 1, new PreventivoService.Registro(null, null, "Justificativa documentada"), usuario()));
        assertEquals("ATIVO", item.getStatus());
    }
    @Test void exigeEvidenciasEAcompanhamento() {
        assertThrows(BadRequestException.class, () -> PreventivoService.validar(null));
        assertThrows(BadRequestException.class, () -> PreventivoService.validar(new PreventivoService.Registro("Risco", "", "Acompanhamento documentado")));
        assertThrows(BadRequestException.class, () -> PreventivoService.validar(new PreventivoService.Registro("Risco", "Fonte clinica documentada", "   ")));
        assertDoesNotThrow(() -> PreventivoService.validar(new PreventivoService.Registro("Risco registrado", "Fonte clinica documentada", "Acompanhamento documentado")));
    }

    @Test void exigeStatusPreventivoValidoEJustificado() {
        assertThrows(BadRequestException.class, () -> PreventivoService.validarAtualizacaoStatus(null));
        assertThrows(BadRequestException.class, () -> PreventivoService.validarAtualizacaoStatus(new PreventivoService.AtualizacaoStatus("ENCAMINHADO", "curto")));
        assertThrows(BadRequestException.class, () -> PreventivoService.validarAtualizacaoStatus(new PreventivoService.AtualizacaoStatus("ABERTO", "Justificativa clinica documentada")));
        assertEquals("RESOLVIDO", PreventivoService.validarAtualizacaoStatus(new PreventivoService.AtualizacaoStatus("resolvido", "Conduta concluida e documentada")));
    }

    @Test void rejeitaPacienteInvalidoEUnidadeAusenteAntesDaConsulta() {
        PreventivoService service = new PreventivoService();
        assertThrows(BadRequestException.class, () -> service.verificarPaciente(0, AuthenticatedUser.empty()));
        assertThrows(ForbiddenException.class, () -> service.verificarPaciente(12, AuthenticatedUser.empty()));
        assertThrows(ForbiddenException.class, () -> service.verificarPaciente(12, null));
    }

    @Test void calculaAlertaAutomaticoMetabolicoSemDiagnosticar() {
        PreventivoService service = new PreventivoService();
        var alertas = service.avaliarAutomaticamente(List.of(
                new PreventivoService.Fonte("LABORATORIO", "1", "100", "2026-09-01", "LAB", "Glicemia 220 mg/dL", "UNIDADE_A"),
                new PreventivoService.Fonte("ANAMNESE", "2", "100", "2026-09-02", "MEDICO", "Hiperglicemia e suspeita de diabetes", "UNIDADE_B")));

        assertFalse(alertas.isEmpty());
        assertEquals("CDSS_PREV_DIABETES", alertas.get(0).codigo());
        assertEquals("MODERATE", alertas.get(0).severidade());
        assertFalse(alertas.get(0).diagnostico());
    }

    @Test void fraseCopiadaNoMesmoBamContaUmaVezSo() {
        PreventivoService service = new PreventivoService();
        String anamnese = "#COMORBIDADES: HAS/DM - HIPERGLICÊMICO NO MOMENTO. NEGA ALERGIAS.";
        var mesmoBam = service.avaliarAutomaticamente(List.of(
                new PreventivoService.Fonte("ANAMNESE", "1", "100", "2026-09-01", "MEDICO", anamnese + "\n" + anamnese, "UNIDADE_A")));
        assertTrue(mesmoBam.stream().noneMatch(a -> "CDSS_PREV_DIABETES".equals(a.codigo())));

        var bamsDiferentes = service.avaliarAutomaticamente(List.of(
                new PreventivoService.Fonte("ANAMNESE", "1", "100", "2026-09-01", "MEDICO", anamnese, "UNIDADE_A"),
                new PreventivoService.Fonte("ANAMNESE", "2", "101", "2026-09-10", "MEDICO", anamnese, "UNIDADE_A")));
        assertTrue(bamsDiferentes.stream().anyMatch(a -> "CDSS_PREV_DIABETES".equals(a.codigo())));
    }

    @Test void calculaAlertaAutomaticoOncologicoQuandoHaSinaisAssociados() {
        PreventivoService service = new PreventivoService();
        var alertas = service.avaliarAutomaticamente(List.of(
                new PreventivoService.Fonte("EVOLUCAO", "1", "100", "2026-09-01", "MEDICO", "Paciente com perda de peso e anemia", "UNIDADE_A"),
                new PreventivoService.Fonte("PACS", "2", "100", "2026-09-02", "RADIO", "Laudo descreve massa abdominal", "UNIDADE_B")));

        assertTrue(alertas.stream().anyMatch(a -> "CDSS_PREV_ONCOLOGICO".equals(a.codigo())));
        assertTrue(alertas.stream().filter(a -> "CDSS_PREV_ONCOLOGICO".equals(a.codigo())).findFirst().orElseThrow().evidencias().size() >= 2);
    }

    @Test void calculaAlertaAutomaticoRespiratorioComSinaisCombinados() {
        PreventivoService service = new PreventivoService();
        var alertas = service.avaliarAutomaticamente(List.of(
                new PreventivoService.Fonte("TRIAGEM", "1", "100", "2026-09-01", "ENF", "SpO2 91 e falta de ar", "UNIDADE_A"),
                new PreventivoService.Fonte("ANAMNESE", "2", "100", "2026-09-02", "MEDICO", "Tabagismo com tosse cronica e sibilos", "UNIDADE_A")));

        var alerta = alertas.stream().filter(a -> "CDSS_PREV_RESPIRATORIO".equals(a.codigo())).findFirst().orElseThrow();
        assertEquals("HIGH", alerta.severidade());
        assertFalse(alerta.diagnostico());
    }

    @Test void calculaAlertaAutomaticoFragilidadeQuedaComRecorrencia() {
        PreventivoService service = new PreventivoService();
        var alertas = service.avaliarAutomaticamente(List.of(
                new PreventivoService.Fonte("MULTIDISCIPLINAR", "1", "100", "2026-09-01", "FISIO", "Queda em domicilio e marcha instavel", "UNIDADE_A"),
                new PreventivoService.Fonte("EVOLUCAO", "2", "100", "2026-09-02", "MEDICO", "Paciente com fragilidade e perda funcional", "UNIDADE_B")));

        var alerta = alertas.stream().filter(a -> "CDSS_PREV_FRAGILIDADE_QUEDA".equals(a.codigo())).findFirst().orElseThrow();
        assertEquals("HIGH", alerta.severidade());
        assertFalse(alerta.diagnostico());
    }
}

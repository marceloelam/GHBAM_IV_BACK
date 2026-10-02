package br.com.iv.cdss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.iv.cdss.dto.CdssAvaliacaoPrescricaoRequest;
import br.com.iv.cdss.dto.CdssBularioMedicamentoDto;
import br.com.iv.cdss.dto.News2Request;
import br.com.iv.cdss.service.CdssRulesEngine;
import br.com.iv.security.RoutePermissionCatalog;

class CdssRulesEngineTest {

    private final CdssRulesEngine engine = new CdssRulesEngine();

    @Test
    void bloqueiaMedicamentoComAlergiaRegistrada() {
        CdssAvaliacaoPrescricaoRequest request = new CdssAvaliacaoPrescricaoRequest();
        request.setMedicamento("Dipirona 500mg");
        request.setAlergias(List.of("dipirona"));

        var response = engine.avaliarPrescricao(request);

        assertTrue(response.isPossuiAlertaCritico());
        assertTrue(response.isBloqueante());
        assertEquals("CDSS_ALERGIA_MEDICAMENTO", response.getAlertas().get(0).getCodigo());
    }

    @Test
    void detectaInteracaoCriticaNosDoisSentidos() {
        CdssAvaliacaoPrescricaoRequest ida = new CdssAvaliacaoPrescricaoRequest();
        ida.setMedicamento("Varfarina");
        ida.setMedicamentosAtuais(List.of("AAS"));

        CdssAvaliacaoPrescricaoRequest volta = new CdssAvaliacaoPrescricaoRequest();
        volta.setMedicamento("AAS");
        volta.setMedicamentosAtuais(List.of("Varfarina"));

        assertTrue(engine.avaliarPrescricao(ida).isPossuiAlertaCritico());
        assertTrue(engine.avaliarPrescricao(volta).isPossuiAlertaCritico());
    }

    @Test
    void bloqueiaDosePesoDependenteSemPeso() {
        CdssAvaliacaoPrescricaoRequest request = new CdssAvaliacaoPrescricaoRequest();
        request.setMedicamento("Gentamicina");
        request.setDose("300mg");

        var response = engine.avaliarPrescricao(request);

        assertTrue(response.isBloqueante());
        assertTrue(response.getAlertas().stream().anyMatch(a -> "CDSS_PESO_OBRIGATORIO".equals(a.getCodigo())));
    }

    @Test
    void bloqueiaDoseDiariaCalculadaPelaFrequencia() {
        CdssAvaliacaoPrescricaoRequest request = new CdssAvaliacaoPrescricaoRequest();
        request.setMedicamento("Paracetamol");
        request.setDose("1 g");
        request.setFrequencia("de 4/4 horas");

        var response = engine.avaliarPrescricao(request);

        assertTrue(response.isBloqueante());
        assertTrue(response.getAlertas().stream().anyMatch(a -> "CDSS_DOSE_MAXIMA_DIA".equals(a.getCodigo())));
    }

    @Test
    void exigePesoAtualParaValidacaoPediatrica() {
        CdssAvaliacaoPrescricaoRequest request = new CdssAvaliacaoPrescricaoRequest();
        request.setMedicamento("Paracetamol");
        request.setDose("500 mg");
        request.setIdadeAnos(8);

        var response = engine.avaliarPrescricao(request);

        assertTrue(response.isBloqueante());
        assertTrue(response.getAlertas().stream().anyMatch(a -> "CDSS_PESO_PEDIATRICO_OBRIGATORIO".equals(a.getCodigo())));
    }

    @Test
    void indicaBularioQuandoAindaNaoExisteRegraEstruturadaDeDose() {
        CdssBularioMedicamentoDto bula = new CdssBularioMedicamentoDto();
        bula.setNome("Medicamento de teste");
        bula.setPrincipioAtivo("medicamento de teste");
        bula.setPosologia("Uso conforme prescricao medica.");
        bula.setFonte("Bulário institucional");

        CdssAvaliacaoPrescricaoRequest request = new CdssAvaliacaoPrescricaoRequest();
        request.setMedicamento("Medicamento de teste");
        request.setDose("500 mg");
        request.setBulario(bula);

        var response = engine.avaliarPrescricao(request);

        assertTrue(response.getAlertas().stream().anyMatch(a -> "CDSS_BULARIO_REGRA_PENDENTE".equals(a.getCodigo())));
    }

    @Test
    void bularioGeraAlertaDeInteracaoComMedicamentoAtual() {
        CdssBularioMedicamentoDto bula = new CdssBularioMedicamentoDto();
        bula.setNome("Zoup SL");
        bula.setInteracoes("O uso concomitante com cetoconazol pode aumentar efeitos sedativos.");

        CdssAvaliacaoPrescricaoRequest request = new CdssAvaliacaoPrescricaoRequest();
        request.setMedicamento("Zolpidem");
        request.setMedicamentosAtuais(List.of("Cetoconazol"));
        request.setBulario(bula);

        var response = engine.avaliarPrescricao(request);

        assertTrue(response.getAlertas().stream().anyMatch(a -> "CDSS_BULARIO_INTERACAO".equals(a.getCodigo())));
    }

    @Test
    void bularioBloqueiaContraindicacaoRelacionadaAAlergia() {
        CdssBularioMedicamentoDto bula = new CdssBularioMedicamentoDto();
        bula.setNome("Amoxicilina");
        bula.setPrincipioAtivo("amoxicilina");
        bula.setContraindicacoes("Contraindicado para pacientes com hipersensibilidade a amoxicilina.");

        CdssAvaliacaoPrescricaoRequest request = new CdssAvaliacaoPrescricaoRequest();
        request.setMedicamento("Amoxicilina");
        request.setAlergias(List.of("amoxicilina"));
        request.setBulario(bula);

        var response = engine.avaliarPrescricao(request);

        assertTrue(response.isBloqueante());
        assertTrue(response.getAlertas().stream().anyMatch(a -> "CDSS_BULARIO_CONTRAINDICACAO".equals(a.getCodigo())));
    }

    @Test
    void calculaNews2ComRiscoAlto() {
        News2Request request = new News2Request();
        request.setFrequenciaRespiratoria(24);
        request.setSaturacaoOxigenio(93);
        request.setOxigenioSuplementar(true);
        request.setTemperatura(38.5);
        request.setPressaoSistolica(100);
        request.setFrequenciaCardiaca(110);
        request.setConsciencia("voz");

        var response = engine.calcularNews2(request);

        assertEquals(13, response.getTotal());
        assertEquals("ALTO", response.getRisco());
    }

    @Test
    void news2ExigeCamposObrigatorios() {
        News2Request request = new News2Request();

        assertThrows(IllegalArgumentException.class, () -> engine.calcularNews2(request));
    }

    @Test
    void rotaCdssTemCoberturaRbacClinica() {
        RoutePermissionCatalog catalog = new RoutePermissionCatalog();
        var permissoes = catalog.requiredPermissionsFor("/cdss/prescricao/avaliar").orElseThrow();

        assertTrue(permissoes.contains("kmedico"));
        assertTrue(permissoes.contains("kenfermagem"));
        assertTrue(permissoes.contains("kfarmaciacentral"));
        assertTrue(permissoes.contains("pauditoriaclinica"));
        assertFalse(permissoes.contains("krecepcao"));
    }
}

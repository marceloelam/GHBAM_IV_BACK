package br.com.iv.cdss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.com.iv.cdss.dto.CdssAlertDto;
import br.com.iv.cdss.dto.CdssAvaliacaoPrescricaoRequest;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico;

class CdssBaseFarmaceuticaRegrasTest {

    static CdssBaseFarmaceuticaRegras regras;
    static CdssRulesEngine motor = new CdssRulesEngine();

    @BeforeAll
    static void carregarBase() throws Exception {
        BaseConhecimentoFarmaceutico base = new BaseConhecimentoFarmaceutico();
        Method carregar = BaseConhecimentoFarmaceutico.class.getDeclaredMethod("carregar");
        carregar.setAccessible(true);
        carregar.invoke(base);
        regras = new CdssBaseFarmaceuticaRegras();
        regras.base = base;
    }

    private List<CdssAlertDto> avaliar(CdssAvaliacaoPrescricaoRequest r) {
        List<CdssAlertDto> existentes = motor.avaliarPrescricao(r).getAlertas();
        return regras.avaliar(r, existentes);
    }

    private CdssAvaliacaoPrescricaoRequest req(String medicamento, String dose, String frequencia) {
        CdssAvaliacaoPrescricaoRequest r = new CdssAvaliacaoPrescricaoRequest();
        r.setMedicamento(medicamento);
        r.setDose(dose);
        r.setFrequencia(frequencia);
        r.setPesoKg(70d);
        r.setIdadeAnos(40);
        return r;
    }

    private boolean tem(List<CdssAlertDto> alertas, String codigo) {
        return alertas.stream().anyMatch(a -> codigo.equals(a.getCodigo()));
    }

    @Test
    void alergiaPorNomeComercialBloqueia() {
        CdssAvaliacaoPrescricaoRequest r = req("Dipirona 1g", "1 g", "6/6h");
        r.setAlergias(List.of("novalgina"));
        List<CdssAlertDto> alertas = avaliar(r);
        CdssAlertDto a = alertas.stream().filter(x -> "CDSS_ALERGIA_MEDICAMENTO".equals(x.getCodigo())).findFirst().orElseThrow();
        assertTrue(a.isBloqueante());
    }

    @Test
    void alergiaDiretaJaAlertadaPeloMotorNaoDuplica() {
        CdssAvaliacaoPrescricaoRequest r = req("Dipirona 1g", "1 g", "6/6h");
        r.setAlergias(List.of("dipirona"));
        assertFalse(tem(avaliar(r), "CDSS_ALERGIA_MEDICAMENTO"));
    }

    @Test
    void alergiaPenicilinaAlertaCefalosporina() {
        CdssAvaliacaoPrescricaoRequest r = req("Cefalexina 500mg", "500 mg", "6/6h");
        r.setAlergias(List.of("penicilina"));
        CdssAlertDto a = avaliar(r).stream().filter(x -> "CDSS_ALERGIA_CRUZADA".equals(x.getCodigo())).findFirst().orElseThrow();
        assertEquals("MAJOR", a.getSeveridade());
        assertFalse(a.isBloqueante());
    }

    @Test
    void interacaoPorClasseAnticoagulanteComAine() {
        CdssAvaliacaoPrescricaoRequest r = req("Ibuprofeno 600mg", "600 mg", "8/8h");
        r.setMedicamentosAtuais(List.of("Marevan 5mg"));
        assertTrue(tem(avaliar(r), "CDSS_INTERACAO_BASE"));
    }

    @Test
    void interacaoJaNaTabelaFixaNaoDuplica() {
        CdssAvaliacaoPrescricaoRequest r = req("Varfarina 5mg", "5 mg", "1x ao dia");
        r.setMedicamentosAtuais(List.of("AAS 100mg"));
        assertTrue(tem(motor.avaliarPrescricao(r).getAlertas(), "CDSS_INTERACAO_CRITICA"));
        assertFalse(tem(avaliar(r), "CDSS_INTERACAO_BASE"));
    }

    @Test
    void mesmoPrincipioAtivoJaAtivo() {
        CdssAvaliacaoPrescricaoRequest r = req("Novalgina 1g", "1 g", "6/6h");
        r.setMedicamentosAtuais(List.of("Dipirona sodica 500mg/ml"));
        assertTrue(tem(avaliar(r), "CDSS_MESMO_PRINCIPIO_ATIVO"));
    }

    @Test
    void duplicidadeTerapeuticaEntreIbps() {
        CdssAvaliacaoPrescricaoRequest r = req("Pantoprazol 40mg", "40 mg", "1x ao dia");
        r.setMedicamentosAtuais(List.of("Omeprazol 20mg"));
        assertTrue(tem(avaliar(r), "CDSS_DUPLICIDADE_TERAPEUTICA"));
    }

    @Test
    void doseMaximaDaBaseQuandoTabelaFixaNaoConhece() {
        // Tramadol nao esta na tabela fixa; a base limita em 400 mg/dia.
        List<CdssAlertDto> alertas = avaliar(req("Tramadol 100mg", "100 mg", "4/4h"));
        CdssAlertDto a = alertas.stream().filter(x -> "CDSS_DOSE_MAXIMA_DIA".equals(x.getCodigo())).findFirst().orElseThrow();
        assertTrue(a.isBloqueante());
    }

    @Test
    void doseDentroDoLimiteNaoAlerta() {
        assertFalse(tem(avaliar(req("Tramadol 50mg", "50 mg", "6/6h")), "CDSS_DOSE_MAXIMA_DIA"));
    }

    @Test
    void dosePorKgNaoComparaComLimiteAbsoluto() {
        assertFalse(tem(avaliar(req("Tramadol", "1 mg/kg", "6/6h")), "CDSS_DOSE_MAXIMA_DIA"));
    }

    @Test
    void enoxaparinaSemPesoExigePeso() {
        CdssAvaliacaoPrescricaoRequest r = req("Enoxaparina 40mg", "40 mg", "1x ao dia");
        r.setPesoKg(null);
        assertTrue(tem(avaliar(r), "CDSS_PESO_OBRIGATORIO"));
    }

    @Test
    void idadeMinimaEBeers() {
        CdssAvaliacaoPrescricaoRequest crianca = req("Tramadol 50mg", "50 mg", "8/8h");
        crianca.setIdadeAnos(8);
        assertTrue(tem(avaliar(crianca), "CDSS_IDADE_MINIMA"));

        CdssAvaliacaoPrescricaoRequest idoso = req("Ibuprofeno 400mg", "400 mg", "8/8h");
        idoso.setIdadeAnos(78);
        assertTrue(tem(avaliar(idoso), "CDSS_IDOSO_BEERS"));
    }

    @Test
    void amicacinaRespeitaLimitePorPesoEAbsoluto() {
        CdssAvaliacaoPrescricaoRequest r = req("AMICACINA (SULFATO DE) 250MG/M - AMICACINA", "1,5 g", "1x ao dia");
        r.setPesoKg(60d); // 20 mg/kg x 60 = 1200 mg
        assertTrue(tem(avaliar(r), "CDSS_DOSE_MAXIMA_PESO"));

        r.setPesoKg(80d); // 1600 por peso, mas o teto absoluto e 1500
        assertFalse(tem(avaliar(r), "CDSS_DOSE_MAXIMA_PESO"));
        r.setDose("2 g");
        assertTrue(tem(avaliar(r), "CDSS_DOSE_MAXIMA_DIA"));

        assertFalse(tem(avaliar(req("Amicacina", "15 mg/kg", "1x ao dia")), "CDSS_DOSE_MAXIMA_PESO"));
        assertTrue(tem(avaliar(req("Amicacina", "25 mg/kg", "1x ao dia")), "CDSS_DOSE_MAXIMA_PESO"));
    }

    @Test
    void semTetoFixoNaoAlertaDose() {
        assertFalse(tem(avaliar(req("Morfina 10mg/ml", "1000 mg", "4/4h")), "CDSS_DOSE_MAXIMA_DIA"));
        assertTrue(regras.possuiDoseMaxima("Morfina"));
        assertTrue(regras.regrasDose().size() > 200);
    }

    @Test
    void medicamentoForaDaBaseNaoGeraAlerta() {
        assertTrue(avaliar(req("Soro fisiologico 0,9%", "500 ml", "12/12h")).isEmpty());
    }
}

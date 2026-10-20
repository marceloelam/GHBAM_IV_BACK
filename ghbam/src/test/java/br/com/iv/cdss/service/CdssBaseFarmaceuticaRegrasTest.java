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
    void limitePendenteDeValidacaoAvisaSemBloquear() {
        BaseConhecimentoFarmaceutico.Medicamento tramadol = regras.base.identificar("tramadol");
        tramadol.limitePendente = true;
        try {
            CdssAlertDto a = avaliar(req("Tramadol 100mg", "100 mg", "4/4h")).stream()
                    .filter(x -> "CDSS_DOSE_MAXIMA_DIA".equals(x.getCodigo())).findFirst().orElseThrow();
            assertFalse(a.isBloqueante());
            assertEquals("MAJOR", a.getSeveridade());
            assertTrue(a.getTitulo().contains("pendente"));
        } finally {
            tramadol.limitePendente = false;
        }
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
    void limitePediatricoPorPeso() {
        // 20 kg: paracetamol 75 mg/kg/dia = 1500 mg/dia.
        CdssAvaliacaoPrescricaoRequest crianca = req("Paracetamol 200mg/ml", "500 mg", "6/6h");
        crianca.setIdadeAnos(6);
        crianca.setPesoKg(20d);
        assertTrue(tem(avaliar(crianca), "CDSS_DOSE_MAXIMA_PEDIATRICA"));

        crianca.setDose("300 mg");
        assertFalse(tem(avaliar(crianca), "CDSS_DOSE_MAXIMA_PEDIATRICA"));

        // Dose escrita em mg/kg: amoxicilina 60 mg/kg 12/12h = 120 mg/kg/dia > 100.
        CdssAvaliacaoPrescricaoRequest amox = req("Amoxicilina", "60 mg/kg", "12/12h");
        amox.setIdadeAnos(3);
        amox.setPesoKg(14d);
        assertTrue(tem(avaliar(amox), "CDSS_DOSE_MAXIMA_PEDIATRICA"));
    }

    @Test
    void adolescentePesadoLimitadoAoTetoDeAdulto() {
        // 70 kg x 75 = 5250, mas o teto de adulto (4000) prevalece.
        CdssAvaliacaoPrescricaoRequest r = req("Paracetamol", "1 g", "6/6h");
        r.setIdadeAnos(16);
        r.setPesoKg(70d);
        assertFalse(tem(avaliar(r), "CDSS_DOSE_MAXIMA_PEDIATRICA"));
        r.setFrequencia("4/4h"); // 6 g/dia: o motor fixo de adulto ja barra antes do pediatrico
        List<CdssAlertDto> alertas = avaliar(r);
        List<CdssAlertDto> todos = new java.util.ArrayList<>(motor.avaliarPrescricao(r).getAlertas());
        todos.addAll(alertas);
        assertTrue(tem(todos, "CDSS_DOSE_MAXIMA_DIA") || tem(todos, "CDSS_DOSE_MAXIMA_PEDIATRICA"));
    }

    @Test
    void revisaoNomesParecidosNaoTrocamDeSubstancia() {
        // Ciprofloxacino 500 mg 12/12h e dose usual de adulto: nada de alerta de dose.
        assertFalse(tem(avaliar(req("Ciprofloxacino 500mg", "500 mg", "12/12h")), "CDSS_DOSE_MAXIMA_DIA"));
        // Ciprofibrato tem limite proprio (200 mg), nao o do ciprofloxacino.
        assertEquals("ciprofibrato", regras.base.identificar("CIPROFIBRATO 100MG").nome);
        assertTrue(tem(avaliar(req("Ciprofibrato 100mg", "100 mg", "12/12h")) , "CDSS_DOSE_MAXIMA_DIA") == false);
        assertTrue(tem(avaliar(req("Ciprofibrato 100mg", "300 mg", "1x ao dia")), "CDSS_DOSE_MAXIMA_DIA"));
        // Decanoato (deposito) nao e comparado ao teto do haloperidol oral.
        assertFalse(tem(avaliar(req("HALOPERIDOL (DECANOATO) 50MG/ML", "200 mg", "dose unica")), "CDSS_DOSE_MAXIMA_DIA"));
        // Adrenalina e titulada: sem limite de anestesico local.
        assertFalse(tem(avaliar(req("Epinefrina 1mg/ml", "1 mg", "4/4h")), "CDSS_DOSE_MAXIMA_DIA"));
    }

    @Test
    void azitromicinaDoseUnicaDeOtiteNaoBloqueia() {
        CdssAvaliacaoPrescricaoRequest r = req("Azitromicina 200mg/5ml", "30 mg/kg", "dose unica");
        r.setIdadeAnos(4);
        r.setPesoKg(16d);
        assertFalse(tem(avaliar(r), "CDSS_DOSE_MAXIMA_PEDIATRICA"));
    }

    @Test
    void medicamentoForaDaBaseNaoGeraAlerta() {
        assertTrue(avaliar(req("Soro fisiologico 0,9%", "500 ml", "12/12h")).isEmpty());
    }
}

package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.iv.service.EvidenciaCasoEpidemiologico.Evidencia;
import br.com.iv.service.EvidenciaCasoEpidemiologico.Sugestao;

class EvidenciaCasoEpidemiologicoTest {

    private static final String DENGUE = "Suspeita de Dengue";
    private static final String TERMOS = "dengue, febre, mialgia";

    private static Evidencia exame(String nome, String valor) {
        return EvidenciaCasoEpidemiologico.doExame(nome, valor, DENGUE, TERMOS);
    }

    @Test
    void reagenteSugereConfirmarPorLaboratorio() {
        Sugestao s = EvidenciaCasoEpidemiologico.sugerir(List.of(exame("Antígeno NS1", "Reagente")));
        assertEquals("CONFIRMADO", s.classificacao());
        assertEquals("LABORATORIAL", s.criterio());
    }

    @Test
    void naoReagenteNaoVemComoReagenteENaoSugereDescarte() {
        assertEquals("NAO_REAGENTE", exame("Dengue IgM", "Não reagente").resultado());
        assertNull(EvidenciaCasoEpidemiologico.sugerir(List.of(exame("Dengue IgM", "Não reagente"))));
    }

    @Test
    void iggReagenteNaoConfirma() {
        assertEquals("VER_LAUDO", exame("Sorologia dengue IgG", "Reagente").resultado());
        assertNull(EvidenciaCasoEpidemiologico.sugerir(List.of(exame("Sorologia dengue IgG", "Reagente"))));
    }

    @Test
    void exameDeOutraDoencaEIgnorado() {
        assertNull(exame("Hemograma completo", "Reagente"));
        assertNull(exame("Sorologia para zika", "Reagente"));
    }

    @Test
    void valorNuloOuNumericoPedeOLaudo() {
        assertEquals("VER_LAUDO", exame("Dengue NS1", "").resultado());
        assertEquals("VER_LAUDO", exame("Dengue NS1", "1,8").resultado());
    }

    @Test
    void fichaSinanConfirmaDescartaOuNadaInforma() {
        Evidencia confirma = EvidenciaCasoEpidemiologico.daFichaSinan("DENGUE_CHIKUNGUNYA",
                Map.of("agravo", "Dengue", "classificacaoDengue", "10 - Dengue", "criterioConfirmacaoDescarteDengue", "1 - Laboratório"), DENGUE, TERMOS);
        assertEquals("CONFIRMADO", confirma.resultado());
        assertEquals("LABORATORIAL", confirma.criterio());
        assertEquals("DESCARTADO", EvidenciaCasoEpidemiologico.daFichaSinan("DENGUE", Map.of("classificacaoDengue", "5 - Descartado"), DENGUE, TERMOS).resultado());
        assertNull(EvidenciaCasoEpidemiologico.daFichaSinan("DENGUE", Map.of("classificacaoDengue", ""), DENGUE, TERMOS));
    }

    @Test
    void fichaDeOutroAgravoEClassificacaoClinicaSaoIgnoradas() {
        assertNull(EvidenciaCasoEpidemiologico.daFichaSinan("RAIVA", Map.of("classificacaoFinalRaiva", "1 - Confirmado"), DENGUE, TERMOS));
        assertNull(EvidenciaCasoEpidemiologico.daFichaSinan("SIFILIS", Map.of("classificacaoClinicaSifilis", "Confirmado"), "Sífilis", "sifilis"));
    }

    @Test
    void divergenciaNaoSugereNada() {
        Evidencia ficha = new Evidencia("SINAN", "Ficha", "DESCARTADO", "");
        Sugestao s = EvidenciaCasoEpidemiologico.sugerir(List.of(exame("NS1", "Reagente"), ficha));
        assertNotNull(s);
        assertEquals("", s.classificacao());
    }

    /** Nomes como o leitor do PDF os grava: minusculos e sem acento. */
    @Test
    void nomesLidosDoPdfChegamASugestao() {
        assertEquals("CONFIRMADO", EvidenciaCasoEpidemiologico.sugerir(List.of(exame("dengue - antigeno ns1", "Reagente"))).classificacao());
        assertEquals("CONFIRMADO", EvidenciaCasoEpidemiologico.sugerir(List.of(
                EvidenciaCasoEpidemiologico.doExame("sars-cov-2 rt-pcr", "Reagente", "Sindrome gripal", "febre, tosse"))).classificacao());
        assertNull(EvidenciaCasoEpidemiologico.sugerir(List.of(exame("dengue igg", "Reagente"))));
        assertNull(EvidenciaCasoEpidemiologico.sugerir(List.of(exame("dengue igm", "Não reagente"))));
    }

    @Test
    void fichaDescartadaSugereDescartar() {
        assertEquals("DESCARTADO", EvidenciaCasoEpidemiologico.sugerir(List.of(new Evidencia("SINAN", "Ficha", "DESCARTADO", ""))).classificacao());
    }
}

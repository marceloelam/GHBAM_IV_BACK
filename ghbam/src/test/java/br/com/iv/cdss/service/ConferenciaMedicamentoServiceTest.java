package br.com.iv.cdss.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico;

class ConferenciaMedicamentoServiceTest {

    static BaseConhecimentoFarmaceutico base = new BaseConhecimentoFarmaceutico();

    @BeforeAll
    static void carregar() throws Exception {
        Method m = BaseConhecimentoFarmaceutico.class.getDeclaredMethod("carregar");
        m.setAccessible(true);
        m.invoke(base);
    }

    private boolean consta(String principio, String nome, String prescricao) {
        return ConferenciaMedicamentoService.constaNaPrescricao(base.identificar(principio), principio, nome, prescricao);
    }

    @Test
    void encontraPeloNomeComercialPrescrito() {
        // Embalagem de dipirona; o medico escreveu o nome comercial.
        assertTrue(consta("dipirona sodica", "Dipirona 500mg/ml", "1) Novalgina 1g EV 6/6h se dor"));
    }

    @Test
    void encontraPeloPrincipioAtivo() {
        assertTrue(consta("ceftriaxona", "Rocefin 1g", "<p>Ceftriaxona 1g EV 12/12h</p>"));
    }

    @Test
    void naoEncontraMedicamentoAusente() {
        assertFalse(consta("tramadol", "Tramal 50mg", "Dipirona 1g EV 6/6h; Omeprazol 40mg EV 1x ao dia"));
    }

    @Test
    void prescricaoVaziaNaoConfirma() {
        assertFalse(consta("dipirona", "Dipirona", ""));
    }
}

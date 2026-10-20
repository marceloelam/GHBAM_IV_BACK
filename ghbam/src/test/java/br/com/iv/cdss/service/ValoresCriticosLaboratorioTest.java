package br.com.iv.cdss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.com.iv.cdss.service.ValoresCriticosLaboratorio.Achado;

class ValoresCriticosLaboratorioTest {

    static ValoresCriticosLaboratorio v = new ValoresCriticosLaboratorio();

    @BeforeAll
    static void carregar() {
        v.carregar();
    }

    @Test
    void potassioAltoPorLoincComVirgulaDecimal() {
        Achado a = v.avaliar("Potassio", "2823-3", "6,8", "mEq/L");
        assertNotNull(a);
        assertEquals("ALTO", a.direcao());
    }

    @Test
    void potassioNormalNaoAcusa() {
        assertNull(v.avaliar("Potassio", "2823-3", "4,2", "mEq/L"));
    }

    @Test
    void sodioBaixoPeloNomeSemLoinc() {
        Achado a = v.avaliar("Sodio (soro)", null, "118", "mmol/L");
        assertNotNull(a);
        assertEquals("BAIXO", a.direcao());
    }

    @Test
    void glicoseEmMmolNaoComparaComLimiteEmMgDl() {
        assertNull(v.avaliar("Glicose", "2345-7", "2,0", "mmol/L"));
        assertNotNull(v.avaliar("Glicose", "2345-7", "35", "mg/dL"));
    }

    @Test
    void plaquetasEmFormatosBrasileiros() {
        assertNotNull(v.avaliar("Plaquetas", "777-3", "15.000", "/mm3"));
        assertNotNull(v.avaliar("Plaquetas", "777-3", "15", "mil/mm³"));
        assertNotNull(v.avaliar("Plaquetas", "777-3", "15", "x10³/µL"));
        assertNotNull(v.avaliar("Plaquetas", "777-3", "15", null));
        assertNull(v.avaliar("Plaquetas", "777-3", "250.000", "/mm3"));
        assertNull(v.avaliar("Plaquetas", "777-3", "250", "mil/mm3"));
        assertNull(v.avaliar("Plaquetas", "777-3", "250", null));
    }

    @Test
    void leucocitosComUnidadeExplicitaNaoEscala() {
        // 800/mm3 e realmente 800: critico baixo, nao 800 mil.
        Achado a = v.avaliar("Leucocitos", "6690-2", "800", "/mm3");
        assertNotNull(a);
        assertEquals("BAIXO", a.direcao());
        assertNull(v.avaliar("Leucocitos", "6690-2", "8.500", "/mm3"));
    }

    @Test
    void valorComSinalDeLimiteDeDeteccao() {
        assertNotNull(v.avaliar("Glicose", "2345-7", "> 600", "mg/dL"));
    }

    @Test
    void textoNaoNumericoNaoAcusa() {
        assertNull(v.avaliar("Glicose", "2345-7", "Negativo", "mg/dL"));
    }

    @Test
    void phSoPorLoincParaNaoConfundirComUrina() {
        assertNull(v.avaliar("pH", null, "8,0", null));
        assertNotNull(v.avaliar("pH", "2744-1", "7,05", null));
    }

    @Test
    void loincDeOutroExameNaoUsaNome() {
        assertNull(v.avaliar("Potassio", "2160-0", "9,0", "mg/dL"));
    }

    @Test
    void inrAlto() {
        assertNotNull(v.avaliar("RNI", "6301-6", "6,2", null));
    }
}

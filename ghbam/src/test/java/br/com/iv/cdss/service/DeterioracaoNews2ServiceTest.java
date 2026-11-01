package br.com.iv.cdss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DeterioracaoNews2ServiceTest {
    private final DeterioracaoNews2Service service = new DeterioracaoNews2Service();
    private final LocalDateTime agora = LocalDateTime.of(2026, 10, 29, 14, 0);

    private DeterioracaoNews2Service.Afericao afericao(Double fr, Double spo, Double pas, Double fc, Double tax) {
        return new DeterioracaoNews2Service.Afericao(1, "29/10/2026", "06:30", fr, spo, pas, fc, tax);
    }

    @Test
    void pacienteGraveDaAltoRisco() {
        // FR 26 (3) + SpO2 90 (3) + PAS 95 (2) + FC 118 (2) + Tax 38,5 (1) = 11
        Map<String, Object> r = service.calcular(afericao(26d, 90d, 95d, 118d, 38.5), agora);
        assertEquals(11, r.get("total"));
        assertEquals("ALTO", r.get("risco"));
        assertEquals(7L, r.get("horasDesdeAfericao"));
        assertEquals(true, r.get("afericaoAtrasada"));
    }

    @Test
    void sinaisNormaisDaBaixoRisco() {
        Map<String, Object> r = service.calcular(afericao(16d, 98d, 120d, 80d, 36.8), agora);
        assertEquals(0, r.get("total"));
        assertEquals("BAIXO", r.get("risco"));
        assertEquals(List.of(), r.get("faltando"));
    }

    @Test
    void zeroOuForaDaFaixaContaComoNaoAferido() {
        // SpO2 0 = nao aferido; FC 900 = erro de digitacao: nao pontuam nem escondem alerta.
        Map<String, Object> r = service.calcular(afericao(18d, 0d, 130d, 900d, 36.5), agora);
        assertEquals(0, r.get("total"));
        assertEquals(List.of("SpO2", "FC"), r.get("faltando"));
    }

    @Test
    void menosDeTresParametrosNaoCalcula() {
        Map<String, Object> r = service.calcular(afericao(18d, 0d, 0d, 0d, 36.5), agora);
        assertNull(r.get("total"));
        assertEquals("INSUFICIENTE", r.get("risco"));
    }

    @Test
    void tendenciaDePioraComDoisPontos() {
        assertEquals("PIORA", DeterioracaoNews2Service.tendencia(5, 3));
        assertEquals("MELHORA", DeterioracaoNews2Service.tendencia(1, 4));
        assertEquals("ESTAVEL", DeterioracaoNews2Service.tendencia(4, 3));
        assertEquals("SEM_COMPARACAO", DeterioracaoNews2Service.tendencia(null, 3));
    }

    @Test
    void lerDataNosFormatosGravadosNoBanco() {
        assertEquals(LocalDateTime.of(2026, 10, 29, 6, 30), DeterioracaoNews2Service.dataHora("29/10/2026", "06:30"));
        assertEquals(LocalDateTime.of(2026, 10, 29, 6, 30), DeterioracaoNews2Service.dataHora("2026-10-29", "06:30:00"));
        assertEquals(LocalDateTime.of(2026, 10, 29, 0, 0), DeterioracaoNews2Service.dataHora("29/10/26", ""));
        assertNull(DeterioracaoNews2Service.dataHora("", "06:30"));
        assertTrue(DeterioracaoNews2Service.AVISO_PARCIAL.contains("O2 suplementar"));
    }

    @Test
    void o2EConscienciaRegistradosEntramNoEscore() {
        // Sinais normais (0) + O2 suplementar (2) + confuso (3) = 5, e um parametro 3 ja e risco medio.
        var a = new DeterioracaoNews2Service.Afericao(1, "29/10/2026", "13:00", 16d, 98d, 120d, 80d, 36.8, true, "C");
        Map<String, Object> r = service.calcular(a, agora);
        assertEquals(5, r.get("total"));
        assertEquals("MEDIO", r.get("risco"));
        assertNull(r.get("aviso"));
    }

    @Test
    void semO2EConscienciaMantemAvisoDeParcial() {
        Map<String, Object> r = service.calcular(afericao(16d, 98d, 120d, 80d, 36.8), agora);
        assertEquals(DeterioracaoNews2Service.AVISO_PARCIAL, r.get("aviso"));
    }

    @Test
    void qsofaPositivoComFrEPasAlteradas() {
        Map<String, Object> r = service.calcular(afericao(24d, 95d, 98d, 110d, 38.4), agora);
        Map<?, ?> q = (Map<?, ?>) r.get("qsofa");
        assertEquals(2, q.get("total"));
        assertEquals(true, q.get("positivo"));
        assertEquals(List.of("Consciência"), q.get("naoAvaliados"));
    }

    @Test
    void qsofaComUmCriterioESemConscienciaEhPossivel() {
        Map<?, ?> q = DeterioracaoNews2Service.qsofa(24d, 120d, null);
        assertEquals(1, q.get("total"));
        assertEquals(false, q.get("positivo"));
        assertEquals(true, q.get("possivel"));
    }

    @Test
    void qsofaComConscienciaAlteradaENormalNosDemais() {
        Map<?, ?> q = DeterioracaoNews2Service.qsofa(16d, 120d, "V");
        assertEquals(1, q.get("total"));
        assertEquals(false, q.get("possivel"));
        assertEquals(3, ((Map<?, ?>) DeterioracaoNews2Service.qsofa(22d, 100d, "C")).get("total"));
        assertEquals(0, DeterioracaoNews2Service.qsofa(null, null, null).get("total"));
    }
}

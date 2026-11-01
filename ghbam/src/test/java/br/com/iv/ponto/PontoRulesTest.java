package br.com.iv.ponto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PontoRulesTest {

    private static final LocalDateTime D = LocalDateTime.of(2026, 10, 30, 7, 0);

    @Test
    void alternaEntradaESaidaDentroDaJanela() {
        assertEquals("ENTRADA", PontoRules.proximoTipo(null, D));
        assertEquals("SAIDA", PontoRules.proximoTipo(new PontoRules.Marca("ENTRADA", D), D.plusHours(12)));
        assertEquals("SAIDA", PontoRules.proximoTipo(new PontoRules.Marca("ENTRADA", D), D.plusHours(24)));
        // esqueceu de bater a saida ontem: hoje abre nova jornada
        assertEquals("ENTRADA", PontoRules.proximoTipo(new PontoRules.Marca("ENTRADA", D), D.plusHours(30)));
        assertEquals("ENTRADA", PontoRules.proximoTipo(new PontoRules.Marca("SAIDA", D), D.plusHours(1)));
    }

    @Test
    void bloqueiaDuplicadaEmPoucosMinutos() {
        assertTrue(PontoRules.duplicada(new PontoRules.Marca("ENTRADA", D), D.plusMinutes(1)));
        assertFalse(PontoRules.duplicada(new PontoRules.Marca("ENTRADA", D), D.plusMinutes(5)));
    }

    @Test
    void pareiaJornadasEMarcaIncompletas() {
        List<PontoRules.Par> pares = PontoRules.parear(List.of(
                new PontoRules.Marca("ENTRADA", D),
                new PontoRules.Marca("SAIDA", D.plusHours(12)),
                new PontoRules.Marca("ENTRADA", D.plusDays(2)),
                new PontoRules.Marca("SAIDA", D.plusDays(4))));
        assertEquals(2, pares.size());
        assertEquals(720, pares.get(0).minutos());
        assertFalse(pares.get(0).incompleto());
        assertEquals(720, PontoRules.minutosTrabalhados(List.of(pares.get(0))));
        List<PontoRules.Par> semSaida = PontoRules.parear(List.of(new PontoRules.Marca("ENTRADA", D)));
        assertTrue(semSaida.get(0).incompleto());
    }

    @Test
    void previstoRecortaOPeriodo() {
        LocalDateTime inicioMes = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime fimMes = LocalDateTime.of(2026, 12, 1, 0, 0);
        // plantao 31/10 19h -> 01/11 07h: so 7h caem em novembro
        long previsto = PontoRules.minutosPrevistos(List.of(
                new PontoRules.Intervalo(LocalDateTime.of(2026, 10, 31, 19, 0), LocalDateTime.of(2026, 11, 1, 7, 0)),
                new PontoRules.Intervalo(LocalDateTime.of(2026, 11, 3, 7, 0), LocalDateTime.of(2026, 11, 3, 19, 0))),
                inicioMes, fimMes);
        assertEquals((7 + 12) * 60, previsto);
        assertEquals("-1h05", PontoRules.horas(-65));
    }
}

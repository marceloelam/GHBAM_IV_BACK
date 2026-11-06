package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ClassificacaoCasoEpidemiologicoTest {

    private static List<Object[]> lista(Object[]... linhas) {
        return new ArrayList<>(Arrays.asList(linhas));
    }

    /** Dados ficticios: so as colunas que a classificacao usa. */
    private static Object[] linha(String fonte, String paciente, String bam, String casos, String cumprem, String citam) {
        Object[] l = new Object[16];
        l[0] = fonte; l[1] = paciente; l[2] = bam; l[13] = casos; l[14] = cumprem; l[15] = citam;
        return l;
    }

    @Test
    void descartadoSaiDaContagem() {
        List<Object[]> linhas = lista(linha("GHBAM", "1", "100", "0", "0", "0"));
        long[][] resumo = ClassificacaoCasoEpidemiologico.aplicar(linhas, List.of(Map.of("100", "DESCARTADO")));
        assertEquals("", linhas.get(0)[13]);
        assertEquals("", linhas.get(0)[14]);
        assertEquals("", linhas.get(0)[15]);
        assertArrayEquals(new long[] { 0, 0, 1 }, resumo[0]);
    }

    @Test
    void pacienteComOutroAtendimentoContandoNaoEDescartado() {
        List<Object[]> linhas = lista(linha("GHBAM", "1", "100", "0", "0", "0"), linha("GHBAM", "1", "101", "0", "0", "0"));
        long[][] resumo = ClassificacaoCasoEpidemiologico.aplicar(linhas, List.of(Map.of("100", "DESCARTADO")));
        assertEquals("0", linhas.get(1)[13]);
        assertArrayEquals(new long[] { 0, 0, 0 }, resumo[0]);
    }

    @Test
    void confirmadoForaDaDefinicaoPassaAContar() {
        List<Object[]> linhas = lista(linha("GHBAM", "1", "100", "", "", "0"));
        long[][] resumo = ClassificacaoCasoEpidemiologico.aplicar(linhas, List.of(Map.of("100", "CONFIRMADO")));
        assertEquals("0", linhas.get(0)[13]);
        assertEquals("0", linhas.get(0)[14]);
        assertArrayEquals(new long[] { 1, 0, 0 }, resumo[0]);
    }

    @Test
    void confirmadoValeSobreProvavelDoMesmoPaciente() {
        List<Object[]> linhas = lista(linha("GHBAM", "1", "100", "0", "0", "0"), linha("GHBAM", "1", "101", "0", "0", "0"));
        long[][] resumo = ClassificacaoCasoEpidemiologico.aplicar(linhas, List.of(Map.of("100", "PROVAVEL", "101", "CONFIRMADO")));
        assertArrayEquals(new long[] { 1, 0, 0 }, resumo[0]);
    }

    @Test
    void classificacaoValeSoParaARegraDela() {
        List<Object[]> linhas = lista(linha("GHBAM", "1", "100", "0,1", "0,1", "0,1"));
        ClassificacaoCasoEpidemiologico.aplicar(linhas, List.of(Map.of("100", "DESCARTADO"), Map.of()));
        assertEquals("1", linhas.get(0)[13]);
    }

    @Test
    void gpecNaoEAfetado() {
        List<Object[]> linhas = lista(linha("GPEC", "GPEC-1", "100", "0", "0", "0"));
        ClassificacaoCasoEpidemiologico.aplicar(linhas, List.of(Map.of("100", "DESCARTADO")));
        assertEquals("0", linhas.get(0)[13]);
    }

    @Test
    void pacienteMostraADecisaoMaisForte() {
        assertEquals("SUSPEITO", ClassificacaoCasoEpidemiologico.maisForte("", null));
        assertEquals("PROVAVEL", ClassificacaoCasoEpidemiologico.maisForte("SUSPEITO", "PROVAVEL"));
        assertEquals("CONFIRMADO", ClassificacaoCasoEpidemiologico.maisForte("CONFIRMADO", "PROVAVEL"));
    }

    @Test
    void validacao() {
        assertNull(ClassificacaoCasoEpidemiologico.validar("CONFIRMADO", "LABORATORIAL", "NS1 reagente"));
        assertNull(ClassificacaoCasoEpidemiologico.validar("DESCARTADO", "", "Sorologia não reagente"));
        assertNotNull(ClassificacaoCasoEpidemiologico.validar("CONFIRMADO", "", "NS1 reagente"));
        assertNotNull(ClassificacaoCasoEpidemiologico.validar("DESCARTADO", "", " "));
        assertNotNull(ClassificacaoCasoEpidemiologico.validar("OUTRA", "", "x"));
        assertNotNull(ClassificacaoCasoEpidemiologico.validar("PROVAVEL", "OUTRO", "x"));
    }
}

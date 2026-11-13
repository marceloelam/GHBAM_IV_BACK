package br.com.iv.aps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Campos obrigatorios do LEDI 8.7 que o serializador nao acusa (optional no .thrift) e que o
 * Centralizador rejeitaria: turno em FP/FAD/FAE/FCZM, data/hora na FP, regras do AD (dic-fad).
 */
class ApsCdsObrigatoriosTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 3);

    private static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> filho(Map<String, Object> dados, String lista) {
        return ((List<Map<String, Object>>) dados.get(lista)).get(0);
    }

    @Test
    void procedimentoRecebeTurnoEDataHora() {
        Map<String, Object> d = mapa("atendProcedimentos", new ArrayList<>(List.of(mapa("procedimentos", List.of("0301100039")))));
        ApsCdsService.completarObrigatorios(7, d, DIA);
        Map<String, Object> c = filho(d, "atendProcedimentos");
        assertEquals(2, c.get("turno"));
        assertEquals("2026-11-03T11:50", c.get("dataHoraInicialAtendimento"));
        assertEquals("2026-11-03T12:00", c.get("dataHoraFinalAtendimento"));
    }

    @Test
    void elegibilidadeEZikaRecebemTurnoNaRaiz() {
        for (int tipo : new int[] { 11, 13 }) {
            Map<String, Object> d = mapa("cid10Principal", "G80");
            ApsCdsService.completarObrigatorios(tipo, d, DIA);
            assertNotNull(d.get("turno"), "tipo " + tipo);
        }
    }

    @Test
    void adRemoveSituacaoSemUuidEExigeModalidade() {
        Map<String, Object> prob = mapa("ciap", "K86", "situacao", 0, "isAvaliado", true);
        Map<String, Object> d = mapa("atendimentosDomiciliares", new ArrayList<>(List.of(mapa(
                "tipoAtendimento", 7, "atencaoDomiciliarModalidade", 1, "condutaDesfecho", 1,
                "problemasCondicoes", new ArrayList<>(List.of(prob))))));
        ApsCdsService.completarObrigatorios(10, d, DIA);
        assertFalse(prob.containsKey("situacao"));
        assertNotNull(filho(d, "atendimentosDomiciliares").get("turno"));

        Map<String, Object> semModalidade = mapa("atendimentosDomiciliares", new ArrayList<>(List.of(mapa(
                "tipoAtendimento", 7, "condutaDesfecho", 1, "problemasCondicoes", List.of(mapa("ciap", "K86"))))));
        assertThrows(IllegalArgumentException.class, () -> ApsCdsService.completarObrigatorios(10, semModalidade, DIA));
    }

    @Test
    void marcadorSemCpfSempreVai() {
        Map<String, Object> comCpf = ApsLediBuilder.identificacao(mapa("cpf", "52998224725"), ApsLediBuilder.Estilo.PADRAO);
        assertEquals(false, comCpf.get("stCidadaoNaoPossuiCpf"));
        Map<String, Object> soCns = ApsLediBuilder.identificacao(mapa("cns", "898001160655528"), ApsLediBuilder.Estilo.VACINA);
        assertEquals(false, soCns.get("stNaoPossuiCpf"));
        Map<String, Object> justificado = ApsLediBuilder.identificacao(mapa("dados",
                Map.of("identificacaoUsuarioCidadao", Map.of("justificativaNaoPossuiCpf", 1))), ApsLediBuilder.Estilo.PADRAO);
        assertEquals(true, justificado.get("stCidadaoNaoPossuiCpf"));
        assertEquals(1, justificado.get("justificativaCidadaoNaoPossuiCpf"));
    }

    @Test
    void adPosObitoDescartaModalidadeEProcedimentos() {
        Map<String, Object> d = mapa("atendimentosDomiciliares", new ArrayList<>(List.of(mapa(
                "tipoAtendimento", 9, "atencaoDomiciliarModalidade", 1, "procedimentos", List.of("0301050104"),
                "condutaDesfecho", 1, "problemasCondicoes", List.of(mapa("cid10", "R99"))))));
        ApsCdsService.completarObrigatorios(10, d, DIA);
        Map<String, Object> c = filho(d, "atendimentosDomiciliares");
        assertFalse(c.containsKey("atencaoDomiciliarModalidade"));
        assertFalse(c.containsKey("procedimentos"));
        assertTrue(c.containsKey("turno"));
    }
}

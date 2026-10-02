package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.PedidoResultadoLaboratorio;
import br.com.iv.entity.PedidoResultadoLaboratorioItem;

class FhirResultadoLaboratorioMapperServiceTest {

    private static Bam bam() {
        Pacientes paciente = new Pacientes();
        paciente.setNome("Paciente Teste");
        paciente.setCpf("12345678900");
        Bam bam = new Bam();
        bam.setIdbam(42);
        bam.setPaciente(paciente);
        return bam;
    }

    private static PedidoResultadoLaboratorio resultado() {
        PedidoResultadoLaboratorio resultado = new PedidoResultadoLaboratorio();
        resultado.setIdPedidoResultado(7);
        resultado.setDescricao("Hemograma");
        resultado.setDt("2026/09/30");
        resultado.setHr("10:30");
        resultado.setGrupopedidos("Hemácias e leucócitos");
        return resultado;
    }

    private static PedidoResultadoLaboratorioItem item(String nome, String loinc, String valor, String unidade) {
        PedidoResultadoLaboratorioItem item = new PedidoResultadoLaboratorioItem();
        item.setNomeExame(nome);
        item.setCodigoLoinc(loinc);
        item.setValor(valor);
        item.setUnidadeMedida(unidade);
        return item;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> observations(Map<String, Object> bundle) {
        return ((List<Map<String, Object>>) bundle.get("entry")).stream()
                .map(e -> (Map<String, Object>) e.get("resource"))
                .filter(r -> "Observation".equals(r.get("resourceType")))
                .toList();
    }

    @Test
    void montaBundleCollectionComPatientEncounterReportEObservation() {
        FhirResultadoLaboratorioMapperService mapper = new FhirResultadoLaboratorioMapperService();
        mapper.carregarItens = id -> List.of();

        Map<String, Object> bundle = mapper.montarBundle(42, bam(), List.of(resultado()));

        assertEquals("Bundle", bundle.get("resourceType"));
        assertEquals("document", bundle.get("type"));
        List<?> entries = (List<?>) bundle.get("entry");
        assertEquals(4, entries.size());
        assertNotNull(entries.stream().filter(e -> ((Map<?, ?>) ((Map<?, ?>) e).get("resource")).get("resourceType").equals("Composition")).findFirst().orElse(null));
        assertEquals(1, observations(bundle).size());
    }

    @Test
    void umObservationPorExameDoLaudoComValorDiretoNoRecurso() {
        FhirResultadoLaboratorioMapperService mapper = new FhirResultadoLaboratorioMapperService();
        mapper.carregarItens = id -> List.of(
                item("Glicose", "2345-7", "95", "mg/dL"),
                item("Exame desconhecido", null, "Reagente", null));

        List<Map<String, Object>> obs = observations(mapper.montarBundle(42, bam(), List.of(resultado())));

        assertEquals(2, obs.size());
        Map<String, Object> glicose = obs.get(0);
        // FHIR R4: valueQuantity direto no Observation, nao aninhado em "value".
        assertFalse(glicose.containsKey("value"));
        Map<?, ?> quantidade = (Map<?, ?>) glicose.get("valueQuantity");
        assertEquals(new BigDecimal("95"), quantidade.get("value"));
        assertEquals("mg/dL", quantidade.get("unit"));
        assertTrue(glicose.get("code").toString().contains("2345-7"));
        assertEquals("Reagente", obs.get(1).get("valueString"));

        List<String> pendencias = new FhirR4PayloadValidator().avaliarResultadoLaboratorial(
                mapper.montarBundle(42, bam(), List.of(resultado())));
        assertTrue(pendencias.contains("Exame sem codigo LOINC: Exame desconhecido"));
        assertFalse(pendencias.contains("Exame sem codigo LOINC: Glicose"));
    }
}

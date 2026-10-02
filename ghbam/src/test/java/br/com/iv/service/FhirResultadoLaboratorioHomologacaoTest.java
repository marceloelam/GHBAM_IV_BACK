package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FhirResultadoLaboratorioHomologacaoTest {

    private final FhirR4PayloadValidator validator = new FhirR4PayloadValidator();

    @Test
    void identificaPendenciasSemInventarDadosClinicos() {
        List<String> pendencias = validator.avaliarResultadoLaboratorial(Map.of(
                "resourceType", "Bundle",
                "type", "document",
                "timestamp", "2026-09-30T10:00:00",
                "entry", List.of(Map.of("resource", Map.of(
                        "resourceType", "Observation",
                        "meta", Map.of("profile", List.of(
                                "https://rnds-fhir.saude.gov.br/StructureDefinition/BRDiagnosticoLaboratorioClinico-3.2.1")),
                        "code", Map.of("text", "Hemograma"))))));

        assertTrue(pendencias.contains("Composition ausente"));
        assertTrue(pendencias.contains("Organization/CNES da unidade ausente"));
        assertTrue(pendencias.contains("Observation sem valor estruturado ou dataAbsentReason"));
        assertTrue(pendencias.contains("Observation sem referencia de amostra biologica"));
        // Nome sem LOINC trava o envio automatico.
        assertTrue(pendencias.contains("Exame sem codigo LOINC: Hemograma"));
    }

    @Test
    void aceitaBaseDocumentalQuandoRecursosMinimosEstaoPresentes() {
        Map<String, Object> composition = Map.of(
                "resourceType", "Composition",
                "meta", Map.of("profile", List.of(
                        "https://rnds-fhir.saude.gov.br/StructureDefinition/BRResultadoExameLaboratorial-3.2.1")),
                "type", Map.of("coding", List.of(Map.of("code", "REL"))));
        Map<String, Object> observation = Map.of(
                "resourceType", "Observation",
                "meta", Map.of("profile", List.of(
                        "https://rnds-fhir.saude.gov.br/StructureDefinition/BRDiagnosticoLaboratorioClinico-3.2.1")),
                "code", Map.of("coding", List.of(Map.of("code", "718-7"))),
                "valueString", "resultado",
                "specimen", Map.of("reference", "Specimen/1"));

        List<String> pendencias = validator.avaliarResultadoLaboratorial(Map.of(
                "resourceType", "Bundle", "type", "document", "timestamp", "2026-09-30T10:00:00",
                "entry", List.of(
                        Map.of("resource", composition),
                        Map.of("resource", Map.of("resourceType", "Patient")),
                        Map.of("resource", Map.of("resourceType", "Organization")),
                        Map.of("resource", observation))));

        assertTrue(pendencias.isEmpty());
    }
}

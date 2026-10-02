package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FhirR4PayloadValidatorTest {

    private final FhirR4PayloadValidator validator = new FhirR4PayloadValidator();

    @Test
    void aceitaBundleMinimoConforme() {
        assertDoesNotThrow(() -> validator.validarSumarioAlta(bundle(true)));
    }

    @Test
    void rejeitaSecaoObrigatoriaSemDadoEstruturado() {
        assertThrows(FhirPayloadValidationException.class, () -> validator.validarSumarioAlta(bundle(false)));
    }

    private Map<String, Object> bundle(boolean dadosEstruturados) {
        Map<String, Object> composition = new LinkedHashMap<>();
        composition.put("resourceType", "Composition");
        composition.put("meta", Map.of("profile", List.of("https://br-core.saude.gov.br/fhir/StructureDefinition/br-core-sumarioalta")));
        composition.put("status", "final");
        composition.put("title", "Sumario Internacional do Paciente");
        composition.put("subject", Map.of("reference", "Patient/p1"));
        composition.put("section", List.of(
                section("42347-5", dadosEstruturados), section("48765-2", dadosEstruturados),
                section("57852-6", dadosEstruturados), section("47519-4", dadosEstruturados),
                section("8654-6", dadosEstruturados), section("18776-5", dadosEstruturados),
                section("54522-8", dadosEstruturados)));

        return Map.of("resourceType", "Bundle", "type", "document", "timestamp", "2026-09-22T10:00:00Z",
                "entry", List.of(entry("Composition/c1", composition), entry("Patient/p1", Map.of("resourceType", "Patient")),
                        entry("Encounter/e1", Map.of("resourceType", "Encounter")), entry("Provenance/a1", Map.of("resourceType", "Provenance"))));
    }

    private Map<String, Object> section(String codigo, boolean dadosEstruturados) {
        return Map.of("code", Map.of("coding", List.of(Map.of("code", codigo))),
                "text", Map.of("div", dadosEstruturados ? "<div>Informacao clinica</div>" : "<div>Sem registro estruturado disponivel no GHBAM para esta secao.</div>"));
    }

    private Map<String, Object> entry(String fullUrl, Map<String, Object> resource) {
        return Map.of("fullUrl", "urn:ghbam:" + fullUrl, "resource", resource);
    }
}

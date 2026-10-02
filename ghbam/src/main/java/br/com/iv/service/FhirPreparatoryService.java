package br.com.iv.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.entity.AssinaturaClinica;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FhirPreparatoryService {

    @Inject
    AssinaturaClinicaService assinaturaClinicaService;

    public Map<String, Object> capabilityStatement() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resourceType", "CapabilityStatement");
        body.put("status", "draft");
        body.put("kind", "capability");
        body.put("fhirVersion", "4.0.1");
        body.put("format", List.of("json"));
        body.put("publisher", "GHBAM IV");
        body.put("purpose", "Camada preparatoria para mapeamento RNDS/FHIR. Nao substitui integracao oficial com a RNDS.");
        body.put("implementation", Map.of("description", "GHBAM IV FHIR preparatorio"));
        return body;
    }

    public Map<String, Object> assinaturasPorBam(String tipoDocumento, Integer idbam) {
        List<AssinaturaClinica> assinaturas = assinaturaClinicaService.listarPorBam(tipoDocumento.toUpperCase(), idbam);
        return bundle(assinaturas);
    }

    public Map<String, Object> assinaturasPorDocumento(String tipoDocumento, Integer iddocumento) {
        List<AssinaturaClinica> assinaturas = assinaturaClinicaService.listarPorDocumento(tipoDocumento.toUpperCase(), iddocumento);
        return bundle(assinaturas);
    }

    // Exposto para FhirSumarioAltaMapperService reaproveitar a mesma logica de
    // Provenance/assinatura, em vez de duplicar.
    public Map<String, Object> provenancePublico(AssinaturaClinica assinatura) {
        return provenance(assinatura);
    }

    private Map<String, Object> bundle(List<AssinaturaClinica> assinaturas) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "collection");
        bundle.put("total", assinaturas.size());
        bundle.put("entry", assinaturas.stream()
                .map(item -> Map.of("resource", provenance(item)))
                .toList());
        return bundle;
    }

    private Map<String, Object> provenance(AssinaturaClinica assinatura) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Provenance");
        resource.put("id", String.valueOf(assinatura.getIdassinatura()));
        resource.put("recorded", toInstantString(assinatura));
        resource.put("activity", Map.of("text", safe(assinatura.getStatus())));
        resource.put("target", List.of(target(assinatura)));
        resource.put("agent", List.of(agent(assinatura)));
        resource.put("signature", List.of(signature(assinatura)));
        resource.put("entity", List.of(entity(assinatura)));
        return resource;
    }

    private Map<String, Object> target(AssinaturaClinica assinatura) {
        String id = assinatura.getIddocumento() != null
                ? "DocumentReference/" + safe(assinatura.getTipoDocumento()) + "-" + assinatura.getIddocumento()
                : "Encounter/bam-" + assinatura.getIdbam();
        return Map.of("reference", id);
    }

    private Map<String, Object> agent(AssinaturaClinica assinatura) {
        String nome = "ADENDO".equalsIgnoreCase(assinatura.getStatus()) ? assinatura.getAdendoPor() : assinatura.getAssinadoPor();
        String matricula = "ADENDO".equalsIgnoreCase(assinatura.getStatus())
                ? assinatura.getAdendoPorMatricula()
                : assinatura.getAssinadoPorMatricula();
        return Map.of(
                "type", Map.of("text", "author"),
                "who", Map.of(
                        "reference", "Practitioner/" + safe(matricula),
                        "display", safe(nome)));
    }

    private Map<String, Object> signature(AssinaturaClinica assinatura) {
        String nome = "ADENDO".equalsIgnoreCase(assinatura.getStatus()) ? assinatura.getAdendoPor() : assinatura.getAssinadoPor();
        return Map.of(
                "type", List.of(Map.of("code", "1.2.840.10065.1.12.1.1", "display", "Author's Signature")),
                "when", toInstantString(assinatura),
                "who", Map.of("display", safe(nome)),
                "sigFormat", "application/vnd.ghbam.sha256",
                "data", safe(assinatura.getConteudoHash()));
    }

    private Map<String, Object> entity(AssinaturaClinica assinatura) {
        Map<String, Object> what = new LinkedHashMap<>();
        what.put("identifier", Map.of(
                "system", "urn:ghbam:assinatura-clinica",
                "value", safe(assinatura.getConteudoHash())));
        what.put("display", safe(assinatura.getTipoDocumento()));
        return Map.of("role", "source", "what", what);
    }

    private String toInstantString(AssinaturaClinica assinatura) {
        var value = "ADENDO".equalsIgnoreCase(assinatura.getStatus()) ? assinatura.getAdendoEm() : assinatura.getAssinadoEm();
        if (value == null) {
            return "";
        }
        // Horario local do GHBAM (nao UTC), com segundos e fuso como o FHIR exige.
        return FhirDataHora.formatar(value);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

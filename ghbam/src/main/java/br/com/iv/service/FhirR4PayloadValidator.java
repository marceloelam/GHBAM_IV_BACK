package br.com.iv.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FhirR4PayloadValidator {

    private static final String SUMARIO_ALTA_PROFILE =
            "https://br-core.saude.gov.br/fhir/StructureDefinition/br-core-sumarioalta";
    private static final List<String> SECOES_SUMARIO_ALTA = List.of(
            "42347-5", "48765-2", "57852-6", "47519-4", "8654-6", "18776-5", "54522-8");

    public void validarSumarioAlta(Map<String, Object> bundle) {
        List<String> erros = new ArrayList<>();
        if (bundle == null) {
            throw new FhirPayloadValidationException(List.of("Bundle ausente"));
        }
        exigir(bundle, "resourceType", "Bundle", erros);
        exigir(bundle, "type", "document", erros);
        exigirTexto(bundle, "timestamp", "timestamp ausente", erros);

        List<Map<String, Object>> entries = listaMapas(bundle.get("entry"));
        if (entries.isEmpty()) erros.add("Bundle sem entry");

        Map<String, Object> composition = null;
        boolean patient = false;
        boolean encounter = false;
        boolean provenance = false;
        for (Map<String, Object> entry : entries) {
            Map<String, Object> resource = mapa(entry.get("resource"));
            if (resource == null) { erros.add("entry sem resource"); continue; }
            String tipo = texto(resource.get("resourceType"));
            if ("Composition".equals(tipo)) composition = resource;
            if ("Patient".equals(tipo)) patient = true;
            if ("Encounter".equals(tipo)) encounter = true;
            if ("Provenance".equals(tipo)) provenance = true;
            if (!entry.containsKey("fullUrl") || texto(entry.get("fullUrl")).isBlank()) {
                erros.add("entry sem fullUrl");
            }
        }
        if (composition == null) erros.add("Composition ausente");
        if (!patient) erros.add("Patient ausente");
        if (!encounter) erros.add("Encounter ausente");
        if (!provenance) erros.add("Provenance ausente");

        if (composition != null) {
            validarComposition(composition, erros);
        }
        if (!erros.isEmpty()) throw new FhirPayloadValidationException(erros);
    }

    /**
     * Avalia a prontidao do resultado laboratorial sem bloquear o Bundle
     * preparatorio. Os dados ausentes dependem de fonte clinica/configuracao e
     * nao podem ser preenchidos por inferencia.
     */
    public List<String> avaliarResultadoLaboratorial(Map<String, Object> bundle) {
        List<String> pendencias = new ArrayList<>();
        if (bundle == null) {
            return List.of("Bundle ausente");
        }
        exigir(bundle, "resourceType", "Bundle", pendencias);
        exigir(bundle, "type", "document", pendencias);
        exigirTexto(bundle, "timestamp", "timestamp ausente", pendencias);

        List<Map<String, Object>> entries = listaMapas(bundle.get("entry"));
        Map<String, Object> composition = null;
        boolean patient = false;
        boolean organization = false;
        int observations = 0;
        for (Map<String, Object> entry : entries) {
            Map<String, Object> resource = mapa(entry.get("resource"));
            if (resource == null) continue;
            switch (texto(resource.get("resourceType"))) {
                case "Composition" -> composition = resource;
                case "Patient" -> patient = true;
                case "Organization" -> organization = true;
                case "Observation" -> {
                    observations++;
                    avaliarObservation(resource, pendencias);
                }
                default -> { }
            }
        }
        if (composition == null) pendencias.add("Composition ausente");
        if (!patient) pendencias.add("Patient ausente");
        if (!organization) pendencias.add("Organization/CNES da unidade ausente");
        if (observations == 0) pendencias.add("Observation ausente");
        if (composition != null) {
            Map<String, Object> meta = mapa(composition.get("meta"));
            if (!listaTextos(meta == null ? null : meta.get("profile"))
                    .contains("https://rnds-fhir.saude.gov.br/StructureDefinition/BRResultadoExameLaboratorial-3.2.1")) {
                pendencias.add("Composition sem perfil RNDS de Resultado de Exame Laboratorial");
            }
            Map<String, Object> type = mapa(composition.get("type"));
            List<Map<String, Object>> codings = listaMapas(type == null ? null : type.get("coding"));
            boolean rel = codings.stream().anyMatch(c -> "REL".equals(texto(c.get("code"))));
            if (!rel) pendencias.add("Composition sem tipo documental REL");
        }
        return pendencias.stream().distinct().toList();
    }

    private void avaliarObservation(Map<String, Object> observation, List<String> pendencias) {
        Map<String, Object> meta = mapa(observation.get("meta"));
        if (!listaTextos(meta == null ? null : meta.get("profile"))
                .stream().anyMatch(profile -> profile.contains("BRDiagnosticoLaboratorioClinico"))) {
            pendencias.add("Observation sem perfil RNDS de Diagnostico em Laboratorio Clinico");
        }
        Map<String, Object> code = mapa(observation.get("code"));
        List<Map<String, Object>> codings = listaMapas(code == null ? null : code.get("coding"));
        String codeText = code == null ? "" : texto(code.get("text"));
        // So o nome do exame nao basta para a RNDS: exige o codigo LOINC (trava do preenchimento automatico).
        if (code == null || codings.stream().noneMatch(c -> !texto(c.get("code")).isBlank())) {
            pendencias.add(codeText.isBlank()
                    ? "Observation sem codigo do exame (LOINC)"
                    : "Exame sem codigo LOINC: " + codeText);
        }
        if (!observation.containsKey("valueQuantity") && !observation.containsKey("valueCodeableConcept")
                && !observation.containsKey("valueString") && !observation.containsKey("dataAbsentReason")) {
            pendencias.add("Observation sem valor estruturado ou dataAbsentReason");
        }
        if (!observation.containsKey("specimen")) pendencias.add("Observation sem referencia de amostra biologica");
    }

    private void validarComposition(Map<String, Object> composition, List<String> erros) {
        Map<String, Object> meta = mapa(composition.get("meta"));
        List<String> profiles = listaTextos(meta == null ? null : meta.get("profile"));
        if (!profiles.contains(SUMARIO_ALTA_PROFILE)) erros.add("Composition sem perfil RNDS de Sumario de Alta");
        exigirTexto(composition, "status", "Composition sem status", erros);
        exigirTexto(composition, "title", "Composition sem title", erros);
        Map<String, Object> subject = mapa(composition.get("subject"));
        if (subject == null || texto(subject.get("reference")).isBlank()) erros.add("Composition sem subject");
        List<Map<String, Object>> sections = listaMapas(composition.get("section"));
        List<String> encontrados = new ArrayList<>();
        for (Map<String, Object> section : sections) {
            Map<String, Object> code = mapa(section.get("code"));
            List<Map<String, Object>> codings = listaMapas(code == null ? null : code.get("coding"));
            for (Map<String, Object> coding : codings) {
                String codigo = texto(coding.get("code"));
                if (SECOES_SUMARIO_ALTA.contains(codigo)) encontrados.add(codigo);
            }
            Map<String, Object> text = mapa(section.get("text"));
            String div = texto(text == null ? null : text.get("div"));
            if (div.contains("Sem registro estruturado disponivel")) erros.add("Secao do Sumario de Alta sem dado estruturado");
        }
        for (String codigo : SECOES_SUMARIO_ALTA) if (!encontrados.contains(codigo)) erros.add("Secao obrigatoria ausente: " + codigo);
    }

    private void exigir(Map<String, Object> mapa, String chave, String esperado, List<String> erros) {
        if (!esperado.equals(mapa.get(chave))) erros.add(chave + " invalido");
    }

    private void exigirTexto(Map<String, Object> mapa, String chave, String erro, List<String> erros) {
        if (texto(mapa.get(chave)).isBlank()) erros.add(erro);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapa(Object valor) { return valor instanceof Map<?, ?> m ? (Map<String, Object>) m : null; }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listaMapas(Object valor) {
        if (!(valor instanceof List<?> lista)) return List.of();
        return lista.stream().filter(item -> item instanceof Map<?, ?>).map(item -> (Map<String, Object>) item).toList();
    }

    private String texto(Object valor) { return valor == null ? "" : String.valueOf(valor); }

    private List<String> listaTextos(Object valor) {
        if (!(valor instanceof List<?> lista)) return List.of();
        return lista.stream().map(this::texto).toList();
    }
}

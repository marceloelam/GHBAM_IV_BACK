package br.com.iv.service;

import java.util.List;

public class FhirPayloadValidationException extends RuntimeException {
    public FhirPayloadValidationException(List<String> erros) {
        super("Payload FHIR invalido: " + String.join("; ", erros));
    }
}

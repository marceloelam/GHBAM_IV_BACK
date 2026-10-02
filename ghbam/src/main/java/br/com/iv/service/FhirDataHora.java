package br.com.iv.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Data/hora no formato exigido pelo FHIR R4 (dateTime/instant com hora): sempre com
 * segundos e fuso, ex. 2026-09-30T14:40:00-03:00. LocalDateTime.toString() omite os
 * segundos quando sao zero ("14:40") e nao tem fuso - a RNDS recusa com HTTP 400.
 */
final class FhirDataHora {

    // Horarios do GHBAM sao gravados no horario local (LocalDateTime), sem fuso.
    static final ZoneId FUSO_GHBAM = ZoneId.of("America/Sao_Paulo");

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private FhirDataHora() {
    }

    static String formatar(LocalDateTime valor) {
        return valor == null ? null : valor.atZone(FUSO_GHBAM).format(FORMATO);
    }

    static String agora() {
        return formatar(LocalDateTime.now(FUSO_GHBAM));
    }
}

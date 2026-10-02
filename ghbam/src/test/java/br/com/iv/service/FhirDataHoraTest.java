package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

// Regressao do HTTP 400 da RNDS: "Invalid date/time format: 2022-06-02T14:40".
class FhirDataHoraTest {

    @Test
    void sempreComSegundosEFuso() {
        assertEquals("2022-06-02T14:40:00-03:00", FhirDataHora.formatar(LocalDateTime.of(2022, 6, 2, 14, 40)));
        assertEquals("2026-09-30T08:05:09-03:00", FhirDataHora.formatar(LocalDateTime.of(2026, 9, 30, 8, 5, 9)));
    }

    @Test
    void agoraTambemNoFormatoFhir() {
        assertTrue(FhirDataHora.agora().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[-+]\\d{2}:\\d{2}"));
    }

    @Test
    void nuloContinuaNulo() {
        assertNull(FhirDataHora.formatar(null));
    }
}

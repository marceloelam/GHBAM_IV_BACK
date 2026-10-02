package br.com.iv.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PacientesRepositoryDateTest {

    @Test
    void converteDataDoPostgresqlParaFormatoLegadoDoDto() {
        var data = PacientesRepository.toLegacyDate(LocalDate.of(2026, 7, 29));

        assertEquals("2026-07-29", data.toString());
    }
}

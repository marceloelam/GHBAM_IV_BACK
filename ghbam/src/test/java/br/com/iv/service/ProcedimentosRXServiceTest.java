package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.com.iv.dto.ProcedimentosRXDto;
import br.com.iv.entity.ProcedimentosRX;
import jakarta.persistence.Column;

class ProcedimentosRXServiceTest {

    @Test
    void deveAceitarUltrassonografiaComoModo() {
        ProcedimentosRXDto dto = procedimentoValido();
        dto.setModo("ULTRASSONOGRAFIA");

        assertDoesNotThrow(() -> new ProcedimentosRXService().validar(dto));
    }

    @Test
    void colunaModoDeveComportarUltrassonografia() throws NoSuchFieldException {
        int tamanho = ProcedimentosRX.class
                .getDeclaredField("modo")
                .getAnnotation(Column.class)
                .length();

        assertTrue(tamanho >= "ULTRASSONOGRAFIA".length());
    }

    @Test
    void dtoModoDeveComportarUltrassonografia() throws NoSuchFieldException {
        int tamanho = ProcedimentosRXDto.class
                .getDeclaredField("modo")
                .getAnnotation(Column.class)
                .length();

        assertTrue(tamanho >= "ULTRASSONOGRAFIA".length());
    }

    @Test
    void deveRejeitarModoDesconhecido() {
        ProcedimentosRXDto dto = procedimentoValido();
        dto.setModo("DESCONHECIDO");

        IllegalArgumentException erro = assertThrows(
                IllegalArgumentException.class,
                () -> new ProcedimentosRXService().validar(dto));

        assertEquals("Modo invalido.", erro.getMessage());
    }

    private ProcedimentosRXDto procedimentoValido() {
        ProcedimentosRXDto dto = new ProcedimentosRXDto();
        dto.setCodprocedimento("0201010542");
        dto.setDescricao("BIOPSIA PERCUTANEA ORIENTADA POR IMAGEM");
        dto.setModo("-");
        dto.setModo2("-");
        dto.setCid("0000");
        return dto;
    }
}

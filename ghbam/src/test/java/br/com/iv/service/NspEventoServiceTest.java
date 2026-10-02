package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.com.iv.dto.NspEventoDto;

class NspEventoServiceTest {

    @Test
    void exigeMedicamentoQuandoTipoEhErroDeMedicacao() {
        NspEventoDto dto = new NspEventoDto();

        var erro = assertThrows(IllegalArgumentException.class,
                () -> NspEventoService.validarCamposMedicamento("ERRO_MEDICACAO", dto));

        assertTrue(erro.getMessage().toLowerCase().contains("medicamento"));
    }

    @Test
    void exigeMedicamentoQuandoTipoEhReacaoAdversa() {
        NspEventoDto dto = new NspEventoDto();
        dto.setMedicamento("   ");

        assertThrows(IllegalArgumentException.class,
                () -> NspEventoService.validarCamposMedicamento("REACAO_ADVERSA_MEDICAMENTO", dto));
    }

    @Test
    void aceitaQuandoMedicamentoInformado() {
        NspEventoDto dto = new NspEventoDto();
        dto.setMedicamento("Dipirona 500mg");

        assertDoesNotThrow(() -> NspEventoService.validarCamposMedicamento("ERRO_MEDICACAO", dto));
        assertDoesNotThrow(() -> NspEventoService.validarCamposMedicamento("REACAO_ADVERSA_MEDICAMENTO", dto));
    }

    @Test
    void naoExigeMedicamentoParaOutrosTiposDeIncidente() {
        NspEventoDto dto = new NspEventoDto();

        assertDoesNotThrow(() -> NspEventoService.validarCamposMedicamento("QUEDA", dto));
        assertDoesNotThrow(() -> NspEventoService.validarCamposMedicamento("FLEBITE", dto));
    }

    @Test
    void tiposDeMedicamentoEstaoDentroDosTiposValidosDoNsp() {
        assertTrue(NspEventoService.TIPOS.containsAll(NspEventoService.TIPOS_MEDICAMENTO));
    }
}

package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.iv.dto.ImportarRetornoDto;
import br.com.iv.dto.RetornoItemImportDto;
import br.com.iv.entity.FaturamentoRemessa;
import br.com.iv.enums.RemessaStatus;

// Retorno aprovado marca a producao como faturada: estes testes travam as guardas
// que impedem isso fora de uma remessa transmitida. O cruzamento item a item
// (idafaturar x numero da remessa/competencia/unidade) depende do banco e nao
// e coberto aqui.
class FaturamentoRetornoServiceTest {

    private final FaturamentoRetornoService service = new FaturamentoRetornoService();

    @Test
    void recusaRetornoSemItens() {
        ImportarRetornoDto dto = new ImportarRetornoDto();
        dto.setIdremessa(1);
        dto.setItens(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.importar(dto, null));
    }

    @Test
    void recusaRetornoSemRemessaVinculada() {
        ImportarRetornoDto dto = new ImportarRetornoDto();
        dto.setItens(List.of(new RetornoItemImportDto()));

        assertThrows(IllegalArgumentException.class, () -> service.importar(dto, null));
    }

    @Test
    void aceitaSomenteRemessaJaTransmitida() {
        for (RemessaStatus status : RemessaStatus.values()) {
            FaturamentoRemessa remessa = new FaturamentoRemessa();
            remessa.setStatus(status.name());
            boolean transmitida = status == RemessaStatus.TRANSMITIDA
                    || status == RemessaStatus.PROCESSADA;

            if (transmitida) {
                assertDoesNotThrow(() -> FaturamentoRetornoService.exigirRemessaTransmitida(remessa),
                        "status=" + status);
            } else {
                assertThrows(IllegalArgumentException.class,
                        () -> FaturamentoRetornoService.exigirRemessaTransmitida(remessa), "status=" + status);
            }
        }
    }

    @Test
    void recusaRemessaInexistente() {
        assertThrows(IllegalArgumentException.class, () -> FaturamentoRetornoService.exigirRemessaTransmitida(null));
    }
}

package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.List;

import org.junit.jupiter.api.Test;
import br.com.iv.entity.FaturamentoAih;
import br.com.iv.entity.FaturamentoCompetencia;

// O backend nao tem banco de teste isolado (dev e copia da producao - ver
// FhirSumarioAltaMapperServiceTest), entao FaturamentoAih.list (finder estatico do
// Panache) e mockado em vez de bater no banco real.
//
// Cobre a recusa documentada na propria classe (AihExporter): "sem faixa cadastrada o
// exportador recusa dizendo a razao, em vez de gerar um arquivo que seria rejeitado la na
// frente" - a unica regra de negocio do exportador que nao tinha nenhum teste ate 2026-09-22.
class AihExporterTest {

    private final AihExporter exporter = spy(new AihExporter());

    private FaturamentoCompetencia competencia() {
        FaturamentoCompetencia competencia = new FaturamentoCompetencia();
        competencia.setUnidade("HMSFX");
        competencia.setCompetencia("202609");
        return competencia;
    }

    @Test
    void recusaQuandoNaoHaContaFechadaNaCompetencia() {
        doReturn(List.of()).when(exporter).buscarContas(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> exporter.exportar(List.of(), competencia()));

        assertTrue(erro.getMessage().contains("Nenhuma conta hospitalar fechada"));
    }

    // A regra central da classe: conta fechada sem NU_AIH (faixa nao cadastrada pelo
    // gestor) nao gera arquivo - o exportador recusa em vez de mandar um lote que o
    // SISAIH01 rejeitaria por numeracao ausente ou fora da faixa.
    @Test
    void recusaQuandoContasFechadasNaoTemNumeroDeAih() {
        FaturamentoAih semNumero = new FaturamentoAih();
        semNumero.setNumeroAih("");

        doReturn(List.of(semNumero)).when(exporter).buscarContas(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> exporter.exportar(List.of(), competencia()));

        assertTrue(erro.getMessage().contains("sem numero de AIH"));
    }
}

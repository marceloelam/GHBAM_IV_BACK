package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.Afaturar;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.enums.InstrumentoRegistro;

// Layout oficial do BPA confirmado (circular tecnica FEHOSP 1071/2020,
// reproduzindo o instrutivo DATASUS/SIA) - estes testes travam o contrato de
// posicoes fixas dos registros, para nao regredir silenciosamente se alguem
// mexer no layout depois.
class BpaExportersTest {

    @Test
    void bpaConsolidadoDeclaraLayoutDisponivelEIdentificaInstrumento() {
        BpaConsolidadoExporter exporter = new BpaConsolidadoExporter();
        assertEquals(InstrumentoRegistro.BPA_C, exporter.instrumento());
        assertTrue(exporter.layoutDisponivel());
    }

    @Test
    void bpaConsolidadoRejeitaListaVazia() {
        BpaConsolidadoExporter exporter = new BpaConsolidadoExporter();
        FaturamentoCompetencia competencia = new FaturamentoCompetencia();
        competencia.setCompetencia("202608");
        assertThrows(IllegalArgumentException.class, () -> exporter.exportar(List.of(), competencia));
    }

    @Test
    void bpaConsolidadoGeraRegistroTipo02Com50Caracteres() {
        BpaConsolidadoExporter exporter = new BpaConsolidadoExporter();
        Afaturar producao = new Afaturar();
        producao.setUnidade("");
        producao.setCbo("225125");
        producao.setProcedimento("0301010064");
        producao.setIdade(30);
        producao.setOrigeminfo("bpa");
        FaturamentoCompetencia competencia = new FaturamentoCompetencia();
        competencia.setCompetencia("202608");

        var conteudo = exporter.exportar(List.of(producao), competencia);
        String texto = new String(conteudo.conteudo(), StandardCharsets.ISO_8859_1);
        String[] linhas = texto.split("\r\n");

        assertEquals(1, conteudo.quantidade());
        assertTrue(linhas[0].startsWith("01#BPA#"), "cabecalho deve comecar com 01#BPA#");
        // 50 caracteres no layout oficial = 48 de campos + 2 do terminador CR+LF,
        // que o split() ja removeu daqui.
        assertEquals(48, linhas[1].length(), "registro BPA-C deve ter exatamente 48 caracteres de campos (+2 CR/LF)");
        assertTrue(linhas[1].startsWith("02"), "registro BPA-C deve comecar com identificador 02");
    }

    @Test
    void bpaIndividualizadoDeclaraLayoutDisponivelEIdentificaInstrumento() {
        BpaIndividualizadoExporter exporter = new BpaIndividualizadoExporter();
        assertEquals(InstrumentoRegistro.BPA_I, exporter.instrumento());
        assertTrue(exporter.layoutDisponivel());
    }

    @Test
    void bpaIndividualizadoGeraRegistroTipo03Com340Caracteres() {
        BpaIndividualizadoExporter exporter = new BpaIndividualizadoExporter();
        Afaturar producao = new Afaturar();
        producao.setUnidade("");
        producao.setCbo("225125");
        producao.setProcedimento("0301010064");
        producao.setIdade(30);
        producao.setSexo("M");
        producao.setNomepaciente("PACIENTE TESTE");
        producao.setOrigeminfo("bpa");
        FaturamentoCompetencia competencia = new FaturamentoCompetencia();
        competencia.setCompetencia("202608");

        var conteudo = exporter.exportar(List.of(producao), competencia);
        String texto = new String(conteudo.conteudo(), StandardCharsets.ISO_8859_1);
        String[] linhas = texto.split("\r\n");

        assertEquals(1, conteudo.quantidade());
        // 340 caracteres no layout oficial = 338 de campos + 2 do terminador CR+LF.
        assertEquals(338, linhas[1].length(), "registro BPA-I deve ter exatamente 338 caracteres de campos (+2 CR/LF)");
        assertTrue(linhas[1].startsWith("03"), "registro BPA-I deve comecar com identificador 03");
    }
}

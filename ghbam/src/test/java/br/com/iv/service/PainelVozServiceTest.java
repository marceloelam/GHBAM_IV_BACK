package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PainelVozServiceTest {

    @Test
    void montaFraseNaturalComArtigoESetor() {
        assertEquals("Boa tarde, paciente Maria da Silva. Por favor, compareça ao Consultório 1, no setor de Clínica Médica.",
                PainelVozService.montarTexto("Boa tarde", "MARIA DA SILVA", "CONSULTÓRIO 01", "CLÍNICA MÉDICA"));
        assertEquals("Bom dia, paciente Ana Lima. Por favor, compareça à Sala 3, no setor Azul.",
                PainelVozService.montarTexto("Bom dia", "ANA LIMA", "SALA 3", "AZUL"));
        assertEquals("Bom dia, paciente Ana Lima. Por favor, compareça à u tê i.",
                PainelVozService.montarTexto("Bom dia", "ANA LIMA", "UTI", "UTI"));
        assertEquals("Boa noite, paciente João dos Santos e Souza. Por favor, compareça à Triagem.",
                PainelVozService.montarTexto("Boa noite", "  JOÃO   DOS SANTOS E SOUZA ", "TRIAGEM", ""));
        assertEquals("Bom dia. Por favor, compareça à Sala 2.",
                PainelVozService.montarTexto("Bom dia", "", "SALA 2", null));
    }

    @Test
    void aplicaFrasePersonalizadaDaUnidadeComConcordancia() {
        String modelo = "[Saudação]! Atenção, [paciente], dirija-se [ao local], [no setor].";
        assertEquals("Boa tarde! Atenção, Maria da Silva, dirija-se à Sala 2, no setor de Pediatria.",
                PainelVozService.montarTexto(modelo, "Boa tarde", "MARIA DA SILVA", "SALA 02", "PEDIATRIA"));
        // Sem setor: o marcador some sem deixar ", ." sobrando.
        assertEquals("Boa tarde! Atenção, Maria da Silva, dirija-se ao Consultório 1.",
                PainelVozService.montarTexto(modelo, "Boa tarde", "MARIA DA SILVA", "CONSULTORIO 1", ""));
        // Frase vazia = frase padrão.
        assertEquals(PainelVozService.montarTexto("Bom dia", "ANA LIMA", "SALA 3", "AZUL"),
                PainelVozService.montarTexto("  ", "Bom dia", "ANA LIMA", "SALA 3", "AZUL"));
    }

    @Test
    void recusaMarcadorDesconhecidoNaFrase() {
        assertEquals(null, PainelVozService.validarModeloFrase("   "));
        assertEquals("[saudacao], [nome].", PainelVozService.validarModeloFrase("  [saudacao],\n [nome]. "));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PainelVozService.validarModeloFrase("Olá [pacinte]"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PainelVozService.validarModeloFrase("x".repeat(301)));
    }

    @Test
    void escolheAoOuAConformeOLocal() {
        assertEquals("ao", PainelVozService.artigo("Consultório 3"));
        assertEquals("ao", PainelVozService.artigo("Guichê 2"));
        assertEquals("ao", PainelVozService.artigo("10"));
        assertEquals("à", PainelVozService.artigo("Sala de Medicação"));
        assertEquals("à", PainelVozService.artigo("Recepção"));
        assertEquals("à", PainelVozService.artigo("Coleta"));
        assertEquals("à", PainelVozService.artigo("Hemodiálise"));
        assertEquals("à", PainelVozService.artigo("Uti"));
        assertEquals("ao", PainelVozService.artigo("Eletrocardiograma"));
        assertEquals("ao", PainelVozService.artigo("Trauma"));
    }

    @Test
    void normalizaCaixaAltaEZerosAEsquerda() {
        assertEquals("", PainelVozService.normalizarParaFala(null));
        assertEquals("", PainelVozService.normalizarParaFala("   "));
        assertEquals("De Oliveira", PainelVozService.normalizarParaFala("DE OLIVEIRA"));
        assertEquals("Sala 10", PainelVozService.normalizarParaFala("SALA\t10\n"));
        assertEquals("Consultório 3", PainelVozService.normalizarParaFala("CONSULTÓRIO 003"));
    }

    @Test
    void saudacaoSegueOMesmoCorteDeHorarioDoPainel() {
        assertEquals("Bom dia", PainelVozService.saudacao(11));
        assertEquals("Boa tarde", PainelVozService.saudacao(12));
        assertEquals("Boa tarde", PainelVozService.saudacao(17));
        assertEquals("Boa noite", PainelVozService.saudacao(18));
    }

    @Test
    void conversaoCp1252EntregaOsBytesUtf8DoTextoComAcentos() {
        String texto = "boa noite, joão da conceição. compareça à sala de medicação, no setor de emergência.";
        String convertido = PainelVozService.converterParaCp1252(texto);
        assertArrayEquals(texto.getBytes(StandardCharsets.UTF_8),
                convertido.getBytes(Charset.forName("windows-1252")));
    }
}

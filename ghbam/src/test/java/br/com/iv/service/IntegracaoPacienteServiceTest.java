package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.com.iv.service.IntegracaoPacienteService.Filtro;

class IntegracaoPacienteServiceTest {

    private static final String CHAVE_A = "a".repeat(40);
    private static final String CHAVE_B = "b".repeat(40);
    private static final String CONFIG = "sistemaA|ITG|" + CHAVE_A + ", sistemaB|UPA|" + CHAVE_B;

    @Test
    void chaveValidaIdentificaSistemaEUnidade() {
        var integrador = IntegracaoPacienteService.autenticar(CHAVE_B, CONFIG).orElseThrow();
        assertEquals("sistemaB", integrador.sistema());
        assertEquals("UPA", integrador.unidade());
    }

    @Test
    void chaveAusenteErradaOuSemConfiguracaoNaoAutentica() {
        assertTrue(IntegracaoPacienteService.autenticar(null, CONFIG).isEmpty());
        assertTrue(IntegracaoPacienteService.autenticar("", CONFIG).isEmpty());
        assertTrue(IntegracaoPacienteService.autenticar("c".repeat(40), CONFIG).isEmpty());
        assertTrue(IntegracaoPacienteService.autenticar(CHAVE_A, "").isEmpty());
    }

    @Test
    void chaveCurtaNaConfiguracaoNuncaAutentica() {
        assertTrue(IntegracaoPacienteService.autenticar("curta", "sistema|ITG|curta").isEmpty());
    }

    @Test
    void cpfOuCnsSozinhosSaoBuscasValidas() {
        assertNull(IntegracaoPacienteService.validar(new Filtro(null, null, null, "000.000.000-00", null)));
        assertNull(IntegracaoPacienteService.validar(new Filtro(null, null, null, null, "700000000000000")));
    }

    @Test
    void doisCamposDemograficosSaoBuscaValida() {
        assertNull(IntegracaoPacienteService.validar(new Filtro("FULANO", "31/01/1980", null, null, null)));
        assertNull(IntegracaoPacienteService.validar(new Filtro("FULANO", null, "BELTRANA", null, null)));
        assertNull(IntegracaoPacienteService.validar(new Filtro(null, "1980-01-31", "BELTRANA", null, null)));
    }

    @Test
    void buscaFracaOuMalFormadaERecusada() {
        assertNotNull(IntegracaoPacienteService.validar(new Filtro("FULANO", null, null, null, null)));
        assertNotNull(IntegracaoPacienteService.validar(new Filtro(null, null, null, "123", null)));
        assertNotNull(IntegracaoPacienteService.validar(new Filtro(null, null, null, null, "123")));
        assertNotNull(IntegracaoPacienteService.validar(new Filtro("FULANO", "99/99/1980", null, null, null)));
        // CPF sem nenhum digito nao pode virar busca por cpf vazio.
        assertNotNull(IntegracaoPacienteService.validar(new Filtro("FULANO", null, null, "abc", null)));
    }

    @Test
    void textoBuscaTiraAcentoCuringaEEspacoExtra() {
        assertEquals("JOAO DA SILVA", IntegracaoPacienteService.textoBusca("  João   da%_ Silva "));
    }

    @Test
    void nascimentoDoCadsusViraFormatoDoCadastro() {
        assertEquals("1980/01/31", IntegracaoPacienteService.nascimentoCadsus("19800131"));
        assertEquals("1980/01/31", IntegracaoPacienteService.nascimentoCadsus("19800131000000"));
        assertNull(IntegracaoPacienteService.nascimentoCadsus(""));
    }
}

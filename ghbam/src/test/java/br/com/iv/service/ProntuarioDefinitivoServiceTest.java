package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProntuarioDefinitivoServiceTest {

	@Test
	void pendenteQuandoVazioOuZero() {
		assertTrue(ProntuarioDefinitivoService.pendente(null));
		assertTrue(ProntuarioDefinitivoService.pendente(""));
		assertTrue(ProntuarioDefinitivoService.pendente(" 0 "));
		assertFalse(ProntuarioDefinitivoService.pendente("2026-123"));
	}

	// RN da Admissao: ordem do RN + prontuario da mae (2026-123 -> 12026-123, gemeo 22026-123).
	@Test
	void reconheceFormatoDoRn() {
		assertTrue(ProntuarioDefinitivoService.formatoRn("12026-123"));
		assertTrue(ProntuarioDefinitivoService.formatoRn("22026-123"));
		assertTrue(ProntuarioDefinitivoService.formatoRn(" 12024-9 "));
	}

	@Test
	void definitivoNaoTemFormatoDoRn() {
		assertFalse(ProntuarioDefinitivoService.formatoRn("2026-123"));
		assertFalse(ProntuarioDefinitivoService.formatoRn("02026-123"));
		assertFalse(ProntuarioDefinitivoService.formatoRn("-"));
		assertFalse(ProntuarioDefinitivoService.formatoRn("10"));
		assertFalse(ProntuarioDefinitivoService.formatoRn(null));
	}

	// Mesma regra do "Gerar prontuario" do NIR (GerarProntuario.jsx): ano-idpaciente.
	@Test
	void geraAnoHifenId() {
		assertEquals("2026-128900", ProntuarioDefinitivoService.gerar(128900, 2026));
	}
}

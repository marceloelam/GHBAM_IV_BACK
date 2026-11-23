package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** Regras puras da pendência de cadastro (V264) e da inatividade (V265). */
class ContaServidorRegrasTest {

	@Test
	void normalizaRegistroDeConselhoParaComparar() {
		assertEquals("1858667", PendenciaCadastroService.normalizarRegistro("001858667"));
		assertEquals("436112", PendenciaCadastroService.normalizarRegistro("436112-TE"));
		assertEquals("988904", PendenciaCadastroService.normalizarRegistro(" 988904-ENF "));
		assertEquals("123456", PendenciaCadastroService.normalizarRegistro("123.456"));
		assertEquals("", PendenciaCadastroService.normalizarRegistro("0"));
		assertEquals("", PendenciaCadastroService.normalizarRegistro(null));
	}

	@Test
	void identificaCargosDeEnfermagem() {
		assertTrue(PendenciaCadastroService.ehEnfermagem("TÉC"));
		assertTrue(PendenciaCadastroService.ehEnfermagem("TEC"));
		assertTrue(PendenciaCadastroService.ehEnfermagem("ENFERMEIRO"));
		assertTrue(PendenciaCadastroService.ehEnfermagem("Técnico de enfermagem"));
		assertFalse(PendenciaCadastroService.ehEnfermagem("MÉDICO"));
		assertFalse(PendenciaCadastroService.ehEnfermagem("SERVIDOR"));
		assertFalse(PendenciaCadastroService.ehEnfermagem(null));
	}

	/** Regressão: dentro do Quarkus o Hibernate devolve java.time; o painel mostrava "null dias". */
	@Test
	void converteDatasDoHibernateEDoJdbc() {
		LocalDateTime agora = LocalDateTime.of(2026, 11, 22, 9, 30);
		assertEquals(agora, InatividadeAcessoService.paraDataHora(agora));
		assertEquals(agora, InatividadeAcessoService.paraDataHora(Timestamp.valueOf(agora)));
		assertNull(InatividadeAcessoService.paraDataHora(null));

		LocalDate dia = LocalDate.of(2026, 12, 12);
		assertEquals(dia, InatividadeAcessoService.paraData(dia));
		assertEquals(dia, InatividadeAcessoService.paraData(java.sql.Date.valueOf(dia)));
		assertEquals(dia, InatividadeAcessoService.paraData(dia.atStartOfDay()));
		assertNull(InatividadeAcessoService.paraData(null));
	}
}

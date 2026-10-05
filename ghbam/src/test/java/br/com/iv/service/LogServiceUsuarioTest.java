package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogServiceUsuarioTest {

    @Test
    void reconheceEventoQueJaTrazUsuario() {
        assertTrue(LogService.jaIdentificaUsuario("usuario=Maria Souza | matricula=123 | acao=x", "Maria Souza"));
        assertTrue(LogService.jaIdentificaUsuario("evento x | usuario: Joao", ""));
        assertTrue(LogService.jaIdentificaUsuario("Cirurgia agendada - por MARIA SOUZA (matricula 123)", "Maria Souza"));
    }

    @Test
    void pedeIdentificacaoQuandoSoTemMatriculaOuNada() {
        assertFalse(LogService.jaIdentificaUsuario("123 dispensou farmacia SAIDA #9", "Maria Souza"));
        assertFalse(LogService.jaIdentificaUsuario("CDSS_PREVENTIVO aceitar paciente=5 profissional=123", "Maria Souza"));
        assertFalse(LogService.jaIdentificaUsuario("RAG: pergunta | paciente=Fulano", ""));
    }
}

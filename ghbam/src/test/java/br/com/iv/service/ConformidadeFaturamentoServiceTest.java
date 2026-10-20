package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.Tratamento;

/**
 * Trava a regra que impede faturar consulta que nao aconteceu: o tratamento nasce
 * vazio na triagem, entao so campo preenchido pelo medico autoriza a producao.
 */
class ConformidadeFaturamentoServiceTest {

    @Test
    void semTratamentoNaoFatura() {
        assertFalse(ConformidadeFaturamentoService.temEvidenciaAtendimentoMedico(null));
    }

    @Test
    void tratamentoCriadoNaTriagemNaoFatura() {
        Tratamento t = new Tratamento();
        t.setMedicoecrm("  ");
        assertFalse(ConformidadeFaturamentoService.temEvidenciaAtendimentoMedico(t));
    }

    @Test
    void registroDoMedicoAutorizaFaturamento() {
        Tratamento comMedico = new Tratamento();
        comMedico.setMedicoecrm("MEDICO TESTE - CRM 12345");
        assertTrue(ConformidadeFaturamentoService.temEvidenciaAtendimentoMedico(comMedico));

        Tratamento comDiagnostico = new Tratamento();
        comDiagnostico.setDiagnostico_provisorio("Hipotese diagnostica");
        assertTrue(ConformidadeFaturamentoService.temEvidenciaAtendimentoMedico(comDiagnostico));

        Tratamento tratado = new Tratamento();
        tratado.setTratado(1);
        assertTrue(ConformidadeFaturamentoService.temEvidenciaAtendimentoMedico(tratado));
    }
}

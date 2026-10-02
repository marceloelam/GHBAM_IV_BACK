package br.com.iv.resumoclinico.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import br.com.iv.resumoclinico.dto.*;

class GeradorResumoClinicoLocalTest {
    @Test
    void geraResumoSemModeloExternoEConservaDadosRegistrados() {
        Pacientes paciente = new Pacientes();
        paciente.setIdpaciente(12);
        paciente.setNome("PACIENTE FICTÍCIO");
        paciente.setProntuario("P-100");
        Bam bam = new Bam();
        bam.setIdbam(321);
        bam.setUnidade("UNIDADE TESTE");
        ContextoClinico contexto = new ContextoClinico(
            paciente.getIdpaciente(), paciente.getNome(), paciente.getProntuario(),
            bam.getIdbam(), bam.getUnidade(), String.valueOf(bam.getData()), bam.getHora(),
            TipoResumoClinico.MEDICO, PeriodoResumoClinico.ATENDIMENTO_ATUAL,
            "QUEIXA: dado fictício", List.of(), List.of(), List.of());

        ResumoClinicoGerado resultado = new GeradorResumoClinicoLocal().gerar(contexto);

        assertEquals("LOCAL", resultado.modo());
        assertTrue(resultado.texto().contains("PACIENTE FICTÍCIO"));
        assertTrue(resultado.texto().contains("QUEIXA: dado fictício"));
    }
}

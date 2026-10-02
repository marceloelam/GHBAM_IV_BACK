package br.com.iv.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.ProcedimentosSus;

// Escolha da competencia do SIGTAP. O caso real que originou o teste: a base tinha de
// 202009 a 202608 instaladas e a producao de 2026 era classificada com o pa_tpreg de
// 2020 - procedimento que hoje e BPA-I virava NAO_FATURAVEL e deixava de faturar.
class ProcedimentosSusRepositoryTest {

    private ProcedimentosSus procedimento(String competencia) {
        ProcedimentosSus p = new ProcedimentosSus();
        p.setPa_cmp(competencia);
        return p;
    }

    // Lista sempre chega ordenada por competencia decrescente (ORDER BY pa_cmp DESC).
    private List<ProcedimentosSus> base() {
        return List.of(procedimento("202608"), procedimento("202607"), procedimento("202102"),
                procedimento("202009"));
    }

    @Test
    void prefereACompetenciaExataDaProducao() {
        assertEquals("202607", ProcedimentosSusRepository.escolherPorCompetencia(base(), "202607").getPa_cmp());
    }

    // Competencia sem SIGTAP proprio usa a tabela vigente na epoca - a mais recente
    // anterior a ela, nunca uma posterior.
    @Test
    void semACompetenciaExataUsaAMaisRecenteAnterior() {
        assertEquals("202102", ProcedimentosSusRepository.escolherPorCompetencia(base(), "202305").getPa_cmp());
    }

    // Producao anterior a qualquer tabela instalada: melhor a mais recente que existe do
    // que nada.
    @Test
    void semTabelaAnteriorUsaAMaisRecente() {
        assertEquals("202608", ProcedimentosSusRepository.escolherPorCompetencia(base(), "201901").getPa_cmp());
    }

    // Producao sem competencia vinculada (o mes que ninguem abriu ainda).
    @Test
    void semCompetenciaUsaAMaisRecente() {
        assertEquals("202608", ProcedimentosSusRepository.escolherPorCompetencia(base(), null).getPa_cmp());
        assertEquals("202608", ProcedimentosSusRepository.escolherPorCompetencia(base(), "  ").getPa_cmp());
    }

    @Test
    void procedimentoInexistenteDevolveNulo() {
        assertNull(ProcedimentosSusRepository.escolherPorCompetencia(List.of(), "202608"));
        assertNull(ProcedimentosSusRepository.escolherPorCompetencia(null, "202608"));
    }
}

package br.com.iv.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import br.com.iv.dto.UnidadeResumoDto;
import br.com.iv.repository.FaturamentoProducaoRepository;

// Fase 6 - visao "todas as unidades" (secao 40) do escopo. Reaproveita as mesmas
// tabelas ja usadas nas fases anteriores, sem tabela nova - so agregacoes.
@ApplicationScoped
public class FaturamentoGestaoMunicipalService {

    @Inject
    FaturamentoProducaoRepository producaoRepository;

    public List<UnidadeResumoDto> resumoPorUnidade(int idcompetencia) {
        return resumoPorUnidade(idcompetencia, null);
    }

    public List<UnidadeResumoDto> resumoPorUnidade(int idcompetencia, String unidade) {
        return producaoRepository.resumoPorUnidade(idcompetencia, unidade).stream()
                .map(linha -> new UnidadeResumoDto(
                        (String) linha[0],
                        ((Number) linha[1]).longValue(),
                        ((Number) linha[2]).longValue(),
                        ((Number) linha[3]).longValue(),
                        ((Number) linha[4]).doubleValue(),
                        ((Number) linha[5]).doubleValue(),
                        ((Number) linha[6]).doubleValue()))
                .toList();
    }
}

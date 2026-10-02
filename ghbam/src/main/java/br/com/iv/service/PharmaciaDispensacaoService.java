package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import br.com.iv.entity.PharmaciaDispensacao;
import br.com.iv.entity.PharmaciaEstoque;
import br.com.iv.entity.PharmaciaLote;

@ApplicationScoped
public class PharmaciaDispensacaoService {

    public List<PharmaciaDispensacao> listAll() {
        return PharmaciaDispensacao.listAll();
    }

    public PharmaciaDispensacao findById(Long id) {
        return PharmaciaDispensacao.findById(id);
    }

    public List<PharmaciaDispensacao> findByPacienteId(Long pacienteId) {
        return PharmaciaDispensacao.list("pacienteId = ?1 and ativo = true order by dataHora desc", pacienteId);
    }

    @Transactional
    public PharmaciaDispensacao save(PharmaciaDispensacao dispensacao) {
        if (dispensacao.getLote() != null && dispensacao.getLote().getId() != null) {
            PharmaciaLote lote = PharmaciaLote.findById(dispensacao.getLote().getId());
            dispensacao.setLote(lote);
            if (lote != null && lote.getProduto() != null && dispensacao.getProdutoNome() == null) {
                dispensacao.setProdutoNome(lote.getProduto().getNomeComercial());
            }
        }
        dispensacao.setDataHora(LocalDateTime.now());
        dispensacao.persist();

        // Decrement estoque for the unit
        if (dispensacao.getLote() != null && dispensacao.getUnidadeSigla() != null) {
            PharmaciaEstoque estoque = PharmaciaEstoque.find(
                    "lote.id = ?1 and unidadeSigla = ?2",
                    dispensacao.getLote().getId(), dispensacao.getUnidadeSigla()
            ).firstResult();
            if (estoque != null && dispensacao.getQuantidade() != null) {
                estoque.setQuantidade(estoque.getQuantidade() - dispensacao.getQuantidade());
            }
        }

        return dispensacao;
    }

    @Transactional
    public void devolver(Long dispensacaoId, Long loteId, Integer quantidade, String unidadeSigla) {
        PharmaciaDispensacao dispensacao = PharmaciaDispensacao.findById(dispensacaoId);
        if (dispensacao != null) {
            dispensacao.setAtivo(false);
        }

        // Restore estoque
        if (loteId != null && unidadeSigla != null && quantidade != null) {
            PharmaciaEstoque estoque = PharmaciaEstoque.find(
                    "lote.id = ?1 and unidadeSigla = ?2", loteId, unidadeSigla
            ).firstResult();
            if (estoque != null) {
                estoque.setQuantidade(estoque.getQuantidade() + quantidade);
            }

            // A dispensacao original decrementa lote.quantidadeAtual (via
            // movimentacao); sem isso aqui, a devolucao restaura o estoque
            // mas o lote fica "devendo" a quantidade devolvida para sempre.
            PharmaciaLote lote = PharmaciaLote.findById(loteId);
            if (lote != null) {
                lote.setQuantidadeAtual(lote.getQuantidadeAtual() + quantidade);
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        PharmaciaDispensacao.deleteById(id);
    }
}

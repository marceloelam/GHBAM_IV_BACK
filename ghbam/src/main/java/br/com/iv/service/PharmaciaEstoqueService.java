package br.com.iv.service;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import br.com.iv.entity.PharmaciaEstoque;
import br.com.iv.entity.PharmaciaLote;

@ApplicationScoped
public class PharmaciaEstoqueService {

    public List<PharmaciaEstoque> listAll() {
        return PharmaciaEstoque.listAll();
    }

    public PharmaciaEstoque findById(Long id) {
        return PharmaciaEstoque.findById(id);
    }

    public PharmaciaEstoque findByLoteAndSigla(Long loteId, String sigla) {
        return PharmaciaEstoque.find("lote.id = ?1 and unidadeSigla = ?2", loteId, sigla).firstResult();
    }

    public List<PharmaciaEstoque> findByUnidadeSigla(String sigla) {
        return PharmaciaEstoque.list("unidadeSigla", sigla);
    }

    // Todas as unidades que hoje tem saldo (ou ja tiveram) desse lote —
    // usado no rastreio de lote (recall).
    public List<PharmaciaEstoque> findByLoteId(Long loteId) {
        return PharmaciaEstoque.list("lote.id", loteId);
    }

    public List<PharmaciaEstoque> findEstoqueBaixo() {
        return PharmaciaEstoque.list("quantidade <= estoqueMinimo and ativo = true");
    }

    @Transactional
    public PharmaciaEstoque save(PharmaciaEstoque estoque) {
        if (estoque.getLote() != null && estoque.getLote().getId() != null) {
            estoque.setLote(PharmaciaLote.findById(estoque.getLote().getId()));
        }
        if (estoque.getId() == null) {
            estoque.persist();
        } else {
            PharmaciaEstoque existing = PharmaciaEstoque.findById(estoque.getId());
            if (existing != null) {
                existing.setLote(estoque.getLote());
                existing.setUnidadeSigla(estoque.getUnidadeSigla());
                existing.setQuantidade(estoque.getQuantidade());
                existing.setLocalizacao(estoque.getLocalizacao());
                existing.setEstoqueMinimo(estoque.getEstoqueMinimo());
                existing.setAtivo(estoque.getAtivo());
            }
        }
        return estoque;
    }

    @Transactional
    public void updateQuantidade(Long estoqueId, Integer novaQuantidade) {
        PharmaciaEstoque estoque = PharmaciaEstoque.findById(estoqueId);
        if (estoque != null) {
            estoque.setQuantidade(novaQuantidade);
            estoque.persist();
        }
    }

    @Transactional
    public void delete(Long id) {
        PharmaciaEstoque.deleteById(id);
    }
}

package br.com.iv.service;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import br.com.iv.entity.PharmaciaEstoque;
import br.com.iv.entity.PharmaciaLote;

@ApplicationScoped
public class PharmaciaEstoqueService {
    @jakarta.inject.Inject jakarta.persistence.EntityManager em;

    public List<PharmaciaEstoque> listAll() {
        return PharmaciaEstoque.listAll();
    }

    public PharmaciaEstoque findById(Long id) {
        return PharmaciaEstoque.findById(id);
    }

    public PharmaciaEstoque findByLoteAndSigla(Long loteId, String sigla) {
        return em.createQuery("from PharmaciaEstoque where lote.id = :l and unidadeSigla = :u", PharmaciaEstoque.class)
                .setParameter("l", loteId).setParameter("u", sigla).setMaxResults(1).getResultStream().findFirst().orElse(null);
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
            if (estoque.getQuantidade() == null || estoque.getQuantidade() != 0) throw new IllegalArgumentException("Novo saldo deve ser registrado por movimentacao de entrada.");
            estoque.persist();
        } else {
            PharmaciaEstoque existing = em.find(PharmaciaEstoque.class, estoque.getId(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (existing != null) {
                if (!existing.getLote().getId().equals(estoque.getLote().getId()) || !existing.getUnidadeSigla().equals(estoque.getUnidadeSigla())) throw new IllegalArgumentException("Nao altere lote/unidade de um estoque existente.");
                if (!existing.getQuantidade().equals(estoque.getQuantidade())) throw new IllegalArgumentException("Use inventario ou movimentacao justificada para alterar saldo.");
                if (estoque.getEstoqueMinimo() == null || estoque.getEstoqueMinimo() < 0) throw new IllegalArgumentException("Estoque minimo invalido.");
                existing.setLote(estoque.getLote());
                existing.setUnidadeSigla(estoque.getUnidadeSigla());
                existing.setQuantidade(estoque.getQuantidade());
                existing.setLocalizacao(estoque.getLocalizacao());
                existing.setEstoqueMinimo(estoque.getEstoqueMinimo());
                if (Boolean.FALSE.equals(estoque.getAtivo()) && existing.getQuantidade() != 0) throw new IllegalArgumentException("Estoque com saldo nao pode ser inativado.");
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
        PharmaciaEstoque estoque = em.find(PharmaciaEstoque.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (estoque == null || estoque.getQuantidade() != 0) throw new IllegalArgumentException("Somente estoque zerado pode ser inativado.");
        estoque.setAtivo(false);
    }
}

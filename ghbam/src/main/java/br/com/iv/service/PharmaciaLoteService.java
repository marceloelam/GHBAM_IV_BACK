package br.com.iv.service;

import java.time.LocalDate;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import br.com.iv.entity.PharmaciaEstoque;
import br.com.iv.entity.PharmaciaLote;
import br.com.iv.entity.PharmaciaProduto;

@ApplicationScoped
public class PharmaciaLoteService {
    @jakarta.inject.Inject jakarta.persistence.EntityManager em;

    public List<PharmaciaLote> listAll() {
        return PharmaciaLote.listAll();
    }

    public PharmaciaLote findById(Long id) {
        return PharmaciaLote.findById(id);
    }

    @Transactional
    public PharmaciaLote save(PharmaciaLote lote) {
        if (lote == null || lote.getNumeroLote() == null || lote.getNumeroLote().isBlank() || lote.getNumeroLote().length() > 50 || lote.getDataFabricacao() == null || lote.getDataValidade() == null || lote.getDataValidade().isBefore(lote.getDataFabricacao()) || lote.getQuantidadeInicial() == null || lote.getQuantidadeInicial() < 0 || lote.getQuantidadeAtual() == null || lote.getQuantidadeAtual() < 0) throw new IllegalArgumentException("Identificacao, datas e quantidades do lote invalidas.");
        if (lote.getId() == null && !lote.getQuantidadeAtual().equals(lote.getQuantidadeInicial())) throw new IllegalArgumentException("Saldo inicial deve corresponder a quantidade recebida.");
        if (lote.getProduto() != null && lote.getProduto().getId() != null) {
            lote.setProduto(PharmaciaProduto.findById(lote.getProduto().getId()));
        } else {
            lote.setProduto(null);
        }

        // Valida duplicidade produto + numeroLote
        if (lote.getProduto() != null && lote.getNumeroLote() != null) {
            PharmaciaLote duplicado = PharmaciaLote.find("produto.id = ?1 and numeroLote = ?2",
                    lote.getProduto().getId(), lote.getNumeroLote()).firstResult();
            if (duplicado != null && !duplicado.getId().equals(lote.getId())) {
                throw new IllegalArgumentException(
                    "Já existe um lote '" + lote.getNumeroLote() + "' cadastrado para este produto.");
            }
        }
        if (lote.getId() == null) {
            lote.persist();

            // Um lote recem-cadastrado ainda nao tem estoque disponivel para
            // dispensacao (pharmacia_estoque e uma tabela separada, ligada a
            // este lote). Sem isso, o lote fica "invisivel" em Atendimento a
            // Paciente ate alguem lembrar de registrar uma Nova Entrada manual
            // em Controle de Estoque para o mesmo lote.
            PharmaciaEstoque estoque = new PharmaciaEstoque();
            estoque.setLote(lote);
            estoque.setUnidadeSigla(lote.getUnidadeSigla());
            estoque.setQuantidade(lote.getQuantidadeAtual() != null ? lote.getQuantidadeAtual() : lote.getQuantidadeInicial());
            estoque.setEstoqueMinimo(0);
            estoque.setAtivo(true);
            estoque.persist();
        } else {
            PharmaciaLote existing = em.find(PharmaciaLote.class, lote.getId(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (existing != null) {
                if (!existing.getProduto().getId().equals(lote.getProduto().getId()) || !java.util.Objects.equals(existing.getUnidadeSigla(), lote.getUnidadeSigla())) throw new IllegalArgumentException("Nao altere produto ou unidade do lote existente.");
                if (!existing.getQuantidadeAtual().equals(lote.getQuantidadeAtual()) || !existing.getQuantidadeInicial().equals(lote.getQuantidadeInicial())) throw new IllegalArgumentException("Use inventario ou movimentacao para alterar quantidades.");
                if (!existing.getNumeroLote().equals(lote.getNumeroLote()) || !existing.getDataFabricacao().equals(lote.getDataFabricacao()) || !existing.getDataValidade().equals(lote.getDataValidade())) throw new IllegalArgumentException("Identificacao e validade do lote registrado devem ser preservadas.");
                existing.setNumeroLote(lote.getNumeroLote());
                existing.setDataFabricacao(lote.getDataFabricacao());
                existing.setDataValidade(lote.getDataValidade());
                existing.setProduto(lote.getProduto());
                existing.setUnidadeSigla(lote.getUnidadeSigla());
                existing.setQuantidadeInicial(lote.getQuantidadeInicial());
                existing.setQuantidadeAtual(lote.getQuantidadeAtual());
                existing.setBloqueado(lote.getBloqueado());
                existing.setAtivo(lote.getAtivo());
            }
        }
        return lote;
    }

    @Transactional
    public void delete(Long id) {
        PharmaciaLote lote = em.find(PharmaciaLote.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (lote == null || lote.getQuantidadeAtual() != 0) throw new IllegalArgumentException("Somente lote sem saldo pode ser inativado.");
        lote.setAtivo(false);
    }

    public List<PharmaciaLote> findByProduto(Long produtoId) {
        return PharmaciaLote.list("produto.id", produtoId);
    }

    public List<PharmaciaLote> findByUnidadeSigla(String sigla) {
        return PharmaciaLote.list("unidadeSigla", sigla);
    }

    public List<PharmaciaLote> findVencendoEm(int dias) {
        LocalDate limite = LocalDate.now().plusDays(dias);
        return PharmaciaLote.list("dataValidade >= ?1 and dataValidade <= ?2 and ativo = true and bloqueado = false", LocalDate.now(), limite);
    }

    public List<PharmaciaLote> findVencidos() {
        return PharmaciaLote.list("dataValidade < ?1 and ativo = true", LocalDate.now());
    }

    @Transactional
    public void bloquearLoteVencido(Long loteId) {
        PharmaciaLote lote = PharmaciaLote.findById(loteId);
        if (lote == null) {
            throw new IllegalArgumentException("Lote não encontrado.");
        }
        if (lote.getDataValidade() == null || !lote.getDataValidade().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Lote ainda não está vencido.");
        }
        lote.setBloqueado(true);
    }
}

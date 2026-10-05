package br.com.iv.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import br.com.iv.entity.PharmaciaEstoque;
import br.com.iv.entity.PharmaciaLote;
import br.com.iv.entity.PharmaciaMovimentacao;

@ApplicationScoped
public class PharmaciaMovimentacaoService {
    @Inject EntityManager em;
    @Inject PharmaciaOperacaoService operacoes;

    public List<PharmaciaMovimentacao> listAll() { return PharmaciaMovimentacao.listAll(); }
    public List<PharmaciaMovimentacao> findByUnidade(String unidade) {
        return em.createQuery("from PharmaciaMovimentacao where unidadeOrigemSigla = :u or unidadeDestinoSigla = :u order by dataHora desc", PharmaciaMovimentacao.class).setParameter("u", unidade).getResultList();
    }
    public List<PharmaciaMovimentacao> findByLoteEUnidade(Long lote, String unidade) {
        return em.createQuery("from PharmaciaMovimentacao where lote.id = :l and (unidadeOrigemSigla = :u or unidadeDestinoSigla = :u) order by dataHora desc", PharmaciaMovimentacao.class).setParameter("l", lote).setParameter("u", unidade).getResultList();
    }
    public PharmaciaMovimentacao findById(Long id) { return PharmaciaMovimentacao.findById(id); }
    public List<PharmaciaMovimentacao> findByUnidadeDestinoSigla(String sigla) { return PharmaciaMovimentacao.list("unidadeDestinoSigla", sigla); }
    public List<PharmaciaMovimentacao> findByUnidadeOrigemSigla(String sigla) { return PharmaciaMovimentacao.list("unidadeOrigemSigla", sigla); }
    public List<PharmaciaMovimentacao> findByLote(Long id) { return PharmaciaMovimentacao.list("lote.id", id); }

    static void validarLoteDispensavel(PharmaciaLote lote) {
        if (lote == null || !Boolean.TRUE.equals(lote.getAtivo()) || Boolean.TRUE.equals(lote.getBloqueado()) ||
                lote.getDataValidade() == null || lote.getDataValidade().isBefore(LocalDate.now()) ||
                lote.getProduto() == null || !Boolean.TRUE.equals(lote.getProduto().getAtivo())) {
            throw new IllegalArgumentException("Lote vencido, bloqueado, inativo ou sem validade/produto valido.");
        }
    }

    @Transactional
    public PharmaciaMovimentacao save(PharmaciaMovimentacao m) {
        if (m == null || m.getId() != null) throw new IllegalArgumentException("Movimentacao registrada nao pode ser alterada; registre um ajuste justificado.");
        if (m.getTipo() == null || m.getQuantidade() == null || m.getQuantidade() == 0 ||
                (m.getTipo() != PharmaciaMovimentacao.TipoMovimentacao.AJUSTE && m.getQuantidade() < 0)) {
            throw new IllegalArgumentException("Informe o tipo e uma quantidade positiva (ajustes aceitam negativos).");
        }
        if (m.getUnidadeOrigemSigla() == null || m.getUnidadeOrigemSigla().isBlank() ||
                m.getUsuarioMatricula() == null || m.getUsuarioMatricula().isBlank() ||
                m.getLote() == null || m.getLote().getId() == null) {
            throw new IllegalArgumentException("Lote, unidade e profissional sao obrigatorios.");
        }
        if (m.getTipo() == PharmaciaMovimentacao.TipoMovimentacao.TRANSFERENCIA) {
            throw new IllegalArgumentException("Use o fluxo de permuta para transferir estoque.");
        }
        if (m.getTipo() == PharmaciaMovimentacao.TipoMovimentacao.AJUSTE &&
                (m.getJustificativa() == null || m.getJustificativa().isBlank())) {
            throw new IllegalArgumentException("Informe a justificativa do ajuste.");
        }
        if (m.getTipo() == PharmaciaMovimentacao.TipoMovimentacao.DISPENSACAO &&
                (m.getPacienteNome() == null || m.getPacienteNome().isBlank())) {
            throw new IllegalArgumentException("Informe o paciente.");
        }
        PharmaciaLote lote = em.find(PharmaciaLote.class, m.getLote().getId(), LockModeType.PESSIMISTIC_WRITE);
        if (lote == null) throw new IllegalArgumentException("Lote nao encontrado.");
        if (m.getTipo() == PharmaciaMovimentacao.TipoMovimentacao.DISPENSACAO) { validarLoteDispensavel(lote); operacoes.verificarDisponivel(lote.getId(), m.getUnidadeOrigemSigla()); }
        PharmaciaEstoque estoque = em.createQuery("from PharmaciaEstoque where lote.id = :lote and unidadeSigla = :unidade", PharmaciaEstoque.class)
                .setParameter("lote", lote.getId()).setParameter("unidade", m.getUnidadeOrigemSigla())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElse(null);
        int delta = switch (m.getTipo()) {
            case DISPENSACAO, SAIDA -> Math.negateExact(m.getQuantidade());
            default -> m.getQuantidade();
        };
        if (estoque == null) {
            if (delta < 0 || !m.getUnidadeOrigemSigla().equals(lote.getUnidadeSigla())) {
                throw new IllegalArgumentException("Lote sem estoque na unidade informada.");
            }
            estoque = new PharmaciaEstoque();
            estoque.setLote(lote); estoque.setUnidadeSigla(m.getUnidadeOrigemSigla()); estoque.setQuantidade(0);
            em.persist(estoque);
        }
        if (!Boolean.TRUE.equals(estoque.getAtivo())) throw new IllegalArgumentException("Estoque inativo.");
        int saldoLote = Math.addExact(lote.getQuantidadeAtual(), delta);
        int saldoUnidade = Math.addExact(estoque.getQuantidade(), delta);
        if (saldoLote < 0 || saldoUnidade < 0) throw new IllegalArgumentException("Estoque insuficiente no lote ou na unidade.");
        lote.setQuantidadeAtual(saldoLote);
        estoque.setQuantidade(saldoUnidade);
        m.setLote(lote); m.setDataHora(LocalDateTime.now()); m.setAtivo(true);
        em.persist(m);
        return m;
    }

    @Transactional
    public void delete(Long id) {
        throw new IllegalArgumentException("Movimentacao registrada nao pode ser excluida; registre um ajuste justificado.");
    }
}

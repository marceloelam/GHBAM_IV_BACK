package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import br.com.iv.entity.PharmaciaDispensacao;
import br.com.iv.entity.PharmaciaLote;
import br.com.iv.entity.PharmaciaMovimentacao;

@ApplicationScoped
public class PharmaciaDispensacaoService {
    @Inject EntityManager em;
    @Inject PharmaciaOperacaoService operacoes;
    @Inject PharmaciaMovimentacaoService movimentacoes;

    public List<PharmaciaDispensacao> listAll() { return PharmaciaDispensacao.listAll(); }
    public List<PharmaciaDispensacao> findByUnidade(String unidade) {
        return em.createQuery("from PharmaciaDispensacao where unidadeSigla = :u order by dataHora desc", PharmaciaDispensacao.class).setParameter("u", unidade).getResultList();
    }
    public List<PharmaciaDispensacao> findByPacienteEUnidade(Long paciente, String unidade) {
        return em.createQuery("from PharmaciaDispensacao where unidadeSigla = :u and pacienteId = :p order by dataHora desc", PharmaciaDispensacao.class).setParameter("u", unidade).setParameter("p", paciente).getResultList();
    }
    public PharmaciaDispensacao findById(Long id) { return PharmaciaDispensacao.findById(id); }
    public List<PharmaciaDispensacao> findByPacienteId(Long id) {
        return PharmaciaDispensacao.list("pacienteId = ?1 order by dataHora desc", id);
    }

    static void validar(PharmaciaDispensacao d) {
        if (d == null || d.getId() != null || d.getQuantidade() == null || d.getQuantidade() <= 0 ||
                d.getDuracao() == null || d.getDuracao() <= 0 || d.getPacienteId() == null || d.getPacienteId() <= 0 ||
                d.getLote() == null || d.getLote().getId() == null ||
                d.getPacienteNome() == null || d.getPacienteNome().isBlank() ||
                d.getMedicoNome() == null || d.getMedicoNome().isBlank() ||
                d.getMedicoCRM() == null || d.getMedicoCRM().isBlank() ||
                d.getUnidadeSigla() == null || d.getUnidadeSigla().isBlank() ||
                d.getUsuarioMatricula() == null || d.getUsuarioMatricula().isBlank()) {
            throw new IllegalArgumentException("Informe paciente, lote, quantidade e duracao positivas, medico, CRM, unidade e profissional.");
        }
    }

    @Transactional
    public PharmaciaDispensacao save(PharmaciaDispensacao d) {
        validar(d);
        PharmaciaLote lote = em.find(PharmaciaLote.class, d.getLote().getId(), LockModeType.PESSIMISTIC_WRITE);
        PharmaciaMovimentacaoService.validarLoteDispensavel(lote);
        d.setLote(lote); d.setProdutoNome(lote.getProduto().getNomeComercial());
        d.setDataHora(LocalDateTime.now()); d.setAtivo(true);
        PharmaciaMovimentacao movimento = new PharmaciaMovimentacao();
        movimento.setTipo(PharmaciaMovimentacao.TipoMovimentacao.DISPENSACAO);
        movimento.setLote(lote); movimento.setQuantidade(d.getQuantidade());
        movimento.setUnidadeOrigemSigla(d.getUnidadeSigla()); movimento.setUsuarioMatricula(d.getUsuarioMatricula());
        movimento.setPacienteNome(d.getPacienteNome()); movimento.setPacienteCpf(d.getPacienteCpf());
        movimento.setJustificativa("Dispensacao vinculada ao paciente " + d.getPacienteId());
        movimentacoes.save(movimento);
        em.persist(d);
        return d;
    }

    @Transactional
    public List<PharmaciaDispensacao> saveBatch(List<PharmaciaDispensacao> lista) {
        if (lista == null || lista.isEmpty() || lista.size() > 100) throw new IllegalArgumentException("Informe entre 1 e 100 itens.");
        lista.forEach(PharmaciaDispensacaoService::validar);
        PharmaciaDispensacao primeiro = lista.get(0);
        if (lista.stream().anyMatch(d -> !d.getPacienteId().equals(primeiro.getPacienteId()) || !d.getUnidadeSigla().equals(primeiro.getUnidadeSigla()))) {
            throw new IllegalArgumentException("Todos os itens devem pertencer ao mesmo paciente e unidade.");
        }
        // Ordem unica de locks evita deadlocks entre carrinhos com varios lotes.
        lista.stream().map(d -> d.getLote().getId()).distinct().sorted().forEach(id ->
            em.find(PharmaciaLote.class, id, LockModeType.PESSIMISTIC_WRITE));
        return lista.stream().map(this::save).toList();
    }

    @Transactional
    public void devolver(Long id, Long loteId, Integer quantidade, String unidade) {
        devolver(id, loteId, quantidade, unidade, null);
    }

    @Transactional
    public void devolver(Long id, Long loteId, Integer quantidade, String unidade, String matricula) {
        PharmaciaDispensacao d = em.find(PharmaciaDispensacao.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (d == null || !Boolean.TRUE.equals(d.getAtivo())) throw new IllegalArgumentException("Dispensacao inexistente ou ja devolvida.");
        if (!d.getLoteId().equals(loteId) || !d.getQuantidade().equals(quantidade) || !d.getUnidadeSigla().equals(unidade)) {
            throw new IllegalArgumentException("A devolucao deve corresponder ao lote, unidade e quantidade da dispensacao original.");
        }
        operacoes.devolver(d, matricula == null ? d.getUsuarioMatricula() : matricula, "Devolucao integral da dispensacao #" + d.getId());
    }

    @Transactional
    public void delete(Long id) { throw new IllegalArgumentException("Dispensacao registrada nao pode ser excluida; use a devolucao."); }
}

package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import br.com.iv.entity.PharmaciaEstoque;
import br.com.iv.entity.PharmaciaLote;
import br.com.iv.entity.PharmaciaMovimentacao;

@ApplicationScoped
public class PharmaciaMovimentacaoService {

    public List<PharmaciaMovimentacao> listAll() {
        return PharmaciaMovimentacao.listAll();
    }

    public PharmaciaMovimentacao findById(Long id) {
        return PharmaciaMovimentacao.findById(id);
    }

    public List<PharmaciaMovimentacao> findByUnidadeDestinoSigla(String sigla) {
        return PharmaciaMovimentacao.list("unidadeDestinoSigla", sigla);
    }

    public List<PharmaciaMovimentacao> findByUnidadeOrigemSigla(String sigla) {
        return PharmaciaMovimentacao.list("unidadeOrigemSigla", sigla);
    }

    public List<PharmaciaMovimentacao> findByLote(Long loteId) {
        return PharmaciaMovimentacao.list("lote.id", loteId);
    }

    @Transactional
    public PharmaciaMovimentacao save(PharmaciaMovimentacao movimentacao) {
        if (movimentacao.getLote() != null && movimentacao.getLote().getId() != null) {
            movimentacao.setLote(PharmaciaLote.findById(movimentacao.getLote().getId()));
        }
        if (movimentacao.getId() == null) {
            movimentacao.setDataHora(LocalDateTime.now());
            PharmaciaLote lote = movimentacao.getLote();
            if (lote != null && movimentacao.getTipo() != null) {
                int delta = movimentacao.getQuantidade() != null ? movimentacao.getQuantidade() : 0;
                switch (movimentacao.getTipo()) {
                    case AJUSTE:
                        lote.setQuantidadeAtual(lote.getQuantidadeAtual() + delta);
                        sincronizarEstoque(lote, movimentacao.getUnidadeOrigemSigla(), delta);
                        break;
                    case DISPENSACAO:
                    case SAIDA:
                        if (lote.getQuantidadeAtual() - delta < 0) {
                            throw new IllegalArgumentException(
                                "Quantidade insuficiente no lote (disponivel: " + lote.getQuantidadeAtual() + ").");
                        }
                        lote.setQuantidadeAtual(lote.getQuantidadeAtual() - delta);
                        sincronizarEstoque(lote, movimentacao.getUnidadeOrigemSigla(), -delta);
                        break;
                    case ENTRADA:
                        lote.setQuantidadeAtual(lote.getQuantidadeAtual() + delta);
                        sincronizarEstoque(lote, movimentacao.getUnidadeOrigemSigla(), delta);
                        break;
                    default:
                        break;
                }
            }
            movimentacao.persist();
        } else {
            PharmaciaMovimentacao existing = PharmaciaMovimentacao.findById(movimentacao.getId());
            if (existing != null) {
                existing.setTipo(movimentacao.getTipo());
                existing.setLote(movimentacao.getLote());
                existing.setUnidadeOrigemSigla(movimentacao.getUnidadeOrigemSigla());
                existing.setUnidadeDestinoSigla(movimentacao.getUnidadeDestinoSigla());
                existing.setQuantidade(movimentacao.getQuantidade());
                existing.setJustificativa(movimentacao.getJustificativa());
                existing.setPacienteNome(movimentacao.getPacienteNome());
                existing.setPacienteCpf(movimentacao.getPacienteCpf());
                existing.setUsuarioMatricula(movimentacao.getUsuarioMatricula());
                existing.setAtivo(movimentacao.getAtivo());
            }
        }
        return movimentacao;
    }

    @Transactional
    public void delete(Long id) {
        PharmaciaMovimentacao.deleteById(id);
    }

    // PharmaciaLote.quantidadeAtual (acima) e PharmaciaEstoque.quantidade sao
    // dois contadores separados no banco — Controle de Estoque/Atendimento a
    // Paciente leem so o segundo. Sem isso, um Ajuste/Entrada/Saida corrige o
    // lote mas "some" para quem consulta o estoque da unidade. Cria o registro
    // de estoque na hora se ainda nao existir (lotes antigos, criados antes do
    // auto-create em PharmaciaLoteService).
    private void sincronizarEstoque(PharmaciaLote lote, String unidadeSigla, int delta) {
        if (lote == null || unidadeSigla == null) return;
        PharmaciaEstoque estoque = PharmaciaEstoque.find(
                "lote.id = ?1 and unidadeSigla = ?2", lote.getId(), unidadeSigla
        ).firstResult();
        if (estoque == null) {
            estoque = new PharmaciaEstoque();
            estoque.setLote(lote);
            estoque.setUnidadeSigla(unidadeSigla);
            estoque.setQuantidade(0);
            estoque.setEstoqueMinimo(0);
            estoque.setAtivo(true);
        }
        estoque.setQuantidade(estoque.getQuantidade() + delta);
        estoque.persist();
    }
}

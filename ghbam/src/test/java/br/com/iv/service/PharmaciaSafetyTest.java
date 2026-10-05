package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import br.com.iv.entity.*;
import br.com.iv.pharmacia.validacao.dto.ValidacaoFarmaceuticaResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PharmaciaSafetyTest {
    EntityManager em;
    PharmaciaMovimentacaoService movimentos;
    PharmaciaDispensacaoService dispensacoes;
    PharmaciaLote lote;
    PharmaciaEstoque estoque;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        em = mock(EntityManager.class);
        movimentos = new PharmaciaMovimentacaoService(); movimentos.em = em; movimentos.operacoes = mock(PharmaciaOperacaoService.class);
        dispensacoes = new PharmaciaDispensacaoService(); dispensacoes.em = em; dispensacoes.movimentacoes = movimentos;
        dispensacoes.operacoes = mock(PharmaciaOperacaoService.class);
        when(dispensacoes.operacoes.devolver(any(), anyString(), anyString())).thenAnswer(i -> { ((PharmaciaDispensacao)i.getArgument(0)).setAtivo(false); return new PharmaciaOperacao(); });
        PharmaciaProduto produto = new PharmaciaProduto(); produto.setId(1L); produto.setNomeComercial("TESTE"); produto.setAtivo(true);
        lote = new PharmaciaLote(); lote.setId(1L); lote.setProduto(produto); lote.setUnidadeSigla("QA");
        lote.setDataValidade(LocalDate.now().plusDays(30)); lote.setQuantidadeAtual(10);
        estoque = new PharmaciaEstoque(); estoque.setLote(lote); estoque.setUnidadeSigla("QA"); estoque.setQuantidade(10);
        when(em.find(PharmaciaLote.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(lote);
        TypedQuery<PharmaciaEstoque> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PharmaciaEstoque.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setLockMode(any())).thenReturn(query);
        when(query.getResultStream()).thenAnswer(i -> Stream.of(estoque));
    }

    PharmaciaDispensacao dispensacao(int qtd) {
        PharmaciaDispensacao d = new PharmaciaDispensacao(); d.setLote(lote); d.setQuantidade(qtd); d.setDuracao(30);
        d.setPacienteId(1L); d.setPacienteNome("PACIENTE FICTICIO"); d.setMedicoNome("MEDICO TESTE"); d.setMedicoCRM("QA");
        d.setUsuarioMatricula("QA001"); d.setUnidadeSigla("QA"); return d;
    }
    PharmaciaMovimentacao movimento(int qtd, PharmaciaMovimentacao.TipoMovimentacao tipo) {
        PharmaciaMovimentacao m = new PharmaciaMovimentacao(); m.setTipo(tipo); m.setLote(lote); m.setQuantidade(qtd);
        m.setUnidadeOrigemSigla("QA"); m.setUsuarioMatricula("QA001"); m.setPacienteNome("PACIENTE FICTICIO"); return m;
    }

    @Test void dispensacaoDescontaUmaVezEGeraHistorico() {
        dispensacoes.save(dispensacao(3));
        assertEquals(7, estoque.getQuantidade()); assertEquals(7, lote.getQuantidadeAtual());
        verify(em).persist(isA(PharmaciaDispensacao.class)); verify(em).persist(isA(PharmaciaMovimentacao.class));
        verify(em, atLeastOnce()).find(PharmaciaLote.class, 1L, LockModeType.PESSIMISTIC_WRITE);
    }
    @Test void dispensaSaldoExato() { dispensacoes.save(dispensacao(10)); assertEquals(0, estoque.getQuantidade()); }
    @Test void rejeitaNegativoAntesDePersistir() {
        assertThrows(IllegalArgumentException.class, () -> dispensacoes.save(dispensacao(-3)));
        verify(em, never()).persist(any()); assertEquals(10, estoque.getQuantidade());
    }
    @Test void rejeitaZero() { assertThrows(IllegalArgumentException.class, () -> dispensacoes.save(dispensacao(0))); }
    @Test void rejeitaSaldoInsuficienteDaUnidade() {
        estoque.setQuantidade(2);
        assertThrows(IllegalArgumentException.class, () -> dispensacoes.save(dispensacao(3)));
        assertEquals(10, lote.getQuantidadeAtual()); verify(em, never()).persist(any());
    }
    @Test void rejeitaSaldoInsuficienteDoLote() {
        lote.setQuantidadeAtual(2);
        assertThrows(IllegalArgumentException.class, () -> dispensacoes.save(dispensacao(3)));
        assertEquals(10, estoque.getQuantidade());
    }
    @Test void rejeitaVencido() {
        lote.setDataValidade(LocalDate.now().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> dispensacoes.save(dispensacao(1)));
        verify(em, never()).persist(any());
    }
    @Test void validadeDeHojeContinuaValida() { lote.setDataValidade(LocalDate.now()); dispensacoes.save(dispensacao(1)); }
    @Test void rejeitaBloqueado() { lote.setBloqueado(true); assertThrows(IllegalArgumentException.class, () -> dispensacoes.save(dispensacao(1))); }
    @Test void rejeitaInativo() { lote.setAtivo(false); assertThrows(IllegalArgumentException.class, () -> dispensacoes.save(dispensacao(1))); }
    @Test void rejeitaEstoqueInativo() { estoque.setAtivo(false); assertThrows(IllegalArgumentException.class, () -> dispensacoes.save(dispensacao(1))); }
    @Test void ajusteNaoPodeNegativarEstoque() {
        PharmaciaMovimentacao m = movimento(-11, PharmaciaMovimentacao.TipoMovimentacao.AJUSTE); m.setJustificativa("CONTAGEM QA");
        assertThrows(IllegalArgumentException.class, () -> movimentos.save(m)); assertEquals(10, estoque.getQuantidade());
    }
    @Test void ajusteNegativoValido() {
        PharmaciaMovimentacao m = movimento(-3, PharmaciaMovimentacao.TipoMovimentacao.AJUSTE); m.setJustificativa("CONTAGEM QA");
        movimentos.save(m); assertEquals(7, estoque.getQuantidade()); assertEquals(7, lote.getQuantidadeAtual());
    }
    @Test void ajusteExigeJustificativa() { assertThrows(IllegalArgumentException.class, () -> movimentos.save(movimento(-1, PharmaciaMovimentacao.TipoMovimentacao.AJUSTE))); }
    @Test void dispensacaoMovimentacaoRejeitaNegativo() { assertThrows(IllegalArgumentException.class, () -> movimentos.save(movimento(-1, PharmaciaMovimentacao.TipoMovimentacao.DISPENSACAO))); }
    @Test void rejeitaCarrinhoComPacientesDiferentes() {
        PharmaciaDispensacao a = dispensacao(1), b = dispensacao(1); b.setPacienteId(2L);
        assertThrows(IllegalArgumentException.class, () -> dispensacoes.saveBatch(List.of(a,b))); verify(em, never()).persist(any());
    }
    @Test void historicoImutavel() { assertThrows(IllegalArgumentException.class, () -> movimentos.delete(1L)); assertThrows(IllegalArgumentException.class, () -> dispensacoes.delete(1L)); }
    @Test void devolucaoSegregaSemCreditarSaldo() {
        PharmaciaDispensacao d = dispensacao(3); d.setId(1L);
        when(em.find(PharmaciaDispensacao.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(d);
        dispensacoes.devolver(1L, 1L, 3, "QA"); assertEquals(10, estoque.getQuantidade()); assertFalse(d.getAtivo());
        assertThrows(IllegalArgumentException.class, () -> dispensacoes.devolver(1L, 1L, 3, "QA")); assertEquals(10, estoque.getQuantidade());
    }
    @Test void devolucaoRejeitaQuantidadeAdulterada() {
        PharmaciaDispensacao d = dispensacao(3); d.setId(1L);
        when(em.find(PharmaciaDispensacao.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(d);
        assertThrows(IllegalArgumentException.class, () -> dispensacoes.devolver(1L, 1L, 30, "QA")); assertEquals(10, estoque.getQuantidade());
    }
    @Test void criticoExigeJustificativaNoServidor() {
        ValidacaoFarmaceuticaResponse r = new ValidacaoFarmaceuticaResponse(); r.setPossuiCritico(true);
        assertThrows(IllegalArgumentException.class, () -> PharmaciaConfirmacaoService.exigirDecisao(r, "", "", "QA001"));
        PharmaciaConfirmacaoService.exigirDecisao(r, "Contato documentado com prescritor", "", "QA001");
    }
    @Test void duplaChecagemExigeOutroProfissional() {
        ValidacaoFarmaceuticaResponse r = new ValidacaoFarmaceuticaResponse(); r.setExigeDuplaChecagem(true);
        assertThrows(IllegalArgumentException.class, () -> PharmaciaConfirmacaoService.exigirDecisao(r, "", "QA001", "QA001"));
        assertThrows(IllegalArgumentException.class, () -> PharmaciaConfirmacaoService.exigirDecisao(r, "", "", "QA001"));
        PharmaciaConfirmacaoService.exigirDecisao(r, "", "QA002", "QA001");
    }
}

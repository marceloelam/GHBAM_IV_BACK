package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.List;
import br.com.iv.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PharmaciaPermutaSafetyTest {
    PharmaciaPermutaService service;
    PharmaciaPermuta permuta;
    PharmaciaLote lote;
    PharmaciaEstoque estoque;

    @BeforeEach void preparar() {
        service = new PharmaciaPermutaService(); service.operacoes = mock(PharmaciaOperacaoService.class); service.em = mock(EntityManager.class);
        service.estoqueService = mock(PharmaciaEstoqueService.class);
        permuta = spy(new PharmaciaPermuta()); doNothing().when(permuta).persist();
        permuta.setId(1L); permuta.setUnidadeOrigemSigla("QA_RECEBEDOR"); permuta.setUnidadeDestinoSigla("QA_FORNECEDOR");
        permuta.setStatus(PharmaciaPermuta.StatusPermuta.EM_TRANSPORTE);
        lote = new PharmaciaLote(); lote.setId(1L); lote.setQuantidadeAtual(7);
        lote.setDataValidade(LocalDate.now().plusDays(10));
        PharmaciaProduto produto = new PharmaciaProduto(); produto.setId(1L); produto.setAtivo(true); lote.setProduto(produto);
        PharmaciaPermutaItem item = new PharmaciaPermutaItem(); item.setId(1L); item.setLote(lote); item.setQuantidadeSolicitada(3); item.setQuantidadeAprovada(3);
        permuta.setItens(List.of(item));
        estoque = spy(new PharmaciaEstoque()); doNothing().when(estoque).persist();
        estoque.setQuantidade(0); estoque.setUnidadeSigla("QA_RECEBEDOR"); estoque.setLote(lote);
        when(service.em.find(PharmaciaPermuta.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(permuta);
        when(service.em.find(PharmaciaLote.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(lote);
        when(service.estoqueService.findByLoteAndSigla(1L, "QA_RECEBEDOR")).thenReturn(estoque);
    }
    @Test void recebimentoMescladoRecompoeLoteEEstoque() {
        service.finalizar(1L, "QA001", null, "QA_RECEBEDOR");
        assertEquals(10, lote.getQuantidadeAtual()); assertEquals(3, estoque.getQuantidade());
        assertEquals(PharmaciaPermuta.StatusPermuta.FINALIZADO, permuta.getStatus());
        verify(service.em).persist(isA(PharmaciaMovimentacao.class));
        assertThrows(IllegalStateException.class, () -> service.finalizar(1L, "QA001", null, "QA_RECEBEDOR"));
        assertEquals(10, lote.getQuantidadeAtual());
    }
    @Test void outraUnidadeNaoPodeReceber() {
        assertThrows(IllegalStateException.class, () -> service.finalizar(1L, "QA001", null, "OUTRA"));
        assertEquals(7, lote.getQuantidadeAtual());
    }
    @Test void transporteRejeitaLoteBloqueado() {
        permuta.setStatus(PharmaciaPermuta.StatusPermuta.APROVADO); lote.setBloqueado(true);
        assertThrows(IllegalArgumentException.class, () -> service.iniciarTransporte(1L, "QA001", "QA_FORNECEDOR"));
        assertEquals(PharmaciaPermuta.StatusPermuta.APROVADO, permuta.getStatus());
    }
    @Test void fornecedoraCancelaAprovadaSemMoverEstoque() {
        permuta.setStatus(PharmaciaPermuta.StatusPermuta.APROVADO);
        assertThrows(IllegalStateException.class, () -> service.recusar(1L, "Sem envio", "QA001", "QA_RECEBEDOR"));
        service.recusar(1L, "Lote reservado", "QA001", "QA_FORNECEDOR");
        assertEquals(PharmaciaPermuta.StatusPermuta.RECUSADO, permuta.getStatus());
        assertEquals(7, lote.getQuantidadeAtual());
        verify(service.em, never()).persist(isA(PharmaciaMovimentacao.class));
    }
    @Test void emTransporteNaoPodeSerRecusada() {
        assertThrows(IllegalStateException.class, () -> service.recusar(1L, "Tarde demais", "QA001", "QA_FORNECEDOR"));
        assertEquals(PharmaciaPermuta.StatusPermuta.EM_TRANSPORTE, permuta.getStatus());
    }
    @Test void solicitacaoRejeitaPedidoInvalido() {
        PharmaciaPermuta mesma = new PharmaciaPermuta(); mesma.setUnidadeOrigemSigla("QA"); mesma.setUnidadeDestinoSigla("qa");
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(mesma));
        PharmaciaPermuta vazia = new PharmaciaPermuta(); vazia.setUnidadeOrigemSigla("QA"); vazia.setUnidadeDestinoSigla("OUTRA"); vazia.setItens(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(vazia));
        PharmaciaPermuta zero = new PharmaciaPermuta(); zero.setUnidadeOrigemSigla("QA"); zero.setUnidadeDestinoSigla("OUTRA");
        PharmaciaPermutaItem item = new PharmaciaPermutaItem(); item.setLote(lote); item.setQuantidadeSolicitada(0);
        zero.setItens(List.of(item));
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(zero));
    }
}

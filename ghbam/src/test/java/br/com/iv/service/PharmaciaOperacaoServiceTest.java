package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.stream.Stream;
import br.com.iv.dto.PharmaciaOperacaoRequest;
import br.com.iv.entity.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class PharmaciaOperacaoServiceTest {
    PharmaciaOperacaoService service;
    PharmaciaEstoque estoque;
    PharmaciaOperacao o;
    PharmaciaCondicaoUnidade condicao;
    @BeforeEach @SuppressWarnings("unchecked") void preparar() {
        service = new PharmaciaOperacaoService(); service.em = mock(EntityManager.class); service.movimentos = mock(PharmaciaMovimentacaoService.class); service.logs = mock(LogService.class); service.mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        TypedQuery<PharmaciaOperacao> oq = mock(TypedQuery.class); when(service.em.createQuery(anyString(), eq(PharmaciaOperacao.class))).thenReturn(oq);
        when(oq.setParameter(anyString(), any())).thenReturn(oq); when(oq.getResultStream()).thenAnswer(i -> Stream.empty());
        PharmaciaProduto p = new PharmaciaProduto(); p.setAtivo(true);
        PharmaciaLote l = new PharmaciaLote(); l.setId(1L); l.setProduto(p); l.setDataValidade(LocalDate.now().plusDays(30));
        estoque = new PharmaciaEstoque(); estoque.setLote(l); estoque.setQuantidade(10); estoque.setUnidadeSigla("QA");
        when(service.em.find(PharmaciaLote.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(l);
        TypedQuery<PharmaciaEstoque> eq = mock(TypedQuery.class);
        when(service.em.createQuery(anyString(), eq(PharmaciaEstoque.class))).thenReturn(eq);
        when(eq.setParameter(anyString(), any())).thenReturn(eq); when(eq.setLockMode(any())).thenReturn(eq); when(eq.getResultStream()).thenAnswer(i -> Stream.of(estoque));
        TypedQuery<PharmaciaCondicaoUnidade> cq = mock(TypedQuery.class);
        when(service.em.createQuery(anyString(), eq(PharmaciaCondicaoUnidade.class))).thenReturn(cq);
        when(cq.setParameter(anyString(), any())).thenReturn(cq); when(cq.getResultStream()).thenAnswer(i -> condicao == null ? Stream.empty() : Stream.of(condicao));
        doAnswer(i -> { if (i.getArgument(0) instanceof PharmaciaOperacao a) a.id = 1L; if (i.getArgument(0) instanceof PharmaciaCondicaoUnidade c) condicao = c; return null; }).when(service.em).persist(any());
        o = new PharmaciaOperacao(); o.id = 1L; o.loteId = 1L; o.unidade = "QA"; o.tipo = "DEVOLUCAO"; o.status = "PENDENTE"; o.quantidade = 3; o.matricula = "P1";
        when(service.em.find(PharmaciaOperacao.class, 1L)).thenReturn(o); when(service.em.find(PharmaciaOperacao.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);
    }
    PharmaciaOperacaoRequest request(String tipo) { PharmaciaOperacaoRequest r = new PharmaciaOperacaoRequest(); r.tipo = tipo; r.chaveOperacao = java.util.UUID.randomUUID().toString(); r.loteId = 1L; r.quantidade = 3; r.justificativa = "MOTIVO TESTE"; r.documento = "DOC TESTE"; return r; }
    @Test void devolucaoNaoCreditaSaldo() { PharmaciaDispensacao d = new PharmaciaDispensacao(); d.setId(9L); d.setLote(estoque.getLote()); d.setUnidadeSigla("QA"); d.setQuantidade(3); service.devolver(d, "P1", "Recebido lacrado"); assertFalse(d.getAtivo()); verifyNoInteractions(service.movimentos); }
    @Test void quarentenaRetiraSaldoDisponivel() { PharmaciaOperacao saved = service.criar(request("QUARENTENA"), "QA", "P1"); assertEquals("PENDENTE", saved.status); ArgumentCaptor<PharmaciaMovimentacao> c = ArgumentCaptor.forClass(PharmaciaMovimentacao.class); verify(service.movimentos).save(c.capture()); assertEquals(-3, c.getValue().getQuantidade()); }
    @Test void descarteSegregadoNaoDescontaDuasVezes() { service.avaliar(1L, "QA", "P2", "DESCARTAR", "Embalagem violada", "DOC"); assertEquals("DESCARTADO", o.status); verifyNoInteractions(service.movimentos); }
    @Test void liberacaoCreditaApenasAposParecer() { service.avaliar(1L, "QA", "P2", "LIBERAR", "Integridade e conservacao confirmadas", "DOC"); ArgumentCaptor<PharmaciaMovimentacao> c = ArgumentCaptor.forClass(PharmaciaMovimentacao.class); verify(service.movimentos).save(c.capture()); assertEquals(3, c.getValue().getQuantidade()); }
    @Test void naoLiberaDevolucaoVencida() { estoque.getLote().setDataValidade(LocalDate.now().minusDays(1)); assertThrows(IllegalArgumentException.class, () -> service.avaliar(1L, "QA", "P2", "LIBERAR", "TESTE", "DOC")); verifyNoInteractions(service.movimentos); }
    @Test void naoLiberaSemDocumento() { assertThrows(IllegalArgumentException.class, () -> service.avaliar(1L, "QA", "P2", "LIBERAR", "TESTE", "")); }
    @Test void naoAvaliaDuasVezes() { o.status = "LIBERADO"; assertThrows(IllegalArgumentException.class, () -> service.avaliar(1L, "QA", "P2", "LIBERAR", "TESTE", "DOC")); }
    @Test void inventarioExigeOutroProfissional() { o.tipo = "INVENTARIO"; o.saldoReferencia = 10; assertThrows(IllegalArgumentException.class, () -> service.avaliar(1L, "QA", "P1", "CONCILIAR", "TESTE", "")); }
    @Test void inventarioComSaldoAlteradoExigeRecontagem() { o.tipo = "INVENTARIO"; o.saldoReferencia = 11; assertThrows(IllegalArgumentException.class, () -> service.avaliar(1L, "QA", "P2", "CONCILIAR", "TESTE", "")); verifyNoInteractions(service.movimentos); }
    @Test void inventarioConciliadoAjustaDiferenca() { o.tipo = "INVENTARIO"; o.saldoReferencia = 10; o.quantidade = 7; service.avaliar(1L, "QA", "P2", "CONCILIAR", "Contagem revisada", ""); ArgumentCaptor<PharmaciaMovimentacao> c = ArgumentCaptor.forClass(PharmaciaMovimentacao.class); verify(service.movimentos).save(c.capture()); assertEquals(-3, c.getValue().getQuantidade()); }
    @Test void temperaturaForaDoLimiteBloqueiaUnidade() { PharmaciaOperacaoRequest r = request("TEMPERATURA"); r.temperatura = new BigDecimal("11"); r.limiteMinimo = new BigDecimal("2"); r.limiteMaximo = new BigDecimal("8"); assertEquals("PENDENTE", service.criar(r, "QA", "P1").status); assertTrue(condicao.bloqueado); assertThrows(IllegalArgumentException.class, () -> service.verificarDisponivel(1L, "QA")); }
    @Test void temperaturaDentroDosLimitesNaoBloqueia() { PharmaciaOperacaoRequest r = request("TEMPERATURA"); r.temperatura = new BigDecimal("8"); r.limiteMinimo = new BigDecimal("2"); r.limiteMaximo = new BigDecimal("8"); assertEquals("FINALIZADO", service.criar(r, "QA", "P1").status); assertNull(condicao); }
    @Test void aberturaNaoProlongaValidade() { condicao = new PharmaciaCondicaoUnidade(); condicao.validadeUso = LocalDateTime.now().plusDays(1); PharmaciaOperacaoRequest r = request("ABERTURA"); r.validadeUso = LocalDateTime.now().plusDays(2); assertThrows(IllegalArgumentException.class, () -> service.criar(r, "QA", "P1")); }
    @Test void validadeDeUsoExpiradaImpedeDispensar() { condicao = new PharmaciaCondicaoUnidade(); condicao.validadeUso = LocalDateTime.now().minusSeconds(1); assertThrows(IllegalArgumentException.class, () -> service.verificarDisponivel(1L, "QA")); }
    @Test void quantidadeSuperiorAoDisponivelNaoSegrega() { PharmaciaOperacaoRequest r = request("QUARENTENA"); r.quantidade = 11; assertThrows(IllegalArgumentException.class, () -> service.criar(r, "QA", "P1")); verifyNoInteractions(service.movimentos); }
    @Test void inventarioAceitaZeroMasQuarentenaNao() { PharmaciaOperacaoRequest r = request("INVENTARIO"); r.quantidade = 0; assertDoesNotThrow(() -> PharmaciaOperacaoService.validar(r)); r.tipo = "QUARENTENA"; assertThrows(IllegalArgumentException.class, () -> PharmaciaOperacaoService.validar(r)); }
    @Test void pdfValidaAssinaturaETamanho() {
        assertNull(PharmaciaOperacaoService.validarPdf(null, null));
        String pdf = java.util.Base64.getEncoder().encodeToString("%PDF-1.7\nTESTE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertNotNull(PharmaciaOperacaoService.validarPdf("referencia.pdf", pdf));
        assertThrows(IllegalArgumentException.class, () -> PharmaciaOperacaoService.validarPdf("arquivo.txt", pdf));
        assertThrows(IllegalArgumentException.class, () -> PharmaciaOperacaoService.validarPdf("arquivo.pdf", "dGVzdGU="));
        assertThrows(IllegalArgumentException.class, () -> PharmaciaOperacaoService.validarPdf("arquivo.pdf", "?"));
        assertThrows(IllegalArgumentException.class, () -> PharmaciaOperacaoService.validarPdf("arquivo.pdf", "A".repeat(6990509)));
    }
    @Test void documentoOutraUnidadeNaoAbre() {
        o.documentoNome = "referencia.pdf";
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> service.documento(1L, "OUTRA", "P1"));
        verify(service.em, never()).find(eq(PharmaciaOperacaoDocumento.class), any());
    }
    @Test void unidadeDiferenteNaoAvalia() { assertThrows(IllegalArgumentException.class, () -> service.avaliar(1L, "OUTRA", "P2", "LIBERAR", "TESTE", "DOC")); }
}

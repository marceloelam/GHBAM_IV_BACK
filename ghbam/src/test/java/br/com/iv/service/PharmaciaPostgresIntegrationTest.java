package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import br.com.iv.entity.*;
import br.com.iv.dto.PharmaciaOperacaoRequest;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.nio.charset.StandardCharsets;

/** Executa SOMENTE em PostgreSQL efemero local. Nunca conecta ao banco hospitalar. */
@EnabledIfEnvironmentVariable(named = "PHARMACIA_TEST_JDBC_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:55439/pharmacia_qa")
class PharmaciaPostgresIntegrationTest {
    static SessionFactory factory;
    static long loteId;
    static String categoriaNome;
    @BeforeAll static void iniciar() throws Exception {
        String url = System.getenv("PHARMACIA_TEST_JDBC_URL");
        if (!"jdbc:postgresql://127.0.0.1:55439/pharmacia_qa".equals(url)) throw new IllegalStateException("Banco isolado obrigatorio.");
        Configuration config = new Configuration();
        for (Class<?> type : List.of(PharmaciaCategoria.class, PharmaciaProduto.class, PharmaciaLote.class, PharmaciaEstoque.class,
                PharmaciaMovimentacao.class, PharmaciaDispensacao.class, PharmaciaOperacao.class, PharmaciaCondicaoUnidade.class,
                PharmaciaConfirmacaoChave.class, PrescricaoItemEstruturado.class, PharmaciaPermuta.class, PharmaciaPermutaItem.class)) config.addAnnotatedClass(type);
        config.setProperty("hibernate.connection.url", url).setProperty("hibernate.connection.username", "pharmacia_qa")
                .setProperty("hibernate.connection.password", "").setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create").setProperty("hibernate.show_sql", "false")
                .setProperty("hibernate.connection.pool_size", "8");
        factory = config.buildSessionFactory();
        // Voltar ao esquema anterior em banco descartavel e aplicar a migration real.
        // Nao usar drop-create ou este teste em qualquer endereco diferente do isolado acima.
        try (var session = factory.openSession()) {
            session.doWork(conn -> {
                try (var st = conn.createStatement()) {
                    st.execute("DROP TABLE pharmacia_operacao_unidade"); st.execute("DROP TABLE pharmacia_condicao_unidade"); st.execute("DROP TABLE pharmacia_confirmacao_chave");
                    st.execute("ALTER TABLE pharmacia_dispensacao DROP COLUMN prescricao_item_id, DROP COLUMN bam_id, DROP COLUMN receita_referencia, DROP COLUMN receita_data, DROP COLUMN receita_validade, DROP COLUMN receita_retida, DROP COLUMN conferida, DROP COLUMN conferencia_justificativa");
                    for (String migration : List.of("V190__pharmacia_operacoes_unidade.sql", "V191__pharmacia_lotes_derivados_idempotencia.sql")) try (var resource = PharmaciaPostgresIntegrationTest.class.getResourceAsStream("/db/migration-v2/" + migration)) {
                        if (resource == null) throw new IllegalStateException("Migration ausente");
                        st.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                    } catch (java.io.IOException e) { throw new RuntimeException(e); }
                }
            });
        }
    }
    @AfterAll static void fechar() { if (factory != null) factory.close(); }
    static <T> T transacao(Function<EntityManager, T> fn) {
        try (var em = factory.createEntityManager()) {
            var tx = em.getTransaction(); tx.begin();
            try { T value = fn.apply(em); tx.commit(); return value; }
            catch (RuntimeException ex) { if (tx.isActive()) tx.rollback(); throw ex; }
        }
    }
    @BeforeEach void preparar() {
        loteId = transacao(em -> {
            PharmaciaCategoria c = new PharmaciaCategoria(); c.setNome("TESTE " + java.util.UUID.randomUUID()); c.setUnidadeSigla("QA"); em.persist(c);
            categoriaNome = c.getNome();
            PharmaciaProduto p = new PharmaciaProduto(); p.setCategoria(c); p.setNomeComercial(c.getNome()); p.setNomeGenerico("TESTE"); p.setFormaFarmaceutica("comprimido"); p.setUnidadeMedida("comprimido"); p.setUnidadeSigla("QA"); em.persist(p);
            PharmaciaLote l = new PharmaciaLote(); l.setProduto(p); l.setNumeroLote(c.getNome()); l.setDataFabricacao(LocalDate.now().minusDays(1)); l.setDataValidade(LocalDate.now().plusDays(30)); l.setQuantidadeInicial(10); l.setQuantidadeAtual(10); l.setUnidadeSigla("QA"); em.persist(l);
            PharmaciaEstoque e = new PharmaciaEstoque(); e.setLote(l); e.setQuantidade(10); e.setUnidadeSigla("QA"); em.persist(e); em.flush(); return l.getId();
        });
    }
    static PharmaciaOperacaoService operacoes(EntityManager em) {
        PharmaciaOperacaoService o = new PharmaciaOperacaoService(); o.em = em; o.logs = mock(LogService.class); o.mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        PharmaciaMovimentacaoService m = new PharmaciaMovimentacaoService(); m.em = em; m.operacoes = o; o.movimentos = m; return o;
    }
    static PharmaciaDispensacaoService dispensacoes(EntityManager em) {
        PharmaciaDispensacaoService d = new PharmaciaDispensacaoService(); d.em = em; d.operacoes = operacoes(em); d.movimentacoes = d.operacoes.movimentos; return d;
    }
    static PharmaciaDispensacao item(int quantidade) {
        PharmaciaLote l = new PharmaciaLote(); l.setId(loteId);
        PharmaciaDispensacao d = new PharmaciaDispensacao(); d.setLote(l); d.setPacienteId(1L); d.setPacienteNome("PACIENTE FICTICIO");
        d.setQuantidade(quantidade); d.setDuracao(3); d.setMedicoNome("MEDICO TESTE"); d.setMedicoCRM("TESTE"); d.setUsuarioMatricula("P1"); d.setUnidadeSigla("QA"); return d;
    }
    static int saldo() { return transacao(em -> em.createQuery("select e.quantidade from PharmaciaEstoque e where e.lote.id = :l and e.unidadeSigla = 'QA'", Integer.class).setParameter("l", loteId).getSingleResult()); }
    @Test void disputaPelasUltimasUnidadesNaoNegativaSaldo() throws Exception {
        CountDownLatch inicio = new CountDownLatch(1); ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> retirar = () -> { inicio.await(); try { transacao(em -> dispensacoes(em).save(item(7))); return true; } catch (IllegalArgumentException e) { return false; } };
            Future<Boolean> a = pool.submit(retirar); Future<Boolean> b = pool.submit(retirar); inicio.countDown();
            assertNotEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)); assertEquals(3, saldo());
            assertEquals(Long.valueOf(1), transacao(em -> em.createQuery("select count(d) from PharmaciaDispensacao d where d.lote.id = :l", Long.class).setParameter("l", loteId).getSingleResult()));
        } finally { pool.shutdownNow(); }
    }
    @Test void falhaNoSegundoItemRevertePrimeiraBaixaEHistorico() {
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> dispensacoes(em).saveBatch(List.of(item(7), item(7)))));
        assertEquals(10, saldo());
        assertEquals(Long.valueOf(0), transacao(em -> em.createQuery("select count(m) from PharmaciaMovimentacao m where m.lote.id = :l", Long.class).setParameter("l", loteId).getSingleResult()));
    }
    @Test void devolucaoQuarentenaLiberacaoEDescartePreservamSaldo() {
        Long id = transacao(em -> dispensacoes(em).save(item(3)).getId()); assertEquals(7, saldo());
        transacao(em -> { dispensacoes(em).devolver(id, loteId, 3, "QA", "P1"); return null; }); assertEquals(7, saldo());
        Long op = transacao(em -> em.createQuery("select o.id from PharmaciaOperacao o where o.dispensacaoId = :d", Long.class).setParameter("d", id).getSingleResult());
        transacao(em -> operacoes(em).avaliar(op, "QA", "P2", "LIBERAR", "Integridade e conservacao verificadas", "DOC TESTE")); assertEquals(10, saldo());
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> operacoes(em).avaliar(op, "QA", "P2", "LIBERAR", "REPETIR", "DOC TESTE"))); assertEquals(10, saldo());
        PharmaciaOperacaoRequest r = new PharmaciaOperacaoRequest(); r.chaveOperacao = java.util.UUID.randomUUID().toString(); r.tipo = "QUARENTENA"; r.loteId = loteId; r.quantidade = 2; r.justificativa = "Embalagem comprometida";
        Long q = transacao(em -> operacoes(em).criar(r, "QA", "P1").id); assertEquals(8, saldo());
        transacao(em -> operacoes(em).avaliar(q, "QA", "P2", "DESCARTAR", "Destinacao confirmada", "DOC TESTE")); assertEquals(8, saldo());
    }
    @Test void bloqueioDeQualidadeImpedeDispensacao() {
        PharmaciaOperacaoRequest r = new PharmaciaOperacaoRequest(); r.chaveOperacao = java.util.UUID.randomUUID().toString(); r.tipo = "RECOLHIMENTO"; r.loteId = loteId; r.justificativa = "Alerta teste"; r.documento = "DOC TESTE";
        transacao(em -> operacoes(em).criar(r, "QA", "P1"));
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> dispensacoes(em).save(item(1)))); assertEquals(10, saldo());
    }
    @Test void inventarioDetectaMovimentacaoPosterior() {
        PharmaciaOperacaoRequest r = new PharmaciaOperacaoRequest(); r.chaveOperacao = java.util.UUID.randomUUID().toString(); r.tipo = "INVENTARIO"; r.loteId = loteId; r.quantidade = 9; r.justificativa = "Contagem teste";
        Long id = transacao(em -> operacoes(em).criar(r, "QA", "P1").id);
        transacao(em -> dispensacoes(em).save(item(2)));
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> operacoes(em).avaliar(id, "QA", "P2", "CONCILIAR", "TESTE", ""))); assertEquals(8, saldo());
    }
    @Test void fracionamentoConservaTotalERastreiaLoteDerivado() {
        PharmaciaOperacaoRequest r = new PharmaciaOperacaoRequest(); r.chaveOperacao = java.util.UUID.randomUUID().toString(); r.tipo = "FRACIONAMENTO"; r.loteId = loteId;
        r.numeroLoteDestino = "FRACIONADO-" + loteId; r.quantidade = 4; r.justificativa = "Separacao em unidades individuais"; r.documento = "PROTOCOLO TESTE"; r.validadeUso = java.time.LocalDateTime.now().plusDays(3);
        Long destino = transacao(em -> operacoes(em).criar(r, "QA", "P1").loteDestinoId);
        assertEquals(6, saldo());
        int saldoDestino = transacao(em -> em.createQuery("select e.quantidade from PharmaciaEstoque e where e.lote.id = :l", Integer.class).setParameter("l", destino).getSingleResult());
        assertEquals(4, saldoDestino); assertEquals(10, saldo() + saldoDestino);
        long eventos = transacao(em -> em.createQuery("select count(m) from PharmaciaMovimentacao m where m.lote.id in (:a, :b)", Long.class).setParameter("a", loteId).setParameter("b", destino).getSingleResult());
        assertEquals(2, eventos);
        PharmaciaOperacao repetida = transacao(em -> operacoes(em).criar(r, "QA", "P1")); assertEquals(destino, repetida.loteDestinoId); assertEquals(6, saldo());
        PharmaciaOperacaoRequest recall = new PharmaciaOperacaoRequest(); recall.chaveOperacao = java.util.UUID.randomUUID().toString(); recall.tipo = "RECOLHIMENTO"; recall.loteId = loteId; recall.justificativa = "RECOLHIMENTO TESTE"; recall.documento = "DOC TESTE";
        transacao(em -> operacoes(em).criar(recall, "QA", "P1"));
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> { operacoes(em).verificarDisponivel(destino, "QA"); return null; }));
        long bloqueados = transacao(em -> em.createQuery("select count(c) from PharmaciaCondicaoUnidade c where c.unidade = 'QA' and c.loteId in (:a,:b) and c.bloqueado = true", Long.class).setParameter("a", loteId).setParameter("b", destino).getSingleResult()); assertEquals(2, bloqueados);
    }
    @Test void operacaoRepetidaNaoDescontaNovamente() {
        PharmaciaOperacaoRequest r = new PharmaciaOperacaoRequest(); r.chaveOperacao = java.util.UUID.randomUUID().toString(); r.tipo = "QUARENTENA"; r.loteId = loteId; r.quantidade = 2; r.justificativa = "TESTE";
        Long id = transacao(em -> operacoes(em).criar(r, "QA", "P1").id);
        assertEquals(id, transacao(em -> operacoes(em).criar(r, "QA", "P1").id)); assertEquals(8, saldo());
        r.quantidade = 3; assertThrows(IllegalArgumentException.class, () -> transacao(em -> operacoes(em).criar(r, "QA", "P1"))); assertEquals(8, saldo());
    }
    @Test void confirmacaoRepetidaRecuperaRespostaSemNovaSaida() {
        br.com.iv.dto.PharmaciaConfirmacaoRequest req = new br.com.iv.dto.PharmaciaConfirmacaoRequest(); req.itens = List.of(item(3)); req.chaveConfirmacao = java.util.UUID.randomUUID().toString();
        String hash = transacao(em -> { PharmaciaDispensacaoControleService c = new PharmaciaDispensacaoControleService(); c.em = em; c.mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(); return c.hash(req); });
        Long id = transacao(em -> { PharmaciaDispensacao d = dispensacoes(em).save(item(3)); PharmaciaDispensacaoControleService c = new PharmaciaDispensacaoControleService(); c.em = em; c.mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(); c.registrar(req, hash, List.of(d)); return d.getId(); });
        List<PharmaciaDispensacao> recuperada = transacao(em -> { PharmaciaDispensacaoControleService c = new PharmaciaDispensacaoControleService(); c.em = em; c.mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(); return c.recuperar(req, c.hash(req)); });
        assertEquals(id, recuperada.get(0).getId()); assertEquals(7, saldo());
        req.itens.get(0).setQuantidade(4);
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> { PharmaciaDispensacaoControleService c = new PharmaciaDispensacaoControleService(); c.em = em; c.mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(); return c.recuperar(req, c.hash(req)); })); assertEquals(7, saldo());
    }

    @Test void entradaDocumentadaSomaUmaVezComMetadados() {
        PharmaciaOperacaoRequest r = new PharmaciaOperacaoRequest(); r.chaveOperacao = java.util.UUID.randomUUID().toString(); r.tipo = "ENTRADA"; r.loteId = loteId;
        r.quantidade = 5; r.documento = "ORIGEM TESTE"; r.justificativa = "Recebimento teste"; r.estoqueMinimo = 2; r.localizacao = "PRATELEIRA TESTE";
        Long id = transacao(em -> operacoes(em).criar(r, "QA", "P1").id); assertEquals(15, saldo());
        assertEquals(id, transacao(em -> operacoes(em).criar(r, "QA", "P1").id)); assertEquals(15, saldo());
        long quantidadeMovimentos = transacao(em -> em.createQuery("select count(m) from PharmaciaMovimentacao m where m.lote.id = :l and m.tipo = :t", Long.class).setParameter("l", loteId).setParameter("t", PharmaciaMovimentacao.TipoMovimentacao.ENTRADA).getSingleResult()); assertEquals(1, quantidadeMovimentos);
    }
    @Test void inativacaoZeradaPreservaDispensacaoELote() {
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> { PharmaciaLoteService s = new PharmaciaLoteService(); s.em = em; s.delete(loteId); return null; }));
        transacao(em -> dispensacoes(em).save(item(10))); assertEquals(0, saldo());
        transacao(em -> { PharmaciaLoteService s = new PharmaciaLoteService(); s.em = em; s.delete(loteId); return null; });
        boolean ativo = transacao(em -> em.find(PharmaciaLote.class, loteId).getAtivo()); assertFalse(ativo);
        long historico = transacao(em -> em.createQuery("select count(d) from PharmaciaDispensacao d where d.lote.id = :l", Long.class).setParameter("l", loteId).getSingleResult()); assertEquals(1, historico);
    }

    // Permuta: SOLIC pede a QA (fornecedora, que tem o saldo do lote).
    static PharmaciaPermutaService permutas(EntityManager em) {
        PharmaciaPermutaService s = new PharmaciaPermutaService(); s.em = em; s.operacoes = operacoes(em);
        s.estoqueService = new PharmaciaEstoqueService(); s.estoqueService.em = em; return s;
    }
    static Long solicitarPermuta(int quantidade) {
        return transacao(em -> {
            PharmaciaPermuta p = new PharmaciaPermuta(); p.setUnidadeOrigemSigla("SOLIC"); p.setUnidadeDestinoSigla("QA");
            p.setSolicitanteMatricula("P1"); p.setAprovadorMatricula("INJETADO"); p.setStatus(PharmaciaPermuta.StatusPermuta.FINALIZADO);
            PharmaciaLote l = new PharmaciaLote(); l.setId(loteId);
            PharmaciaPermutaItem i = new PharmaciaPermutaItem(); i.setLote(l); i.setQuantidadeSolicitada(quantidade); i.setQuantidadeAprovada(999);
            p.setItens(new java.util.ArrayList<>(List.of(i)));
            return permutas(em).solicitar(p).getId();
        });
    }
    static Long itemDaPermuta(Long id) { return transacao(em -> em.find(PharmaciaPermuta.class, id).getItens().get(0).getId()); }
    static PharmaciaPermuta.StatusPermuta status(Long id) { return transacao(em -> em.find(PharmaciaPermuta.class, id).getStatus()); }
    static Integer saldoEm(String unidade) {
        return transacao(em -> em.createQuery("select e.quantidade from PharmaciaEstoque e where e.lote.id = :l and e.unidadeSigla = :u", Integer.class)
                .setParameter("l", loteId).setParameter("u", unidade).getResultStream().findFirst().orElse(null));
    }
    static int loteAtual() { return transacao(em -> em.find(PharmaciaLote.class, loteId).getQuantidadeAtual()); }

    @Test void permutaCompletaTransportaEConfereRecebimentoComDivergencia() {
        Long id = solicitarPermuta(6);
        assertEquals(PharmaciaPermuta.StatusPermuta.PENDENTE, status(id));
        transacao(em -> { PharmaciaPermuta p = em.find(PharmaciaPermuta.class, id);
            assertNull(p.getAprovadorMatricula()); assertEquals(6, p.getItens().get(0).getQuantidadeAprovada()); return null; });
        Long item = itemDaPermuta(id);

        assertThrows(IllegalStateException.class, () -> transacao(em -> permutas(em).aprovar(id, "P2", java.util.Map.of(String.valueOf(item), 5), "SOLIC")));
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> permutas(em).aprovar(id, "P2", java.util.Map.of(String.valueOf(item), 7), "QA")));
        transacao(em -> permutas(em).aprovar(id, "P2", java.util.Map.of(String.valueOf(item), 5), "QA"));
        assertEquals(PharmaciaPermuta.StatusPermuta.APROVADO, status(id));
        assertEquals(10, saldo()); assertEquals(10, loteAtual());

        assertThrows(IllegalStateException.class, () -> transacao(em -> permutas(em).iniciarTransporte(id, "P3", "SOLIC")));
        transacao(em -> permutas(em).iniciarTransporte(id, "P3", "QA"));
        assertEquals(PharmaciaPermuta.StatusPermuta.EM_TRANSPORTE, status(id));
        assertEquals(5, saldo()); assertEquals(5, loteAtual()); assertNull(saldoEm("SOLIC"));
        assertThrows(IllegalStateException.class, () -> transacao(em -> permutas(em).iniciarTransporte(id, "P3", "QA")));
        assertEquals(5, saldo());
        assertThrows(IllegalStateException.class, () -> transacao(em -> permutas(em).recusar(id, "Tarde", "P2", "QA")));

        var decisao = new br.com.iv.dto.PharmaciaPermutaFinalizarRequest.ItemDecisao(); decisao.setQuantidadeRecebida(4);
        var req = new br.com.iv.dto.PharmaciaPermutaFinalizarRequest(); req.setDecisoes(java.util.Map.of(String.valueOf(item), decisao));
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> permutas(em).finalizar(id, "P4", req, "SOLIC")));
        assertThrows(IllegalStateException.class, () -> transacao(em -> permutas(em).finalizar(id, "P4", req, "QA")));
        assertEquals(PharmaciaPermuta.StatusPermuta.EM_TRANSPORTE, status(id)); assertNull(saldoEm("SOLIC"));
        decisao.setNotaDivergencia("1 frasco quebrado no transporte");
        transacao(em -> permutas(em).finalizar(id, "P4", req, "SOLIC"));
        assertEquals(PharmaciaPermuta.StatusPermuta.FINALIZADO, status(id));
        assertEquals(4, saldoEm("SOLIC")); assertEquals(5, saldo()); assertEquals(9, loteAtual());
        assertThrows(IllegalStateException.class, () -> transacao(em -> permutas(em).finalizar(id, "P4", req, "SOLIC")));
        assertEquals(4, saldoEm("SOLIC"));

        List<PharmaciaMovimentacao> movs = transacao(em -> em.createQuery("from PharmaciaMovimentacao m where m.lote.id = :l order by m.id", PharmaciaMovimentacao.class).setParameter("l", loteId).getResultList());
        assertEquals(2, movs.size());
        assertEquals(PharmaciaMovimentacao.TipoMovimentacao.SAIDA, movs.get(0).getTipo()); assertEquals(5, movs.get(0).getQuantidade());
        assertEquals("QA", movs.get(0).getUnidadeOrigemSigla()); assertEquals("SOLIC", movs.get(0).getUnidadeDestinoSigla());
        assertEquals(PharmaciaMovimentacao.TipoMovimentacao.ENTRADA, movs.get(1).getTipo()); assertEquals(4, movs.get(1).getQuantidade());
        assertTrue(movs.get(1).getJustificativa().contains("DIVERGENCIA: enviado 5, recebido 4"));
    }

    @Test void permutaCanceladaAntesDoTransporteNaoMoveEstoque() {
        Long id = solicitarPermuta(3);
        transacao(em -> permutas(em).aprovar(id, "P2", null, "QA"));
        assertThrows(IllegalStateException.class, () -> transacao(em -> permutas(em).recusar(id, "Pedido indevido", "P2", "SOLIC")));
        transacao(em -> permutas(em).recusar(id, "Lote reservado para uso interno", "P2", "QA"));
        assertEquals(PharmaciaPermuta.StatusPermuta.RECUSADO, status(id));
        assertThrows(IllegalStateException.class, () -> transacao(em -> permutas(em).iniciarTransporte(id, "P3", "QA")));
        assertEquals(10, saldo()); assertEquals(10, loteAtual());
    }

    @Test void transporteRevalidaSaldoConsumidoAposAprovacao() {
        Long id = solicitarPermuta(5);
        transacao(em -> permutas(em).aprovar(id, "P2", null, "QA"));
        transacao(em -> dispensacoes(em).save(item(8))); assertEquals(2, saldo());
        assertThrows(IllegalStateException.class, () -> transacao(em -> permutas(em).iniciarTransporte(id, "P3", "QA")));
        assertEquals(PharmaciaPermuta.StatusPermuta.APROVADO, status(id)); assertEquals(2, saldo());
    }

    @Test void transporteBloqueadoPorRecolhimentoNaUnidadeFornecedora() {
        Long id = solicitarPermuta(2);
        transacao(em -> permutas(em).aprovar(id, "P2", null, "QA"));
        PharmaciaOperacaoRequest r = new PharmaciaOperacaoRequest(); r.chaveOperacao = java.util.UUID.randomUUID().toString(); r.tipo = "RECOLHIMENTO"; r.loteId = loteId; r.justificativa = "Alerta sanitario teste"; r.documento = "DOC TESTE";
        transacao(em -> operacoes(em).criar(r, "QA", "P1"));
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> permutas(em).iniciarTransporte(id, "P3", "QA")));
        assertEquals(10, saldo());
    }

    static PharmaciaCatalogoService catalogo(EntityManager em) { PharmaciaCatalogoService s = new PharmaciaCatalogoService(); s.em = em; return s; }
    static long produtosEm(String unidade) {
        return transacao(em -> em.createQuery("select count(p) from PharmaciaProduto p where p.unidadeSigla = :u", Long.class).setParameter("u", unidade).getSingleResult());
    }

    @Test void catalogoDeOutraUnidadeVisualizaEImportaSoOsEscolhidos() {
        String origem = "ORIG" + loteId, destino = "DEST" + loteId;
        Long[] ids = transacao(em -> {
            PharmaciaProduto base = em.find(PharmaciaLote.class, loteId).getProduto();
            PharmaciaProduto repetido = PharmaciaCatalogoService.copia(base, origem); repetido.setNomeComercial("REPETIDO " + loteId); em.persist(repetido);
            PharmaciaProduto novo = PharmaciaCatalogoService.copia(base, origem); novo.setNomeComercial("NOVO " + loteId); novo.setPrincipioAtivo("AMOXICILINA"); novo.setAntimicrobiano(true); em.persist(novo);
            PharmaciaProduto naoEscolhido = PharmaciaCatalogoService.copia(base, origem); naoEscolhido.setNomeComercial("OUTRO " + loteId); em.persist(naoEscolhido);
            PharmaciaProduto existente = PharmaciaCatalogoService.copia(base, destino); existente.setNomeComercial("repetido  " + loteId); em.persist(existente);
            return new Long[] { repetido.getId(), novo.getId() };
        });
        List<java.util.Map<String, Object>> visao = transacao(em -> catalogo(em).visualizar(origem, destino));
        assertEquals(3, visao.size());
        assertEquals(Boolean.TRUE, visao.stream().filter(m -> m.get("id").equals(ids[0])).findFirst().orElseThrow().get("jaExiste"));
        assertEquals(1L, produtosEm(destino));

        var r = transacao(em -> catalogo(em).importar(origem, destino, List.of(ids[0], ids[1])));
        assertEquals(1, r.get("importados")); assertEquals(List.of("REPETIDO " + loteId), r.get("ignorados"));
        assertEquals(2L, produtosEm(destino)); assertEquals(3L, produtosEm(origem));
        PharmaciaProduto copia = transacao(em -> em.createQuery("from PharmaciaProduto p where p.unidadeSigla = :u and p.nomeComercial = :n", PharmaciaProduto.class)
                .setParameter("u", destino).setParameter("n", "NOVO " + loteId).getSingleResult());
        assertNotEquals(ids[1], copia.getId()); assertEquals("AMOXICILINA", copia.getPrincipioAtivo()); assertTrue(copia.getAntimicrobiano());

        assertEquals(0, transacao(em -> catalogo(em).importar(origem, destino, List.of(ids[1]))).get("importados"));
        assertThrows(IllegalArgumentException.class, () -> transacao(em -> catalogo(em).importar(destino, destino, List.of(ids[1]))));
    }

    @Test void planilhaSimulaSemGravarEGravaSoLinhasValidas() {
        String destino = "PLAN" + loteId;
        var valida = new java.util.HashMap<String, Object>(java.util.Map.of("nomeComercial", "DIPIRONA 500MG PLAN", "nomeGenerico", "DIPIRONA", "formaFarmaceutica", "comprimido",
                "unidadeMedida", "mg", "categoria", categoriaNome.toLowerCase(), "principioAtivo", "DIPIRONA", "altaVigilancia", "SIM", "listaControlada", ""));
        var semGenerico = new java.util.HashMap<String, Object>(valida); semGenerico.put("nomeComercial", "SEM GENERICO"); semGenerico.remove("nomeGenerico");
        var repetida = new java.util.HashMap<String, Object>(valida); repetida.put("nomeComercial", "dipirona 500mg plan");
        var listaErrada = new java.util.HashMap<String, Object>(valida); listaErrada.put("nomeComercial", "OUTRO PLAN"); listaErrada.put("listaControlada", "Z9");
        var semCategoria = new java.util.HashMap<String, Object>(valida); semCategoria.put("nomeComercial", "SEM CATEGORIA"); semCategoria.put("categoria", "INEXISTENTE");
        List<java.util.Map<String, Object>> linhas = List.of(valida, semGenerico, repetida, listaErrada, semCategoria);

        var simulado = transacao(em -> catalogo(em).importarPlanilha(destino, linhas, true));
        assertEquals(1, simulado.get("validas")); assertEquals(4, simulado.get("invalidas")); assertEquals(0L, produtosEm(destino));

        var gravado = transacao(em -> catalogo(em).importarPlanilha(destino, linhas, false));
        assertEquals(1, gravado.get("importados")); assertEquals(1L, produtosEm(destino));
        PharmaciaProduto p = transacao(em -> em.createQuery("from PharmaciaProduto p where p.unidadeSigla = :u", PharmaciaProduto.class).setParameter("u", destino).getSingleResult());
        assertEquals("COMPRIMIDO", p.getFormaFarmaceutica()); assertTrue(p.getAltaVigilancia()); assertNull(p.getListaControlada());
        assertEquals(0, transacao(em -> catalogo(em).importarPlanilha(destino, linhas, false)).get("importados"));
    }

    @Test void naoSolicitaLoteVencido() {
        transacao(em -> { em.find(PharmaciaLote.class, loteId).setDataValidade(LocalDate.now().minusDays(1)); return null; });
        assertThrows(IllegalArgumentException.class, () -> solicitarPermuta(1));
    }

}

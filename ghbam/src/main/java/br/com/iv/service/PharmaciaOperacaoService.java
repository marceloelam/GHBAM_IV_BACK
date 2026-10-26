package br.com.iv.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import br.com.iv.dto.PharmaciaOperacaoRequest;
import br.com.iv.dto.LogDto;
import br.com.iv.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PharmaciaOperacaoService {
    @Inject EntityManager em;
    @Inject com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Inject PharmaciaMovimentacaoService movimentos;
    @Inject LogService logs;
    @Inject ArmazenamentoArquivoService armazenamento;
    static final Set<String> TIPOS = Set.of("ENTRADA", "INVENTARIO", "QUARENTENA", "RECOLHIMENTO", "DESCARTE", "TEMPERATURA", "ABERTURA", "FRACIONAMENTO");

    static String exigirTexto(String texto, int limite, String campo) {
        if (texto == null || texto.isBlank() || texto.trim().length() > limite) throw new IllegalArgumentException(campo + " obrigatorio, ate " + limite + " caracteres.");
        return texto.trim();
    }

    static byte[] validarPdf(String nome, String base64) {
        if (nome == null && base64 == null) return null;
        exigirTexto(nome, 120, "Nome do PDF");
        if (!nome.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf") || nome.contains("\r") || nome.contains("\n")) throw new IllegalArgumentException("Nome do PDF invalido.");
        if (base64 == null || base64.length() > 6990508) throw new IllegalArgumentException("PDF acima de 5 MB ou sem conteudo.");
        byte[] bytes;
        try { bytes = java.util.Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Conteudo do PDF invalido."); }
        if (bytes.length < 5 || bytes.length > 5242880 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') throw new IllegalArgumentException("Arquivo PDF invalido ou acima de 5 MB.");
        return bytes;
    }
    public byte[] documento(Long id, String unidade, String matricula) {
        PharmaciaOperacao o = em.find(PharmaciaOperacao.class, id);
        if (o == null || !unidade.equals(o.unidade) || o.documentoNome == null) throw new jakarta.ws.rs.NotFoundException();
        PharmaciaOperacaoDocumento d = em.find(PharmaciaOperacaoDocumento.class, id);
        if (d == null) throw new jakarta.ws.rs.NotFoundException();
        auditar(o, "visualizou documento", matricula);
        if (d.armazenamentoChave != null && !d.armazenamentoChave.isBlank()) {
            try { return armazenamento.ler(d.armazenamentoChave); }
            catch (RuntimeException e) { throw new jakarta.ws.rs.ServiceUnavailableException("Documento temporariamente indisponivel"); }
        }
        return java.util.Base64.getDecoder().decode(d.conteudoBase64);
    }
    static void validar(PharmaciaOperacaoRequest r) {
        if (r == null || !TIPOS.contains(r.tipo == null ? "" : r.tipo) || r.loteId == null || r.loteId <= 0) throw new IllegalArgumentException("Informe operacao e lote validos.");
        validarPdf(r.documentoNome, r.documentoBase64);
        exigirTexto(r.justificativa, 1000, "Justificativa");
        exigirTexto(r.chaveOperacao, 80, "Chave da operacao");
        if (r.estoqueMinimo != null && r.estoqueMinimo < 0 || r.localizacao != null && r.localizacao.length() > 50) throw new IllegalArgumentException("Parametros de estoque invalidos.");
        if (r.documento != null && r.documento.length() > 120) throw new IllegalArgumentException("Documento excede 120 caracteres.");
        if (Set.of("ENTRADA", "INVENTARIO", "QUARENTENA", "DESCARTE", "ABERTURA", "FRACIONAMENTO").contains(r.tipo) &&
                (r.quantidade == null || r.quantidade < 0 || (!"INVENTARIO".equals(r.tipo) && r.quantidade == 0))) throw new IllegalArgumentException("Quantidade inteira invalida.");
        if ("FRACIONAMENTO".equals(r.tipo)) exigirTexto(r.numeroLoteDestino, 50, "Identificacao do lote fracionado");
        if ("TEMPERATURA".equals(r.tipo) && (r.temperatura == null || r.limiteMinimo == null || r.limiteMaximo == null ||
                r.limiteMinimo.compareTo(r.limiteMaximo) >= 0 || r.temperatura.abs().doubleValue() > 9999 ||
                r.limiteMinimo.abs().doubleValue() > 9999 || r.limiteMaximo.abs().doubleValue() > 9999)) throw new IllegalArgumentException("Informe temperatura e limites aprovados para o produto.");
        if (Set.of("ABERTURA", "FRACIONAMENTO").contains(r.tipo) && (r.validadeUso == null || !r.validadeUso.isAfter(LocalDateTime.now()))) throw new IllegalArgumentException("Informe validade de uso futura aprovada para o produto.");
        if (Set.of("ENTRADA", "DESCARTE", "ABERTURA", "FRACIONAMENTO", "RECOLHIMENTO", "TEMPERATURA").contains(r.tipo)) exigirTexto(r.documento, 120, "Referencia do documento/protocolo");
    }

    public Map<String, Long> resumo(String unidade) {
        Long pendentes = em.createQuery("select count(o) from PharmaciaOperacao o where o.unidade = :u and o.status = 'PENDENTE'", Long.class).setParameter("u", unidade).getSingleResult();
        Long bloqueados = em.createQuery("select count(c) from PharmaciaCondicaoUnidade c where c.unidade = :u and c.bloqueado = true", Long.class).setParameter("u", unidade).getSingleResult();
        Long expirados = em.createQuery("select count(c) from PharmaciaCondicaoUnidade c where c.unidade = :u and c.validadeUso <= :agora", Long.class).setParameter("u", unidade).setParameter("agora", LocalDateTime.now()).getSingleResult();
        Long negativos = em.createQuery("select count(e) from PharmaciaEstoque e where e.unidadeSigla = :u and e.quantidade < 0", Long.class).setParameter("u", unidade).getSingleResult();
        return Map.of("pendentes", pendentes, "bloqueados", bloqueados, "validadeUsoExpirada", expirados, "saldosNegativos", negativos);
    }

    public List<PharmaciaOperacao> listar(String unidade) {
        return em.createQuery("from PharmaciaOperacao where unidade = :u order by criadoEm desc, id desc", PharmaciaOperacao.class).setParameter("u", unidade).setMaxResults(1000).getResultList();
    }
    public List<PharmaciaCondicaoUnidade> condicoes(String unidade) {
        return em.createQuery("from PharmaciaCondicaoUnidade where unidade = :u", PharmaciaCondicaoUnidade.class).setParameter("u", unidade).getResultList();
    }

    public void verificarDisponivel(Long lote, String unidade) {
        PharmaciaCondicaoUnidade c = condicao(lote, unidade, false);
        if (c != null && (c.bloqueado || (c.validadeUso != null && !c.validadeUso.isAfter(LocalDateTime.now())))) throw new IllegalArgumentException("Lote indisponivel na unidade: bloqueio de qualidade ou validade apos abertura expirada.");
    }
    private PharmaciaCondicaoUnidade condicao(Long lote, String unidade, boolean criar) {
        PharmaciaCondicaoUnidade c = em.createQuery("from PharmaciaCondicaoUnidade where loteId = :l and unidade = :u", PharmaciaCondicaoUnidade.class)
                .setParameter("l", lote).setParameter("u", unidade).getResultStream().findFirst().orElse(null);
        if (c == null && criar) { c = new PharmaciaCondicaoUnidade(); c.loteId = lote; c.unidade = unidade; em.persist(c); }
        return c;
    }
    private PharmaciaEstoque estoque(Long lote, String unidade) {
        if (em.find(PharmaciaLote.class, lote, LockModeType.PESSIMISTIC_WRITE) == null) throw new IllegalArgumentException("Lote inexistente.");
        PharmaciaEstoque e = em.createQuery("from PharmaciaEstoque where lote.id = :l and unidadeSigla = :u", PharmaciaEstoque.class)
                .setParameter("l", lote).setParameter("u", unidade).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElse(null);
        if (e != null) em.refresh(e, LockModeType.PESSIMISTIC_WRITE);
        if (e == null || !Boolean.TRUE.equals(e.getAtivo())) throw new IllegalArgumentException("Lote sem estoque ativo na unidade.");
        return e;
    }
    private PharmaciaOperacao registro(String tipo, Long lote, String unidade, String matricula, String motivo) {
        PharmaciaOperacao o = new PharmaciaOperacao(); o.tipo = tipo; o.loteId = lote; o.unidade = unidade; o.matricula = matricula;
        o.criadoEm = LocalDateTime.now(); o.justificativa = exigirTexto(motivo, 1000, "Justificativa"); return o;
    }
    private void ajustar(PharmaciaOperacao o, int delta, String matricula, String motivo) {
        if (delta == 0) return;
        PharmaciaMovimentacao m = new PharmaciaMovimentacao(); m.setTipo("ENTRADA".equals(o.tipo) ? PharmaciaMovimentacao.TipoMovimentacao.ENTRADA : PharmaciaMovimentacao.TipoMovimentacao.AJUSTE);
        PharmaciaLote lote = new PharmaciaLote(); lote.setId(o.loteId); m.setLote(lote);
        m.setQuantidade(delta); m.setUnidadeOrigemSigla(o.unidade); m.setUsuarioMatricula(matricula);
        m.setJustificativa((o.tipo + " #" + o.id + ": " + motivo).substring(0, Math.min(255, (o.tipo + " #" + o.id + ": " + motivo).length())));
        movimentos.save(m);
    }
    private void auditar(PharmaciaOperacao o, String acao, String matricula) {
        LogDto log = new LogDto(); log.setData(LocalDate.now()); log.setHora(java.time.LocalTime.now().toString().substring(0, 5)); log.setUnidade(o.unidade);
        log.setEvento(matricula + " " + acao + " farmacia " + o.tipo + " #" + o.id); logs.salvarLog(log);
    }

    @Transactional
    public PharmaciaOperacao criar(PharmaciaOperacaoRequest r, String unidade, String matricula) {
        validar(r); PharmaciaEstoque e = estoque(r.loteId, unidade);
        String hash;
        try {
            com.fasterxml.jackson.databind.node.ObjectNode json = mapper.valueToTree(r); json.remove("chaveOperacao");
            hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(json)));
        } catch (Exception ex) { throw new IllegalArgumentException("Solicitacao invalida."); }
        PharmaciaOperacao anterior = em.createQuery("from PharmaciaOperacao where unidade = :u and chave = :c", PharmaciaOperacao.class)
            .setParameter("u", unidade).setParameter("c", r.chaveOperacao).getResultStream().findFirst().orElse(null);
        if (anterior != null) {
            if (!hash.equals(anterior.hash) || !matricula.equals(anterior.matricula)) throw new IllegalArgumentException("Chave utilizada para outra solicitacao.");
            return anterior;
        }
        PharmaciaOperacao o = registro(r.tipo, r.loteId, unidade, matricula, r.justificativa);
        o.chave = r.chaveOperacao; o.hash = hash;
        o.quantidade = r.quantidade; o.saldoReferencia = e.getQuantidade(); o.documento = r.documento; o.documentoNome = r.documentoNome;
        o.temperatura = r.temperatura; o.limiteMinimo = r.limiteMinimo; o.limiteMaximo = r.limiteMaximo; o.validadeUso = r.validadeUso;
        boolean excursao = "TEMPERATURA".equals(r.tipo) && (r.temperatura.compareTo(r.limiteMinimo) < 0 || r.temperatura.compareTo(r.limiteMaximo) > 0);
        o.status = Set.of("INVENTARIO", "QUARENTENA", "RECOLHIMENTO").contains(r.tipo) || excursao ? "PENDENTE" : "FINALIZADO";
        if (Set.of("QUARENTENA", "DESCARTE", "ABERTURA", "FRACIONAMENTO").contains(r.tipo) && r.quantidade > e.getQuantidade()) throw new IllegalArgumentException("Quantidade superior ao saldo disponivel.");
        if ("ENTRADA".equals(r.tipo)) { PharmaciaMovimentacaoService.validarLoteDispensavel(e.getLote()); verificarDisponivel(r.loteId, unidade); }
        if ("ABERTURA".equals(r.tipo)) {
            if (r.validadeUso.isAfter(e.getLote().getDataValidade().plusDays(1).atStartOfDay().minusSeconds(1))) throw new IllegalArgumentException("Validade de uso nao pode ultrapassar a validade do fabricante.");
            PharmaciaCondicaoUnidade c = condicao(r.loteId, unidade, true);
            if (c.validadeUso != null && r.validadeUso.isAfter(c.validadeUso)) throw new IllegalArgumentException("Nao e permitido prolongar validade de uso ja registrada.");
            c.validadeUso = r.validadeUso;
        }
        if ("RECOLHIMENTO".equals(r.tipo) || excursao) { PharmaciaCondicaoUnidade c = condicao(r.loteId, unidade, true); c.bloqueado = true; c.motivo = r.justificativa; }
        if ("FRACIONAMENTO".equals(r.tipo)) {
            PharmaciaMovimentacaoService.validarLoteDispensavel(e.getLote()); verificarDisponivel(r.loteId, unidade);
            if (r.validadeUso.isAfter(e.getLote().getDataValidade().plusDays(1).atStartOfDay().minusSeconds(1))) throw new IllegalArgumentException("Validade derivada ultrapassa fabricante.");
            PharmaciaCondicaoUnidade origem = condicao(r.loteId, unidade, false);
            if (origem != null && origem.validadeUso != null && r.validadeUso.isAfter(origem.validadeUso)) throw new IllegalArgumentException("Validade derivada ultrapassa a validade de uso da origem.");
            Long duplicados = em.createQuery("select count(l) from PharmaciaLote l where l.produto.id = :p and l.numeroLote = :n", Long.class)
                .setParameter("p", e.getLote().getProduto().getId()).setParameter("n", r.numeroLoteDestino.trim()).getSingleResult();
            if (duplicados > 0) throw new IllegalArgumentException("Identificacao do lote derivado ja utilizada.");
            PharmaciaLote derivado = new PharmaciaLote(); derivado.setProduto(e.getLote().getProduto()); derivado.setNumeroLote(r.numeroLoteDestino.trim());
            derivado.setDataFabricacao(e.getLote().getDataFabricacao()); derivado.setDataValidade(r.validadeUso.toLocalDate());
            derivado.setQuantidadeInicial(r.quantidade); derivado.setQuantidadeAtual(0); derivado.setUnidadeSigla(unidade); em.persist(derivado); em.flush();
            PharmaciaEstoque novo = new PharmaciaEstoque(); novo.setLote(derivado); novo.setUnidadeSigla(unidade); novo.setQuantidade(0); novo.setEstoqueMinimo(0);
            novo.setObservacao("Fracionado do lote #" + r.loteId + ", documento " + r.documento); em.persist(novo);
            PharmaciaCondicaoUnidade destino = condicao(derivado.getId(), unidade, true); destino.validadeUso = r.validadeUso;
            o.loteDestinoId = derivado.getId();
        }
        em.persist(o);
        if (r.documentoBase64 != null) {
            em.flush();
            PharmaciaOperacaoDocumento anexo = new PharmaciaOperacaoDocumento();
            anexo.operacaoId = o.id; anexo.conteudoBase64 = r.documentoBase64;
            // MinIO ligado: PDF sai do banco; se falhar, fica no banco como antes.
            if (armazenamento.habilitado()) {
                try {
                    anexo.armazenamentoChave = armazenamento.salvar("farmacia/" + ArmazenamentoArquivoService.pastaUnidade(o.unidade) + "/operacao-" + o.id + "-" + java.util.UUID.randomUUID(),
                            java.util.Base64.getDecoder().decode(r.documentoBase64), "application/pdf");
                    anexo.conteudoBase64 = null;
                } catch (RuntimeException falha) {
                    org.jboss.logging.Logger.getLogger(PharmaciaOperacaoService.class)
                            .warnf("Farmacia: MinIO indisponivel, documento da operacao %s gravado no banco: %s", o.id, falha.getMessage());
                }
            }
            em.persist(anexo);
        } em.flush();
        if ("FRACIONAMENTO".equals(r.tipo)) {
            ajustar(o, -r.quantidade, matricula, r.justificativa);
            PharmaciaOperacao credito = registro("FRACIONAMENTO", o.loteDestinoId, unidade, matricula, "Origem lote #" + o.loteId + ", operacao #" + o.id);
            credito.id = o.id; ajustar(credito, r.quantidade, matricula, r.justificativa);
        }
        if ("ENTRADA".equals(r.tipo)) { ajustar(o, r.quantidade, matricula, r.justificativa); if (r.estoqueMinimo != null) e.setEstoqueMinimo(r.estoqueMinimo); if (r.localizacao != null) e.setLocalizacao(r.localizacao); }
        if (Set.of("QUARENTENA", "DESCARTE").contains(r.tipo)) ajustar(o, -r.quantidade, matricula, r.justificativa);
        if ("RECOLHIMENTO".equals(r.tipo)) {
            for (Long filho : descendentes(r.loteId, unidade)) if (!filho.equals(r.loteId)) {
                estoque(filho, unidade); PharmaciaCondicaoUnidade c = condicao(filho, unidade, true); c.bloqueado = true; c.motivo = "Recolhimento do lote de origem #" + r.loteId;
                PharmaciaOperacao bloqueio = registro("RECOLHIMENTO", filho, unidade, matricula, c.motivo); bloqueio.status = "PENDENTE"; bloqueio.origemId = o.id; bloqueio.documento = r.documento; em.persist(bloqueio);
            }
        }
        auditar(o, "registrou", matricula); return o;
    }

    @Transactional
    public PharmaciaOperacao devolver(PharmaciaDispensacao d, String matricula, String motivo) {
        estoque(d.getLoteId(), d.getUnidadeSigla());
        PharmaciaOperacao o = registro("DEVOLUCAO", d.getLoteId(), d.getUnidadeSigla(), matricula, motivo);
        o.quantidade = d.getQuantidade(); o.dispensacaoId = d.getId(); o.status = "PENDENTE";
        // O medicamento devolvido fica segregado. Nao creditar saldo ate a avaliacao.
        em.persist(o); d.setAtivo(false); auditar(o, "recebeu em quarentena", matricula); return o;
    }

    @Transactional
    public PharmaciaOperacao avaliar(Long id, String unidade, String matricula, String decisao, String parecer, String documento) {
        // Ordem de locks: lote, estoque, operacao. Igual a criacao e a dispensacao.
        PharmaciaOperacao inicial = em.find(PharmaciaOperacao.class, id);
        if (inicial == null || !unidade.equals(inicial.unidade)) throw new IllegalArgumentException("Operacao nao encontrada na unidade.");
        PharmaciaEstoque e = estoque(inicial.loteId, unidade);
        PharmaciaOperacao o = em.find(PharmaciaOperacao.class, id, LockModeType.PESSIMISTIC_WRITE); em.refresh(o, LockModeType.PESSIMISTIC_WRITE);
        if (!"PENDENTE".equals(o.status)) throw new IllegalArgumentException("Operacao ja avaliada.");
        String motivo = exigirTexto(parecer, 1000, "Parecer de integridade, conservacao e destino");
        if (!Set.of("LIBERAR", "DESCARTAR", "CONCILIAR", "CANCELAR").contains(decisao == null ? "" : decisao)) throw new IllegalArgumentException("Decisao invalida.");
        if ("INVENTARIO".equals(o.tipo)) {
            if (!Set.of("CONCILIAR", "CANCELAR").contains(decisao)) throw new IllegalArgumentException("Inventario aceita conciliar ou cancelar.");
            if (matricula.equals(o.matricula)) throw new IllegalArgumentException("Conciliacao exige outro profissional autenticado.");
            if ("CONCILIAR".equals(decisao)) {
                if (!o.saldoReferencia.equals(e.getQuantidade())) throw new IllegalArgumentException("Saldo mudou apos a contagem. Cancele e faca nova contagem.");
                ajustar(o, Math.subtractExact(o.quantidade, e.getQuantidade()), matricula, motivo);
            }
        } else if (Set.of("DEVOLUCAO", "QUARENTENA").contains(o.tipo)) {
            if (!Set.of("LIBERAR", "DESCARTAR").contains(decisao)) throw new IllegalArgumentException("Informe liberar ou descartar o material segregado.");
            exigirTexto(documento, 120, "Referencia da avaliacao/destinacao");
            if ("LIBERAR".equals(decisao)) { PharmaciaMovimentacaoService.validarLoteDispensavel(e.getLote()); verificarDisponivel(o.loteId, unidade); ajustar(o, o.quantidade, matricula, motivo); }
            // Descarte de material segregado nao desconta novamente o estoque disponivel.
        } else if (Set.of("RECOLHIMENTO", "TEMPERATURA").contains(o.tipo)) {
            if (!"LIBERAR".equals(decisao)) throw new IllegalArgumentException("Registre destino em Descarte/Quarentena e conclua o bloqueio mediante parecer.");
            exigirTexto(documento, 120, "Referencia da avaliacao tecnica");
        } else throw new IllegalArgumentException("Operacao nao avaliavel.");
        o.status = switch (decisao) { case "LIBERAR" -> "LIBERADO"; case "DESCARTAR" -> "DESCARTADO"; case "CANCELAR" -> "CANCELADO"; default -> "CONCILIADO"; };
        o.avaliador = matricula; o.avaliadoEm = LocalDateTime.now(); o.parecer = motivo;
        PharmaciaOperacao avaliacao = registro(decisao, o.loteId, unidade, matricula, motivo); avaliacao.origemId = o.id; avaliacao.status = "FINALIZADO"; avaliacao.documento = documento;
        em.persist(avaliacao); em.flush();
        if (Set.of("RECOLHIMENTO", "TEMPERATURA").contains(o.tipo)) {
            Long pendentes = em.createQuery("select count(o) from PharmaciaOperacao o where o.unidade = :u and o.loteId = :l and o.status = 'PENDENTE' and o.tipo in ('RECOLHIMENTO','TEMPERATURA')", Long.class).setParameter("u", unidade).setParameter("l", o.loteId).getSingleResult();
            condicao(o.loteId, unidade, true).bloqueado = pendentes > 0;
        }
        auditar(o, "avaliou " + decisao, matricula); return o;
    }

    private java.util.Set<Long> descendentes(Long lote, String unidade) {
        java.util.Set<Long> todos = new java.util.TreeSet<>(); todos.add(lote); java.util.Set<Long> fronteira = new java.util.HashSet<>(todos);
        while (!fronteira.isEmpty()) {
            List<Long> filhos = em.createQuery("select distinct o.loteDestinoId from PharmaciaOperacao o where o.unidade = :u and o.loteId in :l and o.loteDestinoId is not null", Long.class).setParameter("u", unidade).setParameter("l", fronteira).getResultList();
            fronteira = new java.util.HashSet<>(); for (Long filho : filhos) if (todos.add(filho)) fronteira.add(filho);
        }
        return todos;
    }
    public Map<String, Object> rastrear(Long lote, String unidade) {
        estoqueLeitura(lote, unidade); java.util.Set<Long> lotes = descendentes(lote, unidade);
        return Map.of("loteId", lote, "unidade", unidade, "lotesRelacionados", lotes,
            "operacoes", em.createQuery("from PharmaciaOperacao where (loteId in :l or loteDestinoId in :l) and unidade = :u order by criadoEm", PharmaciaOperacao.class).setParameter("l", lotes).setParameter("u", unidade).getResultList(),
            "dispensacoes", em.createQuery("from PharmaciaDispensacao where lote.id in :l and unidadeSigla = :u order by dataHora", PharmaciaDispensacao.class).setParameter("l", lotes).setParameter("u", unidade).getResultList(),
            "movimentacoes", em.createQuery("from PharmaciaMovimentacao where lote.id in :l and (unidadeOrigemSigla = :u or unidadeDestinoSigla = :u) order by dataHora", PharmaciaMovimentacao.class).setParameter("l", lotes).setParameter("u", unidade).getResultList());
    }
    private void estoqueLeitura(Long lote, String unidade) {
        if (em.createQuery("select count(e) from PharmaciaEstoque e where e.lote.id = :l and e.unidadeSigla = :u", Long.class).setParameter("l", lote).setParameter("u", unidade).getSingleResult() == 0) throw new IllegalArgumentException("Lote nao pertence ao estoque da unidade.");
    }
}

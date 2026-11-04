package br.com.iv.almoxarifado;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Almoxarifado, 2a etapa: fornecedores, pedidos de compra e patrimonio. */
@ApplicationScoped
public class AlmoxGestaoService {

    @Inject
    AlmoxService almoxService;

    @Inject
    AlmoxAuditoria auditoria;

    // ---- Fornecedores ----

    public List<AlmoxFornecedor> listarFornecedores(String unidade) {
        return AlmoxFornecedor.<AlmoxFornecedor>find("unidade = ?1 order by ativo desc, nome", unidade).list();
    }

    @Transactional
    public AlmoxFornecedor salvarFornecedor(String unidade, Long id, Map<String, Object> p, String usuario) {
        String nome = texto(p, "nome");
        if (vazio(nome)) throw new IllegalArgumentException("Informe o nome do fornecedor.");
        String n = nome.trim().toUpperCase();
        long repetido = id == null ? AlmoxFornecedor.count("unidade = ?1 and nome = ?2", unidade, n)
                : AlmoxFornecedor.count("unidade = ?1 and nome = ?2 and idfornecedor <> ?3", unidade, n, id);
        if (repetido > 0) throw new IllegalStateException("Fornecedor ja cadastrado nesta unidade.");
        AlmoxFornecedor f = id == null ? new AlmoxFornecedor() : fornecedorDaUnidade(unidade, id);
        String[] antes = {f.nome, f.cnpj, f.telefone, f.email};
        Boolean ativoAntes = f.ativo;
        f.unidade = unidade;
        f.nome = n;
        String cnpj = textoOuNulo(p, "cnpj");
        if (cnpj != null && !AlmoxRules.cnpjValido(cnpj)) throw new IllegalArgumentException("CNPJ invalido: confira os digitos.");
        f.cnpj = cnpj == null ? null : AlmoxRules.cnpjLimpo(cnpj);
        f.telefone = textoOuNulo(p, "telefone");
        f.email = textoOuNulo(p, "email");
        if (p != null && p.containsKey("ativo")) f.ativo = !"false".equalsIgnoreCase(texto(p, "ativo"));
        if (id == null) {
            f.usuarioRegistro = usuario;
            f.persist();
            auditoria.registrar(unidade, usuario, "cadastrou o fornecedor " + f.nome + (f.cnpj == null ? "" : " (CNPJ " + f.cnpj + ")"));
        } else {
            if (!java.util.Objects.equals(ativoAntes, f.ativo)) {
                auditoria.registrar(unidade, usuario, (Boolean.FALSE.equals(f.ativo) ? "excluiu" : "reativou") + " o fornecedor " + f.nome);
            }
            AlmoxAuditoria.Diferencas d = new AlmoxAuditoria.Diferencas()
                    .campo("nome", antes[0], f.nome).campo("CNPJ", antes[1], f.cnpj)
                    .campo("telefone", antes[2], f.telefone).campo("e-mail", antes[3], f.email);
            if (!d.vazio()) auditoria.registrar(unidade, usuario, "alterou o fornecedor " + f.nome + ": " + d);
        }
        return f;
    }

    // ---- Compras ----

    public List<Map<String, Object>> listarCompras(String unidade, String status) {
        String s = AlmoxRules.normalizar(status);
        List<AlmoxCompra> lista = s.isEmpty()
                ? AlmoxCompra.<AlmoxCompra>find("unidade = ?1 order by dataRegistro desc", unidade).page(0, 200).list()
                : AlmoxCompra.<AlmoxCompra>find("unidade = ?1 and status = ?2 order by dataRegistro desc", unidade, s).page(0, 200).list();
        return lista.stream().map(this::detalharCompra).toList();
    }

    @Transactional
    public Map<String, Object> criarCompra(String unidade, Map<String, Object> p, String usuario) {
        Object bruto = p == null ? null : p.get("itens");
        if (!(bruto instanceof List<?> itens) || itens.isEmpty()) {
            throw new IllegalArgumentException("Inclua ao menos um item no pedido de compra.");
        }
        AlmoxCompra c = novaCompra(unidade, p, usuario);
        for (Object o : itens) {
            if (!(o instanceof Map<?, ?> linha)) throw new IllegalArgumentException("Item invalido.");
            @SuppressWarnings("unchecked")
            Map<String, Object> l = (Map<String, Object>) linha;
            AlmoxItem item = itemDaUnidade(unidade, longo(l, "iditem"));
            incluirItem(c, item, positivo(decimal(l, "quantidade"), "Quantidade de " + item.descricao), decimal(l, "custoUnitario"));
        }
        auditoria.registrar(unidade, usuario, "criou o pedido de compra n " + c.idcompra + " com " + itens.size() + " item(ns)");
        return detalharCompra(c);
    }

    /** Um clique: monta o pedido com todos os itens no ponto de pedido, na quantidade que leva ao maximo. */
    @Transactional
    public Map<String, Object> gerarCompraDoPontoDePedido(String unidade, Map<String, Object> p, String usuario) {
        List<AlmoxItem> abaixo = almoxService.abaixoDoMinimo(unidade);
        List<AlmoxItem> comFalta = abaixo.stream()
                .filter(i -> AlmoxRules.quantidadeSugerida(i.saldo, i.estoqueMinimo, i.estoqueMaximo).signum() > 0).toList();
        if (comFalta.isEmpty()) throw new IllegalStateException("Nenhum item no ponto de pedido.");
        AlmoxCompra c = novaCompra(unidade, p, usuario);
        if (c.observacao == null) c.observacao = "Gerado a partir do ponto de pedido";
        for (AlmoxItem i : comFalta) {
            incluirItem(c, i, AlmoxRules.quantidadeSugerida(i.saldo, i.estoqueMinimo, i.estoqueMaximo),
                    i.custoMedio.signum() > 0 ? i.custoMedio : null);
        }
        auditoria.registrar(unidade, usuario, "gerou o pedido de compra n " + c.idcompra + " pelo ponto de pedido: "
                + String.join(", ", comFalta.stream().map(i -> i.descricao).toList()));
        return detalharCompra(c);
    }

    @Transactional
    public Map<String, Object> enviarCompra(String unidade, Long id, String usuario) {
        AlmoxCompra c = compraTravada(unidade, id);
        if (!AlmoxRules.COMPRA_ABERTO.equals(c.status)) throw new IllegalStateException("So pedido aberto pode ser enviado.");
        c.status = AlmoxRules.COMPRA_ENVIADO;
        c.dataEnvio = LocalDateTime.now();
        auditoria.registrar(unidade, usuario, "enviou ao fornecedor o pedido de compra n " + c.idcompra);
        return detalharCompra(c);
    }

    @Transactional
    public Map<String, Object> cancelarCompra(String unidade, Long id, String usuario) {
        AlmoxCompra c = compraTravada(unidade, id);
        if (!AlmoxRules.compraRecebivel(c.status)) {
            throw new IllegalStateException("Pedido " + c.status.toLowerCase() + " nao pode ser cancelado.");
        }
        // Parte ja entrou no estoque: o pedido e encerrado (o restante nao vem), nao cancelado.
        c.status = AlmoxRules.COMPRA_PARCIAL.equals(c.status) ? AlmoxRules.COMPRA_ENCERRADO : AlmoxRules.COMPRA_CANCELADO;
        auditoria.registrar(unidade, usuario, (AlmoxRules.COMPRA_ENCERRADO.equals(c.status) ? "encerrou" : "cancelou") + " o pedido de compra n " + c.idcompra);
        return detalharCompra(c);
    }

    /**
     * Recebimento (pode vir em varias entregas): cada quantidade desta entrega vira ENTRADA
     * no estoque e soma ao ja recebido, sem passar do que foi pedido.
     */
    @Transactional
    public Map<String, Object> receberCompra(String unidade, Long id, Map<String, Object> p, String usuario) {
        AlmoxCompra c = compraTravada(unidade, id);
        if (!AlmoxRules.compraRecebivel(c.status)) {
            throw new IllegalStateException("Pedido " + c.status.toLowerCase() + " nao pode ser recebido.");
        }
        Map<Long, BigDecimal> recebido = new HashMap<>();
        Map<Long, BigDecimal> custo = new HashMap<>();
        Map<Long, Object[]> loteValidade = new HashMap<>();
        Object bruto = p == null ? null : p.get("itens");
        if (bruto instanceof List<?> lista) {
            for (Object o : lista) {
                if (o instanceof Map<?, ?> linha) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> l = (Map<String, Object>) linha;
                    Long idci = longo(l, "idcompraitem");
                    BigDecimal q = decimal(l, "quantidadeRecebida");
                    if (q != null && q.signum() < 0) throw new IllegalArgumentException("Quantidade recebida nao pode ser negativa.");
                    recebido.put(idci, q == null ? BigDecimal.ZERO : q);
                    BigDecimal cu = decimal(l, "custoUnitario");
                    if (cu != null) custo.put(idci, cu);
                    // Lote e validade seguem para a entrada (AlmoxService.entrada recusa material vencido).
                    loteValidade.put(idci, new Object[] {l.get("lote"), l.get("validade")});
                }
            }
        }
        String fornecedor = c.idfornecedor == null ? null : AlmoxFornecedor.<AlmoxFornecedor>findByIdOptional(c.idfornecedor).map(f -> f.nome).orElse(null);
        String nf = textoOuNulo(p, "notaFiscal");
        if (recebido.values().stream().noneMatch(q -> q.signum() > 0)) {
            throw new IllegalArgumentException("Informe a quantidade recebida de ao menos um item.");
        }
        List<AlmoxCompraItem> itensCompra = itensDaCompra(c.idcompra);
        almoxService.travarItens(unidade, itensCompra.stream().map(i -> i.iditem).toList());
        List<BigDecimal[]> resumo = new ArrayList<>();
        for (AlmoxCompraItem ci : itensCompra) {
            BigDecimal q = recebido.getOrDefault(ci.idcompraitem, BigDecimal.ZERO);
            BigDecimal antes = ci.quantidadeRecebida == null ? BigDecimal.ZERO : ci.quantidadeRecebida;
            if (antes.add(q).compareTo(ci.quantidade) > 0) {
                throw new IllegalArgumentException("Recebimento acima do pedido: faltavam "
                        + ci.quantidade.subtract(antes).stripTrailingZeros().toPlainString() + " de um dos itens.");
            }
            ci.quantidadeRecebida = antes.add(q);
            if (custo.containsKey(ci.idcompraitem)) ci.custoUnitario = custo.get(ci.idcompraitem);
            resumo.add(new BigDecimal[] {ci.quantidade, ci.quantidadeRecebida});
            if (q.signum() > 0) {
                Map<String, Object> entrada = new HashMap<>();
                entrada.put("iditem", ci.iditem);
                entrada.put("quantidade", q);
                if (ci.custoUnitario != null) entrada.put("custoUnitario", ci.custoUnitario);
                entrada.put("documento", nf == null ? "PEDIDO DE COMPRA " + c.idcompra : nf);
                if (fornecedor != null) entrada.put("fornecedor", fornecedor);
                Object[] lv = loteValidade.get(ci.idcompraitem);
                if (lv != null && lv[0] != null) entrada.put("lote", lv[0]);
                if (lv != null && lv[1] != null) entrada.put("validade", lv[1]);
                almoxService.entrada(unidade, entrada, usuario);
            }
        }
        String situacao = AlmoxRules.statusAtendimento(resumo);
        c.status = AlmoxRules.REQ_ATENDIDA.equals(situacao) ? AlmoxRules.COMPRA_RECEBIDO : AlmoxRules.COMPRA_PARCIAL;
        if (nf != null) {
            // Entregas em notas diferentes ficam todas registradas no pedido.
            c.notaFiscal = c.notaFiscal == null || c.notaFiscal.equals(nf) ? nf : c.notaFiscal + "; " + nf;
        }
        c.dataRecebimento = LocalDateTime.now();
        c.usuarioRecebimento = usuario;
        auditoria.registrar(unidade, usuario, "recebeu o pedido de compra n " + c.idcompra + " (" + c.status.toLowerCase() + ")"
                + (nf == null ? "" : ", nota fiscal " + nf));
        return detalharCompra(c);
    }

    // ---- Patrimonio ----

    public List<AlmoxPatrimonio> listarPatrimonio(String unidade, String status) {
        String s = AlmoxRules.normalizar(status);
        return s.isEmpty()
                ? AlmoxPatrimonio.<AlmoxPatrimonio>find("unidade = ?1 and status <> ?2 order by tombamento", unidade, AlmoxRules.BEM_BAIXADO).list()
                : AlmoxPatrimonio.<AlmoxPatrimonio>find("unidade = ?1 and status = ?2 order by tombamento", unidade, s).list();
    }

    @Transactional
    public AlmoxPatrimonio cadastrarBem(String unidade, Map<String, Object> p, String usuario) {
        String tombamento = texto(p, "tombamento");
        String descricao = texto(p, "descricao");
        if (vazio(tombamento) || vazio(descricao)) throw new IllegalArgumentException("Informe o tombamento e a descricao do bem.");
        String t = tombamento.trim().toUpperCase();
        if (AlmoxPatrimonio.count("unidade = ?1 and tombamento = ?2", unidade, t) > 0) {
            throw new IllegalStateException("Ja existe um bem com o tombamento " + t + ".");
        }
        AlmoxPatrimonio b = new AlmoxPatrimonio();
        b.unidade = unidade;
        b.tombamento = t;
        b.descricao = descricao.trim().toUpperCase();
        b.categoria = AlmoxRules.categoriaBem(texto(p, "categoria"));
        b.marca = textoOuNulo(p, "marca");
        b.modelo = textoOuNulo(p, "modelo");
        b.numeroSerie = textoOuNulo(p, "numeroSerie");
        String idf = texto(p, "idfornecedor");
        b.idfornecedor = vazio(idf) ? null : fornecedorDaUnidade(unidade, longo(p, "idfornecedor")).idfornecedor;
        b.notaFiscal = textoOuNulo(p, "notaFiscal");
        b.dataAquisicao = data(p, "dataAquisicao");
        if (b.dataAquisicao != null && b.dataAquisicao.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Data de aquisicao no futuro.");
        }
        b.valorAquisicao = decimal(p, "valorAquisicao");
        if (b.valorAquisicao != null && b.valorAquisicao.signum() < 0) {
            throw new IllegalArgumentException("Valor de aquisicao nao pode ser negativo.");
        }
        b.setor = textoOuNulo(p, "setor") == null ? null : texto(p, "setor").trim().toUpperCase();
        b.responsavel = textoOuNulo(p, "responsavel");
        b.status = AlmoxRules.BEM_EM_USO;
        b.usuarioRegistro = usuario;
        b.persist();
        evento(b, "CADASTRO", null, b.setor, "Incorporado ao patrimonio", usuario);
        return b;
    }

    /** Transferir de setor, enviar para manutencao, retornar ou baixar, sempre com historico. */
    @Transactional
    public AlmoxPatrimonio acaoBem(String unidade, Long id, String acao, Map<String, Object> p, String usuario) {
        AlmoxPatrimonio b = bemDaUnidade(unidade, id);
        String a = AlmoxRules.normalizar(acao);
        String bloqueio = AlmoxRules.bloqueioBem(b.status, a);
        if (bloqueio != null) throw new IllegalStateException(bloqueio);
        String detalhe = textoOuNulo(p, "detalhe");
        switch (a) {
            case "TRANSFERIR" -> {
                String destino = texto(p, "setor");
                if (vazio(destino)) throw new IllegalArgumentException("Informe o setor de destino.");
                String origem = b.setor;
                b.setor = destino.trim().toUpperCase();
                if (!vazio(texto(p, "responsavel"))) b.responsavel = texto(p, "responsavel").trim();
                evento(b, "TRANSFERENCIA", origem, b.setor, detalhe, usuario);
            }
            case "MANUTENCAO" -> {
                if (detalhe == null) throw new IllegalArgumentException("Descreva o defeito ou a manutencao.");
                b.status = AlmoxRules.BEM_MANUTENCAO;
                evento(b, "MANUTENCAO", b.setor, null, detalhe, usuario);
            }
            case "RETORNO" -> {
                b.status = AlmoxRules.BEM_EM_USO;
                evento(b, "RETORNO", null, b.setor, detalhe == null ? "Voltou da manutencao" : detalhe, usuario);
            }
            case "BAIXA" -> {
                if (detalhe == null) throw new IllegalArgumentException("Informe o motivo da baixa.");
                b.status = AlmoxRules.BEM_BAIXADO;
                b.dataBaixa = LocalDateTime.now();
                b.motivoBaixa = detalhe;
                evento(b, "BAIXA", b.setor, null, detalhe, usuario);
            }
            default -> throw new IllegalArgumentException("Acao invalida.");
        }
        return b;
    }

    public Map<String, Object> historicoBem(String unidade, Long id) {
        AlmoxPatrimonio b = bemDaUnidade(unidade, id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bem", b);
        r.put("fornecedor", b.idfornecedor == null ? null : AlmoxFornecedor.findById(b.idfornecedor));
        r.put("eventos", AlmoxPatrimonioEvento.<AlmoxPatrimonioEvento>find("idpatrimonio = ?1 order by dataEvento desc", id).list());
        return r;
    }

    // ---- Apoio ----

    private AlmoxCompra novaCompra(String unidade, Map<String, Object> p, String usuario) {
        AlmoxCompra c = new AlmoxCompra();
        c.unidade = unidade;
        String idf = texto(p, "idfornecedor");
        c.idfornecedor = vazio(idf) ? null : fornecedorDaUnidade(unidade, longo(p, "idfornecedor")).idfornecedor;
        c.status = AlmoxRules.COMPRA_ABERTO;
        c.observacao = textoOuNulo(p, "observacao");
        c.usuarioRegistro = usuario;
        c.persist();
        return c;
    }

    private void incluirItem(AlmoxCompra c, AlmoxItem item, BigDecimal quantidade, BigDecimal custo) {
        AlmoxCompraItem ci = new AlmoxCompraItem();
        ci.idcompra = c.idcompra;
        ci.iditem = item.iditem;
        ci.quantidade = quantidade;
        ci.custoUnitario = custo;
        ci.persist();
    }

    private List<AlmoxCompraItem> itensDaCompra(Long idcompra) {
        return AlmoxCompraItem.<AlmoxCompraItem>find("idcompra = ?1 order by idcompraitem", idcompra).list();
    }

    private Map<String, Object> detalharCompra(AlmoxCompra c) {
        List<AlmoxCompraItem> itens = itensDaCompra(c.idcompra);
        Map<Long, AlmoxItem> cadastro = itens.isEmpty() ? Map.of()
                : AlmoxItem.<AlmoxItem>find("iditem in ?1", itens.stream().map(i -> i.iditem).distinct().toList()).list()
                        .stream().collect(Collectors.toMap(i -> i.iditem, Function.identity()));
        List<Map<String, Object>> linhas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (AlmoxCompraItem ci : itens) {
            AlmoxItem i = cadastro.get(ci.iditem);
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("idcompraitem", ci.idcompraitem);
            l.put("iditem", ci.iditem);
            l.put("descricao", i == null ? "" : i.descricao);
            l.put("unidadeMedida", i == null ? "" : i.unidadeMedida);
            l.put("saldo", i == null ? BigDecimal.ZERO : i.saldo);
            l.put("quantidade", ci.quantidade);
            l.put("custoUnitario", ci.custoUnitario);
            l.put("quantidadeRecebida", ci.quantidadeRecebida);
            if (ci.custoUnitario != null) total = total.add(ci.custoUnitario.multiply(ci.quantidade));
            linhas.add(l);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("compra", c);
        r.put("fornecedor", c.idfornecedor == null ? null : AlmoxFornecedor.<AlmoxFornecedor>findByIdOptional(c.idfornecedor).map(f -> f.nome).orElse(null));
        r.put("valorEstimado", total);
        r.put("itens", linhas);
        return r;
    }

    private void evento(AlmoxPatrimonio b, String tipo, String origem, String destino, String detalhe, String usuario) {
        AlmoxPatrimonioEvento e = new AlmoxPatrimonioEvento();
        e.idpatrimonio = b.idpatrimonio;
        e.tipo = tipo;
        e.setorOrigem = origem;
        e.setorDestino = destino;
        e.detalhe = detalhe;
        e.usuario = usuario;
        e.persist();
        // Alem do historico do bem, vai para o log geral do sistema.
        auditoria.registrar(b.unidade, usuario, "patrimonio " + tipo.toLowerCase() + " do bem " + b.tombamento + " " + b.descricao
                + (origem == null && destino == null ? "" : " (" + (origem == null ? "-" : origem) + " -> " + (destino == null ? "-" : destino) + ")")
                + (detalhe == null ? "" : ": " + detalhe));
    }

    private AlmoxFornecedor fornecedorDaUnidade(String unidade, Long id) {
        AlmoxFornecedor f = AlmoxFornecedor.<AlmoxFornecedor>find("idfornecedor = ?1 and unidade = ?2", id, unidade).firstResult();
        if (f == null) throw new NotFoundException("Fornecedor nao encontrado nesta unidade.");
        return f;
    }

    /** Compra travada: dois cliques em "Receber" nao dao entrada duas vezes. */
    private AlmoxCompra compraTravada(String unidade, Long id) {
        AlmoxCompra c = id == null ? null : AlmoxCompra.<AlmoxCompra>find("idcompra = ?1 and unidade = ?2", id, unidade)
                .withLock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (c == null) throw new NotFoundException("Pedido de compra nao encontrado nesta unidade.");
        return c;
    }

    private AlmoxPatrimonio bemDaUnidade(String unidade, Long id) {
        AlmoxPatrimonio b = id == null ? null : AlmoxPatrimonio.<AlmoxPatrimonio>find("idpatrimonio = ?1 and unidade = ?2", id, unidade).firstResult();
        if (b == null) throw new NotFoundException("Bem nao encontrado nesta unidade.");
        return b;
    }

    private AlmoxItem itemDaUnidade(String unidade, Long iditem) {
        AlmoxItem item = AlmoxItem.<AlmoxItem>find("iditem = ?1 and unidade = ?2", iditem, unidade).firstResult();
        if (item == null) throw new NotFoundException("Item nao encontrado nesta unidade.");
        return item;
    }

    private static boolean vazio(String v) {
        return v == null || v.trim().isEmpty();
    }

    private static String texto(Map<String, Object> p, String chave) {
        Object v = p == null ? null : p.get(chave);
        return v == null ? null : String.valueOf(v);
    }

    private static String textoOuNulo(Map<String, Object> p, String chave) {
        String v = texto(p, chave);
        return vazio(v) ? null : v.trim();
    }

    private static Long longo(Map<String, Object> p, String chave) {
        String v = texto(p, chave);
        if (vazio(v)) throw new IllegalArgumentException("Informe " + chave + ".");
        try {
            return new BigDecimal(v.trim()).longValueExact();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Valor invalido em " + chave + ".");
        }
    }

    private static BigDecimal decimal(Map<String, Object> p, String chave) {
        String v = texto(p, chave);
        if (vazio(v)) return null;
        try {
            return new BigDecimal(v.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numerico invalido em " + chave + ".");
        }
    }

    private static BigDecimal positivo(BigDecimal v, String nome) {
        if (v == null || v.signum() <= 0) throw new IllegalArgumentException(nome + " deve ser maior que zero.");
        return v;
    }

    private static LocalDate data(Map<String, Object> p, String chave) {
        String v = texto(p, chave);
        if (vazio(v)) return null;
        try {
            return LocalDate.parse(v.trim().substring(0, Math.min(10, v.trim().length())));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Data invalida em " + chave + " (use aaaa-MM-dd).");
        }
    }
}

package br.com.iv.almoxarifado;

import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Almoxarifado geral por unidade: itens, entradas, pedidos dos setores, ajustes e relatorios. */
@ApplicationScoped
public class AlmoxService {

    // ---- Itens ----

    public List<AlmoxItem> listarItens(String unidade, boolean apenasAtivos) {
        return apenasAtivos
                ? AlmoxItem.<AlmoxItem>find("unidade = ?1 and ativo = true order by descricao", unidade).list()
                : AlmoxItem.<AlmoxItem>find("unidade = ?1 order by ativo desc, descricao", unidade).list();
    }

    public List<AlmoxItem> abaixoDoMinimo(String unidade) {
        return listarItens(unidade, true).stream()
                .filter(i -> AlmoxRules.abaixoDoMinimo(i.saldo, i.estoqueMinimo)).toList();
    }

    @Transactional
    public AlmoxItem salvarItem(String unidade, Long iditem, Map<String, Object> payload, String usuario) {
        String descricao = texto(payload, "descricao");
        if (vazio(descricao)) {
            throw new IllegalArgumentException("Informe a descricao do item.");
        }
        String desc = descricao.trim().toUpperCase();
        AlmoxItem item = iditem == null ? new AlmoxItem() : itemDaUnidade(unidade, iditem);
        long repetido = iditem == null
                ? AlmoxItem.count("unidade = ?1 and descricao = ?2", unidade, desc)
                : AlmoxItem.count("unidade = ?1 and descricao = ?2 and iditem <> ?3", unidade, desc, iditem);
        if (repetido > 0) {
            throw new IllegalStateException("Ja existe um item com essa descricao nesta unidade.");
        }
        item.unidade = unidade;
        item.descricao = desc;
        item.codigo = vazio(texto(payload, "codigo")) ? null : texto(payload, "codigo").trim().toUpperCase();
        item.unidadeMedida = vazio(texto(payload, "unidadeMedida")) ? "UN" : texto(payload, "unidadeMedida").trim().toUpperCase();
        item.grupo = AlmoxRules.grupo(texto(payload, "grupo"));
        item.estoqueMinimo = naoNegativo(decimal(payload, "estoqueMinimo"), "Estoque minimo");
        BigDecimal maximo = decimal(payload, "estoqueMaximo");
        item.estoqueMaximo = maximo == null ? null : naoNegativo(maximo, "Estoque maximo");
        if (item.estoqueMaximo != null && item.estoqueMaximo.signum() > 0 && item.estoqueMaximo.compareTo(item.estoqueMinimo) < 0) {
            throw new IllegalArgumentException("Estoque maximo nao pode ser menor que o minimo.");
        }
        if (payload != null && payload.containsKey("ativo")) {
            item.ativo = !"false".equalsIgnoreCase(texto(payload, "ativo"));
        }
        if (iditem == null) {
            item.usuarioRegistro = usuario;
            item.persist();
        } else {
            item.dataAtualizacao = LocalDateTime.now();
            item.usuarioAtualizacao = usuario;
        }
        return item;
    }

    // ---- Entrada e ajuste ----

    @Transactional
    public AlmoxItem entrada(String unidade, Map<String, Object> payload, String usuario) {
        AlmoxItem item = itemTravado(unidade, longo(payload, "iditem"));
        BigDecimal qtd = positivo(decimal(payload, "quantidade"), "Quantidade");
        BigDecimal custo = decimal(payload, "custoUnitario");
        if (custo != null && custo.signum() < 0) {
            throw new IllegalArgumentException("Custo unitario nao pode ser negativo.");
        }
        LocalDate validade = data(payload, "validade");
        if (validade != null && validade.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Material vencido nao entra no estoque (validade " + validade + ").");
        }
        item.custoMedio = AlmoxRules.custoMedio(item.saldo, item.custoMedio, qtd, custo);
        item.saldo = item.saldo.add(qtd);
        item.dataAtualizacao = LocalDateTime.now();
        item.usuarioAtualizacao = usuario;

        AlmoxMovimentacao m = mov(item, AlmoxRules.MOV_ENTRADA, qtd, custo == null ? item.custoMedio : custo, usuario);
        m.documento = textoOuNulo(payload, "documento");
        m.fornecedor = textoOuNulo(payload, "fornecedor");
        m.lote = textoOuNulo(payload, "lote");
        m.validade = validade;
        m.persist();
        return item;
    }

    /** Ajuste de inventario: informa o saldo contado; a diferenca vira movimentacao com justificativa. */
    @Transactional
    public AlmoxItem ajustar(String unidade, Map<String, Object> payload, String usuario) {
        AlmoxItem item = itemTravado(unidade, longo(payload, "iditem"));
        BigDecimal contado = naoNegativo(decimal(payload, "saldoContado"), "Saldo contado");
        String justificativa = texto(payload, "justificativa");
        if (vazio(justificativa)) {
            throw new IllegalArgumentException("Informe a justificativa do ajuste (inventario, perda, avaria...).");
        }
        BigDecimal diferenca = contado.subtract(item.saldo);
        if (diferenca.signum() == 0) {
            throw new IllegalStateException("O saldo contado e igual ao saldo do sistema.");
        }
        item.saldo = contado;
        item.dataAtualizacao = LocalDateTime.now();
        item.usuarioAtualizacao = usuario;
        AlmoxMovimentacao m = mov(item, AlmoxRules.MOV_AJUSTE, diferenca, item.custoMedio, usuario);
        m.justificativa = justificativa.trim();
        m.persist();
        return item;
    }

    public List<Map<String, Object>> movimentacoes(String unidade, Long iditem) {
        List<AlmoxMovimentacao> lista = iditem == null
                ? AlmoxMovimentacao.<AlmoxMovimentacao>find("unidade = ?1 order by dataMovimentacao desc", unidade).page(0, 300).list()
                : AlmoxMovimentacao.<AlmoxMovimentacao>find("unidade = ?1 and iditem = ?2 order by dataMovimentacao desc", unidade, iditem).page(0, 300).list();
        Map<Long, AlmoxItem> itens = itensPorId(lista.stream().map(m -> m.iditem).toList());
        List<Map<String, Object>> resposta = new ArrayList<>();
        for (AlmoxMovimentacao m : lista) {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("mov", m);
            AlmoxItem i = itens.get(m.iditem);
            linha.put("descricao", i == null ? "" : i.descricao);
            linha.put("unidadeMedida", i == null ? "" : i.unidadeMedida);
            resposta.add(linha);
        }
        return resposta;
    }

    // ---- Pedidos dos setores ----

    /** Qualquer usuario logado pede material para o seu setor. */
    @Transactional
    public Map<String, Object> criarRequisicao(String unidade, Map<String, Object> payload, AuthenticatedUser u) {
        String setor = setorValido(unidade, texto(payload, "setor"));
        Object bruto = payload == null ? null : payload.get("itens");
        if (!(bruto instanceof List<?> itens) || itens.isEmpty()) {
            throw new IllegalArgumentException("Inclua ao menos um item no pedido.");
        }
        AlmoxRequisicao req = new AlmoxRequisicao();
        req.unidade = unidade;
        req.setor = setor;
        req.status = AlmoxRules.REQ_ABERTA;
        req.observacao = textoOuNulo(payload, "observacao");
        req.matriculaSolicitante = u.matricula();
        req.nomeSolicitante = u.nome();
        req.persist();
        java.util.Set<Long> incluidos = new java.util.HashSet<>();
        for (Object o : itens) {
            if (!(o instanceof Map<?, ?> linha)) {
                throw new IllegalArgumentException("Item do pedido invalido.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> l = (Map<String, Object>) linha;
            AlmoxItem item = itemDaUnidade(unidade, longo(l, "iditem"));
            if (!incluidos.add(item.iditem)) {
                throw new IllegalArgumentException(item.descricao + " aparece mais de uma vez no pedido; some as quantidades.");
            }
            if (!Boolean.TRUE.equals(item.ativo)) {
                throw new IllegalStateException(item.descricao + " esta inativo.");
            }
            AlmoxRequisicaoItem ri = new AlmoxRequisicaoItem();
            ri.idrequisicao = req.idrequisicao;
            ri.iditem = item.iditem;
            ri.quantidadeSolicitada = positivo(decimal(l, "quantidade"), "Quantidade de " + item.descricao);
            ri.persist();
        }
        return detalhar(req);
    }

    public List<Map<String, Object>> listarRequisicoes(String unidade, String status) {
        String s = AlmoxRules.normalizar(status);
        List<AlmoxRequisicao> lista = s.isEmpty()
                ? AlmoxRequisicao.<AlmoxRequisicao>find("unidade = ?1 order by dataSolicitacao desc", unidade).page(0, 200).list()
                : AlmoxRequisicao.<AlmoxRequisicao>find("unidade = ?1 and status = ?2 order by dataSolicitacao desc", unidade, s).page(0, 200).list();
        return lista.stream().map(this::detalhar).toList();
    }

    /**
     * Setores que pedem material: lista propria do almoxarifado (almox_setor), mantida
     * na gestao. Nao usa o Cadastrar Setores, que e assistencial e nao tem NIR, recepcao etc.
     */
    public List<String> setoresDaUnidade(String unidade) {
        return listarSetores(unidade).stream().map(s -> s.nome).toList();
    }

    public List<AlmoxSetor> listarSetores(String unidade) {
        return AlmoxSetor.<AlmoxSetor>find("unidade = ?1 order by nome", unidade).list();
    }

    @Transactional
    public AlmoxSetor incluirSetor(String unidade, Map<String, Object> payload, String usuario) {
        String nome = texto(payload, "nome");
        if (vazio(nome)) throw new IllegalArgumentException("Informe o nome do setor.");
        String n = nome.trim().replaceAll("\\s+", " ").toUpperCase();
        if (n.length() > 80) throw new IllegalArgumentException("Nome do setor muito longo.");
        if (AlmoxSetor.count("unidade = ?1 and nome = ?2", unidade, n) > 0) {
            throw new IllegalStateException("Setor " + n + " ja esta na lista.");
        }
        AlmoxSetor s = new AlmoxSetor();
        s.unidade = unidade;
        s.nome = n;
        s.usuarioRegistro = usuario;
        s.persist();
        return s;
    }

    /** Inclui os setores comuns que ainda faltam na lista da unidade. */
    @Transactional
    public List<AlmoxSetor> incluirSetoresComuns(String unidade, String usuario) {
        List<String> existentes = setoresDaUnidade(unidade);
        for (String nome : AlmoxRules.SETORES_COMUNS) {
            if (existentes.contains(nome)) continue;
            AlmoxSetor s = new AlmoxSetor();
            s.unidade = unidade;
            s.nome = nome;
            s.usuarioRegistro = usuario;
            s.persist();
        }
        return listarSetores(unidade);
    }

    /** Tira o setor da lista; os pedidos antigos dele continuam na gestao. */
    @Transactional
    public Map<String, Object> removerSetor(String unidade, Long idsetor) {
        AlmoxSetor s = idsetor == null ? null
                : AlmoxSetor.<AlmoxSetor>find("idsetor = ?1 and unidade = ?2", idsetor, unidade).firstResult();
        if (s == null) throw new NotFoundException("Setor nao encontrado nesta unidade.");
        s.delete();
        return Map.of("removido", s.nome);
    }

    /**
     * Setor do pedido: precisa estar na lista de setores do almoxarifado. Unidade sem a
     * lista montada ainda aceita o nome digitado, para nao travar quem nao a cadastrou.
     */
    public String setorValido(String unidade, String setor) {
        String s = setor == null ? "" : setor.trim().toUpperCase();
        if (s.isEmpty()) throw new IllegalArgumentException("Informe o setor que esta pedindo.");
        List<String> cadastrados = setoresDaUnidade(unidade);
        if (!cadastrados.isEmpty() && !cadastrados.contains(s)) {
            throw new IllegalArgumentException("Setor " + s + " nao esta na lista de setores do almoxarifado.");
        }
        return s;
    }

    /** Pedidos de um setor (de qualquer solicitante); outros setores nao aparecem. */
    public List<Map<String, Object>> requisicoesDoSetor(String unidade, String setor, String matricula) {
        String s = setorValido(unidade, setor);
        return AlmoxRequisicao.<AlmoxRequisicao>find("unidade = ?1 and setor = ?2 order by dataSolicitacao desc", unidade, s)
                .page(0, 100).list().stream().map(req -> {
                    Map<String, Object> d = detalhar(req);
                    // So quem pediu pode cancelar; a tela usa isto para mostrar o botao.
                    d.put("meu", matricula != null && matricula.equals(req.matriculaSolicitante));
                    return d;
                }).toList();
    }

    /**
     * Atende o pedido item a item (quantidade entregue pode ser menor que a pedida).
     * Baixa o saldo, registra a saida para o setor e fecha o pedido.
     */
    @Transactional
    public Map<String, Object> atender(String unidade, Long idrequisicao, Map<String, Object> payload, String usuario) {
        AlmoxRequisicao req = requisicaoTravada(unidade, idrequisicao);
        if (!AlmoxRules.REQ_ABERTA.equals(req.status)) {
            throw new IllegalStateException("Pedido ja esta " + req.status.toLowerCase() + ".");
        }
        Map<Long, BigDecimal> entregue = new HashMap<>();
        Object bruto = payload == null ? null : payload.get("itens");
        if (bruto instanceof List<?> lista) {
            for (Object o : lista) {
                if (o instanceof Map<?, ?> linha) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> l = (Map<String, Object>) linha;
                    entregue.put(longo(l, "idrequisicaoitem"), naoNegativo(decimal(l, "quantidadeAtendida"), "Quantidade entregue"));
                }
            }
        }
        List<AlmoxRequisicaoItem> itens = AlmoxRequisicaoItem.<AlmoxRequisicaoItem>find("idrequisicao", idrequisicao).list();
        Map<Long, AlmoxItem> travados = travarItens(unidade, itens.stream().map(i -> i.iditem).toList());
        List<BigDecimal[]> resumo = new ArrayList<>();
        for (AlmoxRequisicaoItem ri : itens) {
            BigDecimal qtd = entregue.getOrDefault(ri.idrequisicaoitem, BigDecimal.ZERO);
            if (qtd.compareTo(ri.quantidadeSolicitada) > 0) {
                throw new IllegalArgumentException("Entrega maior que o pedido em um dos itens.");
            }
            AlmoxItem item = travados.get(ri.iditem);
            if (item == null) throw new NotFoundException("Item nao encontrado nesta unidade.");
            if (qtd.compareTo(item.saldo) > 0) {
                throw new IllegalStateException("Saldo insuficiente de " + item.descricao + " (tem "
                        + item.saldo.stripTrailingZeros().toPlainString() + ").");
            }
            ri.quantidadeAtendida = qtd;
            resumo.add(new BigDecimal[] {ri.quantidadeSolicitada, qtd});
            if (qtd.signum() > 0) {
                item.saldo = item.saldo.subtract(qtd);
                item.dataAtualizacao = LocalDateTime.now();
                item.usuarioAtualizacao = usuario;
                AlmoxMovimentacao m = mov(item, AlmoxRules.MOV_SAIDA, qtd.negate(), item.custoMedio, usuario);
                m.setor = req.setor;
                m.idrequisicao = req.idrequisicao;
                m.persist();
            }
        }
        req.status = AlmoxRules.statusAtendimento(resumo);
        req.motivo = textoOuNulo(payload, "motivo");
        if (AlmoxRules.REQ_RECUSADA.equals(req.status) && req.motivo == null) {
            throw new IllegalArgumentException("Nada foi entregue: informe o motivo da recusa para o setor.");
        }
        req.matriculaAtendente = usuario;
        req.dataAtendimento = LocalDateTime.now();
        return detalhar(req);
    }

    /** O proprio solicitante cancela enquanto o pedido nao foi atendido. */
    @Transactional
    public Map<String, Object> cancelar(String unidade, Long idrequisicao, String matricula) {
        AlmoxRequisicao req = requisicaoTravada(unidade, idrequisicao);
        if (!req.matriculaSolicitante.equals(matricula)) {
            throw new IllegalStateException("Somente quem fez o pedido pode cancela-lo.");
        }
        if (!AlmoxRules.REQ_ABERTA.equals(req.status)) {
            throw new IllegalStateException("Pedido ja esta " + req.status.toLowerCase() + ".");
        }
        req.status = AlmoxRules.REQ_CANCELADA;
        req.dataAtendimento = LocalDateTime.now();
        return detalhar(req);
    }

    // ---- Relatorio ----

    /** Curva ABC pelo valor das saidas dos ultimos N dias (quantidade x custo da saida). */
    public List<Map<String, Object>> curvaAbc(String unidade, int dias) {
        LocalDateTime desde = LocalDate.now().minusDays(Math.max(1, dias)).atStartOfDay();
        List<AlmoxMovimentacao> saidas = AlmoxMovimentacao.<AlmoxMovimentacao>find(
                "unidade = ?1 and tipo = ?2 and dataMovimentacao >= ?3", unidade, AlmoxRules.MOV_SAIDA, desde).list();
        Map<Long, BigDecimal> valor = new HashMap<>();
        Map<Long, BigDecimal> quantidade = new HashMap<>();
        for (AlmoxMovimentacao m : saidas) {
            BigDecimal q = m.quantidade.abs();
            BigDecimal c = m.custoUnitario == null ? BigDecimal.ZERO : m.custoUnitario;
            valor.merge(m.iditem, q.multiply(c), BigDecimal::add);
            quantidade.merge(m.iditem, q, BigDecimal::add);
        }
        Map<Long, String> classes = AlmoxRules.curvaAbc(valor);
        Map<Long, AlmoxItem> itens = itensPorId(new ArrayList<>(classes.keySet()));
        BigDecimal total = valor.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Map.Entry<Long, String> e : classes.entrySet()) {
            AlmoxItem i = itens.get(e.getKey());
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("iditem", e.getKey());
            linha.put("descricao", i == null ? "" : i.descricao);
            linha.put("unidadeMedida", i == null ? "" : i.unidadeMedida);
            linha.put("quantidade", quantidade.get(e.getKey()));
            linha.put("valor", valor.get(e.getKey()).setScale(2, RoundingMode.HALF_UP));
            linha.put("percentual", total.signum() == 0 ? BigDecimal.ZERO
                    : valor.get(e.getKey()).multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP));
            linha.put("classe", e.getValue());
            resposta.add(linha);
        }
        return resposta;
    }

    // ---- Apoio ----

    private Map<String, Object> detalhar(AlmoxRequisicao req) {
        List<AlmoxRequisicaoItem> itens = AlmoxRequisicaoItem.<AlmoxRequisicaoItem>find("idrequisicao = ?1 order by idrequisicaoitem", req.idrequisicao).list();
        Map<Long, AlmoxItem> cadastro = itensPorId(itens.stream().map(i -> i.iditem).toList());
        List<Map<String, Object>> linhas = new ArrayList<>();
        for (AlmoxRequisicaoItem ri : itens) {
            AlmoxItem i = cadastro.get(ri.iditem);
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("idrequisicaoitem", ri.idrequisicaoitem);
            l.put("iditem", ri.iditem);
            l.put("descricao", i == null ? "" : i.descricao);
            l.put("unidadeMedida", i == null ? "" : i.unidadeMedida);
            l.put("saldo", i == null ? BigDecimal.ZERO : i.saldo);
            l.put("quantidadeSolicitada", ri.quantidadeSolicitada);
            l.put("quantidadeAtendida", ri.quantidadeAtendida);
            linhas.add(l);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("requisicao", req);
        r.put("itens", linhas);
        return r;
    }

    private AlmoxMovimentacao mov(AlmoxItem item, String tipo, BigDecimal qtd, BigDecimal custo, String usuario) {
        AlmoxMovimentacao m = new AlmoxMovimentacao();
        m.unidade = item.unidade;
        m.iditem = item.iditem;
        m.tipo = tipo;
        m.quantidade = qtd;
        m.custoUnitario = custo;
        m.saldoApos = item.saldo;
        m.usuario = usuario;
        return m;
    }

    private Map<Long, AlmoxItem> itensPorId(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return AlmoxItem.<AlmoxItem>find("iditem in ?1", ids.stream().distinct().toList()).list().stream()
                .collect(Collectors.toMap(i -> i.iditem, Function.identity()));
    }

    private AlmoxItem itemDaUnidade(String unidade, Long iditem) {
        AlmoxItem item = iditem == null ? null
                : AlmoxItem.<AlmoxItem>find("iditem = ?1 and unidade = ?2", iditem, unidade).firstResult();
        if (item == null) throw new NotFoundException("Item nao encontrado nesta unidade.");
        return item;
    }

    /**
     * Item com a linha travada ate o fim da transacao: duas baixas/entradas simultaneas
     * do mesmo item ficam em fila em vez de uma sobrescrever o saldo da outra.
     */
    private AlmoxItem itemTravado(String unidade, Long iditem) {
        AlmoxItem item = iditem == null ? null
                : AlmoxItem.<AlmoxItem>find("iditem = ?1 and unidade = ?2", iditem, unidade)
                        .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (item == null) throw new NotFoundException("Item nao encontrado nesta unidade.");
        return item;
    }

    /** Trava varios itens sempre na mesma ordem (por id), para dois atendimentos nao se travarem mutuamente. */
    Map<Long, AlmoxItem> travarItens(String unidade, List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return AlmoxItem.<AlmoxItem>find("iditem in ?1 and unidade = ?2 order by iditem", ids.stream().distinct().toList(), unidade)
                .withLock(LockModeType.PESSIMISTIC_WRITE).list().stream()
                .collect(Collectors.toMap(i -> i.iditem, Function.identity()));
    }

    /** Pedido travado: dois cliques em "Entregar" nao baixam o estoque duas vezes. */
    private AlmoxRequisicao requisicaoTravada(String unidade, Long id) {
        AlmoxRequisicao req = id == null ? null
                : AlmoxRequisicao.<AlmoxRequisicao>find("idrequisicao = ?1 and unidade = ?2", id, unidade)
                        .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (req == null) throw new NotFoundException("Pedido nao encontrado nesta unidade.");
        return req;
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

    private static BigDecimal naoNegativo(BigDecimal v, String nome) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw new IllegalArgumentException(nome + " nao pode ser negativo.");
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

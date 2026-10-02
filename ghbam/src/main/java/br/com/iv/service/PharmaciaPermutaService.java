package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import br.com.iv.entity.*;
import br.com.iv.dto.PharmaciaPermutaFinalizarRequest;

@ApplicationScoped
public class PharmaciaPermutaService {

    @Inject
    PharmaciaEstoqueService estoqueService;

    @Inject
    PharmaciaLoteService loteService;

    @Inject
    PharmaciaMovimentacaoService movimentacaoService;

    public List<PharmaciaPermuta> listAll() {
        return PharmaciaPermuta.listAll();
    }

    public PharmaciaPermuta findById(Long id) {
        return PharmaciaPermuta.findById(id);
    }

    public List<PharmaciaPermuta> findByUnidadeOrigemSigla(String sigla) {
        return PharmaciaPermuta.list("unidadeOrigemSigla", sigla);
    }

    public List<PharmaciaPermuta> findByUnidadeDestinoSigla(String sigla) {
        return PharmaciaPermuta.list("unidadeDestinoSigla", sigla);
    }

    public List<PharmaciaPermuta> findByStatus(PharmaciaPermuta.StatusPermuta status) {
        return PharmaciaPermuta.list("status", status);
    }

    // Todas as permutas que em algum momento tiveram um item desse lote —
    // usado no rastreio de lote (recall): pra que unidades esse lote pode
    // ter ido parar via permuta.
    public List<PharmaciaPermuta> findByLoteId(Long loteId) {
        return PharmaciaPermuta.list(
            "SELECT DISTINCT p FROM PharmaciaPermuta p JOIN p.itens i WHERE i.lote.id = ?1 ORDER BY p.dataSolicitacao DESC",
            loteId
        );
    }

    // Guarda de autorizacao no back-end — o front ja esconde os botoes pro
    // lado errado, mas isso sozinho nao impede uma chamada direta a API.
    // Cada etapa do ciclo de vida so pode ser executada por quem de fato
    // tem o papel correspondente (fornecedora aprova/recusa/transporta,
    // solicitante confirma o recebimento).
    private void exigirUnidade(String esperado, String executora, String acao) {
        String esp = esperado == null ? "" : esperado.trim();
        String exec = executora == null ? "" : executora.trim();
        if (esp.isEmpty() || exec.isEmpty() || !esp.equalsIgnoreCase(exec)) {
            throw new IllegalStateException("Apenas a unidade " + esp + " pode " + acao + " esta permuta.");
        }
    }

    @Transactional
    public PharmaciaPermuta solicitar(PharmaciaPermuta permuta) {
        permuta.setStatus(PharmaciaPermuta.StatusPermuta.PENDENTE);
        permuta.setDataSolicitacao(LocalDateTime.now());
        if (permuta.getItens() != null) {
            for (PharmaciaPermutaItem item : permuta.getItens()) {
                item.setPermuta(permuta);
                if (item.getLote() != null && item.getLote().getId() != null) {
                    PharmaciaLote loteGerenciado = PharmaciaLote.findById(item.getLote().getId());
                    if (loteGerenciado == null) {
                        throw new IllegalArgumentException("Lote nao encontrado: id=" + item.getLote().getId());
                    }
                    item.setLote(loteGerenciado);
                } else {
                    throw new IllegalArgumentException("Item sem lote valido informado");
                }
            }
        }
        permuta.persist();
        return permuta;
    }

    @Transactional
    public PharmaciaPermuta aprovar(Long permutaId, String aprovadorMatricula, Map<String, Integer> quantidadesAprovadas, String unidadeExecutora) {
        PharmaciaPermuta permuta = PharmaciaPermuta.find(
            "SELECT p FROM PharmaciaPermuta p LEFT JOIN FETCH p.itens i LEFT JOIN FETCH i.lote WHERE p.id = ?1", permutaId)
            .firstResult();
        if (permuta == null) throw new RuntimeException("Permuta nao encontrada");
        if (permuta.getStatus() != PharmaciaPermuta.StatusPermuta.PENDENTE)
            throw new IllegalStateException("Apenas permutas PENDENTES podem ser aprovadas");
        exigirUnidade(permuta.getUnidadeDestinoSigla(), unidadeExecutora, "aprovar");

        if (quantidadesAprovadas != null && !quantidadesAprovadas.isEmpty()) {
            for (PharmaciaPermutaItem item : permuta.getItens()) {
                Integer qtd = quantidadesAprovadas.get(String.valueOf(item.getId()));
                if (qtd != null) {
                    if (qtd < 0) throw new IllegalArgumentException("Quantidade aprovada nao pode ser negativa");
                    if (qtd > item.getQuantidadeSolicitada()) throw new IllegalArgumentException(
                        "Quantidade aprovada (" + qtd + ") nao pode superar a solicitada (" + item.getQuantidadeSolicitada() + ")");
                    item.setQuantidadeAprovada(qtd);
                    item.persist();
                }
            }
        }

        // Quem aprova (unidadeDestinoSigla, a fornecedora) so pode liberar o
        // que de fato tem em estoque — nao da pra aprovar mais do que possui,
        // mesmo que a quantidade solicitada seja menor.
        for (PharmaciaPermutaItem item : permuta.getItens()) {
            int qtd = item.getQuantidadeAprovada() != null ? item.getQuantidadeAprovada() : item.getQuantidadeSolicitada();
            if (qtd <= 0) continue;
            PharmaciaEstoque estoqueFornecedor = estoqueService.findByLoteAndSigla(
                    item.getLote().getId(), permuta.getUnidadeDestinoSigla());
            int disponivel = estoqueFornecedor != null && estoqueFornecedor.getQuantidade() != null ? estoqueFornecedor.getQuantidade() : 0;
            if (disponivel < qtd) {
                String nomeProduto = item.getLote().getProduto() != null
                        ? (item.getLote().getProduto().getNomeComercial() != null ? item.getLote().getProduto().getNomeComercial() : item.getLote().getProduto().getNomeGenerico())
                        : "item";
                throw new IllegalArgumentException("Estoque insuficiente em " + permuta.getUnidadeDestinoSigla()
                        + " para " + nomeProduto + " (lote " + item.getLote().getNumeroLote() + "): disponivel " + disponivel + ", aprovado " + qtd);
            }
        }

        permuta.setStatus(PharmaciaPermuta.StatusPermuta.APROVADO);
        permuta.setDataAprovacao(LocalDateTime.now());
        permuta.setAprovadorMatricula(aprovadorMatricula);
        permuta.persist();

        // Estoque e movimentacao sao registrados no transporte e no recebimento
        return permuta;
    }

    @Transactional
    public PharmaciaPermuta recusar(Long permutaId, String motivo, String aprovadorMatricula, String unidadeExecutora) {
        PharmaciaPermuta permuta = PharmaciaPermuta.find(
            "SELECT p FROM PharmaciaPermuta p LEFT JOIN FETCH p.itens i LEFT JOIN FETCH i.lote WHERE p.id = ?1", permutaId)
            .firstResult();
        if (permuta == null) throw new RuntimeException("Permuta nao encontrada");
        if (permuta.getStatus() != PharmaciaPermuta.StatusPermuta.PENDENTE)
            throw new IllegalStateException("Apenas permutas PENDENTES podem ser recusadas");
        exigirUnidade(permuta.getUnidadeDestinoSigla(), unidadeExecutora, "recusar");
        if (motivo == null || motivo.trim().isEmpty())
            throw new IllegalArgumentException("Informe o motivo da recusa");

        permuta.setStatus(PharmaciaPermuta.StatusPermuta.RECUSADO);
        permuta.setMotivoRecusa(motivo);
        permuta.setAprovadorMatricula(aprovadorMatricula);
        permuta.persist();

        return permuta;
    }

    @Transactional
    public PharmaciaPermuta iniciarTransporte(Long permutaId, String transporteMatricula, String unidadeExecutora) {
        PharmaciaPermuta permuta = PharmaciaPermuta.find(
            "SELECT p FROM PharmaciaPermuta p LEFT JOIN FETCH p.itens i LEFT JOIN FETCH i.lote WHERE p.id = ?1", permutaId)
            .firstResult();
        if (permuta == null) throw new RuntimeException("Permuta nao encontrada");
        if (permuta.getStatus() != PharmaciaPermuta.StatusPermuta.APROVADO)
            throw new IllegalStateException("Apenas permutas APROVADAS podem ser colocadas em transporte");
        // Quem fisicamente envia (tem o estoque) e a unidadeDestinoSigla —
        // so ela pode informar que o transporte comecou.
        exigirUnidade(permuta.getUnidadeDestinoSigla(), unidadeExecutora, "iniciar o transporte de");

        // "unidadeOrigemSigla" na Permuta e quem SOLICITOU (quer receber);
        // "unidadeDestinoSigla" e quem foi PEDIDA e TEM o estoque (quem envia
        // fisicamente, se aprovar). O nome dos campos e enganoso pro sentido
        // fisico do transporte — por isso descontamos do DESTINO aqui.
        // Revalida o estoque aqui (nao so na aprovacao) porque o saldo pode
        // ter mudado entre aprovar e iniciar o transporte (ex: dispensacao
        // consumiu o lote nesse meio-tempo) — nao pode deixar ir a negativo.
        for (PharmaciaPermutaItem item : permuta.getItens()) {
            int qtd = item.getQuantidadeAprovada() != null ? item.getQuantidadeAprovada() : item.getQuantidadeSolicitada();
            if (qtd <= 0) continue;
            PharmaciaEstoque estoqueFornecedor = estoqueService.findByLoteAndSigla(
                    item.getLote().getId(), permuta.getUnidadeDestinoSigla());
            int disponivel = estoqueFornecedor != null && estoqueFornecedor.getQuantidade() != null ? estoqueFornecedor.getQuantidade() : 0;
            if (disponivel < qtd) {
                String nomeProduto = item.getLote().getProduto() != null
                        ? (item.getLote().getProduto().getNomeComercial() != null ? item.getLote().getProduto().getNomeComercial() : item.getLote().getProduto().getNomeGenerico())
                        : "item";
                throw new IllegalStateException("Estoque insuficiente em " + permuta.getUnidadeDestinoSigla()
                        + " para " + nomeProduto + " (lote " + item.getLote().getNumeroLote() + "): disponivel " + disponivel + ", necessario " + qtd);
            }
        }

        permuta.setStatus(PharmaciaPermuta.StatusPermuta.EM_TRANSPORTE);
        permuta.setTransporteMatricula(transporteMatricula != null && !transporteMatricula.isBlank() ? transporteMatricula : "sistema");
        permuta.setDataTransporte(LocalDateTime.now());
        permuta.persist();

        for (PharmaciaPermutaItem item : permuta.getItens()) {
            int qtd = item.getQuantidadeAprovada() != null ? item.getQuantidadeAprovada() : item.getQuantidadeSolicitada();
            if (qtd <= 0) continue;

            PharmaciaEstoque estoqueFornecedor = estoqueService.findByLoteAndSigla(
                    item.getLote().getId(), permuta.getUnidadeDestinoSigla());
            estoqueFornecedor.setQuantidade(estoqueFornecedor.getQuantidade() - qtd);
            estoqueFornecedor.persist();

            PharmaciaLote lote = item.getLote();
            lote.setQuantidadeAtual(lote.getQuantidadeAtual() - qtd);

            PharmaciaMovimentacao mov = new PharmaciaMovimentacao();
            mov.setTipo(PharmaciaMovimentacao.TipoMovimentacao.SAIDA);
            mov.setLote(lote);
            // Fisicamente: sai de quem tinha o estoque (destino da permuta) e
            // vai para quem solicitou (origem da permuta).
            mov.setUnidadeOrigemSigla(permuta.getUnidadeDestinoSigla());
            mov.setUnidadeDestinoSigla(permuta.getUnidadeOrigemSigla());
            mov.setQuantidade(qtd);
            mov.setDataHora(LocalDateTime.now());
            mov.setJustificativa("Saida para permuta #" + permuta.getId() + " em transporte, iniciado por " + permuta.getTransporteMatricula());
            mov.setUsuarioMatricula(permuta.getTransporteMatricula() != null ? permuta.getTransporteMatricula() : "sistema");
            mov.setAtivo(true);
            mov.persist();
        }

        return permuta;
    }

    @Transactional
    public PharmaciaPermuta finalizar(Long permutaId, String recebedorMatricula, PharmaciaPermutaFinalizarRequest request, String unidadeExecutora) {
        PharmaciaPermuta permuta = PharmaciaPermuta.find(
            "SELECT p FROM PharmaciaPermuta p LEFT JOIN FETCH p.itens i LEFT JOIN FETCH i.lote WHERE p.id = ?1", permutaId)
            .firstResult();
        if (permuta == null) throw new RuntimeException("Permuta nao encontrada");
        if (permuta.getStatus() != PharmaciaPermuta.StatusPermuta.EM_TRANSPORTE)
            throw new IllegalStateException("Apenas permutas EM_TRANSPORTE podem ser finalizadas");
        // Quem confirma o recebimento e quem SOLICITOU (unidadeOrigemSigla) —
        // e nao necessariamente a mesma pessoa que abriu o pedido original,
        // por isso o recebedor e registrado separado do solicitante.
        exigirUnidade(permuta.getUnidadeOrigemSigla(), unidadeExecutora, "confirmar o recebimento de");

        String quemConfirmou = recebedorMatricula != null && !recebedorMatricula.isBlank() ? recebedorMatricula : "sistema";
        String dataHoraTexto = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        // Quem RECEBE fisicamente e quem SOLICITOU (unidadeOrigemSigla da
        // Permuta) — o fornecedor (unidadeDestinoSigla) ja teve seu estoque
        // descontado em iniciarTransporte. Por item, a unidade solicitante
        // escolhe: mesclar no lote de origem (padrao) ou cadastrar como um
        // lote proprio novo — nos dois casos fica registrada a observacao
        // de proveniencia da permuta. A quantidade fisicamente conferida no
        // recebimento pode divergir da enviada (quebra, erro de contagem) —
        // nesse caso e obrigatorio justificar, e so a quantidade realmente
        // recebida entra no estoque do solicitante (a diferenca fica
        // documentada, nao "aparece" magicamente em lugar nenhum).
        for (PharmaciaPermutaItem item : permuta.getItens()) {
            int qtdEnviada = item.getQuantidadeAprovada() != null ? item.getQuantidadeAprovada() : item.getQuantidadeSolicitada();
            if (qtdEnviada <= 0) continue;

            PharmaciaPermutaFinalizarRequest.ItemDecisao decisao = request != null && request.getDecisoes() != null
                    ? request.getDecisoes().get(String.valueOf(item.getId())) : null;
            boolean comoNovo = decisao != null && "NOVO".equalsIgnoreCase(decisao.getModo());

            int qtd = decisao != null && decisao.getQuantidadeRecebida() != null ? decisao.getQuantidadeRecebida() : qtdEnviada;
            if (qtd < 0 || qtd > qtdEnviada) {
                throw new IllegalArgumentException("Item " + item.getId() + ": quantidade recebida (" + qtd
                        + ") invalida — nao pode ser negativa nem maior que a enviada (" + qtdEnviada + ").");
            }
            boolean divergiu = qtd != qtdEnviada;
            if (divergiu && (decisao.getNotaDivergencia() == null || decisao.getNotaDivergencia().trim().isEmpty())) {
                throw new IllegalArgumentException("Item " + item.getId() + ": quantidade recebida (" + qtd
                        + ") diferente da enviada (" + qtdEnviada + ") — informe uma justificativa para a divergencia.");
            }

            String observacaoPadrao = (divergiu ? "[DIVERGENCIA: enviado " + qtdEnviada + ", recebido " + qtd
                    + " — " + decisao.getNotaDivergencia().trim() + "] " : "")
                    + "Recebido via permuta #" + permuta.getId() + " da unidade " + permuta.getUnidadeDestinoSigla()
                    + " em " + dataHoraTexto + " por " + quemConfirmou + ".";

            if (qtd == 0) {
                // Nada foi efetivamente recebido (perda total) — so registra a
                // movimentacao/observacao de divergencia, sem credito de estoque.
                PharmaciaMovimentacao movPerda = new PharmaciaMovimentacao();
                movPerda.setTipo(PharmaciaMovimentacao.TipoMovimentacao.ENTRADA);
                movPerda.setLote(item.getLote());
                movPerda.setUnidadeOrigemSigla(permuta.getUnidadeDestinoSigla());
                movPerda.setUnidadeDestinoSigla(permuta.getUnidadeOrigemSigla());
                movPerda.setQuantidade(0);
                movPerda.setDataHora(LocalDateTime.now());
                movPerda.setJustificativa(observacaoPadrao);
                movPerda.setUsuarioMatricula(quemConfirmou);
                movPerda.setAtivo(true);
                movPerda.persist();
                continue;
            }

            PharmaciaLote loteParaEstoque;
            if (comoNovo) {
                if (decisao.getProdutoId() == null || decisao.getNumeroLote() == null || decisao.getNumeroLote().trim().isEmpty()
                        || decisao.getDataValidade() == null) {
                    throw new IllegalArgumentException("Item " + item.getId() + ": informe produto, numero de lote e validade para cadastrar como novo lote.");
                }
                PharmaciaProduto produto = PharmaciaProduto.findById(decisao.getProdutoId());
                if (produto == null) throw new IllegalArgumentException("Produto nao encontrado: id=" + decisao.getProdutoId());

                String numeroLote = decisao.getNumeroLote().trim();
                PharmaciaLote existente = PharmaciaLote.find("produto.id = ?1 and numeroLote = ?2", produto.getId(), numeroLote).firstResult();
                if (existente != null) {
                    throw new IllegalArgumentException("Ja existe um lote " + numeroLote + " cadastrado para este produto — use outro numero de lote.");
                }

                PharmaciaLote novoLote = new PharmaciaLote();
                novoLote.setProduto(produto);
                novoLote.setNumeroLote(numeroLote);
                novoLote.setDataFabricacao(decisao.getDataFabricacao() != null ? decisao.getDataFabricacao() : LocalDateTime.now().toLocalDate());
                novoLote.setDataValidade(decisao.getDataValidade());
                novoLote.setQuantidadeInicial(qtd);
                novoLote.setQuantidadeAtual(qtd);
                novoLote.setBloqueado(false);
                novoLote.setAtivo(true);
                novoLote.setUnidadeSigla(permuta.getUnidadeOrigemSigla());
                novoLote.persist();
                loteParaEstoque = novoLote;

                PharmaciaEstoque estoqueNovo = new PharmaciaEstoque();
                estoqueNovo.setLote(novoLote);
                estoqueNovo.setUnidadeSigla(permuta.getUnidadeOrigemSigla());
                estoqueNovo.setQuantidade(qtd);
                estoqueNovo.setEstoqueMinimo(0);
                estoqueNovo.setAtivo(true);
                estoqueNovo.setObservacao(observacaoPadrao);
                estoqueNovo.persist();
            } else {
                loteParaEstoque = item.getLote();
                PharmaciaEstoque estoqueSolicitante = estoqueService.findByLoteAndSigla(
                        item.getLote().getId(), permuta.getUnidadeOrigemSigla());
                if (estoqueSolicitante == null) {
                    estoqueSolicitante = new PharmaciaEstoque();
                    estoqueSolicitante.setLote(item.getLote());
                    estoqueSolicitante.setUnidadeSigla(permuta.getUnidadeOrigemSigla());
                    estoqueSolicitante.setQuantidade(qtd);
                    estoqueSolicitante.setEstoqueMinimo(0);
                    estoqueSolicitante.setAtivo(true);
                    estoqueSolicitante.setObservacao(observacaoPadrao);
                    estoqueSolicitante.persist();
                } else {
                    estoqueSolicitante.setQuantidade(estoqueSolicitante.getQuantidade() + qtd);
                    estoqueSolicitante.setObservacao(observacaoPadrao);
                    estoqueSolicitante.persist();
                }
            }

            PharmaciaMovimentacao mov = new PharmaciaMovimentacao();
            mov.setTipo(PharmaciaMovimentacao.TipoMovimentacao.ENTRADA);
            mov.setLote(loteParaEstoque);
            mov.setUnidadeOrigemSigla(permuta.getUnidadeDestinoSigla());
            mov.setUnidadeDestinoSigla(permuta.getUnidadeOrigemSigla());
            mov.setQuantidade(qtd);
            mov.setDataHora(LocalDateTime.now());
            mov.setJustificativa(observacaoPadrao);
            mov.setUsuarioMatricula(quemConfirmou);
            mov.setAtivo(true);
            mov.persist();
        }

        permuta.setStatus(PharmaciaPermuta.StatusPermuta.FINALIZADO);
        permuta.setDataFinalizacao(LocalDateTime.now());
        permuta.setRecebedorMatricula(quemConfirmou);
        permuta.persist();

        return permuta;
    }

    @Transactional
    public void delete(Long id, String unidadeExecutora) {
        PharmaciaPermuta permuta = PharmaciaPermuta.findById(id);
        if (permuta == null) throw new RuntimeException("Permuta nao encontrada");
        if (permuta.getStatus() != PharmaciaPermuta.StatusPermuta.PENDENTE)
            throw new IllegalStateException("So e possivel excluir permutas PENDENTES — permutas ja aprovadas, em transporte ou finalizadas precisam ficar no historico.");
        // So quem solicitou (unidadeOrigemSigla) pode cancelar o proprio pedido.
        exigirUnidade(permuta.getUnidadeOrigemSigla(), unidadeExecutora, "cancelar");
        PharmaciaPermuta.deleteById(id);
    }

    public List<PharmaciaPermutaMensagem> listarMensagens(Long permutaId) {
        return PharmaciaPermutaMensagem.list("permuta.id = ?1 order by criadoEm asc", permutaId);
    }

    // Canal de comunicacao livre entre origem e destino ate a unidade
    // recebedora encerrar o pedido (finalizar ou recusar) — depois disso a
    // conversa fica congelada, so leitura.
    @Transactional
    public PharmaciaPermutaMensagem enviarMensagem(Long permutaId, PharmaciaPermutaMensagem mensagem) {
        PharmaciaPermuta permuta = PharmaciaPermuta.findById(permutaId);
        if (permuta == null) {
            throw new IllegalArgumentException("Permuta nao encontrada");
        }
        if (permuta.getStatus() == PharmaciaPermuta.StatusPermuta.FINALIZADO
                || permuta.getStatus() == PharmaciaPermuta.StatusPermuta.RECUSADO) {
            throw new IllegalStateException("Esta permuta ja foi encerrada — nao e mais possivel enviar mensagens.");
        }
        if (mensagem.getTexto() == null || mensagem.getTexto().trim().isEmpty()) {
            throw new IllegalArgumentException("Mensagem vazia.");
        }
        mensagem.setPermuta(permuta);
        mensagem.setCriadoEm(LocalDateTime.now());
        mensagem.persist();
        return mensagem;
    }
}
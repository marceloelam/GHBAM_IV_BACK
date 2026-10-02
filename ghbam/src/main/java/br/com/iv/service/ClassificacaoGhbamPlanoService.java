package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.iv.entity.ClassificacaoGhbamAuditoria;
import br.com.iv.entity.ClassificacaoGhbamItem;
import br.com.iv.entity.ClassificacaoGhbamPlanoExecucao;
import br.com.iv.entity.ClassificacaoGhbamPlanoItem;
import br.com.iv.repository.ClassificacaoGhbamAuditoriaRepository;
import br.com.iv.repository.ClassificacaoGhbamItemRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Plano de cuidados montado a partir do catálogo aprovado da Classificação GHBAM.
 * O sistema nunca inclui, executa, avalia ou encerra itens sozinho: cada ação é
 * uma decisão registrada do profissional autenticado.
 */
@ApplicationScoped
public class ClassificacaoGhbamPlanoService {
    private static final int MAX_ITENS_POR_INCLUSAO = 30;
    private static final Set<String> TIPOS_PLANO = Set.of("NECESSIDADE_CUIDADO", "ACAO_CUIDADO", "RESULTADO_AVALIACAO");
    private static final Set<String> SITUACOES_EXECUCAO = Set.of("REALIZADO", "NAO_REALIZADO");
    private static final Set<String> AVALIACOES = Set.of("ALCANCADO", "PARCIAL", "NAO_ALCANCADO");

    @Inject ClassificacaoGhbamItemRepository catalogo;
    @Inject ClassificacaoGhbamAuditoriaRepository auditoriaRepository;

    public List<Map<String, Object>> listar(Integer bam) {
        List<ClassificacaoGhbamPlanoItem> itens = ClassificacaoGhbamPlanoItem.list(
                "bam = ?1 order by status, itemTipo, itemCodigo", bam);
        Map<String, ClassificacaoGhbamPlanoExecucao> ultimas = new HashMap<>();
        if (!itens.isEmpty()) {
            List<ClassificacaoGhbamPlanoExecucao> execucoes = ClassificacaoGhbamPlanoExecucao.list(
                    "bam = ?1 order by executadoEm desc, id desc", bam);
            for (ClassificacaoGhbamPlanoExecucao execucao : execucoes) ultimas.putIfAbsent(execucao.getPlanoItemUuid(), execucao);
        }
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (ClassificacaoGhbamPlanoItem item : itens) {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("item", item);
            linha.put("ultimaExecucao", ultimas.get(item.getUuid()));
            resultado.add(linha);
        }
        return resultado;
    }

    public List<ClassificacaoGhbamPlanoExecucao> execucoes(String uuid) {
        existente(uuid);
        return ClassificacaoGhbamPlanoExecucao.list("planoItemUuid = ?1 order by executadoEm desc, id desc", uuid);
    }

    @Transactional
    public Map<String, Object> incluir(JsonNode body, AuthenticatedUser usuario) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException("Dados do plano obrigatorios");
        int bam = body.path("bam").asInt(0);
        if (bam <= 0) throw new IllegalArgumentException("BAM obrigatorio");
        Integer idpaciente = body.path("idpaciente").asInt(0) > 0 ? body.path("idpaciente").asInt() : null;
        if (!body.path("decisaoProfissional").asBoolean(false)) {
            throw new IllegalArgumentException("Confirme que a inclusao e decisao do profissional apos avaliacao da pessoa");
        }
        JsonNode itens = body.path("itens");
        if (!itens.isArray() || itens.isEmpty()) throw new IllegalArgumentException("Selecione ao menos um item do catalogo");
        if (itens.size() > MAX_ITENS_POR_INCLUSAO) throw new IllegalArgumentException("Inclua no maximo " + MAX_ITENS_POR_INCLUSAO + " itens por vez");
        String observacao = texto(body.path("observacao").asText(""));

        List<ClassificacaoGhbamPlanoItem> incluidos = new ArrayList<>();
        List<String> ignorados = new ArrayList<>();
        for (JsonNode valor : itens) {
            ClassificacaoGhbamItem origem = catalogo.porUuid(valor.asText(""));
            if (origem == null) throw new IllegalArgumentException("Item de catalogo nao encontrado");
            if (!"APROVADO".equals(origem.getStatus())) {
                throw new IllegalStateException("Somente itens aprovados podem entrar no plano: " + origem.getCodigo());
            }
            if (!TIPOS_PLANO.contains(origem.getTipo())) {
                throw new IllegalArgumentException("Indicadores e alertas de fluxo nao entram no plano: " + origem.getCodigo());
            }
            if (ClassificacaoGhbamPlanoItem.count("bam = ?1 and itemCodigo = ?2 and status = 'ATIVO'", bam, origem.getCodigo()) > 0) {
                ignorados.add(origem.getCodigo());
                continue;
            }
            ClassificacaoGhbamPlanoItem item = new ClassificacaoGhbamPlanoItem();
            item.setUuid(UUID.randomUUID().toString());
            item.setBam(bam);
            item.setIdpaciente(idpaciente);
            item.setUnidade(usuario.unidade());
            item.setItemUuid(origem.getUuid());
            item.setItemCodigo(origem.getCodigo());
            item.setItemVersao(origem.getVersao());
            item.setItemTipo(origem.getTipo());
            item.setItemTitulo(origem.getTitulo());
            item.setStatus("ATIVO");
            item.setObservacao(observacao.isBlank() ? null : observacao);
            item.setIncluidoPor(usuario.nome());
            item.setIncluidoPorMatricula(usuario.matricula());
            item.persist();
            registrar(item, "PLANO_INCLUIDO", usuario, "Incluido no plano do BAM " + bam + ": " + origem.getCodigo() + " v" + origem.getVersao() + " (decisao profissional confirmada)");
            incluidos.add(item);
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("incluidos", incluidos);
        resposta.put("ignorados", ignorados);
        return resposta;
    }

    @Transactional
    public ClassificacaoGhbamPlanoItem registrarExecucao(String uuid, String situacao, String observacao, AuthenticatedUser usuario) {
        ClassificacaoGhbamPlanoItem item = ativo(uuid);
        if (!"ACAO_CUIDADO".equals(item.getItemTipo())) throw new IllegalStateException("Execucao so pode ser registrada para acoes de cuidado");
        String valor = texto(situacao).toUpperCase();
        if (!SITUACOES_EXECUCAO.contains(valor)) throw new IllegalArgumentException("Situacao da execucao invalida");
        String justificativa = texto(observacao);
        if ("NAO_REALIZADO".equals(valor) && justificativa.isBlank()) throw new IllegalArgumentException("Informe o motivo da nao realizacao");
        ClassificacaoGhbamPlanoExecucao execucao = new ClassificacaoGhbamPlanoExecucao();
        execucao.setPlanoItemUuid(item.getUuid());
        execucao.setBam(item.getBam());
        execucao.setSituacao(valor);
        execucao.setObservacao(justificativa.isBlank() ? null : justificativa);
        execucao.setExecutadoPor(usuario.nome());
        execucao.setExecutadoPorMatricula(usuario.matricula());
        execucao.persist();
        registrar(item, "PLANO_EXECUCAO", usuario, valor + (justificativa.isBlank() ? "" : ": " + justificativa));
        return item;
    }

    @Transactional
    public ClassificacaoGhbamPlanoItem avaliar(String uuid, String avaliacao, String observacao, AuthenticatedUser usuario) {
        ClassificacaoGhbamPlanoItem item = ativo(uuid);
        if (!"RESULTADO_AVALIACAO".equals(item.getItemTipo())) throw new IllegalStateException("Avaliacao so pode ser registrada para resultados");
        String valor = texto(avaliacao).toUpperCase();
        if (!AVALIACOES.contains(valor)) throw new IllegalArgumentException("Avaliacao invalida");
        String justificativa = texto(observacao);
        if (!"ALCANCADO".equals(valor) && justificativa.isBlank()) throw new IllegalArgumentException("Descreva a situacao quando o resultado nao foi alcancado");
        item.setAvaliacao(valor);
        item.setAvaliacaoObservacao(justificativa.isBlank() ? null : justificativa);
        item.setAvaliadoPor(usuario.nome());
        item.setAvaliadoPorMatricula(usuario.matricula());
        item.setAvaliadoEm(LocalDateTime.now());
        registrar(item, "PLANO_AVALIADO", usuario, valor + (justificativa.isBlank() ? "" : ": " + justificativa));
        return item;
    }

    @Transactional
    public ClassificacaoGhbamPlanoItem encerrar(String uuid, String motivo, AuthenticatedUser usuario) {
        ClassificacaoGhbamPlanoItem item = ativo(uuid);
        String justificativa = texto(motivo);
        if (justificativa.isBlank()) throw new IllegalArgumentException("Motivo do encerramento obrigatorio");
        item.setStatus("ENCERRADO");
        item.setEncerradoPor(usuario.nome());
        item.setEncerradoPorMatricula(usuario.matricula());
        item.setEncerradoEm(LocalDateTime.now());
        item.setMotivoEncerramento(justificativa);
        registrar(item, "PLANO_ENCERRADO", usuario, justificativa);
        return item;
    }

    private ClassificacaoGhbamPlanoItem ativo(String uuid) {
        ClassificacaoGhbamPlanoItem item = existente(uuid);
        if (!"ATIVO".equals(item.getStatus())) throw new IllegalStateException("Item do plano ja encerrado");
        return item;
    }

    private ClassificacaoGhbamPlanoItem existente(String uuid) {
        ClassificacaoGhbamPlanoItem item = uuid == null || uuid.isBlank() ? null
                : ClassificacaoGhbamPlanoItem.find("uuid", uuid.trim()).firstResult();
        if (item == null) throw new IllegalArgumentException("Item do plano nao encontrado");
        return item;
    }

    private void registrar(ClassificacaoGhbamPlanoItem item, String evento, AuthenticatedUser usuario, String detalhe) {
        ClassificacaoGhbamAuditoria registro = new ClassificacaoGhbamAuditoria();
        registro.setItemUuid(item.getUuid());
        registro.setEvento(evento);
        registro.setAtorNome(usuario.nome());
        registro.setAtorMatricula(usuario.matricula());
        registro.setDetalhe(detalhe);
        auditoriaRepository.persist(registro);
    }

    private String texto(String valor) {
        String limpo = valor == null ? "" : valor.trim();
        return limpo.length() > 2000 ? limpo.substring(0, 2000) : limpo;
    }
}

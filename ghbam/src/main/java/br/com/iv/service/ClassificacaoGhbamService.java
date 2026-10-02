package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.entity.ClassificacaoGhbamItem;
import br.com.iv.entity.ClassificacaoGhbamAuditoria;
import br.com.iv.repository.ClassificacaoGhbamAuditoriaRepository;
import br.com.iv.repository.ClassificacaoGhbamItemRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ClassificacaoGhbamService {
    private static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "NECESSIDADE_CUIDADO", "ACAO_CUIDADO", "RESULTADO_AVALIACAO", "INDICADOR", "ALERTA_FLUXO");
    private static final Map<String, String> PREFIXOS = Map.of(
            "NECESSIDADE_CUIDADO", "NC", "ACAO_CUIDADO", "AC", "RESULTADO_AVALIACAO", "RA", "INDICADOR", "IN", "ALERTA_FLUXO", "AL");
    private static final int MAX_ITENS_LISTA = 50;
    private static final int MAX_TAMANHO_ITEM_LISTA = 200;
    @Inject ClassificacaoGhbamItemRepository repository;
    @Inject ClassificacaoGhbamAuditoriaRepository auditoriaRepository;
    @Inject ObjectMapper mapper;

    public List<ClassificacaoGhbamItem> listar(String status) {
        return repository.listar(status);
    }

    public ClassificacaoGhbamItem buscar(String uuid) {
        return repository.porUuid(uuid);
    }

    @Transactional
    public ClassificacaoGhbamItem salvar(JsonNode body, AuthenticatedUser usuario) {
        String uuid = texto(body, "uuid");
        ClassificacaoGhbamItem item = uuid.isBlank() ? new ClassificacaoGhbamItem() : repository.porUuid(uuid);
        if (!uuid.isBlank() && item == null) throw new IllegalArgumentException("Item nao encontrado");
        validar(body);
        boolean novo = item.getId() == null;
        if (!novo && "APROVADO".equals(item.getStatus())) {
            throw new IllegalStateException("Item aprovado deve ser versionado antes de alterar");
        }
        if (!novo && !"RASCUNHO".equals(item.getStatus())) {
            throw new IllegalStateException("Somente rascunho pode ser alterado; devolva o item ao rascunho antes de editar");
        }
        // Código é sempre do sistema: gerado pelo tipo (ex.: AC-017) e nunca aceito do cliente.
        String tipo = textoObrigatorio(body, "tipo").toUpperCase();
        String codigo = novo ? proximoCodigo(tipo) : item.getCodigo();
        if (!novo && !tipo.equals(item.getTipo())) {
            if (repository.count("codigo", item.getCodigo()) > 1) {
                throw new IllegalStateException("O tipo nao pode ser alterado em nova versao de item ja existente");
            }
            codigo = proximoCodigo(tipo);
        }
        String versao = texto(body, "versao").isBlank() ? (novo ? "0.1.0" : item.getVersao()) : texto(body, "versao");
        garantirCodigoVersaoLivre(item, codigo, versao);
        if (novo) {
            item.setUuid(UUID.randomUUID().toString());
            item.setStatus("RASCUNHO");
            item.setCriadoPor(usuario.nome());
            item.setCriadoPorMatricula(usuario.matricula());
        }
        item.setCodigo(codigo);
        item.setTipo(tipo);
        item.setTitulo(textoObrigatorio(body, "titulo"));
        item.setDefinicao(texto(body, "definicao"));
        item.setVersao(versao);
        item.setIndicadoresJson(listaTexto(body.get("indicadores"), "Indicadores"));
        item.setRelacionamentosJson(relacionamentosValidos(body.get("relacionamentos"), codigo));
        item.setRevisadoPor(null);
        item.setRevisadoPorMatricula(null);
        item.setAprovadoPor(null);
        item.setAprovadoPorMatricula(null);
        item.setAprovadoEm(null);
        if (novo) repository.persist(item); else repository.getEntityManager().merge(item);
        registrar(item, novo ? "CRIADO" : "ALTERADO", usuario, "Conteudo do item atualizado");
        return item;
    }

    @Transactional
    public ClassificacaoGhbamItem enviarRevisao(String uuid, AuthenticatedUser usuario) {
        ClassificacaoGhbamItem item = existente(uuid);
        if (!"RASCUNHO".equals(item.getStatus())) throw new IllegalStateException("Somente rascunho pode ser enviado para revisao");
        item.setStatus("EM_REVISAO");
        item.setRevisadoPor(usuario.nome());
        item.setRevisadoPorMatricula(usuario.matricula());
        registrar(item, "ENVIADO_REVISAO", usuario, "Item enviado para revisao");
        return item;
    }

    @Transactional
    public ClassificacaoGhbamItem aprovar(String uuid, AuthenticatedUser usuario) {
        ClassificacaoGhbamItem item = existente(uuid);
        if (!"EM_REVISAO".equals(item.getStatus())) throw new IllegalStateException("Item precisa estar em revisao");
        String aprovador = usuario.matricula();
        if (aprovador.equalsIgnoreCase(valor(item.getCriadoPorMatricula())) || aprovador.equalsIgnoreCase(valor(item.getRevisadoPorMatricula()))) {
            throw new IllegalStateException("A aprovacao deve ser feita por profissional diferente de quem elaborou ou enviou o item para revisao");
        }
        // Uma unica versao aprovada por codigo: a anterior sai do catalogo ativo.
        for (ClassificacaoGhbamItem anterior : repository.aprovadosPorCodigo(item.getCodigo())) {
            if (anterior.getId().equals(item.getId())) continue;
            anterior.setStatus("ARQUIVADO");
            registrar(anterior, "SUBSTITUIDO", usuario, "Substituido pela versao " + item.getVersao() + " (" + item.getUuid() + ")");
        }
        item.setStatus("APROVADO");
        item.setAprovadoPor(usuario.nome());
        item.setAprovadoPorMatricula(aprovador);
        item.setAprovadoEm(LocalDateTime.now());
        registrar(item, "APROVADO", usuario, "Item aprovado para uso assistencial");
        return item;
    }

    @Transactional
    public ClassificacaoGhbamItem rejeitar(String uuid, String motivo, AuthenticatedUser usuario) {
        ClassificacaoGhbamItem item = existente(uuid);
        if (!"EM_REVISAO".equals(item.getStatus())) throw new IllegalStateException("Somente item em revisao pode ser devolvido");
        String justificativa = motivo == null ? "" : motivo.trim();
        if (justificativa.isBlank()) throw new IllegalArgumentException("Motivo da devolucao obrigatorio");
        item.setStatus("RASCUNHO");
        item.setRevisadoPor(usuario.nome());
        item.setRevisadoPorMatricula(usuario.matricula());
        registrar(item, "DEVOLVIDO_RASCUNHO", usuario, justificativa);
        return item;
    }

    @Transactional
    public ClassificacaoGhbamItem novaVersao(String uuid, AuthenticatedUser usuario) {
        ClassificacaoGhbamItem original = existente(uuid);
        if (!"APROVADO".equals(original.getStatus())) throw new IllegalStateException("Somente item aprovado pode gerar nova versao");
        String versao = proximaVersao(original.getVersao());
        if (repository.porCodigoVersao(original.getCodigo(), versao) != null) throw new IllegalStateException("A nova versao ja existe");
        ClassificacaoGhbamItem copia = new ClassificacaoGhbamItem();
        copia.setUuid(UUID.randomUUID().toString());
        copia.setCodigo(original.getCodigo());
        copia.setTipo(original.getTipo());
        copia.setTitulo(original.getTitulo());
        copia.setDefinicao(original.getDefinicao());
        copia.setVersao(versao);
        copia.setIndicadoresJson(original.getIndicadoresJson());
        copia.setRelacionamentosJson(original.getRelacionamentosJson());
        copia.setStatus("RASCUNHO");
        copia.setCriadoPor(usuario.nome());
        copia.setCriadoPorMatricula(usuario.matricula());
        repository.persist(copia);
        registrar(copia, "NOVA_VERSAO", usuario, "Nova versao criada a partir de " + original.getUuid());
        return copia;
    }

    private String proximaVersao(String atual) {
        try {
            String[] partes = atual.split("\\.");
            return partes[0] + "." + partes[1] + "." + (Integer.parseInt(partes[2]) + 1);
        } catch (Exception ignorado) {
            return "0.1.0";
        }
    }

    @Transactional
    public ClassificacaoGhbamItem arquivar(String uuid, String motivo, AuthenticatedUser usuario) {
        ClassificacaoGhbamItem item = existente(uuid);
        if ("ARQUIVADO".equals(item.getStatus())) return item;
        String justificativa = motivo == null ? "" : motivo.trim();
        if (justificativa.isBlank()) throw new IllegalArgumentException("Motivo do arquivamento obrigatorio");
        item.setStatus("ARQUIVADO");
        registrar(item, "ARQUIVADO", usuario, justificativa);
        return item;
    }

    public List<ClassificacaoGhbamAuditoria> auditoria(String uuid) {
        return auditoriaRepository.porItem(uuid);
    }

    private void registrar(ClassificacaoGhbamItem item, String evento, AuthenticatedUser usuario, String detalhe) {
        ClassificacaoGhbamAuditoria registro = new ClassificacaoGhbamAuditoria();
        registro.setItemUuid(item.getUuid());
        registro.setEvento(evento);
        registro.setAtorNome(usuario.nome());
        registro.setAtorMatricula(usuario.matricula());
        registro.setDetalhe(detalhe);
        auditoriaRepository.persist(registro);
    }

    private ClassificacaoGhbamItem existente(String uuid) {
        ClassificacaoGhbamItem item = repository.porUuid(uuid);
        if (item == null) throw new IllegalArgumentException("Item nao encontrado");
        return item;
    }

    private void validar(JsonNode body) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException("Dados do item obrigatorios");
        String tipo = texto(body, "tipo").toUpperCase();
        String titulo = texto(body, "titulo");
        String versao = texto(body, "versao");
        if (tipo.isBlank() || titulo.isBlank()) {
            throw new IllegalArgumentException("Tipo e titulo sao obrigatorios");
        }
        if (!TIPOS_PERMITIDOS.contains(tipo)) throw new IllegalArgumentException("Tipo de classificacao invalido");
        if (titulo.length() > 180) throw new IllegalArgumentException("Titulo excede o limite permitido");
        if (versao.length() > 20 || (!versao.isBlank() && !versao.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
            throw new IllegalArgumentException("Versao deve seguir o formato maior.menor.revisao");
        }
    }

    private String textoObrigatorio(JsonNode body, String campo) {
        String valor = texto(body, campo);
        if (valor.isBlank()) throw new IllegalArgumentException(campo + " obrigatorio");
        return valor;
    }

    private String texto(JsonNode body, String campo) {
        return body == null ? "" : body.path(campo).asText("").trim();
    }

    private String proximoCodigo(String tipo) {
        String prefixo = PREFIXOS.get(tipo);
        if (prefixo == null) throw new IllegalArgumentException("Tipo de classificacao invalido");
        int maior = repository.codigosComPrefixo(prefixo + "-").stream()
                .map(codigo -> codigo.substring(prefixo.length() + 1))
                .filter(numero -> numero.matches("[0-9]{1,6}"))
                .mapToInt(Integer::parseInt)
                .max().orElse(0);
        return String.format("%s-%03d", prefixo, maior + 1);
    }

    private void garantirCodigoVersaoLivre(ClassificacaoGhbamItem item, String codigo, String versao) {
        boolean codigoMudou = item.getId() == null || !codigo.equals(item.getCodigo());
        if (codigoMudou && repository.existeCodigo(codigo)) {
            throw new IllegalStateException("Codigo " + codigo + " ja existe; use 'Criar nova versao' a partir do item aprovado");
        }
        ClassificacaoGhbamItem mesmo = repository.porCodigoVersao(codigo, versao);
        if (mesmo != null && !mesmo.getId().equals(item.getId())) {
            throw new IllegalStateException("Ja existe o codigo " + codigo + " na versao " + versao);
        }
    }

    private String listaTexto(JsonNode node, String campo) {
        if (node == null || node.isNull()) return "[]";
        if (!node.isArray()) throw new IllegalArgumentException(campo + " devem ser uma lista");
        if (node.size() > MAX_ITENS_LISTA) throw new IllegalArgumentException(campo + " excedem o limite de " + MAX_ITENS_LISTA + " itens");
        var lista = mapper.createArrayNode();
        for (JsonNode valor : node) {
            if (!valor.isTextual()) throw new IllegalArgumentException(campo + " devem conter apenas texto");
            String texto = valor.asText().trim();
            if (texto.length() > MAX_TAMANHO_ITEM_LISTA) throw new IllegalArgumentException(campo + ": item excede " + MAX_TAMANHO_ITEM_LISTA + " caracteres");
            if (!texto.isEmpty()) lista.add(texto);
        }
        return lista.toString();
    }

    // Relacionamentos só podem apontar para códigos que existem no catálogo (e nunca para o próprio item).
    private String relacionamentosValidos(JsonNode node, String codigoProprio) {
        String json = listaTexto(node, "Relacionamentos");
        var lista = mapper.createArrayNode();
        java.util.Set<String> vistos = new java.util.LinkedHashSet<>();
        try {
            for (JsonNode valor : mapper.readTree(json)) {
                String codigo = valor.asText().trim().toUpperCase();
                if (codigo.isEmpty() || !vistos.add(codigo)) continue;
                if (codigo.equals(codigoProprio)) throw new IllegalArgumentException("Um item nao pode se relacionar com ele mesmo");
                if (!repository.existeCodigo(codigo)) throw new IllegalArgumentException("Relacionamento com codigo inexistente: " + codigo);
                lista.add(codigo);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Relacionamentos invalidos");
        }
        return lista.toString();
    }

    private String valor(String texto) {
        return texto == null ? "" : texto.trim();
    }
}

package br.com.iv.service;

import java.io.Serializable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;

import br.com.iv.dto.AlergiaTriagemDto;
import br.com.iv.dto.ProntuarioClinicoItemDto;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.repository.ProntuarioClinicoItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ProntuarioClinicoItemService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    ProntuarioClinicoItemRepository repository;

    public List<ProntuarioClinicoItem> listar(int idpaciente, String unidade) {
        return repository.listarPorPaciente(idpaciente, unidade);
    }

    public List<ProntuarioClinicoItem> listarPorTipo(int idpaciente, String tipo, String unidade) {
        return repository.listarPorPacienteTipo(idpaciente, normalizeTipo(tipo), unidade);
    }

    @Transactional
    public ProntuarioClinicoItem salvar(ProntuarioClinicoItemDto dto) {
        ProntuarioClinicoItem item = new ProntuarioClinicoItem();
        preencher(item, dto, true);
        item.persist();
        return item;
    }

    @Transactional
    public ProntuarioClinicoItem atualizar(int id, ProntuarioClinicoItemDto dto) {
        ProntuarioClinicoItem item = repository.findById(id);
        if (item == null) {
            return null;
        }
        preencher(item, dto, false);
        return item;
    }

    @Transactional
    public boolean inativar(int id, ProntuarioClinicoItemDto dto) {
        ProntuarioClinicoItem item = repository.findById(id);
        if (item == null) {
            return false;
        }
        item.setStatus("INATIVO");
        item.setAtualizadoPor(safe(dto.getAtualizadoPor()));
        item.setAtualizadoPorMatricula(safe(dto.getAtualizadoPorMatricula()));
        return true;
    }

    private void preencher(ProntuarioClinicoItem item, ProntuarioClinicoItemDto dto, boolean novo) {
        item.setIdpaciente(dto.getIdpaciente());
        item.setIdbam(dto.getIdbam());
        item.setUnidade(safe(dto.getUnidade()));
        item.setTipo(normalizeTipo(dto.getTipo()));
        item.setStatus(safeDefault(dto.getStatus(), "ATIVO").toUpperCase());
        item.setCodigo(safe(dto.getCodigo()));
        item.setTitulo(safe(dto.getTitulo()));
        item.setDescricao(safe(dto.getDescricao()));
        item.setObservacao(safe(dto.getObservacao()));
        if (novo) {
            item.setCriadoPor(safe(dto.getCriadoPor()));
            item.setCriadoPorMatricula(safe(dto.getCriadoPorMatricula()));
        }
        item.setAtualizadoPor(safe(dto.getAtualizadoPor()));
        item.setAtualizadoPorMatricula(safe(dto.getAtualizadoPorMatricula()));
    }

    private static final int MAX_ALERGIAS_TRIAGEM = 10;

    /**
     * Registra como itens ALERGIA do paciente a descricao livre digitada na
     * triagem. Idempotente: alergia ja ativa com o mesmo termo nao e duplicada
     * a cada novo atendimento. Retorna somente os itens efetivamente criados.
     */
    @Transactional
    public List<ProntuarioClinicoItem> registrarAlergiasDaTriagem(AlergiaTriagemDto dto) {
        if (dto == null || dto.getIdpaciente() <= 0) {
            throw new IllegalArgumentException("Paciente obrigatorio para registrar alergia.");
        }
        List<String> termos = separarAlergias(dto.getDescricao());
        if (termos.isEmpty()) {
            return List.of();
        }
        Set<String> jaRegistradas = repository.listarPorPacienteTipo(dto.getIdpaciente(), "ALERGIA", "TODAS").stream()
                .filter(item -> !"INATIVO".equalsIgnoreCase(item.getStatus()))
                .map(item -> chaveAlergia(item.getTitulo()))
                .filter(chave -> !chave.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ProntuarioClinicoItem> criados = new ArrayList<>();
        for (String termo : termos) {
            String chave = chaveAlergia(termo);
            if (chave.isBlank() || !jaRegistradas.add(chave)) {
                continue;
            }
            ProntuarioClinicoItem item = new ProntuarioClinicoItem();
            item.setIdpaciente(dto.getIdpaciente());
            item.setIdbam(dto.getIdbam());
            item.setUnidade(safe(dto.getUnidade()));
            item.setTipo("ALERGIA");
            item.setStatus("ATIVO");
            item.setCodigo("TRIAGEM");
            item.setTitulo(termo);
            item.setDescricao(safe(dto.getDescricao()));
            item.setObservacao(dto.getIdbam() == null || dto.getIdbam() <= 0
                    ? "Registrada na triagem."
                    : "Registrada na triagem do BAM " + dto.getIdbam() + ".");
            item.setCriadoPor(safe(dto.getCriadoPor()));
            item.setCriadoPorMatricula(safe(dto.getCriadoPorMatricula()));
            item.setAtualizadoPor(safe(dto.getCriadoPor()));
            item.setAtualizadoPorMatricula(safe(dto.getCriadoPorMatricula()));
            item.persist();
            criados.add(item);
        }
        return criados;
    }

    /**
     * A triagem digita em texto corrido ("dipirona, AAS e frutos do mar").
     * Quebrar em termos e o que faz o casamento por substancia do CDSS
     * funcionar, em vez de comparar o nome do medicamento com a frase inteira.
     */
    private List<String> separarAlergias(String descricao) {
        String texto = safe(descricao);
        if (texto.isBlank()) {
            return List.of();
        }
        return Arrays.stream(texto.split("[,;/]|\\s+e\\s+"))
                .map(String::trim)
                .map(termo -> termo.replaceAll("(?i)^(alergias?|alergic[oa])\\s+(a[oso]?s?|com)?\\s*", ""))
                .map(String::trim)
                .filter(termo -> termo.length() >= 3)
                .distinct()
                .limit(MAX_ALERGIAS_TRIAGEM)
                .toList();
    }

    private String chaveAlergia(String valor) {
        String base = safe(valor).toLowerCase(Locale.ROOT);
        return Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String normalizeTipo(String tipo) {
        return safeDefault(tipo, "OBSERVACAO").toUpperCase().replace("-", "_").replace(" ", "_");
    }

    private String safeDefault(String value, String fallback) {
        String safe = safe(value);
        return safe.isBlank() ? fallback : safe;
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}

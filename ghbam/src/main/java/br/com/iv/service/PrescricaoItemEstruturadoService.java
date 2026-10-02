package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import br.com.iv.dto.PrescricaoItemEstruturadoDto;
import br.com.iv.entity.PrescricaoItemEstruturado;
import br.com.iv.repository.PrescricaoItemEstruturadoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PrescricaoItemEstruturadoService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    PrescricaoItemEstruturadoRepository repository;

    @Inject
    AssinaturaClinicaService assinaturaClinicaService;

    public List<PrescricaoItemEstruturado> listarPorBam(int idbam, boolean apenasAtivos) {
        return apenasAtivos ? repository.listarAtivosPorBam(idbam) : repository.listarPorBam(idbam);
    }

    public List<PrescricaoItemEstruturado> listarPorPrescricao(int idprescricao) {
        return repository.listarPorPrescricao(idprescricao);
    }

    @Transactional
    public PrescricaoItemEstruturado salvar(PrescricaoItemEstruturadoDto dto) {
        PrescricaoItemEstruturado item = new PrescricaoItemEstruturado();
        preencher(item, dto, true);
        item.persist();
        return item;
    }

    @Transactional
    public PrescricaoItemEstruturado atualizar(int id, PrescricaoItemEstruturadoDto dto) {
        PrescricaoItemEstruturado item = repository.findById(id);
        if (item == null) {
            return null;
        }
        validarItemNaoAssinado(item);
        if ("SUSPENSO".equalsIgnoreCase(item.getStatus())) {
            return item;
        }
        preencher(item, dto, false);
        return item;
    }

    @Transactional
    public PrescricaoItemEstruturado suspender(int id, PrescricaoItemEstruturadoDto dto) {
        PrescricaoItemEstruturado item = repository.findById(id);
        if (item == null) {
            return null;
        }
        validarItemNaoAssinado(item);
        item.setStatus("SUSPENSO");
        item.setSuspensoPor(safe(dto.getSuspensoPor()));
        item.setSuspensoPorMatricula(safe(dto.getSuspensoPorMatricula()));
        item.setMotivoSuspensao(safe(dto.getMotivoSuspensao()));
        item.setAtualizadoPor(safe(dto.getAtualizadoPor()));
        item.setAtualizadoPorMatricula(safe(dto.getAtualizadoPorMatricula()));
        item.setSuspensoEm(LocalDateTime.now());
        return item;
    }

    private void preencher(PrescricaoItemEstruturado item, PrescricaoItemEstruturadoDto dto, boolean novo) {
        item.setIdprescricao(dto.getIdprescricao());
        item.setIdpaciente(dto.getIdpaciente());
        item.setIdbam(dto.getIdbam());
        item.setUnidade(safe(dto.getUnidade()));
        item.setStatus(safeDefault(dto.getStatus(), "ATIVO").toUpperCase());
        item.setTipoItem(safeDefault(dto.getTipoItem(), "MEDICAMENTO").toUpperCase());
        item.setMedicamento(safe(dto.getMedicamento()));
        item.setDose(safe(dto.getDose()));
        item.setVia(safe(dto.getVia()));
        item.setFrequencia(safe(dto.getFrequencia()));
        item.setDuracao(safe(dto.getDuracao()));
        item.setObservacao(safeText(dto.getObservacao()));
        if (novo) {
            item.setPrescritoPor(safe(dto.getPrescritoPor()));
            item.setPrescritoPorMatricula(safe(dto.getPrescritoPorMatricula()));
        }
        item.setAtualizadoPor(safe(dto.getAtualizadoPor()));
        item.setAtualizadoPorMatricula(safe(dto.getAtualizadoPorMatricula()));
    }

    private String safeDefault(String value, String fallback) {
        String safe = safe(value);
        return safe.isBlank() ? fallback : safe;
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void validarItemNaoAssinado(PrescricaoItemEstruturado item) {
        if (item == null || item.getIdbam() <= 0) {
            return;
        }
        var assinatura = assinaturaClinicaService.assinaturaAtivaPorBam(
                AssinaturaClinicaService.PRESCRICAO_ESTRUTURADA_BAM,
                item.getIdbam());
        if (assinatura == null || assinatura.getAssinadoEm() == null || item.getCriadoEm() == null) {
            return;
        }
        if (!item.getCriadoEm().isAfter(assinatura.getAssinadoEm())) {
            throw new IllegalStateException("Item ja coberto por prescricao assinada. Use adendo para registrar correcoes.");
        }
    }
}

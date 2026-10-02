package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import br.com.iv.dto.PrescricaoItemCicloEventoDto;
import br.com.iv.entity.PrescricaoItemCicloEvento;
import br.com.iv.entity.PrescricaoItemEstruturado;
import br.com.iv.repository.PrescricaoItemCicloEventoRepository;
import br.com.iv.repository.PrescricaoItemEstruturadoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PrescricaoItemCicloEventoService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    PrescricaoItemCicloEventoRepository repository;

    @Inject
    PrescricaoItemEstruturadoRepository itemRepository;

    public List<PrescricaoItemCicloEvento> listarPorItem(int iditem) {
        return repository.listarPorItem(iditem);
    }

    public List<PrescricaoItemCicloEvento> listarPorBam(int idbam) {
        return repository.listarPorBam(idbam);
    }

    @Transactional
    public PrescricaoItemCicloEvento registrar(int iditem, PrescricaoItemCicloEventoDto dto) {
        PrescricaoItemEstruturado item = itemRepository.findById(iditem);
        if (item == null) {
            return null;
        }

        PrescricaoItemCicloEvento evento = new PrescricaoItemCicloEvento();
        evento.setIditem(iditem);
        evento.setIdbam(item.getIdbam());
        evento.setIdpaciente(item.getIdpaciente());
        evento.setIdprescricao(item.getIdprescricao());
        evento.setUnidade(safeDefault(dto.getUnidade(), item.getUnidade()));
        evento.setTipoEvento(safe(dto.getTipoEvento()).toUpperCase());
        evento.setStatusEvento(safeDefault(dto.getStatusEvento(), "REGISTRADO").toUpperCase());
        evento.setDataHoraPrevista(dto.getDataHoraPrevista());
        evento.setDataHoraRealizada(dto.getDataHoraRealizada() != null ? dto.getDataHoraRealizada() : LocalDateTime.now());
        evento.setQuantidade(dto.getQuantidade());
        evento.setDoseAdministrada(safe(dto.getDoseAdministrada()));
        evento.setResponsavel(safe(dto.getResponsavel()));
        evento.setResponsavelMatricula(safe(dto.getResponsavelMatricula()));
        evento.setObservacao(safeText(dto.getObservacao()));
        evento.persist();
        return evento;
    }

    private String safeDefault(String value, String fallback) {
        String safe = safe(value);
        return safe.isBlank() ? safe(fallback) : safe;
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}

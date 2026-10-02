package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

import br.com.iv.dto.AnotacaoEnfermagemDto;
import br.com.iv.entity.AnotacaoEnfermagem;
import br.com.iv.repository.AnotacaoEnfermagemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AnotacaoEnfermagemService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    AnotacaoEnfermagemRepository repository;

    public List<AnotacaoEnfermagem> listarPorPaciente(int idpaciente) {
        return repository.listarPorPaciente(idpaciente);
    }

    public List<AnotacaoEnfermagem> listarPorBam(int idbam) {
        return repository.listarPorBam(idbam);
    }

    @Transactional
    public AnotacaoEnfermagem salvar(AnotacaoEnfermagemDto dto) {
        AnotacaoEnfermagem item = new AnotacaoEnfermagem();
        item.setIdpaciente(dto.getIdpaciente());
        item.setIdbam(dto.getIdbam());
        item.setProntuario(safe(dto.getProntuario()));
        item.setNomepaciente(safe(dto.getNomepaciente()));
        item.setUnidade(safe(dto.getUnidade()));
        item.setStatus("ATIVA");
        item.setAnotacao(safe(dto.getAnotacao()));
        item.setCriadoPor(safe(dto.getCriadoPor()));
        item.setCriadoPorMatricula(safe(dto.getCriadoPorMatricula()));
        item.setAtualizadoPor(safe(dto.getAtualizadoPor()));
        item.setAtualizadoPorMatricula(safe(dto.getAtualizadoPorMatricula()));
        item.persist();
        return item;
    }

    @Transactional
    public AnotacaoEnfermagem atualizar(int id, AnotacaoEnfermagemDto dto) {
        AnotacaoEnfermagem item = repository.findById(id);
        if (item == null) {
            return null;
        }
        item.setAnotacao(safe(dto.getAnotacao()));
        item.setMotivo(safe(dto.getMotivo()));
        item.setAtualizadoPor(safe(dto.getAtualizadoPor()));
        item.setAtualizadoPorMatricula(safe(dto.getAtualizadoPorMatricula()));
        return item;
    }

    @Transactional
    public boolean inativar(int id, AnotacaoEnfermagemDto dto) {
        AnotacaoEnfermagem item = repository.findById(id);
        if (item == null) {
            return false;
        }
        item.setStatus("INATIVA");
        item.setMotivo(safe(dto.getMotivo()));
        item.setAtualizadoPor(safe(dto.getAtualizadoPor()));
        item.setAtualizadoPorMatricula(safe(dto.getAtualizadoPorMatricula()));
        return true;
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}

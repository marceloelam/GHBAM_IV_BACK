package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import br.com.iv.dto.PrescricaoAprazamentoChecagemDto;
import br.com.iv.entity.Prescricao;
import br.com.iv.entity.PrescricaoAprazamentoChecagem;
import br.com.iv.repository.PrescricaoAprazamentoChecagemRepository;
import br.com.iv.repository.PrescricaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PrescricaoAprazamentoChecagemService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    PrescricaoAprazamentoChecagemRepository repository;

    @Inject
    PrescricaoRepository prescricaoRepository;

    public List<PrescricaoAprazamentoChecagem> listarPorPrescricao(int idprescricao) {
        return repository.listarPorPrescricao(idprescricao);
    }

    @Transactional
    public List<PrescricaoAprazamentoChecagem> salvarHorarios(int idprescricao, List<PrescricaoAprazamentoChecagemDto> horarios) {
        Prescricao prescricao = prescricaoRepository.findById(idprescricao);
        if (prescricao == null) {
            throw new IllegalArgumentException("Prescricao nao localizada.");
        }

        Set<Integer> indicesInformados = new HashSet<>();

        for (PrescricaoAprazamentoChecagemDto dto : horarios) {
            if (dto == null || dto.getIndiceHorario() < 1 || dto.getIndiceHorario() > 8 || vazio(dto.getHorarioAprazado())) {
                continue;
            }

            indicesInformados.add(dto.getIndiceHorario());

            PrescricaoAprazamentoChecagem registro = repository
                    .buscarHorario(idprescricao, dto.getIndiceHorario())
                    .orElseGet(PrescricaoAprazamentoChecagem::new);

            registro.setIdprescricao(idprescricao);
            registro.setIdbam(prescricao.getBam());
            registro.setIndiceHorario(dto.getIndiceHorario());
            registro.setHorarioAprazado(dto.getHorarioAprazado());
            registro.setObservacaoAprazamento(dto.getObservacaoAprazamento());
            registro.setAprazadoPor(dto.getAprazadoPor());
            registro.setAprazadoPorMatricula(dto.getAprazadoPorMatricula());
            if (registro.getAprazadoEm() == null) {
                registro.setAprazadoEm(LocalDateTime.now());
            }

            if (!repository.isPersistent(registro)) {
                repository.persist(registro);
            }
        }

        for (PrescricaoAprazamentoChecagem registro : repository.listarPorPrescricao(idprescricao)) {
            if (!indicesInformados.contains(registro.getIndiceHorario()) && !registro.isChecado()) {
                repository.delete(registro);
            }
        }

        return repository.listarPorPrescricao(idprescricao);
    }

    @Transactional
    public PrescricaoAprazamentoChecagem checarHorario(int idprescricao, int indiceHorario, PrescricaoAprazamentoChecagemDto dto) {
        if (indiceHorario < 1 || indiceHorario > 8) {
            throw new IllegalArgumentException("Horario invalido.");
        }

        PrescricaoAprazamentoChecagem registro = repository
                .buscarHorario(idprescricao, indiceHorario)
                .orElseThrow(() -> new IllegalArgumentException("Horario de aprazamento nao localizado."));

        registro.setChecado(true);
        registro.setChecadoEm(dto != null && dto.getChecadoEm() != null ? dto.getChecadoEm() : LocalDateTime.now());
        registro.setChecadoPor(dto != null ? dto.getChecadoPor() : null);
        registro.setChecadoPorMatricula(dto != null ? dto.getChecadoPorMatricula() : null);
        registro.setObservacaoChecagem(dto != null ? dto.getObservacaoChecagem() : null);

        return registro;
    }

    @Transactional
    public PrescricaoAprazamentoChecagem desfazerChecagem(int idprescricao, int indiceHorario) {
        PrescricaoAprazamentoChecagem registro = repository
                .buscarHorario(idprescricao, indiceHorario)
                .orElseThrow(() -> new IllegalArgumentException("Horario de aprazamento nao localizado."));

        registro.setChecado(false);
        registro.setChecadoEm(null);
        registro.setChecadoPor(null);
        registro.setChecadoPorMatricula(null);
        registro.setObservacaoChecagem(null);

        return registro;
    }

    private boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}

package br.com.iv.service;

import java.io.Serializable;
import java.util.List;

import br.com.iv.dto.PacientesImagensPacsDto;
import br.com.iv.repository.PacsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PacsService implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    PacsRepository pacsRepository;

    public List<PacientesImagensPacsDto> listarPorNumero(int numero, int first, int pageSize) {
        return pacsRepository.listarPorNumero(numero, first, pageSize);
    }

    public List<PacientesImagensPacsDto> listarPorNome(String nome, int first, int pageSize) {
        return pacsRepository.listarPorNome(nome, first, pageSize);
    }

    public List<PacientesImagensPacsDto> listarPorBam(int bam) {
        return pacsRepository.listarPorBam(bam);
    }
}

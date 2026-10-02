package br.com.iv.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.dto.PepModeloAnotacaoDto;
import br.com.iv.entity.PepModeloAnotacao;
import br.com.iv.repository.PepModeloAnotacaoRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class PepModeloAnotacaoService {

    @Inject
    PepModeloAnotacaoRepository repository;

    public List<Map<String, Object>> listar(AuthenticatedUser usuario) {
        String unidade = unidade(usuario);
        String matricula = matricula(usuario);
        return repository.listarPorUnidade(unidade).stream()
                .map(modelo -> toMap(modelo, podeAlterar(modelo, matricula)))
                .toList();
    }

    @Transactional
    public PepModeloAnotacao criar(PepModeloAnotacaoDto dto, AuthenticatedUser usuario) {
        validar(dto);
        PepModeloAnotacao modelo = new PepModeloAnotacao();
        modelo.setUnidade(unidade(usuario));
        modelo.setNome(dto.getNome().trim());
        modelo.setTexto(dto.getTexto().trim());
        modelo.setCriadoPorMatricula(matricula(usuario));
        modelo.setCriadoPorNome(usuario == null ? "" : usuario.nome());
        repository.persist(modelo);
        return modelo;
    }

    @Transactional
    public PepModeloAnotacao atualizar(Long id, PepModeloAnotacaoDto dto, AuthenticatedUser usuario) {
        validar(dto);
        PepModeloAnotacao modelo = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Modelo nao encontrado."));
        validarCriador(modelo, usuario);
        modelo.setNome(dto.getNome().trim());
        modelo.setTexto(dto.getTexto().trim());
        repository.persist(modelo);
        return modelo;
    }

    @Transactional
    public void remover(Long id, AuthenticatedUser usuario) {
        PepModeloAnotacao modelo = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Modelo nao encontrado."));
        validarCriador(modelo, usuario);
        modelo.delete();
    }

    public Map<String, Object> toMap(PepModeloAnotacao modelo, AuthenticatedUser usuario) {
        return toMap(modelo, podeAlterar(modelo, matricula(usuario)));
    }

    private Map<String, Object> toMap(PepModeloAnotacao modelo, boolean podeAlterar) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", modelo.getId());
        map.put("unidade", modelo.getUnidade());
        map.put("nome", modelo.getNome());
        map.put("texto", modelo.getTexto());
        map.put("criadoPorMatricula", modelo.getCriadoPorMatricula());
        map.put("criadoPorNome", modelo.getCriadoPorNome());
        map.put("criadoEm", modelo.getCriadoEm());
        map.put("atualizadoEm", modelo.getAtualizadoEm());
        map.put("podeAlterar", podeAlterar);
        return map;
    }

    private void validar(PepModeloAnotacaoDto dto) {
        if (dto == null || dto.getNome() == null || dto.getNome().trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do modelo obrigatorio.");
        }
        if (dto.getTexto() == null || dto.getTexto().trim().isEmpty()) {
            throw new IllegalArgumentException("Texto do modelo obrigatorio.");
        }
    }

    private void validarCriador(PepModeloAnotacao modelo, AuthenticatedUser usuario) {
        if (!podeAlterar(modelo, matricula(usuario))) {
            throw new ForbiddenException("Somente o criador do modelo pode alterar ou remover.");
        }
    }

    private boolean podeAlterar(PepModeloAnotacao modelo, String matricula) {
        return modelo != null
                && matricula != null
                && !matricula.isBlank()
                && modelo.getCriadoPorMatricula() != null
                && modelo.getCriadoPorMatricula().equals(matricula);
    }

    private String unidade(AuthenticatedUser usuario) {
        return usuario == null ? "geral" : valor(usuario.unidade(), "geral");
    }

    private String matricula(AuthenticatedUser usuario) {
        return usuario == null ? "" : valor(usuario.matricula(), "");
    }

    private String valor(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

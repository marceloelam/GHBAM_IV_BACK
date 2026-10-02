package br.com.iv.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.dto.PepAnotacaoRascunhoDto;
import br.com.iv.entity.PepAnotacaoRascunho;
import br.com.iv.repository.PepAnotacaoRascunhoRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class PepAnotacaoRascunhoService {

    private static final Duration PRAZO_EDICAO = Duration.ofHours(12);

    @Inject
    PepAnotacaoRascunhoRepository repository;

    public List<Map<String, Object>> listarPorBam(Integer idbam, AuthenticatedUser usuario) {
        String matricula = matricula(usuario);
        return repository.listarPorBam(idbam).stream()
                .map(item -> toMap(item, podeAlterar(item, matricula, LocalDateTime.now())))
                .toList();
    }

    @Transactional
    public PepAnotacaoRascunho salvar(PepAnotacaoRascunhoDto dto, AuthenticatedUser usuario) {
        if (dto == null || dto.getIdbam() == null) {
            throw new IllegalArgumentException("BAM obrigatorio.");
        }
        if (dto.getDescricao() == null || dto.getDescricao().trim().isEmpty()) {
            throw new IllegalArgumentException("Descricao obrigatoria.");
        }

        String matricula = matricula(usuario);
        PepAnotacaoRascunho rascunho = repository.buscarAtivoDoUsuario(dto.getIdbam(), matricula)
                .filter(item -> podeAlterar(item, matricula, LocalDateTime.now()))
                .orElseGet(PepAnotacaoRascunho::new);

        if (rascunho.getId() == null) {
            rascunho.setIdbam(dto.getIdbam());
            rascunho.setCriadoPorMatricula(matricula);
            rascunho.setCriadoPorNome(usuario == null ? "" : usuario.nome());
            rascunho.setCriadoPorCargo(usuario == null ? "" : usuario.cargo());
            rascunho.setCriadoPorConselho(conselho(usuario));
        }

        rascunho.setUnidade(valor(dto.getUnidade(), usuario == null ? "" : usuario.unidade()));
        rascunho.setDescricao(dto.getDescricao().trim());
        rascunho.setAtualizadoPorMatricula(matricula);
        repository.persist(rascunho);
        return rascunho;
    }

    @Transactional
    public PepAnotacaoRascunho atualizar(Long id, PepAnotacaoRascunhoDto dto, AuthenticatedUser usuario) {
        PepAnotacaoRascunho rascunho = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Rascunho nao encontrado."));
        validarAlteracao(rascunho, usuario);
        if (dto == null || dto.getDescricao() == null || dto.getDescricao().trim().isEmpty()) {
            throw new IllegalArgumentException("Descricao obrigatoria.");
        }
        rascunho.setDescricao(dto.getDescricao().trim());
        rascunho.setUnidade(valor(dto.getUnidade(), rascunho.getUnidade()));
        rascunho.setAtualizadoPorMatricula(matricula(usuario));
        repository.persist(rascunho);
        return rascunho;
    }

    @Transactional
    public void remover(Long id, AuthenticatedUser usuario) {
        PepAnotacaoRascunho rascunho = repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Rascunho nao encontrado."));
        validarAlteracao(rascunho, usuario);
        rascunho.delete();
    }

    public Map<String, Object> toMap(PepAnotacaoRascunho rascunho, AuthenticatedUser usuario) {
        return toMap(rascunho, podeAlterar(rascunho, matricula(usuario), LocalDateTime.now()));
    }

    private Map<String, Object> toMap(PepAnotacaoRascunho rascunho, boolean podeAlterar) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rascunho.getId());
        map.put("idbam", rascunho.getIdbam());
        map.put("unidade", rascunho.getUnidade());
        map.put("descricao", rascunho.getDescricao());
        map.put("criadoPorMatricula", rascunho.getCriadoPorMatricula());
        map.put("criadoPorNome", rascunho.getCriadoPorNome());
        map.put("criadoPorCargo", rascunho.getCriadoPorCargo());
        map.put("criadoPorConselho", rascunho.getCriadoPorConselho());
        map.put("criadoEm", rascunho.getCriadoEm());
        map.put("atualizadoEm", rascunho.getAtualizadoEm());
        map.put("podeAlterar", podeAlterar);
        return map;
    }

    private void validarAlteracao(PepAnotacaoRascunho rascunho, AuthenticatedUser usuario) {
        if (!podeAlterar(rascunho, matricula(usuario), LocalDateTime.now())) {
            throw new ForbiddenException("Somente o criador pode alterar ou remover no mesmo dia, ate 12 horas apos a criacao.");
        }
    }

    private boolean podeAlterar(PepAnotacaoRascunho rascunho, String matricula, LocalDateTime agora) {
        if (rascunho == null || matricula == null || matricula.isBlank()) return false;
        if (rascunho.getCriadoPorMatricula() == null || !rascunho.getCriadoPorMatricula().equals(matricula)) return false;
        if (rascunho.getCriadoEm() == null) return false;
        if (!rascunho.getCriadoEm().toLocalDate().equals(LocalDate.now())) return false;
        return !rascunho.getCriadoEm().plus(PRAZO_EDICAO).isBefore(agora);
    }

    private String matricula(AuthenticatedUser usuario) {
        return usuario == null ? "" : valor(usuario.matricula(), "");
    }

    private String conselho(AuthenticatedUser usuario) {
        if (usuario == null) return "";
        String corem = valor(usuario.corem(), "");
        if (!corem.isBlank()) return "COREN " + corem;
        String crm = valor(usuario.crm(), "");
        if (!crm.isBlank()) return "CRM " + crm;
        String cns = valor(usuario.cns(), "");
        if (!cns.isBlank()) return "CNS " + cns;
        return "";
    }

    private String valor(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

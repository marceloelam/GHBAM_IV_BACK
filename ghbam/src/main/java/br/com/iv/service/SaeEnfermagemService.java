package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.dto.SaeEnfermagemDto;
import br.com.iv.entity.SaeEnfermagem;
import br.com.iv.repository.SaeEnfermagemRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SaeEnfermagemService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    SaeEnfermagemRepository repository;

    @Inject
    ObjectMapper mapper;

    public SaeEnfermagemDto buscar(Integer idbam, Integer idpaciente, LocalDate data, String tipoSae) {
        return toDto(repository.buscarRegistro(idbam, idpaciente, data, tipoSae(tipoSae)));
    }

    public List<SaeEnfermagemDto> listarHistorico(Integer idbam, Integer idpaciente) {
        return repository.listarHistorico(idbam, idpaciente).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ResultadoSalvar salvar(SaeEnfermagemDto dto, AuthenticatedUser usuario) {
        if (dto == null || dto.getPaciente() == null || dto.getPaciente().isNull()) {
            throw new IllegalArgumentException("Paciente obrigatorio");
        }
        String uuid = safe(dto.getUuid());
        SaeEnfermagem item = uuid.isBlank() ? null : repository.buscarPorUuid(uuid);
        JsonNode paciente = dto.getPaciente();
        Integer idPaciente = intNode(paciente, "idpaciente", "idPaciente", "pacienteId");
        Integer idBam = intNode(paciente, "bam", "idbam", "nbam");
        if ((idPaciente == null || idPaciente <= 0) && (idBam == null || idBam <= 0)) {
            throw new IllegalArgumentException("Informe identificador valido do paciente ou BAM");
        }
        boolean criado = item == null;
        if (item == null) {
            uuid = UUID.randomUUID().toString();
            item = new SaeEnfermagem();
            item.setUuid(uuid);
        } else {
            if (!mesmoPaciente(item, idPaciente, idBam)) {
                throw new IllegalStateException("UUID da SAE nao pertence ao paciente informado");
            }
            if ("CONCLUIDA".equals(item.getStatus())) {
                throw new IllegalStateException("SAE concluida nao pode ser alterada; crie uma retificacao formal");
            }
        }

        item.setStatus(status(dto.getStatus()));
        item.setTipoSae(tipoSae(dto.getTipoSae()));
        item.setData(dto.getData() == null ? LocalDate.now() : dto.getData());
        item.setIdpaciente(idPaciente);
        item.setIdbam(idBam);
        item.setProntuario(textNode(paciente, "prontuario", "nprontuario"));
        item.setNomepaciente(textNode(paciente, "nome", "paciente", "nomepaciente"));
        item.setUnidade(safe(usuario.unidade()));
        item.setPacienteJson(toJson(dto.getPaciente(), "{}"));
        item.setHistoricoJson(toJson(dto.getHistorico(), "{}"));
        item.setDiagnosticosJson(toJson(dto.getDiagnosticos(), "[]"));
        item.setCuidadosJson(toJson(dto.getCuidados(), "[]"));
        item.setPrescricoesJson(toJson(dto.getPrescricoes(), "[]"));
        item.setEvolucoesJson(toJson(dto.getEvolucoes(), "[]"));
        if (criado) {
            item.setCriadoPor(safe(usuario.nome()));
            item.setCriadoPorMatricula(safe(usuario.matricula()));
        }
        item.setAtualizadoPor(safe(usuario.nome()));
        item.setAtualizadoPorMatricula(safe(usuario.matricula()));
        item.persist();

        return new ResultadoSalvar(toDto(item), criado);
    }

    public record ResultadoSalvar(SaeEnfermagemDto sae, boolean criado) { }

    private boolean mesmoPaciente(SaeEnfermagem item, Integer idPaciente, Integer idBam) {
        boolean pacienteDivergente = idPaciente != null && item.getIdpaciente() != null
                && !idPaciente.equals(item.getIdpaciente());
        boolean bamDivergente = idBam != null && item.getIdbam() != null
                && !idBam.equals(item.getIdbam());
        if (pacienteDivergente || bamDivergente) {
            return false;
        }
        return (idPaciente != null && idPaciente.equals(item.getIdpaciente()))
                || (idBam != null && idBam.equals(item.getIdbam()));
    }

    private SaeEnfermagemDto toDto(SaeEnfermagem item) {
        if (item == null) {
            return null;
        }
        SaeEnfermagemDto dto = new SaeEnfermagemDto();
        dto.setId(item.getId());
        dto.setUuid(item.getUuid());
        dto.setStatus(item.getStatus());
        dto.setTipoSae(item.getTipoSae());
        dto.setVersao(item.getVersao());
        dto.setUuidOrigem(item.getUuidOrigem());
        dto.setMotivoRetificacao(item.getMotivoRetificacao());
        dto.setData(item.getData());
        dto.setPaciente(fromJson(item.getPacienteJson(), "{}"));
        dto.setHistorico(fromJson(item.getHistoricoJson(), "{}"));
        dto.setDiagnosticos(fromJson(item.getDiagnosticosJson(), "[]"));
        dto.setCuidados(fromJson(item.getCuidadosJson(), "[]"));
        dto.setPrescricoes(fromJson(item.getPrescricoesJson(), "[]"));
        dto.setEvolucoes(fromJson(item.getEvolucoesJson(), "[]"));
        dto.setCriadoPor(mapper.createObjectNode()
                .put("nome", safe(item.getCriadoPor()))
                .put("matricula", safe(item.getCriadoPorMatricula())));
        dto.setAtualizadoPor(mapper.createObjectNode()
                .put("nome", safe(item.getAtualizadoPor()))
                .put("matricula", safe(item.getAtualizadoPorMatricula())));
        dto.setCriadoEm(item.getCriadoEm());
        dto.setAtualizadoEm(item.getAtualizadoEm());
        dto.setGravado(true);
        return dto;
    }

    @Transactional
    public SaeEnfermagemDto retificar(String uuid, String motivo, AuthenticatedUser usuario) {
        SaeEnfermagem original = repository.buscarPorUuid(uuid);
        if (original == null) {
            throw new IllegalArgumentException("SAE original nao encontrada");
        }
        if (!"CONCLUIDA".equals(original.getStatus())) {
            throw new IllegalStateException("Somente SAE concluida pode ser retificada");
        }
        String motivoSeguro = safe(motivo);
        if (motivoSeguro.isBlank()) {
            throw new IllegalArgumentException("Motivo da retificacao obrigatorio");
        }

        SaeEnfermagem nova = new SaeEnfermagem();
        nova.setUuid(UUID.randomUUID().toString());
        nova.setUuidOrigem(original.getUuid());
        nova.setMotivoRetificacao(motivoSeguro);
        nova.setVersao((original.getVersao() == null ? 1 : original.getVersao()) + 1);
        nova.setStatus("EM_ANDAMENTO");
        nova.setTipoSae(original.getTipoSae());
        nova.setData(original.getData());
        nova.setIdpaciente(original.getIdpaciente());
        nova.setIdbam(original.getIdbam());
        nova.setProntuario(original.getProntuario());
        nova.setNomepaciente(original.getNomepaciente());
        nova.setUnidade(safe(usuario.unidade()));
        nova.setPacienteJson(original.getPacienteJson());
        nova.setHistoricoJson(original.getHistoricoJson());
        nova.setDiagnosticosJson(original.getDiagnosticosJson());
        nova.setCuidadosJson(original.getCuidadosJson());
        nova.setPrescricoesJson(original.getPrescricoesJson());
        nova.setEvolucoesJson(original.getEvolucoesJson());
        nova.setCriadoPor(safe(usuario.nome()));
        nova.setCriadoPorMatricula(safe(usuario.matricula()));
        nova.setAtualizadoPor(safe(usuario.nome()));
        nova.setAtualizadoPorMatricula(safe(usuario.matricula()));
        nova.persist();
        return toDto(nova);
    }

    private String toJson(JsonNode node, String fallback) {
        return node == null || node.isNull() ? fallback : node.toString();
    }

    private JsonNode fromJson(String json, String fallback) {
        try {
            return mapper.readTree(safe(json).isBlank() ? fallback : json);
        } catch (Exception e) {
            try {
                return mapper.readTree(fallback);
            } catch (Exception ignored) {
                return mapper.createObjectNode();
            }
        }
    }

    private String status(String value) {
        String normalized = safe(value);
        return normalized.isBlank() ? "EM_ANDAMENTO" : normalized;
    }

    private String tipoSae(String value) {
        String tipo = safe(value).toUpperCase();
        return switch (tipo) {
            case "PEDIATRICA", "CTI", "GERAL" -> tipo;
            default -> "GERAL";
        };
    }

    private String textNode(JsonNode node, String... names) {
        if (node == null || node.isNull()) {
            return "";
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return safe(value.asText());
            }
        }
        return "";
    }

    private Integer intNode(JsonNode node, String... names) {
        String value = textNode(node, names).replaceAll("\\D+", "");
        if (value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }
}

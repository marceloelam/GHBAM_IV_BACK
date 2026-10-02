package br.com.iv.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.entity.ControleAssistencialRegistro;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ControleAssistencialService {

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper mapper;

    @Transactional
    public ControleAssistencialRegistro salvar(JsonNode dto, AuthenticatedUser usuario) {
        if (dto == null || dto.isNull()) {
            throw new IllegalArgumentException("Dados do formulario obrigatorios.");
        }

        String tipo = normalizarTipo(texto(dto, "tipo"));
        String responsavel = primeiroTexto(texto(dto, "responsavel"), usuario.nome());
        String matricula = primeiroTexto(texto(dto, "responsavelMatricula"), usuario.matricula());
        String conselho = primeiroTexto(texto(dto, "conselho"), conselhoPadrao(usuario));
        String numeroConselho = primeiroTexto(texto(dto, "numeroConselho"), usuario.corem(), usuario.crm());

        if (responsavel.isBlank() || matricula.isBlank()) {
            throw new IllegalArgumentException("Responsavel e matricula sao obrigatorios.");
        }
        if (conselho.isBlank() || numeroConselho.isBlank()) {
            throw new IllegalArgumentException("Conselho profissional e numero do registro sao obrigatorios para validade do formulario.");
        }

        ControleAssistencialRegistro registro = new ControleAssistencialRegistro();
        registro.setUuid(UUID.randomUUID().toString());
        registro.setTipo(tipo);
        registro.setStatus(normalizarStatus(texto(dto, "status")));
        registro.setUnidade(primeiroTexto(texto(dto, "unidade"), usuario.unidade()));
        registro.setSetor(texto(dto, "setor"));
        registro.setDataRegistro(dataOuHoje(texto(dto, "dataRegistro")));
        registro.setTurno(texto(dto, "turno"));
        registro.setResponsavel(responsavel);
        registro.setResponsavelMatricula(matricula);
        registro.setResponsavelCargo(primeiroTexto(texto(dto, "responsavelCargo"), usuario.cargo()));
        registro.setConselho(conselho.toUpperCase());
        registro.setNumeroConselho(numeroConselho);
        registro.setIdbam(inteiro(dto, "idbam"));
        registro.setIdpaciente(inteiro(dto, "idpaciente"));
        registro.setPaciente(texto(dto, "paciente"));
        registro.setObservacao(texto(dto, "observacao"));
        registro.setDadosJson(json(dto.path("dados")));
        registro.persist();
        return registro;
    }

    @Transactional
    public ControleAssistencialRegistro atualizar(String uuid, JsonNode dto, AuthenticatedUser usuario) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("Identificador do registro obrigatorio.");
        }
        if (dto == null || dto.isNull()) {
            throw new IllegalArgumentException("Dados do formulario obrigatorios.");
        }

        ControleAssistencialRegistro registro = ControleAssistencialRegistro.find("uuid", uuid).firstResult();
        if (registro == null) {
            return null;
        }
        if ("CANCELADO".equalsIgnoreCase(registro.getStatus())) {
            throw new IllegalArgumentException("Registro cancelado nao pode ser editado.");
        }

        preencherRegistro(registro, dto, usuario);
        registro.persist();
        return registro;
    }

    @Transactional
    public ControleAssistencialRegistro cancelar(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("Identificador do registro obrigatorio.");
        }
        ControleAssistencialRegistro registro = ControleAssistencialRegistro.find("uuid", uuid).firstResult();
        if (registro == null) {
            return null;
        }
        registro.setStatus("CANCELADO");
        registro.persist();
        return registro;
    }

    private void preencherRegistro(ControleAssistencialRegistro registro, JsonNode dto, AuthenticatedUser usuario) {
        String tipo = normalizarTipo(texto(dto, "tipo"));
        String responsavel = primeiroTexto(texto(dto, "responsavel"), usuario.nome());
        String matricula = primeiroTexto(texto(dto, "responsavelMatricula"), usuario.matricula());
        String conselho = primeiroTexto(texto(dto, "conselho"), conselhoPadrao(usuario));
        String numeroConselho = primeiroTexto(texto(dto, "numeroConselho"), usuario.corem(), usuario.crm());

        if (responsavel.isBlank() || matricula.isBlank()) {
            throw new IllegalArgumentException("Responsavel e matricula sao obrigatorios.");
        }
        if (conselho.isBlank() || numeroConselho.isBlank()) {
            throw new IllegalArgumentException("Conselho profissional e numero do registro sao obrigatorios para validade do formulario.");
        }

        registro.setTipo(tipo);
        registro.setStatus(normalizarStatus(texto(dto, "status")));
        registro.setUnidade(primeiroTexto(texto(dto, "unidade"), usuario.unidade()));
        registro.setSetor(texto(dto, "setor"));
        registro.setDataRegistro(dataOuHoje(texto(dto, "dataRegistro")));
        registro.setTurno(texto(dto, "turno"));
        registro.setResponsavel(responsavel);
        registro.setResponsavelMatricula(matricula);
        registro.setResponsavelCargo(primeiroTexto(texto(dto, "responsavelCargo"), usuario.cargo()));
        registro.setConselho(conselho.toUpperCase());
        registro.setNumeroConselho(numeroConselho);
        registro.setIdbam(inteiro(dto, "idbam"));
        registro.setIdpaciente(inteiro(dto, "idpaciente"));
        registro.setPaciente(texto(dto, "paciente"));
        registro.setObservacao(texto(dto, "observacao"));
        registro.setDadosJson(json(dto.path("dados")));
    }

    public List<Map<String, Object>> listar(String unidade, String tipo, String status, LocalDate inicio, LocalDate fim, int limite) {
        LocalDate dataInicio = inicio == null ? LocalDate.now().minusDays(7) : inicio;
        LocalDate dataFim = fim == null ? LocalDate.now() : fim;
        if (dataFim.isBefore(dataInicio)) {
            LocalDate troca = dataInicio;
            dataInicio = dataFim;
            dataFim = troca;
        }

        String filtroUnidade = unidade == null ? "" : unidade.split(":")[0].trim();
        String filtroTipo = normalizarFiltro(tipo);
        String filtroStatus = normalizarFiltro(status);

        StringBuilder jpql = new StringBuilder("""
                from ControleAssistencialRegistro r
                where r.dataRegistro between :inicio and :fim
                """);
        if (!filtroUnidade.isBlank() && !"TODAS".equalsIgnoreCase(filtroUnidade)) {
            jpql.append(" and upper(coalesce(r.unidade, '')) = upper(:unidade)");
        }
        if (!filtroTipo.isBlank() && !"TODOS".equalsIgnoreCase(filtroTipo)) {
            jpql.append(" and r.tipo = :tipo");
        }
        if (!filtroStatus.isBlank() && !"TODOS".equalsIgnoreCase(filtroStatus)) {
            jpql.append(" and r.status = :status");
        }
        jpql.append(" order by r.dataRegistro desc, r.criadoEm desc");

        var query = entityManager.createQuery(jpql.toString(), ControleAssistencialRegistro.class)
                .setParameter("inicio", dataInicio)
                .setParameter("fim", dataFim)
                .setMaxResults(Math.max(1, Math.min(limite <= 0 ? 80 : limite, 300)));
        if (!filtroUnidade.isBlank() && !"TODAS".equalsIgnoreCase(filtroUnidade)) {
            query.setParameter("unidade", filtroUnidade);
        }
        if (!filtroTipo.isBlank() && !"TODOS".equalsIgnoreCase(filtroTipo)) {
            query.setParameter("tipo", filtroTipo);
        }
        if (!filtroStatus.isBlank() && !"TODOS".equalsIgnoreCase(filtroStatus)) {
            query.setParameter("status", filtroStatus);
        }

        return query.getResultList().stream().map(this::toMap).toList();
    }

    public Map<String, Object> buscarPorUuid(String uuid) {
        ControleAssistencialRegistro registro = ControleAssistencialRegistro.find("uuid", uuid).firstResult();
        return registro == null ? null : toMap(registro);
    }

    public List<String> listarSetoresDaUnidade(String unidade) {
        String filtroUnidade = unidade == null ? "" : unidade.split(":")[0].trim();
        if (filtroUnidade.isBlank()) {
            return List.of();
        }

        return entityManager.createQuery("""
                select distinct s.setor
                from Setores s
                where upper(coalesce(s.siglaunidade, '')) = upper(:unidade)
                  and s.setor is not null
                  and trim(s.setor) <> ''
                order by s.setor
                """, String.class)
                .setParameter("unidade", filtroUnidade)
                .getResultStream()
                .map(String::trim)
                .distinct()
                .toList();
    }

    public Map<String, Object> toMap(ControleAssistencialRegistro r) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", r.getId());
        item.put("uuid", r.getUuid());
        item.put("tipo", r.getTipo());
        item.put("status", r.getStatus());
        item.put("unidade", r.getUnidade());
        item.put("setor", r.getSetor());
        item.put("dataRegistro", r.getDataRegistro());
        item.put("turno", r.getTurno());
        item.put("responsavel", r.getResponsavel());
        item.put("responsavelMatricula", r.getResponsavelMatricula());
        item.put("responsavelCargo", r.getResponsavelCargo());
        item.put("conselho", r.getConselho());
        item.put("numeroConselho", r.getNumeroConselho());
        item.put("idbam", r.getIdbam());
        item.put("idpaciente", r.getIdpaciente());
        item.put("paciente", r.getPaciente());
        item.put("observacao", r.getObservacao());
        item.put("dados", parseJson(r.getDadosJson()));
        item.put("criadoEm", r.getCriadoEm());
        item.put("atualizadoEm", r.getAtualizadoEm());
        return item;
    }

    private JsonNode parseJson(String json) {
        try {
            return json == null || json.isBlank() ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private String json(JsonNode node) {
        try {
            return node == null || node.isMissingNode() || node.isNull() ? "{}" : mapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String texto(JsonNode node, String campo) {
        JsonNode value = node == null ? null : node.get(campo);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private Integer inteiro(JsonNode node, String campo) {
        JsonNode value = node == null ? null : node.get(campo);
        if (value == null || value.isNull() || value.asText("").isBlank()) return null;
        try {
            return value.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate dataOuHoje(String value) {
        try {
            return value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value.substring(0, 10));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private String normalizarTipo(String tipo) {
        String t = tipo == null ? "" : tipo.trim().toUpperCase();
        return switch (t) {
            case "CARRO_PARADA", "PSICOTROPICO", "INVENTARIO", "EVENTO_ADVERSO" -> t;
            default -> "CHECKLIST_SETOR";
        };
    }

    private String normalizarStatus(String status) {
        String s = status == null ? "" : status.trim().toUpperCase();
        return switch (s) {
            case "PENDENTE", "EM_ANALISE", "CONCLUIDO", "CANCELADO" -> s;
            default -> "REGISTRADO";
        };
    }

    private String normalizarFiltro(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String primeiroTexto(String... valores) {
        if (valores == null) return "";
        for (String valor : valores) {
            if (valor != null && !valor.trim().isBlank()) return valor.trim();
        }
        return "";
    }

    private String conselhoPadrao(AuthenticatedUser usuario) {
        String cargo = usuario == null ? "" : String.valueOf(usuario.cargo()).toUpperCase();
        if (cargo.contains("MEDICO")) return "CRM";
        if (cargo.contains("FARMACEUT")) return "CRF";
        if (cargo.contains("ENFERM") || cargo.contains("TEC")) return "COREN";
        return "REGISTRO";
    }
}

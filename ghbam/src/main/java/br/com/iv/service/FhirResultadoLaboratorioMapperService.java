package br.com.iv.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import br.com.iv.entity.Bam;
import br.com.iv.entity.PedidoResultadoLaboratorioItem;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.PedidoResultadoLaboratorio;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Prepara resultados laboratoriais no formato FHIR R4.
 *
 * Este componente somente mapeia dados locais; o envio para a RNDS depende de
 * homologação do perfil e de uma operação protegida por RBAC/auditoria.
 */
@ApplicationScoped
public class FhirResultadoLaboratorioMapperService {

    private static final String PERFIL_DIAGNOSTIC_REPORT =
            "https://rnds-fhir.saude.gov.br/StructureDefinition/BRDiagnosticoLaboratorioClinico-3.2.1";
    private static final String PERFIL_RESULTADO_LABORATORIAL =
            "https://rnds-fhir.saude.gov.br/StructureDefinition/BRResultadoExameLaboratorial-3.2.1";

    @Inject
    PedidoResultadoLaboratorioService laboratorioService;

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    // Substituivel em teste (sem banco).
    Function<Integer, List<PedidoResultadoLaboratorioItem>> carregarItens = PedidoResultadoLaboratorioItem::doResultado;

    public Map<String, Object> montarBundle(int idbam) {
        Bam bam = Bam.<Bam>findById(idbam);
        List<PedidoResultadoLaboratorio> resultados = laboratorioService
                .filtrarBamPedidoResultadoLaboratorio(idbam);
        return montarBundle(idbam, bam, resultados);
    }

    /** Documento de um único resultado, para envio à RNDS sem reenviar os demais do BAM. */
    public Map<String, Object> montarBundleResultado(PedidoResultadoLaboratorio resultado) {
        Bam bam = Bam.<Bam>findById(resultado.getBam());
        Map<String, Object> bundle = montarBundle(resultado.getBam(), bam, List.of(resultado));
        bundle.put("identifier", Map.of("system", "urn:ghbam:fhir:laboratorio",
                "value", "resultado-" + resultado.getIdPedidoResultado()));
        return bundle;
    }

    Map<String, Object> montarBundle(int idbam, Bam bam, List<PedidoResultadoLaboratorio> resultados) {
        Pacientes paciente = bam == null ? null : bam.getPaciente();
        String patientId = "patient-" + idbam;
        String encounterId = "encounter-bam-" + idbam;
        String unidade = bam == null ? "" : bam.getUnidade();
        ConfiguracaoUnidade configuracao = buscarConfiguracao(unidade);
        String organizationId = "organization-" + normalizarId(unidade);
        List<Map<String, Object>> entries = new ArrayList<>();

        if (paciente != null) {
            entries.add(entrada("Patient/" + patientId, patient(paciente, patientId)));
        }
        if (bam != null) {
            entries.add(entrada("Encounter/" + encounterId, encounter(bam, encounterId, patientId)));
        }
        if (configuracao != null && preenchido(configuracao.getCnesDoHospital())) {
            entries.add(entrada("Organization/" + organizationId,
                    organization(configuracao, organizationId)));
        }

        for (PedidoResultadoLaboratorio resultado : resultados == null ? List.<PedidoResultadoLaboratorio>of() : resultados) {
            if (resultado == null) continue;
            String observationId = "observation-" + resultado.getIdPedidoResultado();
            String specimenId = "specimen-" + resultado.getIdPedidoResultado();
            String specimenRef = preenchido(resultado.getAmostraBiologica()) ? specimenId : null;
            String orgRef = configuracao == null ? null : organizationId;
            if (specimenRef != null) {
                entries.add(entrada("Specimen/" + specimenId, specimen(resultado, specimenId, patientId)));
            }
            List<PedidoResultadoLaboratorioItem> itens = carregarItens.apply(resultado.getIdPedidoResultado());
            if (itens == null || itens.isEmpty()) {
                entries.add(entrada("Observation/" + observationId,
                        observation(resultado, null, observationId, patientId, encounterId, unidade, orgRef, specimenRef)));
            } else {
                // Um Observation por exame do laudo, todos da mesma amostra.
                for (int i = 0; i < itens.size(); i++) {
                    String itemId = observationId + "-" + (i + 1);
                    entries.add(entrada("Observation/" + itemId,
                            observation(resultado, itens.get(i), itemId, patientId, encounterId, unidade, orgRef, specimenRef)));
                }
            }
        }

        entries.add(0, entrada("Composition/composition-bam-" + idbam,
                composition(idbam, paciente, patientId, entries,
                        configuracao == null ? null : organizationId)));

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "document");
        bundle.put("identifier", Map.of("system", "urn:ghbam:fhir:laboratorio", "value", "bam-" + idbam));
        bundle.put("timestamp", FhirDataHora.agora());
        bundle.put("entry", entries);
        return bundle;
    }

    private Map<String, Object> patient(Pacientes paciente, String id) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Patient");
        resource.put("id", id);
        List<Map<String, Object>> identifiers = new ArrayList<>();
        if (preenchido(paciente.getCpf())) {
            identifiers.add(Map.of("system", "urn:oid:2.16.840.1.113883.13.237", "value", paciente.getCpf().trim()));
        }
        if (preenchido(paciente.getCns())) {
            identifiers.add(Map.of("system", "urn:oid:2.16.840.1.113883.13.236", "value", paciente.getCns().trim()));
        }
        resource.put("identifier", identifiers);
        resource.put("name", List.of(Map.of("text", texto(paciente.getNome()))));
        return resource;
    }

    private Map<String, Object> encounter(Bam bam, String id, String patientId) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Encounter");
        resource.put("id", id);
        resource.put("status", bam.isDarAlta() ? "finished" : "in-progress");
        resource.put("subject", Map.of("reference", "Patient/" + patientId));
        return resource;
    }

    private Map<String, Object> composition(int idbam, Pacientes paciente, String patientId,
            List<Map<String, Object>> entries, String organizationId) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Composition");
        resource.put("id", "composition-bam-" + idbam);
        resource.put("meta", Map.of("profile", List.of(PERFIL_RESULTADO_LABORATORIAL)));
        resource.put("status", "final");
        resource.put("type", Map.of("coding", List.of(Map.of(
                "system", "https://rnds-fhir.saude.gov.br/CodeSystem/BRTipoDocumento",
                "code", "REL",
                "display", "Resultado de Exame Laboratorial"))));
        resource.put("subject", Map.of("reference", "Patient/" + patientId));
        resource.put("date", FhirDataHora.agora());
        resource.put("title", "Resultado de Exame Laboratorial");
        List<Map<String, Object>> referencias = entries.stream()
                .map(entry -> (Map<String, Object>) entry.get("resource"))
                .filter(resourceEntry -> "Observation".equals(resourceEntry.get("resourceType")))
                .map(resourceEntry -> {
                    Map<String, Object> referencia = new LinkedHashMap<>();
                    referencia.put("reference", "Observation/" + resourceEntry.get("id"));
                    return referencia;
                })
                .toList();
        resource.put("section", List.of(Map.of(
                "title", "Resultados laboratoriais",
                "code", Map.of("text", "Resultados de exames laboratoriais"),
                "entry", referencias)));
        resource.put("author", organizationId == null
                ? List.of(Map.of("display", "GHBAM"))
                : List.of(Map.of("reference", "Organization/" + organizationId)));
        return resource;
    }

    // item != null: um exame do laudo (V189); item == null: campos unicos do resultado (V188).
    private Map<String, Object> observation(PedidoResultadoLaboratorio resultado, PedidoResultadoLaboratorioItem item,
            String id, String patientId, String encounterId, String unidade, String organizationId, String specimenId) {
        String nome = item != null ? item.getNomeExame() : resultado.getDescricao();
        String loinc = item != null ? item.getCodigoLoinc() : resultado.getCodigoLoinc();
        String valor = item != null ? item.getValor() : resultado.getValorEstruturado();
        String unidadeMedida = item != null ? item.getUnidadeMedida() : resultado.getUnidadeMedida();
        String referencia = item != null ? item.getReferenciaValor() : resultado.getReferenciaValor();
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Observation");
        resource.put("id", id);
        resource.put("meta", Map.of("profile", List.of(PERFIL_DIAGNOSTIC_REPORT)));
        resource.put("status", "final");
        resource.put("category", List.of(Map.of("text", "Exame laboratorial")));
        Map<String, Object> code = new LinkedHashMap<>();
        code.put("text", texto(nome));
        if (preenchido(loinc)) {
            code.put("coding", List.of(Map.of("system", "http://loinc.org", "code", loinc.trim())));
        }
        resource.put("code", code);
        resource.put("subject", Map.of("reference", "Patient/" + patientId));
        String quando = dataHora(resultado) == null ? FhirDataHora.agora() : dataHora(resultado);
        resource.put("effectiveDateTime", quando);
        resource.put("issued", quando);
        resource.put("performer", organizationId == null
                ? List.of(Map.of("display", texto(unidade)))
                : List.of(Map.of("reference", "Organization/" + organizationId)));
        if (preenchido(valor)) {
            resource.putAll(valor(valor, unidadeMedida));
        } else if (item == null && preenchido(resultado.getJustificativaAusenciaValor())) {
            resource.put("dataAbsentReason", Map.of("text", resultado.getJustificativaAusenciaValor().trim()));
        }
        if (preenchido(referencia)) {
            resource.put("referenceRange", List.of(Map.of("text", referencia.trim())));
        }
        if (specimenId != null) {
            resource.put("specimen", Map.of("reference", "Specimen/" + specimenId));
        }
        if (preenchido(resultado.getGrupopedidos())) {
            resource.put("note", List.of(Map.of("text", resultado.getGrupopedidos().trim())));
        }
        return resource;
    }

    private Map<String, Object> specimen(PedidoResultadoLaboratorio resultado, String id, String patientId) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Specimen");
        resource.put("id", id);
        resource.put("status", "available");
        resource.put("subject", Map.of("reference", "Patient/" + patientId));
        resource.put("type", Map.of("text", resultado.getAmostraBiologica().trim()));
        return resource;
    }

    // Chave direto no Observation (valueQuantity/valueString), como o FHIR R4 exige.
    static Map<String, Object> valor(String valorInformado, String unidadeMedida) {
        String valor = valorInformado.trim().replace(',', '.');
        try {
            Map<String, Object> quantity = new LinkedHashMap<>();
            quantity.put("value", new BigDecimal(valor));
            if (preenchido(unidadeMedida)) quantity.put("unit", unidadeMedida.trim());
            return Map.of("valueQuantity", quantity);
        } catch (NumberFormatException ex) {
            return Map.of("valueString", valorInformado.trim());
        }
    }

    private Map<String, Object> organization(ConfiguracaoUnidade configuracao, String id) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Organization");
        resource.put("id", id);
        resource.put("identifier", List.of(Map.of(
                "system", "https://rnds-fhir.saude.gov.br/StructureDefinition/BREstabelecimentoSaude-1.0",
                "value", configuracao.getCnesDoHospital().trim())));
        resource.put("name", texto(configuracao.getHospital()));
        resource.put("active", true);
        return resource;
    }

    private ConfiguracaoUnidade buscarConfiguracao(String unidade) {
        if (configuracaoUnidadeRepository == null || !preenchido(unidade)) return null;
        return configuracaoUnidadeRepository
                .find("upper(sigla) = upper(?1)", unidade.trim())
                .firstResult();
    }

    private String normalizarId(String valor) {
        if (!preenchido(valor)) return "unidade";
        return valor.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    private Map<String, Object> entrada(String fullUrl, Map<String, Object> resource) {
        return Map.of("fullUrl", "urn:ghbam:" + fullUrl, "resource", resource);
    }

    private String dataHora(PedidoResultadoLaboratorio resultado) {
        try {
            String dataInformada = preenchido(resultado.getDataColeta()) ? resultado.getDataColeta() : resultado.getDt();
            LocalDate data;
            try {
                data = LocalDate.parse(dataInformada, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            } catch (DateTimeParseException ex) {
                data = LocalDate.parse(dataInformada, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
            LocalTime hora = preenchido(resultado.getHr())
                    ? LocalTime.parse(resultado.getHr(), DateTimeFormatter.ofPattern("HH:mm"))
                    : LocalTime.MIDNIGHT;
            return FhirDataHora.formatar(LocalDateTime.of(data, hora));
        } catch (DateTimeParseException | NullPointerException ex) {
            return null;
        }
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String texto(String valor) {
        return preenchido(valor) ? valor.trim() : "Resultado laboratorial";
    }
}

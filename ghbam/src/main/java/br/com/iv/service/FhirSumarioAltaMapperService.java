package br.com.iv.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import br.com.iv.entity.AssinaturaClinica;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.entity.PrescricaoItemEstruturado;
import br.com.iv.entity.ResumoDeAlta;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

// Monta o Bundle FHIR R4 (type=document) do sumario de alta a partir dos dados
// ja existentes no GHBAM (Bam + Pacientes + ResumoDeAlta + AssinaturaClinica).
// Reaproveita os helpers de Provenance/assinatura de FhirPreparatoryService em
// vez de duplicar a logica de proveniencia ja usada no endpoint preparatorio.
@ApplicationScoped
public class FhirSumarioAltaMapperService {

    private static final DateTimeFormatter[] FORMATOS_NASCIMENTO = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ISO_LOCAL_DATE,
    };

    @Inject
    FhirPreparatoryService fhirPreparatoryService;

    @Inject
    ProntuarioClinicoItemService prontuarioClinicoItemService;

    @Inject
    PrescricaoItemEstruturadoService prescricaoItemEstruturadoService;

    public Map<String, Object> montarBundleDocumento(AssinaturaClinica assinatura) {
        Bam bam = assinatura.getIdbam() != null ? Bam.<Bam>findById(assinatura.getIdbam()) : null;
        ResumoDeAlta resumo = assinatura.getIddocumento() != null
                ? ResumoDeAlta.<ResumoDeAlta>findById(assinatura.getIddocumento())
                : null;
        return montarBundleDocumento(assinatura, bam, resumo);
    }

    // Overload sem lookup via Panache: recebe as entidades ja carregadas, o que
    // permite testar o mapeamento em memoria, sem banco/contexto Quarkus.
    Map<String, Object> montarBundleDocumento(AssinaturaClinica assinatura, Bam bam, ResumoDeAlta resumo) {
        Pacientes paciente = bam != null ? bam.getPaciente() : null;

        List<Map<String, Object>> entries = new ArrayList<>();
        String patientId = "patient-" + safe(assinatura.getIdpaciente());
        String encounterId = "encounter-bam-" + safe(assinatura.getIdbam());

        if (paciente != null) {
            entries.add(entrada("Patient/" + patientId, patient(paciente, patientId)));
        }
        if (bam != null) {
            entries.add(entrada("Encounter/" + encounterId, encounter(bam, encounterId, patientId)));
        }
        if (resumo != null) {
            condicao(resumo.getCid(), resumo.getDiaginicial(), patientId, "condition-admissao-" + safe(assinatura.getIdbam()), entries);
            condicao(resumo.getCid2(), resumo.getDiagfinal(), patientId, "condition-final-" + safe(assinatura.getIdbam()), entries);
        }
        entries.add(0, entrada("Composition/composition-" + safe(assinatura.getIdassinatura()),
                composition(assinatura, resumo, patientId, encounterId)));
        entries.add(entrada("Provenance/" + safe(assinatura.getIdassinatura()),
                fhirPreparatoryService.provenancePublico(assinatura)));

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "document");
        bundle.put("timestamp", FhirDataHora.formatar(assinatura.getAssinadoEm()));
        bundle.put("entry", entries);
        return bundle;
    }

    private Map<String, Object> entrada(String fullUrl, Map<String, Object> resource) {
        Map<String, Object> entry = new LinkedHashMap<>();
        if (fullUrl != null) {
            entry.put("fullUrl", "urn:ghbam:" + fullUrl);
        }
        entry.put("resource", resource);
        return entry;
    }

    // Perfil oficial RNDS/BR-Core para Sumario de Alta, confirmado em 19/09/2026 em
    // hl7.org.br/fhir/core/StructureDefinition-br-core-sumarioalta.html (v1.1.0):
    // type/category/title abaixo sao fixos pelo perfil. As 7 secoes obrigatorias (cada
    // uma com seu LOINC) sao montadas em secoesObrigatorias(), em narrativa (text.div);
    // ainda sem entries estruturadas (Condition/AllergyIntolerance/Procedure por secao).
    // Se a validacao de conformidade da RNDS exigir entries, e ai que a homologacao vai
    // apontar - a resposta HTTP fica gravada na fila (respostaHttp).
    private static final String RNDS_PROFILE_SUMARIO_ALTA =
            "https://br-core.saude.gov.br/fhir/StructureDefinition/br-core-sumarioalta";

    private Map<String, Object> composition(AssinaturaClinica assinatura, ResumoDeAlta resumo, String patientId, String encounterId) {
        Map<String, Object> composition = new LinkedHashMap<>();
        composition.put("resourceType", "Composition");
        composition.put("meta", Map.of("profile", List.of(RNDS_PROFILE_SUMARIO_ALTA)));
        composition.put("status", "final");
        composition.put("type", Map.of(
                "coding", List.of(Map.of(
                        "system", "https://rnds-fhir.saude.gov.br/CodeSystem/BRTipoDocumento",
                        "code", "SA",
                        "display", "Sumario de Alta"))));
        composition.put("category", List.of(Map.of(
                "coding", List.of(Map.of(
                        "system", "http://loinc.org",
                        "code", "18842-5",
                        "display", "Discharge summary")))));
        composition.put("subject", Map.of("reference", "Patient/" + patientId));
        composition.put("encounter", Map.of("reference", "Encounter/" + encounterId));
        composition.put("date", FhirDataHora.formatar(assinatura.getAssinadoEm()));
        composition.put("author", List.of(Map.of(
                "display", safeText(assinatura.getAssinadoPor()))));
        // Fixo pelo perfil oficial - nao e um titulo livre do GHBAM.
        composition.put("title", "Sumario Internacional do Paciente");
        composition.put("section", secoesObrigatorias(assinatura, resumo));
        return composition;
    }

    private static final String SEM_REGISTRO =
            "Sem registro estruturado disponivel no GHBAM para esta secao.";

    private static final String SEM_PRESCRICAO_ALTA =
            "Nenhuma prescricao de alta registrada no GHBAM.";

    /** As 7 secoes sao obrigatorias no perfil (cardinalidade 7..7) mesmo quando o GHBAM ainda nao tem a fonte de dado - por isso toda secao existe, com narrativa honesta quando vazia. */
    private List<Map<String, Object>> secoesObrigatorias(AssinaturaClinica assinatura, ResumoDeAlta resumo) {
        List<Map<String, Object>> lista = new ArrayList<>();
        lista.add(secaoLoinc("Diagnosticos de Admissao", "42347-5",
                resumo != null && preenchido(resumo.getDiaginicial())
                        ? textoComCid(resumo.getDiaginicial(), resumo.getCid()) : SEM_REGISTRO));
        lista.add(secaoLoinc("Alergias e Intolerancias", "48765-2", textoAlergias(assinatura)));
        lista.add(secaoLoinc("Diagnosticos Avaliados", "57852-6",
                resumo != null && preenchido(resumo.getDiagfinal())
                        ? textoComCid(resumo.getDiagfinal(), resumo.getCid2()) : SEM_REGISTRO));
        lista.add(secaoLoinc("Procedimentos Realizados", "47519-4", textoProcedimentos(resumo)));
        lista.add(secaoLoinc("Prescricao de Alta", "8654-6", textoPrescricaoAlta(assinatura)));
        lista.add(secaoLoinc("Plano de Cuidados", "18776-5", textoOuSemRegistro(resumo == null ? null : resumo.getPlanoCuidados())));
        lista.add(secaoLoinc("Capacidade Funcional", "54522-8", textoOuSemRegistro(resumo == null ? null : resumo.getCapacidadeFuncional())));
        return lista;
    }

    private String textoOuSemRegistro(String valor) {
        return preenchido(valor) ? safeText(valor) : SEM_REGISTRO;
    }

    private String textoPrescricaoAlta(AssinaturaClinica assinatura) {
        if (prescricaoItemEstruturadoService == null || assinatura.getIdbam() == null) {
            return SEM_REGISTRO;
        }
        List<PrescricaoItemEstruturado> itens = prescricaoItemEstruturadoService.listarPorBam(assinatura.getIdbam(), true);
        // Alta sem medicacao (obito, transferencia, evasao) e legitima: narrativa verdadeira, nao "sem registro".
        if (itens == null || itens.isEmpty()) return SEM_PRESCRICAO_ALTA;
        List<String> linhas = new ArrayList<>();
        for (PrescricaoItemEstruturado item : itens) {
            if (item == null || !preenchido(item.getMedicamento())) continue;
            StringBuilder linha = new StringBuilder(item.getMedicamento().trim());
            if (preenchido(item.getDose())) linha.append(" | dose: ").append(item.getDose().trim());
            if (preenchido(item.getVia())) linha.append(" | via: ").append(item.getVia().trim());
            if (preenchido(item.getFrequencia())) linha.append(" | frequencia: ").append(item.getFrequencia().trim());
            if (preenchido(item.getDuracao())) linha.append(" | duracao: ").append(item.getDuracao().trim());
            if (preenchido(item.getObservacao())) linha.append(" | observacao: ").append(item.getObservacao().trim());
            linhas.add(linha.toString());
        }
        return linhas.isEmpty() ? SEM_PRESCRICAO_ALTA : String.join("; ", linhas);
    }

    private Map<String, Object> secaoLoinc(String titulo, String codigoLoinc, String textoNarrativo) {
        Map<String, Object> secao = new LinkedHashMap<>();
        secao.put("title", titulo);
        secao.put("code", Map.of("coding", List.of(Map.of("system", "http://loinc.org", "code", codigoLoinc))));
        secao.put("text", Map.of("status", "additional", "div", "<div>" + safeText(textoNarrativo) + "</div>"));
        return secao;
    }

    private String textoComCid(String descricao, String cid) {
        String cidTexto = preenchido(cid) ? " (CID-10 " + cid.trim() + ")" : "";
        return safeText(descricao) + cidTexto;
    }

    // Fonte real: ProntuarioClinicoItem tipo ALERGIA, o mesmo que o CDSS ja consulta -
    // ver ProntuarioClinicoItemService#listarPorTipo. Sem idpaciente ainda carregado
    // (assinatura pode ter apenas idbam), a secao fica "sem registro" em vez de arriscar
    // buscar pelo paciente errado.
    private String textoAlergias(AssinaturaClinica assinatura) {
        if (assinatura.getIdpaciente() == null || assinatura.getIdpaciente() <= 0) {
            return SEM_REGISTRO;
        }
        List<ProntuarioClinicoItem> alergias = prontuarioClinicoItemService.listarPorTipo(
                assinatura.getIdpaciente(), "ALERGIA", assinatura.getUnidade() == null ? "" : assinatura.getUnidade());
        if (alergias.isEmpty()) {
            return "Nenhuma alergia registrada no prontuario do GHBAM.";
        }
        StringBuilder texto = new StringBuilder();
        for (ProntuarioClinicoItem item : alergias) {
            if (texto.length() > 0) texto.append("; ");
            texto.append(safeText(preenchido(item.getTitulo()) ? item.getTitulo() : item.getDescricao()));
        }
        return texto.toString();
    }

    // Agrega os booleans/campos ja existentes no ResumoDeAlta que representam
    // procedimentos realizados durante a internacao - nenhum dado inventado, so
    // reunido num texto narrativo unico (a secao exige narrativa, nao exige "entry").
    private String textoProcedimentos(ResumoDeAlta resumo) {
        if (resumo == null) {
            return SEM_REGISTRO;
        }
        List<String> itens = new ArrayList<>();
        if (resumo.isEndoscopiadiges()) itens.add("Endoscopia digestiva");
        if (resumo.isTomografiacom()) itens.add("Tomografia computadorizada");
        if (resumo.isUltrasono()) itens.add("Ultrassonografia");
        if (resumo.isPuncao()) itens.add("Puncao" + (preenchido(resumo.getNdiaspuncao()) ? " (" + resumo.getNdiaspuncao().trim() + " dia(s))" : ""));
        if (resumo.isPuncaotora()) itens.add("Puncao toracica");
        if (resumo.isEntubacao()) itens.add("Entubacao");
        if (resumo.isCateterismo()) itens.add("Cateterismo" + (preenchido(resumo.getNdiascateterismo()) ? " (" + resumo.getNdiascateterismo().trim() + " dia(s))" : ""));
        if (resumo.isCti()) itens.add("Internacao em CTI" + (preenchido(resumo.getNdiascti()) ? " (" + resumo.getNdiascti().trim() + " dia(s))" : ""));
        if (resumo.isProcedimentocir()) {
            itens.add(preenchido(resumo.getProcedimentocirespecifi())
                    ? "Procedimento cirurgico: " + resumo.getProcedimentocirespecifi().trim()
                    : "Procedimento cirurgico");
        }
        return itens.isEmpty() ? "Nenhum procedimento registrado nesta internacao." : String.join("; ", itens);
    }

    private Map<String, Object> patient(Pacientes paciente, String id) {
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("resourceType", "Patient");
        patient.put("id", id);
        List<Map<String, Object>> identifiers = new ArrayList<>();
        if (preenchido(paciente.getCpf())) {
            identifiers.add(Map.of("system", "urn:oid:2.16.840.1.113883.13.237", "value", paciente.getCpf().trim()));
        }
        if (preenchido(paciente.getCns())) {
            identifiers.add(Map.of("system", "urn:oid:2.16.840.1.113883.13.236", "value", paciente.getCns().trim()));
        }
        patient.put("identifier", identifiers);
        patient.put("name", List.of(Map.of("text", safeText(paciente.getNome()))));
        patient.put("gender", genero(paciente.getSexo()));
        String nascimentoIso = parseNascimentoParaIso(paciente.getNascimento());
        if (nascimentoIso != null) {
            patient.put("birthDate", nascimentoIso);
        }
        return patient;
    }

    private Map<String, Object> encounter(Bam bam, String id, String patientId) {
        Map<String, Object> encounter = new LinkedHashMap<>();
        encounter.put("resourceType", "Encounter");
        encounter.put("id", id);
        encounter.put("status", bam.isDarAlta() ? "finished" : "in-progress");
        encounter.put("subject", Map.of("reference", "Patient/" + patientId));
        encounter.put("serviceProvider", Map.of("display", safeText(bam.getUnidade())));
        return encounter;
    }

    private void condicao(String cid, String texto, String patientId, String id, List<Map<String, Object>> entries) {
        if (!preenchido(cid)) {
            return;
        }
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("resourceType", "Condition");
        condition.put("id", id);
        condition.put("subject", Map.of("reference", "Patient/" + patientId));
        condition.put("code", Map.of(
                "coding", List.of(Map.of("system", "http://hl7.org/fhir/sid/icd-10", "code", cid.trim())),
                "text", safeText(texto)));
        // Todo entry de Bundle document precisa de fullUrl (FhirR4PayloadValidator rejeita sem).
        entries.add(entrada("Condition/" + id, condition));
    }

    private String genero(String sexo) {
        if (!preenchido(sexo)) {
            return "unknown";
        }
        return switch (sexo.trim().toUpperCase(Locale.ROOT)) {
            case "M" -> "male";
            case "F" -> "female";
            default -> "unknown";
        };
    }

    // Pacientes.nascimento e String com formato inconsistente entre modulos do
    // sistema (dd/MM/yyyy em uns, yyyy/MM/dd em outros). O mapeamento FHIR nunca
    // pode quebrar a assinatura clinica por causa disso: se nada parsear, omite.
    private String parseNascimentoParaIso(String valor) {
        if (!preenchido(valor)) {
            return null;
        }
        String limpo = valor.trim();
        for (DateTimeFormatter formato : FORMATOS_NASCIMENTO) {
            try {
                return LocalDate.parse(limpo, formato).format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // tenta o proximo formato
            }
        }
        return null;
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String safeText(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String safe(Integer valor) {
        return valor == null ? "0" : String.valueOf(valor);
    }
}

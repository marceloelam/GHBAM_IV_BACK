package br.com.iv.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.entity.FhirEnvioDestino;
import br.com.iv.entity.PedidoResultadoLaboratorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Teste de conexao com a RNDS de HOMOLOGACAO sem expor paciente real: monta um Resultado de Exame com o
 * mesmo gerador do envio real (formato valido - a RNDS valida o documento antes de checar a autorizacao)
 * e troca o paciente por dados ficticios antes de enviar. Nao grava nada nem usa a fila.
 * Serve para saber se o CNPJ do certificado ja foi liberado (EHR-ERR884 = ainda nao).
 */
@ApplicationScoped
public class FhirTesteRndsService {

    private static final Logger LOG = Logger.getLogger(FhirTesteRndsService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    static final String NOME_FICTICIO = "PACIENTE TESTE FICTICIO GHBAM";
    private static final String SISTEMA_CPF = "urn:oid:2.16.840.1.113883.13.237";

    @Inject
    FhirEnvioDestinoService destinoService;

    @Inject
    FhirResultadoLaboratorioMapperService mapper;

    @Inject
    FhirR4PayloadValidator validator;

    @Inject
    CadsusPdqClienteService cadsus;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntityManager em;

    /** CPF ficticio com digitos verificadores validos. */
    static String cpfFicticio() {
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) {
            d[i] = RANDOM.nextInt(10);
        }
        d[0] = d[0] == 0 ? 9 : d[0];
        for (int t = 9; t < 11; t++) {
            int soma = 0;
            for (int i = 0; i < t; i++) {
                soma += d[i] * (t + 1 - i);
            }
            int r = (soma * 10) % 11;
            d[t] = r == 10 ? 0 : r;
        }
        StringBuilder sb = new StringBuilder();
        for (int x : d) {
            sb.append(x);
        }
        return sb.toString();
    }

    /** Troca o Patient por um ficticio e apaga do JSON qualquer CPF/CNS/nome real que tenha sobrado. */
    @SuppressWarnings("unchecked")
    String anonimizar(Map<String, Object> bundle) throws Exception {
        List<String> reais = new ArrayList<>();
        List<Map<String, Object>> entries = (List<Map<String, Object>>) bundle.get("entry");
        String cpf = cpfFicticio();
        for (int i = 0; entries != null && i < entries.size(); i++) {
            // Entradas do mapper podem ser imutaveis (Map.of): trabalha numa copia e recoloca.
            Map<String, Object> entry = new LinkedHashMap<>(entries.get(i));
            entries.set(i, entry);
            Object recurso = entry.get("resource");
            if (!(recurso instanceof Map<?, ?> r) || !"Patient".equals(r.get("resourceType"))) {
                continue;
            }
            Map<String, Object> paciente = (Map<String, Object>) r;
            if (paciente.get("identifier") instanceof List<?> ids) {
                for (Object id : ids) {
                    if (id instanceof Map<?, ?> m && m.get("value") != null) {
                        reais.add(m.get("value").toString());
                    }
                }
            }
            if (paciente.get("name") instanceof List<?> nomes) {
                for (Object n : nomes) {
                    if (n instanceof Map<?, ?> m && m.get("text") != null) {
                        reais.add(m.get("text").toString());
                    }
                }
            }
            Map<String, Object> ficticio = new LinkedHashMap<>();
            ficticio.put("resourceType", "Patient");
            ficticio.put("id", paciente.get("id"));
            ficticio.put("identifier", List.of(Map.of("system", SISTEMA_CPF, "value", cpf)));
            ficticio.put("name", List.of(Map.of("text", NOME_FICTICIO)));
            entry.put("resource", ficticio);
        }
        String json = objectMapper.writeValueAsString(bundle);
        for (String real : reais) {
            if (real != null && real.trim().length() >= 4) {
                json = json.replace(real.trim(), real.trim().matches("\\d+") ? cpf : NOME_FICTICIO);
            }
        }
        // Trava: nenhum identificador/nome real pode sair no teste.
        for (String real : reais) {
            if (real != null && real.trim().length() >= 4 && json.contains(real.trim())) {
                throw new IllegalStateException("Dado real do paciente permaneceu no documento de teste; envio abortado.");
            }
        }
        return json;
    }

    /** Troca cada Observation por glicose ficticia completa e garante uma amostra (Specimen) referenciada. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> exameFicticio(Map<String, Object> bundle) {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) bundle.get("entry");
        String patientRef = null;
        boolean temSpecimen = false;
        for (Map<String, Object> e : entries) {
            Map<String, Object> r = (Map<String, Object>) e.get("resource");
            if ("Patient".equals(r.get("resourceType"))) {
                patientRef = "Patient/" + r.get("id");
            }
            if ("Specimen".equals(r.get("resourceType"))) {
                temSpecimen = true;
            }
        }
        String specimenId = "specimen-teste";
        for (Map<String, Object> e : entries) {
            Map<String, Object> r = (Map<String, Object>) e.get("resource");
            if ("Specimen".equals(r.get("resourceType"))) {
                specimenId = String.valueOf(r.get("id"));
            }
            if (!"Observation".equals(r.get("resourceType"))) {
                continue;
            }
            r.remove("dataAbsentReason");
            r.remove("valueString");
            r.remove("note");
            r.put("code", Map.of("text", "Glicose", "coding",
                    List.of(Map.of("system", "http://loinc.org", "code", "2345-7"))));
            r.put("valueQuantity", Map.of("value", 90, "unit", "mg/dL", "system", "http://unitsofmeasure.org",
                    "code", "mg/dL"));
            r.put("referenceRange", List.of(Map.of("text", "70 a 99 mg/dL")));
            r.put("specimen", Map.of("reference", "Specimen/" + specimenId));
        }
        if (!temSpecimen) {
            Map<String, Object> sp = new LinkedHashMap<>();
            sp.put("resourceType", "Specimen");
            sp.put("id", specimenId);
            sp.put("type", Map.of("text", "Sangue"));
            if (patientRef != null) {
                sp.put("subject", Map.of("reference", patientRef));
            }
            Map<String, Object> entrada = new LinkedHashMap<>();
            entrada.put("fullUrl", "Specimen/" + specimenId);
            entrada.put("resource", sp);
            entries.add(entrada);
        }
        return bundle;
    }

    /** Interpretacao para o operador da resposta da RNDS. */
    static String interpretar(Integer http, String corpo) {
        String c = corpo == null ? "" : corpo;
        if (http == null) {
            return "Sem resposta da RNDS (rede ou certificado): " + c;
        }
        if (http >= 200 && http < 300) {
            return "ACEITO: o CNPJ esta liberado na RNDS de homologacao e o documento foi aceito.";
        }
        if (c.contains("EHR-ERR884")) {
            return "CNPJ do certificado AINDA NAO AUTORIZADO na RNDS (EHR-ERR884). Falta aprovar a solicitacao no Portal de Servicos DATASUS.";
        }
        if (http == 401 || http == 403) {
            return "Recusado na autenticacao (HTTP " + http + "). Conferir certificado e CNS do requisitante.";
        }
        return "Autenticacao passou; a RNDS recusou o CONTEUDO do documento (HTTP " + http
                + "). Ajustar o que a mensagem apontar.";
    }

    @Transactional
    public Map<String, Object> testar(String unidade) {
        return testar(unidade, false);
    }

    /** simular=true: monta e anonimiza, devolve o paciente que iria no envio, e NAO envia. */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> testar(String unidade, boolean simular) {
        Optional<FhirEnvioDestino> destinoOpt = destinoService.buscarAtivoParaEnvio(unidade, "RESULTADO_EXAME")
                .or(() -> destinoService.buscarAtivoParaEnvio(unidade, "SUMARIO_ALTA"));
        FhirEnvioDestino destino = destinoOpt.orElseThrow(() -> new IllegalArgumentException(
                "Nenhum destino ativo (Resultado de Exame ou Sumario de Alta) para a unidade " + unidade + "."));
        if (!FhirDispatchService.TIPO_RNDS.equals(destino.getTipoAutenticacao())) {
            throw new IllegalArgumentException("O destino nao usa 'RNDS (certificado da Integracao SUS)'.");
        }
        if ("PRODUCAO".equalsIgnoreCase(destino.getAmbiente())) {
            throw new IllegalArgumentException("Teste so em HOMOLOGACAO: o destino esta em producao.");
        }

        @SuppressWarnings("unchecked")
        List<Number> ids = em.createNativeQuery("SELECT r.idpedidoresultado FROM pedidoresultadolaboratorio r"
                + " JOIN bam b ON b.idbam = r.bam WHERE upper(trim(b.unidade)) = upper(trim(?1))"
                + " AND EXISTS (SELECT 1 FROM pedidoresultadolaboratorio_item i WHERE i.idpedidoresultado = r.idpedidoresultado)"
                + " ORDER BY r.idpedidoresultado DESC").setParameter(1, unidade).setMaxResults(200).getResultList();
        Map<String, Object> bundle = null;
        Integer usado = null;
        Map<String, Integer> pendencias = new LinkedHashMap<>();
        for (Number id : ids) {
            PedidoResultadoLaboratorio r = PedidoResultadoLaboratorio.findById(id.intValue());
            if (r == null) {
                continue;
            }
            Map<String, Object> b = mapper.montarBundleResultado(r);
            List<String> faltas = validator.avaliarResultadoLaboratorial(b);
            if (faltas.isEmpty()) {
                bundle = b;
                usado = id.intValue();
                break;
            }
            // Agrupa pelo tipo de pendencia (sem o nome do exame), so para diagnostico.
            for (String f : faltas) {
                String tipo = f.contains(":") ? f.substring(0, f.indexOf(':')) : f;
                pendencias.merge(tipo, 1, Integer::sum);
            }
        }
        boolean exameFicticio = false;
        if (bundle == null) {
            // Nenhum resultado real tem itens com LOINC: usa a ESTRUTURA do resultado mais recente da unidade
            // e troca o exame por um ficticio completo (glicose, LOINC 2345-7, 90 mg/dL, amostra de sangue).
            @SuppressWarnings("unchecked")
            List<Number> qualquer = em.createNativeQuery("SELECT r.idpedidoresultado FROM pedidoresultadolaboratorio r"
                    + " JOIN bam b ON b.idbam = r.bam WHERE upper(trim(b.unidade)) = upper(trim(?1))"
                    + " ORDER BY r.idpedidoresultado DESC").setParameter(1, unidade).setMaxResults(1).getResultList();
            PedidoResultadoLaboratorio base = qualquer.isEmpty() ? null
                    : PedidoResultadoLaboratorio.findById(qualquer.get(0).intValue());
            if (base == null) {
                throw new IllegalArgumentException("A unidade nao tem nenhum resultado de exame para servir de base.");
            }
            bundle = exameFicticio(mapper.montarBundleResultado(base));
            List<String> faltas = validator.avaliarResultadoLaboratorial(bundle);
            if (!faltas.isEmpty()) {
                throw new IllegalArgumentException("Documento de teste incompleto: " + faltas
                        + (pendencias.isEmpty() ? "" : " | pendencias dos resultados reais: " + pendencias));
            }
            usado = base.getIdPedidoResultado();
            exameFicticio = true;
        }

        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("ambiente", destino.getAmbiente());
        saida.put("modelo", exameFicticio
                ? "estrutura do resultado " + usado + " com EXAME e PACIENTE ficticios (glicose 90 mg/dL)"
                : "resultado " + usado + " com paciente substituido por ficticio");
        if (simular) {
            try {
                String payload = anonimizar(bundle);
                Map<String, Object> doc = objectMapper.readValue(payload, Map.class);
                for (Object e : (List<Object>) doc.get("entry")) {
                    Object r = ((Map<String, Object>) e).get("resource");
                    if (r instanceof Map<?, ?> m && "Patient".equals(m.get("resourceType"))) {
                        saida.put("pacienteNoEnvio", m);
                    }
                }
                saida.put("tamanhoBytes", payload.length());
                saida.put("interpretacao", "SIMULACAO: nada foi enviado.");
            } catch (Exception e) {
                saida.put("erro", e.toString());
            }
            return saida;
        }
        Integer http = null;
        String corpo;
        try {
            String payload = anonimizar(bundle);
            CadsusPdqClienteService.AcessoRnds acesso = cadsus.acessoRnds(unidade, false);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(destino.getEndpointBase()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/fhir+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            FhirDispatchService.aplicarAutenticacaoRnds(builder, acesso.token(), destinoService.credencialDecifrada(destino));
            HttpClient cliente = HttpClient.newBuilder().sslContext(acesso.sslContext())
                    .connectTimeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> resp = cliente.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            http = resp.statusCode();
            corpo = resp.body() == null ? "" : resp.body();
        } catch (Exception e) {
            corpo = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        String resumo = corpo.length() > 1500 ? corpo.substring(0, 1500) : corpo;
        saida.put("http", http);
        saida.put("resposta", resumo);
        saida.put("interpretacao", interpretar(http, corpo));
        LOG.infof("[RNDS-TESTE] unidade=%s ambiente=%s http=%s %s", unidade, destino.getAmbiente(), http,
                interpretar(http, corpo));
        return saida;
    }
}

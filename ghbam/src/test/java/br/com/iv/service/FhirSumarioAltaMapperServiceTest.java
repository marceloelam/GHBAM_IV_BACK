package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.AssinaturaClinica;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.ResumoDeAlta;

// Testa o mapeamento FHIR do sumario de alta em memoria (sem persistir nada,
// sem @QuarkusTest): o backend nao tem banco de teste isolado hoje (dev =
// copia da producao), entao qualquer teste que precise de Bam.findById/
// ResumoDeAlta.findById nao deve rodar aqui - ver overload de
// montarBundleDocumento que recebe as entidades ja carregadas.
class FhirSumarioAltaMapperServiceTest {

    private final FhirSumarioAltaMapperService mapper = criarMapper();

    // provenance() nao usa o AssinaturaClinicaService injetado em
    // FhirPreparatoryService (so le campos do proprio parametro), entao uma
    // instancia sem CDI e segura aqui.
    private static FhirSumarioAltaMapperService criarMapper() {
        FhirSumarioAltaMapperService mapper = new FhirSumarioAltaMapperService();
        mapper.fhirPreparatoryService = new FhirPreparatoryService();
        // listarPorTipo faz uma consulta Panache real - sem contexto de persistencia
        // (sem @QuarkusTest, ver comentario da classe), sobrescreve para devolver vazio
        // em vez de tocar banco. Alergias vazias e o mesmo cenario que estes testes ja
        // cobriam antes desta secao do FHIR existir.
        mapper.prontuarioClinicoItemService = new ProntuarioClinicoItemService() {
            @Override
            public List<br.com.iv.entity.ProntuarioClinicoItem> listarPorTipo(int idpaciente, String tipo, String unidade) {
                return List.of();
            }
        };
        return mapper;
    }

    @Test
    void deveMapearPacienteComCpfCnsGeneroENascimentoDdMmYyyy() {
        AssinaturaClinica assinatura = assinaturaBase();
        Bam bam = bamComPaciente("111.111.111-11", "700000000000000", "M", "15/03/1990");
        ResumoDeAlta resumo = resumoComCid("A00", "Colera", "B99", "Doenca nao especificada");

        Map<String, Object> bundle = mapper.montarBundleDocumento(assinatura, bam, resumo);

        Map<String, Object> patient = recursoPorTipo(bundle, "Patient");
        assertEquals("male", patient.get("gender"));
        assertEquals("1990-03-15", patient.get("birthDate"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> identifiers = (List<Map<String, Object>>) patient.get("identifier");
        assertEquals(2, identifiers.size());
    }

    @Test
    void deveAceitarNascimentoNoFormatoAnoMesDia() {
        AssinaturaClinica assinatura = assinaturaBase();
        Bam bam = bamComPaciente("222.222.222-22", "", "F", "1990/03/15");

        Map<String, Object> bundle = mapper.montarBundleDocumento(assinatura, bam, null);

        Map<String, Object> patient = recursoPorTipo(bundle, "Patient");
        assertEquals("1990-03-15", patient.get("birthDate"));
        assertEquals("female", patient.get("gender"));
    }

    @Test
    void naoDeveQuebrarQuandoNascimentoEstaEmFormatoNaoReconhecido() {
        AssinaturaClinica assinatura = assinaturaBase();
        Bam bam = bamComPaciente("333.333.333-33", "", "M", "data invalida qualquer");

        Map<String, Object> bundle = mapper.montarBundleDocumento(assinatura, bam, null);

        Map<String, Object> patient = recursoPorTipo(bundle, "Patient");
        assertFalse(patient.containsKey("birthDate"));
    }

    @Test
    void deveGerarConditionParaCadaCidPreenchidoEOmitirQuandoVazio() {
        AssinaturaClinica assinatura = assinaturaBase();
        Bam bam = bamComPaciente("444.444.444-44", "", "M", "01/01/2000");
        ResumoDeAlta resumo = resumoComCid("J18", "Pneumonia", "", "");

        Map<String, Object> bundle = mapper.montarBundleDocumento(assinatura, bam, resumo);

        long condicoes = entradas(bundle).stream()
                .filter(entry -> "Condition".equals(((Map<?, ?>) entry.get("resource")).get("resourceType")))
                .count();
        assertEquals(1, condicoes);
    }

    @Test
    void bundleDeveSerDoTipoDocumentoEIncluirProvenance() {
        AssinaturaClinica assinatura = assinaturaBase();
        Bam bam = bamComPaciente("555.555.555-55", "", "M", "01/01/2000");

        Map<String, Object> bundle = mapper.montarBundleDocumento(assinatura, bam, null);

        assertEquals("Bundle", bundle.get("resourceType"));
        assertEquals("document", bundle.get("type"));
        boolean temProvenance = entradas(bundle).stream()
                .anyMatch(entry -> "Provenance".equals(((Map<?, ?>) entry.get("resource")).get("resourceType")));
        assertTrue(temProvenance);
    }

    private AssinaturaClinica assinaturaBase() {
        AssinaturaClinica assinatura = new AssinaturaClinica();
        assinatura.setIdassinatura(1);
        assinatura.setTipoDocumento("RESUMO_ALTA");
        assinatura.setIdbam(10);
        assinatura.setIddocumento(20);
        assinatura.setIdpaciente(30);
        assinatura.setUnidade("HMSFX");
        assinatura.setStatus("ASSINADO");
        assinatura.setAssinadoPor("Dr. Teste");
        assinatura.setAssinadoPorMatricula("999");
        assinatura.setAssinadoEm(LocalDateTime.of(2026, 9, 2, 10, 0));
        return assinatura;
    }

    private Bam bamComPaciente(String cpf, String cns, String sexo, String nascimento) {
        Bam bam = new Bam();
        bam.setIdbam(10);
        bam.setUnidade("HMSFX");
        bam.setDarAlta(true);
        Pacientes paciente = new Pacientes();
        paciente.setNome("Paciente Teste");
        paciente.setCpf(cpf);
        paciente.setCns(cns);
        paciente.setSexo(sexo);
        paciente.setNascimento(nascimento);
        bam.setPaciente(paciente);
        return bam;
    }

    private ResumoDeAlta resumoComCid(String cid, String diagInicial, String cid2, String diagFinal) {
        ResumoDeAlta resumo = new ResumoDeAlta();
        resumo.setCid(cid);
        resumo.setDiaginicial(diagInicial);
        resumo.setCid2(cid2);
        resumo.setDiagfinal(diagFinal);
        return resumo;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> entradas(Map<String, Object> bundle) {
        return (List<Map<String, Object>>) bundle.get("entry");
    }

    private Map<String, Object> recursoPorTipo(Map<String, Object> bundle, String resourceType) {
        return entradas(bundle).stream()
                .map(entry -> (Map<String, Object>) entry.get("resource"))
                .filter(resource -> resourceType.equals(resource.get("resourceType")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Recurso " + resourceType + " nao encontrado no bundle"));
    }
}

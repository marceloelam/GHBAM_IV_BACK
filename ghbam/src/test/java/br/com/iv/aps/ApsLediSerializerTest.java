package br.com.iv.aps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.thrift.TDeserializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.junit.jupiter.api.Test;

import br.gov.saude.esusab.dadotransp.DadoTransporteThrift;
import br.gov.saude.esusab.ras.atendindividual.FichaAtendimentoIndividualChildThrift;
import br.gov.saude.esusab.ras.atendindividual.FichaAtendimentoIndividualMasterThrift;
import br.gov.saude.esusab.ras.cadastroindividual.CadastroIndividualThrift;

/**
 * Serializacao LEDI APS 8.7.0 (dados sinteticos): ida e volta pelo TBinaryProtocol
 * oficial, conversao de tipos e validacao de campos obrigatorios.
 */
class ApsLediSerializerTest {

    private final ApsLediSerializer serializer = new ApsLediSerializer();

    private static Map<String, Object> fai() {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("lotacaoFormPrincipal", Map.of("profissionalCNS", "898001160660761", "cboCodigo_2002", "225142",
                "cnes", "1234567", "ine", "0000123456"));
        header.put("dataAtendimento", "2026-09-23");
        header.put("codigoIbgeMunicipio", "3302007");
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("cpfCidadao", "52998224725");
        child.put("dataNascimento", "1980-05-10");
        child.put("sexo", 1);
        child.put("localDeAtendimento", 1);
        child.put("turno", 1);
        child.put("tipoAtendimento", 5);
        child.put("dataHoraInicialAtendimento", "2026-09-23T08:10:00");
        child.put("dataHoraFinalAtendimento", "2026-09-23T08:30:00");
        child.put("condutas", List.of(9));
        child.put("problemasCondicoes", List.of(Map.of("ciap", "K86", "situacao", 0, "isAvaliado", true)));
        child.put("medicoes", Map.of("pressaoArterialSistolica", 140, "pressaoArterialDiastolica", "90", "peso", "72,5"));
        child.put("campoQueNaoExisteNoLedi", "ignorado");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuidFicha", "1234567-2c0d4b1e-6a5f-4c2e-9d8b-1a2b3c4d5e6f");
        m.put("tpCdsOrigem", 3);
        m.put("headerTransport", header);
        m.put("atendimentosIndividuais", List.of(child));
        return m;
    }

    @Test
    void transporteIdaEVoltaPreservaCampos() throws Exception {
        byte[] bytes = serializer.serializarTransporte(
                new ApsLediSerializer.Envelope("1234567-2c0d4b1e-6a5f-4c2e-9d8b-1a2b3c4d5e6f", 4, "1234567", "3302007", "0000123456", 1L),
                fai(), new ApsLediSerializer.Instalacao("GHBAM APS - 4.0", "abc", "12345678000195", "PREFEITURA TESTE", null, null));
        DadoTransporteThrift t = new DadoTransporteThrift();
        new TDeserializer(new TBinaryProtocol.Factory()).deserialize(t, bytes);
        assertEquals(4L, t.getTipoDadoSerializado());
        assertEquals(8, t.getVersao().getMajor());
        assertEquals(7, t.getVersao().getMinor());

        FichaAtendimentoIndividualMasterThrift m = new FichaAtendimentoIndividualMasterThrift();
        new TDeserializer(new TBinaryProtocol.Factory()).deserialize(m, t.getDadoSerializado());
        FichaAtendimentoIndividualChildThrift c = m.getAtendimentosIndividuais().get(0);
        assertEquals("225142", m.getHeaderTransport().getLotacaoFormPrincipal().getCboCodigo_2002());
        assertEquals("52998224725", c.getCpfCidadao());
        assertEquals(90, c.getMedicoes().getPressaoArterialDiastolica());
        assertEquals(72.5, c.getMedicoes().getPeso(), 0.0001);
        assertEquals("K86", c.getProblemasCondicoes().get(0).getCiap());
        assertEquals(ApsLediSerializer.epochMillis(java.time.LocalDate.of(1980, 5, 10)), c.getDataNascimento());
    }

    @Test
    void cadastroIndividualComListasEBooleanos() throws Exception {
        Map<String, Object> fci = new LinkedHashMap<>();
        fci.put("uuid", "1234567-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        fci.put("tpCdsOrigem", 3);
        fci.put("identificacaoUsuarioCidadao", Map.of("nomeCidadao", "MARIA TESTE", "sexoCidadao", 1, "racaCorCidadao", 4));
        fci.put("condicoesDeSaude", Map.of("statusTemHipertensaoArterial", true, "doencaCardiaca", List.of(24, 25)));
        CadastroIndividualThrift ci = new CadastroIndividualThrift();
        new TDeserializer(new TBinaryProtocol.Factory()).deserialize(ci, serializer.serializarFicha(2, fci));
        assertTrue(ci.getCondicoesDeSaude().isStatusTemHipertensaoArterial());
        assertEquals(List.of(24L, 25L), ci.getCondicoesDeSaude().getDoencaCardiaca());
    }

    @Test
    void enumsAceitamCodigoOuNome() {
        Map<String, Object> mca = new LinkedHashMap<>();
        mca.put("uuidFicha", "1234567-x");
        mca.put("perguntasQuestionarioCriancasComMaisDoisAnos", List.of(
                Map.of("pergunta", 11, "respostaUnicaEscolha", "SIM"),
                Map.of("pergunta", 12, "respostaMultiplaEscolha", List.of(12, 14))));
        assertTrue(serializer.serializarFicha(12, mca).length > 0);
        Map<String, Object> invalido = Map.of("uuidFicha", "1234567-x", "perguntasQuestionarioCriancasComMaisDoisAnos",
                List.of(Map.of("pergunta", 999)));
        assertThrows(IllegalArgumentException.class, () -> serializer.serializarFicha(12, invalido));
    }

    @Test
    void campoObrigatorioAusenteEBarrado() {
        assertThrows(IllegalArgumentException.class, () -> serializer.serializarFicha(4, Map.of("tpCdsOrigem", 3)));
        assertThrows(IllegalArgumentException.class, () -> serializer.serializarFicha(99, Map.of()));
    }

    @Test
    void validacoesDeIdentificacao() {
        assertTrue(ApsValidacao.cnsValido("898001160660761"));
        assertFalse(ApsValidacao.cnsValido("898001160660762"));
        assertTrue(ApsValidacao.cpfValido("529.982.247-25"));
        assertFalse(ApsValidacao.cpfValido("11111111111"));
        assertThrows(IllegalArgumentException.class, () -> ApsValidacao.validarIdentificacao(null, null, null, "teste"));
        ApsValidacao.validarIdentificacao(null, null, 3, "recem-nascido");
    }

    @Test
    void uuidRecomendadoTem44Caracteres() {
        String uuid = ApsContextoService.novoUuid("1234567");
        assertEquals(44, uuid.length());
        assertTrue(uuid.startsWith("1234567-"));
    }
}

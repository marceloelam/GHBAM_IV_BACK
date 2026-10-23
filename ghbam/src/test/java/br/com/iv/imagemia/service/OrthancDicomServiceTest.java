package br.com.iv.imagemia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * Regras de parsing da URL do Orthanc (base REST, basic auth e remocao de
 * credenciais). Logica pura, sem CDI nem rede.
 */
class OrthancDicomServiceTest {

    @Test
    void baseRest_removeDicomWebETrailingSlash() {
        assertEquals("http://admin:admin@10.200.200.78:8042",
                OrthancDicomService.baseRest("http://admin:admin@10.200.200.78:8042/dicom-web"));
        assertEquals("http://10.200.200.78:8042",
                OrthancDicomService.baseRest("http://10.200.200.78:8042/dicom-web/"));
        assertEquals("http://10.200.200.78:8042",
                OrthancDicomService.baseRest("http://10.200.200.78:8042/"));
    }

    @Test
    void baseRest_vazioOuNulo() {
        assertNull(OrthancDicomService.baseRest(null));
        assertNull(OrthancDicomService.baseRest("   "));
    }

    @Test
    void basicAuth_doUserinfo() {
        String esperado = "Basic " + Base64.getEncoder()
                .encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));
        assertEquals(esperado, OrthancDicomService.basicAuth("http://admin:admin@10.200.200.78:8042/dicom-web"));
    }

    @Test
    void basicAuth_semCredencial() {
        assertNull(OrthancDicomService.basicAuth("http://10.200.200.78:8042/dicom-web"));
    }

    @Test
    void mesmoPaciente_confere() {
        assertTrue(OrthancDicomService.mesmoPaciente("12345", 12345));
        assertTrue(OrthancDicomService.mesmoPaciente(" 0012345 ", 12345));
    }

    @Test
    void mesmoPaciente_divergenteOuAusenteBloqueia() {
        assertFalse(OrthancDicomService.mesmoPaciente("12346", 12345));
        assertFalse(OrthancDicomService.mesmoPaciente(null, 12345));
        assertFalse(OrthancDicomService.mesmoPaciente("   ", 12345));
        assertFalse(OrthancDicomService.mesmoPaciente("ANON", 12345));
        assertFalse(OrthancDicomService.mesmoPaciente("12345", null));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode serie(String id, String modalidade,
                                                                       String descricao, int cortes) {
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        var s = om.createObjectNode().put("ID", id);
        s.putObject("MainDicomTags").put("Modality", modalidade).put("SeriesDescription", descricao);
        var inst = s.putArray("Instances");
        for (int i = 0; i < cortes; i++) inst.add(id + "-" + i);
        return s;
    }

    @Test
    void escolherSerie_descartaScoutEPrefereSemContrasteComMenosCortes() {
        // Espelha o estudo de amostra do Orthanc de teste.
        var series = new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode()
                .add(serie("c", "CT", "+C", 501))
                .add(serie("s", "CT", "scout", 2))
                .add(serie("p", "CT", "Smart Prep Series", 5))
                .add(serie("n", "CT", "natyve", 267))
                .add(serie("r", "CT", "Recon 2: natyve", 195));
        assertTrue(OrthancDicomService.temTc(series));
        assertEquals("r", OrthancDicomService.escolherSerie(series).path("ID").asText());
    }

    @Test
    void escolherSerie_semElegivelDevolveNull() {
        var series = new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode()
                .add(serie("s", "CT", "Topogram", 2))
                .add(serie("o", "CT", "Cranio osso", 40))
                .add(serie("k", "CT", "COR 3mm", 60));
        assertNull(OrthancDicomService.escolherSerie(series));
    }

    @Test
    void temTc_raioXNaoEhTc() {
        var series = new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode()
                .add(serie("x", "CR", "TORAX PA", 1));
        assertFalse(OrthancDicomService.temTc(series));
        assertNull(OrthancDicomService.escolherSerie(series));
    }

    @Test
    void multipart_extraiPrimeiraParte() {
        byte[] corpo = ("--abc\r\nContent-Type: application/dicom\r\n\r\nDICM-DADOS\r\n--abc--\r\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("DICM-DADOS", new String(OrthancDicomService.primeiraParteMultipart(corpo,
                "multipart/related; type=\"application/dicom\"; boundary=abc"), StandardCharsets.ISO_8859_1));
        // boundary entre aspas
        assertEquals("DICM-DADOS", new String(OrthancDicomService.primeiraParteMultipart(corpo,
                "multipart/related; boundary=\"abc\""), StandardCharsets.ISO_8859_1));
        // sem multipart: corpo inteiro
        assertEquals(3, OrthancDicomService.primeiraParteMultipart(new byte[] {1, 2, 3}, "application/dicom").length);
    }

    @Test
    void valorTag_leJsonDicom() throws Exception {
        var item = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3\"]},\"00100020\":{\"vr\":\"LO\"}}");
        assertEquals("1.2.3", OrthancDicomService.valorTag(item, "0020000D"));
        assertNull(OrthancDicomService.valorTag(item, "00100020"));
        assertNull(OrthancDicomService.valorTag(item, "00080018"));
    }

    @Test
    void semCredenciais_removeUserinfo() {
        assertEquals("http://10.200.200.78:8042",
                OrthancDicomService.semCredenciais("http://admin:admin@10.200.200.78:8042"));
        assertEquals("http://10.200.200.78:8042",
                OrthancDicomService.semCredenciais("http://10.200.200.78:8042"));
    }
}

package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DefinicaoCasoEpidemiologicoTest {

    private static boolean conta(String texto, String nome, String termos) {
        return DefinicaoCasoEpidemiologico.atende(" " + texto + " ", nome, termos);
    }

    private static final String DENGUE = "febre, dengue, mialgia, artralgia, exantema";
    private static final String GRIPAL = "influenza, tosse, gripe, diarreia, febre";

    @Test
    void sindromeFebrilContaPelosSintomasSemCitarDengue() {
        assertTrue(conta("febre mialgia cefaleia", "Síndrome Febril", "febre, mialgia"));
        assertFalse(conta("febre mialgia", "Síndrome Febril", "febre, mialgia"));
        assertFalse(conta("mialgia cefaleia vomitos", "Síndrome Febril", "febre, mialgia"));
    }

    /** A regra antiga dava zero casos porque o nome com "dengue" exigia a palavra escrita. */
    @Test
    void regraAntigaFebrilComDengueContaPelosSintomas() {
        assertTrue(conta("febre mialgia cefaleia", "Síndrome Febril / Suspeita de Dengue", DENGUE));
    }

    @Test
    void descarteSoRetiraDoGrupoQueCitaADoenca() {
        assertTrue(DefinicaoCasoEpidemiologico.diagnosticoCitaRegra("Tuberculose pulmonar", "Suspeita de Tuberculose", "tuberculose, tosse"));
        assertFalse(DefinicaoCasoEpidemiologico.diagnosticoCitaRegra("Pneumonia, tosse", "Suspeita de Tuberculose", "tuberculose, tosse"));
    }

    @Test
    void febreSozinhaNaoEDengue() {
        assertFalse(conta("febre ha dois dias", "Suspeita de Dengue", DENGUE));
    }

    @Test
    void sintomasSemHipoteseNaoSaoDengue() {
        assertFalse(conta("febre mialgia cefaleia", "Suspeita de Dengue", DENGUE));
    }

    @Test
    void hipoteseSemSintomasNaoEDengue() {
        assertFalse(conta("orientado sobre sinais de dengue", "Suspeita de Dengue", DENGUE));
    }

    @Test
    void hipoteseComConjuntoEDengue() {
        assertTrue(conta("suspeita de dengue febre mialgia cefaleia", "Suspeita de Dengue", DENGUE));
    }

    @Test
    void sindromeGripalContaPeloConjuntoENaoPelaDiarreia() {
        assertTrue(conta("febre e tosse", "Sindrome gripal", GRIPAL));
        assertFalse(conta("diarreia", "Sindrome gripal", GRIPAL));
        assertFalse(conta("tosse", "Sindrome gripal", GRIPAL));
    }

    @Test
    void sragExigeSinalDeGravidade() {
        String termos = "dispneia, desconforto respiratorio";
        assertFalse(conta("dispneia", "Síndrome Respiratória Aguda Grave", termos));
        assertFalse(conta("febre tosse", "Síndrome Respiratória Aguda Grave", termos));
        assertTrue(conta("febre tosse dispneia", "Síndrome Respiratória Aguda Grave", termos));
    }

    @Test
    void diarreiaContaNaDoencaDiarreica() {
        assertTrue(conta("diarreia", "Doença Diarreica Aguda", "diarreia, vômitos, dor abdominal"));
        assertFalse(conta("vomitos dor abdominal", "Doença Diarreica Aguda", "diarreia, vômitos, dor abdominal"));
    }

    @Test
    void sarampoExigeFebreExantemaESinalCatarral() {
        assertFalse(conta("sarampo febre", "Sarampo", "sarampo"));
        assertTrue(conta("sarampo febre exantema coriza", "Sarampo", "sarampo"));
    }

    @Test
    void mpoxNaoContaSemLesao() {
        assertFalse(conta("mpox febre", "Mpox", "mpox, febre"));
        assertTrue(conta("mpox febre lesoes", "Mpox", "mpox, febre"));
    }

    @Test
    void regraSemDefinicaoExigeAgravoOuDoisSintomas() {
        assertTrue(conta("bacteremia", "IRAS", "bacteremia, febre, calafrios"));
        assertFalse(conta("febre", "IRAS", "bacteremia, febre, calafrios"));
        assertTrue(conta("febre calafrios", "IRAS", "bacteremia, febre, calafrios"));
    }

    @Test
    void regraDeUmSintomaSoContaEsseSintoma() {
        assertTrue(conta("febre", "Febre", "febre"));
    }

    @Test
    void hantaviroseExigeHipoteseFebreEMialgiaEApareceDesdeUmCaso() {
        String termos = "hantavirose, hantavirus, hanta virus";
        assertTrue(conta("suspeita de hantavirose febre mialgia", "Hantavirose", termos));
        assertFalse(conta("febre mialgia", "Hantavirose", termos));
        assertFalse(conta("hantavirus febre", "Hantavirose", termos));
        assertTrue(DefinicaoCasoEpidemiologico.casoUnicoImporta("Hantavirose", termos));
    }

    @Test
    void casoUnicoSoImportaEmAgravoRaroOuGrave() {
        assertTrue(DefinicaoCasoEpidemiologico.casoUnicoImporta("Síndrome Meningítica", "rigidez de nuca, meningite"));
        assertTrue(DefinicaoCasoEpidemiologico.casoUnicoImporta("Síndrome Exantemática", "exantema, suspeita de sarampo, rubéola."));
        assertTrue(DefinicaoCasoEpidemiologico.casoUnicoImporta("Febre amarela", "febre amarela"));
        assertFalse(DefinicaoCasoEpidemiologico.casoUnicoImporta("Sindrome gripal", GRIPAL));
        assertFalse(DefinicaoCasoEpidemiologico.casoUnicoImporta("Febril", "febre, amarela"));
    }

    @Test
    void sqlSoAceitaLiteralNormalizado() {
        String sql = DefinicaoCasoEpidemiologico.predicadoSql("x", "IRAS", "bactere'mia; DROP");
        assertTrue(sql.contains("% bactere mia drop %"), sql);
        assertFalse(sql.contains(";"), sql);
        assertEquals("FALSE", DefinicaoCasoEpidemiologico.predicadoSql("x", "Regra", " , "));
    }
}

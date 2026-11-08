package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SindromeEpidemiologicaTest {

    private static final SindromeEpidemiologica.Sindrome FEBRIL = SindromeEpidemiologica.sindrome("FEBRIL_AGUDA");

    private static SindromeEpidemiologica.Hipotese hipotese(SindromeEpidemiologica.Sindrome s, String nome) {
        return s.hipoteses().stream().filter(h -> h.nome().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    void febreComTosseEGripalENaoFebrilIndiferenciada() {
        List<String> sindromes = SindromeEpidemiologica.sindromesDoTexto("febre tosse dor de garganta cefaleia mialgia");
        assertTrue(sindromes.contains("GRIPAL"));
        assertFalse(sindromes.contains("FEBRIL_AGUDA"));
    }

    @Test
    void umSintomaSoNaoFazSindrome() {
        assertTrue(SindromeEpidemiologica.sindromesDoTexto("febre").isEmpty());
        assertTrue(SindromeEpidemiologica.sindromesDoTexto("febre mialgia").isEmpty());
    }

    @Test
    void dispneiaTransformaGripalEmSrag() {
        assertTrue(SindromeEpidemiologica.sindromesDoTexto("tosse febre dispneia").contains("SRAG"));
        assertFalse(SindromeEpidemiologica.sindromesDoTexto("tosse febre").contains("SRAG"));
    }

    @Test
    void febreComRigidezDeNucaEhNeurologicaGrave() {
        List<String> sindromes = SindromeEpidemiologica.sindromesDoTexto("febre rigidez de nuca petequias vomitos");
        assertTrue(sindromes.contains("NEUROLOGICA_FEBRIL"));
        assertFalse(sindromes.contains("FEBRIL_AGUDA"));
        var a = SindromeEpidemiologica.avaliar(hipotese(SindromeEpidemiologica.sindrome("NEUROLOGICA_FEBRIL"), "Doença meningocócica"),
                "febre rigidez de nuca petequias");
        assertTrue(a.compativel());
        assertFalse(a.registrada());
    }

    /** Sintomas iguais nao escolhem doenca: dor retro-orbitaria reforca dengue, mas nao a registra. */
    @Test
    void pistaReforcaSemRegistrarHipotese() {
        var dengue = SindromeEpidemiologica.avaliar(hipotese(FEBRIL, "Dengue"), "febre mialgia dor retro orbitaria");
        assertTrue(dengue.compativel());
        assertFalse(dengue.registrada());
        assertEquals(List.of("Dor retro-orbitária"), dengue.aFavor());
        assertEquals(SindromeEpidemiologica.Grau.COMPATIVEL, SindromeEpidemiologica.grau(FEBRIL, "febre mialgia dor retro orbitaria"));
        assertEquals(SindromeEpidemiologica.Grau.SINDROMICO, SindromeEpidemiologica.grau(FEBRIL, "febre mialgia cefaleia"));
    }

    @Test
    void antecedenteNaoEhHipoteseAtual() {
        assertFalse(SindromeEpidemiologica.avaliar(hipotese(FEBRIL, "Dengue"), "febre mialgia cefaleia ja teve dengue em 2019").registrada());
        assertFalse(SindromeEpidemiologica.avaliar(hipotese(FEBRIL, "Dengue"), "febre mialgia cefaleia historia previa de dengue").registrada());
        assertTrue(SindromeEpidemiologica.avaliar(hipotese(FEBRIL, "Dengue"), "febre mialgia cefaleia hd dengue").registrada());
    }

    @Test
    void contatoComCasoEhVinculoENaoDiagnostico() {
        String texto = "febre mialgia cefaleia contato com caso confirmado de dengue";
        var a = SindromeEpidemiologica.avaliar(hipotese(FEBRIL, "Dengue"), texto);
        assertTrue(a.vinculo());
        assertFalse(a.registrada());
        assertEquals(SindromeEpidemiologica.Grau.COM_VINCULO, SindromeEpidemiologica.grau(FEBRIL, texto));
        assertTrue(SindromeEpidemiologica.avaliar(hipotese(FEBRIL, "Dengue"), "febre mialgia cefaleia filho com dengue").vinculo());
    }

    /** Exposicao a rato nao e contato com caso. */
    @Test
    void exposicaoAmbientalNaoViraVinculo() {
        var a = SindromeEpidemiologica.avaliar(hipotese(FEBRIL, "Leptospirose"), "febre mialgia cefaleia contato com rato suspeita leptospirose");
        assertFalse(a.vinculo());
        assertTrue(a.registrada());
        assertTrue(a.aFavor().contains("Contato com roedores"));
    }

    @Test
    void vacinaEnfraqueceENaoRegistra() {
        var a = SindromeEpidemiologica.avaliar(hipotese(FEBRIL, "Febre amarela"), "febre mialgia cefaleia vacinado contra febre amarela");
        assertFalse(a.registrada());
        assertTrue(a.contra().contains("Vacinação citada"));
    }

    private static List<SindromeEpidemiologica.Caso> casos(int quantidade, String prefixo, LocalDate data, String bairro, String texto) {
        List<SindromeEpidemiologica.Caso> out = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) out.add(new SindromeEpidemiologica.Caso(prefixo + i, data, bairro, texto));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sindromeDoPainel(Map<String, Object> painel, String codigo) {
        return ((List<Map<String, Object>>) painel.get("sindromes")).stream()
                .filter(s -> codigo.equals(s.get("codigo"))).findFirst().orElseThrow();
    }

    @Test
    void contaPacienteUmaVezENaoDevolveIdentificador() {
        LocalDate fim = LocalDate.of(2026, 11, 8);
        List<SindromeEpidemiologica.Caso> lista = new ArrayList<>();
        lista.add(new SindromeEpidemiologica.Caso("pac77", fim, "CENTRO", "diarreia vomitos"));
        lista.add(new SindromeEpidemiologica.Caso("pac77", fim.minusDays(1), "CENTRO", "diarreia"));
        Map<String, Object> painel = SindromeEpidemiologica.painel(lista, fim.minusDays(6), fim, 1d);
        assertEquals(1L, sindromeDoPainel(painel, "DIARREICA_AGUDA").get("casos"));
        assertFalse(painel.toString().contains("pac77"));
    }

    @Test
    void aglomeradoComVinculoViraPossivelSurto() {
        LocalDate fim = LocalDate.of(2026, 11, 8);
        List<SindromeEpidemiologica.Caso> lista = casos(3, "a", fim, "JARDIM", "diarreia vomitos comeu na festa outras pessoas com mesmos sintomas");
        Map<String, Object> dda = sindromeDoPainel(SindromeEpidemiologica.painel(lista, fim.minusDays(6), fim, 1d), "DIARREICA_AGUDA");
        assertEquals("POSSIVEL_SURTO", dda.get("escala"));
    }

    @Test
    void concentracaoSemVinculoEhSoAglomerado() {
        LocalDate fim = LocalDate.of(2026, 11, 8);
        Map<String, Object> dda = sindromeDoPainel(SindromeEpidemiologica.painel(casos(3, "a", fim, "JARDIM", "diarreia"),
                fim.minusDays(6), fim, 1d), "DIARREICA_AGUDA");
        assertEquals("AGLOMERADO", dda.get("escala"));
    }

    @Test
    void altaSustentadaEEspalhadaEhPossivelEpidemia() {
        LocalDate fim = LocalDate.of(2026, 11, 8);
        LocalDate inicio = fim.minusDays(6);
        String texto = "febre mialgia cefaleia";
        List<SindromeEpidemiologica.Caso> lista = new ArrayList<>();
        // 3 semanas antigas com 2 casos cada; as 2 ultimas com 12 cada, em 4 bairros
        for (int semana = 1; semana <= 3; semana++) {
            lista.addAll(casos(2, "s" + semana + "-", inicio.minusDays(7L * (5 - semana)), "CENTRO", texto));
        }
        for (String bairro : List.of("A", "B", "C", "D")) {
            lista.addAll(casos(3, "ant" + bairro, inicio.minusDays(3), bairro, texto));
            lista.addAll(casos(3, "atu" + bairro, fim, bairro, texto));
        }
        Map<String, Object> febril = sindromeDoPainel(SindromeEpidemiologica.painel(lista, inicio, fim, 1d), "FEBRIL_AGUDA");
        assertEquals("POSSIVEL_EPIDEMIA", febril.get("escala"));
    }

    @Test
    void sindromeGraveComUmCasoPedeAvaliacao() {
        LocalDate fim = LocalDate.of(2026, 11, 8);
        Map<String, Object> neuro = sindromeDoPainel(SindromeEpidemiologica.painel(
                List.of(new SindromeEpidemiologica.Caso("x", fim, "", "febre rigidez de nuca")), fim.minusDays(6), fim, 1d), "NEUROLOGICA_FEBRIL");
        assertEquals("CASO_GRAVE", neuro.get("escala"));
    }

    @Test
    void preFiltroCobreTodasAsSindromes() {
        List<String> termos = SindromeEpidemiologica.termosDeBusca();
        assertTrue(termos.contains("febre"));
        assertTrue(termos.contains("tosse"));
        assertTrue(termos.contains("diarreia"));
    }
}

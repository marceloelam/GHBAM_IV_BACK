package br.com.iv.aps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Cidadao completado pelo cadastro do hospital (dados sinteticos): quando a FCI salva fica
 * "a atualizar" e como a data do hospital (texto livre) e lida.
 */
class ApsCidadaoCadastroDesatualizadoTest {

    private static Map<String, Object> cidadao(String cpf, String cns, String nasc, Map<String, Object> ident) {
        Map<String, Object> c = new HashMap<>();
        c.put("cpf", cpf);
        c.put("cns", cns);
        c.put("data_nascimento", nasc);
        if (ident != null) {
            c.put("dados", Map.of("identificacaoUsuarioCidadao", ident));
        }
        return c;
    }

    @Test
    void semFichaNaoMarca() {
        assertFalse(ApsCidadaoService.cadastroDesatualizado(cidadao("52998224725", null, "2014-11-23", null)));
    }

    @Test
    void fichaCompletaNaoMarca() {
        Map<String, Object> ident = Map.of("cpfCidadao", "52998224725", "dataNascimentoCidadao", "2014-11-23");
        assertFalse(ApsCidadaoService.cadastroDesatualizado(cidadao("52998224725", "898001160655528", "2014-11-23", ident)));
    }

    @Test
    void cpfCompletadoDepoisDaFichaMarca() {
        Map<String, Object> ident = Map.of("stNaoPossuiCpf", true, "dataNascimentoCidadao", "2014-11-23");
        assertTrue(ApsCidadaoService.cadastroDesatualizado(cidadao("52998224725", null, "2014-11-23", ident)));
    }

    @Test
    void cnsSoFaltaQuandoFichaNaoTemCpfNemCns() {
        Map<String, Object> comCpf = Map.of("cpfCidadao", "52998224725", "dataNascimentoCidadao", "2014-11-23");
        assertFalse(ApsCidadaoService.cadastroDesatualizado(cidadao("52998224725", "898001160655528", "2014-11-23", comCpf)));
        Map<String, Object> semDoc = Map.of("dataNascimentoCidadao", "2014-11-23");
        assertTrue(ApsCidadaoService.cadastroDesatualizado(cidadao(null, "898001160655528", "2014-11-23", semDoc)));
    }

    @Test
    void nascimentoCompletadoDepoisDaFichaMarca() {
        Map<String, Object> ident = Map.of("cpfCidadao", "52998224725");
        assertTrue(ApsCidadaoService.cadastroDesatualizado(cidadao("52998224725", null, "2014-11-23", ident)));
    }

    @Test
    void dataDoHospitalNosFormatosDaRecepcao() {
        assertEquals("2014-11-23", ApsCidadaoService.dataPaciente("2014/11/23"));
        assertEquals("2014-11-23", ApsCidadaoService.dataPaciente("23/11/2014"));
        assertEquals("2014-11-23", ApsCidadaoService.dataPaciente("2014-11-23 00:00:00"));
        assertNull(ApsCidadaoService.dataPaciente("sem data"));
    }

    @Test
    void cpfDePreenchimentoNaoEhAceito() {
        assertFalse(ApsValidacao.cpfValido("00000000000"));
        assertTrue(ApsValidacao.cpfValido("52998224725"));
    }
}

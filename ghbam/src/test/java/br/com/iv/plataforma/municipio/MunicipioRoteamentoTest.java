package br.com.iv.plataforma.municipio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class MunicipioRoteamentoTest {

    private static MunicipioRoteamento.Ajustes ajustes(boolean cabecalho) {
        return new MunicipioRoteamento.Ajustes(true, "330200",
                Map.of("330350", "municipio_330350"),
                Map.of("ni.exemplo.com.br", "330350", "itaguai.exemplo.com.br", "330200"),
                cabecalho, false, 20);
    }

    @Test
    void enderecoNaoCadastradoVaiParaOPadrao() {
        assertEquals("330200", MunicipioRoteamento.resolver(ajustes(false), null, null, "10.200.200.7:8080", null));
        assertEquals("330200", MunicipioRoteamento.resolver(ajustes(false), null, null, null, null));
    }

    @Test
    void hostEncaminhadoTemPrioridadeESemPorta() {
        assertEquals("330350", MunicipioRoteamento.resolver(ajustes(false), null,
                "NI.exemplo.com.br:443, proxy", "api.exemplo.com.br", null));
    }

    @Test
    void apiEmOutroDominioUsaOOrigin() {
        assertEquals("330350", MunicipioRoteamento.resolver(ajustes(false), null, null, "api.exemplo.com.br",
                "https://ni.exemplo.com.br"));
        assertEquals("330200", MunicipioRoteamento.resolver(ajustes(false), null, null, "api.exemplo.com.br", "null"));
    }

    @Test
    void cabecalhoSoValeQuandoLiberadoEComMunicipioConhecido() {
        assertEquals("330200", MunicipioRoteamento.resolver(ajustes(false), "330350", null, null, null));
        assertEquals("330350", MunicipioRoteamento.resolver(ajustes(true), "330350", null, null, null));
        assertEquals("330200", MunicipioRoteamento.resolver(ajustes(true), "999999", null, null, null));
    }

    @Test
    void urlDoBancoTrocaSoONome() {
        String url = "jdbc:postgresql://10.0.0.1:5432/hospitalit?tcpKeepAlive=true&applicationName=ghbam-app&x=1";
        assertEquals("jdbc:postgresql://10.0.0.1:5432/municipio_330350?tcpKeepAlive=true"
                + "&applicationName=ghbam-municipio_330350&x=1", MunicipioRoteamento.urlDoBanco(url, "municipio_330350"));
        assertThrows(IllegalStateException.class, () -> MunicipioRoteamento.urlDoBanco("jdbc:h2:mem:x", "a"));
    }

    @Test
    void paresIgnoraLixo() {
        assertEquals(Map.of("a.b", "1", "c", "2"), MunicipioRoteamento.pares(" A.B=1 ,,=x, c = 2, semvalor="));
    }
}

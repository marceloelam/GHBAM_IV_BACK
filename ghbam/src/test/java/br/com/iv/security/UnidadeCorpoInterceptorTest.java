package br.com.iv.security;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.ext.ReaderInterceptorContext;

/** Unidade no corpo de POST/PUT diferente da sessao: registra e, com a chave ligada, 403. */
class UnidadeCorpoInterceptorTest {

    static class Negado extends RuntimeException { }

    public static class CorpoCampo {
        public String sigla;
    }

    public static class Corpo {
        private final String unidade;
        public Corpo(String unidade) { this.unidade = unidade; }
        public String getUnidade() { return unidade; }
        public String getNome() { return "UPA1"; } // campo que nao e' unidade
    }

    @Test
    void detectaUnidadeDeOutraNoObjetoMapaELista() {
        assertNull(UnidadeCorpoInterceptor.unidadeDivergente("HPS", new Corpo("hps")));
        assertNull(UnidadeCorpoInterceptor.unidadeDivergente("HPS", new Corpo(""))); // vazia: servico completa
        assertNull(UnidadeCorpoInterceptor.unidadeDivergente("HPS", new Corpo(null)));
        assertEquals("UPA1", UnidadeCorpoInterceptor.unidadeDivergente("HPS", new Corpo("UPA1")));
        assertEquals("TODAS", UnidadeCorpoInterceptor.unidadeDivergente("HPS", new Corpo("TODAS")));
        assertEquals("UPA1", UnidadeCorpoInterceptor.unidadeDivergente("HPS", Map.of("sigla", "UPA1")));
        assertNull(UnidadeCorpoInterceptor.unidadeDivergente("HPS", Map.of("unidadeDestino", "UPA1")));
        assertEquals("UPA1", UnidadeCorpoInterceptor.unidadeDivergente("HPS",
                List.of(new Corpo("HPS"), new Corpo("UPA1"))));
        assertNull(UnidadeCorpoInterceptor.unidadeDivergente("HPS", "texto"));
        CorpoCampo campo = new CorpoCampo();
        campo.sigla = "UPA1";
        assertEquals("UPA1", UnidadeCorpoInterceptor.unidadeDivergente("HPS", campo));
    }

    @Test
    void soBloqueiaComAChaveLigadaEQuandoOFiltroMarcou() throws Exception {
        assertNotNull(ler(new Corpo("UPA1"), "HPS", false)); // desligada: so registra
        assertThrows(Negado.class, () -> ler(new Corpo("UPA1"), "HPS", true));
        assertNotNull(ler(new Corpo("HPS"), "HPS", true));
        // Sem marca do filtro (multiunidade, leitura, cadastro do paciente): nao avalia.
        assertNotNull(ler(new Corpo("UPA1"), null, true));
    }

    private static Object ler(Object corpo, String sessao, boolean bloquear) throws Exception {
        Map<String, Object> props = new HashMap<>();
        if (sessao != null) {
            props.put(UnidadeEscopoFilter.PROP_CORPO_SESSAO, sessao);
        }
        ReaderInterceptorContext ctx = (ReaderInterceptorContext) Proxy.newProxyInstance(
                ReaderInterceptorContext.class.getClassLoader(), new Class<?>[] { ReaderInterceptorContext.class },
                (p, m, args) -> switch (m.getName()) {
                    case "proceed" -> corpo;
                    case "getProperty" -> props.get(args[0]);
                    case "setProperty" -> {
                        props.put((String) args[0], args[1]);
                        yield null;
                    }
                    default -> null;
                });
        // Fora do Quarkus Response.build() nao funciona: o 403 vira uma excecao marcadora.
        UnidadeCorpoInterceptor interceptor = new UnidadeCorpoInterceptor() {
            @Override
            RuntimeException negar() {
                return new Negado();
            }
        };
        interceptor.bloquear = bloquear;
        return interceptor.aroundReadFrom(ctx);
    }
}

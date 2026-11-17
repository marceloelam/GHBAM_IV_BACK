package br.com.iv.security;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import br.com.iv.util.UnidadeUtil;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

/**
 * Confere a unidade que vem no CORPO de POST/PUT/PATCH (unidade/sigla/unid/yunidade) contra a
 * unidade da sessao. Atua depois do Jackson, sobre o objeto ja lido, sem tocar no fluxo da
 * requisicao. So age quando o {@link UnidadeEscopoFilter} marcou a requisicao (escrita de usuario
 * comum fora do cadastro do paciente). Comeca so registrando (UNIDADE-SHADOW decision=corpo-divergente);
 * com ghbam.security.unidade.corpo.bloquear=true vira 403.
 */
@Provider
public class UnidadeCorpoInterceptor implements ReaderInterceptor {

    private static final Logger LOGGER = Logger.getLogger(UnidadeCorpoInterceptor.class.getName());
    private static final int LIMITE_ITENS_LISTA = 50;

    // Lida na 1a requisicao: o provider nasce antes do ghbam-secrets.properties (ver UnidadeEscopoFilter).
    Boolean bloquear;

    boolean bloquear() {
        Boolean valor = bloquear;
        if (valor == null) {
            valor = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getOptionalValue("ghbam.security.unidade.corpo.bloquear", Boolean.class).orElse(false);
            bloquear = valor;
        }
        return valor;
    }

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
        Object corpo = context.proceed();
        Object sessao = context.getProperty(UnidadeEscopoFilter.PROP_CORPO_SESSAO);
        if (!(sessao instanceof String unidadeSessao) || corpo == null) {
            return corpo;
        }
        String pedida;
        try {
            pedida = unidadeDivergente(unidadeSessao, corpo);
        } catch (RuntimeException e) {
            return corpo; // falha na avaliacao nunca derruba a requisicao
        }
        if (pedida == null) {
            return corpo;
        }
        boolean bloqueia = bloquear();
        LOGGER.warning("UNIDADE-SHADOW decision=corpo-divergente recurso="
                + context.getProperty(UnidadeEscopoFilter.PROP_CORPO_RECURSO)
                + " sessao=" + UnidadeUtil.normalizar(unidadeSessao) + " pedida=" + UnidadeUtil.normalizar(pedida)
                + " bloquearCorpo=" + bloqueia);
        if (bloqueia) {
            context.setProperty("ghbam.unidade.decision", "bloqueada");
            throw negar();
        }
        return corpo;
    }

    /** 403 igual ao do UnidadeEscopoFilter. */
    RuntimeException negar() {
        return new WebApplicationException(Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"erro\":\"UNIDADE_FORA_DA_SESSAO\",\"mensagem\":\"A operacao deve pertencer a unidade da sessao.\"}")
                .build());
    }

    /** Primeira unidade do corpo diferente da sessao (vazia nao conta), ou null. */
    static String unidadeDivergente(String sessao, Object corpo) {
        if (corpo instanceof Collection<?> lista) {
            int n = 0;
            for (Object item : lista) {
                if (n++ >= LIMITE_ITENS_LISTA) {
                    break;
                }
                String achada = item == null ? null : unidadeDivergente(sessao, item);
                if (achada != null) {
                    return achada;
                }
            }
            return null;
        }
        if (corpo instanceof Map<?, ?> mapa) {
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (e.getKey() != null && e.getValue() instanceof String valor
                        && ehCampoUnidade(e.getKey().toString()) && diverge(sessao, valor)) {
                    return valor;
                }
            }
            return null;
        }
        if (corpo instanceof CharSequence || corpo instanceof Number || corpo instanceof Boolean
                || corpo.getClass().isArray()) {
            return null;
        }
        for (Method m : corpo.getClass().getMethods()) {
            String nome = m.getName();
            if (m.getParameterCount() != 0 || m.getReturnType() != String.class || !nome.startsWith("get")
                    || !ehCampoUnidade(nome.substring(3))) {
                continue;
            }
            try {
                String valor = (String) m.invoke(corpo);
                if (diverge(sessao, valor)) {
                    return valor;
                }
            } catch (ReflectiveOperationException ignored) {
                // getter que falha nao decide nada
            }
        }
        // DTOs com campo publico sem getter (ex.: ChatLimparRequest.unidade).
        for (java.lang.reflect.Field f : corpo.getClass().getFields()) {
            if (f.getType() != String.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())
                    || !ehCampoUnidade(f.getName())) {
                continue;
            }
            try {
                String valor = (String) f.get(corpo);
                if (diverge(sessao, valor)) {
                    return valor;
                }
            } catch (ReflectiveOperationException ignored) {
                // campo inacessivel nao decide nada
            }
        }
        return null;
    }

    private static boolean ehCampoUnidade(String nome) {
        return UnidadeEscopoFilter.PARAMETROS_UNIDADE.contains(nome.toLowerCase(Locale.ROOT));
    }

    // Vazia no corpo nao bloqueia (servicos costumam completar com a sessao); TODAS ou outra sim.
    private static boolean diverge(String sessao, String valor) {
        String decisao = UnidadeEscopoFilter.avaliar(sessao, valor);
        return "divergente".equals(decisao) || "todas".equals(decisao);
    }
}

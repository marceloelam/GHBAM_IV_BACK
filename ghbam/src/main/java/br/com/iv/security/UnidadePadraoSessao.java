package br.com.iv.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca recurso/metodo que, sem unidade na requisicao, usa a unidade da SESSAO (nunca "todas").
 * Nesses, unidade vazia/ausente nao e' bloqueada pelo {@link UnidadeEscopoFilter}
 * (ghbam.security.unidade.vazia.bloquear). So marcar depois de conferir o fallback no codigo.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface UnidadePadraoSessao {
}

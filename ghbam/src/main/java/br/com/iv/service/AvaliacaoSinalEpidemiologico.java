package br.com.iv.service;

/**
 * Decide a situacao de uma regra da vigilancia. Sinal e motivo para investigar: nao confirma surto.
 *
 * A comparacao so com as janelas anteriores apaga o alerta no pico, porque a propria epidemia eleva a
 * media. Por isso tambem se compara com a mesma epoca de anos anteriores, e o sinal aberto continua
 * "em curso" ate alguem encerrar a ocorrencia. Limites a calibrar com a vigilancia e dados reais.
 */
public final class AvaliacaoSinalEpidemiologico {

    private AvaliacaoSinalEpidemiologico() {}

    public enum Estado { NORMAL, CASO_RARO, SUBINDO, ACIMA_DA_EPOCA, EM_CURSO, REVISADO }

    /**
     * @param mediaEpoca media da mesma janela em anos anteriores; null = sem historico nesses anos
     * @param casoUnico agravo raro ou grave: um caso ja justifica investigar
     * @param revisadoNaJanela alguem encerrou a ocorrencia desta regra dentro da janela atual
     */
    public record Entrada(long atual, double mediaRecente, Double mediaEpoca, double ajusteRecente, double ajusteEpoca,
                          int minimo, double fator, boolean casoUnico, boolean ocorrenciaAberta, boolean revisadoNaJanela) {}

    public record Resultado(Estado estado, boolean compativelComEpoca, double esperadoRecente, Double esperadoEpoca) {
        public boolean alerta() {
            return estado != Estado.NORMAL && estado != Estado.REVISADO;
        }
        /** Em curso nao abre nem atualiza a ocorrencia: ela ja existe e a contagem pode estar so estavel. */
        public boolean registrarOcorrencia() {
            return estado == Estado.CASO_RARO || estado == Estado.SUBINDO || estado == Estado.ACIMA_DA_EPOCA;
        }
    }

    private static final double SIGNIFICANCIA = 0.05;

    public static Resultado avaliar(Entrada e) {
        double esperadoRecente = e.mediaRecente() * e.ajusteRecente();
        Double esperadoEpoca = e.mediaEpoca() == null ? null : e.mediaEpoca() * e.ajusteEpoca();
        if (e.atual() < Math.max(1, e.minimo())) return new Resultado(Estado.NORMAL, false, esperadoRecente, esperadoEpoca);
        // Agravo raro nao fica sujeito a "revisado": um caso novo depois de encerrar precisa aparecer.
        if (e.casoUnico()) return new Resultado(Estado.CASO_RARO, false, esperadoRecente, esperadoEpoca);

        boolean acimaRecente = acima(e.atual(), esperadoRecente, e.fator());
        boolean acimaEpoca = esperadoEpoca != null && acima(e.atual(), esperadoEpoca, e.fator());
        if (!acimaRecente && !acimaEpoca) {
            return new Resultado(e.ocorrenciaAberta() ? Estado.EM_CURSO : Estado.NORMAL, false, esperadoRecente, esperadoEpoca);
        }
        if (e.revisadoNaJanela() && !e.ocorrenciaAberta()) return new Resultado(Estado.REVISADO, false, esperadoRecente, esperadoEpoca);
        boolean compativel = acimaRecente && esperadoEpoca != null && !acimaEpoca;
        return new Resultado(acimaRecente ? Estado.SUBINDO : Estado.ACIMA_DA_EPOCA, compativel, esperadoRecente, esperadoEpoca);
    }

    /** Sem esperado nao ha o que comparar e vale o minimo. Com poucos casos, o fator sozinho dispara por acaso. */
    private static boolean acima(long atual, double esperado, double fator) {
        if (esperado <= 0) return true;
        return atual >= esperado * fator && probabilidadeAoAcaso(atual, esperado) < SIGNIFICANCIA;
    }

    /** P(X >= casos) em Poisson com a media esperada; aproximacao normal quando a media e alta. */
    static double probabilidadeAoAcaso(long casos, double esperado) {
        if (casos <= 0) return 1d;
        if (esperado >= 50) {
            double z = (casos - 0.5 - esperado) / Math.sqrt(esperado);
            return z >= 1.645 ? 0d : 1d;
        }
        double termo = Math.exp(-esperado);
        double abaixo = 0d;
        for (long k = 0; k < casos; k++) {
            abaixo += termo;
            termo *= esperado / (k + 1);
        }
        return Math.max(0d, 1d - abaixo);
    }

    /**
     * Quanto subiu o volume de atendimentos em relacao a referencia. Limitado a 2x porque a propria epidemia
     * aumenta o movimento, e nunca abaixo de 1x: movimento menor nao pode tornar poucos casos um alerta.
     */
    public static double ajustePorAtendimentos(double atendimentosAtuais, double atendimentosReferencia) {
        if (atendimentosReferencia <= 0) return 1d;
        return Math.max(1d, Math.min(2d, atendimentosAtuais / atendimentosReferencia));
    }
}

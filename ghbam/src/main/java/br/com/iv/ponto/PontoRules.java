package br.com.iv.ponto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Regras do ponto sem banco: alternancia entrada/saida, pares do dia e horas previstas. */
public final class PontoRules {

    public static final String ENTRADA = "ENTRADA";
    public static final String SAIDA = "SAIDA";
    /** Plantao de 24h ainda fecha como saida; acima disso a marcacao abre nova jornada. */
    public static final long JANELA_SAIDA_HORAS = 26;
    /** Evita registro duplicado por dois cliques ou duas leituras seguidas. */
    public static final long INTERVALO_MINIMO_MINUTOS = 2;

    private PontoRules() {
    }

    public record Marca(String tipo, LocalDateTime quando) {
    }

    public record Intervalo(LocalDateTime inicio, LocalDateTime fim) {
    }

    /** Par entrada-saida; saida nula = jornada sem fechamento (esqueceu de bater). */
    public record Par(LocalDateTime entrada, LocalDateTime saida, long minutos) {
        public boolean incompleto() {
            return entrada == null || saida == null;
        }
    }

    /** Proxima marcacao: saida se a ultima foi entrada dentro da janela; senao, entrada. */
    public static String proximoTipo(Marca ultima, LocalDateTime agora) {
        if (ultima == null || !ENTRADA.equals(ultima.tipo())) return ENTRADA;
        long horas = Duration.between(ultima.quando(), agora).toHours();
        return horas < JANELA_SAIDA_HORAS ? SAIDA : ENTRADA;
    }

    public static boolean duplicada(Marca ultima, LocalDateTime agora) {
        return ultima != null && Duration.between(ultima.quando(), agora).toMinutes() < INTERVALO_MINIMO_MINUTOS;
    }

    /** Monta os pares em ordem cronologica; saida sem entrada ou entrada sem saida ficam incompletos. */
    public static List<Par> parear(List<Marca> marcas) {
        List<Par> pares = new ArrayList<>();
        LocalDateTime aberta = null;
        for (Marca m : marcas) {
            if (ENTRADA.equals(m.tipo())) {
                if (aberta != null) pares.add(new Par(aberta, null, 0));
                aberta = m.quando();
            } else {
                if (aberta == null) {
                    pares.add(new Par(null, m.quando(), 0));
                } else {
                    pares.add(new Par(aberta, m.quando(), Math.max(0, Duration.between(aberta, m.quando()).toMinutes())));
                    aberta = null;
                }
            }
        }
        if (aberta != null) pares.add(new Par(aberta, null, 0));
        return pares;
    }

    public static long minutosTrabalhados(List<Par> pares) {
        return pares.stream().mapToLong(Par::minutos).sum();
    }

    /** Minutos previstos na escala, recortados ao periodo (plantao que atravessa o mes conta so a parte dentro). */
    public static long minutosPrevistos(List<Intervalo> escala, LocalDateTime inicio, LocalDateTime fim) {
        long total = 0;
        for (Intervalo i : escala) {
            if (i.inicio() == null || i.fim() == null || !i.fim().isAfter(i.inicio())) continue;
            LocalDateTime a = i.inicio().isBefore(inicio) ? inicio : i.inicio();
            LocalDateTime b = i.fim().isAfter(fim) ? fim : i.fim();
            if (b.isAfter(a)) total += Duration.between(a, b).toMinutes();
        }
        return total;
    }

    public static String horas(long minutos) {
        long abs = Math.abs(minutos);
        return (minutos < 0 ? "-" : "") + (abs / 60) + "h" + String.format("%02d", abs % 60);
    }
}

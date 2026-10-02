package br.com.iv.util;

import java.util.Map;

public class ClassificacaoRiscoResponse {
    private Map<String, Long> dia;
    private Map<String, Long> semana;
    private Map<String, Long> mes;
    private Map<String, Long> ano;

    private long totalDia;
    private long totalSemana;
    private long totalMes;
    private long totalAno;

    // Construtor
    public ClassificacaoRiscoResponse(Map<String, Long> dia, Map<String, Long> semana,
                                      Map<String, Long> mes, Map<String, Long> ano) {
        this.dia = dia;
        this.semana = semana;
        this.mes = mes;
        this.ano = ano;

        this.totalDia = calcularTotal(dia);
        this.totalSemana = calcularTotal(semana);
        this.totalMes = calcularTotal(mes);
        this.totalAno = calcularTotal(ano);
    }

    // Método auxiliar para somar os valores de um mapa
    private long calcularTotal(Map<String, Long> mapa) {
        return mapa != null ? mapa.values().stream().mapToLong(Long::longValue).sum() : 0L;
    }

    // Getters
    public Map<String, Long> getDia() {
        return dia;
    }

    public Map<String, Long> getSemana() {
        return semana;
    }

    public Map<String, Long> getMes() {
        return mes;
    }

    public Map<String, Long> getAno() {
        return ano;
    }

    public long getTotalDia() {
        return totalDia;
    }

    public long getTotalSemana() {
        return totalSemana;
    }

    public long getTotalMes() {
        return totalMes;
    }

    public long getTotalAno() {
        return totalAno;
    }
}

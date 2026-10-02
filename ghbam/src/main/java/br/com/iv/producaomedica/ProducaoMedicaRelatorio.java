package br.com.iv.producaomedica;
import java.time.LocalDate;
import java.util.List;
public record ProducaoMedicaRelatorio(LocalDate inicio, LocalDate fim, String unidade, String matricula,
        long totalAtendimentos, long pacientesUnicos, long diasAtivos, double mediaDia, long altas,
        long semDesfecho, Comparativo comparativo, List<Agrupamento> porDia, List<Agrupamento> porSemana,
        List<Agrupamento> porMes, List<Agrupamento> porAno, List<Medico> porMedico,
        List<Agrupamento> porUnidade, List<Agrupamento> porSetor, List<Agrupamento> porDesfecho,
        List<ProducaoMedicaLinha> detalhes) {
    public record Agrupamento(String chave, long total) {}
    public record Medico(String matricula, String nome, String crm, String foto, boolean ativo, long total) {}
    public record Comparativo(long periodoAtual, long periodoAnterior, double variacaoPercentual) {}
}

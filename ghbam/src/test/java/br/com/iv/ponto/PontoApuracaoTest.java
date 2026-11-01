package br.com.iv.ponto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PontoApuracaoTest {

    private static final LocalDate SEG = LocalDate.of(2026, 10, 26); // segunda
    private static final LocalDateTime DEPOIS = LocalDateTime.of(2026, 11, 5, 0, 0);

    private static PontoRules.Intervalo plantao(LocalDate d, int hIni, int horas) {
        LocalDateTime i = d.atTime(hIni, 0);
        return new PontoRules.Intervalo(i, i.plusHours(horas));
    }

    private static PontoApuracao.Batida b(String tipo, LocalDateTime t) {
        return new PontoApuracao.Batida(null, tipo, t, "FACIAL", false);
    }

    private static PontoApuracao.Resultado apurar(LocalDate de, LocalDate ate, List<PontoRules.Intervalo> escala,
            List<PontoApuracao.Batida> batidas, List<PontoApuracao.Ocorrencia> ocorr) {
        return PontoApuracao.apurar(de, ate, escala, batidas, ocorr, Map.of(), DEPOIS);
    }

    @Test
    void toleranciaDeDezMinutosNaoGeraSaldo() {
        var r = apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)),
                List.of(b("ENTRADA", SEG.atTime(7, 3)), b("SAIDA", SEG.atTime(19, 8))), List.of());
        assertEquals(0, r.totais().saldo());
        assertEquals(725, r.totais().trabalhado());
    }

    @Test
    void horaExtraEmDiaUtilE50EmDomingoE100() {
        LocalDate dom = SEG.minusDays(1);
        var r = apurar(dom, SEG, List.of(plantao(dom, 7, 12), plantao(SEG, 7, 12)),
                List.of(b("ENTRADA", dom.atTime(7, 0)), b("SAIDA", dom.atTime(20, 0)),
                        b("ENTRADA", SEG.atTime(7, 0)), b("SAIDA", SEG.atTime(20, 0))), List.of());
        assertEquals(60, r.totais().extra100());
        assertEquals(60, r.totais().extra50());
        assertEquals(120, r.totais().saldo());
    }

    @Test
    void plantaoNoturnoFicaNoDiaEmQueComecaEContaAdicional() {
        var r = apurar(SEG, SEG.plusDays(1), List.of(plantao(SEG, 19, 12)),
                List.of(b("ENTRADA", SEG.atTime(18, 55)), b("SAIDA", SEG.plusDays(1).atTime(7, 0))), List.of());
        var dia = r.dias().get(0);
        assertEquals(725, dia.trabalhado());
        assertEquals(420, dia.noturno()); // 22h as 5h
        assertEquals(0, r.dias().get(1).trabalhado());
        assertEquals(480, r.totais().noturnoReduzido());
    }

    @Test
    void plantaoSemBatidaEFaltaMasAtestadoAbona() {
        var escala = List.of(plantao(SEG, 7, 12), plantao(SEG.plusDays(2), 7, 12));
        var atestado = new PontoApuracao.Ocorrencia("ATESTADO_MEDICO", SEG.plusDays(2), SEG.plusDays(2), null);
        var r = apurar(SEG, SEG.plusDays(2), escala, List.of(), List.of(atestado));
        assertEquals(1, r.totais().faltasDias());
        assertEquals(-720, r.totais().saldo());
        assertEquals(720, r.totais().abonado());
        assertEquals("Atestado médico", r.dias().get(2).situacao());
    }

    @Test
    void folgaCompensatoriaDebitaBancoSemSerFalta() {
        var folga = new PontoApuracao.Ocorrencia("FOLGA_COMPENSATORIA", SEG, SEG, null);
        var r = apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)), List.of(), List.of(folga));
        assertEquals(0, r.totais().faltasDias());
        assertEquals(-720, r.totais().saldo());
        assertEquals(720, r.totais().compensacaoMinutos());
    }

    @Test
    void saidaEmAbertoFicaPendenteForaDoSaldo() {
        var r = apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)), List.of(b("ENTRADA", SEG.atTime(7, 0))), List.of());
        assertFalse(r.dias().get(0).apurado());
        assertEquals(0, r.totais().saldo());
        assertEquals(1, r.totais().saidasEmAberto());
        assertEquals(0, r.totais().faltasDias());
    }

    @Test
    void atrasoAcimaDeCincoMinutosEAbonoParcial() {
        var consulta = new PontoApuracao.Ocorrencia("ATESTADO_MEDICO", SEG, SEG, 120);
        var r = apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)),
                List.of(b("ENTRADA", SEG.atTime(7, 20)), b("SAIDA", SEG.atTime(17, 0))), List.of(consulta));
        assertEquals(20, r.totais().atrasos());
        // a saida 2h antes esta justificada pelo abono: nao conta como saida antecipada
        assertEquals(0, r.totais().saidasAntecipadas());
        assertEquals(120, r.totais().abonado());
        assertEquals(-20, r.totais().saldo());
        assertEquals("Atestado médico (parcial 2h00)", r.dias().get(0).situacao());
    }

    @Test
    void plantaoFuturoNaoViraFalta() {
        var r = PontoApuracao.apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)), List.of(), List.of(), Map.of(), SEG.atTime(10, 0));
        assertEquals(0, r.totais().faltasDias());
        assertEquals("A apurar", r.dias().get(0).situacao());
    }

    @Test
    void anuladaApareceComoDesconsideradaENaoConta() {
        var anulada = new PontoApuracao.Batida(9L, "SAIDA", SEG.atTime(8, 0), "FACIAL", true);
        var r = apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)),
                List.of(b("ENTRADA", SEG.atTime(7, 0)), anulada, b("SAIDA", SEG.atTime(19, 0))), List.of());
        assertEquals(720, r.totais().trabalhado());
        assertTrue(r.dias().get(0).marcacoes().stream().anyMatch(m -> "D".equals(m.tratamento())));
    }

    @Test
    void bancoNuncaFicaNegativoODebitoSemSaldoViraDesconto() {
        // atraso de 25 min sem banco: vai para desconto, banco fica 0
        var r = apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)),
                List.of(b("ENTRADA", SEG.atTime(7, 25)), b("SAIDA", SEG.atTime(19, 0))), List.of());
        assertEquals(0, r.totais().bancoFinal());
        assertEquals(25, r.totais().horasDescontar());
    }

    @Test
    void creditoEntraNoBancoEDebitoConsomeSoOQueTem() {
        LocalDate ter = SEG.plusDays(1);
        // seg: +60 de credito; ter: atraso 90 -> usa 60 do banco e desconta 30
        var r = apurar(SEG, ter, List.of(plantao(SEG, 7, 12), plantao(ter, 7, 12)),
                List.of(b("ENTRADA", SEG.atTime(7, 0)), b("SAIDA", SEG.atTime(20, 0)),
                        b("ENTRADA", ter.atTime(8, 30)), b("SAIDA", ter.atTime(19, 0))), List.of());
        assertEquals(60, r.totais().creditoBanco());
        assertEquals(60, r.totais().debitoBanco());
        assertEquals(30, r.totais().horasDescontar());
        assertEquals(0, r.totais().bancoFinal());
    }

    @Test
    void faltaVaiDiretoParaDescontoSemConsumirBanco() {
        var r = PontoApuracao.apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)), List.of(), List.of(), Map.of(), DEPOIS, 300);
        assertEquals(300, r.totais().bancoFinal());
        assertEquals(720, r.totais().horasDescontar());
    }

    @Test
    void folgaCompensatoriaUsaOBancoDisponivel() {
        var folga = new PontoApuracao.Ocorrencia("FOLGA_COMPENSATORIA", SEG, SEG, null);
        var r = PontoApuracao.apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)), List.of(), List.of(folga), Map.of(), DEPOIS, 800);
        assertEquals(720, r.totais().debitoBanco());
        assertEquals(80, r.totais().bancoFinal());
        assertEquals(0, r.totais().horasDescontar());
    }

    @Test
    void faltaJustificadaUsaOBancoAntesDeDescontar() {
        var fj = new PontoApuracao.Ocorrencia("FALTA_JUSTIFICADA", SEG, SEG, null);
        var r = PontoApuracao.apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)), List.of(), List.of(fj), Map.of(), DEPOIS, 300);
        assertEquals(300, r.totais().debitoBanco());
        assertEquals(0, r.totais().bancoFinal());
        assertEquals(420, r.totais().horasDescontar());
        assertEquals(1, r.totais().faltasJustificadasDias());
    }

    @Test
    void faltaInjustificadaNaoUsaOBanco() {
        var fi = new PontoApuracao.Ocorrencia("FALTA_INJUSTIFICADA", SEG, SEG, null);
        var r = PontoApuracao.apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)), List.of(), List.of(fi), Map.of(), DEPOIS, 300);
        assertEquals(300, r.totais().bancoFinal());
        assertEquals(720, r.totais().horasDescontar());
    }

    @Test
    void folgaParcialPeloBancoAutorizaSairMaisCedo() {
        var folga = new PontoApuracao.Ocorrencia("FOLGA_COMPENSATORIA", SEG, SEG, 180);
        var r = PontoApuracao.apurar(SEG, SEG, List.of(plantao(SEG, 7, 12)),
                List.of(b("ENTRADA", SEG.atTime(7, 0)), b("SAIDA", SEG.atTime(16, 0))), List.of(folga), Map.of(), DEPOIS, 200);
        assertEquals(0, r.totais().saidasAntecipadas());
        assertEquals(180, r.totais().debitoBanco());
        assertEquals(20, r.totais().bancoFinal());
        assertEquals(0, r.totais().horasDescontar());
        assertEquals(180, r.totais().compensacaoMinutos());
        assertEquals("Folga compensatória (parcial 3h00)", r.dias().get(0).situacao());
    }

    @Test
    void feriadosNacionaisMoveis() {
        var f = PontoApuracao.feriadosNacionais(2026);
        assertEquals("Paixão de Cristo", f.get(LocalDate.of(2026, 4, 3)).descricao());
        assertEquals("Corpus Christi", f.get(LocalDate.of(2026, 6, 4)).descricao());
        assertTrue(f.get(LocalDate.of(2026, 2, 17)).facultativo());
        assertTrue(f.containsKey(LocalDate.of(2026, 11, 20)));
    }
}

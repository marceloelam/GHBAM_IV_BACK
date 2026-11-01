package br.com.iv.ponto;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Apuracao dia a dia da folha de ponto, sem banco: escala x batidas x ocorrencias x feriados.
 *
 * Convencoes (as mesmas da folha impressa):
 * - o plantao pertence ao dia em que comeca; a jornada pertence ao dia do plantao que ela
 *   cobre (chegada ate 6h antes), senao ao dia da entrada. Plantao noturno nao vira dois dias;
 * - tolerancia de 10 min por dia (CLT art. 58 par. 1; o municipio pode adotar a mesma regra):
 *   diferenca ate 10 min nao gera extra nem debito; atraso/saida antecipada so acima de 5 min;
 * - dia com batida em aberto ou plantao ainda nao terminado fica pendente e fora do saldo;
 * - plantao terminado sem nenhuma batida e sem ocorrencia que abone = falta.
 */
public final class PontoApuracao {

    public static final long TOLERANCIA_DIA_MIN = 10;
    public static final long TOLERANCIA_MARCACAO_MIN = 5;
    /** Chegada antecipada / saida tardia ainda ligada ao plantao. */
    public static final long JANELA_PLANTAO_HORAS = 6;

    private PontoApuracao() {
    }

    /** O que a ocorrencia faz com o dia. */
    public enum Efeito {
        /** Dia justificado e remunerado: sai do previsto, nao e falta. */
        ABONA,
        FERIAS,
        /** Sai do previsto, mas e afastamento sem remuneracao (vai destacado para a folha). */
        SEM_VENCIMENTO,
        /** Fica no previsto e debita o banco de horas; nao e falta. */
        COMPENSACAO,
        FALTA_JUSTIFICADA,
        FALTA_INJUSTIFICADA
    }

    public enum TipoOcorrencia {
        FERIAS("FE", "Férias", Efeito.FERIAS),
        ATESTADO_MEDICO("AM", "Atestado médico", Efeito.ABONA),
        ATESTADO_ACOMPANHANTE("AA", "Atestado de acompanhante (familiar)", Efeito.ABONA),
        LICENCA_SAUDE("LS", "Licença para tratamento de saúde", Efeito.ABONA),
        AFASTAMENTO_PREVIDENCIARIO("AP", "Afastamento previdenciário (INSS/perícia)", Efeito.ABONA),
        ACIDENTE_TRABALHO("AT", "Acidente de trabalho", Efeito.ABONA),
        LICENCA_MATERNIDADE("LM", "Licença-maternidade", Efeito.ABONA),
        LICENCA_PATERNIDADE("LP", "Licença-paternidade", Efeito.ABONA),
        LICENCA_NOJO("LN", "Licença nojo (falecimento)", Efeito.ABONA),
        LICENCA_GALA("LG", "Licença gala (casamento)", Efeito.ABONA),
        LICENCA_PREMIO("LR", "Licença-prêmio", Efeito.ABONA),
        DOACAO_SANGUE("DS", "Doação de sangue", Efeito.ABONA),
        SERVICO_OBRIGATORIO("SO", "Júri, serviço eleitoral ou convocação judicial", Efeito.ABONA),
        CURSO_SERVICO_EXTERNO("SE", "Curso, capacitação ou serviço externo", Efeito.ABONA),
        ABONO_CHEFIA("AB", "Abono da chefia", Efeito.ABONA),
        LICENCA_SEM_VENCIMENTO("SV", "Licença sem vencimentos", Efeito.SEM_VENCIMENTO),
        FOLGA_COMPENSATORIA("FC", "Folga compensatória (banco de horas)", Efeito.COMPENSACAO),
        FALTA_JUSTIFICADA("FJ", "Falta justificada (sem abono)", Efeito.FALTA_JUSTIFICADA),
        FALTA_INJUSTIFICADA("FI", "Falta injustificada", Efeito.FALTA_INJUSTIFICADA);

        public final String codigo;
        public final String descricao;
        public final Efeito efeito;

        TipoOcorrencia(String codigo, String descricao, Efeito efeito) {
            this.codigo = codigo;
            this.descricao = descricao;
            this.efeito = efeito;
        }

        public static TipoOcorrencia de(String nome) {
            if (nome == null) return null;
            try {
                return valueOf(nome.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Batida bruta (inclusive anulada). Tratamento: I = incluida pelo RH, D = desconsiderada (anulada). */
    public record Batida(Long id, String tipo, LocalDateTime quando, String origem, boolean anulada) {
        public String tratamento() {
            if (anulada) return "D";
            return "AJUSTE".equals(origem) ? "I" : "";
        }
    }

    public record Ocorrencia(String tipo, LocalDate inicio, LocalDate fim, Integer minutos) {
        boolean cobre(LocalDate d) {
            return !d.isBefore(inicio) && !d.isAfter(fim);
        }
    }

    public record DiaFeriado(String descricao, String tipo) {
        public boolean facultativo() {
            return "PONTO_FACULTATIVO".equals(tipo);
        }
    }

    public record Dia(LocalDate data, String diaSemana, String feriado, boolean domingoOuFeriado,
            List<PontoRules.Intervalo> escala, List<Batida> marcacoes, List<PontoRules.Par> jornadas,
            String ocorrencia, String ocorrenciaCodigo,
            long previsto, long abonado, long trabalhado, long noturno, long extra50, long extra100,
            long atraso, long saidaAntecipada, long falta, long saldo, boolean apurado, String situacao,
            long credito, long debitoBanco, long descontar, long banco) {
    }

    /**
     * saldo = trabalhado - previsto liquido (informativo, pode ser negativo).
     * O BANCO nunca fica negativo: credito entra; debito so consome o que houver e o resto
     * vira horasDescontar (desconto em folha). Falta de dia inteiro vai direto para desconto.
     */
    public record Totais(long previsto, long abonado, long trabalhado, long saldo, long extra50, long extra100,
            long noturno, long noturnoReduzido, long trabalhadoDomingoFeriado, long atrasos, long saidasAntecipadas,
            long faltasDias, long faltasMinutos, long faltasJustificadasDias, long compensacaoMinutos,
            long semVencimentoDias, long diasTrabalhados, long plantoes, long saidasEmAberto, long entradasEmAberto,
            long diasPendentes, Map<String, Long> diasPorOcorrencia,
            long bancoInicial, long creditoBanco, long debitoBanco, long horasDescontar, long bancoFinal) {
    }

    public record Resultado(List<Dia> dias, Totais totais) {
        public List<PontoRules.Par> jornadas() {
            return dias.stream().flatMap(d -> d.jornadas().stream()).toList();
        }
    }

    private record ParBatidas(Batida entrada, Batida saida) {
        PontoRules.Par par() {
            LocalDateTime e = entrada == null ? null : entrada.quando();
            LocalDateTime s = saida == null ? null : saida.quando();
            long min = e != null && s != null ? Math.max(0, Duration.between(e, s).toMinutes()) : 0;
            return new PontoRules.Par(e, s, min);
        }
    }

    private static final String[] SEMANA = { "SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM" };

    /**
     * @param escala plantoes (podem sobrar dias fora do periodo; so contam os que comecam nele)
     * @param batidas todas as batidas do servidor perto do periodo, inclusive anuladas
     */
    public static Resultado apurar(LocalDate de, LocalDate ate, List<PontoRules.Intervalo> escala, List<Batida> batidas,
            List<Ocorrencia> ocorrencias, Map<LocalDate, DiaFeriado> feriados, LocalDateTime agora) {
        return apurar(de, ate, escala, batidas, ocorrencias, feriados, agora, 0);
    }

    /** @param bancoInicial saldo do banco de horas no dia anterior a "de" (nunca negativo) */
    public static Resultado apurar(LocalDate de, LocalDate ate, List<PontoRules.Intervalo> escala, List<Batida> batidas,
            List<Ocorrencia> ocorrencias, Map<LocalDate, DiaFeriado> feriados, LocalDateTime agora, long bancoInicial) {
        long banco = Math.max(0, bancoInicial);
        long tCredito = 0, tDebitoBanco = 0, tDescontar = 0;
        List<PontoRules.Intervalo> plantoes = escala.stream()
                .filter(i -> i.inicio() != null && i.fim() != null && i.fim().isAfter(i.inicio()))
                .sorted(Comparator.comparing(PontoRules.Intervalo::inicio)).toList();

        // Pareia as validas mantendo a referencia a batida (para o espelho mostrar origem/tratamento).
        List<Batida> validas = batidas.stream().filter(b -> !b.anulada()).sorted(Comparator.comparing(Batida::quando)).toList();
        List<ParBatidas> pares = new ArrayList<>();
        Batida aberta = null;
        for (Batida b : validas) {
            if (PontoRules.ENTRADA.equals(b.tipo())) {
                if (aberta != null) pares.add(new ParBatidas(aberta, null));
                aberta = b;
            } else if (aberta != null && Duration.between(aberta.quando(), b.quando()).toHours() < PontoRules.JANELA_SAIDA_HORAS) {
                pares.add(new ParBatidas(aberta, b));
                aberta = null;
            } else {
                if (aberta != null) pares.add(new ParBatidas(aberta, null));
                aberta = null;
                pares.add(new ParBatidas(null, b));
            }
        }
        if (aberta != null) pares.add(new ParBatidas(aberta, null));

        Map<LocalDate, List<ParBatidas>> paresPorDia = new TreeMap<>();
        for (ParBatidas p : pares) paresPorDia.computeIfAbsent(diaDoPar(p, plantoes), k -> new ArrayList<>()).add(p);
        Map<LocalDate, List<Batida>> anuladasPorDia = new TreeMap<>();
        for (Batida b : batidas) {
            if (b.anulada()) anuladasPorDia.computeIfAbsent(diaDoInstante(b.quando(), plantoes, PontoRules.ENTRADA.equals(b.tipo())), k -> new ArrayList<>()).add(b);
        }

        List<Dia> dias = new ArrayList<>();
        long tPrev = 0, tAbon = 0, tTrab = 0, tSaldo = 0, tE50 = 0, tE100 = 0, tNot = 0, tDomFer = 0, tAtr = 0, tSaida = 0;
        long tFaltaDias = 0, tFaltaMin = 0, tFaltaJust = 0, tComp = 0, tSemVenc = 0, tDiasTrab = 0, tPlant = 0;
        long tSaidaAberta = 0, tEntradaAberta = 0, tPend = 0;
        Map<String, Long> diasPorOcorrencia = new LinkedHashMap<>();

        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            final LocalDate dia = d;
            List<PontoRules.Intervalo> escalaDia = plantoes.stream().filter(i -> i.inicio().toLocalDate().equals(dia)).toList();
            List<ParBatidas> paresDia = paresPorDia.getOrDefault(dia, List.of());
            DiaFeriado fer = feriados == null ? null : feriados.get(dia);
            boolean domFer = dia.getDayOfWeek() == DayOfWeek.SUNDAY || (fer != null && !fer.facultativo());

            // Ocorrencia de dia inteiro tem prioridade; as parciais (minutos) somam abono.
            TipoOcorrencia inteira = null;
            TipoOcorrencia parcial = null;
            long abonoParcial = 0;
            // folga compensatoria de parte do plantao (ex.: sair 3h antes usando o banco)
            long compensacaoParcial = 0;
            for (Ocorrencia o : ocorrencias) {
                if (!o.cobre(dia)) continue;
                TipoOcorrencia t = TipoOcorrencia.de(o.tipo());
                if (t == null) continue;
                if (o.minutos() != null && o.minutos() > 0) {
                    if (t.efeito == Efeito.ABONA) {
                        abonoParcial += o.minutos();
                        if (parcial == null) parcial = t;
                    } else if (t.efeito == Efeito.COMPENSACAO) {
                        compensacaoParcial += o.minutos();
                    }
                } else if (inteira == null) {
                    inteira = t;
                }
            }
            if (inteira != null) diasPorOcorrencia.merge(inteira.codigo, 1L, Long::sum);

            long previsto = escalaDia.stream().mapToLong(i -> Duration.between(i.inicio(), i.fim()).toMinutes()).sum();
            long abonado;
            if (inteira != null && (inteira.efeito == Efeito.ABONA || inteira.efeito == Efeito.FERIAS || inteira.efeito == Efeito.SEM_VENCIMENTO)) {
                abonado = previsto;
                if (inteira.efeito == Efeito.SEM_VENCIMENTO && previsto > 0) tSemVenc++;
            } else {
                abonado = Math.min(previsto, abonoParcial);
            }
            long previstoLiq = previsto - abonado;

            List<PontoRules.Par> jornadas = paresDia.stream().map(ParBatidas::par).toList();
            long trabalhado = jornadas.stream().filter(p -> !p.incompleto()).mapToLong(PontoRules.Par::minutos).sum();
            long noturno = jornadas.stream().filter(p -> !p.incompleto()).mapToLong(p -> minutosNoturnos(p.entrada(), p.saida())).sum();
            long saidasAbertas = jornadas.stream().filter(p -> p.entrada() != null && p.saida() == null).count();
            long entradasAbertas = jornadas.stream().filter(p -> p.entrada() == null && p.saida() != null).count();
            boolean incompleto = saidasAbertas + entradasAbertas > 0;
            boolean terminado = escalaDia.isEmpty()
                    ? dia.isBefore(agora.toLocalDate()) || !jornadas.isEmpty()
                    : escalaDia.stream().allMatch(i -> !i.fim().isAfter(agora));
            boolean apurado = terminado && !incompleto;

            long atraso = 0, saidaAnt = 0;
            for (PontoRules.Intervalo p : escalaDia) {
                LocalDateTime entrada = jornadas.stream().map(PontoRules.Par::entrada)
                        .filter(e -> e != null && !e.isBefore(p.inicio().minusHours(JANELA_PLANTAO_HORAS)) && e.isBefore(p.fim()))
                        .min(Comparator.naturalOrder()).orElse(null);
                LocalDateTime saida = jornadas.stream().map(PontoRules.Par::saida)
                        .filter(s -> s != null && s.isAfter(p.inicio()) && !s.isAfter(p.fim().plusHours(JANELA_PLANTAO_HORAS)))
                        .max(Comparator.naturalOrder()).orElse(null);
                if (entrada != null) {
                    long a = Duration.between(p.inicio(), entrada).toMinutes();
                    if (a > TOLERANCIA_MARCACAO_MIN) atraso += a;
                }
                if (saida != null) {
                    long s = Duration.between(saida, p.fim()).toMinutes();
                    if (s > TOLERANCIA_MARCACAO_MIN) saidaAnt += s;
                }
            }
            if (inteira != null || (previsto > 0 && abonado >= previsto)) {
                atraso = 0;
                saidaAnt = 0;
            } else if (abonado + compensacaoParcial > 0) {
                // Abono parcial ou folga parcial pelo banco justificam a ausencia (ex.: saiu antes
                // para consulta): desconta primeiro da saida antecipada, depois do atraso.
                long resta = abonado + compensacaoParcial;
                long s = Math.min(saidaAnt, resta);
                saidaAnt -= s;
                resta -= s;
                atraso -= Math.min(atraso, resta);
            }

            long saldo = 0, extra50 = 0, extra100 = 0, falta = 0;
            String situacao;
            if (!apurado) {
                situacao = incompleto ? (saidasAbertas > 0 ? "Saída em aberto" : "Entrada em aberto")
                        : escalaDia.isEmpty() ? "" : "A apurar";
                if (incompleto) tPend++;
                if (inteira != null) situacao = inteira.descricao + (situacao.isBlank() ? "" : " · " + situacao);
            } else if (previstoLiq > 0 && jornadas.isEmpty()) {
                saldo = -previstoLiq;
                if (inteira != null && inteira.efeito == Efeito.COMPENSACAO) {
                    tComp += previstoLiq;
                    situacao = inteira.descricao;
                } else {
                    falta = previstoLiq;
                    tFaltaDias++;
                    tFaltaMin += previstoLiq;
                    if (inteira != null && inteira.efeito == Efeito.FALTA_JUSTIFICADA) {
                        tFaltaJust++;
                        situacao = "Falta justificada";
                    } else {
                        situacao = "Falta";
                    }
                }
            } else {
                long diff = trabalhado - previstoLiq;
                if (Math.abs(diff) <= TOLERANCIA_DIA_MIN) diff = 0;
                saldo = diff;
                if (diff > 0) {
                    if (domFer) extra100 = diff;
                    else extra50 = diff;
                }
                situacao = inteira != null ? inteira.descricao
                        : previsto == 0 && trabalhado == 0 ? (fer != null ? fer.descricao() : "Folga")
                        : abonado > 0 && parcial != null ? parcial.descricao + " (parcial " + PontoRules.horas(abonado) + ")"
                        : compensacaoParcial > 0 ? "Folga compensatória (parcial " + PontoRules.horas(compensacaoParcial) + ")" : "";
                if (compensacaoParcial > 0 && saldo < 0) tComp += Math.min(compensacaoParcial, -saldo);
            }

            long credito = 0, debitoBanco = 0, descontar = 0;
            // Falta injustificada nunca usa o banco (vai inteira para desconto). A justificada
            // consome o banco antes de descontar; o banco continua nunca negativo.
            boolean faltaUsaBanco = inteira != null && inteira.efeito == Efeito.FALTA_JUSTIFICADA;
            if (falta > 0 && !faltaUsaBanco) {
                descontar = falta;
            } else if (saldo > 0) {
                credito = saldo;
                banco += saldo;
            } else if (saldo < 0) {
                debitoBanco = Math.min(banco, -saldo);
                banco -= debitoBanco;
                descontar = -saldo - debitoBanco;
            }
            tCredito += credito;
            tDebitoBanco += debitoBanco;
            tDescontar += descontar;

            List<Batida> marcacoes = new ArrayList<>();
            for (ParBatidas p : paresDia) {
                if (p.entrada() != null) marcacoes.add(p.entrada());
                if (p.saida() != null) marcacoes.add(p.saida());
            }
            marcacoes.addAll(anuladasPorDia.getOrDefault(dia, List.of()));
            marcacoes.sort(Comparator.comparing(Batida::quando));

            tPrev += previsto;
            tAbon += abonado;
            tTrab += trabalhado;
            tSaldo += saldo;
            tE50 += extra50;
            tE100 += extra100;
            tNot += noturno;
            if (domFer) tDomFer += trabalhado;
            tAtr += atraso;
            tSaida += saidaAnt;
            if (trabalhado > 0) tDiasTrab++;
            tPlant += escalaDia.size();
            tSaidaAberta += saidasAbertas;
            tEntradaAberta += entradasAbertas;

            dias.add(new Dia(dia, SEMANA[dia.getDayOfWeek().getValue() - 1], fer == null ? null : fer.descricao(), domFer,
                    escalaDia, marcacoes, jornadas,
                    inteira == null ? null : inteira.descricao, inteira == null ? null : inteira.codigo,
                    previsto, abonado, trabalhado, noturno, extra50, extra100, atraso, saidaAnt, falta, saldo, apurado, situacao,
                    credito, debitoBanco, descontar, banco));
        }
        // Hora noturna reduzida (52min30s): cada 7 min de relogio valem 8.
        long noturnoReduzido = Math.round(tNot * 8.0 / 7.0);
        Totais t = new Totais(tPrev, tAbon, tTrab, tSaldo, tE50, tE100, tNot, noturnoReduzido, tDomFer, tAtr, tSaida,
                tFaltaDias, tFaltaMin, tFaltaJust, tComp, tSemVenc, tDiasTrab, tPlant, tSaidaAberta, tEntradaAberta, tPend,
                diasPorOcorrencia, Math.max(0, bancoInicial), tCredito, tDebitoBanco, tDescontar, banco);
        return new Resultado(dias, t);
    }

    private static LocalDate diaDoPar(ParBatidas p, List<PontoRules.Intervalo> plantoes) {
        if (p.entrada() != null) return diaDoInstante(p.entrada().quando(), plantoes, true);
        return diaDoInstante(p.saida().quando(), plantoes, false);
    }

    /** Dia do plantao a que o instante pertence (entrada: ate 6h antes do inicio; saida: ate 6h depois do fim). */
    static LocalDate diaDoInstante(LocalDateTime t, List<PontoRules.Intervalo> plantoes, boolean entrada) {
        for (PontoRules.Intervalo p : plantoes) {
            boolean dentro = entrada
                    ? !t.isBefore(p.inicio().minusHours(JANELA_PLANTAO_HORAS)) && t.isBefore(p.fim())
                    : t.isAfter(p.inicio()) && !t.isAfter(p.fim().plusHours(JANELA_PLANTAO_HORAS));
            if (dentro) return p.inicio().toLocalDate();
        }
        return t.toLocalDate();
    }

    /** Minutos entre 22h e 5h dentro do intervalo. */
    static long minutosNoturnos(LocalDateTime a, LocalDateTime b) {
        if (a == null || b == null || !b.isAfter(a)) return 0;
        long total = 0;
        for (LocalDate d = a.toLocalDate().minusDays(1); !d.isAfter(b.toLocalDate()); d = d.plusDays(1)) {
            LocalDateTime ini = d.atTime(22, 0);
            LocalDateTime fim = d.plusDays(1).atTime(LocalTime.of(5, 0));
            LocalDateTime x = a.isAfter(ini) ? a : ini;
            LocalDateTime y = b.isBefore(fim) ? b : fim;
            if (y.isAfter(x)) total += Duration.between(x, y).toMinutes();
        }
        return total;
    }

    // ---- Feriados nacionais ----

    /** Feriados nacionais (Leis 662/49, 6.802/80, 14.759/23) e pontos facultativos moveis. */
    public static Map<LocalDate, DiaFeriado> feriadosNacionais(int ano) {
        Map<LocalDate, DiaFeriado> f = new TreeMap<>();
        LocalDate pascoa = pascoa(ano);
        f.put(LocalDate.of(ano, 1, 1), new DiaFeriado("Confraternização Universal", "NACIONAL"));
        f.put(pascoa.minusDays(48), new DiaFeriado("Carnaval", "PONTO_FACULTATIVO"));
        f.put(pascoa.minusDays(47), new DiaFeriado("Carnaval", "PONTO_FACULTATIVO"));
        f.put(pascoa.minusDays(2), new DiaFeriado("Paixão de Cristo", "NACIONAL"));
        f.put(LocalDate.of(ano, 4, 21), new DiaFeriado("Tiradentes", "NACIONAL"));
        f.put(LocalDate.of(ano, 5, 1), new DiaFeriado("Dia do Trabalho", "NACIONAL"));
        f.put(pascoa.plusDays(60), new DiaFeriado("Corpus Christi", "PONTO_FACULTATIVO"));
        f.put(LocalDate.of(ano, 9, 7), new DiaFeriado("Independência do Brasil", "NACIONAL"));
        f.put(LocalDate.of(ano, 10, 12), new DiaFeriado("Nossa Senhora Aparecida", "NACIONAL"));
        f.put(LocalDate.of(ano, 11, 2), new DiaFeriado("Finados", "NACIONAL"));
        f.put(LocalDate.of(ano, 11, 15), new DiaFeriado("Proclamação da República", "NACIONAL"));
        if (ano >= 2024) f.put(LocalDate.of(ano, 11, 20), new DiaFeriado("Dia Nacional de Zumbi e da Consciência Negra", "NACIONAL"));
        f.put(LocalDate.of(ano, 12, 25), new DiaFeriado("Natal", "NACIONAL"));
        return f;
    }

    /** Domingo de Pascoa (algoritmo de Meeus/Jones/Butcher). */
    static LocalDate pascoa(int ano) {
        int a = ano % 19, b = ano / 100, c = ano % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31, dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(ano, mes, dia);
    }
}

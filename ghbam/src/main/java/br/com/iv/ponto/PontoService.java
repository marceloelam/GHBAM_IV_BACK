package br.com.iv.ponto;

import br.com.iv.biofingertech.SourceAfisFingerprintService;
import br.com.iv.entity.Servidor;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.IdentificacaoFacialService;
import br.com.iv.service.ProvaVidaFacialService;
import br.com.iv.service.ReconhecimentoFacialService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponto eletronico: marcacao por rosto com prova de vida (mesmo desafio do reset
 * de senha), espelho mensal e banco de horas contra a escala (tabela plantao).
 */
@ApplicationScoped
public class PontoService {

    private static final int MAX_FALHAS = 5;
    private static final long JANELA_FALHAS_MS = 10 * 60 * 1000L;

    @Inject
    ServidorRepository servidorRepository;

    @Inject
    SecurityPermissionService securityPermissionService;

    @Inject
    ProvaVidaFacialService provaVidaFacialService;

    @Inject
    ReconhecimentoFacialService reconhecimentoFacialService;

    @Inject
    EntityManager em;

    @Inject
    IdentificacaoFacialService identificacaoFacialService;

    @Inject
    SourceAfisFingerprintService sourceAfis;

    @ConfigProperty(name = "ghbam.biometria.sourceafis.limiar", defaultValue = "60")
    Double limiarBiometria;

    /** Falhas recentes por matricula (terminal publico: barra tentativa repetida com foto/outra pessoa). */
    private final Map<String, List<Long>> falhas = new ConcurrentHashMap<>();

    /** Identificacoes por IP no ultimo minuto (o terminal manda no maximo um quadro a cada ~2 s). */
    private final Map<String, List<Long>> identificacoesPorIp = new ConcurrentHashMap<>();
    private static final int IDENTIFICACOES_POR_MINUTO = 40;

    // ---- Terminal ----

    /**
     * Terminal sem matricula: identifica o rosto 1:N (CompreFace Recognition) e ja abre o
     * desafio de prova de vida dessa pessoa. A prova de vida confere de novo, 1:1, com a foto
     * cadastrada antes de gravar; a identificacao so diz "quem parece ser".
     */
    public Map<String, Object> identificar(String imagemBase64, String ip) {
        long agora = System.currentTimeMillis();
        List<Long> recentes = identificacoesPorIp.compute(ip == null ? "" : ip, (k, v) -> {
            List<Long> l = new ArrayList<>(v == null ? List.of() : v);
            l.removeIf(t -> agora - t > 60_000);
            l.add(agora);
            return l;
        });
        if (recentes.size() > IDENTIFICACOES_POR_MINUTO)
            throw new IllegalStateException("Muitas tentativas neste terminal. Aguarde um minuto.");

        IdentificacaoFacialService.ResultadoIdentificacao r = identificacaoFacialService.identificar(imagemBase64);
        if (r.erro() != null) throw new IllegalStateException(r.erro());
        if (!r.rostoDetectado()) throw new IllegalArgumentException("Nenhum rosto detectado. Fique de frente para a câmera.");
        if (!r.identificado() || r.servidor() == null)
            throw new IllegalArgumentException("Rosto não reconhecido. Aproxime-se da câmera ou use sua matrícula.");

        ProvaVidaFacialService.Desafio d = desafio(r.servidor().getMatricula());
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("matricula", r.servidor().getMatricula());
        resposta.put("nome", r.servidor().getNome());
        resposta.put("desafioId", d.id());
        resposta.put("etapas", d.etapas());
        resposta.put("validadeSegundos", d.validadeSegundos());
        return resposta;
    }

    public ProvaVidaFacialService.Desafio desafio(String matricula) {
        Servidor s = servidorComFoto(matricula);
        exigirSemBloqueio(s.getMatricula());
        return provaVidaFacialService.criar(s.getMatricula());
    }

    @Transactional
    public Map<String, Object> registrar(String matricula, String desafioId, List<String> imagens, String unidadeTerminal, String terminal) {
        Servidor s = servidorComFoto(matricula);
        exigirSemBloqueio(s.getMatricula());
        if (imagens == null || imagens.isEmpty()) throw new IllegalArgumentException("Faltam as fotos da validacao.");
        ReconhecimentoFacialService.ResultadoValidacao r;
        try {
            r = provaVidaFacialService.validar(desafioId, s.getMatricula(), imagens, s.getFoto());
        } catch (IllegalArgumentException e) {
            registrarFalha(s.getMatricula());
            throw e;
        }
        if (!r.isAprovado()) {
            registrarFalha(s.getMatricula());
            throw new IllegalArgumentException(r.getMensagem() == null ? "Rosto nao reconhecido." : r.getMensagem());
        }
        falhas.remove(s.getMatricula());
        return gravarBatida(s, unidadeTerminal, terminal, "FACIAL", r.getSimilaridade());
    }

    /**
     * Terminal com leitor de digital: o agente local manda a imagem e a comparacao 1:N e
     * feita aqui (SourceAFIS, mesma base e limiar do login biometrico). As digitais
     * cadastradas nunca saem do servidor.
     */
    @Transactional
    public Map<String, Object> registrarBiometria(String imagemBase64, Double dpi, String unidadeTerminal, String terminal,
            java.util.function.Supplier<Servidor> conferenciaNitgen) {
        limitarPorIp(terminal);
        if (vazio(imagemBase64)) throw new IllegalArgumentException("Digital nao capturada. Coloque o dedo no leitor.");
        String modelo;
        try {
            modelo = sourceAfis.createTemplateFromImage(imagemBase64, dpi);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Leitura da digital invalida. Limpe o leitor e tente de novo.");
        }
        List<Servidor> comModelo = servidorRepository.findAllComSourceafis();
        SourceAfisFingerprintService.MatchResult r = comModelo.isEmpty() ? null : sourceAfis.identifyServidores(modelo, comModelo, limiarBiometria);
        if (r != null && r.matched() && r.servidor() != null) {
            exigirSemBloqueio(r.servidor().getMatricula());
            return gravarBatida(r.servidor(), unidadeTerminal, terminal, "BIOMETRIA", Math.min(1.0, r.percentual() / 100.0));
        }
        // Como no login: na rede interna, confere pelo SDK Nitgen do agente da estacao.
        Servidor s = conferenciaNitgen == null ? null : conferenciaNitgen.get();
        if (s == null)
            throw new IllegalArgumentException("Digital nao reconhecida. Tente outro dedo cadastrado ou use o rosto.");
        exigirSemBloqueio(s.getMatricula());
        return gravarBatida(s, unidadeTerminal, terminal, "BIOMETRIA", 1.0);
    }

    private void limitarPorIp(String ip) {
        long agora = System.currentTimeMillis();
        List<Long> recentes = identificacoesPorIp.compute(ip == null ? "" : ip, (k, v) -> {
            List<Long> l = new ArrayList<>(v == null ? List.of() : v);
            l.removeIf(t -> agora - t > 60_000);
            l.add(agora);
            return l;
        });
        if (recentes.size() > IDENTIFICACOES_POR_MINUTO)
            throw new IllegalStateException("Muitas tentativas neste terminal. Aguarde um minuto.");
    }

    /** Grava a batida ja identificada (rosto ou digital): escala da unidade, duplicidade e entrada/saida. */
    private Map<String, Object> gravarBatida(Servidor s, String unidadeTerminal, String terminal, String origem, double similaridade) {
        exigirAcesso(s);
        LocalDateTime agora = LocalDateTime.now();
        if (!vazio(unidadeTerminal)) exigirEscalaNestaUnidade(s.getMatricula(), unidadeTerminal.trim().toUpperCase(), agora);
        PontoMarcacao ultima = ultimaValida(s.getMatricula());
        PontoRules.Marca marcaAnterior = ultima == null ? null : new PontoRules.Marca(ultima.tipo, ultima.dataHora);
        if (PontoRules.duplicada(marcaAnterior, agora)) {
            throw new IllegalStateException("Ponto ja registrado as " + ultima.dataHora.format(DateTimeFormatter.ofPattern("HH:mm")) + ".");
        }
        PontoMarcacao m = new PontoMarcacao();
        m.unidade = vazio(unidadeTerminal) ? s.getUnidade() : unidadeTerminal.trim().toUpperCase();
        m.matricula = s.getMatricula();
        m.nome = s.getNome();
        m.tipo = PontoRules.proximoTipo(marcaAnterior, agora);
        // Esqueceu a saida e voltou para o plantao seguinte: a janela de 26h chamaria isso de
        // SAIDA (jornada falsa de 24h). Se um plantao novo dele comeca agora, e ENTRADA, e a
        // entrada anterior fica pendente para o RH.
        String aviso = null;
        if (PontoRules.SAIDA.equals(m.tipo) && iniciaPlantaoNovo(s.getMatricula(), ultima.dataHora, agora)) {
            m.tipo = PontoRules.ENTRADA;
            aviso = "Você não registrou a saída do plantão de " + ultima.dataHora.format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"))
                    + ". Procure o RH para regularizar.";
        } else if (PontoRules.ENTRADA.equals(m.tipo) && ultima != null && PontoRules.SAIDA.equals(ultima.tipo)
                && ultima.dataHora.toLocalDate().equals(agora.toLocalDate())
                && ultima.dataHora.plusHours(3).isBefore(agora)
                && !iniciaPlantaoNovo(s.getMatricula(), ultima.dataHora, agora)) {
            // Saida e entrada longe uma da outra no mesmo dia, sem plantao novo: provavel batida
            // esquecida (ex.: volta do almoco). So avisa; a correcao e do RH, com justificativa.
            aviso = "Sua última batida foi a SAÍDA das " + ultima.dataHora.format(DateTimeFormatter.ofPattern("HH:mm"))
                    + ". Se você esqueceu de bater a volta (ex.: do almoço), procure o RH para incluir.";
        }
        m.dataHora = agora;
        m.origem = origem;
        m.similaridade = BigDecimal.valueOf(similaridade).setScale(4, RoundingMode.HALF_UP);
        m.terminal = terminal;
        m.persist();

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("nome", s.getNome());
        resposta.put("tipo", m.tipo);
        resposta.put("dataHora", m.dataHora);
        resposta.put("unidade", m.unidade);
        if (aviso != null) resposta.put("aviso", aviso);
        return resposta;
    }

    /** Ha plantao do servidor comecando perto de agora (3h) e bem depois da ultima entrada? */
    private boolean iniciaPlantaoNovo(String matricula, LocalDateTime ultimaEntrada, LocalDateTime agora) {
        Long n = em.createQuery(
                "select count(p) from Plantao p where p.servidor.matricula = ?1 and p.dataInicio between ?2 and ?3 and p.dataInicio > ?4", Long.class)
                .setParameter(1, matricula)
                .setParameter(2, agora.minusHours(3))
                .setParameter(3, agora.plusHours(3))
                .setParameter(4, ultimaEntrada.plusHours(3))
                .getSingleResult();
        return n != null && n > 0;
    }

    // ---- Espelho, resumo e banco de horas ----

    public Map<String, Object> espelho(String unidade, String matricula, YearMonth mes) {
        return espelho(unidade, matricula, mes, mes.atDay(1), mes.atEndOfMonth());
    }

    /** Espelho de um periodo livre (datas inclusivas); o banco do ano vai ate o mes do fim. */
    public Map<String, Object> espelho(String unidade, String matricula, YearMonth mes, LocalDate de, LocalDate ate) {
        Servidor s = servidorRepository.localizarMatricula(matricula);
        if (s == null) throw new NotFoundException("Matricula nao encontrada.");
        List<PontoMarcacao> todas = PontoMarcacao.<PontoMarcacao>find(
                "matricula = ?1 and unidade = ?2 and dataHora >= ?3 and dataHora < ?4 order by dataHora",
                matricula, unidade, de.atStartOfDay(), ate.plusDays(1).atStartOfDay()).list();
        PontoApuracao.Resultado c = apurar(matricula, unidade, de, ate);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("matricula", matricula);
        r.put("nome", s.getNome());
        r.put("cpf", s.getCpf());
        r.put("cargo", s.getCargo());
        r.put("cbo", s.getCbo());
        r.put("unidade", unidade);
        r.put("mes", mes.toString());
        r.put("inicio", de.toString());
        r.put("fim", ate.toString());
        r.put("fechado", mesFechado(unidade, mes));
        r.put("marcacoes", todas);
        r.put("ocorrencias", ocorrencias(unidade, matricula, de, ate));
        r.put("dias", c.dias());
        r.put("totais", c.totais());
        preencherResumo(r, c);
        return r;
    }

    /**
     * Campos que a tela e o CSV usam. saldoAnoMinutos agora e o BANCO de horas no fim do
     * periodo (nunca negativo); o que faltou sem banco vai em horasDescontarMinutos.
     */
    private static void preencherResumo(Map<String, Object> r, PontoApuracao.Resultado c) {
        long saldoAno = c.totais().bancoFinal();
        r.put("horasDescontarMinutos", c.totais().horasDescontar());
        r.put("creditoBancoMinutos", c.totais().creditoBanco());
        PontoApuracao.Totais t = c.totais();
        r.put("jornadas", c.jornadas());
        r.put("minutosTrabalhados", t.trabalhado());
        r.put("minutosPrevistos", t.previsto());
        r.put("minutosAbonados", t.abonado());
        r.put("saldoMinutos", t.saldo());
        r.put("saldoAnoMinutos", saldoAno);
        r.put("jornadasIncompletas", t.saidasEmAberto() + t.entradasEmAberto());
        r.put("saidasEmAberto", t.saidasEmAberto());
        r.put("entradasEmAberto", t.entradasEmAberto());
    }

    public List<Map<String, Object>> resumo(String unidade, YearMonth mes) {
        return resumo(unidade, mes, mes.atDay(1), mes.atEndOfMonth());
    }

    /** Resumo de um periodo livre (datas inclusivas). */
    /**
     * Resumo de um periodo livre (datas inclusivas). Entra quem bateu ponto, foi escalado
     * ou teve ocorrencia (ex.: o mes inteiro de ferias) na unidade.
     * Com dias=true leva tambem a apuracao diaria (folhas de ponto de todos de uma vez).
     */
    public List<Map<String, Object>> resumo(String unidade, YearMonth mes, LocalDate de, LocalDate ate) {
        return resumo(unidade, mes, de, ate, false);
    }

    public List<Map<String, Object>> resumo(String unidade, YearMonth mes, LocalDate de, LocalDate ate, boolean dias) {
        LocalDateTime inicio = de.atStartOfDay();
        LocalDateTime fim = ate.plusDays(1).atStartOfDay();
        Map<String, String> pessoas = new TreeMap<>();
        Map<String, PontoMarcacao> ultimas = new HashMap<>();
        for (PontoMarcacao m : PontoMarcacao.<PontoMarcacao>find(
                "unidade = ?1 and dataHora >= ?2 and dataHora < ?3 and anulada = false", unidade, inicio, fim).list()) {
            pessoas.putIfAbsent(m.matricula, m.nome);
            ultimas.merge(m.matricula, m, (a, b) -> b.dataHora.isAfter(a.dataHora) ? b : a);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> escalados = em.createQuery(
                "select p.servidor.matricula, p.servidor.nome from Plantao p where p.unidade = ?1 and p.dataInicio < ?3 and (p.dataFim is null or p.dataFim > ?2)")
                .setParameter(1, unidade).setParameter(2, inicio).setParameter(3, fim).getResultList();
        for (Object[] e : escalados) pessoas.putIfAbsent(String.valueOf(e[0]), e[1] == null ? "" : String.valueOf(e[1]));
        for (PontoOcorrencia o : ocorrencias(unidade, null, de, ate)) pessoas.putIfAbsent(o.matricula, o.nome);

        List<Map<String, Object>> linhas = new ArrayList<>();
        for (Map.Entry<String, String> p : pessoas.entrySet()) {
            PontoApuracao.Resultado c = apurar(p.getKey(), unidade, de, ate);
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("matricula", p.getKey());
            l.put("nome", p.getValue());
            preencherResumo(l, c);
            PontoMarcacao ult = ultimas.get(p.getKey());
            if (ult != null) {
                l.put("ultimaMarcacaoTipo", ult.tipo);
                l.put("ultimaMarcacaoDataHora", ult.dataHora);
            }
            l.put("totais", c.totais());
            if (dias) {
                Servidor s = servidorRepository.localizarMatricula(p.getKey());
                if (s != null) {
                    l.put("cpf", s.getCpf());
                    l.put("cargo", s.getCargo());
                    l.put("cbo", s.getCbo());
                }
                l.put("dias", c.dias());
            }
            linhas.add(l);
        }
        linhas.sort((a, b) -> String.valueOf(a.get("nome")).compareToIgnoreCase(String.valueOf(b.get("nome"))));
        return linhas;
    }

    /** Arquivo para a folha da prefeitura: uma linha por servidor com os eventos do mes (horas em h:mm). */
    public String exportarCsv(String unidade, YearMonth mes) {
        StringBuilder sb = new StringBuilder("matricula;nome;horas_previstas;horas_abonadas;horas_trabalhadas;credito_banco_mes;horas_descontar;banco_horas;"
                + "extra_50;extra_100;adicional_noturno_relogio;adicional_noturno_reduzido;atrasos;saidas_antecipadas;"
                + "faltas_dias;faltas_horas;faltas_justificadas_dias;dias_ferias;dias_atestado_licenca;dias_sem_vencimento;"
                + "folga_compensatoria_horas;pendencias;mes_fechado\n");
        boolean fechado = mesFechado(unidade, mes);
        for (Map<String, Object> l : resumo(unidade, mes)) {
            PontoApuracao.Totais t = (PontoApuracao.Totais) l.get("totais");
            long ferias = t.diasPorOcorrencia().getOrDefault("FE", 0L);
            long afast = t.diasPorOcorrencia().entrySet().stream()
                    .filter(e -> !List.of("FE", "FC", "FJ", "FI", "SV").contains(e.getKey()))
                    .mapToLong(Map.Entry::getValue).sum();
            sb.append(l.get("matricula")).append(';')
                    .append(String.valueOf(l.get("nome")).replace(';', ',')).append(';')
                    .append(PontoRules.horas(t.previsto())).append(';')
                    .append(PontoRules.horas(t.abonado())).append(';')
                    .append(PontoRules.horas(t.trabalhado())).append(';')
                    .append(PontoRules.horas(t.creditoBanco())).append(';')
                    .append(PontoRules.horas(t.horasDescontar())).append(';')
                    .append(PontoRules.horas(t.bancoFinal())).append(';')
                    .append(PontoRules.horas(t.extra50())).append(';')
                    .append(PontoRules.horas(t.extra100())).append(';')
                    .append(PontoRules.horas(t.noturno())).append(';')
                    .append(PontoRules.horas(t.noturnoReduzido())).append(';')
                    .append(PontoRules.horas(t.atrasos())).append(';')
                    .append(PontoRules.horas(t.saidasAntecipadas())).append(';')
                    .append(t.faltasDias()).append(';')
                    .append(PontoRules.horas(t.faltasMinutos())).append(';')
                    .append(t.faltasJustificadasDias()).append(';')
                    .append(ferias).append(';')
                    .append(afast).append(';')
                    .append(t.semVencimentoDias()).append(';')
                    .append(PontoRules.horas(t.compensacaoMinutos())).append(';')
                    .append(t.saidasEmAberto() + t.entradasEmAberto()).append(';')
                    .append(fechado ? "S" : "N").append('\n');
        }
        return sb.toString();
    }

    /**
     * Quem esta escalado agora (tolerancia de 6h) em outra unidade e nao nesta recebe aviso
     * e a batida nao e gravada. Sem escala em lugar nenhum (cobertura de ultima hora) passa.
     */
    private void exigirEscalaNestaUnidade(String matricula, String unidade, LocalDateTime agora) {
        @SuppressWarnings("unchecked")
        List<Object[]> escalas = em.createQuery(
                "select p.unidade, p.dataInicio, p.dataFim from Plantao p where p.servidor.matricula = ?1 and p.dataInicio <= ?2 and (p.dataFim is null or p.dataFim >= ?3) order by p.dataInicio")
                .setParameter(1, matricula).setParameter(2, agora.plusHours(6)).setParameter(3, agora.minusHours(6))
                .getResultList();
        if (escalas.isEmpty()) return;
        boolean aqui = escalas.stream().anyMatch(e -> unidade.equalsIgnoreCase(String.valueOf(e[0]).trim()));
        if (aqui) return;
        Object[] e = escalas.get(0);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        String horario = ((LocalDateTime) e[1]).format(f) + (e[2] == null ? "" : " às " + ((LocalDateTime) e[2]).format(DateTimeFormatter.ofPattern("HH:mm")));
        throw new IllegalStateException("Você está escalado(a) para a unidade " + e[0] + " (plantão " + horario
                + "), não para " + unidade + ". Bata o ponto no terminal de " + e[0] + " ou procure o RH.");
    }

    // ---- Ajustes do RH ----

    /** Esquecimento de bater: o RH inclui a marcacao com justificativa (origem AJUSTE, auditavel). */
    @Transactional
    public PontoMarcacao ajustar(String unidade, Map<String, Object> p, String usuario) {
        String matricula = texto(p, "matricula");
        String justificativa = texto(p, "justificativa");
        String tipo = texto(p, "tipo") == null ? "" : texto(p, "tipo").trim().toUpperCase();
        if (vazio(matricula) || vazio(justificativa)) throw new IllegalArgumentException("Informe matricula e justificativa.");
        if (!PontoRules.ENTRADA.equals(tipo) && !PontoRules.SAIDA.equals(tipo)) throw new IllegalArgumentException("Tipo deve ser ENTRADA ou SAIDA.");
        LocalDateTime quando;
        try {
            quando = LocalDateTime.parse(texto(p, "dataHora").trim().substring(0, 16));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Data e hora invalidas (aaaa-MM-ddTHH:mm).");
        }
        if (quando.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("Ajuste nao pode ser no futuro.");
        exigirMesesAbertos(unidade, quando.toLocalDate(), quando.toLocalDate());
        Servidor s = servidorRepository.localizarMatricula(matricula.trim());
        if (s == null) throw new NotFoundException("Matricula nao encontrada.");
        // So ajusta batida de quem estava escalado: o horario precisa cair num plantao do
        // Controle de Plantoes (tolerancia de 6h para chegada antecipada ou saida tardia).
        Long escalado = em.createQuery(
                "select count(p) from Plantao p where p.servidor.matricula = ?1 and p.unidade = ?2 and p.dataInicio <= ?3 and (p.dataFim is null or p.dataFim >= ?4)", Long.class)
            .setParameter(1, s.getMatricula())
            .setParameter(2, unidade)
            .setParameter(3, quando.plusHours(6))
            .setParameter(4, quando.minusHours(6))
            .getSingleResult();
        // Sem escala, o terminal aceita a batida (cobertura de ultima hora); o RH entao pode
        // completar essa jornada: precisa haver batida real do terminal (nao ajuste) na
        // unidade dentro da janela da jornada. Ajuste solto, sem plantao nem batida, segue barrado.
        if (escalado == null || escalado == 0) {
            long batidasTerminal = PontoMarcacao.count(
                    "matricula = ?1 and unidade = ?2 and anulada = false and origem <> 'AJUSTE' and dataHora between ?3 and ?4",
                    s.getMatricula(), unidade, quando.minusHours(PontoRules.JANELA_SAIDA_HORAS), quando.plusHours(PontoRules.JANELA_SAIDA_HORAS));
            if (batidasTerminal == 0)
                throw new IllegalArgumentException("Servidor sem plantao no Controle de Plantoes e sem batida no terminal perto desse horario. Registre o plantao antes de incluir a marcacao.");
        }
        PontoMarcacao m = new PontoMarcacao();
        m.unidade = unidade;
        m.matricula = s.getMatricula();
        m.nome = s.getNome();
        m.tipo = tipo;
        m.dataHora = quando;
        m.origem = "AJUSTE";
        m.justificativa = justificativa.trim();
        m.usuarioAjuste = usuario;
        m.persist();
        return m;
    }

    @Transactional
    public PontoMarcacao anular(String unidade, Long id, String motivo, String usuario) {
        if (vazio(motivo)) throw new IllegalArgumentException("Informe o motivo da anulacao.");
        PontoMarcacao m = PontoMarcacao.<PontoMarcacao>find("idmarcacao = ?1 and unidade = ?2", id, unidade).firstResult();
        if (m == null) throw new NotFoundException("Marcacao nao encontrada nesta unidade.");
        if (Boolean.TRUE.equals(m.anulada)) throw new IllegalStateException("Marcacao ja anulada.");
        exigirMesesAbertos(unidade, m.dataHora.toLocalDate(), m.dataHora.toLocalDate());
        m.anulada = true;
        m.motivoAnulacao = motivo.trim();
        m.usuarioAnulacao = usuario;
        return m;
    }

    // ---- Apoio ----

    /**
     * Apuracao diaria do servidor NA unidade (batidas, escala e ocorrencias so desta unidade:
     * quem trabalha em duas nao conta em dobro). Ver regras em PontoApuracao.
     */
    private PontoApuracao.Resultado apurar(String matricula, String unidade, LocalDate de, LocalDate ate) {
        return apurar(matricula, unidade, de, ate, bancoAte(matricula, unidade, de.minusDays(1)));
    }

    /**
     * Banco de horas no fim do dia informado. O banco e anual (zera em 1o de janeiro) e
     * nunca fica negativo: debito sem saldo vira desconto em folha (ver PontoApuracao).
     */
    private long bancoAte(String matricula, String unidade, LocalDate dia) {
        LocalDate inicioAno = LocalDate.of(dia.plusDays(1).getYear(), 1, 1);
        if (dia.isBefore(inicioAno)) return 0;
        return apurar(matricula, unidade, inicioAno, dia, 0).totais().bancoFinal();
    }

    private PontoApuracao.Resultado apurar(String matricula, String unidade, LocalDate de, LocalDate ate, long bancoInicial) {
        // Folga de 2 dias nas pontas para fechar o plantao que atravessa a virada do periodo.
        List<PontoApuracao.Batida> batidas = PontoMarcacao.<PontoMarcacao>find(
                "matricula = ?1 and unidade = ?2 and dataHora >= ?3 and dataHora < ?4 order by dataHora",
                matricula, unidade, de.minusDays(2).atStartOfDay(), ate.plusDays(3).atStartOfDay()).list().stream()
                .map(m -> new PontoApuracao.Batida(m.idmarcacao, m.tipo, m.dataHora, m.origem, Boolean.TRUE.equals(m.anulada)))
                .toList();
        @SuppressWarnings("unchecked")
        List<Object[]> escala = em.createQuery(
                "select p.dataInicio, p.dataFim from Plantao p where p.servidor.matricula = ?1 and p.unidade = ?2 and p.dataInicio >= ?3 and p.dataInicio < ?4")
                .setParameter(1, matricula).setParameter(2, unidade)
                .setParameter(3, de.minusDays(1).atStartOfDay()).setParameter(4, ate.plusDays(2).atStartOfDay()).getResultList();
        List<PontoRules.Intervalo> intervalos = escala.stream()
                .map(e -> new PontoRules.Intervalo((LocalDateTime) e[0], (LocalDateTime) e[1])).toList();
        List<PontoApuracao.Ocorrencia> ocorr = ocorrencias(unidade, matricula, de, ate).stream()
                .map(o -> new PontoApuracao.Ocorrencia(o.tipo, o.dataInicio, o.dataFim, o.minutos)).toList();
        return PontoApuracao.apurar(de, ate, intervalos, batidas, ocorr, feriados(unidade, de, ate), LocalDateTime.now(), bancoInicial);
    }

    // ---- Ocorrencias (ferias, atestados, licencas, abonos, faltas) ----

    /** Ocorrencias validas que tocam o periodo; matricula nula = todas da unidade. */
    public List<PontoOcorrencia> ocorrencias(String unidade, String matricula, LocalDate de, LocalDate ate) {
        if (vazio(matricula))
            return PontoOcorrencia.<PontoOcorrencia>find("unidade = ?1 and anulada = false and dataInicio <= ?3 and dataFim >= ?2 order by dataInicio",
                    unidade, de, ate).list();
        return PontoOcorrencia.<PontoOcorrencia>find("unidade = ?1 and matricula = ?2 and anulada = false and dataInicio <= ?4 and dataFim >= ?3 order by dataInicio",
                unidade, matricula.trim(), de, ate).list();
    }

    public List<Map<String, Object>> tiposOcorrencia() {
        List<Map<String, Object>> tipos = new ArrayList<>();
        for (PontoApuracao.TipoOcorrencia t : PontoApuracao.TipoOcorrencia.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", t.name());
            m.put("codigo", t.codigo);
            m.put("descricao", t.descricao);
            m.put("efeito", t.efeito.name());
            m.put("parcial", t.efeito == PontoApuracao.Efeito.ABONA || t.efeito == PontoApuracao.Efeito.COMPENSACAO);
            tipos.add(m);
        }
        return tipos;
    }

    @Transactional
    public PontoOcorrencia registrarOcorrencia(String unidade, Map<String, Object> p, String usuario) {
        String matricula = texto(p, "matricula");
        PontoApuracao.TipoOcorrencia tipo = PontoApuracao.TipoOcorrencia.de(texto(p, "tipo"));
        if (vazio(matricula)) throw new IllegalArgumentException("Informe a matricula.");
        if (tipo == null) throw new IllegalArgumentException("Tipo de ocorrencia invalido.");
        LocalDate de = data(texto(p, "dataInicio"), "Data inicial invalida.");
        LocalDate ate = vazio(texto(p, "dataFim")) ? de : data(texto(p, "dataFim"), "Data final invalida.");
        if (ate.isBefore(de)) throw new IllegalArgumentException("A data final e anterior a inicial.");
        if (de.plusYears(1).isBefore(ate)) throw new IllegalArgumentException("Periodo maximo de 1 ano por lancamento.");
        Integer minutos = null;
        if (!vazio(texto(p, "minutos"))) {
            try {
                minutos = (int) Math.round(Double.parseDouble(texto(p, "minutos")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Minutos invalidos.");
            }
            if (minutos <= 0) minutos = null;
        }
        if (minutos != null) {
            if (!de.equals(ate)) throw new IllegalArgumentException("Abono parcial (em horas) vale para um dia so.");
            if (tipo.efeito != PontoApuracao.Efeito.ABONA && tipo.efeito != PontoApuracao.Efeito.COMPENSACAO)
                throw new IllegalArgumentException("Esse tipo nao aceita lancamento em horas; lance o dia inteiro.");
            if (minutos > 24 * 60) throw new IllegalArgumentException("Abono parcial maior que 24h.");
        }
        Servidor s = servidorRepository.localizarMatricula(matricula.trim());
        if (s == null) throw new NotFoundException("Matricula nao encontrada.");
        exigirMesesAbertos(unidade, de, ate);
        if (minutos == null) {
            for (PontoOcorrencia o : ocorrencias(unidade, s.getMatricula(), de, ate)) {
                if (o.minutos == null) {
                    PontoApuracao.TipoOcorrencia t = PontoApuracao.TipoOcorrencia.de(o.tipo);
                    throw new IllegalStateException("Ja existe " + (t == null ? o.tipo : t.descricao) + " de "
                            + br(o.dataInicio) + " a " + br(o.dataFim) + ". Anule-a antes de lancar outra no mesmo periodo.");
                }
            }
        }
        String periodo = texto(p, "periodoAquisitivo") == null ? null : texto(p, "periodoAquisitivo").trim();
        if (tipo == PontoApuracao.TipoOcorrencia.FERIAS) {
            if (vazio(periodo)) throw new IllegalArgumentException("Informe o periodo aquisitivo das ferias (ex.: 2025/2026).");
            long dias = java.time.temporal.ChronoUnit.DAYS.between(de, ate) + 1;
            if (dias < 5) throw new IllegalArgumentException("Cada periodo de ferias deve ter no minimo 5 dias.");
            long jaGozados = diasFerias(s.getMatricula(), periodo);
            if (jaGozados + dias > 30)
                throw new IllegalStateException("Periodo aquisitivo " + periodo + " ja tem " + jaGozados + " dia(s) de ferias; restam " + (30 - jaGozados) + ".");
        }
        if (tipo == PontoApuracao.TipoOcorrencia.FOLGA_COMPENSATORIA) {
            // Banco de horas nao pode ficar negativo: so concede folga com saldo que cubra a escala.
            @SuppressWarnings("unchecked")
            List<Object[]> escala = em.createQuery(
                    "select p.dataInicio, p.dataFim from Plantao p where p.servidor.matricula = ?1 and p.unidade = ?2 and p.dataInicio >= ?3 and p.dataInicio < ?4 and p.dataFim is not null")
                    .setParameter(1, s.getMatricula()).setParameter(2, unidade)
                    .setParameter(3, de.atStartOfDay()).setParameter(4, ate.plusDays(1).atStartOfDay()).getResultList();
            long necessario = escala.stream()
                    .mapToLong(e -> Math.max(0, java.time.Duration.between((LocalDateTime) e[0], (LocalDateTime) e[1]).toMinutes())).sum();
            if (necessario <= 0) throw new IllegalArgumentException("Nao ha plantao na escala nesse periodo para compensar.");
            if (minutos != null) {
                // folga parcial (ex.: sair 3h antes): no maximo a duracao do plantao do dia
                if (minutos > necessario) throw new IllegalArgumentException("A folga parcial e maior que o plantao do dia (" + PontoRules.horas(necessario) + ").");
                necessario = minutos;
            }
            long banco = bancoAte(s.getMatricula(), unidade, de.minusDays(1));
            if (banco < necessario)
                throw new IllegalStateException("Saldo insuficiente no banco de horas: disponivel " + PontoRules.horas(banco)
                        + ", a folga precisa de " + PontoRules.horas(necessario) + ". O banco nao pode ficar negativo.");
        }
        PontoOcorrencia o = new PontoOcorrencia();
        o.unidade = unidade;
        o.matricula = s.getMatricula();
        o.nome = s.getNome();
        o.tipo = tipo.name();
        o.dataInicio = de;
        o.dataFim = ate;
        o.minutos = minutos;
        o.periodoAquisitivo = tipo == PontoApuracao.TipoOcorrencia.FERIAS ? periodo : null;
        o.documento = limitar(texto(p, "documento"), 80);
        o.observacao = vazio(texto(p, "observacao")) ? null : texto(p, "observacao").trim();
        o.usuario = usuario;
        o.persist();
        return o;
    }

    @Transactional
    public PontoOcorrencia anularOcorrencia(String unidade, Long id, String motivo, String usuario) {
        if (vazio(motivo)) throw new IllegalArgumentException("Informe o motivo da anulacao.");
        PontoOcorrencia o = PontoOcorrencia.<PontoOcorrencia>find("idocorrencia = ?1 and unidade = ?2", id, unidade).firstResult();
        if (o == null) throw new NotFoundException("Ocorrencia nao encontrada nesta unidade.");
        if (Boolean.TRUE.equals(o.anulada)) throw new IllegalStateException("Ocorrencia ja anulada.");
        exigirMesesAbertos(unidade, o.dataInicio, o.dataFim);
        o.anulada = true;
        o.motivoAnulacao = motivo.trim();
        o.usuarioAnulacao = usuario;
        return o;
    }

    private long diasFerias(String matricula, String periodo) {
        return PontoOcorrencia.<PontoOcorrencia>find("matricula = ?1 and tipo = 'FERIAS' and anulada = false and periodoAquisitivo = ?2", matricula, periodo)
                .list().stream().mapToLong(o -> java.time.temporal.ChronoUnit.DAYS.between(o.dataInicio, o.dataFim) + 1).sum();
    }

    /** Controle de ferias da unidade: por servidor e periodo aquisitivo, dias gozados/programados e saldo. */
    public List<Map<String, Object>> controleFerias(String unidade, int ano) {
        List<PontoOcorrencia> ferias = PontoOcorrencia.<PontoOcorrencia>find(
                "unidade = ?1 and tipo = 'FERIAS' and anulada = false and dataInicio <= ?3 and dataFim >= ?2 order by nome, dataInicio",
                unidade, LocalDate.of(ano - 1, 1, 1), LocalDate.of(ano + 1, 12, 31)).list();
        Map<String, Map<String, Object>> grupos = new LinkedHashMap<>();
        LocalDate hoje = LocalDate.now();
        for (PontoOcorrencia o : ferias) {
            String chave = o.matricula + "|" + o.periodoAquisitivo;
            Map<String, Object> g = grupos.computeIfAbsent(chave, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("matricula", o.matricula);
                m.put("nome", o.nome);
                m.put("periodoAquisitivo", o.periodoAquisitivo);
                m.put("dias", 0L);
                m.put("periodos", new ArrayList<Map<String, Object>>());
                return m;
            });
            long dias = java.time.temporal.ChronoUnit.DAYS.between(o.dataInicio, o.dataFim) + 1;
            g.put("dias", (Long) g.get("dias") + dias);
            Map<String, Object> per = new LinkedHashMap<>();
            per.put("idocorrencia", o.idocorrencia);
            per.put("inicio", o.dataInicio);
            per.put("fim", o.dataFim);
            per.put("dias", dias);
            per.put("situacao", o.dataFim.isBefore(hoje) ? "Gozadas" : o.dataInicio.isAfter(hoje) ? "Programadas" : "Em gozo");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lista = (List<Map<String, Object>>) g.get("periodos");
            lista.add(per);
        }
        // Saldo pelo periodo aquisitivo inteiro (pode ter parcela lancada em outro ano).
        for (Map<String, Object> g : grupos.values()) {
            long total = diasFerias(String.valueOf(g.get("matricula")), String.valueOf(g.get("periodoAquisitivo")));
            g.put("diasNoPeriodo", total);
            g.put("saldo", 30 - total);
        }
        return new ArrayList<>(grupos.values());
    }

    // ---- Feriados ----

    /** Nacionais (calculados) + cadastrados para todas as unidades ou para esta. */
    public Map<LocalDate, PontoApuracao.DiaFeriado> feriados(String unidade, LocalDate de, LocalDate ate) {
        Map<LocalDate, PontoApuracao.DiaFeriado> f = new TreeMap<>();
        for (int ano = de.getYear(); ano <= ate.getYear(); ano++) f.putAll(PontoApuracao.feriadosNacionais(ano));
        for (Feriado l : Feriado.<Feriado>find("data >= ?1 and data <= ?2 and (unidade is null or unidade = ?3)", de, ate, unidade).list()) {
            f.put(l.data, new PontoApuracao.DiaFeriado(l.descricao, l.tipo));
        }
        f.keySet().removeIf(d -> d.isBefore(de) || d.isAfter(ate));
        return f;
    }

    public List<Map<String, Object>> listarFeriados(String unidade, int ano) {
        List<Map<String, Object>> lista = new ArrayList<>();
        PontoApuracao.feriadosNacionais(ano).forEach((d, f) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("data", d);
            m.put("descricao", f.descricao());
            m.put("tipo", f.tipo());
            m.put("nacional", true);
            lista.add(m);
        });
        for (Feriado l : Feriado.<Feriado>find("data >= ?1 and data <= ?2 and (unidade is null or unidade = ?3) order by data",
                LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31), unidade).list()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idferiado", l.idferiado);
            m.put("data", l.data);
            m.put("descricao", l.descricao);
            m.put("tipo", l.tipo);
            m.put("unidade", l.unidade);
            m.put("nacional", false);
            lista.add(m);
        }
        lista.sort((a, b) -> ((LocalDate) a.get("data")).compareTo((LocalDate) b.get("data")));
        return lista;
    }

    @Transactional
    public Feriado registrarFeriado(String unidade, Map<String, Object> p, String usuario) {
        LocalDate d = data(texto(p, "data"), "Data invalida.");
        String descricao = texto(p, "descricao");
        if (vazio(descricao)) throw new IllegalArgumentException("Informe a descricao.");
        String tipo = vazio(texto(p, "tipo")) ? "MUNICIPAL" : texto(p, "tipo").trim().toUpperCase();
        if (!List.of("MUNICIPAL", "ESTADUAL", "PONTO_FACULTATIVO").contains(tipo)) throw new IllegalArgumentException("Tipo de feriado invalido.");
        boolean todas = Boolean.parseBoolean(String.valueOf(p == null ? null : p.get("todasUnidades")));
        exigirMesesAbertos(unidade, d, d);
        String un = todas ? null : unidade;
        long ja = un == null ? Feriado.count("data = ?1 and unidade is null", d) : Feriado.count("data = ?1 and unidade = ?2", d, un);
        if (ja > 0) throw new IllegalStateException("Ja existe feriado cadastrado nesse dia.");
        Feriado f = new Feriado();
        f.data = d;
        f.descricao = limitar(descricao, 120);
        f.tipo = tipo;
        f.unidade = un;
        f.usuario = usuario;
        f.persist();
        return f;
    }

    @Transactional
    public Map<String, Object> excluirFeriado(String unidade, Long id) {
        Feriado f = Feriado.<Feriado>find("idferiado = ?1 and (unidade is null or unidade = ?2)", id, unidade).firstResult();
        if (f == null) throw new NotFoundException("Feriado nao encontrado.");
        exigirMesesAbertos(unidade, f.data, f.data);
        f.delete();
        return Map.of("excluido", id);
    }

    // ---- Fechamento do mes ----

    public boolean mesFechado(String unidade, YearMonth mes) {
        return PontoFechamento.count("unidade = ?1 and mes = ?2 and ativo = true", unidade, mes.toString()) > 0;
    }

    public Map<String, Object> situacaoFechamento(String unidade, YearMonth mes) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mes", mes.toString());
        PontoFechamento f = PontoFechamento.<PontoFechamento>find("unidade = ?1 and mes = ?2 and ativo = true", unidade, mes.toString()).firstResult();
        r.put("fechado", f != null);
        if (f != null) {
            r.put("fechadoPor", f.fechadoPor);
            r.put("dataFechamento", f.dataFechamento);
        }
        return r;
    }

    @Transactional
    public Map<String, Object> fecharMes(String unidade, YearMonth mes, String usuario) {
        if (!mes.atEndOfMonth().isBefore(LocalDate.now())) throw new IllegalArgumentException("So e possivel fechar um mes que ja terminou.");
        if (mesFechado(unidade, mes)) throw new IllegalStateException("O mes " + mes + " ja esta fechado.");
        PontoFechamento f = new PontoFechamento();
        f.unidade = unidade;
        f.mes = mes.toString();
        f.fechadoPor = usuario;
        f.persist();
        return situacaoFechamento(unidade, mes);
    }

    @Transactional
    public Map<String, Object> reabrirMes(String unidade, YearMonth mes, String motivo, String usuario) {
        if (vazio(motivo)) throw new IllegalArgumentException("Informe o motivo da reabertura.");
        PontoFechamento f = PontoFechamento.<PontoFechamento>find("unidade = ?1 and mes = ?2 and ativo = true", unidade, mes.toString()).firstResult();
        if (f == null) throw new IllegalStateException("O mes nao esta fechado.");
        f.ativo = false;
        f.reabertoPor = usuario;
        f.dataReabertura = LocalDateTime.now();
        f.motivoReabertura = motivo.trim();
        return situacaoFechamento(unidade, mes);
    }

    private void exigirMesesAbertos(String unidade, LocalDate de, LocalDate ate) {
        for (YearMonth m = YearMonth.from(de); !m.isAfter(YearMonth.from(ate)); m = m.plusMonths(1)) {
            if (mesFechado(unidade, m))
                throw new IllegalStateException("O ponto de " + m.getMonthValue() + "/" + m.getYear() + " ja foi fechado para a folha. Reabra o mes para alterar.");
        }
    }

    private static LocalDate data(String v, String erro) {
        try {
            return LocalDate.parse(v.trim().substring(0, 10));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(erro);
        }
    }

    private static String br(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static String limitar(String v, int max) {
        if (vazio(v)) return null;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private PontoMarcacao ultimaValida(String matricula) {
        return PontoMarcacao.<PontoMarcacao>find("matricula = ?1 and anulada = false order by dataHora desc", matricula).firstResult();
    }

    private Servidor servidorComFoto(String matricula) {
        String mat = matricula == null ? "" : matricula.replaceAll("\\D", "");
        if (mat.isBlank()) throw new IllegalArgumentException("Informe a matricula.");
        if (!reconhecimentoFacialService.configurado()) throw new IllegalStateException("Reconhecimento facial nao configurado no servidor.");
        Servidor s = servidorRepository.localizarMatricula(mat);
        if (s == null) throw new IllegalArgumentException("Matricula nao encontrada.");
        exigirAcesso(s);
        if (s.getFoto() == null || s.getFoto().isBlank()) throw new IllegalArgumentException("Servidor sem foto cadastrada. Procure o RH.");
        return s;
    }

    private void exigirAcesso(Servidor s) {
        if (!securityPermissionService.acessoPermitido(s)) {
            throw new IllegalArgumentException("Ponto bloqueado. Procure a administracao da unidade para regularizar o cadastro.");
        }
    }

    private void exigirSemBloqueio(String matricula) {
        long agora = System.currentTimeMillis();
        List<Long> lista = falhas.getOrDefault(matricula, List.of()).stream().filter(t -> agora - t < JANELA_FALHAS_MS).toList();
        if (lista.size() >= MAX_FALHAS) throw new IllegalStateException("Muitas tentativas sem reconhecimento. Aguarde 10 minutos ou procure o RH.");
    }

    private void registrarFalha(String matricula) {
        long agora = System.currentTimeMillis();
        falhas.compute(matricula, (k, v) -> {
            List<Long> l = new ArrayList<>(v == null ? List.of() : v);
            l.removeIf(t -> agora - t >= JANELA_FALHAS_MS);
            l.add(agora);
            return l;
        });
    }

    private static boolean vazio(String v) {
        return v == null || v.trim().isEmpty();
    }

    private static String texto(Map<String, Object> p, String chave) {
        Object v = p == null ? null : p.get(chave);
        return v == null ? null : String.valueOf(v);
    }
}

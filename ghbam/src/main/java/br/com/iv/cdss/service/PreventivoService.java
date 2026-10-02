package br.com.iv.cdss.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.regex.Pattern;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ForbiddenException;

@ApplicationScoped
public class PreventivoService {
    @Inject EntityManager em;
    public static final String TIPO = "RISCO_PREVENTIVO";

    public record Registro(String titulo, String descricao, String observacao, String codigo) {
        public Registro(String titulo, String descricao, String observacao) {
            this(titulo, descricao, observacao, null);
        }
    }
    public record AtualizacaoStatus(String status, String observacao) {}
    public record Fonte(String tipo, String id, String bam, String data, String profissional, String texto, String unidadeOrigem) {}
    public record Vital(String idbam, String data, Double pasist, Double padiast, Double fc, Double fr, Double spo, Double peso, Double tax, String unidadeOrigem) {}
    public record Evidencia(String label, String detalhe) {}
    public record AlertaPreventivo(String codigo, String condicao, String severidade, String especialista,
            String mensagem, String recomendacao, List<Evidencia> evidencias, boolean diagnostico) {}
    public record FilaPreventivaItem(Integer idpaciente, String nome, String idade, String ultimoBam,
            String ultimaData, List<String> unidadesOrigem, List<AlertaPreventivo> alertasAutomaticos, List<ProntuarioClinicoItem> acompanhamentos) {}

    public void verificarPaciente(int id, AuthenticatedUser user) {
        if (id <= 0) throw new BadRequestException("Paciente invalido.");
        if (user == null || user.unidade() == null || user.unidade().isBlank() || "TODAS".equalsIgnoreCase(user.unidade())) throw new ForbiddenException();
        boolean redeCompleta = escopoRedeCompleta(user);
        var query = em.createNativeQuery(redeCompleta
                ? "SELECT count(*) FROM bam WHERE paciente_idpaciente = :id"
                : "SELECT count(*) FROM bam WHERE paciente_idpaciente = :id AND unidade = :unidade")
                .setParameter("id", id);
        if (!redeCompleta) query.setParameter("unidade", user.unidade());
        if (((Number) query.getSingleResult()).intValue() == 0) throw new NotFoundException();
    }

    public Map<String, Object> avaliar(int id, AuthenticatedUser user) {
        verificarPaciente(id, user);
        List<ProntuarioClinicoItem> alertas = em.createQuery(
            "from ProntuarioClinicoItem where idpaciente = :id and tipo = :tipo and unidade = :unidade order by criadoEm desc", ProntuarioClinicoItem.class)
            .setParameter("id", id).setParameter("tipo", TIPO).setParameter("unidade", user.unidade()).getResultList();
        List<Fonte> fontes = fontesPaciente(id, user);
        List<Vital> vitais = vitaisPaciente(id, user);
        List<AlertaPreventivo> automaticos = avaliarAutomaticamente(fontes, vitais);
        boolean redeCompleta = escopoRedeCompleta(user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idpaciente", id);
        result.put("modo", "REVISAO_PROFISSIONAL");
        result.put("previsaoAutomaticaAtiva", true);
        result.put("podeRegistrar", user.hasPermission("kmedico") || user.hasPermission("kenfermagem"));
        result.put("avaliadoEm", LocalDateTime.now().toString());
        result.put("alertas", alertas);
        result.put("fontes", fontes);
        result.put("alertasAutomaticos", automaticos);
        result.put("escopoHistorico", redeCompleta ? "REDE_COMPLETA" : "UNIDADE_ATUAL");
        result.put("incluiOutrasUnidades", redeCompleta);
        result.put("limitePorFonte", 201);
        result.put("aviso", redeCompleta
                ? "Alertas automaticos sao triagem preventiva por regras auditaveis e incluem historico de outras unidades autorizadas. Nao representam diagnostico e ausencia de alerta nao significa ausencia de risco."
                : "Alertas automaticos sao triagem preventiva por regras auditaveis. Nao representam diagnostico e ausencia de alerta nao significa ausencia de risco.");
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> listarFila(AuthenticatedUser user, int limite) {
        return listarFila(user, limite, null);
    }

    public Map<String, Object> listarFila(AuthenticatedUser user, int limite, String busca) {
        if (user == null || user.unidade() == null || user.unidade().isBlank() || "TODAS".equalsIgnoreCase(user.unidade())) throw new ForbiddenException();
        int limiteSeguro = Math.max(10, Math.min(limite <= 0 ? 80 : limite, 200));
        String buscaLimpa = texto(busca).trim();
        boolean filtrarNome = buscaLimpa.length() >= 2;
        boolean redeCompleta = escopoRedeCompleta(user);
        String buscaNormalizada = normalizar(buscaLimpa);
        // Busca por nome/BAM identifica um paciente especifico de proposito (o
        // profissional ja sabe quem procura), entao nao aplica o filtro de "ainda
        // em atendimento" - so a fila padrao (sem busca), que e' navegacao passiva,
        // fica restrita a quem ainda esta aguardando/em atendimento na unidade.
        String sql = filtrarNome ? """
            SELECT p.idpaciente, p.nome, p.idade, max(b.idbam) AS ultimo_bam, max(CAST(b.data AS date)) AS ultima_data
            FROM bam b
            JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
            JOIN avaliacao a ON a.idavaliacao = b.avaliacao_idavaliacao
            JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
            WHERE b.unidade = :unidade
              AND b.paciente_idpaciente IS NOT NULL
              AND (lower(p.nome) LIKE :busca OR CAST(b.idbam AS text) LIKE :busca)
            GROUP BY p.idpaciente, p.nome, p.idade
            ORDER BY p.nome ASC, ultimo_bam DESC
            """ : """
            SELECT p.idpaciente, p.nome, p.idade, max(b.idbam) AS ultimo_bam, max(CAST(b.data AS date)) AS ultima_data
            FROM bam b
            JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente
            JOIN avaliacao a ON a.idavaliacao = b.avaliacao_idavaliacao
            JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento
            WHERE b.unidade = :unidade
              AND b.paciente_idpaciente IS NOT NULL
              AND t.tratado = 0
              AND COALESCE(t.tipo_alta, '-') NOT IN ('internacao', 'obs', 'obito')
              AND (a.esperando = 1 OR b.ematendimento = true)
              AND CAST(b.data AS date) >= :desde
            GROUP BY p.idpaciente, p.nome, p.idade
            ORDER BY ultima_data DESC, ultimo_bam DESC
            """;
        var query = em.createNativeQuery(sql)
            .setMaxResults(filtrarNome ? Math.max(10, Math.min(limiteSeguro, 80)) : Math.min(limiteSeguro, 40));
        query.setParameter("unidade", user.unidade());
        if (filtrarNome) query.setParameter("busca", "%" + buscaNormalizada + "%");
        // Fila passiva: BAM esquecido "em atendimento" ha dias nao e' paciente
        // na unidade. b.data so tem dia, entao hoje-2 garante no minimo 48h.
        else query.setParameter("desde", LocalDate.now().minusDays(2));
        List<Object[]> rows = query.getResultList();

        List<FilaPreventivaItem> itens = new ArrayList<>();
        for (Object[] row : rows) {
            Integer idpaciente = numero(row[0]);
            if (idpaciente == null) continue;
            List<ProntuarioClinicoItem> acompanhamentos = alertasDoPaciente(idpaciente, user);
            List<String> codigosJaTratados = acompanhamentos.stream().map(ProntuarioClinicoItem::getCodigo).filter(c -> !texto(c).isBlank()).toList();
            List<Fonte> fontesPaciente = fontesPaciente(idpaciente, user);
            List<Vital> vitaisPaciente = vitaisPaciente(idpaciente, user);
            List<AlertaPreventivo> alertas = avaliarAutomaticamente(fontesPaciente, vitaisPaciente).stream()
                    .filter(alerta -> !codigosJaTratados.contains(alerta.codigo()))
                    .toList();
            List<String> unidadesOrigem = fontesPaciente.stream()
                    .map(Fonte::unidadeOrigem)
                    .map(PreventivoService::texto)
                    .map(String::trim)
                    .filter(valor -> !valor.isBlank())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            List<ProntuarioClinicoItem> ativos = acompanhamentos.stream().filter(item -> "ATIVO".equals(item.getStatus())).toList();
            FilaPreventivaItem item = new FilaPreventivaItem(idpaciente, texto(row[1]), texto(row[2]), texto(row[3]), texto(row[4]), unidadesOrigem, alertas, ativos);
            if ((!alertas.isEmpty() || !ativos.isEmpty()) && passaBuscaFila(item, buscaNormalizada)) {
                itens.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modo", "FILA_PREVENTIVA");
        result.put("previsaoAutomaticaAtiva", true);
        result.put("avaliadoEm", LocalDateTime.now().toString());
        result.put("unidade", user.unidade());
        result.put("escopoHistorico", redeCompleta ? "REDE_COMPLETA" : "UNIDADE_ATUAL");
        result.put("incluiOutrasUnidades", redeCompleta);
        result.put("limitePacientesRecentes", limiteSeguro);
        result.put("busca", buscaLimpa);
        result.put("totalAvaliados", rows.size());
        result.put("totalSinalizados", itens.size());
        result.put("itens", itens);
        result.put("aviso", redeCompleta
                ? "Fila limitada aos pacientes aguardando atendimento ou em atendimento na unidade atual. Historico de outras unidades autorizadas pode ser usado apenas como evidencia desses pacientes. Nao representa diagnostico, priorizacao final ou ausencia de risco nos pacientes nao listados."
                : "Fila limitada aos pacientes aguardando atendimento ou em atendimento na unidade atual. Nao representa diagnostico, priorizacao final ou ausencia de risco nos pacientes nao listados.");
        return result;
    }

    public List<AlertaPreventivo> avaliarAutomaticamente(List<Fonte> fontes) {
        return avaliarAutomaticamente(fontes, List.of());
    }

    public List<AlertaPreventivo> avaliarAutomaticamente(List<Fonte> fontes, List<Vital> vitais) {
        String texto = normalizar(String.join("\n", fontes.stream().map(f -> texto(f.texto())).toList()));
        List<AlertaPreventivo> alertas = new ArrayList<>();
        avaliarRiscoDiabetes(texto, alertas);
        avaliarRiscoOncologico(vitais, texto, alertas);
        avaliarRiscoCardiovascular(fontes, vitais, texto, alertas);
        avaliarRiscoRenal(texto, alertas);
        avaliarRiscoRespiratorio(vitais, texto, alertas);
        avaliarRiscoFragilidadeQueda(texto, alertas);
        avaliarRiscoInfeccioso(vitais, texto, alertas);
        alertas.sort(Comparator.comparingInt(a -> ordemSeveridadePreventiva(a.severidade())));
        return alertas;
    }

    private void avaliarRiscoDiabetes(String texto, List<AlertaPreventivo> alertas) {
        List<Evidencia> evidencias = new ArrayList<>();
        int termos = ocorrencias(texto, "\\bdiabet\\w*|\\bhiperglicem\\w*|\\bglicemia alterada\\b|\\bhemoglobina glicada\\b|\\bhba1c\\b");
        if (termos >= 2) evidencias.add(new Evidencia("Termos metabólicos recorrentes", termos + " menção(ões) no histórico"));
        if (ocorrencias(texto, "\\bglicemia\\D{0,20}(?:1[2-9]\\d|[2-9]\\d{2})\\b|\\bhgt\\D{0,20}(?:1[2-9]\\d|[2-9]\\d{2})\\b") >= 2)
            evidencias.add(new Evidencia("Glicemia alterada recorrente", "Dois ou mais registros textuais compatíveis"));
        if (!evidencias.isEmpty()) {
            alertas.add(alertaPreventivo("CDSS_PREV_DIABETES", "Risco metabólico/diabetes", evidencias.size() >= 2 ? "HIGH" : "MODERATE",
                    "Endocrinologia ou clínica médica", "Padrão longitudinal sugere risco metabólico que merece revisão clínica.",
                    "Avaliar glicemia de jejum, HbA1c, fatores de risco e necessidade de acompanhamento especializado.", evidencias));
        }
    }

    private void avaliarRiscoOncologico(List<Vital> vitais, String texto, List<AlertaPreventivo> alertas) {
        List<Evidencia> evidencias = new ArrayList<>();
        adicionarSeEncontrar(evidencias, texto, "\\bperda de peso\\b|\\bemagrecimento\\b|\\bcaquex\\w*", "Perda ponderal ou caquexia");
        adicionarSeEncontrar(evidencias, texto, "\\banemia\\b|\\bhemoglobina baixa\\b|\\bhb baixa\\b", "Anemia ou hemoglobina baixa");
        adicionarSeEncontrar(evidencias, texto, "\\bsangramento\\b|\\bhematoquezia\\b|\\bmelena\\b|\\bhemoptise\\b", "Sangramento registrado");
        adicionarSeEncontrar(evidencias, texto, "\\bnodulo\\b|\\bmassa\\b|\\blesao suspeita\\b|\\bneoplas\\w*|\\btumor\\b|\\bcancer\\b", "Lesão, nódulo ou suspeita oncológica");
        List<Double> pesos = vitais.stream()
                .filter(v -> v.peso() != null && v.peso() > 0)
                .map(Vital::peso)
                .toList();
        if (pesos.size() >= 2) {
            double pesoMaisRecente = pesos.get(0);
            double pesoMaisAntigo = pesos.get(pesos.size() - 1);
            double quedaPercentual = (pesoMaisAntigo - pesoMaisRecente) / pesoMaisAntigo * 100;
            if (quedaPercentual >= 5) {
                evidencias.add(new Evidencia("Perda de peso ≥5% (medição estruturada)",
                        String.format(Locale.ROOT, "Queda de %.0f%% entre %d medições", quedaPercentual, pesos.size())));
            }
        }
        if (evidencias.size() >= 2) {
            alertas.add(alertaPreventivo("CDSS_PREV_ONCOLOGICO", "Sinais para investigação oncológica", evidencias.size() >= 3 ? "HIGH" : "MODERATE",
                    "Especialista conforme sítio suspeito ou oncologia", "Associação de sinais no histórico pode justificar investigação dirigida.",
                    "Revisar sintomas, exame físico, exames laboratoriais/imagem e definir encaminhamento conforme protocolo local.", evidencias));
        }
    }

    private void avaliarRiscoCardiovascular(List<Fonte> fontes, List<Vital> vitais, String texto, List<AlertaPreventivo> alertas) {
        List<Evidencia> evidencias = new ArrayList<>();
        long triagensComPaAlta = fontes.stream().filter(f -> "TRIAGEM".equals(f.tipo()))
                .filter(f -> Pattern.compile("\\b1[4-9]\\d\\b|\\b[2-9]\\d{2}\\b").matcher(texto(f.texto())).find()).count();
        if (triagensComPaAlta >= 2) evidencias.add(new Evidencia("Pressão arterial elevada recorrente", triagensComPaAlta + " atendimento(s)"));
        long medicoesPaAlta = vitais.stream()
                .filter(v -> (v.pasist() != null && v.pasist() >= 140) || (v.padiast() != null && v.padiast() >= 90))
                .count();
        if (medicoesPaAlta >= 2) evidencias.add(new Evidencia("PA sistólica/diastólica elevada (medição estruturada)", medicoesPaAlta + " atendimento(s)"));
        int termos = ocorrencias(texto, "\\bhipertens\\w*|\\bdislipidem\\w*|\\btabag\\w*|\\bdor toracica\\b|\\binsuficiencia cardiaca\\b");
        if (termos >= 2) evidencias.add(new Evidencia("Fatores cardiovasculares recorrentes", termos + " menção(ões)"));
        if (!evidencias.isEmpty()) {
            alertas.add(alertaPreventivo("CDSS_PREV_CARDIOVASCULAR", "Risco cardiovascular", evidencias.size() >= 2 ? "HIGH" : "MODERATE",
                    "Cardiologia ou clínica médica", "Histórico sugere necessidade de revisão de risco cardiovascular.",
                    "Conferir PA seriada, perfil lipídico, diabetes, tabagismo e estratificação cardiovascular.", evidencias));
        }
    }

    private void avaliarRiscoRenal(String texto, List<AlertaPreventivo> alertas) {
        List<Evidencia> evidencias = new ArrayList<>();
        int termos = ocorrencias(texto, "\\bcreatinina (alta|elevada|subindo)\\b|\\btfg baixa\\b|\\begfr baixo\\b|\\bclearance baixo\\b|\\bproteinuria\\b|\\binsuficiencia renal\\b|\\bdrc\\b|\\binjuria renal\\b");
        if (termos > 0) evidencias.add(new Evidencia("Marcadores renais alterados", termos + " menção(ões)"));
        if (!evidencias.isEmpty()) {
            alertas.add(alertaPreventivo("CDSS_PREV_RENAL", "Risco renal", termos >= 2 ? "HIGH" : "MODERATE",
                    "Nefrologia ou clínica médica", "Há achados compatíveis com risco de piora da função renal.",
                    "Revisar creatinina/eTFG seriadas, proteinúria, medicamentos nefrotóxicos e comorbidades.", evidencias));
        }
    }

    private void avaliarRiscoRespiratorio(List<Vital> vitais, String texto, List<AlertaPreventivo> alertas) {
        List<Evidencia> evidencias = new ArrayList<>();
        int sintomas = ocorrencias(texto, "\\bdispneia\\b|\\bfalta de ar\\b|\\bsibil\\w*|\\btosse cronica\\b|\\bbroncoespasmo\\b");
        int condicoes = ocorrencias(texto, "\\bdpoc\\b|\\benfisema\\b|\\basma\\b|\\btabag\\w*|\\bex-tabag\\w*");
        int saturacao = ocorrencias(texto, "\\bspo2?\\D{0,15}(?:8\\d|9[0-2])\\b|\\bsaturacao\\D{0,15}(?:8\\d|9[0-2])\\b");
        long medicoesSpo2Baixo = vitais.stream().filter(v -> v.spo() != null && v.spo() > 0 && v.spo() < 93).count();
        long medicoesTaquipneia = vitais.stream().filter(v -> v.fr() != null && v.fr() >= 24).count();
        if (sintomas >= 2) evidencias.add(new Evidencia("Sintomas respiratórios recorrentes", sintomas + " menção(ões)"));
        if (condicoes > 0) evidencias.add(new Evidencia("Condição ou fator respiratório registrado", condicoes + " menção(ões)"));
        if (saturacao > 0) evidencias.add(new Evidencia("Saturação baixa registrada", saturacao + " registro(s) textual(is)"));
        if (medicoesSpo2Baixo > 0) evidencias.add(new Evidencia("Saturação baixa (medição estruturada)", medicoesSpo2Baixo + " atendimento(s)"));
        if (medicoesTaquipneia >= 2) evidencias.add(new Evidencia("Frequência respiratória elevada recorrente", medicoesTaquipneia + " atendimento(s)"));
        if (evidencias.size() >= 2) {
            alertas.add(alertaPreventivo("CDSS_PREV_RESPIRATORIO", "Risco respiratório crônico", evidencias.size() >= 3 ? "HIGH" : "MODERATE",
                    "Pneumologia ou clínica médica", "Histórico sugere necessidade de revisão preventiva respiratória.",
                    "Revisar sintomas persistentes, saturação seriada, tabagismo, espirometria quando indicada e plano de acompanhamento.", evidencias));
        }
    }

    private void avaliarRiscoFragilidadeQueda(String texto, List<AlertaPreventivo> alertas) {
        List<Evidencia> evidencias = new ArrayList<>();
        int quedas = ocorrencias(texto, "\\bqueda\\b|\\bquedas\\b|\\bcaiu\\b|\\btrauma por queda\\b");
        int fragilidade = ocorrencias(texto, "\\bfragilidade\\b|\\bidoso fragil\\b|\\bdesnutr\\w*|\\bperda funcional\\b|\\bdependencia funcional\\b");
        int mobilidade = ocorrencias(texto, "\\bdificuldade de marcha\\b|\\bmarcha instavel\\b|\\btontura\\b|\\bsincope\\b|\\brisco de queda\\b");
        if (quedas > 0) evidencias.add(new Evidencia("Queda registrada", quedas + " menção(ões)"));
        if (fragilidade > 0) evidencias.add(new Evidencia("Fragilidade ou perda funcional", fragilidade + " menção(ões)"));
        if (mobilidade > 0) evidencias.add(new Evidencia("Mobilidade ou equilíbrio alterado", mobilidade + " menção(ões)"));
        if (evidencias.size() >= 2 || quedas >= 2) {
            alertas.add(alertaPreventivo("CDSS_PREV_FRAGILIDADE_QUEDA", "Risco de fragilidade/queda", evidencias.size() >= 3 || quedas >= 2 ? "HIGH" : "MODERATE",
                    "Geriatria, fisioterapia ou clínica médica", "Histórico sugere risco prevenível de queda, perda funcional ou fragilidade.",
                    "Revisar medicamentos, marcha, visão, cognição, suporte domiciliar e necessidade de plano multidisciplinar.", evidencias));
        }
    }

    private void avaliarRiscoInfeccioso(List<Vital> vitais, String texto, List<AlertaPreventivo> alertas) {
        List<Evidencia> evidencias = new ArrayList<>();
        int termos = ocorrencias(texto, "\\bfebre\\b|\\bcalafrio\\w*|\\bsepse\\b|\\bsept\\w*|\\bfoco infeccioso\\b|\\binfecca\\w*|\\binfeccios\\w*");
        if (termos >= 2) evidencias.add(new Evidencia("Sinais infecciosos recorrentes", termos + " menção(ões)"));

        long encontrosComCriteriosSirs = vitais.stream().filter(this::atendeCriteriosSirs).count();
        boolean maisRecenteComSirs = !vitais.isEmpty() && atendeCriteriosSirs(vitais.get(0));
        if (maisRecenteComSirs) {
            evidencias.add(new Evidencia("Critérios SIRS no atendimento mais recente (medição estruturada)",
                    "Febre/hipotermia, taquicardia ou taquipneia combinadas"));
        } else if (encontrosComCriteriosSirs >= 2) {
            evidencias.add(new Evidencia("Critérios SIRS recorrentes (medição estruturada)", encontrosComCriteriosSirs + " atendimento(s)"));
        }

        if (!evidencias.isEmpty()) {
            // SIRS no atendimento mais recente e agudo/tempo-sensivel: HIGH mesmo com uma unica evidencia,
            // diferente das demais categorias (cronicas) que exigem 2+ evidencias para HIGH.
            String severidade = maisRecenteComSirs || evidencias.size() >= 2 ? "HIGH" : "MODERATE";
            alertas.add(alertaPreventivo("CDSS_PREV_INFECCIOSO", "Risco infeccioso/resposta inflamatória sistêmica", severidade,
                    "Clínica médica, infectologia ou conforme foco suspeito",
                    "Sinais vitais e/ou histórico sugerem possível processo infeccioso ou resposta inflamatória sistêmica.",
                    "Avaliar foco infeccioso, solicitar exames pertinentes (hemograma, lactato, culturas conforme indicação) e considerar critérios de sepse/qSOFA.",
                    evidencias));
        }
    }

    private boolean atendeCriteriosSirs(Vital vital) {
        int criterios = 0;
        if (vital.tax() != null && vital.tax() > 0 && (vital.tax() >= 38 || vital.tax() < 36)) criterios++;
        if (vital.fc() != null && vital.fc() > 90) criterios++;
        if (vital.fr() != null && vital.fr() > 20) criterios++;
        return criterios >= 2;
    }

    private AlertaPreventivo alertaPreventivo(String codigo, String condicao, String severidade, String especialista,
            String mensagem, String recomendacao, List<Evidencia> evidencias) {
        return new AlertaPreventivo(codigo, condicao, severidade, especialista, mensagem, recomendacao, List.copyOf(evidencias), false);
    }

    private void adicionarSeEncontrar(List<Evidencia> evidencias, String texto, String regex, String label) {
        int total = ocorrencias(texto, regex);
        if (total > 0) evidencias.add(new Evidencia(label, total + " menção(ões)"));
    }

    private int ocorrencias(String texto, String regex) {
        var matcher = Pattern.compile(regex).matcher(texto);
        int total = 0;
        while (matcher.find()) total++;
        return total;
    }

    private int ordemSeveridadePreventiva(String severidade) {
        return "HIGH".equalsIgnoreCase(severidade) ? 0 : "MODERATE".equalsIgnoreCase(severidade) ? 1 : 2;
    }

    private boolean passaBuscaFila(FilaPreventivaItem item, String buscaNormalizada) {
        if (buscaNormalizada == null || buscaNormalizada.isBlank()) return true;
        String texto = normalizar(String.join(" ",
                texto(item.nome()),
                texto(item.idpaciente()),
                texto(item.ultimoBam()),
                String.join(" ", item.unidadesOrigem()),
                String.join(" ", item.alertasAutomaticos().stream().flatMap(alerta -> List.of(
                        alerta.codigo(),
                        alerta.condicao(),
                        alerta.especialista(),
                        alerta.mensagem(),
                        alerta.recomendacao()).stream()).toList())));
        return texto.contains(buscaNormalizada);
    }

    private boolean contemTermoClinicoDeFila(String buscaNormalizada) {
        return !buscaNormalizada.matches("\\d+") && ocorrencias(buscaNormalizada,
                "diabet|metabolic|endocrin|oncolog|cancer|tumor|cardio|hipertens|renal|nefro|respirator|pneumo|dpoc|asma|queda|fragil|geriatr|alta|moderad") > 0;
    }

    @SuppressWarnings("unchecked")
    private List<Fonte> fontes(int id, AuthenticatedUser user, String tipo, String sql) {
        boolean redeCompleta = escopoRedeCompleta(user);
        var query = em.createNativeQuery(sql).setParameter("id", id).setMaxResults(201);
        if (!redeCompleta) query.setParameter("unidade", user.unidade());
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(r -> new Fonte(tipo, texto(r[0]), texto(r[1]), texto(r[2]), texto(r[3]), texto(r[4]), texto(r[5]))).toList();
    }

    private List<ProntuarioClinicoItem> alertasDoPaciente(int id, AuthenticatedUser user) {
        return em.createQuery(
            "from ProntuarioClinicoItem where idpaciente = :id and tipo = :tipo and unidade = :unidade order by criadoEm desc", ProntuarioClinicoItem.class)
            .setParameter("id", id).setParameter("tipo", TIPO).setParameter("unidade", user.unidade()).getResultList();
    }

    private List<Fonte> fontesPaciente(int id, AuthenticatedUser user) {
        boolean redeCompleta = escopoRedeCompleta(user);
        String filtroBam = redeCompleta ? "" : " AND b.unidade = :unidade";
        String filtroUnidade = redeCompleta ? "" : " AND unidade = :unidade";
        String filtroLaboratorio = redeCompleta ? "" : " AND b.unidade = :unidade AND l.unidade = :unidade";
        List<Fonte> fontes = new ArrayList<>();
        fontes.addAll(fontes(id, user, "PACS", "SELECT i.idimagens, i.idbam, i.data, i.atendidopor, i.laudo, b.unidade FROM imagens i JOIN bam b ON b.idbam = i.idbam WHERE i.idpacientes = :id AND b.paciente_idpaciente = :id" + filtroBam + " ORDER BY i.idimagens DESC"));
        fontes.addAll(fontes(id, user, "LABORATORIO", "SELECT l.idpedidoresultado, l.bam, l.dt, l.medico, concat_ws(' - ', l.descricao, l.grupopedidos), coalesce(l.unidade, b.unidade) FROM pedidoresultadolaboratorio l JOIN bam b ON b.idbam = l.bam WHERE b.paciente_idpaciente = :id" + filtroLaboratorio + " ORDER BY l.idpedidoresultado DESC"));
        fontes.addAll(fontes(id, user, "MULTIDISCIPLINAR", "SELECT id, idbam, dataatendimento, profissional, concat_ws(E'\\n', queixaprincipal, historiaatual, riscosidentificados, evolucao, condutas), unidade FROM atendimentomultidisciplinar WHERE idpaciente = :id" + filtroUnidade + " AND ativo = true ORDER BY id DESC"));
        fontes.addAll(fontes(id, user, "ANAMNESE", "SELECT b.idbam, b.idbam, b.data, t.medicoecrm, concat_ws(E'\\n', a.anamnese, a.hpp, a.comentario), b.unidade FROM bam b JOIN avaliacao a ON a.idavaliacao = b.avaliacao_idavaliacao JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento WHERE b.paciente_idpaciente = :id" + filtroBam + " ORDER BY b.idbam DESC"));
        fontes.addAll(fontes(id, user, "TRIAGEM", "SELECT b.idbam, b.idbam, b.data, t.medicoecrm, concat_ws(E'\\n', a.pasist, a.padiast, a.fc, a.fr, a.spo, a.tax, a.peso, a.risco), b.unidade FROM bam b JOIN avaliacao a ON a.idavaliacao = b.avaliacao_idavaliacao JOIN tratamento t ON t.idtratamento = b.tratamento_idtratamento WHERE b.paciente_idpaciente = :id" + filtroBam + " ORDER BY b.idbam DESC"));
        return fontes;
    }

    @SuppressWarnings("unchecked")
    private List<Vital> vitaisPaciente(int id, AuthenticatedUser user) {
        boolean redeCompleta = escopoRedeCompleta(user);
        String filtroBam = redeCompleta ? "" : " AND b.unidade = :unidade";
        String sql = "SELECT b.idbam, b.data, a.pasist, a.padiast, a.fc, a.fr, a.spo, a.peso, a.tax, b.unidade " +
                "FROM bam b JOIN avaliacao a ON a.idavaliacao = b.avaliacao_idavaliacao " +
                "WHERE b.paciente_idpaciente = :id" + filtroBam + " ORDER BY b.idbam DESC";
        var query = em.createNativeQuery(sql).setParameter("id", id).setMaxResults(201);
        if (!redeCompleta) query.setParameter("unidade", user.unidade());
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(r -> new Vital(texto(r[0]), texto(r[1]), numeroDouble(r[2]), numeroDouble(r[3]), numeroDouble(r[4]), numeroDouble(r[5]), numeroDouble(r[6]), numeroDouble(r[7]), numeroDouble(r[8]), texto(r[9]))).toList();
    }

    private static Double numeroDouble(Object valor) {
        if (valor instanceof Number n) return n.doubleValue();
        try { return valor == null ? null : Double.parseDouble(valor.toString()); }
        catch (NumberFormatException ex) { return null; }
    }

    private boolean escopoRedeCompleta(AuthenticatedUser user) {
        return user != null && user.hasPermission("pprontuariocompleto");
    }

    // Texto com o historico de VARIOS atendimentos (nao so o ultimo BAM), pra uso
    // por quem precisa perceber TENDENCIA ao longo do tempo (ex.: Analise de Risco
    // por IA do Prontuario Completo) - reaproveita as mesmas buscas ja usadas pelo
    // motor de regras do CDSS (fontesPaciente/vitaisPaciente), so muda a saida
    // (texto legivel em vez de records internos). Limitado aos ultimos 40
    // registros de cada lista pra nao estourar o contexto do modelo de IA.
    public String montarContextoLongitudinal(int idpaciente, AuthenticatedUser user) {
        List<Fonte> fontes = fontesPaciente(idpaciente, user);
        List<Vital> vitais = vitaisPaciente(idpaciente, user);
        if (fontes.isEmpty() && vitais.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("--- HISTORICO LONGITUDINAL DO PACIENTE (multiplos atendimentos, mais recente primeiro) ---\n\n");
        if (!vitais.isEmpty()) {
            sb.append("SERIE DE SINAIS VITAIS POR ATENDIMENTO:\n");
            vitais.stream().limit(40).forEach(v -> sb.append("  BAM ").append(v.idbam())
                    .append(" (").append(v.data()).append("): PA ")
                    .append(v.pasist() != null ? v.pasist().intValue() : "-").append("/")
                    .append(v.padiast() != null ? v.padiast().intValue() : "-")
                    .append(" FC ").append(v.fc() != null ? v.fc().intValue() : "-")
                    .append(" FR ").append(v.fr() != null ? v.fr().intValue() : "-")
                    .append(" SpO2 ").append(v.spo() != null ? v.spo().intValue() : "-")
                    .append(" Temp ").append(v.tax() != null ? v.tax() : "-")
                    .append(" Peso ").append(v.peso() != null ? v.peso() : "-")
                    .append("\n"));
            sb.append("\n");
        }
        if (!fontes.isEmpty()) {
            sb.append("REGISTROS POR TIPO/ATENDIMENTO:\n");
            fontes.stream().limit(40).forEach(f -> sb.append("  [").append(f.tipo()).append("] BAM ")
                    .append(f.bam()).append(" (").append(f.data()).append(", ").append(f.profissional()).append("): ")
                    .append(resumoCurto(f.texto())).append("\n"));
        }
        return sb.toString();
    }

    private String resumoCurto(String valor) {
        String limpo = texto(valor).replace(" ", "").trim();
        return limpo.length() <= 400 ? limpo : limpo.substring(0, 400) + "...";
    }

    private static Integer numero(Object valor) {
        if (valor instanceof Number n) return n.intValue();
        try { return valor == null ? null : Integer.parseInt(valor.toString()); }
        catch (NumberFormatException ex) { return null; }
    }

    private static String texto(Object valor) { return valor == null ? "" : valor.toString(); }

    private static String normalizar(String valor) {
        String normalizado = Normalizer.normalize(texto(valor).toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalizado.replaceAll("\\p{M}", "");
    }

    public static void validar(Registro registro) {
        if (registro == null || registro.titulo() == null || registro.titulo().isBlank() || registro.titulo().length() > 180)
            throw new BadRequestException("Informe o risco (ate 180 caracteres).");
        if (registro.descricao() == null || registro.descricao().trim().length() < 15 || registro.descricao().length() > 10000)
            throw new BadRequestException("Registre as evidencias e suas fontes (15 a 10000 caracteres).");
        if (registro.observacao() == null || registro.observacao().trim().length() < 15 || registro.observacao().length() > 10000)
            throw new BadRequestException("Registre o acompanhamento ou justificativa (15 a 10000 caracteres).");
    }

    @Transactional
    public ProntuarioClinicoItem registrar(int id, Registro registro, AuthenticatedUser user) {
        verificarPaciente(id, user);
        validar(registro);
        ProntuarioClinicoItem item = new ProntuarioClinicoItem();
        item.setIdpaciente(id);
        item.setTipo(TIPO);
        item.setStatus("ATIVO");
        item.setCodigo(codigoRegistro(registro));
        item.setTitulo(registro.titulo().trim());
        item.setDescricao(registro.descricao().trim());
        item.setObservacao(registro.observacao().trim());
        item.setUnidade(user.unidade());
        item.setCriadoPor(user.nome());
        item.setCriadoPorMatricula(user.matricula());
        item.setAtualizadoPor(user.nome());
        item.setAtualizadoPorMatricula(user.matricula());
        em.persist(item);
        return item;
    }

    private static String codigoRegistro(Registro registro) {
        String codigo = texto(registro.codigo()).trim().toUpperCase(Locale.ROOT);
        return codigo.startsWith("CDSS_PREV_") && codigo.length() <= 80 ? codigo : "REVISAO_PROFISSIONAL_V1";
    }

    @Transactional
    public ProntuarioClinicoItem encerrar(int id, int itemId, Registro registro, AuthenticatedUser user) {
        return atualizarStatus(id, itemId, new AtualizacaoStatus("INATIVO", registro == null ? null : registro.observacao()), user);
    }

    @Transactional
    public ProntuarioClinicoItem atualizarStatus(int id, int itemId, AtualizacaoStatus atualizacao, AuthenticatedUser user) {
        verificarPaciente(id, user);
        String status = validarAtualizacaoStatus(atualizacao);
        ProntuarioClinicoItem item = em.find(ProntuarioClinicoItem.class, itemId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (item == null || item.getIdpaciente() != id || !TIPO.equals(item.getTipo()) || !user.unidade().equals(item.getUnidade())) throw new NotFoundException();
        if (!"ATIVO".equals(item.getStatus()) && !"ATIVO".equals(status)) throw new BadRequestException("Alerta preventivo ja finalizado.");
        item.setStatus(status);
        item.setObservacao(texto(item.getObservacao()) + "\nStatus " + status + ": " + atualizacao.observacao().trim());
        item.setAtualizadoPor(user.nome());
        item.setAtualizadoPorMatricula(user.matricula());
        return item;
    }

    public static String validarAtualizacaoStatus(AtualizacaoStatus atualizacao) {
        if (atualizacao == null || atualizacao.observacao() == null || atualizacao.observacao().trim().length() < 15 || atualizacao.observacao().length() > 10000)
            throw new BadRequestException("Justifique a atualizacao do alerta preventivo (15 a 10000 caracteres).");
        String status = texto(atualizacao.status()).trim().toUpperCase(Locale.ROOT);
        if (!List.of("ATIVO", "ENCAMINHADO", "DESCARTADO", "RESOLVIDO", "INATIVO").contains(status))
            throw new BadRequestException("Status preventivo invalido.");
        return status;
    }
}

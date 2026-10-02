package br.com.iv.aps;

import static br.com.iv.aps.ApsCidadaoService.intOrNull;
import static br.com.iv.aps.ApsCidadaoService.isoDate;
import static br.com.iv.aps.ApsConfiguracaoService.num;
import static br.com.iv.aps.ApsConfiguracaoService.str;
import static br.com.iv.aps.ApsDb.p;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Agenda do profissional (manual PEC cap. 5 e 3.5.5/3.5.6) e Garantia do
 * Acesso (cap. 12.1): grade semanal, fechamentos, horarios livres,
 * agendamento/cancelamento/falta/reserva e fila de demanda reprimida.
 */
@ApplicationScoped
public class ApsAgendaService {

    @Inject
    ApsDb db;

    public List<Map<String, Object>> configuracao(String matricula, String unidade) {
        return db.list("""
                SELECT * FROM aps_agenda_config WHERE matricula = :m AND UPPER(unidade) = UPPER(:u) AND ativo
                ORDER BY dia_semana, hora_inicio
                """, p("m", matricula, "u", unidade));
    }

    /** Agenda so para profissional com lotacao ativa na unidade logada. */
    void exigirLotacao(String matricula, String unidade) {
        Long n = db.count("SELECT COUNT(*) FROM aps_lotacao WHERE matricula = :m AND UPPER(unidade) = UPPER(:u) AND ativo",
                p("m", matricula, "u", unidade));
        if (n == null || n == 0) {
            throw new IllegalArgumentException("Profissional " + matricula + " sem lotacao ativa na unidade " + unidade
                    + ". Cadastre a lotacao em Configuracao antes de montar a agenda.");
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> salvarConfiguracao(String matricula, String unidade, List<Map<String, Object>> itens) {
        exigirLotacao(matricula, unidade);
        db.exec("UPDATE aps_agenda_config SET ativo = FALSE WHERE matricula = :m AND UPPER(unidade) = UPPER(:u)",
                p("m", matricula, "u", unidade));
        for (Map<String, Object> it : itens) {
            Integer dia = intOrNull(it.get("dia_semana"));
            String ini = str(it.get("hora_inicio"));
            String fim = str(it.get("hora_fim"));
            Integer dur = intOrNull(it.get("duracao_minutos"));
            if (dia == null || ini == null || fim == null) {
                continue;
            }
            if (!LocalTime.parse(fim).isAfter(LocalTime.parse(ini))) {
                throw new IllegalArgumentException("Horario final deve ser maior que o inicial.");
            }
            db.exec("""
                    INSERT INTO aps_agenda_config (matricula, unidade, dia_semana, hora_inicio, hora_fim, duracao_minutos)
                    VALUES (:m, :u, :d, CAST(:i AS time), CAST(:f AS time), :dur)
                    """, p("m", matricula, "u", unidade, "d", dia, "i", ini, "f", fim, "dur", dur == null ? 20 : dur));
        }
        return configuracao(matricula, unidade);
    }

    public List<Map<String, Object>> fechamentos(String matricula, String unidade) {
        return db.list("""
                SELECT * FROM aps_agenda_fechamento WHERE matricula = :m AND UPPER(unidade) = UPPER(:u) AND data_fim >= CURRENT_DATE - 30
                ORDER BY data_inicio
                """, p("m", matricula, "u", unidade));
    }

    @Transactional
    public Map<String, Object> fecharAgenda(Map<String, Object> body, String unidade, String autor) {
        String matricula = str(body.get("matricula"));
        String ini = isoDate(body.get("dataInicio"));
        String fim = isoDate(body.get("dataFim"));
        String motivo = str(body.get("motivo"));
        if (matricula == null || ini == null || fim == null || motivo == null) {
            throw new IllegalArgumentException("Informe profissional, periodo e motivo do fechamento.");
        }
        exigirLotacao(matricula, unidade);
        Long id = db.insert("""
                INSERT INTO aps_agenda_fechamento (matricula, unidade, data_inicio, data_fim, motivo, criado_por)
                VALUES (:m, :u, CAST(:i AS date), CAST(:f AS date), :mot, :a) RETURNING id
                """, p("m", matricula, "u", unidade, "i", ini, "f", fim, "mot", motivo, "a", autor));
        return db.one("SELECT * FROM aps_agenda_fechamento WHERE id = :id", p("id", id));
    }

    @Transactional
    public void removerFechamento(Long id) {
        db.exec("DELETE FROM aps_agenda_fechamento WHERE id = :id", p("id", id));
    }

    /** Horarios livres do profissional no dia, pela grade semanal. */
    public List<Map<String, Object>> horarios(String matricula, String unidade, LocalDate data) {
        List<Map<String, Object>> out = new ArrayList<>();
        Long fechado = db.count("""
                SELECT COUNT(*) FROM aps_agenda_fechamento WHERE matricula = :m AND UPPER(unidade) = UPPER(:u)
                  AND CAST(:d AS date) BETWEEN data_inicio AND data_fim
                """, p("m", matricula, "u", unidade, "d", data));
        if (fechado > 0) {
            return out;
        }
        int dia = data.getDayOfWeek().getValue();
        List<Map<String, Object>> grade = db.list("""
                SELECT * FROM aps_agenda_config WHERE matricula = :m AND UPPER(unidade) = UPPER(:u) AND ativo AND dia_semana = :d
                ORDER BY hora_inicio
                """, p("m", matricula, "u", unidade, "d", dia));
        List<Map<String, Object>> ocupados = db.list("""
                SELECT data_hora, duracao_minutos FROM aps_agendamento
                WHERE matricula_profissional = :m AND CAST(data_hora AS date) = CAST(:d AS date)
                  AND status NOT IN ('CANCELADO')
                """, p("m", matricula, "d", data));
        for (Map<String, Object> g : grade) {
            LocalTime t = LocalTime.parse((String) g.get("hora_inicio"));
            LocalTime fim = LocalTime.parse((String) g.get("hora_fim"));
            int dur = ((Number) g.get("duracao_minutos")).intValue();
            while (!t.plusMinutes(dur).isAfter(fim)) {
                LocalDateTime slot = data.atTime(t);
                boolean livre = ocupados.stream().noneMatch(o -> {
                    LocalDateTime oi = ApsLediBuilder.dataHora(o.get("data_hora"));
                    LocalDateTime of = oi.plusMinutes(((Number) o.get("duracao_minutos")).intValue());
                    return slot.isBefore(of) && slot.plusMinutes(dur).isAfter(oi);
                });
                if (livre && slot.isAfter(LocalDateTime.now())) {
                    out.add(Map.of("dataHora", slot.toString(), "duracao", dur));
                }
                t = t.plusMinutes(dur);
            }
        }
        return out;
    }

    public List<Map<String, Object>> agendamentos(String unidade, String matricula, LocalDate de, LocalDate ate, Long idCidadao) {
        return db.list("""
                SELECT a.*, c.nome AS cidadao_nome, c.cpf, c.cns, c.data_nascimento, c.telefone
                FROM aps_agendamento a
                LEFT JOIN aps_cidadao c ON c.id = a.id_cidadao
                WHERE (CAST(:u AS varchar) IS NULL OR UPPER(a.unidade) = UPPER(CAST(:u AS varchar)))
                  AND (CAST(:m AS varchar) IS NULL OR a.matricula_profissional = CAST(:m AS varchar))
                  AND (CAST(:c AS bigint) IS NULL OR a.id_cidadao = CAST(:c AS bigint))
                  AND (CAST(:de AS date) IS NULL OR CAST(a.data_hora AS date) >= CAST(:de AS date))
                  AND (CAST(:ate AS date) IS NULL OR CAST(a.data_hora AS date) <= CAST(:ate AS date))
                ORDER BY a.data_hora
                """, p("u", ApsFichaService.vazioParaNulo(unidade), "m", ApsFichaService.vazioParaNulo(matricula), "c", idCidadao,
                "de", de, "ate", ate));
    }

    @Transactional
    public Map<String, Object> agendar(Map<String, Object> body, String unidade, String autor) {
        String tipo = str(body.get("tipo")) == null ? "CONSULTA" : str(body.get("tipo"));
        String matricula = str(body.get("matricula"));
        String dataHora = str(body.get("dataHora"));
        if (matricula == null || dataHora == null) {
            throw new IllegalArgumentException("Informe profissional e data/hora.");
        }
        exigirLotacao(matricula, unidade);
        LocalDateTime inicio = ApsLediBuilder.dataHora(dataHora);
        if (inicio.toLocalDate().isBefore(LocalDate.now(ApsLediSerializer.ZONA))) {
            throw new IllegalArgumentException("Nao e possivel agendar em data passada.");
        }
        int dur = intOrNull(body.get("duracao")) == null ? 20 : intOrNull(body.get("duracao"));
        Long idCidadao = num(body.get("idCidadao"));
        String nome;
        if ("RESERVA".equals(tipo)) {
            nome = str(body.get("motivo")) == null ? "Reserva de horario" : str(body.get("motivo"));
        } else {
            if (idCidadao == null) {
                throw new IllegalArgumentException("Selecione o cidadao.");
            }
            Map<String, Object> c = db.one("SELECT nome, saida_motivo, microarea FROM aps_cidadao WHERE id = :id", p("id", idCidadao));
            if (c == null) {
                throw new IllegalArgumentException("Cidadao nao encontrado.");
            }
            if (Integer.valueOf(135).equals(c.get("saida_motivo"))) {
                throw new IllegalArgumentException("Cidadao com registro de obito.");
            }
            nome = (String) c.get("nome");
        }
        Long fechado = db.count("""
                SELECT COUNT(*) FROM aps_agenda_fechamento WHERE matricula = :m AND UPPER(unidade) = UPPER(:u)
                  AND CAST(:d AS date) BETWEEN data_inicio AND data_fim
                """, p("m", matricula, "u", unidade, "d", inicio.toLocalDate()));
        if (fechado > 0) {
            throw new IllegalArgumentException("Agenda do profissional fechada nesta data.");
        }
        Long conflito = db.count("""
                SELECT COUNT(*) FROM aps_agendamento
                WHERE matricula_profissional = :m AND status NOT IN ('CANCELADO')
                  AND data_hora < CAST(:fim AS timestamp)
                  AND data_hora + (duracao_minutos || ' minutes')::interval > CAST(:ini AS timestamp)
                """, p("m", matricula, "ini", inicio.toString(), "fim", inicio.plusMinutes(dur).toString()));
        if (conflito > 0 && !Boolean.TRUE.equals(body.get("encaixe"))) {
            throw new IllegalArgumentException("Horario ja ocupado. Marque 'encaixe' para agendar mesmo assim.");
        }
        Map<String, Object> prof = db.one("SELECT nome, cbo FROM servidor WHERE matricula = :m LIMIT 1", p("m", matricula));
        Long id = db.insert("""
                INSERT INTO aps_agendamento (unidade, microarea, nome_paciente, profissional, matricula_profissional, data_hora,
                    duracao_minutos, status, motivo, observacao, id_cidadao, cbo, tipo, local, pre_natal, agendado_por)
                VALUES (:u, :micro, :nome, :prof, :m, CAST(:dh AS timestamp), :dur, :st, :motivo, :obs, :c, :cbo, :tipo, :local,
                    :pn, :autor)
                RETURNING id
                """, p("u", unidade, "micro", str(body.get("microarea")), "nome", nome,
                "prof", prof == null ? matricula : prof.get("nome"), "m", matricula, "dh", inicio.toString(), "dur", dur,
                "st", "RESERVA".equals(tipo) ? "CONFIRMADO" : "AGENDADO", "motivo", str(body.get("motivo")),
                "obs", str(body.get("observacao")), "c", idCidadao, "cbo", prof == null ? null : prof.get("cbo"),
                "tipo", tipo, "local", str(body.get("local")) == null ? "UBS" : str(body.get("local")),
                "pn", Boolean.TRUE.equals(body.get("preNatal")), "autor", autor));
        Long idGarantia = num(body.get("idGarantia"));
        if (idGarantia != null) {
            db.exec("UPDATE aps_garantia_acesso SET status = 'AGENDADO', resolvido_em = CURRENT_TIMESTAMP WHERE id = :id",
                    p("id", idGarantia));
        }
        return db.one("SELECT * FROM aps_agendamento WHERE id = :id", p("id", id));
    }

    @Transactional
    public Map<String, Object> alterarStatus(Long id, String status, String motivo, String autor) {
        if (!List.of("AGENDADO", "CONFIRMADO", "FALTOU", "CANCELADO").contains(status)) {
            throw new IllegalArgumentException("Status invalido.");
        }
        db.exec("""
                UPDATE aps_agendamento SET status = :s,
                    observacao = CASE WHEN CAST(:mot AS varchar) IS NULL THEN observacao
                                      ELSE COALESCE(observacao || ' | ', '') || CAST(:mot AS varchar) END,
                    atualizado_em = CURRENT_TIMESTAMP
                WHERE id = :id
                """, p("s", status, "mot", motivo == null ? null : status + " por " + autor + ": " + motivo, "id", id));
        return db.one("SELECT * FROM aps_agendamento WHERE id = :id", p("id", id));
    }

    // ------------------------------------------------------------------ Garantia do acesso

    public List<Map<String, Object>> garantia(String unidade, String status) {
        return db.list("""
                SELECT g.*, c.nome, c.cpf, c.cns, c.data_nascimento, c.telefone, e.nome AS equipe_nome,
                       CURRENT_DATE - CAST(g.criado_em AS date) AS dias_espera
                FROM aps_garantia_acesso g
                JOIN aps_cidadao c ON c.id = g.id_cidadao
                LEFT JOIN aps_equipe e ON e.id = g.id_equipe
                WHERE UPPER(g.unidade) = UPPER(:u) AND (CAST(:s AS varchar) IS NULL OR g.status = CAST(:s AS varchar))
                ORDER BY g.criado_em
                """, p("u", unidade, "s", ApsFichaService.vazioParaNulo(status)));
    }

    @Transactional
    public Map<String, Object> incluirGarantia(Map<String, Object> body, String unidade, String autor) {
        Long idCidadao = num(body.get("idCidadao"));
        String tipo = str(body.get("tipoServico"));
        if (idCidadao == null || tipo == null) {
            throw new IllegalArgumentException("Informe cidadao e tipo de servico.");
        }
        Map<String, Object> c = db.one("SELECT id_equipe FROM aps_cidadao WHERE id = :id", p("id", idCidadao));
        Long id = db.insert("""
                INSERT INTO aps_garantia_acesso (unidade, id_cidadao, id_equipe, tipo_servico, motivo, criado_por)
                VALUES (:u, :c, :eq, :tipo, :motivo, :a) RETURNING id
                """, p("u", unidade, "c", idCidadao, "eq", num(body.get("idEquipe")) != null ? num(body.get("idEquipe"))
                : (c == null ? null : c.get("id_equipe")), "tipo", tipo, "motivo", str(body.get("motivo")), "a", autor));
        return db.one("SELECT * FROM aps_garantia_acesso WHERE id = :id", p("id", id));
    }

    @Transactional
    public Map<String, Object> removerGarantia(Long id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Informe o motivo da saida da lista.");
        }
        db.exec("""
                UPDATE aps_garantia_acesso SET status = 'REMOVIDO', motivo_saida = :m, resolvido_em = CURRENT_TIMESTAMP WHERE id = :id
                """, p("m", motivo, "id", id));
        return db.one("SELECT * FROM aps_garantia_acesso WHERE id = :id", p("id", id));
    }

    public Map<String, Object> resumoDia(String unidade, LocalDate data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agendados", db.count("""
                SELECT COUNT(*) FROM aps_agendamento WHERE UPPER(unidade) = UPPER(:u) AND CAST(data_hora AS date) = CAST(:d AS date)
                  AND tipo = 'CONSULTA' AND status <> 'CANCELADO'
                """, p("u", unidade, "d", data)));
        m.put("faltas", db.count("""
                SELECT COUNT(*) FROM aps_agendamento WHERE UPPER(unidade) = UPPER(:u) AND CAST(data_hora AS date) = CAST(:d AS date)
                  AND status = 'FALTOU'
                """, p("u", unidade, "d", data)));
        return m;
    }
}

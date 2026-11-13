package br.com.iv.aps;

import static br.com.iv.aps.ApsDb.p;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Administracao da APS (manual PEC cap. 3): equipes (INE), microareas + ACS,
 * lotacoes (CNS/CBO/INE) e parametros da instalacao (remetente LEDI).
 */
@ApplicationScoped
public class ApsConfiguracaoService {

    @Inject
    ApsDb db;

    @Inject
    ApsContextoService contexto;

    public List<Map<String, Object>> equipes(String unidade, boolean somenteAtivas) {
        return db.list("""
                SELECT e.*,
                       (SELECT COUNT(*) FROM aps_microarea m WHERE m.id_equipe = e.id AND m.ativo) AS microareas,
                       (SELECT COUNT(*) FROM aps_lotacao l WHERE l.id_equipe = e.id AND l.ativo) AS profissionais,
                       (SELECT COUNT(*) FROM aps_cidadao c WHERE c.id_equipe = e.id AND c.ativo) AS cidadaos
                FROM aps_equipe e
                WHERE (CAST(:unidade AS varchar) IS NULL OR UPPER(e.unidade) = UPPER(CAST(:unidade AS varchar)))
                  AND (NOT CAST(:ativas AS boolean) OR e.ativo)
                ORDER BY e.unidade, e.nome
                """, p("unidade", ApsFichaService.vazioParaNulo(unidade), "ativas", somenteAtivas));
    }

    @Transactional
    public Map<String, Object> salvarEquipe(Map<String, Object> body, String unidadeSessao) {
        String ine = ApsValidacao.digitos(str(body.get("ine")));
        String unidade = str(unidadeSessao);
        String cnes = unidade == null ? null : contexto.unidade(unidade).cnes();
        if (cnes == null) {
            cnes = ApsValidacao.digitos(str(body.get("cnes")));
        }
        String nome = str(body.get("nome"));
        if (body.get("id") != null) {
            exigirEquipeDaUnidade(num(body.get("id")), unidade);
        }
        if (ine == null || ine.length() != 10) {
            throw new IllegalArgumentException("INE deve ter 10 digitos.");
        }
        if (unidade == null || nome == null) {
            throw new IllegalArgumentException("Informe unidade e nome da equipe.");
        }
        if (cnes == null) {
            cnes = contexto.unidade(unidade).cnes();
        }
        if (cnes == null || cnes.length() != 7) {
            throw new IllegalArgumentException("CNES deve ter 7 digitos.");
        }
        Object id = body.get("id");
        Map<String, Object> params = p("unidade", unidade, "cnes", cnes, "ine", ine, "nome", nome,
                "tipo", str(body.get("tipo")) == null ? "ESF" : str(body.get("tipo")),
                "ativo", body.get("ativo") == null || Boolean.TRUE.equals(body.get("ativo")));
        if (id == null) {
            Long novo = db.insert("""
                    INSERT INTO aps_equipe (unidade, cnes, ine, nome, tipo, ativo)
                    VALUES (:unidade, :cnes, :ine, :nome, :tipo, :ativo) RETURNING id
                    """, params);
            return db.one("SELECT * FROM aps_equipe WHERE id = :id", p("id", novo));
        }
        params.put("id", ((Number) id).longValue());
        db.exec("""
                UPDATE aps_equipe SET unidade = :unidade, cnes = :cnes, ine = :ine, nome = :nome, tipo = :tipo, ativo = :ativo
                WHERE id = :id
                """, params);
        db.exec("UPDATE aps_lotacao SET ine = :ine, cnes = :cnes WHERE id_equipe = :id", p("ine", ine, "cnes", cnes, "id", params.get("id")));
        return db.one("SELECT * FROM aps_equipe WHERE id = :id", p("id", params.get("id")));
    }

    public List<Map<String, Object>> microareas(Long idEquipe, String unidade) {
        return db.list("""
                SELECT m.*, e.nome AS equipe_nome, e.ine, e.unidade,
                       (SELECT COUNT(*) FROM aps_domicilio d WHERE d.id_equipe = m.id_equipe AND d.microarea = m.codigo AND d.ativo) AS domicilios,
                       (SELECT COUNT(*) FROM aps_cidadao c WHERE c.id_equipe = m.id_equipe AND c.microarea = m.codigo AND c.ativo) AS cidadaos
                FROM aps_microarea m
                JOIN aps_equipe e ON e.id = m.id_equipe
                WHERE (CAST(:idEquipe AS bigint) IS NULL OR m.id_equipe = CAST(:idEquipe AS bigint))
                  AND (CAST(:unidade AS varchar) IS NULL OR UPPER(e.unidade) = UPPER(CAST(:unidade AS varchar)))
                ORDER BY e.nome, m.codigo
                """, p("idEquipe", idEquipe, "unidade", ApsFichaService.vazioParaNulo(unidade)));
    }

    @Transactional
    public Map<String, Object> salvarMicroarea(Map<String, Object> body, String unidade) {
        Long idEquipe = num(body.get("id_equipe"));
        String codigo = ApsValidacao.digitos(str(body.get("codigo")));
        if (idEquipe == null || codigo == null) {
            throw new IllegalArgumentException("Informe equipe e codigo da microarea.");
        }
        exigirEquipeDaUnidade(idEquipe, unidade);
        if (num(body.get("id")) != null) {
            Map<String, Object> atual = db.one("SELECT id_equipe FROM aps_microarea WHERE id = :id", p("id", num(body.get("id"))));
            exigirEquipeDaUnidade(atual == null ? null : num(atual.get("id_equipe")), unidade);
        }
        if (str(body.get("matricula_acs")) != null) {
            exigirServidorDaUnidade(str(body.get("matricula_acs")), unidade);
        }
        codigo = codigo.length() == 1 ? "0" + codigo : codigo;
        if (codigo.length() != 2) {
            throw new IllegalArgumentException("Microarea deve ter 2 digitos (01 a 99).");
        }
        String matriculaAcs = str(body.get("matricula_acs"));
        String nomeAcs = str(body.get("nome_acs"));
        if (matriculaAcs != null && nomeAcs == null) {
            Map<String, Object> s = db.one("SELECT nome FROM servidor WHERE matricula = :m LIMIT 1", p("m", matriculaAcs));
            nomeAcs = s == null ? null : (String) s.get("nome");
        }
        Map<String, Object> params = p("idEquipe", idEquipe, "codigo", codigo, "descricao", str(body.get("descricao")),
                "matricula", matriculaAcs, "nomeAcs", nomeAcs,
                "ativo", body.get("ativo") == null || Boolean.TRUE.equals(body.get("ativo")));
        Long id = num(body.get("id"));
        if (id == null) {
            id = db.insert("""
                    INSERT INTO aps_microarea (id_equipe, codigo, descricao, matricula_acs, nome_acs, ativo)
                    VALUES (:idEquipe, :codigo, :descricao, :matricula, :nomeAcs, :ativo)
                    ON CONFLICT (id_equipe, codigo) DO UPDATE SET descricao = EXCLUDED.descricao,
                        matricula_acs = EXCLUDED.matricula_acs, nome_acs = EXCLUDED.nome_acs, ativo = EXCLUDED.ativo
                    RETURNING id
                    """, params);
        } else {
            params.put("id", id);
            db.exec("""
                    UPDATE aps_microarea SET id_equipe = :idEquipe, codigo = :codigo, descricao = :descricao,
                        matricula_acs = :matricula, nome_acs = :nomeAcs, ativo = :ativo
                    WHERE id = :id
                    """, params);
        }
        return db.one("SELECT * FROM aps_microarea WHERE id = :id", p("id", id));
    }

    public List<Map<String, Object>> lotacoes(String unidade, Long idEquipe) {
        return db.list("""
                SELECT l.*, e.nome AS equipe_nome
                FROM aps_lotacao l
                LEFT JOIN aps_equipe e ON e.id = l.id_equipe
                WHERE (CAST(:unidade AS varchar) IS NULL OR UPPER(l.unidade) = UPPER(CAST(:unidade AS varchar)))
                  AND (CAST(:idEquipe AS bigint) IS NULL OR l.id_equipe = CAST(:idEquipe AS bigint))
                ORDER BY l.ativo DESC, l.nome
                """, p("unidade", ApsFichaService.vazioParaNulo(unidade), "idEquipe", idEquipe));
    }

    /** ACS e profissionais de ESF/eSB: lotacao sem equipe nao faz sentido. */
    static final java.util.Set<String> CBO_EXIGE_EQUIPE = java.util.Set.of(
            "515105", // agente comunitario de saude
            "225142", // medico da estrategia de saude da familia
            "223565", // enfermeiro da estrategia de saude da familia
            "322245", // tecnico de enfermagem da estrategia de saude da familia
            "223293", // cirurgiao-dentista da estrategia de saude da familia
            "322425"); // auxiliar em saude bucal da estrategia de saude da familia

    @Transactional
    public Map<String, Object> salvarLotacao(Map<String, Object> body, String unidadeSessao) {
        String matricula = str(body.get("matricula"));
        String unidade = str(unidadeSessao);
        if (matricula != null && unidade != null) {
            exigirServidorDaUnidade(matricula, unidade);
            if (num(body.get("id_equipe")) != null) {
                exigirEquipeDaUnidade(num(body.get("id_equipe")), unidade);
            }
            if (num(body.get("id")) != null) {
                Map<String, Object> atual = db.one("SELECT unidade FROM aps_lotacao WHERE id = :id", p("id", num(body.get("id"))));
                if (atual == null || !unidade.equalsIgnoreCase(String.valueOf(atual.get("unidade")).trim())) {
                    throw new IllegalArgumentException("Lotacao de outra unidade - troque para a unidade dela para editar.");
                }
            }
            if (Boolean.TRUE.equals(body.get("estagiario")) && str(body.get("matricula_supervisor")) != null) {
                exigirServidorDaUnidade(str(body.get("matricula_supervisor")), unidade);
            }
        }
        String cbo = ApsValidacao.digitos(str(body.get("cbo")));
        if (matricula == null || unidade == null || cbo == null || cbo.length() != 6) {
            throw new IllegalArgumentException("Informe matricula, unidade e CBO (6 digitos).");
        }
        // Como no PEC/LEDI o INE e opcional, mas estes CBOs so existem dentro de equipe.
        if (num(body.get("id_equipe")) == null && CBO_EXIGE_EQUIPE.contains(cbo)) {
            throw new IllegalArgumentException("O CBO " + cbo + " exige equipe (INE) - selecione a equipe da lotacao.");
        }
        Map<String, Object> servidor = db.one("SELECT nome, cpf, cns, cbo FROM servidor WHERE matricula = :m LIMIT 1", p("m", matricula));
        if (servidor == null) {
            throw new IllegalArgumentException("Servidor com matricula " + matricula + " nao encontrado.");
        }
        // Lotacao nova so para quem tem CBO no cadastro; lotacoes antigas seguem editaveis.
        if (num(body.get("id")) == null && ApsValidacao.digitos(str(servidor.get("cbo"))) == null) {
            throw new IllegalArgumentException("Servidor sem CBO no cadastro - corrija o cadastro antes de criar a lotacao.");
        }
        // Como no PEC: a mesma lotacao (profissional + CBO + equipe) nao pode existir duas vezes ativa na unidade.
        if (body.get("ativo") == null || Boolean.TRUE.equals(body.get("ativo"))) {
            Map<String, Object> dup = db.one("""
                    SELECT id FROM aps_lotacao
                    WHERE matricula = :m AND cbo = :cbo AND ativo = TRUE AND UPPER(unidade) = UPPER(:u)
                      AND id_equipe IS NOT DISTINCT FROM CAST(:idEquipe AS bigint)
                      AND (CAST(:id AS bigint) IS NULL OR id <> CAST(:id AS bigint))
                    LIMIT 1
                    """, p("m", matricula, "cbo", cbo, "u", unidade, "idEquipe", num(body.get("id_equipe")), "id", num(body.get("id"))));
            if (dup != null) {
                throw new IllegalArgumentException("Ja existe lotacao ativa deste profissional com o CBO " + cbo
                        + (num(body.get("id_equipe")) == null ? " sem equipe." : " nesta equipe."));
            }
        }
        // Estagiario: registros saem em nome do supervisor, que precisa ter lotacao ativa (nao de estagio) na unidade.
        String supervisor = str(body.get("matricula_supervisor"));
        boolean ativa = body.get("ativo") == null || Boolean.TRUE.equals(body.get("ativo"));
        if (Boolean.TRUE.equals(body.get("estagiario")) && !ativa) {
            // Inativar sempre pode, mesmo que o supervisor ja tenha saido.
        } else if (Boolean.TRUE.equals(body.get("estagiario"))) {
            if (supervisor == null) {
                throw new IllegalArgumentException("Informe a matricula do supervisor do estagiario.");
            }
            if (supervisor.equalsIgnoreCase(matricula)) {
                throw new IllegalArgumentException("O estagiario nao pode ser o proprio supervisor.");
            }
            Map<String, Object> lotSup = db.one("""
                    SELECT id FROM aps_lotacao
                    WHERE matricula = :m AND ativo = TRUE AND estagiario = FALSE AND UPPER(unidade) = UPPER(:u)
                    LIMIT 1
                    """, p("m", supervisor, "u", unidade));
            if (lotSup == null) {
                throw new IllegalArgumentException("Supervisor " + supervisor + " sem lotacao APS ativa nesta unidade - lote o supervisor primeiro.");
            }
        } else {
            supervisor = null;
        }
        String cns = ApsValidacao.digitos(str(body.get("cns")));
        if (cns == null) {
            cns = ApsValidacao.digitos((String) servidor.get("cns"));
        }
        if (cns == null || !ApsValidacao.cnsValido(cns)) {
            throw new IllegalArgumentException("CNS do profissional ausente ou invalido - o LEDI exige CNS valido do profissional.");
        }
        Long idEquipe = num(body.get("id_equipe"));
        String ine = null;
        String cnes = contexto.unidade(unidade).cnes();
        if (idEquipe != null) {
            Map<String, Object> eq = db.one("SELECT ine, cnes FROM aps_equipe WHERE id = :id", p("id", idEquipe));
            if (eq == null) {
                throw new IllegalArgumentException("Equipe nao encontrada.");
            }
            ine = (String) eq.get("ine");
            cnes = (String) eq.get("cnes");
        }
        Map<String, Object> params = p("matricula", matricula, "nome", servidor.get("nome"), "cns", cns,
                "cpf", ApsValidacao.digitos((String) servidor.get("cpf")), "cbo", cbo, "unidade", unidade, "cnes", cnes,
                "idEquipe", idEquipe, "ine", ine, "estagiario", Boolean.TRUE.equals(body.get("estagiario")),
                "supervisor", supervisor,
                "ativo", body.get("ativo") == null || Boolean.TRUE.equals(body.get("ativo")));
        Long id = num(body.get("id"));
        if (id == null) {
            id = db.insert("""
                    INSERT INTO aps_lotacao (matricula, nome, cns, cpf, cbo, unidade, cnes, id_equipe, ine, estagiario,
                        matricula_supervisor, ativo)
                    VALUES (:matricula, :nome, :cns, :cpf, :cbo, :unidade, :cnes, :idEquipe, :ine, :estagiario, :supervisor, :ativo)
                    RETURNING id
                    """, params);
        } else {
            params.put("id", id);
            db.exec("""
                    UPDATE aps_lotacao SET matricula = :matricula, nome = :nome, cns = :cns, cpf = :cpf, cbo = :cbo,
                        unidade = :unidade, cnes = :cnes, id_equipe = :idEquipe, ine = :ine, estagiario = :estagiario,
                        matricula_supervisor = :supervisor, ativo = :ativo
                    WHERE id = :id
                    """, params);
        }
        return db.one("SELECT * FROM aps_lotacao WHERE id = :id", p("id", id));
    }

    public Map<String, Object> parametros() {
        return contexto.parametros();
    }

    @Transactional
    public Map<String, Object> salvarParametros(Map<String, Object> body, String matricula) {
        String cpfCnpj = ApsValidacao.digitos(str(body.get("cpf_cnpj")));
        if (cpfCnpj != null && cpfCnpj.length() != 11 && cpfCnpj.length() != 14) {
            throw new IllegalArgumentException("CPF (11) ou CNPJ (14) do responsavel pela instalacao invalido.");
        }
        String ibge = ApsValidacao.digitos(str(body.get("cod_ibge")));
        if (ibge != null && ibge.length() != 7) {
            throw new IllegalArgumentException("Codigo IBGE deve ter 7 digitos.");
        }
        db.exec("""
                UPDATE aps_parametro SET contra_chave = COALESCE(:contra, contra_chave), cpf_cnpj = :cpfCnpj,
                    nome_razao_social = :nome, fone = :fone, email = :email, cod_ibge = :ibge,
                    envio_automatico = :auto, atualizado_em = CURRENT_TIMESTAMP, atualizado_por = :matricula
                WHERE chave = 'GLOBAL'
                """, p("contra", str(body.get("contra_chave")), "cpfCnpj", cpfCnpj, "nome", str(body.get("nome_razao_social")),
                "fone", str(body.get("fone")), "email", str(body.get("email")), "ibge", ibge,
                "auto", Boolean.TRUE.equals(body.get("envio_automatico")), "matricula", matricula));
        return parametros();
    }

    /** Equipe precisa pertencer a unidade logada. */
    void exigirEquipeDaUnidade(Long idEquipe, String unidade) {
        if (idEquipe == null || unidade == null) {
            throw new IllegalArgumentException("Equipe nao encontrada.");
        }
        Map<String, Object> eq = db.one("SELECT unidade FROM aps_equipe WHERE id = :id", p("id", idEquipe));
        if (eq == null || !unidade.equalsIgnoreCase(String.valueOf(eq.get("unidade")).trim())) {
            throw new IllegalArgumentException("Equipe nao pertence a unidade logada (" + unidade + ").");
        }
    }

    /** Profissional precisa estar cadastrado na unidade logada. */
    void exigirServidorDaUnidade(String matricula, String unidade) {
        Map<String, Object> s = db.one("SELECT unidade FROM servidor WHERE matricula = :m LIMIT 1", p("m", matricula));
        if (s == null) {
            throw new IllegalArgumentException("Servidor com matricula " + matricula + " nao encontrado.");
        }
        if (unidade == null || !unidade.equalsIgnoreCase(String.valueOf(s.get("unidade")).trim())) {
            throw new IllegalArgumentException("Profissional " + matricula + " nao e da unidade logada (" + unidade + ").");
        }
    }

    public List<Map<String, Object>> buscarServidores(String termo, String unidade) {
        String t = termo == null ? "" : termo.trim();
        if (t.length() < 3 || unidade == null || unidade.isBlank()) {
            return List.of();
        }
        return db.list("""
                SELECT s.matricula, s.nome, s.cbo,
                       COALESCE(NULLIF(regexp_replace(COALESCE(s.cns, ''), '\\D', '', 'g'), ''),
                                (SELECT NULLIF(regexp_replace(COALESCE(s2.cns, ''), '\\D', '', 'g'), '')
                                 FROM servidor2 s2 WHERE s2.matricula = s.matricula
                                 ORDER BY (s2.cns IS NULL) LIMIT 1)) AS cns
                FROM servidor s
                WHERE (UPPER(s.nome) LIKE UPPER(:t) OR s.matricula = :exato)
                  AND UPPER(TRIM(s.unidade)) = UPPER(TRIM(:u))
                ORDER BY s.nome LIMIT 30
                """, p("t", "%" + t + "%", "exato", t, "u", unidade));
    }

    static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    static Long num(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }
}

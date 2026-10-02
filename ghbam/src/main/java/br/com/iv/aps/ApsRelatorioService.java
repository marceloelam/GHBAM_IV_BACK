package br.com.iv.aps;

import static br.com.iv.aps.ApsDb.p;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Relatorios (manual PEC cap. 8), acompanhamentos (cap. 10) e indicadores de
 * qualidade do cofinanciamento federal da APS (estimativa local - o valor
 * oficial e calculado pelo Ministerio no SIAPS a partir dos registros LEDI).
 */
@ApplicationScoped
public class ApsRelatorioService {

    /** Condicoes pre-estruturadas da lista tematica (CIAP2 / prefixos CID10 / campo autorreferido). */
    private static final Map<String, String[]> CONDICOES = new LinkedHashMap<>();

    static {
        CONDICOES.put("HIPERTENSAO", new String[] { "K86,K87", "I10,I11,I12,I13,I15", "statusTemHipertensaoArterial" });
        CONDICOES.put("DIABETES", new String[] { "T89,T90", "E10,E11,E12,E13,E14", "statusTemDiabetes" });
        CONDICOES.put("OBESIDADE", new String[] { "T82", "E66", "" });
        CONDICOES.put("TABAGISMO", new String[] { "P17", "F17", "statusEhFumante" });
        CONDICOES.put("ALCOOL", new String[] { "P15,P16", "F10", "statusEhDependenteAlcool" });
        CONDICOES.put("SAUDE_MENTAL", new String[] { "P76,P72,P73,P74,P98", "F20,F31,F32,F33,F41", "statusDiagnosticoMental" });
        CONDICOES.put("TUBERCULOSE", new String[] { "A70", "A15,A16,A17,A18,A19", "statusTemTuberculose" });
        CONDICOES.put("HANSENIASE", new String[] { "A78", "A30", "statusTemHanseniase" });
        CONDICOES.put("ASMA", new String[] { "R96", "J45", "" });
        CONDICOES.put("DPOC", new String[] { "R95", "J44", "statusTemDoencaRespiratoria" });
        CONDICOES.put("DOENCA_CARDIACA", new String[] { "K74,K75,K76,K77", "I20,I21,I25,I50", "statusTeveDoencaCardiaca" });
        CONDICOES.put("DOENCA_RENAL", new String[] { "U99", "N18", "statusTemTeveDoencasRins" });
        CONDICOES.put("CANCER", new String[] { "", "C", "statusTemTeveCancer" });
        CONDICOES.put("AVC", new String[] { "K90", "I63,I64", "statusTeveAvcDerrame" });
    }

    @Inject
    ApsDb db;

    // ------------------------------------------------------------------ Acompanhamentos (cap. 10)

    /**
     * Lista tematica. condicao: uma das CONDICOES, GESTANTE, CRIANCA (0-2a), IDOSO, ou CUSTOM (codigo CIAP2/CID10).
     */
    public List<Map<String, Object>> listaTematica(String unidade, Long idEquipe, String microarea, String condicao,
            String codigo, Integer idadeMin, Integer idadeMax, Integer sexo, Integer semAtendimentoDias) {
        String cond = condicao == null ? "" : condicao.toUpperCase();
        String[] def = CONDICOES.getOrDefault(cond, new String[] { "", "", "" });
        String ciaps = "CUSTOM".equals(cond) && codigo != null && codigo.matches("[A-Za-z]\\d{2}") && codigo.length() == 3
                && Character.isLetter(codigo.charAt(0)) ? codigo.toUpperCase() : def[0];
        String cids = "CUSTOM".equals(cond) && codigo != null ? codigo.toUpperCase().replace(".", "") : def[1];
        String campo = def[2];
        return db.list("""
                WITH base AS (
                  SELECT c.*, e.nome AS equipe_nome,
                         DATE_PART('year', AGE(c.data_nascimento)) AS idade,
                         (SELECT MAX(a.inicio) FROM aps_atendimento a WHERE a.id_cidadao = c.id AND a.status = 'FINALIZADO') AS ultimo_atendimento,
                         (SELECT MAX(v.data_atendimento) FROM aps_ficha v WHERE v.id_cidadao = c.id AND v.tipo = 8 AND v.status <> 'CANCELADA') AS ultima_visita,
                         (SELECT m.pressao_sistolica || 'x' || m.pressao_diastolica || ' em ' || to_char(m.data_hora, 'DD/MM/YYYY')
                            FROM aps_medicao m WHERE m.id_cidadao = c.id AND m.pressao_sistolica IS NOT NULL ORDER BY m.data_hora DESC LIMIT 1) AS ultima_pa,
                         (SELECT m.glicemia || ' em ' || to_char(m.data_hora, 'DD/MM/YYYY')
                            FROM aps_medicao m WHERE m.id_cidadao = c.id AND m.glicemia IS NOT NULL ORDER BY m.data_hora DESC LIMIT 1) AS ultima_glicemia,
                         (SELECT m.imc FROM aps_medicao m WHERE m.id_cidadao = c.id AND m.imc IS NOT NULL ORDER BY m.data_hora DESC LIMIT 1) AS ultimo_imc,
                         d.logradouro, d.numero, d.bairro
                  FROM aps_cidadao c
                  LEFT JOIN aps_equipe e ON e.id = c.id_equipe
                  LEFT JOIN aps_familia f ON f.id = c.id_familia
                  LEFT JOIN aps_domicilio d ON d.id = f.id_domicilio
                  WHERE c.ativo AND UPPER(c.unidade) = UPPER(:u)
                    AND (CAST(:eq AS bigint) IS NULL OR c.id_equipe = CAST(:eq AS bigint))
                    AND (CAST(:micro AS varchar) IS NULL OR c.microarea = CAST(:micro AS varchar))
                    AND (CAST(:sexo AS integer) IS NULL OR c.sexo = CAST(:sexo AS integer))
                )
                SELECT * FROM base b
                WHERE (CAST(:idMin AS integer) IS NULL OR b.idade >= CAST(:idMin AS integer))
                  AND (CAST(:idMax AS integer) IS NULL OR b.idade <= CAST(:idMax AS integer))
                  AND (CAST(:semDias AS integer) IS NULL OR b.ultimo_atendimento IS NULL
                       OR b.ultimo_atendimento < CURRENT_DATE - CAST(:semDias AS integer))
                  AND (
                    CAST(:cond AS varchar) = ''
                    OR (CAST(:cond AS varchar) = 'GESTANTE' AND EXISTS (SELECT 1 FROM aps_gestacao g WHERE g.id_cidadao = b.id AND g.status = 'ATIVA'))
                    OR (CAST(:cond AS varchar) = 'PUERPERA' AND EXISTS (SELECT 1 FROM aps_gestacao g WHERE g.id_cidadao = b.id AND g.status = 'PUERPERIO'))
                    OR (CAST(:cond AS varchar) = 'CRIANCA' AND b.data_nascimento > CURRENT_DATE - INTERVAL '2 years')
                    OR (CAST(:cond AS varchar) = 'IDOSO' AND b.idade >= 60)
                    OR (CAST(:cond AS varchar) = 'ACAMADO' AND (b.dados -> 'condicoesDeSaude' ->> 'statusEstaAcamado') = 'true')
                    OR (CAST(:cond AS varchar) = 'DOMICILIADO' AND (b.dados -> 'condicoesDeSaude' ->> 'statusEstaDomiciliado') = 'true')
                    OR (CAST(:cond AS varchar) = 'SITUACAO_RUA' AND (b.dados -> 'emSituacaoDeRua' ->> 'statusSituacaoRua') = 'true')
                    OR (CAST(:cond AS varchar) = 'DEFICIENCIA' AND (b.dados -> 'informacoesSocioDemograficas' ->> 'statusTemAlgumaDeficiencia') = 'true')
                    OR EXISTS (
                        SELECT 1 FROM aps_problema pr WHERE pr.id_cidadao = b.id AND pr.tipo = 'PROBLEMA' AND pr.situacao <> 2
                          AND ((CAST(:ciaps AS varchar) <> '' AND pr.ciap = ANY (string_to_array(CAST(:ciaps AS varchar), ',')))
                            OR (CAST(:cids AS varchar) <> '' AND EXISTS (
                                  SELECT 1 FROM unnest(string_to_array(CAST(:cids AS varchar), ',')) px WHERE pr.cid10 LIKE px || '%'))))
                    OR (CAST(:campo AS varchar) <> '' AND (b.dados -> 'condicoesDeSaude' ->> CAST(:campo AS varchar)) = 'true')
                    OR (CAST(:cond AS varchar) = 'OBESIDADE' AND b.ultimo_imc >= 30)
                  )
                ORDER BY b.microarea, b.nome
                LIMIT 5000
                """, p("u", unidade, "eq", idEquipe, "micro", ApsFichaService.vazioParaNulo(microarea), "sexo", sexo,
                "idMin", idadeMin, "idMax", idadeMax, "semDias", semAtendimentoDias, "cond", cond, "ciaps", ciaps == null ? "" : ciaps,
                "cids", cids == null ? "" : cids, "campo", campo));
    }

    public List<Map<String, Object>> cidadaosVinculados(String unidade, Long idEquipe, String microarea) {
        return db.list("""
                SELECT c.id, c.nome, c.cpf, c.cns, c.data_nascimento, c.sexo, c.microarea, c.fora_area, e.nome AS equipe_nome,
                       c.responsavel_familiar, c.id_familia, c.atualizado_em, c.recusa,
                       (c.atualizado_em < CURRENT_DATE - INTERVAL '1 year') AS cadastro_desatualizado
                FROM aps_cidadao c LEFT JOIN aps_equipe e ON e.id = c.id_equipe
                WHERE c.ativo AND UPPER(c.unidade) = UPPER(:u)
                  AND (CAST(:eq AS bigint) IS NULL OR c.id_equipe = CAST(:eq AS bigint))
                  AND (CAST(:micro AS varchar) IS NULL OR c.microarea = CAST(:micro AS varchar))
                ORDER BY c.microarea, c.nome LIMIT 20000
                """, p("u", unidade, "eq", idEquipe, "micro", ApsFichaService.vazioParaNulo(microarea)));
    }

    // ------------------------------------------------------------------ Relatorios consolidados (cap. 8.3)

    public Map<String, Object> consolidadoCadastroIndividual(String unidade, Long idEquipe, String microarea) {
        Map<String, Object> pr = p("u", unidade, "eq", idEquipe, "micro", ApsFichaService.vazioParaNulo(microarea));
        String filtro = " " + """
                c.ativo AND UPPER(c.unidade) = UPPER(:u)
                AND (CAST(:eq AS bigint) IS NULL OR c.id_equipe = CAST(:eq AS bigint))
                AND (CAST(:micro AS varchar) IS NULL OR c.microarea = CAST(:micro AS varchar))
                """;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", db.count("SELECT COUNT(*) FROM aps_cidadao c WHERE " + filtro, pr));
        out.put("recusas", db.count("SELECT COUNT(*) FROM aps_cidadao c WHERE c.recusa AND " + filtro, pr));
        out.put("porSexo", db.list("SELECT c.sexo AS codigo, COUNT(*) AS total FROM aps_cidadao c WHERE " + filtro + " GROUP BY c.sexo ORDER BY 1", pr));
        out.put("porRacaCor", db.list("SELECT c.raca_cor AS codigo, COUNT(*) AS total FROM aps_cidadao c WHERE " + filtro + " GROUP BY c.raca_cor ORDER BY 1", pr));
        out.put("porFaixaEtaria", db.list("""
                SELECT CASE WHEN idade < 1 THEN '0 a 11 meses' WHEN idade < 5 THEN '1 a 4 anos' WHEN idade < 10 THEN '5 a 9 anos'
                            WHEN idade < 15 THEN '10 a 14 anos' WHEN idade < 20 THEN '15 a 19 anos' WHEN idade < 30 THEN '20 a 29 anos'
                            WHEN idade < 40 THEN '30 a 39 anos' WHEN idade < 50 THEN '40 a 49 anos' WHEN idade < 60 THEN '50 a 59 anos'
                            WHEN idade < 70 THEN '60 a 69 anos' WHEN idade < 80 THEN '70 a 79 anos' WHEN idade IS NULL THEN 'Sem data'
                            ELSE '80 anos ou mais' END AS faixa,
                       MIN(COALESCE(idade, 999)) AS ordem, SUM(CASE WHEN sexo = 0 THEN 1 ELSE 0 END) AS masculino,
                       SUM(CASE WHEN sexo = 1 THEN 1 ELSE 0 END) AS feminino, COUNT(*) AS total
                FROM (SELECT c.*, DATE_PART('year', AGE(c.data_nascimento)) AS idade FROM aps_cidadao c WHERE """ + filtro + """
                ) x GROUP BY 1 ORDER BY 2
                """, pr));
        for (String bloco : List.of("condicoesDeSaude", "informacoesSocioDemograficas", "emSituacaoDeRua", "informacoesSocioEconomicas")) {
            out.put(bloco, db.list("""
                    SELECT kv.key AS campo, COUNT(*) AS total
                    FROM aps_cidadao c, jsonb_each(COALESCE(c.dados -> CAST(:bloco AS text), '{}'::jsonb)) kv
                    WHERE jsonb_typeof(kv.value) = 'boolean' AND kv.value = 'true'::jsonb AND """ + filtro + """
                    GROUP BY kv.key ORDER BY 2 DESC
                    """, withParam(pr, "bloco", bloco)));
        }
        out.put("deficiencias", listaCodigos("informacoesSocioDemograficas", "deficienciasCidadao", filtro, pr));
        out.put("doencaCardiaca", listaCodigos("condicoesDeSaude", "doencaCardiaca", filtro, pr));
        out.put("doencaRespiratoria", listaCodigos("condicoesDeSaude", "doencaRespiratoria", filtro, pr));
        out.put("doencaRins", listaCodigos("condicoesDeSaude", "doencaRins", filtro, pr));
        out.put("grauInstrucao", escalar("informacoesSocioDemograficas", "grauInstrucaoCidadao", filtro, pr));
        out.put("mercadoTrabalho", escalar("informacoesSocioDemograficas", "situacaoMercadoTrabalhoCidadao", filtro, pr));
        out.put("situacaoPeso", escalar("condicoesDeSaude", "situacaoPeso", filtro, pr));
        return out;
    }

    private List<Map<String, Object>> listaCodigos(String bloco, String campo, String filtro, Map<String, Object> pr) {
        return db.list("""
                SELECT v.value AS codigo, COUNT(*) AS total
                FROM aps_cidadao c, jsonb_array_elements_text(COALESCE(c.dados -> CAST(:bloco AS text) -> CAST(:campo AS text), '[]'::jsonb)) v
                WHERE """ + filtro + " GROUP BY v.value ORDER BY 2 DESC", withParam(withParam(pr, "bloco", bloco), "campo", campo));
    }

    private List<Map<String, Object>> escalar(String bloco, String campo, String filtro, Map<String, Object> pr) {
        return db.list("""
                SELECT c.dados -> CAST(:bloco AS text) ->> CAST(:campo AS text) AS codigo, COUNT(*) AS total
                FROM aps_cidadao c WHERE """ + filtro + " GROUP BY 1 ORDER BY 2 DESC",
                withParam(withParam(pr, "bloco", bloco), "campo", campo));
    }

    public Map<String, Object> consolidadoDomiciliar(String unidade, Long idEquipe, String microarea) {
        Map<String, Object> pr = p("u", unidade, "eq", idEquipe, "micro", ApsFichaService.vazioParaNulo(microarea));
        String filtro = " " + """
                d.ativo AND UPPER(d.unidade) = UPPER(:u)
                AND (CAST(:eq AS bigint) IS NULL OR d.id_equipe = CAST(:eq AS bigint))
                AND (CAST(:micro AS varchar) IS NULL OR d.microarea = CAST(:micro AS varchar))
                """;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", db.count("SELECT COUNT(*) FROM aps_domicilio d WHERE " + filtro, pr));
        out.put("recusas", db.count("SELECT COUNT(*) FROM aps_domicilio d WHERE d.recusa AND " + filtro, pr));
        out.put("familias", db.count("""
                SELECT COUNT(*) FROM aps_familia f JOIN aps_domicilio d ON d.id = f.id_domicilio WHERE f.ativo AND NOT f.mudou AND """ + filtro, pr));
        out.put("porTipoImovel", db.list("SELECT d.tipo_imovel AS codigo, COUNT(*) AS total FROM aps_domicilio d WHERE " + filtro
                + " GROUP BY 1 ORDER BY 2 DESC", pr));
        for (String campo : List.of("abastecimentoAgua", "destinoLixo", "formaEscoamentoBanheiro", "localizacao",
                "materialPredominanteParedesExtDomicilio", "situacaoMoradiaPosseTerra", "tipoAcessoDomicilio", "tipoDomicilio",
                "aguaConsumoDomicilio", "tipoOrigemEnergiaEletrica", "areaProducaoRural", "stDisponibilidadeEnergiaEletrica")) {
            out.put(campo, db.list("""
                    SELECT d.dados -> 'condicaoMoradia' ->> CAST(:campo AS text) AS codigo, COUNT(*) AS total
                    FROM aps_domicilio d WHERE """ + filtro + " GROUP BY 1 ORDER BY 2 DESC", withParam(pr, "campo", campo)));
        }
        out.put("animais", db.list("""
                SELECT v.value AS codigo, COUNT(*) AS total
                FROM aps_domicilio d, jsonb_array_elements_text(COALESCE(d.dados -> 'animaisNoDomicilio', '[]'::jsonb)) v
                WHERE """ + filtro + " GROUP BY 1 ORDER BY 2 DESC", pr));
        out.put("rendaFamiliar", db.list("""
                SELECT f.renda_familiar AS codigo, COUNT(*) AS total
                FROM aps_familia f JOIN aps_domicilio d ON d.id = f.id_domicilio WHERE f.ativo AND NOT f.mudou AND """ + filtro
                + " GROUP BY 1 ORDER BY 1", pr));
        return out;
    }

    public List<Map<String, Object>> situacaoTerritorio(String unidade, Long idEquipe) {
        return db.list("""
                SELECT e.id AS id_equipe, e.nome AS equipe, e.ine, m.codigo AS microarea, m.nome_acs,
                  (SELECT COUNT(*) FROM aps_domicilio d WHERE d.ativo AND d.id_equipe = e.id AND d.microarea = m.codigo) AS imoveis,
                  (SELECT COUNT(*) FROM aps_domicilio d WHERE d.ativo AND d.id_equipe = e.id AND d.microarea = m.codigo AND d.tipo_imovel = 1) AS domicilios,
                  (SELECT COUNT(*) FROM aps_familia f JOIN aps_domicilio d ON d.id = f.id_domicilio
                     WHERE f.ativo AND NOT f.mudou AND d.id_equipe = e.id AND d.microarea = m.codigo) AS familias,
                  (SELECT COUNT(*) FROM aps_cidadao c WHERE c.ativo AND c.id_equipe = e.id AND c.microarea = m.codigo) AS cidadaos,
                  (SELECT COUNT(*) FROM aps_cidadao c WHERE c.ativo AND c.id_equipe = e.id AND c.microarea = m.codigo
                     AND c.id_familia IS NULL) AS cidadaos_sem_familia,
                  (SELECT COUNT(*) FROM aps_cidadao c WHERE c.ativo AND c.id_equipe = e.id AND c.microarea = m.codigo
                     AND c.atualizado_em < CURRENT_DATE - INTERVAL '1 year') AS cadastros_desatualizados,
                  (SELECT COUNT(*) FROM aps_cidadao c WHERE c.ativo AND c.id_equipe = e.id AND c.microarea = m.codigo AND c.recusa) AS recusas,
                  (SELECT COUNT(DISTINCT f.id_domicilio) FROM aps_ficha f JOIN aps_domicilio d ON d.id = f.id_domicilio
                     WHERE f.tipo = 8 AND f.status <> 'CANCELADA' AND d.id_equipe = e.id AND d.microarea = m.codigo
                       AND f.data_atendimento >= CURRENT_DATE - 30) AS imoveis_visitados_30d
                FROM aps_equipe e JOIN aps_microarea m ON m.id_equipe = e.id AND m.ativo
                WHERE e.ativo AND UPPER(e.unidade) = UPPER(:u) AND (CAST(:eq AS bigint) IS NULL OR e.id = CAST(:eq AS bigint))
                ORDER BY e.nome, m.codigo
                """, p("u", unidade, "eq", idEquipe));
    }

    // ------------------------------------------------------------------ Relatorios de producao (cap. 8.4)

    public List<Map<String, Object>> resumoProducao(String unidade, LocalDate de, LocalDate ate, Long idEquipe) {
        return db.list("""
                SELECT f.tipo, COUNT(*) AS registros, COUNT(DISTINCT f.id_cidadao) AS cidadaos,
                       COUNT(DISTINCT f.matricula) AS profissionais,
                       SUM(CASE WHEN f.status = 'ENVIADA' THEN 1 ELSE 0 END) AS enviados,
                       SUM(CASE WHEN f.status = 'PRONTA' THEN 1 ELSE 0 END) AS pendentes,
                       SUM(CASE WHEN f.status = 'REJEITADA' THEN 1 ELSE 0 END) AS rejeitados
                FROM aps_ficha f
                WHERE f.status <> 'CANCELADA' AND UPPER(f.unidade) = UPPER(:u)
                  AND f.data_atendimento BETWEEN CAST(:de AS date) AND CAST(:ate AS date)
                  AND (CAST(:ine AS varchar) IS NULL OR f.ine = CAST(:ine AS varchar))
                GROUP BY f.tipo ORDER BY f.tipo
                """, p("u", unidade, "de", de, "ate", ate, "ine", ine(idEquipe)));
    }

    public List<Map<String, Object>> producaoPorProfissional(String unidade, LocalDate de, LocalDate ate, Integer tipo, Long idEquipe) {
        return db.list("""
                SELECT f.matricula, f.profissional_nome, f.profissional_cbo, f.tipo, COUNT(*) AS registros,
                       COUNT(DISTINCT f.id_cidadao) AS cidadaos, MIN(f.data_atendimento) AS primeiro, MAX(f.data_atendimento) AS ultimo
                FROM aps_ficha f
                WHERE f.status <> 'CANCELADA' AND UPPER(f.unidade) = UPPER(:u)
                  AND f.data_atendimento BETWEEN CAST(:de AS date) AND CAST(:ate AS date)
                  AND (CAST(:tipo AS integer) IS NULL OR f.tipo = CAST(:tipo AS integer))
                  AND (CAST(:ine AS varchar) IS NULL OR f.ine = CAST(:ine AS varchar))
                GROUP BY f.matricula, f.profissional_nome, f.profissional_cbo, f.tipo
                ORDER BY f.profissional_nome, f.tipo
                """, p("u", unidade, "de", de, "ate", ate, "tipo", tipo, "ine", ine(idEquipe)));
    }

    /** Distribuicoes de um campo dentro dos filhos de um modelo (ex.: FAI por tipoAtendimento, condutas, CIAP). */
    public Map<String, Object> producaoDetalhada(String unidade, LocalDate de, LocalDate ate, int tipo, Long idEquipe) {
        String lista = switch (tipo) {
            case 4 -> "atendimentosIndividuais";
            case 5 -> "atendimentosOdontologicos";
            case 7 -> "atendProcedimentos";
            case 8 -> "visitasDomiciliares";
            case 10 -> "atendimentosDomiciliares";
            case 14 -> "vacinacoes";
            default -> null;
        };
        List<String> escalares = switch (tipo) {
            case 4 -> List.of("tipoAtendimento", "localDeAtendimento", "sexo", "turno", "racionalidadeSaude", "aleitamentoMaterno");
            case 5 -> List.of("tipoAtendimento", "localAtendimento", "sexo", "turno");
            case 7 -> List.of("localAtendimento", "sexo", "turno", "statusEscutaInicialOrientacao");
            case 8 -> List.of("desfecho", "tipoDeImovel", "sexo", "turno");
            case 10 -> List.of("atencaoDomiciliarModalidade", "tipoAtendimento", "condutaDesfecho", "localAtendimento");
            case 6 -> List.of("atividadeTipo", "turno", "pseEducacao", "pseSaude");
            case 12, 13, 11 -> List.of("sexo", "localAtendimento", "turno");
            default -> List.of();
        };
        List<String> listas = switch (tipo) {
            case 4 -> List.of("condutas", "emultis");
            case 5 -> List.of("tiposConsultaOdonto", "tiposVigilanciaSaudeBucal", "tiposEncamOdonto", "tiposFornecimOdonto");
            case 7 -> List.of("procedimentos");
            case 8 -> List.of("motivosVisita");
            case 10 -> List.of("condicoesAvaliadas", "procedimentos");
            case 6 -> List.of("temasParaReuniao", "publicoAlvo", "temasParaSaude", "praticasEmSaude");
            default -> List.of();
        };
        Map<String, Object> base = p("u", unidade, "de", de, "ate", ate, "tipo", tipo, "ine", ine(idEquipe), "lista", lista);
        String where = " " + """
                f.status <> 'CANCELADA' AND f.tipo = :tipo AND UPPER(f.unidade) = UPPER(:u)
                AND f.data_atendimento BETWEEN CAST(:de AS date) AND CAST(:ate AS date)
                AND (CAST(:ine AS varchar) IS NULL OR f.ine = CAST(:ine AS varchar))
                """;
        String fonte = lista == null
                ? "aps_ficha f CROSS JOIN LATERAL (SELECT f.dados AS item) ch"
                : "aps_ficha f CROSS JOIN LATERAL jsonb_array_elements(COALESCE(f.dados -> CAST(:lista AS text), '[]'::jsonb)) AS ch(item)";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registros", db.count("SELECT COUNT(*) FROM aps_ficha f WHERE " + where, base));
        out.put("atendimentos", db.count("SELECT COUNT(*) FROM " + fonte + " WHERE " + where, base));
        if (tipo == 6) {
            out.put("participantes", db.count("""
                    SELECT COALESCE(SUM(CAST(f.dados ->> 'numParticipantes' AS integer)), 0) FROM aps_ficha f WHERE """ + where, base));
        }
        Map<String, Object> dist = new LinkedHashMap<>();
        for (String campo : escalares) {
            dist.put(campo, db.list("SELECT ch.item ->> CAST(:campo AS text) AS codigo, COUNT(*) AS total FROM " + fonte
                    + " WHERE " + where + " GROUP BY 1 ORDER BY 2 DESC", withParam(base, "campo", campo)));
        }
        for (String campo : listas) {
            dist.put(campo, db.list("SELECT v.value AS codigo, COUNT(*) AS total FROM " + fonte
                    + " CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(ch.item -> CAST(:campo AS text), '[]'::jsonb)) v WHERE "
                    + where + " GROUP BY 1 ORDER BY 2 DESC LIMIT 60", withParam(base, "campo", campo)));
        }
        if (tipo == 4 || tipo == 5 || tipo == 10) {
            dist.put("ciap", db.list("SELECT pc.value ->> 'ciap' AS codigo, COUNT(*) AS total FROM " + fonte
                    + " CROSS JOIN LATERAL jsonb_array_elements(COALESCE(ch.item -> 'problemasCondicoes', '[]'::jsonb)) pc WHERE "
                    + where + " AND pc.value ->> 'ciap' IS NOT NULL GROUP BY 1 ORDER BY 2 DESC LIMIT 40", base));
            dist.put("cid10", db.list("SELECT pc.value ->> 'cid10' AS codigo, COUNT(*) AS total FROM " + fonte
                    + " CROSS JOIN LATERAL jsonb_array_elements(COALESCE(ch.item -> 'problemasCondicoes', '[]'::jsonb)) pc WHERE "
                    + where + " AND pc.value ->> 'cid10' IS NOT NULL GROUP BY 1 ORDER BY 2 DESC LIMIT 40", base));
        }
        if (tipo == 5) {
            dist.put("procedimentosRealizados", db.list("SELECT pq.value ->> 'coMsProcedimento' AS codigo, "
                    + "SUM(COALESCE(CAST(pq.value ->> 'quantidade' AS integer), 1)) AS total FROM " + fonte
                    + " CROSS JOIN LATERAL jsonb_array_elements(COALESCE(ch.item -> 'procedimentosRealizados', '[]'::jsonb)) pq WHERE "
                    + where + " GROUP BY 1 ORDER BY 2 DESC LIMIT 60", base));
        }
        out.put("distribuicoes", dist);
        return out;
    }

    public List<Map<String, Object>> vacinacao(String unidade, LocalDate de, LocalDate ate) {
        return db.list("""
                SELECT v.imunobiologico, v.dose, v.estrategia, v.tipo_registro, COUNT(*) AS total,
                       SUM(CASE WHEN DATE_PART('year', AGE(v.data_aplicacao, c.data_nascimento)) < 1 THEN 1 ELSE 0 END) AS menor_1_ano,
                       SUM(CASE WHEN DATE_PART('year', AGE(v.data_aplicacao, c.data_nascimento)) BETWEEN 1 AND 4 THEN 1 ELSE 0 END) AS de_1_a_4,
                       SUM(CASE WHEN DATE_PART('year', AGE(v.data_aplicacao, c.data_nascimento)) BETWEEN 5 AND 19 THEN 1 ELSE 0 END) AS de_5_a_19,
                       SUM(CASE WHEN DATE_PART('year', AGE(v.data_aplicacao, c.data_nascimento)) BETWEEN 20 AND 59 THEN 1 ELSE 0 END) AS de_20_a_59,
                       SUM(CASE WHEN DATE_PART('year', AGE(v.data_aplicacao, c.data_nascimento)) >= 60 THEN 1 ELSE 0 END) AS maior_60,
                       SUM(CASE WHEN v.gestante THEN 1 ELSE 0 END) AS gestantes
                FROM aps_vacinacao v JOIN aps_cidadao c ON c.id = v.id_cidadao
                WHERE NOT v.excluido AND v.tipo_registro IN ('APLICACAO', 'TRANSCRICAO') AND UPPER(v.unidade) = UPPER(:u)
                  AND v.data_aplicacao BETWEEN CAST(:de AS date) AND CAST(:ate AS date)
                GROUP BY v.imunobiologico, v.dose, v.estrategia, v.tipo_registro
                ORDER BY v.imunobiologico, v.dose
                """, p("u", unidade, "de", de, "ate", ate));
    }

    public Map<String, Object> absenteismo(String unidade, LocalDate de, LocalDate ate) {
        Map<String, Object> pr = p("u", unidade, "de", de, "ate", ate);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("porProfissional", db.list("""
                SELECT a.matricula_profissional, a.profissional, COUNT(*) AS agendados,
                       SUM(CASE WHEN a.status = 'FALTOU' THEN 1 ELSE 0 END) AS faltas,
                       SUM(CASE WHEN a.status = 'ATENDIDO' THEN 1 ELSE 0 END) AS atendidos,
                       SUM(CASE WHEN a.status = 'CANCELADO' THEN 1 ELSE 0 END) AS cancelados,
                       ROUND(100.0 * SUM(CASE WHEN a.status = 'FALTOU' THEN 1 ELSE 0 END) / NULLIF(COUNT(*) - SUM(CASE WHEN a.status = 'CANCELADO' THEN 1 ELSE 0 END), 0), 1) AS taxa_absenteismo
                FROM aps_agendamento a
                WHERE a.tipo = 'CONSULTA' AND UPPER(a.unidade) = UPPER(:u)
                  AND CAST(a.data_hora AS date) BETWEEN CAST(:de AS date) AND CAST(:ate AS date)
                GROUP BY a.matricula_profissional, a.profissional ORDER BY taxa_absenteismo DESC NULLS LAST
                """, pr));
        out.put("faltosos", db.list("""
                SELECT c.id, c.nome, c.telefone, COUNT(*) AS faltas, MAX(a.data_hora) AS ultima
                FROM aps_agendamento a JOIN aps_cidadao c ON c.id = a.id_cidadao
                WHERE a.status = 'FALTOU' AND UPPER(a.unidade) = UPPER(:u)
                  AND CAST(a.data_hora AS date) BETWEEN CAST(:de AS date) AND CAST(:ate AS date)
                GROUP BY c.id, c.nome, c.telefone HAVING COUNT(*) >= 2 ORDER BY faltas DESC LIMIT 200
                """, pr));
        return out;
    }

    public List<Map<String, Object>> atendimentos(String unidade, LocalDate de, LocalDate ate) {
        return db.list("""
                SELECT a.matricula, a.profissional, a.cbo, a.tipo, COUNT(*) AS atendimentos,
                       COUNT(DISTINCT a.id_cidadao) AS cidadaos,
                       ROUND(AVG(EXTRACT(EPOCH FROM (a.fim - a.inicio)) / 60)::numeric, 1) AS minutos_medios,
                       SUM(CASE WHEN a.registro_tardio THEN 1 ELSE 0 END) AS registros_tardios
                FROM aps_atendimento a
                WHERE a.status = 'FINALIZADO' AND UPPER(a.unidade) = UPPER(:u)
                  AND a.data_referencia BETWEEN CAST(:de AS date) AND CAST(:ate AS date)
                GROUP BY a.matricula, a.profissional, a.cbo, a.tipo ORDER BY atendimentos DESC
                """, p("u", unidade, "de", de, "ate", ate));
    }

    public Map<String, Object> cuidadoCompartilhado(String unidade, LocalDate de, LocalDate ate) {
        Map<String, Object> pr = p("u", unidade, "de", de, "ate", ate);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("porStatus", db.list("""
                SELECT status, prioridade, COUNT(*) AS total FROM aps_cuidado_compartilhado
                WHERE UPPER(unidade) = UPPER(:u) AND CAST(criado_em AS date) BETWEEN CAST(:de AS date) AND CAST(:ate AS date)
                GROUP BY status, prioridade ORDER BY status, prioridade
                """, pr));
        out.put("porExecutante", db.list("""
                SELECT nome_executante, cbo_executante, COUNT(*) AS total,
                       SUM(CASE WHEN status = 'AGUARDANDO' THEN 1 ELSE 0 END) AS aguardando
                FROM aps_cuidado_compartilhado
                WHERE UPPER(unidade) = UPPER(:u) AND CAST(criado_em AS date) BETWEEN CAST(:de AS date) AND CAST(:ate AS date)
                GROUP BY nome_executante, cbo_executante ORDER BY total DESC
                """, pr));
        return out;
    }

    // ------------------------------------------------------------------ Indicadores (estimativa local)
    //
    // Componente de Qualidade eSF/eAP (Portaria GM/MS 3.493/2024; Notas Metodologicas C1-C7 da SAPS, 2025).
    // C2-C7: cada pessoa do denominador soma os pontos das boas praticas cumpridas (max. 100); a nota e
    // Soma(pontos da pratica x realizadas / elegiveis). C1: % de atendimentos por demanda programada.
    // Estimativa com os registros do G-PEC; o oficial e do Ministerio (SIAPS), com registros de todo o pais.

    /** Consulta de medica(o) ou enfermeira(o) (CBO 2251, 2252, 2253, 2231, 2235). */
    private static final String MED_ENF = "(a.cbo LIKE '2251%' OR a.cbo LIKE '2252%' OR a.cbo LIKE '2253%' OR a.cbo LIKE '2231%' OR a.cbo LIKE '2235%')";
    /** eAP (tipo 76): as boas praticas de visita do ACS nao condicionam a pontuacao. */
    private static final String EAP = "EXISTS (SELECT 1 FROM aps_equipe e WHERE e.id = c.id_equipe AND UPPER(CAST(e.tipo AS text)) IN ('EAP', '76'))";
    private static final String HOJE_FIM = "(CURRENT_DATE + 1)";
    private static final String NAO_ODONTO = "COALESCE(a.tipo, '') <> 'ODONTO'";

    private static final String[] SIFILIS = { "0214010074", "0214010082", "0214010252", "0202031098", "0202031110", "0202031179" };
    private static final String[] HIV = { "0214010040", "0214010279", "0214010058", "0202030300" };
    private static final String[] HEP_B = { "0214010104", "0214010236", "0202030784", "0202030970" };
    private static final String[] HEP_C = { "0214010090", "0214010309", "0202030059", "0202030679" };

    private record Pratica(String letra, String descricao, int pontos, String condicao, String populacao) {
        Pratica(String letra, String descricao, int pontos, String condicao) {
            this(letra, descricao, pontos, condicao, null);
        }
    }

    /** Visita do ACS (ficha 8) com desfecho "realizada" (recusada/ausente nao contam). */
    private static String visitaOk(String v) {
        return v + ".tipo = 8 AND " + v + ".status <> 'CANCELADA' AND CAST(" + v + ".dados AS text) !~ '\"desfecho\"\\s*:\\s*\"?[23]([^0-9]|$)'";
    }

    private static String consultas(String de, String ate) {
        return "(SELECT COUNT(*) FROM aps_atendimento a WHERE a.id_cidadao = c.id AND a.status = 'FINALIZADO' AND " + NAO_ODONTO + " AND "
                + MED_ENF + " AND a.inicio >= " + de + " AND a.inicio < " + ate + ")";
    }

    /** Dias distintos com a medicao (peso+altura no mesmo registro = "simultaneos"). */
    private static String diasMedicao(String cond, String de, String ate) {
        return "(SELECT COUNT(DISTINCT CAST(m.data_hora AS date)) FROM aps_medicao m WHERE m.id_cidadao = c.id AND " + cond
                + " AND m.data_hora >= " + de + " AND m.data_hora < " + ate + ")";
    }

    private static final String PA = "m.pressao_sistolica IS NOT NULL";
    private static final String PESO_ALTURA = "m.peso IS NOT NULL AND m.altura IS NOT NULL";

    private static String visitas(String de, String ate) {
        return "(SELECT COUNT(*) FROM aps_ficha v WHERE v.id_cidadao = c.id AND " + visitaOk("v")
                + " AND v.data_atendimento >= " + de + " AND v.data_atendimento < " + ate + ")";
    }

    /** Duas visitas do ACS com pelo menos 30 dias entre elas, a partir de "de". */
    private static String duasVisitas30(String de) {
        return "EXISTS (SELECT 1 FROM aps_ficha v JOIN aps_ficha v2 ON v2.id_cidadao = v.id_cidadao AND " + visitaOk("v2")
                + " AND v2.data_atendimento >= v.data_atendimento + 30 WHERE v.id_cidadao = c.id AND " + visitaOk("v")
                + " AND v.data_atendimento >= " + de + ")";
    }

    /** Codigo SIGTAP/AB (exame solicitado/avaliado ou procedimento) em alguma ficha do periodo. */
    private static String codigo(String de, String ate, String... codigos) {
        StringBuilder or = new StringBuilder();
        for (String cod : codigos) {
            or.append(or.length() == 0 ? "" : " OR ").append("CAST(f.dados AS text) LIKE '%").append(cod).append("%'");
        }
        return "EXISTS (SELECT 1 FROM aps_ficha f WHERE f.id_cidadao = c.id AND f.status <> 'CANCELADA' AND f.data_atendimento >= "
                + de + " AND f.data_atendimento < " + ate + " AND (" + or + "))";
    }

    private static String vacina(String de, String ate, String... imunos) {
        return "EXISTS (SELECT 1 FROM aps_vacinacao vc WHERE vc.id_cidadao = c.id AND NOT COALESCE(vc.excluido, FALSE)"
                + " AND vc.tipo_registro <> 'APRAZAMENTO' AND CAST(vc.imunobiologico AS text) IN ('" + String.join("', '", imunos) + "')"
                + " AND vc.data_aplicacao >= " + de + " AND vc.data_aplicacao < " + ate + ")";
    }

    /** Condicao ativa na lista de problemas (CIAP-2 ou CID-10 pelos 3 primeiros caracteres). */
    private static String condicaoAtiva(String ciaps, String cids) {
        return "EXISTS (SELECT 1 FROM aps_problema pr WHERE pr.id_cidadao = c.id AND pr.tipo <> 'ALERGIA' AND pr.situacao <> 2"
                + " AND (pr.ciap IN (" + ciaps + ") OR LEFT(REPLACE(COALESCE(pr.cid10, ''), '.', ''), 3) IN (" + cids + ")))";
    }

    private static String idadeEntre(int min, int max) {
        return "DATE_PART('year', AGE(c.data_nascimento)) BETWEEN " + min + " AND " + max;
    }

    private record Indicador(String codigo, String descricao, String fromWhere, List<Pratica> praticas, String nota) {
    }

    public List<Map<String, Object>> indicadores(String unidade, Long idEquipe) {
        Map<String, Object> pr = p("u", unidade, "eq", idEquipe);
        String escopo = "c.ativo AND UPPER(c.unidade) = UPPER(:u) AND (CAST(:eq AS bigint) IS NULL OR c.id_equipe = CAST(:eq AS bigint))";
        List<Map<String, Object>> out = new java.util.ArrayList<>();

        out.add(maisAcesso(pr));
        for (Indicador d : definicoes(escopo)) {
            out.add(boasPraticas(d.codigo(), d.descricao(), d.fromWhere(), d.praticas(), pr, d.nota()));
        }

        Map<String, Object> cadastro = indicador("CADASTRO", "Cidadãos vinculados com cadastro individual atualizado nos últimos 12 meses",
                "SELECT COUNT(*) FROM aps_cidadao c WHERE " + escopo,
                "SELECT COUNT(*) FROM aps_cidadao c WHERE " + escopo + " AND c.atualizado_em >= CURRENT_DATE - INTERVAL '1 year'", pr);
        cadastro.put("oficial", false);
        out.add(cadastro);
        return out;
    }

    /**
     * Cuidados pendentes de UM cidadao: as mesmas boas praticas dos indicadores C2-C7, avaliadas so para ele.
     * Serve de lembrete no atendimento (nao bloqueia nada) e cada pendencia resolvida pontua para a equipe.
     */
    public List<Map<String, Object>> pendencias(Long idCidadao, String unidade) {
        Map<String, Object> pr = p("u", unidade, "cid", idCidadao);
        String escopo = "c.ativo AND c.id = CAST(:cid AS bigint) AND UPPER(c.unidade) = UPPER(:u)";
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Indicador d : definicoes(escopo)) {
            List<Pratica> ps = d.praticas();
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS pessoas");
            for (int i = 0; i < ps.size(); i++) {
                String pop = ps.get(i).populacao() == null ? "TRUE" : ps.get(i).populacao();
                sql.append(", COUNT(*) FILTER (WHERE ").append(pop).append(") AS b").append(i)
                        .append(", COUNT(*) FILTER (WHERE (").append(pop).append(") AND (").append(ps.get(i).condicao()).append(")) AS a").append(i);
            }
            sql.append(" FROM ").append(d.fromWhere());
            Map<String, Object> r = db.one(sql.toString(), pr);
            for (int i = 0; i < ps.size(); i++) {
                Pratica p = ps.get(i);
                // "FALSE" = pratica nao estimada localmente (ex.: vacinas da crianca): nao vira lembrete.
                if (numero(r.get("b" + i)) > 0 && numero(r.get("a" + i)) == 0 && !"FALSE".equals(p.condicao())) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("indicador", d.codigo());
                    m.put("linha", d.descricao());
                    m.put("letra", p.letra());
                    m.put("descricao", p.descricao());
                    m.put("pontos", p.pontos());
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** C2-C7: as mesmas regras servem para a equipe (indicadores) e para um cidadao (pendencias). */
    private List<Indicador> definicoes(String escopo) {
        String m6 = "(CURRENT_DATE - INTERVAL '6 months')";
        String m12 = "(CURRENT_DATE - INTERVAL '12 months')";
        List<Indicador> defs = new java.util.ArrayList<>();

        String nasc = "c.data_nascimento";
        defs.add(new Indicador("C2", "Cuidado no desenvolvimento infantil",
                "aps_cidadao c WHERE " + escopo + " AND c.data_nascimento > CURRENT_DATE - INTERVAL '2 years'", List.of(
                new Pratica("A", "1ª consulta (médico/enfermeiro) até o 30º dia de vida", 20, consultas(nasc, nasc + " + 31") + " >= 1"),
                new Pratica("B", "9 consultas (médico/enfermeiro) até 2 anos", 20, consultas(nasc, "(" + nasc + " + INTERVAL '2 years')") + " >= 9"),
                new Pratica("C", "9 registros simultâneos de peso e altura até 2 anos", 20, diasMedicao(PESO_ALTURA, nasc, "(" + nasc + " + INTERVAL '2 years')") + " >= 9"),
                new Pratica("D", "2 visitas do ACS: a 1ª até 30 dias e a 2ª até 6 meses de vida", 20,
                        "EXISTS (SELECT 1 FROM aps_ficha v JOIN aps_ficha v2 ON v2.id_cidadao = v.id_cidadao AND " + visitaOk("v2")
                        + " AND v2.data_atendimento > v.data_atendimento AND v2.data_atendimento < c.data_nascimento + 183"
                        + " WHERE v.id_cidadao = c.id AND " + visitaOk("v") + " AND v.data_atendimento < c.data_nascimento + 31) OR " + EAP),
                new Pratica("E", "Vacinas do calendário com todas as doses (não estimado aqui: ver Vacinação)", 20, "FALSE")),
                "A boa prática E (vacinas) não é estimada localmente; a nota máxima aqui é 80."));

        String dum = "g.dum";
        String fimGest = "COALESCE(g.data_desfecho, " + HOJE_FIM + ")";
        String fimPuerp = "(g.data_desfecho + 43)";
        String primeiraConsulta = "(SELECT MIN(CAST(a.inicio AS date)) FROM aps_atendimento a WHERE a.id_cidadao = c.id AND a.status = 'FINALIZADO' AND "
                + NAO_ODONTO + " AND " + MED_ENF + " AND a.inicio >= g.dum)";
        defs.add(new Indicador("C3", "Cuidado na gestação e puerpério",
                "aps_gestacao g JOIN aps_cidadao c ON c.id = g.id_cidadao WHERE g.status IN ('ATIVA', 'PUERPERIO') AND g.dum IS NOT NULL AND " + escopo, List.of(
                new Pratica("A", "1ª consulta até a 12ª semana", 10, consultas(dum, dum + " + 85") + " >= 1"),
                new Pratica("B", "7 consultas (médico/enfermeiro) na gestação", 9, consultas(dum, fimGest) + " >= 7"),
                new Pratica("C", "7 aferições de pressão arterial na gestação", 9, diasMedicao(PA, dum, fimGest) + " >= 7"),
                new Pratica("D", "7 registros simultâneos de peso e altura na gestação", 9, diasMedicao(PESO_ALTURA, dum, fimGest) + " >= 7"),
                new Pratica("E", "3 visitas do ACS após a 1ª consulta de pré-natal", 9, visitas(primeiraConsulta, fimGest) + " >= 3 OR " + EAP),
                new Pratica("F", "Vacina dTpa a partir da 20ª semana", 9, vacina(dum + " + 140", fimGest, "57")),
                new Pratica("G", "Testes/exames de sífilis, HIV, hepatites B e C no 1º trimestre", 9,
                        codigo(dum, dum + " + 98", SIFILIS) + " AND " + codigo(dum, dum + " + 98", HIV) + " AND "
                        + codigo(dum, dum + " + 98", HEP_B) + " AND " + codigo(dum, dum + " + 98", HEP_C)),
                new Pratica("H", "Testes/exames de sífilis e HIV no 3º trimestre", 9,
                        codigo(dum + " + 189", fimGest, SIFILIS) + " AND " + codigo(dum + " + 189", fimGest, HIV)),
                new Pratica("I", "Consulta (médico/enfermeiro) no puerpério", 9, "g.data_desfecho IS NOT NULL AND " + consultas("g.data_desfecho", fimPuerp) + " >= 1"),
                new Pratica("J", "Visita do ACS no puerpério", 9, "(g.data_desfecho IS NOT NULL AND " + visitas("g.data_desfecho", fimPuerp) + " >= 1) OR " + EAP),
                new Pratica("K", "Atendimento de saúde bucal na gestação", 9,
                        "EXISTS (SELECT 1 FROM aps_atendimento a WHERE a.id_cidadao = c.id AND a.status = 'FINALIZADO' AND a.tipo = 'ODONTO'"
                        + " AND a.inicio >= g.dum AND a.inicio < " + fimGest + ")")),
                null));

        String diabetes = condicaoAtiva("'T89', 'T90'", "'E10', 'E11', 'E14'");
        defs.add(new Indicador("C4", "Cuidado da pessoa com diabetes", "aps_cidadao c WHERE " + escopo + " AND " + diabetes, List.of(
                new Pratica("A", "Consulta (médico/enfermeiro) nos últimos 6 meses", 20, consultas(m6, HOJE_FIM) + " >= 1"),
                new Pratica("B", "Pressão arterial aferida nos últimos 6 meses", 15, diasMedicao(PA, m6, HOJE_FIM) + " >= 1"),
                new Pratica("C", "Peso e altura nos últimos 12 meses", 15, diasMedicao(PESO_ALTURA, m12, HOJE_FIM) + " >= 1"),
                new Pratica("D", "2 visitas do ACS (30 dias entre elas) em 12 meses", 20, duasVisitas30(m12) + " OR " + EAP),
                new Pratica("E", "Hemoglobina glicada solicitada/avaliada em 12 meses", 15, codigo(m12, HOJE_FIM, "0202010503", "ABEX008")),
                new Pratica("F", "Avaliação dos pés em 12 meses", 15, codigo(m12, HOJE_FIM, "0301040095"))),
                null));

        String hipertensao = condicaoAtiva("'K86', 'K87'", "'I10', 'I11', 'I12', 'I13', 'I15', 'O10', 'O11'");
        defs.add(new Indicador("C5", "Cuidado da pessoa com hipertensão", "aps_cidadao c WHERE " + escopo + " AND " + hipertensao, List.of(
                new Pratica("A", "Consulta (médico/enfermeiro) nos últimos 6 meses", 25, consultas(m6, HOJE_FIM) + " >= 1"),
                new Pratica("B", "Pressão arterial aferida nos últimos 6 meses", 25, diasMedicao(PA, m6, HOJE_FIM) + " >= 1"),
                new Pratica("C", "Peso e altura simultâneos nos últimos 12 meses", 25, diasMedicao(PESO_ALTURA, m12, HOJE_FIM) + " >= 1"),
                new Pratica("D", "2 visitas do ACS (30 dias entre elas) em 12 meses", 25, duasVisitas30(m12) + " OR " + EAP)),
                null));

        defs.add(new Indicador("C6", "Cuidado da pessoa idosa",
                "aps_cidadao c WHERE " + escopo + " AND c.data_nascimento <= CURRENT_DATE - INTERVAL '60 years'", List.of(
                new Pratica("A", "Consulta (médico/enfermeiro) nos últimos 12 meses", 25, consultas(m12, HOJE_FIM) + " >= 1"),
                new Pratica("B", "Peso e altura simultâneos nos últimos 12 meses", 25, diasMedicao(PESO_ALTURA, m12, HOJE_FIM) + " >= 1"),
                new Pratica("C", "2 visitas do ACS (30 dias entre elas) em 12 meses", 25, duasVisitas30(m12) + " OR " + EAP),
                new Pratica("D", "Vacina influenza nos últimos 12 meses", 25, vacina(m12, HOJE_FIM, "33", "77"))),
                null));

        String sexual = "EXISTS (SELECT 1 FROM aps_atendimento a, jsonb_array_elements(CASE WHEN jsonb_typeof(a.dados -> 'problemasAvaliados') = 'array'"
                + " THEN a.dados -> 'problemasAvaliados' ELSE CAST('[]' AS jsonb) END) pa WHERE a.id_cidadao = c.id AND a.status = 'FINALIZADO' AND "
                + MED_ENF + " AND a.inicio >= " + m12 + " AND (pa ->> 'ciap' IN ('B25', 'W02', 'W10', 'W11', 'W12', 'W13', 'W14', 'W15', 'W79', 'W82',"
                + " 'X01', 'X02', 'X03', 'X04', 'X05', 'X06', 'X07', 'X08', 'X09', 'X10', 'X11', 'X12', 'X13', 'X23', 'X24', 'X82', 'X89', 'Y14')"
                + " OR LEFT(REPLACE(COALESCE(pa ->> 'cid10', ''), '.', ''), 3) IN ('N80', 'N91', 'N92', 'N93', 'N94', 'N95', 'N96', 'N97', 'O03', 'O04', 'Z30', 'Z31', 'Z70')))";
        defs.add(new Indicador("C7", "Cuidado da mulher na prevenção do câncer",
                "aps_cidadao c WHERE " + escopo + " AND c.sexo = 1 AND " + idadeEntre(9, 69), List.of(
                new Pratica("A", "Rastreamento do colo do útero (25 a 64 anos) em 36 meses; HPV molecular em 60 meses", 20,
                        codigo("(CURRENT_DATE - INTERVAL '36 months')", HOJE_FIM, "0201020033", "0201020076", "0201020084", "0203010019", "0203010086", "ABPG010")
                        + " OR " + codigo("(CURRENT_DATE - INTERVAL '60 months')", HOJE_FIM, "0202100251"), idadeEntre(25, 64)),
                new Pratica("B", "1 dose da vacina HPV (9 a 14 anos)", 30, vacina("DATE '1900-01-01'", HOJE_FIM, "67", "93"), idadeEntre(9, 14)),
                new Pratica("C", "Atendimento em saúde sexual e reprodutiva em 12 meses (14 a 69 anos)", 30, sexual, idadeEntre(14, 69)),
                new Pratica("D", "Mamografia solicitada/avaliada em 24 meses (50 a 69 anos)", 20,
                        codigo("(CURRENT_DATE - INTERVAL '24 months')", HOJE_FIM, "0204030030", "0204030188"), idadeEntre(50, 69))),
                "Cada boa prática usa a própria faixa etária. Homens transgênero não são identificados pelo cadastro local."));
        return defs;
    }

    /** C1: atendimentos por demanda programada / todos os atendimentos (medico e enfermeiro), ultimos 4 meses. */
    private Map<String, Object> maisAcesso(Map<String, Object> pr) {
        Map<String, Object> r = db.one("""
                SELECT COUNT(*) FILTER (WHERE a.dados ->> 'tipoAtendimento' IN ('1', '2')) AS programada, COUNT(*) AS total
                FROM aps_atendimento a
                WHERE a.status = 'FINALIZADO' AND UPPER(a.unidade) = UPPER(:u) AND a.inicio >= CURRENT_DATE - INTERVAL '4 months'
                  AND (CAST(:eq AS bigint) IS NULL OR a.ine = (SELECT e.ine FROM aps_equipe e WHERE e.id = CAST(:eq AS bigint)))
                  AND REPLACE(a.cbo, '-', '') IN ('225142', '225170', '225130', '225125', '225250', '223565', '223505')
                  AND a.dados ->> 'tipoAtendimento' IN ('1', '2', '4', '5', '6')
                """, pr);
        long prog = numero(r.get("programada"));
        long total = numero(r.get("total"));
        Double pct = total == 0 ? null : Math.round(1000.0 * prog / total) / 10.0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", "C1");
        m.put("descricao", "Mais acesso (demanda programada sobre o total de atendimentos, últimos 4 meses)");
        m.put("oficial", true);
        m.put("denominador", total);
        m.put("numerador", prog);
        m.put("percentual", pct);
        m.put("nota", pct);
        // Faixa ideal 50-70%: acima de 70 indica pouco espaco para a demanda espontanea.
        m.put("conceito", pct == null ? null : pct > 50 && pct <= 70 ? "OTIMO" : pct > 30 && pct <= 50 ? "BOM" : pct > 10 && pct <= 30 ? "SUFICIENTE" : "REGULAR");
        m.put("boasPraticas", List.of());
        m.put("observacao", "Estimativa local. Faixas: ótimo >50 e <=70; bom >30 e <=50; suficiente >10 e <=30; regular <=10 ou >70.");
        return m;
    }

    private Map<String, Object> boasPraticas(String codigo, String descricao, String fromWhere, List<Pratica> praticas,
                                             Map<String, Object> pr, String nota) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS pessoas");
        for (int i = 0; i < praticas.size(); i++) {
            Pratica p = praticas.get(i);
            String pop = p.populacao() == null ? "TRUE" : p.populacao();
            sql.append(", COUNT(*) FILTER (WHERE ").append(pop).append(") AS b").append(i)
                    .append(", COUNT(*) FILTER (WHERE (").append(pop).append(") AND (").append(p.condicao()).append(")) AS a").append(i);
        }
        sql.append(" FROM ").append(fromWhere);
        Map<String, Object> r = db.one(sql.toString(), pr);
        long pessoas = numero(r.get("pessoas"));
        double soma = 0;
        List<Map<String, Object>> itens = new java.util.ArrayList<>();
        for (int i = 0; i < praticas.size(); i++) {
            Pratica p = praticas.get(i);
            long a = numero(r.get("a" + i));
            long b = numero(r.get("b" + i));
            if (b > 0) {
                soma += p.pontos() * (double) a / b;
            }
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("letra", p.letra());
            it.put("descricao", p.descricao());
            it.put("pontos", p.pontos());
            it.put("realizadas", a);
            it.put("elegiveis", b);
            it.put("percentual", b == 0 ? null : Math.round(1000.0 * a / b) / 10.0);
            itens.add(it);
        }
        Double valor = pessoas == 0 ? null : Math.round(soma * 10.0) / 10.0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("descricao", descricao);
        m.put("oficial", true);
        m.put("denominador", pessoas);
        m.put("numerador", null);
        m.put("percentual", valor);
        m.put("nota", valor);
        m.put("conceito", valor == null ? null : valor > 75 ? "OTIMO" : valor > 50 ? "BOM" : valor > 25 ? "SUFICIENTE" : "REGULAR");
        m.put("boasPraticas", itens);
        m.put("observacao", "Estimativa local (nota de 0 a 100). O resultado oficial é calculado pelo Ministério da Saúde no SIAPS."
                + (nota == null ? "" : " " + nota));
        return m;
    }

    private static long numero(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private Map<String, Object> indicador(String codigo, String descricao, String sqlDen, String sqlNum, Map<String, Object> pr) {
        long den = db.count(sqlDen, pr);
        long num = db.count(sqlNum, pr);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("descricao", descricao);
        m.put("denominador", den);
        m.put("numerador", num);
        m.put("percentual", den == 0 ? null : Math.round(1000.0 * num / den) / 10.0);
        m.put("observacao", "Indicador de gestão do cadastro (não faz parte do componente de qualidade).");
        return m;
    }

    /**
     * Faturamento da APS = producao que chega ao SISAB (cofinanciamento), nao AIH/BPA.
     * Por competencia (mes da data do atendimento): situacao da transmissao por equipe, base cadastral
     * e tudo que impede um registro de contar (sem INE, profissional sem CNS, rejeitado, atrasado).
     */
    public Map<String, Object> faturamento(String unidade, LocalDate inicio) {
        LocalDate de = inicio.withDayOfMonth(1);
        LocalDate ate = de.plusMonths(1).minusDays(1);
        Map<String, Object> pr = p("u", unidade, "de", de, "ate", ate);
        Map<String, Object> resumo = db.one("""
                SELECT COUNT(*) AS registros,
                       COUNT(*) FILTER (WHERE status = 'ENVIADA') AS enviados,
                       COUNT(*) FILTER (WHERE status = 'PRONTA') AS pendentes,
                       COUNT(*) FILTER (WHERE status = 'REJEITADA') AS rejeitados,
                       COUNT(*) FILTER (WHERE ine IS NULL OR ine = '') AS sem_ine,
                       COUNT(DISTINCT id_cidadao) AS cidadaos_atendidos
                FROM aps_ficha
                WHERE status <> 'CANCELADA' AND UPPER(unidade) = UPPER(:u)
                  AND data_atendimento BETWEEN CAST(:de AS date) AND CAST(:ate AS date)
                """, pr);
        List<Map<String, Object>> porEquipe = db.list("""
                SELECT e.id, e.nome, e.ine, e.tipo,
                       (SELECT COUNT(*) FROM aps_ficha f WHERE f.ine = e.ine AND f.status <> 'CANCELADA'
                          AND f.data_atendimento BETWEEN CAST(:de AS date) AND CAST(:ate AS date)) AS registros,
                       (SELECT COUNT(*) FROM aps_ficha f WHERE f.ine = e.ine AND f.status = 'ENVIADA'
                          AND f.data_atendimento BETWEEN CAST(:de AS date) AND CAST(:ate AS date)) AS enviados,
                       (SELECT COUNT(*) FROM aps_ficha f WHERE f.ine = e.ine AND f.status = 'PRONTA'
                          AND f.data_atendimento BETWEEN CAST(:de AS date) AND CAST(:ate AS date)) AS pendentes,
                       (SELECT COUNT(*) FROM aps_ficha f WHERE f.ine = e.ine AND f.status = 'REJEITADA'
                          AND f.data_atendimento BETWEEN CAST(:de AS date) AND CAST(:ate AS date)) AS rejeitados,
                       (SELECT COUNT(*) FROM aps_cidadao c WHERE c.id_equipe = e.id AND c.ativo AND NOT c.recusa) AS cadastrados,
                       (SELECT COUNT(*) FROM aps_cidadao c WHERE c.id_equipe = e.id AND c.ativo AND NOT c.recusa
                          AND COALESCE(c.cpf, '') = '' AND COALESCE(c.cns, '') = '') AS cadastrados_sem_cpf_cns,
                       (SELECT COUNT(*) FROM aps_lotacao l WHERE l.id_equipe = e.id AND l.ativo) AS profissionais
                FROM aps_equipe e
                WHERE UPPER(e.unidade) = UPPER(:u) AND e.ativo
                ORDER BY e.nome
                """, pr);
        List<Map<String, Object>> lotacoesComProblema = db.list("""
                SELECT l.id, l.nome, l.matricula, l.cbo,
                       CASE WHEN COALESCE(l.cns, '') = '' THEN 'Sem CNS do profissional'
                            WHEN l.id_equipe IS NULL THEN 'Sem equipe (INE)' END AS problema
                FROM aps_lotacao l
                WHERE UPPER(l.unidade) = UPPER(:u) AND l.ativo AND (COALESCE(l.cns, '') = '' OR l.id_equipe IS NULL)
                ORDER BY l.nome
                """, pr);
        List<Map<String, Object>> rejeitadas = db.list("""
                SELECT id, tipo, data_atendimento, profissional_nome, ultimo_erro, tentativas
                FROM aps_ficha
                WHERE UPPER(unidade) = UPPER(:u) AND status = 'REJEITADA'
                ORDER BY data_atendimento DESC LIMIT 100
                """, pr);
        List<Map<String, Object>> atrasadas = db.list("""
                SELECT to_char(data_atendimento, 'YYYY-MM') AS competencia, COUNT(*) AS registros
                FROM aps_ficha
                WHERE UPPER(unidade) = UPPER(:u) AND status = 'PRONTA' AND data_atendimento < CAST(:de AS date)
                GROUP BY 1 ORDER BY 1
                """, pr);
        Long semMicroarea = db.count("""
                SELECT COUNT(*) FROM aps_cidadao
                WHERE UPPER(unidade) = UPPER(:u) AND ativo AND NOT recusa AND NOT fora_area AND COALESCE(microarea, '') = ''
                """, pr);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("competencia", de.toString().substring(0, 7));
        out.put("de", de);
        out.put("ate", ate);
        out.put("resumo", resumo);
        out.put("porEquipe", porEquipe);
        out.put("lotacoesComProblema", lotacoesComProblema);
        out.put("rejeitadas", rejeitadas);
        out.put("pendentesAnteriores", atrasadas);
        out.put("cidadaosSemMicroarea", semMicroarea);
        return out;
    }

    private String ine(Long idEquipe) {
        if (idEquipe == null) {
            return null;
        }
        Map<String, Object> e = db.one("SELECT ine FROM aps_equipe WHERE id = :id", p("id", idEquipe));
        return e == null ? null : (String) e.get("ine");
    }

    private static Map<String, Object> withParam(Map<String, Object> base, String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>(base);
        m.put(k, v);
        return m;
    }
}

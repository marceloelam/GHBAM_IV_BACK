package br.com.iv.aps;

import static br.com.iv.aps.ApsCidadaoService.intOrNull;
import static br.com.iv.aps.ApsCidadaoService.isoDate;
import static br.com.iv.aps.ApsConfiguracaoService.num;
import static br.com.iv.aps.ApsConfiguracaoService.str;
import static br.com.iv.aps.ApsDb.p;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.iv.aps.ApsContextoService.Lotacao;
import br.com.iv.aps.ApsLediBuilder.Estilo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Sala de vacina (manual PEC 6.3 / e-SUS Vacinacao): aplicacao, transcricao de
 * caderneta, aprazamento, situacao vacinal, lotes e exclusao de dose com
 * registro de cancelamento LEDI (uuidFichaCancelada). Gera o modelo 14.
 */
@ApplicationScoped
public class ApsVacinacaoService {

    /** Calendario Nacional (PNI) da crianca/adolescente - rotina. {imuno, dose, idadeMeses, idadeMaxMeses, rotulo}. */
    public static final List<Map<String, Object>> CALENDARIO = List.of(
            cal(15, 9, 0, 59, "BCG - dose unica (ao nascer)"),
            cal(9, 8, 0, 1, "Hepatite B - dose ao nascer"),
            cal(42, 1, 2, 83, "Penta - 1a dose"),
            cal(22, 1, 2, 59, "VIP - 1a dose"),
            cal(26, 1, 2, 59, "Pneumo 10 - 1a dose"),
            cal(45, 1, 2, 3, "Rotavirus - 1a dose"),
            cal(41, 1, 3, 59, "Meningo C - 1a dose"),
            cal(42, 2, 4, 83, "Penta - 2a dose"),
            cal(22, 2, 4, 59, "VIP - 2a dose"),
            cal(26, 2, 4, 59, "Pneumo 10 - 2a dose"),
            cal(45, 2, 4, 7, "Rotavirus - 2a dose"),
            cal(41, 2, 5, 59, "Meningo C - 2a dose"),
            cal(42, 3, 6, 83, "Penta - 3a dose"),
            cal(22, 3, 6, 59, "VIP - 3a dose"),
            cal(102, 1, 6, 59, "COVID-19 pediatrica - 1a dose"),
            cal(102, 2, 7, 59, "COVID-19 pediatrica - 2a dose"),
            cal(14, 8, 9, 59, "Febre amarela - dose"),
            cal(102, 3, 9, 59, "COVID-19 pediatrica - 3a dose"),
            cal(24, 1, 12, 719, "Triplice viral - 1a dose"),
            cal(26, 38, 12, 59, "Pneumo 10 - reforco"),
            cal(41, 38, 12, 59, "Meningo C - reforco"),
            cal(22, 6, 15, 59, "VIP - reforco"),
            cal(46, 6, 15, 83, "DTP - 1o reforco"),
            cal(56, 9, 15, 83, "Tetraviral - dose unica"),
            cal(55, 9, 15, 59, "Hepatite A - dose unica"),
            cal(46, 7, 48, 83, "DTP - 2o reforco"),
            cal(14, 38, 48, 59, "Febre amarela - reforco"),
            cal(34, 2, 48, 83, "Varicela - 2a dose"),
            cal(67, 9, 108, 179, "HPV - dose unica (9 a 14 anos)"),
            cal(74, 9, 132, 179, "Meningo ACWY - dose unica (11 a 14 anos)"));

    /** Equivalencias: registros destes imunobiologicos contam para o item do calendario. */
    private static final Map<Integer, Set<Integer>> EQUIVALENTES = Map.of(
            42, Set.of(42, 43, 29),
            22, Set.of(22, 43, 29, 58),
            56, Set.of(56, 73),
            67, Set.of(67, 93, 60),
            24, Set.of(24, 56, 73),
            46, Set.of(46, 39, 47, 58, 111));

    @Inject
    ApsDb db;

    @Inject
    ApsFichaService fichas;

    @Inject
    ApsReferenciaService referencias;

    @Inject
    ApsCidadaoService cidadaos;

    private static Map<String, Object> cal(int imuno, int dose, int idade, int idadeMax, String rotulo) {
        return Map.of("imunobiologico", imuno, "dose", dose, "idadeMeses", idade, "idadeMaxMeses", idadeMax, "rotulo", rotulo);
    }

    public Map<String, Object> caderneta(Long idCidadao) {
        cidadaos.completarDoHospitalSemFalhar(idCidadao);
        Map<String, Object> c = db.one("SELECT id, nome, nome_social, nome_mae, data_nascimento, sexo, cpf, cns FROM aps_cidadao WHERE id = :id",
                p("id", idCidadao));
        if (c == null) {
            throw new IllegalArgumentException("Cidadao nao encontrado.");
        }
        List<Map<String, Object>> registros = db.list("""
                SELECT v.*, f.status AS ficha_status, f.uuid AS ficha_uuid
                FROM aps_vacinacao v LEFT JOIN aps_ficha f ON f.id = v.id_ficha
                WHERE v.id_cidadao = :id
                ORDER BY COALESCE(v.data_aplicacao, v.data_aprazamento) DESC, v.id DESC
                """, p("id", idCidadao));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cidadao", c);
        out.put("registros", registros);
        out.put("calendario", situacao(c, registros));
        return out;
    }

    List<Map<String, Object>> situacao(Map<String, Object> c, List<Map<String, Object>> registros) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (c.get("data_nascimento") == null) {
            return out;
        }
        LocalDate nasc = LocalDate.parse((String) c.get("data_nascimento"));
        int idadeMeses = ApsValidacao.idadeMeses(nasc, null);
        for (Map<String, Object> item : CALENDARIO) {
            int imuno = (Integer) item.get("imunobiologico");
            int dose = (Integer) item.get("dose");
            int idade = (Integer) item.get("idadeMeses");
            int idadeMax = (Integer) item.get("idadeMaxMeses");
            Set<Integer> equivalentes = EQUIVALENTES.getOrDefault(imuno, Set.of(imuno));
            Map<String, Object> aplicado = null;
            Map<String, Object> aprazado = null;
            for (Map<String, Object> r : registros) {
                if (Boolean.TRUE.equals(r.get("excluido"))) {
                    continue;
                }
                Integer ri = intOrNull(r.get("imunobiologico"));
                Integer rd = intOrNull(r.get("dose"));
                boolean mesmo = ri != null && rd != null && equivalentes.contains(ri) && rd == dose;
                if (!mesmo) {
                    continue;
                }
                if ("APRAZAMENTO".equals(r.get("tipo_registro"))) {
                    aprazado = r;
                } else {
                    aplicado = r;
                    break;
                }
            }
            String status;
            if (aplicado != null) {
                status = "APLICADA";
            } else if (idadeMeses > idadeMax) {
                status = "NAO_APLICADA_FORA_FAIXA";
            } else if (idadeMeses >= idade + 1) {
                status = "ATRASADA";
            } else if (idadeMeses >= idade) {
                status = "NO_PRAZO";
            } else {
                status = "FUTURA";
            }
            Map<String, Object> m = new LinkedHashMap<>(item);
            m.put("status", status);
            m.put("dataPrevista", nasc.plusMonths(idade).toString());
            if (aplicado != null) {
                m.put("dataAplicacao", aplicado.get("data_aplicacao"));
                m.put("idRegistro", aplicado.get("id"));
            }
            if (aprazado != null) {
                m.put("dataAprazamento", aprazado.get("data_aprazamento"));
            }
            out.add(m);
        }
        return out;
    }

    /**
     * body: { idAtendimento?, localAtendimento?, gestante, puerpera, viajante, comunicanteHanseniase, condicaoMaternal?,
     *         vacinas:[{ tipoRegistro: APLICACAO|TRANSCRICAO, imunobiologico, estrategia, dose, lote, fabricante,
     *                    coRndsFabricante, viaAdministracao, localAplicacao, grupoAtendimento, dataAplicacao,
     *                    aplicadoExterior, dataAprazamento, observacao }] }
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> registrar(Long idCidadao, Map<String, Object> body, Lotacao lot) {
        Map<String, Object> cid = db.one("SELECT * FROM aps_cidadao WHERE id = :id", p("id", idCidadao));
        if (cid == null) {
            throw new IllegalArgumentException("Cidadao nao encontrado.");
        }
        List<Map<String, Object>> vacinas = (List<Map<String, Object>>) body.getOrDefault("vacinas", List.of());
        if (vacinas.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma vacina.");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> v : vacinas) {
            rows.add(validarVacina(v, lot));
        }
        boolean gestante = Boolean.TRUE.equals(body.get("gestante"));
        boolean puerpera = Boolean.TRUE.equals(body.get("puerpera"));
        if ((gestante || puerpera) && Integer.valueOf(0).equals(cid.get("sexo"))) {
            throw new IllegalArgumentException("Condicao gestante/puerpera nao se aplica ao sexo masculino.");
        }
        LocalDateTime agora = LocalDateTime.now().withNano(0);
        Map<String, Object> child = new LinkedHashMap<>(ApsLediBuilder.identificacao(cid, Estilo.VACINA));
        child.put("turno", ApsLediBuilder.turno(agora));
        child.put("localAtendimento", body.getOrDefault("localAtendimento", 1));
        child.put("viajante", Boolean.TRUE.equals(body.get("viajante")));
        child.put("comunicanteHanseniase", Boolean.TRUE.equals(body.get("comunicanteHanseniase")));
        // LEDI 8.7 (dic-fv #13): gestante/puerpera foram descontinuados na 7.2.2 - o PEC 5.6 recusa a ficha
        // com eles (visto no PEC oficial de treinamento em 18/11/2026). No lugar: condicaoMaternal, proibido
        // para sexo masculino e obrigatorio nos demais (1 nenhuma, 2 gestante, 3 puerpera).
        if (!Integer.valueOf(0).equals(cid.get("sexo"))) {
            Object informada = body.get("condicaoMaternal");
            child.put("condicaoMaternal", informada != null ? informada : gestante ? 2 : puerpera ? 3 : 1);
        }
        child.put("dataHoraInicialAtendimento", agora.minusMinutes(10).toString());
        child.put("dataHoraFinalAtendimento", agora.toString());
        child.put("vacinas", rows.stream().map(r -> (Map<String, Object>) r.get("ledi")).toList());
        Map<String, Object> master = new LinkedHashMap<>();
        master.put("vacinacoes", List.of(ApsLediBuilder.limpar(child)));
        Map<String, Object> ficha = fichas.registrar(new ApsFichaService.Registro(14, master, lot, LocalDate.now(), idCidadao,
                null, num(body.get("idAtendimento")), "VACINACAO", null, null));
        Long idFicha = ((Number) ficha.get("id")).longValue();
        for (Map<String, Object> r : rows) {
            Map<String, Object> v = (Map<String, Object>) r.get("entrada");
            boolean transcricao = "TRANSCRICAO".equals(str(v.get("tipoRegistro")));
            db.insert("""
                    INSERT INTO aps_vacinacao (id_cidadao, id_atendimento, unidade, tipo_registro, data_aplicacao, imunobiologico,
                        estrategia, dose, lote, fabricante, co_rnds_fabricante, via_administracao, local_aplicacao,
                        grupo_atendimento, aplicado_exterior, gestante, puerpera, viajante, comunicante_hanseniase, matricula,
                        profissional, cns_profissional, cbo, observacao, id_ficha)
                    VALUES (:c, :at, :u, :tipo, CAST(:data AS date), :imuno, :estr, :dose, :lote, :fab, :rnds, :via, :local,
                        :grupo, :ext, :gest, :puer, :viaj, :hans, :m, :nome, :cns, :cbo, :obs, :ficha)
                    RETURNING id
                    """, p("c", idCidadao, "at", num(body.get("idAtendimento")), "u", lot.unidade(),
                    "tipo", transcricao ? "TRANSCRICAO" : "APLICACAO",
                    "data", transcricao ? isoDate(v.get("dataAplicacao")) : LocalDate.now().toString(),
                    "imuno", intOrNull(v.get("imunobiologico")), "estr", intOrNull(v.get("estrategia")), "dose", intOrNull(v.get("dose")),
                    "lote", str(v.get("lote")), "fab", str(v.get("fabricante")), "rnds", str(v.get("coRndsFabricante")),
                    "via", intOrNull(v.get("viaAdministracao")), "local", intOrNull(v.get("localAplicacao")),
                    "grupo", intOrNull(v.get("grupoAtendimento")), "ext", Boolean.TRUE.equals(v.get("aplicadoExterior")),
                    "gest", gestante, "puer", puerpera, "viaj", Boolean.TRUE.equals(body.get("viajante")),
                    "hans", Boolean.TRUE.equals(body.get("comunicanteHanseniase")), "m", lot.matricula(), "nome", lot.nome(),
                    "cns", lot.cns(), "cbo", lot.cbo(), "obs", str(v.get("observacao")), "ficha", idFicha));
            String apraz = isoDate(v.get("dataAprazamento"));
            if (apraz != null) {
                aprazar(idCidadao, p("imunobiologico", v.get("imunobiologicoAprazado") != null ? v.get("imunobiologicoAprazado") : v.get("imunobiologico"),
                        "dose", v.get("doseAprazada"), "data", apraz), lot);
            }
        }
        db.exec("""
                DELETE FROM aps_vacinacao a
                WHERE a.id_cidadao = :c AND a.tipo_registro = 'APRAZAMENTO' AND EXISTS (
                    SELECT 1 FROM aps_vacinacao v WHERE v.id_cidadao = a.id_cidadao AND v.tipo_registro <> 'APRAZAMENTO'
                      AND NOT v.excluido AND v.imunobiologico = a.imunobiologico AND v.dose = a.dose)
                """, p("c", idCidadao));
        return caderneta(idCidadao);
    }

    private Map<String, Object> validarVacina(Map<String, Object> v, Lotacao lot) {
        String tipo = str(v.get("tipoRegistro")) == null ? "APLICACAO" : str(v.get("tipoRegistro"));
        Integer imuno = intOrNull(v.get("imunobiologico"));
        Integer estrategia = intOrNull(v.get("estrategia"));
        Integer dose = intOrNull(v.get("dose"));
        if (imuno == null || dose == null) {
            throw new IllegalArgumentException("Informe imunobiologico e dose.");
        }
        Map<String, Object> ledi = new LinkedHashMap<>();
        ledi.put("imunobiologico", imuno);
        ledi.put("dose", dose);
        if ("TRANSCRICAO".equals(tipo)) {
            String data = isoDate(v.get("dataAplicacao"));
            if (data == null || LocalDate.parse(data).isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Transcricao de caderneta exige data de aplicacao anterior ou igual a hoje.");
            }
            ledi.put("stRegistroAnterior", true);
            ledi.put("dataRegistroAnterior", data);
            if (estrategia != null) {
                ledi.put("estrategiaVacinacao", estrategia);
            }
            if (Boolean.TRUE.equals(v.get("aplicadoExterior"))) {
                ledi.put("stAplicadoExterior", true);
            }
        } else {
            if (estrategia == null) {
                throw new IllegalArgumentException("Informe a estrategia de vacinacao.");
            }
            Integer rnds = estrategiaRnds(estrategia);
            if (rnds != null && !referencias.regraVacinalValida(imuno, rnds, dose)) {
                throw new IllegalArgumentException("Combinacao imunobiologico x estrategia x dose nao permitida pelas regras "
                        + "vacinais do LEDI (imuno " + imuno + ", estrategia " + estrategia + ", dose " + dose + "). "
                        + "Se a dose foi aplicada em outro servico, use Transcricao de caderneta.");
            }
            String lote = str(v.get("lote"));
            if (lote == null || str(v.get("fabricante")) == null) {
                throw new IllegalArgumentException("Lote e fabricante sao obrigatorios na aplicacao.");
            }
            Map<String, Object> l = db.one("""
                    SELECT validade, ativo FROM aps_lote_imunobiologico
                    WHERE UPPER(unidade) = UPPER(:u) AND imunobiologico = :i AND UPPER(lote) = UPPER(:l)
                    """, p("u", lot.unidade(), "i", imuno, "l", lote));
            if (l != null && (LocalDate.parse((String) l.get("validade")).isBefore(LocalDate.now()) || !Boolean.TRUE.equals(l.get("ativo")))) {
                throw new IllegalArgumentException("Lote " + lote + " vencido ou inativo - nao aplicar.");
            }
            ledi.put("estrategiaVacinacao", estrategia);
            ledi.put("lote", lote);
            ledi.put("fabricante", str(v.get("fabricante")));
            putIf(ledi, "coRndsFabricante", str(v.get("coRndsFabricante")));
            putIf(ledi, "viaAdministracao", intOrNull(v.get("viaAdministracao")));
            putIf(ledi, "localAplicacao", intOrNull(v.get("localAplicacao")));
            putIf(ledi, "grupoAtendimento", intOrNull(v.get("grupoAtendimento")));
            ledi.put("stRegistroAnterior", false);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("ledi", ledi);
        out.put("entrada", v);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Integer estrategiaRnds(Integer codigoEsus) {
        Map<String, Object> enums = (Map<String, Object>) referencias.referencias().get("enums");
        Map<String, Object> e = (Map<String, Object>) enums.get("estrategiavacinacao");
        for (Map<String, Object> item : (List<Map<String, Object>>) e.get("itens")) {
            if (String.valueOf(codigoEsus).equals(String.valueOf(item.get("codigo")))) {
                Map<String, Object> extra = (Map<String, Object>) item.get("extra");
                Object rnds = extra == null ? null : extra.get("Código RNDS");
                return rnds == null ? null : Integer.valueOf(String.valueOf(rnds));
            }
        }
        return null;
    }

    @Transactional
    public Map<String, Object> aprazar(Long idCidadao, Map<String, Object> body, Lotacao lot) {
        Integer imuno = intOrNull(body.get("imunobiologico"));
        String data = isoDate(body.get("data"));
        if (imuno == null || data == null) {
            throw new IllegalArgumentException("Informe imunobiologico e data do aprazamento.");
        }
        if (!LocalDate.parse(data).isAfter(LocalDate.now().minusDays(1))) {
            throw new IllegalArgumentException("A data de aprazamento deve ser hoje ou futura.");
        }
        db.insert("""
                INSERT INTO aps_vacinacao (id_cidadao, unidade, tipo_registro, data_aprazamento, imunobiologico, dose, matricula, profissional)
                VALUES (:c, :u, 'APRAZAMENTO', CAST(:d AS date), :i, :dose, :m, :nome)
                RETURNING id
                """, p("c", idCidadao, "u", lot.unidade(), "d", data, "i", imuno, "dose", intOrNull(body.get("dose")),
                "m", lot.matricula(), "nome", lot.nome()));
        return caderneta(idCidadao);
    }

    /** Exclusao de dose (manual 6.3.6). Se o registro ja foi transmitido gera registro com uuidFichaCancelada. */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> excluir(Long idVacinacao, String motivo, Lotacao lot) {
        Map<String, Object> v = db.one("SELECT * FROM aps_vacinacao WHERE id = :id", p("id", idVacinacao));
        if (v == null) {
            throw new IllegalArgumentException("Registro de vacina nao encontrado.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Informe o motivo da exclusao.");
        }
        Long idCidadao = ((Number) v.get("id_cidadao")).longValue();
        db.exec("UPDATE aps_vacinacao SET excluido = TRUE, motivo_exclusao = :m WHERE id = :id",
                p("m", motivo + " (" + lot.matricula() + ")", "id", idVacinacao));
        if (v.get("id_ficha") == null) {
            return caderneta(idCidadao);
        }
        Map<String, Object> ficha = fichas.detalhe(((Number) v.get("id_ficha")).longValue());
        Map<String, Object> dados = new LinkedHashMap<>((Map<String, Object>) ficha.get("dados"));
        List<Map<String, Object>> restantes = db.list("""
                SELECT * FROM aps_vacinacao WHERE id_ficha = :f AND NOT excluido
                """, p("f", ficha.get("id")));
        List<Map<String, Object>> vacinacoes = (List<Map<String, Object>>) dados.get("vacinacoes");
        Map<String, Object> child = new LinkedHashMap<>(vacinacoes.get(0));
        List<Map<String, Object>> linhas = new ArrayList<>();
        for (Map<String, Object> linha : (List<Map<String, Object>>) child.get("vacinas")) {
            boolean mantem = restantes.stream().anyMatch(r -> String.valueOf(r.get("imunobiologico")).equals(String.valueOf(linha.get("imunobiologico")))
                    && String.valueOf(r.get("dose")).equals(String.valueOf(linha.get("dose"))));
            if (mantem) {
                linhas.add(linha);
            }
        }
        child.put("vacinas", linhas);
        Map<String, Object> novo = new LinkedHashMap<>();
        novo.put("vacinacoes", linhas.isEmpty() ? List.of() : List.of(child));
        if ("ENVIADA".equals(ficha.get("status"))) {
            novo.put("uuidFichaCancelada", ficha.get("uuid"));
        } else {
            fichas.cancelar(((Number) ficha.get("id")).longValue(), "Exclusao de dose: " + motivo, lot.matricula());
            if (linhas.isEmpty()) {
                return caderneta(idCidadao);
            }
        }
        Map<String, Object> nova = fichas.registrar(new ApsFichaService.Registro(14, novo, lot, LocalDate.now(), idCidadao, null,
                null, "VACINACAO_RETIFICACAO", null, null));
        db.exec("UPDATE aps_vacinacao SET id_ficha = :n WHERE id_ficha = :f AND NOT excluido",
                p("n", nova.get("id"), "f", ficha.get("id")));
        return caderneta(idCidadao);
    }

    public List<Map<String, Object>> lotes(String unidade, boolean somenteValidos) {
        return db.list("""
                SELECT l.*, (l.validade < CURRENT_DATE) AS vencido,
                       (SELECT COUNT(*) FROM aps_vacinacao v WHERE UPPER(v.lote) = UPPER(l.lote) AND v.imunobiologico = l.imunobiologico
                          AND NOT v.excluido AND UPPER(v.unidade) = UPPER(l.unidade)) AS doses_aplicadas
                FROM aps_lote_imunobiologico l
                WHERE UPPER(l.unidade) = UPPER(:u)
                  AND (NOT CAST(:validos AS boolean) OR (l.ativo AND l.validade >= CURRENT_DATE))
                ORDER BY l.imunobiologico, l.validade
                """, p("u", unidade, "validos", somenteValidos));
    }

    @Transactional
    public Map<String, Object> salvarLote(Map<String, Object> body, String unidade) {
        Integer imuno = intOrNull(body.get("imunobiologico"));
        String lote = str(body.get("lote"));
        String validade = isoDate(body.get("validade"));
        if (imuno == null || lote == null || validade == null) {
            throw new IllegalArgumentException("Informe imunobiologico, lote e validade.");
        }
        Map<String, Object> params = p("u", unidade, "i", imuno, "l", lote.toUpperCase(), "fab", str(body.get("fabricante")),
                "rnds", str(body.get("coRndsFabricante")), "val", validade,
                "ativo", body.get("ativo") == null || Boolean.TRUE.equals(body.get("ativo")));
        Long id = num(body.get("id"));
        if (id == null) {
            id = db.insert("""
                    INSERT INTO aps_lote_imunobiologico (unidade, imunobiologico, lote, fabricante, co_rnds_fabricante, validade, ativo)
                    VALUES (:u, :i, :l, :fab, :rnds, CAST(:val AS date), :ativo)
                    ON CONFLICT (unidade, imunobiologico, lote) DO UPDATE SET fabricante = EXCLUDED.fabricante,
                        co_rnds_fabricante = EXCLUDED.co_rnds_fabricante, validade = EXCLUDED.validade, ativo = EXCLUDED.ativo
                    RETURNING id
                    """, params);
        } else {
            params.put("id", id);
            db.exec("""
                    UPDATE aps_lote_imunobiologico SET imunobiologico = :i, lote = :l, fabricante = :fab, co_rnds_fabricante = :rnds,
                        validade = CAST(:val AS date), ativo = :ativo
                    WHERE id = :id AND UPPER(unidade) = UPPER(:u)
                    """, params);
        }
        return db.one("SELECT * FROM aps_lote_imunobiologico WHERE id = :id", p("id", id));
    }

    /** Busca ativa de vacinacao (manual 11.1): criancas/adolescentes com doses atrasadas no territorio. */
    public List<Map<String, Object>> buscaAtiva(String unidade, Long idEquipe, String microarea) {
        List<Map<String, Object>> cidadaos = db.list("""
                SELECT c.id, c.nome, c.data_nascimento, c.sexo, c.cpf, c.cns, c.microarea, c.telefone, e.nome AS equipe_nome,
                       d.logradouro, d.numero, d.bairro
                FROM aps_cidadao c
                LEFT JOIN aps_equipe e ON e.id = c.id_equipe
                LEFT JOIN aps_familia f ON f.id = c.id_familia
                LEFT JOIN aps_domicilio d ON d.id = f.id_domicilio
                WHERE c.ativo AND c.data_nascimento >= CURRENT_DATE - INTERVAL '15 years'
                  AND UPPER(c.unidade) = UPPER(:u)
                  AND (CAST(:eq AS bigint) IS NULL OR c.id_equipe = CAST(:eq AS bigint))
                  AND (CAST(:micro AS varchar) IS NULL OR c.microarea = CAST(:micro AS varchar))
                ORDER BY c.microarea, c.nome
                """, p("u", unidade, "eq", idEquipe, "micro", ApsFichaService.vazioParaNulo(microarea)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : cidadaos) {
            List<Map<String, Object>> regs = db.list("SELECT * FROM aps_vacinacao WHERE id_cidadao = :id", p("id", c.get("id")));
            List<Map<String, Object>> atrasadas = situacao(c, regs).stream().filter(s -> "ATRASADA".equals(s.get("status"))).toList();
            if (!atrasadas.isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>(c);
                m.put("atrasadas", atrasadas);
                m.put("totalAtrasadas", atrasadas.size());
                out.add(m);
            }
        }
        return out;
    }

    private static void putIf(Map<String, Object> m, String k, Object v) {
        if (v != null) {
            m.put(k, v);
        }
    }
}

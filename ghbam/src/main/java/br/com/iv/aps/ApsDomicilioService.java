package br.com.iv.aps;

import static br.com.iv.aps.ApsCidadaoService.intOrNull;
import static br.com.iv.aps.ApsCidadaoService.isoDate;
import static br.com.iv.aps.ApsConfiguracaoService.num;
import static br.com.iv.aps.ApsConfiguracaoService.str;
import static br.com.iv.aps.ApsDb.p;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.aps.ApsContextoService.Lotacao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Cadastro Domiciliar e Territorial (LEDI tipo 3) e familias. Base do
 * territorio: imovel -> familias -> cidadaos (aps_cidadao.id_familia).
 */
@ApplicationScoped
public class ApsDomicilioService {

    @Inject
    ApsDb db;

    @Inject
    ApsFichaService fichas;

    public List<Map<String, Object>> listar(String unidade, Long idEquipe, String microarea, String termo, int limite) {
        String t = termo == null ? "" : termo.trim();
        return db.list("""
                SELECT d.id, d.unidade, d.id_equipe, e.nome AS equipe_nome, d.microarea, d.fora_area, d.tipo_imovel,
                       d.cep, d.logradouro, d.numero, d.sem_numero, d.complemento, d.bairro, d.ponto_referencia,
                       d.telefone_contato, d.latitude, d.longitude, d.recusa, d.ativo, d.atualizado_em,
                       (SELECT COUNT(*) FROM aps_familia f WHERE f.id_domicilio = d.id AND f.ativo AND NOT f.mudou) AS familias,
                       (SELECT COUNT(*) FROM aps_cidadao c JOIN aps_familia f ON f.id = c.id_familia
                          WHERE f.id_domicilio = d.id AND c.ativo) AS moradores
                FROM aps_domicilio d
                LEFT JOIN aps_equipe e ON e.id = d.id_equipe
                WHERE d.ativo
                  AND (CAST(:unidade AS varchar) IS NULL OR UPPER(d.unidade) = UPPER(CAST(:unidade AS varchar)))
                  AND (CAST(:idEquipe AS bigint) IS NULL OR d.id_equipe = CAST(:idEquipe AS bigint))
                  AND (CAST(:microarea AS varchar) IS NULL OR d.microarea = CAST(:microarea AS varchar))
                  AND (CAST(:t AS varchar) = '' OR UPPER(COALESCE(d.logradouro, '') || ' ' || COALESCE(d.numero, '') || ' '
                        || COALESCE(d.bairro, '') || ' ' || COALESCE(d.cep, '')) LIKE UPPER(CAST(:like AS varchar)))
                ORDER BY d.microarea, d.logradouro, d.numero
                LIMIT :limite
                """, p("unidade", ApsFichaService.vazioParaNulo(unidade), "idEquipe", idEquipe,
                "microarea", ApsFichaService.vazioParaNulo(microarea), "t", t, "like", "%" + t + "%",
                "limite", Math.max(1, Math.min(limite, 1000))));
    }

    public Map<String, Object> obter(Long id) {
        Map<String, Object> d = db.one("""
                SELECT d.*, e.nome AS equipe_nome, e.ine FROM aps_domicilio d LEFT JOIN aps_equipe e ON e.id = d.id_equipe
                WHERE d.id = :id
                """, p("id", id));
        if (d == null) {
            return null;
        }
        List<Map<String, Object>> familias = db.list("""
                SELECT f.*, c.nome AS responsavel_nome
                FROM aps_familia f LEFT JOIN aps_cidadao c ON c.id = f.id_cidadao_responsavel
                WHERE f.id_domicilio = :id ORDER BY f.mudou, f.id
                """, p("id", id));
        for (Map<String, Object> f : familias) {
            f.put("membros", db.list("""
                    SELECT id, nome, cpf, cns, data_nascimento, sexo, responsavel_familiar, ativo
                    FROM aps_cidadao WHERE id_familia = :f ORDER BY responsavel_familiar DESC, data_nascimento
                    """, p("f", f.get("id"))));
        }
        d.put("familias", familias);
        d.put("visitas", db.list("""
                SELECT id, data_atendimento, profissional_nome, status, dados
                FROM aps_ficha WHERE id_domicilio = :id AND tipo = 8 AND status <> 'CANCELADA'
                ORDER BY data_atendimento DESC LIMIT 20
                """, p("id", id)));
        return d;
    }

    /**
     * body: { id?, idEquipe?, data?, domicilio:{...endereco}, fcd:{condicaoMoradia, animaisNoDomicilio,
     *         stAnimaisNoDomicilio, quantosAnimaisNoDomicilio, instituicaoPermanencia, statusTermoRecusa, tipoEndereco},
     *         familias:[{id?, idCidadaoResponsavel?, cpfResponsavel, cnsResponsavel, dataNascimentoResponsavel,
     *                    numeroProntuario, numeroMembros, rendaFamiliar, resideDesde, mudou}] }
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> salvar(Map<String, Object> body, Lotacao lot) {
        Map<String, Object> dom = (Map<String, Object>) body.getOrDefault("domicilio", Map.of());
        Map<String, Object> fcdIn = (Map<String, Object>) body.getOrDefault("fcd", Map.of());
        List<Map<String, Object>> familiasIn = (List<Map<String, Object>>) body.getOrDefault("familias", List.of());
        boolean recusa = Boolean.TRUE.equals(fcdIn.get("statusTermoRecusa"));
        Integer tipoImovel = intOrNull(dom.get("tipoImovel"));
        if (tipoImovel == null) {
            throw new IllegalArgumentException("Informe o tipo de imovel.");
        }
        boolean foraArea = Boolean.TRUE.equals(dom.get("foraArea"));
        String microarea = str(dom.get("microarea"));
        if (!foraArea && microarea == null) {
            throw new IllegalArgumentException("Informe a microarea ou marque 'fora de area'.");
        }
        String cep = ApsValidacao.digitos(str(dom.get("cep")));
        if (!recusa && (cep == null || cep.length() != 8)) {
            throw new IllegalArgumentException("CEP com 8 digitos e obrigatorio.");
        }
        String ibge = ApsValidacao.digitos(str(dom.get("codIbge")));
        if (ibge == null) {
            ibge = lot.codIbge();
        }
        Long idEquipe = num(body.get("idEquipe"));
        if (idEquipe == null) {
            idEquipe = lot.idEquipe();
        }
        Map<String, Object> params = p("unidade", lot.unidade(), "idEquipe", idEquipe, "microarea", foraArea ? null : microarea,
                "fora", foraArea, "tipoImovel", tipoImovel, "cep", cep, "ibge", ibge, "uf", str(dom.get("ufDne")),
                "tipoLog", str(dom.get("tipoLogradouroDne")), "log", str(dom.get("logradouro")), "num", str(dom.get("numero")),
                "semNum", Boolean.TRUE.equals(dom.get("semNumero")), "comp", str(dom.get("complemento")),
                "bairro", str(dom.get("bairro")), "ref", str(dom.get("pontoReferencia")),
                "telRes", ApsValidacao.digitos(str(dom.get("telefoneResidencia"))),
                "telCont", ApsValidacao.digitos(str(dom.get("telefoneContato"))),
                "lat", dbl(dom.get("latitude")), "lon", dbl(dom.get("longitude")), "dados", new ApsDb.Json(fcdIn),
                "recusa", recusa, "m", lot.matricula());
        Long id = num(body.get("id"));
        String uuidOrigem = null;
        if (id == null) {
            id = db.insert("""
                    INSERT INTO aps_domicilio (unidade, id_equipe, microarea, fora_area, tipo_imovel, cep, cod_ibge, uf_dne,
                        tipo_logradouro_dne, logradouro, numero, sem_numero, complemento, bairro, ponto_referencia,
                        telefone_residencia, telefone_contato, latitude, longitude, dados, recusa, criado_por, atualizado_por)
                    VALUES (:unidade, :idEquipe, :microarea, :fora, :tipoImovel, :cep, :ibge, :uf, :tipoLog, :log, :num, :semNum,
                        :comp, :bairro, :ref, :telRes, :telCont, :lat, :lon, CAST(:dados AS jsonb), :recusa, :m, :m)
                    RETURNING id
                    """, params);
        } else {
            Map<String, Object> atual = db.one("SELECT uuid_ficha_origem FROM aps_domicilio WHERE id = :id", p("id", id));
            if (atual == null) {
                throw new IllegalArgumentException("Domicilio nao encontrado.");
            }
            uuidOrigem = (String) atual.get("uuid_ficha_origem");
            params.put("id", id);
            db.exec("""
                    UPDATE aps_domicilio SET unidade = :unidade, id_equipe = :idEquipe, microarea = :microarea, fora_area = :fora,
                        tipo_imovel = :tipoImovel, cep = :cep, cod_ibge = :ibge, uf_dne = :uf, tipo_logradouro_dne = :tipoLog,
                        logradouro = :log, numero = :num, sem_numero = :semNum, complemento = :comp, bairro = :bairro,
                        ponto_referencia = :ref, telefone_residencia = :telRes, telefone_contato = :telCont, latitude = :lat,
                        longitude = :lon, dados = CAST(:dados AS jsonb), recusa = :recusa, atualizado_em = CURRENT_TIMESTAMP,
                        atualizado_por = :m
                    WHERE id = :id
                    """, params);
        }
        List<Map<String, Object>> familiasLedi = new ArrayList<>();
        for (Map<String, Object> f : familiasIn) {
            String cpfResp = ApsValidacao.digitos(str(f.get("cpfResponsavel")));
            String cnsResp = ApsValidacao.digitos(str(f.get("cnsResponsavel")));
            Long idResp = num(f.get("idCidadaoResponsavel"));
            if (idResp != null && cpfResp == null && cnsResp == null) {
                Map<String, Object> c = db.one("SELECT cpf, cns, data_nascimento FROM aps_cidadao WHERE id = :id", p("id", idResp));
                if (c != null) {
                    cpfResp = (String) c.get("cpf");
                    cnsResp = (String) c.get("cns");
                    if (f.get("dataNascimentoResponsavel") == null) {
                        f.put("dataNascimentoResponsavel", c.get("data_nascimento"));
                    }
                }
            }
            if (cpfResp == null && cnsResp == null) {
                throw new IllegalArgumentException("Cada familia precisa do CPF ou CNS do responsavel familiar.");
            }
            if (cpfResp != null && !ApsValidacao.cpfValido(cpfResp)) {
                throw new IllegalArgumentException("CPF do responsavel familiar invalido. Corrija o CPF ou informe o CNS.");
            }
            if (cpfResp == null && !ApsValidacao.cnsValido(cnsResp)) {
                throw new IllegalArgumentException("CNS do responsavel familiar invalido.");
            }
            Map<String, Object> fp = p("dom", id, "idResp", idResp, "cpf", cpfResp, "cns", cnsResp,
                    "nasc", isoDate(f.get("dataNascimentoResponsavel")), "pront", str(f.get("numeroProntuario")),
                    "membros", intOrNull(f.get("numeroMembros")), "renda", intOrNull(f.get("rendaFamiliar")),
                    "desde", isoDate(f.get("resideDesde")), "mudou", Boolean.TRUE.equals(f.get("mudou")));
            Long idFam = num(f.get("id"));
            if (idFam == null) {
                idFam = db.insert("""
                        INSERT INTO aps_familia (id_domicilio, id_cidadao_responsavel, cpf_responsavel, cns_responsavel,
                            data_nascimento_responsavel, numero_prontuario, numero_membros, renda_familiar, reside_desde, mudou)
                        VALUES (:dom, :idResp, :cpf, :cns, CAST(:nasc AS date), :pront, :membros, :renda, CAST(:desde AS date), :mudou)
                        RETURNING id
                        """, fp);
            } else {
                fp.put("id", idFam);
                db.exec("""
                        UPDATE aps_familia SET id_cidadao_responsavel = :idResp, cpf_responsavel = :cpf, cns_responsavel = :cns,
                            data_nascimento_responsavel = CAST(:nasc AS date), numero_prontuario = :pront, numero_membros = :membros,
                            renda_familiar = :renda, reside_desde = CAST(:desde AS date), mudou = :mudou
                        WHERE id = :id AND id_domicilio = :dom
                        """, fp);
            }
            if (idResp != null) {
                db.exec("UPDATE aps_cidadao SET id_familia = :f, responsavel_familiar = TRUE WHERE id = :c",
                        p("f", idFam, "c", idResp));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            putIf(row, "cpfResponsavel", cpfResp);
            putIf(row, "numeroCnsResponsavel", cpfResp == null ? cnsResp : null);
            putIf(row, "dataNascimentoResponsavel", isoDate(f.get("dataNascimentoResponsavel")));
            putIf(row, "numeroProntuario", str(f.get("numeroProntuario")));
            putIf(row, "numeroMembrosFamilia", intOrNull(f.get("numeroMembros")));
            putIf(row, "rendaFamiliar", intOrNull(f.get("rendaFamiliar")));
            putIf(row, "resideDesde", isoDate(f.get("resideDesde")));
            row.put("stMudanca", Boolean.TRUE.equals(f.get("mudou")));
            familiasLedi.add(row);
        }

        Map<String, Object> fcd = new LinkedHashMap<>(fcdIn);
        Map<String, Object> end = new LinkedHashMap<>();
        putIf(end, "bairro", str(dom.get("bairro")));
        putIf(end, "cep", cep);
        putIf(end, "codigoIbgeMunicipio", ibge);
        putIf(end, "complemento", str(dom.get("complemento")));
        putIf(end, "nomeLogradouro", str(dom.get("logradouro")));
        putIf(end, "numero", Boolean.TRUE.equals(dom.get("semNumero")) ? null : str(dom.get("numero")));
        putIf(end, "numeroDneUf", str(dom.get("ufDne")));
        putIf(end, "telefoneContato", ApsValidacao.digitos(str(dom.get("telefoneContato"))));
        putIf(end, "telefoneResidencia", ApsValidacao.digitos(str(dom.get("telefoneResidencia"))));
        putIf(end, "tipoLogradouroNumeroDne", str(dom.get("tipoLogradouroDne")));
        end.put("stSemNumero", Boolean.TRUE.equals(dom.get("semNumero")));
        putIf(end, "pontoReferencia", str(dom.get("pontoReferencia")));
        putIf(end, "microArea", foraArea ? null : microarea);
        end.put("stForaArea", foraArea);
        fcd.put("enderecoLocalPermanencia", end);
        fcd.put("tipoDeImovel", tipoImovel);
        if (!familiasLedi.isEmpty()) {
            fcd.put("familias", familiasLedi);
        }
        putIf(fcd, "latitude", dbl(dom.get("latitude")));
        putIf(fcd, "longitude", dbl(dom.get("longitude")));
        Map<String, Object> ficha = fichas.registrar(new ApsFichaService.Registro(3, fcd, lot,
                ApsCidadaoService.dataRegistro(body.get("data")), null, id, null, "CADASTRO", uuidOrigem, null));
        db.exec("""
                UPDATE aps_domicilio SET uuid_ficha_origem = COALESCE(uuid_ficha_origem, :u), uuid_ultima_ficha = :u WHERE id = :id
                """, p("u", ficha.get("uuid"), "id", id));
        db.exec("UPDATE aps_familia SET ativo = FALSE WHERE id_domicilio = :id AND mudou", p("id", id));
        Map<String, Object> out = obter(id);
        out.put("ficha", Map.of("id", ficha.get("id"), "uuid", ficha.get("uuid"), "status", ficha.get("status")));
        return out;
    }

    /** Vincula cidadao a uma familia (prontuario familiar). */
    @Transactional
    public Map<String, Object> vincularMembro(Long idFamilia, Long idCidadao, boolean responsavel) {
        Map<String, Object> f = db.one("SELECT f.*, d.id_equipe, d.microarea FROM aps_familia f JOIN aps_domicilio d ON d.id = f.id_domicilio WHERE f.id = :id",
                p("id", idFamilia));
        if (f == null) {
            throw new IllegalArgumentException("Familia nao encontrada.");
        }
        db.exec("""
                UPDATE aps_cidadao SET id_familia = :f, responsavel_familiar = :r,
                    id_equipe = COALESCE(id_equipe, :eq), microarea = COALESCE(microarea, :micro), atualizado_em = CURRENT_TIMESTAMP
                WHERE id = :c
                """, p("f", idFamilia, "r", responsavel, "eq", f.get("id_equipe"), "micro", f.get("microarea"), "c", idCidadao));
        if (responsavel) {
            Map<String, Object> c = db.one("SELECT cpf, cns, data_nascimento FROM aps_cidadao WHERE id = :id", p("id", idCidadao));
            db.exec("""
                    UPDATE aps_familia SET id_cidadao_responsavel = :c, cpf_responsavel = :cpf, cns_responsavel = :cns,
                        data_nascimento_responsavel = CAST(:nasc AS date)
                    WHERE id = :f
                    """, p("c", idCidadao, "cpf", c.get("cpf"), "cns", c.get("cns"), "nasc", c.get("data_nascimento"), "f", idFamilia));
        }
        return obter(((Number) f.get("id_domicilio")).longValue());
    }

    @Transactional
    public void desvincularMembro(Long idCidadao) {
        db.exec("UPDATE aps_cidadao SET id_familia = NULL, responsavel_familiar = FALSE WHERE id = :id", p("id", idCidadao));
    }

    private static void putIf(Map<String, Object> m, String k, Object v) {
        if (v != null && !(v instanceof String s && s.isBlank())) {
            m.put(k, v);
        }
    }

    static Double dbl(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        String s = String.valueOf(o).trim().replace(',', '.');
        return s.isEmpty() ? null : Double.valueOf(s);
    }
}

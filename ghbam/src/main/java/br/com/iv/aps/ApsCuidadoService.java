package br.com.iv.aps;

import static br.com.iv.aps.ApsCidadaoService.intOrNull;
import static br.com.iv.aps.ApsConfiguracaoService.num;
import static br.com.iv.aps.ApsConfiguracaoService.str;
import static br.com.iv.aps.ApsDb.p;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.iv.aps.ApsContextoService.Lotacao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Cuidado compartilhado / discussao de caso (manual PEC cap. 13) - cada
 * evolucao gera o modelo LEDI 16 (CuidadoCompartilhadoThrift).
 */
@ApplicationScoped
public class ApsCuidadoService {

    @Inject
    ApsDb db;

    @Inject
    ApsFichaService fichas;

    @Inject
    ApsContextoService contexto;

    public List<Map<String, Object>> listar(String unidade, String matricula, String papel, String status) {
        return db.list("""
                SELECT cc.*, c.nome AS cidadao_nome, c.data_nascimento, c.cpf, c.cns,
                       (SELECT COUNT(*) FROM aps_cuidado_evolucao e WHERE e.id_cuidado = cc.id) AS evolucoes,
                       (SELECT MAX(e.criado_em) FROM aps_cuidado_evolucao e WHERE e.id_cuidado = cc.id) AS ultima_evolucao
                FROM aps_cuidado_compartilhado cc
                JOIN aps_cidadao c ON c.id = cc.id_cidadao
                WHERE UPPER(cc.unidade) = UPPER(:u)
                  AND (CAST(:status AS varchar) IS NULL OR cc.status = CAST(:status AS varchar))
                  AND (CAST(:papel AS varchar) IS NULL
                       OR (CAST(:papel AS varchar) = 'SOLICITANTE' AND cc.matricula_solicitante = :m)
                       OR (CAST(:papel AS varchar) = 'EXECUTANTE' AND cc.matricula_executante = :m))
                ORDER BY cc.prioridade DESC, cc.atualizado_em DESC
                """, p("u", unidade, "status", ApsFichaService.vazioParaNulo(status), "papel", ApsFichaService.vazioParaNulo(papel),
                "m", matricula));
    }

    public Map<String, Object> obter(Long id) {
        Map<String, Object> cc = db.one("""
                SELECT cc.*, c.nome AS cidadao_nome, c.data_nascimento, c.sexo, c.cpf, c.cns
                FROM aps_cuidado_compartilhado cc JOIN aps_cidadao c ON c.id = cc.id_cidadao WHERE cc.id = :id
                """, p("id", id));
        if (cc != null) {
            cc.put("evolucoes", db.list("SELECT * FROM aps_cuidado_evolucao WHERE id_cuidado = :id ORDER BY sequencial", p("id", id)));
        }
        return cc;
    }

    /** body: { idCidadao, idAtendimento?, idLotacaoExecutante, ciap?, cid10?, prioridade(1-4), discussao } */
    @Transactional
    public Map<String, Object> compartilhar(Map<String, Object> body, Lotacao lot) {
        Long idCidadao = num(body.get("idCidadao"));
        Long idExec = num(body.get("idLotacaoExecutante"));
        String discussao = str(body.get("discussao"));
        if (idCidadao == null || idExec == null || discussao == null) {
            throw new IllegalArgumentException("Informe cidadao, profissional executante e a discussao do caso.");
        }
        if (str(body.get("ciap")) == null && str(body.get("cid10")) == null) {
            throw new IllegalArgumentException("Informe CIAP2 ou CID10 do problema compartilhado.");
        }
        Lotacao exec = contexto.lotacaoPorId(idExec);
        Integer prioridade = intOrNull(body.get("prioridade")) == null ? 2 : intOrNull(body.get("prioridade"));
        String uuidCuidado = lot.cnes() + "-" + UUID.randomUUID();
        Long id = db.insert("""
                INSERT INTO aps_cuidado_compartilhado (uuid, unidade, id_cidadao, id_atendimento, matricula_solicitante,
                    nome_solicitante, cbo_solicitante, cns_solicitante, ine_solicitante, matricula_executante, nome_executante,
                    cbo_executante, cns_executante, ine_executante, ciap, cid10, prioridade, discussao, status)
                VALUES (:uuid, :u, :c, :at, :ms, :ns, :cs, :cnss, :is, :me, :ne, :ce, :cnse, :ie, :ciap, :cid, :pri, :disc, 'AGUARDANDO')
                RETURNING id
                """, p("uuid", uuidCuidado, "u", lot.unidade(), "c", idCidadao, "at", num(body.get("idAtendimento")),
                "ms", lot.matricula(), "ns", lot.nome(), "cs", lot.cbo(), "cnss", lot.cns(), "is", lot.ine(),
                "me", exec.matricula(), "ne", exec.nome(), "ce", exec.cbo(), "cnse", exec.cns(), "ie", exec.ine(),
                "ciap", str(body.get("ciap")), "cid", str(body.get("cid10")), "pri", prioridade, "disc", discussao));
        evoluir(id, Map.of("conduta", 1, "texto", discussao), lot, true);
        return obter(id);
    }

    /** body: { conduta (condutaevolucao 1-13), texto, reclassificacaoPrioridade? } */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> evoluir(Long idCuidado, Map<String, Object> body, Lotacao lot, boolean primeira) {
        Map<String, Object> cc = db.one("SELECT * FROM aps_cuidado_compartilhado WHERE id = :id", p("id", idCuidado));
        if (cc == null) {
            throw new IllegalArgumentException("Cuidado compartilhado nao encontrado.");
        }
        Integer conduta = intOrNull(body.get("conduta"));
        if (conduta == null) {
            throw new IllegalArgumentException("Informe a conduta.");
        }
        Map<String, Object> anterior = db.one("""
                SELECT sequencial, criado_em FROM aps_cuidado_evolucao WHERE id_cuidado = :id ORDER BY sequencial DESC LIMIT 1
                """, p("id", idCuidado));
        int seq = anterior == null ? 1 : ((Number) anterior.get("sequencial")).intValue() + 1;
        Map<String, Object> cid = db.one("SELECT * FROM aps_cidadao WHERE id = :id", p("id", cc.get("id_cidadao")));
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("lotacaoEvolucao", lotacao(lot.cpf(), lot.cns(), lot.cbo(), lot.ine(), lot.cnes(), lot.codIbge()));
        dados.put("dataEvolucao", java.time.LocalDateTime.now().withNano(0).toString());
        if (anterior != null) {
            dados.put("dataEvolucaoAnterior", anterior.get("criado_em"));
        }
        dados.put("coSequencialEvolucao", seq);
        dados.put("condutaEvolucao", conduta);
        if (cid.get("cpf") != null) {
            dados.put("cpfCidadao", cid.get("cpf"));
        } else {
            dados.put("cnsCidadao", cid.get("cns"));
        }
        dados.put("sexoCidadao", cid.get("sexo"));
        if (cid.get("data_nascimento") != null) {
            dados.put("dataNascimentoCidadao", String.valueOf(ApsLediSerializer.epochMillis(LocalDate.parse((String) cid.get("data_nascimento")))));
        }
        dados.put("uuidCuidadoCompartilhado", cc.get("uuid"));
        dados.put("solicitante", lotacao(null, (String) cc.get("cns_solicitante"), (String) cc.get("cbo_solicitante"),
                (String) cc.get("ine_solicitante"), lot.cnes(), lot.codIbge()));
        dados.put("executante", lotacao(null, (String) cc.get("cns_executante"), (String) cc.get("cbo_executante"),
                (String) cc.get("ine_executante"), lot.cnes(), lot.codIbge()));
        dados.put("dataCriacaoCuidado", String.valueOf(cc.get("criado_em")));
        if (cc.get("cid10") != null) {
            dados.put("cid10", cc.get("cid10"));
        }
        if (cc.get("ciap") != null) {
            dados.put("ciap", cc.get("ciap"));
        }
        dados.put("prioridadeCuidado", cc.get("prioridade"));
        Integer reclass = intOrNull(body.get("reclassificacaoPrioridade"));
        if (reclass != null) {
            dados.put("reclassificacaoPrioridadeCuidado", reclass);
            db.exec("UPDATE aps_cuidado_compartilhado SET prioridade = :p WHERE id = :id", p("p", reclass, "id", idCuidado));
        }
        Map<String, Object> ficha = fichas.registrar(new ApsFichaService.Registro(16, ApsLediBuilder.limpar(dados), lot,
                LocalDate.now(), ((Number) cc.get("id_cidadao")).longValue(), null, num(cc.get("id_atendimento")),
                "CUIDADO_COMPARTILHADO", null, null));
        db.exec("""
                INSERT INTO aps_cuidado_evolucao (uuid, id_cuidado, sequencial, matricula, nome, cbo, conduta, texto, id_ficha)
                VALUES (:uuid, :c, :seq, :m, :n, :cbo, :cond, :txt, :f)
                """, p("uuid", ficha.get("uuid"), "c", idCuidado, "seq", seq, "m", lot.matricula(), "n", lot.nome(),
                "cbo", lot.cbo(), "cond", conduta, "txt", str(body.get("texto")), "f", ficha.get("id")));
        String status = switch (conduta) {
            case 1, 2 -> lot.matricula().equals(cc.get("matricula_solicitante")) ? "AGUARDANDO" : "RESPONDIDO";
            case 4, 5, 6, 9, 10, 11, 12, 13 -> "CONCLUIDO";
            default -> "RESPONDIDO";
        };
        if (primeira) {
            status = "AGUARDANDO";
        }
        if (conduta == 7) {
            Long nova = num(body.get("idLotacaoExecutante"));
            if (nova != null) {
                Lotacao e = contexto.lotacaoPorId(nova);
                db.exec("""
                        UPDATE aps_cuidado_compartilhado SET matricula_executante = :m, nome_executante = :n, cbo_executante = :c,
                            cns_executante = :cns, ine_executante = :ine WHERE id = :id
                        """, p("m", e.matricula(), "n", e.nome(), "c", e.cbo(), "cns", e.cns(), "ine", e.ine(), "id", idCuidado));
                status = "AGUARDANDO";
            }
        }
        db.exec("UPDATE aps_cuidado_compartilhado SET status = :s, atualizado_em = CURRENT_TIMESTAMP WHERE id = :id",
                p("s", status, "id", idCuidado));
        return obter(idCuidado);
    }

    private static Map<String, Object> lotacao(String cpf, String cns, String cbo, String ine, String cnes, String ibge) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (cpf != null) {
            m.put("cpf", cpf);
        } else if (cns != null) {
            m.put("cns", cns);
        }
        m.put("cboCodigo_2002", cbo);
        if (ine != null) {
            m.put("ine", ine);
        }
        m.put("cnes", cnes);
        m.put("codigoIbgeMunicipio", ibge);
        return m;
    }
}

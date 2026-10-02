package br.com.iv.aps;

import static br.com.iv.aps.ApsDb.p;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.iv.aps.ApsContextoService.Lotacao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Registro LEDI (aps_ficha): monta header de lotacao, UUID (CNES-UUID), origem
 * (tpCdsOrigem = 3, sistema terceiro), valida CBO x modelo de informacao e a
 * serializacao Thrift antes de gravar - o que e gravado como PRONTA ja e um
 * registro serializavel; a validacao de negocio final e do PEC Centralizador.
 */
@ApplicationScoped
public class ApsFichaService {

    /** Modelos com VariasLotacoesHeaderThrift; os demais usam UnicaLotacaoHeaderThrift. */
    private static final Set<Integer> VARIAS_LOTACOES = Set.of(4, 5, 10, 11);

    @Inject
    ApsDb db;

    @Inject
    ApsLediSerializer serializer;

    @Inject
    ApsReferenciaService referencias;

    public record Registro(int tipo, Map<String, Object> dados, Lotacao lotacao, LocalDate data,
            Long idCidadao, Long idDomicilio, Long idAtendimento, String origem, String uuidOriginadora,
            Lotacao lotacaoCompartilhada) {
    }

    @Transactional
    public Map<String, Object> registrar(Registro r) {
        Lotacao lot = r.lotacao();
        if (lot == null) {
            throw new IllegalStateException("Profissional sem lotacao na unidade.");
        }
        if (r.tipo() != 16) {
            referencias.validarCbo(r.tipo(), lot.cbo());
        }
        if (lot.cnes() == null || lot.cnes().length() != 7) {
            throw new IllegalArgumentException("A unidade " + lot.unidade() + " esta sem CNES valido (7 digitos) em Configuracao de Unidades.");
        }
        if (lot.cns() == null || !ApsValidacao.cnsValido(lot.cns())) {
            throw new IllegalArgumentException("O profissional " + lot.nome() + " esta sem CNS valido na lotacao APS (o LEDI exige o CNS do profissional).");
        }
        LocalDate data = r.data() == null ? LocalDate.now() : r.data();
        if (data.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("A data do registro nao pode ser posterior a data atual (regra LEDI).");
        }
        String uuid = ApsContextoService.novoUuid(lot.cnes());
        Map<String, Object> dados = new LinkedHashMap<>(r.dados() == null ? Map.of() : r.dados());
        preencherIdentificacao(r.tipo(), dados, uuid, r.uuidOriginadora());
        if (r.tipo() != 16) {
            dados.put("headerTransport", header(r.tipo(), lot, r.lotacaoCompartilhada(), data));
        }
        validarSerializacao(r.tipo(), dados);
        Long id = db.insert("""
                INSERT INTO aps_ficha (uuid, uuid_origem, tipo, unidade, cnes, ine, cod_ibge, matricula,
                    profissional_nome, profissional_cns, profissional_cbo, data_atendimento, id_cidadao,
                    id_domicilio, id_atendimento, origem, dados, status, criado_por)
                VALUES (:uuid, :uuidOrigem, :tipo, :unidade, :cnes, :ine, :ibge, :matricula, :nome, :cns, :cbo,
                    :data, :idCidadao, :idDomicilio, :idAtendimento, :origem, CAST(:dados AS jsonb), 'PRONTA', :matricula)
                RETURNING id
                """, p("uuid", uuid, "uuidOrigem", r.uuidOriginadora(), "tipo", r.tipo(), "unidade", lot.unidade(),
                "cnes", lot.cnes(), "ine", lot.ine(), "ibge", lot.codIbge(), "matricula", lot.matricula(),
                "nome", lot.nome(), "cns", lot.cns(), "cbo", lot.cbo(), "data", data,
                "idCidadao", r.idCidadao(), "idDomicilio", r.idDomicilio(), "idAtendimento", r.idAtendimento(),
                "origem", r.origem() == null ? "CDS" : r.origem(), "dados", new ApsDb.Json(dados)));
        return detalhe(id);
    }

    private void preencherIdentificacao(int tipo, Map<String, Object> dados, String uuid, String uuidOriginadora) {
        switch (tipo) {
            case 2, 3 -> {
                dados.put("uuid", uuid);
                boolean atualizacao = uuidOriginadora != null && !uuidOriginadora.isBlank();
                dados.put("fichaAtualizada", atualizacao);
                dados.put("uuidFichaOriginadora", atualizacao ? uuidOriginadora : uuid);
                dados.put("tpCdsOrigem", 3);
            }
            case 6 -> {
                dados.put("uuidFicha", uuid);
                dados.put("tbCdsOrigem", 3);
            }
            case 16 -> {
                dados.put("uuidEvolucao", uuid);
                dados.put("tpCdsOrigem", 3);
            }
            default -> {
                dados.put("uuidFicha", uuid);
                dados.put("tpCdsOrigem", 3);
            }
        }
    }

    private Map<String, Object> header(int tipo, Lotacao lot, Lotacao compartilhada, LocalDate data) {
        long epoch = ApsLediSerializer.epochMillis(data);
        Map<String, Object> h = new LinkedHashMap<>();
        if (VARIAS_LOTACOES.contains(tipo)) {
            h.put("lotacaoFormPrincipal", lotacaoHeader(lot));
            if (compartilhada != null) {
                h.put("lotacaoFormAtendimentoCompartilhado", lotacaoHeader(compartilhada));
            }
            h.put("dataAtendimento", epoch);
            h.put("codigoIbgeMunicipio", lot.codIbge());
        } else {
            h.putAll(lotacaoHeader(lot));
            h.put("dataAtendimento", epoch);
            h.put("codigoIbgeMunicipio", lot.codIbge());
        }
        return h;
    }

    private Map<String, Object> lotacaoHeader(Lotacao lot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profissionalCNS", lot.cns());
        m.put("cboCodigo_2002", lot.cbo());
        m.put("cnes", lot.cnes());
        if (lot.ine() != null && !lot.ine().isBlank()) {
            m.put("ine", lot.ine());
        }
        return m;
    }

    private void validarSerializacao(int tipo, Map<String, Object> dados) {
        try {
            serializer.serializarFicha(tipo, dados);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ApsReferenciaService.nomeTipo(tipo) + ": " + e.getMessage(), e);
        }
    }

    public Map<String, Object> detalhe(Long id) {
        return db.one("SELECT * FROM aps_ficha WHERE id = :id", p("id", id));
    }

    public Map<String, Object> porUuid(String uuid) {
        return db.one("SELECT * FROM aps_ficha WHERE uuid = :u", p("u", uuid));
    }

    public List<Map<String, Object>> listar(String unidade, Integer tipo, String status, LocalDate de, LocalDate ate,
            String matricula, Long idCidadao, int limite, int offset) {
        return db.list("""
                SELECT id, uuid, tipo, unidade, cnes, ine, matricula, profissional_nome, profissional_cbo,
                       data_atendimento, id_cidadao, id_domicilio, id_atendimento, origem, status, tentativas,
                       ultimo_erro, enviado_em, criado_em
                FROM aps_ficha
                WHERE (CAST(:unidade AS varchar) IS NULL OR UPPER(unidade) = UPPER(CAST(:unidade AS varchar)))
                  AND (CAST(:tipo AS integer) IS NULL OR tipo = CAST(:tipo AS integer))
                  AND (CAST(:status AS varchar) IS NULL OR status = CAST(:status AS varchar))
                  AND (CAST(:de AS date) IS NULL OR data_atendimento >= CAST(:de AS date))
                  AND (CAST(:ate AS date) IS NULL OR data_atendimento <= CAST(:ate AS date))
                  AND (CAST(:matricula AS varchar) IS NULL OR matricula = CAST(:matricula AS varchar))
                  AND (CAST(:idCidadao AS bigint) IS NULL OR id_cidadao = CAST(:idCidadao AS bigint))
                ORDER BY criado_em DESC
                LIMIT :limite OFFSET :offset
                """, p("unidade", vazioParaNulo(unidade), "tipo", tipo, "status", vazioParaNulo(status), "de", de,
                "ate", ate, "matricula", vazioParaNulo(matricula), "idCidadao", idCidadao,
                "limite", Math.max(1, Math.min(limite, 500)), "offset", Math.max(0, offset)));
    }

    public List<Map<String, Object>> resumo(String unidade, LocalDate de, LocalDate ate) {
        return db.list("""
                SELECT tipo, status, COUNT(*) AS total
                FROM aps_ficha
                WHERE (CAST(:unidade AS varchar) IS NULL OR UPPER(unidade) = UPPER(CAST(:unidade AS varchar)))
                  AND (CAST(:de AS date) IS NULL OR data_atendimento >= CAST(:de AS date))
                  AND (CAST(:ate AS date) IS NULL OR data_atendimento <= CAST(:ate AS date))
                GROUP BY tipo, status
                ORDER BY tipo, status
                """, p("unidade", vazioParaNulo(unidade), "de", de, "ate", ate));
    }

    /** Corrige um registro ainda nao aceito (PRONTA/REJEITADA) mantendo UUID e header. */
    @Transactional
    public Map<String, Object> corrigir(Long id, Map<String, Object> novosDados, String matricula) {
        Map<String, Object> atual = detalhe(id);
        if (atual == null) {
            return null;
        }
        String status = (String) atual.get("status");
        if (!"PRONTA".equals(status) && !"REJEITADA".equals(status) && !"RASCUNHO".equals(status)) {
            throw new IllegalStateException("Somente registros nao transmitidos podem ser corrigidos (status atual: " + status + ").");
        }
        int tipo = ((Number) atual.get("tipo")).intValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> anterior = (Map<String, Object>) atual.get("dados");
        Map<String, Object> dados = new LinkedHashMap<>(novosDados);
        for (String chave : List.of("uuid", "uuidFicha", "uuidFichaOriginadora", "fichaAtualizada", "tpCdsOrigem",
                "tbCdsOrigem", "headerTransport", "uuidEvolucao")) {
            if (anterior != null && anterior.containsKey(chave)) {
                dados.put(chave, anterior.get(chave));
            }
        }
        validarSerializacao(tipo, dados);
        db.exec("""
                UPDATE aps_ficha SET dados = CAST(:dados AS jsonb), status = 'PRONTA', ultimo_erro = NULL,
                       atualizado_em = CURRENT_TIMESTAMP
                WHERE id = :id
                """, p("dados", new ApsDb.Json(dados), "id", id));
        registrarLog(id, true, null, "Registro corrigido por " + matricula, null, matricula);
        return detalhe(id);
    }

    @Transactional
    public Map<String, Object> cancelar(Long id, String motivo, String matricula) {
        Map<String, Object> atual = detalhe(id);
        if (atual == null) {
            return null;
        }
        String status = (String) atual.get("status");
        if ("ENVIADA".equals(status)) {
            throw new IllegalStateException("Registro ja transmitido ao Centralizador. Para vacinacao use a exclusao "
                    + "de dose (gera registro de cancelamento); para cadastros, registre uma atualizacao.");
        }
        if ("CANCELADA".equals(status)) {
            return atual;
        }
        db.exec("UPDATE aps_ficha SET status = 'CANCELADA', ultimo_erro = :motivo, atualizado_em = CURRENT_TIMESTAMP WHERE id = :id",
                p("motivo", "Cancelado: " + (motivo == null ? "" : motivo), "id", id));
        registrarLog(id, true, null, "Cancelado por " + matricula + ": " + motivo, null, matricula);
        return detalhe(id);
    }

    public void registrarLog(Long idFicha, boolean sucesso, Integer httpStatus, String mensagem, Long lote, String matricula) {
        db.exec("""
                INSERT INTO aps_transmissao_log (id_ficha, sucesso, http_status, mensagem, lote, matricula)
                VALUES (:id, :sucesso, :http, :msg, :lote, :matricula)
                """, p("id", idFicha, "sucesso", sucesso, "http", httpStatus, "msg", mensagem, "lote", lote,
                "matricula", matricula));
    }

    public List<Map<String, Object>> historico(Long idFicha) {
        return db.list("SELECT * FROM aps_transmissao_log WHERE id_ficha = :id ORDER BY data_hora DESC", p("id", idFicha));
    }

    public static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static List<Object> lista(Object... itens) {
        List<Object> l = new ArrayList<>();
        for (Object o : itens) {
            if (o != null) {
                l.add(o);
            }
        }
        return l;
    }
}

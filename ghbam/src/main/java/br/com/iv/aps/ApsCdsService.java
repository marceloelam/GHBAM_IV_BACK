package br.com.iv.aps;

import static br.com.iv.aps.ApsConfiguracaoService.num;
import static br.com.iv.aps.ApsConfiguracaoService.str;
import static br.com.iv.aps.ApsDb.p;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.iv.aps.ApsContextoService.Lotacao;
import br.com.iv.aps.ApsLediBuilder.Estilo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Registros CDS avulsos (manual PEC cap. 7 / modulo Atividade Coletiva) e o
 * app offline do ACS/ACE (substitui o e-SUS Territorio, que so sincroniza com
 * PEC em modo Prontuario - manual 1.4.2).
 */
@ApplicationScoped
public class ApsCdsService {

    /** Modelos que podem ser registrados diretamente (os demais tem fluxo proprio). */
    public static final Set<Integer> TIPOS_AVULSOS = Set.of(6, 7, 8, 10, 11, 12, 13);

    @Inject
    ApsDb db;

    @Inject
    ApsFichaService fichas;

    @Inject
    ApsContextoService contexto;

    @Inject
    ApsCidadaoService cidadaos;

    @Inject
    ApsDomicilioService domicilios;

    /**
     * body: { data?, idCidadao?, idDomicilio?, idLotacaoCompartilhada?, dados:{modelo LEDI sem uuid/header} }
     * Com idCidadao, a identificacao do cidadao e preenchida a partir do cadastro (CPF tem prioridade sobre CNS).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> registrar(int tipo, Map<String, Object> body, Lotacao lot) {
        if (!TIPOS_AVULSOS.contains(tipo)) {
            throw new IllegalArgumentException("Modelo " + tipo + " deve ser registrado pelo fluxo proprio (cadastro, atendimento ou vacinacao).");
        }
        Map<String, Object> dados = new LinkedHashMap<>((Map<String, Object>) body.getOrDefault("dados", Map.of()));
        Long idCidadao = num(body.get("idCidadao"));
        Long idDomicilio = num(body.get("idDomicilio"));
        if (idCidadao != null) {
            Map<String, Object> c = db.one("SELECT * FROM aps_cidadao WHERE id = :id", p("id", idCidadao));
            if (c == null) {
                throw new IllegalArgumentException("Cidadao nao encontrado.");
            }
            exigirDocumentoValido(c);
            preencherCidadao(tipo, dados, c);
        }
        if (idDomicilio != null && tipo == 8) {
            Map<String, Object> d = db.one("SELECT * FROM aps_domicilio WHERE id = :id", p("id", idDomicilio));
            if (d != null) {
                for (Map<String, Object> v : (List<Map<String, Object>>) dados.getOrDefault("visitasDomiciliares", List.of())) {
                    v.putIfAbsent("tipoDeImovel", d.get("tipo_imovel"));
                    v.putIfAbsent("stForaArea", Boolean.TRUE.equals(d.get("fora_area")));
                    if (d.get("microarea") != null) {
                        v.putIfAbsent("microArea", d.get("microarea"));
                    }
                    if (d.get("uuid_ficha_origem") != null) {
                        v.putIfAbsent("uuidOrigemCadastroDomiciliar", d.get("uuid_ficha_origem"));
                    }
                }
            }
        }
        completarObrigatorios(tipo, dados, ApsCidadaoService.dataRegistro(body.get("data")));
        if (tipo == 8) {
            for (Map<String, Object> v : (List<Map<String, Object>>) dados.getOrDefault("visitasDomiciliares", List.of())) {
                v.putIfAbsent("turno", ApsLediBuilder.turno(null));
                Object motivos = v.get("motivosVisita");
                if (!(motivos instanceof List<?> l) || l.isEmpty()) {
                    throw new IllegalArgumentException("Informe ao menos um motivo da visita.");
                }
                if (v.get("desfecho") == null) {
                    throw new IllegalArgumentException("Informe o desfecho da visita (realizada, recusada ou ausente).");
                }
            }
        }
        if (tipo == 6) {
            validarAtividadeColetiva(dados);
        }
        Lotacao compartilhada = num(body.get("idLotacaoCompartilhada")) == null ? null
                : contexto.lotacaoPorId(num(body.get("idLotacaoCompartilhada")));
        Map<String, Object> limpo = ApsLediBuilder.limpar(dados);
        return fichas.registrar(new ApsFichaService.Registro(tipo, limpo, lot, ApsCidadaoService.dataRegistro(body.get("data")),
                idCidadao, idDomicilio, null, str(body.get("origem")) == null ? "CDS" : str(body.get("origem")), null, compartilhada));
    }

    /**
     * LEDI 8.7: turno e obrigatorio no child de FP (7) e FAD (10) e na raiz de FAE (11) e FCZM (13);
     * a FP exige ainda data/hora inicial e final. O serializador nao acusa a falta (campos optional no
     * .thrift) - sem isto a ficha so seria rejeitada pelo Centralizador, na transmissao.
     */
    @SuppressWarnings("unchecked")
    static void completarObrigatorios(int tipo, Map<String, Object> dados, LocalDate data) {
        LocalDateTime agora = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime fim = data.equals(agora.toLocalDate()) ? agora : data.atTime(12, 0);
        LocalDateTime inicio = fim.minusMinutes(10).toLocalDate().equals(data) ? fim.minusMinutes(10) : data.atStartOfDay();
        int turno = ApsLediBuilder.turno(fim);
        switch (tipo) {
            case 7 -> {
                for (Map<String, Object> c : (List<Map<String, Object>>) dados.getOrDefault("atendProcedimentos", List.of())) {
                    c.putIfAbsent("turno", turno);
                    c.putIfAbsent("dataHoraInicialAtendimento", inicio.toString());
                    c.putIfAbsent("dataHoraFinalAtendimento", fim.toString());
                }
            }
            case 10 -> {
                for (Map<String, Object> c : (List<Map<String, Object>>) dados.getOrDefault("atendimentosDomiciliares", List.of())) {
                    c.putIfAbsent("turno", turno);
                    Integer tipoAtend = ApsCidadaoService.intOrNull(c.get("tipoAtendimento"));
                    if (tipoAtend == null || tipoAtend < 7 || tipoAtend > 9) {
                        throw new IllegalArgumentException("Informe o tipo de atendimento domiciliar (programado, nao programado ou pos-obito).");
                    }
                    if (tipoAtend == 9) {
                        // dic-fad: pos-obito nao leva modalidade, condicoes avaliadas nem procedimentos.
                        c.remove("atencaoDomiciliarModalidade");
                        c.remove("condicoesAvaliadas");
                        c.remove("procedimentos");
                    } else if (ApsCidadaoService.intOrNull(c.get("atencaoDomiciliarModalidade")) == null) {
                        throw new IllegalArgumentException("Informe a modalidade AD (AD1, AD2 ou AD3).");
                    }
                    if (c.get("condutaDesfecho") == null) {
                        throw new IllegalArgumentException("Informe a conduta/desfecho do atendimento domiciliar.");
                    }
                    Object probs = c.get("problemasCondicoes");
                    if (!(probs instanceof List<?> lp) || lp.isEmpty()) {
                        throw new IllegalArgumentException("Registre ao menos um problema/condicao avaliado (CIAP2 ou CID10).");
                    }
                    for (Object o : lp) {
                        // situacao so pode ir junto com uuidProblema/uuidEvolucaoProblema/coSequencialEvolucao.
                        if (o instanceof Map<?, ?> pc && pc.get("uuidProblema") == null) {
                            ((Map<String, Object>) pc).remove("situacao");
                        }
                    }
                }
            }
            case 11, 13 -> dados.putIfAbsent("turno", turno);
            default -> {
            }
        }
    }

    /**
     * CPF/CNS do cadastro vao para a ficha como estao; com digito errado o Centralizador rejeita a ficha
     * inteira dias depois (HTTP 400 "cpfCidadao CPF invalido"). Barrar aqui, onde o profissional ainda
     * pode corrigir o cadastro.
     */
    static void exigirDocumentoValido(Map<String, Object> c) {
        String nome = c.get("nome") == null ? "o cidadao" : String.valueOf(c.get("nome"));
        String cpf = c.get("cpf") == null ? null : ApsValidacao.digitos(String.valueOf(c.get("cpf")));
        if (cpf != null && !cpf.isEmpty()) {
            if (!ApsValidacao.cpfValido(cpf)) {
                throw new IllegalArgumentException("CPF " + cpf + " de " + nome
                        + " e invalido (digito verificador). Corrija o cadastro do cidadao antes de registrar.");
            }
            return;
        }
        String cns = c.get("cns") == null ? null : String.valueOf(c.get("cns")).replaceAll("\\D", "");
        if (cns != null && !cns.isEmpty() && !ApsValidacao.cnsValido(cns)) {
            throw new IllegalArgumentException("CNS " + cns + " de " + nome
                    + " e invalido. Corrija o cadastro do cidadao antes de registrar.");
        }
    }

    @SuppressWarnings("unchecked")
    private void preencherCidadao(int tipo, Map<String, Object> dados, Map<String, Object> c) {
        switch (tipo) {
            case 7 -> preencherFilhos(dados, "atendProcedimentos", c, Estilo.PADRAO);
            case 8 -> preencherFilhos(dados, "visitasDomiciliares", c, Estilo.PADRAO);
            case 10 -> preencherFilhos(dados, "atendimentosDomiciliares", c, Estilo.DOMICILIAR);
            case 12, 13 -> {
                Map<String, Object> id = ApsLediBuilder.identificacao(c, Estilo.DOMICILIAR);
                id.remove("numProntuario");
                id.forEach(dados::putIfAbsent);
            }
            case 11 -> {
                Map<String, Object> id = ApsLediBuilder.identificacao(c, Estilo.DOMICILIAR);
                id.remove("numProntuario");
                if (id.containsKey("dataNascimento")) {
                    dados.putIfAbsent("dataNascimentoCidadao", id.remove("dataNascimento"));
                }
                if (id.containsKey("sexo")) {
                    dados.putIfAbsent("sexoCidadao", id.remove("sexo"));
                }
                id.forEach(dados::putIfAbsent);
                dados.putIfAbsent("nomeCidadao", c.get("nome"));
                dados.putIfAbsent("nomeSocialCidadao", c.get("nome_social"));
                dados.putIfAbsent("racaCorCidadao", c.get("raca_cor"));
                if (c.get("nome_mae") != null) {
                    dados.putIfAbsent("nomeMaeCidadao", c.get("nome_mae"));
                } else {
                    dados.putIfAbsent("desconheceNomeMae", true);
                }
                Object ident = c.get("dados") instanceof Map<?, ?> d ? ((Map<String, Object>) d).get("identificacaoUsuarioCidadao") : null;
                if (ident instanceof Map<?, ?> im) {
                    for (String k : List.of("nacionalidadeCidadao", "codigoIbgeMunicipioNascimento", "paisNascimento", "etnia",
                            "numeroNisPisPasep", "emailCidadao", "nomePaiCidadao", "desconheceNomePai")) {
                        if (im.get(k) != null) {
                            dados.putIfAbsent(k, im.get(k));
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void preencherFilhos(Map<String, Object> dados, String lista, Map<String, Object> c, Estilo estilo) {
        Object l = dados.get(lista);
        List<Map<String, Object>> filhos = l instanceof List<?> ll ? new ArrayList<>((List<Map<String, Object>>) ll) : new ArrayList<>();
        if (filhos.isEmpty()) {
            filhos.add(new LinkedHashMap<>());
        }
        Map<String, Object> id = ApsLediBuilder.identificacao(c, estilo);
        for (int i = 0; i < filhos.size(); i++) {
            Map<String, Object> f = new LinkedHashMap<>(filhos.get(i));
            if (f.get("cpfCidadao") == null && f.get("cnsCidadao") == null && f.get("cns") == null) {
                id.forEach(f::putIfAbsent);
            }
            filhos.set(i, f);
        }
        dados.put(lista, filhos);
    }

    @SuppressWarnings("unchecked")
    private void validarAtividadeColetiva(Map<String, Object> d) {
        Integer tipoAtv = ApsCidadaoService.intOrNull(d.get("atividadeTipo"));
        if (tipoAtv == null) {
            throw new IllegalArgumentException("Informe o tipo de atividade coletiva.");
        }
        boolean reuniao = tipoAtv <= 3;
        if (reuniao && (d.get("temasParaReuniao") == null || ((List<?>) d.get("temasParaReuniao")).isEmpty())) {
            throw new IllegalArgumentException("Reunioes exigem ao menos um tema para reuniao.");
        }
        if (!reuniao) {
            List<?> part = (List<?>) d.getOrDefault("participantes", List.of());
            Integer num = ApsCidadaoService.intOrNull(d.get("numParticipantes"));
            if ((num == null || num < 1) && part.isEmpty()) {
                throw new IllegalArgumentException("Informe o numero de participantes.");
            }
            if (num == null) {
                d.put("numParticipantes", part.size());
            }
            if (tipoAtv == 6 && str(d.get("procedimento")) == null) {
                throw new IllegalArgumentException("Atividade 'Avaliacao/Procedimento coletivo' exige o procedimento SIGTAP.");
            }
            for (Map<String, Object> pt : (List<Map<String, Object>>) part) {
                ApsValidacao.validarIdentificacao(str(pt.get("cpfParticipante")), str(pt.get("cnsParticipante")),
                        pt.get("justificativaCidadaoNaoPossuiCpf"), "participante");
                if (str(pt.get("cpfParticipante")) != null) {
                    pt.remove("cnsParticipante");
                } else {
                    pt.put("stCidadaoNaoPossuiCpf", pt.get("justificativaCidadaoNaoPossuiCpf") != null);
                }
            }
        }
    }

    // ------------------------------------------------------------------ App do ACS (offline-first)

    /** Pacote de trabalho do ACS: microareas sob sua responsabilidade (ou as informadas pela gestao). */
    public Map<String, Object> pacoteAcs(String matricula, String unidade, Long idEquipe, String microarea) {
        List<Map<String, Object>> micros = db.list("""
                SELECT m.id, m.codigo, m.descricao, m.id_equipe, e.nome AS equipe_nome, e.ine, e.unidade
                FROM aps_microarea m JOIN aps_equipe e ON e.id = m.id_equipe
                WHERE m.ativo AND e.ativo
                  AND ((CAST(:eq AS bigint) IS NOT NULL AND m.id_equipe = CAST(:eq AS bigint)
                        AND (CAST(:micro AS varchar) IS NULL OR m.codigo = CAST(:micro AS varchar)))
                       OR (CAST(:eq AS bigint) IS NULL AND m.matricula_acs = :mat))
                ORDER BY e.nome, m.codigo
                """, p("eq", idEquipe, "micro", ApsFichaService.vazioParaNulo(microarea), "mat", matricula));
        List<Map<String, Object>> doms = new ArrayList<>();
        List<Map<String, Object>> cids = new ArrayList<>();
        for (Map<String, Object> m : micros) {
            Map<String, Object> pr = p("eq", m.get("id_equipe"), "micro", m.get("codigo"));
            doms.addAll(db.list("""
                    SELECT d.id, d.id_equipe, d.microarea, d.tipo_imovel, d.cep, d.logradouro, d.numero, d.sem_numero, d.complemento,
                           d.bairro, d.ponto_referencia, d.telefone_contato, d.latitude, d.longitude, d.recusa, d.dados,
                           d.cod_ibge, d.uf_dne, d.tipo_logradouro_dne, d.atualizado_em,
                           COALESCE((SELECT json_agg(json_build_object('id', f.id, 'cpf_responsavel', f.cpf_responsavel,
                                'cns_responsavel', f.cns_responsavel, 'numero_membros', f.numero_membros,
                                'renda_familiar', f.renda_familiar, 'reside_desde', f.reside_desde,
                                'id_cidadao_responsavel', f.id_cidadao_responsavel))
                              FROM aps_familia f WHERE f.id_domicilio = d.id AND f.ativo AND NOT f.mudou), '[]') AS familias,
                           (SELECT MAX(fi.data_atendimento) FROM aps_ficha fi WHERE fi.id_domicilio = d.id AND fi.tipo = 8
                              AND fi.status <> 'CANCELADA') AS ultima_visita
                    FROM aps_domicilio d WHERE d.ativo AND d.id_equipe = :eq AND d.microarea = :micro
                    ORDER BY d.logradouro, d.numero
                    """, pr));
            cids.addAll(db.list("""
                    SELECT c.id, c.nome, c.nome_social, c.cpf, c.cns, c.data_nascimento, c.sexo, c.raca_cor, c.nome_mae, c.telefone,
                           c.id_familia, c.responsavel_familiar, c.microarea, c.id_equipe, c.dados, c.atualizado_em,
                           (SELECT MAX(fi.data_atendimento) FROM aps_ficha fi WHERE fi.id_cidadao = c.id AND fi.tipo = 8
                              AND fi.status <> 'CANCELADA') AS ultima_visita,
                           EXISTS (SELECT 1 FROM aps_gestacao g WHERE g.id_cidadao = c.id AND g.status = 'ATIVA') AS gestante
                    FROM aps_cidadao c WHERE c.ativo AND c.id_equipe = :eq AND c.microarea = :micro
                    ORDER BY c.nome
                    """, pr));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("geradoEm", java.time.LocalDateTime.now().withNano(0).toString());
        out.put("microareas", micros);
        out.put("domicilios", doms);
        out.put("cidadaos", cids);
        return out;
    }

    /**
     * Sincronizacao do app do ACS. body: { itens:[{ idLocal, tipo: CADASTRO_DOMICILIAR | CADASTRO_INDIVIDUAL | VISITA,
     *   payload }] } - processado na ordem: domicilios, individuos, visitas. Idempotente por idLocal.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sincronizar(Map<String, Object> body, Lotacao lot) {
        List<Map<String, Object>> itens = new ArrayList<>((List<Map<String, Object>>) body.getOrDefault("itens", List.of()));
        itens.sort((a, b) -> Integer.compare(ordem(str(a.get("tipo"))), ordem(str(b.get("tipo")))));
        List<Map<String, Object>> resultados = new ArrayList<>();
        Map<String, Long> domicilioPorLocal = new LinkedHashMap<>();
        Map<String, Long> cidadaoPorLocal = new LinkedHashMap<>();
        for (Map<String, Object> item : itens) {
            String idLocal = str(item.get("idLocal"));
            String tipo = str(item.get("tipo"));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("idLocal", idLocal);
            r.put("tipo", tipo);
            if (idLocal == null || tipo == null) {
                r.put("sucesso", false);
                r.put("erro", "Item sem idLocal/tipo.");
                resultados.add(r);
                continue;
            }
            Map<String, Object> ja = db.one("SELECT * FROM aps_sync_item WHERE id_local = :id AND sucesso", p("id", idLocal));
            if (ja != null) {
                r.put("sucesso", true);
                r.put("resultado", ja.get("resultado"));
                r.put("duplicado", true);
                lembrar(tipo, idLocal, (Map<String, Object>) ja.get("resultado"), domicilioPorLocal, cidadaoPorLocal);
                resultados.add(r);
                continue;
            }
            try {
                Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) item.getOrDefault("payload", Map.of()));
                Map<String, Object> res;
                switch (tipo) {
                    case "CADASTRO_DOMICILIAR" -> {
                        Map<String, Object> d = domicilios.salvar(payload, lot);
                        res = Map.of("id", d.get("id"), "ficha", d.get("ficha"));
                    }
                    case "CADASTRO_INDIVIDUAL" -> {
                        String localDom = str(payload.get("idLocalDomicilio"));
                        Map<String, Object> c = cidadaos.salvarCadastroIndividual(payload, lot);
                        Long idC = ((Number) c.get("id")).longValue();
                        Long idDom = num(payload.get("idDomicilio"));
                        if (idDom == null && localDom != null) {
                            idDom = domicilioPorLocal.get(localDom);
                        }
                        if (idDom != null) {
                            vincularPorResponsavel(idDom, idC, c);
                        }
                        res = Map.of("id", idC, "ficha", c.get("ficha"));
                    }
                    case "VISITA" -> {
                        if (payload.get("idCidadao") == null && str(payload.get("idLocalCidadao")) != null) {
                            payload.put("idCidadao", cidadaoPorLocal.get(str(payload.get("idLocalCidadao"))));
                        }
                        if (payload.get("idDomicilio") == null && str(payload.get("idLocalDomicilio")) != null) {
                            payload.put("idDomicilio", domicilioPorLocal.get(str(payload.get("idLocalDomicilio"))));
                        }
                        payload.put("origem", "APP_ACS");
                        Map<String, Object> f = registrar(8, payload, lot);
                        res = Map.of("id", f.get("id"), "uuid", f.get("uuid"));
                    }
                    default -> throw new IllegalArgumentException("Tipo de item desconhecido: " + tipo);
                }
                db.exec("""
                        INSERT INTO aps_sync_item (id_local, matricula, tipo, sucesso, resultado, erro)
                        VALUES (:id, :m, :t, TRUE, CAST(:r AS jsonb), NULL)
                        ON CONFLICT (id_local) DO UPDATE SET sucesso = TRUE, resultado = EXCLUDED.resultado, erro = NULL
                        """, p("id", idLocal, "m", lot.matricula(), "t", tipo, "r", new ApsDb.Json(res)));
                lembrar(tipo, idLocal, res, domicilioPorLocal, cidadaoPorLocal);
                r.put("sucesso", true);
                r.put("resultado", res);
            } catch (RuntimeException e) {
                db.exec("""
                        INSERT INTO aps_sync_item (id_local, matricula, tipo, sucesso, erro)
                        VALUES (:id, :m, :t, FALSE, :e)
                        ON CONFLICT (id_local) DO UPDATE SET sucesso = FALSE, erro = EXCLUDED.erro
                        """, p("id", idLocal, "m", lot.matricula(), "t", tipo, "e", e.getMessage()));
                r.put("sucesso", false);
                r.put("erro", e.getMessage());
            }
            resultados.add(r);
        }
        long ok = resultados.stream().filter(x -> Boolean.TRUE.equals(x.get("sucesso"))).count();
        return Map.of("processados", resultados.size(), "sucesso", ok, "falhas", resultados.size() - ok, "itens", resultados);
    }

    private static int ordem(String tipo) {
        if ("CADASTRO_DOMICILIAR".equals(tipo)) {
            return 0;
        }
        return "CADASTRO_INDIVIDUAL".equals(tipo) ? 1 : 2;
    }

    private static void lembrar(String tipo, String idLocal, Map<String, Object> res, Map<String, Long> doms, Map<String, Long> cids) {
        if (res == null || res.get("id") == null) {
            return;
        }
        long id = ((Number) res.get("id")).longValue();
        if ("CADASTRO_DOMICILIAR".equals(tipo)) {
            doms.put(idLocal, id);
        } else if ("CADASTRO_INDIVIDUAL".equals(tipo)) {
            cids.put(idLocal, id);
        }
    }

    private void vincularPorResponsavel(Long idDomicilio, Long idCidadao, Map<String, Object> c) {
        List<Map<String, Object>> familias = db.list("SELECT * FROM aps_familia WHERE id_domicilio = :d AND ativo AND NOT mudou",
                p("d", idDomicilio));
        if (familias.isEmpty()) {
            return;
        }
        boolean responsavel = Boolean.TRUE.equals(c.get("responsavel_familiar"));
        String cpfResp = responsavel ? (String) c.get("cpf") : (String) c.get("cpf_responsavel");
        String cnsResp = responsavel ? (String) c.get("cns") : (String) c.get("cns_responsavel");
        for (Map<String, Object> f : familias) {
            boolean match = (cpfResp != null && cpfResp.equals(f.get("cpf_responsavel")))
                    || (cnsResp != null && cnsResp.equals(f.get("cns_responsavel")));
            if (match) {
                domicilios.vincularMembro(((Number) f.get("id")).longValue(), idCidadao, responsavel);
                return;
            }
        }
        if (familias.size() == 1) {
            domicilios.vincularMembro(((Number) familias.get(0).get("id")).longValue(), idCidadao, responsavel);
        }
    }
}

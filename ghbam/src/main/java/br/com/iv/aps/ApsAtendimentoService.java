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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.iv.aps.ApsContextoService.Lotacao;
import br.com.iv.aps.ApsLediBuilder.Estilo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Fluxo de atendimento da UBS (manual PEC cap. 6): lista de atendimentos,
 * escuta inicial, prontuario SOAP (individual e odontologico), lista de
 * problemas/alergias, medicoes, pre-natal e finalizacao com geracao dos
 * registros LEDI: Atendimento Individual (4), Odontologico (5) e Procedimentos (7).
 */
@ApplicationScoped
public class ApsAtendimentoService {

    /** Campos do FichaAtendimentoIndividualChildThrift que o frontend pode enviar diretamente. */
    private static final List<String> CAMPOS_FAI = List.of("localDeAtendimento", "tipoAtendimento", "aleitamentoMaterno",
            "dumDaGestante", "idadeGestacional", "atencaoDomiciliarModalidade", "exame", "vacinaEmDia", "pic",
            "ficouEmObservacao", "condutas", "stGravidezPlanejada", "nuGestasPrevias", "nuPartos", "racionalidadeSaude",
            "medicamentos", "encaminhamentos", "resultadosExames", "tipoParticipacaoCidadao",
            "tipoParticipacaoProfissionalConvidado", "emultis", "medicoes", "ivcf", "solicitacoesOci");

    /** Campos do FichaAtendimentoOdontologicoChildThrift. */
    private static final List<String> CAMPOS_FAO = List.of("gestante", "necessidadesEspeciais", "localAtendimento",
            "tipoAtendimento", "tiposEncamOdonto", "tiposFornecimOdonto", "tiposVigilanciaSaudeBucal", "tiposConsultaOdonto",
            "procedimentosRealizados", "medicamentos", "encaminhamentos", "resultadosExames", "medicoes", "ivcf", "exame",
            "solicitacoesOci");

    private static final Set<String> STATUS_LISTA = Set.of("AGUARDANDO", "EM_ESCUTA", "AGUARDANDO_ATENDIMENTO",
            "EM_ATENDIMENTO", "EM_OBSERVACAO", "ATENDIDO", "NAO_AGUARDOU", "CANCELADO");

    @Inject
    ApsDb db;

    @Inject
    ApsFichaService fichas;

    @Inject
    ApsContextoService contexto;

    @Inject
    ApsReferenciaService referencias;

    @Inject
    ApsCidadaoService cidadaos;

    // ------------------------------------------------------------------ Lista de atendimentos

    public List<Map<String, Object>> lista(String unidade, LocalDate data, String status, String matriculaDestino) {
        return db.list("""
                SELECT l.*, c.nome, c.nome_social, c.cpf, c.cns, c.data_nascimento, c.sexo, c.id_paciente,
                       DATE_PART('year', AGE(COALESCE(c.data_nascimento, CURRENT_DATE))) AS idade,
                       e.nome AS equipe_nome,
                       a.status AS atendimento_status, a.profissional AS atendimento_profissional,
                       EXTRACT(EPOCH FROM (COALESCE(l.hora_inicio, CURRENT_TIMESTAMP) - l.hora_chegada)) / 60 AS minutos_espera,
                       (SELECT COUNT(*) FROM aps_problema pr WHERE pr.id_cidadao = c.id AND pr.tipo = 'ALERGIA' AND pr.situacao <> 2) AS alergias
                FROM aps_lista_atendimento l
                JOIN aps_cidadao c ON c.id = l.id_cidadao
                LEFT JOIN aps_equipe e ON e.id = l.id_equipe
                LEFT JOIN aps_atendimento a ON a.id = l.id_atendimento
                WHERE UPPER(l.unidade) = UPPER(:unidade) AND l.data = CAST(:data AS date)
                  AND (CAST(:status AS varchar) IS NULL OR l.status = CAST(:status AS varchar))
                  AND (CAST(:dest AS varchar) IS NULL OR l.matricula_destino IS NULL OR l.matricula_destino = CAST(:dest AS varchar))
                ORDER BY CASE l.status WHEN 'EM_ATENDIMENTO' THEN 0 WHEN 'EM_ESCUTA' THEN 1 WHEN 'AGUARDANDO_ATENDIMENTO' THEN 2
                                       WHEN 'AGUARDANDO' THEN 3 WHEN 'EM_OBSERVACAO' THEN 4 ELSE 9 END,
                         COALESCE(l.classificacao_risco, 9), l.hora_chegada
                """, p("unidade", unidade, "data", data == null ? LocalDate.now() : data,
                "status", ApsFichaService.vazioParaNulo(status), "dest", ApsFichaService.vazioParaNulo(matriculaDestino)));
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> adicionarNaLista(Map<String, Object> body, String unidade, String matricula) {
        Long idCidadao = num(body.get("idCidadao"));
        if (idCidadao == null) {
            throw new IllegalArgumentException("Selecione o cidadao.");
        }
        Map<String, Object> c = db.one("SELECT id, ativo, id_equipe, saida_motivo FROM aps_cidadao WHERE id = :id", p("id", idCidadao));
        if (c == null) {
            throw new IllegalArgumentException("Cidadao nao encontrado.");
        }
        if (Integer.valueOf(135).equals(c.get("saida_motivo"))) {
            throw new IllegalArgumentException("Cidadao com registro de obito nao pode ser adicionado a lista.");
        }
        Map<String, Object> jaNaLista = db.one("""
                SELECT id FROM aps_lista_atendimento
                WHERE id_cidadao = :c AND data = CURRENT_DATE AND UPPER(unidade) = UPPER(:u)
                  AND status NOT IN ('ATENDIDO', 'NAO_AGUARDOU', 'CANCELADO')
                """, p("c", idCidadao, "u", unidade));
        if (jaNaLista != null) {
            throw new IllegalArgumentException("Cidadao ja esta na lista de atendimentos de hoje.");
        }
        Object servicos = body.get("servicos");
        String serv = servicos instanceof List<?> l ? String.join(",", (List<String>) l) : str(servicos);
        String destino = str(body.get("matriculaDestino"));
        String nomeDestino = null;
        if (destino != null) {
            Map<String, Object> s = db.one("SELECT nome FROM servidor WHERE matricula = :m LIMIT 1", p("m", destino));
            nomeDestino = s == null ? null : (String) s.get("nome");
        }
        Long idAgendamento = num(body.get("idAgendamento"));
        Long id = db.insert("""
                INSERT INTO aps_lista_atendimento (unidade, data, id_cidadao, tipo_demanda, id_agendamento, servicos,
                    matricula_destino, profissional_destino, id_equipe, status, observacao, criado_por)
                VALUES (:u, CURRENT_DATE, :c, :tipo, :ag, :serv, :dest, :nomeDest, :eq, 'AGUARDANDO', :obs, :m)
                RETURNING id
                """, p("u", unidade, "c", idCidadao, "tipo", idAgendamento != null ? "AGENDADA" : "ESPONTANEA",
                "ag", idAgendamento, "serv", serv, "dest", destino, "nomeDest", nomeDestino,
                "eq", num(body.get("idEquipe")) != null ? num(body.get("idEquipe")) : c.get("id_equipe"),
                "obs", str(body.get("observacao")), "m", matricula));
        if (idAgendamento != null) {
            db.exec("UPDATE aps_agendamento SET status = 'CONFIRMADO', atualizado_em = CURRENT_TIMESTAMP WHERE id = :id",
                    p("id", idAgendamento));
        }
        return db.one("SELECT * FROM aps_lista_atendimento WHERE id = :id", p("id", id));
    }

    @Transactional
    public Map<String, Object> alterarStatusLista(Long id, String status, String matricula) {
        if (!STATUS_LISTA.contains(status)) {
            throw new IllegalArgumentException("Status invalido: " + status);
        }
        db.exec("""
                UPDATE aps_lista_atendimento SET status = :s, atualizado_em = CURRENT_TIMESTAMP,
                    hora_fim = CASE WHEN :s IN ('ATENDIDO', 'NAO_AGUARDOU', 'CANCELADO') THEN CURRENT_TIMESTAMP ELSE hora_fim END,
                    observacao = CASE WHEN :s = 'NAO_AGUARDOU' THEN COALESCE(observacao, '') || ' [Nao aguardou - ' || :m || ']' ELSE observacao END
                WHERE id = :id
                """, p("s", status, "m", matricula, "id", id));
        Map<String, Object> l = db.one("SELECT * FROM aps_lista_atendimento WHERE id = :id", p("id", id));
        if (l != null && l.get("id_agendamento") != null && "NAO_AGUARDOU".equals(status)) {
            db.exec("UPDATE aps_agendamento SET status = 'FALTOU', atualizado_em = CURRENT_TIMESTAMP WHERE id = :id",
                    p("id", l.get("id_agendamento")));
        }
        return l;
    }

    // ------------------------------------------------------------------ Escuta inicial

    /**
     * Escuta inicial (manual 6.2). body: { motivo:{texto, ciap}, medicoes:{MedicoesThrift}, classificacaoRisco,
     * procedimentos:[sigtap], desfecho: "LIBERAR" | "ATENDIMENTO", matriculaDestino?, observacao }
     * Gera Procedimentos (7) com statusEscutaInicialOrientacao = true.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> registrarEscuta(Long idLista, Map<String, Object> body, Lotacao lot) {
        Map<String, Object> l = db.one("SELECT * FROM aps_lista_atendimento WHERE id = :id", p("id", idLista));
        if (l == null) {
            throw new IllegalArgumentException("Registro da lista nao encontrado.");
        }
        Map<String, Object> cid = cidadao(((Number) l.get("id_cidadao")).longValue());
        Map<String, Object> medicoes = (Map<String, Object>) body.getOrDefault("medicoes", Map.of());
        List<Object> procedimentos = new ArrayList<>((List<Object>) body.getOrDefault("procedimentos", List.of()));
        LocalDateTime inicio = l.get("hora_inicio") != null ? ApsLediBuilder.dataHora(l.get("hora_inicio")) : LocalDateTime.now().minusMinutes(5);
        Map<String, Object> child = new LinkedHashMap<>(ApsLediBuilder.identificacao(cid, Estilo.PADRAO));
        child.put("localAtendimento", 1);
        child.put("turno", ApsLediBuilder.turno(inicio));
        child.put("statusEscutaInicialOrientacao", true);
        child.put("procedimentos", procedimentos);
        child.put("dataHoraInicialAtendimento", inicio.toString());
        child.put("dataHoraFinalAtendimento", LocalDateTime.now().withNano(0).toString());
        child.put("medicoes", medicoes);
        Map<String, Object> master = new LinkedHashMap<>();
        master.put("atendProcedimentos", List.of(ApsLediBuilder.limpar(child)));
        Map<String, Object> ficha = fichas.registrar(new ApsFichaService.Registro(7, master, lot, LocalDate.now(),
                ((Number) l.get("id_cidadao")).longValue(), null, null, "ESCUTA_INICIAL", null, null));
        registrarMedicao(((Number) l.get("id_cidadao")).longValue(), null, medicoes, lot.matricula());
        String desfecho = str(body.get("desfecho"));
        String novoStatus = "LIBERAR".equals(desfecho) ? "ATENDIDO" : "AGUARDANDO_ATENDIMENTO";
        Map<String, Object> escuta = new LinkedHashMap<>(body);
        escuta.put("profissional", lot.nome());
        escuta.put("cbo", lot.cbo());
        escuta.put("idFicha", ficha.get("id"));
        escuta.put("registradoEm", LocalDateTime.now().withNano(0).toString());
        String destino = str(body.get("matriculaDestino"));
        db.exec("""
                UPDATE aps_lista_atendimento SET escuta = CAST(:esc AS jsonb), classificacao_risco = :risco, status = :st,
                    matricula_destino = COALESCE(:dest, matricula_destino),
                    hora_inicio = COALESCE(hora_inicio, CURRENT_TIMESTAMP),
                    hora_fim = CASE WHEN :st = 'ATENDIDO' THEN CURRENT_TIMESTAMP ELSE hora_fim END,
                    atualizado_em = CURRENT_TIMESTAMP
                WHERE id = :id
                """, p("esc", new ApsDb.Json(escuta), "risco", intOrNull(body.get("classificacaoRisco")), "st", novoStatus,
                "dest", destino, "id", idLista));
        return db.one("SELECT * FROM aps_lista_atendimento WHERE id = :id", p("id", idLista));
    }

    // ------------------------------------------------------------------ Atendimento (SOAP)

    @Transactional
    public Map<String, Object> iniciar(Map<String, Object> body, Lotacao lot) {
        Long idLista = num(body.get("idLista"));
        Long idCidadao = num(body.get("idCidadao"));
        if (idLista != null) {
            Map<String, Object> l = db.one("SELECT * FROM aps_lista_atendimento WHERE id = :id", p("id", idLista));
            if (l == null) {
                throw new IllegalArgumentException("Registro da lista nao encontrado.");
            }
            if (l.get("id_atendimento") != null) {
                Map<String, Object> a = db.one("SELECT * FROM aps_atendimento WHERE id = :id AND status = 'EM_ANDAMENTO'",
                        p("id", l.get("id_atendimento")));
                if (a != null) {
                    return obter(((Number) a.get("id")).longValue());
                }
            }
            idCidadao = ((Number) l.get("id_cidadao")).longValue();
        }
        if (idCidadao == null) {
            throw new IllegalArgumentException("Informe o cidadao ou o registro da lista.");
        }
        String tipo = str(body.get("tipo")) == null ? "INDIVIDUAL" : str(body.get("tipo"));
        if (!Set.of("INDIVIDUAL", "ODONTO", "DOMICILIAR").contains(tipo)) {
            throw new IllegalArgumentException("Tipo de atendimento invalido.");
        }
        // Mesma regra de CBO do LEDI aplicada na finalizacao: avisa antes de o profissional preencher tudo.
        referencias.validarCbo("ODONTO".equals(tipo) ? 5 : "DOMICILIAR".equals(tipo) ? 10 : 4, lot.cbo());
        boolean tardio = Boolean.TRUE.equals(body.get("registroTardio"));
        LocalDate ref = tardio ? LocalDate.parse(isoDate(body.get("dataReferencia"))) : LocalDate.now();
        if (ref.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Registro tardio nao pode ter data futura.");
        }
        Long id = db.insert("""
                INSERT INTO aps_atendimento (tipo, unidade, cnes, ine, id_lista, id_cidadao, matricula, profissional,
                    cns_profissional, cbo, matricula_supervisor, registro_tardio, data_referencia)
                VALUES (:tipo, :u, :cnes, :ine, :lista, :c, :m, :nome, :cns, :cbo, :sup, :tardio, :ref)
                RETURNING id
                """, p("tipo", tipo, "u", lot.unidade(), "cnes", lot.cnes(), "ine", lot.ine(), "lista", idLista, "c", idCidadao,
                "m", lot.matricula(), "nome", lot.nome(), "cns", lot.cns(), "cbo", lot.cbo(), "sup", lot.matriculaSupervisor(),
                "tardio", tardio, "ref", ref));
        if (idLista != null) {
            db.exec("""
                    UPDATE aps_lista_atendimento SET status = 'EM_ATENDIMENTO', id_atendimento = :a,
                        hora_inicio = COALESCE(hora_inicio, CURRENT_TIMESTAMP), atualizado_em = CURRENT_TIMESTAMP
                    WHERE id = :id
                    """, p("a", id, "id", idLista));
        }
        return obter(id);
    }

    public Map<String, Object> obter(Long id) {
        Map<String, Object> a = db.one("SELECT * FROM aps_atendimento WHERE id = :id", p("id", id));
        if (a == null) {
            return null;
        }
        Long idCidadao = ((Number) a.get("id_cidadao")).longValue();
        cidadaos.completarDoHospitalSemFalhar(idCidadao);
        Map<String, Object> c = cidadao(idCidadao);
        c.put("cadastro_desatualizado", ApsCidadaoService.cadastroDesatualizado(c));
        a.put("cidadao", c);
        if (a.get("id_lista") != null) {
            a.put("lista", db.one("SELECT * FROM aps_lista_atendimento WHERE id = :id", p("id", a.get("id_lista"))));
        }
        return a;
    }

    @Transactional
    public Map<String, Object> salvarRascunho(Long id, Map<String, Object> body, String matricula) {
        Map<String, Object> a = db.one("SELECT matricula, status FROM aps_atendimento WHERE id = :id", p("id", id));
        if (a == null) {
            return null;
        }
        if (!"EM_ANDAMENTO".equals(a.get("status"))) {
            throw new IllegalStateException("Atendimento ja finalizado ou cancelado.");
        }
        db.exec("""
                UPDATE aps_atendimento SET soap = CAST(:soap AS jsonb), dados = CAST(:dados AS jsonb), atualizado_em = CURRENT_TIMESTAMP
                WHERE id = :id
                """, p("soap", new ApsDb.Json(body.getOrDefault("soap", Map.of())),
                "dados", new ApsDb.Json(body.getOrDefault("dados", Map.of())), "id", id));
        return obter(id);
    }

    @Transactional
    public Map<String, Object> cancelar(Long id, String matricula) {
        Map<String, Object> a = db.one("SELECT * FROM aps_atendimento WHERE id = :id", p("id", id));
        if (a == null) {
            return null;
        }
        if (!"EM_ANDAMENTO".equals(a.get("status"))) {
            throw new IllegalStateException("Somente atendimentos em andamento podem ser cancelados.");
        }
        db.exec("UPDATE aps_atendimento SET status = 'CANCELADO', fim = CURRENT_TIMESTAMP WHERE id = :id", p("id", id));
        if (a.get("id_lista") != null) {
            db.exec("""
                    UPDATE aps_lista_atendimento SET status = 'AGUARDANDO_ATENDIMENTO', id_atendimento = NULL,
                        atualizado_em = CURRENT_TIMESTAMP WHERE id = :id
                    """, p("id", a.get("id_lista")));
        }
        return obter(id);
    }

    /**
     * Finaliza o atendimento e gera os registros LEDI. body = { soap:{subjetivo, objetivo, avaliacao, plano},
     * dados:{ ...campos LEDI do child (ver CAMPOS_FAI / CAMPOS_FAO)..., problemasAvaliados:[{ciap, cid10, situacao,
     * dataInicio, incluirNaLista, descricao}], procedimentos:[sigtap], compartilhamento:{idLotacao},
     * gestacao:{dum, gravidezPlanejada, gestasPrevias, partos, altoRisco} } }
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> finalizar(Long id, Map<String, Object> body, Lotacao lotUsuario) {
        Map<String, Object> a = db.one("SELECT * FROM aps_atendimento WHERE id = :id", p("id", id));
        if (a == null) {
            throw new IllegalArgumentException("Atendimento nao encontrado.");
        }
        if (!"EM_ANDAMENTO".equals(a.get("status"))) {
            throw new IllegalStateException("Atendimento ja finalizado ou cancelado.");
        }
        Map<String, Object> soap = (Map<String, Object>) body.getOrDefault("soap", a.get("soap"));
        Map<String, Object> dados = new LinkedHashMap<>((Map<String, Object>) body.getOrDefault("dados", a.get("dados")));
        Long idCidadao = ((Number) a.get("id_cidadao")).longValue();
        Map<String, Object> cid = cidadao(idCidadao);
        String tipo = (String) a.get("tipo");
        boolean tardio = Boolean.TRUE.equals(a.get("registro_tardio"));
        LocalDate ref = LocalDate.parse((String) a.get("data_referencia"));
        LocalDateTime inicio = ApsLediBuilder.dataHora(a.get("inicio"));
        LocalDateTime fim = LocalDateTime.now().withNano(0);
        if (tardio) {
            LocalTime hi = dados.get("horaInicio") != null ? LocalTime.parse(String.valueOf(dados.get("horaInicio"))) : LocalTime.of(8, 0);
            LocalTime hf = dados.get("horaFim") != null ? LocalTime.parse(String.valueOf(dados.get("horaFim"))) : hi.plusMinutes(20);
            inicio = ref.atTime(hi);
            fim = ref.atTime(hf);
        }

        Lotacao lot = lotUsuario;
        if (lot.estagiario()) {
            if (lot.matriculaSupervisor() == null) {
                throw new IllegalArgumentException("Estagiario sem supervisor cadastrado na lotacao: o atendimento e registrado em nome do supervisor.");
            }
            lot = contexto.lotacaoPorMatricula(lot.matriculaSupervisor(), lot.unidade());
        }
        Lotacao compartilhada = null;
        Map<String, Object> comp = (Map<String, Object>) dados.get("compartilhamento");
        if (comp != null && num(comp.get("idLotacao")) != null) {
            compartilhada = contexto.lotacaoPorId(num(comp.get("idLotacao")));
        }

        List<Map<String, Object>> avaliados = (List<Map<String, Object>>) dados.getOrDefault("problemasAvaliados", List.of());
        List<Map<String, Object>> problemasLedi = new ArrayList<>();
        Set<String> ciapsVistos = new LinkedHashSet<>();
        for (Map<String, Object> pr : avaliados) {
            String ciap = str(pr.get("ciap"));
            String cid10 = str(pr.get("cid10"));
            if (ciap == null && cid10 == null) {
                continue;
            }
            if (ciap != null && !ciapsVistos.add(ciap)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            if (ciap != null) {
                item.put("ciap", ciap);
            }
            if (cid10 != null) {
                item.put("cid10", cid10.replace(".", "").toUpperCase());
            }
            // LEDI (dic-fai/fao ProblemaCondicao): situacao so pode ir junto com uuidProblema,
            // uuidEvolucaoProblema e coSequencialEvolucao - nao rastreamos evolucao por problema, entao
            // a situacao fica so na lista local (aps_problema), salva mais abaixo.
            if (pr.get("dataInicio") != null) {
                item.put("dataInicioProblema", isoDate(pr.get("dataInicio")));
            }
            item.put("isAvaliado", true);
            problemasLedi.add(item);
        }

        List<Long> fichasGeradas = new ArrayList<>();
        if ("ODONTO".equals(tipo)) {
            Map<String, Object> child = new LinkedHashMap<>(ApsLediBuilder.identificacao(cid, Estilo.PADRAO));
            for (String k : CAMPOS_FAO) {
                if (dados.containsKey(k)) {
                    child.put(k, dados.get(k));
                }
            }
            child.putIfAbsent("localAtendimento", 1);
            child.putIfAbsent("tipoAtendimento", tipoAtendimentoLista(a));
            child.put("turno", ApsLediBuilder.turno(inicio));
            child.put("dataHoraInicialAtendimento", inicio.toString());
            child.put("dataHoraFinalAtendimento", fim.toString());
            if (!problemasLedi.isEmpty()) {
                child.put("problemasCondicoes", problemasLedi);
            }
            if (child.get("tiposEncamOdonto") == null || ((List<?>) child.get("tiposEncamOdonto")).isEmpty()) {
                throw new IllegalArgumentException("Informe a conduta/desfecho do atendimento odontologico.");
            }
            Map<String, Object> master = Map.of("atendimentosOdontologicos", List.of(ApsLediBuilder.limpar(child)));
            fichasGeradas.add(idFicha(fichas.registrar(new ApsFichaService.Registro(5, new LinkedHashMap<>(master), lot, ref,
                    idCidadao, null, id, "ATENDIMENTO", null, compartilhada))));
        } else {
            Map<String, Object> child = new LinkedHashMap<>(ApsLediBuilder.identificacao(cid, Estilo.FAI));
            for (String k : CAMPOS_FAI) {
                if (dados.containsKey(k)) {
                    child.put(k, dados.get(k));
                }
            }
            child.putIfAbsent("localDeAtendimento", 1);
            child.putIfAbsent("tipoAtendimento", tipoAtendimentoLista(a));
            child.put("turno", ApsLediBuilder.turno(inicio));
            child.put("dataHoraInicialAtendimento", inicio.toString());
            child.put("dataHoraFinalAtendimento", fim.toString());
            if (problemasLedi.isEmpty()) {
                throw new IllegalArgumentException("Registre ao menos um problema/condicao avaliado (CIAP2 ou CID10) na Avaliacao.");
            }
            child.put("problemasCondicoes", problemasLedi);
            Object condutas = child.get("condutas");
            if (!(condutas instanceof List<?> lc) || lc.isEmpty()) {
                throw new IllegalArgumentException("Informe ao menos uma conduta/desfecho do atendimento.");
            }
            child.put("condutas", new ArrayList<>(new LinkedHashSet<>((List<Object>) condutas)));
            if (Integer.valueOf(0).equals(cid.get("sexo"))) {
                child.remove("dumDaGestante");
                child.remove("idadeGestacional");
                child.remove("stGravidezPlanejada");
                child.remove("nuGestasPrevias");
                child.remove("nuPartos");
            }
            Map<String, Object> master = Map.of("atendimentosIndividuais", List.of(ApsLediBuilder.limpar(child)));
            fichasGeradas.add(idFicha(fichas.registrar(new ApsFichaService.Registro(4, new LinkedHashMap<>(master), lot, ref,
                    idCidadao, null, id, "ATENDIMENTO", null, compartilhada))));
        }

        List<Object> procedimentos = (List<Object>) dados.getOrDefault("procedimentos", List.of());
        if (!procedimentos.isEmpty() && !"ODONTO".equals(tipo)) {
            Map<String, Object> child = new LinkedHashMap<>(ApsLediBuilder.identificacao(cid, Estilo.PADRAO));
            child.put("localAtendimento", dados.getOrDefault("localDeAtendimento", 1));
            child.put("turno", ApsLediBuilder.turno(inicio));
            child.put("procedimentos", procedimentos);
            child.put("dataHoraInicialAtendimento", inicio.toString());
            child.put("dataHoraFinalAtendimento", fim.toString());
            Map<String, Object> master = Map.of("atendProcedimentos", List.of(ApsLediBuilder.limpar(child)));
            fichasGeradas.add(idFicha(fichas.registrar(new ApsFichaService.Registro(7, new LinkedHashMap<>(master), lot, ref,
                    idCidadao, null, id, "ATENDIMENTO", null, null))));
        }

        // Efeitos clinicos locais: lista de problemas, medicoes, gestacao.
        for (Map<String, Object> pr : avaliados) {
            if (Boolean.TRUE.equals(pr.get("incluirNaLista")) && (str(pr.get("ciap")) != null || str(pr.get("cid10")) != null)) {
                salvarProblema(idCidadao, p(
                        "tipo", "PROBLEMA",
                        "ciap", pr.get("ciap"),
                        "cid10", pr.get("cid10"),
                        "descricao", pr.get("descricao"),
                        "situacao", pr.get("situacao") == null ? 0 : pr.get("situacao"),
                        "dataInicio", pr.get("dataInicio") == null ? ref.toString() : pr.get("dataInicio"),
                        "idAtendimento", id), lot.matricula());
            }
        }
        Object medicoes = dados.get("medicoes");
        if (medicoes instanceof Map<?, ?> mm && !mm.isEmpty()) {
            registrarMedicao(idCidadao, id, (Map<String, Object>) mm, lot.matricula());
        }
        Object gest = dados.get("gestacao");
        if (gest instanceof Map<?, ?> g && !g.isEmpty()) {
            salvarGestacao(idCidadao, (Map<String, Object>) g);
        }

        dados.put("fichasGeradas", fichasGeradas);
        if (lotUsuario.estagiario()) {
            dados.put("estagiario", Map.of("matricula", lotUsuario.matricula(), "nome", lotUsuario.nome(), "cbo", lotUsuario.cbo()));
        }
        db.exec("""
                UPDATE aps_atendimento SET status = 'FINALIZADO', fim = CAST(:fim AS timestamp), soap = CAST(:soap AS jsonb),
                    dados = CAST(:dados AS jsonb), atualizado_em = CURRENT_TIMESTAMP
                WHERE id = :id
                """, p("fim", fim.toString(), "soap", new ApsDb.Json(soap), "dados", new ApsDb.Json(dados), "id", id));
        if (a.get("id_lista") != null) {
            boolean observacao = Boolean.TRUE.equals(dados.get("ficouEmObservacao"));
            db.exec("""
                    UPDATE aps_lista_atendimento SET status = :s, hora_fim = CURRENT_TIMESTAMP, atualizado_em = CURRENT_TIMESTAMP
                    WHERE id = :id
                    """, p("s", observacao ? "EM_OBSERVACAO" : "ATENDIDO", "id", a.get("id_lista")));
            Map<String, Object> l = db.one("SELECT id_agendamento FROM aps_lista_atendimento WHERE id = :id", p("id", a.get("id_lista")));
            if (l != null && l.get("id_agendamento") != null) {
                db.exec("UPDATE aps_agendamento SET status = 'ATENDIDO', id_atendimento = :a, atualizado_em = CURRENT_TIMESTAMP WHERE id = :id",
                        p("a", id, "id", l.get("id_agendamento")));
            }
        }
        return obter(id);
    }

    private int tipoAtendimentoLista(Map<String, Object> a) {
        if (a.get("id_lista") == null) {
            return 5;
        }
        Map<String, Object> l = db.one("SELECT tipo_demanda FROM aps_lista_atendimento WHERE id = :id", p("id", a.get("id_lista")));
        return l != null && "AGENDADA".equals(l.get("tipo_demanda")) ? 2 : 5;
    }

    private static Long idFicha(Map<String, Object> f) {
        return ((Number) f.get("id")).longValue();
    }

    // ------------------------------------------------------------------ Problemas / alergias

    public List<Map<String, Object>> problemas(Long idCidadao) {
        return db.list("SELECT * FROM aps_problema WHERE id_cidadao = :id ORDER BY tipo, situacao, atualizado_em DESC", p("id", idCidadao));
    }

    @Transactional
    public Map<String, Object> salvarProblema(Long idCidadao, Map<String, Object> body, String matricula) {
        String tipo = str(body.get("tipo")) == null ? "PROBLEMA" : str(body.get("tipo"));
        String ciap = str(body.get("ciap"));
        String cid10 = str(body.get("cid10"));
        String descricao = str(body.get("descricao"));
        if ("PROBLEMA".equals(tipo) && ciap == null && cid10 == null) {
            throw new IllegalArgumentException("Informe CIAP2 ou CID10 do problema/condicao.");
        }
        if ("ALERGIA".equals(tipo) && descricao == null) {
            throw new IllegalArgumentException("Informe o agente/substancia da alergia.");
        }
        Long id = num(body.get("id"));
        if (id == null && "PROBLEMA".equals(tipo)) {
            Map<String, Object> ex = db.one("""
                    SELECT id FROM aps_problema WHERE id_cidadao = :c AND tipo = 'PROBLEMA'
                      AND ((CAST(:ciap AS varchar) IS NOT NULL AND ciap = CAST(:ciap AS varchar))
                        OR (CAST(:cid AS varchar) IS NOT NULL AND cid10 = CAST(:cid AS varchar)))
                    LIMIT 1
                    """, p("c", idCidadao, "ciap", ciap, "cid", cid10 == null ? null : cid10.replace(".", "").toUpperCase()));
            if (ex != null) {
                id = ((Number) ex.get("id")).longValue();
            }
        }
        Map<String, Object> params = p("c", idCidadao, "tipo", tipo, "ciap", ciap,
                "cid", cid10 == null ? null : cid10.replace(".", "").toUpperCase(), "desc", descricao,
                "sit", intOrNull(body.get("situacao")) == null ? 0 : intOrNull(body.get("situacao")),
                "ini", isoDate(body.get("dataInicio")), "fim", isoDate(body.get("dataFim")), "cat", str(body.get("categoria")),
                "crit", str(body.get("criticidade")), "reacao", str(body.get("reacao")), "obs", str(body.get("observacao")),
                "at", num(body.get("idAtendimento")), "m", matricula);
        if (id == null) {
            params.put("uuid", ApsContextoService.novoUuid("0000000").substring(8));
            id = db.insert("""
                    INSERT INTO aps_problema (uuid, id_cidadao, tipo, ciap, cid10, descricao, situacao, data_inicio, data_fim,
                        categoria, criticidade, reacao, observacao, id_atendimento, criado_por)
                    VALUES (:uuid, :c, :tipo, :ciap, :cid, :desc, :sit, CAST(:ini AS date), CAST(:fim AS date), :cat, :crit,
                        :reacao, :obs, :at, :m)
                    RETURNING id
                    """, params);
        } else {
            params.put("id", id);
            db.exec("""
                    UPDATE aps_problema SET ciap = COALESCE(:ciap, ciap), cid10 = COALESCE(:cid, cid10),
                        descricao = COALESCE(:desc, descricao), situacao = :sit,
                        data_inicio = COALESCE(CAST(:ini AS date), data_inicio), data_fim = CAST(:fim AS date),
                        categoria = COALESCE(:cat, categoria), criticidade = COALESCE(:crit, criticidade),
                        reacao = COALESCE(:reacao, reacao), observacao = COALESCE(:obs, observacao),
                        atualizado_em = CURRENT_TIMESTAMP
                    WHERE id = :id AND id_cidadao = :c AND tipo = :tipo
                    """, params);
        }
        return db.one("SELECT * FROM aps_problema WHERE id = :id", p("id", id));
    }

    // ------------------------------------------------------------------ Medicoes e gestacao

    public void registrarMedicao(Long idCidadao, Long idAtendimento, Map<String, Object> m, String matricula) {
        if (m == null || m.isEmpty()) {
            return;
        }
        Double peso = ApsDomicilioService.dbl(m.get("peso"));
        Double altura = ApsDomicilioService.dbl(m.get("altura"));
        Double imc = peso != null && altura != null && altura > 0 ? Math.round(peso / Math.pow(altura / 100.0, 2) * 100.0) / 100.0 : null;
        db.exec("""
                INSERT INTO aps_medicao (id_cidadao, id_atendimento, peso, altura, imc, pressao_sistolica, pressao_diastolica,
                    frequencia_cardiaca, frequencia_respiratoria, temperatura, saturacao_o2, glicemia, tipo_glicemia,
                    perimetro_cefalico, circunferencia_abdominal, perimetro_panturrilha, registrado_por)
                VALUES (:c, :a, :peso, :altura, :imc, :pas, :pad, :fc, :fr, :temp, :sat, :gli, :tgli, :pc, :ca, :pp, :m)
                """, p("c", idCidadao, "a", idAtendimento, "peso", peso, "altura", altura, "imc", imc,
                "pas", intOrNull(m.get("pressaoArterialSistolica")), "pad", intOrNull(m.get("pressaoArterialDiastolica")),
                "fc", intOrNull(m.get("frequenciaCardiaca")), "fr", intOrNull(m.get("frequenciaRespiratoria")),
                "temp", ApsDomicilioService.dbl(m.get("temperatura")), "sat", intOrNull(m.get("saturacaoO2")),
                "gli", intOrNull(m.get("glicemiaCapilar")), "tgli", intOrNull(m.get("tipoGlicemiaCapilar")),
                "pc", ApsDomicilioService.dbl(m.get("perimetroCefalico")),
                "ca", ApsDomicilioService.dbl(m.get("circunferenciaAbdominal")),
                "pp", ApsDomicilioService.dbl(m.get("perimetroPanturrilha")), "m", matricula));
    }

    public List<Map<String, Object>> medicoes(Long idCidadao) {
        return db.list("SELECT * FROM aps_medicao WHERE id_cidadao = :id ORDER BY data_hora", p("id", idCidadao));
    }

    public Map<String, Object> gestacaoAtiva(Long idCidadao) {
        Map<String, Object> g = db.one("""
                SELECT * FROM aps_gestacao WHERE id_cidadao = :id AND status IN ('ATIVA', 'PUERPERIO') ORDER BY id DESC LIMIT 1
                """, p("id", idCidadao));
        if (g != null && g.get("dum") != null) {
            LocalDate dum = LocalDate.parse((String) g.get("dum"));
            long dias = java.time.temporal.ChronoUnit.DAYS.between(dum, LocalDate.now());
            g.put("idade_gestacional_semanas", dias / 7);
            g.put("idade_gestacional_dias", dias % 7);
            g.put("consultas", db.list("""
                    SELECT a.id, a.inicio, a.profissional, a.cbo, a.dados -> 'medicoes' AS medicoes,
                           a.dados -> 'idadeGestacional' AS idade_gestacional
                    FROM aps_atendimento a
                    WHERE a.id_cidadao = :c AND a.status = 'FINALIZADO' AND a.inicio >= CAST(:dum AS date)
                      AND (a.dados -> 'dumDaGestante') IS NOT NULL
                    ORDER BY a.inicio
                    """, p("c", idCidadao, "dum", dum)));
        }
        return g;
    }

    @Transactional
    public Map<String, Object> salvarGestacao(Long idCidadao, Map<String, Object> body) {
        Map<String, Object> atual = db.one("SELECT id FROM aps_gestacao WHERE id_cidadao = :id AND status IN ('ATIVA', 'PUERPERIO') ORDER BY id DESC LIMIT 1",
                p("id", idCidadao));
        String dum = isoDate(body.get("dum"));
        String dpp = dum == null ? isoDate(body.get("dpp")) : LocalDate.parse(dum).plusDays(280).toString();
        Map<String, Object> params = p("c", idCidadao, "dum", dum, "dpp", dpp,
                "plan", body.get("gravidezPlanejada") == null ? null : Boolean.TRUE.equals(body.get("gravidezPlanejada")),
                "gestas", intOrNull(body.get("gestasPrevias")), "partos", intOrNull(body.get("partos")),
                "risco", Boolean.TRUE.equals(body.get("altoRisco")), "dados", new ApsDb.Json(body));
        if (atual == null) {
            Long id = db.insert("""
                    INSERT INTO aps_gestacao (id_cidadao, dum, dpp, gravidez_planejada, gestas_previas, partos, alto_risco, dados)
                    VALUES (:c, CAST(:dum AS date), CAST(:dpp AS date), :plan, :gestas, :partos, :risco, CAST(:dados AS jsonb))
                    RETURNING id
                    """, params);
            return db.one("SELECT * FROM aps_gestacao WHERE id = :id", p("id", id));
        }
        params.put("id", atual.get("id"));
        db.exec("""
                UPDATE aps_gestacao SET dum = COALESCE(CAST(:dum AS date), dum), dpp = COALESCE(CAST(:dpp AS date), dpp),
                    gravidez_planejada = COALESCE(:plan, gravidez_planejada), gestas_previas = COALESCE(:gestas, gestas_previas),
                    partos = COALESCE(:partos, partos), alto_risco = :risco OR alto_risco, dados = dados || CAST(:dados AS jsonb),
                    atualizado_em = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params);
        return db.one("SELECT * FROM aps_gestacao WHERE id = :id", p("id", atual.get("id")));
    }

    @Transactional
    public Map<String, Object> encerrarGestacao(Long idGestacao, Map<String, Object> body) {
        String desfecho = str(body.get("desfecho"));
        if (desfecho == null) {
            throw new IllegalArgumentException("Informe o desfecho da gestacao.");
        }
        boolean puerperio = Set.of("PARTO_NORMAL", "PARTO_CESAREO").contains(desfecho);
        db.exec("""
                UPDATE aps_gestacao SET status = :st, desfecho = :d, data_desfecho = COALESCE(CAST(:data AS date), CURRENT_DATE),
                    atualizado_em = CURRENT_TIMESTAMP
                WHERE id = :id
                """, p("st", puerperio ? "PUERPERIO" : "ENCERRADA", "d", desfecho, "data", isoDate(body.get("data")), "id", idGestacao));
        return db.one("SELECT * FROM aps_gestacao WHERE id = :id", p("id", idGestacao));
    }

    @Transactional
    public Map<String, Object> encerrarPuerperio(Long idGestacao) {
        db.exec("UPDATE aps_gestacao SET status = 'ENCERRADA', atualizado_em = CURRENT_TIMESTAMP WHERE id = :id AND status = 'PUERPERIO'",
                p("id", idGestacao));
        return db.one("SELECT * FROM aps_gestacao WHERE id = :id", p("id", idGestacao));
    }

    Map<String, Object> cidadao(Long id) {
        Map<String, Object> c = db.one("SELECT * FROM aps_cidadao WHERE id = :id", p("id", id));
        if (c == null) {
            throw new IllegalArgumentException("Cidadao nao encontrado.");
        }
        return c;
    }
}

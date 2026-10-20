package br.com.iv.aps;

import static br.com.iv.aps.ApsConfiguracaoService.num;
import static br.com.iv.aps.ApsConfiguracaoService.str;
import static br.com.iv.aps.ApsDb.p;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.aps.ApsContextoService.Lotacao;
import br.com.iv.dto.PacientesDto;
import br.com.iv.service.PacientesService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Cidadao na APS (manual PEC cap. 4 e 9): busca local + cadastro do hospital,
 * Cadastro Individual (FCI / LEDI tipo 2) completo, saida (obito/mudanca),
 * unificacao de cadastros, reterritorializacao e folha de rosto.
 *
 * O cidadao APS e ligado ao paciente unico do hospital (pacientes.idpaciente)
 * por CPF/CNS - o prontuario hospitalar continua sendo o mesmo.
 */
@ApplicationScoped
public class ApsCidadaoService {

    @Inject
    PacientesService pacientesService;
    @Inject
    ApsDb db;

    @Inject
    ApsFichaService fichas;

    public List<Map<String, Object>> buscar(String termo, String unidade, Long idEquipe, String microarea,
            boolean incluirHospital, int limite) {
        String t = termo == null ? "" : termo.trim();
        String dig = ApsValidacao.digitos(t);
        List<Map<String, Object>> out = new ArrayList<>(db.list("""
                SELECT c.id, c.id_paciente, c.nome, c.nome_social, c.cpf, c.cns, c.data_nascimento, c.sexo, c.nome_mae,
                       c.telefone, c.unidade, c.id_equipe, e.nome AS equipe_nome, e.ine, c.microarea, c.fora_area,
                       c.recusa, c.ativo, c.saida_motivo, c.id_familia, c.responsavel_familiar, c.atualizado_em,
                       'APS' AS origem
                FROM aps_cidadao c
                LEFT JOIN aps_equipe e ON e.id = c.id_equipe
                WHERE (CAST(:unidade AS varchar) IS NULL OR UPPER(c.unidade) = UPPER(CAST(:unidade AS varchar)))
                  AND (CAST(:idEquipe AS bigint) IS NULL OR c.id_equipe = CAST(:idEquipe AS bigint))
                  AND (CAST(:microarea AS varchar) IS NULL OR c.microarea = CAST(:microarea AS varchar))
                  AND (
                        CAST(:termo AS varchar) = ''
                     OR UPPER(c.nome) LIKE UPPER(CAST(:like AS varchar))
                     OR UPPER(COALESCE(c.nome_social, '')) LIKE UPPER(CAST(:like AS varchar))
                     OR (CAST(:dig AS varchar) IS NOT NULL AND (c.cpf = CAST(:dig AS varchar) OR c.cns = CAST(:dig AS varchar)))
                  )
                ORDER BY c.ativo DESC, c.nome
                LIMIT :limite
                """, p("unidade", ApsFichaService.vazioParaNulo(unidade), "idEquipe", idEquipe,
                "microarea", ApsFichaService.vazioParaNulo(microarea), "termo", t, "like", "%" + t + "%",
                "dig", dig != null && dig.length() >= 11 ? dig : null, "limite", Math.max(1, Math.min(limite, 200)))));
        if (incluirHospital && t.length() >= 3) {
            List<Map<String, Object>> hosp = db.list("""
                    SELECT p.idpaciente AS id_paciente, p.nome, p.cpf, p.cns, p.nascimento, p.sexo, p.mae AS nome_mae,
                           p.telefone, p.bairro, p.municipio, 'HOSPITAL' AS origem
                    FROM pacientes p
                    WHERE (UPPER(p.nome) LIKE UPPER(:like)
                           OR (CAST(:dig AS varchar) IS NOT NULL AND (regexp_replace(COALESCE(p.cpf, ''), '\\D', '', 'g') = CAST(:dig AS varchar)
                               OR regexp_replace(COALESCE(p.cns, ''), '\\D', '', 'g') = CAST(:dig AS varchar))))
                      AND NOT EXISTS (SELECT 1 FROM aps_cidadao c WHERE c.id_paciente = p.idpaciente)
                    ORDER BY p.nome
                    LIMIT 30
                    """, p("like", "%" + t + "%", "dig", dig != null && dig.length() >= 11 ? dig : null));
            for (Map<String, Object> h : hosp) {
                h.put("data_nascimento", dataPaciente((String) h.get("nascimento")));
                h.put("sexo_texto", h.get("sexo"));
                h.remove("sexo");
                out.add(h);
            }
        }
        return out;
    }

    public Map<String, Object> obter(Long id) {
        completarDoHospitalSemFalhar(id);
        Map<String, Object> c = db.one("""
                SELECT c.*, e.nome AS equipe_nome, e.ine, e.cnes AS equipe_cnes,
                       d.id AS id_domicilio, d.logradouro, d.numero, d.bairro, d.complemento, d.cep
                FROM aps_cidadao c
                LEFT JOIN aps_equipe e ON e.id = c.id_equipe
                LEFT JOIN aps_familia f ON f.id = c.id_familia
                LEFT JOIN aps_domicilio d ON d.id = f.id_domicilio
                WHERE c.id = :id
                """, p("id", id));
        if (c != null && c.get("data_nascimento") != null) {
            LocalDate n = LocalDate.parse((String) c.get("data_nascimento"));
            c.put("idade_anos", ApsValidacao.idadeAnos(n, null));
            c.put("idade_meses", ApsValidacao.idadeMeses(n, null));
        }
        if (c != null) {
            c.put("cadastro_desatualizado", cadastroDesatualizado(c));
        }
        return c;
    }

    /**
     * Completa CPF/CNS/nascimento VAZIOS do cidadao a partir do cadastro do hospital (mesmo paciente,
     * via id_paciente). Nunca sobrescreve; so aceita CPF/CNS validos e que nao sejam de outro cidadao.
     * Nao gera ficha: o e-SUS recebe o dado quando o Cadastro Individual for revisado e salvo
     * (ver cadastroDesatualizado). atualizado_por = SYNC-HOSPITAL deixa a origem rastreavel.
     */
    void completarDoHospitalSemFalhar(Long id) {
        try {
            completarDoHospital(id);
        } catch (RuntimeException e) {
            // Complemento e oportunista: nunca impede abrir o cidadao.
            org.jboss.logging.Logger.getLogger(ApsCidadaoService.class)
                    .warnf("APS: falha ao completar cidadao %d pelo hospital: %s", id, e.getMessage());
        }
    }

    void completarDoHospital(Long id) {
        Map<String, Object> c = db.one("""
                SELECT id, id_paciente, cpf, cns, data_nascimento FROM aps_cidadao
                WHERE id = :id AND id_paciente IS NOT NULL AND (cpf IS NULL OR cns IS NULL OR data_nascimento IS NULL)
                """, p("id", id));
        if (c == null) {
            return;
        }
        Map<String, Object> pac = db.one("SELECT cpf, cns, nascimento FROM pacientes WHERE idpaciente = :p",
                p("p", c.get("id_paciente")));
        if (pac == null) {
            return;
        }
        String cpf = c.get("cpf") == null ? ApsValidacao.digitos((String) pac.get("cpf")) : null;
        String cns = c.get("cns") == null ? ApsValidacao.digitos((String) pac.get("cns")) : null;
        cpf = ApsValidacao.cpfValido(cpf) && duplicado(cpf, null, id) == null ? cpf : null;
        cns = ApsValidacao.cnsValido(cns) && duplicado(null, cns, id) == null ? cns : null;
        String nasc = c.get("data_nascimento") == null ? dataPaciente((String) pac.get("nascimento")) : null;
        if (nasc != null && LocalDate.parse(nasc).isAfter(LocalDate.now())) {
            nasc = null;
        }
        if (cpf == null && cns == null && nasc == null) {
            return;
        }
        db.exec("""
                UPDATE aps_cidadao SET cpf = COALESCE(cpf, :cpf), cns = COALESCE(cns, :cns),
                       data_nascimento = COALESCE(data_nascimento, CAST(:nasc AS date)),
                       atualizado_em = CURRENT_TIMESTAMP, atualizado_por = 'SYNC-HOSPITAL'
                WHERE id = :id
                """, p("id", id, "cpf", cpf, "cns", cns, "nasc", nasc));
    }

    /**
     * Ficha de Cadastro Individual salva sem um dado que o cidadao ja tem (ex.: completado pelo
     * hospital): o e-SUS esta com o cadastro antigo ate alguem revisar e salvar o Cadastro Individual.
     * Calculado, nao gravado em `dados` - `dados` e a FCI que vai para o LEDI/app do ACS.
     */
    @SuppressWarnings("unchecked")
    static boolean cadastroDesatualizado(Map<String, Object> c) {
        if (!(c.get("dados") instanceof Map<?, ?> d) || !(d.get("identificacaoUsuarioCidadao") instanceof Map<?, ?> i)) {
            return false;
        }
        Map<String, Object> ident = (Map<String, Object>) i;
        String fichaCpf = ApsValidacao.digitos(str(ident.get("cpfCidadao")));
        String fichaCns = ApsValidacao.digitos(str(ident.get("cnsCidadao")));
        boolean semCpfNaFicha = fichaCpf == null || fichaCpf.isEmpty();
        if (c.get("cpf") != null && semCpfNaFicha) {
            return true;
        }
        // A FCI leva so o CPF quando ha CPF; CNS so falta de fato quando a ficha nao tem nenhum dos dois.
        if (c.get("cns") != null && semCpfNaFicha && (fichaCns == null || fichaCns.isEmpty())) {
            return true;
        }
        return c.get("data_nascimento") != null && str(ident.get("dataNascimentoCidadao")) == null;
    }

    /** Traz para a APS um paciente ja cadastrado no hospital (sem gerar ficha: o cadastro individual e feito depois). */
    @Transactional
    public Map<String, Object> importarPaciente(Integer idPaciente, String unidade, String matricula) {
        Map<String, Object> existente = db.one("SELECT id FROM aps_cidadao WHERE id_paciente = :id", p("id", idPaciente));
        if (existente != null) {
            return obter(((Number) existente.get("id")).longValue());
        }
        Map<String, Object> pac = db.one("""
                SELECT idpaciente, nome, cpf, cns, nascimento, sexo, mae, telefone FROM pacientes WHERE idpaciente = :id
                """, p("id", idPaciente));
        if (pac == null) {
            throw new IllegalArgumentException("Paciente nao encontrado no cadastro do hospital.");
        }
        String cpf = ApsValidacao.digitos((String) pac.get("cpf"));
        String cns = ApsValidacao.digitos((String) pac.get("cns"));
        Map<String, Object> dup = duplicado(cpf, cns, null);
        if (dup != null) {
            db.exec("UPDATE aps_cidadao SET id_paciente = :p WHERE id = :id AND id_paciente IS NULL",
                    p("p", idPaciente, "id", dup.get("id")));
            return obter(((Number) dup.get("id")).longValue());
        }
        String nasc = dataPaciente((String) pac.get("nascimento"));
        Long id = db.insert("""
                INSERT INTO aps_cidadao (id_paciente, cpf, cns, nome, data_nascimento, sexo, nome_mae, telefone, unidade, criado_por, atualizado_por)
                VALUES (:idp, :cpf, :cns, :nome, CAST(:nasc AS date), :sexo, :mae, :tel, :unidade, :m, :m)
                RETURNING id
                """, p("idp", idPaciente, "cpf", cpf != null && cpf.length() == 11 ? cpf : null,
                "cns", cns != null && cns.length() == 15 ? cns : null, "nome", pac.get("nome"), "nasc", nasc,
                "sexo", sexoLedi((String) pac.get("sexo")), "mae", pac.get("mae"), "tel", telefoneImportado((String) pac.get("telefone")),
                "unidade", unidade, "m", matricula));
        return obter(id);
    }

    /**
     * O telefone do cadastro do hospital e texto livre (mascara, dois numeros "x / y", observacoes) e
     * estourava aps_cidadao.telefone VARCHAR(20). Fica so o primeiro numero, em digitos.
     */
    static String telefoneImportado(String bruto) {
        if (bruto == null) {
            return null;
        }
        for (String parte : bruto.split("[/;,|]|\\s+(?i:e|ou)\\s+")) {
            String d = ApsValidacao.digitos(parte);
            if (d != null && !d.isEmpty()) {
                return d.length() > 20 ? d.substring(0, 20) : d;
            }
        }
        return null;
    }

    /**
     * Cadastro Individual completo. body:
     *   { idCidadao?, idPaciente?, idFamilia?, idEquipe?, data?, fci: {CadastroIndividualThrift sem uuid/header} }
     * O frontend envia a FCI com os nomes oficiais do LEDI.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> salvarCadastroIndividual(Map<String, Object> body, Lotacao lot) {
        Map<String, Object> fci = (Map<String, Object>) body.get("fci");
        if (fci == null) {
            throw new IllegalArgumentException("Dados do cadastro individual ausentes.");
        }
        boolean recusa = Boolean.TRUE.equals(fci.get("statusTermoRecusaCadastroIndividualAtencaoBasica"));
        Map<String, Object> ident = (Map<String, Object>) fci.getOrDefault("identificacaoUsuarioCidadao", new LinkedHashMap<>());
        String nome = str(ident.get("nomeCidadao"));
        if (nome == null) {
            throw new IllegalArgumentException("Nome do cidadao e obrigatorio.");
        }
        String cpf = ApsValidacao.digitos(str(ident.get("cpfCidadao")));
        String cns = ApsValidacao.digitos(str(ident.get("cnsCidadao")));
        if (!recusa) {
            // Regra do G-PEC: CPF obrigatorio, salvo "nao possui CPF" com justificativa.
            boolean semCpf = Boolean.TRUE.equals(ident.get("stNaoPossuiCpf"));
            if (semCpf && ident.get("justificativaNaoPossuiCpf") == null) {
                throw new IllegalArgumentException("Informe a justificativa de o cidadao nao possuir CPF.");
            }
            if (!semCpf && cpf == null) {
                throw new IllegalArgumentException("CPF e obrigatorio (ou marque 'cidadao nao possui CPF' com justificativa).");
            }
            ApsValidacao.validarIdentificacao(cpf, cns, ident.get("justificativaNaoPossuiCpf"), nome);
            if (ident.get("dataNascimentoCidadao") == null) {
                throw new IllegalArgumentException("Data de nascimento e obrigatoria.");
            }
            if (ident.get("sexoCidadao") == null || ident.get("racaCorCidadao") == null) {
                throw new IllegalArgumentException("Sexo e raca/cor sao obrigatorios no cadastro individual.");
            }
            if (!Boolean.TRUE.equals(ident.get("desconheceNomeMae")) && str(ident.get("nomeMaeCidadao")) == null) {
                throw new IllegalArgumentException("Informe o nome da mae ou marque 'desconhece'.");
            }
            if (ident.get("nacionalidadeCidadao") == null) {
                throw new IllegalArgumentException("Nacionalidade e obrigatoria.");
            }
            String microarea = str(ident.get("microArea"));
            if (!Boolean.TRUE.equals(ident.get("stForaArea")) && microarea == null) {
                throw new IllegalArgumentException("Informe a microarea ou marque 'fora de area'.");
            }
            if (!Boolean.TRUE.equals(ident.get("statusEhResponsavel"))
                    && str(ident.get("cpfResponsavelFamiliar")) == null && str(ident.get("cnsResponsavelFamiliar")) == null) {
                throw new IllegalArgumentException("Informe o CPF/CNS do responsavel familiar ou marque o cidadao como responsavel.");
            }
        }
        // LEDI: CPF e CNS sao exclusivos na ficha. O CNS continua gravado no cidadao (coluna cns).
        if (cpf != null) {
            ident.put("cpfCidadao", cpf);
            ident.remove("cnsCidadao");
        } else if (cns != null) {
            ident.put("cnsCidadao", cns);
        }
        Long idCidadao = num(body.get("idCidadao"));
        Map<String, Object> dup = duplicado(cpf, cns, idCidadao);
        if (dup != null) {
            throw new IllegalArgumentException("Ja existe cidadao cadastrado com este CPF/CNS: " + dup.get("nome")
                    + " (use a busca e atualize o cadastro existente).");
        }
        String nascIso = isoDate(ident.get("dataNascimentoCidadao"));
        Long idPaciente = num(body.get("idPaciente"));
        if (idPaciente == null) {
            idPaciente = vincularPaciente(cpf, cns);
        }
        if (idPaciente == null && !recusa && nascIso != null) {
            // Cidadao novo para o GHBAM: cria o paciente (mesmo servico/formato da Recepcao)
            // para ele ser encontrado no hospital e o Historico Completo trazer a APS.
            idPaciente = criarPacienteHospital(nome, nascIso, cpf, cns, ident);
        }
        Long idEquipe = num(body.get("idEquipe"));
        if (idEquipe == null && lot.idEquipe() != null) {
            idEquipe = lot.idEquipe();
        }
        String microarea = str(ident.get("microArea"));
        Map<String, Object> params = p("idp", idPaciente == null ? null : idPaciente.intValue(), "cpf", cpf, "cns", cns,
                "nome", nome, "social", str(ident.get("nomeSocial")), "nasc", nascIso,
                "sexo", intOrNull(ident.get("sexoCidadao")), "raca", intOrNull(ident.get("racaCorCidadao")),
                "mae", str(ident.get("nomeMaeCidadao")), "tel", str(ident.get("telefoneCelular")),
                "unidade", lot.unidade(), "idEquipe", idEquipe, "microarea", microarea,
                "fora", Boolean.TRUE.equals(ident.get("stForaArea")), "idFamilia", num(body.get("idFamilia")),
                "resp", Boolean.TRUE.equals(ident.get("statusEhResponsavel")),
                "cpfResp", ApsValidacao.digitos(str(ident.get("cpfResponsavelFamiliar"))),
                "cnsResp", ApsValidacao.digitos(str(ident.get("cnsResponsavelFamiliar"))),
                "dados", new ApsDb.Json(fci), "recusa", recusa, "m", lot.matricula());
        String uuidOrigem = null;
        if (idCidadao == null) {
            idCidadao = db.insert("""
                    INSERT INTO aps_cidadao (id_paciente, cpf, cns, nome, nome_social, data_nascimento, sexo, raca_cor, nome_mae,
                        telefone, unidade, id_equipe, microarea, fora_area, id_familia, responsavel_familiar, cpf_responsavel,
                        cns_responsavel, dados, recusa, criado_por, atualizado_por)
                    VALUES (:idp, :cpf, :cns, :nome, :social, CAST(:nasc AS date), :sexo, :raca, :mae, :tel, :unidade, :idEquipe,
                        :microarea, :fora, :idFamilia, :resp, :cpfResp, :cnsResp, CAST(:dados AS jsonb), :recusa, :m, :m)
                    RETURNING id
                    """, params);
        } else {
            Map<String, Object> atual = db.one("SELECT uuid_ficha_origem FROM aps_cidadao WHERE id = :id", p("id", idCidadao));
            if (atual == null) {
                throw new IllegalArgumentException("Cidadao nao encontrado.");
            }
            uuidOrigem = (String) atual.get("uuid_ficha_origem");
            params.put("id", idCidadao);
            db.exec("""
                    UPDATE aps_cidadao SET id_paciente = COALESCE(:idp, id_paciente), cpf = :cpf, cns = :cns, nome = :nome,
                        nome_social = :social, data_nascimento = CAST(:nasc AS date), sexo = :sexo, raca_cor = :raca,
                        nome_mae = :mae, telefone = :tel, unidade = :unidade, id_equipe = :idEquipe, microarea = :microarea,
                        fora_area = :fora, id_familia = COALESCE(:idFamilia, id_familia), responsavel_familiar = :resp,
                        cpf_responsavel = :cpfResp, cns_responsavel = :cnsResp, dados = CAST(:dados AS jsonb),
                        recusa = :recusa, ativo = TRUE, atualizado_em = CURRENT_TIMESTAMP, atualizado_por = :m
                    WHERE id = :id
                    """, params);
        }
        Map<String, Object> ficha = fichas.registrar(new ApsFichaService.Registro(2, fci, lot,
                dataRegistro(body.get("data")), idCidadao, null, null, "CADASTRO", uuidOrigem, null));
        String uuid = (String) ficha.get("uuid");
        db.exec("""
                UPDATE aps_cidadao SET uuid_ficha_origem = COALESCE(uuid_ficha_origem, :u), uuid_ultima_ficha = :u WHERE id = :id
                """, p("u", uuid, "id", idCidadao));
        Map<String, Object> out = obter(idCidadao);
        out.put("ficha", Map.of("id", ficha.get("id"), "uuid", uuid, "status", ficha.get("status")));
        return out;
    }

    /** Saida do cadastro (obito ou mudanca de territorio) - FCI com saidaCidadaoCadastro. */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> registrarSaida(Long idCidadao, Map<String, Object> body, Lotacao lot) {
        Map<String, Object> c = db.one("SELECT * FROM aps_cidadao WHERE id = :id", p("id", idCidadao));
        if (c == null) {
            throw new IllegalArgumentException("Cidadao nao encontrado.");
        }
        Integer motivo = intOrNull(body.get("motivoSaidaCidadao"));
        if (motivo == null) {
            throw new IllegalArgumentException("Informe o motivo da saida (obito ou mudanca de territorio).");
        }
        Map<String, Object> fci = new LinkedHashMap<>((Map<String, Object>) c.get("dados"));
        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("motivoSaidaCidadao", motivo);
        String dataObito = isoDate(body.get("dataObito"));
        if (dataObito != null) {
            saida.put("dataObito", dataObito);
        }
        if (str(body.get("numeroDO")) != null) {
            saida.put("numeroDO", str(body.get("numeroDO")));
        }
        fci.put("saidaCidadaoCadastro", saida);
        Map<String, Object> ficha = fichas.registrar(new ApsFichaService.Registro(2, fci, lot, LocalDate.now(), idCidadao,
                null, null, "SAIDA", (String) c.get("uuid_ficha_origem"), null));
        db.exec("""
                UPDATE aps_cidadao SET ativo = FALSE, saida_motivo = :motivo, saida_data = COALESCE(CAST(:obito AS date), CURRENT_DATE),
                    numero_do = :do, uuid_ultima_ficha = :u, atualizado_em = CURRENT_TIMESTAMP, atualizado_por = :m
                WHERE id = :id
                """, p("motivo", motivo, "obito", dataObito, "do", str(body.get("numeroDO")), "u", ficha.get("uuid"),
                "m", lot.matricula(), "id", idCidadao));
        if (motivo == 135) {
            db.exec("""
                    UPDATE aps_agendamento SET status = 'CANCELADO', observacao = 'Cancelado automaticamente: obito do cidadao',
                        atualizado_em = CURRENT_TIMESTAMP
                    WHERE id_cidadao = :id AND status IN ('AGENDADO', 'CONFIRMADO') AND data_hora >= CURRENT_TIMESTAMP
                    """, p("id", idCidadao));
        }
        return obter(idCidadao);
    }

    /** Unificacao de cadastros duplicados (manual 9.2): o historico do removido passa ao mantido. */
    @Transactional
    public Map<String, Object> unificar(Long idManter, Long idRemover, String matricula) {
        if (idManter == null || idRemover == null || idManter.equals(idRemover)) {
            throw new IllegalArgumentException("Selecione dois cadastros diferentes.");
        }
        Map<String, Object> manter = db.one("SELECT * FROM aps_cidadao WHERE id = :id", p("id", idManter));
        Map<String, Object> remover = db.one("SELECT * FROM aps_cidadao WHERE id = :id", p("id", idRemover));
        if (manter == null || remover == null) {
            throw new IllegalArgumentException("Cadastro nao encontrado.");
        }
        for (String tabela : List.of("aps_lista_atendimento", "aps_atendimento", "aps_problema", "aps_medicao",
                "aps_gestacao", "aps_vacinacao", "aps_garantia_acesso", "aps_cuidado_compartilhado", "aps_ficha",
                "aps_agendamento")) {
            db.exec("UPDATE " + tabela + " SET id_cidadao = :m WHERE id_cidadao = :r", p("m", idManter, "r", idRemover));
        }
        db.exec("""
                UPDATE aps_cidadao SET id_paciente = COALESCE(id_paciente, :idp), cpf = COALESCE(cpf, :cpf),
                    cns = COALESCE(cns, :cns), atualizado_em = CURRENT_TIMESTAMP, atualizado_por = :mat
                WHERE id = :id
                """, p("idp", remover.get("id_paciente"), "cpf", remover.get("cpf"), "cns", remover.get("cns"),
                "mat", matricula, "id", idManter));
        db.exec("""
                UPDATE aps_cidadao SET ativo = FALSE, cpf = NULL, cns = NULL, id_paciente = NULL,
                    dados = dados || CAST(:marca AS jsonb), atualizado_em = CURRENT_TIMESTAMP, atualizado_por = :mat
                WHERE id = :id
                """, p("marca", new ApsDb.Json(Map.of("unificadoEm", idManter, "unificadoPor", matricula,
                "unificadoData", Instant.now().toString(), "cpfOriginal", String.valueOf(remover.get("cpf")),
                "cnsOriginal", String.valueOf(remover.get("cns")))), "mat", matricula, "id", idRemover));
        return obter(idManter);
    }

    /** Reterritorializacao (manual 9.3): troca equipe/microarea de um conjunto de cidadaos. */
    @Transactional
    public int reterritorializar(List<Long> ids, Long idEquipe, String microarea, boolean foraArea, String matricula) {
        int n = 0;
        for (Long id : ids) {
            n += db.exec("""
                    UPDATE aps_cidadao SET id_equipe = :eq, microarea = :micro, fora_area = :fora,
                        dados = jsonb_set(jsonb_set(dados, '{identificacaoUsuarioCidadao,microArea}', to_jsonb(CAST(:microTxt AS text)), true),
                                          '{identificacaoUsuarioCidadao,stForaArea}', to_jsonb(CAST(:fora AS boolean)), true),
                        atualizado_em = CURRENT_TIMESTAMP, atualizado_por = :m
                    WHERE id = :id AND (dados -> 'identificacaoUsuarioCidadao') IS NOT NULL
                    """, p("eq", idEquipe, "micro", foraArea ? null : microarea, "microTxt", foraArea ? "" : microarea,
                    "fora", foraArea, "m", matricula, "id", id));
            n += db.exec("""
                    UPDATE aps_cidadao SET id_equipe = :eq, microarea = :micro, fora_area = :fora,
                        atualizado_em = CURRENT_TIMESTAMP, atualizado_por = :m
                    WHERE id = :id AND (dados -> 'identificacaoUsuarioCidadao') IS NULL
                    """, p("eq", idEquipe, "micro", foraArea ? null : microarea, "fora", foraArea, "m", matricula, "id", id));
        }
        return n;
    }

    /** Folha de rosto (manual 6.4.2). */
    public Map<String, Object> folhaDeRosto(Long idCidadao) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cidadao", obter(idCidadao));
        out.put("problemas", db.list("""
                SELECT * FROM aps_problema WHERE id_cidadao = :id AND tipo = 'PROBLEMA' ORDER BY situacao, atualizado_em DESC
                """, p("id", idCidadao)));
        out.put("alergias", db.list("""
                SELECT * FROM aps_problema WHERE id_cidadao = :id AND tipo = 'ALERGIA' AND situacao <> 2 ORDER BY criado_em DESC
                """, p("id", idCidadao)));
        out.put("ultimasMedicoes", db.list("""
                SELECT * FROM aps_medicao WHERE id_cidadao = :id ORDER BY data_hora DESC LIMIT 12
                """, p("id", idCidadao)));
        out.put("ultimosAtendimentos", db.list("""
                SELECT a.id, a.tipo, a.inicio, a.fim, a.profissional, a.cbo, a.status, a.unidade,
                       a.soap -> 'avaliacao' AS avaliacao, a.soap -> 'plano' AS plano
                FROM aps_atendimento a WHERE a.id_cidadao = :id AND a.status = 'FINALIZADO'
                ORDER BY a.inicio DESC LIMIT 10
                """, p("id", idCidadao)));
        out.put("gestacao", db.one("""
                SELECT * FROM aps_gestacao WHERE id_cidadao = :id AND status IN ('ATIVA', 'PUERPERIO') ORDER BY id DESC LIMIT 1
                """, p("id", idCidadao)));
        out.put("vacinasAprazadas", db.list("""
                SELECT * FROM aps_vacinacao WHERE id_cidadao = :id AND tipo_registro = 'APRAZAMENTO' AND NOT excluido
                  AND data_aprazamento IS NOT NULL ORDER BY data_aprazamento LIMIT 20
                """, p("id", idCidadao)));
        out.put("proximosAgendamentos", db.list("""
                SELECT * FROM aps_agendamento WHERE id_cidadao = :id AND data_hora >= CURRENT_DATE
                  AND status IN ('AGENDADO', 'CONFIRMADO') ORDER BY data_hora LIMIT 10
                """, p("id", idCidadao)));
        out.put("cuidadosCompartilhados", db.list("""
                SELECT * FROM aps_cuidado_compartilhado WHERE id_cidadao = :id AND status <> 'CONCLUIDO' ORDER BY criado_em DESC
                """, p("id", idCidadao)));
        Map<String, Object> c = db.one("SELECT id_paciente FROM aps_cidadao WHERE id = :id", p("id", idCidadao));
        if (c != null && c.get("id_paciente") != null) {
            out.put("hospital", db.one("""
                    SELECT COUNT(*) AS atendimentos, MAX(CAST(b.data AS date)) AS ultimo
                    FROM bam b WHERE b.paciente_idpaciente = :idp
                    """, p("idp", c.get("id_paciente"))));
        }
        return out;
    }

    /** Historico longitudinal: atendimentos APS, vacinas e registros CDS do cidadao. */
    public Map<String, Object> historico(Long idCidadao) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("atendimentos", db.list("""
                SELECT id, tipo, unidade, inicio, fim, profissional, cbo, status, registro_tardio, soap, dados
                FROM aps_atendimento WHERE id_cidadao = :id AND status = 'FINALIZADO' ORDER BY inicio DESC LIMIT 200
                """, p("id", idCidadao)));
        out.put("vacinas", db.list("""
                SELECT * FROM aps_vacinacao WHERE id_cidadao = :id AND NOT excluido ORDER BY COALESCE(data_aplicacao, data_aprazamento) DESC
                """, p("id", idCidadao)));
        out.put("registros", db.list("""
                SELECT id, tipo, data_atendimento, profissional_nome, profissional_cbo, origem, status
                FROM aps_ficha WHERE id_cidadao = :id AND status <> 'CANCELADA' ORDER BY data_atendimento DESC LIMIT 300
                """, p("id", idCidadao)));
        out.put("medicoes", db.list("SELECT * FROM aps_medicao WHERE id_cidadao = :id ORDER BY data_hora", p("id", idCidadao)));
        return out;
    }

    private Map<String, Object> duplicado(String cpf, String cns, Long ignorar) {
        if (cpf == null && cns == null) {
            return null;
        }
        return db.one("""
                SELECT id, nome FROM aps_cidadao
                WHERE ativo AND (CAST(:ignorar AS bigint) IS NULL OR id <> CAST(:ignorar AS bigint))
                  AND ((CAST(:cpf AS varchar) IS NOT NULL AND cpf = CAST(:cpf AS varchar))
                    OR (CAST(:cns AS varchar) IS NOT NULL AND cns = CAST(:cns AS varchar)))
                LIMIT 1
                """, p("ignorar", ignorar, "cpf", cpf, "cns", cns));
    }

    private static String limite(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private Long criarPacienteHospital(String nome, String nascIso, String cpf, String cns, Map<String, Object> ident) {
        PacientesDto dto = new PacientesDto();
        dto.setNome(limite(nome.toUpperCase(), 55));
        dto.setNascimento(nascIso); // salvarPaciente converte para aaaa/MM/dd (padrao da Recepcao)
        Integer sexo = intOrNull(ident.get("sexoCidadao"));
        dto.setSexo(sexo == null ? null : sexo == 0 ? "M" : sexo == 1 ? "F" : null);
        String mae = str(ident.get("nomeMaeCidadao"));
        dto.setMae(limite(mae == null ? "NAO INFORMADO" : mae.toUpperCase(), 55));
        String pai = str(ident.get("nomePaiCidadao"));
        dto.setPai(limite(pai == null ? null : pai.toUpperCase(), 55));
        dto.setCpf(cpf);
        dto.setCns(cns);
        dto.setTelefone(ApsValidacao.digitos(str(ident.get("telefoneCelular"))));
        dto.setProntuario("0");
        dto.setPacienteespecial("TIPICO");
        dto.setIdade(Math.max(0, ApsValidacao.idadeAnos(LocalDate.parse(nascIso), LocalDate.now())));
        dto.setData(LocalDate.now());
        // pacientes.hora e varchar(5): "HH:mm", como a Recepcao grava.
        dto.setHora(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        return (long) pacientesService.salvarPaciente(dto).getIdpaciente();
    }

    private Long vincularPaciente(String cpf, String cns) {
        if (cpf == null && cns == null) {
            return null;
        }
        Map<String, Object> r = db.one("""
                SELECT idpaciente FROM pacientes
                WHERE (CAST(:cpf AS varchar) IS NOT NULL AND regexp_replace(COALESCE(cpf, ''), '\\D', '', 'g') = CAST(:cpf AS varchar))
                   OR (CAST(:cns AS varchar) IS NOT NULL AND regexp_replace(COALESCE(cns, ''), '\\D', '', 'g') = CAST(:cns AS varchar))
                ORDER BY idpaciente LIMIT 1
                """, p("cpf", cpf, "cns", cns));
        return r == null ? null : ((Number) r.get("idpaciente")).longValue();
    }

    /** pacientes.nascimento e texto: recepcao grava aaaa/MM/dd, legado dd/MM/aaaa ou ISO. */
    static String dataPaciente(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        try {
            if (t.matches("\\d{4}[/-]\\d{2}[/-]\\d{2}.*")) {
                return LocalDate.parse(t.substring(0, 10).replace('/', '-')).toString();
            }
            if (t.matches("\\d{2}/\\d{2}/\\d{4}.*")) {
                String[] p = t.substring(0, 10).split("/");
                return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0])).toString();
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /** Sexo LEDI: 0 masculino, 1 feminino, 4 ignorado. */
    static Integer sexoLedi(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toUpperCase();
        if (t.startsWith("M")) {
            return 0;
        }
        if (t.startsWith("F")) {
            return 1;
        }
        return 4;
    }

    static String isoDate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue()).atZone(ApsLediSerializer.ZONA).toLocalDate().toString();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.matches("\\d+")) {
            return Instant.ofEpochMilli(Long.parseLong(s)).atZone(ApsLediSerializer.ZONA).toLocalDate().toString();
        }
        String d = dataPaciente(s);
        if (d == null) {
            throw new IllegalArgumentException("Data invalida: " + s);
        }
        return d;
    }

    static LocalDate dataRegistro(Object v) {
        String s = isoDate(v);
        return s == null ? LocalDate.now() : LocalDate.parse(s);
    }

    static Integer intOrNull(Object o) {
        Long l = num(o);
        return l == null ? null : l.intValue();
    }
}

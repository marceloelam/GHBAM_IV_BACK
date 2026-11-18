package br.com.iv.aps;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import br.com.iv.entity.ConfiguracaoUnidade;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Migracao de CIDADAOS do banco do PEC para o G-PEC (decisao de Marcelo 18/11/2026: so cidadaos,
 * nada de historico clinico, nada transmitido ao SISAB).
 *
 * Etapa 1 = PREVIA: so leitura, devolve apenas METADADOS (tabelas/colunas do PEC) e CONTAGENS
 * agregadas. Nunca devolve nome, CPF, CNS ou qualquer dado que identifique cidadao. O esquema do PEC
 * varia por versao, por isso as colunas sao descobertas pelo information_schema, nao presumidas.
 */
@ApplicationScoped
public class ApsMigracaoPecService {

    /** Tabelas do PEC que interessam para cidadao e vinculo com equipe/unidade. */
    static final List<String> TABELAS = List.of("tb_cidadao", "tb_cidadao_vinculacao_equipe", "tb_equipe",
            "tb_unidade_saude", "tb_fat_cidadao_pec", "tb_dim_equipe", "tb_dim_unidade_saude",
            "tb_cds_cad_individual", "tb_fat_cad_individual");

    static final List<String> COL_CPF = List.of("nu_cpf", "nu_cpf_cidadao");
    static final List<String> COL_CNS = List.of("nu_cns", "nu_cns_cidadao");
    static final List<String> COL_NASC = List.of("dt_nascimento", "dt_nascimento_cidadao");
    static final List<String> COL_NOME = List.of("no_cidadao", "no_cidadao_filtro");
    static final List<String> COL_OBITO = List.of("st_faleceu", "dt_obito");
    static final List<String> COL_ATIVO = List.of("st_ativo");
    static final List<String> COL_INE = List.of("nu_ine");

    @Inject
    ApsIntegracaoService integracoes;
    @Inject
    ApsDb db;
    @Inject
    ApsContextoService contexto;
    @Inject
    EntityManager entityManager;
    @Inject
    br.com.iv.plataforma.municipio.MunicipioRoteamento municipioRoteamento;

    public Map<String, Object> previa() {
        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection con = integracoes.abrirPec(300)) {
            con.setAutoCommit(false); // o driver do PostgreSQL so le em lotes (fetchSize) fora do autocommit
            Map<String, List<String>> colunas = colunas(con);
            out.put("colunas", colunas);
            out.put("linhasEstimadas", linhasEstimadas(con));

            List<String> cid = colunas.getOrDefault("tb_cidadao", List.of());
            Map<String, String> mapa = new LinkedHashMap<>();
            mapa.put("nome", escolher(cid, COL_NOME));
            mapa.put("cpf", escolher(cid, COL_CPF));
            mapa.put("cns", escolher(cid, COL_CNS));
            mapa.put("nascimento", escolher(cid, COL_NASC));
            mapa.put("obito", escolher(cid, COL_OBITO));
            mapa.put("ativo", escolher(cid, COL_ATIVO));
            out.put("mapeamento", mapa);

            if (cid.isEmpty()) {
                out.put("ok", false);
                out.put("mensagem", "Tabela tb_cidadao nao encontrada no banco do PEC (ou o usuario nao tem acesso a ela).");
                return out;
            }
            if (mapa.get("cpf") == null && mapa.get("cns") == null) {
                out.put("ok", false);
                out.put("mensagem", "tb_cidadao sem coluna de CPF/CNS reconhecida: veja a lista de colunas.");
                return out;
            }
            out.put("cidadaos", contarCidadaos(con, mapa));
            out.put("equipes", compararEquipes(con, colunas));
            out.put("ok", true);
            out.put("mensagem", "Previa concluida. Nada foi gravado.");
            con.rollback();
        } catch (IllegalStateException e) {
            out.put("ok", false);
            out.put("mensagem", e.getMessage());
        } catch (Exception e) {
            out.put("ok", false);
            out.put("mensagem", "Falha ao ler o banco do PEC: " + e.getMessage());
        }
        return out;
    }

    private Map<String, List<String>> colunas(Connection con) throws Exception {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT table_name, column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ANY (?)
                ORDER BY table_name, ordinal_position""")) {
            ps.setArray(1, con.createArrayOf("varchar", TABELAS.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                }
            }
        }
        return out;
    }

    private Map<String, Long> linhasEstimadas(Connection con) throws Exception {
        Map<String, Long> out = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT c.relname, CAST(c.reltuples AS bigint) FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname = ANY (?)""")) {
            ps.setArray(1, con.createArrayOf("varchar", TABELAS.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        return out;
    }

    /**
     * Percorre tb_cidadao (so CPF/CNS/nascimento/obito/ativo) e cruza com aps_cidadao e pacientes do
     * GHBAM em memoria. Devolve so contagens.
     */
    private Map<String, Object> contarCidadaos(Connection con, Map<String, String> mapa) throws Exception {
        Set<String> gpec = new HashSet<>();
        for (Map<String, Object> r : db.list("SELECT cpf, cns FROM aps_cidadao WHERE ativo")) {
            adicionar(gpec, (String) r.get("cpf"));
            adicionar(gpec, (String) r.get("cns"));
        }
        Set<String> hospital = new HashSet<>();
        for (Map<String, Object> r : db.list("SELECT cpf, cns FROM pacientes")) {
            adicionar(hospital, (String) r.get("cpf"));
            adicionar(hospital, (String) r.get("cns"));
        }

        String sql = "SELECT CAST(" + col(mapa, "cpf") + " AS text), CAST(" + col(mapa, "cns") + " AS text), "
                + "(" + col(mapa, "nascimento") + ") IS NOT NULL, CAST(" + col(mapa, "obito") + " AS text), "
                + "CAST(" + col(mapa, "ativo") + " AS text) FROM tb_cidadao";

        long total = 0, cpfOk = 0, cnsOk = 0, semDoc = 0, semNasc = 0, falecidos = 0, inativos = 0,
                jaNoGpec = 0, soNoHospital = 0, novos = 0, docRepetido = 0;
        Set<String> vistos = new HashSet<>();
        try (Statement st = con.createStatement()) {
            st.setFetchSize(5000);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    total++;
                    String c = ApsValidacao.digitos(rs.getString(1));
                    String n = ApsValidacao.digitos(rs.getString(2));
                    boolean vc = ApsValidacao.cpfValido(c);
                    boolean vn = ApsValidacao.cnsValido(n);
                    if (vc) {
                        cpfOk++;
                    }
                    if (vn) {
                        cnsOk++;
                    }
                    if (!rs.getBoolean(3)) {
                        semNasc++;
                    }
                    if (verdadeiro(rs.getString(4))) {
                        falecidos++;
                    }
                    String a = rs.getString(5);
                    if (a != null && !verdadeiro(a)) {
                        inativos++;
                    }
                    if (!vc && !vn) {
                        semDoc++;
                        continue;
                    }
                    if (!vistos.add(vc ? c : n)) {
                        docRepetido++;
                    }
                    if ((vc && gpec.contains(c)) || (vn && gpec.contains(n))) {
                        jaNoGpec++;
                    } else if ((vc && hospital.contains(c)) || (vn && hospital.contains(n))) {
                        soNoHospital++;
                    } else {
                        novos++;
                    }
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("cpfValido", cpfOk);
        out.put("cnsValido", cnsOk);
        out.put("semCpfNemCns", semDoc);
        out.put("semNascimento", semNasc);
        out.put("falecidos", falecidos);
        out.put("inativos", inativos);
        out.put("documentoRepetidoNoPec", docRepetido);
        out.put("jaNoGpec", jaNoGpec);
        out.put("jaNoHospital", soNoHospital);
        out.put("novos", novos);
        return out;
    }

    /** INE das equipes do PEC x equipes cadastradas no G-PEC (INE identifica equipe, nao pessoa). */
    private Map<String, Object> compararEquipes(Connection con, Map<String, List<String>> colunas) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        Set<String> gpec = new TreeSet<>();
        for (Map<String, Object> r : db.list("SELECT ine FROM aps_equipe WHERE ativo")) {
            adicionar(gpec, (String) r.get("ine"));
        }
        out.put("noGpec", gpec.size());
        for (String tabela : List.of("tb_equipe", "tb_dim_equipe")) {
            String c = escolher(colunas.getOrDefault(tabela, List.of()), COL_INE);
            if (c == null) {
                continue;
            }
            Set<String> pec = new TreeSet<>();
            try (Statement st = con.createStatement();
                    ResultSet rs = st.executeQuery("SELECT DISTINCT CAST(" + q(c) + " AS text) FROM " + q(tabela))) {
                while (rs.next()) {
                    adicionar(pec, rs.getString(1));
                }
            }
            Set<String> comuns = new TreeSet<>(pec);
            comuns.retainAll(gpec);
            Set<String> faltam = new TreeSet<>(gpec);
            faltam.removeAll(pec);
            out.put("tabela", tabela);
            out.put("noPec", pec.size());
            out.put("emComum", comuns);
            out.put("gpecSemCorrespondente", faltam);
            break;
        }
        return out;
    }

    // ================================================================== equipes

    /** Codigo de tipo de equipe do CNES (tb_equipe.tp_equipe) -> sigla usada no G-PEC. */
    static final Map<String, String> TIPO_EQUIPE = Map.of("70", "ESF", "71", "ESB", "72", "EMULTI", "73", "ECR",
            "74", "EAPP", "76", "EAP", "22", "EMAD", "23", "EMAP");

    /**
     * Equipes do PEC com a unidade (CNES) e a sugestao de unidade do GHBAM pelo mesmo CNES em
     * Configuracao de Unidades. INE/CNES/nome de equipe nao identificam cidadao.
     */
    public Map<String, Object> equipes() {
        Map<String, String> siglaPorCnes = new LinkedHashMap<>();
        List<Map<String, Object>> unidades = new ArrayList<>();
        for (ConfiguracaoUnidade u : entityManager
                .createQuery("SELECT c FROM configurarunidade c ORDER BY c.sigla", ConfiguracaoUnidade.class).getResultList()) {
            String cnes = ApsValidacao.digitos(u.getCnesDoHospital());
            if (u.getSigla() == null || u.getSigla().isBlank()) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sigla", u.getSigla());
            m.put("nome", u.getHospital());
            m.put("cnes", cnes);
            unidades.add(m);
            if (cnes != null && cnes.length() == 7) {
                siglaPorCnes.putIfAbsent(cnes, u.getSigla());
            }
        }
        Map<String, Map<String, Object>> noGpec = new LinkedHashMap<>();
        for (Map<String, Object> r : db.list("SELECT id, ine, unidade, ativo FROM aps_equipe")) {
            noGpec.put((String) r.get("ine"), r);
        }
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection con = integracoes.abrirPec(120); Statement st = con.createStatement()) {
            Map<String, Long> porIne = new LinkedHashMap<>();
            try (ResultSet rs = st.executeQuery("""
                    SELECT LPAD(CAST(nu_ine AS text), 10, '0'), COUNT(DISTINCT co_cidadao) FROM tb_cidadao_vinculacao_equipe
                    WHERE nu_ine IS NOT NULL GROUP BY 1""")) {
                while (rs.next()) {
                    porIne.put(ApsValidacao.digitos(rs.getString(1)), rs.getLong(2));
                }
            }
            try (ResultSet rs = st.executeQuery("""
                    SELECT LPAD(CAST(e.nu_ine AS text), 10, '0'), e.no_equipe, CAST(e.tp_equipe AS text), CAST(e.st_ativo AS text),
                           LPAD(CAST(u.nu_cnes AS text), 7, '0'), u.no_unidade_saude
                    FROM tb_equipe e LEFT JOIN tb_unidade_saude u ON u.co_seq_unidade_saude = e.co_unidade_saude
                    ORDER BY u.no_unidade_saude, e.no_equipe""")) {
                while (rs.next()) {
                    String ine = ApsValidacao.digitos(rs.getString(1));
                    if (ine == null || ine.isEmpty()) {
                        continue;
                    }
                    String cnes = ApsValidacao.digitos(rs.getString(5));
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ine", ine);
                    m.put("nome", rs.getString(2));
                    m.put("tipoPec", rs.getString(3));
                    m.put("tipo", TIPO_EQUIPE.getOrDefault(rs.getString(3), "ESF"));
                    m.put("ativaNoPec", verdadeiro(rs.getString(4)));
                    m.put("cnes", cnes);
                    m.put("unidadePec", rs.getString(6));
                    m.put("cidadaos", porIne.getOrDefault(ine, 0L));
                    m.put("unidadeSugerida", cnes == null ? null : siglaPorCnes.get(cnes));
                    Map<String, Object> g = noGpec.get(ine);
                    m.put("noGpec", g != null);
                    m.put("unidadeGpec", g == null ? null : g.get("unidade"));
                    lista.add(m);
                }
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler as equipes do PEC: " + e.getMessage(), e);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipes", lista);
        out.put("unidades", unidades);
        return out;
    }

    /**
     * Cria no G-PEC as equipes escolhidas (itens: {ine, unidade}). Nome/tipo/CNES vem do PEC; a unidade
     * do GHBAM precisa ter o MESMO CNES (senao a ficha sairia com CNES errado no cabecalho LEDI).
     * Equipe ja existente (mesmo INE) nao e alterada.
     */
    public Map<String, Object> importarEquipes(List<Map<String, Object>> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Selecione ao menos uma equipe.");
        }
        Map<String, Map<String, Object>> pec = new LinkedHashMap<>();
        for (Object o : (List<?>) equipes().get("equipes")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            pec.put((String) m.get("ine"), m);
        }
        int criadas = 0;
        List<String> recusadas = new ArrayList<>();
        for (Map<String, Object> item : itens) {
            String ine = ApsValidacao.digitos(ApsConfiguracaoService.str(item.get("ine")));
            String sigla = ApsConfiguracaoService.str(item.get("unidade"));
            Map<String, Object> e = pec.get(ine);
            if (e == null || sigla == null) {
                recusadas.add(ine + ": equipe ou unidade nao informada");
                continue;
            }
            if (Boolean.TRUE.equals(e.get("noGpec"))) {
                continue;
            }
            String cnesUnidade = contexto.unidade(sigla).cnes();
            if (cnesUnidade == null || !cnesUnidade.equals(e.get("cnes"))) {
                recusadas.add(ine + " (" + e.get("nome") + "): a unidade " + sigla + " tem CNES " + cnesUnidade
                        + " e a equipe e do CNES " + e.get("cnes"));
                continue;
            }
            String nome = (String) e.get("nome");
            criadas += db.exec("""
                    INSERT INTO aps_equipe (unidade, cnes, ine, nome, tipo, ativo)
                    VALUES (:unidade, :cnes, :ine, :nome, :tipo, :ativo) ON CONFLICT (ine) DO NOTHING
                    """, ApsDb.p("unidade", contexto.unidade(sigla).sigla(), "cnes", cnesUnidade, "ine", ine,
                    "nome", nome == null ? "EQUIPE " + ine : nome.length() > 120 ? nome.substring(0, 120) : nome,
                    "tipo", e.get("tipo"), "ativo", Boolean.TRUE.equals(e.get("ativaNoPec"))));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("criadas", criadas);
        out.put("recusadas", recusadas);
        return out;
    }

    // ================================================================== cidadaos

    private final AtomicBoolean rodando = new AtomicBoolean(false);
    private volatile Map<String, Object> status = Map.of("etapa", "PARADO");

    public Map<String, Object> status() {
        return status;
    }

    /**
     * Importa (ou simula) os cidadaos vinculados, no PEC, as equipes ja cadastradas no G-PEC
     * (opcionalmente so as da unidade informada = piloto). Roda em segundo plano; acompanhar por status().
     * Regras: CPF/CNS valido obrigatorio; ignora unificado/inativo/falecido/saida; ja existente no G-PEC
     * so tem campos VAZIOS completados; mesmo documento com outra data de nascimento = conflito, nao junta;
     * vincula id_paciente do hospital por CPF/CNS sem criar paciente; nao gera ficha (nada vai ao SISAB).
     */
    public Map<String, Object> iniciarCidadaos(boolean simular, String unidade) {
        if (!rodando.compareAndSet(false, true)) {
            throw new IllegalStateException("Ja ha uma migracao de cidadaos em andamento.");
        }
        try {
            DataSource ds = municipioRoteamento.dataSourceAtual(); // capturado aqui: a thread nao tem requisicao
            Map<String, Long[]> equipes = new LinkedHashMap<>(); // ine -> {id}
            Map<String, String> unidadePorIne = new LinkedHashMap<>();
            for (Map<String, Object> r : db.list("""
                    SELECT id, ine, unidade FROM aps_equipe
                    WHERE ativo AND (CAST(:u AS varchar) IS NULL OR UPPER(unidade) = UPPER(CAST(:u AS varchar)))
                    """, ApsDb.p("u", ApsFichaService.vazioParaNulo(unidade)))) {
                equipes.put((String) r.get("ine"), new Long[] { ((Number) r.get("id")).longValue() });
                unidadePorIne.put((String) r.get("ine"), (String) r.get("unidade"));
            }
            if (equipes.isEmpty()) {
                throw new IllegalArgumentException(unidade == null || unidade.isBlank()
                        ? "Nenhuma equipe ativa no G-PEC: importe as equipes do PEC primeiro."
                        : "A unidade " + unidade + " nao tem equipe ativa no G-PEC: importe as equipes dela primeiro.");
            }
            Map<String, Object> s = novoStatus(simular, unidade, equipes.size());
            status = s;
            Thread t = new Thread(() -> {
                try {
                    executarCidadaos(ds, simular, equipes, unidadePorIne, s);
                    s.put("etapa", "CONCLUIDO");
                } catch (Exception e) {
                    s.put("etapa", "ERRO");
                    s.put("erro", String.valueOf(e.getMessage()));
                    org.jboss.logging.Logger.getLogger(ApsMigracaoPecService.class).error("Migracao de cidadaos do PEC falhou", e);
                } finally {
                    s.put("fim", java.time.LocalDateTime.now().toString());
                    rodando.set(false);
                }
            }, "gpec-migracao-cidadaos");
            t.setDaemon(true);
            t.start();
            return s;
        } catch (RuntimeException e) {
            rodando.set(false);
            throw e;
        }
    }

    private static Map<String, Object> novoStatus(boolean simular, String unidade, int equipes) {
        Map<String, Object> s = new java.util.concurrent.ConcurrentHashMap<>();
        s.put("etapa", "LENDO");
        s.put("simulacao", simular);
        s.put("unidade", unidade == null || unidade.isBlank() ? "TODAS" : unidade.toUpperCase());
        s.put("equipes", equipes);
        s.put("inicio", java.time.LocalDateTime.now().toString());
        s.put("contagens", new java.util.concurrent.ConcurrentHashMap<String, Long>());
        return s;
    }

    @SuppressWarnings("unchecked")
    private void executarCidadaos(DataSource ds, boolean simular, Map<String, Long[]> equipes,
            Map<String, String> unidadePorIne, Map<String, Object> s) throws Exception {
        Map<String, Long> cont = (Map<String, Long>) s.get("contagens");
        java.util.function.Consumer<String> mais = k -> cont.merge(k, 1L, Long::sum);

        // Estado atual do GHBAM em memoria (documento -> cidadao; documento -> paciente do hospital).
        Map<String, Object[]> gpec = new java.util.HashMap<>(); // doc -> {id, nascimento}
        try (Connection g = ds.getConnection(); Statement st = g.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, cpf, cns, CAST(data_nascimento AS varchar) FROM aps_cidadao WHERE ativo")) {
            while (rs.next()) {
                Object[] v = { rs.getLong(1), rs.getString(4) };
                if (rs.getString(2) != null) {
                    gpec.put(rs.getString(2), v);
                }
                if (rs.getString(3) != null) {
                    gpec.put(rs.getString(3), v);
                }
            }
        }
        Map<String, Integer> hospital = new java.util.HashMap<>();
        try (Connection g = ds.getConnection(); Statement st = g.createStatement();
                ResultSet rs = st.executeQuery("SELECT idpaciente, cpf, cns FROM pacientes ORDER BY idpaciente DESC")) {
            while (rs.next()) { // DESC + put = fica o menor idpaciente, como vincularPaciente
                String c = ApsValidacao.digitos(rs.getString(2));
                String n = ApsValidacao.digitos(rs.getString(3));
                if (ApsValidacao.cpfValido(c)) {
                    hospital.put(c, rs.getInt(1));
                }
                if (ApsValidacao.cnsValido(n)) {
                    hospital.put(n, rs.getInt(1));
                }
            }
        }

        String sql = """
                SELECT DISTINCT ON (c.co_seq_cidadao)
                       CAST(c.nu_cpf AS text), CAST(c.nu_cns AS text), c.no_cidadao, c.no_social, CAST(c.dt_nascimento AS date),
                       c.no_sexo, CAST(c.co_raca_cor AS text), c.no_mae, CAST(c.st_desconhece_nome_mae AS text),
                       c.nu_telefone_celular, c.nu_telefone_residencial, c.nu_telefone_contato,
                       CAST(c.nu_micro_area AS text), CAST(c.st_fora_area AS text), CAST(c.st_faleceu AS text),
                       CAST(c.dt_obito AS text), CAST(c.st_ativo AS text), CAST(c.st_unificado AS text),
                       LPAD(CAST(v.nu_ine AS text), 10, '0'), CAST(v.st_saida_cadastro_obito AS text), CAST(v.st_saida_cadastro_territorio AS text),
                       v.co_unico_ficha, f.co_unico_ficha_origem
                FROM tb_cidadao c
                JOIN tb_cidadao_vinculacao_equipe v ON v.co_cidadao = c.co_seq_cidadao
                LEFT JOIN tb_cds_cad_individual f ON f.co_unico_ficha = v.co_unico_ficha
                WHERE LPAD(CAST(v.nu_ine AS text), 10, '0') = ANY (?)
                ORDER BY c.co_seq_cidadao, v.dt_atualizacao_cadastro DESC NULLS LAST""";

        String insert = """
                INSERT INTO aps_cidadao (id_paciente, cpf, cns, nome, nome_social, data_nascimento, sexo, raca_cor, nome_mae,
                    telefone, unidade, id_equipe, microarea, fora_area, uuid_ficha_origem, uuid_ultima_ficha, criado_por, atualizado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MIGRACAO-PEC', 'MIGRACAO-PEC') RETURNING id""";
        String update = """
                UPDATE aps_cidadao SET id_paciente = COALESCE(id_paciente, ?), cpf = COALESCE(cpf, ?), cns = COALESCE(cns, ?),
                    nome_social = COALESCE(nome_social, ?), data_nascimento = COALESCE(data_nascimento, ?),
                    sexo = COALESCE(sexo, ?), raca_cor = COALESCE(raca_cor, ?), nome_mae = COALESCE(nome_mae, ?),
                    telefone = COALESCE(telefone, ?),
                    -- equipe/microarea so quando o cidadao e da MESMA unidade da equipe (nunca de outra UBS)
                    id_equipe = CASE WHEN UPPER(unidade) = UPPER(CAST(? AS varchar)) THEN COALESCE(id_equipe, ?) ELSE id_equipe END,
                    microarea = CASE WHEN UPPER(unidade) = UPPER(CAST(? AS varchar)) AND id_equipe IS NULL THEN COALESCE(microarea, ?) ELSE microarea END,
                    uuid_ficha_origem = COALESCE(uuid_ficha_origem, ?), uuid_ultima_ficha = COALESCE(uuid_ultima_ficha, ?),
                    atualizado_em = CURRENT_TIMESTAMP, atualizado_por = 'MIGRACAO-PEC'
                WHERE id = ?""";

        try (Connection pec = integracoes.abrirPec(1800); Connection g = ds.getConnection();
                PreparedStatement ins = g.prepareStatement(insert); PreparedStatement upd = g.prepareStatement(update)) {
            pec.setAutoCommit(false);
            s.put("etapa", simular ? "SIMULANDO" : "IMPORTANDO");
            try (PreparedStatement ps = pec.prepareStatement(sql)) {
                ps.setFetchSize(2000);
                ps.setArray(1, pec.createArrayOf("varchar", equipes.keySet().toArray()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        mais.accept("lidos");
                        if (verdadeiro(rs.getString(18))) {
                            mais.accept("ignoradosUnificados");
                            continue;
                        }
                        if (rs.getString(17) != null && !verdadeiro(rs.getString(17))) {
                            mais.accept("ignoradosInativos");
                            continue;
                        }
                        if (verdadeiro(rs.getString(15)) || rs.getString(16) != null || verdadeiro(rs.getString(20))) {
                            mais.accept("ignoradosFalecidos");
                            continue;
                        }
                        if (verdadeiro(rs.getString(21))) {
                            mais.accept("ignoradosMudaramDeTerritorio");
                            continue;
                        }
                        String cpf = ApsValidacao.digitos(rs.getString(1));
                        String cns = ApsValidacao.digitos(rs.getString(2));
                        cpf = ApsValidacao.cpfValido(cpf) ? cpf : null;
                        cns = ApsValidacao.cnsValido(cns) ? cns : null;
                        String nome = limite(rs.getString(3), 160);
                        java.sql.Date nasc = rs.getDate(5);
                        if (cpf == null && cns == null) {
                            mais.accept("ignoradosSemCpfCns");
                            continue;
                        }
                        if (nome == null || nome.isBlank() || nasc == null) {
                            mais.accept("ignoradosSemNomeOuNascimento");
                            continue;
                        }
                        String ine = ApsValidacao.digitos(rs.getString(19));
                        Long idEquipe = equipes.get(ine)[0];
                        String micro = rs.getString(13);
                        micro = micro != null && micro.trim().matches("\\d{1,2}") ? String.format("%02d", Integer.parseInt(micro.trim())) : null;
                        boolean fora = verdadeiro(rs.getString(14));
                        String mae = verdadeiro(rs.getString(9)) ? null : limite(rs.getString(8), 160);
                        String tel = ApsCidadaoService.telefoneImportado(primeiro(rs.getString(10), rs.getString(11), rs.getString(12)));
                        Integer sexo = ApsCidadaoService.sexoLedi(rs.getString(6));
                        Integer raca = racaCor(rs.getString(7));
                        String uuidUltima = uuid(rs.getString(22));
                        String uuidOrigem = uuid(primeiro(rs.getString(23), rs.getString(22)));
                        Integer idPaciente = cpf != null && hospital.containsKey(cpf) ? hospital.get(cpf)
                                : cns != null ? hospital.get(cns) : null;

                        Object[] existente = cpf != null && gpec.containsKey(cpf) ? gpec.get(cpf) : cns != null ? gpec.get(cns) : null;
                        if (existente != null) {
                            String nascExistente = (String) existente[1];
                            if (nascExistente != null && !nascExistente.equals(nasc.toString())) {
                                mais.accept("conflitosDocumentoOutraPessoa");
                                continue;
                            }
                            long id = (Long) existente[0];
                            // Documento que ja pertence a OUTRO cidadao do G-PEC nao e copiado.
                            String cpfNovo = cpf != null && (gpec.get(cpf) == null || gpec.get(cpf) == existente) ? cpf : null;
                            String cnsNovo = cns != null && (gpec.get(cns) == null || gpec.get(cns) == existente) ? cns : null;
                            if (!simular) {
                                int i = 1;
                                setInt(upd, i++, idPaciente);
                                upd.setString(i++, cpfNovo);
                                upd.setString(i++, cnsNovo);
                                upd.setString(i++, limite(rs.getString(4), 160));
                                upd.setDate(i++, nasc);
                                setInt(upd, i++, sexo);
                                setInt(upd, i++, raca);
                                upd.setString(i++, mae);
                                upd.setString(i++, tel);
                                upd.setString(i++, unidadePorIne.get(ine));
                                upd.setLong(i++, idEquipe);
                                upd.setString(i++, unidadePorIne.get(ine));
                                upd.setString(i++, fora ? null : micro);
                                upd.setString(i++, uuidOrigem);
                                upd.setString(i++, uuidUltima);
                                upd.setLong(i, id);
                                upd.executeUpdate();
                            }
                            if (cpfNovo != null) {
                                gpec.put(cpfNovo, existente);
                            }
                            if (cnsNovo != null) {
                                gpec.put(cnsNovo, existente);
                            }
                            mais.accept("completados");
                            continue;
                        }
                        long id = -1;
                        if (!simular) {
                            int i = 1;
                            setInt(ins, i++, idPaciente);
                            ins.setString(i++, cpf);
                            ins.setString(i++, cns);
                            ins.setString(i++, nome);
                            ins.setString(i++, limite(rs.getString(4), 160));
                            ins.setDate(i++, nasc);
                            setInt(ins, i++, sexo);
                            setInt(ins, i++, raca);
                            ins.setString(i++, mae);
                            ins.setString(i++, tel);
                            ins.setString(i++, unidadePorIne.get(ine));
                            ins.setLong(i++, idEquipe);
                            ins.setString(i++, fora ? null : micro);
                            ins.setBoolean(i++, fora);
                            ins.setString(i++, uuidOrigem);
                            ins.setString(i, uuidUltima);
                            try (ResultSet r = ins.executeQuery()) {
                                r.next();
                                id = r.getLong(1);
                            }
                        }
                        Object[] novo = { id, nasc.toString() };
                        if (cpf != null) {
                            gpec.put(cpf, novo);
                        }
                        if (cns != null) {
                            gpec.put(cns, novo);
                        }
                        mais.accept(idPaciente != null ? "criadosVinculadosAoHospital" : "criadosNovos");
                    }
                }
            }
            pec.rollback();
        }
    }

    /** LEDI raca/cor: 1 branca, 2 preta, 3 parda, 4 amarela, 5 indigena, 6 sem informacao. */
    static Integer racaCor(String v) {
        String d = ApsValidacao.digitos(v);
        if (d == null || d.isEmpty()) {
            return null;
        }
        int n = Integer.parseInt(d);
        return n >= 1 && n <= 6 ? n : null;
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws java.sql.SQLException {
        if (v == null) {
            ps.setNull(i, java.sql.Types.INTEGER);
        } else {
            ps.setInt(i, v);
        }
    }

    private static String uuid(String v) {
        return v == null || v.isBlank() || v.length() > 44 ? null : v.trim();
    }

    private static String primeiro(String... v) {
        for (String s : v) {
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private static String limite(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t.length() > max ? t.substring(0, max) : t;
    }

    private static String col(Map<String, String> mapa, String campo) {
        String c = mapa.get(campo);
        return c == null ? "NULL" : q(c);
    }

    private static void adicionar(Set<String> s, String v) {
        String d = ApsValidacao.digitos(v);
        if (d != null && !d.isEmpty()) {
            s.add(d);
        }
    }

    /** st_* do PEC costuma ser 0/1; dt_obito preenchido tambem conta como obito. */
    static boolean verdadeiro(String v) {
        if (v == null) {
            return false;
        }
        String t = v.trim().toLowerCase();
        return t.equals("1") || t.equals("t") || t.equals("true") || t.matches("\\d{4}-\\d{2}-\\d{2}.*");
    }

    static String escolher(List<String> existentes, List<String> candidatas) {
        for (String c : candidatas) {
            if (existentes.contains(c)) {
                return c;
            }
        }
        return null;
    }

    /** Identificadores vem de listas fixas desta classe; a regex e as aspas sao so garantia extra. */
    static String q(String ident) {
        if (!ident.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("Identificador inesperado: " + ident);
        }
        return "\"" + ident + "\"";
    }
}

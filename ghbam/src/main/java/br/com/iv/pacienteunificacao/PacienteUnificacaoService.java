package br.com.iv.pacienteunificacao;

import br.com.iv.dto.LogDto;
import br.com.iv.service.LogService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unificacao de cadastros duplicados do paciente.
 *
 * Diferente da antiga opcao 5 da Correcao de BAM: os candidatos saem de CPF, CNS ou
 * nome + (mae ou nascimento) normalizados, nunca de pedaco de nome; o operador ve a previa
 * e escolhe qual cadastro fica; TODAS as colunas que guardam o id do paciente sao movidas
 * (descobertas no catalogo do banco, para nao depender de lista fixa); e o cadastro
 * duplicado nao e apagado, fica com pacientes.unificado_em apontando para o que ficou.
 */
@ApplicationScoped
public class PacienteUnificacaoService {

    /** Nomes de coluna usados no sistema para o id do paciente (entidades e SQL legado). */
    private static final List<String> COLUNAS_PACIENTE = List.of(
            "idpaciente", "iddopaciente", "paciente_idpaciente", "pacientes_idpaciente",
            "paciente_id", "idpacientes", "id_paciente");

    /** Trilhas de auditoria e acesso guardam o id que existia na hora: nao sao reescritas. */
    private static final String TABELAS_PRESERVADAS = "(^|_)(log|logs|audit|auditoria|shadow)($|_)|lgpd|unificacao";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject
    EntityManager em;

    @Inject
    LogService logService;

    // ---- Candidatos ----

    public Map<String, Object> candidatos(int idpaciente) {
        Object[] p = paciente(idpaciente);
        if (p == null) throw new IllegalArgumentException("Paciente nao encontrado.");
        // CPF/CNS genericos ("0000", "000.000.000-00") existem aos milhares no cadastro:
        // so contam quando tem o tamanho certo, nao sao um digito repetido e nao se repetem demais.
        String cpf = documentoConfiavel("cpf", digitos(p[4]), 11);
        String cns = documentoConfiavel("cns", digitos(p[5]), 15);
        String nome = normalizar(p[1]);
        String mae = normalizar(p[2]);
        String[] nasc = datasPossiveis(p[3]);
        if (nome.isEmpty()) throw new IllegalArgumentException("Paciente sem nome: nao ha como procurar duplicidade.");

        String norm = "upper(regexp_replace(translate(coalesce(%s,''), "
                + "'áàâãäéèêëíìîïóòôõöúùûüçÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇ', "
                + "'aaaaaeeeeiiiiooooouuuucAAAAAEEEEIIIIOOOOOUUUUC'), '[^A-Za-z]', '', 'g'))";
        String sql = "SELECT p.idpaciente, p.nome, p.mae, p.nascimento, p.cpf, p.cns, p.prontuario, "
                + " (SELECT count(*) FROM bam b WHERE b.paciente_idpaciente = p.idpaciente), "
                + " (SELECT max(b.data) FROM bam b WHERE b.paciente_idpaciente = p.idpaciente) "
                + "FROM pacientes p WHERE p.idpaciente <> :id AND p.unificado_em IS NULL AND ("
                + "   (:cpf <> '' AND regexp_replace(coalesce(p.cpf,''), '\\D', '', 'g') = :cpf)"
                + "   OR (:cns <> '' AND regexp_replace(coalesce(p.cns,''), '\\D', '', 'g') = :cns)"
                + "   OR (" + String.format(norm, "p.nome") + " = :nome AND ("
                + "        (:mae <> '' AND " + String.format(norm, "p.mae") + " = :mae)"
                + "        OR (:nasc1 <> '' AND regexp_replace(coalesce(p.nascimento,''), '\\D', '', 'g') IN (:nasc1, :nasc2))))"
                + ") ORDER BY p.idpaciente LIMIT 20";
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(sql)
                .setParameter("id", idpaciente)
                .setParameter("cpf", cpf)
                .setParameter("cns", cns)
                .setParameter("nome", nome)
                .setParameter("mae", mae)
                .setParameter("nasc1", nasc[0])
                .setParameter("nasc2", nasc[1])
                .getResultList();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("atual", cadastro(p, contagemBam(idpaciente)));
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Object[] l : linhas) {
            Map<String, Object> c = cadastro(l, new Object[] {l[7], l[8]});
            List<String> motivos = new ArrayList<>();
            if (!cpf.isEmpty() && cpf.equals(digitos(l[4]))) motivos.add("mesmo CPF");
            if (!cns.isEmpty() && cns.equals(digitos(l[5]))) motivos.add("mesmo CNS");
            if (nome.equals(normalizar(l[1]))) motivos.add("mesmo nome");
            if (!mae.isEmpty() && mae.equals(normalizar(l[2]))) motivos.add("mesma mãe");
            String[] n2 = datasPossiveis(l[3]);
            if (!nasc[0].isEmpty() && nasc[0].equals(n2[0])) motivos.add("mesmo nascimento");
            c.put("motivos", motivos);
            lista.add(c);
        }
        r.put("candidatos", lista);
        return r;
    }

    // ---- Previa ----

    public Map<String, Object> previa(int origem, int destino) {
        validarPar(origem, destino);
        List<Map<String, Object>> movidas = new ArrayList<>();
        for (String[] col : colunas()) {
            long n = contar(col, origem);
            if (n == 0) continue;
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("tabela", col[0]);
            l.put("coluna", col[1]);
            l.put("registros", n);
            movidas.add(l);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("origem", cadastro(paciente(origem), contagemBam(origem)));
        r.put("destino", cadastro(paciente(destino), contagemBam(destino)));
        r.put("tabelas", movidas);
        r.put("total", movidas.stream().mapToLong(m -> (Long) m.get("registros")).sum());
        return r;
    }

    // ---- Unificar ----

    @Transactional
    public Map<String, Object> unificar(int origem, int destino, String motivo, String matricula, String unidade) {
        if (motivo == null || motivo.trim().length() < 10) {
            throw new IllegalArgumentException("Descreva o motivo da unificação (mínimo 10 caracteres).");
        }
        // Trava os dois cadastros: ninguem altera nem unifica no meio da operacao.
        em.createNativeQuery("SELECT idpaciente FROM pacientes WHERE idpaciente IN (:a, :b) ORDER BY idpaciente FOR UPDATE")
                .setParameter("a", origem).setParameter("b", destino).getResultList();
        validarPar(origem, destino);

        Map<String, Long> movidos = new LinkedHashMap<>();
        for (String[] col : colunas()) {
            String sql = "UPDATE " + ident(col[0]) + " SET " + ident(col[1]) + " = :destino WHERE " + ident(col[1]) + " = :origem";
            Query q = em.createNativeQuery(sql);
            vincular(q, col[2], destino, origem);
            int n = q.executeUpdate();
            if (n > 0) movidos.put(col[0] + "." + col[1], (long) n);
        }

        // Identificadores que faltam no cadastro que fica sao herdados do duplicado.
        for (String campo : List.of("prontuario", "cpf", "cns")) {
            em.createNativeQuery("UPDATE pacientes d SET " + campo + " = o." + campo
                    + " FROM pacientes o WHERE d.idpaciente = :destino AND o.idpaciente = :origem"
                    + " AND (d." + campo + " IS NULL OR btrim(d." + campo + ") IN ('', '0'))"
                    + " AND o." + campo + " IS NOT NULL AND btrim(o." + campo + ") NOT IN ('', '0')")
                    .setParameter("destino", destino).setParameter("origem", origem).executeUpdate();
        }
        em.createNativeQuery("UPDATE pacientes SET unificado_em = :destino WHERE idpaciente = :origem")
                .setParameter("destino", destino).setParameter("origem", origem).executeUpdate();

        StringBuilder resumo = new StringBuilder();
        movidos.forEach((k, v) -> resumo.append(resumo.length() == 0 ? "" : "; ").append(k).append("=").append(v));
        String registros = resumo.length() == 0 ? "nenhum registro vinculado" : resumo.toString();
        em.createNativeQuery("INSERT INTO paciente_unificacao (id_origem, id_destino, motivo, unidade, matricula, registros_movidos) "
                        + "VALUES (:o, :d, :m, :u, :mat, :r)")
                .setParameter("o", origem).setParameter("d", destino)
                .setParameter("m", motivo.trim().length() > 500 ? motivo.trim().substring(0, 500) : motivo.trim())
                .setParameter("u", unidade).setParameter("mat", matricula).setParameter("r", registros)
                .executeUpdate();

        long total = movidos.values().stream().mapToLong(Long::longValue).sum();
        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalTime.now().format(HORA));
        log.setEvento("UNIFICACAO DE CADASTRO: matricula " + matricula + " unificou o paciente " + origem + " no paciente " + destino
                + " (" + total + " registros movidos). Motivo: " + motivo.trim());
        log.setUnidade(unidade == null ? null : unidade.length() > 20 ? unidade.substring(0, 20) : unidade);
        logService.salvarLog(log);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("origem", origem);
        r.put("destino", destino);
        r.put("movidos", movidos);
        r.put("total", total);
        return r;
    }

    // ---- Apoio ----

    private void validarPar(int origem, int destino) {
        if (origem == destino) throw new IllegalArgumentException("Escolha dois cadastros diferentes.");
        Object[] o = paciente(origem);
        Object[] d = paciente(destino);
        if (o == null || d == null) throw new IllegalArgumentException("Cadastro não encontrado.");
        if (o[7] != null) throw new IllegalStateException("O cadastro " + origem + " já foi unificado no " + o[7] + ".");
        if (d[7] != null) throw new IllegalStateException("O cadastro que vai ficar (" + destino + ") já foi unificado no " + d[7] + ".");
    }

    /** [idpaciente, nome, mae, nascimento, cpf, cns, prontuario, unificado_em] */
    private Object[] paciente(int id) {
        @SuppressWarnings("unchecked")
        List<Object[]> l = em.createNativeQuery(
                "SELECT idpaciente, nome, mae, nascimento, cpf, cns, prontuario, unificado_em FROM pacientes WHERE idpaciente = :id")
                .setParameter("id", id).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    private Object[] contagemBam(int id) {
        return (Object[]) em.createNativeQuery(
                "SELECT count(*), max(data) FROM bam WHERE paciente_idpaciente = :id").setParameter("id", id).getSingleResult();
    }

    private static Map<String, Object> cadastro(Object[] p, Object[] bam) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("idpaciente", p[0]);
        c.put("nome", p[1]);
        c.put("mae", p[2]);
        c.put("nascimento", p[3]);
        c.put("cpf", p[4]);
        c.put("cns", p[5]);
        c.put("prontuario", p[6]);
        c.put("bams", bam[0] == null ? 0 : ((Number) bam[0]).longValue());
        String ultimo = bam[1] == null ? null : String.valueOf(bam[1]);
        c.put("ultimoBam", ultimo == null ? null : ultimo.substring(0, Math.min(10, ultimo.length())));
        return c;
    }

    /**
     * Colunas que guardam o id do paciente: pelo nome (inteiro ou texto) ou por chave
     * estrangeira para pacientes. [tabela, coluna, tipo]
     */
    private List<String[]> colunas() {
        String sql = "SELECT c.table_name, c.column_name, c.data_type FROM information_schema.columns c "
                + "JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE' "
                + "WHERE c.table_schema = current_schema() AND c.table_name <> 'pacientes' "
                + "AND c.data_type IN ('integer', 'bigint', 'smallint', 'character varying', 'text') "
                + "AND c.table_name !~* :preservadas "
                + "AND (lower(c.column_name) IN (:nomes) OR EXISTS ("
                + "   SELECT 1 FROM information_schema.table_constraints tc "
                + "   JOIN information_schema.key_column_usage k ON k.constraint_name = tc.constraint_name AND k.table_schema = tc.table_schema "
                + "   JOIN information_schema.constraint_column_usage u ON u.constraint_name = tc.constraint_name AND u.table_schema = tc.table_schema "
                + "   WHERE tc.constraint_type = 'FOREIGN KEY' AND u.table_name = 'pacientes' "
                + "     AND k.table_name = c.table_name AND k.column_name = c.column_name)) "
                + "ORDER BY c.table_name, c.column_name";
        @SuppressWarnings("unchecked")
        List<Object[]> l = em.createNativeQuery(sql)
                .setParameter("preservadas", TABELAS_PRESERVADAS)
                .setParameter("nomes", COLUNAS_PACIENTE)
                .getResultList();
        List<String[]> r = new ArrayList<>();
        for (Object[] o : l) r.add(new String[] {String.valueOf(o[0]), String.valueOf(o[1]), String.valueOf(o[2])});
        return r;
    }

    private long contar(String[] col, int id) {
        Query q = em.createNativeQuery("SELECT count(*) FROM " + ident(col[0]) + " WHERE " + ident(col[1]) + " = :origem");
        vincular(q, col[2], null, id);
        return ((Number) q.getSingleResult()).longValue();
    }

    private static void vincular(Query q, String tipo, Integer destino, int origem) {
        boolean texto = tipo.startsWith("character") || tipo.equals("text");
        boolean longo = tipo.equals("bigint");
        q.setParameter("origem", texto ? String.valueOf(origem) : longo ? (Object) (long) origem : (Object) origem);
        if (destino != null) {
            q.setParameter("destino", texto ? String.valueOf(destino) : longo ? (Object) (long) destino : (Object) destino);
        }
    }

    /** Nome vindo do catalogo do banco, entre aspas (nunca de entrada do usuario). */
    private static String ident(String nome) {
        return "\"" + nome.replace("\"", "\"\"") + "\"";
    }

    /** Mais cadastros que isso com o mesmo numero: e numero generico, nao identifica ninguem. */
    private static final int MAX_REPETICOES_DOCUMENTO = 5;

    private String documentoConfiavel(String coluna, String numero, int tamanho) {
        if (numero.length() != tamanho || numero.chars().distinct().count() == 1) return "";
        long repeticoes = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM pacientes WHERE regexp_replace(coalesce(" + coluna + ",''), '\\D', '', 'g') = :n")
                .setParameter("n", numero).getSingleResult()).longValue();
        return repeticoes > MAX_REPETICOES_DOCUMENTO ? "" : numero;
    }

    private static String digitos(Object v) {
        return v == null ? "" : String.valueOf(v).replaceAll("\\D", "");
    }

    private static String normalizar(Object v) {
        if (v == null) return "";
        String s = Normalizer.normalize(String.valueOf(v), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("[^A-Za-z]", "").toUpperCase();
    }

    /** Nascimento gravado como aaaa/MM/dd ou dd/MM/aaaa: devolve as duas leituras em digitos. */
    private static String[] datasPossiveis(Object v) {
        String d = digitos(v);
        if (d.length() != 8) return new String[] {"", ""};
        boolean anoPrimeiro = Integer.parseInt(d.substring(0, 4)) > 1800;
        String aaaammdd = anoPrimeiro ? d : d.substring(4) + d.substring(2, 4) + d.substring(0, 2);
        String ddmmaaaa = aaaammdd.substring(6) + aaaammdd.substring(4, 6) + aaaammdd.substring(0, 4);
        return new String[] {aaaammdd, ddmmaaaa};
    }
}

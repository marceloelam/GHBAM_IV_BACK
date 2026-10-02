package br.com.iv.producaomedica;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class ProducaoMedicaRepository {
    @Inject EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<ProducaoMedicaLinha> buscar(LocalDate inicio, LocalDate fim, String unidade, String matricula) {
        String unidadeNormalizada = nulo(unidade);
        String matriculaNormalizada = nulo(matricula);
        StringBuilder sql = new StringBuilder("""
            WITH producao AS (
              SELECT DISTINCT ON (a.bam, TRIM(s.matricula))
                a.bam, a.idpaciente, CAST(a.data AS date), a.hora,
                TRIM(s.matricula),
                COALESCE(NULLIF(TRIM(s.nome), ''), NULLIF(BTRIM(r.atendidopor, ' .-'), ''), NULLIF(BTRIM(a.nomedomedico, ' .-'), ''), 'NAO INFORMADO'),
                COALESCE(NULLIF(TRIM(b.unidade), ''), 'NAO INFORMADA'),
                COALESCE(NULLIF(TRIM(r.setor), ''), 'NAO INFORMADO'),
                COALESCE(NULLIF(TRIM(t.tipo_alta), ''), ''),
                COALESCE(NULLIF(TRIM(p.nome), ''), 'NAO INFORMADO')
              FROM atendimento a JOIN bam b ON b.idbam=a.bam
              JOIN registro r ON r.idregistro=b.registro_idregistro
              JOIN tratamento t ON t.idtratamento=b.tratamento_idtratamento
              JOIN pacientes p ON p.idpaciente=b.paciente_idpaciente
              JOIN servidor s ON COALESCE(s.ativo,FALSE)=TRUE
               AND (COALESCE(s.medico,FALSE)=TRUE OR UPPER(TRIM(s.cargo)) IN ('MEDICO', 'MÉDICO'))
               AND (
                    (NULLIF(TRIM(r.matriculaatendidopor), '') IS NOT NULL
                     AND TRIM(s.matricula)=TRIM(r.matriculaatendidopor))
                    OR
                    (NULLIF(TRIM(r.matriculaatendidopor), '') IS NULL
                     AND UPPER(TRIM(s.nome))=UPPER(BTRIM(r.atendidopor, ' .-')))
               )
               AND REGEXP_REPLACE(UPPER(TRIM(SPLIT_PART(s.unidade, ':', 1))), '[_[:space:]-]+', '', 'g')
                   =REGEXP_REPLACE(UPPER(TRIM(SPLIT_PART(b.unidade, ':', 1))), '[_[:space:]-]+', '', 'g')
              WHERE CAST(a.data AS date) BETWEEN :inicio AND :fim
            """);
        if (unidadeNormalizada != null) {
            sql.append(" AND REGEXP_REPLACE(UPPER(TRIM(SPLIT_PART(b.unidade, ':', 1))), '[_[:space:]-]+', '', 'g')"
                + "=REGEXP_REPLACE(UPPER(TRIM(SPLIT_PART(:unidade, ':', 1))), '[_[:space:]-]+', '', 'g')\n");
        }
        if (matriculaNormalizada != null) {
            sql.append(" AND TRIM(s.matricula)=TRIM(:matricula)\n");
        }
        sql.append("""
              ORDER BY a.bam, TRIM(s.matricula),
                       CAST(a.data AS date) DESC, a.hora DESC, a.idatendimento DESC)
            SELECT * FROM producao ORDER BY 3 DESC, 4 DESC, 1 DESC
            """);
        var q=entityManager.createNativeQuery(sql.toString()).setParameter("inicio",Date.valueOf(inicio))
            .setParameter("fim",Date.valueOf(fim));
        if (unidadeNormalizada != null) q.setParameter("unidade", unidadeNormalizada);
        if (matriculaNormalizada != null) q.setParameter("matricula", matriculaNormalizada);
        return ((List<Object[]>)q.getResultList()).stream().map(this::mapear).toList();
    }
    public List<ProducaoMedicaRelatorio.Medico> listarMedicos(LocalDate inicio, LocalDate fim, String unidade) {
        @SuppressWarnings("unchecked")
        String sqlServidores = """
            SELECT TRIM(matricula), COALESCE(NULLIF(TRIM(nome), ''), 'NAO INFORMADO'),
                   COALESCE(NULLIF(TRIM(crm), ''), ''), COALESCE(foto, ''), COALESCE(ativo, FALSE)
              FROM servidor
             WHERE COALESCE(ativo, FALSE) = TRUE
               AND (COALESCE(medico, FALSE) = TRUE OR UPPER(TRIM(cargo)) IN ('MEDICO', 'MÉDICO'))
            """;
        var consultaServidores = entityManager.createNativeQuery(nulo(unidade) == null ? sqlServidores
            : sqlServidores + " AND REGEXP_REPLACE(UPPER(TRIM(SPLIT_PART(unidade, ':', 1))), '[_[:space:]-]+', '', 'g')"
                + " = REGEXP_REPLACE(UPPER(TRIM(SPLIT_PART(:unidade, ':', 1))), '[_[:space:]-]+', '', 'g')");
        if (nulo(unidade) != null) consultaServidores.setParameter("unidade", nulo(unidade));
        List<Object[]> servidores = consultaServidores.getResultList();
        var totaisPorMatricula = buscar(inicio,fim,unidade,null).stream().collect(Collectors.groupingBy(
            ProducaoMedicaLinha::matricula, Collectors.counting()));
        return servidores.stream().map(cadastro -> {
                String matricula = txt(cadastro[0]);
                return new ProducaoMedicaRelatorio.Medico(matricula, txt(cadastro[1]), txt(cadastro[2]), txt(cadastro[3]),
                    Boolean.TRUE.equals(cadastro[4]), totaisPorMatricula.getOrDefault(matricula, 0L));
            }).sorted(Comparator.comparing(ProducaoMedicaRelatorio.Medico::nome,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private ProducaoMedicaLinha mapear(Object[] r){return new ProducaoMedicaLinha(num(r[0]),num(r[1]),data(r[2]),
        txt(r[3]),txt(r[4]),txt(r[5]),txt(r[6]),txt(r[7]),txt(r[8]),txt(r[9]));}
    private static int num(Object v){return v==null?0:((Number)v).intValue();}
    private static String txt(Object v){return v==null?"":v.toString();}
    private static LocalDate data(Object v){if(v instanceof Date d)return d.toLocalDate();if(v instanceof LocalDate d)return d;return LocalDate.parse(v.toString());}
    private static String nulo(String v){return v==null||v.isBlank()?null:v.trim();}
}

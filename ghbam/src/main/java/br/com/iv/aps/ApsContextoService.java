package br.com.iv.aps;

import static br.com.iv.aps.ApsDb.p;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Contexto de trabalho do profissional na APS: unidade (CNES/IBGE), lotacao
 * (CNS + CBO + INE) e parametros da instalacao usados no header e no envelope LEDI.
 */
@ApplicationScoped
public class ApsContextoService {

    @Inject
    ApsDb db;

    @Inject
    EntityManager entityManager;

    @Inject
    ApsReferenciaService referencias;

    public record Unidade(String sigla, String nome, String cnes, String codIbge, String cnpj, String municipio) {
    }

    public record Lotacao(Long id, String matricula, String nome, String cns, String cpf, String cbo,
            String unidade, String cnes, Long idEquipe, String ine, boolean estagiario,
            String matriculaSupervisor, String codIbge) {
    }

    public Unidade unidade(String sigla) {
        if (sigla == null || sigla.isBlank()) {
            throw new IllegalArgumentException("Unidade de trabalho nao informada.");
        }
        List<ConfiguracaoUnidade> r = entityManager
                .createQuery("SELECT c FROM configurarunidade c WHERE UPPER(c.sigla) = UPPER(:s)", ConfiguracaoUnidade.class)
                .setParameter("s", sigla.trim())
                .setMaxResults(1)
                .getResultList();
        if (r.isEmpty()) {
            throw new IllegalArgumentException("Unidade '" + sigla + "' nao cadastrada em Configuracao de Unidades.");
        }
        ConfiguracaoUnidade c = r.get(0);
        String ibge = c.getCodIbge() > 0 ? ibge7(String.valueOf(c.getCodIbge())) : parametroIbge();
        return new Unidade(c.getSigla(), c.getHospital(), somenteDigitos(c.getCnesDoHospital()), ibge,
                somenteDigitos(c.getCnpjDoHospital()), c.getMunicipioPadrao());
    }

    /**
     * O cadastro de unidades do GHBAM guarda o IBGE com 6 digitos (padrao BPA/SIA);
     * o LEDI exige 7 (com digito verificador). Completa pela tabela oficial de municipios.
     */
    @SuppressWarnings("unchecked")
    String ibge7(String ibge) {
        String d = somenteDigitos(ibge);
        if (d == null || d.length() != 6) {
            return d;
        }
        for (Map<String, Object> m : (List<Map<String, Object>>) referencias.localidades().get("municipios")) {
            String cod = String.valueOf(m.get("ibge"));
            if (cod.length() == 7 && cod.startsWith(d)) {
                return cod;
            }
        }
        return d;
    }

    private String parametroIbge() {
        Map<String, Object> p = parametros();
        return p == null ? null : (String) p.get("cod_ibge");
    }

    public Map<String, Object> parametros() {
        return db.one("SELECT * FROM aps_parametro WHERE chave = 'GLOBAL'", Map.of());
    }

    public List<Map<String, Object>> lotacoes(String matricula) {
        return db.list("""
                SELECT l.*, e.nome AS equipe_nome, e.tipo AS equipe_tipo
                FROM aps_lotacao l
                LEFT JOIN aps_equipe e ON e.id = l.id_equipe
                WHERE l.matricula = :m AND l.ativo = TRUE
                ORDER BY l.unidade, l.cbo
                """, p("m", matricula));
    }

    /**
     * Lotacao ativa do usuario na unidade. Se houver mais de uma, usa a
     * escolhida (idLotacao, enviado pelo frontend no header X-APS-LOTACAO).
     * Sem lotacao APS cadastrada cai para os dados do servidor (CNS/CBO) na
     * unidade da sessao, sem INE - suficiente para fichas que nao exigem equipe.
     */
    public Lotacao lotacao(AuthenticatedUser user, String unidade, Long idLotacao) {
        if (user == null || user.matricula() == null || user.matricula().isBlank()) {
            throw new IllegalStateException("Usuario nao autenticado.");
        }
        String sigla = unidade == null || unidade.isBlank() ? user.unidade() : unidade;
        Unidade u = unidade(sigla);
        Map<String, Object> row = null;
        if (idLotacao != null) {
            row = db.one("SELECT * FROM aps_lotacao WHERE id = :id AND matricula = :m AND ativo = TRUE",
                    p("id", idLotacao, "m", user.matricula()));
        }
        if (row == null) {
            row = db.one("""
                    SELECT * FROM aps_lotacao
                    WHERE matricula = :m AND ativo = TRUE AND UPPER(unidade) = UPPER(:u)
                    ORDER BY (id_equipe IS NULL), id LIMIT 1
                    """, p("m", user.matricula(), "u", sigla));
        }
        if (row != null) {
            return new Lotacao(((Number) row.get("id")).longValue(), user.matricula(), (String) row.get("nome"),
                    (String) row.get("cns"), (String) row.get("cpf"), (String) row.get("cbo"),
                    (String) row.get("unidade"), (String) row.get("cnes"),
                    row.get("id_equipe") == null ? null : ((Number) row.get("id_equipe")).longValue(),
                    (String) row.get("ine"), Boolean.TRUE.equals(row.get("estagiario")),
                    (String) row.get("matricula_supervisor"), u.codIbge());
        }
        Map<String, Object> s = db.one("SELECT nome, cpf, cns, cbo FROM servidor WHERE matricula = :m LIMIT 1",
                p("m", user.matricula()));
        String cbo = s == null ? user.cbo() : (String) s.get("cbo");
        return new Lotacao(null, user.matricula(), s == null ? user.nome() : (String) s.get("nome"),
                somenteDigitos(s == null ? user.cns() : (String) s.get("cns")),
                somenteDigitos(s == null ? null : (String) s.get("cpf")),
                somenteDigitos(cbo), u.sigla(), u.cnes(), null, null, false, null, u.codIbge());
    }

    /** Lotacao de outro profissional (supervisor do estagiario ou profissional do atendimento compartilhado). */
    public Lotacao lotacaoPorId(Long idLotacao) {
        Map<String, Object> row = db.one("SELECT * FROM aps_lotacao WHERE id = :id AND ativo = TRUE", p("id", idLotacao));
        if (row == null) {
            throw new IllegalArgumentException("Lotacao APS " + idLotacao + " nao encontrada ou inativa.");
        }
        return new Lotacao(((Number) row.get("id")).longValue(), (String) row.get("matricula"), (String) row.get("nome"),
                (String) row.get("cns"), (String) row.get("cpf"), (String) row.get("cbo"), (String) row.get("unidade"),
                (String) row.get("cnes"), row.get("id_equipe") == null ? null : ((Number) row.get("id_equipe")).longValue(),
                (String) row.get("ine"), Boolean.TRUE.equals(row.get("estagiario")),
                (String) row.get("matricula_supervisor"), unidade((String) row.get("unidade")).codIbge());
    }

    public Lotacao lotacaoPorMatricula(String matricula, String unidade) {
        Map<String, Object> row = db.one("""
                SELECT id FROM aps_lotacao WHERE matricula = :m AND ativo = TRUE AND UPPER(unidade) = UPPER(:u)
                ORDER BY (id_equipe IS NULL), id LIMIT 1
                """, p("m", matricula, "u", unidade));
        if (row == null) {
            throw new IllegalArgumentException("Profissional " + matricula + " sem lotacao APS ativa na unidade " + unidade + ".");
        }
        return lotacaoPorId(((Number) row.get("id")).longValue());
    }

    public Map<String, Object> contextoResumo(AuthenticatedUser user, String unidade, Long idLotacao) {
        Map<String, Object> out = new LinkedHashMap<>();
        Unidade u = unidade(unidade == null || unidade.isBlank() ? user.unidade() : unidade);
        out.put("unidade", u);
        out.put("lotacoes", lotacoes(user.matricula()));
        try {
            out.put("lotacaoAtual", lotacao(user, u.sigla(), idLotacao));
        } catch (RuntimeException e) {
            out.put("lotacaoAtual", null);
        }
        // Administrador do GHBAM nao ganha o G-PEC automaticamente: vale o card G-PEC do servidor.
        out.put("permissoes", Map.of(
                "aps", user.hasPermission("paps"),
                "acs", user.hasPermission("papsacs"),
                "vacina", user.hasPermission("papsvacina"),
                "gestao", user.hasPermission("papsgestao"),
                "admin", false));
        Map<String, Object> partes = new LinkedHashMap<>();
        for (String parte : List.of("atendimentos", "cidadaos", "agenda", "vacinacao", "territorio", "registros",
                "acompanhamento", "relatorios", "transmissao", "configuracao", "appacs")) {
            partes.put(parte, user.hasPermission("pgpec" + parte));
        }
        out.put("partes", partes);
        out.put("profissional", Map.of("matricula", user.matricula(), "nome", user.nome() == null ? "" : user.nome()));
        return out;
    }

    /** UUID LEDI recomendado: CNES + '-' + UUID canonico (44 caracteres). */
    public static String novoUuid(String cnes) {
        String c = somenteDigitos(cnes);
        return (c == null || c.isBlank() ? "0000000" : c) + "-" + UUID.randomUUID();
    }

    public static String somenteDigitos(String s) {
        if (s == null) {
            return null;
        }
        String d = s.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }
}

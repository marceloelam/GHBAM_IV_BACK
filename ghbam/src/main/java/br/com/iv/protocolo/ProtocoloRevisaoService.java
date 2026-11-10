package br.com.iv.protocolo;

import br.com.iv.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edicao dos textos dos protocolos (V249). O backend guarda os textos como JSON opaco; quem
 * aplica sobre a estrutura do protocolo (e recusa o que nao encaixa) e o motor do frontend.
 * Cada edicao gera versao nova "<base>-rN", que nasce como rascunho ate ser aprovada.
 */
@ApplicationScoped
public class ProtocoloRevisaoService {

    static final int TEXTOS_MAX = 200_000;

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper mapper;

    String textosValidos(Object textos) {
        String json;
        JsonNode no;
        try {
            json = mapper.writeValueAsString(textos);
            no = mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Textos inválidos.");
        }
        if (no == null || !no.isObject() || no.isEmpty()) throw new IllegalArgumentException("Nenhuma alteração enviada.");
        if (json.length() > TEXTOS_MAX) throw new IllegalArgumentException("Conteúdo grande demais.");
        return json;
    }

    private Map<String, Object> linha(Object[] l, boolean comTextos) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ((Number) l[0]).longValue());
        m.put("protocoloId", l[1]);
        m.put("versaoBase", l[2]);
        m.put("versao", l[3]);
        if (comTextos) {
            try {
                m.put("textos", mapper.readTree(String.valueOf(l[4])));
            } catch (Exception e) {
                m.put("textos", Map.of());
            }
        }
        m.put("justificativa", l[5]);
        m.put("nome", l[6]);
        m.put("registro", l[7]);
        m.put("unidade", l[8]);
        m.put("criadoEm", l[9] instanceof Timestamp t ? t.toLocalDateTime().toString() : l[9]);
        return m;
    }

    private static final String COLUNAS =
            "id, protocolo_id, versao_base, versao, CAST(textos AS TEXT), justificativa, nome, registro, unidade, criado_em";

    /** Revisao mais recente de cada protocolo, por versao-base (o frontend usa a da versao do codigo). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> vigentes() {
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT DISTINCT ON (protocolo_id, versao_base) " + COLUNAS + " FROM protocolo_fluxo_revisao "
                        + "ORDER BY protocolo_id, versao_base, criado_em DESC, id DESC")
                .getResultList();
        List<Map<String, Object>> saida = new ArrayList<>();
        for (Object[] l : linhas) saida.add(linha(l, true));
        return saida;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> historico(String protocoloId) {
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT " + COLUNAS + " FROM protocolo_fluxo_revisao WHERE protocolo_id = :p "
                        + "ORDER BY criado_em DESC, id DESC LIMIT 100")
                .setParameter("p", protocoloId)
                .getResultList();
        List<Map<String, Object>> saida = new ArrayList<>();
        for (Object[] l : linhas) saida.add(linha(l, false));
        return saida;
    }

    @Transactional
    public String salvar(String protocoloId, String versaoBase, Object textos, String justificativa, String unidade,
                         AuthenticatedUser user) {
        String p = ProtocoloExecucaoService.limitar(protocoloId, 80);
        String b = ProtocoloExecucaoService.limitar(versaoBase, 20);
        if (p == null || b == null) throw new IllegalArgumentException("Informe protocolo e versão.");
        String j = ProtocoloExecucaoService.limitar(justificativa, 4000);
        if (j == null || j.length() < 5) throw new IllegalArgumentException("Informe a justificativa da alteração (mínimo 5 caracteres).");
        String json = textosValidos(textos);
        long n = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM protocolo_fluxo_revisao WHERE protocolo_id = :p AND versao_base = :b")
                .setParameter("p", p)
                .setParameter("b", b)
                .getSingleResult()).longValue();
        String versao = b + "-r" + (n + 1);
        em.createNativeQuery(
                "INSERT INTO protocolo_fluxo_revisao (protocolo_id, versao_base, versao, textos, justificativa, matricula, nome, registro, unidade) "
                        + "VALUES (:p, :b, :v, CAST(:t AS jsonb), :j, :m, CAST(:n AS VARCHAR), CAST(:r AS VARCHAR), CAST(:u AS VARCHAR))")
                .setParameter("p", p)
                .setParameter("b", b)
                .setParameter("v", versao)
                .setParameter("t", json)
                .setParameter("j", j)
                .setParameter("m", user.matricula().trim())
                .setParameter("n", ProtocoloExecucaoService.limitar(user.nome(), 200))
                .setParameter("r", ProtocoloExecucaoService.registroProfissional(user))
                .setParameter("u", ProtocoloExecucaoService.limitar(unidade, 80))
                .executeUpdate();
        return versao;
    }
}

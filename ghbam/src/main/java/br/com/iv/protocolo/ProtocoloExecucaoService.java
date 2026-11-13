package br.com.iv.protocolo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Execucoes de protocolos em fluxograma (V245). SQL nativo de proposito: sem @Entity, a
 * validacao de schema do Hibernate nao depende desta tabela para o sistema subir.
 * Encerrada a execucao, ela nao muda mais: refazer gera uma execucao nova.
 */
@ApplicationScoped
public class ProtocoloExecucaoService {

    static final Set<String> STATUS_FINAIS = Set.of("CONCLUIDO", "CRITERIO_MEDICO", "SAIDA");
    static final int PASSOS_MAX_BYTES = 200_000;
    static final int TEXTO_MAX = 20_000;

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "ghbam.protocolos.gravar", defaultValue = "false")
    boolean gravacaoAtiva;

    public boolean gravacaoAtiva() {
        return gravacaoAtiva;
    }

    /** "CRM 1234" ou "COREN 5678" do usuario autenticado; null se nao houver. */
    static String registroProfissional(br.com.iv.security.AuthenticatedUser user) {
        if (user == null) return null;
        String crm = limitar(user.crm(), 40);
        if (crm != null) return "CRM " + crm;
        String coren = limitar(user.corem(), 40);
        return coren != null ? "COREN " + coren : null;
    }

    static String limitar(String valor, int max) {
        if (valor == null) return null;
        String v = valor.trim();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }

    /** Passos chegam como JSON; so aceita lista e com tamanho limitado. */
    String passosValidos(Object passos) {
        String json;
        JsonNode no;
        try {
            json = mapper.writeValueAsString(passos == null ? List.of() : passos);
            no = mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Percurso inválido.");
        }
        if (!no.isArray()) throw new IllegalArgumentException("Percurso inválido.");
        if (json.length() > PASSOS_MAX_BYTES) throw new IllegalArgumentException("Percurso grande demais.");
        return json;
    }

    static String statusValido(String status, String justificativa) {
        String s = status == null || status.isBlank() ? "EM_ANDAMENTO" : status.trim().toUpperCase();
        if (!"EM_ANDAMENTO".equals(s) && !STATUS_FINAIS.contains(s)) {
            throw new IllegalArgumentException("Status inválido.");
        }
        if (("CRITERIO_MEDICO".equals(s) || "SAIDA".equals(s)) && limitar(justificativa, TEXTO_MAX) == null) {
            throw new IllegalArgumentException("Justificativa obrigatória para sair do protocolo.");
        }
        return s;
    }

    @Transactional
    public long iniciar(String protocoloId, String versao, String titulo, String idpaciente, String bam,
                        String unidade, String matricula, String profissional, String registro) {
        Number id = (Number) em.createNativeQuery(
                "INSERT INTO protocolo_fluxo_execucao (protocolo_id, protocolo_versao, protocolo_titulo, idpaciente, bam, unidade, matricula, profissional, registro) "
                        + "VALUES (:p, :v, :t, :pac, CAST(:bam AS VARCHAR), :u, :m, CAST(:prof AS VARCHAR), CAST(:reg AS VARCHAR)) RETURNING id")
                .setParameter("reg", limitar(registro, 60))
                .setParameter("p", limitar(protocoloId, 80))
                .setParameter("v", limitar(versao, 20))
                .setParameter("t", limitar(titulo, 200))
                .setParameter("pac", limitar(idpaciente, 40))
                .setParameter("bam", limitar(bam, 40))
                .setParameter("u", unidade)
                .setParameter("m", matricula)
                .setParameter("prof", limitar(profissional, 200))
                .getSingleResult();
        return id.longValue();
    }

    /**
     * Atualiza o percurso. So o profissional que iniciou, na mesma unidade, e so enquanto a
     * execucao estiver em andamento. Retorna false se nada foi alterado.
     */
    @Transactional
    public boolean atualizar(long id, String matricula, String unidade, Object passos, String status,
                             String desfecho, String justificativa, String resumo) {
        String novoStatus = statusValido(status, justificativa);
        String encerramento = STATUS_FINAIS.contains(novoStatus) ? "LOCALTIMESTAMP" : "NULL";
        int linhas = em.createNativeQuery(
                "UPDATE protocolo_fluxo_execucao SET passos = CAST(:passos AS jsonb), status = :s, desfecho = CAST(:d AS VARCHAR), "
                        + "justificativa = CAST(:j AS TEXT), resumo = CAST(:r AS TEXT), atualizado_em = LOCALTIMESTAMP, "
                        + "encerrado_em = " + encerramento + " "
                        + "WHERE id = :id AND matricula = :m AND unidade = :u AND status = 'EM_ANDAMENTO'")
                .setParameter("passos", passosValidos(passos))
                .setParameter("s", novoStatus)
                .setParameter("d", limitar(desfecho, 300))
                .setParameter("j", limitar(justificativa, TEXTO_MAX))
                .setParameter("r", limitar(resumo, TEXTO_MAX))
                .setParameter("id", id)
                .setParameter("m", matricula)
                .setParameter("u", unidade)
                .executeUpdate();
        return linhas > 0;
    }

    /**
     * Hora como esta gravada na coluna (timestamp sem fuso, hora local). Com jdbc.timezone=UTC o
     * driver entrega esse valor como se fosse UTC; ler em UTC devolve a mesma hora do banco, sem
     * depender do fuso da maquina.
     */
    private static Object horaGravada(Object valor) {
        if (valor instanceof Timestamp t) {
            return t.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime().toString();
        }
        if (valor instanceof java.time.LocalDateTime ldt) {
            // Hibernate ja converteu "UTC" para o fuso da JVM: desfaz para voltar a hora gravada.
            return ldt.atZone(java.time.ZoneId.systemDefault()).withZoneSameInstant(java.time.ZoneOffset.UTC)
                    .toLocalDateTime().toString();
        }
        return valor;
    }

    /** Execucoes do paciente (ou do BAM) na unidade da sessao, mais recentes primeiro. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listar(String idpaciente, String bam, String unidade) {
        String pac = limitar(idpaciente, 40);
        String b = limitar(bam, 40);
        if (pac == null && b == null) return List.of();
        String filtro = pac != null ? "idpaciente = :chave" : "bam = :chave";
        Query q = em.createNativeQuery(
                "SELECT id, protocolo_id, protocolo_versao, protocolo_titulo, idpaciente, bam, profissional, status, "
                        + "desfecho, justificativa, resumo, iniciado_em, encerrado_em, registro FROM protocolo_fluxo_execucao "
                        + "WHERE unidade = :u AND " + filtro + " ORDER BY iniciado_em DESC LIMIT 50")
                .setParameter("u", unidade)
                .setParameter("chave", pac != null ? pac : b);
        List<Map<String, Object>> saida = new ArrayList<>();
        for (Object[] l : (List<Object[]>) q.getResultList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) l[0]).longValue());
            m.put("protocoloId", l[1]);
            m.put("versao", l[2]);
            m.put("titulo", l[3]);
            m.put("idpaciente", l[4]);
            m.put("bam", l[5]);
            m.put("profissional", l[6]);
            m.put("status", l[7]);
            m.put("desfecho", l[8]);
            m.put("justificativa", l[9]);
            m.put("resumo", l[10]);
            m.put("iniciadoEm", horaGravada(l[11]));
            m.put("encerradoEm", horaGravada(l[12]));
            m.put("registro", l[13]);
            saida.add(m);
        }
        return saida;
    }
}

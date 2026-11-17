package br.com.iv.security;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

// Traduz a referencia extraida do path auditado (BAM, id do paciente ou prontuario)
// em "NOME (prontuario X)". Roda na thread assincrona do log, nunca na requisicao;
// tem transacao propria para que uma falha aqui nao contamine a gravacao do log.
@ApplicationScoped
public class AuditPacienteResolver {

    public static final String TIPO_BAM = "bam";
    public static final String TIPO_ID_PACIENTE = "idpaciente";
    public static final String TIPO_PRONTUARIO = "prontuario";

    @Inject
    EntityManager em;

    /** Valor = "Entidade#id" (ver AuditRegistroPaciente): o numero da rota era do registro, nao do paciente. */
    public static final String TIPO_REGISTRO = "registro";

    /**
     * Busca o registro pela chave e devolve {tipo, valor} do paciente/BAM dele, ou null.
     * Usado antes do recurso executar (para DELETE, depois nao ha mais o que buscar) e no log assincrono.
     */
    @Transactional
    public String[] referenciaDoRegistro(Class<?> entidade, String id) {
        try {
            if (AuditRegistroPaciente.buscaPorUuid(entidade, id)) {
                var cb = em.getCriteriaBuilder();
                jakarta.persistence.criteria.CriteriaQuery<Object> q = cb.createQuery(Object.class);
                jakarta.persistence.criteria.Root<?> r = q.from(entidade);
                q.select(r).where(cb.equal(r.get("uuid"), id));
                java.util.List<Object> achados = em.createQuery(q).setMaxResults(1).getResultList();
                return achados.isEmpty() ? null : AuditRespostaPaciente.extrair(achados.get(0));
            }
            Class<?> tipoId = em.getMetamodel().entity(entidade).getIdType().getJavaType();
            Object chave = (tipoId == Integer.class || tipoId == int.class) ? Integer.valueOf(id)
                    : (tipoId == Long.class || tipoId == long.class) ? Long.valueOf(id)
                    : id;
            Object registro = em.find(entidade, chave);
            return registro == null ? null : AuditRespostaPaciente.extrair(registro);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Transactional
    public String descrever(String tipo, String valor) {
        if (TIPO_REGISTRO.equals(tipo)) {
            AuditRegistroPaciente.Alvo alvo = AuditRegistroPaciente.deTexto(valor);
            String[] ref = alvo == null ? null : referenciaDoRegistro(alvo.entidade(), alvo.id());
            if (ref == null) return " | paciente=nao localizado (" + valor + ")";
            return descrever(ref[0], ref[1]);
        }
        String rotulo = tipo + "=" + valor;
        Object[] linha = buscar(tipo, valor);
        if (linha == null) {
            return " | paciente=nao localizado (" + rotulo + ")";
        }
        String nome = linha[0] == null ? "" : String.valueOf(linha[0]).trim();
        String prontuario = linha[1] == null ? "" : String.valueOf(linha[1]).trim();
        return " | paciente=" + nome + " (prontuario " + prontuario + ", " + rotulo + ")";
    }

    private Object[] buscar(String tipo, String valor) {
        String sql;
        Object parametro;
        if (TIPO_BAM.equals(tipo)) {
            sql = "SELECT p.nome, p.prontuario FROM bam b JOIN pacientes p ON p.idpaciente = b.paciente_idpaciente WHERE b.idbam = :v";
            parametro = Integer.valueOf(valor);
        } else if (TIPO_ID_PACIENTE.equals(tipo)) {
            sql = "SELECT p.nome, p.prontuario FROM pacientes p WHERE p.idpaciente = :v";
            parametro = Integer.valueOf(valor);
        } else if (TIPO_PRONTUARIO.equals(tipo)) {
            sql = "SELECT p.nome, p.prontuario FROM pacientes p WHERE p.prontuario = :v";
            parametro = valor;
        } else {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(sql).setParameter("v", parametro).setMaxResults(1).getResultList();
        return linhas.isEmpty() ? null : linhas.get(0);
    }
}

package br.com.iv.security;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import org.hibernate.proxy.HibernateProxy;

/**
 * Ultimo recurso para o log dizer "de qual paciente": quando path, query e corpo nao trazem BAM nem
 * paciente (ex.: PUT /evolucaopaciente/123, onde 123 e o id da evolucao), olha o objeto que o recurso
 * devolveu. Entidades guardam a relacao como objeto (Bam bam, Pacientes pacientes); o id e lido do proxy
 * sem inicializar nada, para nao disparar consulta nem LazyInitializationException fora da sessao.
 *
 * Nunca lanca excecao: auditoria nao pode atrapalhar a resposta.
 */
final class AuditRespostaPaciente {

    private AuditRespostaPaciente() {
    }

    // Ordem de preferencia: BAM identifica o atendimento; paciente/prontuario so a pessoa.
    private static final String[][] CAMPOS = {
            { "idbam", AuditPacienteResolver.TIPO_BAM },
            { "nbam", AuditPacienteResolver.TIPO_BAM },
            { "numerobam", AuditPacienteResolver.TIPO_BAM },
            { "bamid", AuditPacienteResolver.TIPO_BAM },
            { "bamnumero", AuditPacienteResolver.TIPO_BAM },
            { "bam", AuditPacienteResolver.TIPO_BAM },
            { "idpaciente", AuditPacienteResolver.TIPO_ID_PACIENTE },
            { "iddopaciente", AuditPacienteResolver.TIPO_ID_PACIENTE },
            { "pacienteid", AuditPacienteResolver.TIPO_ID_PACIENTE },
            { "pacientes", AuditPacienteResolver.TIPO_ID_PACIENTE },
            { "paciente", AuditPacienteResolver.TIPO_ID_PACIENTE },
            { "prontuario", AuditPacienteResolver.TIPO_PRONTUARIO } };

    static String[] extrair(Object entidade) {
        try {
            if (entidade == null || entidade instanceof Collection<?> || entidade.getClass().isArray()
                    || entidade instanceof CharSequence || entidade instanceof Number) {
                return null; // lista de varios pacientes nao identifica um so
            }
            for (String[] campo : CAMPOS) {
                Object valor = ler(entidade, campo[0]);
                String numero = numero(valor, campo[1]);
                if (numero != null) return new String[] { campo[1], numero };
            }
        } catch (RuntimeException | LinkageError ignorado) {
            // segue sem referencia
        }
        return null;
    }

    private static Object ler(Object alvo, String nome) {
        if (alvo instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (nome.equalsIgnoreCase(String.valueOf(e.getKey()))) return e.getValue();
            }
            return null;
        }
        if (alvo instanceof HibernateProxy) return null;
        for (Class<?> c = alvo.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.getName().toLowerCase(Locale.ROOT).equals(nome)) continue;
                try {
                    f.setAccessible(true);
                    return f.get(alvo);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Numero do campo: direto (int/String) ou o id de uma entidade relacionada (Bam/Pacientes). */
    private static String numero(Object valor, String tipo) {
        if (valor == null) return null;
        if (valor instanceof HibernateProxy proxy) {
            return valido(proxy.getHibernateLazyInitializer().getIdentifier());
        }
        if (valor instanceof Number || valor instanceof CharSequence) {
            return valido(valor);
        }
        if (AuditPacienteResolver.TIPO_PRONTUARIO.equals(tipo)) return null;
        // Entidade relacionada ja carregada: pega o id dela.
        Object id = ler(valor, AuditPacienteResolver.TIPO_BAM.equals(tipo) ? "idbam" : "idpaciente");
        return id instanceof HibernateProxy ? null : valido(id);
    }

    private static String valido(Object id) {
        if (id == null) return null;
        String s = String.valueOf(id).trim();
        if (!s.matches("\\d{1,12}") || s.chars().allMatch(ch -> ch == '0')) return null;
        return s;
    }
}

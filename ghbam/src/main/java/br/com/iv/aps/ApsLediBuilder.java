package br.com.iv.aps;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helpers para montar os filhos (child) dos modelos de informacao LEDI.
 * Os nomes dos campos de identificacao variam por modelo; aqui fica a regra
 * unica: CPF tem prioridade e NAO pode ir junto com CNS (dicionario FAI #30);
 * sem CPF vai CNS e a justificativa de "nao possui CPF".
 */
public final class ApsLediBuilder {

    private ApsLediBuilder() {
    }

    public enum Estilo {
        /** FAI: cns, dataNascimento, numeroProntuario, stCidadaoNaoPossuiCpf. */
        FAI,
        /** FAO/FP/FVD/FAD/FCZM/MCA: cnsCidadao, dtNascimento|dataNascimento, numProntuario. */
        PADRAO,
        /** Vacinacao: cnsCidadao, dtNascimento, stNaoPossuiCpf, justificativaNaoPossuiCpf. */
        VACINA,
        /** Atendimento domiciliar/MCA: cnsCidadao, dataNascimento. */
        DOMICILIAR
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> identificacao(Map<String, Object> cidadao, Estilo estilo) {
        Map<String, Object> m = new LinkedHashMap<>();
        String cpf = (String) cidadao.get("cpf");
        String cns = (String) cidadao.get("cns");
        Object justificativa = null;
        Object dados = cidadao.get("dados");
        if (dados instanceof Map<?, ?> d && d.get("identificacaoUsuarioCidadao") instanceof Map<?, ?> ident) {
            justificativa = ((Map<String, Object>) ident).get("justificativaNaoPossuiCpf");
        }
        String kCns = estilo == Estilo.FAI ? "cns" : "cnsCidadao";
        String kNasc = switch (estilo) {
            case FAI, DOMICILIAR -> "dataNascimento";
            default -> "dtNascimento";
        };
        String kSemCpf = estilo == Estilo.VACINA ? "stNaoPossuiCpf" : "stCidadaoNaoPossuiCpf";
        String kJust = estilo == Estilo.VACINA ? "justificativaNaoPossuiCpf" : "justificativaCidadaoNaoPossuiCpf";
        // dic-fai/fp/fv: o marcador e obrigatorio e "deve ser false se cpfCidadao for preenchido";
        // true exige a justificativa, entao sem ela (so CNS) tambem vai false.
        if (cpf != null && !cpf.isBlank()) {
            m.put("cpfCidadao", cpf);
            m.put(kSemCpf, false);
        } else {
            if (cns != null && !cns.isBlank()) {
                m.put(kCns, cns);
            }
            if (justificativa != null) {
                m.put(kSemCpf, true);
                m.put(kJust, justificativa);
            } else {
                m.put(kSemCpf, false);
            }
        }
        if (cidadao.get("data_nascimento") != null) {
            m.put(kNasc, cidadao.get("data_nascimento"));
        }
        if (cidadao.get("sexo") != null) {
            m.put("sexo", cidadao.get("sexo"));
        }
        Object prontuario = cidadao.get("id_paciente") != null ? cidadao.get("id_paciente") : cidadao.get("id");
        if (prontuario != null) {
            m.put(estilo == Estilo.FAI ? "numeroProntuario" : "numProntuario", String.valueOf(prontuario));
        }
        return m;
    }

    /** Turno LEDI: 1 manha (ate 12h), 2 tarde (ate 18h), 3 noite. */
    public static int turno(LocalDateTime t) {
        int h = (t == null ? LocalDateTime.now() : t).getHour();
        if (h < 12) {
            return 1;
        }
        return h < 18 ? 2 : 3;
    }

    public static LocalDateTime dataHora(Object iso) {
        if (iso == null) {
            return null;
        }
        String s = String.valueOf(iso).trim();
        if (s.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(s.length() == 16 ? s + ":00" : s.replace(' ', 'T'));
    }

    /** Remove chaves nulas/vazias recursivamente (o LEDI rejeita listas vazias em varios campos). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> limpar(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            Object v = e.getValue();
            if (v == null || (v instanceof String s && s.isBlank())) {
                continue;
            }
            if (v instanceof Map<?, ?> mm) {
                Map<String, Object> sub = limpar((Map<String, Object>) mm);
                if (!sub.isEmpty()) {
                    out.put(e.getKey(), sub);
                }
                continue;
            }
            if (v instanceof java.util.Collection<?> c) {
                if (c.isEmpty()) {
                    continue;
                }
                java.util.List<Object> l = new java.util.ArrayList<>();
                for (Object item : c) {
                    if (item instanceof Map<?, ?> im) {
                        Map<String, Object> sub = limpar((Map<String, Object>) im);
                        if (!sub.isEmpty()) {
                            l.add(sub);
                        }
                    } else if (item != null && !(item instanceof String s && s.isBlank())) {
                        l.add(item);
                    }
                }
                if (!l.isEmpty()) {
                    out.put(e.getKey(), l);
                }
                continue;
            }
            out.put(e.getKey(), v);
        }
        return out;
    }
}

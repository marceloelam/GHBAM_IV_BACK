package br.com.iv.cdss.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Fila de trabalho do ciclo de medicacao (prescricao estruturada) da unidade da sessao:
 * pacientes com etapa pendente para o perfil (farmacia: dispensacao; enfermagem:
 * recebimento, aprazamento, checagem). O paciente sai da fila quando nao ha mais pendencia.
 * A unidade pode declarar que a farmacia nao registra dispensacao (ciclo_medicacao_config).
 *
 * Excecao por medicamento: a enfermagem pode receber sem dispensacao no sistema informando a
 * origem no inicio da observacao do recebimento: [ORIGEM:ESTOQUE_SETOR] (estoque do setor /
 * ja estava com a enfermagem) ou [ORIGEM:PAPEL] (farmacia dispensou em papel). O de papel volta
 * para a fila da farmacia como "regularizar" ate a dispensacao ser lancada no sistema; o do
 * estoque do setor volta como "repor estoque do setor"; [ORIGEM:PACIENTE] (trazido pelo
 * paciente/familia, com conferencia) nao gera pendencia para a farmacia.
 *
 * Itens que nao sao medicamento (dieta, cuidado, procedimento, oxigenoterapia) nao passam
 * pela farmacia: so aprazamento e checagem pela enfermagem.
 */
@ApplicationScoped
public class CicloMedicacaoFilaService {
    static final String ORIGEM_PAPEL = "[ORIGEM:PAPEL]";
    static final String ORIGEM_ESTOQUE = "[ORIGEM:ESTOQUE_SETOR]";

    @Inject
    EntityManager em;

    record ItemCiclo(int idbam, String medicamento, LocalDateTime criadoEm,
            boolean dispensado, boolean recebido, boolean aprazado, boolean checado, boolean recebidoPapel,
            boolean recebidoEstoque, boolean ehMedicamento) {
        ItemCiclo(int idbam, String medicamento, LocalDateTime criadoEm,
                boolean dispensado, boolean recebido, boolean aprazado, boolean checado) {
            this(idbam, medicamento, criadoEm, dispensado, recebido, aprazado, checado, false, false, true);
        }

        ItemCiclo(int idbam, String medicamento, LocalDateTime criadoEm,
                boolean dispensado, boolean recebido, boolean aprazado, boolean checado, boolean recebidoPapel) {
            this(idbam, medicamento, criadoEm, dispensado, recebido, aprazado, checado, recebidoPapel, false, true);
        }
    }

    public boolean farmaciaDispensa(String unidade) {
        List<?> r = em.createNativeQuery("select farmacia_dispensa from ciclo_medicacao_config where unidade = :u")
                .setParameter("u", unidade(unidade)).getResultList();
        return r.isEmpty() || !Boolean.FALSE.equals(r.get(0));
    }

    public Map<String, Object> config(String unidade) {
        return Map.of("unidade", unidade(unidade), "farmaciaDispensa", farmaciaDispensa(unidade));
    }

    @Transactional
    public Map<String, Object> salvarConfig(boolean farmaciaDispensa, AuthenticatedUser usuario) {
        if (usuario == null || !usuario.hasPermission("kadm")) {
            throw new SecurityException("Somente o administrador pode alterar esta configuracao.");
        }
        em.createNativeQuery("insert into ciclo_medicacao_config (unidade, farmacia_dispensa, atualizado_por, atualizado_em)"
                + " values (:u, :f, :p, now()) on conflict (unidade) do update set farmacia_dispensa = :f,"
                + " atualizado_por = :p, atualizado_em = now()")
                .setParameter("u", unidade(usuario.unidade()))
                .setParameter("f", farmaciaDispensa)
                .setParameter("p", (usuario.nome() + " (" + usuario.matricula() + ")").trim())
                .executeUpdate();
        return config(usuario.unidade());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fila(String unidadeSessao, String perfil) {
        String unidade = unidade(unidadeSessao);
        boolean farmacia = "farmacia".equalsIgnoreCase(perfil);
        boolean comDispensacao = farmaciaDispensa(unidade);

        List<Object[]> linhas = em.createNativeQuery(
                "select i.idbam, i.medicamento, i.criado_em,"
                        + " bool_or(e.tipo_evento = 'DISPENSACAO'), bool_or(e.tipo_evento = 'RECEBIMENTO_ENFERMAGEM'),"
                        + " bool_or(e.tipo_evento = 'APRAZAMENTO'), bool_or(e.tipo_evento = 'CHECAGEM'),"
                        + " bool_or(e.tipo_evento = 'RECEBIMENTO_ENFERMAGEM' and e.observacao like :papel),"
                        + " bool_or(e.tipo_evento = 'RECEBIMENTO_ENFERMAGEM' and e.observacao like :estoque),"
                        + " upper(coalesce(i.tipo_item, 'MEDICAMENTO')) = 'MEDICAMENTO'"
                        + " from prescricao_item_estruturado i"
                        + " left join prescricao_item_ciclo_evento e on e.iditem = i.iditem"
                        + " where i.unidade = :u and upper(coalesce(i.status, '')) <> 'SUSPENSO'"
                        // So internados: pedido da PA e executado pelo SPA (telas de Medicacao), nao pela enfermagem da internacao.
                        + " and exists (select 1 from novointernado n where n.nbam = i.idbam and coalesce(n.alta, false) = false)"
                        + " group by i.idbam, i.iditem, i.medicamento, i.criado_em, i.tipo_item")
                .setParameter("u", unidade)
                .setParameter("papel", ORIGEM_PAPEL + "%")
                .setParameter("estoque", ORIGEM_ESTOQUE + "%")
                .getResultList();

        Map<Integer, List<ItemCiclo>> pendentesPorBam = new LinkedHashMap<>();
        for (Object[] l : linhas) {
            ItemCiclo i = new ItemCiclo(((Number) l[0]).intValue(), texto(l[1]),
                    l[2] instanceof Timestamp t ? t.toLocalDateTime() : null,
                    Boolean.TRUE.equals(l[3]), Boolean.TRUE.equals(l[4]), Boolean.TRUE.equals(l[5]), Boolean.TRUE.equals(l[6]), Boolean.TRUE.equals(l[7]),
                    Boolean.TRUE.equals(l[8]), !Boolean.FALSE.equals(l[9]));
            if (etapaPendente(i, farmacia, comDispensacao) != null) {
                pendentesPorBam.computeIfAbsent(i.idbam(), k -> new ArrayList<>()).add(i);
            }
        }

        Map<Integer, Object[]> pacientes = pacientes(new ArrayList<>(pendentesPorBam.keySet()));
        LocalDateTime agora = LocalDateTime.now();
        List<Map<String, Object>> fila = new ArrayList<>();
        pendentesPorBam.forEach((idbam, lista) -> {
            Map<String, Integer> porEtapa = new LinkedHashMap<>();
            for (ItemCiclo i : lista) porEtapa.merge(etapaPendente(i, farmacia, comDispensacao), 1, Integer::sum);
            LocalDateTime maisAntigo = lista.stream().map(ItemCiclo::criadoEm).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            Object[] p = pacientes.get(idbam);
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("idbam", idbam);
            linha.put("nome", p == null ? null : p[1]);
            linha.put("prontuario", p == null ? null : p[2]);
            linha.put("setor", p == null ? null : p[3]);
            linha.put("leito", p == null ? null : p[4]);
            linha.put("pendentes", lista.size());
            linha.put("porEtapa", porEtapa);
            linha.put("medicamentos", lista.stream().map(ItemCiclo::medicamento).limit(4).toList());
            linha.put("prescritoEm", maisAntigo == null ? null : maisAntigo.toString());
            linha.put("esperaMinutos", maisAntigo == null ? null : Duration.between(maisAntigo, agora).toMinutes());
            fila.add(linha);
        });
        // Quem espera ha mais tempo primeiro.
        fila.sort(Comparator.comparing(m -> m.get("prescritoEm") == null ? "9" : (String) m.get("prescritoEm")));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("unidade", unidade);
        r.put("perfil", farmacia ? "farmacia" : "enfermagem");
        r.put("farmaciaDispensa", comDispensacao);
        r.put("fila", fila);
        r.put("adesao", adesao(unidade));
        return r;
    }

    /** Proxima etapa pendente do item para o perfil, ou null se nao ha nada a fazer. */
    static String etapaPendente(ItemCiclo i, boolean farmacia, boolean comDispensacao) {
        if (!i.ehMedicamento()) {
            if (farmacia) return null;
            if (!i.aprazado()) return "APRAZAMENTO";
            return i.checado() ? null : "CHECAGEM";
        }
        if (farmacia) {
            if (!comDispensacao || i.dispensado()) return null;
            if (!i.recebido()) return "DISPENSACAO";
            // Enfermagem ja recebeu sem dispensacao no sistema: papel precisa ser lancado, estoque do
            // setor precisa ser reposto; trazido pelo paciente nao gera pendencia para a farmacia.
            if (i.recebidoPapel()) return "REGULARIZAR_PAPEL";
            return i.recebidoEstoque() ? "REPOR_ESTOQUE" : null;
        }
        // Ainda sem dispensacao: aparece para a enfermagem (pode receber do estoque do setor ou do papel).
        if (comDispensacao && !i.dispensado() && !i.recebido()) return "AGUARDANDO_FARMACIA";
        if (!i.recebido()) return "RECEBIMENTO_ENFERMAGEM";
        if (!i.aprazado()) return "APRAZAMENTO";
        if (!i.checado()) return "CHECAGEM";
        return null;
    }

    /** Internados ativos da unidade e quantos tem prescricao estruturada (indicador de adesao). */
    private Map<String, Object> adesao(String unidade) {
        Object[] l = (Object[]) em.createNativeQuery(
                "select count(distinct n.nbam), count(distinct n.nbam) filter (where exists"
                        + " (select 1 from prescricao_item_estruturado i where i.idbam = n.nbam))"
                        + " from novointernado n where n.unidade = :u and coalesce(n.alta, false) = false")
                .setParameter("u", unidade).getSingleResult();
        return Map.of("internados", ((Number) l[0]).intValue(), "comPrescricaoEstruturada", ((Number) l[1]).intValue());
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Object[]> pacientes(List<Integer> bams) {
        Map<Integer, Object[]> r = new LinkedHashMap<>();
        if (bams.isEmpty()) return r;
        List<Object[]> linhas = em.createNativeQuery(
                "select distinct on (b.idbam) b.idbam, p.nome, p.prontuario, s.setor, l.local from bam b"
                        + " join pacientes p on p.idpaciente = b.paciente_idpaciente"
                        + " left join novointernado n on n.nbam = b.idbam and coalesce(n.alta, false) = false"
                        + " left join setorcenso s on s.idsetorcenso = n.setor_idsetorcenso"
                        + " left join localcenso l on l.idsetorlocalcenso = n.local_idsetorlocalcenso"
                        + " where b.idbam in (:bams) order by b.idbam")
                .setParameter("bams", bams).getResultList();
        for (Object[] l : linhas) r.put(((Number) l[0]).intValue(), l);
        return r;
    }

    private static String unidade(String unidade) {
        if (unidade == null || unidade.isBlank()) throw new SecurityException("Sessao sem unidade identificada.");
        return unidade.trim();
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}

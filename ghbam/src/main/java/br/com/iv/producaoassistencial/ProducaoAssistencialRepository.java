package br.com.iv.producaoassistencial;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import br.com.iv.producaoassistencial.ProducaoAssistencialService.Linha;
import br.com.iv.producaoassistencial.ProducaoAssistencialService.Tipo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class ProducaoAssistencialRepository {
    @Inject EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Linha> buscar(LocalDate inicio, LocalDate fim, String unidade, EnumSet<Tipo> tipos) {
        if (tipos.isEmpty()) return List.of();
        List<String> consultas = new ArrayList<>();
        for (Tipo tipo : tipos) consultas.add(consulta(tipo));
        String sql = "SELECT tipo, categoria, profissional, registro, data, SUM(quantidade) AS total FROM ("
                + String.join(" UNION ALL ", consultas)
                + ") producao WHERE quantidade > 0 GROUP BY tipo, categoria, profissional, registro, data"
                + " ORDER BY tipo, categoria, profissional, registro, data";
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("inicio", Date.valueOf(inicio)).setParameter("fim", Date.valueOf(fim))
                .setParameter("unidade", unidade).getResultList();
        return rows.stream().map(r -> new Linha(texto(r[0]), texto(r[1]), texto(r[2]), texto(r[3]), "",
                r[4] instanceof Date date ? date.toLocalDate() : LocalDate.parse(r[4].toString()),
                ((Number) r[5]).longValue())).toList();
    }

    // Resolve CRM/conselho e matricula para a mesma pessoa sem multiplicar registros por JOIN.
    // Nomes ambiguos permanecem separados pelo registro de origem.
    @SuppressWarnings("unchecked")
    public List<Linha> normalizarAutores(List<Linha> linhas, String unidade) {
        if (linhas.isEmpty()) return List.of();
        List<Object[]> servidores = entityManager.createNativeQuery(
                "SELECT matricula, nome, crm, corem FROM servidor s WHERE NULLIF(TRIM(s.matricula), '') IS NOT NULL"
                + unidade("s.unidade")).setParameter("unidade", unidade).getResultList();
        return linhas.stream().map(linha -> {
            List<Object[]> candidatos = servidores.stream().filter(s -> {
                if (!linha.matricula().isBlank()) return norm(s[0]).equals(norm(linha.matricula()));
                return norm(s[1]).equals(norm(linha.profissional()))
                        && List.of(norm(s[0]), norm(s[2]), norm(s[3])).contains(norm(linha.registro()));
            }).toList();
            if (candidatos.isEmpty() && !"Nao informado".equals(linha.profissional()))
                candidatos = servidores.stream().filter(s -> norm(s[1]).equals(norm(linha.profissional()))).toList();
            var ids = candidatos.stream().map(s -> texto(s[0]).trim()).distinct().toList();
            if (ids.size() != 1) return linha;
            var cadastro = candidatos.get(0);
            return new Linha(linha.tipo(), linha.categoria(), texto(cadastro[1]).trim(), linha.registro(),
                    ids.get(0), linha.data(), linha.total());
        }).toList();
    }

    // Filtros usam o cadastro da unidade, mesmo quando nao ha producao no periodo.
    @SuppressWarnings("unchecked")
    public ProducaoAssistencialService.OpcoesFiltros opcoesFiltros(String unidade, EnumSet<Tipo> tipos) {
        boolean aps = tipos.stream().anyMatch(t -> t.name().startsWith("APS_"));
        boolean medico = aps || tipos.stream().anyMatch(t -> List.of(Tipo.MEDICO, Tipo.PARECER_RESPONDIDO, Tipo.ANAMNESE, Tipo.EVOLUCAO_MEDICA).contains(t));
        boolean multi = aps || tipos.contains(Tipo.MULTIDISCIPLINAR) || tipos.contains(Tipo.EVOLUCAO_MULTIDISCIPLINAR);
        boolean enfermagem = aps || tipos.contains(Tipo.EVOLUCAO_ENFERMAGEM);
        boolean odonto = aps || tipos.contains(Tipo.ODONTOLOGICO);
        List<String> papeis = new ArrayList<>();
        if (medico) papeis.add("COALESCE(s.medico, FALSE) OR UPPER(s.cargo) IN ('MEDICO', 'MÉDICO')");
        if (odonto) papeis.add("COALESCE(s.dentista, FALSE) OR UPPER(s.cargo) LIKE 'DENT%' OR UPPER(s.cargo) LIKE 'ODONTO%'");
        if (enfermagem) papeis.add("COALESCE(s.enfermagem, FALSE) OR COALESCE(s.tcenfermagem, FALSE)");
        if (multi) papeis.add("COALESCE(s.fisioterapia, FALSE) OR COALESCE(s.nutricao, FALSE) OR COALESCE(s.psicologia, FALSE)"
                + " OR COALESCE(s.assistencia, FALSE) OR COALESCE(s.fonoaudiologia, FALSE)"
                + " OR UPPER(s.cargo) LIKE 'FISIO%' OR UPPER(s.cargo) LIKE 'NUTRI%' OR UPPER(s.cargo) LIKE 'PSICO%'"
                + " OR UPPER(s.cargo) LIKE 'FONO%' OR UPPER(s.cargo) LIKE 'ASSIST%' OR UPPER(s.cargo) LIKE 'SERVI%SOCIAL%'"
                + " OR UPPER(s.cargo) LIKE 'TERAP%OCUP%' OR UPPER(s.cargo) LIKE 'FARM%CLIN%' OR UPPER(s.cargo) LIKE 'ENFERM%ESPEC%'");
        if (papeis.isEmpty()) return new ProducaoAssistencialService.OpcoesFiltros(List.of());
        String sql = "SELECT s.nome, s.matricula, COALESCE(NULLIF(TRIM(s.crm), ''), NULLIF(TRIM(s.corem), ''), s.matricula),"
                + " s.cargo, s.especialidade, s.cbo, s.medico, s.dentista, s.enfermagem, s.tcenfermagem,"
                + " s.fisioterapia, s.nutricao, s.psicologia, s.assistencia, s.fonoaudiologia"
                + " FROM servidor s WHERE COALESCE(s.ativo, FALSE) = TRUE AND NULLIF(TRIM(s.nome), '') IS NOT NULL AND ("
                + String.join(" OR ", papeis) + ")" + unidade("s.unidade") + " ORDER BY s.nome, s.matricula";
        List<Object[]> rows = entityManager.createNativeQuery(sql).setParameter("unidade", unidade).getResultList();
        return new ProducaoAssistencialService.OpcoesFiltros(rows.stream().map(r -> {
            List<String> categorias = new ArrayList<>();
            if (medico && Boolean.TRUE.equals(r[6])) categorias.add("Medicina");
            if (odonto && Boolean.TRUE.equals(r[7])) categorias.add("Odontologia");
            if (enfermagem && (Boolean.TRUE.equals(r[8]) || Boolean.TRUE.equals(r[9]))) categorias.add("Enfermagem");
            if (multi) {
                String[] nomes = {"Fisioterapia", "Nutrição", "Psicologia", "Serviço Social", "Fonoaudiologia"};
                for (int i = 0; i < nomes.length; i++) if (Boolean.TRUE.equals(r[10 + i])) categorias.add(nomes[i]);
            }
            if (categorias.isEmpty() && !texto(r[3]).isBlank()) categorias.add(texto(r[3]).trim());
            for (int i : new int[]{4, 5}) if (!texto(r[i]).isBlank()) categorias.add(texto(r[i]).trim());
            return new ProducaoAssistencialService.FiltroProfissional(texto(r[0]).trim(), texto(r[2]).trim(),
                    texto(r[1]).trim(), categorias.stream().distinct().toList());
        }).toList());
    }


    @SuppressWarnings("unchecked")
    public List<ProducaoAssistencialService.RegistroProducao> buscarDetalhes(LocalDate inicio, LocalDate fim,
            String unidade, Tipo tipo, String categoria, String registro) {
        String sql = "SELECT tipo, categoria, profissional, registro, data, quantidade, idregistro, bam, prontuario, hora, paciente, descricao, situacao"
                + " FROM (" + consultaDetalhes(tipo) + ") producao WHERE quantidade > 0 AND categoria = :categoria AND registro = :registro"
                + " ORDER BY data DESC, hora DESC NULLS LAST, idregistro DESC";
        List<Object[]> rows = entityManager.createNativeQuery(sql).setParameter("inicio", Date.valueOf(inicio))
                .setParameter("fim", Date.valueOf(fim)).setParameter("unidade", unidade)
                .setParameter("categoria", categoria).setParameter("registro", registro).getResultList();
        return rows.stream().map(r -> {
            LocalDate data = r[4] instanceof Date date ? date.toLocalDate() : LocalDate.parse(r[4].toString());
            long quantidade = ((Number) r[5]).longValue();
            var linha = new Linha(texto(r[0]), texto(r[1]), texto(r[2]), texto(r[3]), "", data, quantidade);
            var detalhe = new ProducaoAssistencialService.Detalhe(texto(r[6]), r[7] == null ? null : ((Number) r[7]).intValue(),
                    texto(r[8]), data, texto(r[9]), texto(r[10]), texto(r[11]), texto(r[12]), quantidade);
            return new ProducaoAssistencialService.RegistroProducao(linha, detalhe);
        }).toList();
    }

    // Projecao de identificacao acrescentada a MESMA fonte e criterios da contagem.
    static String consultaDetalhes(Tipo tipo) {
        List<String> partes = new ArrayList<>();
        for (String base : consulta(tipo).split(" UNION ALL ")) {
            String campos;
            String joins;
            switch (tipo) {
                case MULTIDISCIPLINAR -> {
                    campos = "a.id::text, a.idbam, a.prontuario, a.hora_inicial, detalhe_paciente.nome, a.setor, a.status";
                    joins = " LEFT JOIN pacientes detalhe_paciente ON detalhe_paciente.idpaciente = a.idpaciente";
                }
                case PARECER_RESPONDIDO -> {
                    campos = "p.idparecerespecialista::text, p.nbam, p.prontuario, NULL::text, detalhe_paciente.nome, p.especialidadedestino, 'Respondido'::text";
                    joins = " LEFT JOIN bam detalhe_bam ON detalhe_bam.idbam = p.nbam LEFT JOIN pacientes detalhe_paciente ON detalhe_paciente.idpaciente = detalhe_bam.paciente_idpaciente";
                }
                case ANAMNESE -> {
                    campos = "a.idanamneseinternacao::text, a.nbam, a.prontuario, NULL::text, detalhe_paciente.nome, 'Anamnese de internacao'::text, 'Registrada'::text";
                    joins = " LEFT JOIN pacientes detalhe_paciente ON detalhe_paciente.idpaciente = b.paciente_idpaciente";
                }
                case EVOLUCAO_MEDICA, EVOLUCAO_MULTIDISCIPLINAR, EVOLUCAO_ENFERMAGEM -> {
                    campos = "e.idevolucao::text, e.nbam, e.prontuario, " + (tipo == Tipo.EVOLUCAO_MEDICA ? "e.horaev" : "e.hora")
                            + ", detalhe_paciente.nome, e.local, 'Ativa'::text";
                    joins = " LEFT JOIN pacientes detalhe_paciente ON detalhe_paciente.idpaciente = b.paciente_idpaciente";
                }
                case ODONTOLOGICO -> {
                    campos = "o.idatendimento::text, o.idbam, NULL::text, TO_CHAR(o.criado_em, 'HH24:MI'), detalhe_paciente.nome, 'Odontologia'::text, o.status";
                    joins = " LEFT JOIN pacientes detalhe_paciente ON detalhe_paciente.idpaciente = o.idpaciente";
                }
                default -> {
                    String descricao = tipo == Tipo.APS_PROCEDIMENTO
                            ? "CASE WHEN jsonb_typeof(proc.item) = 'object' THEN proc.item->>'coMsProcedimento' ELSE proc.item #>> '{}' END"
                            : "'Ficha APS tipo ' || f.tipo";
                    String referencia = "f.id::text";
                    if (base.contains(" AS filho(item)")) {
                        base = base.replace(" AS filho(item)", " WITH ORDINALITY AS filho(item, ordem)");
                        referencia += " || ':' || filho.ordem::text";
                    }
                    if (base.contains(" AS proc(item)")) {
                        base = base.replace(" AS proc(item)", " WITH ORDINALITY AS proc(item, ordem)");
                        referencia += " || ':' || proc.ordem::text";
                    }
                    campos = referencia + ", NULL::integer, NULL::text, NULL::text, detalhe_paciente.nome, " + descricao + ", f.status";
                    joins = " LEFT JOIN aps_cidadao detalhe_paciente ON detalhe_paciente.id = f.id_cidadao";
                }
            }
            String[] expressoes = campos.split(", ", 7);
            // expressoes com virgulas internas sao construidas separadamente abaixo.
            if (tipo == Tipo.ODONTOLOGICO) expressoes = new String[]{"o.idatendimento::text", "o.idbam", "NULL::text", "TO_CHAR(o.criado_em, 'HH24:MI')", "detalhe_paciente.nome", "'Odontologia'::text", "o.status"};
            String[] nomes = {"idregistro", "bam", "prontuario", "hora", "paciente", "descricao", "situacao"};
            StringBuilder projecao = new StringBuilder();
            for (int i = 0; i < nomes.length; i++) projecao.append(", ").append(expressoes[i]).append(" AS ").append(nomes[i]);
            int from = base.indexOf("FROM ");
            String consulta = base.substring(0, from) + projecao + " " + base.substring(from);
            int where = consulta.indexOf(" WHERE ");
            partes.add(consulta.substring(0, where) + joins + consulta.substring(where));
        }
        return String.join(" UNION ALL ", partes);
    }


    @SuppressWarnings("unchecked")
    public List<ProducaoAssistencialService.Secao> conteudoRegistro(Tipo tipo, ProducaoAssistencialService.Detalhe detalhe,
            LocalDate inicio, LocalDate fim, String unidadeAtual) {
        String sql;
        String[] titulos;
        String formato = "HTML";
        switch (tipo) {
            case MEDICO -> {
                sql = "SELECT a.relato, a.receita FROM atendimento a JOIN bam b ON b.idbam = a.bam WHERE CAST(a.bam AS text) = :id"
                        + " AND CAST(a.data AS date) BETWEEN :inicio AND :fim" + unidade("b.unidade")
                        + " ORDER BY CAST(a.data AS date) DESC, a.hora DESC, a.idatendimento DESC LIMIT 1";
                titulos = new String[]{"Relato do atendimento", "Prescricao / receita"};
            }
            case ANAMNESE -> {
                sql = "SELECT a.frente, a.verso FROM anamneseinternacao a JOIN bam b ON b.idbam = a.nbam WHERE CAST(a.idanamneseinternacao AS text) = :id"
                        + " AND a.data BETWEEN :inicio AND :fim" + unidade("b.unidade");
                titulos = new String[]{"Anamnese — frente", "Anamnese — verso"};
            }
            case PARECER_RESPONDIDO -> {
                sql = "SELECT COALESCE((SELECT CONCAT_WS('<br><br>', NULLIF(TRIM(a.frente), ''), NULLIF(TRIM(a.verso), ''))"
                        + " FROM anamneseinternacao a JOIN bam b ON b.idbam = a.nbam WHERE a.nbam = p.nbam" + unidade("b.unidade")
                        + " ORDER BY a.data DESC NULLS LAST, a.idanamneseinternacao DESC LIMIT 1), 'Resumo clínico não informado.'),"
                        + " p.desc1, p.desc2 FROM parecerespecialista p WHERE CAST(p.idparecerespecialista AS text) = :id"
                        + " AND p.dataresp BETWEEN :inicio AND :fim" + unidade("p.unidade");
                titulos = new String[]{"Resumo clínico", "Pedido médico solicitante", "Resposta do especialista"};
                formato = "TEXTO";
            }
            case EVOLUCAO_MEDICA, EVOLUCAO_MULTIDISCIPLINAR, EVOLUCAO_ENFERMAGEM -> {
                String tabela = tipo == Tipo.EVOLUCAO_MEDICA ? "evolucaopaciente" : "evolucaopacienteenf";
                sql = "SELECT e.relato, e.pedido, e.pedidodomed FROM " + tabela + " e JOIN bam b ON b.idbam = e.nbam WHERE CAST(e.idevolucao AS text) = :id"
                        + " AND e.data BETWEEN :inicio AND :fim" + unidade("COALESCE(NULLIF(TRIM(e.unidade), ''), b.unidade)");
                titulos = new String[]{"Evolucao", "Pedidos / observacoes", "Conduta registrada"};
            }
            case MULTIDISCIPLINAR -> {
                sql = "SELECT a.motivo, a.queixa_principal, a.historia_atual, a.condicao_clinica_observada, a.objetivos, a.condutas, a.intervencoes, a.orientacoes, a.evolucao, a.resposta_intervencoes, a.intercorrencias, a.resultado_atendimento, a.observacoes_finais, a.dados_especificos"
                        + " FROM atendimento_multidisciplinar a WHERE CAST(a.id AS text) = :id AND a.data_atendimento BETWEEN :inicio AND :fim" + unidade("a.unidade");
                titulos = new String[]{"Motivo", "Queixa principal", "Historia atual", "Condicao clinica observada", "Objetivos", "Condutas", "Intervencoes", "Orientacoes", "Evolucao", "Resposta as intervencoes", "Intercorrencias", "Resultado do atendimento", "Observacoes finais", "Avaliacao especifica"};
                formato = "TEXTO";
            }
            case ODONTOLOGICO -> { return conteudoOdontologico(detalhe, unidadeAtual); }
            default -> { return conteudoAps(tipo, detalhe, inicio, fim, unidadeAtual); }
        }
        List<Object[]> rows = entityManager.createNativeQuery(sql).setParameter("id", detalhe.id()).setParameter("inicio", Date.valueOf(inicio))
                .setParameter("fim", Date.valueOf(fim)).setParameter("unidade", unidadeAtual).getResultList();
        if (rows.isEmpty()) throw new jakarta.ws.rs.NotFoundException("Conteudo do registro nao localizado.");
        List<ProducaoAssistencialService.Secao> secoes = new ArrayList<>();
        Object[] valores = rows.get(0);
        for (int i = 0; i < titulos.length; i++) {
            if (valores[i] == null || valores[i].toString().isBlank()) continue;
            String conteudo = valores[i].toString();
            String tipoConteudo = ((tipo == Tipo.MULTIDISCIPLINAR && i == 13) || (tipo == Tipo.ODONTOLOGICO && i >= 6)) ? "JSON" : formato;
            secoes.add(new ProducaoAssistencialService.Secao(titulos[i], conteudo, tipo == Tipo.PARECER_RESPONDIDO && i == 0 ? "HTML" : tipoConteudo));
        }
        return secoes;
    }

    private List<ProducaoAssistencialService.Secao> conteudoOdontologico(ProducaoAssistencialService.Detalhe detalhe, String unidadeAtual) {
        var atendimento = entityManager.find(br.com.iv.odontologia.OdontologiaAtendimento.class, Integer.valueOf(detalhe.id()));
        if (atendimento == null || !sigla(atendimento.getUnidade()).equals(sigla(unidadeAtual))) throw new jakarta.ws.rs.NotFoundException("Registro odontologico nao localizado.");
        String[] titulos = {"Queixa principal", "História da doença atual", "Exame geral", "Exame extraoral", "Exame intraoral", "Observações", "Anamnese odontológica", "Evoluções odontológicas", "Evoluções periodontais"};
        String[] valores = {atendimento.getQueixaPrincipal(), atendimento.getHistoriaDoencaAtual(), atendimento.getExameGeral(), atendimento.getExameExtraoral(), atendimento.getExameIntraoral(), atendimento.getObservacoes(), atendimento.getAnamneseJson(), atendimento.getEvolucoesJson(), atendimento.getPeriodontaisJson()};
        List<ProducaoAssistencialService.Secao> secoes = new ArrayList<>();
        for (int i = 0; i < titulos.length; i++) if (valores[i] != null && !valores[i].isBlank()) secoes.add(new ProducaoAssistencialService.Secao(titulos[i], valores[i], i >= 6 ? "JSON" : "TEXTO"));
        return secoes;
    }
    private static String sigla(String valor) { return norm(valor).split(":")[0].replaceAll("[_\\s-]+", ""); }

    @SuppressWarnings("unchecked")
    private List<ProducaoAssistencialService.Secao> conteudoAps(Tipo tipo, ProducaoAssistencialService.Detalhe detalhe,
            LocalDate inicio, LocalDate fim, String unidadeAtual) {
        String[] referencia = detalhe.id().split(":");
        String filho = switch (tipo) {
            case APS_INDIVIDUAL -> "atendimentosIndividuais";
            case APS_ODONTOLOGICO -> "atendimentosOdontologicos";
            case APS_DOMICILIAR -> "atendimentosDomiciliares";
            case APS_VISITA -> "visitasDomiciliares";
            case APS_VACINACAO -> "vacinacoes";
            default -> "";
        };
        String dados = "f.dados - ARRAY['headerTransport','uuidFicha','uuidFichaOriginadora','tpCdsOrigem']";
        if (tipo == Tipo.APS_PROCEDIMENTO) {
            filho = "CASE f.tipo WHEN 5 THEN 'atendimentosOdontologicos' WHEN 10 THEN 'atendimentosDomiciliares' ELSE 'atendProcedimentos' END";
            dados = "(f.dados -> (" + filho + ")) -> :indice";
            String procedimentos = "CASE f.tipo WHEN 5 THEN 'procedimentosRealizados' ELSE 'procedimentos' END";
            dados = "jsonb_set(" + dados + ", ARRAY[" + procedimentos + "], jsonb_build_array((" + dados + " -> (" + procedimentos + ")) -> :procedimento))";
        } else if (!filho.isBlank()) {
            dados = "(f.dados -> '" + filho + "') -> :indice";
        }
        String modelos = switch (tipo) {
            case APS_INDIVIDUAL -> "= 4"; case APS_ODONTOLOGICO -> "= 5"; case APS_DOMICILIAR -> "= 10";
            case APS_VISITA -> "= 8"; case APS_VACINACAO -> "= 14"; case APS_COLETIVA -> "= 6";
            default -> "IN (5, 7, 10)";
        };
        String sql = "SELECT CAST(" + dados + " AS text), f.tipo FROM aps_ficha f WHERE CAST(f.id AS text) = :id AND f.tipo " + modelos
                + " AND f.status IN ('PRONTA', 'ENVIADA', 'REJEITADA') AND f.data_atendimento BETWEEN :inicio AND :fim" + unidade("f.unidade");
        var query = entityManager.createNativeQuery(sql).setParameter("id", referencia[0]).setParameter("inicio", Date.valueOf(inicio))
                .setParameter("fim", Date.valueOf(fim)).setParameter("unidade", unidadeAtual);
        if (referencia.length > 1) query.setParameter("indice", Integer.parseInt(referencia[1]) - 1);
        if (referencia.length > 2) query.setParameter("procedimento", Integer.parseInt(referencia[2]) - 1);
        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty() || rows.get(0)[0] == null) throw new jakarta.ws.rs.NotFoundException("Dados do registro APS nao localizados.");
        return List.of(new ProducaoAssistencialService.Secao("Dados registrados", texto(rows.get(0)[0]), "JSON"));
    }

    private static String norm(Object value) { return texto(value).trim().toUpperCase(Locale.ROOT); }
    private static String identificacao(String coluna) {
        return "COALESCE(NULLIF(TRIM(" + coluna + "), ''), 'Nao informado')";
    }
    private static String unidade(String coluna) {
        return " AND REGEXP_REPLACE(UPPER(TRIM(SPLIT_PART(" + coluna + ", ':', 1))), '[_[:space:]-]+', '', 'g')"
                + " = REGEXP_REPLACE(UPPER(TRIM(SPLIT_PART(:unidade, ':', 1))), '[_[:space:]-]+', '', 'g')";
    }
    private static String data(String coluna) { return ", CAST(" + coluna + " AS date) AS data, 1::bigint AS quantidade "; }

    static String consulta(Tipo tipo) {
        String select = "SELECT '" + tipo.name() + "' AS tipo, ";
        return switch (tipo) {
            case MULTIDISCIPLINAR -> select + identificacao("a.categoria_profissional") + " AS categoria, "
                    + identificacao("a.profissional") + " AS profissional, " + identificacao("a.numero_conselho")
                    + " AS registro" + data("a.data_atendimento") + "FROM atendimento_multidisciplinar a"
                    + " WHERE a.data_atendimento BETWEEN :inicio AND :fim AND a.ativo = TRUE AND a.status = 'FINALIZADO'"
                    + unidade("a.unidade");
            case PARECER_RESPONDIDO -> select + identificacao("p.especialidadedestino") + " AS categoria, "
                    + identificacao("p.medicorecebedor") + " AS profissional, " + identificacao("p.crmrecebedor")
                    + " AS registro" + data("p.dataresp") + "FROM parecerespecialista p"
                    + " WHERE p.dataresp BETWEEN :inicio AND :fim AND NULLIF(TRIM(p.desc2), '') IS NOT NULL"
                    + unidade("p.unidade");
            case ANAMNESE -> select + "'Medicina' AS categoria, " + identificacao("a.medico") + " AS profissional, "
                    + identificacao("a.crm") + " AS registro" + data("a.data") + "FROM anamneseinternacao a JOIN bam b ON b.idbam = a.nbam"
                    + " WHERE a.data BETWEEN :inicio AND :fim"
                    + " AND (NULLIF(TRIM(a.frente), '') IS NOT NULL OR NULLIF(TRIM(a.verso), '') IS NOT NULL)"
                    + unidade("b.unidade");
            case EVOLUCAO_MEDICA -> select + "'Medicina' AS categoria, " + identificacao("e.medico") + " AS profissional, "
                    + identificacao("e.crm") + " AS registro" + data("e.data") + "FROM evolucaopaciente e JOIN bam b ON b.idbam = e.nbam"
                    + " WHERE e.data BETWEEN :inicio AND :fim AND COALESCE(e.inativo, FALSE) = FALSE"
                    + " AND NULLIF(TRIM(e.relato), '') IS NOT NULL"
                    + unidade("COALESCE(NULLIF(TRIM(e.unidade), ''), b.unidade)");
            case EVOLUCAO_MULTIDISCIPLINAR, EVOLUCAO_ENFERMAGEM -> select + identificacao("e.quem") + " AS categoria, "
                    + identificacao("e.enfermeiro") + " AS profissional, " + identificacao("e.crm") + " AS registro"
                    + data("e.data") + "FROM evolucaopacienteenf e JOIN bam b ON b.idbam = e.nbam"
                    + " WHERE e.data BETWEEN :inicio AND :fim AND NULLIF(TRIM(e.relato), '') IS NOT NULL"
                    + " AND e.idevolucao_retificacao IS NULL AND UPPER(TRIM(COALESCE(e.quem, ''))) "
                    + (tipo == Tipo.EVOLUCAO_ENFERMAGEM ? "IN" : "NOT IN")
                    + " ('', 'ENFERMEIRO', 'TECNICO', 'TÉCNICO', 'TEC')"
                    + unidade("COALESCE(NULLIF(TRIM(e.unidade), ''), b.unidade)");
            case ODONTOLOGICO -> select + "'Odontologia' AS categoria, " + identificacao("o.criado_por") + " AS profissional, "
                    + identificacao("o.criado_por_matricula") + " AS registro" + data("o.criado_em")
                    + "FROM odontologia_atendimento o WHERE o.criado_em >= CAST(:inicio AS date) AND o.criado_em < CAST(:fim AS date) + INTERVAL '1 day'"
                    + " AND o.status = 'ASSINADO'" + unidade("o.unidade");
            case APS_INDIVIDUAL -> ficha(tipo, 4, "atendimentosIndividuais", false);
            case APS_ODONTOLOGICO -> ficha(tipo, 5, "atendimentosOdontologicos", false);
            case APS_DOMICILIAR -> ficha(tipo, 10, "atendimentosDomiciliares", false);
            case APS_VISITA -> ficha(tipo, 8, "visitasDomiciliares", false);
            case APS_VACINACAO -> ficha(tipo, 14, "vacinacoes", false);
            case APS_COLETIVA -> ficha(tipo, 6, null, false);
            case APS_PROCEDIMENTO -> ficha(tipo, 7, "atendProcedimentos", true)
                    + " UNION ALL " + ficha(tipo, 10, "atendimentosDomiciliares", true)
                    + " UNION ALL " + ficha(tipo, 5, "atendimentosOdontologicos", true);
            default -> throw new IllegalArgumentException("Fonte nao agregada: " + tipo);
        };
    }

    // Uma ficha LEDI pode conter varios atendimentos; expandir filhos evita contar apenas a ficha.
    private static String array(String expression) {
        return "CASE WHEN jsonb_typeof(" + expression + ") = 'array' THEN " + expression + " ELSE '[]'::jsonb END";
    }
    private static String ficha(Tipo tipo, int modelo, String filhos, boolean procedimentos) {
        String quantidade = procedimentos && modelo == 5
                ? "CASE WHEN proc.item->>'quantidade' IS NULL THEN 1 WHEN proc.item->>'quantidade' ~ '^[1-9][0-9]{0,8}$'"
                    + " THEN (proc.item->>'quantidade')::bigint ELSE 0 END"
                : "1::bigint";
        String sql = "SELECT '" + tipo.name() + "' AS tipo, " + identificacao("f.profissional_cbo")
                + " AS categoria, " + identificacao("f.profissional_nome") + " AS profissional, "
                + identificacao("f.matricula") + " AS registro, f.data_atendimento AS data, " + quantidade + " AS quantidade"
                + " FROM aps_ficha f";
        if (filhos != null) sql += " CROSS JOIN LATERAL jsonb_array_elements(" + array("f.dados->'" + filhos + "'") + ") AS filho(item)";
        if (procedimentos) sql += " CROSS JOIN LATERAL jsonb_array_elements(" + array("filho.item->'"
                + (modelo == 5 ? "procedimentosRealizados" : "procedimentos") + "'") + ") AS proc(item)";
        sql += " WHERE f.tipo = " + modelo + " AND f.status IN ('PRONTA', 'ENVIADA', 'REJEITADA')"
                + " AND f.data_atendimento BETWEEN :inicio AND :fim" + unidade("f.unidade");
        if (procedimentos) sql += modelo == 5
                ? " AND NULLIF(TRIM(proc.item->>'coMsProcedimento'), '') IS NOT NULL"
                : " AND jsonb_typeof(proc.item) = 'string' AND NULLIF(TRIM(proc.item #>> '{}'), '') IS NOT NULL";
        return sql;
    }
    private static String texto(Object value) { return value == null ? "" : value.toString(); }
}

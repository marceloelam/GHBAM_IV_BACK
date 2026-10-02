package br.com.iv.repository;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.entity.Afaturar;
import br.com.iv.entity.FaturamentoAih;

// Repositorio dedicado a producao do faturamento SUS (Fase 1), operando sobre a
// mesma tabela "afaturar" ja usada por AfaturarRepository - reaproveitamento
// deliberado (ver decisao de arquitetura no plano da Fase 1), sem tocar nas
// queries/uso ja existentes de AfaturarRepository.
@ApplicationScoped
public class FaturamentoProducaoRepository implements PanacheRepository<Afaturar> {

    public Afaturar buscarPorOrigem(String origemModulo, int origemId) {
        return find("origemModulo = ?1 and origemId = ?2", origemModulo, origemId).firstResult();
    }

    public List<Afaturar> listarPorOrigem(String origemModulo, int origemId) {
        return list("origemModulo = ?1 and origemId = ?2", origemModulo, origemId);
    }

    public List<Afaturar> listarParaContaAih(FaturamentoAih aih) {
        if (aih == null) {
            return List.of();
        }

        StringBuilder jpql = new StringBuilder("select distinct a from Afaturar a where (");
        Map<String, Object> params = new HashMap<>();
        jpql.append("(a.origemModulo = :origemModulo and a.origemId = :origemId)");
        params.put("origemModulo", "INTERNACAO");
        params.put("origemId", aih.getIdInternacao());

        if (aih.getIdcompetencia() != null && aih.getIdpaciente() > 0) {
            jpql.append(" or (a.idcompetencia = :idcompetenciaPaciente and a.idpaciente = :idpaciente)");
            params.put("idcompetenciaPaciente", aih.getIdcompetencia());
            params.put("idpaciente", aih.getIdpaciente());
        }

        Integer bam = numero(aih.getBam());
        if (aih.getIdcompetencia() != null && bam != null) {
            jpql.append(" or (a.idcompetencia = :idcompetenciaBam and a.bam = :bam)");
            params.put("idcompetenciaBam", aih.getIdcompetencia());
            params.put("bam", bam);
        }

        jpql.append(")");
        if (preenchido(aih.getUnidade()) && !"TODAS".equalsIgnoreCase(aih.getUnidade())) {
            jpql.append(" and upper(a.unidade) = :unidade");
            params.put("unidade", aih.getUnidade().trim().toUpperCase());
        }
        jpql.append(" order by a.data asc, a.idafaturar asc");

        var query = getEntityManager().createQuery(jpql.toString(), Afaturar.class);
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    public List<Afaturar> listarPaginado(Integer idcompetencia, String unidade, String status,
            String instrumentoRegistro, String busca, int pagina, int tamanho) {
        StringBuilder jpql = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();

        if (idcompetencia != null) {
            jpql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            jpql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        if (preenchido(status)) {
            jpql.append(" and status = :status");
            params.put("status", status.trim().toUpperCase());
        }
        if (preenchido(instrumentoRegistro)) {
            jpql.append(" and upper(replace(instrumentoRegistro, '-', '_')) = :instrumentoRegistro");
            params.put("instrumentoRegistro", normalizarInstrumentoRegistro(instrumentoRegistro));
        }
        if (preenchido(busca)) {
            jpql.append(" and (upper(nomepaciente) like :busca or upper(procedimento) like :busca or upper(descricao) like :busca)");
            params.put("busca", "%" + busca.trim().toUpperCase() + "%");
        }

        // Pedido acima do teto e limitado ao teto, nao rebaixado para 50. Antes cair
        // para 50 era silencioso e enganava quem varre a competencia paginando: o
        // cliente pedia 500, recebia 50 e concluia "veio menos que o pedido, acabou" -
        // parando na primeira pagina e enxergando so as 50 producoes mais recentes de
        // cada status.
        int tam = tamanho <= 0 ? 50 : Math.min(tamanho, 300);
        int pag = Math.max(pagina, 0);
        return find(jpql.toString(), Sort.by("idafaturar").descending(), params).page(pag, tam).list();
    }

    public long contarPorStatus(Integer idcompetencia, String unidade, String status) {
        StringBuilder jpql = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();
        if (idcompetencia != null) {
            jpql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            jpql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        if (preenchido(status)) {
            jpql.append(" and status = :status");
            params.put("status", status.trim().toUpperCase());
        }
        return count(jpql.toString(), params);
    }

    /** Producoes que o SIGTAP classificou como RAAS e ja passaram pelas criticas basicas. */
    public List<Afaturar> listarRaasAptasParaRascunho(Integer idcompetencia, String unidade) {
        StringBuilder jpql = new StringBuilder("idcompetencia = :idcompetencia and instrumentoRegistro = :instrumento and status = :status and idpaciente > 0");
        Map<String, Object> params = new HashMap<>();
        params.put("idcompetencia", idcompetencia);
        params.put("instrumento", "RAAS");
        params.put("status", "APTO_FATURAMENTO");
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            jpql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        return list(jpql.toString(), Sort.by("idafaturar").ascending(), params);
    }

    // Conferencia de fechamento: recem-nascido cuja producao saiu do BPA como coberta
    // pela AIH, mas cujo BAM nao tem internacao no censo.
    //
    // Esses casos nao sao faturados por caminho nenhum. A producao deixou o BPA porque
    // "a AIH paga", e a AIH nao existe: laudo de autorizacao so vira pre-AIH via
    // "join internacao" pelo BAM (LaudoAutorizacaoRepository.listarElegiveisParaAih),
    // entao bebe sem alocacao no censo fica invisivel para a conta hospitalar. O join e
    // interno - a linha some sem erro nem aviso, e o buraco so apareceria na glosa.
    //
    // A causa costuma ser operacional e ter conserto barato: a maternidade admitiu o RN
    // mas a alocacao no censo nao foi feita. Por isso a lista traz BAM e nome - e com
    // eles que alguem vai atras do registro.
    //
    // O casamento do BAM repete o do laudo de proposito (texto sem nao-digitos de um
    // lado, numero do outro): conferir por um criterio diferente daquele que decide a
    // elegibilidade daria falso conforto.
    //
    // So internacao.bam e texto (varchar 20); afaturar.bam ja e integer. Por isso a
    // limpeza de nao-digitos e o coalesce('') se aplicam apenas ao lado da internacao -
    // envolver a.bam em coalesce(a.bam, '') fazia o Postgres tentar converter '' para
    // integer e derrubava a consulta inteira com "sintaxe de entrada e invalida para
    // tipo integer". O cast e para bigint porque o campo texto aceita ate 20 digitos,
    // mais do que integer comporta.
    public List<Map<String, Object>> listarRecemNascidosSemInternacao(Integer idcompetencia, String unidade) {
        StringBuilder sql = new StringBuilder("""
                select a.bam as bam, min(a.nomepaciente) as paciente, count(*) as linhas
                  from afaturar a
                 where a.status = 'NAO_FATURAVEL'
                   and coalesce(a.classificacao_manual, false) = true
                   and trim(a.nomepaciente) ~* '^(r\\.?\\s*n\\.?(\\s|\\d|$)|recem[\\s-]?nascid)'
                   and not exists (
                         select 1 from internacao i
                          where nullif(regexp_replace(coalesce(i.bam, ''), '\\D', '', 'g'), '')::bigint
                                = a.bam
                       )
                """);
        Map<String, Object> params = new HashMap<>();
        if (idcompetencia != null) {
            sql.append(" and a.idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            sql.append(" and upper(a.unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        sql.append(" group by a.bam order by count(*) desc");

        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Map<String, Object>> resultado = new java.util.ArrayList<>();
        for (Object linha : query.getResultList()) {
            Object[] col = (Object[]) linha;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bam", col[0] == null ? "" : String.valueOf(col[0]));
            item.put("paciente", col[1] == null ? "" : String.valueOf(col[1]));
            item.put("linhas", col[2] == null ? 0 : ((Number) col[2]).intValue());
            resultado.add(item);
        }
        return resultado;
    }

    // Nao faturaveis que o lote "Corrigir todas" de fato pode mexer: os que cairam
    // nesse status por falta de dado, nao os que um operador descartou de proposito.
    // Descarte manual tem motivo e autor gravados (recem-nascido coberto pela AIH, por
    // exemplo) e nao e pendencia - contá-lo no botao prometia um trabalho que a
    // correcao nao faz, e que nao deve fazer.
    public long contarNaoFaturavelCorrigivel(Integer idcompetencia, String unidade) {
        StringBuilder jpql = new StringBuilder(
                "status = 'NAO_FATURAVEL' and coalesce(classificacaoManual, false) = false");
        Map<String, Object> params = new HashMap<>();
        if (idcompetencia != null) {
            jpql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            jpql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        return count(jpql.toString(), params);
    }

    // Quantos recem-nascidos a competencia ainda tem para tratar. Serve para a tela de
    // Producao decidir se o botao do painel de RN aparece: sem esta contagem o
    // frontend precisaria baixar a competencia inteira pagina a pagina so para
    // responder "existe algum?" - dezenas de milhares de linhas serializadas por uma
    // pergunta de sim/nao.
    //
    // A regex e a mesma regra de reconhecimento do frontend (recemNascidoSus.js):
    // maternidade cadastra o bebe sem documento com nome provisorio ("RN DE MARIA",
    // "R.N. DE ...", "RN 1 DE ..." nos gemeos). O separador depois do "RN" e
    // obrigatorio de proposito - sem ele "RNALDO PEREIRA" contaria como recem-nascido.
    //
    // Sao contados so os dois status que o painel trata: apto e revisao ja seguiram
    // adiante e nao entram na fila de RN.
    public long contarRecemNascidos(Integer idcompetencia, String unidade) {
        StringBuilder sql = new StringBuilder(
                "select count(*) from afaturar where status in ('PRODUCAO_COM_CRITICA', 'PENDENTE_CLASSIFICACAO') "
                + "and trim(nomepaciente) ~* '^(r\\.?\\s*n\\.?(\\s|\\d|$)|recem[\\s-]?nascid)'");
        Map<String, Object> params = new HashMap<>();
        if (idcompetencia != null) {
            sql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            sql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return ((Number) query.getSingleResult()).longValue();
    }

    // Producao sem CBO agrupada por profissional. A correcao mora no cadastro do
    // servidor, nao na linha: 667 linhas sem CBO costumam ser algumas dezenas de
    // profissionais, e resolver por matricula troca centenas de consultas por uma.
    // Exportado fica de fora - remessa enviada nao se reescreve.
    @SuppressWarnings("unchecked")
    public List<Object[]> agruparMatriculasSemCbo(Integer idcompetencia, String unidade) {
        StringBuilder sql = new StringBuilder("""
                select trim(matriculaservidor) as matricula,
                       max(coalesce(nomeservidor, '')) as nome,
                       count(*) as linhas
                  from afaturar
                 where coalesce(exportado, false) = false
                   and coalesce(trim(cbo), '') = ''
                   and coalesce(trim(matriculaservidor), '') <> ''
                """);
        Map<String, Object> params = new HashMap<>();
        if (idcompetencia != null) {
            sql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            sql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        sql.append(" group by trim(matriculaservidor) order by count(*) desc limit 300");
        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    /**
     * Remessas geradas que nunca foram transmitidas.
     *
     * Gerar o arquivo nao e receber. A remessa fica GERADA no disco do servidor ate alguem
     * importar no programa do DATASUS e registrar o protocolo - e enquanto isso o dinheiro
     * nao entrou, sem que nada na tela dissesse isso. Foi o que aconteceu com julho das duas
     * unidades: R$ 549 mil em arquivo pronto, parados por dias.
     *
     * Devolve [quantidade, valor, dias da mais antiga].
     */
    public Object[] remessasPendentesTransmissao(Integer idcompetencia, String unidade) {
        StringBuilder sql = new StringBuilder("""
                select count(*),
                       coalesce(sum(valor_estimado), 0),
                       coalesce(max(extract(day from now() - data_geracao)), 0)
                  from faturamento_remessa
                 where data_transmissao is null
                   and coalesce(status, '') not in ('CANCELADA', 'SUBSTITUIDA')
                """);
        Map<String, Object> params = new HashMap<>();
        if (idcompetencia != null) {
            sql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            sql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return (Object[]) query.getSingleResult();
    }

    /**
     * A proxima competencia que a unidade tem condicao de abrir.
     *
     * E a mais antiga com SIGTAP importado e sem competencia criada: importar a tabela e o
     * ato pelo qual o operador declara qual mes vai faturar, e abrir exige justamente essa
     * tabela. Ordem crescente de proposito - havendo meses atrasados, fatura-se o mais
     * antigo primeiro, que e o que corre risco de perder o prazo.
     *
     * Devolve null quando toda tabela importada ja virou competencia. Sem isto, depois de
     * fechar um mes a tela nao tinha como oferecer o seguinte: a competencia fechada
     * continuava selecionada e o botao de abrir nunca reaparecia.
     */
    public String proximaCompetenciaParaAbrir(String unidade) {
        StringBuilder sql = new StringBuilder("""
                select i.competencia
                  from faturamento_sigtap_importacao i
                 where coalesce(i.status, '') <> 'ERRO'
                   and not exists (
                       select 1
                         from faturamento_competencia c
                        where c.competencia = i.competencia
                          and upper(coalesce(c.unidade, '')) = upper(coalesce(i.unidade, ''))
                   )
                """);
        Map<String, Object> params = new HashMap<>();
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            sql.append(" and upper(i.unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        sql.append(" order by i.competencia limit 1");
        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        var resultado = query.getResultList();
        return resultado.isEmpty() ? null : String.valueOf(resultado.get(0));
    }

    // A competencia do SIGTAP mais recente que a unidade importou.
    //
    // Importar a tabela e o ato pelo qual o operador declara qual mes vai faturar - e abrir
    // a competencia exige justamente essa tabela. Sem consultar isto, a tela propunha um mes
    // pela data do calendario e oferecia um botao que o backend so sabia recusar.
    public String ultimaCompetenciaSigtapImportada(String unidade) {
        StringBuilder sql = new StringBuilder("""
                select competencia
                  from faturamento_sigtap_importacao
                 where coalesce(status, '') <> 'ERRO'
                """);
        Map<String, Object> params = new HashMap<>();
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            sql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        sql.append(" order by competencia desc limit 1");
        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        var resultado = query.getResultList();
        return resultado.isEmpty() ? null : String.valueOf(resultado.get(0));
    }

    // Producao apta separada por instrumento.
    //
    // O painel somava tudo num numero so e chamava de "pronto para enviar", mas os
    // instrumentos nao saem pelo mesmo caminho. BPA, APAC e RAAS o GHBAM gera E transmite
    // ao SIA. A AIH o GHBAM tambem gera (AihExporter, layout de interface texto do
    // SISAIH01), mas o arquivo e importado no SISAIH01, que numera, critica e transmite -
    // ou seja, depende de uma acao em outro sistema. Somar os dois faz o operador conferir
    // a remessa do SIA contra um total que ela nunca vai ter, e esquecer essa acao.
    public List<Object[]> agruparAptosPorInstrumento(Integer idcompetencia, String unidade) {
        StringBuilder sql = new StringBuilder("""
                select coalesce(nullif(btrim(instrumento_registro), ''), 'INDEFINIDO') as instrumento,
                       count(*) as linhas,
                       coalesce(sum(coalesce(pa_sa, 0) + coalesce(pa_sp, 0) + coalesce(pa_sh, 0)), 0) as valor
                  from afaturar
                 where status = 'APTO_FATURAMENTO'
                """);
        Map<String, Object> params = new HashMap<>();
        if (idcompetencia != null) {
            sql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            sql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        sql.append(" group by 1 order by 3 desc");
        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    // Herda o CBO do cadastro do servidor para a producao da competencia, em massa.
    //
    // A heranca ja existia em reprocessarInterno (recarregarProfissionalDoCadastro), mas
    // a preparacao em lote e SQL puro e nao passa por la: a producao antiga so herdava se
    // alguem clicasse em "Herdar CBO do servidor". Resultado pratico: medico com CBO
    // preenchido no cadastro aparecia como pendencia todo mes, num botao que sempre
    // devolvia o mesmo trabalho manual.
    //
    // Casa por matricula quando a linha tem, e pelo nome quando nao tem - a linha grava
    // nomeservidor e deixa matriculaservidor em branco com frequencia. O having garante a
    // mesma cautela do caminho individual: so grava quando todos os cadastros casados
    // apontam UM unico CBO de 6 digitos. Homonimo, ou o mesmo profissional com dois
    // cadastros divergentes, fica de fora e vai para a lista de pendencias.
    public int herdarCboDoCadastro(int idcompetencia, String unidade, String usuario, int limite) {
        String filtroUnidade = preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)
                ? " and upper(a.unidade) = :unidade"
                : "";
        var query = getEntityManager().createNativeQuery("""
                with candidatos as (
                    select a.idafaturar,
                           regexp_replace(coalesce(s.cbo, ''), '\\D', '', 'g') as cbo
                      from afaturar a
                      join servidor s
                        on (btrim(coalesce(a.matriculaservidor, '')) <> ''
                            and btrim(s.matricula) = btrim(a.matriculaservidor))
                        or (btrim(coalesce(a.matriculaservidor, '')) = ''
                            and btrim(coalesce(a.nomeservidor, '')) <> ''
                            and upper(btrim(s.nome)) = upper(btrim(a.nomeservidor)))
                     where a.idcompetencia = :idcompetencia
                       and btrim(coalesce(a.cbo, '')) = ''
                       and coalesce(a.faturado, false) = false
                       and coalesce(a.exportado, false) = false
                """ + filtroUnidade + """
                ),
                alvo as (
                    select idafaturar, min(cbo) as cbo
                      from candidatos
                     group by idafaturar
                    having count(distinct cbo) = 1 and length(min(cbo)) = 6
                     order by idafaturar
                """ + (limite > 0 ? " limit " + limite : "") + """
                )
                update afaturar a
                   set cbo = alvo.cbo,
                       dataalteracao = now(),
                       usuarioalterador = :usuario
                  from alvo
                 where a.idafaturar = alvo.idafaturar
                """)
                .setParameter("idcompetencia", idcompetencia)
                .setParameter("usuario", usuario == null ? "" : usuario);
        if (!filtroUnidade.isBlank()) {
            query.setParameter("unidade", unidade.trim().toUpperCase());
        }
        return query.executeUpdate();
    }

    // Producao sem CBO que tambem esta sem matricula do servidor, agrupada pelo nome.
    //
    // Existe porque o lancamento nem sempre grava a matricula: a linha fica com
    // nomeservidor preenchido e matriculaservidor em branco. Como agruparMatriculasSemCbo
    // exige matricula, essas linhas eram invisiveis para a acao em lote - o CBO estava
    // no cadastro do profissional e mesmo assim a producao ficava travada, sem caminho
    // nenhum na tela. O nome e a unica ponte que sobra.
    public List<Object[]> agruparNomesSemCboSemMatricula(Integer idcompetencia, String unidade) {
        StringBuilder sql = new StringBuilder("""
                select upper(trim(nomeservidor)) as nome,
                       count(*) as linhas
                  from afaturar
                 where coalesce(exportado, false) = false
                   and coalesce(trim(cbo), '') = ''
                   and coalesce(trim(matriculaservidor), '') = ''
                   and coalesce(trim(nomeservidor), '') <> ''
                """);
        Map<String, Object> params = new HashMap<>();
        if (idcompetencia != null) {
            sql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            sql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        sql.append(" group by upper(trim(nomeservidor)) order by count(*) desc limit 300");
        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    // Par de idsSemCboPorMatricula para o caminho do nome.
    @SuppressWarnings("unchecked")
    public List<Integer> idsSemCboPorNome(Integer idcompetencia, String unidade, String nome, int limite) {
        StringBuilder sql = new StringBuilder("""
                select idafaturar
                  from afaturar
                 where coalesce(exportado, false) = false
                   and coalesce(trim(cbo), '') = ''
                   and coalesce(trim(matriculaservidor), '') = ''
                   and upper(trim(nomeservidor)) = :nome
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("nome", nome == null ? "" : nome.trim().toUpperCase());
        if (idcompetencia != null) {
            sql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            sql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        sql.append(" order by idafaturar");
        if (limite > 0) {
            sql.append(" limit ").append(limite);
        }
        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    // Linhas de um profissional que continuam sem CBO. Chamado depois de gravar o
    // codigo no cadastro dele: sao estas que o reprocessamento faz herdar o dado.
    public List<Integer> idsSemCboPorMatricula(Integer idcompetencia, String unidade, String matricula,
            int limite) {
        StringBuilder sql = new StringBuilder("""
                select idafaturar
                  from afaturar
                 where coalesce(exportado, false) = false
                   and coalesce(trim(cbo), '') = ''
                   and trim(matriculaservidor) = :matricula
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("matricula", matricula.trim());
        if (idcompetencia != null) {
            sql.append(" and idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            sql.append(" and upper(unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        sql.append(" order by idafaturar limit ").append(Math.max(1, limite));
        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<?> linhas = query.getResultList();
        List<Integer> ids = new java.util.ArrayList<>();
        for (Object linha : linhas) {
            ids.add(((Number) linha).intValue());
        }
        return ids;
    }

    public long contarSemCompetenciaPorCompetencia(String competenciaAaaamm) {
        return contarSemCompetenciaPorCompetencia(competenciaAaaamm, null);
    }

    public long contarSemCompetenciaPorCompetencia(String competenciaAaaamm, String unidade) {
        String ano = competenciaAaaamm.substring(0, 4);
        String mes = competenciaAaaamm.substring(4, 6);
        String filtroUnidade = preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)
                ? " and upper(unidade) = :unidade"
                : "";
        var query = getEntityManager().createNativeQuery("""
                select count(*)
                  from afaturar
                 where coalesce(exportado, false) = false
                   and idcompetencia is null
                """ + filtroUnidade + """
                   and (
                         (data ~ '^\\d{4}-\\d{2}-\\d{2}' and substring(data,1,4) = :ano and substring(data,6,2) = :mes)
                      or (data ~ '^\\d{2}/\\d{2}/\\d{4}' and substring(data,4,2) = :mes and substring(data,7,4) = :ano)
                       )
                """)
                .setParameter("ano", ano)
                .setParameter("mes", mes);
        if (!filtroUnidade.isBlank()) {
            query.setParameter("unidade", unidade.trim().toUpperCase());
        }
        Object total = query.getSingleResult();
        return ((Number) total).longValue();
    }

    public int vincularSemCompetenciaPorCompetencia(int idcompetencia, String competenciaAaaamm, String unidade,
            String usuario) {
        return vincularSemCompetenciaPorCompetencia(idcompetencia, competenciaAaaamm, unidade, usuario, 0);
    }

    // Com limite: uma competencia real tem dezenas de milhares de linhas e o UPDATE
    // inteiro nao cabe no statement_timeout de 60s. Zero mantem o comportamento antigo
    // (sem LIMIT) para quem ja chamava assim.
    public int vincularSemCompetenciaPorCompetencia(int idcompetencia, String competenciaAaaamm, String unidade,
            String usuario, int limite) {
        String ano = competenciaAaaamm.substring(0, 4);
        String mes = competenciaAaaamm.substring(4, 6);
        String filtroUnidade = preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)
                ? " and upper(unidade) = :unidade"
                : "";
        // Trava as linhas em ordem crescente de idafaturar (CTE com FOR UPDATE) antes
        // de atualizar. Sem ordem definida este update em massa entrava em deadlock
        // (SQLSTATE 40P01) contra qualquer outra sessao mexendo em afaturar - duas
        // chamadas concorrentes desta mesma acao, ou uma migration de dados.
        var query = getEntityManager().createNativeQuery("""
                with alvo as (
                    select idafaturar
                      from afaturar
                     where coalesce(exportado, false) = false
                       and idcompetencia is null
                """ + filtroUnidade + """
                       and (
                             (data ~ '^\\d{4}-\\d{2}-\\d{2}' and substring(data,1,4) = :ano and substring(data,6,2) = :mes)
                          or (data ~ '^\\d{2}/\\d{2}/\\d{4}' and substring(data,4,2) = :mes and substring(data,7,4) = :ano)
                           )
                     order by idafaturar
                """ + (limite > 0 ? " limit " + limite : "") + """
                       for update
                )
                update afaturar a
                   set idcompetencia = :idcompetencia,
                       dataalteracao = now(),
                       usuarioalterador = :usuario
                  from alvo
                 where a.idafaturar = alvo.idafaturar
                """)
                .setParameter("idcompetencia", idcompetencia)
                .setParameter("ano", ano)
                .setParameter("mes", mes)
                .setParameter("usuario", usuario == null ? "" : usuario);
        if (!filtroUnidade.isBlank()) {
            query.setParameter("unidade", unidade.trim().toUpperCase());
        }
        return query.executeUpdate();
    }

    // Base do processamento em lote. IMPORTANTE: filtra por ano/mes da propria
    // competencia direto no SQL (mesmo padrao dual-formato ja usado em
    // AfaturarRepository.filtarPorPeriodoEunidade) - sem isso, "idcompetencia
    // is null" sozinho bateria em TODO o historico nao classificado do
    // sistema inteiro (qualquer unidade, qualquer epoca), travando o backend
    // numa unica transacao gigante. Limite defensivo de 2000 linhas por
    // chamada: se sobrar mais, o operador clica "Processar competencia" de
    // novo (idempotente) ate zerar.
    @SuppressWarnings("unchecked")
    public List<Afaturar> listarParaReprocessar(int idcompetencia, String competenciaAaaamm) {
        return listarParaReprocessar(idcompetencia, competenciaAaaamm, null);
    }

    @SuppressWarnings("unchecked")
    public List<Afaturar> listarParaReprocessar(int idcompetencia, String competenciaAaaamm, String unidade) {
        String ano = competenciaAaaamm.substring(0, 4);
        String mes = competenciaAaaamm.substring(4, 6);
        String filtroUnidade = preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)
                ? " and upper(unidade) = :unidade"
                : "";
        var query = getEntityManager().createNativeQuery("""
                select *
                  from afaturar
                 where coalesce(exportado, false) = false
                """ + filtroUnidade + """
                   and (
                         idcompetencia = :idcompetencia
                      or (
                            idcompetencia is null
                        and (
                              (data ~ '^\\d{4}-\\d{2}-\\d{2}' and substring(data,1,4) = :ano and substring(data,6,2) = :mes)
                           or (data ~ '^\\d{2}/\\d{2}/\\d{4}' and substring(data,4,2) = :mes and substring(data,7,4) = :ano)
                            )
                         )
                       )
                 order by case when idcompetencia is null then 0 else 1 end, idafaturar
                 limit 2000
                """, Afaturar.class)
                .setParameter("idcompetencia", idcompetencia)
                .setParameter("ano", ano)
                .setParameter("mes", mes);
        if (!filtroUnidade.isBlank()) {
            query.setParameter("unidade", unidade.trim().toUpperCase());
        }
        return query.getResultList();
    }

    // Pendentes de classificacao de instrumento da competencia. Usado pela acao
    // "Classificar todas" da producao: o lote roda inteiro no servidor em vez de o
    // frontend disparar uma requisicao por producao.
    // Classificacao em massa por UPDATE, no lugar de reprocessar produção a produção.
    // Reprocessar item a item (recarregar SIGTAP + revalidar + auditar, cada um em
    // transacao propria) levava mais que o statement_timeout de 60s ja em um lote de
    // 100, e a tela ficava presa em "Enviando o primeiro lote".
    //
    // Regra identica a FaturamentoProducaoService.classificarInstrumento. O status sai
    // APTO_FATURAMENTO e o proximo reprocessamento revalida e ajusta se preciso.
    //
    // ORDER BY idafaturar mantem a ordem de lock usada no resto da classe (evita
    // deadlock 40P01) e SKIP LOCKED pula linha que a aplicacao esteja usando agora,
    // em vez de esperar por ela - a proxima rodada a pega.
    public int classificarPendentesEmMassa(int idcompetencia, String unidade, int limite, String usuario) {
        int tam = limite <= 0 || limite > 5000 ? 2000 : limite;
        String filtroUnidade = preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)
                ? " and upper(unidade) = :unidade"
                : "";
        // ORDER BY/LIMIT vao em string comum, com os \n explicitos, em vez de continuarem
        // no text block: o bloco que vinha aqui fechava em `limit """` (delimitador fora
        // da propria linha), e nesse caso o Java descarta a indentacao inteira das linhas
        // de conteudo e ainda o espaco final de cada uma. O SQL saia com dois defeitos -
        // ":unidadeorder by idafaturar" (quebrava com "No parameter named ':unidade'"
        // quando havia unidade filtrada) e "limit2000" colado.
        var query = getEntityManager().createNativeQuery("""
                with alvo as (
                    select idafaturar
                      from afaturar
                     where idcompetencia = :idcompetencia
                       and (status = 'PENDENTE_CLASSIFICACAO'
                            or instrumento_registro = 'PENDENTE_CLASSIFICACAO')
                """ + filtroUnidade + "\n order by idafaturar\n limit " + tam + "\n" + """
                       for update skip locked
                )
                update afaturar a
                   set instrumento_registro = case
                           when btrim(coalesce(a.pa_tpreg, '')) in ('02', '2', '03', '3') then 'BPA_I'
                           when btrim(coalesce(a.pa_tpreg, '')) in ('01', '1') then 'BPA_C'
                           when coalesce(a.pa_sa, 0) <= 0 and coalesce(a.pa_sp, 0) <= 0
                                and coalesce(a.pa_sh, 0) <= 0 then 'NAO_FATURAVEL'
                           when length(regexp_replace(coalesce(a.cnsdopciente, ''), '\\D', '', 'g')) = 15 then 'BPA_I'
                           else 'BPA_C'
                       end,
                       classificacao_manual = case
                           when coalesce(a.pa_sa, 0) <= 0 and coalesce(a.pa_sp, 0) <= 0
                                and coalesce(a.pa_sh, 0) <= 0 then false
                           else true
                       end,
                       status = case
                           when coalesce(a.pa_sa, 0) <= 0 and coalesce(a.pa_sp, 0) <= 0
                                and coalesce(a.pa_sh, 0) <= 0 then 'NAO_FATURAVEL'
                           else 'APTO_FATURAMENTO'
                       end,
                       dataalteracao = now(),
                       usuarioalterador = :usuario
                  from alvo
                 where a.idafaturar = alvo.idafaturar
                """)
                .setParameter("idcompetencia", idcompetencia)
                .setParameter("usuario", usuario == null ? "" : usuario);
        if (!filtroUnidade.isBlank()) {
            query.setParameter("unidade", unidade.trim().toUpperCase());
        }
        return query.executeUpdate();
    }

    // Classificacao em massa como AIH para a conta hospitalar. Mesmo racional de
    // classificarPendentesEmMassa: reprocessar producao a producao em transacao
    // propria (recarregar SIGTAP + revalidar + regravar criticas + auditar) deixava
    // o contexto de persistencia da requisicao acumular o batch de update ate
    // estourar o socketTimeout do datasource e o commit falhar com "Este comando foi
    // fechado" (RollbackException ARJUNA016053). Aqui o UPDATE e unico, o
    // instrumento e forcado para AIH (classificacao manual explicita, regra distinta
    // da pa_tpreg usada em classificarPendentesEmMassa) e o status sai
    // APTO_FATURAMENTO; o proximo reprocessamento revalida e ajusta se preciso.
    public int classificarPendentesComoAihEmMassa(List<Integer> ids, String usuario) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return getEntityManager().createNativeQuery("""
                update afaturar a
                   set instrumento_registro = 'AIH',
                       classificacao_manual = true,
                       possui_critica = false,
                       status = 'APTO_FATURAMENTO',
                       dataalteracao = now(),
                       usuarioalterador = :usuario
                 where a.idafaturar in (:ids)
                   and (a.instrumento_registro = 'PENDENTE_CLASSIFICACAO'
                        or a.status = 'PENDENTE_CLASSIFICACAO')
                """)
                .setParameter("ids", ids)
                .setParameter("usuario", usuario == null ? "" : usuario)
                .executeUpdate();
    }

    // Vincula a conta hospitalar producao que estava parada em NAO_FATURAVEL porque o
    // procedimento nao e de BPA (tipo de registro 05: AIH/APAC/RAAS).
    //
    // Nao mexe em quem foi classificado a mao (classificacao_manual): descarte com motivo
    // gravado e decisao humana, e o lote nao desfaz decisao humana. O UPDATE tambem repete
    // a condicao de status para nao pegar linha que mudou entre a leitura e a gravacao.
    public int vincularNaoFaturaveisComoAihEmMassa(List<Integer> ids, String usuario) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return getEntityManager().createNativeQuery("""
                update afaturar a
                   set instrumento_registro = 'AIH',
                       classificacao_manual = true,
                       possui_critica = false,
                       status = 'APTO_FATURAMENTO',
                       dataalteracao = now(),
                       usuarioalterador = :usuario
                 where a.idafaturar in (:ids)
                   and a.status = 'NAO_FATURAVEL'
                   and coalesce(a.classificacao_manual, false) = false
                """)
                .setParameter("ids", ids)
                .setParameter("usuario", usuario == null ? "" : usuario)
                .executeUpdate();
    }

    // Quantos procedimentos o SIGTAP importado tem para a competencia. Zero
    // significa competencia nao importada - e nesse caso sincronizarComSigtap nao
    // pode rodar, senao marcaria a producao inteira como pendente.
    public long contarSigtapDaCompetencia(String competencia) {
        Number total = (Number) getEntityManager()
                .createNativeQuery("select count(*) from s_proced where pa_cmp = :competencia")
                .setParameter("competencia", competencia)
                .getSingleResult();
        return total == null ? 0 : total.longValue();
    }

    // Cruza a producao da competencia com o SIGTAP importado (s_proced da mesma
    // competencia) e resolve instrumento e status de uma vez, em vez de deixar a
    // linha "pendente" esperando reprocessamento individual.
    //
    // Por que isto existe: pa_sa/pa_sp/pa_sh/pa_tpreg sao copiados para afaturar no
    // momento em que a producao nasce (GravaFaturamento). Quando o SIGTAP e
    // importado depois - o caso normal - esses campos ficam zerados e a producao cai
    // em NAO_FATURAVEL/PENDENTE por falta de dado, nao por regra de negocio. Aqui os
    // valores sao relidos do SIGTAP da competencia e as regras sao aplicadas em um
    // UPDATE unico.
    //
    // As regras sao as mesmas do motor, para as duas fontes nao divergirem:
    //   instrumento -> FaturamentoProducaoService.classificarInstrumento
    //   status      -> FaturamentoValidationService (CNS obrigatorio fora do BPA-C,
    //                  CNES da unidade, CBO como alerta)
    // O CNES nao entra no SQL porque e configuracao da unidade, igual para todas as
    // linhas: o service resolve antes e passa em cnesConfigurado.
    //
    // Nunca toca em producao ja faturada nem em classificacao manual: decisao humana
    // e producao fechada continuam valendo.
    public int sincronizarComSigtapDaCompetencia(int idcompetencia, String competencia, String unidade,
            boolean cnesConfigurado, String usuario) {
        return sincronizarComSigtapDaCompetencia(idcompetencia, competencia, unidade, cnesConfigurado, usuario, 0);
    }

    // Com limite: usado pela preparacao em lote (prepararEmLote), que chama isto uma vez
    // por rodada. Sem limite, a rodada N reescrevia todas as N x 5.000 linhas ja
    // vinculadas - a rodada 1 tocava 5.000, a rodada 17 tocava 81.412, e o total dava
    // ~765.000 escritas para 81.412 linhas. Cada rodada ficava mais lenta que a anterior
    // ate a query estourar o socketTimeout de 120s do driver JDBC, derrubando a conexao
    // com "An I/O error occurred while sending to the backend".
    //
    // Com limite, so entram as linhas que ainda nao foram classificadas - exatamente as
    // que a rodada acabou de vincular. Zero mantem a varredura completa, que e o que a
    // acao explicita "Cruzar com o SIGTAP" precisa: ali o objetivo e justamente reler o
    // SIGTAP sobre producao ja classificada (SIGTAP corrigido, competencia reimportada).
    public int sincronizarComSigtapDaCompetencia(int idcompetencia, String competencia, String unidade,
            boolean cnesConfigurado, String usuario, int limite) {
        String filtroUnidade = preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)
                ? " and upper(a.unidade) = :unidade"
                : "";
        // Mesmo criterio de contarPendentesDeClassificacao: pendente e quando o status OU
        // o instrumento ficou em PENDENTE_CLASSIFICACAO.
        String filtroPendentes = limite > 0
                ? """
                       and (a.status is null
                            or a.instrumento_registro is null
                            or a.status = 'PENDENTE_CLASSIFICACAO'
                            or a.instrumento_registro = 'PENDENTE_CLASSIFICACAO')
                """
                : "";
        String ordemLimite = limite > 0 ? " order by a.idafaturar limit " + limite : "";
        var query = getEntityManager().createNativeQuery("""
                with sigtap as (
                    select concat(sp.pa_id, sp.pa_dv) as codigo,
                           sp.pa_dc, sp.pa_sa, sp.pa_sp, sp.pa_sh, sp.pa_tpreg, sp.pa_eletiva,
                           row_number() over (partition by concat(sp.pa_id, sp.pa_dv)
                                              order by sp.idprocedimentossus desc) as rn
                      from s_proced sp
                     where sp.pa_cmp = :competencia
                ),
                alvo as (
                    select a.idafaturar,
                           s.pa_dc, s.pa_sa, s.pa_sp, s.pa_sh, s.pa_tpreg, s.pa_eletiva,
                           -- Instrumento de registro do SIGTAP (CO_REGISTRO):
                           --   01 BPA-C | 02 BPA-I | 03/04 AIH | 05/06/07 APAC | 08/09 RAAS
                           -- Conferido contra os dados da propria tabela: 1.612 dos 1.623
                           -- procedimentos com registro 03 tem valor de Servico Hospitalar
                           -- (pa_sh), e nenhum dos 864 com 05/06/07 tem. SH so se paga em
                           -- internacao. Ver FaturamentoProducaoService.classificarInstrumento.
                           --
                           -- pa_tpreg guarda TODOS os registros do procedimento concatenados
                           -- (ver SigtapImportacaoService.lerRegistros), por isso o teste e
                           -- "contem" e nao igualdade. O ambulatorial vem primeiro: quando o
                           -- procedimento aceita BPA e AIH, o que decide e o paciente estar
                           -- internado, e essa informacao nao esta nesta linha.
                           case
                               when btrim(coalesce(s.pa_tpreg, '')) like '%02%' then 'BPA_I'
                               when btrim(coalesce(s.pa_tpreg, '')) like '%01%' then 'BPA_C'
                               when btrim(coalesce(s.pa_tpreg, '')) like '%08%'
                                    or btrim(coalesce(s.pa_tpreg, '')) like '%09%' then 'RAAS'
                               -- 03 a 07 sao todos AIH: principal, especial e secundario.
                               -- Os sem valor (05/06/07) nao sao ambulatoriais - sao os que
                               -- compoem a conta e nao se cobram a parte, como a consulta
                               -- diaria ao paciente internado. Ver classificarInstrumento.
                               when btrim(coalesce(s.pa_tpreg, '')) like '%03%'
                                    or btrim(coalesce(s.pa_tpreg, '')) like '%04%'
                                    or btrim(coalesce(s.pa_tpreg, '')) like '%05%'
                                    or btrim(coalesce(s.pa_tpreg, '')) like '%06%'
                                    or btrim(coalesce(s.pa_tpreg, '')) like '%07%' then 'AIH'
                               when coalesce(s.pa_sa, 0) <= 0 and coalesce(s.pa_sp, 0) <= 0
                                    and coalesce(s.pa_sh, 0) <= 0 then 'NAO_FATURAVEL'
                               when length(regexp_replace(coalesce(a.cnsdopciente, ''), '\\D', '', 'g')) = 15
                                    then 'BPA_I'
                               else 'BPA_C'
                           end as instrumento,
                           btrim(coalesce(a.cnsdopciente, '')) <> '' as tem_cns,
                           btrim(coalesce(a.cbo, '')) <> '' as tem_cbo
                      from afaturar a
                      join sigtap s on s.codigo = a.procedimento and s.rn = 1
                     where a.idcompetencia = :idcompetencia
                       and coalesce(a.classificacao_manual, false) = false
                       and coalesce(a.faturado, false) = false
                """ + filtroUnidade + filtroPendentes + ordemLimite + """
                )
                update afaturar a
                   set descricao = alvo.pa_dc,
                       pa_sa = alvo.pa_sa,
                       pa_sp = alvo.pa_sp,
                       pa_sh = alvo.pa_sh,
                       pa_tpreg = alvo.pa_tpreg,
                       pa_eletiva = alvo.pa_eletiva,
                       instrumento_registro = alvo.instrumento,
                       status = case
                           when alvo.instrumento = 'NAO_FATURAVEL' then 'NAO_FATURAVEL'
                           when not :cnesconfigurado then 'PRODUCAO_COM_CRITICA'
                           when alvo.instrumento = 'BPA_I' and not alvo.tem_cns then 'PRODUCAO_COM_CRITICA'
                           when not alvo.tem_cbo then 'REVISAO_RECOMENDADA'
                           else 'APTO_FATURAMENTO'
                       end,
                       possui_critica = case
                           when alvo.instrumento = 'NAO_FATURAVEL' then false
                           when not :cnesconfigurado then true
                           when alvo.instrumento = 'BPA_I' and not alvo.tem_cns then true
                           when not alvo.tem_cbo then true
                           else false
                       end,
                       dataalteracao = now(),
                       usuarioalterador = :usuario
                  from alvo
                 where a.idafaturar = alvo.idafaturar
                """)
                .setParameter("idcompetencia", idcompetencia)
                .setParameter("competencia", competencia)
                .setParameter("cnesconfigurado", cnesConfigurado)
                .setParameter("usuario", usuario == null ? "" : usuario);
        if (!filtroUnidade.isBlank()) {
            query.setParameter("unidade", unidade.trim().toUpperCase());
        }
        return query.executeUpdate();
    }

    // Producao cujo procedimento nao existe no SIGTAP importado da competencia: fica
    // pendente de verdade (falta importar a competencia certa ou o codigo esta
    // errado), e nao "nao faturavel" - que sugeriria decisao ja tomada. So roda
    // quando ha SIGTAP importado para a competencia (ver contarSigtapDaCompetencia).
    public int marcarSemSigtapComoPendente(int idcompetencia, String competencia, String unidade, String usuario) {
        return marcarSemSigtapComoPendente(idcompetencia, competencia, unidade, usuario, 0);
    }

    // Com limite: o outro passo sem teto de prepararEmLote. Alem do limite, pula quem ja
    // esta pendente - sem isso a rodada reescrevia as mesmas linhas toda vez, gravando
    // exatamente o valor que ja estava la. Zero mantem o comportamento antigo (varredura
    // completa, contagem total), que e o que a acao explicita reporta ao operador como
    // "N sem procedimento no SIGTAP".
    public int marcarSemSigtapComoPendente(int idcompetencia, String competencia, String unidade, String usuario,
            int limite) {
        String filtroUnidade = preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)
                ? " and upper(a.unidade) = :unidade"
                : "";
        String filtroJaPendente = limite > 0
                ? """
                       and (a.status is distinct from 'PENDENTE_CLASSIFICACAO'
                            or a.instrumento_registro is distinct from 'PENDENTE_CLASSIFICACAO')
                """
                : "";
        String ordemLimite = limite > 0 ? " order by a.idafaturar limit " + limite : "";
        var query = getEntityManager().createNativeQuery("""
                with alvo as (
                    select a.idafaturar
                      from afaturar a
                     where a.idcompetencia = :idcompetencia
                       and coalesce(a.classificacao_manual, false) = false
                       and coalesce(a.faturado, false) = false
                       and not exists (
                           select 1
                             from s_proced sp
                            where concat(sp.pa_id, sp.pa_dv) = a.procedimento
                              and sp.pa_cmp = :competencia
                       )
                """ + filtroUnidade + filtroJaPendente + ordemLimite + """
                )
                update afaturar a
                   set instrumento_registro = 'PENDENTE_CLASSIFICACAO',
                       status = 'PENDENTE_CLASSIFICACAO',
                       dataalteracao = now(),
                       usuarioalterador = :usuario
                  from alvo
                 where a.idafaturar = alvo.idafaturar
                """)
                .setParameter("idcompetencia", idcompetencia)
                .setParameter("competencia", competencia)
                .setParameter("usuario", usuario == null ? "" : usuario);
        if (!filtroUnidade.isBlank()) {
            query.setParameter("unidade", unidade.trim().toUpperCase());
        }
        return query.executeUpdate();
    }

    // Conta pelo mesmo criterio de classificarPendentesEmMassa: a producao esta
    // pendente quando o status OU o instrumento ficou em PENDENTE_CLASSIFICACAO
    // (uma linha com critica pode ter status PRODUCAO_COM_CRITICA e instrumento
    // ainda pendente). Contar so pelo status escondia essas linhas do lote.
    public long contarPendentesDeClassificacao(int idcompetencia, String unidade) {
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            return count("idcompetencia = ?1 and (status = ?2 or instrumentoRegistro = ?2) and upper(unidade) = ?3",
                    idcompetencia, "PENDENTE_CLASSIFICACAO", unidade.trim().toUpperCase());
        }
        return count("idcompetencia = ?1 and (status = ?2 or instrumentoRegistro = ?2)",
                idcompetencia, "PENDENTE_CLASSIFICACAO");
    }

    // Devolve para a fila de exportacao a producao que saiu numa remessa cancelada.
    // Casa pelo numero da remessa gravado na linha (V119), que e o unico criterio que
    // nao arrasta junto a producao de outra remessa do mesmo mes.
    public long reverterExportacaoPorRemessa(String numeroRemessa) {
        return update("exportado = false, numeroRemessa = null where numeroRemessa = ?1", numeroRemessa);
    }

    // Fallback para remessa gerada antes da V119, quando a linha nao guardava o numero:
    // libera o recorte inteiro (competencia + unidade + instrumento), limitado as linhas
    // sem numero de remessa. So e seguro quando aquela e a unica remessa viva do recorte -
    // quem decide isso e o service, que enxerga as outras remessas.
    public long reverterExportacaoPorInstrumento(int idcompetencia, String instrumentoRegistro, String unidade) {
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            return update("exportado = false, numeroRemessa = null where idcompetencia = ?1"
                    + " and upper(replace(instrumentoRegistro, '-', '_')) = ?2 and coalesce(exportado, false) = true"
                    + " and numeroRemessa is null and upper(unidade) = ?3",
                    idcompetencia, normalizarInstrumentoRegistro(instrumentoRegistro), unidade.trim().toUpperCase());
        }
        return update("exportado = false, numeroRemessa = null where idcompetencia = ?1"
                + " and upper(replace(instrumentoRegistro, '-', '_')) = ?2 and coalesce(exportado, false) = true"
                + " and numeroRemessa is null",
                idcompetencia, normalizarInstrumentoRegistro(instrumentoRegistro));
    }

    public List<Afaturar> listarElegiveisParaExportacao(int idcompetencia, String instrumentoRegistro) {
        return listarElegiveisParaExportacao(idcompetencia, instrumentoRegistro, null);
    }

    public List<Afaturar> listarElegiveisParaExportacao(int idcompetencia, String instrumentoRegistro, String unidade) {
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            return list("idcompetencia = ?1 and upper(replace(instrumentoRegistro, '-', '_')) = ?2 and status = ?3 and coalesce(exportado, false) = false and upper(unidade) = ?4",
                    idcompetencia, normalizarInstrumentoRegistro(instrumentoRegistro), "APTO_FATURAMENTO",
                    unidade.trim().toUpperCase());
        }
        return list("idcompetencia = ?1 and upper(replace(instrumentoRegistro, '-', '_')) = ?2 and status = ?3 and coalesce(exportado, false) = false",
                idcompetencia, normalizarInstrumentoRegistro(instrumentoRegistro), "APTO_FATURAMENTO");
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> diagnosticoExportacao(int idcompetencia, String unidade) {
        String filtroUnidade = preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)
                ? " and upper(unidade) = :unidade"
                : "";
        var query = getEntityManager().createNativeQuery("""
                select coalesce(nullif(upper(replace(instrumento_registro, '-', '_')), ''), 'SEM_INSTRUMENTO') as instrumento,
                       count(*) as total,
                       count(*) filter (where status = 'APTO_FATURAMENTO' and coalesce(exportado, false) = false) as aptos_nao_exportados,
                       count(*) filter (where status = 'APTO_FATURAMENTO' and coalesce(exportado, false) = true) as aptos_exportados,
                       count(*) filter (where status = 'PRODUCAO_COM_CRITICA') as com_critica,
                       count(*) filter (where status = 'PENDENTE_CLASSIFICACAO') as pendentes,
                       count(*) filter (where status = 'NAO_FATURAVEL') as nao_faturavel,
                       coalesce(sum(pa_sa + pa_sp + pa_sh) filter (where status = 'APTO_FATURAMENTO' and coalesce(exportado, false) = false), 0) as valor_apto_nao_exportado
                  from afaturar
                 where idcompetencia = :idcompetencia
                """ + filtroUnidade + """
                 group by instrumento
                 order by instrumento
                """)
                .setParameter("idcompetencia", idcompetencia);
        if (!filtroUnidade.isBlank()) {
            query.setParameter("unidade", unidade.trim().toUpperCase());
        }
        return query.getResultList();
    }


    // O que está travando a competência, agrupado por causa, com o dinheiro parado em
    // cada uma. É o dado que o painel de fechamento mostra.
    //
    // Por que em SQL e não pelo motor de validação: agruparMotivosDeCritica revalida
    // linha a linha em memória, e cada validação consulta SIGTAP, configuração da unidade
    // e competência. Numa competência real (12 mil linhas) isso são dezenas de milhares
    // de consultas e a tela não abre. Aqui é uma varredura só.
    //
    // O preço dessa escolha é honesto: as regras aparecem duas vezes no código, e esta
    // versão é uma aproximação da outra. A diferença conhecida está no CNS - o motor roda
    // o módulo 11 completo e aqui só se confere o tamanho, então CNS com 15 dígitos e
    // dígito verificador errado conta como válido neste agrupamento e cai em crítica
    // depois. O agrupamento serve para priorizar trabalho, não para decidir o status de
    // uma linha - quem decide isso continua sendo o motor.
    public List<Object[]> agruparBloqueios(int idcompetencia, String unidade, String competenciaAaaamm) {
        String filtroUnidade = preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)
                ? " and upper(a.unidade) = :unidade"
                : "";
        // Os dois cadastros entram no from porque duas causas precisam saber se o dado
        // que falta na producao existe no cadastro - e essa e a diferenca entre "clique
        // aqui que eu resolvo" e "alguem precisa buscar no CADSUS".
        String base = """
                 from afaturar a
                 left join pacientes p on p.idpaciente = a.idpaciente
                 left join servidor s on trim(s.matricula) = trim(a.matriculaservidor)
                where a.idcompetencia = :idcompetencia
                  and coalesce(a.exportado, false) = false
                  and a.status in ('PRODUCAO_COM_CRITICA', 'REVISAO_RECOMENDADA', 'PENDENTE_CLASSIFICACAO')
                """ + filtroUnidade;

        var query = getEntityManager().createNativeQuery("""
                select 'CNS_PACIENTE' as causa,
                       count(*) as linhas,
                       coalesce(sum(a.pa_sa + a.pa_sp + a.pa_sh), 0) as valor,
                       count(*) filter (where length(regexp_replace(coalesce(p.cns, ''), '[^0-9]', '', 'g')) = 15) as resolve_sozinho
                """ + base + """
                  and upper(coalesce(a.instrumento_registro, '')) <> 'BPA_C'
                  and length(regexp_replace(coalesce(a.cnsdopciente, ''), '[^0-9]', '', 'g')) <> 15
                  and trim(coalesce(a.nomepaciente, '')) !~* '^(r[.]?[[:space:]]*n[.]?([[:space:]]|[0-9]|$)|recem[[:space:]-]?nascid)'
                union all
                -- Recem-nascido e outra conversa: ele nao tem CNS porque ainda nao existe
                -- documento, e o atendimento dele nasce coberto pela AIH da internacao.
                -- Cobrar CNS aqui misturaria com o paciente comum, onde o CNS existe e so
                -- nao foi informado - sao tarefas diferentes para quem vai corrigir.
                select 'RECEM_NASCIDO_SEM_CNS', count(*), coalesce(sum(a.pa_sa + a.pa_sp + a.pa_sh), 0), 0
                """ + base + """
                  and length(regexp_replace(coalesce(a.cnsdopciente, ''), '[^0-9]', '', 'g')) <> 15
                  and trim(coalesce(a.nomepaciente, '')) ~* '^(r[.]?[[:space:]]*n[.]?([[:space:]]|[0-9]|$)|recem[[:space:]-]?nascid)'
                union all
                select 'CBO_PROFISSIONAL', count(*), coalesce(sum(a.pa_sa + a.pa_sp + a.pa_sh), 0),
                       count(*) filter (where length(regexp_replace(coalesce(s.cbo, ''), '[^0-9]', '', 'g')) = 6)
                """ + base + """
                  and coalesce(trim(a.cbo), '') = ''
                union all
                select 'PROCEDIMENTO_FORA_SIGTAP', count(*), coalesce(sum(a.pa_sa + a.pa_sp + a.pa_sh), 0), 0
                """ + base + """
                  and not exists (select 1 from s_proced sp
                                   where concat(sp.pa_id, sp.pa_dv) = a.procedimento
                                     and sp.pa_cmp = :competencia)
                union all
                select 'SEM_CLASSIFICACAO', count(*), coalesce(sum(a.pa_sa + a.pa_sp + a.pa_sh), 0), 0
                """ + base + """
                  and a.status = 'PENDENTE_CLASSIFICACAO'
                """);
        query.setParameter("idcompetencia", idcompetencia);
        query.setParameter("competencia", competenciaAaaamm == null ? "" : competenciaAaaamm);
        if (!filtroUnidade.isBlank()) {
            query.setParameter("unidade", unidade.trim().toUpperCase());
        }
        return query.getResultList();
    }
    public double somarValorEstimado(Integer idcompetencia, String unidade) {
        return somarValorEstimado(idcompetencia, unidade, null);
    }

    // status nulo = toda a producao da competencia (comportamento historico, usado no
    // snapshot de fechamento). Com status, soma so aquele grupo - e o que permite ao
    // painel separar o valor que vai ser exportado do valor preso em critica, em vez de
    // mostrar um total que mistura os dois e parece maior do que sera pago.
    public double somarValorEstimado(Integer idcompetencia, String unidade, String status) {
        StringBuilder jpql = new StringBuilder("select coalesce(sum(a.pa_sa + a.pa_sp + a.pa_sh), 0) from Afaturar a where 1=1");
        Map<String, Object> params = new HashMap<>();
        if (idcompetencia != null) {
            jpql.append(" and a.idcompetencia = :idcompetencia");
            params.put("idcompetencia", idcompetencia);
        }
        if (preenchido(unidade) && !"TODAS".equalsIgnoreCase(unidade)) {
            jpql.append(" and upper(a.unidade) = :unidade");
            params.put("unidade", unidade.trim().toUpperCase());
        }
        if (preenchido(status)) {
            jpql.append(" and a.status = :status");
            params.put("status", status.trim().toUpperCase());
        }
        var query = getEntityManager().createQuery(jpql.toString(), Double.class);
        params.forEach(query::setParameter);
        return query.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> resumoPorUnidade(int idcompetencia) {
        return resumoPorUnidade(idcompetencia, null);
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> resumoPorUnidade(int idcompetencia, String unidade) {
        String filtroUnidade = unidade == null || unidade.isBlank() ? "" : " and upper(unidade) = :unidade";
        var query = getEntityManager().createNativeQuery("""
                select unidade,
                       count(*) as total,
                       count(*) filter (where status = 'APTO_FATURAMENTO') as aptos,
                       count(*) filter (where status = 'PRODUCAO_COM_CRITICA') as criticas,
                       coalesce(sum(pa_sa + pa_sp + pa_sh), 0) as valor_estimado,
                       coalesce(sum(pa_sa + pa_sp + pa_sh)
                           filter (where status = 'APTO_FATURAMENTO'), 0) as valor_apto,
                       coalesce(sum(pa_sa + pa_sp + pa_sh)
                           filter (where status in ('PRODUCAO_COM_CRITICA', 'REVISAO_RECOMENDADA',
                                                    'PENDENTE_CLASSIFICACAO')), 0) as valor_retido
                  from afaturar
                 where idcompetencia = :idcompetencia
                """ + filtroUnidade + """
                 group by unidade
                 order by unidade
                """)
                .setParameter("idcompetencia", idcompetencia);
        if (!filtroUnidade.isBlank()) {
            query.setParameter("unidade", unidade.trim().toUpperCase());
        }
        return query.getResultList();
    }

    private String normalizarInstrumentoRegistro(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase().replace('-', '_');
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.trim().isBlank();
    }

    private Integer numero(String valor) {
        if (!preenchido(valor)) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        if (digitos.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(digitos);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

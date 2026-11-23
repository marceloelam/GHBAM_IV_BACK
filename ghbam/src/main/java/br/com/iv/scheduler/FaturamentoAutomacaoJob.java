package br.com.iv.scheduler;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import br.com.iv.dto.SigtapArquivoDatasusDto;
import br.com.iv.dto.SigtapImportarDatasusRequestDto;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import br.com.iv.repository.FaturamentoCompetenciaRepository;
import br.com.iv.repository.FaturamentoSigtapImportacaoRepository;
import br.com.iv.service.FaturamentoAuditService;
import br.com.iv.service.FaturamentoCompetenciaService;
import br.com.iv.service.FaturamentoProducaoService;
import br.com.iv.service.SigtapImportacaoService;

// Preparo automatico da competencia de faturamento.
//
// O modulo pedia que o operador executasse, na ordem certa, seis etapas que o sistema
// tem como fazer sozinho: importar o SIGTAP, abrir a competencia, vincular a producao,
// cruzar com o SIGTAP, classificar o que ficou pendente e puxar o CNS do cadastro.
// Nenhuma delas envolve decisao - sao mecanicas, e errar a ordem so gera retrabalho.
//
// A ABERTURA da competencia saiu dessa lista e hoje vem desligada
// (ghbam.faturamento.automacao.abrir-competencia=false). Abrir e o ato pelo qual o
// operador declara qual mes ele vai faturar; quando o job abria sozinho, ele chegava na
// tela com uma competencia em andamento que nao foi ele quem escolheu, e sem saber de
// onde tinha vindo. O job prepara o mes que o operador abriu - nao decide qual e.
//
// O que este job NAO faz, de proposito: fechar competencia, exportar remessa, descartar
// producao ou inventar dado que nao existe. Sao as acoes com consequencia fora do GHBAM
// (dinheiro, prazo, auditoria) e continuam sendo decisao de uma pessoa, no painel.
//
// Roda de madrugada porque processa a competencia inteira. E pode ser desligado sem
// deploy: ghbam.faturamento.automacao.habilitada=false.
@ApplicationScoped
public class FaturamentoAutomacaoJob {

    private static final Logger LOG = Logger.getLogger(FaturamentoAutomacaoJob.class);
    private static final DateTimeFormatter AAAAMM = DateTimeFormatter.ofPattern("yyyyMM");

    @ConfigProperty(name = "ghbam.faturamento.automacao.habilitada", defaultValue = "true")
    boolean habilitada;

    // A importacao busca o arquivo no FTP do DATASUS. Em ambiente sem saida para a
    // internet isso falha toda madrugada e polui o log - por isso e uma chave separada:
    // da para manter o preparo da producao e desligar so o download.
    @ConfigProperty(name = "ghbam.faturamento.automacao.importar-sigtap", defaultValue = "true")
    boolean importarSigtap;

    // Abrir a competencia sozinho fica DESLIGADO por padrao: quem escolhe o mes que sera
    // faturado e o operador, na tela. Sem competencia aberta o job nao tem o que preparar
    // naquela unidade e apenas registra o adiamento na auditoria, dizendo o proximo passo.
    // Quem preferir o comportamento antigo liga a chave e o job volta a abrir o mes alvo.
    @ConfigProperty(name = "ghbam.faturamento.automacao.abrir-competencia", defaultValue = "false")
    boolean abrirCompetencia;

    // Busca de CNS sozinha, sem esperar a rodada de madrugada: RN cadastrado no CADSUS
    // Web de manha ja aparece resolvido a tarde, sem ninguem clicar em "Buscar CNS no
    // CADSUS". So repete a consulta ao CNES/CNS-PDQ - nao reabre nem reprocessa nada.
    @ConfigProperty(name = "ghbam.faturamento.automacao.cns-periodico.habilitada", defaultValue = "true")
    boolean cnsPeriodicoHabilitada;

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    @Inject
    FaturamentoCompetenciaRepository competenciaRepository;

    @Inject
    FaturamentoSigtapImportacaoRepository sigtapImportacaoRepository;

    @Inject
    SigtapImportacaoService sigtapImportacaoService;

    @Inject
    FaturamentoCompetenciaService competenciaService;

    @Inject
    FaturamentoProducaoService producaoService;

    @Inject
    FaturamentoAuditService auditService;

    @Inject
    br.com.iv.service.FaturamentoAihService aihService;

    @Scheduled(cron = "{ghbam.faturamento.automacao.cron:0 20 3 * * ?}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void prepararCompetenciasAgendado() {
        // Em cada municipio (sem requisicao o banco seria sempre o padrao).
        br.com.iv.plataforma.municipio.MunicipioRoteamento.emCadaMunicipio(FaturamentoAutomacaoJob.class, "prepararCompetencias", b -> b.prepararCompetencias());
    }

    void prepararCompetencias() {
        if (!habilitada) {
            return;
        }
        if (importarSigtap) {
            // CID x procedimento (V263): carrega sozinho a competencia instalada que ainda
            // nao tem. Ja carregada, nao acessa o FTP. Falha so registra log.
            sigtapImportacaoService.garantirCidsDaCompetenciaInstalada();
        }
        for (String unidade : unidadesComCnes()) {
            // Quem manda e a competencia aberta da unidade, nao a data de hoje: o
            // faturamento acontece dentro do proprio mes da competencia, e enquanto ela
            // nao fechar e nela que se trabalha. Abrir a do mes corrente por cima de uma
            // aberta deixaria duas em andamento e dividiria a producao entre elas.
            String competencia = competenciaEmAndamento(unidade);
            try {
                prepararUnidade(unidade, competencia);
            } catch (RuntimeException e) {
                // Uma unidade com problema nao pode impedir o preparo das outras.
                LOG.errorf(e, "[FATURAMENTO-AUTO] falha ao preparar %s da unidade %s", competencia, unidade);
                auditService.registrar(unidade, "AUTOMACAO_FATURAMENTO", "ERRO", "FaturamentoCompetencia",
                        competencia, null, null, e.getMessage(), "preparo automatico", null);
            }
        }
    }

    private void prepararUnidade(String unidade, String competencia) {
        boolean sigtapInstalado = sigtapImportacaoRepository
                .buscarUltimaPorCompetenciaUnidade(competencia, unidade) != null;
        if (!sigtapInstalado && importarSigtap) {
            sigtapInstalado = importarDoDatasus(unidade, competencia);
        }
        if (!sigtapInstalado) {
            // Sem SIGTAP a competencia nem pode ser aberta (regra do
            // FaturamentoCompetenciaService.abrir) e o cruzamento nao teria com o que
            // comparar. Nada a fazer nesta unidade hoje.
            //
            // Vai para a auditoria, e nao so para o log: adiar em silencio e como o mes
            // inteiro passa sem ninguem faturar. O DATASUS retira as competencias antigas
            // do FTP, entao adiamento que se repete vira producao perdida - quem acompanha
            // o faturamento precisa ver isso na tela, nao no console do servidor.
            LOG.warnf("[FATURAMENTO-AUTO] %s sem SIGTAP %s; preparo adiado", unidade, competencia);
            auditService.registrar(unidade, "AUTOMACAO_FATURAMENTO", "ADIADO", "FaturamentoCompetencia",
                    competencia, null, null, "SIGTAP da competencia nao esta instalado",
                    "importe o SIGTAP " + competencia + " para a unidade " + unidade
                            + "; o DATASUS retira as competencias antigas do FTP", null);
            return;
        }

        FaturamentoCompetencia aberta = competenciaRepository.buscarPorCompetenciaUnidade(competencia, unidade);
        if (aberta == null && !abrirCompetencia) {
            // Nao existe competencia e o job nao abre: e o operador quem declara o mes que
            // vai faturar. A tabela SIGTAP acima ja ficou instalada, entao o que falta e um
            // clique - e a auditoria diz exatamente esse clique, para o adiamento nao
            // parecer "o sistema nao fez nada".
            LOG.infof("[FATURAMENTO-AUTO] %s sem competencia %s aberta; preparo adiado (abertura manual)",
                    unidade, competencia);
            auditService.registrar(unidade, "AUTOMACAO_FATURAMENTO", "ADIADO", "FaturamentoCompetencia",
                    competencia, null, null, "Competencia nao foi aberta pelo operador",
                    "abra a competencia " + competencia + " em Faturamento SUS > Competencias"
                            + " (a tabela SIGTAP dela ja esta instalada)", null);
            return;
        }
        if (aberta == null) {
            aberta = competenciaService.abrir(competencia, null, unidade);
            auditService.registrar(unidade, "AUTOMACAO_ABRIR_COMPETENCIA", "SUCESSO", "FaturamentoCompetencia",
                    String.valueOf(aberta.getIdcompetencia()), null, competencia, null, "abertura automatica", null);
        }

        // Competencia ja fechada nao se mexe: reprocessar mudaria o status de linhas que
        // o operador ja decidiu enviar (ou nao enviar).
        if (!statusProcessavel(aberta.getStatus())) {
            return;
        }

        int idcompetencia = aberta.getIdcompetencia();
        // Vincula a producao do mes, cruza com o SIGTAP e revalida linha a linha.
        var resultado = producaoService.processarCompetencia(idcompetencia, null, unidade);
        // Puxa CNS do cadastro do paciente para o que ficou em critica por CNS.
        var cns = producaoService.sincronizarCnsDoCadastro(idcompetencia, unidade, null);
        // O que o SIGTAP nao classificou sozinho.
        var pendentes = producaoService.classificarPendentesDaCompetencia(idcompetencia, null, unidade, 5000);

        // Herda o CBO do cadastro do servidor. Sem isto, profissional com CBO preenchido no
        // cadastro voltava como pendencia todo mes, esperando alguem clicar num botao.
        var cbo = producaoService.completarCboDosProfissionais(idcompetencia, unidade, null);

        // Procura no CNES do DATASUS o CNS de quem nao tem numero valido no cadastro local.
        // E ida a servidor de terceiro, por isso vem depois de tudo que e local: se o
        // DATASUS estiver fora do ar, o resto do preparo ja aconteceu.
        java.util.Map<String, Object> cnes = java.util.Map.of();
        try {
            cnes = producaoService.buscarCnsNoCnes(idcompetencia, unidade, null);
        } catch (RuntimeException e) {
            LOG.warnf("[FATURAMENTO-AUTO] %s %s: consulta ao CNES indisponivel (%s)", unidade, competencia,
                    e.getMessage());
        }

        // Abre as contas hospitalares dos laudos com internacao na competencia. Criar a
        // conta e mecanico e reversivel; FECHAR continua sendo decisao do operador, e por
        // isso nao entra aqui.
        int contas = 0;
        try {
            var preAihs = aihService.criarPreAihsDaCompetencia(idcompetencia, null, null);
            contas = ((Number) preAihs.getOrDefault("processadas", 0)).intValue();
        } catch (RuntimeException e) {
            LOG.warnf("[FATURAMENTO-AUTO] %s %s: falha ao abrir contas hospitalares (%s)", unidade, competencia,
                    e.getMessage());
        }

        String placar = "processadas=" + resultado.get("processados")
                + " cnsCadastro=" + cns.get("corrigidas")
                + " cnsCnes=" + cnes.getOrDefault("linhasAtualizadas", 0)
                + " classificadas=" + pendentes.get("classificadas")
                + " cboHerdado=" + cbo.get("linhasAtualizadas")
                + " contasAih=" + contas;

        LOG.infof("[FATURAMENTO-AUTO] %s %s: %s", unidade, competencia, placar);
        auditService.registrar(unidade, "AUTOMACAO_PREPARAR_PRODUCAO", "SUCESSO", "FaturamentoCompetencia",
                String.valueOf(idcompetencia), null, competencia, null, placar, null);
    }

    // Reconsulta o CNS de quem ainda esta pendente, sem tocar no resto da preparacao
    // (cruzamento com SIGTAP, contas hospitalares, etc). Existe para rodar com uma
    // frequencia bem maior que o preparo noturno, ja que so depende de um servico externo
    // (CNES/CNS-PDQ) e nao de nada que o operador precise decidir antes.
    @Scheduled(cron = "{ghbam.faturamento.automacao.cns-periodico.cron:0 0 */4 * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void buscarCnsPeriodicoAgendado() {
        // Em cada municipio (sem requisicao o banco seria sempre o padrao).
        br.com.iv.plataforma.municipio.MunicipioRoteamento.emCadaMunicipio(FaturamentoAutomacaoJob.class, "buscarCnsPeriodico", b -> b.buscarCnsPeriodico());
    }

    void buscarCnsPeriodico() {
        if (!habilitada || !cnsPeriodicoHabilitada) {
            return;
        }
        for (String unidade : unidadesComCnes()) {
            String competencia = competenciaEmAndamento(unidade);
            FaturamentoCompetencia aberta = competenciaRepository.buscarPorCompetenciaUnidade(competencia, unidade);
            if (aberta == null || !statusProcessavel(aberta.getStatus())) {
                continue;
            }
            try {
                var cns = producaoService.buscarCnsNoCnes(aberta.getIdcompetencia(), unidade, null);
                LOG.infof("[FATURAMENTO-AUTO-CNS] %s %s: cnsCnes=%s", unidade, competencia,
                        cns.getOrDefault("linhasAtualizadas", 0));
            } catch (RuntimeException e) {
                LOG.warnf("[FATURAMENTO-AUTO-CNS] %s %s: consulta ao CNES/CNS-PDQ indisponivel (%s)", unidade,
                        competencia, e.getMessage());
            }
            // Mesma consulta, agora para o cadastro do profissional (medico/servidor) sem
            // CNS - independe de competencia aberta, mas roda aqui para nao multiplicar
            // jobs por uma consulta tao rara quanto a de paciente.
            try {
                var cnsProfissionais = producaoService.buscarCnsDeProfissionaisNoCnes(unidade, null);
                LOG.infof("[FATURAMENTO-AUTO-CNS-PROFISSIONAL] %s: corrigidos=%s", unidade,
                        cnsProfissionais.getOrDefault("corrigidos", 0));
            } catch (RuntimeException e) {
                LOG.warnf("[FATURAMENTO-AUTO-CNS-PROFISSIONAL] %s: consulta ao CNES/CNS-PDQ indisponivel (%s)",
                        unidade, e.getMessage());
            }
        }
    }

    // Mesmo criterio usado no preparo noturno: competencia fechada nao se reprocessa, para
    // nao mudar o status de linhas que o operador ja decidiu enviar (ou nao enviar).
    private boolean statusProcessavel(String status) {
        return "ABERTA".equals(status) || "EM_PROCESSAMENTO".equals(status) || "EM_AUDITORIA".equals(status);
    }

    // Baixa a competencia do FTP do DATASUS, se ela ja tiver sido publicada. O arquivo
    // costuma sair alguns dias depois da virada do mes, entao a ausencia e normal no
    // comeco - por isso e log informativo, nao erro.
    private boolean importarDoDatasus(String unidade, String competencia) {
        List<SigtapArquivoDatasusDto> disponiveis = sigtapImportacaoService.listarCompetenciasDatasus();
        SigtapArquivoDatasusDto arquivo = disponiveis.stream()
                .filter(a -> competencia.equals(a.getCompetencia()))
                .findFirst()
                .orElse(null);
        if (arquivo == null) {
            LOG.infof("[FATURAMENTO-AUTO] SIGTAP %s ainda nao publicado no DATASUS", competencia);
            return false;
        }
        SigtapImportarDatasusRequestDto pedido = new SigtapImportarDatasusRequestDto();
        pedido.setCompetencia(competencia);
        pedido.setUnidade(unidade);
        pedido.setArquivo(arquivo.getNome());
        pedido.setObservacao("Importacao automatica");
        var resumo = sigtapImportacaoService.importarDatasus(pedido, null, unidade);
        LOG.infof("[FATURAMENTO-AUTO] SIGTAP %s importado para %s: %s", competencia, unidade, resumo);
        return true;
    }

    // Competencia a faturar e sempre o mes anterior: o mes corrente ainda esta
    // recebendo producao.
    private String competenciaAFaturar() {
        // Palpite para quando a unidade nao tem competencia aberta: o mes ANTERIOR, porque
        // se fatura o mes que ja terminou. Ver competenciaEmAndamento.
        return LocalDate.now().minusMonths(1).format(AAAAMM);
    }

    /**
     * A competencia em que a unidade esta trabalhando.
     *
     * E a aberta, se houver - o faturamento acontece dentro do proprio mes da competencia,
     * e enquanto ela nao fecha e nela que se mexe, seja qual for a data de hoje. So quando
     * nao ha nenhuma aberta o job propoe o mes ANTERIOR (ver competenciaAFaturar): e o mes
     * que ja terminou que se fatura. Havendo mais de uma aberta (nao deveria, mas
     * acontece), vale a mais recente, para nao voltar a um mes ja superado.
     *
     * Com abrir-competencia desligado, esse palpite deixa de abrir mes nenhum: serve so
     * para saber de qual competencia baixar o SIGTAP e adiar o resto com a mensagem certa.
     */
    private String competenciaEmAndamento(String unidade) {
        return competenciaRepository.listarPorUnidade(unidade).stream()
                .filter(c -> "ABERTA".equals(c.getStatus()) || "EM_PROCESSAMENTO".equals(c.getStatus())
                        || "EM_AUDITORIA".equals(c.getStatus()))
                .map(FaturamentoCompetencia::getCompetencia)
                .max(String::compareTo)
                .orElseGet(this::competenciaAFaturar);
    }

    // So unidades com CNES configurado entram: sem CNES o motor reprova toda a producao
    // e o preparo automatico so encheria o painel de critica que uma configuracao
    // resolve.
    private Set<String> unidadesComCnes() {
        Set<String> unidades = new LinkedHashSet<>();
        for (ConfiguracaoUnidade configuracao : configuracaoUnidadeRepository.listAll()) {
            String sigla = configuracao.getSigla() == null ? "" : configuracao.getSigla().trim().toUpperCase();
            String cnes = configuracao.getCnesDoHospital() == null ? "" : configuracao.getCnesDoHospital().trim();
            if (!sigla.isBlank() && !cnes.isBlank()) {
                unidades.add(sigla);
            }
        }
        return unidades;
    }
}

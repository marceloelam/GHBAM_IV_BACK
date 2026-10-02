package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.enums.CompetenciaStatus;
import br.com.iv.enums.ProducaoStatus;
import br.com.iv.repository.FaturamentoCompetenciaRepository;
import br.com.iv.repository.FaturamentoProducaoRepository;
import br.com.iv.repository.FaturamentoSigtapImportacaoRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class FaturamentoCompetenciaService {

    @Inject
    FaturamentoCompetenciaRepository competenciaRepository;

    @Inject
    FaturamentoProducaoRepository producaoRepository;

    @Inject
    FaturamentoSigtapImportacaoRepository sigtapImportacaoRepository;

    @Inject
    FaturamentoAuditService auditService;

    public List<FaturamentoCompetencia> listarTodas() {
        return listarTodas(null);
    }

    @Transactional
    public List<FaturamentoCompetencia> listarTodas(String unidade) {
        String unidadeNormalizada = normalizarUnidade(unidade);
        // Unidade em branco nao e mais sinonimo de "todas".
        //
        // Cada unidade cuida so do que e seu, e sessao sem unidade definida nao pode virar
        // acesso ao hospital inteiro por omissao. "TODAS" continua valendo porque e escolha
        // explicita de quem tem permissao para isso (gestao municipal); o branco, nao.
        List<FaturamentoCompetencia> competencias;
        if ("TODAS".equals(unidadeNormalizada)) {
            competencias = competenciaRepository.listarTodas();
        } else if (unidadeNormalizada.isBlank()) {
            competencias = List.of();
        } else {
            competencias = competenciaRepository.listarPorUnidade(unidadeNormalizada);
        }
        for (FaturamentoCompetencia competencia : competencias) {
            String unidadeDaCompetencia = normalizarUnidade(competencia.getUnidade());
            long producaoAtual = producaoRepository.contarPorStatus(competencia.getIdcompetencia(), unidadeDaCompetencia, null);
            long pendentesSemVinculacao = producaoRepository.contarSemCompetenciaPorCompetencia(
                    competencia.getCompetencia(), unidadeDaCompetencia);
            competencia.setQuantidadeProducaoAtual((int) producaoAtual);
            competencia.setPendentesSemVinculacao(pendentesSemVinculacao);
        }
        return competencias;
    }

    @Transactional
    public FaturamentoCompetencia abrir(String competencia, AuthenticatedUser user) {
        return abrir(competencia, user, null);
    }

    @Transactional
    public FaturamentoCompetencia abrir(String competencia, AuthenticatedUser user, String unidadeParametro) {
        String normalizada = competencia == null ? "" : competencia.trim();
        if (!normalizada.matches("\\d{6}")) {
            throw new IllegalArgumentException("Competencia deve estar no formato AAAAMM.");
        }
        String unidade = resolverUnidade(unidadeParametro, user);
        if (unidade.isBlank() || "TODAS".equals(unidade)) {
            throw new IllegalArgumentException("Informe a unidade para abrir a competencia.");
        }
        if (competenciaRepository.buscarPorCompetenciaUnidade(normalizada, unidade) != null) {
            throw new IllegalArgumentException("Competencia " + normalizada + " ja foi aberta anteriormente.");
        }

        var sigtap = sigtapImportacaoRepository.buscarUltimaPorCompetenciaUnidade(normalizada, unidade);
        if (sigtap == null) {
            throw new IllegalArgumentException("Importe a competencia SIGTAP " + normalizada
                    + " para a unidade " + unidade + " antes de abrir o faturamento.");
        }

        FaturamentoCompetencia entidade = new FaturamentoCompetencia();
        entidade.setCompetencia(normalizada);
        entidade.setUnidade(unidade);
        entidade.setStatus(CompetenciaStatus.ABERTA.name());
        entidade.setVersaoSigtapUtilizada(sigtap == null ? null : sigtap.getCompetencia());
        entidade.setDataAbertura(LocalDateTime.now());
        entidade.setUsuarioAbertura(user == null ? "" : user.matricula());
        entidade.persist();

        auditService.registrar(unidade, "ABRIR_COMPETENCIA", "SUCESSO",
                "FaturamentoCompetencia", String.valueOf(entidade.getIdcompetencia()), null,
                CompetenciaStatus.ABERTA.name(), null, "Competencia " + normalizada + " aberta para " + unidade + ".", user);
        return entidade;
    }

    @Transactional
    public FaturamentoCompetencia fechar(int idcompetencia, AuthenticatedUser user) {
        return fechar(idcompetencia, user, null);
    }

    @Transactional
    public FaturamentoCompetencia fechar(int idcompetencia, AuthenticatedUser user, String unidade) {
        FaturamentoCompetencia competencia = obrigatoria(idcompetencia, unidade);
        String statusAnterior = competencia.getStatus();
        if (CompetenciaStatus.FECHADA.name().equals(statusAnterior)
                || CompetenciaStatus.CANCELADA.name().equals(statusAnterior)) {
            throw new IllegalStateException("Competencia " + competencia.getCompetencia()
                    + " ja esta " + statusAnterior + ".");
        }

        long semCompetencia = producaoRepository.contarSemCompetenciaPorCompetencia(competencia.getCompetencia(), unidade);
        if (semCompetencia > 0) {
            throw new IllegalStateException("Fechamento bloqueado: existem " + semCompetencia
                    + " producao(oes) da competencia " + competencia.getCompetencia()
                    + " ainda sem vinculacao. Execute Processar competencia antes de fechar.");
        }

        // Critica nao bloqueia mais o fechamento. Prender o mes inteiro por causa de
        // algumas linhas travadas atrasava o envio da producao que ja estava correta -
        // e o prazo do SIA nao espera a correcao. Fecha-se o que existe; a exportacao
        // leva apenas os APTO_FATURAMENTO (ver
        // FaturamentoProducaoRepository.listarElegiveisParaExportacao), e a linha que
        // for corrigida depois vira apta e sai numa remessa complementar, porque o
        // filtro tambem exige exportado = false.
        //
        // O unico bloqueio que sobra e producao sem vinculacao (acima): essas linhas nao
        // foram sequer avaliadas contra a competencia, entao fechar com elas soltas nao
        // seria fechar com critica, seria fechar sem saber o que existe - e resolve com
        // um clique em Preparar producao.
        long pendentes = producaoRepository.contarPorStatus(idcompetencia, unidade,
                ProducaoStatus.PENDENTE_CLASSIFICACAO.name());
        long revisao = producaoRepository.contarPorStatus(idcompetencia, unidade,
                ProducaoStatus.REVISAO_RECOMENDADA.name());
        long criticas = producaoRepository.contarPorStatus(idcompetencia, unidade,
                ProducaoStatus.PRODUCAO_COM_CRITICA.name());
        long aptos = producaoRepository.contarPorStatus(idcompetencia, unidade,
                ProducaoStatus.APTO_FATURAMENTO.name());
        long retidas = pendentes + revisao + criticas;

        // Snapshot agregado da Fase 1 (contadores/valor estimado no momento do
        // fechamento). Um snapshot completo por registro fica para fase futura,
        // conforme escopo combinado.
        //
        // O valor do snapshot passa a ser o dos aptos, nao o da competencia inteira: com
        // fechamento parcial, somar tudo faria o fechamento anunciar um valor que
        // ninguem vai receber enquanto as criticas nao forem resolvidas.
        long producaoTotal = producaoRepository.contarPorStatus(idcompetencia, unidade, null);
        double valorApto = producaoRepository.somarValorEstimado(idcompetencia, unidade,
                ProducaoStatus.APTO_FATURAMENTO.name());

        competencia.setStatus(CompetenciaStatus.FECHADA.name());
        competencia.setDataFechamento(LocalDateTime.now());
        competencia.setUsuarioFechamento(user == null ? "" : user.matricula());
        competencia.setQuantidadeProducao((int) producaoTotal);
        competencia.setQuantidadeCriticas((int) retidas);
        competencia.setValorEstimado(valorApto);

        auditService.registrar(user == null ? "" : user.unidade(), "FECHAR_COMPETENCIA", "SUCESSO",
                "FaturamentoCompetencia", String.valueOf(idcompetencia), statusAnterior,
                CompetenciaStatus.FECHADA.name(), null,
                "Competencia " + competencia.getCompetencia() + " fechada com " + producaoTotal
                        + " producoes: " + aptos + " apta(s) para exportacao e " + retidas
                        + " retida(s) (" + criticas + " com critica, " + revisao + " em revisao, "
                        + pendentes + " pendente(s) de classificacao).", user);
        return competencia;
    }

    @Transactional
    public void retirar(int idcompetencia, AuthenticatedUser user) {
        retirar(idcompetencia, user, null);
    }

    @Transactional
    public void retirar(int idcompetencia, AuthenticatedUser user, String unidade) {
        FaturamentoCompetencia competencia = obrigatoria(idcompetencia, unidade);
        String statusAnterior = competencia.getStatus();
        if (!CompetenciaStatus.ABERTA.name().equals(statusAnterior)) {
            throw new IllegalStateException("Somente competencias abertas podem ser retiradas (status atual: "
                    + statusAnterior + ").");
        }

        long producaoTotal = producaoRepository.contarPorStatus(idcompetencia, unidade, null);
        if (producaoTotal > 0) {
            throw new IllegalStateException("Retirada bloqueada: existem " + producaoTotal
                    + " producao(oes) vinculada(s) a competencia " + competencia.getCompetencia() + ".");
        }

        auditService.registrar(user == null ? "" : user.unidade(), "RETIRAR_COMPETENCIA", "SUCESSO",
                "FaturamentoCompetencia", String.valueOf(idcompetencia), statusAnterior,
                null, null,
                "Competencia " + competencia.getCompetencia() + " retirada.", user);
        competencia.delete();
    }

    @Transactional
    public FaturamentoCompetencia reabrir(int idcompetencia, String motivo, AuthenticatedUser user) {
        return reabrir(idcompetencia, motivo, user, null);
    }

    @Transactional
    public FaturamentoCompetencia reabrir(int idcompetencia, String motivo, AuthenticatedUser user, String unidade) {
        if (motivo == null || motivo.trim().isBlank()) {
            throw new IllegalArgumentException("Motivo da reabertura e obrigatorio.");
        }
        FaturamentoCompetencia competencia = obrigatoria(idcompetencia, unidade);
        if (!CompetenciaStatus.FECHADA.name().equals(competencia.getStatus())) {
            throw new IllegalStateException("Somente competencias fechadas podem ser reabertas (status atual: "
                    + competencia.getStatus() + ").");
        }
        String statusAnterior = competencia.getStatus();
        competencia.setStatus(CompetenciaStatus.ABERTA.name());
        competencia.setDataReabertura(LocalDateTime.now());
        competencia.setUsuarioReabertura(user == null ? "" : user.matricula());
        competencia.setMotivoReabertura(motivo.trim());

        auditService.registrar(user == null ? "" : user.unidade(), "REABRIR_COMPETENCIA", "SUCESSO",
                "FaturamentoCompetencia", String.valueOf(idcompetencia), statusAnterior,
                CompetenciaStatus.ABERTA.name(), motivo.trim(),
                "Competencia " + competencia.getCompetencia() + " reaberta.", user);
        return competencia;
    }

    private FaturamentoCompetencia obrigatoria(int idcompetencia, String unidade) {
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
        }
        String unidadeSolicitada = normalizarUnidade(unidade);
        if (!unidadeSolicitada.isBlank() && !"TODAS".equals(unidadeSolicitada)
                && !unidadeSolicitada.equals(normalizarUnidade(competencia.getUnidade()))) {
            throw new IllegalArgumentException("Competencia nao pertence a unidade atual.");
        }
        return competencia;
    }

    private String resolverUnidade(String unidadeParametro, AuthenticatedUser user) {
        String unidade = texto(unidadeParametro);
        if (unidade.isBlank() && user != null) {
            unidade = texto(user.unidade());
        }
        return normalizarUnidade(unidade);
    }

    private String normalizarUnidade(String unidade) {
        return texto(unidade).split(":")[0].trim().toUpperCase();
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}

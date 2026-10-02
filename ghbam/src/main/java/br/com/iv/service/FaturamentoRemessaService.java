package br.com.iv.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import br.com.iv.dto.ConteudoExportado;
import br.com.iv.dto.DiagnosticoExportacaoDto;
import br.com.iv.dto.ProntidaoSiaDto;
import br.com.iv.dto.ResumoExportacaoDto;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.entity.FaturamentoApac;
import br.com.iv.entity.FaturamentoRaas;
import br.com.iv.entity.FaturamentoRemessa;
import br.com.iv.enums.CompetenciaStatus;
import br.com.iv.enums.InstrumentoRegistro;
import br.com.iv.enums.RemessaStatus;
import br.com.iv.repository.FaturamentoProducaoRepository;
import br.com.iv.repository.FaturamentoApacRepository;
import br.com.iv.repository.FaturamentoRaasRepository;
import br.com.iv.repository.FaturamentoRemessaRepository;
import br.com.iv.security.AuthenticatedUser;

// Orquestra a geracao de remessa por instrumento (secoes 31-34 do escopo):
// exportacao (gerar arquivo/hash) e transmissao (registro manual do protocolo)
// sao acoes separadas - o GHBAM nunca simula sucesso de transmissao real.
@ApplicationScoped
public class FaturamentoRemessaService {

    private static final Logger LOG = Logger.getLogger(FaturamentoRemessaService.class);

    private static final List<InstrumentoRegistro> INSTRUMENTOS_MONITORADOS =
            List.of(InstrumentoRegistro.BPA_I, InstrumentoRegistro.BPA_C, InstrumentoRegistro.APAC, InstrumentoRegistro.RAAS);

    // Estados em que a competencia ja passou pelo fechamento e continua aceitando
    // remessa. Nao e so FECHADA porque a producao corrigida depois do primeiro envio
    // precisa sair em remessa complementar - e a competencia, a essa altura, ja pode
    // estar marcada como EXPORTADA/TRANSMITIDA/RETORNO_RECEBIDO. O filtro de elegiveis
    // (status apto + exportado = false) garante que a complementar nunca repete o que
    // ja foi enviado.
    private static final List<String> STATUS_LIBERADOS_PARA_REMESSA = List.of(
            CompetenciaStatus.FECHADA.name(),
            CompetenciaStatus.EXPORTADA.name(),
            CompetenciaStatus.TRANSMITIDA.name(),
            CompetenciaStatus.RETORNO_RECEBIDO.name());

    @Inject
    FaturamentoRemessaRepository remessaRepository;

    @Inject
    FaturamentoProducaoRepository producaoRepository;

    @Inject
    FaturamentoApacRepository apacRepository;

    @Inject
    FaturamentoRaasRepository raasRepository;

    @Inject
    ApacExporter apacExporter;

    @Inject
    RaasExporter raasExporter;

    @Inject
    FaturamentoAuditService auditService;

    @Inject
    IntegracaoSusCredencialService integracaoSusCredencialService;

    @Inject
    Instance<FaturamentoExporter> exporters;

    @Inject
    ProcedimentosSusService procedimentosSusService;

    public ResumoExportacaoDto resumo(int idcompetencia, String tipo) {
        return resumo(idcompetencia, tipo, null);
    }

    public ResumoExportacaoDto resumo(int idcompetencia, String tipo, String unidade) {
        InstrumentoRegistro instrumento = instrumentoSuportado(tipo);
        if (instrumento == InstrumentoRegistro.APAC) {
            List<FaturamentoApac> elegiveis = apacRepository.listarElegiveisExportacao(idcompetencia, unidade);
            return new ResumoExportacaoDto(instrumento.name(), idcompetencia, elegiveis.size(),
                    valorTotalApac(elegiveis), apacExporter.layoutDisponivel());
        }
        if (instrumento == InstrumentoRegistro.RAAS) {
            List<FaturamentoRaas> elegiveis = raasRepository.listarElegiveisExportacao(idcompetencia, unidade);
            return new ResumoExportacaoDto(instrumento.name(), idcompetencia, elegiveis.size(),
                    valorTotalRaas(elegiveis), raasExporter.layoutDisponivel());
        }
        List<Afaturar> elegiveis = producaoRepository.listarElegiveisParaExportacao(idcompetencia, instrumento.name(), unidade);
        double valorEstimado = elegiveis.stream().mapToDouble(this::valorProducao).sum();
        return new ResumoExportacaoDto(instrumento.name(), idcompetencia, elegiveis.size(), valorEstimado,
                exporterPara(instrumento).layoutDisponivel());
    }

    public List<DiagnosticoExportacaoDto> diagnostico(int idcompetencia, String unidade) {
        return producaoRepository.diagnosticoExportacao(idcompetencia, unidade).stream()
                .map(linha -> new DiagnosticoExportacaoDto(
                        (String) linha[0],
                        ((Number) linha[1]).longValue(),
                        ((Number) linha[2]).longValue(),
                        ((Number) linha[3]).longValue(),
                        ((Number) linha[4]).longValue(),
                        ((Number) linha[5]).longValue(),
                        ((Number) linha[6]).longValue(),
                        ((Number) linha[7]).doubleValue()))
                .toList();
    }

    public ProntidaoSiaDto prontidao(int idcompetencia, String unidade) {
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
        }
        String unidadeEfetiva = unidade != null && !unidade.isBlank() ? unidade.trim().toUpperCase() : competencia.getUnidade();
        boolean competenciaFechada = competenciaLiberadaParaRemessa(competencia);
        boolean credencialConfigurada = integracaoSusCredencialService.buscar(unidadeEfetiva)
                .map(c -> Boolean.TRUE.equals(c.getAtivo())
                        && ((Boolean.TRUE.equals(c.getPossuiClientSecret())) || Boolean.TRUE.equals(c.getPossuiCertificado()))
                        && c.getEndpointBase() != null && !c.getEndpointBase().isBlank())
                .orElse(false);

        var diagnosticoPorInstrumento = producaoRepository.diagnosticoExportacao(idcompetencia, unidadeEfetiva).stream()
                .collect(java.util.stream.Collectors.toMap(
                        linha -> String.valueOf(linha[0]).toUpperCase(),
                        linha -> linha,
                        (a, b) -> a));

        List<ProntidaoSiaDto.ModuloSiaDto> modulos = INSTRUMENTOS_MONITORADOS.stream()
                .map(instrumento -> instrumento == InstrumentoRegistro.RAAS
                        ? moduloRaasProntidao(idcompetencia, unidadeEfetiva, competenciaFechada, credencialConfigurada)
                        : instrumento == InstrumentoRegistro.APAC
                                ? moduloApacProntidao(idcompetencia, unidadeEfetiva, competenciaFechada, credencialConfigurada)
                        : moduloProntidao(instrumento, diagnosticoPorInstrumento.get(instrumento.name()),
                                competenciaFechada, credencialConfigurada))
                .toList();

        String orientacao = credencialConfigurada
                ? "Credencial SUS configurada para a unidade. O GHBAM registra protocolo e retorno; envio externo direto so deve ser ativado quando houver endpoint oficial homologado para o modulo."
                : "Sem credencial SUS ativa para a unidade. O GHBAM gera os arquivos com layout disponivel e registra a transmissao feita no canal oficial DATASUS.";

        return new ProntidaoSiaDto(idcompetencia, competencia.getCompetencia(), competencia.getStatus(), unidadeEfetiva,
                credencialConfigurada, false, orientacao, modulos);
    }

    @Transactional
    public FaturamentoRemessa gerar(Integer idcompetencia, String tipo, AuthenticatedUser user) {
        return gerar(idcompetencia, tipo, user, null);
    }

    @Transactional
    public FaturamentoRemessa gerar(Integer idcompetencia, String tipo, AuthenticatedUser user, String unidade) {
        if (idcompetencia == null) {
            throw new IllegalArgumentException("Informe a competencia.");
        }
        InstrumentoRegistro instrumento = instrumentoSuportado(tipo);
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
        }
        if (!competenciaLiberadaParaRemessa(competencia)) {
            throw new IllegalStateException(
                    "A competencia precisa estar FECHADA para gerar remessa (status atual: " + competencia.getStatus() + ").");
        }
        if (instrumento == InstrumentoRegistro.APAC) {
            return gerarApac(idcompetencia, competencia, user, unidade);
        }
        if (instrumento == InstrumentoRegistro.RAAS) {
            return gerarRaas(idcompetencia, competencia, user, unidade);
        }

        List<Afaturar> elegiveis = producaoRepository.listarElegiveisParaExportacao(idcompetencia, instrumento.name(), unidade);
        if (elegiveis.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nenhuma producao apta e ainda nao exportada para " + instrumento + " nesta competencia.");
        }

        // Sem layout oficial confirmado, exportar() lanca UnsupportedOperationException
        // aqui (ver BpaIndividualizadoExporter/BpaConsolidadoExporter) - nada abaixo
        // desta chamada roda ate isso ser resolvido, o que e o comportamento esperado.
        ConteudoExportado conteudo = exporterPara(instrumento).exportar(elegiveis, competencia);

        String numero = proximoNumero(idcompetencia, competencia.getCompetencia(), instrumento.name(), unidade);
        String hash = sha256(conteudo.conteudo());
        String caminhoArquivo = salvarArquivo(numero, conteudo.conteudo());

        FaturamentoRemessa remessa = new FaturamentoRemessa();
        remessa.setNumero(numero);
        remessa.setIdcompetencia(idcompetencia);
        remessa.setUnidade(unidade);
        remessa.setTipo(instrumento.name());
        remessa.setLayout(conteudo.layout());
        remessa.setStatus(RemessaStatus.GERADA.name());
        remessa.setQuantidade(conteudo.quantidade());
        remessa.setValorEstimado(elegiveis.stream().mapToDouble(this::valorProducao).sum());
        remessa.setHash(hash);
        remessa.setTamanho(conteudo.conteudo().length);
        remessa.setCaminhoArquivo(caminhoArquivo);
        remessa.setDataGeracao(LocalDateTime.now());
        remessa.setUsuarioGeracao(user == null ? "" : user.matricula());
        remessa.persist();

        for (Afaturar producao : elegiveis) {
            producao.setExportado(true);
            // Guarda em qual remessa a linha saiu: e o que permite cancelar a remessa
            // depois e devolver exatamente esta producao para o proximo arquivo.
            producao.setNumeroRemessa(numero);
        }

        auditService.registrar(user == null ? "" : user.unidade(), "GERAR_REMESSA", "SUCESSO",
                "FaturamentoRemessa", numero, null, RemessaStatus.GERADA.name(), null,
                "instrumento=" + instrumento + " quantidade=" + conteudo.quantidade() + " hash=" + hash, user);
        return remessa;
    }

    private FaturamentoRemessa gerarApac(Integer idcompetencia, FaturamentoCompetencia competencia,
            AuthenticatedUser user, String unidade) {
        List<FaturamentoApac> elegiveis = apacRepository.listarElegiveisExportacao(idcompetencia, unidade);
        if (elegiveis.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma APAC ENCERRADA e ainda nao exportada nesta competencia/unidade.");
        }

        ConteudoExportado conteudo = apacExporter.exportarApac(elegiveis, competencia);
        String numero = proximoNumero(idcompetencia, competencia.getCompetencia(), InstrumentoRegistro.APAC.name(), unidade);
        String hash = sha256(conteudo.conteudo());
        String caminhoArquivo = salvarArquivo(numero, conteudo.conteudo());

        FaturamentoRemessa remessa = new FaturamentoRemessa();
        remessa.setNumero(numero);
        remessa.setIdcompetencia(idcompetencia);
        remessa.setUnidade(unidade);
        remessa.setTipo(InstrumentoRegistro.APAC.name());
        remessa.setLayout(conteudo.layout());
        remessa.setStatus(RemessaStatus.GERADA.name());
        remessa.setQuantidade(conteudo.quantidade());
        remessa.setValorEstimado(valorTotalApac(elegiveis));
        remessa.setHash(hash);
        remessa.setTamanho(conteudo.conteudo().length);
        remessa.setCaminhoArquivo(caminhoArquivo);
        remessa.setDataGeracao(LocalDateTime.now());
        remessa.setUsuarioGeracao(user == null ? "" : user.matricula());
        remessa.persist();

        for (FaturamentoApac apac : elegiveis) {
            apac.setExportado(true);
            apac.setDataExportacao(LocalDateTime.now());
            apac.setNumeroRemessa(numero);
        }

        auditService.registrar(user == null ? "" : user.unidade(), "GERAR_REMESSA_APAC", "SUCESSO",
                "FaturamentoRemessa", numero, null, RemessaStatus.GERADA.name(), null,
                "instrumento=APAC quantidade=" + conteudo.quantidade() + " hash=" + hash, user);
        return remessa;
    }

    private FaturamentoRemessa gerarRaas(Integer idcompetencia, FaturamentoCompetencia competencia,
            AuthenticatedUser user, String unidade) {
        List<FaturamentoRaas> elegiveis = raasRepository.listarElegiveisExportacao(idcompetencia, unidade);
        if (elegiveis.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma RAAS CONCLUIDA e ainda nao exportada nesta competencia/unidade.");
        }

        ConteudoExportado conteudo = raasExporter.exportarRaas(elegiveis, competencia);
        String numero = proximoNumero(idcompetencia, competencia.getCompetencia(), InstrumentoRegistro.RAAS.name(), unidade);
        String hash = sha256(conteudo.conteudo());
        String caminhoArquivo = salvarArquivo(numero, conteudo.conteudo());

        FaturamentoRemessa remessa = new FaturamentoRemessa();
        remessa.setNumero(numero);
        remessa.setIdcompetencia(idcompetencia);
        remessa.setUnidade(unidade);
        remessa.setTipo(InstrumentoRegistro.RAAS.name());
        remessa.setLayout(conteudo.layout());
        remessa.setStatus(RemessaStatus.GERADA.name());
        remessa.setQuantidade(conteudo.quantidade());
        remessa.setValorEstimado(valorTotalRaas(elegiveis));
        remessa.setHash(hash);
        remessa.setTamanho(conteudo.conteudo().length);
        remessa.setCaminhoArquivo(caminhoArquivo);
        remessa.setDataGeracao(LocalDateTime.now());
        remessa.setUsuarioGeracao(user == null ? "" : user.matricula());
        remessa.persist();

        for (FaturamentoRaas raas : elegiveis) {
            raas.setExportado(true);
            raas.setDataExportacao(LocalDateTime.now());
            raas.setNumeroRemessa(numero);
        }

        auditService.registrar(user == null ? "" : user.unidade(), "GERAR_REMESSA_RAAS", "SUCESSO",
                "FaturamentoRemessa", numero, null, RemessaStatus.GERADA.name(), null,
                "instrumento=RAAS quantidade=" + conteudo.quantidade() + " hash=" + hash, user);
        return remessa;
    }

    // Cancela uma remessa e devolve a producao dela para a fila de exportacao.
    //
    // A remessa nao e apagada: fica com status CANCELADA e o motivo na auditoria. Apagar
    // a linha esconderia que um arquivo chegou a ser gerado - e, se ele ja tinha sido
    // transmitido, esconderia tambem que existe um protocolo no DATASUS referente a ele.
    // Por isso o cancelamento de remessa TRANSMITIDA exige motivo: quem transmitiu
    // precisa dizer o que aconteceu do lado de la (rejeitada, reenviada, envio de teste).
    //
    // O arquivo em disco e removido, porque ele deixa de valer como remessa.
    @Transactional
    public FaturamentoRemessa cancelar(int idremessa, String motivo, AuthenticatedUser user, String unidade) {
        FaturamentoRemessa remessa = obrigatoria(idremessa, unidade);
        String statusAnterior = remessa.getStatus();
        if (RemessaStatus.CANCELADA.name().equals(statusAnterior)) {
            throw new IllegalStateException("Remessa " + remessa.getNumero() + " ja esta cancelada.");
        }
        String justificativa = motivo == null ? "" : motivo.trim();
        if (RemessaStatus.TRANSMITIDA.name().equals(statusAnterior) && justificativa.isBlank()) {
            throw new IllegalArgumentException("Esta remessa consta como TRANSMITIDA. Informe o motivo do cancelamento"
                    + " (ex.: rejeitada no DATASUS, envio de teste, substituida por reenvio).");
        }

        long liberadas = liberarProducaoDaRemessa(remessa);

        remessa.setStatus(RemessaStatus.CANCELADA.name());
        remessa.setObservacaoTransmissao(justificativa.isBlank()
                ? remessa.getObservacaoTransmissao()
                : justificativa);
        apagarArquivo(remessa);

        auditService.registrar(user == null ? "" : user.unidade(), "CANCELAR_REMESSA", "SUCESSO",
                "FaturamentoRemessa", remessa.getNumero(), statusAnterior, RemessaStatus.CANCELADA.name(),
                justificativa, liberadas + " producao(oes) devolvida(s) para exportacao.", user);
        return remessa;
    }

    // Producao de APAC e RAAS ja nascia com numeroRemessa; para BPA isso so existe a
    // partir da V119. Quando a linha nao tem o numero (remessa antiga), liberar por
    // competencia/unidade/instrumento seria devolver junto o que saiu em outra remessa -
    // entao isso so acontece se esta for a unica remessa viva daquele recorte.
    private long liberarProducaoDaRemessa(FaturamentoRemessa remessa) {
        if (InstrumentoRegistro.APAC.name().equals(remessa.getTipo())) {
            return apacRepository.reverterExportacaoPorRemessa(remessa.getNumero());
        }
        if (InstrumentoRegistro.RAAS.name().equals(remessa.getTipo())) {
            return raasRepository.reverterExportacaoPorRemessa(remessa.getNumero());
        }

        long liberadas = producaoRepository.reverterExportacaoPorRemessa(remessa.getNumero());
        if (liberadas > 0) {
            return liberadas;
        }

        long outrasVivas = remessaRepository.listar(remessa.getIdcompetencia(), remessa.getUnidade()).stream()
                .filter(outra -> outra.getIdremessa() != remessa.getIdremessa())
                .filter(outra -> remessa.getTipo() != null && remessa.getTipo().equals(outra.getTipo()))
                .filter(outra -> !RemessaStatus.CANCELADA.name().equals(outra.getStatus()))
                .count();
        if (outrasVivas > 0) {
            throw new IllegalStateException("Remessa " + remessa.getNumero() + " foi gerada antes do GHBAM gravar o"
                    + " numero da remessa em cada linha, e existe outra remessa " + remessa.getTipo()
                    + " ativa nesta competencia. Cancele a mais recente primeiro para nao devolver producao trocada.");
        }
        return producaoRepository.reverterExportacaoPorInstrumento(remessa.getIdcompetencia(), remessa.getTipo(),
                remessa.getUnidade());
    }

    private void apagarArquivo(FaturamentoRemessa remessa) {
        String caminho = remessa.getCaminhoArquivo();
        if (caminho == null || caminho.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(caminho));
            remessa.setCaminhoArquivo(null);
        } catch (IOException e) {
            // Arquivo preso pelo sistema de arquivos nao invalida o cancelamento: o que
            // decide o faturamento e o status da remessa e a producao liberada. Mantemos
            // o caminho gravado justamente para nao perder o rastro do .txt que sobrou.
            LOG.warn("Remessa " + remessa.getNumero() + " cancelada, mas o arquivo " + caminho
                    + " nao pode ser removido: " + e.getMessage());
        }
    }

    @Transactional
    public FaturamentoRemessa registrarTransmissao(int idremessa, String protocolo, String observacao, AuthenticatedUser user) {
        return registrarTransmissao(idremessa, protocolo, observacao, user, null);
    }

    @Transactional
    public FaturamentoRemessa registrarTransmissao(int idremessa, String protocolo, String observacao, AuthenticatedUser user, String unidade) {
        FaturamentoRemessa remessa = obrigatoria(idremessa, unidade);
        String statusAnterior = remessa.getStatus();
        remessa.setStatus(RemessaStatus.TRANSMITIDA.name());
        remessa.setDataTransmissao(LocalDateTime.now());
        remessa.setUsuarioTransmissao(user == null ? "" : user.matricula());
        remessa.setProtocoloTransmissao(protocolo);
        remessa.setObservacaoTransmissao(observacao);

        auditService.registrar(user == null ? "" : user.unidade(), "REGISTRAR_TRANSMISSAO_REMESSA", "SUCESSO",
                "FaturamentoRemessa", remessa.getNumero(), statusAnterior, RemessaStatus.TRANSMITIDA.name(),
                observacao, "protocolo=" + protocolo, user);
        return remessa;
    }

    public List<FaturamentoRemessa> listar(Integer idcompetencia) {
        return remessaRepository.listar(idcompetencia);
    }

    public List<FaturamentoRemessa> listar(Integer idcompetencia, String unidade) {
        return remessaRepository.listar(idcompetencia, unidade);
    }

    public byte[] arquivo(int idremessa) {
        return arquivo(idremessa, null);
    }

    public byte[] arquivo(int idremessa, String unidade) {
        FaturamentoRemessa remessa = obrigatoria(idremessa, unidade);
        if (remessa.getCaminhoArquivo() == null || remessa.getCaminhoArquivo().isBlank()) {
            throw new IllegalStateException("Arquivo da remessa " + remessa.getNumero() + " nao esta disponivel.");
        }
        try {
            return Files.readAllBytes(Path.of(remessa.getCaminhoArquivo()));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler arquivo da remessa " + remessa.getNumero(), e);
        }
    }

    private boolean competenciaLiberadaParaRemessa(FaturamentoCompetencia competencia) {
        return competencia != null && STATUS_LIBERADOS_PARA_REMESSA.contains(
                String.valueOf(competencia.getStatus()).trim().toUpperCase());
    }

    private FaturamentoRemessa obrigatoria(int idremessa) {
        return obrigatoria(idremessa, null);
    }

    private FaturamentoRemessa obrigatoria(int idremessa, String unidade) {
        FaturamentoRemessa remessa = FaturamentoRemessa.findById(idremessa);
        if (remessa == null) {
            throw new IllegalArgumentException("Remessa nao encontrada para o id: " + idremessa);
        }
        if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)
                && (remessa.getUnidade() == null || !remessa.getUnidade().equalsIgnoreCase(unidade.trim()))) {
            throw new IllegalArgumentException("Remessa nao encontrada para a unidade informada.");
        }
        return remessa;
    }

    // O numero passou a carregar o instrumento. Sem ele, BPA-C recomecava em 00001 e
    // colidia com o numero ja gravado do BPA-I da mesma competencia/unidade - e o numero
    // e UNIQUE global, entao a segunda remessa do mes quebrava no insert.
    //
    // O sequencial vem do maior numero ja usado com este prefixo, nao de uma contagem de
    // linhas: contagem depende de a coluna unidade estar gravada exatamente como o filtro
    // espera, e remessa antiga com unidade nula fazia a numeracao reiniciar.
    private String proximoNumero(int idcompetencia, String competencia, String tipo, String unidade) {
        String sufixoUnidade = unidade == null || unidade.isBlank() || "TODAS".equalsIgnoreCase(unidade)
                ? "GERAL"
                : unidade.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        String sufixoTipo = String.valueOf(tipo).trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        String prefixo = String.format("GHBAM-%s-%s-%s-", competencia, sufixoUnidade, sufixoTipo);
        return prefixo + String.format("%05d", remessaRepository.maiorSequencialPorPrefixo(prefixo) + 1);
    }

    private String salvarArquivo(String numero, byte[] conteudo) {
        try {
            Path diretorio = Path.of("remessas");
            Files.createDirectories(diretorio);
            Path arquivo = diretorio.resolve(numero + ".txt");
            Files.write(arquivo, conteudo);
            return arquivo.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao salvar arquivo da remessa " + numero, e);
        }
    }

    private String sha256(byte[] dados) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(dados);
            StringBuilder hexadecimal = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hexadecimal.append(String.format("%02x", b));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM.", e);
        }
    }

    private double valorProducao(Afaturar producao) {
        return producao.getPa_sa() + producao.getPa_sp() + producao.getPa_sh();
    }

    // APAC e RAAS nao carregam os valores do SIGTAP como Afaturar (pa_sa/pa_sp/pa_sh):
    // vivem em tabelas proprias que so guardam o codigo do procedimento. O valor sai do
    // SIGTAP local ja com o override por unidade aplicado (s_proced_unidade_valor).
    // O cache evita repetir a consulta para o mesmo procedimento dentro da mesma remessa.
    private double valorTotalApac(List<FaturamentoApac> registros) {
        Map<String, Double> cache = new HashMap<>();
        return registros.stream()
                .mapToDouble(apac -> valorProcedimento(apac.getProcedimentoprincipal(), apac.getUnidade(), cache)
                        * (apac.getQuantidade() <= 0 ? 1 : apac.getQuantidade()))
                .sum();
    }

    // A RAAS nao tem quantidade - e registro de atendimento, nao producao quantitativa.
    private double valorTotalRaas(List<FaturamentoRaas> registros) {
        Map<String, Double> cache = new HashMap<>();
        return registros.stream()
                .mapToDouble(raas -> valorProcedimento(raas.getProcedimento(), raas.getUnidade(), cache))
                .sum();
    }

    private double valorProcedimento(String codigo, String unidade, Map<String, Double> cache) {
        String procedimento = codigo == null ? "" : codigo.replaceAll("\\D", "");
        if (procedimento.isBlank()) {
            return 0;
        }
        return cache.computeIfAbsent(procedimento + "|" + (unidade == null ? "" : unidade), chave -> {
            var valor = procedimentosSusService.buscarValorEfetivo(procedimento, unidade);
            return valor == null ? 0 : valor.getPa_sa() + valor.getPa_sp() + valor.getPa_sh();
        });
    }

    private FaturamentoExporter exporterPara(InstrumentoRegistro instrumento) {
        for (FaturamentoExporter exporter : exporters) {
            if (exporter.instrumento() == instrumento) {
                return exporter;
            }
        }
        throw new IllegalArgumentException("Nenhum exportador disponivel para o instrumento " + instrumento + ".");
    }

    private InstrumentoRegistro instrumentoSuportado(String tipo) {
        InstrumentoRegistro instrumento;
        try {
            instrumento = InstrumentoRegistro.valueOf(String.valueOf(tipo).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de instrumento invalido: " + tipo);
        }
        if (!INSTRUMENTOS_MONITORADOS.contains(instrumento)) {
            throw new IllegalArgumentException(
                    "Instrumento de faturamento nao monitorado para exportacao SIA: " + instrumento + ".");
        }
        return instrumento;
    }

    private ProntidaoSiaDto.ModuloSiaDto moduloProntidao(InstrumentoRegistro instrumento, Object[] linha,
            boolean competenciaFechada, boolean credencialConfigurada) {
        long total = numero(linha, 1);
        long aptos = numero(linha, 2);
        long criticas = numero(linha, 4);
        long pendentes = numero(linha, 5);
        long naoFaturavel = numero(linha, 6);
        double valorApto = decimal(linha, 7);
        boolean layoutDisponivel = exporterPara(instrumento).layoutDisponivel();
        boolean podeGerar = competenciaFechada && layoutDisponivel && aptos > 0;
        String situacao;
        String orientacao;
        if (!layoutDisponivel) {
            situacao = "LAYOUT_EM_IMPLEMENTACAO";
            orientacao = "Formulario e acompanhamento existem no GHBAM, mas o arquivo posicional oficial ainda nao esta implementado campo a campo.";
        } else if (!competenciaFechada) {
            situacao = "AGUARDANDO_FECHAMENTO";
            orientacao = "Feche a competencia antes de gerar remessa oficial.";
        } else if (aptos > 0 && (criticas > 0 || pendentes > 0)) {
            // Ter critica nao segura mais a remessa: o arquivo sai com os aptos e o que
            // ficou travado volta numa remessa complementar depois de corrigido. A
            // situacao propria existe para a tela nao repetir o texto de bloqueio num
            // botao que esta habilitado.
            situacao = "PRONTO_PARCIAL";
            orientacao = "Pode gerar agora com " + aptos + " producao(oes) apta(s). As "
                    + (criticas + pendentes) + " travada(s) nao entram neste arquivo: corrija em"
                    + " Faturamento SUS > Producao e gere uma remessa complementar.";
        } else if (aptos > 0) {
            situacao = "PRONTO_GERAR";
            orientacao = credencialConfigurada
                    ? "Pode gerar arquivo no GHBAM e registrar protocolo; envio externo depende do canal oficial homologado."
                    : "Pode gerar arquivo no GHBAM; transmita no canal oficial DATASUS e registre o protocolo.";
        } else if (criticas > 0 || pendentes > 0) {
            situacao = "COM_PENDENCIAS";
            orientacao = "Nenhuma producao apta nao exportada. As " + (criticas + pendentes)
                    + " travada(s) precisam de correcao para entrar numa remessa complementar.";
        } else {
            situacao = "SEM_PRODUCAO_APTA";
            orientacao = "Nao ha producao apta nao exportada para este instrumento.";
        }
        return new ProntidaoSiaDto.ModuloSiaDto(instrumento.name(), nomeInstrumento(instrumento),
                operacaoGhbam(instrumento), situacao, total, aptos, criticas, pendentes, naoFaturavel,
                valorApto, layoutDisponivel, podeGerar, orientacao);
    }

    private ProntidaoSiaDto.ModuloSiaDto moduloRaasProntidao(int idcompetencia, String unidade,
            boolean competenciaFechada, boolean credencialConfigurada) {
        long total = raasRepository.contarPorCompetenciaUnidade(idcompetencia, unidade);
        List<FaturamentoRaas> elegiveis = raasRepository.listarElegiveisExportacao(idcompetencia, unidade);
        long aptos = elegiveis.size();
        long concluidas = raasRepository.contarStatusPorCompetenciaUnidade(idcompetencia, unidade, "CONCLUIDA");
        long exportadas = raasRepository.contarExportadosPorCompetenciaUnidade(idcompetencia, unidade);
        long pendentes = Math.max(0, total - concluidas);
        boolean layoutDisponivel = raasExporter.layoutDisponivel();
        boolean podeGerar = competenciaFechada && layoutDisponivel && aptos > 0;
        String situacao;
        String orientacao;
        if (!competenciaFechada) {
            situacao = "AGUARDANDO_FECHAMENTO";
            orientacao = "Feche a competencia antes de gerar remessa RAAS.";
        } else if (aptos > 0 && pendentes > 0) {
            situacao = "PRONTO_PARCIAL";
            orientacao = "Pode gerar agora com " + aptos + " RAAS concluida(s). As " + pendentes
                    + " ainda abertas nao entram neste arquivo: conclua e gere uma remessa complementar.";
        } else if (pendentes > 0) {
            situacao = "COM_PENDENCIAS";
            orientacao = "Nenhuma RAAS concluida nao exportada. Conclua as " + pendentes
                    + " RAAS abertas para gerar uma remessa complementar.";
        } else if (aptos > 0) {
            situacao = "PRONTO_GERAR";
            orientacao = credencialConfigurada
                    ? "Pode gerar arquivo RAAS no GHBAM e registrar protocolo; envio externo depende do canal oficial homologado."
                    : "Pode gerar arquivo RAAS no GHBAM; transmita no canal oficial DATASUS e registre o protocolo.";
        } else {
            situacao = exportadas > 0 ? "JA_EXPORTADO" : "SEM_PRODUCAO_APTA";
            orientacao = exportadas > 0
                    ? "RAAS desta competencia/unidade ja foi exportada."
                    : "Nao ha RAAS concluida e nao exportada para esta competencia/unidade.";
        }
        return new ProntidaoSiaDto.ModuloSiaDto(InstrumentoRegistro.RAAS.name(), nomeInstrumento(InstrumentoRegistro.RAAS),
                operacaoGhbam(InstrumentoRegistro.RAAS), situacao, total, aptos, 0, pendentes, 0,
                valorTotalRaas(elegiveis), layoutDisponivel, podeGerar, orientacao);
    }

    private ProntidaoSiaDto.ModuloSiaDto moduloApacProntidao(int idcompetencia, String unidade,
            boolean competenciaFechada, boolean credencialConfigurada) {
        long total = apacRepository.contarPorCompetenciaUnidade(idcompetencia, unidade);
        List<FaturamentoApac> elegiveis = apacRepository.listarElegiveisExportacao(idcompetencia, unidade);
        long aptos = elegiveis.size();
        long encerradas = apacRepository.contarStatusPorCompetenciaUnidade(idcompetencia, unidade, "ENCERRADA");
        long exportadas = apacRepository.contarExportadosPorCompetenciaUnidade(idcompetencia, unidade);
        long pendentes = Math.max(0, total - encerradas);
        boolean layoutDisponivel = apacExporter.layoutDisponivel();
        boolean podeGerar = competenciaFechada && layoutDisponivel && aptos > 0;
        String situacao;
        String orientacao;
        if (!competenciaFechada) {
            situacao = "AGUARDANDO_FECHAMENTO";
            orientacao = "Feche a competencia antes de gerar remessa APAC.";
        } else if (aptos > 0 && pendentes > 0) {
            situacao = "PRONTO_PARCIAL";
            orientacao = "Pode gerar agora com " + aptos + " APAC encerrada(s). As " + pendentes
                    + " ainda abertas nao entram neste arquivo: encerre e gere uma remessa complementar.";
        } else if (pendentes > 0) {
            situacao = "COM_PENDENCIAS";
            orientacao = "Nenhuma APAC encerrada nao exportada. Encerre as " + pendentes
                    + " APAC autorizadas para gerar uma remessa complementar.";
        } else if (aptos > 0) {
            situacao = "PRONTO_GERAR";
            orientacao = credencialConfigurada
                    ? "Pode gerar arquivo APAC no GHBAM e registrar protocolo; envio externo depende do canal oficial homologado."
                    : "Pode gerar arquivo APAC no GHBAM; transmita no canal oficial DATASUS e registre o protocolo.";
        } else {
            situacao = exportadas > 0 ? "JA_EXPORTADO" : "SEM_PRODUCAO_APTA";
            orientacao = exportadas > 0
                    ? "APAC desta competencia/unidade ja foi exportada."
                    : "Nao ha APAC encerrada e nao exportada para esta competencia/unidade.";
        }
        return new ProntidaoSiaDto.ModuloSiaDto(InstrumentoRegistro.APAC.name(), nomeInstrumento(InstrumentoRegistro.APAC),
                operacaoGhbam(InstrumentoRegistro.APAC), situacao, total, aptos, 0, pendentes, 0,
                valorTotalApac(elegiveis), layoutDisponivel, podeGerar, orientacao);
    }

    private String nomeInstrumento(InstrumentoRegistro instrumento) {
        return switch (instrumento) {
            case BPA_I -> "BPA Individualizado";
            case BPA_C -> "BPA Consolidado";
            case APAC -> "APAC Magnetico";
            case RAAS -> "RAAS";
            default -> instrumento.name();
        };
    }

    private String operacaoGhbam(InstrumentoRegistro instrumento) {
        return switch (instrumento) {
            case BPA_I, BPA_C -> "Gerar remessa oficial SIA/BPA, baixar arquivo, registrar protocolo e importar retorno.";
            case APAC -> "Solicitar, autorizar, imprimir laudo, acompanhar APAC e preparar dados para arquivo SIA.";
            case RAAS -> "Registrar RAAS-PSI/AD, validar CID/profissional/acoes e preparar dados para arquivo SIA.";
            default -> "Acompanhar faturamento SUS.";
        };
    }

    private long numero(Object[] linha, int indice) {
        if (linha == null || linha.length <= indice || !(linha[indice] instanceof Number n)) {
            return 0;
        }
        return n.longValue();
    }

    private double decimal(Object[] linha, int indice) {
        if (linha == null || linha.length <= indice || !(linha[indice] instanceof Number n)) {
            return 0;
        }
        return n.doubleValue();
    }
}

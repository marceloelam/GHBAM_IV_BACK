package br.com.iv.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.narayana.jta.QuarkusTransaction;

import br.com.iv.dto.AihLaudoElegivelDto;
import br.com.iv.dto.ConteudoExportado;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.entity.FaturamentoAih;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.entity.Internacao;
import br.com.iv.entity.NovoInternado;
import br.com.iv.enums.AihStatus;
import br.com.iv.enums.ProducaoStatus;
import br.com.iv.enums.ResultadoValidacao;
import br.com.iv.dto.ValidacaoResultado;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import br.com.iv.repository.FaturamentoAihRepository;
import br.com.iv.repository.FaturamentoCompetenciaRepository;
import br.com.iv.repository.FaturamentoProducaoRepository;
import br.com.iv.repository.LaudoAutorizacaoRepository;
import br.com.iv.security.AuthenticatedUser;

// Pre-AIH nasce vinculada a internacao ja existente (secao 23 do escopo) - nao
// duplica dados clinicos da internacao, so referencia idinternacao. A "conta
// hospitalar" e montada dinamicamente a partir das producoes de Afaturar. Quando
// a origem da internacao ainda nao foi gravada na producao, o repositorio usa
// tambem competencia + paciente/BAM/unidade para localizar os itens da conta.
@ApplicationScoped
public class FaturamentoAihService {

    @Inject
    FaturamentoAihRepository aihRepository;

    @Inject
    FaturamentoProducaoRepository producaoRepository;

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    @Inject
    LaudoAutorizacaoRepository laudoAutorizacaoRepository;

    @Inject
    FaturamentoCompetenciaRepository competenciaRepository;

    @Inject
    FaturamentoAihFaixaService faixaService;

    @Inject
    FaturamentoAuditService auditService;

    @Inject
    FaturamentoValidationService validationService;

    @Inject
    FaturamentoProducaoService producaoService;

    @Inject
    AihExporter aihExporter;

    public List<FaturamentoAih> listar(Integer idcompetencia) {
        return listar(idcompetencia, (String) null);
    }

    public List<FaturamentoAih> listar(Integer idcompetencia, AuthenticatedUser user) {
        return listar(idcompetencia, unidadeRestrita(user));
    }

    public List<FaturamentoAih> listar(Integer idcompetencia, String unidade) {
        List<FaturamentoAih> aihs = aihRepository.listar(idcompetencia, unidade);
        promoverContasComAlta(aihs);
        aihs.forEach(this::preencherPendenciasClassificacao);
        return aihs;
    }

    /** Gera o arquivo de importacao SISAIH01 sem transmitir dados ao DATASUS. */
    @Transactional
    public ConteudoExportado exportarArquivo(Integer idcompetencia, AuthenticatedUser user) {
        if (idcompetencia == null) {
            throw new IllegalArgumentException("Informe a competencia da AIH.");
        }
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
        }
        String unidade = unidadeRestrita(user);
        if (unidade != null && !unidade.isBlank() && !unidade.equalsIgnoreCase(competencia.getUnidade())) {
            throw new IllegalArgumentException("Competencia nao pertence a unidade atual.");
        }
        List<Afaturar> producoes = listar(idcompetencia, unidade).stream()
                .flatMap(conta -> producaoRepository.listarParaContaAih(conta).stream())
                .toList();
        return aihExporter.exportar(producoes, competencia);
    }

    @Transactional
    public Map<String, Object> numerarFechadasSemNumero(Integer idcompetencia, String unidade, AuthenticatedUser user) {
        if (idcompetencia == null) {
            throw new IllegalArgumentException("Informe a competencia da AIH.");
        }
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
        }
        String restrita = unidadeRestrita(user);
        String unidadeAlvo = restrita;
        if (unidadeAlvo == null && !texto(unidade).isBlank() && !"TODAS".equalsIgnoreCase(texto(unidade))) {
            unidadeAlvo = unidade;
        }
        if (unidadeAlvo == null || texto(unidadeAlvo).isBlank() || "TODAS".equalsIgnoreCase(texto(unidadeAlvo))) {
            throw new IllegalArgumentException("Informe a unidade para numerar AIH.");
        }
        if (!texto(competencia.getUnidade()).isBlank()
                && !"TODAS".equalsIgnoreCase(texto(competencia.getUnidade()))
                && !texto(competencia.getUnidade()).equalsIgnoreCase(texto(unidadeAlvo))) {
            throw new IllegalArgumentException("Competencia nao pertence a unidade informada.");
        }

        List<FaturamentoAih> contas = FaturamentoAih.list(
                "idcompetencia = ?1 and upper(unidade) = ?2 and status = ?3 and (numeroAih is null or trim(numeroAih) = '') order by numero",
                idcompetencia, texto(unidadeAlvo).toUpperCase(), AihStatus.FECHADA.name());
        int numeradas = 0;
        for (FaturamentoAih aih : contas) {
            String numeroOficial = faixaService.reservarNumero(aih.getUnidade(), competencia.getCompetencia(), user);
            if (numeroOficial == null) {
                break;
            }
            aih.setNumeroAih(numeroOficial);
            aih.setAtualizadoEm(LocalDateTime.now());
            numeradas++;
            auditService.registrar(aih.getUnidade(), "ATRIBUIR_NUMERO_AIH", "SUCESSO", "FaturamentoAih",
                    aih.getNumero(), null, numeroOficial, null,
                    "numero oficial atribuido em lote a conta ja fechada", user);
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("contasSemNumero", contas.size());
        resultado.put("numeradas", numeradas);
        resultado.put("pendentesSemFaixa", Math.max(0, contas.size() - numeradas));
        return resultado;
    }

    // EM_INTERNACAO so era gravado no nascimento da pre-AIH (criarPreAih), a partir de
    // internacao.datasaida == null. Nada reavaliava depois: quando o paciente recebia
    // alta, a conta continuava EM_INTERNACAO para sempre e ficava fora de qualquer lote
    // de fechamento - o operador via a conta inteira apta, com valor calculado, e mesmo
    // assim ela nao fechava. Aqui a listagem reconcilia o status com a alta que ja
    // aconteceu no censo.
    //
    // Promove so nesse sentido (internado -> pendente de faturamento). O caminho inverso
    // nao existe de proposito: conta fechada ou cancelada e decisao registrada, e nao
    // deve voltar atras por causa de mudanca no censo.
    private void promoverContasComAlta(List<FaturamentoAih> aihs) {
        List<FaturamentoAih> candidatas = aihs.stream()
                .filter(aih -> AihStatus.EM_INTERNACAO.name().equals(aih.getStatus()))
                .toList();
        if (candidatas.isEmpty()) {
            return;
        }
        // Transacao propria: listar e leitura, e o chamador pode nao ter transacao ativa.
        QuarkusTransaction.requiringNew().run(() -> candidatas.forEach(aih -> {
            LocalDateTime alta = dataDaAlta(aih);
            if (alta == null) {
                return;
            }
            FaturamentoAih gerenciada = FaturamentoAih.findById(aih.getIdaih());
            if (gerenciada == null || !AihStatus.EM_INTERNACAO.name().equals(gerenciada.getStatus())) {
                return;
            }
            gerenciada.setStatus(AihStatus.PENDENTE_FATURAMENTO.name());
            gerenciada.setDataAlta(alta);
            gerenciada.setAtualizadoEm(LocalDateTime.now());
            // Reflete no objeto que ja vai para a resposta desta requisicao.
            aih.setStatus(gerenciada.getStatus());
            aih.setDataAlta(gerenciada.getDataAlta());
        }));
    }

    // A alta tem duas fontes no GHBAM, e so a segunda e usada na pratica:
    //
    //   1. Internacao.datasaida - preenchida apenas pelo cadastro de internacao
    //      (InternacaoService), caminho pouco usado no dia a dia;
    //   2. NovoInternado.alta - o censo de leitos, que e por onde a alta realmente
    //      acontece (a tela de internados grava alta/quemdeualta no BAM).
    //
    // criarPreAih olhava so a primeira. Como ela fica nula, toda pre-AIH nascia
    // EM_INTERNACAO e nunca saia desse status nem depois da alta - por isso contas com a
    // producao inteira apta ficavam de fora do fechamento em lote. Conferir as duas
    // fontes e o que destrava essas contas.
    // Visibilidade de pacote (nao private) so para o teste: mesma convencao ja usada em
    // FaturamentoValidationService.sexoIncompativel/idadeForaDaFaixa e em
    // FaturamentoProducaoService.admiteRegistro - decisao isolada, testada sem banco.
    LocalDateTime dataDaAlta(FaturamentoAih aih) {
        if (aih.getIdInternacao() > 0) {
            Internacao internacao = buscarInternacao(aih.getIdInternacao());
            if (internacao != null && internacao.getDatasaida() != null) {
                return internacao.getDatasaida().atStartOfDay(ZoneId.systemDefault()).toLocalDateTime();
            }
        }

        int bam = somenteDigitos(aih.getBam());
        if (bam <= 0) {
            return null;
        }
        NovoInternado internado = buscarInternadoComAlta(bam);
        if (internado == null) {
            return null;
        }
        // Censo antigo pode nao ter data da acao; a alta existe do mesmo jeito, entao o
        // fallback e "agora" - o que importa aqui e destravar o status, e a data oficial
        // da conta vem da producao.
        return internado.getDatadaacao() == null
                ? LocalDateTime.now()
                : internado.getDatadaacao().atStartOfDay(ZoneId.systemDefault()).toLocalDateTime();
    }

    Internacao buscarInternacao(int idinternacao) {
        return Internacao.findById(idinternacao);
    }

    NovoInternado buscarInternadoComAlta(int bam) {
        return NovoInternado
                .find("nbam = ?1 and alta = true order by datadaacao desc", bam)
                .firstResult();
    }

    private int somenteDigitos(String valor) {
        String digitos = valor == null ? "" : valor.replaceAll("\\D", "");
        if (digitos.isEmpty() || digitos.length() > 9) {
            return 0;
        }
        return Integer.parseInt(digitos);
    }

    public List<AihLaudoElegivelDto> listarLaudosElegiveis(Integer idcompetencia, String busca, AuthenticatedUser user) {
        String competencia = null;
        if (idcompetencia != null) {
            FaturamentoCompetencia comp = FaturamentoCompetencia.findById(idcompetencia);
            if (comp != null) {
                competencia = comp.getCompetencia();
            }
        }
        return laudoAutorizacaoRepository.listarElegiveisParaAih(competencia, unidadeRestrita(user), busca);
    }

    @Transactional
    public FaturamentoAih criarPreAihPorLaudo(int idlaudo, AuthenticatedUser user) {
        Integer idinternacao = laudoAutorizacaoRepository.buscarIdInternacaoPorLaudo(idlaudo, unidadeRestrita(user));
        if (idinternacao == null) {
            throw new IllegalArgumentException("Internacao nao encontrada para o laudo de autorizacao informado.");
        }
        return criarPreAih(idinternacao, user);
    }

    @Transactional
    public FaturamentoAih criarPreAih(int idinternacao, AuthenticatedUser user) {
        FaturamentoAih existente = aihRepository.buscarPorInternacao(idinternacao);
        if (existente != null) {
            if (existente.getIdcompetencia() == null) {
                existente.setIdcompetencia(competenciaDaInternacao(existente.getDataInternacao(), existente.getUnidade()));
            }
            return existente;
        }
        Internacao internacao = Internacao.findById(idinternacao);
        if (internacao == null) {
            throw new IllegalArgumentException("Internacao nao encontrada para o id: " + idinternacao);
        }

        String cnes = "";
        if (internacao.getUnidade() != null && !internacao.getUnidade().isBlank()) {
            ConfiguracaoUnidade configuracao = configuracaoUnidadeRepository
                    .filtarPorUnidadeSigla(internacao.getUnidade())
                    .stream()
                    .filter(c -> internacao.getUnidade().equalsIgnoreCase(texto(c.getSigla())))
                    .findFirst()
                    .orElse(null);
            cnes = configuracao == null ? "" : texto(configuracao.getCnesDoHospital());
        }

        long sequencial = aihRepository.count() + 1;
        String competenciaChave = internacao.getDatainternacao() == null
                ? "000000"
                : String.format("%04d%02d", internacao.getDatainternacao().getYear(), internacao.getDatainternacao().getMonthValue());

        FaturamentoAih aih = new FaturamentoAih();
        aih.setNumero(String.format("AIH-%s-%05d", competenciaChave, sequencial));
        aih.setIdInternacao(idinternacao);
        aih.setIdpaciente(internacao.getIdDoPaciente());
        aih.setProntuario(limitar(internacao.getProntuario(), 20));
        aih.setBam(limitar(internacao.getBam(), 20));
        aih.setUnidade(limitar(internacao.getUnidade(), 25));
        aih.setCnes(limitar(cnes, 15));
        aih.setCid(normalizarCid(internacao.getCid()));
        // A alta usa a mesma fonte que promoverContasComAlta (Internacao.datasaida OU
        // NovoInternado.alta): olhar so datasaida fazia toda pre-AIH nascer EM_INTERNACAO,
        // porque esse campo fica quase sempre nulo, e so era corrigida depois, na
        // listagem. Nascer certa evita status desatualizado para quem le direto do banco
        // (relatorio, integracao, ou o gatilho automatico criarPreAihSeElegivel).
        LocalDateTime dataAlta = dataDaAlta(aih);
        aih.setIdcompetencia(competenciaDaInternacao(internacao.getDatainternacao() == null ? null
                : internacao.getDatainternacao().atStartOfDay(ZoneId.systemDefault()).toLocalDateTime(),
                internacao.getUnidade()));
        aih.setStatus(dataAlta == null ? AihStatus.EM_INTERNACAO.name() : AihStatus.PENDENTE_FATURAMENTO.name());
        aih.setDataInternacao(internacao.getDatainternacao() == null ? null
                : internacao.getDatainternacao().atStartOfDay(ZoneId.systemDefault()).toLocalDateTime());
        aih.setDataAlta(dataAlta);
        aih.setCriadoEm(LocalDateTime.now());
        aih.setUsuarioCriador(user == null ? "" : user.matricula());
        aih.persist();

        auditService.registrar(internacao.getUnidade(), "CRIAR_PRE_AIH", "SUCESSO", "FaturamentoAih", aih.getNumero(),
                null, aih.getStatus(), null, "idinternacao=" + idinternacao, user);
        return aih;
    }

    // Gatilho automatico da alta (InternacaoService#alterarInternacao): so cria a pre-AIH
    // se ja existir laudo de autorizacao vinculado, a mesma regra usada em
    // listarIdsInternacaoElegiveisParaAih. Sem isso, toda alta de internacao sem laudo
    // (ambulatorial, por exemplo) viraria pendencia de faturamento sem sentido.
    @Transactional
    public void criarPreAihSeElegivel(int idinternacao, AuthenticatedUser user) {
        if (aihRepository.buscarPorInternacao(idinternacao) != null) {
            return;
        }
        if (!laudoAutorizacaoRepository.existeLaudoAutorizacaoParaInternacao(idinternacao)) {
            return;
        }
        criarPreAih(idinternacao, user);
    }

    @Transactional
    public Map<String, Object> criarPreAihsDaCompetencia(Integer idcompetencia, String busca, AuthenticatedUser user) {
        if (idcompetencia == null) {
            throw new IllegalArgumentException("Informe a competencia.");
        }
        FaturamentoCompetencia comp = FaturamentoCompetencia.findById(idcompetencia);
        if (comp == null) {
            throw new IllegalArgumentException("Competencia nao encontrada.");
        }
        List<Integer> ids = laudoAutorizacaoRepository.listarIdsInternacaoElegiveisParaAih(comp.getCompetencia(),
                unidadeRestrita(user), busca);
        int criadasOuAtualizadas = 0;
        for (Integer idinternacao : ids) {
            FaturamentoAih aih = criarPreAih(idinternacao, user);
            if (aih.getIdcompetencia() == null || !aih.getIdcompetencia().equals(idcompetencia)) {
                aih.setIdcompetencia(idcompetencia);
            }
            criadasOuAtualizadas++;
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("elegiveis", ids.size());
        resultado.put("processadas", criadasOuAtualizadas);
        return resultado;
    }

    @Transactional
    public Map<String, Object> contaHospitalar(int idaih, AuthenticatedUser user) {
        FaturamentoAih aih = obrigatoria(idaih);
        List<Afaturar> producoes = producaoRepository.listarParaContaAih(aih);
        int classificadasAutomaticamente = classificarPendentesComoAih(aih, producoes, user,
                "Classificacao automatica como AIH pela conta hospitalar " + aih.getNumero() + ".");
        if (classificadasAutomaticamente > 0) {
            // O UPDATE em massa roda em transacao propria; limpar o contexto evita
            // que a releitura devolva as instancias obsoletas da primeira carga.
            producaoRepository.getEntityManager().clear();
            aih = obrigatoria(idaih);
            producoes = producaoRepository.listarParaContaAih(aih);
        }
        producoes.forEach(this::preencherMotivoStatusProducao);
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("aih", aih);
        resultado.put("producoes", producoes);
        resultado.put("valorEstimado", valorDaConta(producoes));
        resultado.put("classificadasAutomaticamente", classificadasAutomaticamente);
        // A tela precisa saber quantas linhas ficaram de fora para dizer isso ao
        // operador em vez de deixa-lo procurando o que "esta travando" a conta.
        resultado.put("naoFaturaveis", producoes.stream().filter(p -> !compoeContaDaAih(p)).count());
        // Sem nenhuma linha apta a conta esta vazia e o servidor vai recusar - dizer isso
        // aqui evita a tela oferecer um fechamento que ela sabe que nao vai acontecer.
        resultado.put("linhasAptas", producoes.stream().filter(this::compoeContaDaAih).count());
        resultado.put("bloqueiosParaFechar", producoes.stream()
                .filter(this::compoeContaDaAih)
                .filter(p -> !ProducaoStatus.APTO_FATURAMENTO.name().equals(p.getStatus()))
                .count());
        return resultado;
    }

    // Sem @Transactional de propósito: classificarPendentesComoAih abre sua propria
    // transacao (requiringNew) para o UPDATE em massa. Manter uma transacao externa
    // aberta durante a operacao a deixaria ociosa e o idle_in_transaction_session_timeout
    // (120s) do datasource poderia mata-la no meio do caminho.
    public Map<String, Object> classificarPendentesDaConta(int idaih, AuthenticatedUser user) {
        FaturamentoAih aih = obrigatoria(idaih);
        List<Afaturar> producoes = producaoRepository.listarParaContaAih(aih);
        int classificadas = classificarPendentesComoAih(aih, producoes, user,
                "Classificacao automatica em lote como AIH pela lista de contas hospitalares " + aih.getNumero()
                        + ".");
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("aih", aih);
        resultado.put("classificadas", classificadas);
        return resultado;
    }

    @Transactional
    public FaturamentoAih fechar(int idaih, AuthenticatedUser user) {
        return fecharConta(idaih, user);
    }

    // Sem @Transactional: quem chama define o escopo. Pelo endpoint individual vem a
    // transacao do fechar(); pelo lote, uma transacao por conta (requiringNew), para que
    // uma conta recusada nao desfaca as que ja fecharam.
    private FaturamentoAih fecharConta(int idaih, AuthenticatedUser user) {
        FaturamentoAih aih = obrigatoria(idaih);
        String anterior = aih.getStatus();
        if (AihStatus.FECHADA.name().equals(anterior) || AihStatus.CANCELADA.name().equals(anterior)) {
            throw new IllegalStateException("AIH " + aih.getNumero() + " ja esta " + anterior + ".");
        }
        List<Afaturar> producoes = producaoRepository.listarParaContaAih(aih);
        int classificadasAutomaticamente = classificarPendentesComoAih(aih, producoes, user,
                "Classificacao automatica como AIH antes do fechamento da conta hospitalar " + aih.getNumero()
                        + ".");
        if (classificadasAutomaticamente > 0) {
            // O UPDATE em massa roda em transacao propria; limpar o contexto evita
            // que a releitura devolva as instancias obsoletas da primeira carga.
            producaoRepository.getEntityManager().clear();
            aih = obrigatoria(idaih);
            producoes = producaoRepository.listarParaContaAih(aih);
        }
        validarProducoesParaFechamento(producoes);
        aih.setValorEstimado(valorDaConta(producoes));
        aih.setStatus(AihStatus.FECHADA.name());
        aih.setAtualizadoEm(LocalDateTime.now());

        // Numero oficial (NU_AIH) so no fechamento, e nunca por cima de um ja atribuido.
        //
        // Numero e recurso escasso: vem de faixa concedida pelo gestor e o SISAIH01 recusa
        // repetido. Atribuir na criacao da pre-AIH queimaria numero em conta que depois
        // fosse cancelada. Sem faixa cadastrada a conta fecha do mesmo jeito e fica sem
        // numero - e assim que o modulo funciona hoje, e nao pode piorar por causa disso.
        if (texto(aih.getNumeroAih()).isBlank()) {
            FaturamentoCompetencia competencia = aih.getIdcompetencia() == null ? null
                    : FaturamentoCompetencia.findById(aih.getIdcompetencia());
            String numeroOficial = faixaService.reservarNumero(aih.getUnidade(),
                    competencia == null ? null : competencia.getCompetencia(), user);
            if (numeroOficial != null) {
                aih.setNumeroAih(numeroOficial);
                auditService.registrar(aih.getUnidade(), "ATRIBUIR_NUMERO_AIH", "SUCESSO", "FaturamentoAih",
                        aih.getNumero(), null, numeroOficial, null,
                        "numero oficial tirado da faixa concedida pelo gestor", user);
            }
        }

        // Quantas linhas ficaram fora da conta fica no rastro: e a diferenca entre o que
        // a producao mostra e o que a AIH fechou, e alguem vai perguntar isso depois.
        long foraDaConta = producoes.stream().filter(p -> !compoeContaDaAih(p)).count();
        auditService.registrar(aih.getUnidade(), "FECHAR_AIH", "SUCESSO", "FaturamentoAih", aih.getNumero(),
                anterior, AihStatus.FECHADA.name(), null,
                "producoes=" + producoes.size() + " naoFaturaveisForaDaConta=" + foraDaConta, user);
        return aih;
    }

    // Fechada e cancelada sao decisao ja registrada; exportada saiu na remessa. O lote
    // passa por elas em silencio - nao sao falha, so nao ha o que fazer.
    private static final Set<String> STATUS_FORA_DO_LOTE = Set.of(
            AihStatus.FECHADA.name(), AihStatus.CANCELADA.name(), AihStatus.EXPORTADA.name());

    // Fecha a competencia inteira em uma requisicao. O laco vivia no navegador - um POST
    // por conta, em serie - e em competencia grande virava uma barra de progresso de
    // minutos que morria se o operador fechasse a aba. Aqui a varredura roda no servidor e
    // volta so o placar.
    //
    // Sem @Transactional de proposito: cada conta abre a sua (requiringNew), entao conta
    // recusada entra na lista de falhas com o motivo e as demais continuam fechando, em vez de um
    // erro derrubar o lote. Uma transacao unica em cima de centenas de contas ainda
    // ficaria ociosa tempo demais para o idle_in_transaction_session_timeout do datasource.
    //
    // EM_INTERNACAO nao e falha, e espera: a internacao nao terminou, a producao ainda vai
    // crescer e fechar agora congelaria a conta pela metade. Vai contada a parte para o
    // operador saber que essas voltam no lote seguinte, depois da alta no censo.
    public Map<String, Object> fecharCompetencia(Integer idcompetencia, String unidade, AuthenticatedUser user) {
        // Quem nao e kadm fecha so a propria unidade, doa a quem doer o que a tela mandou.
        // Para o kadm vale a unidade escolhida na tela, senao o lote fecharia contas de
        // unidades que ele nem esta vendo.
        String restrita = unidadeRestrita(user);
        String unidadeAlvo = restrita;
        if (unidadeAlvo == null && !texto(unidade).isBlank() && !"TODAS".equalsIgnoreCase(texto(unidade))) {
            unidadeAlvo = unidade;
        }

        List<FaturamentoAih> aihs = aihRepository.listar(idcompetencia, unidadeAlvo);
        // Mesma reconciliacao da listagem: conta cujo paciente ja teve alta no censo sai de
        // EM_INTERNACAO aqui e entra neste lote, em vez de esperar o operador abrir a tela.
        promoverContasComAlta(aihs);

        int fechadas = 0;
        int emInternacao = 0;
        int semProducaoApta = 0;
        List<Map<String, String>> falhas = new ArrayList<>();
        for (FaturamentoAih aih : aihs) {
            String status = texto(aih.getStatus());
            if (AihStatus.EM_INTERNACAO.name().equals(status)) {
                emInternacao++;
                continue;
            }
            if (STATUS_FORA_DO_LOTE.contains(status)) {
                continue;
            }
            int idaih = aih.getIdaih();
            try {
                QuarkusTransaction.requiringNew().call(() -> fecharConta(idaih, user));
                fechadas++;
            } catch (ContaSemProducaoAptaException semApta) {
                // Revalidar ainda vale: linha travada por critica pode virar apta e dar
                // conteudo a conta. So depois disso ela e realmente uma conta vazia.
                if (revalidarEFecharDeNovo(aih, idaih, user)) {
                    fechadas++;
                    continue;
                }
                semProducaoApta++;
            } catch (RuntimeException e) {
                if (revalidarEFecharDeNovo(aih, idaih, user)) {
                    fechadas++;
                    continue;
                }
                // A conta continua aberta e aparece na lista para tratamento manual: o
                // motivo volta junto para o operador nao ter de abrir uma a uma.
                Map<String, String> falha = new LinkedHashMap<>();
                falha.put("numero", texto(aih.getNumero()));
                falha.put("motivo", motivoDaFalha(e));
                falhas.add(falha);
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("contas", aihs.size());
        resultado.put("fechadas", fechadas);
        resultado.put("emInternacao", emInternacao);
        resultado.put("semProducaoApta", semProducaoApta);
        resultado.put("falhas", falhas);
        return resultado;
    }

    // Antes de desistir da conta: boa parte das criticas que sobraram ja foi resolvida em
    // outro lugar (SIGTAP importado, CNES da unidade, dado corrigido na origem) e a linha
    // so continua marcada porque ninguem a revalidou. Reprocessa o que nao esta apto e
    // tenta fechar de novo. O que depende de dado faltando continua barrado - e ai a conta
    // volta na lista de falhas com o motivo.
    //
    // NAO_FATURAVEL fica de fora da revalidacao: ela nao entra na conta nem impede o
    // fechamento, entao reprocessa-la so gastaria tempo para devolver o mesmo status - e
    // sao dezenas por conta.
    private boolean revalidarEFecharDeNovo(FaturamentoAih aih, int idaih, AuthenticatedUser user) {
        List<Afaturar> travadas = producaoRepository.listarParaContaAih(aih).stream()
                .filter(p -> !compoeContaDaAih(p))
                .filter(p -> !ProducaoStatus.NAO_FATURAVEL.name().equals(p.getStatus()))
                .toList();
        if (travadas.isEmpty()) {
            return false;
        }
        int reprocessadas = 0;
        for (Afaturar producao : travadas) {
            try {
                producaoService.reprocessar(producao.getIdafaturar(), user);
                reprocessadas++;
            } catch (RuntimeException ignorada) {
                // Linha que nem reprocessa nao muda o destino da conta: o que importa e se
                // alguma virou apta, e isso a segunda tentativa responde.
            }
        }
        if (reprocessadas == 0) {
            return false;
        }
        try {
            QuarkusTransaction.requiringNew().call(() -> fecharConta(idaih, user));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String motivoDaFalha(RuntimeException e) {
        String mensagem = e.getMessage();
        if (mensagem != null && !mensagem.isBlank()) {
            return mensagem;
        }
        Throwable causa = e.getCause();
        if (causa != null && causa.getMessage() != null && !causa.getMessage().isBlank()) {
            return causa.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    private FaturamentoAih obrigatoria(int idaih) {
        FaturamentoAih aih = FaturamentoAih.findById(idaih);
        if (aih == null) {
            throw new IllegalArgumentException("AIH nao encontrada para o id: " + idaih);
        }
        return aih;
    }

    // Uma leitura so da producao serve as duas respostas da lista: quantas pendencias de
    // classificacao a conta tem e o que ficou de fora dela. Eram duas varreduras da mesma
    // producao quando a segunda foi adicionada.
    private void preencherPendenciasClassificacao(FaturamentoAih aih) {
        List<Afaturar> producoes = producaoRepository.listarParaContaAih(aih);
        aih.setPendenciasClassificacao(producoes.stream()
                .filter(this::producaoPendenteClassificacao)
                .count());
        preencherMotivosForaDaConta(aih, producoes);
    }

    // Classificacao em massa via UPDATE unico (classificarPendentesComoAihEmMassa),
    // mesmo racional do "Classificar todas" da tela de criticas. A versao anterior
    // reprocessava producao a producao em transacao propria, mas o contexto de
    // persistencia da requisicao (compartilhado entre as transacoes aninhadas)
    // acumulava um batch de update que crescia a cada iteracao e o commit estourava
    // o socketTimeout do datasource, derrubando tudo com "Este comando foi fechado"
    // (RollbackException ARJUNA016053). O UPDATE em massa e unico e o que ja estava
    // classificado permanece.
    private int classificarPendentesComoAih(FaturamentoAih aih, List<Afaturar> producoes, AuthenticatedUser user,
            String motivo) {
        List<Integer> idsPendentes = producoes.stream()
                .filter(this::producaoPendenteClassificacao)
                .map(Afaturar::getIdafaturar)
                .toList();
        if (idsPendentes.isEmpty()) {
            return 0;
        }
        String unidade = aih.getUnidade();
        int[] classificadas = new int[1];
        QuarkusTransaction.requiringNew().run(() -> {
            classificadas[0] = producaoRepository.classificarPendentesComoAihEmMassa(idsPendentes,
                    user == null ? "" : user.matricula());
            auditService.registrar(unidade, "CLASSIFICAR_PENDENTES_AIH", "SUCESSO", "FaturamentoAih",
                    aih.getNumero(), null, String.valueOf(classificadas[0]), motivo,
                    "classificacao em massa como AIH (instrumento=AIH)", user);
        });
        return classificadas[0];
    }

    private boolean producaoPendenteClassificacao(Afaturar producao) {
        return producao != null && ("PENDENTE_CLASSIFICACAO".equals(producao.getInstrumentoRegistro())
                || "PENDENTE_CLASSIFICACAO".equals(producao.getStatus()));
    }

    // Producao NAO_FATURAVEL nao entra na conta da AIH: ela nasce desse status quando o
    // SIGTAP local devolve valor zero para o procedimento (FaturamentoProducaoService
    // .classificarInstrumento) ou quando o operador a descartou com motivo gravado. Em
    // qualquer dos dois casos a linha vale R$ 0,00 - somar ou nao somar da no mesmo, e
    // travar o fechamento por causa dela nao protege receita nenhuma.
    //
    // Ela nao some nem e apagada: continua em Afaturar e aparece em /producao com o
    // status NAO_FATURAVEL, que e onde a decisao pode ser revista (reverter descarte,
    // corrigir o SIGTAP). O que muda aqui e so a pergunta "esta linha impede fechar a
    // conta?".
    // A conta e o que esta pronto para faturar hoje: so APTO_FATURAMENTO entra. O resto
    // (nao faturavel, com critica, em revisao, pendente) fica de fora - continua vivo em
    // Afaturar e aparece em /producao com o proprio status, que e onde se corrige.
    //
    // O que torna isso seguro e reabrir(): sem ela, fechar deixando linha com critica de
    // fora congelaria a conta com valor menor que o real e a correcao feita depois nunca
    // voltaria para a AIH. Com ela, corrigir a critica e refazer a conta e um caminho de
    // ida e volta - por isso as duas coisas mudaram juntas.
    private boolean compoeContaDaAih(Afaturar producao) {
        return producao != null && ProducaoStatus.APTO_FATURAMENTO.name().equals(producao.getStatus());
    }

    private double valorDaConta(List<Afaturar> producoes) {
        return producoes.stream()
                .filter(this::compoeContaDaAih)
                .mapToDouble(p -> p.getPa_sa() + p.getPa_sp() + p.getPa_sh())
                .sum();
    }

    // Conta vazia nao e o mesmo que conta com problema: no lote ela nao precisa de
    // correcao nenhuma, so nao tem o que faturar. Um tipo proprio deixa o placar separar
    // "12 sem producao apta" de "3 recusadas por critica" sem depender do texto da
    // mensagem.
    public static class ContaSemProducaoAptaException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public ContaSemProducaoAptaException(String mensagem) {
            super(mensagem);
        }
    }

    // Uma conta sem nenhuma linha apta nao tem o que fechar: fechar aqui so registraria
    // uma AIH de R$ 0,00 e esconderia a producao que ainda precisa ser tratada.
    private void validarProducoesParaFechamento(List<Afaturar> producoes) {
        boolean temApta = producoes.stream().anyMatch(this::compoeContaDaAih);
        if (!temApta) {
            long fora = producoes.stream().filter(p -> !compoeContaDaAih(p)).count();
            throw new ContaSemProducaoAptaException("Fechamento da AIH bloqueado: nenhuma producao apta nesta conta"
                    + (fora > 0 ? " (" + fora + " linha(s) pendente(s), com critica ou nao faturavel)" : "")
                    + ". Trate a producao em Faturamento SUS > Producao antes de fechar.");
        }
    }

    // Por que cada linha ficou de fora, agrupado, para a lista dizer o que corrigir sem
    // obrigar o operador a abrir conta por conta. A validacao roda so nas linhas que
    // ficaram de fora (nunca na conta inteira) e nao grava nada - e a mesma leitura em
    // memoria que a tela de motivos de critica ja faz.
    private void preencherMotivosForaDaConta(FaturamentoAih aih, List<Afaturar> producoes) {
        List<Afaturar> fora = producoes.stream().filter(p -> !compoeContaDaAih(p)).toList();
        aih.setLinhasForaDaConta(fora.size());
        aih.setLinhasAptas(producoes.size() - fora.size());
        if (fora.isEmpty()) {
            aih.setMotivosForaDaConta(List.of());
            return;
        }

        Map<String, Integer> porMotivo = new LinkedHashMap<>();
        for (Afaturar producao : fora) {
            if (ProducaoStatus.NAO_FATURAVEL.name().equals(producao.getStatus())) {
                porMotivo.merge("Nao faturavel (sem valor no SIGTAP)", 1, Integer::sum);
                continue;
            }
            List<ValidacaoResultado> resultados = validationService.validar(producao);
            boolean algum = false;
            for (ValidacaoResultado resultado : resultados) {
                if (resultado.resultado() == ResultadoValidacao.SUCESSO) {
                    continue;
                }
                algum = true;
                porMotivo.merge(resultado.codigo(), 1, Integer::sum);
            }
            if (!algum) {
                porMotivo.merge(texto(producao.getStatus()), 1, Integer::sum);
            }
        }

        aih.setMotivosForaDaConta(porMotivo.entrySet().stream()
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .toList());
    }

    private void preencherMotivoStatusProducao(Afaturar producao) {
        if (producao == null || texto(producao.getStatus()).isBlank()) {
            return;
        }
        if (ProducaoStatus.APTO_FATURAMENTO.name().equals(producao.getStatus())) {
            producao.setMotivoStatus("");
            return;
        }

        List<ValidacaoResultado> resultados = validationService.validar(producao).stream()
                .filter(resultado -> resultado.resultado() != ResultadoValidacao.SUCESSO)
                .toList();
        if (resultados.isEmpty()) {
            producao.setMotivoStatus(texto(producao.getStatus()));
            return;
        }
        // CPF, nascimento e nome do cadastro. So a linha criticada precisa deles -
        // enriquecer as aptas custaria uma consulta por linha sem nada em troca. E a
        // tela da conta usa esses campos para oferecer a correcao ali mesmo, em vez de
        // mandar o operador para o painel da competencia inteira.
        producaoService.enriquecerComPaciente(producao);
        producao.setMotivoStatus(resultados.stream()
                .flatMap(resultado -> Stream.of(resultado.codigo() + " - " + resultado.mensagem(),
                        producaoService.orientacaoParaCorrigir(resultado, producao)))
                .filter(mensagem -> mensagem != null && !mensagem.isBlank())
                .distinct()
                .toList()
                .stream()
                .reduce((a, b) -> a + "; " + b)
                .orElse(""));
    }

    // Desfaz o fechamento para que a conta seja refeita depois que a producao travada for
    // corrigida em /producao. Nao apaga nada e nao e "desfazer": e um evento auditado com
    // autor e motivo, no mesmo espirito de reverterClassificacaoManual.
    //
    // Conta exportada nao volta: a remessa ja saiu e reabrir aqui deixaria a base local
    // divergente do que foi entregue.
    @Transactional
    public FaturamentoAih reabrir(int idaih, String motivo, AuthenticatedUser user) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Motivo e obrigatorio para reabrir a conta hospitalar.");
        }
        FaturamentoAih aih = obrigatoria(idaih);
        String anterior = aih.getStatus();
        if (AihStatus.EXPORTADA.name().equals(anterior)) {
            throw new IllegalStateException("AIH " + aih.getNumero()
                    + " ja foi exportada e nao pode ser reaberta.");
        }
        if (!AihStatus.FECHADA.name().equals(anterior)) {
            throw new IllegalStateException("AIH " + aih.getNumero() + " nao esta fechada (status " + anterior + ").");
        }
        aih.setStatus(AihStatus.PENDENTE_FATURAMENTO.name());
        aih.setValorEstimado(valorDaConta(producaoRepository.listarParaContaAih(aih)));
        aih.setAtualizadoEm(LocalDateTime.now());

        auditService.registrar(aih.getUnidade(), "REABRIR_AIH", "SUCESSO", "FaturamentoAih", aih.getNumero(),
                anterior, aih.getStatus(), motivo, "reabertura para recompor a conta apos correcao", user);
        return aih;
    }

    // Encerra a conta que nunca vai fechar: internacao que nao gera AIH, pre-AIH criada em
    // duplicidade, conta cuja producao inteira e nao faturavel. Sem isto ela ficava
    // PENDENTE_FATURAMENTO para sempre, aparecia em toda listagem e o lote a tentava de
    // novo a cada rodada, sempre com o mesmo erro.
    //
    // Nao apaga nada: a producao continua em Afaturar e visivel em /producao, disponivel
    // para outro caminho de faturamento. O que morre e a conta, com autor e motivo na
    // auditoria.
    //
    // Conta fechada precisa ser reaberta antes: o valor dela ja foi contabilizado, e
    // cancelar por cima esconderia essa passagem do rastro. Exportada nao volta de jeito
    // nenhum - a remessa ja saiu.
    @Transactional
    public FaturamentoAih cancelar(int idaih, String motivo, AuthenticatedUser user) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Motivo e obrigatorio para cancelar a conta hospitalar.");
        }
        FaturamentoAih aih = obrigatoria(idaih);
        String anterior = aih.getStatus();
        if (AihStatus.CANCELADA.name().equals(anterior)) {
            throw new IllegalStateException("AIH " + aih.getNumero() + " ja esta cancelada.");
        }
        if (AihStatus.EXPORTADA.name().equals(anterior)) {
            throw new IllegalStateException("AIH " + aih.getNumero()
                    + " ja foi exportada e nao pode ser cancelada.");
        }
        if (AihStatus.FECHADA.name().equals(anterior)) {
            throw new IllegalStateException("AIH " + aih.getNumero()
                    + " esta fechada. Reabra a conta antes de cancelar.");
        }
        aih.setStatus(AihStatus.CANCELADA.name());
        // O valor deixa de existir junto com a conta: manter o estimado antigo faria a
        // conta cancelada continuar somando em qualquer total da tela.
        aih.setValorEstimado(0);
        aih.setAtualizadoEm(LocalDateTime.now());

        long producoes = producaoRepository.listarParaContaAih(aih).size();
        auditService.registrar(aih.getUnidade(), "CANCELAR_AIH", "SUCESSO", "FaturamentoAih", aih.getNumero(),
                anterior, aih.getStatus(), motivo,
                "producao mantida em Afaturar: " + producoes + " linha(s)", user);
        return aih;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String limitar(String valor, int tamanho) {
        String texto = texto(valor);
        return texto.length() <= tamanho ? texto : texto.substring(0, tamanho);
    }

    private String normalizarCid(String valor) {
        String texto = texto(valor).toUpperCase();
        if (texto.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[A-Z][0-9][0-9A-Z](?:\\.[0-9A-Z]{1,4})?")
                .matcher(texto);
        if (matcher.find()) {
            return limitar(matcher.group(), 20);
        }
        return limitar(texto, 20);
    }

    private Integer competenciaDaInternacao(LocalDateTime dataInternacao, String unidade) {
        if (dataInternacao == null) {
            return null;
        }
        String competencia = String.format("%04d%02d", dataInternacao.getYear(), dataInternacao.getMonthValue());
        FaturamentoCompetencia comp = competenciaRepository.buscarPorCompetenciaUnidade(competencia, unidade);
        return comp == null ? null : comp.getIdcompetencia();
    }

    private String unidadeRestrita(AuthenticatedUser user) {
        if (user == null || user.hasAnyPermission(List.of("kadm"))) {
            return null;
        }
        return user.unidade();
    }
}

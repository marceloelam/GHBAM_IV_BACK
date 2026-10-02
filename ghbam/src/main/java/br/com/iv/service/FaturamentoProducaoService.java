package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.jboss.logging.Logger;

import br.com.iv.dto.AfaturarDto;
import br.com.iv.dto.ConsultaCnsDto;
import br.com.iv.dto.ValidacaoResultado;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.FaturamentoAih;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.entity.Pacientes;
import br.com.iv.enums.AihStatus;
import br.com.iv.enums.CompetenciaStatus;
import br.com.iv.enums.InstrumentoRegistro;
import br.com.iv.enums.ProducaoStatus;
import br.com.iv.enums.ResultadoValidacao;
import br.com.iv.entity.ProcedimentosSus;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import br.com.iv.repository.FaturamentoCompetenciaRepository;
import br.com.iv.repository.FaturamentoProducaoRepository;
import br.com.iv.repository.ProcedimentosSusRepository;
import br.com.iv.security.AuthenticatedUser;

// Motor de faturamento da Fase 1: classifica o instrumento, roda o motor de
// validacao e mantem o status da producao. Reaproveita "afaturar" como producao
// central (ver decisao de arquitetura do plano da Fase 1) em vez de duplicar a
// tabela. O resultado da validacao vive apenas no status/semaforo da producao -
// nao ha mais registro de critica persistido.
@ApplicationScoped
public class FaturamentoProducaoService {

    private static final Logger LOG = Logger.getLogger(FaturamentoProducaoService.class);

    // Varredura do agrupamento de motivos: le a competencia inteira em lotes. O teto de
    // paginas evita varredura infinita se o repositorio devolver pagina cheia sempre.
    private static final int TAMANHO_PAGINA_MOTIVOS = 500;
    private static final int MAX_PAGINAS_MOTIVOS = 200;

    // Separador [-/] em vez de fixo: o campo data de Afaturar chega de dezenas de
    // origens diferentes (evolucao, prescricao, admissao de RN, etc.) e convive com pelo
    // menos 4 formatos reais - confirmado direto no banco em 2026-09-22, checando por que
    // producao com data legivel ainda ficava sem competencia vinculada. Alem de
    // "aaaa-mm-dd" e "dd/mm/aaaa" (os dois originais), existe "aaaa/mm/dd" (mesmo formato
    // que a /recepcao grava no nascimento, ver Cad_pacientes.jsx) e "dd-mm-aaaa". So
    // adiciona casamento: uma data que ja batia com o padrao antigo continua batendo
    // igual, porque o separador era so um dos dois caracteres agora aceitos.
    private static final Pattern DATA_ISO = Pattern.compile("^(\\d{4})[-/](\\d{2})[-/]\\d{2}");
    private static final Pattern DATA_BR = Pattern.compile("^\\d{2}[-/](\\d{2})[-/](\\d{4})");

    @Inject
    FaturamentoProducaoRepository producaoRepository;

    @Inject
    FaturamentoCompetenciaRepository competenciaRepository;

    @Inject
    FaturamentoValidationService validationService;

    @Inject
    FaturamentoAuditService auditService;

    @Inject
    ProcedimentosSusRepository procedimentosSusRepository;

    @Inject
    ConsultaCnsService consultaCnsService;

    @Inject
    ConfiguracaoCnsPdqService configuracaoCnsPdqService;

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    @Inject
    br.com.iv.repository.CboRepository cboRepository;

    @Inject
    FaturamentoRaasService raasService;

    public List<Afaturar> listar(Integer idcompetencia, String unidade, String status, String instrumentoRegistro,
            String busca, int pagina, int tamanho) {
        List<Afaturar> producoes = producaoRepository.listarPaginado(idcompetencia, unidade, status,
                instrumentoRegistro, busca, pagina, tamanho);
        for (Afaturar producao : producoes) {
            enriquecerComPaciente(producao);
        }
        enriquecerComProfissional(producoes);
        return producoes;
    }

    // Ocupacao do profissional da linha, vinda do cadastro do servidor: a producao
    // guarda so matricula e nome. Em lote de proposito - sao 50 linhas por pagina e
    // varias repetem o mesmo servidor, entao uma consulta pelas matriculas distintas
    // resolve todas (enriquecerComPaciente ainda faz uma por linha porque a chave de
    // paciente e sempre diferente).
    private void enriquecerComProfissional(List<Afaturar> producoes) {
        java.util.Set<String> matriculas = new java.util.HashSet<>();
        for (Afaturar producao : producoes) {
            String matricula = producao.getMatriculaservidor();
            if (matricula != null && !matricula.isBlank()) {
                matriculas.add(matricula.trim());
            }
        }
        if (matriculas.isEmpty()) {
            return;
        }
        Map<String, br.com.iv.entity.Servidor> porMatricula = new java.util.HashMap<>();
        List<br.com.iv.entity.Servidor> servidores = br.com.iv.entity.Servidor
                .list("matricula in ?1", List.copyOf(matriculas));
        for (br.com.iv.entity.Servidor servidor : servidores) {
            if (servidor.getMatricula() != null) {
                porMatricula.put(servidor.getMatricula().trim(), servidor);
            }
        }
        for (Afaturar producao : producoes) {
            String matricula = producao.getMatriculaservidor();
            br.com.iv.entity.Servidor servidor = matricula == null ? null : porMatricula.get(matricula.trim());
            if (servidor == null) {
                continue;
            }
            producao.setCargoServidor(servidor.getCargo());
            producao.setCboServidor(servidor.getCbo());
            producao.setCrmServidor(servidor.getCrm());
            producao.setCoremServidor(servidor.getCorem());
            producao.setEspecialidadeServidor(servidor.getEspecialidade());
        }
    }

    // Publico porque a conta hospitalar tambem precisa: a linha criticada por CNS so
    // vira acao na tela quando o operador ve o CPF e o nascimento do cadastro, que sao
    // exatamente os dados que a busca no CADSUS consome.
    public void enriquecerComPaciente(Afaturar producao) {
        br.com.iv.entity.Pacientes paciente = br.com.iv.entity.Pacientes.findById(producao.getIdpaciente());
        if (paciente == null) {
            return;
        }
        producao.setCpfPaciente(paciente.getCpf());
        producao.setNascimentoPaciente(paciente.getNascimento());
        producao.setMaePaciente(paciente.getMae());
        producao.setMunicipioPaciente(paciente.getMunicipio());
    }

    public Afaturar buscar(int idafaturar) {
        Afaturar producao = Afaturar.findById(idafaturar);
        if (producao == null) {
            throw new IllegalArgumentException("Producao nao encontrada para o id: " + idafaturar);
        }
        return producao;
    }

    public Afaturar buscar(int idafaturar, String unidade) {
        Afaturar producao = buscar(idafaturar);
        if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)
                && (producao.getUnidade() == null || !producao.getUnidade().equalsIgnoreCase(unidade.trim()))) {
            throw new IllegalArgumentException("Producao nao encontrada para a unidade informada.");
        }
        return producao;
    }

    // Tabela oficial de instrumento de registro do SIGTAP (rl_procedimento_registro,
    // campo CO_REGISTRO), conforme a wiki do Ministerio da Saude:
    //
    //   01 BPA-C   02 BPA-I   03/04 AIH   05/06/07 APAC   08/09 RAAS
    //
    // A correspondencia foi conferida contra os proprios dados da tabela 202607, e nao so
    // contra a documentacao: dos 1.623 procedimentos com registro 03, 1.612 tem valor de
    // Servico Hospitalar (pa_sh) - PARTO NORMAL, ARTROPLASTIA DE QUADRIL, TRATAMENTO DE
    // INFARTO. Registro 04 idem (412 de 437). Ja 05, 06 e 07 nao tem pa_sh em nenhum dos
    // 864 procedimentos. SH so se paga em internacao, entao 03/04 e AIH e 05/06/07 e APAC.
    //
    // A regra antiga tratava 03 como BPA-I. Nao e: 03 e APAC. O efeito real, medido na
    // competencia 202607 do HMSFX, foi 186 lancamentos de parto, cesariana, artroplastia
    // e tratamento cirurgico de fratura - R$ 112.460,38 - classificados como BPA-I, com
    // 96% deles pertencendo a pacientes internados e 41 ja com pre-AIH aberta (cobranca
    // em duplicidade a caminho).
    static boolean admiteRegistro(String registros, String... codigos) {
        String valor = registros == null ? "" : registros.trim();
        for (String codigo : codigos) {
            if (valor.contains(codigo)) {
                return true;
            }
        }
        return false;
    }

    public InstrumentoRegistro classificarInstrumento(Afaturar producao) {
        String tipoRegistro = normalizarTipoRegistro(producao.getPa_tpreg());
        // A ordem e deliberadamente conservadora: o ambulatorial vem primeiro, e a AIH so
        // leva o procedimento que NAO pode ser registrado em nenhum instrumento
        // ambulatorial. Um procedimento que aceita BPA e AIH depende de o paciente estar
        // internado, e essa informacao nao esta nesta linha - na duvida fica no BPA, que e
        // de onde vem a maior parte desta producao. Ja parto e cirurgia so aceitam AIH, e
        // e exatamente esse conjunto que estava saindo errado.
        if (admiteRegistro(tipoRegistro, "02")) {
            return InstrumentoRegistro.BPA_I;
        }
        if (admiteRegistro(tipoRegistro, "01")) {
            return InstrumentoRegistro.BPA_C;
        }
        if (admiteRegistro(tipoRegistro, "08", "09")) {
            return InstrumentoRegistro.RAAS;
        }
        // 03 a 07 sao todos AIH - principal, especial e secundario.
        //
        // A leitura anterior separava 05/06/07 como APAC porque nenhum deles tem valor
        // hospitalar (pa_sh). O motivo do valor zero e outro: sao procedimentos que
        // compoem a conta e nao se cobram a parte. O caso que mostrou isso foi
        // 0301010170 CONSULTA/AVALIACAO EM PACIENTE INTERNADO, registro 05, R$ 0,00 e
        // 2.778 lancamentos - e a visita medica diaria, coberta pela diaria da AIH.
        // Classifica-la como APAC criava uma remessa ambulatorial de procedimento que so
        // existe dentro de uma internacao.
        //
        // Estes entram na AIH como procedimento secundario (as 10 ocorrencias do layout,
        // ver AihExporter), nao como instrumento proprio.
        if (admiteRegistro(tipoRegistro, "03", "04", "05", "06", "07")) {
            return InstrumentoRegistro.AIH;
        }
        boolean semValorSigtap = producao.getPa_sa() <= 0 && producao.getPa_sp() <= 0 && producao.getPa_sh() <= 0;
        if (semValorSigtap) {
            return InstrumentoRegistro.NAO_FATURAVEL;
        }
        // Ha valor SIGTAP, mas o tipo de registro nao veio definido. Para nao deixar
        // a producao presa em PENDENTE_CLASSIFICACAO, classifica automaticamente pelo
        // CNS individual: com CNS valido gera BPA-I; sem CNS gera BPA-C.
        return possuiCnsPacienteValido(producao) ? InstrumentoRegistro.BPA_I : InstrumentoRegistro.BPA_C;
    }

    @Transactional
    public Afaturar reprocessar(int idafaturar, AuthenticatedUser user) {
        return reprocessarInterno(buscar(idafaturar), user);
    }

    @Transactional
    public Afaturar reprocessar(int idafaturar, AuthenticatedUser user, String unidade) {
        return reprocessarInterno(buscar(idafaturar, unidade), user);
    }

    // Campos que a tela de criticas pode corrigir diretamente (ver
    // FaturamentoValidationService: cada regra aponta um campoRelacionado). CNES e
    // competencia ficam de fora de proposito - nao sao dado da producao em si (sao
    // configuracao da unidade / workflow de competencia), corrigir aqui daria a
    // impressao de resolver algo que precisa ser resolvido em outro lugar.
    private static final java.util.Set<String> CAMPOS_CORRIGIVEIS = java.util.Set.of(
            "cnsdopciente", "cnsdoprofissional", "cbo", "procedimento", "cid10", "ibgecod");

    // Correcao direta de producao a partir de uma critica (pedido do usuario: "tem
    // como corrigir o que a descricao informou"). Atualiza o campo, reprocessa (o que
    // marca as criticas antigas como REPROCESSADA e roda a validacao de novo - se o
    // problema sumiu, nenhuma critica nova para essa regra e criada) e audita.
    @Transactional
    public Afaturar corrigirCampo(int idafaturar, String campo, String valor, AuthenticatedUser user) {
        return corrigirCampo(idafaturar, campo, valor, user, null);
    }

    @Transactional
    public Afaturar corrigirCampo(int idafaturar, String campo, String valor, AuthenticatedUser user, String unidade) {
        String campoNormalizado = campo == null ? "" : campo.trim();
        if (!CAMPOS_CORRIGIVEIS.contains(campoNormalizado)) {
            throw new IllegalArgumentException("Campo '" + campo + "' nao pode ser corrigido por aqui.");
        }
        if (valor == null || valor.trim().isBlank()) {
            throw new IllegalArgumentException("Informe um valor para o campo.");
        }
        Afaturar producao = buscar(idafaturar, unidade);
        String valorNovo = valor.trim();
        // Barra o CNS invalido na porta de entrada, e nao so na validacao depois de
        // gravado: quem corrige por aqui esta justamente resolvendo uma critica, e
        // gravar um numero que o DATASUS vai recusar troca uma pendencia por outra pior
        // (a linha fica APTA e a rejeicao so aparece depois do envio).
        if (("cnsdopciente".equals(campoNormalizado) || "cnsdoprofissional".equals(campoNormalizado))
                && !CnsUtils.valido(valorNovo)) {
            throw new IllegalArgumentException(CnsUtils.motivoInvalido(valorNovo));
        }
        String valorAnterior;
        if ("cnsdopciente".equals(campoNormalizado)) {
            valorAnterior = producao.getCnsdopciente();
            producao.setCnsdopciente(valorNovo);
        } else if ("cnsdoprofissional".equals(campoNormalizado)) {
            valorAnterior = producao.getCnsdoprofissional();
            producao.setCnsdoprofissional(valorNovo);
        } else if ("cbo".equals(campoNormalizado)) {
            valorAnterior = producao.getCbo();
            producao.setCbo(valorNovo);
        } else if ("cid10".equals(campoNormalizado)) {
            valorAnterior = producao.getCid10();
            producao.setCid10(valorNovo.toUpperCase());
        } else if ("ibgecod".equals(campoNormalizado)) {
            valorAnterior = producao.getIbgecod();
            producao.setIbgecod(valorNovo);
        } else {
            valorAnterior = producao.getProcedimento();
            producao.setProcedimento(valorNovo);
            // Corrigir so o texto do codigo nao adianta nada sozinho: pa_sa/pa_sp/pa_sh/pa_tpreg
            // sao gravados na producao no momento da criacao (GravaFaturamento) e nunca mais
            // recalculados. Reler o SIGTAP local aqui e o unico jeito de tirar a producao de
            // NAO_FATURAVEL/PENDENTE_CLASSIFICACAO/PROCEDIMENTO_NAO_ENCONTRADO por essa tela -
            // inclusive quando o codigo ja estava certo e so a competencia SIGTAP acabou de ser
            // importada.
            ProcedimentosSus sigtap = procedimentosSusRepository.filtrarPorProcedimentoNaCompetencia(valorNovo,
                    derivarCompetencia(producao));
            if (sigtap == null) {
                throw new IllegalArgumentException("Procedimento " + valorNovo
                        + " nao encontrado no SIGTAP local. Importe a competencia em Faturamento SUS > SIGTAP antes de corrigir.");
            }
            producao.setDescricao(sigtap.getPa_dc());
            producao.setPa_sa(sigtap.getPa_sa());
            producao.setPa_sp(sigtap.getPa_sp());
            producao.setPa_sh(sigtap.getPa_sh());
            producao.setPa_tpreg(sigtap.getPa_tpreg());
            producao.setPa_eletiva(sigtap.getPa_eletiva());
        }
        Afaturar resultado = reprocessarInterno(producao, user);
        auditService.registrar(producao.getUnidade(), "CORRIGIR_CAMPO_PRODUCAO", "SUCESSO", "Afaturar",
                String.valueOf(idafaturar), valorAnterior, valorNovo, null,
                "campo=" + campoNormalizado, user);
        return resultado;
    }

    // Correcao manual (secao "PENDENTE_CLASSIFICACAO tem como corrigir"): quando o
    // SIGTAP local (pa_tpreg) nao define BPA-I/BPA-C/AIH para o procedimento,
    // classificarInstrumento nunca sai de PENDENTE_CLASSIFICACAO sozinho. Isto e
    // uma correcao humana auditada, nao uma regra oficial nova - so aceita
    // instrumentos terminais conhecidos e exige motivo.
    //
    // NAO_FATURAVEL entra pela mesma porta com outro proposito: descartar producao que
    // nao pode ir para o BPA (registro de teste, lancamento indevido). Nao existe
    // exclusao fisica de proposito - faturamento SUS e registro auditavel, entao o
    // descarte e um status com autor e motivo gravados. Como classificacaoManual fica
    // true, reprocessarInterno preserva a decisao e nao reclassifica a linha depois.
    private static final java.util.Set<InstrumentoRegistro> INSTRUMENTOS_MANUAIS = java.util.Set.of(
            InstrumentoRegistro.BPA_I, InstrumentoRegistro.BPA_C, InstrumentoRegistro.AIH,
            InstrumentoRegistro.NAO_FATURAVEL);

    @Transactional
    public Afaturar classificarManualmente(int idafaturar, InstrumentoRegistro instrumento, String motivo,
            AuthenticatedUser user) {
        return classificarManualmente(idafaturar, instrumento, motivo, user, null);
    }

    @Transactional
    public Afaturar classificarManualmente(int idafaturar, InstrumentoRegistro instrumento, String motivo,
            AuthenticatedUser user, String unidade) {
        if (!INSTRUMENTOS_MANUAIS.contains(instrumento)) {
            throw new IllegalArgumentException(
                    "Classificacao manual so aceita BPA_I, BPA_C, AIH ou NAO_FATURAVEL.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Motivo e obrigatorio para classificacao manual.");
        }
        Afaturar producao = buscar(idafaturar, unidade);
        String instrumentoAnterior = producao.getInstrumentoRegistro();
        producao.setInstrumentoRegistro(instrumento.name());
        producao.setClassificacaoManual(true);
        Afaturar resultado = reprocessarInterno(producao, user);
        boolean descarte = instrumento == InstrumentoRegistro.NAO_FATURAVEL;
        auditService.registrar(producao.getUnidade(),
                descarte ? "DESCARTAR_PRODUCAO" : "CLASSIFICAR_MANUAL_INSTRUMENTO", "SUCESSO", "Afaturar",
                String.valueOf(idafaturar), instrumentoAnterior, instrumento.name(), motivo,
                descarte ? "descarte manual (producao fora do BPA por decisao do operador)"
                        : "classificacao manual (SIGTAP local sem pa_tpreg definido)",
                user);
        return resultado;
    }

    // Conferencia antes do fechamento: recem-nascidos cuja producao saiu do BPA como
    // coberta pela AIH sem ter internacao no censo. Sao os casos que nenhum caminho
    // fatura - ver a explicacao completa no repositorio.
    public List<Map<String, Object>> listarRecemNascidosSemInternacao(Integer idcompetencia, String unidade) {
        return producaoRepository.listarRecemNascidosSemInternacao(idcompetencia, unidade);
    }

    // Volta atras numa classificacao manual: limpa a marca e devolve a linha ao motor
    // automatico. E a porta de saida que faltava - descartar era irreversivel pela tela,
    // e sem ela um engano (o recem-nascido marcado como coberto pela AIH sem estar
    // internado, por exemplo) so se desfazia mexendo no banco.
    //
    // Nao e "desfazer": nada e apagado. A reversao e mais um evento auditado, com autor
    // e motivo proprios, e o instrumento e recalculado do zero por reprocessarInterno -
    // que agora volta a mandar, justamente porque classificacaoManual saiu do caminho.
    // Por isso o resultado nao e necessariamente o instrumento anterior ao descarte: e
    // o que a regra atual diz, com o SIGTAP e os dados de hoje.
    @Transactional
    public Afaturar reverterClassificacaoManual(int idafaturar, String motivo, AuthenticatedUser user,
            String unidade) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Motivo e obrigatorio para reverter a classificacao manual.");
        }
        Afaturar producao = buscar(idafaturar, unidade);
        if (!Boolean.TRUE.equals(producao.getClassificacaoManual())) {
            throw new IllegalArgumentException(
                    "Esta producao nao tem classificacao manual para reverter.");
        }
        // Exportada nao volta: a remessa ja foi entregue ao DATASUS e mudar o
        // instrumento aqui deixaria a base local divergente do que foi enviado.
        if (Boolean.TRUE.equals(producao.getExportado())) {
            throw new IllegalArgumentException(
                    "Producao ja exportada nao pode ter a classificacao revertida.");
        }
        String instrumentoAnterior = producao.getInstrumentoRegistro();
        producao.setClassificacaoManual(false);
        Afaturar resultado = reprocessarInterno(producao, user);
        auditService.registrar(producao.getUnidade(), "REVERTER_CLASSIFICACAO_MANUAL", "SUCESSO", "Afaturar",
                String.valueOf(idafaturar), instrumentoAnterior, resultado.getInstrumentoRegistro(), motivo,
                "reversao de classificacao manual (linha devolvida ao motor automatico)", user);
        return resultado;
    }

    // Processamento em lote (secao 71 do escopo): reprocessa de uma vez toda
    // producao ainda nao exportada que ja pertence a esta competencia ou cuja
    // data caia nela (idcompetencia ainda nulo - nunca foi classificada).
    // Idempotente, igual ao reprocessar individual.
    public Map<String, Object> processarCompetencia(int idcompetencia, AuthenticatedUser user) {
        return processarCompetencia(idcompetencia, user, null);
    }

    public Map<String, Object> processarCompetencia(int idcompetencia, AuthenticatedUser user, String unidade) {
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
        }
        long pendentesAntes = producaoRepository.contarSemCompetenciaPorCompetencia(competencia.getCompetencia(),
                unidade);
        int vinculados = 0;
        if (pendentesAntes > 0) {
            final int[] vinculadosHolder = new int[1];
            QuarkusTransaction.requiringNew().run(() -> vinculadosHolder[0] = producaoRepository.vincularSemCompetenciaPorCompetencia(idcompetencia,
                    competencia.getCompetencia(), unidade, user == null ? "" : user.matricula()));
            vinculados = vinculadosHolder[0];
            long pendentesDepois = producaoRepository.contarSemCompetenciaPorCompetencia(competencia.getCompetencia(),
                    unidade);
            auditService.registrar(unidade, "VINCULAR_PRODUCAO_COMPETENCIA", "SUCESSO", "FaturamentoCompetencia",
                    String.valueOf(idcompetencia), String.valueOf(pendentesAntes), String.valueOf(pendentesDepois),
                    null, "competencia=" + competencia.getCompetencia() + " vinculados=" + vinculados, user);
            // Antes o metodo retornava aqui. Com isso, bastava uma producao sem
            // competencia para a chamada inteira virar so vinculacao: nada era
            // reprocessado, as pendencias de classificacao continuavam na tela e o
            // frontend ja tinha marcado a competencia como processada. Segue direto
            // para o reprocessamento na mesma chamada.
        }
        // Passo novo: antes de reprocessar linha a linha, cruza a producao inteira da
        // competencia com o SIGTAP importado dela e ja resolve instrumento e status.
        // Isso tira do caminho tudo que so estava pendente por falta de dado do SIGTAP
        // (os valores sao copiados para afaturar quando a producao nasce, normalmente
        // antes da importacao) e deixa para revisao humana apenas o que realmente
        // depende de decisao: CNS, CBO, CNES e procedimento fora do SIGTAP.
        Map<String, Object> sincronizacao = sincronizarComSigtap(idcompetencia, competencia.getCompetencia(),
                unidade, user);

        List<Afaturar> candidatos = producaoRepository.listarParaReprocessar(idcompetencia, competencia.getCompetencia(),
                unidade);
        int processados = 0;
        int falhas = 0;
        for (Afaturar producao : candidatos) {
            boolean jaPertence = producao.getIdcompetencia() != null && producao.getIdcompetencia() == idcompetencia;
            boolean derivaParaEstaCompetencia = !jaPertence && competencia.getCompetencia().equals(derivarCompetencia(producao));
            if (!jaPertence && !derivaParaEstaCompetencia) {
                continue;
            }
            try {
                int idafaturar = producao.getIdafaturar();
                QuarkusTransaction.requiringNew().run(() -> reprocessarInterno(buscar(idafaturar), user));
                processados++;
            } catch (RuntimeException e) {
                falhas++;
                auditService.registrar(producao.getUnidade(), "REPROCESSAR_PRODUCAO_LOTE", "ERRO", "Afaturar",
                        String.valueOf(producao.getIdafaturar()), producao.getStatus(), null, e.getMessage(),
                        "competencia=" + competencia.getCompetencia(), user);
            }
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        int raasPreparadas = raasService.prepararRascunhosAutomaticos(competencia, unidade, user);
        resultado.put("analisados", candidatos.size());
        resultado.put("processados", processados);
        resultado.put("falhas", falhas);
        resultado.put("vinculados", vinculados);
        resultado.put("raasPreparadas", raasPreparadas);
        resultado.put("pendentesAntes", pendentesAntes);
        resultado.put("pendentesSemVinculacao",
                producaoRepository.contarSemCompetenciaPorCompetencia(competencia.getCompetencia(), unidade));
        resultado.putAll(sincronizacao);
        return resultado;
    }

    // Cruzamento producao x SIGTAP da competencia. Fica separado de
    // processarCompetencia para poder ser chamado sozinho e para deixar explicito o
    // caso em que nao deve rodar: sem SIGTAP importado para a competencia, marcar a
    // producao como pendente seria mentira - o que falta e a importacao.
    public Map<String, Object> sincronizarComSigtap(int idcompetencia, String competenciaAaaamm, String unidade,
            AuthenticatedUser user) {
        return sincronizarComSigtap(idcompetencia, competenciaAaaamm, unidade, user, 0);
    }

    // Com limite, para a preparacao em lote: ali este cruzamento roda uma vez por rodada
    // e, sem teto, reprocessava toda a producao ja vinculada a cada rodada - custo
    // quadratico que terminava em SocketTimeoutException no driver JDBC. Zero mantem a
    // varredura completa, que e o comportamento da acao "Cruzar com o SIGTAP".
    public Map<String, Object> sincronizarComSigtap(int idcompetencia, String competenciaAaaamm, String unidade,
            AuthenticatedUser user, int limite) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        long procedimentosSigtap = producaoRepository.contarSigtapDaCompetencia(competenciaAaaamm);
        resultado.put("sigtapDaCompetencia", procedimentosSigtap);

        if (procedimentosSigtap == 0) {
            resultado.put("sincronizadas", 0);
            resultado.put("semSigtap", 0);
            resultado.put("sigtapImportado", false);
            return resultado;
        }

        boolean cnesConfigurado = cnesConfigurado(unidade);
        String matricula = user == null ? "" : user.matricula();
        final int[] sincronizadas = new int[1];
        QuarkusTransaction.requiringNew().run(() -> sincronizadas[0] = producaoRepository
                .sincronizarComSigtapDaCompetencia(idcompetencia, competenciaAaaamm, unidade, cnesConfigurado,
                        matricula, limite));
        final int[] semSigtap = new int[1];
        QuarkusTransaction.requiringNew().run(() -> semSigtap[0] = producaoRepository
                .marcarSemSigtapComoPendente(idcompetencia, competenciaAaaamm, unidade, matricula, limite));

        auditService.registrar(unidade, "SINCRONIZAR_PRODUCAO_SIGTAP", "SUCESSO", "FaturamentoCompetencia",
                String.valueOf(idcompetencia), null, String.valueOf(sincronizadas[0]), null,
                "competencia=" + competenciaAaaamm + " sincronizadas=" + sincronizadas[0]
                        + " semSigtap=" + semSigtap[0] + " cnesConfigurado=" + cnesConfigurado,
                user);

        resultado.put("sincronizadas", sincronizadas[0]);
        resultado.put("semSigtap", semSigtap[0]);
        resultado.put("sigtapImportado", true);
        resultado.put("cnesConfigurado", cnesConfigurado);
        return resultado;
    }

    // CNES e configuracao da unidade (mesma regra do FaturamentoValidationService),
    // igual para todas as linhas: resolvido uma vez e passado para o UPDATE.
    private boolean cnesConfigurado(String unidade) {
        String sigla = texto(unidade);
        if (sigla.isBlank() || "TODAS".equalsIgnoreCase(sigla)) {
            return true;
        }
        return configuracaoUnidadeRepository.filtarPorUnidadeSigla(sigla).stream()
                .filter(c -> sigla.equalsIgnoreCase(texto(c.getSigla())))
                .anyMatch(c -> !texto(c.getCnesDoHospital()).isBlank());
    }

    // "Classificar todas" da tela de criticas.
    //
    // A primeira versao reprocessava produção a produção (classificarManualmente ->
    // reprocessarInterno: recarregar SIGTAP, revalidar, regravar criticas, auditar,
    // cada uma em transacao propria). Isso estourava o statement_timeout de 60s ja
    // em um lote de 100 e a tela ficava parada em "Enviando o primeiro lote".
    //
    // Agora a classificacao e um UPDATE em massa com a mesma regra de
    // classificarInstrumento (ver classificarPendentesEmMassa). O tradeoff e
    // explicito: nao revalida cada producao neste momento - o status sai pelo mesmo
    // criterio do motor (critica aberta -> PRODUCAO_COM_CRITICA, senao
    // APTO_FATURAMENTO) e o proximo reprocessamento revalida e corrige. Para
    // classificar uma producao especifica com revalidacao imediata continua existindo
    // classificarManualmente (botao "Classificar" da linha).
    public Map<String, Object> classificarPendentesDaCompetencia(int idcompetencia, AuthenticatedUser user,
            String unidade, int limite) {
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
        }
        long pendentesAntes = producaoRepository.contarPendentesDeClassificacao(idcompetencia, unidade);
        final int[] classificadasHolder = new int[1];
        QuarkusTransaction.requiringNew().run(() -> classificadasHolder[0] = producaoRepository
                .classificarPendentesEmMassa(idcompetencia, unidade, limite,
                        user == null ? "" : user.matricula()));
        int classificadas = classificadasHolder[0];

        auditService.registrar(unidade, "CLASSIFICAR_PENDENTES_LOTE", "SUCESSO", "FaturamentoCompetencia",
                String.valueOf(idcompetencia), String.valueOf(pendentesAntes), String.valueOf(classificadas), null,
                "competencia=" + competencia.getCompetencia() + " classificadas=" + classificadas, user);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("pendentes", pendentesAntes);
        resultado.put("classificadas", classificadas);
        resultado.put("falhas", List.of());
        // Quantas continuam pendentes depois desta rodada. Enquanto for > 0 e houver
        // progresso, o operador (ou o frontend) repete a chamada.
        resultado.put("restantes", producaoRepository.contarPendentesDeClassificacao(idcompetencia, unidade));
        return resultado;
    }

    // Ponto de entrada idempotente para producao originada de um modulo
    // assistencial: chama-lo duas vezes para a mesma (origemModulo, origemId) nao
    // duplica registro, apenas reprocessa (secao 14/48 do escopo). Nenhum modulo
    // assistencial chama isto automaticamente ainda nesta Fase 1 (nao ha mecanismo
    // de evento de dominio no projeto hoje) - fica pronto para ser ligado nas
    // fases seguintes.
    @Transactional
    public Afaturar processarOrigem(String origemModulo, int origemId, AfaturarDto dados, AuthenticatedUser user) {
        if (dados == null || dados.getProcedimento() == null || dados.getProcedimento().isBlank()) {
            throw new IllegalArgumentException("Procedimento e obrigatorio.");
        }
        Afaturar producao = producaoRepository.buscarPorOrigem(origemModulo, origemId);
        boolean novo = producao == null;
        if (novo) {
            producao = new Afaturar();
        }
        aplicarDados(producao, dados);
        producao.setOrigemModulo(origemModulo);
        producao.setOrigemId(origemId);
        if (novo) {
            producao.persist();
        }
        return reprocessarInterno(producao, user);
    }

    private Afaturar reprocessarInterno(Afaturar producao, AuthenticatedUser user) {
        String statusAnterior = producao.getStatus();
        ResultadoMotor resultado = aplicarMotor(producao, true);
        carimbarAutoria(producao, user);

        auditService.registrar(producao.getUnidade(), "REPROCESSAR_PRODUCAO", "SUCESSO", "Afaturar",
                String.valueOf(producao.getIdafaturar()), statusAnterior, producao.getStatus(), null,
                "instrumento=" + resultado.instrumento() + " criticas=" + resultado.possuiCritica(), user);
        return producao;
    }

    // O que o motor decidiu sobre a producao, para quem precisa auditar o resultado.
    private record ResultadoMotor(InstrumentoRegistro instrumento, boolean possuiCritica) {
    }

    // Miolo do motor, sem auditoria e sem carimbo de autoria: vincula a competencia,
    // rele SIGTAP e cadastros, classifica o instrumento e valida. Fica separado de
    // reprocessarInterno porque a producao que acaba de chegar de um modulo
    // assistencial ainda nao tem id (auditar por id nao faria sentido) e precisa
    // exatamente deste mesmo tratamento - ver prepararProducaoNova.
    private ResultadoMotor aplicarMotor(Afaturar producao, boolean criticarCompetencia) {
        String competenciaAaaamm = derivarCompetencia(producao);
        FaturamentoCompetencia competencia = competenciaAaaamm == null ? null
                : competenciaRepository.buscarPorCompetenciaUnidade(competenciaAaaamm, producao.getUnidade());
        producao.setIdcompetencia(competencia == null ? null : competencia.getIdcompetencia());
        recarregarSigtapLocal(producao, competenciaAaaamm);
        recarregarCnsDoCadastro(producao);
        recarregarProfissionalDoCadastro(producao);

        InstrumentoRegistro instrumento;
        if (Boolean.TRUE.equals(producao.getClassificacaoManual()) && producao.getInstrumentoRegistro() != null) {
            instrumento = InstrumentoRegistro.valueOf(producao.getInstrumentoRegistro());
        } else {
            instrumento = classificarInstrumento(producao);
            producao.setInstrumentoRegistro(instrumento.name());
        }

        List<ValidacaoResultado> resultados = validationService.validar(producao);

        boolean possuiErro = false;
        boolean possuiAlerta = false;
        List<String> pendencias = new ArrayList<>();
        for (ValidacaoResultado resultado : resultados) {
            // Na porta de entrada a competencia ainda nao aberta nao e erro do
            // lancamento e sim workflow administrativo (a etapa 2 da esteira abre a
            // competencia e a 3 vincula a producao). Sem esta excecao, todo lancamento
            // feito antes de alguem abrir a competencia do mes nasceria em critica. Nao
            // ha risco em nao criticar aqui: a remessa e montada por competencia, entao
            // producao sem idcompetencia nunca chega na exportacao.
            if (!criticarCompetencia && "idcompetencia".equals(resultado.campo())) {
                continue;
            }
            if (resultado.resultado() == ResultadoValidacao.ERRO) {
                possuiErro = true;
                pendencias.add(resultado.codigo() + " - " + resultado.mensagem());
                String orientacao = orientacaoParaCorrigir(resultado, producao);
                if (!orientacao.isBlank()) {
                    pendencias.add(orientacao);
                }
            } else if (resultado.resultado() == ResultadoValidacao.ALERTA) {
                possuiAlerta = true;
                pendencias.add(resultado.codigo() + " - " + resultado.mensagem());
                String orientacao = orientacaoParaCorrigir(resultado, producao);
                if (!orientacao.isBlank()) {
                    pendencias.add(orientacao);
                }
            }
        }

        producao.setStatus(statusPara(instrumento, possuiErro, possuiAlerta));
        producao.setPossuiCritica(possuiErro || possuiAlerta);
        producao.setMotivoStatus(pendencias.stream().distinct().reduce((a, b) -> a + "; " + b).orElse(""));
        return new ResultadoMotor(instrumento, possuiErro || possuiAlerta);
    }

    // Regra de status num lugar so: o instrumento manda quando e terminal (pendente de
    // classificacao / nao faturavel); no resto quem decide e a validacao.
    static String statusPara(InstrumentoRegistro instrumento, boolean possuiErro, boolean possuiAlerta) {
        if (instrumento == InstrumentoRegistro.PENDENTE_CLASSIFICACAO) {
            return ProducaoStatus.PENDENTE_CLASSIFICACAO.name();
        }
        if (instrumento == InstrumentoRegistro.NAO_FATURAVEL) {
            return ProducaoStatus.NAO_FATURAVEL.name();
        }
        if (possuiErro) {
            return ProducaoStatus.PRODUCAO_COM_CRITICA.name();
        }
        if (possuiAlerta) {
            return ProducaoStatus.REVISAO_RECOMENDADA.name();
        }
        return ProducaoStatus.APTO_FATURAMENTO.name();
    }

    // Publico pelo mesmo motivo de enriquecerComPaciente: a conta hospitalar monta o
    // motivo da critica sem passar pelo reprocessamento, e sem esta orientacao ela
    // mostrava "CNS do paciente nao informado" sem dizer que o que trava a busca
    // automatica e o CPF ausente no cadastro.
    public String orientacaoParaCorrigir(ValidacaoResultado resultado, Afaturar producao) {
        if (resultado == null || producao == null) {
            return "";
        }
        String codigo = texto(resultado.codigo());
        if (!"CNS_PACIENTE_AUSENTE".equals(codigo) && !"CNS_PACIENTE_INVALIDO".equals(codigo)) {
            return "";
        }
        if (producao.getIdpaciente() <= 0) {
            return "Para buscar o CNS automaticamente, falta vincular esta producao ao cadastro do paciente.";
        }
        Pacientes paciente = Pacientes.findById(producao.getIdpaciente());
        String cpf = paciente == null ? "" : texto(paciente.getCpf());
        String nascimentoCadastro = paciente == null ? "" : texto(paciente.getNascimento());
        String nascimentoProducao = texto(producao.getDtnascimento());
        List<String> faltando = new ArrayList<>();
        if (!CpfUtils.valido(cpf)) {
            faltando.add("CPF valido");
        }
        if (ConsultaCnsService.formatarData(nascimentoCadastro) == null
                && ConsultaCnsService.formatarData(nascimentoProducao) == null) {
            faltando.add("nascimento");
        }
        if (!faltando.isEmpty()) {
            return "Para buscar o CNS automaticamente, falta " + String.join(" e ", faltando)
                    + " no cadastro do paciente.";
        }
        return "CPF e nascimento estao disponiveis: use Buscar CNS para consultar o CADSUS/CNES e reprocessar.";
    }

    private void carimbarAutoria(Afaturar producao, AuthenticatedUser user) {
        LocalDateTime agora = LocalDateTime.now();
        if (producao.getDatacriacao() == null) {
            producao.setDatacriacao(agora);
        }
        producao.setDataalteracao(agora);
        if (producao.getUsuariocriador() == null || producao.getUsuariocriador().isBlank()) {
            producao.setUsuariocriador(user == null ? "" : user.matricula());
        }
        producao.setUsuarioalterador(user == null ? "" : user.matricula());
    }

    // Porta de entrada da producao que nasce nos modulos assistenciais (POST /afaturar,
    // chamado por triagem, atendimento medico, exames, medicacao, odontologia, centro
    // cirurgico...).
    //
    // Antes daqui a linha era gravada com o instrumento classificado e status
    // APTO_FATURAMENTO sem nunca ter passado pelo motor: sem competencia vinculada, sem
    // reler o SIGTAP e sem validacao nenhuma. Producao sem CNS, sem CBO, sem CNES e sem
    // competencia nascia "apta" - e como a exportacao seleciona apto + nao exportado,
    // uma competencia exportada sem passar pela etapa "preparar producao" da esteira
    // levava para a remessa linha que nunca foi validada.
    //
    // A producao ainda nao esta persistida quando este metodo roda: aqui so ha leitura
    // do banco e quem chama persiste depois. Por isso a falha do motor nao pode derrubar
    // a gravacao - producao perdida e receita perdida, e o operador nem fica sabendo (a
    // tela assistencial so escreve no console do navegador). Se o motor falhar, a linha
    // e gravada com a classificacao antiga e o proximo "preparar producao" da esteira a
    // corrige.
    public Afaturar prepararProducaoNova(Afaturar producao, AuthenticatedUser user) {
        if (producao == null) {
            throw new IllegalArgumentException("Producao nao informada.");
        }
        normalizarFlags(producao);
        try {
            aplicarMotor(producao, false);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Falha ao validar producao na entrada (procedimento=%s unidade=%s). "
                    + "Gravando sem validacao; a esteira revalida em preparar producao.",
                    producao.getProcedimento(), producao.getUnidade());
            classificarSemValidar(producao);
        }
        carimbarAutoria(producao, user);
        return producao;
    }

    // As flags precisam nascer com false explicito. O DEFAULT FALSE da migration so
    // vale para INSERT que omite a coluna, e o Hibernate sempre a envia - sem isto a
    // producao nasce com exportado NULL e as consultas de lote ("exportado = false")
    // nunca a enxergam: nao e reprocessada, nao entra na exportacao BPA e fica presa no
    // status em que foi gravada.
    private void normalizarFlags(Afaturar producao) {
        if (producao.getExportado() == null) {
            producao.setExportado(false);
        }
        if (producao.getFaturado() == null) {
            producao.setFaturado(false);
        }
        if (producao.getPossuiCritica() == null) {
            producao.setPossuiCritica(false);
        }
        if (producao.getClassificacaoManual() == null) {
            producao.setClassificacaoManual(false);
        }
    }

    // Rede de seguranca: a mesma classificacao que valia antes de o motor assumir a
    // porta de entrada, usada so quando a validacao falha.
    private void classificarSemValidar(Afaturar producao) {
        InstrumentoRegistro instrumento = classificarInstrumento(producao);
        producao.setInstrumentoRegistro(instrumento.name());
        producao.setStatus(statusPara(instrumento, false, false));
    }

    // Puxa o CNS do cadastro do paciente para a producao que esta em critica por CNS.
    //
    // O CNS e copiado para afaturar no momento em que a producao e gerada. Quando o
    // cadastro e corrigido depois (ou quando a copia pegou lixo do tipo 000000000000000),
    // a producao fica com o valor velho e cai em critica - mesmo existindo um CNS valido
    // no cadastro do paciente.
    //
    // Desde que reprocessarInterno passou a reler o cadastro (recarregarCnsDoCadastro),
    // qualquer revalidacao ja faz esta correcao. Esta acao continua porque e dirigida: nao
    // pede para revalidar a competencia inteira e devolve o placar do que o cadastro
    // resolveu e do que sobrou para o CADSUS.
    //
    // A regra e conservadora de proposito: so grava se o CNS do cadastro passar no
    // modulo 11. Nunca inventa numero, nunca sobrescreve um CNS valido por um invalido, e
    // nao mexe em producao que ja esta apta.
    @Transactional
    public Map<String, Object> sincronizarCnsDoCadastro(Integer idcompetencia, String unidade,
            AuthenticatedUser user) {
        int corrigidas = 0;
        int semCnsValidoNoCadastro = 0;
        int semPacienteVinculado = 0;

        List<Afaturar> alvos = new java.util.ArrayList<>();
        int pagina = 0;
        while (pagina < MAX_PAGINAS_MOTIVOS) {
            List<Afaturar> lote = producaoRepository.listarPaginado(idcompetencia, unidade,
                    ProducaoStatus.PRODUCAO_COM_CRITICA.name(), null, null, pagina, TAMANHO_PAGINA_MOTIVOS);
            if (lote.isEmpty()) {
                break;
            }
            alvos.addAll(lote);
            if (lote.size() < TAMANHO_PAGINA_MOTIVOS) {
                break;
            }
            pagina++;
        }

        for (Afaturar producao : alvos) {
            String cnsAtual = texto(producao.getCnsdopciente());
            if (CnsUtils.valido(cnsAtual)) {
                continue;
            }
            if (producao.getIdpaciente() <= 0) {
                semPacienteVinculado++;
                continue;
            }
            Pacientes paciente = Pacientes.findById(producao.getIdpaciente());
            String cnsCadastro = paciente == null ? "" : texto(paciente.getCns());
            if (!CnsUtils.valido(cnsCadastro)) {
                semCnsValidoNoCadastro++;
                continue;
            }
            producao.setCnsdopciente(cnsCadastro);
            reprocessarInterno(producao, user);
            corrigidas++;
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("analisadas", alvos.size());
        resultado.put("corrigidas", corrigidas);
        resultado.put("semCnsValidoNoCadastro", semCnsValidoNoCadastro);
        resultado.put("semPacienteVinculado", semPacienteVinculado);
        return resultado;
    }

    // Grava o CNS informado pelo operador e destrava a producao daquele paciente.
    //
    // O caminho anterior era exportar a lista e pedir correcao no cadastro por fora. Isso
    // tira o operador do fechamento e depende de outra pessoa voltar para avisar. Aqui a
    // correcao acontece no mesmo lugar: valida o numero, grava no cadastro do paciente
    // (para nao voltar no mes seguinte), aplica em todas as linhas dele na competencia e
    // revalida - a linha sai de critica na hora.
    @Transactional
    public Map<String, Object> corrigirCnsDoPaciente(int idpaciente, String cnsInformado, Integer idcompetencia,
            String unidade, AuthenticatedUser user) {
        String cns = CnsUtils.somenteDigitos(cnsInformado);
        if (!CnsUtils.valido(cns)) {
            throw new IllegalArgumentException(CnsUtils.motivoInvalido(cns));
        }
        if (idpaciente <= 0) {
            throw new IllegalArgumentException("Paciente nao identificado na producao.");
        }

        // Grava no cadastro: sem isso a producao do mes que vem nasce com o erro de novo.
        Pacientes paciente = Pacientes.findById(idpaciente);
        if (paciente != null) {
            paciente.setCns(cns);
        }

        int linhasAtualizadas = 0;
        int aptasAgora = 0;
        int pagina = 0;
        while (pagina < MAX_PAGINAS_MOTIVOS) {
            List<Afaturar> lote = producaoRepository.listarPaginado(idcompetencia, unidade,
                    ProducaoStatus.PRODUCAO_COM_CRITICA.name(), null, null, pagina, TAMANHO_PAGINA_MOTIVOS);
            if (lote.isEmpty()) {
                break;
            }
            for (Afaturar producao : lote) {
                if (producao.getIdpaciente() != idpaciente) {
                    continue;
                }
                producao.setCnsdopciente(cns);
                reprocessarInterno(producao, user);
                linhasAtualizadas++;
                if (ProducaoStatus.APTO_FATURAMENTO.name().equals(producao.getStatus())) {
                    aptasAgora++;
                }
            }
            if (lote.size() < TAMANHO_PAGINA_MOTIVOS) {
                break;
            }
            pagina++;
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("cns", cns);
        resultado.put("linhasAtualizadas", linhasAtualizadas);
        resultado.put("aptasAgora", aptasAgora);
        resultado.put("cadastroAtualizado", paciente != null);
        return resultado;
    }

    public Map<String, Object> atualizarDadosPacienteEBuscarCns(int idafaturar, String cpfInformado,
            String nascimentoInformado, String unidade, AuthenticatedUser user) {
        Afaturar producao = buscar(idafaturar, unidade);
        if (producao.getIdpaciente() <= 0) {
            throw new IllegalArgumentException("Producao sem paciente vinculado para buscar CNS.");
        }

        String cpf = texto(cpfInformado);
        String nascimento = texto(nascimentoInformado);
        if (nascimento.isBlank()) {
            throw new IllegalArgumentException("Informe o nascimento do paciente para buscar o CNS.");
        }
        int idpaciente = producao.getIdpaciente();

        // Sem CPF so vale tentar pela API oficial do CADSUS (Nome + Nascimento) - mesmo
        // caminho ja usado para RN sem documento no lote (ver buscarCnsNoCnes). O scraping
        // do CNES exige CPF e nem seria chamado; sem o toggle da unidade ligado nao ha o
        // que tentar.
        boolean buscaPorNome = cpf.isBlank();
        String nomePaciente = "";
        if (buscaPorNome) {
            if (!configuracaoCnsPdqService.ativo(unidade)) {
                throw new IllegalArgumentException(
                        "Sem CPF, so e possivel buscar por Nome com a consulta oficial ao CADSUS ativada para a unidade.");
            }
            Pacientes pacienteAtual = Pacientes.findById(idpaciente);
            nomePaciente = pacienteAtual == null ? "" : texto(pacienteAtual.getNome());
            if (nomePaciente.isBlank()) {
                throw new IllegalArgumentException("Paciente sem nome cadastrado - nao ha o que buscar por Nome.");
            }
        }
        final boolean buscaPorNomeFinal = buscaPorNome;
        final String nomeParaConsulta = nomePaciente;

        QuarkusTransaction.requiringNew().run(() -> {
            Pacientes paciente = Pacientes.findById(idpaciente);
            if (paciente == null) {
                throw new IllegalArgumentException("Paciente nao encontrado para o id: " + idpaciente);
            }
            String anterior = texto(paciente.getCpf()) + " | " + texto(paciente.getNascimento());
            if (!buscaPorNomeFinal) {
                paciente.setCpf(cpf);
            }
            paciente.setNascimento(nascimento);
            String depois = texto(paciente.getCpf()) + " | " + nascimento;
            auditService.registrar(producao.getUnidade(), "CORRIGIR_DADOS_PACIENTE_CNS", "SUCESSO", "Pacientes",
                    String.valueOf(idpaciente), anterior, depois, null,
                    "origem=faturamento-producao idafaturar=" + idafaturar, user);
        });

        ConsultaCnsDto resposta = buscaPorNome
                ? consultaCnsService.consultarPorNome(nomeParaConsulta, nascimento, unidade)
                : consultaCnsService.consultar(cpf, nascimento, unidade);
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("idpaciente", idpaciente);
        resultado.put("cpf", cpf);
        resultado.put("nascimento", nascimento);
        resultado.put("buscaPorNome", buscaPorNome);
        resultado.put("encontrado", resposta != null && resposta.encontrado());
        resultado.put("mensagens", resposta == null ? List.of() : resposta.mensagens());

        String cnsEncontrado = resposta == null ? "" : texto(resposta.cns());
        if (resposta == null || !resposta.encontrado() || !CnsUtils.valido(cnsEncontrado)) {
            resultado.put("cns", cnsEncontrado);
            resultado.put("cadastroAtualizado", true);
            resultado.put("linhasAtualizadas", 0);
            resultado.put("aptasAgora", 0);
            return resultado;
        }

        // Achou por Nome e o CADSUS trouxe CPF junto: grava so se o cadastro continuar sem
        // CPF - mesma regra do lote (buscarCnsNoCnes), nunca sobrescreve o que ja existe.
        if (buscaPorNome) {
            String cpfEncontrado = resposta.cpf() == null ? "" : resposta.cpf().replaceAll("[^0-9]", "");
            if (CpfUtils.valido(cpfEncontrado)) {
                QuarkusTransaction.requiringNew().run(() -> {
                    Pacientes alvo = Pacientes.findById(idpaciente);
                    if (alvo == null || !texto(alvo.getCpf()).isBlank()) {
                        return;
                    }
                    alvo.setCpf(cpfEncontrado);
                    auditService.registrar(unidade, "CORRIGIR_DADOS_PACIENTE_CNS", "SUCESSO", "Pacientes",
                            String.valueOf(idpaciente), "", cpfEncontrado, null,
                            "origem=faturamento-producao-nome idafaturar=" + idafaturar, user);
                });
            }
        }

        Map<String, Object> aplicado = QuarkusTransaction.requiringNew()
                .call(() -> corrigirCnsDoPaciente(idpaciente, cnsEncontrado, producao.getIdcompetencia(), unidade, user));
        resultado.putAll(aplicado);
        resultado.put("cns", cnsEncontrado);
        resultado.put("encontrado", true);
        resultado.put("cadastroAtualizado", true);
        return resultado;
    }

    // Teto por chamada. Cada paciente custa tres requisicoes ao CNES e ate 15s de timeout,
    // entao um lote sem limite passaria de meia hora e morreria no proxy antes de gravar o
    // placar. O que sobrar volta no proximo clique - e o resultado positivo fica em cache,
    // entao repetir nao refaz o que ja deu certo.
    private static final int MAX_CONSULTAS_CNES = 100;

    // O passo seguinte ao sincronizarCnsDoCadastro: quando nem o cadastro tem CNS valido,
    // busca no cadastro publico do CNES por CPF + data de nascimento (o mesmo caminho que
    // a recepcao usa) e grava o numero encontrado.
    //
    // Fora de transacao de proposito: sao chamadas HTTP a um servidor de terceiro, e uma
    // transacao aberta durante elas ficaria ociosa muito alem do idle_in_transaction
    // _session_timeout do datasource. Cada paciente corrigido abre a sua (requiringNew).
    //
    // Reaproveita corrigirCnsDoPaciente inteiro: grava no cadastro (para o mes que vem nao
    // nascer com o mesmo erro), aplica em todas as linhas do paciente na competencia e
    // revalida. Aqui so muda de onde vem o numero - do CNES, e nao da mao do operador.
    //
    // CNES fora do ar interrompe o lote em vez de marcar todo mundo como nao localizado:
    // gravar "nao encontrado" para 30 pacientes por causa de uma indisponibilidade daria
    // ao operador uma conclusao falsa sobre o cadastro deles.
    public Map<String, Object> buscarCnsNoCnes(Integer idcompetencia, String unidade, AuthenticatedUser user) {
        // Passo 1, aqui dentro: o cadastro do paciente. E de graca (nao sai da base) e
        // resolve a fatia da critica em que o CNS certo ja existe no cadastro e so a copia
        // gravada na producao ficou velha. Rodar antes evita perguntar ao CNES por
        // paciente que nao precisava - cada consulta custa tres requisicoes e ate 15s.
        //
        // Via requiringNew porque sincronizarCnsDoCadastro e @Transactional e este metodo
        // roda fora de transacao: em chamada direta o interceptor nao se aplicaria.
        Map<String, Object> cadastro = QuarkusTransaction.requiringNew()
                .call(() -> sincronizarCnsDoCadastro(idcompetencia, unidade, user));

        // Passo 2: quem o cadastro nao resolveu vai para o CNES por CPF + data de
        // nascimento - o mesmo caminho que a /recepcao usa para preencher o CNS sem
        // digitacao (ConsultaCnsController).
        List<Map<String, Object>> pacientes = listarPacientesSemCnsValido(idcompetencia, unidade);

        int consultados = 0;
        int corrigidos = 0;
        int linhasAtualizadas = 0;
        int semCpfOuNascimento = 0;
        int semPacienteVinculado = 0;
        int cpfInvalido = 0;
        int nascimentoInvalido = 0;
        int naoLocalizados = 0;
        // Quantos desses "consultados" foram RN sem CPF, via API oficial (Nome+Nascimento)
        // em vez do caminho normal por CPF - visibilidade separada porque e um caminho
        // novo, mais sensivel a falso-negativo (nome no CADSUS pode nao bater com o
        // temporario do GHBAM) do que a busca por CPF.
        int rnConsultadosPorNome = 0;
        String indisponivel = null;

        for (Map<String, Object> item : pacientes) {
            if (consultados >= MAX_CONSULTAS_CNES) {
                break;
            }
            Object id = item.get("idpaciente");
            int idpaciente = id instanceof Number valor ? valor.intValue() : 0;
            if (idpaciente <= 0) {
                semPacienteVinculado++;
                continue;
            }
            Pacientes paciente = Pacientes.findById(idpaciente);
            String cpf = paciente == null ? "" : texto(paciente.getCpf());
            String nascimentoCadastro = paciente == null ? "" : texto(paciente.getNascimento());
            // A producao carrega a data de nascimento do atendimento. Quando o cadastro
            // esta sem ela, o modal de edicao cai para esse valor e acha o CNS - o lote
            // nao caia, contava "sem CPF/nascimento" e pulava. Mesma fonte, mesmo
            // resultado: quem funciona de um em um passa a funcionar no lote.
            String nascimentoProducao = texto((String) item.get("nascimentoProducao"));
            if (nascimentoCadastro.isBlank() && nascimentoProducao.isBlank()) {
                // Sem nascimento nao ha o que perguntar nem ao CNES nem ao CADSUS oficial.
                semCpfOuNascimento++;
                continue;
            }
            // Cadastro primeiro; producao quando o cadastro nao tem data ou tem uma que
            // nao da para consultar (formato que o CNES/CADSUS nao aceita).
            String nascimentoConsulta = ConsultaCnsService.formatarData(nascimentoCadastro);
            boolean nascimentoVeioDaProducao = false;
            if (nascimentoConsulta == null) {
                nascimentoConsulta = ConsultaCnsService.formatarData(nascimentoProducao);
                nascimentoVeioDaProducao = nascimentoConsulta != null;
            }
            if (nascimentoConsulta == null) {
                nascimentoInvalido++;
                continue;
            }

            ConsultaCnsDto resposta;
            if (!cpf.isBlank()) {
                String cpfConsulta = CpfUtils.valido(cpf) ? ConsultaCnsService.formatarCpf(cpf) : null;
                if (cpfConsulta == null) {
                    // CPF com digito que nao fecha (ou tamanho errado) nao tem o que
                    // perguntar ao CNES: conta no placar e o lote segue para o proximo.
                    cpfInvalido++;
                    continue;
                }
                try {
                    resposta = consultaCnsService.consultar(cpf, nascimentoConsulta, unidade);
                    consultados++;
                } catch (RuntimeException e) {
                    indisponivel = e.getMessage();
                    break;
                }
            } else {
                // Sem CPF e o caso classico do recem-nascido: nome temporario ("RN DE
                // MARIA...") e nascimento, nada mais. O scraping do CNES exige CPF e nao
                // teria o que fazer aqui; so a API oficial do PDQ aceita buscar por Nome +
                // Data de nascimento - e e o unico jeito de achar o CNS PROVISORIO (comeca
                // com 7/8/9) que a maternidade ja tenha cadastrado no CADSUS. So tenta com
                // o toggle global ligado (ConfiguracaoCnsPdqService); desligado, o
                // comportamento e identico ao de antes - conta e pula.
                String nomePaciente = paciente == null ? "" : texto(paciente.getNome());
                if (!configuracaoCnsPdqService.ativo(unidade) || nomePaciente.isBlank()) {
                    semCpfOuNascimento++;
                    continue;
                }
                resposta = consultaCnsService.consultarPorNome(nomePaciente, nascimentoConsulta, unidade);
                consultados++;
                rnConsultadosPorNome++;
            }

            String cnsEncontrado = resposta == null ? "" : texto(resposta.cns());
            if (resposta == null || !resposta.encontrado() || !CnsUtils.valido(cnsEncontrado)) {
                naoLocalizados++;
                continue;
            }

            final int idPacienteCorrigir = idpaciente;
            // Quando a busca foi por Nome (paciente sem CPF no cadastro) e a resposta do
            // CADSUS trouxe um CPF junto, grava tambem - so quando o campo esta vazio,
            // nunca sobrescreve um CPF que ja exista. Sem isso o paciente ficava com CNS
            // certo mas seguia "sem CPF" todo mes, ate alguem digitar na mao.
            String cpfEncontrado = resposta.cpf() == null ? "" : resposta.cpf().replaceAll("[^0-9]", "");
            if (cpf.isBlank() && CpfUtils.valido(cpfEncontrado)) {
                final String cpfGravar = cpfEncontrado;
                QuarkusTransaction.requiringNew().run(() -> {
                    Pacientes alvo = Pacientes.findById(idPacienteCorrigir);
                    if (alvo == null || !texto(alvo.getCpf()).isBlank()) {
                        return;
                    }
                    alvo.setCpf(cpfGravar);
                    auditService.registrar(unidade, "CORRIGIR_DADOS_PACIENTE_CNS", "SUCESSO", "Pacientes",
                            String.valueOf(idPacienteCorrigir), "", cpfGravar, null,
                            "origem=faturamento-producao-lote-cns-cpf", user);
                });
            }
            // A data que destravou a consulta volta para o cadastro, como o modal ja fazia:
            // sem isso o mes que vem nasce com a mesma critica e o mesmo trabalho.
            if (nascimentoVeioDaProducao) {
                final String nascimentoGravar = nascimentoConsulta;
                QuarkusTransaction.requiringNew().run(() -> {
                    Pacientes alvo = Pacientes.findById(idPacienteCorrigir);
                    if (alvo == null) {
                        return;
                    }
                    String anterior = texto(alvo.getNascimento());
                    alvo.setNascimento(nascimentoGravar);
                    auditService.registrar(unidade, "CORRIGIR_DADOS_PACIENTE_CNS", "SUCESSO", "Pacientes",
                            String.valueOf(idPacienteCorrigir), anterior, nascimentoGravar, null,
                            "origem=faturamento-producao-lote-cns", user);
                });
            }
            Map<String, Object> aplicado = QuarkusTransaction.requiringNew().call(
                    () -> corrigirCnsDoPaciente(idPacienteCorrigir, cnsEncontrado, idcompetencia, unidade, user));
            corrigidos++;
            Object linhas = aplicado.get("linhasAtualizadas");
            linhasAtualizadas += linhas instanceof Integer valor ? valor : 0;
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        // O que o cadastro do paciente resolveu antes de precisar do CNES.
        resultado.put("corrigidasPeloCadastro", cadastro.getOrDefault("corrigidas", 0));
        resultado.put("pacientes", pacientes.size());
        resultado.put("consultados", consultados);
        resultado.put("rnConsultadosPorNome", rnConsultadosPorNome);
        resultado.put("corrigidos", corrigidos);
        resultado.put("linhasAtualizadas", linhasAtualizadas);
        resultado.put("semCpfOuNascimento", semCpfOuNascimento);
        resultado.put("semPacienteVinculado", semPacienteVinculado);
        resultado.put("cpfInvalido", cpfInvalido);
        resultado.put("nascimentoInvalido", nascimentoInvalido);
        resultado.put("naoLocalizados", naoLocalizados);
        resultado.put("jaTentados", 0);
        // Quantos ficaram sem consulta: o teto por chamada, ou a parada por CNES fora.
        // naoLocalizados NAO entra na subtracao - quem nao foi localizado ja foi contado
        // em consultados, e descontar duas vezes zerava este numero. Era isso que fazia a
        // tela concluir "acabou" com metade da fila ainda por perguntar.
        resultado.put("naoConsultados",
                Math.max(0, pacientes.size() - consultados - semCpfOuNascimento - semPacienteVinculado
                        - cpfInvalido - nascimentoInvalido));
        resultado.put("indisponivel", indisponivel == null ? "" : indisponivel);
        return resultado;
    }

    // Busca do CNS de recem-nascido sem CPF (Nome + Data de nascimento, via API oficial do
    // PDQ) mora agora em ConsultaCnsService#consultarPorNome - o mesmo caminho passou a
    // servir tambem a /recepcao (emergencia sem documento), entao ficou compartilhado em
    // vez de duplicado aqui.

    // Teto por chamada, menor que o de pacientes: o quadro de profissionais de uma
    // unidade e uma fracao do de pacientes atendidos no mes.
    private static final int MAX_CONSULTAS_CNES_PROFISSIONAL = 50;

    // Mesma ideia de buscarCnsNoCnes, mas para o cadastro do profissional: servidor ativo
    // da unidade, sem CNS valido, mas com CPF e nascimento cadastrados. Depois que o
    // cadastro do servidor tem CNS, recarregarProfissionalDoCadastro ja propaga sozinho
    // para toda producao existente e futura - nao precisa reprocessar nada aqui.
    public Map<String, Object> buscarCnsDeProfissionaisNoCnes(String unidade, AuthenticatedUser user) {
        String unidadeFiltro = unidade == null ? "" : unidade.trim().toUpperCase();
        List<br.com.iv.entity.Servidor> servidores = br.com.iv.entity.Servidor
                .find("upper(unidade) = ?1 and ativo = true", unidadeFiltro).list();

        int consultados = 0;
        int corrigidos = 0;
        int semCpfOuNascimento = 0;
        int cpfInvalido = 0;
        int naoLocalizados = 0;
        String indisponivel = null;

        for (br.com.iv.entity.Servidor servidor : servidores) {
            if (CnsUtils.valido(texto(servidor.getCns()))) {
                continue;
            }
            if (consultados >= MAX_CONSULTAS_CNES_PROFISSIONAL) {
                break;
            }
            String cpf = texto(servidor.getCpf());
            String nascimento = texto(servidor.getNascimento());
            if (cpf.isBlank() || nascimento.isBlank()) {
                semCpfOuNascimento++;
                continue;
            }
            if (!CpfUtils.valido(cpf)) {
                cpfInvalido++;
                continue;
            }
            String nascimentoConsulta = ConsultaCnsService.formatarData(nascimento);
            if (nascimentoConsulta == null) {
                semCpfOuNascimento++;
                continue;
            }

            ConsultaCnsDto resposta;
            try {
                resposta = consultaCnsService.consultar(cpf, nascimentoConsulta, unidade);
                consultados++;
            } catch (RuntimeException e) {
                indisponivel = e.getMessage();
                break;
            }

            String cnsEncontrado = resposta == null ? "" : texto(resposta.cns());
            if (resposta == null || !resposta.encontrado() || !CnsUtils.valido(cnsEncontrado)) {
                naoLocalizados++;
                continue;
            }

            final int idservidor = servidor.getIdservidor();
            final String cnsGravar = cnsEncontrado;
            QuarkusTransaction.requiringNew().run(() -> {
                br.com.iv.entity.Servidor alvo = br.com.iv.entity.Servidor.findById(idservidor);
                if (alvo == null || CnsUtils.valido(texto(alvo.getCns()))) {
                    return;
                }
                alvo.setCns(cnsGravar);
                auditService.registrar(unidade, "COMPLETAR_CNS_SERVIDOR", "SUCESSO", "Servidor",
                        String.valueOf(idservidor), "", cnsGravar, null, "origem=faturamento-automatico", user);
            });
            corrigidos++;
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("servidores", servidores.size());
        resultado.put("consultados", consultados);
        resultado.put("corrigidos", corrigidos);
        resultado.put("semCpfOuNascimento", semCpfOuNascimento);
        resultado.put("cpfInvalido", cpfInvalido);
        resultado.put("naoLocalizados", naoLocalizados);
        resultado.put("indisponivel", indisponivel == null ? "" : indisponivel);
        return resultado;
    }

    // Passo seguinte a buscarCnsDeProfissionaisNoCnes: alem de corrigir o cadastro do
    // servidor no CNES, propaga o CNS certo para a producao ja gravada nesta competencia.
    // Sem isto o cadastro fica certo mas a linha em PreviaBpa continua presa ao snapshot
    // antigo (cnsdoprofissional so e relido do cadastro quando a linha e reprocessada -
    // ver recarregarProfissionalDoCadastro), e o operador teria que reprocessar uma por
    // uma manualmente para a critica sumir.
    public Map<String, Object> buscarCnsDeProfissionaisNoCnesEPropagar(Integer idcompetencia, String unidade,
            AuthenticatedUser user) {
        Map<String, Object> cadastro = buscarCnsDeProfissionaisNoCnes(unidade, user);

        List<Afaturar> alvos = new java.util.ArrayList<>();
        int pagina = 0;
        while (pagina < MAX_PAGINAS_MOTIVOS) {
            List<Afaturar> lote = producaoRepository.listarPaginado(idcompetencia, unidade,
                    ProducaoStatus.PRODUCAO_COM_CRITICA.name(), null, null, pagina, TAMANHO_PAGINA_MOTIVOS);
            if (lote.isEmpty()) {
                break;
            }
            alvos.addAll(lote);
            if (lote.size() < TAMANHO_PAGINA_MOTIVOS) {
                break;
            }
            pagina++;
        }

        int linhasAtualizadas = 0;
        int linhasAindaInvalidas = 0;
        int linhasCompetenciaFechada = 0;
        for (Afaturar producao : alvos) {
            if (CnsUtils.valido(texto(producao.getCnsdoprofissional()))) {
                continue;
            }
            // Sem idcompetencia (tela por periodo, nao por competencia) a busca acima ja
            // varreu todas as competencias da unidade. Competencia fechada/cancelada nao
            // se reprocessa - mesmo criterio conservador do job noturno (statusProcessavel
            // em FaturamentoAutomacaoJob): o trabalho nela ja terminou.
            if (producao.getIdcompetencia() != null) {
                FaturamentoCompetencia competencia = competenciaRepository.findById(
                        Long.valueOf(producao.getIdcompetencia()));
                if (competencia != null && (CompetenciaStatus.FECHADA.name().equals(competencia.getStatus())
                        || CompetenciaStatus.CANCELADA.name().equals(competencia.getStatus()))) {
                    linhasCompetenciaFechada++;
                    continue;
                }
            }
            int idafaturar = producao.getIdafaturar();
            QuarkusTransaction.requiringNew().run(() -> reprocessarInterno(buscar(idafaturar), user));
            Afaturar atualizada = buscar(idafaturar);
            if (CnsUtils.valido(texto(atualizada.getCnsdoprofissional()))) {
                linhasAtualizadas++;
            } else {
                linhasAindaInvalidas++;
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>(cadastro);
        resultado.put("linhasAnalisadas", alvos.size());
        resultado.put("linhasAtualizadas", linhasAtualizadas);
        resultado.put("linhasCompetenciaFechada", linhasCompetenciaFechada);
        resultado.put("linhasAindaInvalidas", linhasAindaInvalidas);
        return resultado;
    }

    // Tipos de registro do SIGTAP que sao de BPA. Fora deles o procedimento pertence a
    // outro instrumento (AIH, APAC, RAAS) - e por isso a linha nasce NAO_FATURAVEL com
    // valor zero, o que nao e erro nem "o SUS nao paga".
    private static final java.util.Set<String> TIPOS_REGISTRO_BPA = java.util.Set.of("01", "02", "03");

    // Automatiza o passo 1 da orientacao que a tela da para a producao nao faturavel:
    // "se o paciente ficou internado, lance esta producao na conta hospitalar (AIH)".
    //
    // Ate aqui isso era trabalho manual linha a linha, mesmo quando a resposta ja estava
    // no proprio banco: existe (ou nao) uma conta hospitalar do paciente naquela
    // competencia. Quando existe, a linha e do instrumento AIH e nao do BPA - e a conta
    // ja a le, porque listarParaContaAih traz toda a producao do paciente na competencia.
    // O que faltava era a producao dizer isso no proprio status.
    //
    // O que NAO e automatizado de proposito: o passo 2 (procedimento anotado errado) exige
    // alguem dizer qual e o procedimento certo, e o passo 3 e justamente deixar como esta.
    // Sem conta hospitalar do paciente, a linha continua NAO_FATURAVEL - inventar um
    // vinculo aqui produziria conta hospitalar que nunca existiu.
    public Map<String, Object> vincularNaoFaturaveisAAih(Integer idcompetencia, String unidade,
            AuthenticatedUser user) {
        if (idcompetencia == null) {
            throw new IllegalArgumentException("Informe a competencia.");
        }

        // Contas da competencia, por paciente e por BAM: os dois caminhos que
        // listarParaContaAih usa para dizer que a producao pertence aquela conta.
        List<FaturamentoAih> contas = FaturamentoAih
                .list("idcompetencia = ?1 and status <> ?2", idcompetencia, AihStatus.CANCELADA.name());
        Map<Integer, String> contaPorPaciente = new LinkedHashMap<>();
        Map<Integer, String> contaPorBam = new LinkedHashMap<>();
        for (FaturamentoAih conta : contas) {
            if (unidade != null && !unidade.isBlank() && !"TODAS".equalsIgnoreCase(unidade)
                    && conta.getUnidade() != null && !conta.getUnidade().isBlank()
                    && !conta.getUnidade().trim().equalsIgnoreCase(unidade.trim())) {
                continue;
            }
            if (conta.getIdpaciente() > 0) {
                contaPorPaciente.putIfAbsent(conta.getIdpaciente(), texto(conta.getNumero()));
            }
            String bam = texto(conta.getBam()).replaceAll("[^0-9]", "");
            if (!bam.isBlank()) {
                contaPorBam.putIfAbsent(Integer.valueOf(bam), texto(conta.getNumero()));
            }
        }

        List<Integer> paraVincular = new java.util.ArrayList<>();
        Map<Integer, String> numeroDaConta = new LinkedHashMap<>();
        int semConta = 0;
        int decisaoManual = 0;
        int foraDoEscopo = 0;
        int total = 0;

        int pagina = 0;
        while (pagina < MAX_PAGINAS_MOTIVOS) {
            List<Afaturar> lote = producaoRepository.listarPaginado(idcompetencia, unidade,
                    ProducaoStatus.NAO_FATURAVEL.name(), null, null, pagina, TAMANHO_PAGINA_MOTIVOS);
            if (lote.isEmpty()) {
                break;
            }
            for (Afaturar producao : lote) {
                total++;
                if (Boolean.TRUE.equals(producao.getClassificacaoManual())) {
                    // Descarte com motivo e autor gravados: decisao humana nao e desfeita
                    // por lote nenhum.
                    decisaoManual++;
                    continue;
                }
                String tipoRegistro = normalizarTipoRegistro(producao.getPa_tpreg());
                if (tipoRegistro.isBlank() || TIPOS_REGISTRO_BPA.contains(tipoRegistro)) {
                    // Nao faturavel de BPA mesmo: o que falta e valor/SIGTAP, nao conta
                    // hospitalar. Quem trata isso e "Corrigir todas".
                    foraDoEscopo++;
                    continue;
                }
                String numero = contaPorPaciente.get(producao.getIdpaciente());
                if (numero == null) {
                    numero = contaPorBam.get(producao.getBam());
                }
                if (numero == null) {
                    semConta++;
                    continue;
                }
                paraVincular.add(producao.getIdafaturar());
                numeroDaConta.put(producao.getIdafaturar(), numero);
            }
            if (lote.size() < TAMANHO_PAGINA_MOTIVOS) {
                break;
            }
            pagina++;
        }

        int vinculadas = 0;
        if (!paraVincular.isEmpty()) {
            int[] gravadas = new int[1];
            QuarkusTransaction.requiringNew().run(() -> {
                gravadas[0] = producaoRepository.vincularNaoFaturaveisComoAihEmMassa(paraVincular,
                        user == null ? "" : user.matricula());
                auditService.registrar(unidade, "VINCULAR_NAO_FATURAVEL_AIH", "SUCESSO", "Afaturar",
                        String.valueOf(idcompetencia), ProducaoStatus.NAO_FATURAVEL.name(),
                        String.valueOf(gravadas[0]),
                        "Producao de procedimento fora do BPA vinculada a conta hospitalar do paciente.",
                        "contas=" + numeroDaConta.values().stream().distinct().limit(20)
                                .collect(java.util.stream.Collectors.joining(",")),
                        user);
            });
            vinculadas = gravadas[0];
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("naoFaturaveis", total);
        resultado.put("vinculadas", vinculadas);
        // Tem conta hospitalar em outra competencia? Nao ha como saber daqui: o que este
        // numero diz e que, nesta competencia, nao existe AIH do paciente. Se deveria
        // existir, o caminho e criar as pre-AIHs em Faturamento SUS > AIH.
        resultado.put("semConta", semConta);
        resultado.put("decisaoManual", decisaoManual);
        resultado.put("foraDoEscopo", foraDoEscopo);
        resultado.put("contas", contaPorPaciente.size() + contaPorBam.size());
        return resultado;
    }

    // Pacientes cuja producao esta travada por CNS e que nao tem CNS valido nem no
    // cadastro - ou seja, o que sincronizarCnsDoCadastro nao tem como resolver.
    //
    // Sai agrupado por paciente (e nao por linha) porque a correcao e por pessoa: alguem
    // vai ao CADSUS uma vez e destrava todas as linhas daquele paciente de uma vez. A
    // lista existe para sair do faturamento e chegar a recepcao/cadastro.
    // Teto por chamada: o lote grava cadastro de servidor e reprocessa producao, entao
    // uma competencia inteira sem limite seguraria a tela sem dar noticia. A unidade de
    // trabalho e o profissional distinto, nao a linha - centenas de linhas sem CBO
    // costumam ser algumas dezenas de servidores.
    private static final int MAX_SERVIDORES_CBO = 100;
    private static final int MAX_LINHAS_POR_SERVIDOR_CBO = 500;

    // Cargo que nao identifica ocupacao nenhuma. "SERVIDOR" e o default do cadastro:
    // procurar esse texto na tabela CBO traria qualquer coisa, e gravar o que viesse
    // seria inventar a ocupacao de um profissional.
    private static final java.util.Set<String> CARGOS_SEM_OCUPACAO = java.util.Set.of(
            "SERVIDOR", "OUTROS", "OUTRO", "N/A", "XXXX");

    // Completa o CBO dos profissionais com producao sem ocupacao, sem exigir digitacao
    // linha a linha.
    //
    // Dois caminhos, nesta ordem:
    // 1. cadastro do servidor ja tem CBO -> reprocessar as linhas faz a heranca
    //    (recarregarProfissionalDoCadastro). Nada e inventado, so copiado.
    // 2. cadastro sem CBO -> procura o cargo na tabela CBO. So grava quando o cargo casa
    //    com UM unico titulo; com dois ou mais o servidor entra na lista de pendentes com
    //    as opcoes, porque escolher entre eles seria decidir a ocupacao de alguem - e CBO
    //    errado no BPA glosa igual (ou pior) que CBO em branco.
    //
    // O codigo achado e gravado no cadastro do servidor, e nao so na producao: e la que a
    // proxima competencia vai buscar o dado, senao o mesmo profissional voltaria com o
    // mesmo alerta todo mes.
    // Lista, para a tela de correcao, os profissionais com producao sem CBO junto do que o
    // cadastro sabe deles: cargo e especialidade.
    //
    // Quem corrige precisa desses dois para escolher a ocupacao certa - o CBO nao se
    // adivinha de cabeca a partir do nome. Somente leitura, sem efeito nenhum: o caminho
    // que grava e completarCboDosProfissionais.
    public List<Map<String, Object>> listarProfissionaisSemCbo(Integer idcompetencia, String unidade) {
        List<Map<String, Object>> lista = new java.util.ArrayList<>();

        for (Object[] grupo : producaoRepository.agruparMatriculasSemCbo(idcompetencia, unidade)) {
            String matricula = texto((String) grupo[0]);
            br.com.iv.entity.Servidor servidor = br.com.iv.entity.Servidor
                    .find("matricula", matricula).firstResult();
            lista.add(dadosDoProfissionalSemCbo(matricula, texto((String) grupo[1]), servidor,
                    ((Number) grupo[2]).longValue(), servidor == null ? 0 : 1));
        }

        // Producao que gravou o nome e nao a matricula. O cadastro so e util quando o nome
        // identifica uma pessoa so - com dois cadastros a tela mostra isso, para o operador
        // saber que precisa escolher em vez de confiar no que aparece.
        for (Object[] grupo : producaoRepository.agruparNomesSemCboSemMatricula(idcompetencia, unidade)) {
            String nome = texto((String) grupo[0]);
            List<br.com.iv.entity.Servidor> candidatos = br.com.iv.entity.Servidor
                    .find("upper(trim(nome)) = ?1", nome.toUpperCase()).list();
            lista.add(dadosDoProfissionalSemCbo("", nome, candidatos.size() == 1 ? candidatos.get(0) : null,
                    ((Number) grupo[1]).longValue(), candidatos.size()));
        }
        return lista;
    }

    private Map<String, Object> dadosDoProfissionalSemCbo(String matricula, String nome,
            br.com.iv.entity.Servidor servidor, long linhas, int cadastros) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("matricula", matricula);
        item.put("profissional", nome);
        item.put("cargo", servidor == null ? "" : texto(servidor.getCargo()));
        item.put("especialidade", servidor == null ? "" : texto(servidor.getEspecialidade()));
        item.put("cboCadastro", servidor == null ? "" : cboHerdavel(servidor.getCbo()));
        item.put("linhas", linhas);
        item.put("cadastros", cadastros);
        return item;
    }

    public Map<String, Object> completarCboDosProfissionais(Integer idcompetencia, String unidade,
            AuthenticatedUser user) {
        List<Object[]> grupos = producaoRepository.agruparMatriculasSemCbo(idcompetencia, unidade);
        int analisados = 0;
        int completadosPelaTabelaCbo = 0;
        int herdadosDoCadastro = 0;
        int linhasAtualizadas = 0;
        int falhas = 0;
        List<Map<String, Object>> pendentes = new java.util.ArrayList<>();

        for (Object[] grupo : grupos) {
            if (analisados >= MAX_SERVIDORES_CBO) {
                break;
            }
            analisados++;
            String matricula = texto((String) grupo[0]);
            String nome = texto((String) grupo[1]);
            long linhas = ((Number) grupo[2]).longValue();

            br.com.iv.entity.Servidor servidor = br.com.iv.entity.Servidor
                    .find("matricula", matricula).firstResult();
            if (servidor == null) {
                pendentes.add(pendenciaDeCbo(matricula, nome, "", linhas, "SEM_CADASTRO", List.of()));
                continue;
            }
            String cargo = texto(servidor.getCargo());
            String cboCadastro = cboHerdavel(servidor.getCbo());
            if (cboCadastro.isBlank()) {
                List<br.com.iv.entity.Cbo> opcoes = candidatosDeCbo(cargo);
                if (opcoes.size() != 1) {
                    String motivo = cargo.isBlank() || CARGOS_SEM_OCUPACAO.contains(cargo.toUpperCase())
                            ? "CARGO_NAO_IDENTIFICA_OCUPACAO"
                            : opcoes.isEmpty() ? "NENHUM_CBO_PARA_O_CARGO" : "MAIS_DE_UM_CBO_POSSIVEL";
                    pendentes.add(pendenciaDeCbo(matricula, nome, cargo, linhas, motivo, opcoes));
                    continue;
                }
                String codigo = cboHerdavel(opcoes.get(0).getCodigo());
                if (codigo.isBlank()) {
                    pendentes.add(pendenciaDeCbo(matricula, nome, cargo, linhas, "CBO_DA_TABELA_INVALIDO", opcoes));
                    continue;
                }
                String titulo = texto(opcoes.get(0).getTitulo());
                QuarkusTransaction.requiringNew()
                        .run(() -> gravarCboNoCadastroDoServidor(matricula, codigo, cargo, titulo, user));
                completadosPelaTabelaCbo++;
            } else {
                herdadosDoCadastro++;
            }

            List<Integer> ids = producaoRepository.idsSemCboPorMatricula(idcompetencia, unidade, matricula,
                    MAX_LINHAS_POR_SERVIDOR_CBO);
            for (Integer idafaturar : ids) {
                try {
                    QuarkusTransaction.requiringNew().run(() -> reprocessarInterno(buscar(idafaturar), user));
                    linhasAtualizadas++;
                } catch (RuntimeException e) {
                    falhas++;
                    auditService.registrar(unidade, "COMPLETAR_CBO_PRODUCAO", "ERRO", "Afaturar",
                            String.valueOf(idafaturar), null, null, e.getMessage(),
                            "matricula=" + matricula, user);
                }
            }
        }

        // Segundo caminho: producao que gravou o nome do profissional mas nao a matricula.
        //
        // O laco acima so enxerga quem tem matricula, entao essas linhas ficavam de fora
        // mesmo com o CBO preenchido no cadastro - o dado existia e nao havia como chegar
        // nele. O nome resolve, com a mesma cautela do resto deste metodo: so grava quando
        // ha exatamente um servidor com aquele nome e ele tem CBO. Homonimo (o caso real
        // que motivou este cuidado: dois cadastros para a mesma pessoa, matriculas
        // diferentes) vira pendencia, nao palpite.
        for (Object[] grupo : producaoRepository.agruparNomesSemCboSemMatricula(idcompetencia, unidade)) {
            if (analisados >= MAX_SERVIDORES_CBO) {
                break;
            }
            analisados++;
            String nome = texto((String) grupo[0]);
            long linhas = ((Number) grupo[1]).longValue();

            List<br.com.iv.entity.Servidor> candidatos = br.com.iv.entity.Servidor
                    .find("upper(trim(nome)) = ?1", nome.toUpperCase()).list();
            List<br.com.iv.entity.Servidor> comCbo = candidatos.stream()
                    .filter(s -> !cboHerdavel(s.getCbo()).isBlank())
                    .toList();
            if (comCbo.isEmpty()) {
                pendentes.add(pendenciaDeCbo("", nome, "", linhas,
                        candidatos.isEmpty() ? "SEM_CADASTRO" : "CADASTRO_SEM_CBO", List.of()));
                continue;
            }
            String codigo = cboHerdavel(comCbo.get(0).getCbo());
            boolean divergem = comCbo.stream().anyMatch(s -> !codigo.equals(cboHerdavel(s.getCbo())));
            if (divergem) {
                pendentes.add(pendenciaDeCbo("", nome, "", linhas, "MAIS_DE_UM_CBO_POSSIVEL", List.of()));
                continue;
            }

            herdadosDoCadastro++;
            for (Integer idafaturar : producaoRepository.idsSemCboPorNome(idcompetencia, unidade, nome,
                    MAX_LINHAS_POR_SERVIDOR_CBO)) {
                try {
                    QuarkusTransaction.requiringNew().run(() -> {
                        Afaturar producao = buscar(idafaturar);
                        producao.setCbo(codigo);
                        reprocessarInterno(producao, user);
                    });
                    linhasAtualizadas++;
                } catch (RuntimeException e) {
                    falhas++;
                    auditService.registrar(unidade, "COMPLETAR_CBO_PRODUCAO", "ERRO", "Afaturar",
                            String.valueOf(idafaturar), null, null, e.getMessage(),
                            "nome=" + nome, user);
                }
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("profissionaisAnalisados", analisados);
        resultado.put("profissionaisComCboNoCadastro", herdadosDoCadastro);
        resultado.put("profissionaisCompletadosPeloCargo", completadosPelaTabelaCbo);
        resultado.put("linhasAtualizadas", linhasAtualizadas);
        resultado.put("falhas", falhas);
        resultado.put("profissionaisSemDecisao", pendentes.size());
        resultado.put("pendentes", pendentes);
        return resultado;
    }

    // Candidatos de CBO para um cargo. Titulo igual primeiro; so quando nao ha nenhum e
    // que o like entra, e mesmo assim o chamador exige resultado unico para gravar.
    private List<br.com.iv.entity.Cbo> candidatosDeCbo(String cargo) {
        String alvo = texto(cargo);
        if (alvo.isBlank() || CARGOS_SEM_OCUPACAO.contains(alvo.toUpperCase())) {
            return List.of();
        }
        List<br.com.iv.entity.Cbo> exatos = cboRepository.filtrarPorTituloExato(alvo);
        if (!exatos.isEmpty()) {
            return exatos;
        }
        return cboRepository.filtarPorCbo(alvo);
    }

    void gravarCboNoCadastroDoServidor(String matricula, String codigo, String cargo, String titulo,
            AuthenticatedUser user) {
        br.com.iv.entity.Servidor servidor = br.com.iv.entity.Servidor
                .find("matricula", matricula).firstResult();
        if (servidor == null) {
            return;
        }
        String anterior = texto(servidor.getCbo());
        servidor.setCbo(codigo);
        servidor.persist();
        auditService.registrar(texto(servidor.getUnidade()), "COMPLETAR_CBO_SERVIDOR", "SUCESSO", "Servidor",
                matricula, anterior, codigo, null, "cargo=" + cargo + " titulo=" + titulo, user);
    }

    // Profissional que o lote nao pode resolver sozinho. Vai com as opcoes encontradas
    // (ate cinco) para a tela dizer o que escolher, em vez de so informar que sobrou.
    private Map<String, Object> pendenciaDeCbo(String matricula, String nome, String cargo, long linhas,
            String motivo, List<br.com.iv.entity.Cbo> opcoes) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("matricula", matricula);
        item.put("profissional", nome);
        item.put("cargo", cargo);
        item.put("linhas", linhas);
        item.put("motivo", motivo);
        List<Map<String, Object>> lista = new java.util.ArrayList<>();
        for (br.com.iv.entity.Cbo cbo : opcoes.stream().limit(5).toList()) {
            Map<String, Object> opcao = new LinkedHashMap<>();
            opcao.put("codigo", texto(cbo.getCodigo()));
            opcao.put("titulo", texto(cbo.getTitulo()));
            lista.add(opcao);
        }
        item.put("opcoes", lista);
        item.put("totalOpcoes", opcoes.size());
        return item;
    }

    public List<Map<String, Object>> listarPacientesSemCnsValido(Integer idcompetencia, String unidade) {
        Map<Integer, Map<String, Object>> porPaciente = new LinkedHashMap<>();
        int pagina = 0;
        while (pagina < MAX_PAGINAS_MOTIVOS) {
            List<Afaturar> lote = producaoRepository.listarPaginado(idcompetencia, unidade,
                    ProducaoStatus.PRODUCAO_COM_CRITICA.name(), null, null, pagina, TAMANHO_PAGINA_MOTIVOS);
            if (lote.isEmpty()) {
                break;
            }
            for (Afaturar producao : lote) {
                if (CnsUtils.valido(texto(producao.getCnsdopciente()))) {
                    continue;
                }
                Map<String, Object> item = porPaciente.computeIfAbsent(producao.getIdpaciente(), id -> {
                    Pacientes paciente = id > 0 ? Pacientes.findById(id) : null;
                    Map<String, Object> novo = new LinkedHashMap<>();
                    novo.put("idafaturar", producao.getIdafaturar());
                    novo.put("idpaciente", id);
                    novo.put("paciente", texto(producao.getNomepaciente()));
                    novo.put("prontuario", paciente == null ? "" : texto(paciente.getProntuario()));
                    novo.put("cpf", paciente == null ? "" : texto(paciente.getCpf()));
                    novo.put("nascimento", paciente == null ? "" : texto(paciente.getNascimento()));
                    novo.put("cnsProducao", texto(producao.getCnsdopciente()));
                    novo.put("cnsCadastro", paciente == null ? "" : texto(paciente.getCns()));
                    // A data de nascimento que veio na propria producao. O cadastro do
                    // paciente as vezes esta sem ela (ou com ela em formato que nao da
                    // para consultar), e sem este campo o lote nao tinha o que perguntar
                    // ao CNES - enquanto o modal, que ja caia para este mesmo valor,
                    // achava o CNS. Era essa a diferenca entre "de um em um funciona" e
                    // "o botao de todos nao mexe na critica".
                    novo.put("nascimentoProducao", "");
                    novo.put("linhas", 0);
                    return novo;
                });
                if (texto((String) item.get("nascimentoProducao")).isBlank()) {
                    item.put("nascimentoProducao", texto(producao.getDtnascimento()));
                }
                item.put("linhas", ((Integer) item.get("linhas")) + 1);
            }
            if (lote.size() < TAMANHO_PAGINA_MOTIVOS) {
                break;
            }
            pagina++;
        }

        return porPaciente.values().stream()
                .sorted((a, b) -> ((Integer) b.get("linhas")).compareTo((Integer) a.get("linhas")))
                .toList();
    }

    // Por que a producao esta parada, agrupado por motivo.
    //
    // O motor de validacao ja produz o motivo de cada linha (ValidacaoResultado), mas
    // reprocessarInterno so guardava o resultado final: status + possui_critica booleano.
    // O texto era descartado, entao a tela de pendencias mostrava centenas de linhas
    // "com critica" sem dizer o que falta - e o operador nao tinha como agir. A tabela
    // faturamento_critica existe desde a V93, mas nunca foi populada.
    //
    // Aqui a validacao e refeita em memoria, apenas para leitura: nada e gravado, o
    // status das linhas nao muda, e o resultado sai agrupado por codigo de critica com a
    // contagem. "608 linhas com critica" vira "608x procedimento X ausente no SIGTAP",
    // que ja e uma tarefa.
    public List<Map<String, Object>> agruparMotivosDeCritica(Integer idcompetencia, String unidade) {
        List<Afaturar> comCritica = new java.util.ArrayList<>();
        for (String status : List.of(ProducaoStatus.PRODUCAO_COM_CRITICA.name(),
                ProducaoStatus.REVISAO_RECOMENDADA.name())) {
            int pagina = 0;
            while (pagina < MAX_PAGINAS_MOTIVOS) {
                List<Afaturar> lote = producaoRepository.listarPaginado(idcompetencia, unidade, status,
                        null, null, pagina, TAMANHO_PAGINA_MOTIVOS);
                if (lote.isEmpty()) {
                    break;
                }
                comCritica.addAll(lote);
                if (lote.size() < TAMANHO_PAGINA_MOTIVOS) {
                    break;
                }
                pagina++;
            }
        }

        Map<String, Map<String, Object>> porMotivo = new LinkedHashMap<>();
        for (Afaturar producao : comCritica) {
            for (ValidacaoResultado resultado : validationService.validar(producao)) {
                if (resultado.resultado() == ResultadoValidacao.SUCESSO) {
                    continue;
                }
                // Agrupa por codigo + mensagem: o codigo sozinho juntaria
                // "procedimento X ausente" com "procedimento Y ausente", que sao tarefas
                // diferentes para quem vai corrigir.
                String chave = resultado.codigo() + "|" + resultado.mensagem();
                Map<String, Object> grupo = porMotivo.computeIfAbsent(chave, k -> {
                    Map<String, Object> novo = new LinkedHashMap<>();
                    novo.put("codigo", resultado.codigo());
                    novo.put("severidade", resultado.resultado().name());
                    novo.put("motivo", resultado.mensagem());
                    novo.put("campo", resultado.campo());
                    novo.put("quantidade", 0);
                    novo.put("exemploProcedimento", texto(producao.getProcedimento()));
                    novo.put("exemploPaciente", texto(producao.getNomepaciente()));
                    return novo;
                });
                grupo.put("quantidade", ((Integer) grupo.get("quantidade")) + 1);
            }
        }

        return porMotivo.values().stream()
                .sorted((a, b) -> ((Integer) b.get("quantidade")).compareTo((Integer) a.get("quantidade")))
                .toList();
    }



    // Preparo incremental da competencia. Uma chamada = um lote; o painel repete ate
    // "restantes" zerar, mostrando o progresso.
    //
    // Por que nao usar processarCompetencia aqui: ele reprocessa linha a linha, cada uma
    // em transacao propria e com meia duzia de consultas (SIGTAP, paciente, servidor,
    // configuracao). Numa competencia real - 81 mil linhas em uma unica unidade, medidas
    // nesta base - isso nao termina dentro de uma requisicao, e o statement_timeout de
    // 60s derruba no meio.
    //
    // Aqui tudo e UPDATE em massa, que e onde o banco e bom: vincular a competencia,
    // cruzar com o SIGTAP (resolve instrumento, valores e tipo de registro) e classificar
    // o que sobrou. A validacao linha a linha nao acontece neste caminho - e proposital.
    // O painel agrega os bloqueios por SQL, entao o operador ve o que falta; e as acoes
    // de correcao (CNS, CBO) continuam revalidando as linhas que tocam.
    public Map<String, Object> prepararEmLote(int idcompetencia, String unidade, AuthenticatedUser user, int limite) {
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
        }
        int tamanhoLote = limite <= 0 || limite > 20000 ? 5000 : limite;
        String aaaamm = competencia.getCompetencia();
        String matricula = user == null ? "" : user.matricula();

        final int[] vinculadas = new int[1];
        QuarkusTransaction.requiringNew().run(() -> vinculadas[0] = producaoRepository
                .vincularSemCompetenciaPorCompetencia(idcompetencia, aaaamm, unidade, matricula, tamanhoLote));

        // Herda o CBO do cadastro antes de classificar e validar. Sem isto a linha nasce
        // em REVISAO_RECOMENDADA por um dado que o sistema ja tem, e o operador precisa
        // clicar num botao todo mes para resolver o mesmo caso.
        final int[] cboHerdado = new int[1];
        QuarkusTransaction.requiringNew().run(() -> cboHerdado[0] = producaoRepository
                .herdarCboDoCadastro(idcompetencia, unidade, matricula, tamanhoLote));

        // Com o tamanho do lote: sem ele, esta chamada reprocessava toda a producao ja
        // vinculada a cada rodada, e a rodada acabava estourando o socketTimeout do JDBC.
        Map<String, Object> sincronizacao = sincronizarComSigtap(idcompetencia, aaaamm, unidade, user, tamanhoLote);

        final int[] classificadas = new int[1];
        QuarkusTransaction.requiringNew().run(() -> classificadas[0] = producaoRepository
                .classificarPendentesEmMassa(idcompetencia, unidade, tamanhoLote, matricula));

        long faltamVincular = producaoRepository.contarSemCompetenciaPorCompetencia(aaaamm, unidade);
        long faltamClassificar = producaoRepository.contarPendentesDeClassificacao(idcompetencia, unidade);

        auditService.registrar(unidade, "PREPARAR_COMPETENCIA_LOTE", "SUCESSO", "FaturamentoCompetencia",
                String.valueOf(idcompetencia), null, aaaamm, null,
                "vinculadas=" + vinculadas[0] + " classificadas=" + classificadas[0]
                        + " restamVincular=" + faltamVincular + " restamClassificar=" + faltamClassificar,
                user);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("vinculadas", vinculadas[0]);
        resultado.put("cboHerdado", cboHerdado[0]);
        resultado.put("classificadas", classificadas[0]);
        resultado.putAll(sincronizacao);
        resultado.put("restantes", faltamVincular + faltamClassificar);
        // Sem avanco o painel para de repetir: ou acabou, ou ha algo que este caminho
        // nao resolve, e insistir so gera chamada infinita.
        resultado.put("avancou", vinculadas[0] + classificadas[0] > 0);
        return resultado;
    }
    // Painel de fechamento: um retrato da competencia em vez das seis etapas da esteira.
    //
    // A esteira nasceu quando a producao chegava sem classificacao e sem validacao, e
    // alguem precisava empurra-la etapa por etapa. Desde que o motor passou a rodar na
    // porta de entrada (prepararProducaoNova), a maior parte daquelas etapas virou
    // cerimonia: o que sobra e dinheiro parado esperando um dado que so uma pessoa pode
    // resolver. Este metodo devolve exatamente isso - quanto sai, quanto esta preso, e
    // por qual causa, ordenado pelo valor.
    public Map<String, Object> montarPainel(Integer idcompetencia, String unidade) {
        Map<String, Object> painel = new LinkedHashMap<>();
        FaturamentoCompetencia competencia = idcompetencia == null ? null
                : FaturamentoCompetencia.findById(idcompetencia);

        painel.put("competencia", competencia == null ? null : competencia.getCompetencia());
        painel.put("statusCompetencia", competencia == null ? null : competencia.getStatus());
        painel.put("unidade", unidade);

        double valorApto = producaoRepository.somarValorEstimado(idcompetencia, unidade,
                ProducaoStatus.APTO_FATURAMENTO.name());
        long linhasAptas = producaoRepository.contarPorStatus(idcompetencia, unidade,
                ProducaoStatus.APTO_FATURAMENTO.name());
        painel.put("valorApto", valorApto);
        painel.put("linhasAptas", linhasAptas);

        // A mesma producao apta, separada pelo caminho de transmissao. BPA, APAC e RAAS o
        // GHBAM gera e o operador confere na tela de Exportacoes; a AIH sai pelo SISAIH01,
        // em outro sistema. Num total unico a parte da AIH some do radar do operador - e e
        // justamente a que depende de uma acao fora daqui para virar receita.
        List<Map<String, Object>> porInstrumento = new java.util.ArrayList<>();
        double valorGhbam = 0;
        long linhasGhbam = 0;
        double valorAih = 0;
        long linhasAih = 0;
        for (Object[] linha : producaoRepository.agruparAptosPorInstrumento(idcompetencia, unidade)) {
            String instrumento = (String) linha[0];
            long quantidade = ((Number) linha[1]).longValue();
            double valor = ((Number) linha[2]).doubleValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("instrumento", instrumento);
            item.put("linhas", quantidade);
            item.put("valor", valor);
            porInstrumento.add(item);
            if (InstrumentoRegistro.AIH.name().equals(instrumento)) {
                valorAih += valor;
                linhasAih += quantidade;
            } else {
                valorGhbam += valor;
                linhasGhbam += quantidade;
            }
        }
        painel.put("aptosPorInstrumento", porInstrumento);
        painel.put("valorAptoGhbam", valorGhbam);
        painel.put("linhasAptasGhbam", linhasGhbam);
        painel.put("valorAptoAih", valorAih);
        painel.put("linhasAptasAih", linhasAih);

        List<Map<String, Object>> bloqueios = new java.util.ArrayList<>();
        double valorRetido = 0;
        long linhasRetidas = 0;
        if (idcompetencia != null) {
            String aaaamm = competencia == null ? null : competencia.getCompetencia();
            for (Object[] linha : producaoRepository.agruparBloqueios(idcompetencia, unidade, aaaamm)) {
                long quantidade = ((Number) linha[1]).longValue();
                if (quantidade == 0) {
                    continue;
                }
                double valor = ((Number) linha[2]).doubleValue();
                long resolveSozinho = ((Number) linha[3]).longValue();
                Map<String, Object> bloqueio = new LinkedHashMap<>();
                String causa = (String) linha[0];
                bloqueio.put("causa", causa);
                bloqueio.put("titulo", tituloBloqueio(causa));
                bloqueio.put("linhas", quantidade);
                bloqueio.put("valor", valor);
                // Quantas o proprio sistema resolve com o que ja tem no cadastro. E a
                // diferenca entre "clique aqui" e "alguem precisa ir buscar o dado".
                bloqueio.put("resolvidasPeloCadastro", resolveSozinho);
                bloqueio.put("acao", acaoBloqueio(causa));
                bloqueios.add(bloqueio);
                valorRetido += valor;
                linhasRetidas += quantidade;
            }
            bloqueios.sort((a, b) -> Double.compare((Double) b.get("valor"), (Double) a.get("valor")));
        }
        painel.put("bloqueios", bloqueios);
        painel.put("valorRetido", valorRetido);
        painel.put("linhasRetidas", linhasRetidas);

        // Pre-requisitos que nao sao "linha com problema" e sim configuracao: aparecem
        // como aviso no topo, nao como bloqueio com valor.
        //
        // Sem competencia escolhida a prontidao olha para a competencia a faturar (mes
        // anterior). E o caso do primeiro acesso: o operador precisa ver que falta
        // importar o SIGTAP antes de clicar em abrir e receber a recusa - abrir exige
        // SIGTAP instalado (FaturamentoCompetenciaService.abrir).
        // Qual competencia esta tela esta tratando, em ordem de autoridade:
        //
        // 1. a competencia aberta/selecionada - o faturamento acontece dentro do proprio
        //    mes da competencia, e enquanto ela nao fecha e nela que se trabalha;
        // 2. a do SIGTAP que a unidade importou - importar a tabela e o ato pelo qual o
        //    operador declara qual mes vai faturar, e abrir exige justamente essa tabela.
        //    Propor mes diferente do SIGTAP instalado dava um botao que so sabia recusar:
        //    "Abrir 08/2026" com a tabela de 07/2026 na mao;
        // 3. o mes corrente, quando nao ha nem competencia nem tabela - so um palpite para
        //    a tela ter o que dizer no primeiro acesso.
        // Competencia FECHADA nao e mais alvo: o trabalho nela acabou e o que a tela precisa
        // oferecer e a proxima. Sem esta distincao a fechada continuava selecionada, o painel
        // se dava por servido e o botao de abrir nunca reaparecia - beco sem saida depois do
        // primeiro fechamento.
        boolean emAndamento = competencia != null
                && !CompetenciaStatus.FECHADA.name().equals(competencia.getStatus())
                && !CompetenciaStatus.CANCELADA.name().equals(competencia.getStatus());

        String competenciaAlvo = emAndamento ? competencia.getCompetencia()
                : producaoRepository.proximaCompetenciaParaAbrir(unidade);
        if (competenciaAlvo == null || competenciaAlvo.isBlank()) {
            competenciaAlvo = producaoRepository.ultimaCompetenciaSigtapImportada(unidade);
        }
        if (competenciaAlvo == null || competenciaAlvo.isBlank()) {
            // Palpite do primeiro acesso: o mes ANTERIOR, porque se fatura o mes que ja
            // terminou - em agosto fecha-se julho.
            competenciaAlvo = java.time.LocalDate.now().minusMonths(1)
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        }
        // Quem decide se a tela mostra "Abrir" e este sinal, e nao a existencia de alguma
        // competencia: fechada a de julho, agosto ainda esta por abrir.
        boolean podeAbrirOutra = !emAndamento
                && !competenciaAlvo.equals(competencia == null ? null : competencia.getCompetencia());
        Map<String, Object> prontidao = new LinkedHashMap<>();
        prontidao.put("competenciaAlvo", competenciaAlvo);
        prontidao.put("podeAbrirOutra", podeAbrirOutra);

        // Remessa gerada e nao transmitida e dinheiro parado sem aviso: o arquivo existe no
        // servidor, o painel se da por satisfeito e ninguem sabe que falta enviar. O ciclo
        // do faturamento nao termina no arquivo, termina no protocolo.
        // Todas as competencias DA UNIDADE, nao so a selecionada.
        //
        // Escopo por unidade e regra: cada uma cuida do que e seu. Escopo por competencia
        // aqui seria furo - a remessa de julho sumia do alerta assim que o operador abria
        // agosto, e o dinheiro parado voltava a ser invisivel. Remessa esquecida nao deixa
        // de existir porque o mes virou.
        Object[] pendentes = producaoRepository.remessasPendentesTransmissao(null, unidade);
        prontidao.put("remessasPendentes", ((Number) pendentes[0]).longValue());
        prontidao.put("valorPendenteTransmissao", ((Number) pendentes[1]).doubleValue());
        prontidao.put("diasSemTransmitir", ((Number) pendentes[2]).longValue());
        prontidao.put("cnesConfigurado", cnesConfigurado(unidade));
        prontidao.put("sigtapImportado", producaoRepository.contarSigtapDaCompetencia(competenciaAlvo) > 0);
        prontidao.put("competenciaAberta", competencia != null
                && ("ABERTA".equals(competencia.getStatus()) || "EM_PROCESSAMENTO".equals(competencia.getStatus())
                        || "EM_AUDITORIA".equals(competencia.getStatus())));
        // Producao do mes que ainda nao foi vinculada a competencia. Sem isto o painel
        // mostra zero em tudo e o operador nao tem o que clicar - foi exatamente o que
        // aconteceu no primeiro uso: a competencia recem-aberta nao "ve" a producao ate
        // alguem rodar o processamento, e a esteira antiga tinha uma etapa so para isso.
        painel.put("producaoNaoVinculada",
                producaoRepository.contarSemCompetenciaPorCompetencia(competenciaAlvo, unidade));

        painel.put("prontidao", prontidao);
        return painel;
    }

    private String tituloBloqueio(String causa) {
        return switch (causa) {
            case "CNS_PACIENTE" -> "CNS do paciente ausente ou inválido";
            case "RECEM_NASCIDO_SEM_CNS" -> "Recém-nascido sem CNS (cobrado na AIH da internação)";
            case "CBO_PROFISSIONAL" -> "CBO do profissional não informado";
            case "PROCEDIMENTO_FORA_SIGTAP" -> "Procedimento fora do SIGTAP da competência";
            case "SEM_CLASSIFICACAO" -> "Produção sem instrumento definido";
            default -> causa;
        };
    }

    // Slug da ação que resolve a causa em lote. A tela usa para saber qual botão mostrar;
    // todos apontam para endpoints que já existem.
    private String acaoBloqueio(String causa) {
        return switch (causa) {
            case "CNS_PACIENTE" -> "SINCRONIZAR_CNS";
            // Nao ha acao em lote aqui de proposito: marcar producao de bebe como coberta
            // pela AIH depende de conferir se ela cai dentro da internacao, e isso e
            // decisao com consequencia. O painel de RN faz essa conferencia caso a caso.
            case "RECEM_NASCIDO_SEM_CNS" -> "PAINEL_RN";
            case "CBO_PROFISSIONAL" -> "COMPLETAR_CBO";
            case "PROCEDIMENTO_FORA_SIGTAP" -> "SINCRONIZAR_SIGTAP";
            case "SEM_CLASSIFICACAO" -> "CLASSIFICAR_PENDENTES";
            default -> "";
        };
    }
    public Map<String, Object> montarDashboard(Integer idcompetencia, String unidade) {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("producaoTotal", producaoRepository.contarPorStatus(idcompetencia, unidade, null));
        dashboard.put("aptos",
                producaoRepository.contarPorStatus(idcompetencia, unidade, ProducaoStatus.APTO_FATURAMENTO.name()));
        dashboard.put("revisaoRecomendada", producaoRepository.contarPorStatus(idcompetencia, unidade,
                ProducaoStatus.REVISAO_RECOMENDADA.name()));
        dashboard.put("comCritica", producaoRepository.contarPorStatus(idcompetencia, unidade,
                ProducaoStatus.PRODUCAO_COM_CRITICA.name()));
        dashboard.put("pendentes", producaoRepository.contarPorStatus(idcompetencia, unidade,
                ProducaoStatus.PENDENTE_CLASSIFICACAO.name()));
        dashboard.put("naoFaturavel", producaoRepository.contarPorStatus(idcompetencia, unidade,
                ProducaoStatus.NAO_FATURAVEL.name()));
        // O que o lote de correcao pode mexer dentro de nao faturavel: o chip continua
        // mostrando o total do status, mas o botao precisa do numero real, senao promete
        // corrigir descartes manuais que ele preserva de proposito.
        dashboard.put("naoFaturavelCorrigivel",
                producaoRepository.contarNaoFaturavelCorrigivel(idcompetencia, unidade));
        // Recem-nascido nao e um status, e um recorte por nome dentro de critica e
        // pendente - por isso vem aqui e nao na contagem por status. A tela usa para
        // decidir se o botao do painel de RN aparece.
        dashboard.put("recemNascidos", producaoRepository.contarRecemNascidos(idcompetencia, unidade));
        // valorEstimado soma TODA a producao da competencia (inclui critica, revisao e nao
        // faturavel), e por isso sempre parece maior do que sera pago - a exportacao leva
        // apenas APTO_FATURAMENTO. Mantido como estava para nao mudar quem ja o consome,
        // mas agora acompanhado do recorte que responde as duas perguntas reais: quanto
        // vai faturar e quanto esta preso esperando correcao.
        dashboard.put("valorEstimado", producaoRepository.somarValorEstimado(idcompetencia, unidade));
        double valorApto = producaoRepository.somarValorEstimado(idcompetencia, unidade,
                ProducaoStatus.APTO_FATURAMENTO.name());
        double valorCritica = producaoRepository.somarValorEstimado(idcompetencia, unidade,
                ProducaoStatus.PRODUCAO_COM_CRITICA.name());
        double valorRevisao = producaoRepository.somarValorEstimado(idcompetencia, unidade,
                ProducaoStatus.REVISAO_RECOMENDADA.name());
        double valorPendente = producaoRepository.somarValorEstimado(idcompetencia, unidade,
                ProducaoStatus.PENDENTE_CLASSIFICACAO.name());
        dashboard.put("valorApto", valorApto);
        // Retido = tem valor e nao sai na exportacao por depender de correcao. Nao
        // faturavel fica fora da conta: ali nao ha o que corrigir na producao.
        dashboard.put("valorRetido", valorCritica + valorRevisao + valorPendente);
        dashboard.put("valorRetidoCritica", valorCritica);
        dashboard.put("valorRetidoRevisao", valorRevisao);
        dashboard.put("valorRetidoPendente", valorPendente);
        // Configuracao da unidade, nao contador: a tela usa para dizer ao operador se a
        // critica vem do CNES ausente em vez de listar causas possiveis.
        dashboard.put("cnesConfigurado", cnesConfigurado(unidade));
        return dashboard;
    }

    private void aplicarDados(Afaturar producao, AfaturarDto dto) {
        producao.setProcedimento(dto.getProcedimento());
        producao.setDescricao(dto.getDescricao());
        producao.setLocal(dto.getLocal());
        producao.setLocalsolicitante(dto.getLocalsolicitante());
        producao.setMatriculaservidor(dto.getMatriculaservidor());
        producao.setNomeservidor(dto.getNomeservidor());
        producao.setBam(dto.getBam());
        producao.setData(dto.getData());
        producao.setIdade(dto.getIdade());
        producao.setIdtratamento(dto.getIdtratamento());
        producao.setIdregistro(dto.getIdregistro());
        producao.setIdpaciente(dto.getIdpaciente());
        producao.setPa_sa(dto.getPa_sa());
        producao.setPa_sp(dto.getPa_sp());
        producao.setPa_sh(dto.getPa_sh());
        producao.setPa_tpreg(dto.getPa_tpreg());
        producao.setPa_eletiva(dto.getPa_eletiva());
        producao.setCnsdoprofissional(dto.getCnsdoprofissional());
        producao.setCnsdopciente(dto.getCnsdopciente());
        producao.setSexo(dto.getSexo());
        producao.setIbgecod(dto.getIbgecod());
        producao.setDtatendimento(dto.getDtatendimento());
        producao.setCid10(dto.getCid10());
        producao.setCarateratend(dto.getCarateratend());
        producao.setAutorizacao(dto.getAutorizacao());
        producao.setOrigeminfo(dto.getOrigeminfo());
        producao.setNomepaciente(dto.getNomepaciente());
        producao.setCbo(dto.getCbo());
        producao.setUnidade(dto.getUnidade());
    }

    // Mesma logica dual-formato ja usada em AfaturarRepository (data pode vir
    // como "AAAA-MM-DD..." ou "DD/MM/AAAA").
    private String derivarCompetencia(Afaturar producao) {
        String data = texto(producao.getData());
        if (data.isBlank()) {
            return null;
        }
        Matcher iso = DATA_ISO.matcher(data);
        if (iso.find()) {
            return iso.group(1) + iso.group(2);
        }
        Matcher br = DATA_BR.matcher(data);
        if (br.find()) {
            return br.group(2) + br.group(1);
        }
        return null;
    }

    // A outra origem da linha. O CNS e copiado do cadastro quando a producao nasce, entao
    // cadastro corrigido depois (ou copia que pegou lixo do tipo 000000000000000) deixava a
    // linha em critica por um numero que o proprio sistema ja tinha certo em outro lugar -
    // e revalidar nao adiantava, porque so reconferia o que estava gravado na linha.
    //
    // Conservador de proposito, na mesma regra de sincronizarCnsDoCadastro: so entra CNS
    // que passa no modulo 11, nunca sobrescreve um CNS valido, nunca inventa numero. Se o
    // cadastro tambem estiver errado, a linha continua em critica - e ai a correcao e no
    // CADSUS, que nenhum botao daqui substitui.
    private void recarregarCnsDoCadastro(Afaturar producao) {
        if (CnsUtils.valido(texto(producao.getCnsdopciente()))) {
            return;
        }
        if (producao.getIdpaciente() <= 0) {
            return;
        }
        Pacientes paciente = Pacientes.findById(producao.getIdpaciente());
        String cnsCadastro = paciente == null ? "" : texto(paciente.getCns());
        if (CnsUtils.valido(cnsCadastro)) {
            producao.setCnsdopciente(cnsCadastro);
        }
    }

    private void recarregarSigtapLocal(Afaturar producao, String competencia) {
        String procedimento = texto(producao.getProcedimento());
        if (procedimento.isBlank()) {
            return;
        }
        ProcedimentosSus sigtap = procedimentosSusRepository.filtrarPorProcedimentoNaCompetencia(procedimento,
                competencia);
        if (sigtap == null) {
            return;
        }
        producao.setDescricao(sigtap.getPa_dc());
        producao.setPa_sa(sigtap.getPa_sa());
        producao.setPa_sp(sigtap.getPa_sp());
        producao.setPa_sh(sigtap.getPa_sh());
        producao.setPa_tpreg(sigtap.getPa_tpreg());
        producao.setPa_eletiva(sigtap.getPa_eletiva());
    }

    // Puxa CBO e CNS do profissional do cadastro do servidor para a producao que esta
    // sem eles.
    //
    // A producao copia matricula e nome do servidor quando nasce e nunca mais rele o
    // cadastro. Quem preencheu o CBO do servidor depois continuava vendo "Revisao
    // recomendada" em toda a producao antiga, e o operador tinha de repetir o mesmo
    // codigo linha a linha em Editar - por isso a heranca acontece aqui, no mesmo ponto
    // em que recarregarCnsDoCadastro rele o cadastro do paciente.
    //
    // Mesma regra conservadora de la: so preenche o que esta vazio, nunca sobrescreve o
    // que a producao ja tem e nunca deduz ocupacao a partir do cargo - se o cadastro do
    // servidor tambem estiver sem CBO, o alerta continua e alguem precisa preencher.
    private void recarregarProfissionalDoCadastro(Afaturar producao) {
        boolean faltaCbo = texto(producao.getCbo()).isBlank();
        boolean faltaCns = !CnsUtils.valido(texto(producao.getCnsdoprofissional()));
        if (!faltaCbo && !faltaCns) {
            return;
        }
        // Sem matricula na linha, o nome e a unica ponte ate o cadastro - e sem ela a
        // heranca desistia caladamente. Era o que fazia produco de medico com CBO
        // preenchido no cadastro nascer em REVISAO_RECOMENDADA todo mes, esperando alguem
        // clicar num botao. So aceita nome que identifique UM servidor: homonimo (ou o
        // mesmo profissional com dois cadastros, que existe nesta base) nao e palpite que
        // valha a pena dar, porque CBO errado no BPA glosa igual a CBO em branco.
        String matricula = texto(producao.getMatriculaservidor());
        br.com.iv.entity.Servidor servidor;
        if (matricula.isBlank()) {
            String nome = texto(producao.getNomeservidor());
            if (nome.isBlank()) {
                return;
            }
            List<br.com.iv.entity.Servidor> candidatos = br.com.iv.entity.Servidor
                    .find("upper(trim(nome)) = ?1", nome.toUpperCase()).list();
            if (candidatos.size() != 1) {
                return;
            }
            servidor = candidatos.get(0);
        } else {
            servidor = br.com.iv.entity.Servidor.find("matricula", matricula).firstResult();
        }
        if (servidor == null) {
            return;
        }
        if (faltaCbo) {
            String cboCadastro = cboHerdavel(servidor.getCbo());
            if (!cboCadastro.isBlank()) {
                producao.setCbo(cboCadastro);
            }
        }
        if (faltaCns) {
            String cnsCadastro = texto(servidor.getCns());
            if (CnsUtils.valido(cnsCadastro)) {
                producao.setCnsdoprofissional(cnsCadastro);
            }
        }
    }

    // O CBO do BPA tem 6 posicoes numericas. O cadastro do servidor guarda o codigo como
    // a tela gravou, e ha registro antigo com separador ("2251-25"), entao a heranca
    // normaliza para digitos e so aceita o tamanho certo: copiar um campo meio preenchido
    // trocaria o alerta de CBO ausente por uma rejeicao do DATASUS depois do envio.
    static String cboHerdavel(String cboCadastro) {
        String digitos = cboCadastro == null ? "" : cboCadastro.replaceAll("\\D", "");
        return digitos.length() == 6 ? digitos : "";
    }

    private boolean possuiCnsPacienteValido(Afaturar producao) {
        return texto(producao.getCnsdopciente()).replaceAll("\\D", "").length() == 15;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String normalizarTipoRegistro(String valor) {
        String apenasDigitos = texto(valor).replaceAll("\\D", "");
        if (apenasDigitos.isBlank()) {
            return "";
        }
        return apenasDigitos.length() == 1 ? "0" + apenasDigitos : apenasDigitos;
    }
}

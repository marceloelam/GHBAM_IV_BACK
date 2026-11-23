package br.com.iv.service;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import br.com.iv.dto.ValidacaoResultado;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.entity.ProcedimentosSus;
import br.com.iv.enums.ResultadoValidacao;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import br.com.iv.repository.FaturamentoCompetenciaRepository;
import br.com.iv.repository.ProcedimentosSusRepository;

// Motor de validacao do faturamento SUS: checa o que da para checar com dado que
// existe localmente - CNS, CBO, CNES da unidade, existencia no SIGTAP local,
// competencia aberta e, desde a V139, sexo e faixa etaria do procedimento (campos
// que o tb_procedimento.txt do DATASUS ja trazia e a importacao descartava).
//
// O que ainda nao e checado depende de tabela de relacionamento do SIGTAP que a
// importacao nao traz (procedimento x CID, x CBO, x servico/classificacao,
// quantidade maxima por competencia). Continua como pendencia declarada em vez de
// regra adivinhada - ver secao 82 do escopo.
@ApplicationScoped
public class FaturamentoValidationService {

    // 1571 meses = 130 anos e 11 meses: o valor que o SIGTAP usa como "sem limite".
    private static final int LIMITE_SEM_RESTRICAO_MESES = 1571;

    @Inject
    ProcedimentosSusRepository procedimentosSusRepository;

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    @Inject
    FaturamentoCompetenciaRepository competenciaRepository;

    @Inject
    SigtapCidService sigtapCidService;

    public List<ValidacaoResultado> validar(Afaturar producao) {
        // O procedimento do SIGTAP e lido uma vez e repassado: tres regras dependem
        // dele (existencia, sexo e faixa etaria) e cada uma buscando por conta
        // triplicaria a consulta em cima da producao inteira da competencia.
        ProcedimentosSus sigtap = buscarSigtap(producao);
        List<ValidacaoResultado> resultados = new ArrayList<>();
        resultados.add(validarCnsPaciente(producao));
        resultados.add(validarCbo(producao));
        resultados.add(validarCnes(producao));
        resultados.add(validarProcedimentoSigtap(producao, sigtap));
        resultados.add(validarCompetencia(producao));
        resultados.add(validarSexoDoProcedimento(producao, sigtap));
        resultados.add(validarIdadeDoProcedimento(producao, sigtap));
        resultados.add(validarCidCompativel(producao));
        return resultados;
    }

    // CID x procedimento (rl_procedimento_cid do SIGTAP, tabela sigtap_procedimento_cid).
    // So BPA-I: e o instrumento que leva CID por atendimento e onde estava o buraco
    // (fisioterapia e demais multiprofissionais lancados com CID "0"). AIH/APAC tem
    // laudo proprio com CID. Procedimento sem lista no SIGTAP, ou tabela ainda nao
    // carregada para a competencia, nao e criticado.
    //
    // ALERTA, e nao ERRO, enquanto a regra e nova: a linha vai para REVISAO_RECOMENDADA,
    // aparece para conferencia e continua exportavel - avisa sem travar o fechamento.
    // Visivel no pacote para o teste: validar() inteiro exige o SIGTAP do banco.
    ValidacaoResultado validarCidCompativel(Afaturar producao) {
        String instrumento = normalizarInstrumento(producao.getInstrumentoRegistro());
        String cid = SigtapCidService.normalizarCid(producao.getCid10());
        if (!"BPAI".equals(instrumento) || sigtapCidService == null) {
            return new ValidacaoResultado("CID_INCOMPATIVEL", "cid10", ResultadoValidacao.SUCESSO,
                    "CID por procedimento so e checado em BPA-I.", cid, "nao se aplica");
        }
        String competencia = competenciaDa(producao);
        java.util.Set<String> aceitos = sigtapCidService.cidsAceitos(producao.getProcedimento(), competencia);
        if (aceitos.isEmpty()) {
            return new ValidacaoResultado("CID_INCOMPATIVEL", "cid10", ResultadoValidacao.SUCESSO,
                    "SIGTAP nao restringe CID para este procedimento.", cid, "livre");
        }
        if (cid.isEmpty() || cid.matches("0+")) {
            return new ValidacaoResultado("CID_AUSENTE", "cid10", ResultadoValidacao.ALERTA,
                    "Procedimento exige CID (SIGTAP) e o lancamento esta sem CID.", "", "CID compativel");
        }
        if (!sigtapCidService.compativel(producao.getProcedimento(), competencia, cid)) {
            return new ValidacaoResultado("CID_INCOMPATIVEL", "cid10", ResultadoValidacao.ALERTA,
                    "CID " + cid + " nao e aceito pelo SIGTAP para este procedimento.", cid, "CID compativel");
        }
        return new ValidacaoResultado("CID_INCOMPATIVEL", "cid10", ResultadoValidacao.SUCESSO,
                "CID compativel com o procedimento.", cid, "CID compativel");
    }

    // O SIGTAP e lido pela competencia da producao, nao "qualquer linha com este codigo":
    // com varias competencias instaladas, a busca sem competencia devolvia a tabela de
    // 2020 e as regras validavam contra valores e tipo de registro de seis anos atras.
    private ProcedimentosSus buscarSigtap(Afaturar producao) {
        String codigo = texto(producao.getProcedimento());
        if (codigo.isBlank()) {
            return null;
        }
        return procedimentosSusRepository.filtrarPorProcedimentoNaCompetencia(codigo, competenciaDa(producao));
    }

    // Competencia da producao, quando ja vinculada. Producao ainda sem competencia (a do
    // mes que ninguem abriu) cai no fallback do repositorio: a tabela mais recente.
    private String competenciaDa(Afaturar producao) {
        Integer idcompetencia = producao.getIdcompetencia();
        if (idcompetencia == null) {
            return null;
        }
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        return competencia == null ? null : competencia.getCompetencia();
    }

    private ValidacaoResultado validarCnsPaciente(Afaturar producao) {
        if ("BPAC".equals(normalizarInstrumento(producao.getInstrumentoRegistro()))) {
            // BPA-C e consolidado: nao leva identificacao do usuario no arquivo, entao
            // CNS preenchido aqui nao chega ao DATASUS e nao vale criticar.
            return new ValidacaoResultado("CNS_PACIENTE_AUSENTE", "cnsdopciente", ResultadoValidacao.SUCESSO,
                    "CNS do paciente nao obrigatorio para BPA-C.", texto(producao.getCnsdopciente()), "opcional");
        }
        String cns = texto(producao.getCnsdopciente());
        if (cns.isBlank()) {
            return new ValidacaoResultado("CNS_PACIENTE_AUSENTE", "cnsdopciente", ResultadoValidacao.ERRO,
                    "CNS do paciente nao informado.", "", "obrigatorio");
        }
        // Preenchido nao e o mesmo que valido: o DATASUS refaz o modulo 11 ao processar
        // a remessa, entao um numero errado aqui viraria APTO, entraria no arquivo e so
        // voltaria como rejeicao depois do envio - com a competencia ja fechada.
        if (!CnsUtils.valido(cns)) {
            return new ValidacaoResultado("CNS_PACIENTE_INVALIDO", "cnsdopciente", ResultadoValidacao.ERRO,
                    CnsUtils.motivoInvalido(cns), cns, "CNS valido (modulo 11)");
        }
        return new ValidacaoResultado("CNS_PACIENTE_AUSENTE", "cnsdopciente", ResultadoValidacao.SUCESSO,
                "CNS do paciente informado e valido.", cns, "obrigatorio");
    }

    private ValidacaoResultado validarCbo(Afaturar producao) {
        String cbo = texto(producao.getCbo());
        if (cbo.isBlank()) {
            return new ValidacaoResultado("CBO_AUSENTE", "cbo", ResultadoValidacao.ALERTA,
                    "CBO do profissional nao informado.", "", "recomendado");
        }
        return new ValidacaoResultado("CBO_AUSENTE", "cbo", ResultadoValidacao.SUCESSO,
                "CBO do profissional informado.", cbo, "recomendado");
    }

    private ValidacaoResultado validarCnes(Afaturar producao) {
        String unidade = texto(producao.getUnidade());
        if (unidade.isBlank()) {
            return new ValidacaoResultado("CNES_INDISPONIVEL", "unidade", ResultadoValidacao.ERRO,
                    "Producao sem unidade associada.", "", "obrigatorio");
        }
        ConfiguracaoUnidade configuracao = configuracaoUnidadeRepository.filtarPorUnidadeSigla(unidade)
                .stream()
                .filter(c -> unidade.equalsIgnoreCase(texto(c.getSigla())))
                .findFirst()
                .orElse(null);
        String cnes = configuracao == null ? "" : texto(configuracao.getCnesDoHospital());
        if (configuracao == null || cnes.isBlank()) {
            return new ValidacaoResultado("CNES_INDISPONIVEL", "cnes", ResultadoValidacao.ERRO,
                    "CNES nao configurado para a unidade " + unidade + ".", "", "obrigatorio");
        }
        return new ValidacaoResultado("CNES_INDISPONIVEL", "cnes", ResultadoValidacao.SUCESSO,
                "CNES configurado para a unidade.", cnes, "obrigatorio");
    }

    private ValidacaoResultado validarProcedimentoSigtap(Afaturar producao, ProcedimentosSus sus) {
        String codigo = texto(producao.getProcedimento());
        if (codigo.isBlank()) {
            return new ValidacaoResultado("PROCEDIMENTO_NAO_ENCONTRADO", "procedimento", ResultadoValidacao.ERRO,
                    "Codigo do procedimento nao informado.", "", "obrigatorio");
        }
        if (sus == null) {
            return new ValidacaoResultado("PROCEDIMENTO_NAO_ENCONTRADO", "procedimento", ResultadoValidacao.ERRO,
                    "Procedimento " + codigo + " nao encontrado no SIGTAP local.", codigo, "cadastrado no SIGTAP");
        }
        // TODO: confirmar regra oficial (Manual SIGTAP) - esta checagem confirma que o
        // codigo existe no SIGTAP local, mas ainda nao restringe pela competencia exata
        // da producao (ProcedimentosSusRepository nao expoe hoje uma busca por
        // pa_id+pa_dv+pa_cmp sem alterar consultas ja usadas por outras telas).
        return new ValidacaoResultado("PROCEDIMENTO_NAO_ENCONTRADO", "procedimento", ResultadoValidacao.SUCESSO,
                "Procedimento localizado no SIGTAP local.", codigo, "cadastrado no SIGTAP");
    }

    // Sexo exigido pelo procedimento (TP_SEXO do SIGTAP). E ERRO porque nao ha margem
    // de interpretacao: procedimento exclusivo de um sexo lancado para o outro volta
    // como rejeicao do DATASUS. So vale quando o SIGTAP restringe de fato (M ou F) e a
    // producao tem o sexo do paciente - competencia importada antes da V139 tem
    // tp_sexo nulo e nao critica nada.
    private ValidacaoResultado validarSexoDoProcedimento(Afaturar producao, ProcedimentosSus sus) {
        String exigido = sus == null ? "" : texto(sus.getTp_sexo());
        String sexo = texto(producao.getSexo());
        if (!sexoIncompativel(exigido, sexo)) {
            return new ValidacaoResultado("PROCEDIMENTO_SEXO_INCOMPATIVEL", "sexo", ResultadoValidacao.SUCESSO,
                    "Sexo do paciente compativel com o procedimento.", sexo, "compativel");
        }
        return new ValidacaoResultado("PROCEDIMENTO_SEXO_INCOMPATIVEL", "sexo", ResultadoValidacao.ERRO,
                "Procedimento " + texto(producao.getProcedimento()) + " e exclusivo do sexo "
                        + exigido.toUpperCase() + " e o paciente esta como " + sexo.toUpperCase() + ".",
                sexo, exigido.toUpperCase());
    }

    // Faixa etaria do procedimento (VL_IDADE_MINIMA/VL_IDADE_MAXIMA do SIGTAP).
    //
    // ALERTA, e nao ERRO, de proposito: o SIGTAP tem procedimento cuja faixa nao esta
    // em anos (pediatria/neonatal), e criticar como erro travaria o fechamento de
    // producao correta. Como alerta, a linha vai para REVISAO_RECOMENDADA - aparece
    // para conferencia e continua exportavel.
    private ValidacaoResultado validarIdadeDoProcedimento(Afaturar producao, ProcedimentosSus sus) {
        Integer minima = sus == null ? null : sus.getVl_idade_minima();
        Integer maxima = sus == null ? null : sus.getVl_idade_maxima();
        int idade = producao.getIdade();
        if (!idadeForaDaFaixa(minima, maxima, idade)) {
            return new ValidacaoResultado("PROCEDIMENTO_IDADE_FORA_FAIXA", "idade", ResultadoValidacao.SUCESSO,
                    "Idade do paciente dentro da faixa do procedimento.", String.valueOf(idade), "dentro da faixa");
        }
        return new ValidacaoResultado("PROCEDIMENTO_IDADE_FORA_FAIXA", "idade", ResultadoValidacao.ALERTA,
                "Idade " + idade + " fora da faixa do procedimento no SIGTAP (" + faixa(minima, maxima)
                        + "). Confira antes de fechar a competencia.",
                String.valueOf(idade), faixa(minima, maxima));
    }

    // M ou F no SIGTAP restringem; qualquer outro valor (I, N, vazio) aceita os dois.
    // Producao sem sexo do paciente nao gera critica aqui - a falta do dado e outro
    // problema, e afirmar incompatibilidade sem conhecer o sexo seria chute.
    static boolean sexoIncompativel(String tpSexoSigtap, String sexoPaciente) {
        String exigido = inicial(tpSexoSigtap);
        String paciente = inicial(sexoPaciente);
        if (!"M".equals(exigido) && !"F".equals(exigido)) {
            return false;
        }
        if (paciente.isBlank()) {
            return false;
        }
        return !exigido.equals(paciente);
    }

    // Faixa etaria do SIGTAP em MESES, nao em anos - confirmado nos dados da competencia
    // 202608: o teto que aparece em 3205 procedimentos e 1571 (130 anos e 11 meses, ou
    // seja "sem limite"), e os pisos reais sao 228 (19 anos), 216 (18), 144 (12) e 120
    // (10). Comparar a idade da producao, que vem em anos, direto com esses numeros
    // reprovaria todo adulto em procedimento com piso - alerta em massa e errado.
    //
    // A idade e convertida para meses por anos*12. Isso subestima ate 11 meses, o que e
    // proposital: na duvida a linha passa. Um alerta a menos e melhor que um alerta falso
    // em cima de producao correta.
    static boolean idadeForaDaFaixa(Integer minimaMeses, Integer maximaMeses, int idadeAnos) {
        if (idadeAnos <= 0) {
            return false;
        }
        int idadeMeses = idadeAnos * 12;
        if (limiteReal(minimaMeses) && idadeMeses < minimaMeses) {
            return true;
        }
        if (limiteReal(maximaMeses) && idadeMeses > maximaMeses) {
            return true;
        }
        return false;
    }

    // 9999 e o "nao informado" do layout e 1571 meses (130 anos) e o teto que o SIGTAP usa
    // para dizer "sem limite". Tratar qualquer um dos dois como limite real reprovaria
    // procedimento que nao tem restricao nenhuma.
    private static boolean limiteReal(Integer meses) {
        return meses != null && meses > 0 && meses < LIMITE_SEM_RESTRICAO_MESES;
    }

    // Mensagem para o operador: o SIGTAP guarda em meses, mas quem le a critica raciocina
    // em anos.
    private String faixa(Integer minimaMeses, Integer maximaMeses) {
        return "de " + emAnos(minimaMeses, "sem minimo") + " a " + emAnos(maximaMeses, "sem maximo");
    }

    private String emAnos(Integer meses, String quandoSemLimite) {
        if (!limiteReal(meses)) {
            return quandoSemLimite;
        }
        int anos = meses / 12;
        int resto = meses % 12;
        if (anos == 0) {
            return resto + (resto == 1 ? " mes" : " meses");
        }
        return resto == 0 ? anos + (anos == 1 ? " ano" : " anos") : anos + "a" + resto + "m";
    }

    private static String inicial(String valor) {
        String texto = valor == null ? "" : valor.trim().toUpperCase();
        return texto.isBlank() ? "" : texto.substring(0, 1);
    }

    private ValidacaoResultado validarCompetencia(Afaturar producao) {
        Integer idcompetencia = producao.getIdcompetencia();
        if (idcompetencia == null) {
            return new ValidacaoResultado("COMPETENCIA_NAO_DEFINIDA", "idcompetencia", ResultadoValidacao.ERRO,
                    "Producao sem competencia associada.", "", "competencia aberta");
        }
        FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
        if (competencia == null) {
            return new ValidacaoResultado("COMPETENCIA_NAO_DEFINIDA", "idcompetencia", ResultadoValidacao.ERRO,
                    "Competencia associada nao encontrada.", String.valueOf(idcompetencia), "competencia aberta");
        }
        boolean aberta = "ABERTA".equals(competencia.getStatus()) || "EM_PROCESSAMENTO".equals(competencia.getStatus())
                || "EM_AUDITORIA".equals(competencia.getStatus());
        if (!aberta) {
            return new ValidacaoResultado("COMPETENCIA_NAO_ABERTA", "idcompetencia", ResultadoValidacao.ERRO,
                    "Competencia " + competencia.getCompetencia() + " nao esta aberta (status "
                            + competencia.getStatus() + ").",
                    competencia.getStatus(), "ABERTA");
        }
        return new ValidacaoResultado("COMPETENCIA_NAO_ABERTA", "idcompetencia", ResultadoValidacao.SUCESSO,
                "Competencia " + competencia.getCompetencia() + " aberta.", competencia.getStatus(), "ABERTA");
    }

    private String normalizarInstrumento(String valor) {
        return texto(valor).toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}

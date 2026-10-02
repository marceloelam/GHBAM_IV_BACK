package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.iv.dto.ValidacaoResultado;
import br.com.iv.entity.Afaturar;
import br.com.iv.enums.ResultadoValidacao;

// Regras que entraram com a V139 (sexo e faixa etaria do SIGTAP). Sao testadas pelas
// funcoes puras porque a decisao mora nelas - o resto do metodo so monta a mensagem.
class FaturamentoValidationServiceTest {

    private final FaturamentoValidationService service = new FaturamentoValidationService();

    // As 5 regras abaixo (validar()) tem parte da logica atras de repositorio injetado
    // (ConfiguracaoUnidade, ProcedimentosSus, FaturamentoCompetencia via Panache). Sem
    // Mockito/Testcontainers no projeto, o service e construido manualmente (repositorios
    // ficam null) e os casos testados sao os que retornam ANTES de tocar o repositorio -
    // exatamente os casos de "ausencia" que a auditoria apontou sem cobertura: CNS
    // ausente/invalido, CNES indisponivel (sem unidade), SIGTAP sem codigo e competencia
    // nao definida. Os ramos que dependem de dado no banco (CNES cadastrado para outra
    // unidade, SIGTAP com o codigo, competencia fechada) continuam exigindo um teste de
    // integracao (@QuarkusTest com banco) - fora do escopo desta correcao.
    private ValidacaoResultado regra(Afaturar producao, String codigo) {
        List<ValidacaoResultado> resultados = service.validar(producao);
        return resultados.stream()
                .filter(r -> codigo.equals(r.codigo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Regra " + codigo + " nao apareceu em validar()."));
    }

    // validarCnsPaciente reaproveita o codigo "CNS_PACIENTE_AUSENTE" tambem para o caso
    // valido (ver FaturamentoValidationService) - "CNS_PACIENTE_INVALIDO" so aparece
    // quando o CNS esta preenchido e nao fecha o modulo 11. Por isso os testes de CNS
    // filtram pelo campo, que e o mesmo ("cnsdopciente") nos quatro ramos do metodo.
    private ValidacaoResultado regraDoCns(Afaturar producao) {
        List<ValidacaoResultado> resultados = service.validar(producao);
        return resultados.stream()
                .filter(r -> "cnsdopciente".equals(r.campo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Regra de CNS nao apareceu em validar()."));
    }

    @Test
    void cnsAusenteEErroQuandoInstrumentoNaoEBpaC() {
        Afaturar producao = new Afaturar();
        producao.setInstrumentoRegistro("BPA_I");
        producao.setCnsdopciente("");

        ValidacaoResultado resultado = regraDoCns(producao);
        assertEquals("CNS_PACIENTE_AUSENTE", resultado.codigo());
        assertEquals(ResultadoValidacao.ERRO, resultado.resultado());
    }

    @Test
    void cnsComDigitoVerificadorErradoEErro() {
        Afaturar producao = new Afaturar();
        producao.setInstrumentoRegistro("BPA_I");
        // Mesmos 11 primeiros digitos do CNS valido usado no teste seguinte, com o
        // digito verificador alterado - a conta do modulo 11 nao fecha.
        producao.setCnsdopciente("100000000010009");

        ValidacaoResultado resultado = regraDoCns(producao);
        assertEquals("CNS_PACIENTE_INVALIDO", resultado.codigo());
        assertEquals(ResultadoValidacao.ERRO, resultado.resultado());
    }

    @Test
    void cnsValidoPassaNaValidacao() {
        Afaturar producao = new Afaturar();
        producao.setInstrumentoRegistro("BPA_I");
        // CNS definitivo (inicia em 1) calculado pelo modulo 11 do proprio CnsUtils.
        producao.setCnsdopciente("100000000010002");

        ValidacaoResultado resultado = regraDoCns(producao);
        assertEquals(ResultadoValidacao.SUCESSO, resultado.resultado());
    }

    // BPA-C e consolidado - nao leva identificacao do paciente no arquivo, entao CNS
    // ausente ali nao pode virar erro de fechamento.
    @Test
    void cnsNaoObrigatorioParaBpaC() {
        Afaturar producao = new Afaturar();
        producao.setInstrumentoRegistro("BPA_C");
        producao.setCnsdopciente("");

        ValidacaoResultado resultado = regraDoCns(producao);
        assertEquals(ResultadoValidacao.SUCESSO, resultado.resultado());
    }

    @Test
    void cboAusenteEAlertaNaoErro() {
        Afaturar producao = new Afaturar();
        producao.setInstrumentoRegistro("BPA_C");
        producao.setCbo("");

        ValidacaoResultado resultado = regra(producao, "CBO_AUSENTE");
        assertEquals(ResultadoValidacao.ALERTA, resultado.resultado());
    }

    @Test
    void cnesIndisponivelQuandoProducaoSemUnidade() {
        Afaturar producao = new Afaturar();
        producao.setInstrumentoRegistro("BPA_C");
        producao.setUnidade("");

        ValidacaoResultado resultado = regra(producao, "CNES_INDISPONIVEL");
        assertEquals(ResultadoValidacao.ERRO, resultado.resultado());
    }

    @Test
    void procedimentoForaDoSigtapQuandoCodigoNaoInformado() {
        Afaturar producao = new Afaturar();
        producao.setInstrumentoRegistro("BPA_C");
        producao.setProcedimento("");

        ValidacaoResultado resultado = regra(producao, "PROCEDIMENTO_NAO_ENCONTRADO");
        assertEquals(ResultadoValidacao.ERRO, resultado.resultado());
    }

    @Test
    void competenciaNaoDefinidaQuandoProducaoSemIdcompetencia() {
        Afaturar producao = new Afaturar();
        producao.setInstrumentoRegistro("BPA_C");
        producao.setIdcompetencia(null);

        ValidacaoResultado resultado = regra(producao, "COMPETENCIA_NAO_DEFINIDA");
        assertEquals(ResultadoValidacao.ERRO, resultado.resultado());
    }

    @Test
    void sexoIncompativelQuandoProcedimentoExclusivoDoOutroSexo() {
        assertTrue(FaturamentoValidationService.sexoIncompativel("F", "M"));
        assertTrue(FaturamentoValidationService.sexoIncompativel("M", "F"));
    }

    @Test
    void sexoCompativelQuandoIgual() {
        assertFalse(FaturamentoValidationService.sexoIncompativel("F", "F"));
        assertFalse(FaturamentoValidationService.sexoIncompativel("M", "M"));
    }

    // Procedimento sem restricao no SIGTAP (I, N ou vazio) aceita os dois sexos, e
    // competencia importada antes da V139 tem tp_sexo nulo.
    @Test
    void procedimentoSemRestricaoNaoCritica() {
        assertFalse(FaturamentoValidationService.sexoIncompativel("I", "M"));
        assertFalse(FaturamentoValidationService.sexoIncompativel("N", "F"));
        assertFalse(FaturamentoValidationService.sexoIncompativel("", "M"));
        assertFalse(FaturamentoValidationService.sexoIncompativel(null, "M"));
    }

    // Sem o sexo do paciente nao da para afirmar incompatibilidade - seria chute.
    @Test
    void producaoSemSexoDoPacienteNaoCritica() {
        assertFalse(FaturamentoValidationService.sexoIncompativel("F", ""));
        assertFalse(FaturamentoValidationService.sexoIncompativel("F", null));
    }

    // A faixa do SIGTAP vem em MESES (confirmado na competencia 202608: pisos de 228, 216,
    // 144 e 120 meses = 19, 18, 12 e 10 anos). A idade da producao vem em anos.
    @Test
    void idadeAbaixoDoPisoEmMesesFicaForaDaFaixa() {
        // piso 228 meses = 19 anos; paciente de 12 anos esta abaixo
        assertTrue(FaturamentoValidationService.idadeForaDaFaixa(228, 1571, 12));
    }

    @Test
    void idadeAcimaDoTetoEmMesesFicaForaDaFaixa() {
        // teto 227 meses = 18 anos e 11 meses; paciente de 19 anos passa do teto
        assertTrue(FaturamentoValidationService.idadeForaDaFaixa(0, 227, 19));
    }

    // O caso que motivou a correcao: adulto em procedimento com piso de 19 anos. Comparar
    // 30 (anos) com 228 (meses) daria alerta em 159 procedimentos da competencia.
    @Test
    void adultoDentroDaFaixaEmMesesNaoCritica() {
        assertFalse(FaturamentoValidationService.idadeForaDaFaixa(228, 1571, 30));
        assertFalse(FaturamentoValidationService.idadeForaDaFaixa(228, 1571, 19));
        assertFalse(FaturamentoValidationService.idadeForaDaFaixa(144, 1571, 12));
    }

    // 1571 meses (130 anos) e 9999 sao os "sem limite" do SIGTAP.
    @Test
    void limiteAusenteNaoCritica() {
        assertFalse(FaturamentoValidationService.idadeForaDaFaixa(null, null, 70));
        assertFalse(FaturamentoValidationService.idadeForaDaFaixa(0, 0, 70));
        assertFalse(FaturamentoValidationService.idadeForaDaFaixa(0, 1571, 120));
        assertFalse(FaturamentoValidationService.idadeForaDaFaixa(9999, 9999, 70));
    }

    // Idade nao informada na producao chega como 0 (o campo e int).
    @Test
    void idadeNaoInformadaNaProducaoNaoCritica() {
        assertFalse(FaturamentoValidationService.idadeForaDaFaixa(228, 1571, 0));
    }
}

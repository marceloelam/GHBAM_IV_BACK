package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.Afaturar;
import br.com.iv.enums.InstrumentoRegistro;
import br.com.iv.enums.ProducaoStatus;

class FaturamentoProducaoServiceTest {

    private final FaturamentoProducaoService service = new FaturamentoProducaoService();

    @Test
    void classificaComoBpaIndividualizadoQuandoTipoRegistroForDois() {
        Afaturar producao = new Afaturar();
        producao.setPa_tpreg("02");
        producao.setPa_sa(10);

        assertEquals(InstrumentoRegistro.BPA_I, service.classificarInstrumento(producao));
    }

    @Test
    void classificaComoBpaConsolidadoQuandoTipoRegistroForUm() {
        Afaturar producao = new Afaturar();
        producao.setPa_tpreg("01");
        producao.setPa_sa(10);

        assertEquals(InstrumentoRegistro.BPA_C, service.classificarInstrumento(producao));
    }

    @Test
    void classificaComoNaoFaturavelQuandoNaoHaValorSigtap() {
        Afaturar producao = new Afaturar();
        producao.setPa_tpreg("");
        producao.setPa_sa(0);
        producao.setPa_sp(0);
        producao.setPa_sh(0);

        assertEquals(InstrumentoRegistro.NAO_FATURAVEL, service.classificarInstrumento(producao));
    }

    // Com valor SIGTAP e pa_tpreg indefinido a producao nao fica mais presa em
    // PENDENTE_CLASSIFICACAO: cai no CNS individual do paciente (ver
    // FaturamentoProducaoService.classificarInstrumento).
    @Test
    void classificaComoBpaConsolidadoQuandoHaValorSemTipoRegistroESemCnsValido() {
        Afaturar producao = new Afaturar();
        producao.setPa_tpreg("");
        producao.setPa_sh(50);

        assertEquals(InstrumentoRegistro.BPA_C, service.classificarInstrumento(producao));
    }

    @Test
    void classificaComoBpaIndividualizadoQuandoHaValorSemTipoRegistroMasComCnsValido() {
        Afaturar producao = new Afaturar();
        producao.setPa_tpreg("");
        producao.setPa_sh(50);
        producao.setCnsdopciente("000000000000000");

        assertEquals(InstrumentoRegistro.BPA_I, service.classificarInstrumento(producao));
    }

    // 03 a 07 sao todos AIH (principal, especial e secundario) - a correcao que trocou a
    // leitura anterior, que tratava 03 como BPA-I e 05/06/07 como APAC (ver comentario de
    // classificarInstrumento). Sem estes testes essa reclassificacao inteira ficava sem
    // cobertura, apesar de ser a que mais mexeu com dinheiro (R$ 112.460,38 na 202607).
    @Test
    void classificaComoAihQuandoTipoRegistroForDeTresASete() {
        for (String tpreg : new String[] {"03", "04", "05", "06", "07"}) {
            Afaturar producao = new Afaturar();
            producao.setPa_tpreg(tpreg);
            producao.setPa_sh(100);

            assertEquals(InstrumentoRegistro.AIH, service.classificarInstrumento(producao),
                    "pa_tpreg=" + tpreg + " deveria classificar como AIH");
        }
    }

    @Test
    void classificaComoRaasQuandoTipoRegistroForOitoOuNove() {
        for (String tpreg : new String[] {"08", "09"}) {
            Afaturar producao = new Afaturar();
            producao.setPa_tpreg(tpreg);
            producao.setPa_sa(30);

            assertEquals(InstrumentoRegistro.RAAS, service.classificarInstrumento(producao),
                    "pa_tpreg=" + tpreg + " deveria classificar como RAAS");
        }
    }

    // Ordem de decisao e conservadora: 02 (BPA-I) e 01 (BPA-C) vem antes de AIH/RAAS mesmo
    // quando o campo traz varios codigos concatenados (rl_procedimento_registro e N:N).
    @Test
    void bpaIndividualizadoTemPrecedenciaSobreAihQuandoRegistroTrazVariosCodigos() {
        Afaturar producao = new Afaturar();
        producao.setPa_tpreg("0203");
        producao.setPa_sa(10);

        assertEquals(InstrumentoRegistro.BPA_I, service.classificarInstrumento(producao));
    }

    // Heranca do CBO do cadastro do servidor (recarregarProfissionalDoCadastro): o codigo
    // so vai para a producao com as 6 posicoes que o BPA exige.
    @Test
    void herdaCboDoCadastroQuandoTemSeisDigitos() {
        assertEquals("225125", FaturamentoProducaoService.cboHerdavel("225125"));
    }

    @Test
    void herdaCboDoCadastroIgnorandoSeparador() {
        assertEquals("225125", FaturamentoProducaoService.cboHerdavel("2251-25"));
    }

    // Cadastro vazio ou meio preenchido nao vira CBO: melhor manter o alerta de CBO
    // ausente do que exportar um codigo que o DATASUS rejeita.
    @Test
    void naoHerdaCboIncompletoNemVazio() {
        assertEquals("", FaturamentoProducaoService.cboHerdavel("2251"));
        assertEquals("", FaturamentoProducaoService.cboHerdavel(""));
        assertEquals("", FaturamentoProducaoService.cboHerdavel(null));
    }

    // Status da producao a partir do instrumento e do resultado da validacao. Vale
    // principalmente para a porta de entrada (prepararProducaoNova): antes dela a
    // producao dos modulos assistenciais nascia APTO_FATURAMENTO sem validacao nenhuma.
    @Test
    void producaoSemCriticaNasceApta() {
        assertEquals(ProducaoStatus.APTO_FATURAMENTO.name(),
                FaturamentoProducaoService.statusPara(InstrumentoRegistro.BPA_I, false, false));
    }

    @Test
    void erroDeValidacaoViraProducaoComCritica() {
        assertEquals(ProducaoStatus.PRODUCAO_COM_CRITICA.name(),
                FaturamentoProducaoService.statusPara(InstrumentoRegistro.BPA_I, true, false));
    }

    @Test
    void alertaSozinhoViraRevisaoRecomendada() {
        assertEquals(ProducaoStatus.REVISAO_RECOMENDADA.name(),
                FaturamentoProducaoService.statusPara(InstrumentoRegistro.BPA_I, false, true));
    }

    // Instrumento terminal manda sobre a validacao: nao adianta dizer que uma linha nao
    // faturavel esta em critica - nao ha o que corrigir nela.
    @Test
    void instrumentoTerminalTemPrecedenciaSobreAValidacao() {
        assertEquals(ProducaoStatus.NAO_FATURAVEL.name(),
                FaturamentoProducaoService.statusPara(InstrumentoRegistro.NAO_FATURAVEL, true, true));
        assertEquals(ProducaoStatus.PENDENTE_CLASSIFICACAO.name(),
                FaturamentoProducaoService.statusPara(InstrumentoRegistro.PENDENTE_CLASSIFICACAO, true, true));
    }
}

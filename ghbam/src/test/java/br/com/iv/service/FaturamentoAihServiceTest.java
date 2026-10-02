package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.text.ParseException;

import org.junit.jupiter.api.Test;
import br.com.iv.entity.FaturamentoAih;
import br.com.iv.entity.Internacao;
import br.com.iv.entity.NovoInternado;

// O backend nao tem banco de teste isolado (dev e copia da producao - ver
// FhirSumarioAltaMapperServiceTest), entao Internacao.findById/NovoInternado.find (finders
// estaticos do Panache) sao isolados por metodos auxiliares em vez de bater no banco real.
//
// Cobre a correcao de 2026-09-22: criarPreAih decidia o status so por
// Internacao.datasaida, que fica quase sempre nula na pratica - toda pre-AIH nascia
// EM_INTERNACAO e so saia dali quando alguem abria a listagem (promoverContasComAlta, que
// ja conferia as duas fontes). dataDaAlta agora e o ponto unico de decisao, usado nos dois
// lugares - por isso testa-lo aqui cobre as duas correcoes de uma vez.
class FaturamentoAihServiceTest {

    private final FaturamentoAihService service = spy(new FaturamentoAihService());

    private FaturamentoAih aih(int idinternacao, String bam) {
        FaturamentoAih aih = new FaturamentoAih();
        aih.setIdInternacao(idinternacao);
        aih.setBam(bam);
        return aih;
    }

    @Test
    void usaDatasaidaDaInternacaoQuandoPreenchida() throws ParseException {
        int idinternacao = 10;
        Internacao internacao = new Internacao();
        internacao.setDatasaida(LocalDate.of(2026, 9, 1));

        doReturn(internacao).when(service).buscarInternacao(idinternacao);

        LocalDateTime resultado = service.dataDaAlta(aih(idinternacao, "12345"));

        assertEquals(LocalDate.of(2026, 9, 1).atStartOfDay(ZoneId.systemDefault()).toLocalDateTime(), resultado);
    }

    // O caso que motivou a correcao: Internacao.datasaida nula, mas o censo (NovoInternado)
    // ja registrou a alta. Antes disto o metodo devolvia null aqui e a pre-AIH nascia presa
    // em EM_INTERNACAO.
    @Test
    void usaAltaDoCensoQuandoDatasaidaDaInternacaoEstaNula() throws ParseException {
        int idinternacao = 11;
        Internacao internacao = new Internacao();
        // datasaida nao preenchida de proposito - e o caso comum na pratica.

        NovoInternado noCenso = new NovoInternado();
        noCenso.setAlta(true);
        noCenso.setDatadaacao(LocalDate.of(2026, 9, 2));

        doReturn(internacao).when(service).buscarInternacao(idinternacao);
        doReturn(noCenso).when(service).buscarInternadoComAlta(54321);

        LocalDateTime resultado = service.dataDaAlta(aih(idinternacao, "54321"));

        assertEquals(LocalDate.of(2026, 9, 2).atStartOfDay(ZoneId.systemDefault()).toLocalDateTime(), resultado);
    }

    // Sem alta em nenhuma das duas fontes o metodo continua devolvendo null - e esse null
    // que faz criarPreAih/promoverContasComAlta manterem o status EM_INTERNACAO
    // corretamente, para quem de fato ainda esta internado.
    @Test
    void devolveNuloQuandoNaoHaAltaEmNenhumaFonte() throws ParseException {
        int idinternacao = 12;
        Internacao internacao = new Internacao();

        doReturn(internacao).when(service).buscarInternacao(idinternacao);
        doReturn(null).when(service).buscarInternadoComAlta(99999);

        LocalDateTime resultado = service.dataDaAlta(aih(idinternacao, "99999"));

        assertNull(resultado);
    }

    // BAM sem digito nenhum (somenteDigitos devolve 0) nao tem por onde consultar o censo -
    // devolve null sem nem tentar NovoInternado.find, em vez de estourar excecao.
    @Test
    void devolveNuloQuandoBamNaoTemDigitos() throws ParseException {
        int idinternacao = 13;
        Internacao internacao = new Internacao();

        doReturn(internacao).when(service).buscarInternacao(idinternacao);

        LocalDateTime resultado = service.dataDaAlta(aih(idinternacao, ""));

        assertNull(resultado);
    }
}

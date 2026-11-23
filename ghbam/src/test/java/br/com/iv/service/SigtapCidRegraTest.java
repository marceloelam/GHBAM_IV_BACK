package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.iv.dto.ValidacaoResultado;
import br.com.iv.entity.Afaturar;
import br.com.iv.enums.ResultadoValidacao;

// Regra CID x procedimento (rl_procedimento_cid do SIGTAP) no motor do faturamento.
// O SigtapCidService e substituido por uma lista fixa: a decisao da regra nao depende
// do banco, so do que o SIGTAP aceita para o procedimento.
class SigtapCidRegraTest {

    private static final String FISIO = "0302050027";

    private final FaturamentoValidationService service = new FaturamentoValidationService();

    SigtapCidRegraTest() {
        service.sigtapCidService = new SigtapCidService() {
            @Override
            public Set<String> cidsAceitos(String procedimento, String competencia) {
                return SigtapCidService.chave(procedimento).equals("030205002") ? Set.of("G819", "M62", "S720") : Set.of();
            }
        };
    }

    private ValidacaoResultado regra(String instrumento, String procedimento, String cid) {
        Afaturar p = new Afaturar();
        p.setInstrumentoRegistro(instrumento);
        p.setProcedimento(procedimento);
        p.setCid10(cid);
        return service.validarCidCompativel(p);
    }

    @Test
    void fisioterapiaComCidZeroViraAlertaDeCidAusente() {
        ValidacaoResultado r = regra("BPA_I", FISIO, "0");
        assertEquals("CID_AUSENTE", r.codigo());
        assertEquals(ResultadoValidacao.ALERTA, r.resultado());
        assertEquals(ResultadoValidacao.ALERTA, regra("BPA_I", FISIO, "").resultado());
        assertEquals(ResultadoValidacao.ALERTA, regra("BPA_I", FISIO, "0000").resultado());
    }

    @Test
    void cidForaDaListaDoSigtapViraAlertaDeIncompativel() {
        ValidacaoResultado r = regra("BPA_I", FISIO, "O80");
        assertEquals("CID_INCOMPATIVEL", r.codigo());
        assertEquals(ResultadoValidacao.ALERTA, r.resultado());
    }

    @Test
    void cidAceitoPassaComPontoOuSubcategoriaDeCategoriaAceita() {
        assertEquals(ResultadoValidacao.SUCESSO, regra("BPA_I", FISIO, "G81.9").resultado());
        assertEquals(ResultadoValidacao.SUCESSO, regra("BPA_I", FISIO, "M621").resultado()); // lista traz M62
    }

    @Test
    void naoCriticaForaDoBpaIOuProcedimentoSemLista() {
        assertEquals(ResultadoValidacao.SUCESSO, regra("BPA_C", FISIO, "0").resultado());
        assertEquals(ResultadoValidacao.SUCESSO, regra("AIH", FISIO, "").resultado());
        assertEquals(ResultadoValidacao.SUCESSO, regra("BPA_I", "0301010072", "0").resultado());
    }

    @Test
    void semServicoInjetadoNaoQuebraOMotor() {
        FaturamentoValidationService semCid = new FaturamentoValidationService();
        Afaturar p = new Afaturar();
        p.setInstrumentoRegistro("BPA_I");
        p.setProcedimento(FISIO);
        p.setCid10("0");
        assertEquals(ResultadoValidacao.SUCESSO, semCid.validarCidCompativel(p).resultado());
    }

    @Test
    void leLinhaDoLayoutOficialDoDatasus() {
        SigtapCidService.Linha l = SigtapCidService.interpretar("0201010038C73 S202611", "202611");
        assertEquals("0201010038", l.procedimento());
        assertEquals("C73", l.cid());
        assertTrue(l.principal());
        assertNull(SigtapCidService.interpretar("0201010038C73 S202610", "202611"));
        assertNull(SigtapCidService.interpretar("curta", null));
    }
}

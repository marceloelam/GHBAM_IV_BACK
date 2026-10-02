package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

// A consulta ao CNES le HTML de terceiro, e HTML de terceiro muda sem aviso.
// Estes testes travam o contrato de leitura contra amostras do markup real do
// cnesadm (aspas duplas, tags auto-fechadas com " />", atributos fora de ordem):
// se o DATASUS mexer no layout, quebra aqui e nao em producao, no meio de um
// cadastro de paciente.
class ConsultaCnsServiceTest {

    // Formato observado na pagina publica do cnesadm em 19/08/2026.
    private static final String PAGINA_ENCONTRADO = """
            <form method="post">
             <div class="form-group">
                <label for="cpf">CPF</label>
                <input id="cpf" name="cpf" class="form-control" type="text" value="000.000.000-00" readonly="readonly"/>
                <label for="dataNascimento">Data de Nascimento</label>
                <input id="dataNascimento" name="dataNascimento" type="text" value="01/01/1980" readonly/>
                <label for="cns">CNS</label>
                <input id="cns" name="cns" type="text" value="198765432100003" readonly="readonly"/>
                <label for="nome">Nome</label>
                <input id="nome" name="nome" type="text" value="FULANO DE TAL" readonly="readonly"/>
                <input type="text" name="email" class="form-control" value=""/>
                <input type="hidden" name="_csrf" value="15cfad5f-8eec-4550-b8eb-553c3c7ff15a" />
             </div>
            </form>
            """;

    private static final String PAGINA_NAO_ENCONTRADO = """
            <div class="alert">
             <ul class="text-red">
                <li>CPF nao encontrado na base do CNES.</li>
                <li><strong>Verifique</strong> a data de nascimento informada.</li>
             </ul>
            </div>
            """;

    @Test
    void leSomenteOsCamposTravadosPeloCnes() {
        Map<String, String> campos = ConsultaCnsService.extrairCamposTravados(PAGINA_ENCONTRADO);

        assertEquals("198765432100003", campos.get("cns"));
        assertEquals("FULANO DE TAL", campos.get("nome"));
        assertEquals("000.000.000-00", campos.get("cpf"));
        assertEquals("01/01/1980", campos.get("dataNascimento"));
        // email e editavel e _csrf e da sessao: nada disso e resposta da consulta.
        assertEquals(4, campos.size());
    }

    @Test
    void oCnsLidoDaPaginaPassaNaValidacaoDoModulo11() {
        String cns = ConsultaCnsService.extrairCamposTravados(PAGINA_ENCONTRADO).get("cns");
        assertTrue(CnsUtils.valido(cns), CnsUtils.motivoInvalido(cns));
    }

    @Test
    void naoInventaCamposQuandoNadaFoiResolvido() {
        assertTrue(ConsultaCnsService.extrairCamposTravados(PAGINA_NAO_ENCONTRADO).isEmpty());
    }

    @Test
    void repassaAsMensagensDoCnesSemMarcacao() {
        List<String> mensagens = ConsultaCnsService.extrairMensagens(PAGINA_NAO_ENCONTRADO);

        assertEquals(2, mensagens.size());
        assertEquals("CPF nao encontrado na base do CNES.", mensagens.get(0));
        assertEquals("Verifique a data de nascimento informada.", mensagens.get(1));
    }

    @Test
    void paginaComResultadoNaoTemMensagemDeErro() {
        assertTrue(ConsultaCnsService.extrairMensagens(PAGINA_ENCONTRADO).isEmpty());
    }

    @Test
    void mascaraOCpfComoOFormularioDoCnesEspera() {
        assertEquals("529.982.247-25", ConsultaCnsService.formatarCpf("52998224725"));
        assertEquals("529.982.247-25", ConsultaCnsService.formatarCpf("529.982.247-25"));
        assertNull(ConsultaCnsService.formatarCpf("5299822472"));
        assertNull(ConsultaCnsService.formatarCpf(""));
        assertNull(ConsultaCnsService.formatarCpf(null));
    }

    // A recepcao digita dd/mm/aaaa, mas o mesmo paciente vindo do banco chega em
    // aaaa-mm-dd: os dois precisam sair no formato que o CNES aceita.
    @Test
    void aceitaAsDuasFormasDeDataQueCirculamNoSistema() {
        assertEquals("07/09/1975", ConsultaCnsService.formatarData("07/09/1975"));
        assertEquals("07/09/1975", ConsultaCnsService.formatarData("1975-09-07"));
        assertEquals("07/09/1975", ConsultaCnsService.formatarData("  07/09/1975  "));
        assertNull(ConsultaCnsService.formatarData("07/09/75"));
        assertNull(ConsultaCnsService.formatarData(null));
    }

    // Terceiro formato, e o mais comum no cadastro: Cad_pacientes grava yyyy/MM/dd.
    // Era o que o faturamento lia ao contrario - a lista de "corrigir CNS" mostrava
    // paciente com CPF e nascimento certos em tela, mas a consulta saia com outra data
    // (ou nem saia), e ele nunca destravava.
    @Test
    void aceitaOFormatoQueARecepcaoGravaNoCadastro() {
        assertEquals("07/09/1975", ConsultaCnsService.formatarData("1975/09/07"));
        assertEquals("13/03/1968", ConsultaCnsService.formatarData("1968/03/13"));
        assertEquals("05/02/1947", ConsultaCnsService.formatarData("1947/02/05"));
        // O caso silencioso: 8 digitos que davam uma data existente, porem trocada.
        assertEquals("12/06/2005", ConsultaCnsService.formatarData("2005/06/12"));
        assertEquals("13/03/1968", ConsultaCnsService.formatarData("1968-03-13 00:00:00"));
        assertEquals("13/03/1968", ConsultaCnsService.formatarData("1968-03-13T00:00:00"));
        // Sem separador a ambiguidade se resolve pelo mes: nao existe mes 68.
        assertEquals("13/03/1968", ConsultaCnsService.formatarData("13031968"));
        assertEquals("13/03/1968", ConsultaCnsService.formatarData("19680313"));
        assertNull(ConsultaCnsService.formatarData("1975/13/07"));
        assertNull(ConsultaCnsService.formatarData("2023/02/30"));
        assertNull(ConsultaCnsService.formatarData("   "));
    }
}

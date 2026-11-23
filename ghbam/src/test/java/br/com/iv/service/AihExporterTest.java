package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.List;

import org.junit.jupiter.api.Test;
import br.com.iv.entity.FaturamentoAih;
import br.com.iv.entity.FaturamentoCompetencia;

// O backend nao tem banco de teste isolado (dev e copia da producao - ver
// FhirSumarioAltaMapperServiceTest), entao FaturamentoAih.list (finder estatico do
// Panache) e mockado em vez de bater no banco real.
//
// Cobre a recusa documentada na propria classe (AihExporter): "sem faixa cadastrada o
// exportador recusa dizendo a razao, em vez de gerar um arquivo que seria rejeitado la na
// frente" - a unica regra de negocio do exportador que nao tinha nenhum teste ate 2026-09-22.
class AihExporterTest {

    private final AihExporter exporter = spy(new AihExporter());

    private FaturamentoCompetencia competencia() {
        FaturamentoCompetencia competencia = new FaturamentoCompetencia();
        competencia.setUnidade("HMSFX");
        competencia.setCompetencia("202609");
        return competencia;
    }

    @Test
    void recusaQuandoNaoHaContaFechadaNaCompetencia() {
        doReturn(List.of()).when(exporter).buscarContas(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> exporter.exportar(List.of(), competencia()));

        assertTrue(erro.getMessage().contains("Nenhuma conta hospitalar fechada"));
    }

    // A regra central da classe: conta fechada sem NU_AIH (faixa nao cadastrada pelo
    // gestor) nao gera arquivo - o exportador recusa em vez de mandar um lote que o
    // SISAIH01 rejeitaria por numeracao ausente ou fora da faixa.
    @Test
    void recusaQuandoContasFechadasNaoTemNumeroDeAih() {
        FaturamentoAih semNumero = new FaturamentoAih();
        semNumero.setNumeroAih("");

        doReturn(List.of(semNumero)).when(exporter).buscarContas(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> exporter.exportar(List.of(), competencia()));

        assertTrue(erro.getMessage().contains("sem numero de AIH"));
    }

    // --- Parto, RN e conta que fica de fora (2026-11-23) -------------------------------

    private static final String CNS_OK = "898001160660761";

    private FaturamentoAih conta(String numero, String bam, String prontuario, String procedimento, int idpaciente) {
        FaturamentoAih c = new FaturamentoAih();
        c.setNumero(numero);
        c.setNumeroAih("33261234567" + numero.substring(numero.length() - 2));
        c.setBam(bam);
        c.setProntuario(prontuario);
        c.setProcedimentoPrincipal(procedimento);
        c.setCid("O800");
        c.setIdpaciente(idpaciente);
        c.setDataInternacao(java.time.LocalDateTime.of(2026, 11, 22, 10, 0));
        c.setDataAlta(java.time.LocalDateTime.of(2026, 11, 24, 10, 0));
        return c;
    }

    private br.com.iv.entity.Afaturar producao(int bam, String nome, String cns, String procedimento) {
        br.com.iv.entity.Afaturar a = new br.com.iv.entity.Afaturar();
        a.setBam(bam);
        a.setNomepaciente(nome);
        a.setCnsdopciente(cns);
        a.setProcedimento(procedimento);
        a.setDtnascimento("2000-01-01");
        a.setSexo("F");
        return a;
    }

    private br.com.iv.entity.Pacientes paciente(String prontuario, String nascimento, String mae) {
        br.com.iv.entity.Pacientes p = new br.com.iv.entity.Pacientes();
        p.setProntuario(prontuario);
        p.setNascimento(nascimento);
        p.setMae(mae);
        return p;
    }

    private void unidadeConfigurada() {
        br.com.iv.entity.ConfiguracaoUnidade cfg = new br.com.iv.entity.ConfiguracaoUnidade();
        cfg.setSigla("HMSFX");
        cfg.setCnesDoHospital("1234567");
        cfg.setCodIbge(330100);
        exporter.configuracaoUnidadeRepository = org.mockito.Mockito.mock(br.com.iv.repository.ConfiguracaoUnidadeRepository.class);
        doReturn(List.of(cfg)).when(exporter.configuracaoUnidadeRepository).filtarPorUnidadeSigla("HMSFX");
    }

    private List<String> registros(br.com.iv.dto.ConteudoExportado arquivo) {
        return List.of(new String(arquivo.conteudo(), java.nio.charset.StandardCharsets.ISO_8859_1).split("\r\n"));
    }

    // Parto da mae com gemeos: QT_VIVOS e QT_ALTA = 2. O RN de um parto anterior (mesmo
    // prontuario, nascido fora da internacao) nao conta. Nome da mae sai no campo 41.
    @Test
    void partoContaRecemNascidosDaInternacaoEPreencheNomeDaMae() {
        unidadeConfigurada();
        FaturamentoAih mae = conta("AIH-01", "100", "2026-50771", "0411010034", 1);
        doReturn(List.of(mae)).when(exporter).buscarContas(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        doReturn(paciente("2026-50771", "2000-01-01", "MARIA DA SILVA")).when(exporter).pacienteDa(mae);
        doReturn(List.of(
                paciente("12026-50771", "2026-11-23", "X"),
                paciente("12026-50771", "2026/11/23", "X"), // mesmo RN cadastrado 2x
                paciente("22026-50771", "2026/11/23", "X"),
                paciente("32026-50771", "2024-03-10", "X"))).when(exporter).recemNascidosDa("2026-50771");

        var arquivo = exporter.exportar(List.of(producao(100, "MICAELA TESTE", CNS_OK, "0411010034")), competencia());
        String r = registros(arquivo).get(0);

        assertEquals(1600, r.length());
        assertEquals("MARIA DA SILVA", r.substring(349, 419).trim());
        assertEquals('2', r.charAt(1423)); // 77 QT_VIVOS
        assertEquals('0', r.charAt(1424)); // 78 QT_MORTOS
        assertEquals('2', r.charAt(1425)); // 79 QT_ALTA
        assertTrue(arquivo.pendencias().isEmpty());
    }

    // AIH propria do RN (UTI neonatal): peso em gramas (digitado em kg) e meses de gestacao.
    @Test
    void recemNascidoLevaPesoEGestacaoDosAntecedentes() {
        unidadeConfigurada();
        FaturamentoAih rn = conta("AIH-02", "200", "12026-50771", "0802010199", 2);
        doReturn(List.of(rn)).when(exporter).buscarContas(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        doReturn(paciente("12026-50771", "2026-11-23", "MICAELA TESTE")).when(exporter).pacienteDa(rn);
        br.com.iv.entity.ConsultaPediatrica ant = new br.com.iv.entity.ConsultaPediatrica();
        ant.setPesoNascimento(2.85);
        ant.setIdadeGestacionalSemanas(36);
        doReturn(ant).when(exporter).antecedentesDo("12026-50771");

        String r = registros(exporter.exportar(
                List.of(producao(200, "RN 1 DE MICAELA TESTE", CNS_OK, "0802010199")), competencia())).get(0);

        assertEquals("MICAELA TESTE", r.substring(349, 419).trim());
        assertEquals("2850", r.substring(1394, 1398)); // 71 PESO_UTINEO
        assertEquals('8', r.charAt(1398));             // 72 MESGEST_UTINEO
        assertEquals('0', r.charAt(1423));             // nao e parto: QT_VIVOS zerado
    }

    // RN sem CNS provisorio nao derruba a competencia: sai a AIH da mae, o RN vai para
    // as pendencias com a orientacao do que fazer.
    @Test
    void contaSemCnsFicaDeForaSemDerrubarAsOutras() {
        unidadeConfigurada();
        FaturamentoAih mae = conta("AIH-01", "100", "2026-50771", "0310010039", 1);
        FaturamentoAih rn = conta("AIH-02", "200", "12026-50771", "0802010199", 2);
        doReturn(List.of(mae, rn)).when(exporter).buscarContas(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        doReturn(null).when(exporter).pacienteDa(org.mockito.ArgumentMatchers.any());
        doReturn(List.of()).when(exporter).recemNascidosDa(org.mockito.ArgumentMatchers.anyString());

        var arquivo = exporter.exportar(List.of(
                producao(100, "MICAELA TESTE", CNS_OK, "0310010039"),
                producao(200, "RN 1 DE MICAELA TESTE", "", "0802010199")), competencia());

        assertEquals(1, arquivo.quantidade());
        assertEquals(1, registros(arquivo).size());
        assertEquals(1, arquivo.pendencias().size());
        assertTrue(arquivo.pendencias().get(0).contains("CNS provisorio"));
    }

    @Test
    void recusaQuandoNenhumaContaPassa() {
        unidadeConfigurada();
        FaturamentoAih rn = conta("AIH-02", "200", "12026-50771", "0802010199", 2);
        doReturn(List.of(rn)).when(exporter).buscarContas(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> exporter.exportar(
                List.of(producao(200, "RN 1 DE MICAELA TESTE", "", "0802010199")), competencia()));

        assertTrue(erro.getMessage().contains("CNS provisorio"));
    }
}

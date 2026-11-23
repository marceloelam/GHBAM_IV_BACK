package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

// Regras puras do quadro de fluxo (sem banco): etapa do BAM, tempo-alvo de Manchester e
// leitura das datas gravadas como texto em mais de um formato.
class FluxoPacientesServiceTest {

    @Test
    void etapaSegueAOrdemDaBuscaDoCabecalho() {
        assertEquals("INTERNADO", SituacaoPacienteService.classificar(true, false, false, "-", false, false, true));
        assertEquals("SAIU", SituacaoPacienteService.classificar(false, false, true, "alta", false, false, true));
        assertEquals("SAIU", SituacaoPacienteService.classificar(true, true, false, "-", false, false, true));
        assertEquals("OBSERVACAO", SituacaoPacienteService.classificar(false, false, false, "obs", true, false, true));
        assertEquals("AGUARDANDO_LEITO", SituacaoPacienteService.classificar(false, false, false, "internacao", false, false, true));
        // Medico indicou internacao/observacao (grava tratado=1) e o NIR ainda nao alocou: segue na unidade.
        assertEquals("AGUARDANDO_LEITO", SituacaoPacienteService.classificar(false, false, true, "internacao", false, false, true));
        assertEquals("OBSERVACAO", SituacaoPacienteService.classificar(false, false, true, "obs", false, false, true));
        // Internacao com alta encerra mesmo com tipo_alta "internacao".
        assertEquals("SAIU", SituacaoPacienteService.classificar(true, true, true, "internacao", false, false, true));
        assertEquals("EM_ATENDIMENTO", SituacaoPacienteService.classificar(false, false, false, "-", true, true, true));
        assertEquals("EM_ESPERA", SituacaoPacienteService.classificar(false, false, false, "-", false, true, true));
        assertEquals("AGUARDANDO_MEDICO", SituacaoPacienteService.classificar(false, false, false, "-", false, false, true));
        assertEquals("AGUARDANDO_CLASSIFICACAO", SituacaoPacienteService.classificar(false, false, false, null, false, false, false));
    }

    @Test
    void setoresAmbulatoriaisSaemDoQuadro() {
        java.util.List<String> lista = java.util.List.of("AMBU", "GINECOLOGIA");
        assertEquals(true, FluxoPacientesService.ambulatorial("CIRURGIA G. AMBU", lista));
        assertEquals(true, FluxoPacientesService.ambulatorial("Ambulatório P.C.", lista));
        assertEquals(true, FluxoPacientesService.ambulatorial("GINECOLOGIA", lista));
        assertEquals(false, FluxoPacientesService.ambulatorial("TRAUMA", lista));
        assertEquals(false, FluxoPacientesService.ambulatorial("DENTISTA", lista));
        assertEquals(false, FluxoPacientesService.ambulatorial(null, lista));
    }

    @Test
    void idadeVemDoNascimentoESemIdentificacaoFicaDesconhecida() {
        LocalDate hoje = LocalDate.of(2026, 11, 20);
        LocalDate chegada = LocalDate.of(2026, 10, 9);
        // Cadastro antigo: idade gravada 40, mas ja fez 41.
        assertEquals(41, FluxoPacientesService.idadeExibida(LocalDate.of(1985, 1, 10), chegada, "TRAUMA", 40, hoje));
        // Sem identificacao: nascimento = dia da chegada -> desconhecida, nao "0 anos".
        assertNull(FluxoPacientesService.idadeExibida(chegada, chegada, "TRAUMA", 0, hoje));
        // Recem-nascido de verdade continua com 0.
        assertEquals(0, FluxoPacientesService.idadeExibida(chegada, chegada, "MATERNIDADE", 0, LocalDate.of(2026, 10, 9)));
        // Sem data valida: usa a idade gravada se houver.
        assertEquals(30, FluxoPacientesService.idadeExibida(null, chegada, "TRAUMA", 30, hoje));
        assertNull(FluxoPacientesService.idadeExibida(null, chegada, "TRAUMA", 0, hoje));
    }

    @Test
    void tempoAlvoDeManchesterPorCor() {
        assertEquals(0, FluxoPacientesService.alvoManchester(5));
        assertEquals(10, FluxoPacientesService.alvoManchester(4));
        assertEquals(60, FluxoPacientesService.alvoManchester(3));
        assertEquals(120, FluxoPacientesService.alvoManchester(2));
        assertEquals(240, FluxoPacientesService.alvoManchester(1));
        assertNull(FluxoPacientesService.alvoManchester(0));
        assertEquals(2, FluxoPacientesService.corDoRisco(" verde "));
        assertEquals(1, FluxoPacientesService.corDoRisco("AZUL"));
    }

    @Test
    void dataEmTextoAceitaOsFormatosGravados() {
        LocalDate dia = LocalDate.of(2026, 11, 2);
        assertEquals(dia, FluxoPacientesService.data("2026-11-02"));
        assertEquals(dia, FluxoPacientesService.data("2026/11/02"));
        assertEquals(dia, FluxoPacientesService.data("02/11/2026"));
        assertEquals(dia, FluxoPacientesService.data("2026-11-02 08:15:00"));
        assertNull(FluxoPacientesService.data("ontem"));
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 15), FluxoPacientesService.momento(dia, "08:15"));
        assertNull(FluxoPacientesService.momento(dia, "-"));
    }

    @Test
    void colunaDeSaidasValeSoParaOProprioDia() {
        java.time.LocalDate hoje = java.time.LocalDate.of(2026, 11, 3);
        java.time.LocalDateTime ontem = java.time.LocalDateTime.of(2026, 11, 2, 23, 50);
        assertEquals(true, FluxoPacientesService.saidaDeHoje(hoje, ontem, hoje));
        assertEquals(false, FluxoPacientesService.saidaDeHoje(hoje, ontem, hoje.plusDays(1)));
        assertEquals(false, FluxoPacientesService.saidaDeHoje(null, ontem, hoje));
        assertEquals(true, FluxoPacientesService.saidaDeHoje(null, hoje.atTime(8, 0), hoje));
        assertEquals(false, FluxoPacientesService.saidaDeHoje(null, null, hoje));
    }
}

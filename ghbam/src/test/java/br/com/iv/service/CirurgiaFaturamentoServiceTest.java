package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import br.com.iv.dto.AfaturarDto;
import br.com.iv.entity.CirurgiaAgendamento;
import br.com.iv.entity.CirurgiaEquipe;
import br.com.iv.entity.CirurgiaProcedimento;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.ProcedimentosSus;
import br.com.iv.entity.Servidor;

/**
 * Trava o mapeamento cirurgia -> producao SUS.
 *
 * Sao as decisoes que so aparecem semanas depois, na remessa: competencia errada
 * por usar a data do agendamento, linha duplicada por chave de controle instavel
 * e CNS validado sobrescrito pelo do cadastro.
 */
class CirurgiaFaturamentoServiceTest {

    private final CirurgiaFaturamentoService service = new CirurgiaFaturamentoService();

    private CirurgiaAgendamento cirurgia() {
        CirurgiaAgendamento c = new CirurgiaAgendamento();
        c.setIdcirurgia(12);
        c.setIdpaciente(9300);
        c.setNomepaciente("PACIENTE DE TESTE");
        c.setUnidade("HMSFX");
        c.setCarater("ELETIVA");
        c.setCid("A050");
        c.setDataPrevista(LocalDateTime.of(2026, 8, 26, 8, 0));
        return c;
    }

    private CirurgiaProcedimento procedimento() {
        CirurgiaProcedimento p = new CirurgiaProcedimento();
        p.setIdprocedimento(34);
        p.setCodigo("0407010173");
        p.setDescricao("PROCEDIMENTO DE TESTE");
        p.setQuantidade(1);
        p.setRealizado(true);
        return p;
    }

    @Test
    void caraterEletivaViraUmEDemaisViramDois() {
        assertEquals(1, service.carater("ELETIVA"));
        assertEquals(2, service.carater("URGENCIA"));
        assertEquals(2, service.carater("EMERGENCIA"));
        assertEquals(2, service.carater(null));
    }

    // Duas cirurgias diferentes, ou dois procedimentos da mesma, nunca podem
    // colidir: a chave e o que impede a producao de duplicar ao reencerrar.
    @Test
    void chaveDeControleIdentificaCirurgiaProcedimentoESequencia() {
        assertEquals("CIR-12-34-1", service.chaveControle(12, 34, 1));
        assertEquals("CIR-12-34-2", service.chaveControle(12, 34, 2));
        assertEquals("CIR-13-34-1", service.chaveControle(13, 34, 1));
        assertEquals("CIR-12-35-1", service.chaveControle(12, 35, 1));
    }

    @Test
    void producaoCarregaProcedimentoCidUnidadeEChaveDeControle() {
        AfaturarDto dto = service.montar(cirurgia(), procedimento(), null, null, null, null,
                "2026-08-26", "CIR-12-34-1", null);

        assertEquals("0407010173", dto.getProcedimento());
        assertEquals("PROCEDIMENTO DE TESTE", dto.getDescricao());
        assertEquals("A050", dto.getCid10());
        assertEquals("HMSFX", dto.getUnidade());
        assertEquals("CIR-12-34-1", dto.getControleid());
        assertEquals("CIR", dto.getOrigeminfo());
        assertEquals(9300, dto.getIdpaciente());
        assertEquals(1, dto.getCarateratend());
        assertTrue(dto.isRealizado());
    }

    // A competencia do BPA sai da data gravada aqui: mandar a data prevista
    // jogaria toda cirurgia remarcada para o mes errado.
    @Test
    void dataDaProducaoEaDoAtoInformadoNaoADoAgendamento() {
        AfaturarDto dto = service.montar(cirurgia(), procedimento(), null, null, null, null,
                "2026-09-03", "CIR-12-34-1", null);

        assertEquals("2026-09-03", dto.getData());
        assertEquals("2026-09-03", dto.getDtatendimento());
    }

    @Test
    void cnsDoPacienteVemDoCadastroQuandoAgendamentoNaoTem() {
        Pacientes paciente = new Pacientes();
        paciente.setCns("707104361565520");
        paciente.setSexo("F");
        paciente.setNascimento("1952/06/22");

        AfaturarDto dto = service.montar(cirurgia(), procedimento(), paciente, null, null, null,
                "2026-08-26", "CIR-12-34-1", null);

        assertEquals("707104361565520", dto.getCnsdopciente());
        assertEquals("F", dto.getSexo());
    }

    // O CNS do agendamento foi conferido (e, quando faltava, buscado no CNES) na
    // hora de agendar: o cadastro nao pode sobrescrever o que ja foi validado.
    @Test
    void cnsDoAgendamentoPrevaleceSobreODoCadastro() {
        CirurgiaAgendamento c = cirurgia();
        c.setCnspaciente("898000469026113");

        Pacientes paciente = new Pacientes();
        paciente.setCns("707104361565520");

        AfaturarDto dto = service.montar(c, procedimento(), paciente, null, null, null,
                "2026-08-26", "CIR-12-34-1", null);

        assertEquals("898000469026113", dto.getCnsdopciente());
    }

    @Test
    void cnsDoProfissionalVemDoCadastroDoServidorEoCboDaEquipe() {
        CirurgiaEquipe cirurgiao = new CirurgiaEquipe();
        cirurgiao.setNome("CIRURGIAO DE TESTE");
        cirurgiao.setCbo("225265");
        cirurgiao.setMatricula("852852");

        Servidor servidor = new Servidor();
        servidor.setCns("980016288450004");

        AfaturarDto dto = service.montar(cirurgia(), procedimento(), null, cirurgiao, servidor, null,
                "2026-08-26", "CIR-12-34-1", null);

        assertEquals("CIRURGIAO DE TESTE", dto.getNomeservidor());
        assertEquals("225265", dto.getCbo());
        assertEquals("852852", dto.getMatriculaservidor());
        assertEquals("980016288450004", dto.getCnsdoprofissional());
    }

    // Valor zerado faz AfaturarService classificar como NAO_FATURAVEL: os valores
    // precisam chegar do SIGTAP para a linha nascer apta.
    @Test
    void valoresEtipoDeRegistroVemDoSigtap() {
        ProcedimentosSus sigtap = new ProcedimentosSus();
        sigtap.setPa_sa(12.5);
        sigtap.setPa_sp(30.0);
        sigtap.setPa_sh(87.25);
        sigtap.setPa_tpreg("02");
        sigtap.setPa_eletiva("1");

        AfaturarDto dto = service.montar(cirurgia(), procedimento(), null, null, null, sigtap,
                "2026-08-26", "CIR-12-34-1", null);

        assertEquals(12.5, dto.getPa_sa());
        assertEquals(30.0, dto.getPa_sp());
        assertEquals(87.25, dto.getPa_sh());
        assertEquals("02", dto.getPa_tpreg());
        assertEquals("1", dto.getPa_eletiva());
    }

    // Codigo fora do SIGTAP vigente nao pode derrubar o encerramento: a linha e
    // gravada zerada e aparece como nao faturavel na tela de faturamento.
    //
    // pa_tpreg fica null, e nao vazio, quando nao ha SIGTAP para copiar. Isso e
    // seguro: FaturamentoProducaoService.normalizarTipoRegistro passa o valor por
    // texto(), que converte null em "" antes de classificar.
    @Test
    void codigoForaDoSigtapNaoQuebraAMontagem() {
        AfaturarDto dto = service.montar(cirurgia(), procedimento(), null, null, null, null,
                "2026-08-26", "CIR-12-34-1", null);

        assertEquals(0.0, dto.getPa_sa());
        assertNull(dto.getPa_tpreg());
        assertEquals("0407010173", dto.getProcedimento());
    }
}

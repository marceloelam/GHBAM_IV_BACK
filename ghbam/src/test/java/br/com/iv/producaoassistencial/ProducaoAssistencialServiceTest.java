package br.com.iv.producaoassistencial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import br.com.iv.producaomedica.ProducaoMedicaLinha;
import br.com.iv.producaomedica.ProducaoMedicaRepository;
import br.com.iv.producaoassistencial.ProducaoAssistencialService.Linha;
import br.com.iv.producaoassistencial.ProducaoAssistencialService.Tipo;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

class ProducaoAssistencialServiceTest {
    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 10, 5);
    private ProducaoAssistencialRepository repo() {
        var repo = mock(ProducaoAssistencialRepository.class);
        when(repo.normalizarAutores(anyList(), anyString())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
        return repo;
    }
    @Test void somaSomenteTiposSelecionadosSemConfundirAtendimentosComAcoes() {
        var repo = repo(); var medica = mock(ProducaoMedicaRepository.class);
        when(medica.buscar(INICIO, FIM, "U", null)).thenReturn(List.of(
                new ProducaoMedicaLinha(1, 1, INICIO, "10:00", "123", "Ana", "U", "CLINICA", "", "P"),
                new ProducaoMedicaLinha(2, 2, INICIO, "11:00", "123", "Ana", "U", "CLINICA", "", "P2")));
        when(repo.buscar(INICIO, FIM, "U", EnumSet.of(Tipo.PARECER_RESPONDIDO, Tipo.APS_PROCEDIMENTO))).thenReturn(List.of(
                new Linha("PARECER_RESPONDIDO", "CARDIO", "Ana", "CRM", "123", FIM, 3),
                new Linha("APS_PROCEDIMENTO", "225125", "Ana", "123", "123", FIM, 5)));
        var result = new ProducaoAssistencialService(repo, medica).gerar(INICIO, FIM, "U", "MEDICO,PARECER_RESPONDIDO,APS_PROCEDIMENTO");
        assertEquals(2, result.totalAtendimentos());
        assertEquals(8, result.totalDocumentos());
        assertEquals(10, result.totalGeral());
        assertEquals(3, result.linhas().size());
        verify(repo).buscar(INICIO, FIM, "U", EnumSet.of(Tipo.PARECER_RESPONDIDO, Tipo.APS_PROCEDIMENTO));
    }
    @Test void naoConsultaProducaoMedicaQuandoNaoFoiSelecionada() {
        var repo = repo(); var medica = mock(ProducaoMedicaRepository.class);
        when(repo.buscar(INICIO, FIM, "U", EnumSet.of(Tipo.MULTIDISCIPLINAR))).thenReturn(List.of(
                new Linha("MULTIDISCIPLINAR", "FISIO", "Bia", "2", "", FIM, 4)));
        var result = new ProducaoAssistencialService(repo, medica).gerar(INICIO, FIM, "U", "MULTIDISCIPLINAR,MULTIDISCIPLINAR");
        assertEquals(4, result.totalGeral()); assertEquals(4, result.totalAtendimentos());
        assertEquals(1, result.tipos().size()); verifyNoInteractions(medica);
    }
    @Test void rejeitaPeriodoTiposEUnidadeInvalidosAntesDeConsultar() {
        var repo = repo(); var medica = mock(ProducaoMedicaRepository.class);
        var service = new ProducaoAssistencialService(repo, medica);
        assertThrows(BadRequestException.class, () -> service.gerar(FIM, INICIO, "U", "MEDICO"));
        assertThrows(BadRequestException.class, () -> service.gerar(INICIO.minusYears(6), FIM, "U", "MEDICO"));
        assertThrows(BadRequestException.class, () -> service.gerar(INICIO, FIM, "", "MEDICO"));
        assertThrows(BadRequestException.class, () -> service.gerar(INICIO, FIM, "U", ""));
        assertThrows(BadRequestException.class, () -> service.gerar(INICIO, FIM, "U", "MEDICO,SQL"));
        verifyNoInteractions(medica); verify(repo, never()).buscar(any(), any(), any(), any());
    }
    @Test void totalZeroParaPeriodoSemRegistros() {
        var repo = repo(); var medica = mock(ProducaoMedicaRepository.class);
        when(repo.buscar(INICIO, INICIO, "U", EnumSet.of(Tipo.ANAMNESE))).thenReturn(List.of());
        var result = new ProducaoAssistencialService(repo, medica).gerar(INICIO, INICIO, "U", "ANAMNESE");
        assertEquals(0, result.totalGeral()); assertTrue(result.linhas().isEmpty());
    }
}

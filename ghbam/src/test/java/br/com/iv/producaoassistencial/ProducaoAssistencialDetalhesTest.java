package br.com.iv.producaoassistencial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import br.com.iv.producaomedica.ProducaoMedicaLinha;
import br.com.iv.producaomedica.ProducaoMedicaRepository;
import br.com.iv.producaoassistencial.ProducaoAssistencialService.*;
import br.com.iv.security.AuthenticatedUser;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;

class ProducaoAssistencialDetalhesTest {
    private final LocalDate date = LocalDate.of(2026, 10, 6);
    private ProducaoAssistencialRepository repo() {
        var repo = mock(ProducaoAssistencialRepository.class);
        when(repo.normalizarAutores(anyList(), anyString())).thenAnswer(i -> List.copyOf(i.getArgument(0)));
        return repo;
    }
    @Test void medicoReutilizaFonteDeContagemEFiltraAutorComPaginacao() {
        var repo = repo(); var medica = mock(ProducaoMedicaRepository.class);
        when(medica.buscar(date, date, "U", "M1")).thenReturn(List.of(
                new ProducaoMedicaLinha(10, 1, date, "10:00", "M1", "Ana", "U", "CLINICA", "ALTA", "Paciente A"),
                new ProducaoMedicaLinha(11, 2, date, "11:00", "M1", "Ana", "U", "CLINICA", "", "Paciente B"),
                new ProducaoMedicaLinha(12, 3, date, "12:00", "M2", "Bia", "U", "CLINICA", "", "Paciente C")));
        var service = new ProducaoAssistencialService(repo, medica);
        var pagina = service.detalhes(date, date, "U", "MEDICO", "Ana", "M1", "M1", "Medicina", 0, 1);
        assertEquals(2, pagina.totalRegistros()); assertEquals(2, pagina.totalProducao());
        assertEquals(1, pagina.registros().size()); assertEquals("Paciente A", pagina.registros().get(0).paciente());
        assertEquals(10, pagina.registros().get(0).bam());
        assertEquals("Paciente B", service.detalhes(date, date, "U", "MEDICO", "Ana", "M1", "M1", "Medicina", 1, 1).registros().get(0).paciente());
        verify(repo, never()).buscarDetalhes(any(), any(), any(), any(), any(), any());
    }
    @Test void quantidadeDeProcedimentosNaoEConfundidaComNumeroDeRegistros() {
        var repo = repo(); var medica = mock(ProducaoMedicaRepository.class);
        when(repo.buscarDetalhes(date, date, "U", Tipo.APS_PROCEDIMENTO, "225125", "M1")).thenReturn(List.of(
                new RegistroProducao(new Linha("APS_PROCEDIMENTO", "225125", "Ana", "M1", "M1", date, 3), new Detalhe("1", null, "", date, "", "Paciente A", "Codigo A", "PRONTA", 3)),
                new RegistroProducao(new Linha("APS_PROCEDIMENTO", "225125", "Ana", "M1", "M1", date, 2), new Detalhe("2", null, "", date, "", "Paciente B", "Codigo B", "PRONTA", 2))));
        var result = new ProducaoAssistencialService(repo, medica).detalhes(date, date, "U", "APS_PROCEDIMENTO", "Ana", "M1", "M1", "225125", 0, 50);
        assertEquals(2, result.totalRegistros()); assertEquals(5, result.totalProducao());
        verifyNoInteractions(medica);
    }
    @Test void limitesInvalidosNaoConsultamORepositorio() {
        var repo = repo(); var medica = mock(ProducaoMedicaRepository.class);
        var service = new ProducaoAssistencialService(repo, medica);
        assertThrows(BadRequestException.class, () -> service.detalhes(date, date, "U", "INVALIDO", "Ana", "M1", "M1", "Medicina", 0, 50));
        assertThrows(BadRequestException.class, () -> service.detalhes(date, date, "U", "MEDICO", "Ana", "M1", "M1", "Medicina", -1, 50));
        assertThrows(BadRequestException.class, () -> service.detalhes(date, date, "U", "MEDICO", "Ana", "M1", "M1", "Medicina", 0, 101));
        verifyNoInteractions(medica); verify(repo, never()).buscarDetalhes(any(), any(), any(), any(), any(), any());
    }
    @Test void todasAsFontesMantemCriteriosEIncluemIdentificacaoSemTextoClinico() {
        for (Tipo tipo : Tipo.values()) {
            if (tipo == Tipo.MEDICO) continue;
            String sql = ProducaoAssistencialRepository.consultaDetalhes(tipo);
            assertTrue(sql.contains("AS idregistro"), tipo.name());
            assertTrue(sql.contains("AS paciente"), tipo.name());
            assertTrue(sql.contains(":unidade"), tipo.name());
            assertTrue(sql.contains(":inicio"), tipo.name());
            assertTrue(sql.contains(":fim"), tipo.name());
            assertFalse(sql.substring(0, sql.indexOf("FROM ")).contains("e.relato"));
        }
        assertTrue(ProducaoAssistencialRepository.consultaDetalhes(Tipo.MULTIDISCIPLINAR).contains("a.status = 'FINALIZADO'"));
        assertTrue(ProducaoAssistencialRepository.consultaDetalhes(Tipo.APS_PROCEDIMENTO).contains("proc.item"));
    }
    @Test void recursoDetalhesExigeAdministrativoEUsaUnidadeDaSessao() {
        var resource = new ProducaoAssistencialResource();
        resource.service = mock(ProducaoAssistencialService.class); resource.request = mock(ContainerRequestContext.class);
        var usuario = new AuthenticatedUser("1", "M1", "Ana", "MEDICO", " U ", "", "", "", "", "", "", "", false, Map.of("kmedico", true));
        when(resource.request.getProperty("ghbam.user")).thenReturn(usuario);
        assertEquals(403, resource.detalhes("2026-10-06", "2026-10-06", "MEDICO", "Ana", "M1", "M1", "Medicina", 0, 50).getStatus());
        verifyNoInteractions(resource.service);
        usuario = new AuthenticatedUser("1", "M1", "Ana", "MEDICO", " U ", "", "", "", "", "", "", "", false, Map.of("kadm", true));
        when(resource.request.getProperty("ghbam.user")).thenReturn(usuario);
        when(resource.service.detalhes(date, date, "U", "MEDICO", "Ana", "M1", "M1", "Medicina", 0, 50)).thenReturn(new Detalhamento(0, 0, 0, 50, List.of()));
        assertEquals(200, resource.detalhes("2026-10-06", "2026-10-06", "MEDICO", "Ana", "M1", "M1", "Medicina", 0, 50).getStatus());
        verify(resource.service).detalhes(date, date, "U", "MEDICO", "Ana", "M1", "M1", "Medicina", 0, 50);
    }
}

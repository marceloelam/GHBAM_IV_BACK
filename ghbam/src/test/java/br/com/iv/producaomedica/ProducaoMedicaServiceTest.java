package br.com.iv.producaomedica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import jakarta.ws.rs.BadRequestException;

class ProducaoMedicaServiceTest {
    @Test void consolidaIndicadoresSeriesEComparativo() {
        var repo = new FakeRepository();
        repo.atual = List.of(
            linha(10, 1, "2026-07-01", "123", "Dra. Ana", "HMSFX", "CLINICA", "ALTA"),
            linha(11, 1, "2026-07-01", "123", "Dra. Ana", "HMSFX", "CLINICA", ""),
            linha(12, 2, "2026-07-08", "456", "Dr. Bruno", "UPA", "PEDIATRIA", "INTERNACAO"));
        repo.anterior = List.of(
            linha(1, 9, "2026-06-16", "123", "Dra. Ana", "HMSFX", "CLINICA", "ALTA"),
            linha(2, 8, "2026-06-17", "123", "Dra. Ana", "HMSFX", "CLINICA", "ALTA"));

        var r = new ProducaoMedicaService(repo).gerar(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15), null, null);

        assertEquals(3, r.totalAtendimentos());
        assertEquals(2, r.pacientesUnicos());
        assertEquals(2, r.diasAtivos());
        assertEquals(1.5, r.mediaDia());
        assertEquals(2, r.altas());
        assertEquals(1, r.semDesfecho());
        assertEquals(50.0, r.comparativo().variacaoPercentual());
        assertEquals(2, r.porMedico().size());
        assertEquals(3, r.detalhes().size());
    }

    @Test void rejeitaPeriodoInvertido() {
        assertThrows(BadRequestException.class, () -> new ProducaoMedicaService(new FakeRepository())
            .gerar(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1), null, null));
    }

    @Test void calculaCemPorCentoQuandoPeriodoAnteriorEraZero() {
        var repo = new FakeRepository();
        repo.atual = List.of(linha(1, 1, "2026-08-01", "1", "Medico", "U", "S", ""));
        var r = new ProducaoMedicaService(repo).gerar(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null);
        assertEquals(100.0, r.comparativo().variacaoPercentual());
    }

    @Test void rejeitaPeriodoSuperiorACincoAnos() {
        assertThrows(BadRequestException.class, () -> new ProducaoMedicaService(new FakeRepository())
            .gerar(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 2), null, null));
    }

    private static ProducaoMedicaLinha linha(int bam, int paciente, String data, String matricula,
            String medico, String unidade, String setor, String desfecho) {
        return new ProducaoMedicaLinha(bam, paciente, LocalDate.parse(data), "10:00", matricula,
            medico, unidade, setor, desfecho, "Paciente " + paciente);
    }

    private static class FakeRepository extends ProducaoMedicaRepository {
        List<ProducaoMedicaLinha> atual = List.of();
        List<ProducaoMedicaLinha> anterior = List.of();
        @Override public List<ProducaoMedicaLinha> buscar(LocalDate inicio, LocalDate fim, String unidade, String matricula) {
            return inicio.getDayOfMonth() == 1 ? atual : anterior;
        }
    }
}

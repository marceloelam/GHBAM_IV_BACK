package br.com.iv.producaoassistencial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import br.com.iv.producaoassistencial.ProducaoAssistencialService.*;
import br.com.iv.producaomedica.ProducaoMedicaRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProducaoAssistencialConteudoTest {
    private final LocalDate date = LocalDate.of(2026, 10, 6);
    @Test void registroPrecisaPertencerAoProfissionalEPeriodoAntesDeLerConteudo() {
        var repo = mock(ProducaoAssistencialRepository.class);
        when(repo.normalizarAutores(anyList(), anyString())).thenAnswer(i -> List.copyOf(i.getArgument(0)));
        var detalhe = new Detalhe("20189", 516510, "P1", date, "", "Paciente A", "Anamnese", "Registrada", 1);
        when(repo.buscarDetalhes(date, date, "U", Tipo.ANAMNESE, "Medicina", "CRM1")).thenReturn(List.of(
                new RegistroProducao(new Linha("ANAMNESE", "Medicina", "Ana", "CRM1", "M1", date, 1), detalhe)));
        when(repo.conteudoRegistro(Tipo.ANAMNESE, detalhe, date, date, "U")).thenReturn(List.of(new Secao("Frente", "Conteudo clinico", "HTML")));
        var service = new ProducaoAssistencialService(repo, mock(ProducaoMedicaRepository.class));
        assertEquals("20189", service.conteudoRegistro(date, date, "U", "ANAMNESE", "Ana", "CRM1", "M1", "Medicina", "20189").registro().id());
        assertThrows(NotFoundException.class, () -> service.conteudoRegistro(date, date, "U", "ANAMNESE", "Ana", "CRM1", "M1", "Medicina", "OUTRO"));
        assertThrows(NotFoundException.class, () -> service.conteudoRegistro(date, date, "U", "ANAMNESE", "Bia", "CRM1", "M2", "Medicina", "20189"));
        verify(repo, times(1)).conteudoRegistro(any(), any(), any(), any(), any());
    }
    @Test void anamneseLeFrenteEVersoComEscopoDaUnidade() {
        var repo = new ProducaoAssistencialRepository(); repo.entityManager = mock(EntityManager.class);
        var query = mock(Query.class);
        when(repo.entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{"<p>Frente</p>", "Verso"}));
        var resultado = repo.conteudoRegistro(Tipo.ANAMNESE, new Detalhe("20189", 516510, "P1", date, "", "Paciente A", "", "", 1), date, date, "U");
        assertEquals(2, resultado.size()); assertEquals("HTML", resultado.get(0).formato());
        var sql = ArgumentCaptor.forClass(String.class); verify(repo.entityManager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("a.frente, a.verso")); assertTrue(sql.getValue().contains(":unidade"));
        verify(query).setParameter("id", "20189"); verify(query).setParameter("unidade", "U");
    }
    @Test void parecerReuneResumoPedidoERespostaSemDuplicarRegistro() {
        var repo = new ProducaoAssistencialRepository(); repo.entityManager = mock(EntityManager.class);
        var query = mock(Query.class);
        when(repo.entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{"<p>Resumo clínico</p>", "Pedido", "Resposta"}));
        var resultado = repo.conteudoRegistro(Tipo.PARECER_RESPONDIDO, new Detalhe("42", 516510, "P1", date, "", "Paciente A", "", "", 1), date, date, "U");
        assertEquals(List.of("Resumo clínico", "Pedido médico solicitante", "Resposta do especialista"), resultado.stream().map(Secao::titulo).toList());
        assertEquals("HTML", resultado.get(0).formato()); assertEquals("TEXTO", resultado.get(1).formato());
        var sql = ArgumentCaptor.forClass(String.class); verify(repo.entityManager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("a.nbam = p.nbam")); assertTrue(sql.getValue().contains("p.dataresp BETWEEN :inicio AND :fim"));
        assertTrue(sql.getValue().contains("b.unidade")); assertTrue(sql.getValue().contains("LIMIT 1"));
        verify(query).setParameter("unidade", "U");
    }
    @Test void fichasApsDistinguemFilhosDaMesmaFicha() {
        assertTrue(ProducaoAssistencialRepository.consultaDetalhes(Tipo.APS_INDIVIDUAL).contains("WITH ORDINALITY AS filho"));
        String procedimentos = ProducaoAssistencialRepository.consultaDetalhes(Tipo.APS_PROCEDIMENTO);
        assertTrue(procedimentos.contains("WITH ORDINALITY AS proc"));
        assertTrue(procedimentos.contains("proc.ordem::text"));
    }
    @Test void conteudoExigeAdministrativoENaoPodeSerArmazenadoEmCacheHttp() {
        var resource = new ProducaoAssistencialResource(); resource.service = mock(ProducaoAssistencialService.class); resource.request = mock(ContainerRequestContext.class);
        var usuario = new AuthenticatedUser("1", "M1", "Ana", "MEDICO", "U", "", "", "", "", "", "", "", false, Map.of("kmedico", true));
        when(resource.request.getProperty("ghbam.user")).thenReturn(usuario);
        assertEquals(403, resource.conteudo("2026-10-06", "2026-10-06", "ANAMNESE", "Ana", "CRM1", "M1", "Medicina", "20189").getStatus());
        verifyNoInteractions(resource.service);
        usuario = new AuthenticatedUser("1", "M1", "Ana", "MEDICO", "U", "", "", "", "", "", "", "", false, Map.of("kadm", true));
        when(resource.request.getProperty("ghbam.user")).thenReturn(usuario);
        var response = resource.conteudo("2026-10-06", "2026-10-06", "ANAMNESE", "Ana", "CRM1", "M1", "Medicina", "20189");
        assertEquals(200, response.getStatus()); assertEquals("no-store", response.getHeaderString("Cache-Control"));
        verify(resource.service).conteudoRegistro(date, date, "U", "ANAMNESE", "Ana", "CRM1", "M1", "Medicina", "20189");
    }
}

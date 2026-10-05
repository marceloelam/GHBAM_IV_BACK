package br.com.iv.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import br.com.iv.entity.PharmaciaMovimentacao;
import br.com.iv.service.PharmaciaMovimentacaoService;
import br.com.iv.service.PharmaciaRequestScope;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.*;

class PharmaciaMovimentacaoResourceTest {
    PharmaciaMovimentacaoResource resource;
    @BeforeEach void preparar() {
        resource = new PharmaciaMovimentacaoResource(); resource.service = mock(PharmaciaMovimentacaoService.class); resource.scope = mock(PharmaciaRequestScope.class); resource.request = mock(ContainerRequestContext.class);
        when(resource.scope.unidadeAtual(resource.request)).thenReturn("QA");
    }
    @Test void consultaSemFiltroUsaUnidadeDaSessao() { when(resource.service.findByUnidade("QA")).thenReturn(List.of()); resource.listAll(null); verify(resource.scope).exigirUnidade(resource.request, "QA"); verify(resource.service).findByUnidade("QA"); verify(resource.service, never()).listAll(); }
    @Test void unidadeForjadaNaoConsultaDados() { when(resource.scope.exigirUnidade(resource.request, "OUTRA")).thenThrow(new ForbiddenException()); assertThrows(ForbiddenException.class, () -> resource.listAll("OUTRA")); verifyNoInteractions(resource.service); }
    @Test void idDeOutraUnidadeNaoExpoePaciente() { PharmaciaMovimentacao m = new PharmaciaMovimentacao(); m.setUnidadeOrigemSigla("OUTRA"); when(resource.service.findById(1L)).thenReturn(m); assertThrows(ForbiddenException.class, () -> resource.findById(1L)); }
    @Test void rastreioPorLoteTambemRestringeUnidade() { resource.findByLote(1L); verify(resource.service).findByLoteEUnidade(1L, "QA"); verify(resource.service, never()).findByLote(1L); }
}

package br.com.iv.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import br.com.iv.dto.PharmaciaProdutoDTO;
import br.com.iv.entity.PharmaciaProduto;
import br.com.iv.service.PharmaciaProdutoService;
import br.com.iv.service.PharmaciaRequestScope;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.*;

class PharmaciaProdutoResourceTest {
    PharmaciaProdutoResource resource;

    static PharmaciaProduto produto(long id, String unidade) {
        PharmaciaProduto p = new PharmaciaProduto(); p.setId(id); p.setUnidadeSigla(unidade); return p;
    }

    @BeforeEach void preparar() {
        resource = new PharmaciaProdutoResource(); resource.service = mock(PharmaciaProdutoService.class);
        resource.scope = mock(PharmaciaRequestScope.class); resource.requestContext = mock(ContainerRequestContext.class);
        when(resource.scope.unidadeAtual(resource.requestContext)).thenReturn("QA");
    }

    @Test void listaSemFiltroTrazSoOCatalogoDaUnidadeDaSessao() {
        resource.listAll(null);
        verify(resource.service).findByUnidadeSigla("QA");
        verify(resource.service, never()).listAll();
    }

    @Test void filtroDeOutraUnidadeERecusado() {
        assertThrows(ForbiddenException.class, () -> resource.listAll("OUTRA"));
        verifyNoInteractions(resource.service);
    }

    @Test void produtoDeOutraUnidadeNaoAparece() {
        when(resource.service.findById(1L)).thenReturn(produto(1, "OUTRA"));
        assertEquals(404, resource.findById(1L).getStatus());
        when(resource.service.findByAtivo(true)).thenReturn(List.of(produto(1, "OUTRA"), produto(2, "QA")));
        assertEquals(List.of(2L), resource.findByAtivo(true).stream().map(PharmaciaProduto::getId).toList());
    }

    @Test void cadastroUsaUnidadeDaSessaoEIgnoraAEnviada() {
        PharmaciaProdutoDTO dto = new PharmaciaProdutoDTO(); dto.setId(99L); dto.setUnidadeSigla("OUTRA");
        resource.save(dto);
        verify(resource.scope).exigirArea(resource.requestContext, "QA", PharmaciaRequestScope.PRODUTOS);
        assertNull(dto.getId());
        assertEquals("QA", dto.getUnidadeSigla());
        verify(resource.service).saveFromDTO(dto);
    }

    @Test void edicaoEExclusaoDeProdutoDeOutraUnidadeNaoAlteramNada() {
        when(resource.service.findById(1L)).thenReturn(produto(1, "OUTRA"));
        assertEquals(404, resource.update(1L, new PharmaciaProdutoDTO()).getStatus());
        assertEquals(404, resource.delete(1L).getStatus());
        verify(resource.service, never()).saveFromDTO(any());
        verify(resource.service, never()).delete(any());
    }

    @Test void semPermissaoDeFarmaciaNaoCadastra() {
        when(resource.scope.exigirArea(resource.requestContext, "QA", PharmaciaRequestScope.PRODUTOS)).thenThrow(new ForbiddenException());
        assertThrows(ForbiddenException.class, () -> resource.save(new PharmaciaProdutoDTO()));
        verify(resource.service, never()).saveFromDTO(any());
    }
}

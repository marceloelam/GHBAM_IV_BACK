package br.com.iv.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.*;

import br.com.iv.entity.PharmaciaEstoque;
import br.com.iv.service.PharmaciaCategoriaService;
import br.com.iv.service.PharmaciaEstoqueService;
import br.com.iv.service.PharmaciaLoteService;
import br.com.iv.service.PharmaciaPermutaService;
import br.com.iv.service.PharmaciaRequestScope;
import jakarta.ws.rs.container.ContainerRequestContext;

/** Listagens da farmacia sem unidadeId devolviam TODAS as unidades; agora usam a unidade da sessao. */
class PharmaciaListagemSessaoTest {

    PharmaciaRequestScope scope;
    ContainerRequestContext req;

    @BeforeEach
    void preparar() {
        scope = mock(PharmaciaRequestScope.class);
        req = mock(ContainerRequestContext.class);
        when(scope.unidadeAtual(req)).thenReturn("QA");
    }

    @Test
    void estoqueSemFiltroUsaSessaoEComFiltroMantemConsulta() {
        PharmaciaEstoqueResource r = new PharmaciaEstoqueResource();
        r.service = mock(PharmaciaEstoqueService.class);
        r.scope = scope;
        r.request = req;
        r.listAll(null);
        verify(r.service).findByUnidadeSigla("QA");
        verify(r.service, never()).listAll();
        r.listAll("FORNECEDORA");
        verify(r.service).findByUnidadeSigla("FORNECEDORA");
    }

    @Test
    void estoqueBaixoSoDaSessao() {
        PharmaciaEstoqueResource r = new PharmaciaEstoqueResource();
        r.service = mock(PharmaciaEstoqueService.class);
        r.scope = scope;
        r.request = req;
        PharmaciaEstoque daqui = new PharmaciaEstoque();
        daqui.setUnidadeSigla("QA");
        PharmaciaEstoque outra = new PharmaciaEstoque();
        outra.setUnidadeSigla("OUTRA");
        when(r.service.findEstoqueBaixo()).thenReturn(List.of(daqui, outra));
        assertEquals(List.of(daqui), r.findEstoqueBaixo());
    }

    @Test
    void lotesSemFiltroUsaSessaoMasPermutaAindaVeFornecedora() {
        PharmaciaLoteResource r = new PharmaciaLoteResource();
        r.service = mock(PharmaciaLoteService.class);
        r.scope = scope;
        r.request = req;
        r.listAll(null);
        verify(r.service).findByUnidadeSigla("QA");
        verify(r.service, never()).listAll();
        r.listAll("FORNECEDORA");
        verify(r.service).findByUnidadeSigla("FORNECEDORA");
    }

    @Test
    void categoriasSemFiltroUsaSessao() {
        PharmaciaCategoriaResource r = new PharmaciaCategoriaResource();
        r.service = mock(PharmaciaCategoriaService.class);
        r.scope = scope;
        r.requestContext = req;
        r.listAll(null);
        verify(r.service).findByUnidadeSigla("QA");
        verify(r.service, never()).listAll();
    }

    @Test
    void permutasSemFiltroUsaSessao() {
        PharmaciaPermutaResource r = new PharmaciaPermutaResource();
        r.service = mock(PharmaciaPermutaService.class);
        r.scope = scope;
        r.requestContext = req;
        r.listAll(null, null);
        verify(r.service).findByUnidadeOrigemSigla("QA");
        verify(r.service).findByUnidadeDestinoSigla("QA");
        verify(r.service, never()).listAll();
    }
}

package br.com.iv.menufavorito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MenuFavoritoServiceTest {

    @Test
    void aceitaCaminhoInternoDoMenu() {
        assertTrue(MenuFavoritoService.caminhoValido("/base2/reimpressaodebam"));
        assertTrue(MenuFavoritoService.caminhoValido("/painel?unidade=HMS"));
    }

    @Test
    void recusaUrlExternaEEntradaMalformada() {
        assertFalse(MenuFavoritoService.caminhoValido(null));
        assertFalse(MenuFavoritoService.caminhoValido(""));
        assertFalse(MenuFavoritoService.caminhoValido("/"));
        assertFalse(MenuFavoritoService.caminhoValido("base2/tela"));
        assertFalse(MenuFavoritoService.caminhoValido("//site.exemplo/tela"));
        assertFalse(MenuFavoritoService.caminhoValido("https://site.exemplo"));
        assertFalse(MenuFavoritoService.caminhoValido("/a b"));
        assertFalse(MenuFavoritoService.caminhoValido("/" + "a".repeat(MenuFavoritoService.TAMANHO_MAXIMO)));
    }
}

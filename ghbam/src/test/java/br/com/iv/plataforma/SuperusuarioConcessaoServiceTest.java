package br.com.iv.plataforma;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SuperusuarioConcessaoServiceTest {

    @Test
    void comprovanteValeUmaVezSoParaQuemConfirmouEParaOAlvo() {
        SuperusuarioConcessaoService s = new SuperusuarioConcessaoService();
        String c = s.emitir("852852", "CONCEDER", "123");
        assertThrows(SuperusuarioConcessaoService.NegadoException.class, () -> s.consumir(c, "999", "CONCEDER", "123"));

        String c2 = s.emitir("852852", "CONCEDER", "123");
        assertThrows(SuperusuarioConcessaoService.NegadoException.class, () -> s.consumir(c2, "852852", "CONCEDER", "456"));

        String c3 = s.emitir("852852", "CONCEDER", "123");
        assertThrows(SuperusuarioConcessaoService.NegadoException.class, () -> s.consumir(c3, "852852", "DESIGNAR", "123"));

        String c4 = s.emitir("852852", "CONCEDER", "123");
        assertDoesNotThrow(() -> s.consumir(c4, "852852", "CONCEDER", "123"));
        assertThrows(SuperusuarioConcessaoService.NegadoException.class, () -> s.consumir(c4, "852852", "CONCEDER", "123"));
    }

    @Test
    void semComprovanteNegado() {
        SuperusuarioConcessaoService s = new SuperusuarioConcessaoService();
        assertThrows(SuperusuarioConcessaoService.NegadoException.class, () -> s.consumir(null, "852852", "CONCEDER", "1"));
        assertThrows(SuperusuarioConcessaoService.NegadoException.class, () -> s.consumir("inventado", "852852", "CONCEDER", "1"));
    }

    @Test
    void finalidadeDesconhecidaRecusada() {
        assertThrows(IllegalArgumentException.class, () -> SuperusuarioConcessaoService.finalidade("TUDO"));
    }

    @Test
    void regrasDeDesignacao() {
        // quemResponsavel, quemPodeDesignar, alvoResponsavel, alvoPodeDesignar, darPoderDesignar
        assertNull(SuperusuarioConcessaoService.recusaDesignacao(true, false, false, false, false));
        assertNull(SuperusuarioConcessaoService.recusaDesignacao(true, false, false, false, true));
        assertNull(SuperusuarioConcessaoService.recusaDesignacao(true, false, false, true, false));
        // autorizado a designar: so mexe em quem apenas concede
        assertNull(SuperusuarioConcessaoService.recusaDesignacao(false, true, false, false, false));
        assertNotNull(SuperusuarioConcessaoService.recusaDesignacao(false, true, false, false, true));
        assertNotNull(SuperusuarioConcessaoService.recusaDesignacao(false, true, false, true, false));
        assertNotNull(SuperusuarioConcessaoService.recusaDesignacao(false, true, true, false, false));
        // quem so concede (ou ninguem) nao designa
        assertNotNull(SuperusuarioConcessaoService.recusaDesignacao(false, false, false, false, false));
        // o responsavel nunca e alvo
        assertNotNull(SuperusuarioConcessaoService.recusaDesignacao(true, false, true, false, false));
    }
}

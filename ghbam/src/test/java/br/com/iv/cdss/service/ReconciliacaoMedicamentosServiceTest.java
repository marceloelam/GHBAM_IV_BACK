package br.com.iv.cdss.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.com.iv.cdss.dto.ReconciliacaoDecisaoRequest;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico;
import br.com.iv.security.AuthenticatedUser;

class ReconciliacaoMedicamentosServiceTest {

    static ReconciliacaoMedicamentosService service = new ReconciliacaoMedicamentosService();

    @BeforeAll
    static void carregar() throws Exception {
        BaseConhecimentoFarmaceutico base = new BaseConhecimentoFarmaceutico();
        Method m = BaseConhecimentoFarmaceutico.class.getDeclaredMethod("carregar");
        m.setAccessible(true);
        m.invoke(base);
        service.base = base;
    }

    @Test
    void reconheceContinuoJaPrescritoPorSinonimo() {
        assertTrue(service.constaNaPrescricao("Marevan 5mg 1x ao dia", List.of("Varfarina 5 mg VO")));
    }

    @Test
    void reconhecePelaPrimeiraPalavraQuandoForaDaBase() {
        assertTrue(service.constaNaPrescricao("Levotiroxina 50mcg", List.of("LEVOTIROXINA SODICA 50MCG VO jejum")));
    }

    @Test
    void naoConfundeMedicamentosDiferentes() {
        assertFalse(service.constaNaPrescricao("Losartana 50mg", List.of("Dipirona 1g EV", "Omeprazol 40mg")));
        assertFalse(service.constaNaPrescricao("", List.of("Dipirona")));
    }

    @Test
    void decisaoExigeSessaoEMotivoParaSuspender() {
        ReconciliacaoDecisaoRequest r = new ReconciliacaoDecisaoRequest();
        r.setIdbam(1);
        r.setIdpaciente(2);
        r.setIditem(3);
        r.setDecisao("SUSPENDER");
        assertThrows(SecurityException.class, () -> service.decidir(r, null));

        AuthenticatedUser usuario = new AuthenticatedUser("1", "999", "Teste", null, "HMSFX", null, null, null, null,
                null, null, null, false, Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.decidir(r, usuario));
        r.setDecisao("QUALQUER");
        assertThrows(IllegalArgumentException.class, () -> service.decidir(r, usuario));
    }
}

package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.iv.dto.LaudoAutorizacaoDto;
import br.com.iv.entity.LaudoAutorizacao;
import br.com.iv.repository.LaudoAutorizacaoRepository;
import jakarta.ws.rs.NotFoundException;

class LaudoAutorizacaoServiceTest {

    private LaudoAutorizacaoService service;
    private FakeLaudoAutorizacaoRepository repository;

    @BeforeEach
    void setUp() {
        service = new LaudoAutorizacaoService();
        repository = new FakeLaudoAutorizacaoRepository();
        service.laudoAutorizacaoRepository = repository;
    }

    @Test
    void encaminhaAoNirQuandoExisteExatamenteUmTratamento() {
        repository.laudo = laudo(475754);
        repository.tratamentosAtualizados = 1;

        LaudoAutorizacao resultado = service.encaminharAoNir(10);

        assertEquals(475754, resultado.getBam());
        assertTrue(repository.registroSinalizado);
    }

    @Test
    void naoSinalizaRegistroQuandoTratamentoNaoFoiLocalizado() {
        repository.laudo = laudo(475754);
        repository.tratamentosAtualizados = 0;

        assertThrows(IllegalStateException.class, () -> service.encaminharAoNir(10));
        assertFalse(repository.registroSinalizado);
    }

    @Test
    void rejeitaEncaminhamentoQuandoMaisDeUmTratamentoSeriaAlterado() {
        repository.laudo = laudo(475754);
        repository.tratamentosAtualizados = 2;

        assertThrows(IllegalStateException.class, () -> service.encaminharAoNir(10));
        assertFalse(repository.registroSinalizado);
    }

    @Test
    void alterarLaudoInexistenteRetornaNotFound() {
        repository.laudo = null;

        assertThrows(NotFoundException.class,
                () -> service.atualizarPaciente(999, new LaudoAutorizacaoDto()));
    }

    private static LaudoAutorizacao laudo(int bam) {
        LaudoAutorizacao laudo = new LaudoAutorizacao();
        laudo.setBam(bam);
        return laudo;
    }

    private static class FakeLaudoAutorizacaoRepository extends LaudoAutorizacaoRepository {
        private LaudoAutorizacao laudo;
        private int tratamentosAtualizados;
        private boolean registroSinalizado;

        @Override
        public LaudoAutorizacao findById(Long id) {
            return laudo;
        }

        @Override
        public int encaminharTratamentoAoNir(int bam) {
            return tratamentosAtualizados;
        }

        @Override
        public void sinalizarRegistroEncaminhadoAoNir(int bam) {
            registroSinalizado = true;
        }
    }
}

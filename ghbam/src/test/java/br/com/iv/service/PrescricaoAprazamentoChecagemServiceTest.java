package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.iv.dto.PrescricaoAprazamentoChecagemDto;
import br.com.iv.entity.Prescricao;
import br.com.iv.entity.PrescricaoAprazamentoChecagem;
import br.com.iv.repository.PrescricaoAprazamentoChecagemRepository;
import br.com.iv.repository.PrescricaoRepository;

/** Regras de seguranca do aprazamento/checagem de medicacao (rastreabilidade da administracao). */
class PrescricaoAprazamentoChecagemServiceTest {

    private PrescricaoAprazamentoChecagemService service;
    private PrescricaoAprazamentoChecagemRepository repository;

    @BeforeEach
    void setUp() {
        service = new PrescricaoAprazamentoChecagemService();
        repository = mock(PrescricaoAprazamentoChecagemRepository.class);
        PrescricaoRepository prescricaoRepository = mock(PrescricaoRepository.class);
        service.repository = repository;
        service.prescricaoRepository = prescricaoRepository;

        Prescricao prescricao = new Prescricao();
        prescricao.setBam(777);
        when(prescricaoRepository.findById(10)).thenReturn(prescricao);
        when(repository.buscarHorario(anyInt(), anyInt())).thenReturn(Optional.empty());
    }

    private static PrescricaoAprazamentoChecagem registro(int indice, boolean checado) {
        PrescricaoAprazamentoChecagem r = new PrescricaoAprazamentoChecagem();
        r.setIdprescricao(10);
        r.setIndiceHorario(indice);
        r.setHorarioAprazado("08:00");
        r.setChecado(checado);
        return r;
    }

    private static PrescricaoAprazamentoChecagemDto horario(int indice, String hora) {
        PrescricaoAprazamentoChecagemDto dto = new PrescricaoAprazamentoChecagemDto();
        dto.setIndiceHorario(indice);
        dto.setHorarioAprazado(hora);
        return dto;
    }

    @Test
    void reaprazarNuncaApagaDoseJaChecada() {
        PrescricaoAprazamentoChecagem checada = registro(1, true);
        PrescricaoAprazamentoChecagem pendente = registro(2, false);
        when(repository.listarPorPrescricao(10)).thenReturn(List.of(checada, pendente));

        // Enfermagem refaz o aprazamento so com o horario 3: 1 (checado) e 2 (pendente) sairam da lista.
        service.salvarHorarios(10, List.of(horario(3, "14:00")));

        verify(repository, never()).delete(checada);
        verify(repository).delete(pendente);
    }

    @Test
    void novoHorarioHerdaBamDaPrescricao() {
        when(repository.listarPorPrescricao(10)).thenReturn(List.of());
        when(repository.isPersistent(any())).thenReturn(false);

        service.salvarHorarios(10, List.of(horario(1, "06:00")));

        verify(repository).persist(argThat((PrescricaoAprazamentoChecagem r) ->
                r.getIdbam() == 777 && r.getIndiceHorario() == 1 && "06:00".equals(r.getHorarioAprazado())
                        && r.getAprazadoEm() != null));
    }

    @Test
    void horariosForaDe1a8OuVaziosSaoIgnorados() {
        when(repository.listarPorPrescricao(10)).thenReturn(List.of());

        service.salvarHorarios(10, Arrays.asList(horario(0, "06:00"), horario(9, "06:00"), horario(2, " "), null));

        verify(repository, never()).persist(any(PrescricaoAprazamentoChecagem.class));
    }

    @Test
    void prescricaoInexistenteEhRejeitada() {
        assertThrows(IllegalArgumentException.class, () -> service.salvarHorarios(99, List.of(horario(1, "06:00"))));
    }

    @Test
    void checarRegistraQuemEQuando() {
        PrescricaoAprazamentoChecagem r = registro(1, false);
        when(repository.buscarHorario(10, 1)).thenReturn(Optional.of(r));
        PrescricaoAprazamentoChecagemDto dto = new PrescricaoAprazamentoChecagemDto();
        dto.setChecadoPor("TECNICO TESTE");
        dto.setChecadoPorMatricula("12345");

        service.checarHorario(10, 1, dto);

        assertTrue(r.isChecado());
        assertEquals("12345", r.getChecadoPorMatricula());
        assertNotNull(r.getChecadoEm());
    }

    @Test
    void checarHorarioInvalidoOuInexistenteFalha() {
        assertThrows(IllegalArgumentException.class, () -> service.checarHorario(10, 9, null));
        assertThrows(IllegalArgumentException.class, () -> service.checarHorario(10, 1, null));
    }

    @Test
    void desfazerChecagemLimpaResponsavel() {
        PrescricaoAprazamentoChecagem r = registro(1, true);
        r.setChecadoPorMatricula("12345");
        when(repository.buscarHorario(10, 1)).thenReturn(Optional.of(r));

        service.desfazerChecagem(10, 1);

        assertFalse(r.isChecado());
        assertNull(r.getChecadoPorMatricula());
        assertNull(r.getChecadoEm());
    }
}

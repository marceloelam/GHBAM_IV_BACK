package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.List;
import br.com.iv.dto.PharmaciaConfirmacaoRequest;
import br.com.iv.entity.*;
import br.com.iv.pharmacia.validacao.dto.*;
import br.com.iv.pharmacia.validacao.entity.PharmaciaValidacao;
import br.com.iv.pharmacia.validacao.service.ValidacaoFarmaceuticaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PharmaciaConfirmacaoServiceTest {
    PharmaciaConfirmacaoService service;
    PharmaciaConfirmacaoRequest req;
    ValidacaoFarmaceuticaResponse resultado;
    @BeforeEach @SuppressWarnings("unchecked") void preparar() {
        service = new PharmaciaConfirmacaoService(); service.em = mock(EntityManager.class);
        service.dispensacoes = mock(PharmaciaDispensacaoService.class); service.validacao = mock(ValidacaoFarmaceuticaService.class);
        service.mapper = new ObjectMapper(); service.controle = mock(PharmaciaDispensacaoControleService.class); when(service.controle.recuperar(any(), any())).thenReturn(null);
        Pacientes paciente = new Pacientes(); paciente.setIdpaciente(1); paciente.setNome("PACIENTE FICTICIO"); paciente.setNascimento("1990-01-01");
        when(service.em.find(Pacientes.class, 1, LockModeType.PESSIMISTIC_WRITE)).thenReturn(paciente);
        PharmaciaProduto produto = new PharmaciaProduto(); produto.setId(1L); produto.setNomeComercial("PRODUTO CADASTRADO"); produto.setAtivo(true);
        PharmaciaLote lote = new PharmaciaLote(); lote.setId(1L); lote.setProduto(produto); lote.setDataValidade(LocalDate.now().plusDays(30));
        when(service.em.find(PharmaciaLote.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(lote);
        when(service.em.find(PharmaciaLote.class, 1L)).thenReturn(lote);
        TypedQuery<PharmaciaDispensacao> emCurso = mock(TypedQuery.class);
        when(service.em.createQuery(anyString(), eq(PharmaciaDispensacao.class))).thenReturn(emCurso);
        when(emCurso.setParameter(anyString(), any())).thenReturn(emCurso); when(emCurso.getResultList()).thenReturn(List.of());
        TypedQuery<ProntuarioClinicoItem> query = mock(TypedQuery.class);
        when(service.em.createQuery(anyString(), eq(ProntuarioClinicoItem.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        ProntuarioClinicoItem alergia = new ProntuarioClinicoItem(); alergia.setTitulo("ALERGIA CADASTRADA");
        when(query.getResultList()).thenReturn(List.of(alergia));
        PharmaciaDispensacao d = new PharmaciaDispensacao(); d.setPacienteId(1L); d.setPacienteNome("NOME INFORMADO"); d.setLote(lote);
        d.setQuantidade(3); d.setDuracao(30); d.setMedicoNome("MEDICO TESTE"); d.setMedicoCRM("QA"); d.setUnidadeSigla("QA"); d.setUsuarioMatricula("QA001");
        req = new PharmaciaConfirmacaoRequest(); req.itens = List.of(d); req.contexto = new ValidacaoDispensacaoRequest();
        req.contexto.setAlergias(List.of());
        resultado = new ValidacaoFarmaceuticaResponse();
        when(service.validacao.validar(any())).thenReturn(resultado);
        when(service.dispensacoes.saveBatch(any())).thenAnswer(i -> i.getArgument(0));
    }
    @Test void usaPacienteProdutoEAlergiasDoBanco() {
        service.confirmar(req);
        ArgumentCaptor<ValidacaoDispensacaoRequest> c = ArgumentCaptor.forClass(ValidacaoDispensacaoRequest.class);
        verify(service.validacao).validar(c.capture());
        assertEquals("PACIENTE FICTICIO", c.getValue().getPacienteNome());
        assertEquals(List.of("ALERGIA CADASTRADA"), c.getValue().getAlergias());
        assertEquals("PRODUTO CADASTRADO", c.getValue().getItens().get(0).getProdutoNome());
        verify(service.em).persist(isA(PharmaciaValidacao.class));
    }
    @Test void criticoSemJustificativaNaoDispensa() {
        resultado.setPossuiCritico(true);
        assertThrows(IllegalArgumentException.class, () -> service.confirmar(req));
        verify(service.dispensacoes, never()).saveBatch(any()); verify(service.em, never()).persist(any());
    }
    @Test void falhaDeValidacaoNaoDispensa() {
        when(service.validacao.validar(any())).thenThrow(new IllegalStateException("FALHA TESTE"));
        assertThrows(IllegalStateException.class, () -> service.confirmar(req));
        verify(service.dispensacoes, never()).saveBatch(any());
    }
    @Test void falhaDeBaixaNaoRegistraSucessoClinico() {
        when(service.dispensacoes.saveBatch(any())).thenThrow(new IllegalArgumentException("Estoque insuficiente"));
        assertThrows(IllegalArgumentException.class, () -> service.confirmar(req));
        verify(service.em, never()).persist(any());
    }
}

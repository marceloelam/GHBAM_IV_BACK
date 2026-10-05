package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.List;
import br.com.iv.dto.PharmaciaConfirmacaoRequest;
import br.com.iv.entity.*;
import br.com.iv.pharmacia.validacao.dto.ItemDispensacaoDto;
import br.com.iv.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;

class PharmaciaDispensacaoControleServiceTest {
    PharmaciaDispensacaoControleService service;
    PharmaciaDispensacao d;
    PharmaciaProduto p;
    @BeforeEach void preparar() {
        service = new PharmaciaDispensacaoControleService(); service.em = mock(EntityManager.class); service.mapper = new ObjectMapper().findAndRegisterModules();
        service.security = mock(SecurityPermissionService.class); service.rate = mock(LoginRateLimitService.class);
        d = new PharmaciaDispensacao(); d.setPacienteId(1L); d.setUnidadeSigla("QA"); d.setConferida(true); d.setConferenciaJustificativa("Conferencia documentada");
        d.setReceitaReferencia("RX TESTE"); d.setReceitaData(LocalDate.now().minusDays(1)); d.setReceitaValidade(LocalDate.now().plusDays(1));
        p = new PharmaciaProduto(); p.setId(1L); p.setAtivo(true);
    }
    @Test void conferenciaObrigatoria() { d.setConferida(false); assertThrows(IllegalArgumentException.class, () -> PharmaciaDispensacaoControleService.validarConferencia(d, p)); }
    @Test void conferenciaPrecisaRegistro() { d.setConferenciaJustificativa(""); assertThrows(IllegalArgumentException.class, () -> PharmaciaDispensacaoControleService.validarConferencia(d, p)); }
    @Test void externaExigeReferencia() { d.setReceitaReferencia(null); assertThrows(IllegalArgumentException.class, () -> PharmaciaDispensacaoControleService.validarConferencia(d, p)); }
    @Test void receitaVencidaNaoDispensa() { d.setReceitaValidade(LocalDate.now().minusDays(1)); assertThrows(IllegalArgumentException.class, () -> PharmaciaDispensacaoControleService.validarConferencia(d, p)); }
    @Test void receitaFuturaNaoDispensa() { d.setReceitaData(LocalDate.now().plusDays(1)); assertThrows(IllegalArgumentException.class, () -> PharmaciaDispensacaoControleService.validarConferencia(d, p)); }
    @Test void validadeHojeEValida() { d.setReceitaValidade(LocalDate.now()); assertDoesNotThrow(() -> PharmaciaDispensacaoControleService.validarConferencia(d, p)); }
    @Test void antimicrobianoExigeArquivo() { p.setAntimicrobiano(true); assertThrows(IllegalArgumentException.class, () -> PharmaciaDispensacaoControleService.validarConferencia(d, p)); d.setReceitaRetida(true); assertDoesNotThrow(() -> PharmaciaDispensacaoControleService.validarConferencia(d, p)); }
    @Test void controladoExigeArquivo() { p.setListaControlada("C1"); assertThrows(IllegalArgumentException.class, () -> PharmaciaDispensacaoControleService.validarConferencia(d, p)); }
    @Test void itemSuspensoNaoDispensa() { d.setPrescricaoItemId(1); d.setBamId(1); Bam b = new Bam(); Pacientes paciente = new Pacientes(); paciente.setIdpaciente(1); b.setPaciente(paciente); b.setUnidade("QA"); when(service.em.find(Bam.class, 1)).thenReturn(b); PrescricaoItemEstruturado rx = new PrescricaoItemEstruturado(); rx.setStatus("SUSPENSO"); when(service.em.find(PrescricaoItemEstruturado.class, 1, LockModeType.PESSIMISTIC_WRITE)).thenReturn(rx); assertThrows(IllegalArgumentException.class, () -> service.conferir(d, p, new ItemDispensacaoDto())); }
    @Test void bamDeOutroPacienteNaoDispensa() { d.setBamId(1); Bam b = new Bam(); Pacientes outro = new Pacientes(); outro.setIdpaciente(2); b.setPaciente(outro); b.setUnidade("QA"); when(service.em.find(Bam.class, 1)).thenReturn(b); assertThrows(IllegalArgumentException.class, () -> service.conferir(d, p, new ItemDispensacaoDto())); }
    @Test void senhaNaoCompõeHashNemHistoricoJson() throws Exception { PharmaciaConfirmacaoRequest r = new PharmaciaConfirmacaoRequest(); r.itens = List.of(d); r.chaveConfirmacao = "CHAVE1"; r.duplaChecagemSenha = "SEGREDO FICTICIO 1"; String hash = service.hash(r); r.duplaChecagemSenha = "SEGREDO FICTICIO 2"; r.chaveConfirmacao = "CHAVE2"; assertEquals(hash, service.hash(r)); assertFalse(service.mapper.writeValueAsString(r).contains("SEGREDO")); }
    @Test void segundoNaoPodeSerOPrimeiro() { PharmaciaConfirmacaoRequest r = new PharmaciaConfirmacaoRequest(); r.duplaChecagemMatricula = "P1"; r.duplaChecagemSenha = "TESTE"; assertThrows(IllegalArgumentException.class, () -> service.autenticarSegundo(r, "P1")); verifyNoInteractions(service.security); }
    @Test void matriculaSemSenhaNaoConfere() { PharmaciaConfirmacaoRequest r = new PharmaciaConfirmacaoRequest(); r.duplaChecagemMatricula = "P2"; assertThrows(IllegalArgumentException.class, () -> service.autenticarSegundo(r, "P1")); }
    @Test void credencialInvalidaContaTentativa() { PharmaciaConfirmacaoRequest r = new PharmaciaConfirmacaoRequest(); r.duplaChecagemMatricula = "P2"; r.duplaChecagemSenha = "INVALIDA"; assertThrows(IllegalArgumentException.class, () -> service.autenticarSegundo(r, "P1")); verify(service.rate).registrarFalhaMatricula("P2"); assertNull(r.duplaChecagemSenha); }
    @Test void contaBloqueadaNaoTentaAutenticar() { PharmaciaConfirmacaoRequest r = new PharmaciaConfirmacaoRequest(); r.duplaChecagemMatricula = "P2"; r.duplaChecagemSenha = "TESTE"; when(service.rate.segundosBloqueadoMatricula("P2")).thenReturn(10L); assertThrows(IllegalArgumentException.class, () -> service.autenticarSegundo(r, "P1")); verifyNoInteractions(service.security); }
}

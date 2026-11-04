package br.com.iv.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.PrescricaoItemEstruturado;
import br.com.iv.security.RoutePermissionCatalog;
import br.com.iv.service.BamService;
import br.com.iv.service.PacientesService;
import br.com.iv.service.PharmaciaRequestScope;
import br.com.iv.service.PrescricaoItemEstruturadoService;
import br.com.iv.service.ProntuarioClinicoItemService;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;

class PharmaciaDispensacaoAtendimentoTest {
    PharmaciaDispensacaoResource resource;

    @BeforeEach
    void preparar() {
        resource = new PharmaciaDispensacaoResource();
        resource.scope = mock(PharmaciaRequestScope.class);
        resource.request = mock(ContainerRequestContext.class);
        resource.pacientes = mock(PacientesService.class);
        resource.prontuarioClinico = mock(ProntuarioClinicoItemService.class);
        resource.bams = mock(BamService.class);
        resource.prescricaoItens = mock(PrescricaoItemEstruturadoService.class);
        when(resource.scope.unidadeAtual(resource.request)).thenReturn("QA");
    }

    private void negarArea() {
        when(resource.scope.exigirArea(resource.request, "QA", PharmaciaRequestScope.DISPENSACAO))
                .thenThrow(new ForbiddenException());
    }

    private PrescricaoItemEstruturado item(String tipo) {
        PrescricaoItemEstruturado item = new PrescricaoItemEstruturado();
        item.setTipoItem(tipo);
        return item;
    }

    @Test
    void buscaDevolveSoOsCamposDoBalcao() {
        Pacientes paciente = new Pacientes();
        paciente.setNome("MARIA FICTICIA");
        paciente.setProntuario("2026-1");
        when(resource.pacientes.xlocalizarPaciente(eq("MARIA"), any())).thenReturn(List.of(paciente));

        List<Map<String, Object>> lista = resource.buscarPacientes("MARIA");

        verify(resource.scope).exigirArea(resource.request, "QA", PharmaciaRequestScope.DISPENSACAO);
        assertEquals(1, lista.size());
        assertEquals("MARIA FICTICIA", lista.get(0).get("nome"));
        assertEquals(java.util.Set.of("idpaciente", "nome", "nascimento", "idade", "mae", "cpf", "sexo"), lista.get(0).keySet());
    }

    @Test
    void buscaComMenosDeDuasLetrasNaoConsulta() {
        assertTrue(resource.buscarPacientes(" M ").isEmpty());
        verifyNoInteractions(resource.pacientes);
    }

    @Test
    void alergiaVemDaLeituraDeAlergiasAtivas() {
        when(resource.prontuarioClinico.listarAlergiasAtivas(7)).thenReturn(List.of("DIPIRONA"));

        assertEquals(List.of("DIPIRONA"), resource.listarAlergias(7));
    }

    @Test
    void falhaAoConsultarAlergiaNaoViraListaVazia() {
        when(resource.prontuarioClinico.listarAlergiasAtivas(7)).thenThrow(new IllegalStateException("banco"));

        assertThrows(IllegalStateException.class, () -> resource.listarAlergias(7));
    }

    @Test
    void prescricaoUsaUltimoBamDaUnidadeDaSessaoESoMedicamento() {
        Bam bam = new Bam();
        bam.setIdbam(55);
        PrescricaoItemEstruturado medicamento = item("medicamento");
        when(resource.bams.filtrarPorIdDoPcienteUltimoBam(7, "QA")).thenReturn(bam);
        when(resource.prescricaoItens.listarPorBam(55, true)).thenReturn(List.of(medicamento, item("DIETA")));

        Map<String, Object> resposta = resource.listarPrescricao(7);

        assertEquals(55, resposta.get("idbam"));
        assertEquals(List.of(medicamento), resposta.get("itens"));
    }

    @Test
    void pacienteSemBamNaUnidadeVoltaSemItens() {
        Map<String, Object> resposta = resource.listarPrescricao(7);

        assertNull(resposta.get("idbam"));
        assertEquals(List.of(), resposta.get("itens"));
        verifyNoInteractions(resource.prescricaoItens);
    }

    @Test
    void semAreaDeDispensacaoNadaEhConsultado() {
        negarArea();

        assertThrows(ForbiddenException.class, () -> resource.buscarPacientes("MARIA"));
        assertThrows(ForbiddenException.class, () -> resource.listarAlergias(7));
        assertThrows(ForbiddenException.class, () -> resource.listarPrescricao(7));
        verifyNoInteractions(resource.pacientes, resource.prontuarioClinico, resource.bams, resource.prescricaoItens);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pharmacia/dispensacao/pacientes/MARIA",
            "pharmacia/dispensacao/paciente/7/alergias",
            "pharmacia/dispensacao/paciente/7/prescricao"
    })
    void rotasAlcancamOsPerfisQueAbremATela(String rota) {
        List<String> permissoes = new RoutePermissionCatalog().requiredPermissionsFor(rota).orElseThrow();
        assertTrue(permissoes.containsAll(List.of("kfarmaciacentral", "kfarmhmsfx", "kremedios", "pfarmaciadispensacao", "kadm")), rota);
    }
}

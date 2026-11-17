package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** Retirar pedido medico: so o medico solicitante e so ate 6 h depois do pedido. */
class PedidoMedico2RetiradaTest {

    private static final LocalDateTime PEDIDO = LocalDateTime.of(2026, 11, 16, 8, 0);

    private static String motivo(String crmUsuario, String nomeUsuario, LocalDateTime agora) {
        return PedidoMedico2Service.motivoRecusaRetirada("1234567 8", "MEDICO EXEMPLO DA SILVA", "16/11/2026", "08:00",
                crmUsuario, nomeUsuario, agora);
    }

    @Test
    void medicoSolicitanteDentroDoPrazoPodeRetirar() {
        assertNull(motivo("12345678", "Outro Nome", PEDIDO.plusHours(1)));
        assertNull(motivo("12.34567-8", "", PEDIDO.plusHours(6))); // limite exato ainda vale
        // CRM do cadastro escrito diferente: o nome resolve.
        assertNull(motivo("999", "Médico  Exemplo da Silva", PEDIDO.plusMinutes(5)));
    }

    @Test
    void outroMedicoNaoRetira() {
        assertNotNull(motivo("87654321", "OUTRO MEDICO", PEDIDO.plusMinutes(1)));
        assertNotNull(motivo("", "", PEDIDO.plusMinutes(1)));
        assertNotNull(motivo(null, null, PEDIDO.plusMinutes(1)));
    }

    @Test
    void depoisDeSeisHorasNinguemRetira() {
        assertNotNull(motivo("12345678", "MEDICO EXEMPLO DA SILVA", PEDIDO.plusHours(6).plusMinutes(1)));
        assertNotNull(motivo("12345678", "MEDICO EXEMPLO DA SILVA", PEDIDO.plusDays(1)));
    }

    @Test
    void dataInvalidaNaoPermite() {
        assertNotNull(PedidoMedico2Service.motivoRecusaRetirada("1", "A", "", "", "1", "A", PEDIDO));
        assertNotNull(PedidoMedico2Service.motivoRecusaRetirada("1", "A", "2026-11-16", "08:00", "1", "A", PEDIDO));
    }
}

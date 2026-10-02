package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Casos reais de invencao observados no ditado do /atmedico. O paciente disse apenas
// "dor de cabeca ha dois dias, vomito e tontura" e a anamnese saiu com "melhoram com
// gelo". Estes testes travam o filtro que remove o que nao foi dito.
class AncoragemClinicaTest {

    private static final String FALA_DOR_DE_CABECA =
            "doutor, tenho tido dor de cabeca ha dois dias, vomito e tontura";

    @Test
    void removeFatorDeMelhoraQueNinguemDisse() {
        String interpretacao = """
                Queixa principal: Dor de cabeca ha 2 dias.
                Historia da doenca atual: Paciente relata dor de cabeca ha 2 dias, que melhoram com gelo.""";

        String filtrada = AncoragemClinica.filtrar(interpretacao, FALA_DOR_DE_CABECA);

        assertFalse(filtrada.toLowerCase().contains("gelo"));
        assertTrue(filtrada.contains("Paciente relata dor de cabeca ha 2 dias."));
    }

    // Numero por extenso na fala e digito na interpretacao sao a mesma informacao.
    @Test
    void aceitaNumeroPorExtensoComoApoioDoDigito() {
        String filtrada = AncoragemClinica.filtrar("Queixa principal: Dor de cabeca ha 2 dias.", FALA_DOR_DE_CABECA);

        assertEquals("Queixa principal: Dor de cabeca ha 2 dias.", filtrada);
    }

    // O medico escreve "cefaleia" onde o paciente falou "dor de cabeca".
    @Test
    void aceitaTermoTecnicoEquivalenteAoTermoLeigo() {
        String filtrada = AncoragemClinica.filtrar("Sintomas associados: Cefaleia e vomitos.", FALA_DOR_DE_CABECA);

        assertTrue(filtrada.contains("Cefaleia e vomitos."));
    }

    @Test
    void campoSemApoioNaFalaViraNaoInformado() {
        String interpretacao = """
                Queixa principal: Dor de cabeca ha 2 dias.
                Antecedentes/alergias/medicamentos: Hipertensao em uso de losartana.""";

        String filtrada = AncoragemClinica.filtrar(interpretacao, FALA_DOR_DE_CABECA);

        assertEquals("""
                Queixa principal: Dor de cabeca ha 2 dias.
                Antecedentes/alergias/medicamentos: Nao informado.""", filtrada);
    }

    // Se a IA devolver uma anamnese inteira sem apoio, nada dela pode ir ao prontuario:
    // o registro fica so com a fala transcrita.
    @Test
    void interpretacaoTotalmenteInventadaEDescartada() {
        String interpretacao = """
                Queixa principal: Dor toracica ha 3 horas.
                Historia da doenca atual: Paciente refere precordialgia irradiada para o membro superior esquerdo.""";

        assertEquals("", AncoragemClinica.filtrar(interpretacao, FALA_DOR_DE_CABECA));
    }

    @Test
    void preservaOsSintomasQueForamRealmenteDitos() {
        String fala = "bom dia doutor, ao longo destes dois dias tenho tido febre e vomito, "
                + "tenho diarreia, tenho comido pouco e tenho sentido um pouco de tontura";
        String interpretacao = """
                Queixa principal: Febre e vomitos ha 2 dias.
                Sintomas associados: Diarreia e tontura.""";

        String filtrada = AncoragemClinica.filtrar(interpretacao, fala);

        assertTrue(filtrada.contains("Febre e vomitos ha 2 dias."));
        assertTrue(filtrada.contains("Diarreia e tontura."));
    }

    @Test
    void entradaVaziaNaoQuebra() {
        assertEquals("", AncoragemClinica.filtrar(null, FALA_DOR_DE_CABECA));
        assertEquals("", AncoragemClinica.filtrar("   ", FALA_DOR_DE_CABECA));
    }
}

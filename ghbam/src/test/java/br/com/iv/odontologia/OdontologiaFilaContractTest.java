package br.com.iv.odontologia;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OdontologiaFilaContractTest {
    @Test
    void filaRespeitaConfiguracaoDeTriagemDoSetor() throws Exception {
        String fonte = Files.readString(Path.of(
                "src/main/java/br/com/iv/odontologia/OdontologiaService.java"));

        assertTrue(fonte.contains("s.triar = true"));
        assertTrue(fonte.contains("a.avaliado = 1"));
        assertTrue(fonte.contains("CAST(b.data AS date) = CAST(:data AS date)"));
    }

    @Test
    void filaReconheceTodosOsNomesDeSetorOdontologico() throws Exception {
        String fonte = Files.readString(Path.of(
                "src/main/java/br/com/iv/odontologia/OdontologiaService.java"));

        assertTrue(fonte.contains("'ODONTOLOGIA', 'DENTISTA', 'BUCOMAXILO'"));
        assertTrue(fonte.contains("UPPER(r.setor)"));
    }

    @Test
    void filaPreservaOrigemEModalidadeDoAgendamentoQueVirouBam() throws Exception {
        String fonte = Files.readString(Path.of(
                "src/main/java/br/com/iv/odontologia/OdontologiaService.java"));

        assertTrue(fonte.contains("ep.agbam = b.idbam"));
        assertTrue(fonte.contains("AS agendado"));
        assertTrue(fonte.contains("AS modalidade"));
        assertTrue(fonte.contains("AS medico_agendado"));
        assertTrue(fonte.contains("AS id_medico_agendado"));
        assertTrue(fonte.contains("setPodeAtender"));
    }
}

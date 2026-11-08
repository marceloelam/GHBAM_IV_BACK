package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.iv.entity.Servidor2;

class RegrasCoordenacaoSetorTest {

    private static final Set<String> TODAS = RegrasCoordenacaoSetor.chavesServidor2();

    @Test
    void leAsColunasBooleanasDoServidor2() {
        assertTrue(TODAS.contains("vigilanciaepidemiologica"));
        assertTrue(TODAS.contains("superusuario"));
        assertFalse(TODAS.contains("matricula"));
        assertFalse(TODAS.contains("nome"));
    }

    @Test
    void coordenadorSoConcedeOQueTemENuncaReservado() {
        List<String> concediveis = RegrasCoordenacaoSetor.concediveis(TODAS, Set.of("vigilanciaepidemiologica", "superusuario", "logs"), false);
        assertEquals(List.of("vigilanciaepidemiologica"), concediveis);
    }

    @Test
    void administracaoConcedeTudoMenosReservado() {
        List<String> concediveis = RegrasCoordenacaoSetor.concediveis(TODAS, Set.of(), true);
        assertTrue(concediveis.contains("vigilanciaepidemiologica"));
        assertFalse(concediveis.contains("superusuario"));
        assertFalse(concediveis.contains("cadusuarios"));
        assertTrue(concediveis.stream().noneMatch(chave -> chave.endsWith("_adm")));
    }

    @Test
    void recusasDeSeguranca() {
        List<String> concediveis = List.of("vigilanciaepidemiologica");
        assertEquals("Você não coordena este setor.",
                RegrasCoordenacaoSetor.recusa(false, false, "MEMBRO", "1", "2", "vigilanciaepidemiologica", concediveis));
        assertEquals("O servidor não faz parte da equipe deste setor.",
                RegrasCoordenacaoSetor.recusa(false, true, null, "1", "2", "vigilanciaepidemiologica", concediveis));
        assertTrue(RegrasCoordenacaoSetor.recusa(false, true, "MEMBRO", "1", "1", "vigilanciaepidemiologica", concediveis)
                .startsWith("Ninguém altera os próprios"));
        assertEquals("Coordenador não altera acessos de outro coordenador.",
                RegrasCoordenacaoSetor.recusa(false, true, "COORDENADOR", "1", "2", "vigilanciaepidemiologica", concediveis));
        assertEquals("Acesso desconhecido.",
                RegrasCoordenacaoSetor.recusa(false, true, "MEMBRO", "1", "2", "nome; DROP TABLE servidor2", concediveis));
        assertEquals("Este acesso é reservado à administração.",
                RegrasCoordenacaoSetor.recusa(false, true, "MEMBRO", "1", "2", "superusuario", concediveis));
        assertEquals("Você só pode conceder acessos que você mesmo possui.",
                RegrasCoordenacaoSetor.recusa(false, true, "MEMBRO", "1", "2", "nir", concediveis));
        assertNull(RegrasCoordenacaoSetor.recusa(false, true, "MEMBRO", "1", "2", "vigilanciaepidemiologica", concediveis));
    }

    @Test
    void setorDoMenuFiltraAcessosEPerfisDoGrupo() {
        assertTrue(RegrasCoordenacaoSetor.setorDoMenu("Laboratório"));
        assertEquals(List.of("laboratorio"), RegrasCoordenacaoSetor.perfisDoSetor("Laboratório"));
        assertEquals(List.of("rx", "tomografia", "ultrassonografia"), RegrasCoordenacaoSetor.perfisDoSetor("Imagem"));
        // Categoria profissional nunca entra: o cargo define.
        assertTrue(RegrasCoordenacaoSetor.perfisDoSetor("Médico").isEmpty());
        assertTrue(RegrasCoordenacaoSetor.acessosDoSetor("Vigilância", List.of("vigilanciaepidemiologica", "nir")).contains("vigilanciaepidemiologica"));
        assertFalse(RegrasCoordenacaoSetor.acessosDoSetor("Vigilância", List.of("vigilanciaepidemiologica", "nir")).contains("nir"));
        // Setor antigo, fora do menu: sem filtro por grupo.
        assertEquals(List.of("nir"), RegrasCoordenacaoSetor.acessosDoSetor("SETOR LIVRE", List.of("nir")));
    }

    @Test
    void regraDoCargoParaPerfis() {
        assertNull(RegrasCoordenacaoSetor.recusaPerfil("laboratorio", "SERVIDOR"));
        assertEquals("Triagem e sala de medicação exigem cargo profissional.", RegrasCoordenacaoSetor.recusaPerfil("triagem", ""));
        assertNull(RegrasCoordenacaoSetor.recusaPerfil("triagem", "ENFERMEIRO"));
        assertEquals("Cargo multidisciplinar recebe só a própria categoria.", RegrasCoordenacaoSetor.recusaPerfil("recepcao", "psic"));
        assertEquals("Este perfil é definido pelo cargo ou pela administração.", RegrasCoordenacaoSetor.recusaPerfil("medico", "MEDICO"));
        assertEquals("Este perfil é definido pelo cargo ou pela administração.", RegrasCoordenacaoSetor.recusaPerfil("administracao", "SERVIDOR"));
    }

    @Test
    void leValoresLigadosDoServidor2() {
        Servidor2 s2 = new Servidor2();
        s2.setVigilanciaepidemiologica(true);
        s2.setNir(false);
        assertTrue(RegrasCoordenacaoSetor.ligados(s2).contains("vigilanciaepidemiologica"));
        assertFalse(RegrasCoordenacaoSetor.ligados(s2).contains("nir"));
        assertEquals(Boolean.TRUE, RegrasCoordenacaoSetor.valor(s2, "vigilanciaepidemiologica"));
        assertNull(RegrasCoordenacaoSetor.valor(s2, "matricula"));
    }
}

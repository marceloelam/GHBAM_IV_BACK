package br.com.iv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class IdentificacaoFacialServiceTest {

    @Test
    void subjectIgnoraPrefixoDataUriEEspacos() {
        String semPrefixo = IdentificacaoFacialService.nomeSubject("12345", "QUJDRA==");
        String comPrefixo = IdentificacaoFacialService.nomeSubject(" 12345 ", "data:image/jpeg;base64, QUJD\nRA==");
        assertEquals(semPrefixo, comPrefixo);
    }

    @Test
    void fotoTrocadaGeraOutroSubject() {
        assertNotEquals(
            IdentificacaoFacialService.nomeSubject("12345", "QUJDRA=="),
            IdentificacaoFacialService.nomeSubject("12345", "RUZHSA=="));
    }

    @Test
    void matriculaVoltaDoSubjectMesmoComSublinhado() {
        String subject = IdentificacaoFacialService.nomeSubject("SEMSA_99", "QUJDRA==");
        assertEquals("SEMSA_99", IdentificacaoFacialService.matriculaDoSubject(subject));
        assertNull(IdentificacaoFacialService.matriculaDoSubject("semsublinhado"));
    }
}

package br.com.iv.odontologia;

import java.util.List;

public record OdontologiaAtendimentoView(
        OdontologiaAtendimento atendimento,
        List<OdontogramaRegistro> registros) {
}

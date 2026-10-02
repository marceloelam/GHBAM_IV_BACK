package br.com.iv.resumoclinico.service;

import java.util.List;
import br.com.iv.resumoclinico.dto.*;

public record ContextoClinico(
    int pacienteId, String pacienteNome, String prontuario,
    int bam, String unidade, String dataAtendimento, String horaAtendimento,
    TipoResumoClinico tipo, PeriodoResumoClinico periodo,
    String dados, List<String> alertas, List<String> pendencias, List<ResumoFonteDto> fontes
) {}

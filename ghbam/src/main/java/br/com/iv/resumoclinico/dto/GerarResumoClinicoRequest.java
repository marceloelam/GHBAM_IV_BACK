package br.com.iv.resumoclinico.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public class GerarResumoClinicoRequest {
    @NotNull @Positive public Integer pacienteId;
    @NotNull @Positive public Integer bam;
    @NotNull public TipoResumoClinico tipoResumo;
    @NotNull public PeriodoResumoClinico periodo;
}

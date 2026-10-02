package br.com.iv.resumoclinico.dto;
import java.time.LocalDateTime;
import java.util.List;
public record ResumoClinicoResponse(int pacienteId, int bam, TipoResumoClinico tipoResumo, PeriodoResumoClinico periodo, String resumo, List<String> alertas, List<String> pendencias, List<ResumoFonteDto> fontes, LocalDateTime geradoEm, String aviso, String modo) {}

package br.com.iv.resumoclinico.dto;
import java.time.LocalDateTime;
public record HistoricoResumoDto(long id, int pacienteId, int bam, String usuario, TipoResumoClinico tipoResumo, PeriodoResumoClinico periodo, LocalDateTime geradoEm, String modo, String status) {}

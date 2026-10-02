package br.com.iv.dto;

import java.time.LocalDate;

public record AihLaudoElegivelDto(
        int idlaudo,
        int idinternacao,
        int bam,
        String paciente,
        String prontuario,
        String unidade,
        String cid,
        String procedimento,
        String numeroAutorizacao,
        LocalDate dataInternacao,
        LocalDate dataAutorizacao,
        boolean preAihCriada) {
}

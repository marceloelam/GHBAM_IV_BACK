package br.com.iv.dto;

import java.time.LocalDate;
import lombok.Data;

/** Item do plano de acao ou comentario na linha do tempo do caso. */
@Data
public class NspAcaoDto {
    private String tipo;
    private String descricao;
    private String responsavel;
    private LocalDate prazo;
}

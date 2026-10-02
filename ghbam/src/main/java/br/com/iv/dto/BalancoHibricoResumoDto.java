package br.com.iv.dto;

import java.io.Serializable;
import br.com.iv.entity.ResumoBalancoHidrico.ClassificacaoBalanco;
import lombok.Data;

@Data
public class BalancoHibricoResumoDto implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private String uuid;
    private String bam;
    private String dataBalanco;
    private Integer totalEntradasMl;
    private Integer totalSaidasMl;
    private Integer saldoHidricoMl;
    private Integer diureseTotalMl;
    private Double diureseMlKgH;
    private ClassificacaoBalanco classificacaoBalanco;
    private String alertasClinicos;
    private String observacoes;
    private String dataCriacao;
    private String dataAtualizacao;
   // private String servidor;
	   
}

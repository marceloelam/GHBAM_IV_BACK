package br.com.iv.dto;

import lombok.Data;
import br.com.iv.entity.Avaliacao;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.Registro;
import br.com.iv.entity.Servidor;
import br.com.iv.entity.Tratamento;

@Data
public class BamPacientesRegistroAvaliacaoTratamentoServidorDTO {
	private Bam bam;
	private Pacientes paciente;
	private Registro registro;
	private Avaliacao avaliacao;
	private Tratamento tratamento;
	private Servidor servidor;
	
	public BamPacientesRegistroAvaliacaoTratamentoServidorDTO(Bam bam, Pacientes paciente, Registro registro,
			Avaliacao avaliacao, Tratamento tratamento, Servidor servidor) {
		super();
		this.bam = bam;
		this.paciente = paciente;
		this.registro = registro;
		this.avaliacao = avaliacao;
		this.tratamento = tratamento;
		this.servidor = servidor;
	}




}

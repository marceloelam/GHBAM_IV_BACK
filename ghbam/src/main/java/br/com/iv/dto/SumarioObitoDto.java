package br.com.iv.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class SumarioObitoDto {
    private Integer id, idResumoAlta;
    private String bam, nomePaciente, prontuario, dataInternacao, dataNascimento, idade, sexo, horaObito, numeroDO, setor, permanenciaHospitalar, periodoMulher, causaExterna, estatura, corIdadePresumida, sinaisAparentes, vestuario, causaTerminal, tempoTerminal, causaIntermediaria1, tempoIntermediaria1, causaIntermediaria2, tempoIntermediaria2, causaBasica, tempoBasica, condicoesContribuintes, agravoNotificacao, agravoEspecificar, sumarioAdmissao, medicoAssistente, crmAssistente, dataAssinaturaAssistente, revisor, crmRevisor, dataAssinaturaRevisor;
    private LocalDate dataObito;
}

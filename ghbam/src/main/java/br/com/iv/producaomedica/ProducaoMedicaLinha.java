package br.com.iv.producaomedica;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
public record ProducaoMedicaLinha(int bam, @JsonIgnore int pacienteId, LocalDate data, String hora, String matricula,
        String medico, String unidade, String setor, String desfecho, String paciente) {}

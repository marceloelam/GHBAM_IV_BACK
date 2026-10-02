package br.com.iv.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import br.com.iv.entity.Violencia;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViolenciaDTO {
    private Long id;

    @NotBlank(message = "Nome da vítima é obrigatório")
    private String nomeVitima;

    private Integer idade;
    private String telefone;
    private int bam;
    private String endereco;
    private String numero;
    private String bairro;
    private String municipio;
    private String estado;

    private String nomeAgressor;

    private String tipo;

    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    @NotNull(message = "Data da ocorrência é obrigatória")
    private LocalDate dataOcorrencia;

    @NotNull(message = "Hora da ocorrência é obrigatória")
    private LocalTime horaOcorrencia;

    private String localOcorrencia;
    private Boolean boletimRegistrado;
    private Boolean medidasProtetivas;

    private String filhosDependentes;
    private String bamNumero;

    private String nomeMedico;
    private String crmMedico;

    public Violencia toEntity() {
        Violencia v = new Violencia();
        v.setId(this.id);
        v.setNomeVitima(this.nomeVitima);
        v.setIdade(this.idade);
        v.setBam(this.bam);
        v.setTelefone(this.telefone);
        v.setEndereco(this.endereco);
        v.setNumero(this.numero);
        v.setBairro(this.bairro);
        v.setMunicipio(this.municipio);
        v.setEstado(this.estado);
        v.setNomeAgressor(this.nomeAgressor);
        v.setTipo(this.tipo);
        v.setDescricao(this.descricao);
        v.setDataOcorrencia(this.dataOcorrencia);
        v.setHoraOcorrencia(this.horaOcorrencia);
        v.setLocalOcorrencia(this.localOcorrencia);
        v.setBoletimRegistrado(this.boletimRegistrado);
        v.setMedidasProtetivas(this.medidasProtetivas);
        v.setFilhosDependentes(this.filhosDependentes);
        v.setBamNumero(this.bamNumero);
        v.setNomeMedico(this.nomeMedico);
        v.setCrmMedico(this.crmMedico);
        return v;
    }

    public static ViolenciaDTO fromEntity(Violencia v) {
        if (v == null) return null;
        ViolenciaDTO dto = new ViolenciaDTO();
        dto.setId(v.getId());
        dto.setNomeVitima(v.getNomeVitima());
        dto.setIdade(v.getIdade());
        dto.setBam(v.getBam());
        dto.setTelefone(v.getTelefone());
        dto.setEndereco(v.getEndereco());
        dto.setNumero(v.getNumero());
        dto.setBairro(v.getBairro());
        dto.setMunicipio(v.getMunicipio());
        dto.setEstado(v.getEstado());
        dto.setNomeAgressor(v.getNomeAgressor());
        dto.setTipo(v.getTipo());
        dto.setDescricao(v.getDescricao());
        dto.setDataOcorrencia(v.getDataOcorrencia());
        dto.setHoraOcorrencia(v.getHoraOcorrencia());
        dto.setLocalOcorrencia(v.getLocalOcorrencia());
        dto.setBoletimRegistrado(v.getBoletimRegistrado());
        dto.setMedidasProtetivas(v.getMedidasProtetivas());
        dto.setFilhosDependentes(v.getFilhosDependentes());
        dto.setBamNumero(v.getBamNumero());
        dto.setNomeMedico(v.getNomeMedico());
        dto.setCrmMedico(v.getCrmMedico());
        return dto;
    }
}

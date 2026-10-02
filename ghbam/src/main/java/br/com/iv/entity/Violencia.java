package br.com.iv.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "violencia")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Violencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nomeVitima;

    private String tipo;
    private Integer idade;
    private String telefone;
    private int bam;
    private String endereco;
    private String numero;
    private String bairro;
    private String municipio;
    private String estado;

    private String nomeAgressor;

    // Caso queira manter uma lista de tipos, troque para List<TipoViolencia> e ajuste o relacionamento
    // private List<TipoViolencia> tiposViolencia = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String descricao;

    private LocalDate dataOcorrencia;
    private LocalTime horaOcorrencia;
    private String localOcorrencia;

    private Boolean boletimRegistrado;
    private Boolean medidasProtetivas;

    private String filhosDependentes;
    private String bamNumero;

    private String nomeMedico;
    private String crmMedico;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

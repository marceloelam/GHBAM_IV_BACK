package br.com.iv.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class EscalaDeEnfermagemDto implements Serializable {
	private static final long serialVersionUID = 1L;

	 // Meta (identificação)
    @Column(length = 20)
    private String bam;

    @Column(length = 200)
    private String paciente;

    @Column(length = 100)
    private String enfermaria;

    @Column(length = 20)
    private String leito;

    private LocalDate data;

    @Column(length = 90)
    private String servidor; // quem preencheu a escala
    // Fugulin - campos numéricos (ex: 0..6)
    private Integer higiene;
    private Integer alimentacao;
    private Integer eliminacao;
    private Integer ventilacao;
    private Integer mobilidade;
    private Integer atividade;
    private Integer estadoEmocional;
    private Integer comunicacao;
    private Integer cuidados;

    // Morse - risco de quedas (Escala de Morse padrão)
    private Boolean historiaQuedas; // Histórico de quedas (0 ou 25 pontos)
    private Boolean diagnosticoSecundario; // Diagnóstico secundário (0 ou 15 pontos)
    private Integer auxilioDeambulacao; // Auxílio na deambulação (0, 15 ou 30 pontos)
    private Boolean dispositivoIv; // Uso de dispositivo intravenoso/heparina (0 ou 20 pontos)
    private Integer marchaTransferencia; // Marcha/transferência (0, 10 ou 20 pontos)
    private Boolean estadoMental; // Estado mental (0 ou 15 pontos)

    // Braden - risco de lesões por pressão
    private Integer sensorial;
    private Integer umidade;
    private Integer atividadeBraden;
    private Integer mobilidadeBraden;
    private Integer nutricional;
    private Integer friccao;

    // Totais / resultados (opcionais)
    private Integer fugulinTotal;
    private String fugulinGrau;

    private Integer morseTotal;
    private String morseRisco;

    private Integer bradenTotal;
    private String bradenRisco;

    // Timestamps
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

package br.com.iv.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PharmaciaOperacaoRequest {
    public String chaveOperacao;
    public Long loteId;
    public String numeroLoteDestino;
    public String tipo;
    public Integer quantidade;
    public Integer estoqueMinimo;
    public String localizacao;
    public String justificativa;
    public String documento;
    public String documentoNome;
    public String documentoBase64;
    public BigDecimal temperatura;
    public BigDecimal limiteMinimo;
    public BigDecimal limiteMaximo;
    public LocalDateTime validadeUso;
    public Integer diasCobertura;
}

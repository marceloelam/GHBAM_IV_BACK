package br.com.iv.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class ZamedEnvioRespostaDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean enviado;
    private int statusCode;
    private String endpoint;
    private String mensagem;
    private String respostaZamed;
}

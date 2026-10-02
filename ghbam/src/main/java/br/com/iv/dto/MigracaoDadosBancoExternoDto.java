package br.com.iv.dto;

import java.util.List;

public class MigracaoDadosBancoExternoDto {
    public String sistemaOrigem;
    public String motor;
    public String host;
    public Integer porta;
    public String banco;
    public String schema;
    public String usuario;
    public String secretRef;
    public Boolean somenteLeitura;
    public Integer limiteAmostra;
    public List<String> tabelasPrioritarias;
}

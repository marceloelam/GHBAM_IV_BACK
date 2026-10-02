package br.com.iv.dto;

import java.util.List;
import java.util.Map;

public class MigracaoDadosExportacaoDto {
    public String sistemaDestino;
    public String formato;
    public String dataInicio;
    public String dataFim;
    public String unidade;
    public Boolean anonimizar;
    public Boolean incluirDadosClinicos;
    public List<String> dominios;
    public Map<String, String> mapeamentoDestino;
}

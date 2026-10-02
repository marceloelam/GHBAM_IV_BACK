package br.com.iv.dto;

import java.util.List;
import java.util.Map;

public class MigracaoDadosPlanoDto {
    public String arquivoOrigem;
    public String geradoEm;
    public String estrategia;
    public Map<String, Object> amostra;
    public Map<String, String> mapeamento;
    public List<String> colunasOrigem;
    public List<Map<String, Object>> camposCanonicos;
    public List<Map<String, Object>> validacoes;
    public List<Map<String, Object>> previewStaging;
}

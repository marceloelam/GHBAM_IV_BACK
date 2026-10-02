package br.com.iv.dto;

import java.time.LocalDate;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Corpo opcional do PUT /pharmacia/permutas/{id}/finalizar — permite que a
// unidade solicitante decida, item a item, como catalogar o que recebeu:
// mesclar no lote de origem (padrao) ou cadastrar como um lote novo, proprio
// da unidade.
@JsonIgnoreProperties(ignoreUnknown = true)
public class PharmaciaPermutaFinalizarRequest {

    private Map<String, ItemDecisao> decisoes;

    public Map<String, ItemDecisao> getDecisoes() { return decisoes; }
    public void setDecisoes(Map<String, ItemDecisao> decisoes) { this.decisoes = decisoes; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemDecisao {
        // "MESCLAR" (padrao, se omitido) ou "NOVO"
        private String modo;
        private Long produtoId;
        private String numeroLote;
        private LocalDate dataFabricacao;
        private LocalDate dataValidade;
        // Conferencia fisica no recebimento — se omitido, assume igual ao
        // enviado. Se divergir, o backend exige notaDivergencia (quebra,
        // erro de contagem etc.) antes de aceitar.
        private Integer quantidadeRecebida;
        private String notaDivergencia;

        public String getModo() { return modo; }
        public void setModo(String modo) { this.modo = modo; }

        public Integer getQuantidadeRecebida() { return quantidadeRecebida; }
        public void setQuantidadeRecebida(Integer quantidadeRecebida) { this.quantidadeRecebida = quantidadeRecebida; }

        public String getNotaDivergencia() { return notaDivergencia; }
        public void setNotaDivergencia(String notaDivergencia) { this.notaDivergencia = notaDivergencia; }

        public Long getProdutoId() { return produtoId; }
        public void setProdutoId(Long produtoId) { this.produtoId = produtoId; }

        public String getNumeroLote() { return numeroLote; }
        public void setNumeroLote(String numeroLote) { this.numeroLote = numeroLote; }

        public LocalDate getDataFabricacao() { return dataFabricacao; }
        public void setDataFabricacao(LocalDate dataFabricacao) { this.dataFabricacao = dataFabricacao; }

        public LocalDate getDataValidade() { return dataValidade; }
        public void setDataValidade(LocalDate dataValidade) { this.dataValidade = dataValidade; }
    }
}

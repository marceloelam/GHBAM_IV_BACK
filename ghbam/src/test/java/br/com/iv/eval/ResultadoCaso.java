package br.com.iv.eval;

/**
 * Resultado de rodar um CasoTeste contra um prompt/modelo, incluindo a nota do
 * juiz (LLM) e metadados de custo (tempo/tokens). Persistido em eval-results/.
 */
public class ResultadoCaso {

    private String id;
    private String categoria;
    private Double nota;
    private String justificativaJuiz;
    private long tempoMs;
    private Integer tokensPrompt;
    private Integer tokensCompletion;
    private Integer tokensTotal;
    private String respostaBruta;
    private String erro;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public Double getNota() {
        return nota;
    }

    public void setNota(Double nota) {
        this.nota = nota;
    }

    public String getJustificativaJuiz() {
        return justificativaJuiz;
    }

    public void setJustificativaJuiz(String justificativaJuiz) {
        this.justificativaJuiz = justificativaJuiz;
    }

    public long getTempoMs() {
        return tempoMs;
    }

    public void setTempoMs(long tempoMs) {
        this.tempoMs = tempoMs;
    }

    public Integer getTokensPrompt() {
        return tokensPrompt;
    }

    public void setTokensPrompt(Integer tokensPrompt) {
        this.tokensPrompt = tokensPrompt;
    }

    public Integer getTokensCompletion() {
        return tokensCompletion;
    }

    public void setTokensCompletion(Integer tokensCompletion) {
        this.tokensCompletion = tokensCompletion;
    }

    public Integer getTokensTotal() {
        return tokensTotal;
    }

    public void setTokensTotal(Integer tokensTotal) {
        this.tokensTotal = tokensTotal;
    }

    public String getRespostaBruta() {
        return respostaBruta;
    }

    public void setRespostaBruta(String respostaBruta) {
        this.respostaBruta = respostaBruta;
    }

    public String getErro() {
        return erro;
    }

    public void setErro(String erro) {
        this.erro = erro;
    }
}

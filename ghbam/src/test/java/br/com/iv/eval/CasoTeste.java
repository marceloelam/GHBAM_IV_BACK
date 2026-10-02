package br.com.iv.eval;

/**
 * Um caso de teste do harness de avaliacao de prompts. Carregado a partir de
 * src/test/resources/eval/casos-teste.json.
 */
public class CasoTeste {

    private String id;
    private String categoria;
    private String systemPromptRef;
    private String contexto;
    private String pergunta;
    private String criterioEsperado;

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

    public String getSystemPromptRef() {
        return systemPromptRef;
    }

    public void setSystemPromptRef(String systemPromptRef) {
        this.systemPromptRef = systemPromptRef;
    }

    public String getContexto() {
        return contexto;
    }

    public void setContexto(String contexto) {
        this.contexto = contexto;
    }

    public String getPergunta() {
        return pergunta;
    }

    public void setPergunta(String pergunta) {
        this.pergunta = pergunta;
    }

    public String getCriterioEsperado() {
        return criterioEsperado;
    }

    public void setCriterioEsperado(String criterioEsperado) {
        this.criterioEsperado = criterioEsperado;
    }
}

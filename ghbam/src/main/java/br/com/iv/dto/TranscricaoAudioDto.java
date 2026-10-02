package br.com.iv.dto;

public class TranscricaoAudioDto {
    private String texto;
    private String transcricao;

    public TranscricaoAudioDto() {
    }

    public TranscricaoAudioDto(String texto) {
        this.texto = texto;
    }

    public TranscricaoAudioDto(String texto, String transcricao) {
        this.texto = texto;
        this.transcricao = transcricao;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public String getTranscricao() {
        return transcricao;
    }

    public void setTranscricao(String transcricao) {
        this.transcricao = transcricao;
    }
}

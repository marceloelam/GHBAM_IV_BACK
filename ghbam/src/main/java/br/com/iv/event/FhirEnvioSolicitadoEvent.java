package br.com.iv.event;

public class FhirEnvioSolicitadoEvent {

    private final Long idFila;

    public FhirEnvioSolicitadoEvent(Long idFila) {
        this.idFila = idFila;
    }

    public Long getIdFila() {
        return idFila;
    }
}

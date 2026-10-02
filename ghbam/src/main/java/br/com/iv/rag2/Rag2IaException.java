package br.com.iv.rag2;

public class Rag2IaException extends RuntimeException {
    public Rag2IaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

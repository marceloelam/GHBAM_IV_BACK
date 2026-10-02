package br.com.iv.util;

public class MensagemDeErro extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public MensagemDeErro(String message) {
		super(message);
	}
}

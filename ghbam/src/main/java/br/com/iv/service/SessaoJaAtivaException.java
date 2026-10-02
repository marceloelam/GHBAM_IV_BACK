package br.com.iv.service;

import br.com.iv.entity.SessaoAtiva;

public class SessaoJaAtivaException extends RuntimeException {
    private final SessaoAtiva sessao;

    public SessaoJaAtivaException(SessaoAtiva sessao) {
        super("Usuário já possui sessão ativa");
        this.sessao = sessao;
    }

    public SessaoAtiva getSessao() {
        return sessao;
    }
}

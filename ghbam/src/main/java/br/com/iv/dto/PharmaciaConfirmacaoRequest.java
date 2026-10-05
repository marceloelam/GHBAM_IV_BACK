package br.com.iv.dto;

import java.util.List;
import br.com.iv.entity.PharmaciaDispensacao;
import br.com.iv.pharmacia.validacao.dto.ValidacaoDispensacaoRequest;

public class PharmaciaConfirmacaoRequest {
    public List<PharmaciaDispensacao> itens;
    public ValidacaoDispensacaoRequest contexto;
    public String justificativa;
    public String duplaChecagemMatricula;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    public String duplaChecagemSenha;
    public String chaveConfirmacao;
}

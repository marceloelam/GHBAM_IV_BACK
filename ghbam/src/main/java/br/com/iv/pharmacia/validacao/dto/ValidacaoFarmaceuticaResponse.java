package br.com.iv.pharmacia.validacao.dto;

import java.util.ArrayList;
import java.util.List;

public class ValidacaoFarmaceuticaResponse {

    /** true se ha ao menos um alerta CRITICAL. */
    private boolean possuiCritico;
    /** true se a confirmacao da dispensacao exige justificativa escrita. */
    private boolean exigeJustificativa;
    /** true se algum item e medicamento potencialmente perigoso (ISMP) - dupla checagem. */
    private boolean exigeDuplaChecagem;
    /** Quantos itens da dispensacao o sistema reconheceu na base de conhecimento. */
    private int itensReconhecidos;
    private int itensTotais;
    /** Versao da base de conhecimento usada - vai para o registro de auditoria. */
    private String versaoBase;
    private List<AlertaFarmaceuticoDto> alertas = new ArrayList<>();

    public boolean isPossuiCritico() { return possuiCritico; }
    public void setPossuiCritico(boolean possuiCritico) { this.possuiCritico = possuiCritico; }
    public boolean isExigeJustificativa() { return exigeJustificativa; }
    public void setExigeJustificativa(boolean exigeJustificativa) { this.exigeJustificativa = exigeJustificativa; }
    public boolean isExigeDuplaChecagem() { return exigeDuplaChecagem; }
    public void setExigeDuplaChecagem(boolean exigeDuplaChecagem) { this.exigeDuplaChecagem = exigeDuplaChecagem; }
    public int getItensReconhecidos() { return itensReconhecidos; }
    public void setItensReconhecidos(int itensReconhecidos) { this.itensReconhecidos = itensReconhecidos; }
    public int getItensTotais() { return itensTotais; }
    public void setItensTotais(int itensTotais) { this.itensTotais = itensTotais; }
    public String getVersaoBase() { return versaoBase; }
    public void setVersaoBase(String versaoBase) { this.versaoBase = versaoBase; }
    public List<AlertaFarmaceuticoDto> getAlertas() { return alertas; }
    public void setAlertas(List<AlertaFarmaceuticoDto> alertas) { this.alertas = alertas == null ? new ArrayList<>() : alertas; }
}

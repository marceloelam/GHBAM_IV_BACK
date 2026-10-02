package br.com.iv.service;

import static br.com.iv.service.BpaLayoutUtils.alfa;
import static br.com.iv.service.BpaLayoutUtils.bytesLatin1;
import static br.com.iv.service.BpaLayoutUtils.data8;
import static br.com.iv.service.BpaLayoutUtils.numerico;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import br.com.iv.dto.ConteudoExportado;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.entity.FaturamentoApac;
import br.com.iv.enums.InstrumentoRegistro;

@ApplicationScoped
public class ApacExporter implements FaturamentoExporter {

    @Override
    public InstrumentoRegistro instrumento() {
        return InstrumentoRegistro.APAC;
    }

    @Override
    public boolean layoutDisponivel() {
        return true;
    }

    @Override
    public ConteudoExportado exportar(List<Afaturar> producoes, FaturamentoCompetencia competencia) {
        throw new UnsupportedOperationException("APAC usa registros da tabela faturamento_apac. Use exportarApac().");
    }

    public ConteudoExportado exportarApac(List<FaturamentoApac> registros, FaturamentoCompetencia competencia) {
        if (registros == null || registros.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma APAC encerrada e nao exportada para gerar arquivo.");
        }
        String competenciaAaaamm = numerico(competencia.getCompetencia(), 6);
        StringBuilder conteudo = new StringBuilder();
        for (FaturamentoApac apac : registros) {
            validar(apac);
            conteudo.append(linhaCorpoPrincipal(apac, competenciaAaaamm));
        }
        return new ConteudoExportado(bytesLatin1(conteudo.toString()), registros.size(), "APAC-DATASUS-2026-CORPO-14");
    }

    private String linhaCorpoPrincipal(FaturamentoApac apac, String competenciaAaaamm) {
        char[] linha = alfa("", 375).toCharArray();
        campo(linha, 1, numerico("14", 2));
        campo(linha, 3, numerico(competenciaAaaamm, 6));
        campo(linha, 9, numerico(numeroApac(apac), 13));
        campo(linha, 22, uf(apac.getUf()));
        campo(linha, 24, numerico(apac.getCnes(), 7));
        campo(linha, 31, numerico(apac.getCnspaciente(), 15));
        campo(linha, 46, alfa(apac.getNomepaciente(), 30));
        campo(linha, 76, alfa(apac.getNomemae(), 30));
        campo(linha, 106, alfa(apac.getEndereco(), 30));
        campo(linha, 136, alfa("", 5));
        campo(linha, 141, alfa(apac.getComplemento(), 10));
        campo(linha, 151, alfa("", 12));
        campo(linha, 163, numerico(apac.getCep(), 8));
        campo(linha, 171, ibge7(apac.getCodigoibge()));
        campo(linha, 178, data8(apac.getDatanascimento()));
        campo(linha, 186, alfa(sexo(apac.getSexo()), 1));
        campo(linha, 187, alfa(responsavelClinico(apac), 30));
        campo(linha, 217, numerico(apac.getProcedimentoprincipal(), 10));
        campo(linha, 227, numerico(motivoSaida(apac), 2));
        campo(linha, 229, data8(""));
        campo(linha, 237, numerico(apac.getCnsprofissional(), 15));
        campo(linha, 252, alfa(apac.getRegistroprofissional(), 6));
        campo(linha, 258, data8(apac.getValidadeinicio()));
        campo(linha, 266, data8(apac.getValidadefim()));
        campo(linha, 274, alfa(cid4(apac.getCid()), 4));
        campo(linha, 278, alfa(cid4(apac.getCidsecundario()), 4));
        campo(linha, 282, numerico(apac.getQuantidade(), 6));
        campo(linha, 288, numerico(apac.getCnesexecutante(), 7));
        campo(linha, 295, numerico(apac.getCnsprofissionalautorizador(), 15));
        campo(linha, 310, alfa(apac.getCodorgaoemissor(), 10));
        campo(linha, 320, alfa("", 39));
        campo(linha, 359, numerico("01", 2));
        campo(linha, 361, numerico("", 13));
        campo(linha, 374, numerico(racaCor(apac.getRacacor()), 2));
        return new String(linha) + BpaLayoutUtils.QUEBRA_LINHA;
    }

    private void validar(FaturamentoApac apac) {
        if (numeroApac(apac).length() < 13) {
            throw new IllegalArgumentException("APAC " + apac.getNumero()
                    + " nao tem numero oficial de autorizacao APAC com 13 digitos.");
        }
        if (digitos(apac.getCnes()).length() < 7) {
            throw new IllegalArgumentException("APAC " + apac.getNumero() + " nao tem CNES valido.");
        }
        if (digitos(apac.getCnspaciente()).length() < 15) {
            throw new IllegalArgumentException("APAC " + apac.getNumero() + " nao tem CNS do paciente valido.");
        }
        if (digitos(apac.getProcedimentoprincipal()).length() < 10) {
            throw new IllegalArgumentException("APAC " + apac.getNumero() + " nao tem procedimento principal valido.");
        }
        if (cid4(apac.getCid()).isBlank()) {
            throw new IllegalArgumentException("APAC " + apac.getNumero() + " nao tem CID principal.");
        }
    }

    private void campo(char[] linha, int inicioUmBase, String valor) {
        int pos = Math.max(0, inicioUmBase - 1);
        for (int i = 0; i < valor.length() && pos + i < linha.length; i++) {
            linha[pos + i] = valor.charAt(i);
        }
    }

    private String numeroApac(FaturamentoApac apac) {
        return digitos(apac.getNumeroautorizacao());
    }

    private String responsavelClinico(FaturamentoApac apac) {
        String nome = texto(apac.getNomeprofissional());
        return nome.isBlank() ? apac.getProfissionalautorizador() : nome;
    }

    private String motivoSaida(FaturamentoApac apac) {
        String status = texto(apac.getStatus()).toUpperCase();
        if (status.contains("CANCEL")) return "21";
        return "51";
    }

    private String uf(String valor) {
        String texto = texto(valor).toUpperCase();
        if (texto.matches("\\d{2}")) return texto;
        return switch (texto) {
            case "RO" -> "11"; case "AC" -> "12"; case "AM" -> "13"; case "RR" -> "14";
            case "PA" -> "15"; case "AP" -> "16"; case "TO" -> "17"; case "MA" -> "21";
            case "PI" -> "22"; case "CE" -> "23"; case "RN" -> "24"; case "PB" -> "25";
            case "PE" -> "26"; case "AL" -> "27"; case "SE" -> "28"; case "BA" -> "29";
            case "MG" -> "31"; case "ES" -> "32"; case "RJ" -> "33"; case "SP" -> "35";
            case "PR" -> "41"; case "SC" -> "42"; case "RS" -> "43"; case "MS" -> "50";
            case "MT" -> "51"; case "GO" -> "52"; case "DF" -> "53";
            default -> "00";
        };
    }

    private String ibge7(String valor) {
        String digitos = digitos(valor);
        if (digitos.length() >= 7) return digitos.substring(0, 7);
        if (digitos.length() == 6) return digitos + " ";
        return alfa(digitos, 7);
    }

    private String sexo(String valor) {
        return texto(valor).toUpperCase().startsWith("F") ? "F" : "M";
    }

    private String racaCor(String valor) {
        String texto = texto(valor).toUpperCase();
        if (texto.matches("\\d{2}")) return texto;
        if (texto.contains("BRAN")) return "01";
        if (texto.contains("PRET")) return "02";
        if (texto.contains("PARD")) return "03";
        if (texto.contains("AMAR")) return "04";
        if (texto.contains("IND")) return "05";
        return "99";
    }

    private String cid4(String valor) {
        return texto(valor).replace(".", "").toUpperCase();
    }

    private String digitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}

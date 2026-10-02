package br.com.iv.service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.iv.entity.ConfiguracaoUnidade;

// Utilitarios do layout posicional oficial do BPA (DATASUS/SIA), conforme
// "Layout da interface texto do BPA" - circular tecnica FEHOSP 1071/2020,
// reproduzindo o instrutivo vigente do DATASUS (inclui prd_ine, exigido a
// partir da competencia 08/2015). Usado por BpaConsolidadoExporter e
// BpaIndividualizadoExporter.
final class BpaLayoutUtils {

    static final String QUEBRA_LINHA = "\r\n";
    private static final Pattern DATA_ISO = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern DATA_BR = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})");
    private static final Pattern DATA_COMPACTA = Pattern.compile("^\\d{8}$");
    // O cadastro de pacientes grava o nascimento como aaaa/MM/dd (com barras) e o
    // DateUtils.toYMD normaliza justamente para esse formato ao salvar a producao. Sem
    // este padrao a data caia no "00000000" do fim do metodo: todo BPA-I saia com
    // nascimento zerado, que o DATASUS critica.
    private static final Pattern DATA_ISO_BARRA = Pattern.compile("^([0-9]{4})/([0-9]{2})/([0-9]{2})");

    private BpaLayoutUtils() {
    }

    // Campos NUM: apenas digitos, zeros a esquerda; se exceder o tamanho,
    // mantem os digitos mais a direita (nunca inventa digito).
    static String numerico(Object valor, int tamanho) {
        String digitos = valor == null ? "" : valor.toString().replaceAll("\\D", "");
        if (digitos.length() > tamanho) {
            digitos = digitos.substring(digitos.length() - tamanho);
        }
        StringBuilder preenchido = new StringBuilder();
        for (int i = digitos.length(); i < tamanho; i++) {
            preenchido.append('0');
        }
        return preenchido.append(digitos).toString();
    }

    // Campos ALFA: maiusculas, espacos a direita ate completar; trunca se
    // exceder (o layout oficial nao admite campo maior que o tamanho).
    static String alfa(String valor, int tamanho) {
        String texto = valor == null ? "" : valor.trim().toUpperCase();
        if (texto.length() > tamanho) {
            texto = texto.substring(0, tamanho);
        }
        StringBuilder preenchido = new StringBuilder(texto);
        while (preenchido.length() < tamanho) {
            preenchido.append(' ');
        }
        return preenchido.toString();
    }

    // Datas do layout sao AAAAMMDD (NUM). Converte "AAAA-MM-DD..." ou
    // "DD/MM/AAAA"; se nao reconhecer o formato, preenche com zeros (nunca
    // inventa uma data) - registro fica sinalizado como pendente de correcao
    // na origem, e a critica correspondente ja e emitida pelo motor da Fase 1.

    // CID do BPA-I: em branco quando nao ha diagnostico. As telas assistenciais mandam
    // "0" quando o campo nao se aplica (ver GravarFaturamento.jsx, que passa cid = '0'
    // na triagem) e isso chegava ao arquivo como "0000" - um CID que nao existe, e o
    // DATASUS critica valor invalido, nao campo vazio. Sem diagnostico o certo e brancos.
    static String cid(String valor) {
        String texto = valor == null ? "" : valor.trim().toUpperCase();
        if (texto.isEmpty() || texto.chars().allMatch(c -> c == '0')) {
            return "";
        }
        return texto;
    }
    static String data8(String valor) {
        String texto = valor == null ? "" : valor.trim();
        if (DATA_COMPACTA.matcher(texto).matches()) {
            return texto;
        }
        Matcher isoBarra = DATA_ISO_BARRA.matcher(texto);
        if (isoBarra.find()) {
            return isoBarra.group(1) + isoBarra.group(2) + isoBarra.group(3);
        }
        Matcher iso = DATA_ISO.matcher(texto);
        if (iso.find()) {
            return iso.group(1) + iso.group(2) + iso.group(3);
        }
        Matcher br = DATA_BR.matcher(texto);
        if (br.find()) {
            return br.group(3) + br.group(2) + br.group(1);
        }
        return "00000000";
    }

    // Campo de controle do cabecalho (cbc-smt-vrf), formula do instrutivo
    // oficial: soma do codigo de cada procedimento + quantidade em todas as
    // linhas, resto da divisao por 1111, some 1111 ao resto. Dominio [1111..2221].
    static long controle(long somaCodigosEQuantidades) {
        return (somaCodigosEQuantidades % 1111) + 1111;
    }

    static String cabecalho(ConfiguracaoUnidade configuracao, String competenciaAaaamm, int linhas, int folhas,
            long somaControle) {
        StringBuilder cabecalho = new StringBuilder();
        cabecalho.append(numerico("01", 2));
        cabecalho.append(alfa("#BPA#", 5));
        cabecalho.append(numerico(competenciaAaaamm, 6));
        cabecalho.append(numerico(linhas, 6));
        cabecalho.append(numerico(folhas, 6));
        cabecalho.append(numerico(controle(somaControle), 4));
        cabecalho.append(alfa(configuracao == null ? "" : configuracao.getHospitalBPA(), 30));
        cabecalho.append(alfa(configuracao == null ? "" : configuracao.getSigla(), 6));
        cabecalho.append(numerico(configuracao == null ? "" : configuracao.getCnpjDoHospital(), 14));
        cabecalho.append(alfa(configuracao == null ? "" : configuracao.getOrgaoDestino(), 40));
        cabecalho.append(alfa(destinoIndicador(configuracao), 1));
        cabecalho.append(alfa(configuracao == null ? "" : configuracao.getVersaoBPA(), 10));
        cabecalho.append(QUEBRA_LINHA);
        return cabecalho.toString();
    }

    // "E" (estadual) ou "M" (municipal), conforme ConfiguracaoUnidade.indicadorBPA.
    // Sem valor configurado, assume "M" (GHBAM e uma plataforma municipal) - o
    // operador pode e deve preencher o campo em Configuracoes se for diferente.
    private static String destinoIndicador(ConfiguracaoUnidade configuracao) {
        String valor = configuracao == null || configuracao.getIndicadorBPA() == null
                ? ""
                : configuracao.getIndicadorBPA().trim().toUpperCase();
        return valor.startsWith("E") ? "E" : "M";
    }

    static byte[] bytesLatin1(String conteudo) {
        return conteudo.getBytes(StandardCharsets.ISO_8859_1);
    }
}

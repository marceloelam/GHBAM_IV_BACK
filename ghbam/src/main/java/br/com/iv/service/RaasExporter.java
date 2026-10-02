package br.com.iv.service;

import static br.com.iv.service.BpaLayoutUtils.alfa;
import static br.com.iv.service.BpaLayoutUtils.bytesLatin1;
import static br.com.iv.service.BpaLayoutUtils.data8;
import static br.com.iv.service.BpaLayoutUtils.numerico;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import br.com.iv.dto.ConteudoExportado;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.entity.FaturamentoRaas;
import br.com.iv.enums.InstrumentoRegistro;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;

@ApplicationScoped
public class RaasExporter implements FaturamentoExporter {

    private static final TypeReference<List<Map<String, Object>>> LISTA_ACOES =
            new TypeReference<List<Map<String, Object>>>() {
            };

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public InstrumentoRegistro instrumento() {
        return InstrumentoRegistro.RAAS;
    }

    @Override
    public boolean layoutDisponivel() {
        return true;
    }

    @Override
    public ConteudoExportado exportar(List<Afaturar> producoes, FaturamentoCompetencia competencia) {
        throw new UnsupportedOperationException("RAAS usa registros da tabela faturamento_raas. Use exportarRaas().");
    }

    public ConteudoExportado exportarRaas(List<FaturamentoRaas> registros, FaturamentoCompetencia competencia) {
        if (registros == null || registros.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma RAAS concluida e nao exportada para gerar arquivo.");
        }

        ConfiguracaoUnidade configuracao = buscarConfiguracao(registros.get(0).getUnidade());
        String competenciaAaaamm = numerico(competencia.getCompetencia(), 6);
        StringBuilder corpo = new StringBuilder();
        long somaControle = 0;

        for (FaturamentoRaas raas : registros) {
            List<Map<String, Object>> acoes = acoes(raas);
            if (acoes.isEmpty()) {
                throw new IllegalArgumentException("RAAS " + raas.getNumero() + " nao possui acoes realizadas para exportar.");
            }
            corpo.append(linhaPaciente(raas, competenciaAaaamm));
            somaControle += numeroLong(raas.getCnes()) + numeroLong(raas.getCnspaciente());

            for (Map<String, Object> acao : acoes) {
                corpo.append(linhaAcao(raas, acao, competenciaAaaamm));
                somaControle += numeroLong(valor(acao, "codigo")) + numeroLong(valor(acao, "quantidade"));
            }
        }

        String cabecalho = cabecalhoRaas(configuracao, competenciaAaaamm, registros.size(), somaControle);
        return new ConteudoExportado(bytesLatin1(cabecalho + corpo), registros.size(), "RAAS-DATASUS-2025");
    }

    private String cabecalhoRaas(ConfiguracaoUnidade configuracao, String competenciaAaaamm, int fichas,
            long somaControle) {
        StringBuilder linha = new StringBuilder();
        linha.append(numerico("01", 2));
        linha.append(alfa("#RAS#", 5));
        linha.append(numerico(competenciaAaaamm, 6));
        linha.append(numerico(fichas, 6));
        linha.append(numerico(BpaLayoutUtils.controle(somaControle), 4));
        linha.append(alfa(configuracao == null ? "" : configuracao.getHospitalBPA(), 30));
        linha.append(alfa(configuracao == null ? "" : configuracao.getSigla(), 6));
        linha.append(numerico(configuracao == null ? "" : configuracao.getCnpjDoHospital(), 14));
        linha.append(alfa(configuracao == null ? "" : configuracao.getOrgaoDestino(), 40));
        linha.append(alfa(destinoIndicador(configuracao), 1));
        linha.append(numerico(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), 8));
        linha.append(alfa(configuracao == null ? "" : configuracao.getVersaoBPA(), 5));
        linha.append(numerico("", 10));
        linha.append(alfa("", 7));
        linha.append(alfa("", 15));
        linha.append(BpaLayoutUtils.QUEBRA_LINHA);
        return linha.toString();
    }

    private String linhaPaciente(FaturamentoRaas raas, String competenciaAaaamm) {
        StringBuilder linha = new StringBuilder();
        linha.append(numerico("15", 2));
        linha.append(uf(raas.getUf()));
        linha.append(numerico(competenciaAaaamm, 6));
        linha.append(numerico(raas.getCnes(), 7));
        linha.append(numerico(raas.getCnspaciente(), 15));
        linha.append(data8(raas.getDataadmissao()));
        linha.append(data8(dataValidadeFinal(raas)));
        linha.append(alfa(raas.getNomepaciente(), 30));
        linha.append(alfa(raas.getProntuario(), 10));
        linha.append(alfa(raas.getNomemae(), 30));
        linha.append(alfa(raas.getEndereco(), 30));
        linha.append(alfa("", 5));
        linha.append(alfa(raas.getComplemento(), 10));
        linha.append(numerico(raas.getCep(), 8));
        linha.append(ibge7(raas.getCodigoibge()));
        linha.append(data8(raas.getDatanascimento()));
        linha.append(alfa(sexo(raas.getSexo()), 1));
        linha.append(numerico(racaCor(raas.getRacacor()), 2));
        linha.append(alfa(raas.getNomeresponsavel(), 30));
        linha.append(numerico(nacionalidade(raas.getNacionalidade()), 3));
        linha.append(numerico(raas.getEtniaindigena(), 4));
        linha.append(numerico(raas.getTelefonecontato(), 11));
        linha.append(numerico(raas.getTelefonecelular(), 11));
        linha.append(numerico(motivoSaida(raas.getConclusao()), 2));
        linha.append(data8(""));
        linha.append(data8(""));
        linha.append(alfa(cid4(raas.getCid()), 4));
        linha.append(alfa(cid4(raas.getCidcausasassociadas()), 4));
        linha.append(alfa(raas.getDiagnosticoprincipal(), 30));
        linha.append(alfa(raas.getDiagnosticocausasassociadas(), 30));
        linha.append(numerico(origemPaciente(raas.getOrigempaciente()), 2));
        linha.append(alfa(Boolean.TRUE.equals(raas.getCoberturaesf()) ? "S" : "N", 1));
        linha.append(numerico(raas.getCnesesf(), 7));
        linha.append(numerico(quantidadeAcoes(raas), 5));
        linha.append(numerico(encaminhamento(raas.getEncaminhamento()), 2));
        linha.append(alfa("RAS", 3));
        linha.append(alfa(Boolean.TRUE.equals(raas.getUsuarioalcooldrogas()) ? "S" : "N", 1));
        linha.append(alfa(Boolean.TRUE.equals(raas.getUsuarioalcooldrogas()) ? "S" : "N", 1));
        linha.append(alfa(tiposDroga(raas), 3));
        linha.append(numerico(raas.getNumeroautorizacao(), 13));
        linha.append(alfa("", 30));
        linha.append(numerico("", 3));
        linha.append(alfa("", 40));
        linha.append(numerico("", 11));
        linha.append(alfa("", 4));
        linha.append(BpaLayoutUtils.QUEBRA_LINHA);
        return linha.toString();
    }

    private String linhaAcao(FaturamentoRaas raas, Map<String, Object> acao, String competenciaAaaamm) {
        StringBuilder linha = new StringBuilder();
        linha.append(numerico("16", 2));
        linha.append(uf(raas.getUf()));
        linha.append(numerico(competenciaAaaamm, 6));
        linha.append(numerico(raas.getCnes(), 7));
        linha.append(numerico(raas.getCnspaciente(), 15));
        linha.append(data8(raas.getDataadmissao()));
        linha.append(numerico(valor(acao, "codigo"), 10));
        linha.append(alfa(valorOu(acao, "cbo", raas.getRegistroprofissional()), 6));
        linha.append(numerico(valorOu(acao, "cnsprofissional", raas.getCnsprofissional()), 15));
        linha.append(data8(valor(acao, "data")));
        linha.append(numerico(servico(acao), 3));
        linha.append(numerico(classificacaoServico(acao), 3));
        linha.append(numerico(valor(acao, "quantidade"), 6));
        linha.append(alfa("RAS", 3));
        linha.append(alfa(localAcao(valor(acao, "classificacao")), 1));
        linha.append(numerico(raas.getCpfprofissional(), 11));
        linha.append(alfa("", 4));
        linha.append(BpaLayoutUtils.QUEBRA_LINHA);
        return linha.toString();
    }

    private List<Map<String, Object>> acoes(FaturamentoRaas raas) {
        String json = raas.getAcoesrealizadas();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LISTA_ACOES);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private int quantidadeAcoes(FaturamentoRaas raas) {
        return acoes(raas).stream().mapToInt(a -> (int) Math.max(1, numeroLong(valor(a, "quantidade")))).sum();
    }

    private ConfiguracaoUnidade buscarConfiguracao(String unidade) {
        if (unidade == null || unidade.isBlank()) {
            return null;
        }
        return configuracaoUnidadeRepository.filtarPorUnidadeSigla(unidade).stream()
                .filter(c -> unidade.equalsIgnoreCase(texto(c.getSigla())))
                .findFirst()
                .orElse(null);
    }

    private String valor(Map<String, Object> acao, String chave) {
        Object valor = acao == null ? null : acao.get(chave);
        return valor == null ? "" : String.valueOf(valor);
    }

    private String valorOu(Map<String, Object> acao, String chave, String fallback) {
        String valor = valor(acao, chave);
        return valor.isBlank() ? fallback : valor;
    }

    private long numeroLong(String valor) {
        String digitos = valor == null ? "" : valor.replaceAll("\\D", "");
        if (digitos.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(digitos);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String uf(String valor) {
        String texto = texto(valor).toUpperCase();
        if (texto.matches("\\d{2}")) {
            return texto;
        }
        return switch (texto) {
            case "RO" -> "11";
            case "AC" -> "12";
            case "AM" -> "13";
            case "RR" -> "14";
            case "PA" -> "15";
            case "AP" -> "16";
            case "TO" -> "17";
            case "MA" -> "21";
            case "PI" -> "22";
            case "CE" -> "23";
            case "RN" -> "24";
            case "PB" -> "25";
            case "PE" -> "26";
            case "AL" -> "27";
            case "SE" -> "28";
            case "BA" -> "29";
            case "MG" -> "31";
            case "ES" -> "32";
            case "RJ" -> "33";
            case "SP" -> "35";
            case "PR" -> "41";
            case "SC" -> "42";
            case "RS" -> "43";
            case "MS" -> "50";
            case "MT" -> "51";
            case "GO" -> "52";
            case "DF" -> "53";
            default -> "00";
        };
    }

    private String ibge7(String valor) {
        String digitos = valor == null ? "" : valor.replaceAll("\\D", "");
        if (digitos.length() >= 7) {
            return digitos.substring(0, 7);
        }
        if (digitos.length() == 6) {
            return digitos + " ";
        }
        return alfa(digitos, 7);
    }

    private String sexo(String valor) {
        String texto = texto(valor).toUpperCase();
        return texto.startsWith("F") ? "F" : "M";
    }

    private String racaCor(String valor) {
        String texto = texto(valor).toUpperCase();
        if (texto.matches("\\d{2}")) {
            return texto;
        }
        if (texto.contains("BRAN")) return "01";
        if (texto.contains("PRET")) return "02";
        if (texto.contains("PARD")) return "03";
        if (texto.contains("AMAR")) return "04";
        if (texto.contains("IND")) return "05";
        return "99";
    }

    private String nacionalidade(String valor) {
        String digitos = texto(valor).replaceAll("\\D", "");
        return digitos.isBlank() ? "010" : digitos;
    }

    private String cid4(String valor) {
        return texto(valor).replace(".", "").toUpperCase();
    }

    private String dataValidadeFinal(FaturamentoRaas raas) {
        String competencia = texto(raas.getMesatendimento()).replaceAll("\\D", "");
        if (competencia.length() == 6) {
            return competencia + "31";
        }
        return "";
    }

    private String motivoSaida(String valor) {
        String texto = texto(valor).toUpperCase();
        if (texto.contains("OBITO")) return "06";
        if (texto.contains("INTERN")) return "05";
        if (texto.contains("ENCAM")) return "02";
        if (texto.contains("ALTA")) return "01";
        return "00";
    }

    private String origemPaciente(String valor) {
        String digitos = texto(valor).replaceAll("\\D", "");
        return digitos.isBlank() ? "01" : digitos;
    }

    private String encaminhamento(String valor) {
        String digitos = texto(valor).replaceAll("\\D", "");
        return digitos.isBlank() ? "00" : digitos;
    }

    private String tiposDroga(FaturamentoRaas raas) {
        return (Boolean.TRUE.equals(raas.getDrogaalcool()) ? "1" : "0")
                + (Boolean.TRUE.equals(raas.getDrogacrack()) ? "1" : "0")
                + (Boolean.TRUE.equals(raas.getDrogaoutras()) ? "1" : "0");
    }

    private String localAcao(String valor) {
        String texto = texto(valor).toUpperCase();
        return texto.startsWith("T") ? "T" : "C";
    }

    private String servico(Map<String, Object> acao) {
        String valor = valor(acao, "servico");
        if (valor.replaceAll("\\D", "").length() == 3) {
            return valor;
        }
        return servicoCapsLegado(valor).isBlank() ? valor : "115";
    }

    private String classificacaoServico(Map<String, Object> acao) {
        String valor = valor(acao, "classificacaoServico");
        if (valor.replaceAll("\\D", "").length() == 3) {
            return valor;
        }
        String legado = servicoCapsLegado(valor(acao, "servico"));
        return legado.isBlank() ? valor : legado;
    }

    private String servicoCapsLegado(String valor) {
        return switch (texto(valor).toUpperCase()) {
            case "CAPS_I" -> "001";
            case "CAPS_II" -> "002";
            case "CAPS_III" -> "003";
            case "CAPS_IJ" -> "004";
            case "CAPS_AD" -> "005";
            case "CAPS_AD_III" -> "006";
            default -> "";
        };
    }

    private String destinoIndicador(ConfiguracaoUnidade configuracao) {
        String valor = configuracao == null ? "" : texto(configuracao.getIndicadorBPA()).toUpperCase();
        return valor.startsWith("E") ? "E" : "M";
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}

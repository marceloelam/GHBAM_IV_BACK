package br.com.iv.service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import br.com.iv.dto.ConteudoExportado;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.entity.FaturamentoAih;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.enums.AihStatus;
import br.com.iv.enums.InstrumentoRegistro;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;

/**
 * Arquivo da AIH no layout da interface texto do SISAIH01.
 *
 * Fonte: "Layout da interface texto do SISAIH01" (DATASUS/SIHD). Registro de 1.600 bytes,
 * posicao fixa, um por conta. O tipo esta no campo 9 (IDENT_AIH, posicoes 57-58); aqui so
 * o 01 - AIH Principal, que e o que a conta hospitalar do GHBAM representa.
 *
 * Este arquivo NAO e a remessa de transmissao: e o que o SISAIH01 importa. A numeracao, a
 * critica e o envio continuam sendo dele. O que deixa de existir e a digitacao.
 *
 * Conta sem NU_AIH nao entra: o numero vem da faixa concedida pelo gestor
 * (FaturamentoAihFaixaService) e o SISAIH01 recusa o lote se vier vazio ou fora da faixa.
 * Sem faixa cadastrada o exportador recusa dizendo a razao, em vez de gerar um arquivo que
 * seria rejeitado la na frente.
 */
@ApplicationScoped
public class AihExporter implements FaturamentoExporter {

    private static final String LAYOUT = "SISAIH01-INTERFACE-TEXTO";
    private static final int TAMANHO_REGISTRO = 1600;
    private static final int OCORRENCIAS_PROC = 10;
    private static final DateTimeFormatter AAAAMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    @Override
    public InstrumentoRegistro instrumento() {
        return InstrumentoRegistro.AIH;
    }

    @Override
    public boolean layoutDisponivel() {
        return true;
    }

    @Override
    public ConteudoExportado exportar(List<Afaturar> producoes, FaturamentoCompetencia competencia) {
        String unidade = competencia == null ? "" : texto(competencia.getUnidade());
        String cmp = competencia == null ? "" : texto(competencia.getCompetencia());

        // As contas mandam no arquivo: o registro e por AIH, nao por linha de producao.
        List<FaturamentoAih> contas = buscarContas(competencia, unidade);

        List<FaturamentoAih> comNumero = contas.stream()
                .filter(c -> !texto(c.getNumeroAih()).isBlank())
                .toList();
        if (comNumero.isEmpty()) {
            throw new IllegalStateException(contas.isEmpty()
                    ? "Nenhuma conta hospitalar fechada nesta competencia."
                    : contas.size() + " conta(s) fechada(s) estao sem numero de AIH. Cadastre a faixa "
                            + "concedida pelo gestor e feche as contas novamente.");
        }

        // A producao chega em lista unica; agrupar por BAM devolve cada linha a sua conta,
        // que e o mesmo criterio de listarParaContaAih.
        Map<String, List<Afaturar>> porBam = new LinkedHashMap<>();
        for (Afaturar producao : producoes) {
            porBam.computeIfAbsent(digitos(String.valueOf(producao.getBam())), k -> new ArrayList<>())
                    .add(producao);
        }

        ConfiguracaoUnidade config = configuracaoDa(unidade);
        String cnesUnidade = config == null ? "" : texto(config.getCnesDoHospital());
        String municipio = config == null ? "" : String.valueOf(config.getCodIbge());

        StringBuilder arquivo = new StringBuilder();
        int gerados = 0;
        for (FaturamentoAih conta : comNumero) {
            List<Afaturar> daConta = porBam.getOrDefault(digitos(conta.getBam()), List.of());
            validar(conta, daConta, cnesUnidade, municipio);
            arquivo.append(registro(conta, daConta, cmp, cnesUnidade, municipio)).append("\r\n");
            gerados++;
        }

        return new ConteudoExportado(arquivo.toString().getBytes(StandardCharsets.ISO_8859_1), gerados, LAYOUT);
    }

    List<FaturamentoAih> buscarContas(FaturamentoCompetencia competencia, String unidade) {
        return FaturamentoAih.list(
                "idcompetencia = ?1 and upper(unidade) = ?2 and status in (?3, ?4) order by numeroAih",
                competencia == null ? null : competencia.getIdcompetencia(),
                unidade.toUpperCase(),
                AihStatus.FECHADA.name(), AihStatus.EXPORTADA.name());
    }

    private void validar(FaturamentoAih conta, List<Afaturar> producoes, String cnesUnidade, String municipio) {
        if (digitos(conta.getNumeroAih()).length() != 13) {
            throw new IllegalArgumentException("AIH " + conta.getNumero()
                    + " nao tem NU_AIH oficial com 13 digitos.");
        }
        if (digitos(cnesUnidade).length() < 7) {
            throw new IllegalArgumentException("AIH " + conta.getNumero()
                    + " nao tem CNES da unidade configurado com 7 digitos.");
        }
        if (digitos(municipio).length() < 6) {
            throw new IllegalArgumentException("AIH " + conta.getNumero()
                    + " nao tem municipio IBGE da unidade configurado.");
        }
        if (conta.getDataInternacao() == null) {
            throw new IllegalArgumentException("AIH " + conta.getNumero() + " nao tem data de internacao.");
        }
        if (conta.getDataAlta() == null) {
            throw new IllegalArgumentException("AIH " + conta.getNumero() + " nao tem data de saida/alta.");
        }
        if (digitos(conta.getProcedimentoPrincipal()).length() < 10) {
            throw new IllegalArgumentException("AIH " + conta.getNumero() + " nao tem procedimento principal valido.");
        }
        if (cid(conta.getCid(), producoes.isEmpty() ? null : producoes.get(0)).isBlank()) {
            throw new IllegalArgumentException("AIH " + conta.getNumero() + " nao tem CID principal.");
        }
        if (producoes.isEmpty()) {
            throw new IllegalArgumentException("AIH " + conta.getNumero() + " nao tem producao vinculada.");
        }
        Afaturar base = producoes.get(0);
        if (texto(base.getNomepaciente()).isBlank()) {
            throw new IllegalArgumentException("AIH " + conta.getNumero() + " nao tem nome do paciente.");
        }
        if (cns(base.getCnsdopciente()).isBlank()) {
            throw new IllegalArgumentException("AIH " + conta.getNumero() + " nao tem CNS do paciente valido.");
        }
    }

    private String registro(FaturamentoAih conta, List<Afaturar> producoes, String competencia,
            String cnesUnidade, String municipio) {
        Afaturar base = producoes.isEmpty() ? null : producoes.get(0);
        StringBuilder r = new StringBuilder();

        // --- Comum a todos os registros (1 a 12) ---------------------------------------
        r.append(num("", 8));                                   // 1  NU_LOTE         1-8
        r.append(num("", 3));                                   // 2  QT_LOTE         9-11   ZEROS
        r.append(num(competencia, 6));                          // 3  APRES_LOTE      12-17  AAAAMM
        r.append(num("", 3));                                   // 4  SEQ_LOTE        18-20  ZEROS
        r.append(alfa("", 10));                                 // 5  ORG_EMIS_AIH    21-30
        r.append(num(cnesUnidade, 7));                          // 6  CNES_HOSP       31-37
        r.append(num(municipio, 6));                            // 7  MUN_HOSP        38-43
        r.append(num(conta.getNumeroAih(), 13));                // 8  NU_AIH          44-56
        r.append("01");                                         // 9  IDENT_AIH       57-58  Principal
        r.append(num("", 2));                                   // 10 ESPEC_AIH       59-60
        r.append(num("", 45));                                  // 11 FILLER          61-105 ZEROS
        r.append("02");                                         // 12 MOD_INTERN      106-107 Hospitalar

        // --- Internacao (13 a 23) ------------------------------------------------------
        r.append(num("", 3));                                   // 13 SEQ_AIH5        108-110
        r.append(num("", 13));                                  // 14 AIH_PROX        111-123
        r.append(num("", 13));                                  // 15 AIH_ANT         124-136
        r.append(data(conta.getAtualizadoEm()));                // 16 DT_EMISSAO      137-144
        r.append(data(conta.getDataInternacao()));              // 17 DT_INTERN       145-152
        r.append(data(conta.getDataAlta()));                    // 18 DT_SAIDA        153-160
        r.append(num(conta.getProcedimentoPrincipal(), 10));    // 19 PROC_SOLICITADO 161-170
        r.append("2");                                          // 20 ST_MUDAPROC     171     2-NAO
        r.append(num(conta.getProcedimentoPrincipal(), 10));    // 21 PROC_REALIZADO  172-181
        r.append(num(conta.getCaraterAtendimento(), 2));        // 22 CAR_INTERN      182-183
        r.append(num("", 2));                                   // 23 MOT_SAIDA       184-185

        // --- Profissionais (24 a 31) ---------------------------------------------------
        // Documento so entra com o identificador correspondente. Sem CNS valido na
        // producao, vai em branco: numero errado aqui e glosa certa.
        String cnsProf = cns(base == null ? "" : base.getCnsdoprofissional());
        String indProf = cnsProf.isBlank() ? "0" : "2";
        r.append(indProf);                                      // 24 IDENT_MED_SOL   186
        r.append(alfa(cnsProf, 15));                            // 25 DOC_MED_SOL     187-201
        r.append(indProf);                                      // 26 IDENT_MED_RESP  202
        r.append(alfa(cnsProf, 15));                            // 27 DOC_MED_RESP    203-217
        r.append("0");                                          // 28 IDENT_DIRCLINICO 218
        r.append(alfa("", 15));                                 // 29 DOC_DIRCLINICO  219-233
        r.append("0");                                          // 30 IDENT_AUTORIZ   234
        r.append(alfa("", 15));                                 // 31 DOC_AUTORIZ     235-249

        // --- Diagnosticos (32 a 36) ----------------------------------------------------
        r.append(alfa(cid(conta.getCid(), base), 4));           // 32 DIAG_PRIN       250-253
        r.append(alfa("", 4));                                  // 33 DIAG_SEC        254-257
        r.append(alfa("", 4));                                  // 34 DIAG_COMPL      258-261
        r.append(alfa("", 4));                                  // 35 DIAG_OBITO      262-265
        r.append(num("", 3));                                   // 36 COD_SOL_LIB     266-268

        // --- Paciente e endereco (37 a 57) ---------------------------------------------
        r.append(alfa(nome(base == null ? "" : base.getNomepaciente()), 70));      // 37 NM_PACIENTE   269-338
        r.append(dataTexto(base == null ? "" : base.getDtnascimento()));           // 38 DT_NASC_PAC   339-346
        r.append(sexo(base == null ? "" : base.getSexo()));                        // 39 SEXO_PAC      347
        r.append(num(base == null ? "" : String.valueOf(base.getRaca_cor()), 2));  // 40 RACA/COR      348-349
        r.append(alfa("", 70));                                 // 41 NM_MAE_PAC     350-419
        r.append(alfa("", 70));                                 // 42 NM_RESP_PAC    420-489
        r.append("5");                                          // 43 TP_DOC_PAC      490  5-IGNORADO
        r.append(alfa("", 11));                                 // 44 NU_DOC_PAC      491-501
        r.append(alfa(cns(base == null ? "" : base.getCnsdopciente()), 15));       // 45 NU_CNS        502-516
        r.append(num("", 3));                                   // 46 NAC_PAC         517-519
        r.append(num("", 3));                                   // 47 TP_LOGRADOURO   520-522
        r.append(alfa(nome(base == null ? "" : base.getEndereco()), 50));          // 48 LOGR_PAC      523-572
        r.append(alfa(nome(base == null ? "" : base.getNumerocasa()), 7));         // 49 NU_END_PAC    573-579
        r.append(alfa(nome(base == null ? "" : base.getComplemento()), 15));       // 50 COMPL_END_PAC 580-594
        r.append(alfa(nome(base == null ? "" : base.getBairro()), 30));            // 51 BAIRRO_PAC    595-624
        r.append(num(base == null ? "" : base.getIbgecod(), 6));                   // 52 COD_MUN_END   625-630
        r.append(alfa("", 2));                                  // 53 UF_PAC          631-632
        r.append(num(base == null ? "" : String.valueOf(base.getCep()), 8));       // 54 CEP_PAC       633-640
        r.append(alfa(nome(conta.getProntuario()), 15));        // 55 NU_PRONTUARIO   641-655
        r.append(num("", 4));                                   // 56 NU_ENFERMARIA   656-659
        r.append(num("", 4));                                   // 57 NU_LEITO        660-663

        // --- Procedimentos secundarios: 10 ocorrencias de 73 bytes (664-1393) ----------
        List<Afaturar> secundarios = producoes.stream()
                .filter(p -> !digitos(p.getProcedimento()).equals(digitos(conta.getProcedimentoPrincipal())))
                .limit(OCORRENCIAS_PROC)
                .toList();
        for (int i = 0; i < OCORRENCIAS_PROC; i++) {
            Afaturar p = i < secundarios.size() ? secundarios.get(i) : null;
            String cnsExec = p == null ? "" : cns(p.getCnsdoprofissional());
            String indExec = cnsExec.isBlank() ? "0" : "2";
            r.append(indExec);                                              // 59 IN_PROF        1
            r.append(alfa(cnsExec, 15));                                    // 60 IDENT_PROF     15
            r.append(num(p == null ? "" : p.getCbo(), 6));                  // 61 CBO_PROF       6
            r.append("0");                                                  // 62 IN_EQUIPE      1
            r.append("0");                                                  // 63 IN_SERVICO     1
            r.append(alfa("", 14));                                         // 64 IDENT_SERVICO  14
            r.append(indExec);                                              // 65 IN_EXECUTOR    1
            r.append(alfa(cnsExec, 15));                                    // 66 IDENT_EXECUTOR 15
            r.append(num(p == null ? "" : p.getProcedimento(), 10));        // 67 COD_PROCED     10
            r.append(num(p == null ? "" : "1", 3));                         // 68 QTD_PROCED     3
            r.append(num(p == null ? "" : competencia, 6));                 // 69 CMPT           6
        }

        // --- UTI neonatal, trabalho, parto e demais (70 a 91) --------------------------
        r.append("0");                                          // 70 SAIDA_UTINEO    1394
        r.append(num("", 4));                                   // 71 PESO_UTINEO     1395-1398
        r.append(num("", 1));                                   // 72 MESGEST_UTINEO  1399
        r.append(num("", 14));                                  // 73 CNPJ_EMPREG     1400-1413
        r.append(num("", 6));                                   // 74 CBOR            1414-1419
        r.append(num("", 3));                                   // 75 CNAER           1420-1422
        r.append(num("", 1));                                   // 76 TP_VINCPREV     1423
        r.append(num("", 1));                                   // 77 QT_VIVOS        1424
        r.append(num("", 1));                                   // 78 QT_MORTOS       1425
        r.append(num("", 1));                                   // 79 QT_ALTA         1426
        r.append(num("", 1));                                   // 80 QT_TRANSF       1427
        r.append(num("", 1));                                   // 81 QT_OBITO        1428
        r.append(num("", 10));                                  // 82 FILLER          1429-1438
        r.append(num("", 2));                                   // 83 QT_FILHOS       1439-1440
        r.append(num("", 1));                                   // 84 GRAU_INSTRU     1441
        r.append(alfa("", 4));                                  // 85 CID_INDICACAO   1442-1445
        r.append(num("", 2));                                   // 86 TP_CONTRACEP1   1446-1447
        r.append(num("", 2));                                   // 87 TP_CONTRACEP2   1448-1449
        r.append(num("", 1));                                   // 88 ST_GESTRISCO    1450
        r.append(alfa("", 35));                                 // 89 RESERVADO       1451-1485
        r.append(num("", 11));                                  // 90 NU_PRENATAL     1486-1496
        r.append(num("", 104));                                 // 91 FILLER          1497-1600

        // Registro fora de 1.600 posicoes e rejeicao do lote, com erro dificil de
        // diagnosticar no SISAIH01. Melhor estourar aqui, com a conta identificada.
        if (r.length() != TAMANHO_REGISTRO) {
            throw new IllegalStateException("Registro da AIH " + conta.getNumero() + " ficou com "
                    + r.length() + " posicoes; o layout exige " + TAMANHO_REGISTRO + ".");
        }
        return r.toString();
    }

    // ---- Formatacao posicional -------------------------------------------------------

    /** Campo numerico: zeros a esquerda, mantendo as ultimas posicoes quando excede. */
    private String num(String valor, int tamanho) {
        String so = digitos(valor);
        if (so.length() > tamanho) {
            so = so.substring(so.length() - tamanho);
        }
        return "0".repeat(tamanho - so.length()) + so;
    }

    /** Campo alfanumerico: brancos a direita. */
    private String alfa(String valor, int tamanho) {
        String v = texto(valor);
        if (v.length() > tamanho) {
            v = v.substring(0, tamanho);
        }
        return v + " ".repeat(tamanho - v.length());
    }

    private String data(LocalDateTime valor) {
        return valor == null ? "00000000" : valor.format(AAAAMMDD);
    }

    /** A producao guarda data como texto, e nesta base convivem aaaa-MM-dd e dd/MM/aaaa. */
    private String dataTexto(String valor) {
        String v = texto(valor).split("[T ]")[0];
        if (v.matches("\\d{4}[-/.]\\d{2}[-/.]\\d{2}")) {
            return v.replaceAll("\\D", "");
        }
        if (v.matches("\\d{2}[-/.]\\d{2}[-/.]\\d{4}")) {
            String d = v.replaceAll("\\D", "");
            return d.substring(4) + d.substring(2, 4) + d.substring(0, 2);
        }
        return "00000000";
    }

    /**
     * O layout aceita apenas letras maiusculas, numeros, barra, parenteses, hifen, virgula
     * e ponto. Acento vira a letra sem acento; o resto vira espaco.
     */
    private String nome(String valor) {
        String semAcento = Normalizer.normalize(texto(valor), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase();
        return semAcento.replaceAll("[^A-Z0-9/()\\-,. ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String cns(String valor) {
        String so = digitos(valor);
        return so.length() == 15 ? so : "";
    }

    private String sexo(String valor) {
        String v = texto(valor).toUpperCase();
        return v.startsWith("M") ? "M" : v.startsWith("F") ? "F" : " ";
    }

    private String cid(String daConta, Afaturar base) {
        String v = texto(daConta);
        if (v.isBlank() && base != null) {
            v = texto(base.getCid10());
        }
        return v.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private ConfiguracaoUnidade configuracaoDa(String unidade) {
        if (texto(unidade).isBlank()) {
            return null;
        }
        return configuracaoUnidadeRepository.filtarPorUnidadeSigla(unidade).stream()
                .filter(c -> unidade.equalsIgnoreCase(texto(c.getSigla())))
                .findFirst()
                .orElse(null);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String digitos(String valor) {
        return texto(valor).replaceAll("\\D", "");
    }
}

package br.com.iv.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.dto.SigtapArquivoDatasusDto;
import br.com.iv.dto.SigtapImportacaoRequestDto;
import br.com.iv.dto.SigtapImportarDatasusRequestDto;
import br.com.iv.dto.SigtapProcedimentoImportDto;
import br.com.iv.entity.FaturamentoSigtapImportacao;
import br.com.iv.entity.ProcedimentosSus;
import br.com.iv.repository.FaturamentoSigtapImportacaoRepository;
import br.com.iv.security.AuthenticatedUser;

// Importador SIGTAP da Fase 1. IMPORTANTE: isto NAO faz parsing do layout bruto
// de arquivo oficial do DATASUS (posicional/fixed-width) - essa especificacao
// nao esta disponivel neste repositorio e nao deve ser inventada (secao 82 do
// escopo). O que esta implementado e honesto e testavel: aceita uma lista de
// procedimentos ja no formato usado pela tabela local (ProcedimentosSus/s_proced)
// e insere apenas o que ainda nao existe para aquela competencia - nunca apaga
// nem sobrescreve competencias anteriores (secao 10/66).
// TODO: confirmar layout oficial do arquivo bruto SIGTAP/DATASUS antes de
// implementar um parser de upload de arquivo .txt posicional.
@ApplicationScoped
public class SigtapImportacaoService {

    private static final String DATASUS_FTP_BASE = "ftp://ftp2.datasus.gov.br/pub/sistemas/tup/downloads/";
    private static final Pattern ARQUIVO_SIGTAP = Pattern.compile(
            "TabelaUnificada_(\\d{6})_(v\\d+)\\.zip", Pattern.CASE_INSENSITIVE);
    private static final int FTP_TIMEOUT_MS = 30000;

    @Inject
    FaturamentoSigtapImportacaoRepository importacaoRepository;

    @Inject
    FaturamentoAuditService auditService;

    public List<FaturamentoSigtapImportacao> listarCompetenciasInstaladas(String unidade) {
        // Unidade em branco nao devolve as importacoes das outras: cada unidade cuida so do
        // que e seu, e sessao sem unidade definida nao pode virar acesso geral por omissao.
        // "TODAS" segue valendo como escolha explicita de quem pode ver o conjunto.
        String unidadeNormalizada = normalizarUnidade(unidade);
        if ("TODAS".equals(unidadeNormalizada)) {
            return importacaoRepository.listarTodas();
        }
        if (unidadeNormalizada.isBlank()) {
            return List.of();
        }
        return importacaoRepository.listarPorUnidade(unidadeNormalizada);
    }

    public List<SigtapArquivoDatasusDto> listarCompetenciasDatasus() {
        try {
            URLConnection connection = new URL(DATASUS_FTP_BASE).openConnection();
            connection.setConnectTimeout(FTP_TIMEOUT_MS);
            connection.setReadTimeout(FTP_TIMEOUT_MS);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.ISO_8859_1))) {
                List<SigtapArquivoDatasusDto> arquivos = new ArrayList<>();
                String linha;
                while ((linha = reader.readLine()) != null) {
                    SigtapArquivoDatasusDto arquivo = parseLinhaFtp(linha);
                    if (arquivo != null) {
                        arquivos.add(arquivo);
                    }
                }
                return arquivos.stream()
                        .sorted(Comparator.comparing(SigtapArquivoDatasusDto::getCompetencia).reversed())
                        .limit(6)
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel consultar o FTP do DATASUS.");
        }
    }

    @Transactional
    public Map<String, Object> importarDatasus(SigtapImportarDatasusRequestDto dto, AuthenticatedUser user, String unidadeParametro) {
        if (dto == null) {
            throw new IllegalArgumentException("Informe o arquivo DATASUS para importar.");
        }
        String nomeArquivo = texto(dto.getArquivo());
        Matcher matcher = ARQUIVO_SIGTAP.matcher(nomeArquivo);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Arquivo DATASUS invalido.");
        }
        String competenciaArquivo = matcher.group(1);
        String competencia = texto(dto.getCompetencia()).isBlank() ? competenciaArquivo : texto(dto.getCompetencia());
        if (!competencia.equals(competenciaArquivo)) {
            throw new IllegalArgumentException("Competencia informada nao corresponde ao arquivo selecionado.");
        }
        String unidade = resolverUnidade(dto.getUnidade(), unidadeParametro, user);

        try {
            List<SigtapProcedimentoImportDto> procedimentos = lerProcedimentosDatasus(baixarArquivoDatasus(nomeArquivo), competencia);
            SigtapImportacaoRequestDto request = new SigtapImportacaoRequestDto();
            request.setCompetencia(competencia);
            request.setUnidade(unidade);
            request.setObservacao(texto(dto.getObservacao()) + " arquivo=" + nomeArquivo);
            request.setProcedimentos(procedimentos);
            Map<String, Object> resumo = importar(request, user, unidade);
            resumo.put("arquivo", nomeArquivo);
            resumo.put("origem", DATASUS_FTP_BASE + nomeArquivo);
            return resumo;
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel baixar ou processar o arquivo oficial do DATASUS.");
        }
    }

    @Transactional
    public Map<String, Object> importar(SigtapImportacaoRequestDto dto, AuthenticatedUser user, String unidadeParametro) {
        String competencia = dto == null ? "" : texto(dto.getCompetencia());
        if (!competencia.matches("\\d{6}")) {
            throw new IllegalArgumentException("Competencia deve estar no formato AAAAMM.");
        }
        String unidade = resolverUnidade(dto == null ? "" : dto.getUnidade(), unidadeParametro, user);
        if (unidade.isBlank() || "TODAS".equals(unidade)) {
            throw new IllegalArgumentException("Informe a unidade para importar a competencia SIGTAP.");
        }
        if (importacaoRepository.buscarUltimaPorCompetenciaUnidade(competencia, unidade) != null) {
            throw new IllegalArgumentException("Competencia " + competencia + " ja esta importada para a unidade " + unidade + ". Remova a competencia instalada nesta unidade antes de importar novamente.");
        }
        List<SigtapProcedimentoImportDto> procedimentos = dto.getProcedimentos();
        if (procedimentos == null || procedimentos.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um procedimento para importar.");
        }

        int inseridos = 0;
        int duplicados = 0;
        int erros = 0;
        for (SigtapProcedimentoImportDto procedimento : procedimentos) {
            try {
                if (inserirSeNaoExistir(competencia, procedimento)) {
                    inseridos++;
                } else {
                    duplicados++;
                }
            } catch (IllegalArgumentException e) {
                erros++;
            }
        }

        FaturamentoSigtapImportacao registro = new FaturamentoSigtapImportacao();
        registro.setCompetencia(competencia);
        registro.setUnidade(unidade);
        registro.setStatus("IMPORTADA");
        registro.setQuantidadeProcedimentos(inseridos);
        registro.setQuantidadeErros(erros);
        registro.setDataImportacao(LocalDateTime.now());
        registro.setUsuario(user == null ? "" : user.matricula());
        registro.setObservacao(texto(dto.getObservacao()));
        registro.persist();

        auditService.registrar(unidade, "IMPORTAR_SIGTAP", "SUCESSO",
                "FaturamentoSigtapImportacao", String.valueOf(registro.getIdsigtapimportacao()), null, competencia,
                null, "unidade=" + unidade + " inseridos=" + inseridos + " duplicados=" + duplicados + " erros=" + erros, user);

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("competencia", competencia);
        resumo.put("unidade", unidade);
        resumo.put("inseridos", inseridos);
        resumo.put("duplicados", duplicados);
        resumo.put("erros", erros);
        resumo.put("total", procedimentos.size());
        return resumo;
    }

    @Transactional
    public Map<String, Object> removerCompetencia(int id, AuthenticatedUser user, String unidadeParametro) {
        FaturamentoSigtapImportacao registro = importacaoRepository.findById((long) id);
        if (registro == null) {
            throw new IllegalArgumentException("Competencia SIGTAP nao localizada.");
        }
        String unidade = resolverUnidade(registro.getUnidade(), unidadeParametro, user);
        String unidadeRegistro = normalizarUnidade(registro.getUnidade());
        if (!unidadeRegistro.isBlank() && !unidadeRegistro.equals(unidade)) {
            throw new IllegalArgumentException("Competencia SIGTAP nao pertence a unidade atual.");
        }
        String competencia = texto(registro.getCompetencia());
        registro.delete();
        long procedimentosRemovidos = 0;
        if (importacaoRepository.contarPorCompetencia(competencia) == 0) {
            procedimentosRemovidos = ProcedimentosSus.delete("pa_cmp", competencia);
        }

        auditService.registrar(unidade, "REMOVER_SIGTAP", "SUCESSO",
                "FaturamentoSigtapImportacao", String.valueOf(id), competencia, null,
                null, "unidade=" + unidade + " procedimentosRemovidos=" + procedimentosRemovidos, user);

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("competencia", competencia);
        resumo.put("unidade", unidade);
        resumo.put("procedimentosRemovidos", procedimentosRemovidos);
        resumo.put("removida", true);
        return resumo;
    }

    private boolean inserirSeNaoExistir(String competencia, SigtapProcedimentoImportDto dto) {
        String paId = limite(texto(dto.getPaId()), 9);
        String paDv = limite(texto(dto.getPaDv()), 1);
        if (paId.isBlank() || paDv.isBlank()) {
            throw new IllegalArgumentException("pa_id e pa_dv sao obrigatorios.");
        }
        boolean jaExiste = !ProcedimentosSus
                .find("pa_id = ?1 and pa_dv = ?2 and pa_cmp = ?3", paId, paDv, competencia)
                .list()
                .isEmpty();
        if (jaExiste) {
            return false;
        }
        ProcedimentosSus novo = new ProcedimentosSus();
        novo.setPa_cmp(competencia);
        novo.setPa_id(paId);
        novo.setPa_dv(paDv);
        novo.setPa_dc(limite(texto(dto.getPaDc()), 60));
        novo.setPa_sa(dto.getPaSa());
        novo.setPa_sp(dto.getPaSp());
        novo.setPa_sh(dto.getPaSh());
        novo.setPa_eletiva(limite(texto(dto.getPaEletiva()), 1));
        novo.setPa_tpreg(texto(dto.getPaTpreg()));
        novo.setTp_sexo(limite(texto(dto.getTpSexo()), 1));
        novo.setVl_idade_minima(dto.getIdadeMinima());
        novo.setVl_idade_maxima(dto.getIdadeMaxima());
        novo.setQt_maxima_execucao(dto.getQuantidadeMaxima());
        novo.setTp_complexidade(limite(texto(dto.getTpComplexidade()), 1));
        novo.setCo_financiamento(limite(texto(dto.getCoFinanciamento()), 2));
        novo.persist();
        return true;
    }

    private SigtapArquivoDatasusDto parseLinhaFtp(String linha) {
        if (linha == null || !linha.contains("TabelaUnificada_")) {
            return null;
        }
        String[] partes = linha.trim().split("\\s+");
        if (partes.length < 9) {
            return null;
        }
        String nome = partes[partes.length - 1];
        Matcher matcher = ARQUIVO_SIGTAP.matcher(nome);
        if (!matcher.matches()) {
            return null;
        }
        long tamanho = 0L;
        try {
            tamanho = Long.parseLong(partes[4]);
        } catch (NumberFormatException ignored) {
            tamanho = 0L;
        }
        String data = partes[5] + " " + partes[6] + " " + partes[7];
        return new SigtapArquivoDatasusDto(nome, matcher.group(1), matcher.group(2), DATASUS_FTP_BASE + nome, tamanho, data);
    }

    private byte[] baixarArquivoDatasus(String nomeArquivo) throws IOException {
        URLConnection connection = new URL(DATASUS_FTP_BASE + nomeArquivo).openConnection();
        connection.setConnectTimeout(FTP_TIMEOUT_MS);
        connection.setReadTimeout(FTP_TIMEOUT_MS);
        try (InputStream input = connection.getInputStream()) {
            return input.readAllBytes();
        }
    }

    private List<SigtapProcedimentoImportDto> lerProcedimentosDatasus(byte[] zipBytes, String competencia) throws IOException {
        Map<String, String> registros = new HashMap<>();
        List<SigtapProcedimentoImportDto> procedimentos = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.ISO_8859_1)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String nome = entry.getName();
                if ("rl_procedimento_registro.txt".equalsIgnoreCase(nome)) {
                    lerRegistros(zip, registros, competencia);
                } else if ("tb_procedimento.txt".equalsIgnoreCase(nome)) {
                    lerProcedimentos(zip, procedimentos, competencia);
                }
                zip.closeEntry();
            }
        }
        for (SigtapProcedimentoImportDto procedimento : procedimentos) {
            procedimento.setPaTpreg(registros.getOrDefault(procedimento.getPaId() + procedimento.getPaDv(), ""));
        }
        return procedimentos;
    }

    private void lerRegistros(InputStream input, Map<String, String> registros, String competencia) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.ISO_8859_1));
        String linha;
        while ((linha = reader.readLine()) != null) {
            if (linha.length() < 18 || !competencia.equals(campo(linha, 13, 18))) {
                continue;
            }
            String procedimento = campo(linha, 1, 10);
            String registro = campo(linha, 11, 12);
            // rl_procedimento_registro e uma relacao N:N - um procedimento pode ser
            // registrado em mais de um instrumento (uma cirurgia que cabe em AIH e em
            // APAC, um exame que cabe em BPA-I e em AIH). Guardar so um deles perdia a
            // informacao que decide o instrumento: era o que fazia PARTO NORMAL ficar
            // com '03' (APAC, o primeiro registro lido) e, pela regra antiga, sair no BPA.
            //
            // Aqui ficam todos, concatenados e ordenados, para o valor nao depender da
            // ordem de leitura do arquivo. Quem le decide com admiteRegistro().
            registros.merge(procedimento, registro, (atual, novo) -> {
                if (atual.contains(novo)) {
                    return atual;
                }
                List<String> todos = new java.util.ArrayList<>();
                for (int i = 0; i + 2 <= atual.length(); i += 2) {
                    todos.add(atual.substring(i, i + 2));
                }
                todos.add(novo);
                java.util.Collections.sort(todos);
                return String.join("", todos);
            });
        }
    }

    private void lerProcedimentos(InputStream input, List<SigtapProcedimentoImportDto> procedimentos, String competencia) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.ISO_8859_1));
        String linha;
        while ((linha = reader.readLine()) != null) {
            if (linha.length() < 336 || !competencia.equals(campo(linha, 331, 336))) {
                continue;
            }
            String codigo = campo(linha, 1, 10);
            SigtapProcedimentoImportDto dto = new SigtapProcedimentoImportDto();
            dto.setPaId(codigo.substring(0, 9));
            dto.setPaDv(codigo.substring(9, 10));
            dto.setPaDc(limite(campo(linha, 11, 260), 60));
            dto.setPaSh(valorMonetario(campo(linha, 283, 294)));
            dto.setPaSa(valorMonetario(campo(linha, 295, 306)));
            dto.setPaSp(valorMonetario(campo(linha, 307, 318)));
            // Posicoes do layout de tb_procedimento.txt. As quatro que ja estavam aqui
            // (VL_SH 283, VL_SA 295, VL_SP 307 e DT_COMPETENCIA 331) fixam o alinhamento,
            // e os campos abaixo preenchem exatamente o intervalo 261-282 que sobra entre
            // NO_PROCEDIMENTO e VL_SH: 1+1+4+4+4+4+4 = 22 posicoes. Nao ha layout
            // adivinhado aqui - so os campos que o arquivo ja trazia e eram descartados.
            dto.setTpComplexidade(campo(linha, 261, 261));
            dto.setTpSexo(campo(linha, 262, 262));
            dto.setQuantidadeMaxima(inteiro(campo(linha, 263, 266)));
            dto.setIdadeMinima(inteiro(campo(linha, 275, 278)));
            dto.setIdadeMaxima(inteiro(campo(linha, 279, 282)));
            dto.setCoFinanciamento(campo(linha, 319, 320));
            dto.setPaEletiva("");
            procedimentos.add(dto);
        }
    }

    private String campo(String linha, int inicio, int fim) {
        int start = Math.max(0, inicio - 1);
        int end = Math.min(linha.length(), fim);
        if (start >= end) {
            return "";
        }
        return linha.substring(start, end).trim();
    }

    // Campo numerico do layout posicional: vazio ou nao numerico vira null, e null
    // significa "o SIGTAP nao informou" - a validacao nao critica em cima disso.
    private Integer inteiro(String valor) {
        String digitos = valor == null ? "" : valor.replaceAll("[^0-9]", "");
        if (digitos.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(digitos);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double valorMonetario(String valor) {
        if (valor == null || valor.isBlank()) {
            return 0D;
        }
        return new BigDecimal(valor).movePointLeft(2).doubleValue();
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String resolverUnidade(String unidadeDto, String unidadeParametro, AuthenticatedUser user) {
        String unidade = normalizarUnidade(unidadeParametro);
        if (unidade.isBlank()) {
            unidade = normalizarUnidade(unidadeDto);
        }
        if (unidade.isBlank() && user != null) {
            unidade = normalizarUnidade(user.unidade());
        }
        return unidade;
    }

    private String normalizarUnidade(String unidade) {
        return texto(unidade).split(":")[0].trim().toUpperCase();
    }

    private String limite(String valor, int tamanhoMaximo) {
        String texto = texto(valor);
        return texto.length() > tamanhoMaximo ? texto.substring(0, tamanhoMaximo) : texto;
    }
}

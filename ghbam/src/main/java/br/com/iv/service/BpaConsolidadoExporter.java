package br.com.iv.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import br.com.iv.dto.ConteudoExportado;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.enums.InstrumentoRegistro;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;

import static br.com.iv.service.BpaLayoutUtils.alfa;
import static br.com.iv.service.BpaLayoutUtils.bytesLatin1;
import static br.com.iv.service.BpaLayoutUtils.numerico;

// Layout oficial confirmado: circular tecnica FEHOSP 1071/2020, "Layout da
// interface texto do BPA" (DATASUS/SIA). Registro BPA-C = tipo "02", 50
// caracteres. BPA-C e "consolidado": uma linha por combinacao distinta de
// CBO+procedimento na competencia, com a quantidade somada - por isso agrupa
// as producoes elegiveis antes de escrever (Afaturar nao tem campo de
// quantidade proprio; cada linha elegivel conta como 1 ocorrencia).
@ApplicationScoped
public class BpaConsolidadoExporter implements FaturamentoExporter {

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    @Override
    public InstrumentoRegistro instrumento() {
        return InstrumentoRegistro.BPA_C;
    }

    @Override
    public boolean layoutDisponivel() {
        return true;
    }

    @Override
    public ConteudoExportado exportar(List<Afaturar> producoes, FaturamentoCompetencia competencia) {
        if (producoes == null || producoes.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma producao elegivel para exportar.");
        }
        ConfiguracaoUnidade configuracao = buscarConfiguracao(producoes.get(0).getUnidade());
        String cnes = numerico(configuracao == null ? "" : configuracao.getCnesDoHospital(), 7);
        String competenciaAaaamm = numerico(competencia.getCompetencia(), 6);

        Map<String, Consolidado> agrupado = new LinkedHashMap<>();
        for (Afaturar producao : producoes) {
            String chave = safe(producao.getCbo()) + "|" + safe(producao.getProcedimento());
            agrupado.computeIfAbsent(chave, k -> new Consolidado(producao)).quantidade++;
        }

        StringBuilder corpo = new StringBuilder();
        int sequencial = 0;
        long somaControle = 0;
        for (Consolidado item : agrupado.values()) {
            int folha = (sequencial / 20) + 1;
            int seq = (sequencial % 20) + 1;
            sequencial++;

            corpo.append(numerico("02", 2))
                    .append(cnes)
                    .append(competenciaAaaamm)
                    .append(alfa(item.producao.getCbo(), 6))
                    .append(numerico(folha, 3))
                    .append(numerico(seq, 2))
                    .append(numerico(item.producao.getProcedimento(), 10))
                    .append(numerico(item.producao.getIdade(), 3))
                    .append(numerico(item.quantidade, 6))
                    .append(alfa(origem(item.producao), 3))
                    .append(BpaLayoutUtils.QUEBRA_LINHA);

            somaControle += numeroProcedimento(item.producao.getProcedimento()) + item.quantidade;
        }

        int folhas = (sequencial + 19) / 20;
        String cabecalho = BpaLayoutUtils.cabecalho(configuracao, competenciaAaaamm, sequencial, folhas, somaControle);
        return new ConteudoExportado(bytesLatin1(cabecalho + corpo), sequencial, "BPA-DATASUS-FEHOSP-2020");
    }

    private ConfiguracaoUnidade buscarConfiguracao(String unidade) {
        if (unidade == null || unidade.isBlank()) {
            return null;
        }
        return configuracaoUnidadeRepository.filtarPorUnidadeSigla(unidade).stream()
                .filter(c -> unidade.equalsIgnoreCase(safe(c.getSigla())))
                .findFirst()
                .orElse(null);
    }

    private String origem(Afaturar producao) {
        String valor = safe(producao.getOrigeminfo());
        return valor.isBlank() ? "BPA" : valor;
    }

    private long numeroProcedimento(String procedimento) {
        String digitos = procedimento == null ? "" : procedimento.replaceAll("\\D", "");
        if (digitos.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(digitos);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String safe(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static final class Consolidado {
        final Afaturar producao;
        int quantidade;

        Consolidado(Afaturar producao) {
            this.producao = producao;
        }
    }
}

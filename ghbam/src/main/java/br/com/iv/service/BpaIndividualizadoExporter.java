package br.com.iv.service;

import java.util.List;

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
import static br.com.iv.service.BpaLayoutUtils.cid;
import static br.com.iv.service.BpaLayoutUtils.data8;
import static br.com.iv.service.BpaLayoutUtils.numerico;

// Layout oficial confirmado: circular tecnica FEHOSP 1071/2020, "Layout da
// interface texto do BPA" (DATASUS/SIA). Registro BPA-I = tipo "03", 340
// caracteres, um por atendimento (sem consolidacao). Os campos de Afaturar ja
// foram modelados com o mesmo tamanho dos campos oficiais correspondentes
// (endereco=30, complemento=10, numerocasa=5, bairro=30, email=40,
// nomepaciente=30, procedimento=10) - mapeamento direto, sem transformacao.
@ApplicationScoped
public class BpaIndividualizadoExporter implements FaturamentoExporter {

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    @Override
    public InstrumentoRegistro instrumento() {
        return InstrumentoRegistro.BPA_I;
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
        String cnesPadrao = numerico(configuracao == null ? "" : configuracao.getCnesDoHospital(), 7);
        String competenciaAaaamm = numerico(competencia.getCompetencia(), 6);

        StringBuilder corpo = new StringBuilder();
        int sequencial = 0;
        long somaControle = 0;
        for (Afaturar producao : producoes) {
            int folha = (sequencial / 20) + 1;
            int seq = (sequencial % 20) + 1;
            sequencial++;

            corpo.append(numerico("03", 2))
                    .append(cnesPadrao)
                    .append(competenciaAaaamm)
                    .append(numerico(producao.getCnsdoprofissional(), 15))
                    .append(alfa(producao.getCbo(), 6))
                    .append(data8(producao.getDtatendimento()))
                    .append(numerico(folha, 3))
                    .append(numerico(seq, 2))
                    .append(numerico(producao.getProcedimento(), 10))
                    .append(numerico(producao.getCnsdopciente(), 15))
                    .append(alfa(sexo(producao.getSexo()), 1))
                    .append(numerico(producao.getIbgecod(), 6))
                    .append(alfa(cid(producao.getCid10()), 4))
                    .append(numerico(producao.getIdade(), 3))
                    .append(numerico(1, 6))
                    .append(numerico(producao.getCarateratend(), 2))
                    .append(numerico(producao.getAutorizacao(), 13))
                    .append(alfa(origem(producao), 3))
                    .append(alfa(producao.getNomepaciente(), 30))
                    .append(data8(producao.getDtnascimento()))
                    .append(numerico(producao.getRaca_cor(), 2))
                    .append(numerico(producao.getEtnia_raca(), 4))
                    .append(numerico(producao.getNacionalidade(), 3))
                    .append(numerico(producao.getCodservico(), 3))
                    .append(numerico(producao.getCodclassificacao(), 3))
                    .append(numerico(producao.getCod_seq_equipe(), 8))
                    .append(numerico(producao.getCod_area_equipe(), 4))
                    .append(numerico(producao.getCod_cnpj(), 14))
                    .append(numerico(producao.getCep(), 8))
                    .append(numerico(producao.getCod_logradouro(), 3))
                    .append(alfa(producao.getEndereco(), 30))
                    .append(alfa(producao.getComplemento(), 10))
                    .append(alfa(producao.getNumerocasa(), 5))
                    .append(alfa(producao.getBairro(), 30))
                    .append(numerico(producao.getTelefone(), 11))
                    .append(alfa(producao.getEmail(), 40))
                    .append(numerico(producao.getIdentnacequipe(), 10))
                    .append(BpaLayoutUtils.QUEBRA_LINHA);

            somaControle += numeroProcedimento(producao.getProcedimento()) + 1;
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

    private String sexo(String valor) {
        String texto = safe(valor).toUpperCase();
        return texto.startsWith("F") ? "F" : "M";
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
}

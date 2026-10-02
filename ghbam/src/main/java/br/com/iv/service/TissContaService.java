package br.com.iv.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.com.iv.dto.TissContaDto;
import br.com.iv.entity.Afaturar;
import br.com.iv.dto.TissContaItemDto;
import br.com.iv.entity.ConfiguracaoUnidade;
import br.com.iv.entity.TissConta;
import br.com.iv.entity.TissContaItem;
import br.com.iv.repository.AfaturarRepository;
import br.com.iv.repository.ConfiguracaoUnidadeRepository;
import br.com.iv.repository.TissContaItemRepository;
import br.com.iv.repository.TissContaRepository;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TissContaService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    TissContaRepository tissContaRepository;

    @Inject
    TissContaItemRepository tissContaItemRepository;

    @Inject
    AfaturarRepository afaturarRepository;

    @Inject
    ConfiguracaoUnidadeRepository configuracaoUnidadeRepository;

    public List<TissConta> listarPorPeriodoUnidade(String dataInicial, String dataFinal, String unidade) {
        validarObrigatorio(dataInicial, "Data inicial");
        validarObrigatorio(dataFinal, "Data final");
        validarObrigatorio(unidade, "Unidade");
        return tissContaRepository.listarPorPeriodoUnidade(dataInicial, dataFinal, unidade);
    }

    public List<TissConta> listarPorBam(int bam) {
        if (bam <= 0) {
            throw new IllegalArgumentException("BAM e obrigatorio");
        }
        return tissContaRepository.listarPorBam(bam);
    }

    public Optional<TissConta> buscarConta(int id) {
        return tissContaRepository.findByIdOptional((long) id);
    }

    public List<TissContaItem> listarItens(int idtissconta) {
        if (idtissconta <= 0) {
            throw new IllegalArgumentException("Conta TISS e obrigatoria");
        }
        return tissContaItemRepository.listarPorConta(idtissconta);
    }

    public Map<String, Object> conciliarBpaIndividualizado(String dataInicial, String dataFinal, String unidade) {
        validarObrigatorio(dataInicial, "Data inicial");
        validarObrigatorio(dataFinal, "Data final");
        validarObrigatorio(unidade, "Unidade");

        List<Afaturar> producao = afaturarRepository.filtarPorPeriodoEunidade(dataInicial, dataFinal, unidade);
        Set<String> origemIdsPeriodo = new HashSet<>();
        for (Afaturar itemAfaturar : producao) {
            if (itemAfaturar.getIdafaturar() > 0) {
                origemIdsPeriodo.add(String.valueOf(itemAfaturar.getIdafaturar()));
            }
        }
        Set<String> origemIdsTiss = new HashSet<>(tissContaItemRepository.listarOrigemIds("AFATURAR", origemIdsPeriodo));

        List<Map<String, Object>> itens = new ArrayList<>();
        BigDecimal totalProducao = BigDecimal.ZERO;
        BigDecimal totalTiss = BigDecimal.ZERO;
        BigDecimal totalBpaIndividualizado = BigDecimal.ZERO;
        int itensComTiss = 0;
        int itensBpaIndividualizado = 0;
        int divergencias = 0;

        for (Afaturar itemAfaturar : producao) {
            BigDecimal valor = BigDecimal.valueOf(itemAfaturar.getPa_sa() + itemAfaturar.getPa_sp() + itemAfaturar.getPa_sh());
            boolean emTiss = origemIdsTiss.contains(String.valueOf(itemAfaturar.getIdafaturar()));
            boolean bpaIndividualizado = isBpaIndividualizado(itemAfaturar);
            List<String> pendenciasBpa = pendenciasBpaIndividualizado(itemAfaturar);
            boolean divergente = emTiss != bpaIndividualizado || (bpaIndividualizado && !pendenciasBpa.isEmpty());

            totalProducao = totalProducao.add(valor);
            if (emTiss) {
                totalTiss = totalTiss.add(valor);
                itensComTiss++;
            }
            if (bpaIndividualizado) {
                totalBpaIndividualizado = totalBpaIndividualizado.add(valor);
                itensBpaIndividualizado++;
            }
            if (divergente) {
                divergencias++;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("idafaturar", itemAfaturar.getIdafaturar());
            item.put("data", itemAfaturar.getData());
            item.put("bam", itemAfaturar.getBam());
            item.put("idpaciente", itemAfaturar.getIdpaciente());
            item.put("paciente", itemAfaturar.getNomepaciente());
            item.put("procedimento", itemAfaturar.getProcedimento());
            item.put("descricao", itemAfaturar.getDescricao());
            item.put("cbo", itemAfaturar.getCbo());
            item.put("cid10", itemAfaturar.getCid10());
            item.put("cnsPaciente", itemAfaturar.getCnsdopciente());
            item.put("cnsProfissional", itemAfaturar.getCnsdoprofissional());
            item.put("origemInfo", itemAfaturar.getOrigeminfo());
            item.put("tipoRegistroBpa", itemAfaturar.getPa_tpreg());
            item.put("emTiss", emTiss);
            item.put("bpaIndividualizado", bpaIndividualizado);
            item.put("pendenciasBpa", pendenciasBpa);
            item.put("divergente", divergente);
            item.put("valor", valor);
            itens.add(item);
        }

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("itensProducao", producao.size());
        resumo.put("itensComTiss", itensComTiss);
        resumo.put("itensBpaIndividualizado", itensBpaIndividualizado);
        resumo.put("divergencias", divergencias);
        resumo.put("totalProducao", totalProducao);
        resumo.put("totalTiss", totalTiss);
        resumo.put("totalBpaIndividualizado", totalBpaIndividualizado);
        resumo.put("diferencaTissBpa", totalTiss.subtract(totalBpaIndividualizado));

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("resumo", resumo);
        resultado.put("itens", itens);
        return resultado;
    }


    @Transactional
    @TransactionConfiguration(timeout = 600)
    public Map<String, Object> capturarPeriodo(String dataInicial, String dataFinal, String unidade, TissContaDto padrao) {
        validarObrigatorio(dataInicial, "Data inicial");
        validarObrigatorio(dataFinal, "Data final");
        validarObrigatorio(unidade, "Unidade");
        if (padrao == null) {
            padrao = new TissContaDto();
        }
        aplicarCnesDaUnidade(padrao, unidade);
        final TissContaDto padraoCaptura = padrao;

        List<Afaturar> producao = afaturarRepository.filtarPorPeriodoEunidade(dataInicial, dataFinal, unidade);
        Set<String> origemIdsPeriodo = new HashSet<>();
        for (Afaturar itemAfaturar : producao) {
            if (itemAfaturar.getIdafaturar() > 0) {
                origemIdsPeriodo.add(String.valueOf(itemAfaturar.getIdafaturar()));
            }
        }
        Set<String> origemIdsJaLancados = new HashSet<>(tissContaItemRepository.listarOrigemIds("AFATURAR", origemIdsPeriodo));
        Map<Integer, TissConta> contasPorBam = new LinkedHashMap<>();
        Map<Integer, BigDecimal> totaisAdicionadosPorBam = new LinkedHashMap<>();
        tissContaRepository.listarPorPeriodoUnidade(dataInicial, dataFinal, unidade).stream()
                .filter(conta -> "ABERTA".equalsIgnoreCase(conta.getStatus()))
                .forEach(conta -> contasPorBam.put(conta.getBam(), conta));
        int itensCriados = 0;
        int itensIgnorados = 0;

        for (Afaturar itemAfaturar : producao) {
            if (itemAfaturar.getBam() <= 0) {
                itensIgnorados++;
                continue;
            }
            String origemId = String.valueOf(itemAfaturar.getIdafaturar());
            if (origemIdsJaLancados.contains(origemId)) {
                itensIgnorados++;
                continue;
            }

            TissConta conta = contasPorBam.get(itemAfaturar.getBam());
            if (conta == null) {
                conta = criarContaCapturada(itemAfaturar, padraoCaptura, unidade);
                contasPorBam.put(itemAfaturar.getBam(), conta);
            }

            TissContaItem item = new TissContaItem();
            item.setIdtissconta(conta.getIdtissconta());
            item.setTipoItem("PROCEDIMENTO");
            item.setTabela("22");
            item.setCodigo(itemAfaturar.getProcedimento());
            item.setDescricao(itemAfaturar.getDescricao());
            item.setQuantidade(BigDecimal.ONE);
            item.setValorUnitario(BigDecimal.valueOf(itemAfaturar.getPa_sa() + itemAfaturar.getPa_sp() + itemAfaturar.getPa_sh()));
            item.setValorTotal(item.getValorUnitario());
            item.setDataExecucao(itemAfaturar.getData());
            item.setExecutante(itemAfaturar.getNomeservidor());
            item.setCbo(itemAfaturar.getCbo());
            item.setNumeroautorizacao(padrao.getNumeroautorizacao());
            item.setOrigem("AFATURAR");
            item.setOrigemId(origemId);
            item.setStatus("PENDENTE");
            item.setObservacao(itemAfaturar.getObs());
            item.persist();
            totaisAdicionadosPorBam.merge(conta.getBam(), item.getValorTotal(), BigDecimal::add);
            origemIdsJaLancados.add(origemId);
            itensCriados++;
        }

        for (Map.Entry<Integer, BigDecimal> totalAdicionado : totaisAdicionadosPorBam.entrySet()) {
            TissConta conta = contasPorBam.get(totalAdicionado.getKey());
            if (conta != null) {
                conta.setValorTotal(valorOuZero(conta.getValorTotal()).add(totalAdicionado.getValue()));
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("atendimentosProcessados", producao.size());
        resultado.put("contasCriadasOuAtualizadas", contasPorBam.size());
        resultado.put("itensCriados", itensCriados);
        resultado.put("itensIgnorados", itensIgnorados);
        return resultado;
    }

    @Transactional
    public Map<String, Object> aplicarPadraoPeriodo(String dataInicial, String dataFinal, String unidade, TissContaDto padrao) {
        validarObrigatorio(dataInicial, "Data inicial");
        validarObrigatorio(dataFinal, "Data final");
        validarObrigatorio(unidade, "Unidade");
        if (padrao == null) {
            throw new IllegalArgumentException("Dados padrao ausentes");
        }

        List<TissConta> contas = tissContaRepository.listarPorPeriodoUnidade(dataInicial, dataFinal, unidade);
        int atualizadas = 0;
        for (TissConta conta : contas) {
            if (!"ABERTA".equalsIgnoreCase(conta.getStatus())) {
                continue;
            }
            boolean alterou = false;
            alterou = aplicarSeInformado(padrao.getTipo(), conta::setTipo) || alterou;
            alterou = aplicarSeInformado(padrao.getOperadora(), conta::setOperadora) || alterou;
            alterou = aplicarSeInformado(cnesEfetivo(padrao.getCnesprestador(), unidade), conta::setCnesprestador) || alterou;
            alterou = aplicarSeInformado(padrao.getRegistroans(), conta::setRegistroans) || alterou;
            alterou = aplicarSeInformado(padrao.getPlano(), conta::setPlano) || alterou;
            alterou = aplicarSeInformado(padrao.getNumerocarteira(), conta::setNumerocarteira) || alterou;
            alterou = aplicarSeInformado(padrao.getNumeroautorizacao(), conta::setNumeroautorizacao) || alterou;
            if (alterou) {
                conta.setAtualizadoPor(padrao.getAtualizadoPor());
                atualizadas++;
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("contasEncontradas", contas.size());
        resultado.put("contasAtualizadas", atualizadas);
        return resultado;
    }

    @Transactional
    public Map<String, Object> validarPeriodo(String dataInicial, String dataFinal, String unidade) {
        validarObrigatorio(dataInicial, "Data inicial");
        validarObrigatorio(dataFinal, "Data final");
        validarObrigatorio(unidade, "Unidade");

        List<TissConta> contas = tissContaRepository.listarPorPeriodoUnidade(dataInicial, dataFinal, unidade);
        List<Map<String, Object>> pendencias = new ArrayList<>();
        int validadas = 0;
        for (TissConta conta : contas) {
            if (!"ABERTA".equalsIgnoreCase(conta.getStatus())) {
                continue;
            }
            List<String> erros = pendenciasDaConta(conta);
            if (!erros.isEmpty()) {
                Map<String, Object> pendencia = new LinkedHashMap<>();
                pendencia.put("idtissconta", conta.getIdtissconta());
                pendencia.put("bam", conta.getBam());
                pendencia.put("paciente", conta.getNomepaciente());
                pendencia.put("pendencias", erros);
                pendencias.add(pendencia);
                continue;
            }
            conta.setStatus("VALIDADA");
            recalcularTotal(conta);
            validadas++;
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("contasAnalisadas", contas.size());
        resultado.put("contasValidadas", validadas);
        resultado.put("contasComPendencia", pendencias.size());
        resultado.put("pendencias", pendencias);
        return resultado;
    }

    private TissConta criarContaCapturada(Afaturar itemAfaturar, TissContaDto padrao, String unidade) {
        TissConta conta = new TissConta();
        conta.setBam(itemAfaturar.getBam());
        conta.setIdpaciente(itemAfaturar.getIdpaciente());
        conta.setNomepaciente(itemAfaturar.getNomepaciente());
        conta.setUnidade(unidade.toUpperCase());
        conta.setCompetencia(padrao.getCompetencia());
        conta.setTipo(padrao.getTipo() == null || padrao.getTipo().isBlank() ? "HOSPITALAR" : padrao.getTipo());
        conta.setStatus("ABERTA");
        conta.setOperadora(padrao.getOperadora());
        conta.setCnesprestador(cnesEfetivo(padrao.getCnesprestador(), unidade));
        conta.setRegistroans(padrao.getRegistroans());
        conta.setPlano(padrao.getPlano());
        conta.setNumerocarteira(padrao.getNumerocarteira());
        conta.setNumeroautorizacao(padrao.getNumeroautorizacao());
        conta.setDataabertura(itemAfaturar.getData());
        conta.setCriadoPor(padrao.getCriadoPor());
        conta.setOrigem("CAPTURA_AFATURAR");
        conta.setObservacao(padrao.getObservacao());
        conta.setValorTotal(BigDecimal.ZERO);
        conta.persist();
        return conta;
    }

    @Transactional
    public TissConta salvarConta(TissContaDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Dados da conta TISS ausentes");
        }
        if (dto.getBam() <= 0 && dto.getIdpaciente() <= 0) {
            throw new IllegalArgumentException("Informe BAM ou paciente para abrir a conta TISS");
        }
        validarObrigatorio(dto.getUnidade(), "Unidade");
        validarObrigatorio(dto.getTipo(), "Tipo da conta");
        aplicarCnesDaUnidade(dto, dto.getUnidade());

        TissConta conta = new TissConta();
        aplicarDto(conta, dto);
        if (conta.getStatus() == null || conta.getStatus().isBlank()) {
            conta.setStatus("ABERTA");
        }
        if (conta.getValorTotal() == null) {
            conta.setValorTotal(BigDecimal.ZERO);
        }
        conta.persist();
        return conta;
    }

    @Transactional
    public TissContaItem salvarItem(int idtissconta, TissContaItemDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Dados do item TISS ausentes");
        }
        TissConta conta = tissContaRepository.findByIdOptional((long) idtissconta)
                .orElseThrow(() -> new IllegalArgumentException("Conta TISS nao encontrada"));
        if (!"ABERTA".equalsIgnoreCase(conta.getStatus())) {
            throw new IllegalArgumentException("Somente contas TISS abertas permitem lancamento de itens");
        }
        validarObrigatorio(dto.getTipoItem(), "Tipo do item");
        validarObrigatorio(dto.getCodigo(), "Codigo do item");
        validarObrigatorio(dto.getDescricao(), "Descricao do item");

        TissContaItem item = new TissContaItem();
        item.setIdtissconta(idtissconta);
        item.setTipoItem(dto.getTipoItem());
        item.setTabela(dto.getTabela());
        item.setCodigo(dto.getCodigo());
        item.setDescricao(dto.getDescricao());
        item.setQuantidade(valorOuZero(dto.getQuantidade()));
        item.setValorUnitario(valorOuZero(dto.getValorUnitario()));
        item.setValorTotal(dto.getValorTotal() == null
                ? item.getValorUnitario().multiply(item.getQuantidade())
                : dto.getValorTotal());
        item.setDataExecucao(dto.getDataExecucao());
        item.setExecutante(dto.getExecutante());
        item.setConselho(dto.getConselho());
        item.setUfConselho(dto.getUfConselho());
        item.setCbo(dto.getCbo());
        item.setNumeroautorizacao(dto.getNumeroautorizacao());
        item.setOrigem(dto.getOrigem());
        item.setOrigemId(dto.getOrigemId());
        item.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? "PENDENTE" : dto.getStatus());
        item.setObservacao(dto.getObservacao());
        item.persist();

        recalcularTotal(conta);
        return item;
    }

    @Transactional
    public TissConta validarConta(int idtissconta) {
        TissConta conta = tissContaRepository.findByIdOptional((long) idtissconta)
                .orElseThrow(() -> new IllegalArgumentException("Conta TISS nao encontrada"));
        List<TissContaItem> itens = tissContaItemRepository.listarPorConta(idtissconta);
        if (itens.isEmpty()) {
            throw new IllegalArgumentException("Conta TISS sem itens para validar");
        }
        validarObrigatorio(conta.getCnesprestador(), "CNES prestador");
        if (temDadosConvenio(conta)) {
            validarObrigatorio(conta.getOperadora(), "Operadora");
            validarObrigatorio(conta.getRegistroans(), "Registro ANS da operadora");
            validarObrigatorio(conta.getNumerocarteira(), "Carteira do beneficiario");
        }
        conta.setStatus("VALIDADA");
        recalcularTotal(conta);
        return conta;
    }

    private void aplicarDto(TissConta conta, TissContaDto dto) {
        conta.setBam(dto.getBam());
        conta.setIdpaciente(dto.getIdpaciente());
        conta.setNomepaciente(dto.getNomepaciente());
        conta.setUnidade(dto.getUnidade() == null ? null : dto.getUnidade().toUpperCase());
        conta.setCompetencia(dto.getCompetencia());
        conta.setTipo(dto.getTipo());
        conta.setStatus(dto.getStatus());
        conta.setOperadora(dto.getOperadora());
        conta.setCnesprestador(cnesEfetivo(dto.getCnesprestador(), dto.getUnidade()));
        conta.setRegistroans(dto.getRegistroans());
        conta.setPlano(dto.getPlano());
        conta.setNumerocarteira(dto.getNumerocarteira());
        conta.setNumeroautorizacao(dto.getNumeroautorizacao());
        conta.setDataabertura(dto.getDataabertura());
        conta.setDatafechamento(dto.getDatafechamento());
        conta.setCriadoPor(dto.getCriadoPor());
        conta.setAtualizadoPor(dto.getAtualizadoPor());
        conta.setOrigem(dto.getOrigem());
        conta.setObservacao(dto.getObservacao());
        conta.setValorTotal(dto.getValorTotal());
    }

    private void recalcularTotal(TissConta conta) {
        BigDecimal total = tissContaItemRepository.listarPorConta(conta.getIdtissconta()).stream()
                .map(TissContaItem::getValorTotal)
                .filter(valor -> valor != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        conta.setValorTotal(total);
    }

    private List<String> pendenciasDaConta(TissConta conta) {
        List<String> erros = new ArrayList<>();
        if (tissContaItemRepository.listarPorConta(conta.getIdtissconta()).isEmpty()) {
            erros.add("Sem itens");
        }
        if (conta.getCnesprestador() == null || conta.getCnesprestador().isBlank()) {
            erros.add("CNES prestador");
        }
        if (temDadosConvenio(conta)) {
            if (conta.getOperadora() == null || conta.getOperadora().isBlank()) {
                erros.add("Operadora");
            }
            if (conta.getRegistroans() == null || conta.getRegistroans().isBlank()) {
                erros.add("Registro ANS da operadora");
            }
            if (conta.getNumerocarteira() == null || conta.getNumerocarteira().isBlank()) {
                erros.add("Carteira");
            }
        }
        return erros;
    }

    private boolean temDadosConvenio(TissConta conta) {
        return temTexto(conta.getOperadora())
                || temTexto(conta.getRegistroans())
                || temTexto(conta.getPlano())
                || temTexto(conta.getNumerocarteira())
                || temTexto(conta.getNumeroautorizacao());
    }

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private boolean isBpaIndividualizado(Afaturar itemAfaturar) {
        String tipoRegistro = itemAfaturar.getPa_tpreg();
        String origemInfo = itemAfaturar.getOrigeminfo();
        return "02".equals(tipoRegistro)
                || (origemInfo != null && "BPA".equalsIgnoreCase(origemInfo.trim()));
    }

    private List<String> pendenciasBpaIndividualizado(Afaturar itemAfaturar) {
        List<String> pendencias = new ArrayList<>();
        if (!isBpaIndividualizado(itemAfaturar)) {
            return pendencias;
        }
        if (itemAfaturar.getCnsdopciente() == null || itemAfaturar.getCnsdopciente().isBlank()) {
            pendencias.add("CNS paciente");
        }
        if (itemAfaturar.getCnsdoprofissional() == null || itemAfaturar.getCnsdoprofissional().isBlank()) {
            pendencias.add("CNS profissional");
        }
        if (itemAfaturar.getCbo() == null || itemAfaturar.getCbo().isBlank()) {
            pendencias.add("CBO");
        }
        if (itemAfaturar.getDtatendimento() == null || itemAfaturar.getDtatendimento().isBlank()) {
            pendencias.add("Data atendimento");
        }
        if (itemAfaturar.getSexo() == null || itemAfaturar.getSexo().isBlank()) {
            pendencias.add("Sexo");
        }
        if (itemAfaturar.getDtnascimento() == null || itemAfaturar.getDtnascimento().isBlank()) {
            pendencias.add("Nascimento");
        }
        return pendencias;
    }

    private boolean aplicarSeInformado(String valor, java.util.function.Consumer<String> setter) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        setter.accept(valor);
        return true;
    }

    private void aplicarCnesDaUnidade(TissContaDto dto, String unidade) {
        if (dto == null) {
            return;
        }
        dto.setCnesprestador(cnesEfetivo(dto.getCnesprestador(), unidade));
    }

    private String cnesEfetivo(String informado, String unidade) {
        if (informado != null && !informado.isBlank()) {
            return informado.trim();
        }
        if (unidade == null || unidade.isBlank()) {
            return "";
        }
        List<ConfiguracaoUnidade> configuracoes = configuracaoUnidadeRepository.filtarPorUnidadeSigla(unidade.trim());
        if (configuracoes.isEmpty()) {
            return "";
        }
        String cnes = configuracoes.get(0).getCnesDoHospital();
        return cnes == null ? "" : cnes.trim();
    }

    private BigDecimal valorOuZero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private void validarObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio");
        }
    }
}

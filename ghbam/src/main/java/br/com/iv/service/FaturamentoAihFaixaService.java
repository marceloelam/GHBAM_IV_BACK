package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.entity.FaturamentoAihFaixa;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Faixas de numeracao de AIH.
 *
 * O NU_AIH nao e gerado pelo hospital: o gestor concede um bloco e o SISAIH01 recusa
 * numero fora dele ou repetido. Este service e o unico lugar que entrega numero, para que
 * a regra de consumo (sequencial, sem pular, sem repetir) exista uma vez so.
 */
@ApplicationScoped
public class FaturamentoAihFaixaService {

    @Inject
    FaturamentoAuditService auditService;

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String digitos(String valor) {
        return texto(valor).replaceAll("\\D", "");
    }

    /** NU_AIH tem 13 posicoes e e numerico, entao zeros a esquerda fazem parte do numero. */
    private String normalizar(String valor) {
        String so = digitos(valor);
        if (so.isEmpty() || so.length() > 13) {
            throw new IllegalArgumentException("Numero de AIH deve ter ate 13 digitos: " + valor);
        }
        return String.format("%013d", Long.parseLong(so));
    }

    /**
     * Faixas da unidade. Sem unidade definida devolve vazio, nunca a lista inteira.
     *
     * Cada unidade cuida so do que e seu: faixa de numeracao e recurso concedido a um CNES
     * especifico, e ver a faixa de outra unidade e tao indevido quanto consumir dela. O
     * listAll que estava aqui vazava todas as faixas em qualquer chamada sem unidade -
     * inclusive a de um usuario cuja unidade viesse em branco na sessao.
     */
    public List<FaturamentoAihFaixa> listar(String unidade) {
        String uni = texto(unidade).toUpperCase();
        if (uni.isBlank() || "TODAS".equals(uni)) {
            return List.of();
        }
        return FaturamentoAihFaixa.list("upper(unidade) = ?1 order by ativa desc, idfaixa desc", uni);
    }

    @Transactional
    public FaturamentoAihFaixa criar(String unidade, String competencia, String inicial, String fim,
            String orgaoEmissor, String observacao, AuthenticatedUser user) {
        String uni = texto(unidade).toUpperCase();
        if (uni.isBlank() || "TODAS".equals(uni)) {
            throw new IllegalArgumentException("Informe a unidade da faixa.");
        }
        String de = normalizar(inicial);
        String ate = normalizar(fim);
        if (Long.parseLong(de) > Long.parseLong(ate)) {
            throw new IllegalArgumentException("O numero inicial nao pode ser maior que o final.");
        }
        String cmp = digitos(competencia);
        if (!cmp.isEmpty() && cmp.length() != 6) {
            throw new IllegalArgumentException("Competencia deve estar no formato AAAAMM.");
        }

        // Faixas sobrepostas na mesma unidade dariam numero repetido em contas diferentes,
        // e o SISAIH01 rejeita o lote inteiro por causa disso.
        for (FaturamentoAihFaixa outra : listar(uni)) {
            if (!outra.isAtiva()) {
                continue;
            }
            long a1 = Long.parseLong(outra.getNumeroInicial());
            long a2 = Long.parseLong(outra.getNumeroFinal());
            if (Long.parseLong(de) <= a2 && Long.parseLong(ate) >= a1) {
                throw new IllegalArgumentException("A faixa se sobrepoe a faixa ativa "
                        + outra.getNumeroInicial() + " a " + outra.getNumeroFinal() + ".");
            }
        }

        FaturamentoAihFaixa faixa = new FaturamentoAihFaixa();
        faixa.setUnidade(uni);
        faixa.setCompetencia(cmp.isEmpty() ? null : cmp);
        faixa.setNumeroInicial(de);
        faixa.setNumeroFinal(ate);
        faixa.setProximoNumero(de);
        faixa.setOrgaoEmissor(texto(orgaoEmissor));
        faixa.setObservacao(texto(observacao));
        faixa.setAtiva(true);
        faixa.setCriadoEm(LocalDateTime.now());
        faixa.setUsuarioCriador(user == null ? "" : user.matricula());
        faixa.persist();

        auditService.registrar(uni, "CADASTRAR_FAIXA_AIH", "SUCESSO", "FaturamentoAihFaixa",
                String.valueOf(faixa.getIdfaixa()), null, de + " a " + ate, null,
                "faixa=" + de + "-" + ate + " competencia=" + (cmp.isEmpty() ? "qualquer" : cmp), user);
        return faixa;
    }

    @Transactional
    public FaturamentoAihFaixa alternarAtiva(int idfaixa, AuthenticatedUser user) {
        FaturamentoAihFaixa faixa = FaturamentoAihFaixa.findById(idfaixa);
        if (faixa == null) {
            throw new IllegalArgumentException("Faixa nao encontrada.");
        }
        faixa.setAtiva(!faixa.isAtiva());
        auditService.registrar(faixa.getUnidade(), "ALTERAR_FAIXA_AIH", "SUCESSO", "FaturamentoAihFaixa",
                String.valueOf(idfaixa), null, faixa.isAtiva() ? "ATIVA" : "INATIVA", null,
                "faixa " + faixa.getNumeroInicial() + "-" + faixa.getNumeroFinal(), user);
        return faixa;
    }

    /**
     * Reserva o proximo numero disponivel e o devolve ja consumido.
     *
     * Transacao propria e curta de proposito: o numero e recurso escasso e nao pode ficar
     * preso numa transacao longa de fechamento. Devolve null quando nao ha faixa - a conta
     * fecha sem numero e recebe um depois, quando o gestor conceder o bloco.
     */
    @Transactional
    public String reservarNumero(String unidade, String competencia, AuthenticatedUser user) {
        String uni = texto(unidade).toUpperCase();
        String cmp = digitos(competencia);

        // Faixa da competencia primeiro; a faixa geral (competencia nula) e o fallback.
        List<FaturamentoAihFaixa> candidatas = FaturamentoAihFaixa.list(
                "ativa = true and upper(unidade) = ?1 and (competencia is null or competencia = ?2)"
                        + " order by case when competencia is null then 1 else 0 end, idfaixa",
                uni, cmp);

        for (FaturamentoAihFaixa faixa : candidatas) {
            long proximo = Long.parseLong(faixa.getProximoNumero());
            long fim = Long.parseLong(faixa.getNumeroFinal());
            if (proximo > fim) {
                // Esgotada: desativa para nao ser reavaliada a cada conta.
                faixa.setAtiva(false);
                auditService.registrar(uni, "FAIXA_AIH_ESGOTADA", "SUCESSO", "FaturamentoAihFaixa",
                        String.valueOf(faixa.getIdfaixa()), null, faixa.getNumeroFinal(), null,
                        "faixa " + faixa.getNumeroInicial() + "-" + faixa.getNumeroFinal() + " esgotada", user);
                continue;
            }
            faixa.setProximoNumero(String.format("%013d", proximo + 1));
            return String.format("%013d", proximo);
        }
        return null;
    }

    /** Quanto ainda ha para consumir, por unidade. Alimenta o aviso na tela. */
    public Map<String, Object> situacao(String unidade) {
        long disponiveis = 0;
        int ativas = 0;
        for (FaturamentoAihFaixa faixa : listar(unidade)) {
            if (!faixa.isAtiva()) {
                continue;
            }
            ativas++;
            disponiveis += faixa.getDisponiveis();
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("faixasAtivas", ativas);
        resultado.put("numerosDisponiveis", disponiveis);
        return resultado;
    }
}

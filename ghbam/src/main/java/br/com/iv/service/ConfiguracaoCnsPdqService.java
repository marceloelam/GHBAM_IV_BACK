package br.com.iv.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.com.iv.dto.LogDto;
import br.com.iv.entity.ConfiguracaoCnsPdq;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Toggle POR UNIDADE da consulta oficial do CNS/PDQ - ver
 * {@link ConsultaCnsService#tentarApiOficial}. Controlado pela tela /base2/integracao-sus:
 * enquanto a unidade nao estiver ativada, ela usa so o scraping do CNES, exatamente como
 * antes desta integracao existir. Por unidade porque a credencial/certificado tambem e
 * por unidade - ativar globalmente nao teria efeito real numa unidade sem credencial.
 */
@ApplicationScoped
public class ConfiguracaoCnsPdqService {

    @Inject
    LogService logService;

    // A /recepcao consulta isto a cada CPF digitado - cache curto para nao bater no banco
    // a cada tecla, mas curto o bastante para o toggle da tela surtir efeito quase na hora.
    private final ConcurrentHashMap<String, CacheAtivo> cache = new ConcurrentHashMap<>();

    private record CacheAtivo(boolean ativo, Instant expiraEm) {
    }

    public boolean ativo(String unidade) {
        String chave = chave(unidade);
        CacheAtivo atual = cache.get(chave);
        if (atual != null && atual.expiraEm().isAfter(Instant.now())) {
            return atual.ativo();
        }
        boolean valor = buscarEntidade(chave).map(ConfiguracaoCnsPdq::getAtivo).orElse(Boolean.FALSE);
        cache.put(chave, new CacheAtivo(valor, Instant.now().plusSeconds(30)));
        return valor;
    }

    @Transactional
    public boolean definir(String unidade, boolean ativo, AuthenticatedUser user) {
        String chave = chave(unidade);
        if (chave.isBlank()) {
            throw new IllegalArgumentException("Informe a unidade para ativar/desativar a consulta oficial.");
        }
        ConfiguracaoCnsPdq configuracao = buscarEntidade(chave).orElseGet(() -> {
            ConfiguracaoCnsPdq nova = new ConfiguracaoCnsPdq();
            nova.setUnidade(chave);
            return nova;
        });
        configuracao.setAtivo(ativo);
        configuracao.setAtualizadopor(user == null ? "" : user.matricula());
        configuracao.setAtualizadoem(LocalDateTime.now());
        configuracao.persist();
        cache.put(chave, new CacheAtivo(ativo, Instant.now().plusSeconds(30)));

        registrarLog(chave, ativo, user);
        return ativo;
    }

    /**
     * Atalho para quando a maioria das unidades ja foi validada e nao faz mais sentido
     * religar uma por uma - grava uma linha por unidade da lista, com o mesmo efeito de
     * chamar {@link #definir} repetidas vezes.
     */
    @Transactional
    public Map<String, Boolean> definirParaTodas(List<String> unidades, boolean ativo, AuthenticatedUser user) {
        Map<String, Boolean> resultado = new LinkedHashMap<>();
        for (String unidade : unidades) {
            resultado.put(chave(unidade), definir(unidade, ativo, user));
        }
        return resultado;
    }

    public Map<String, Boolean> statusPorUnidade(List<String> unidades) {
        Map<String, Boolean> resultado = new LinkedHashMap<>();
        for (String unidade : unidades) {
            resultado.put(chave(unidade), ativo(unidade));
        }
        return resultado;
    }

    private void registrarLog(String unidade, boolean ativo, AuthenticatedUser user) {
        LogDto log = new LogDto();
        log.setData(java.time.LocalDate.now());
        log.setHora(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        log.setUnidade(unidade);
        log.setEvento("INTEGRACAO_SUS CNS_PDQ_OFICIAL " + (ativo ? "ATIVADO" : "DESATIVADO")
                + " - Consulta oficial do CNS/PDQ " + (ativo ? "ligada" : "desligada") + " para a unidade " + unidade
                + ", por " + (user == null ? "sistema" : user.nome()) + ".");
        logService.salvarLogAsync(log);
    }

    private String chave(String unidade) {
        return unidade == null ? "" : unidade.trim().toUpperCase();
    }

    private Optional<ConfiguracaoCnsPdq> buscarEntidade(String chave) {
        return ConfiguracaoCnsPdq.<ConfiguracaoCnsPdq>find("unidade", chave).firstResultOptional();
    }
}

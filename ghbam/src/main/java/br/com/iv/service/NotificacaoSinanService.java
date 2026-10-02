package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.iv.dto.NotificacaoSinanDto;
import br.com.iv.entity.NotificacaoSinan;
import br.com.iv.entity.NotificacaoSinanHistorico;
import br.com.iv.repository.NotificacaoSinanHistoricoRepository;
import br.com.iv.repository.NotificacaoSinanRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class NotificacaoSinanService {
    private static final Set<String> STATUS_FORMULARIO = Set.of("RASCUNHO", "PRONTO", "ENCAMINHADO");
    private static final Set<String> PERIODICIDADES = Set.of("IMEDIATA", "SEMANAL");

    @Inject NotificacaoSinanRepository repository;
    @Inject NotificacaoSinanHistoricoRepository historicoRepository;

    public List<NotificacaoSinan> listarPorUnidade(String unidade) {
        return repository.listarPorUnidade(normalizarUnidade(unidade));
    }

    public List<Map<String, Object>> listarPendenciasPorUnidade(String unidade) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (NotificacaoSinan item : repository.listarPendenciasPorUnidade(normalizarUnidade(unidade))) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", item.getId());
            dto.put("tipo", item.getTipo());
            dto.put("unidade", item.getUnidade());
            dto.put("nomePaciente", item.getNomePaciente());
            dto.put("dataNotificacao", item.getDataNotificacao());
            dto.put("status", item.getStatus());
            dto.put("periodicidade", item.getPeriodicidade());
            dto.put("prazoNotificacao", item.getPrazoNotificacao());
            dto.put("encaminhadoEm", item.getEncaminhadoEm());
            dto.put("encaminhadoPor", item.getEncaminhadoPor());
            dto.put("recebidoEm", item.getRecebidoEm());
            dto.put("recebidoPor", item.getRecebidoPor());
            dto.put("referenciaTransmissao", item.getReferenciaTransmissao());
            resultado.add(dto);
        }
        return resultado;
    }

    @Transactional
    public NotificacaoSinan salvar(NotificacaoSinanDto dto) {
        String status = normalizarStatusFormulario(dto.getStatus());
        NotificacaoSinan item = new NotificacaoSinan();
        item.setTipo(dto.getTipo().trim());
        item.setIdpaciente(dto.getIdpaciente());
        item.setIdbam(dto.getIdbam());
        item.setUnidade(normalizarUnidade(dto.getUnidade()));
        item.setNomePaciente(limpar(dto.getNomePaciente()));
        item.setDataNotificacao(dto.getDataNotificacao());
        item.setDados(dto.getDados());
        item.setCriadoPor(limpar(dto.getCriadoPor()));
        item.setCriadoPorMatricula(limpar(dto.getCriadoPorMatricula()));
        item.setStatus(status);
        configurarEncaminhamento(item, dto, status);
        repository.persist(item);
        repository.flush();
        registrarHistorico(item, "CRIACAO", null, status, dto.getCriadoPor(),
                "Ficha criada" + ("ENCAMINHADO".equals(status) ? " e encaminhada para a Vigilancia Epidemiologica." : "."));
        return item;
    }

    @Transactional
    public NotificacaoSinan atualizar(Long id, NotificacaoSinanDto dto) {
        NotificacaoSinan item = repository.findById(id);
        if (item == null) return null;
        if ("TRANSMITIDO".equals(item.getStatus())) {
            throw new IllegalStateException("Uma ficha ja transmitida nao pode ser alterada.");
        }

        String statusAnterior = item.getStatus();
        String statusSolicitado = normalizarStatusFormulario(dto.getStatus());
        validarTransicaoFormulario(statusAnterior, statusSolicitado);

        if (dto.getIdpaciente() != null) item.setIdpaciente(dto.getIdpaciente());
        if (dto.getIdbam() != null) item.setIdbam(dto.getIdbam());
        if (dto.getNomePaciente() != null) item.setNomePaciente(limpar(dto.getNomePaciente()));
        if (dto.getDataNotificacao() != null) item.setDataNotificacao(dto.getDataNotificacao());
        if (dto.getDados() != null) item.setDados(dto.getDados());
        item.setStatus(statusSolicitado);
        configurarEncaminhamento(item, dto, statusSolicitado);

        String acao = statusAnterior.equals(statusSolicitado) ? "ATUALIZACAO" : "ENCAMINHAMENTO";
        registrarHistorico(item, acao, statusAnterior, statusSolicitado, dto.getCriadoPor(),
                statusAnterior.equals(statusSolicitado) ? "Dados da ficha atualizados." : "Ficha encaminhada para a Vigilancia Epidemiologica.");
        return item;
    }

    @Transactional
    public NotificacaoSinan avancarFluxo(Long id, String acao, String usuario, String referencia, String observacao) {
        NotificacaoSinan item = repository.findById(id);
        if (item == null) return null;

        String comando = limpar(acao).toUpperCase();
        String statusAnterior = item.getStatus();
        LocalDateTime agora = LocalDateTime.now();

        if ("RECEBER".equals(comando)) {
            if (!Set.of("PRONTO", "ENCAMINHADO").contains(statusAnterior)) {
                throw new IllegalStateException("Somente fichas encaminhadas podem ter o recebimento confirmado.");
            }
            item.setStatus("RECEBIDO");
            item.setRecebidoEm(agora);
            item.setRecebidoPor(limitar(limpar(usuario), 120));
        } else if ("TRANSMITIR".equals(comando)) {
            if (!"RECEBIDO".equals(statusAnterior)) {
                throw new IllegalStateException("Confirme o recebimento antes de registrar a transmissao.");
            }
            String protocolo = limpar(referencia);
            if (protocolo.isBlank()) {
                throw new IllegalArgumentException("Informe o protocolo ou a referencia da transmissao oficial.");
            }
            if (protocolo.length() > 160) {
                throw new IllegalArgumentException("O protocolo ou a referencia da transmissao deve ter no maximo 160 caracteres.");
            }
            item.setStatus("TRANSMITIDO");
            item.setTransmitidoEm(agora);
            item.setTransmitidoPor(limitar(limpar(usuario), 120));
            item.setReferenciaTransmissao(protocolo);
        } else {
            throw new IllegalArgumentException("Acao de fluxo SINAN invalida.");
        }

        registrarHistorico(item, comando, statusAnterior, item.getStatus(), usuario, limpar(observacao));
        return item;
    }

    @Transactional
    public boolean excluir(Long id) {
        NotificacaoSinan item = repository.findById(id);
        if (item == null) return false;
        if (!"RASCUNHO".equals(item.getStatus())) {
            throw new IllegalStateException("Somente fichas em rascunho podem ser excluidas.");
        }
        registrarHistorico(item, "EXCLUSAO", item.getStatus(), item.getStatus(), "", "Rascunho excluido.");
        return repository.deleteById(id);
    }

    private void configurarEncaminhamento(NotificacaoSinan item, NotificacaoSinanDto dto, String status) {
        if (!Set.of("PRONTO", "ENCAMINHADO").contains(status)) return;
        String periodicidade = limpar(dto.getPeriodicidade()).toUpperCase();
        if (!PERIODICIDADES.contains(periodicidade)) {
            throw new IllegalArgumentException("Informe se a notificacao e IMEDIATA ou SEMANAL.");
        }
        item.setPeriodicidade(periodicidade);
        if (item.getEncaminhadoEm() == null) item.setEncaminhadoEm(LocalDateTime.now());
        item.setEncaminhadoPor(limpar(dto.getCriadoPor()));
        item.setPrazoNotificacao("IMEDIATA".equals(periodicidade)
                ? item.getEncaminhadoEm().plusHours(24)
                : item.getEncaminhadoEm().plusDays(7));
    }

    private void validarTransicaoFormulario(String atual, String solicitado) {
        if (Set.of("RECEBIDO", "TRANSMITIDO").contains(atual) && !atual.equals(solicitado)) {
            throw new IllegalStateException("O status desta ficha so pode ser alterado pela Vigilancia Epidemiologica.");
        }
        if ("ENCAMINHADO".equals(atual) && "RASCUNHO".equals(solicitado)) {
            throw new IllegalStateException("Uma ficha encaminhada nao pode voltar para rascunho.");
        }
    }

    private String normalizarStatusFormulario(String status) {
        String valor = limpar(status).toUpperCase();
        if (valor.isBlank()) return "RASCUNHO";
        if (!STATUS_FORMULARIO.contains(valor)) {
            throw new IllegalArgumentException("Status de ficha SINAN invalido.");
        }
        return valor;
    }

    private void registrarHistorico(NotificacaoSinan item, String acao, String anterior, String novo, String usuario, String observacao) {
        NotificacaoSinanHistorico historico = new NotificacaoSinanHistorico();
        historico.setNotificacaoId(item.getId());
        historico.setAcao(acao);
        historico.setStatusAnterior(anterior);
        historico.setStatusNovo(novo);
        historico.setUsuario(limitar(limpar(usuario), 120));
        historico.setObservacao(limitar(limpar(observacao), 500));
        historicoRepository.persist(historico);
    }

    private String normalizarUnidade(String valor) {
        return limpar(valor).split(":", 2)[0].trim();
    }

    private String limitar(String valor, int tamanho) {
        return valor.length() <= tamanho ? valor : valor.substring(0, tamanho);
    }

    private String limpar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}

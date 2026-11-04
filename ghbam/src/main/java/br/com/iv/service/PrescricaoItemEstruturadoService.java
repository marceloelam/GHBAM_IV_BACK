package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import br.com.iv.dto.PrescricaoItemEstruturadoDto;
import br.com.iv.entity.PrescricaoItemEstruturado;
import br.com.iv.repository.PrescricaoItemEstruturadoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PrescricaoItemEstruturadoService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    PrescricaoItemEstruturadoRepository repository;

    @Inject
    AssinaturaClinicaService assinaturaClinicaService;

    @Inject
    PedidoMedico2Service pedidoMedico2Service;

    @Inject
    jakarta.persistence.EntityManager em;

    @Inject
    AuditoriaAcaoServidor auditoria;

    /** Marca em pedidomedico2.obs3 que liga o pedido do setor ao item estruturado que o gerou. */
    static final String MARCA_ITEM = "PRESC_ESTR:";

    public List<PrescricaoItemEstruturado> listarPorBam(int idbam, boolean apenasAtivos) {
        return apenasAtivos ? repository.listarAtivosPorBam(idbam) : repository.listarPorBam(idbam);
    }

    public List<PrescricaoItemEstruturado> listarPorPrescricao(int idprescricao) {
        return repository.listarPorPrescricao(idprescricao);
    }

    @Transactional
    public PrescricaoItemEstruturado salvar(PrescricaoItemEstruturadoDto dto) {
        PrescricaoItemEstruturado item = new PrescricaoItemEstruturado();
        preencher(item, dto, true);
        item.persist();
        gerarPedidoDoSetor(item);
        return item;
    }

    @Transactional
    public PrescricaoItemEstruturado atualizar(int id, PrescricaoItemEstruturadoDto dto) {
        PrescricaoItemEstruturado item = repository.findById(id);
        if (item == null) {
            return null;
        }
        validarItemNaoAssinado(item);
        if ("SUSPENSO".equalsIgnoreCase(item.getStatus())) {
            return item;
        }
        preencher(item, dto, false);
        retirarPedidoPendenteDoSetor(item.getIditem());
        gerarPedidoDoSetor(item);
        return item;
    }

    @Transactional
    public PrescricaoItemEstruturado suspender(int id, PrescricaoItemEstruturadoDto dto) {
        PrescricaoItemEstruturado item = repository.findById(id);
        if (item == null) {
            return null;
        }
        validarItemNaoAssinado(item);
        item.setStatus("SUSPENSO");
        item.setSuspensoPor(safe(dto.getSuspensoPor()));
        item.setSuspensoPorMatricula(safe(dto.getSuspensoPorMatricula()));
        item.setMotivoSuspensao(safe(dto.getMotivoSuspensao()));
        item.setAtualizadoPor(safe(dto.getAtualizadoPor()));
        item.setAtualizadoPorMatricula(safe(dto.getAtualizadoPorMatricula()));
        item.setSuspensoEm(LocalDateTime.now());
        retirarPedidoPendenteDoSetor(item.getIditem());
        return item;
    }

    /**
     * Paciente da PA (nao internado) e medicado pelo SPA/Maternidade/Pediatria, cuja fila le pedidomedico2:
     * o medicamento estruturado vira um pedido no setor de execucao escolhido. Internado nao gera pedido;
     * segue no ciclo de medicacao da internacao, para o mesmo item nao ser executado por dois caminhos.
     */
    private void gerarPedidoDoSetor(PrescricaoItemEstruturado item) {
        if (item == null || item.getIdbam() <= 0 || !"MEDICAMENTO".equalsIgnoreCase(item.getTipoItem())) return;
        if (!"ATIVO".equalsIgnoreCase(item.getStatus()) || pacienteInternado(item.getIdbam())) return;

        String setor = item.getSetorExecucao() == null ? "MEDICACAO" : item.getSetorExecucao();
        // Nome + posologia no proprio item ("Aciclovir · 800 mg · EV · de 8/8 horas · por 14 dias"): a tela da
        // medicacao mostra a obs como texto livre, e posologia ali virava linha solta sem o medicamento.
        String medicamento = safe(item.getMedicamento());
        String descricao = String.join(" · ", java.util.stream.Stream.of(
                        medicamento, safe(item.getDose()), safe(item.getVia()), safe(item.getFrequencia()), safe(item.getDuracao()))
                .filter(s -> !s.isBlank()).toList());
        String observacao = safe(item.getObservacao());
        String orientacao = observacao.isBlank() ? "" : medicamento + ": " + observacao;

        br.com.iv.dto.PedidoMedico2Dto pedido = new br.com.iv.dto.PedidoMedico2Dto();
        pedido.setPedido(descricao.length() > 2000 ? descricao.substring(0, 2000) : descricao);
        pedido.setLocal(setor);
        pedido.setReal(setor);
        pedido.setMedico(safe(item.getPrescritoPor()));
        pedido.setCrm("");
        pedido.setBam(item.getIdbam());
        String obs = (orientacao.isBlank() ? "" : orientacao + "  ") + "Pedido realizado por Dr(a). " + safe(item.getPrescritoPor()) + "  ";
        pedido.setObs(obs.length() > 1000 ? obs.substring(0, 1000) : obs);
        pedido.setObs2("");
        pedido.setObs3(MARCA_ITEM + item.getIditem());
        pedido.setConfirmado(true);
        LocalDateTime agora = LocalDateTime.now();
        pedido.setDt(agora.toLocalDate().toString());
        pedido.setHr(String.format("%02d:%02d", agora.getHour(), agora.getMinute()));
        pedido.setUnidade(safe(item.getUnidade()));
        br.com.iv.entity.PedidoMedico2 salvo = pedidoMedico2Service.salvarPedidoMedico2(pedido);
        auditoria.registrar("CRIAR", "PRESCRICAO_ESTRUTURADA", "PEDIDO_MEDICO_SETOR", item.getIdbam(),
                salvo == null ? null : salvo.getIdpedidomedico(),
                "item=" + item.getIditem() + "; setor=" + setor + "; medicamento=" + descricao, item.getUnidade());
    }

    /** Retira o pedido do setor ainda nao executado; o ja executado fica como registro clinico. */
    private void retirarPedidoPendenteDoSetor(int iditem) {
        if (iditem <= 0) return;
        int retirados = em.createNativeQuery("delete from pedidomedico2 where obs3 = :marca and coalesce(efetuado, false) = false")
                .setParameter("marca", MARCA_ITEM + iditem)
                .executeUpdate();
        if (retirados > 0) {
            PrescricaoItemEstruturado item = repository.findById(iditem);
            auditoria.registrar("EXCLUIR", "PRESCRICAO_ESTRUTURADA", "PEDIDO_MEDICO_SETOR",
                    item == null ? 0 : item.getIdbam(), iditem,
                    "pedido(s) pendente(s) retirado(s) do setor=" + retirados + "; motivo=item suspenso ou alterado",
                    item == null ? null : item.getUnidade());
        }
    }

    private boolean pacienteInternado(int idbam) {
        return !em.createNativeQuery(
                        "select 1 from novointernado where nbam = :b and coalesce(alta, false) = false limit 1")
                .setParameter("b", idbam)
                .getResultList().isEmpty();
    }


    private void preencher(PrescricaoItemEstruturado item, PrescricaoItemEstruturadoDto dto, boolean novo) {
        item.setIdprescricao(dto.getIdprescricao());
        item.setIdpaciente(dto.getIdpaciente());
        item.setIdbam(dto.getIdbam());
        item.setUnidade(safe(dto.getUnidade()));
        item.setStatus(safeDefault(dto.getStatus(), "ATIVO").toUpperCase());
        item.setTipoItem(safeDefault(dto.getTipoItem(), "MEDICAMENTO").toUpperCase());
        item.setMedicamento(safe(dto.getMedicamento()));
        item.setDose(safe(dto.getDose()));
        item.setVia(safe(dto.getVia()));
        item.setFrequencia(safe(dto.getFrequencia()));
        item.setDuracao(safe(dto.getDuracao()));
        item.setObservacao(safeText(dto.getObservacao()));
        item.setSetorExecucao(setorExecucao(dto.getSetorExecucao()));
        if (novo) {
            item.setPrescritoPor(safe(dto.getPrescritoPor()));
            item.setPrescritoPorMatricula(safe(dto.getPrescritoPorMatricula()));
        }
        item.setAtualizadoPor(safe(dto.getAtualizadoPor()));
        item.setAtualizadoPorMatricula(safe(dto.getAtualizadoPorMatricula()));
    }

    /** So os tres setores de medicacao da PA; qualquer outro valor fica vazio (= SPA). */
    private String setorExecucao(String value) {
        String setor = safe(value).toUpperCase();
        return switch (setor) {
            case "MEDICACAO", "MEDICACAO2", "MEDICACAO3" -> setor;
            default -> null;
        };
    }

    private String safeDefault(String value, String fallback) {
        String safe = safe(value);
        return safe.isBlank() ? fallback : safe;
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void validarItemNaoAssinado(PrescricaoItemEstruturado item) {
        if (item == null || item.getIdbam() <= 0) {
            return;
        }
        var assinatura = assinaturaClinicaService.assinaturaAtivaPorBam(
                AssinaturaClinicaService.PRESCRICAO_ESTRUTURADA_BAM,
                item.getIdbam());
        if (assinatura == null || assinatura.getAssinadoEm() == null || item.getCriadoEm() == null) {
            return;
        }
        if (!item.getCriadoEm().isAfter(assinatura.getAssinadoEm())) {
            throw new IllegalStateException("Item ja coberto por prescricao assinada. Use adendo para registrar correcoes.");
        }
    }
}

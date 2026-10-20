package br.com.iv.cdss.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import br.com.iv.cdss.dto.ReconciliacaoDecisaoRequest;
import br.com.iv.cdss.entity.CdssAlertaOverride;
import br.com.iv.cdss.repository.CdssAlertaOverrideRepository;
import br.com.iv.entity.PrescricaoItemEstruturado;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Medicamento;
import br.com.iv.repository.PrescricaoItemEstruturadoRepository;
import br.com.iv.repository.ProntuarioClinicoItemRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Reconciliacao medicamentosa: cada medicamento de uso continuo do paciente
 * (prontuario_clinico_item tipo MEDICAMENTO_CONTINUO) recebe a decisao do
 * prescritor neste BAM - manter, suspender ou substituir. A decisao fica em
 * cdss_alerta_override (codigo RECONCILIACAO_{iditem}), mesma trilha auditada
 * do CDSS; a mais recente vale e o historico anterior e preservado.
 */
@ApplicationScoped
public class ReconciliacaoMedicamentosService {
    public static final String PREFIXO_CODIGO = "RECONCILIACAO_";
    public static final String MODULO = "RECONCILIACAO";
    public static final String TIPO_ITEM = "MEDICAMENTO_CONTINUO";
    static final Set<String> DECISOES = Set.of("MANTER", "SUSPENDER", "SUBSTITUIR");

    @Inject
    ProntuarioClinicoItemRepository prontuarioRepository;

    @Inject
    PrescricaoItemEstruturadoRepository prescricaoRepository;

    @Inject
    CdssAlertaOverrideRepository overrideRepository;

    @Inject
    BaseConhecimentoFarmaceutico base;

    public List<Map<String, Object>> listar(int idbam, int idpaciente) {
        List<ProntuarioClinicoItem> continuos = medicamentosContinuos(idpaciente);
        if (continuos.isEmpty()) {
            return List.of();
        }
        List<String> prescritos = prescricaoRepository.listarAtivosPorBam(idbam).stream()
                .map(PrescricaoItemEstruturado::getMedicamento)
                .filter(v -> v != null && !v.isBlank())
                .toList();

        Map<String, CdssAlertaOverride> decisoes = new HashMap<>();
        for (CdssAlertaOverride o : overrideRepository.listarPorBam(idbam)) {
            if (MODULO.equals(o.getModuloOrigem()) && o.getAlertaCodigo() != null) {
                decisoes.putIfAbsent(o.getAlertaCodigo(), o); // lista vem da mais recente
            }
        }

        List<Map<String, Object>> lista = new ArrayList<>();
        for (ProntuarioClinicoItem item : continuos) {
            String nome = nomeItem(item);
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("iditem", item.getIditem());
            linha.put("medicamento", nome);
            linha.put("detalhe", item.getDescricao());
            linha.put("naPrescricao", constaNaPrescricao(nome, prescritos));
            CdssAlertaOverride decisao = decisoes.get(PREFIXO_CODIGO + item.getIditem());
            linha.put("decisao", decisao == null ? null : decisao.getAlertaSeveridade());
            linha.put("observacao", decisao == null ? null : decisao.getJustificativa());
            linha.put("decididoPor", decisao == null ? null : decisao.getUsuarioNome());
            linha.put("decididoEm", decisao == null ? null : decisao.getCriadoEm());
            lista.add(linha);
        }
        return lista;
    }

    @Transactional
    public Map<String, Object> decidir(ReconciliacaoDecisaoRequest request, AuthenticatedUser usuario) {
        if (usuario == null || usuario.matricula() == null || usuario.matricula().isBlank()) {
            throw new SecurityException("Sessao sem profissional identificado.");
        }
        if (request == null || request.getIdbam() == null || request.getIdpaciente() == null || request.getIditem() == null) {
            throw new IllegalArgumentException("Informe BAM, paciente e medicamento.");
        }
        String decisao = request.getDecisao() == null ? "" : request.getDecisao().trim().toUpperCase(Locale.ROOT);
        if (!DECISOES.contains(decisao)) {
            throw new IllegalArgumentException("Decisao deve ser MANTER, SUSPENDER ou SUBSTITUIR.");
        }
        String observacao = request.getObservacao() == null ? "" : request.getObservacao().trim();
        if (!"MANTER".equals(decisao) && observacao.length() < 5) {
            throw new IllegalArgumentException("Informe o motivo da suspensao ou o substituto.");
        }
        // Revalida no servidor: o item precisa ser medicamento continuo deste paciente.
        ProntuarioClinicoItem item = medicamentosContinuos(request.getIdpaciente()).stream()
                .filter(i -> i.getIditem() == request.getIditem())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Medicamento de uso continuo nao encontrado para este paciente."));

        CdssAlertaOverride registro = new CdssAlertaOverride();
        registro.setIdpaciente(request.getIdpaciente());
        registro.setIdbam(request.getIdbam());
        registro.setUnidade(usuario.unidade());
        registro.setAlertaCodigo(PREFIXO_CODIGO + item.getIditem());
        registro.setAlertaSeveridade(decisao);
        registro.setJustificativa(observacao.isBlank() ? nomeItem(item) : nomeItem(item) + " - " + observacao);
        registro.setModuloOrigem(MODULO);
        registro.setUsuarioNome(usuario.nome());
        registro.setUsuarioMatricula(usuario.matricula());
        registro.persist();

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("iditem", item.getIditem());
        resposta.put("decisao", decisao);
        resposta.put("decididoPor", registro.getUsuarioNome());
        resposta.put("decididoEm", registro.getCriadoEm());
        return resposta;
    }

    private List<ProntuarioClinicoItem> medicamentosContinuos(int idpaciente) {
        return prontuarioRepository.listarPorPacienteTipo(idpaciente, TIPO_ITEM, "TODAS").stream()
                .filter(i -> "ATIVO".equalsIgnoreCase(i.getStatus()))
                .toList();
    }

    private static String nomeItem(ProntuarioClinicoItem item) {
        String titulo = item.getTitulo();
        return titulo != null && !titulo.isBlank() ? titulo.trim() : (item.getDescricao() == null ? "" : item.getDescricao().trim());
    }

    boolean constaNaPrescricao(String medicamentoContinuo, List<String> prescritos) {
        String alvo = BaseConhecimentoFarmaceutico.normalizar(medicamentoContinuo);
        if (alvo.isBlank()) return false;
        Medicamento med = base == null ? null : base.identificar(medicamentoContinuo);
        String primeiraPalavra = alvo.split(" ")[0];
        for (String prescrito : prescritos) {
            if (med != null && base.identificar(prescrito) == med) return true;
            String p = BaseConhecimentoFarmaceutico.normalizar(prescrito);
            if (primeiraPalavra.length() >= 4 && p.contains(primeiraPalavra)) return true;
        }
        return false;
    }
}

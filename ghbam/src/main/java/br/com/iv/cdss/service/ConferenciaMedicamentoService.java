package br.com.iv.cdss.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.cdss.dto.CdssAlertDto;
import br.com.iv.cdss.dto.CdssAvaliacaoPrescricaoRequest;
import br.com.iv.cdss.dto.ConferenciaMedicamentoRequest;
import br.com.iv.entity.PharmaciaProduto;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico.Medicamento;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Conferencia do medicamento certo na checagem a beira-leito: a embalagem lida
 * (pharmacia_produto.codigobarras da unidade da sessao) consta na prescricao do
 * paciente e nao bate com alergia registrada? A prescricao legada e texto livre
 * e a checagem e por horario, entao a pergunta e "consta", nao "e o item X".
 */
@ApplicationScoped
public class ConferenciaMedicamentoService {
    public static final String CONFERE = "CONFERE";
    public static final String NAO_CONSTA = "NAO_CONSTA";
    public static final String NAO_CADASTRADO = "NAO_CADASTRADO";

    @Inject
    BaseConhecimentoFarmaceutico base;

    @Inject
    CdssService cdssService;

    public Map<String, Object> conferir(ConferenciaMedicamentoRequest request, AuthenticatedUser usuario) {
        String codigo = request == null || request.getCodigoBarras() == null ? "" : request.getCodigoBarras().trim();
        if (codigo.isBlank()) {
            throw new IllegalArgumentException("Leia o codigo de barras da embalagem.");
        }
        String unidade = usuario == null ? null : usuario.unidade();
        if (unidade == null || unidade.isBlank()) {
            throw new SecurityException("Sessao sem unidade identificada.");
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        PharmaciaProduto produto = PharmaciaProduto
                .<PharmaciaProduto>find("codigoBarras = ?1 and unidadeSigla = ?2", codigo, unidade)
                .firstResult();
        if (produto == null) {
            resposta.put("resultado", NAO_CADASTRADO);
            resposta.put("mensagem", "Codigo de barras nao cadastrado no catalogo da farmacia desta unidade.");
            resposta.put("alertas", List.of());
            return resposta;
        }

        String nomeProduto = primeiro(produto.getNomeComercial(), produto.getNomeGenerico());
        String principio = primeiro(produto.getPrincipioAtivo(), produto.getNomeGenerico(), produto.getNomeComercial());
        Medicamento med = base == null ? null : base.identificar(principio);
        if (med == null && base != null) med = base.identificar(nomeProduto);

        boolean consta = constaNaPrescricao(med, principio, nomeProduto, request.getTextoPrescricao());
        resposta.put("resultado", consta ? CONFERE : NAO_CONSTA);
        resposta.put("produto", nomeProduto);
        resposta.put("principioAtivo", principio);
        resposta.put("mensagem", consta
                ? "Medicamento consta na prescricao do paciente."
                : "ATENCAO: " + principio + " nao foi encontrado na prescricao deste paciente.");
        resposta.put("alertas", alertasDeAlergia(request, principio));
        return resposta;
    }

    static boolean constaNaPrescricao(Medicamento med, String principio, String nomeProduto, String textoPrescricao) {
        String prescricao = BaseConhecimentoFarmaceutico.normalizar(textoPrescricao);
        if (prescricao.isBlank()) return false;
        List<String> termos = new ArrayList<>();
        if (med != null) termos.addAll(med.termos);
        termos.add(BaseConhecimentoFarmaceutico.normalizar(principio));
        termos.add(BaseConhecimentoFarmaceutico.normalizar(nomeProduto));
        String texto = " " + prescricao + " ";
        return termos.stream()
                .filter(t -> t != null && t.length() >= 4)
                .anyMatch(t -> texto.contains(" " + t));
    }

    // Reaproveita o CDSS da prescricao (alergia direta, por sinonimo e cruzada).
    private List<CdssAlertDto> alertasDeAlergia(ConferenciaMedicamentoRequest request, String principio) {
        if (cdssService == null || request.getIdpaciente() == null || request.getIdpaciente() <= 0) {
            return List.of();
        }
        CdssAvaliacaoPrescricaoRequest avaliacao = new CdssAvaliacaoPrescricaoRequest();
        avaliacao.setIdpaciente(request.getIdpaciente());
        avaliacao.setMedicamento(principio);
        try {
            return cdssService.avaliarPrescricao(avaliacao).getAlertas().stream()
                    .filter(a -> "ALERGIA".equals(a.getCategoria()))
                    .toList();
        } catch (RuntimeException e) {
            System.err.println("[ConferenciaMedicamento] Falha ao checar alergia: " + e.getMessage());
            return List.of();
        }
    }

    private static String primeiro(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }
}

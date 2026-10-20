package br.com.iv.cdss.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.cdss.dto.CienciaResultadoCriticoRequest;
import br.com.iv.cdss.dto.ResultadoCriticoDto;
import br.com.iv.cdss.entity.CdssAlertaOverride;
import br.com.iv.cdss.repository.CdssAlertaOverrideRepository;
import br.com.iv.cdss.service.ValoresCriticosLaboratorio.Achado;
import br.com.iv.entity.PedidoResultadoLaboratorio;
import br.com.iv.entity.PedidoResultadoLaboratorioItem;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.PedidoResultadoLaboratorioService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Valores criticos dos laudos laboratoriais de um BAM. A deteccao e feita na
 * leitura, sobre os itens ja gravados (pedidoresultadolaboratorio_item), entao
 * vale tambem para laudos antigos que tenham itens. A ciencia do profissional e
 * gravada em cdss_alerta_override (codigo LAB_CRITICO_{iditem}), a mesma
 * trilha de auditoria dos alertas do CDSS - sem tabela nova.
 */
@ApplicationScoped
public class ResultadoCriticoService {
    public static final String PREFIXO_CODIGO = "LAB_CRITICO_";
    public static final String MODULO = "RESULTADO_CRITICO";

    @Inject
    ValoresCriticosLaboratorio valoresCriticos;

    @Inject
    PedidoResultadoLaboratorioService resultadoService;

    @Inject
    CdssAlertaOverrideRepository overrideRepository;

    public List<ResultadoCriticoDto> listarPorBam(int idbam) {
        List<PedidoResultadoLaboratorio> resultados = resultadoService.filtrarBamPedidoResultadoLaboratorio(idbam);
        if (resultados == null || resultados.isEmpty()) {
            return List.of();
        }
        Map<Integer, PedidoResultadoLaboratorio> porId = new HashMap<>();
        for (PedidoResultadoLaboratorio r : resultados) {
            porId.put(r.getIdPedidoResultado(), r);
        }
        List<PedidoResultadoLaboratorioItem> itens = PedidoResultadoLaboratorioItem
                .list("idpedidoresultado in ?1 order by idpedidoresultado desc, ordem", new ArrayList<>(porId.keySet()));

        Map<String, CdssAlertaOverride> ciencias = new HashMap<>();
        for (CdssAlertaOverride o : overrideRepository.listarPorBam(idbam)) {
            if (MODULO.equals(o.getModuloOrigem()) && o.getAlertaCodigo() != null) {
                ciencias.putIfAbsent(o.getAlertaCodigo(), o);
            }
        }

        List<ResultadoCriticoDto> lista = new ArrayList<>();
        for (PedidoResultadoLaboratorioItem item : itens) {
            Achado achado = valoresCriticos.avaliar(item.getNomeExame(), item.getCodigoLoinc(), item.getValor(),
                    item.getUnidadeMedida());
            if (achado == null) {
                continue;
            }
            PedidoResultadoLaboratorio r = porId.get(item.getIdpedidoresultado());
            CdssAlertaOverride ciencia = ciencias.get(PREFIXO_CODIGO + item.getIditem());
            lista.add(new ResultadoCriticoDto(item.getIditem(), item.getIdpedidoresultado(), idbam,
                    item.getNomeExame(), item.getValor(), item.getUnidadeMedida(), achado.analito(),
                    achado.direcao(), achado.limite(), dataResultado(r), ciencia != null,
                    ciencia == null ? null : ciencia.getCriadoEm(),
                    ciencia == null ? null : ciencia.getUsuarioNome(),
                    ciencia == null ? null : ciencia.getJustificativa()));
        }
        return lista;
    }

    @Transactional
    public ResultadoCriticoDto registrarCiencia(CienciaResultadoCriticoRequest request, AuthenticatedUser usuario) {
        if (usuario == null || usuario.matricula() == null || usuario.matricula().isBlank()) {
            throw new SecurityException("Sessao sem profissional identificado.");
        }
        if (request == null || request.getIdbam() == null || request.getIditem() == null) {
            throw new IllegalArgumentException("Informe o BAM e o exame.");
        }
        String conduta = request.getConduta() == null ? "" : request.getConduta().trim();
        if (conduta.length() < 10) {
            throw new IllegalArgumentException("Descreva a conduta tomada (minimo de 10 caracteres).");
        }
        // Revalida no servidor: so aceita ciencia de item critico daquele BAM.
        ResultadoCriticoDto alvo = listarPorBam(request.getIdbam()).stream()
                .filter(c -> c.iditem() == request.getIditem())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Exame nao e valor critico deste BAM."));
        if (alvo.ciente()) {
            return alvo; // idempotente: a primeira ciencia vale
        }
        CdssAlertaOverride registro = new CdssAlertaOverride();
        registro.setIdbam(request.getIdbam());
        registro.setUnidade(usuario.unidade());
        registro.setAlertaCodigo(PREFIXO_CODIGO + alvo.iditem());
        registro.setAlertaSeveridade("CRITICAL");
        registro.setJustificativa(alvo.exame() + " " + alvo.valor() + " - " + conduta);
        registro.setModuloOrigem(MODULO);
        registro.setUsuarioNome(usuario.nome());
        registro.setUsuarioMatricula(usuario.matricula());
        registro.persist();
        return new ResultadoCriticoDto(alvo.iditem(), alvo.idpedidoresultado(), alvo.bam(), alvo.exame(),
                alvo.valor(), alvo.unidadeMedida(), alvo.analito(), alvo.direcao(), alvo.limite(),
                alvo.dataResultado(), true, registro.getCriadoEm(), registro.getUsuarioNome(),
                registro.getJustificativa());
    }

    private String dataResultado(PedidoResultadoLaboratorio r) {
        if (r == null) return null;
        String dt = r.getDt() == null ? "" : r.getDt().trim();
        String hr = r.getHr() == null ? "" : r.getHr().trim();
        String junto = (dt + " " + hr).trim();
        return junto.isBlank() ? null : junto;
    }
}

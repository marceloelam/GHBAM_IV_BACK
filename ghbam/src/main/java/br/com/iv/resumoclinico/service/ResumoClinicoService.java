package br.com.iv.resumoclinico.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import br.com.iv.resumoclinico.dto.*;
import br.com.iv.resumoclinico.entity.ResumoClinicoAuditoria;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ResumoClinicoService {
    public static final String AVISO = "Este conteúdo foi gerado por Inteligência Artificial com base nas informações registradas no GHBAM IV. O resumo deve ser revisado e validado por um profissional de saúde antes de qualquer utilização clínica.";
    private final ConcurrentHashMap<String, LocalDateTime> ultimaGeracao = new ConcurrentHashMap<>();
    @Inject ResumoClinicoDataService dados;
    @Inject GeradorResumoClinicoIa ia;
    @Inject GeradorResumoClinicoLocal local;
    @Inject ResumoClinicoAuditoriaService auditoriaService;
    @ConfigProperty(name = "ghbam.ia.enabled", defaultValue = "false") boolean iaAtiva;
    @ConfigProperty(name = "ghbam.ia.rate-limit-seconds", defaultValue = "10") int rateLimitSegundos;

    public List<PacienteResumoDto> buscar(String termo, AuthenticatedUser usuario) {
        autorizar(usuario);
        return dados.buscarPacientes(termo);
    }

    public List<AtendimentoResumoDto> atendimentos(int pacienteId, AuthenticatedUser usuario) {
        autorizar(usuario);
        return dados.atendimentos(pacienteId);
    }

    public ResumoClinicoResponse gerar(GerarResumoClinicoRequest req, AuthenticatedUser usuario, String ip) {
        validarInicioGeracao(usuario);
        return gerarAcompanhado(req, usuario, ip, (fase, mensagem, percentual) -> {});
    }

    public void validarInicioGeracao(AuthenticatedUser usuario) {
        autorizar(usuario);
        limitar(usuario);
    }

    public ResumoClinicoResponse gerarAcompanhado(GerarResumoClinicoRequest req, AuthenticatedUser usuario,
                                                   String ip, ProgressoResumoClinico progresso) {
        long inicio = System.nanoTime();
        progresso.atualizar("COLETANDO_DADOS", "Consultando os registros do prontuário…", 12);
        ContextoClinico contexto = dados.carregar(req.pacienteId, req.bam, req.tipoResumo, req.periodo);
        progresso.atualizar("PREPARANDO_CONTEXTO", "Organizando evoluções, exames e medicamentos…", 38);
        ResumoClinicoGerado gerado;
        String erroIa = null;
        try {
            progresso.atualizar("CONSULTANDO_IA", iaAtiva
                ? "A inteligência artificial está elaborando o resumo…"
                : "Elaborando o resumo clínico…", 55);
            gerado = iaAtiva ? ia.gerar(contexto) : local.gerar(contexto);
        } catch (Exception e) {
            erroIa = e.getClass().getSimpleName();
            progresso.atualizar("GERANDO_FALLBACK", "Finalizando com o gerador clínico local…", 82);
            gerado = local.gerar(contexto);
        }
        progresso.atualizar("FINALIZANDO", "Conferindo fontes, alertas e pendências…", 90);
        LocalDateTime agora = LocalDateTime.now();
        auditar(contexto, usuario, ip, gerado, erroIa, inicio, agora);
        return new ResumoClinicoResponse(req.pacienteId, req.bam, req.tipoResumo, req.periodo,
            gerado.texto(), contexto.alertas(), contexto.pendencias(), contexto.fontes(), agora, AVISO,
            erroIa == null ? gerado.modo() : "LOCAL_FALLBACK");
    }

    public List<HistoricoResumoDto> historico(int bam, AuthenticatedUser usuario) {
        autorizar(usuario);
        return ResumoClinicoAuditoria.<ResumoClinicoAuditoria>list("bam = ?1 order by geradoEm desc", bam)
            .stream().limit(100).map(a -> new HistoricoResumoDto(a.id, a.pacienteId, a.bam, a.usuario,
                a.tipoResumo, a.periodo, a.geradoEm, a.modo, a.status)).toList();
    }

    private void autorizar(AuthenticatedUser u) {
        if (u == null || u.matricula() == null || u.matricula().isBlank()) throw new WebApplicationException("Autenticação necessária.", 401);
        if (!(u.hasPermission("presumoclinicoia") || u.hasPermission("kadm")))
            throw new ForbiddenException("Perfil sem permissão para gerar resumo clínico.");
    }

    private void limitar(AuthenticatedUser u) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime anterior = ultimaGeracao.put(u.matricula(), agora);
        if (anterior != null && anterior.plusSeconds(rateLimitSegundos).isAfter(agora)) {
            ultimaGeracao.put(u.matricula(), anterior);
            throw new WebApplicationException("Aguarde alguns segundos antes de gerar outro resumo.", 429);
        }
    }

    private void auditar(ContextoClinico c, AuthenticatedUser u, String ip, ResumoClinicoGerado g,
                         String erro, long inicio, LocalDateTime agora) {
        ResumoClinicoAuditoria a = new ResumoClinicoAuditoria();
        a.pacienteId = c.pacienteId();
        a.bam = c.bam();
        a.usuario = u.matricula();
        a.usuarioNome = u.nome();
        a.tipoResumo = c.tipo();
        a.periodo = c.periodo();
        a.geradoEm = agora;
        a.modelo = g.modelo();
        a.modo = erro == null ? g.modo() : "LOCAL_FALLBACK";
        a.status = erro == null ? "SUCESSO" : "FALLBACK";
        a.tempoProcessamentoMs = (System.nanoTime() - inicio) / 1_000_000;
        a.tokensEntrada = g.tokensEntrada();
        a.tokensSaida = g.tokensSaida();
        a.hashContexto = dados.hash(c.dados());
        a.fontesConsultadas = c.fontes().stream().map(ResumoFonteDto::tipo).distinct().reduce((x, y) -> x + "," + y).orElse("");
        a.erro = erro;
        a.ipUsuario = ip == null ? "" : ip.substring(0, Math.min(ip.length(), 64));
        auditoriaService.registrar(a);
    }
}

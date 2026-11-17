package br.com.iv.resumoclinico.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.context.ManagedExecutor;
import br.com.iv.resumoclinico.dto.*;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class ResumoClinicoProcessoService {
    private static final Duration RETENCAO = Duration.ofMinutes(30);
    private final ConcurrentHashMap<String, Processo> processos = new ConcurrentHashMap<>();

    @Inject ResumoClinicoService resumoClinicoService;
    @Inject ManagedExecutor executor;

    public IniciarResumoClinicoResponse iniciar(GerarResumoClinicoRequest requisicao,
                                                 AuthenticatedUser usuario, String ip) {
        resumoClinicoService.validarInicioGeracao(usuario);
        limparExpirados();

        String processoId = UUID.randomUUID().toString();
        Processo processo = new Processo(processoId, usuario.matricula());
        processos.put(processoId, processo);

        // Sem contexto da requisicao na thread: le/grava no banco do municipio de quem pediu
        // (mesmo padrao do ImagemIaService; sem isso cairia no banco padrao).
        br.com.iv.plataforma.municipio.MunicipioRoteamento municipios = io.quarkus.arc.Arc.container()
                .instance(br.com.iv.plataforma.municipio.MunicipioRoteamento.class).get();
        String municipio = municipios.atual();
        executor.runAsync(() -> municipios.executarEm(municipio, () -> executar(processo, requisicao, usuario, ip)));
        return new IniciarResumoClinicoResponse(processoId);
    }

    public ProgressoResumoClinicoResponse consultar(String processoId, AuthenticatedUser usuario) {
        Processo processo = processos.get(processoId);
        if (processo == null || usuario == null || !processo.usuario.equals(usuario.matricula())) {
            throw new NotFoundException("Processo de geração não encontrado.");
        }
        return processo.resposta();
    }

    private void executar(Processo processo, GerarResumoClinicoRequest requisicao,
                          AuthenticatedUser usuario, String ip) {
        try {
            processo.atualizar("COLETANDO_DADOS", "Consultando os registros do prontuário…", 10);
            processo.resultado = resumoClinicoService.gerarAcompanhado(
                requisicao, usuario, ip, processo::atualizar);
            processo.status = "CONCLUIDO";
            processo.atualizar("CONCLUIDO", "Resumo concluído.", 100);
        } catch (Exception e) {
            processo.erro = mensagemSegura(e);
            processo.status = "ERRO";
            processo.atualizar("ERRO", "Não foi possível concluir o resumo.", 100);
        }
    }

    private void limparExpirados() {
        Instant limite = Instant.now().minus(RETENCAO);
        processos.entrySet().removeIf(item -> item.getValue().criadoEm.isBefore(limite));
    }

    private String mensagemSegura(Exception erro) {
        String mensagem = erro.getMessage();
        return mensagem == null || mensagem.isBlank()
            ? "Não foi possível gerar o resumo clínico."
            : mensagem;
    }

    private static final class Processo {
        private final String id;
        private final String usuario;
        private final Instant criadoEm = Instant.now();
        private volatile String status = "PROCESSANDO";
        private volatile String fase = "AGUARDANDO";
        private volatile String mensagem = "Preparando a geração…";
        private volatile int percentual = 2;
        private volatile ResumoClinicoResponse resultado;
        private volatile String erro;

        private Processo(String id, String usuario) {
            this.id = id;
            this.usuario = usuario;
        }

        private void atualizar(String novaFase, String novaMensagem, int novoPercentual) {
            fase = novaFase;
            mensagem = novaMensagem;
            percentual = novoPercentual;
        }

        private ProgressoResumoClinicoResponse resposta() {
            return new ProgressoResumoClinicoResponse(
                id, status, fase, mensagem, percentual, resultado, erro);
        }
    }
}

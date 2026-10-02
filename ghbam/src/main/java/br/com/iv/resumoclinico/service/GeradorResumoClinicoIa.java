package br.com.iv.resumoclinico.service;

import java.util.List;
import java.util.Map;
import br.com.iv.util.AcessoIa;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GeradorResumoClinicoIa implements GeradorResumoClinico {
    @Inject AcessoIa acessoIa;
    @Inject ResumoClinicoPromptService prompt;
    @ConfigProperty(name = "ghbam.ia.model", defaultValue = "groq:llama-3.3-70b-versatile")
    String modelo;

    @Override
    public ResumoClinicoGerado gerar(ContextoClinico contexto) throws Exception {
        var resposta = acessoIa.enviarMensagensComMetadados(List.of(
            Map.of("role", "system", "content", ResumoClinicoPromptService.SISTEMA),
            Map.of("role", "user", "content", prompt.usuario(contexto))
        ), modelo, 0.2, 2200);
        return new ResumoClinicoGerado(resposta.conteudo(), "IA", resposta.provedor(),
            resposta.tokensPrompt(), resposta.tokensCompletion());
    }
}

package br.com.iv.resumoclinico.service;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GeradorResumoClinicoLocal implements GeradorResumoClinico {
    @Override
    public ResumoClinicoGerado gerar(ContextoClinico c) {
        String registros = c.dados().isBlank()
            ? "Não há dados clínicos suficientes para o escopo selecionado."
            : c.dados().replaceAll("(?m)^([\\p{Lu}ÁÉÍÓÚÇÃÕ0-9 /()_-]{3,}):\\s*$", "### $1");
        String texto = "## Visão geral\n\n"
            + "- **Paciente:** " + c.pacienteNome() + "\n"
            + "- **Prontuário:** " + c.prontuario() + "\n"
            + "- **BAM:** " + c.bam() + "\n"
            + "- **Unidade:** " + c.unidade() + "\n"
            + "- **Perfil:** " + c.tipo() + "\n"
            + "- **Período:** " + c.periodo() + "\n\n"
            + "## Registros localizados\n\n" + registros;
        return new ResumoClinicoGerado(texto, "LOCAL", "gerador-local", null, null);
    }
}

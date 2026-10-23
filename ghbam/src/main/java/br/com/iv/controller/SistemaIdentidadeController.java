package br.com.iv.controller;

import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Nome do sistema exibido ao usuario. Publico (GET) porque a tela de login
 * ja precisa dele antes de existir sessao.
 */
@Path("sistema/identidade")
@Produces(MediaType.APPLICATION_JSON)
public class SistemaIdentidadeController {

    @ConfigProperty(name = "ghbam.sistema.nome", defaultValue = "GHBAM")
    String nome;

    @ConfigProperty(name = "ghbam.sistema.nome-completo", defaultValue = "GHBAM IV")
    String nomeCompleto;

    @ConfigProperty(name = "ghbam.sistema.descricao", defaultValue = "Gestão Hospitalar de Boletim de Atendimento Médico")
    String descricao;

    @GET
    public Map<String, String> identidade() {
        return Map.of("nome", nome, "nomeCompleto", nomeCompleto, "descricao", descricao);
    }
}

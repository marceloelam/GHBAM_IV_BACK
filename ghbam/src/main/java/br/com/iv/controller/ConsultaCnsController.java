package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import br.com.iv.dto.ConsultaCnsDto;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.ConsultaCnsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * CPF + data de nascimento -> CNS, consultando o cadastro publico do CNES.
 *
 * Usado pela /recepcao (Cad_pacientes) para preencher o campo CNS sem digitacao:
 * CNS errado so aparece semanas depois, na rejeicao do BPA, com a competencia ja
 * fechada. O 503 e proposital - o DATASUS cair nao pode travar o atendimento, o
 * frontend segue com o campo em branco para preenchimento manual.
 *
 * Emergencia sem CPF (paciente sem documento): aceita Nome + dataNascimento no lugar de
 * cpf. O scraping do CNES exige CPF e nao ajuda nesse caso; so a API oficial do PDQ
 * (toggle global em /base2/integracao-sus) consegue, buscando por Nome + Nascimento -
 * ver ConsultaCnsService#consultarPorNome.
 */
@Path("consulta-cns")
@Produces(MediaType.APPLICATION_JSON)
public class ConsultaCnsController {

    @Inject
    ConsultaCnsService consultaCnsService;

    @GET
    public Response consultar(@QueryParam("cpf") String cpf,
            @QueryParam("dataNascimento") String dataNascimento,
            @QueryParam("nome") String nome,
            @QueryParam("nomeMae") String nomeMae,
            @Context ContainerRequestContext context) {

        boolean temCpf = cpf != null && !cpf.isBlank();
        boolean temNome = nome != null && !nome.isBlank();
        if ((!temCpf && !temNome) || dataNascimento == null || dataNascimento.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ConsultaCnsDto.naoEncontrado(
                            List.of("Informe cpf+dataNascimento, ou nome+dataNascimento quando nao houver CPF.")))
                    .build();
        }

        try {
            // Unidade vem do usuario logado - nenhuma das 4 telas que chamam este endpoint
            // (recepcao, agendamento, cadastro de servidores, centro cirurgico) precisa
            // mudar para isso funcionar quando a API oficial do PDQ for ativada (ver
            // ConfiguracaoCnsPdqService).
            String unidade = user(context).unidade();
            return Response.ok(consultaCnsService.consultar(cpf, dataNascimento, nome, nomeMae, unidade)).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("encontrado", false, "erro", e.getMessage()))
                    .build();
        }
    }

    private AuthenticatedUser user(ContainerRequestContext context) {
        Object value = context.getProperty("ghbam.user");
        return value instanceof AuthenticatedUser authenticatedUser ? authenticatedUser : AuthenticatedUser.empty();
    }
}

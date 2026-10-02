package br.com.iv.controller;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.entity.Afaturar;
import br.com.iv.entity.FaturamentoCompetencia;
import br.com.iv.enums.InstrumentoRegistro;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.service.FaturamentoProducaoService;

@Path("faturamento/producao")
@Produces(MediaType.APPLICATION_JSON)
public class FaturamentoProducaoController {

    @Context
    ContainerRequestContext context;

    @Inject
    FaturamentoProducaoService producaoService;

    private AuthenticatedUser user() {
        Object u = context.getProperty("ghbam.user");
        if (!(u instanceof AuthenticatedUser a) || !a.hasAnyPermission(List.of("kfaturamento", "kadm"))) {
            throw new ForbiddenException();
        }
        return a;
    }

    private AuthenticatedUser userComPermissaoDeCorrigir() {
        AuthenticatedUser u = user();
        if (!u.hasAnyPermission(List.of("pfaturamentosuscorrigir", "kadm"))) {
            throw new ForbiddenException();
        }
        return u;
    }

    @GET
    public List<Afaturar> listar(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade, @QueryParam("status") String status,
            @QueryParam("instrumento") String instrumento, @QueryParam("q") String busca,
            @QueryParam("pagina") Integer pagina, @QueryParam("tamanho") Integer tamanho) {
        AuthenticatedUser u = user();
        return producaoService.listar(idcompetencia, unidadeDaProducao(u, unidade), status, instrumento, busca,
                pagina == null ? 0 : pagina, tamanho == null ? 50 : tamanho);
    }

    @GET
    @Path("{id}")
    public Afaturar buscar(@PathParam("id") int id) {
        AuthenticatedUser u = user();
        try {
            return producaoService.buscar(id, unidadeDaProducao(u, null));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @POST
    @Path("reprocessar/{id}")
    public Response reprocessar(@PathParam("id") int id, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.reprocessar(id, u, unidadeDaProducao(u, unidade))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    // Preparo incremental da competencia: uma chamada = um lote. O painel repete
    // enquanto "restantes" for maior que zero e "avancou" continuar true. Substitui o
    // processar-competencia na producao real - la o reprocessamento linha a linha nao
    // termina dentro do statement_timeout.
    @POST
    @Path("preparar/{idcompetencia}")
    public Response preparar(@PathParam("idcompetencia") int idcompetencia,
            @QueryParam("unidade") String unidade,
            @QueryParam("limite") Integer limite) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.prepararEmLote(idcompetencia, unidadeDaProducao(u, unidade), u,
                    limite == null ? 0 : limite)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("processar-competencia/{idcompetencia}")
    public Response processarCompetencia(@PathParam("idcompetencia") int idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.processarCompetencia(idcompetencia, u,
                    unidadeDaProducao(u, unidade))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    // Primeira etapa do "Corrigir todas" da tela de producao: cruza a producao da
    // competencia com o SIGTAP importado dela e ja grava instrumento e status
    // (ver FaturamentoProducaoService.sincronizarComSigtap). Roda em massa, sem
    // percorrer a producao linha a linha.
    @POST
    @Path("sincronizar-sigtap/{idcompetencia}")
    public Response sincronizarSigtap(@PathParam("idcompetencia") int idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            FaturamentoCompetencia competencia = FaturamentoCompetencia.findById(idcompetencia);
            if (competencia == null) {
                throw new IllegalArgumentException("Competencia nao encontrada para o id: " + idcompetencia);
            }
            return Response.ok(producaoService.sincronizarComSigtap(idcompetencia, competencia.getCompetencia(),
                    unidadeDaProducao(u, unidade), u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    // "Corrigir todas" da tela de producao: classifica de uma vez toda producao
    // da competencia presa em PENDENTE_CLASSIFICACAO (BPA-I com CNS valido, BPA-C
    // sem CNS valido). Devolve o motivo de cada item que nao saiu do estado, para o
    // operador nao ficar sem explicacao quando a linha continua na lista.
    @POST
    @Path("classificar-pendentes/{idcompetencia}")
    public Response classificarPendentes(@PathParam("idcompetencia") int idcompetencia,
            @QueryParam("unidade") String unidade, @QueryParam("limite") Integer limite) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.classificarPendentesDaCompetencia(idcompetencia, u,
                    unidadeDaProducao(u, unidade), limite == null ? 2000 : limite)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    // Correcao manual de PENDENTE_CLASSIFICACAO quando o SIGTAP local (pa_tpreg)
    // nao define BPA-I/BPA-C para o procedimento (ver FaturamentoProducaoService).
    @POST
    @Path("classificar/{id}")
    public Response classificar(@PathParam("id") int id, ClassificarDto dto, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            InstrumentoRegistro instrumento = InstrumentoRegistro.valueOf(
                    dto == null || dto.instrumento() == null ? "" : dto.instrumento().trim().toUpperCase());
            return Response.ok(producaoService.classificarManualmente(id, instrumento,
                    dto == null ? null : dto.motivo(), u, unidadeDaProducao(u, unidade))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    public record ClassificarDto(String instrumento, String motivo) {
    }

    // Porta de saida do descarte/classificacao manual: devolve a linha ao motor
    // automatico. Mesma permissao de quem classifica - quem pode tirar do BPA precisa
    // poder corrigir o proprio engano.
    @POST
    @Path("reverter-classificacao/{id}")
    public Response reverterClassificacao(@PathParam("id") int id, ReverterDto dto,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.reverterClassificacaoManual(id,
                    dto == null ? null : dto.motivo(), u, unidadeDaProducao(u, unidade))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    public record ReverterDto(String motivo) {
    }

    // Correcao do CNS direto na tela de fechamento: grava no cadastro do paciente e
    // destrava a producao dele na competencia. Mesma permissao de quem corrige producao.
    @POST
    @Path("corrigir-cns-paciente/{idpaciente}")
    public Response corrigirCnsPaciente(@PathParam("idpaciente") int idpaciente, CorrigirCnsDto dto,
            @QueryParam("idcompetencia") Integer idcompetencia, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.corrigirCnsDoPaciente(idpaciente,
                    dto == null ? null : dto.cns(), idcompetencia, unidadeDaProducao(u, unidade), u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    public record CorrigirCnsDto(String cns) {
    }

    // Corrige CPF/nascimento do paciente a partir da linha criticada e tenta buscar o
    // CNS no CADSUS/CNES. Se encontrar CNS valido, aplica no cadastro e na producao
    // da competencia.
    @POST
    @Path("buscar-cns-paciente/{id}")
    public Response buscarCnsPaciente(@PathParam("id") int id, BuscarCnsPacienteDto dto,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.atualizarDadosPacienteEBuscarCns(id,
                    dto == null ? null : dto.cpf(), dto == null ? null : dto.nascimento(),
                    unidadeDaProducao(u, unidade), u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    public record BuscarCnsPacienteDto(String cpf, String nascimento) {
    }

    // Lista para a recepcao/cadastro: pacientes sem CNS valido nem na producao nem no
    // cadastro. Agrupado por paciente, porque a correcao e por pessoa. Somente leitura.
    @GET
    @Path("pacientes-sem-cns")
    public Response pacientesSemCns(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return Response.ok(producaoService.listarPacientesSemCnsValido(idcompetencia,
                unidadeDaProducao(u, unidade))).build();
    }

    // Par de pacientes-sem-cns para o outro lado da conta: profissionais com producao sem
    // CBO, com o cargo e a especialidade do cadastro. Sao esses dois campos que dizem ao
    // operador qual ocupacao informar - o CBO nao se deduz do nome. Somente leitura.
    @GET
    @Path("profissionais-sem-cbo")
    public Response profissionaisSemCbo(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return Response.ok(producaoService.listarProfissionaisSemCbo(idcompetencia,
                unidadeDaProducao(u, unidade))).build();
    }

    // Rebusca o CNS no cadastro do paciente para a producao travada por CNS invalido.
    // Mesma permissao de quem corrige producao: altera dado faturavel.
    @POST
    @Path("sincronizar-cns/{idcompetencia}")
    public Response sincronizarCns(@PathParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.sincronizarCnsDoCadastro(idcompetencia,
                    unidadeDaProducao(u, unidade), u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    // Completa o CBO dos profissionais da competencia: herda o codigo do cadastro do
    // servidor e, quando o cadastro tambem esta vazio, procura o cargo na tabela CBO -
    // gravando so quando o cargo casa com um unico titulo. Quem depende de decisao volta
    // na lista "pendentes" com as opcoes, sem nada gravado.
    @POST
    @Path("completar-cbo/{idcompetencia}")
    public Response completarCbo(@PathParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.completarCboDosProfissionais(idcompetencia,
                    unidadeDaProducao(u, unidade), u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    // Passo seguinte ao sincronizar-cns: busca o CNS no cadastro publico do CNES por CPF +
    // nascimento para quem nao tem numero valido nem no cadastro local. Fala com servidor
    // de terceiro, entao pode demorar - o placar diz quantos ficaram sem consulta.
    @POST
    @Path("buscar-cns-cnes/{idcompetencia}")
    public Response buscarCnsNoCnes(@PathParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.buscarCnsNoCnes(idcompetencia,
                    unidadeDaProducao(u, unidade), u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    // Mesma ideia de buscar-cns-cnes, mas para o CNS do profissional (nao do paciente):
    // corrige o cadastro do servidor no CNES e propaga o numero certo para a producao ja
    // gravada, destravando a critica "CNS invalido" que o BPA acusa em /base2/bpa sem o
    // operador precisar reprocessar linha por linha. idcompetencia e opcional - a tela de
    // Previa BPA busca por periodo (data), nao por competencia, entao sem o parametro a
    // propagacao varre toda a producao com critica da unidade.
    @POST
    @Path("buscar-cns-profissionais-cnes")
    public Response buscarCnsDeProfissionaisNoCnes(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.buscarCnsDeProfissionaisNoCnesEPropagar(idcompetencia,
                    unidadeDaProducao(u, unidade), u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    // Passo 1 da orientacao da tela de nao faturavel, feito em lote: producao de
    // procedimento que nao e de BPA (AIH/APAC/RAAS) vai para a conta hospitalar do
    // paciente quando existe uma nesta competencia. Quem nao tem conta continua nao
    // faturavel - ver FaturamentoProducaoService.vincularNaoFaturaveisAAih.
    @POST
    @Path("vincular-aih/{idcompetencia}")
    public Response vincularNaoFaturaveisAAih(@PathParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.vincularNaoFaturaveisAAih(idcompetencia,
                    unidadeDaProducao(u, unidade), u)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    // Por que a producao esta parada, agrupado por motivo e com a contagem. Somente
    // leitura: refaz a validacao em memoria, nao grava nem muda status de linha nenhuma.
    @GET
    @Path("motivos-criticas")
    public Response motivosDeCritica(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return Response.ok(producaoService.agruparMotivosDeCritica(idcompetencia,
                unidadeDaProducao(u, unidade))).build();
    }

    // Conferencia de fechamento: RN que saiu do BPA pela AIH sem ter internacao no
    // censo - producao que nenhum caminho fatura. Somente leitura.
    @GET
    @Path("rn-sem-internacao")
    public Response recemNascidosSemInternacao(@QueryParam("idcompetencia") Integer idcompetencia,
            @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = user();
        return Response.ok(producaoService.listarRecemNascidosSemInternacao(idcompetencia,
                unidadeDaProducao(u, unidade))).build();
    }

    // Correcao direta de producao a partir da tela de criticas (campo/valor livres,
    // validados em FaturamentoProducaoService.corrigirCampo).
    @POST
    @Path("corrigir-campo/{id}")
    public Response corrigirCampo(@PathParam("id") int id, CorrigirCampoDto dto, @QueryParam("unidade") String unidade) {
        AuthenticatedUser u = userComPermissaoDeCorrigir();
        try {
            return Response.ok(producaoService.corrigirCampo(id, dto == null ? null : dto.campo(),
                    dto == null ? null : dto.valor(), u, unidadeDaProducao(u, unidade))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    public record CorrigirCampoDto(String campo, String valor) {
    }

    private String unidadeDaProducao(AuthenticatedUser user, String unidadeSolicitada) {
        if (user == null || !user.hasPermission("kadm")) {
            return user == null ? null : user.unidade();
        }
        if (unidadeSolicitada != null && !unidadeSolicitada.isBlank()) {
            return unidadeSolicitada;
        }
        return user.unidade();
    }
}

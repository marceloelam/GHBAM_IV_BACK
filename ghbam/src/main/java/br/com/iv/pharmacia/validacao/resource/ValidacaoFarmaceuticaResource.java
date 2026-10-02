package br.com.iv.pharmacia.validacao.resource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.iv.pharmacia.validacao.dto.AlertaFarmaceuticoDto;
import br.com.iv.pharmacia.validacao.dto.ValidacaoDispensacaoRequest;
import br.com.iv.pharmacia.validacao.dto.ValidacaoFarmaceuticaResponse;
import br.com.iv.pharmacia.validacao.entity.PharmaciaValidacao;
import br.com.iv.pharmacia.validacao.service.BaseConhecimentoFarmaceutico;
import br.com.iv.pharmacia.validacao.service.ValidacaoFarmaceuticaService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/pharmacia/validacao")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ValidacaoFarmaceuticaResource {

    @Inject
    ValidacaoFarmaceuticaService service;

    @Inject
    BaseConhecimentoFarmaceutico base;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Avalia a dispensacao sem gravar nada. A tela chama a cada mudanca do
     * carrinho, entao esta rota precisa ser barata e nao deixar rastro.
     */
    @POST
    @Path("/dispensacao")
    public Response validar(ValidacaoDispensacaoRequest request) {
        try {
            return Response.ok(service.validar(request)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", String.valueOf(e.getMessage()))).build();
        }
    }

    /** Corpo do registro do desfecho da validacao. */
    public static class RegistroRequest {
        public ValidacaoDispensacaoRequest contexto;
        public ValidacaoFarmaceuticaResponse resultado;
        public String usuarioMatricula;
        public String usuarioNome;
        public String justificativa;
        public String duplaChecagemMatricula;
        /** DISPENSADO, DISPENSADO_COM_JUSTIFICATIVA, SUSPENSO, ALTERADO, APENAS_CONSULTA. */
        public String desfecho;
        public String intervencao;
    }

    /**
     * Grava o que o farmaceutico decidiu diante dos alertas. E este registro,
     * e nao a avaliacao em si, que documenta a analise farmaceutica.
     */
    @POST
    @Path("/registro")
    @Transactional
    public Response registrar(RegistroRequest req) {
        if (req == null || req.resultado == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "Resultado da validacao obrigatorio.")).build();
        }
        try {
            PharmaciaValidacao registro = new PharmaciaValidacao();
            if (req.contexto != null) {
                registro.setIdpaciente(req.contexto.getIdpaciente());
                registro.setPacienteNome(req.contexto.getPacienteNome());
                registro.setIdbam(req.contexto.getIdbam());
                registro.setUnidadeSigla(req.contexto.getUnidade());
                registro.setItensJson(mapper.writeValueAsString(req.contexto.getItens()));
            }
            registro.setDataHora(LocalDateTime.now());
            registro.setUsuarioMatricula(req.usuarioMatricula);
            registro.setUsuarioNome(req.usuarioNome);
            registro.setVersaoBase(req.resultado.getVersaoBase());

            List<AlertaFarmaceuticoDto> alertas = req.resultado.getAlertas();
            registro.setTotalAlertas(alertas.size());
            registro.setAlertasCriticos((int) alertas.stream()
                    .filter(a -> "CRITICAL".equalsIgnoreCase(a.getSeveridade())).count());
            registro.setAlertasJson(mapper.writeValueAsString(alertas));

            registro.setJustificativa(req.justificativa);
            registro.setDuplaChecagemMatricula(req.duplaChecagemMatricula);
            registro.setDesfecho(req.desfecho == null || req.desfecho.isBlank() ? "DISPENSADO" : req.desfecho);
            registro.setIntervencao(req.intervencao);

            registro.persist();
            return Response.status(Response.Status.CREATED).entity(registro).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", String.valueOf(e.getMessage()))).build();
        }
    }

    /** Historico de validacoes de um paciente, da mais recente para a mais antiga. */
    @GET
    @Path("/paciente/{idpaciente}")
    public List<PharmaciaValidacao> porPaciente(@PathParam("idpaciente") Integer idpaciente) {
        return PharmaciaValidacao.find("idpaciente = ?1 order by dataHora desc", idpaciente).list();
    }

    /**
     * Metadados da base de conhecimento. A tela de ajuda mostra a versao para
     * que o farmaceutico saiba com que conteudo o sistema esta trabalhando.
     */
    @GET
    @Path("/base")
    public Map<String, Object> metadadosDaBase() {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("versao", base.getVersao());
        dados.put("medicamentos", base.getMedicamentos().size());
        dados.put("interacoes", base.getInteracoes().size());
        dados.put("duplicidades", base.getDuplicidades().size());
        dados.put("alergiasCruzadas", base.getAlergiasCruzadas().size());
        dados.put("altaVigilancia", base.getMedicamentos().stream().filter(m -> m.altaVigilancia).count());
        dados.put("controlados", base.getMedicamentos().stream()
                .filter(m -> m.listaControlada != null && !m.listaControlada.isBlank()).count());
        dados.put("antimicrobianos", base.getMedicamentos().stream().filter(m -> m.antimicrobiano).count());
        return dados;
    }

    /**
     * Indicadores de validacao farmaceutica no periodo: quantas dispensacoes
     * foram avaliadas, quantas geraram alerta critico e quantas terminaram em
     * suspensao ou alteracao, ou seja, quantas vezes a farmacia evitou um erro.
     */
    @GET
    @Path("/indicadores")
    public Map<String, Object> indicadores(@QueryParam("unidade") String unidade, @QueryParam("de") String de,
            @QueryParam("ate") String ate) {
        LocalDateTime inicio = de == null || de.isBlank() ? LocalDate.now().minusDays(30).atStartOfDay()
                : LocalDate.parse(de).atStartOfDay();
        LocalDateTime fim = ate == null || ate.isBlank() ? LocalDateTime.now() : LocalDate.parse(ate).atTime(23, 59, 59);

        List<PharmaciaValidacao> registros;
        if (unidade == null || unidade.isBlank()) {
            registros = PharmaciaValidacao.find("dataHora between ?1 and ?2", inicio, fim).list();
        } else {
            registros = PharmaciaValidacao
                    .find("unidadeSigla = ?1 and dataHora between ?2 and ?3", unidade, inicio, fim).list();
        }

        long comCritico = registros.stream()
                .filter(r -> r.getAlertasCriticos() != null && r.getAlertasCriticos() > 0).count();
        long suspensas = registros.stream().filter(r -> "SUSPENSO".equalsIgnoreCase(r.getDesfecho())).count();
        long alteradas = registros.stream().filter(r -> "ALTERADO".equalsIgnoreCase(r.getDesfecho())).count();
        long comJustificativa = registros.stream()
                .filter(r -> r.getJustificativa() != null && !r.getJustificativa().isBlank()).count();
        long comDuplaChecagem = registros.stream()
                .filter(r -> r.getDuplaChecagemMatricula() != null && !r.getDuplaChecagemMatricula().isBlank()).count();

        Map<String, Long> porCategoria = new LinkedHashMap<>();
        for (PharmaciaValidacao r : registros) {
            for (String categoria : categoriasDe(r)) {
                porCategoria.merge(categoria, 1L, Long::sum);
            }
        }

        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("periodoInicio", inicio.toLocalDate().toString());
        saida.put("periodoFim", fim.toLocalDate().toString());
        saida.put("unidade", unidade == null ? "todas" : unidade);
        saida.put("validacoes", registros.size());
        saida.put("comAlertaCritico", comCritico);
        saida.put("dispensacoesSuspensas", suspensas);
        saida.put("dispensacoesAlteradas", alteradas);
        saida.put("intervencoes", suspensas + alteradas);
        saida.put("confirmadasComJustificativa", comJustificativa);
        saida.put("comDuplaChecagem", comDuplaChecagem);
        saida.put("alertasPorCategoria", porCategoria);
        return saida;
    }

    /** Le as categorias dos alertas gravados, tolerando registro antigo ou malformado. */
    private List<String> categoriasDe(PharmaciaValidacao registro) {
        List<String> categorias = new ArrayList<>();
        if (registro.getAlertasJson() == null || registro.getAlertasJson().isBlank()) {
            return categorias;
        }
        try {
            AlertaFarmaceuticoDto[] alertas = mapper.readValue(registro.getAlertasJson(),
                    AlertaFarmaceuticoDto[].class);
            for (AlertaFarmaceuticoDto a : alertas) {
                if (a.getCategoria() != null && !a.getCategoria().isBlank()) {
                    categorias.add(a.getCategoria());
                }
            }
        } catch (Exception ignorado) {
            // Registro fora do formato atual nao invalida o indicador do periodo.
        }
        return categorias;
    }
}

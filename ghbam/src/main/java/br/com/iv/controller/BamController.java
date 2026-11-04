package br.com.iv.controller;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import br.com.iv.dto.BamDto;
import br.com.iv.entity.Bam;
import br.com.iv.service.BamService;

@Path("bam")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BamController {

	@Inject
	BamService bamService;

	@Inject
	br.com.iv.service.SituacaoPacienteService situacaoPacienteService;

	@GET
	@Path("/unidade/{yunidade}/inicio/{dt_inicio}/fim/{dt_fim}")
	public Response xlistarUnidadesPorDatas(@PathParam("yunidade") String yunidade,
			@PathParam("dt_inicio") String dt_inicio, @PathParam("dt_fim") String dt_fim) {

		LocalDate localDate1 = LocalDate.parse(dt_inicio);
		LocalDate localDate2 = LocalDate.parse(dt_fim);

		List<Bam> bam = bamService.filtrarPelaUnidadeDoBam(yunidade, localDate1, localDate2);
		return Response.ok(bam).build();
	}

	
	
	
	@GET
	@Path("/mapad1/{d1}/mapad2/{d2}/unidade/{unidade}")
	public Response filtarMapaGrafico(@PathParam("d1") LocalDate d1,
	                                  @PathParam("d2") LocalDate d2,
	                                  @PathParam("unidade") String unidade) {
		List<Bam> bams = bamService.filtarMapaGrafico(d1, d2, unidade);
		List<Map<String, Object>> marcadores = new java.util.ArrayList<>();
		for (Bam item : bams) {
			if (item.getPaciente() == null || item.getRegistro() == null) continue;
			Map<String, Object> paciente = new java.util.LinkedHashMap<>();
			paciente.put("nome", item.getPaciente().getNome());
			paciente.put("endereco", item.getPaciente().getEndereco());
			paciente.put("numero", item.getPaciente().getNumero());
			paciente.put("bairro", item.getPaciente().getBairro());
			paciente.put("municipio", item.getPaciente().getMunicipio());
			paciente.put("estado", item.getPaciente().getEstado());
			paciente.put("latitude", item.getPaciente().getLatitude());
			paciente.put("longitude", item.getPaciente().getLongitude());
			Map<String, Object> registro = Map.of("setor", String.valueOf(item.getRegistro().getSetor() == null ? "" : item.getRegistro().getSetor()));
			Map<String, Object> marcador = new java.util.LinkedHashMap<>();
			marcador.put("paciente", paciente);
			marcador.put("registro", registro);
			marcadores.add(marcador);
		}
		return Response.ok(marcadores).header("Cache-Control", "no-store, private").build();
	}


	@GET
	@Path("/numerobam/{numero}/unidade/{unidade}")
	public Response xlistarUnidades(@PathParam("numero") int numero, @PathParam("unidade") String unidade) {
		List<Bam> bam = bamService.filtarBamPorUnidade(numero, unidade);
		return Response.ok(bam).build();
	}

	@GET
	@Path("/idpaciente/{idpaciente}")
	public Response xlistarUnidades(@PathParam("idpaciente") int idpaciente) {
		List<Bam> bam = bamService.filtarBamPorIdPaciente(idpaciente);
		if (bam == null) {
			bam = Collections.emptyList();
		}
		return Response.ok(bam).build();
	}

	@GET
	@Path("/idregistro/{idregistro}")
	public Response listarPorIdRegistro(@PathParam("idregistro") int idregistro) {
		Bam bam = bamService.filtarPorUnicoRegistro(idregistro);
		if (bam == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity(Map.of("erro", "BAM nao localizado", "idregistro", idregistro))
					.build();
		}
		return Response.ok(bam).build();
	}

	@GET
	@Path("/idbam/{idbam}")
	public Response listarPorIdUnico(@PathParam("idbam") int idbam) {
		Bam bam = bamService.filtarPorUnicoBam(idbam);
		if (bam == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity(Map.of("erro", "BAM nao localizado", "idbam", idbam))
					.build();
		}
		return Response.ok(bam).build();
	}

	@GET
	@Path("/controle/{controle}")
	public Response listarPorControle(@PathParam("controle") String controle) {
		Bam bam = bamService.listarPorControle(controle);
		if (bam == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity(Map.of("erro", "BAM nao localizado", "controle", controle))
					.build();
		}
		return Response.ok(bam).build();
	}

	@GET
	@Path("/iddopaciente/{idpac}/unidade/{unidade}")
	public Response filtrarPorIdDoPcienteUltimoBam(@PathParam("idpac") int idpac, @PathParam("unidade") String unidade) {
		Bam bam = bamService.filtrarPorIdDoPcienteUltimoBam(idpac, unidade);
		if (bam == null) {
			return Response.ok(Map.of()).build();
		}
		return Response.ok(bam).build();
	}

	/** Situacao atual do paciente na unidade (busca do cabecalho): etapa, linha do tempo e pendencias. */
	@GET
	@Path("/situacaoatual/{idpac}/unidade/{unidade}")
	public Response situacaoAtual(@PathParam("idpac") int idpac, @PathParam("unidade") String unidade) {
		return Response.ok(situacaoPacienteService.situacao(idpac, unidade)).build();
	}

	@GET
	@Path("/idbam/{idbam}/novacor/{novacor}/idservidor/{idservidor}")
	public void atualizarCorNoBam(@PathParam("idbam") int idbam, @PathParam("novacor") int novacor,
			@PathParam("idservidor") int idservidor) {
		atualizarCorNoBamPorPatch(idbam, novacor, idservidor);
	}

	@PATCH
	@Path("/{idbam}/cor/{novacor}/servidor/{idservidor}")
	public void atualizarCorNoBamPorPatch(@PathParam("idbam") int idbam, @PathParam("novacor") int novacor,
			@PathParam("idservidor") int idservidor) {
		bamService.atualizarCorNoBam(idbam, novacor, idservidor);
	}

	@GET
	@Path("/idbamOrigem/{idbam}/origem/{origem}/destino/{destino}")
	public void atualizarOrigemDestinoTransferencia(@PathParam("idbam") int idbam, @PathParam("origem") String origem,
			@PathParam("destino") String destino) {
		bamService.atualizarOrigemDestinoTransferencia(idbam, origem, destino);
	}

	@GET
	@Path("/idbam/{idbam}/ematendimento/{ematendimento}")
	public void atualizarEmAtendimento(@PathParam("idbam") int idbam,
			@PathParam("ematendimento") boolean ematendimento) {
		atualizarEmAtendimentoPorPatch(idbam, ematendimento);
	}

	@PATCH
	@Path("/{idbam}/em-atendimento/{ematendimento}")
	public void atualizarEmAtendimentoPorPatch(@PathParam("idbam") int idbam,
			@PathParam("ematendimento") boolean ematendimento) {
		bamService.atualizarEmAtendimento(idbam, ematendimento);
	}

	@POST
	public Response salvarbam(BamDto dto) {
		Bam bam = bamService.salvarBam(dto);
		return Response.ok(bam).status(201).build();
	}

	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarBam(@PathParam("id") int id, BamDto dto) {
		bamService.atualizarBam(id, dto);
		return Response.status(204).build();
	}

}

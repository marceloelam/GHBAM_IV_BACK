package br.com.iv.controller;

import java.util.List;
import java.util.Collections;
import java.util.Map;

import br.com.iv.dto.AtendMedicoDTO;
import br.com.iv.dto.MunicipioCountDTO;
import br.com.iv.dto.PacienteDto;
import br.com.iv.dto.PacienteResultadoExameDto;
import br.com.iv.dto.PacientesAtendimentoDto;
import br.com.iv.dto.PacientesBamDto;
import br.com.iv.dto.PacientesDto;
import br.com.iv.dto.PacientesNirDto;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import br.com.iv.service.PacientesService;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.QueryParam;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.service.GeocodificacaoPacienteService;

@Path("pacientes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PacientesController {

	@Inject
	PacientesService pacientesService;

	@Inject
	SecurityPermissionService securityPermissionService;

	@Context
	ContainerRequestContext requestContext;

	/** Unidade da sessao (JWT) - base do municipio e da coordenada padrao do cadastro. */
	private String unidadeDaSessao() {
		return securityPermissionService.unidadeDaSessao(requestContext).split(":")[0].trim();
	}

	/** Municipios do estado para o select da recepcao (tabela local, sem internet). */
	@GET
	@Path("localidades/municipios")
	public Response listarMunicipios(@QueryParam("uf") String uf) {
		return Response.ok(GeocodificacaoPacienteService.listarMunicipios(uf)).build();
	}

	/** Bairros conhecidos do municipio; lista vazia = bairro digitado livremente. */
	@GET
	@Path("localidades/bairros")
	public Response listarBairros(@QueryParam("uf") String uf, @QueryParam("municipio") String municipio) {
		return Response.ok(GeocodificacaoPacienteService.listarBairros(uf, municipio)).build();
	}

	@GET
	public Response listarPacientes() {
		List<Pacientes> pacientes = pacientesService.listarPacientes();
		return Response.ok(pacientes).build();
	}

	// Direito de acesso/portabilidade (LGPD art. 18) — devolve os dados
	// cadastrais do paciente mais um resumo dos atendimentos (BAM), pra
	// atender pedido de "quero uma copia dos meus dados". Nao apaga nada —
	// exclusao/retencao de prontuario esbarra em prazo minimo obrigatorio
	// (CFM) e fica de fora de proposito, precisa de decisao juridica antes.
	// Restrito a kadm (ver RoutePermissionCatalog "pacientes/exportar") —
	// e o setor administrativo/ouvidoria quem atende esse tipo de pedido,
	// nao é autosservico do paciente (o sistema nao tem login de paciente).
	@GET
	@Path("exportar/{idpaciente}")
	public Response exportarDadosPaciente(@PathParam("idpaciente") int idpaciente) {
		Pacientes paciente = Pacientes.<Pacientes>findByIdOptional(idpaciente).orElse(null);
		if (paciente == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity("{\"erro\":\"Paciente nao encontrado\"}")
					.build();
		}

		List<Bam> atendimentos = Bam.list("paciente.idpaciente = ?1 order by data desc", idpaciente);
		List<Map<String, Object>> resumoAtendimentos = atendimentos.stream().map(b -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("idbam", b.getIdbam());
			item.put("data", b.getData());
			item.put("unidade", b.getUnidade());
			return item;
		}).collect(Collectors.toList());

		Map<String, Object> resposta = new LinkedHashMap<>();
		resposta.put("dadosCadastrais", paciente);
		resposta.put("atendimentos", resumoAtendimentos);
		resposta.put("totalAtendimentos", resumoAtendimentos.size());
		resposta.put("observacao",
				"Exportacao de dados pessoais (LGPD art. 18). Nao inclui prontuario clinico detalhado "
				+ "(evolucoes, prescricoes, exames) — solicitar via canal proprio se necessario.");

		return Response.ok(resposta).build();
	}

	@GET
	@Path("{nome}")
	public Response xlistarPacientes(@PathParam("nome") String nome) {
		PacientesDto dto = new PacientesDto();
		List<Pacientes> pacientes = pacientesService.xlocalizarPaciente(nome, dto);
		return Response.ok(pacientes).build();
	}

	// Devolve so a identificacao: quem localiza o paciente aqui nao precisa do cadastro nem do historico.
	@GET
	@Path("/por-prontuario/{prontuario}")
	public Response listarPorProntuario(@PathParam("prontuario") String prontuario) {
		List<Map<String, Object>> pacientes = pacientesService.localizarPorProntuario(prontuario).stream()
				.map(paciente -> {
					Map<String, Object> identificacao = new LinkedHashMap<>();
					identificacao.put("idpaciente", paciente.getIdpaciente());
					identificacao.put("nome", paciente.getNome());
					identificacao.put("idade", paciente.getIdade());
					identificacao.put("prontuario", paciente.getProntuario());
					return identificacao;
				})
				.collect(Collectors.toList());
		return Response.ok(pacientes).build();
	}

	@GET
	@Path("/recentes/{nome}/unidade/{unidade}")
	public Response listarPacientesComBamRecente(
			@PathParam("nome") String nome,
			@PathParam("unidade") String unidade) {
		List<Pacientes> pacientes = pacientesService.localizarPacientesComBamRecente(nome, unidade);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/cpfunico/{cpf}")
	public Response xlistarPacientesCPF(@PathParam("cpf") String cpf) {
		PacientesDto dto = new PacientesDto();
		Pacientes pacientes = pacientesService.xlocalizarPacientecpf(cpf, dto);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/rn/{nomern}")
	public Response xlistarPacientesRn(@PathParam("nomern") String nome) {
		PacientesDto dto = new PacientesDto();
		List<Pacientes> pacientes = pacientesService.xlocalizarPacienteRn(nome, dto);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/rnverificar/{nomemae}")
	public Response xlistarPacientesRnVerificar(@PathParam("nomemae") String nomemae) {
		PacientesDto dto = new PacientesDto();
		List<Pacientes> pacientes = pacientesService.xlocalizarPacienteRnVerificar(nomemae, dto);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/ematendimento/{nome}/unidade/{unidade}")
	public Response xlistarPacientesAtendimento(@PathParam("nome") String nome, @PathParam("unidade") String unidade) {
		List<PacientesAtendimentoDto> pacientes = pacientesService.medicoAtendendo(nome, unidade);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/aguardando/unidade/{unidade}")
	public Response xlistarPacientesAguardando(@PathParam("unidade") String unidade) {
		List<AtendMedicoDTO> pacientes = pacientesService.medicoAguardando(unidade);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/nir/{nome}")
	public Response xlistarPacientesNir(@PathParam("nome") String nome) {
		PacientesDto dto = new PacientesDto();
		List<Pacientes> pacientes = pacientesService.xlocalizarPacienteNir(nome, dto); 
		return Response.ok(pacientes).build();
	}

	@GET
	@Path("/paciente/{nome}/mae/{mae}")
	public Response xlistarPacientesEMae(@PathParam("nome") String nome, @PathParam("mae") String mae) {
		PacientesDto dto = new PacientesDto();
		List<Pacientes> pacientes = pacientesService.xlocalizarPacienteEMae(nome, mae, dto);
		return Response.ok(pacientes).build();
	}

	@GET
	@Path("/mae/{mae}")
	public Response listarPacientesPorMae(@PathParam("mae") String mae) {
		return Response.ok(pacientesService.localizarPorMae(mae)).build();
	}
	
	@GET
	@Path("/nomepaciente/{nome}")
	public Response xlistarPacientesNome(@PathParam("nome") String nome) {
		PacientesDto dto = new PacientesDto();
		List<Pacientes> pacientes = pacientesService.xlistarPacientesNome(nome, dto);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/unidade/{unidade}")
	public Response locaisDeTriagem(@PathParam("unidade") String unidade) {
		List<Pacientes> pacientes = pacientesService.locaisDeTriagem(unidade);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/unidade2/{unidade}/setor/{setor}")
	public Response locaisDeTriagem2(@PathParam("unidade") String unidade, @PathParam("setor") String setor) {
		List<PacienteDto> pacientes = pacientesService.locaisDeTriagem2(unidade, setor);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/unidade2t/{unidade}/setor/{setor}")
	public Response locaisDeTriagem2t(@PathParam("unidade") String unidade, @PathParam("setor") String setor) {
		List<PacienteDto> pacientes = pacientesService.locaisDeTriagem2(unidade, setor);
		return Response.ok(pacientes).build();
	}
	
	
	@GET
	@Path("/localizarPaciente/{bam}")
	public Response localizarPeloBam(@PathParam("bam") int bam) {
		List<Pacientes> pacientes = pacientesService.localizarPeloBam(bam);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/idpaciente/{idpaciente}")
	public Response pegarBamDoPacientePorId(@PathParam("idpaciente") int idpaciente) {
		Pacientes pacientes = pacientesService.PacientePorId(idpaciente);
		if (pacientes == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity(Map.of("erro", "Paciente nao localizado", "idpaciente", idpaciente))
					.build();
		}
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/fecharbam/{idpaciente}")
	public void fecharTodosOsBamAbertosDoPaciente(@PathParam("idpaciente") int idpaciente) {
		fecharBamAbertoDoPaciente(idpaciente);
	}

	@PATCH
	@Path("/{idpaciente}/fechamento-bam")
	public void fecharTodosOsBamAbertosDoPacientePorPatch(@PathParam("idpaciente") int idpaciente) {
		fecharBamAbertoDoPaciente(idpaciente);
	}

	private void fecharBamAbertoDoPaciente(int idpaciente) {
		pacientesService.fecharBamAbertoDoPaciente(idpaciente);
	}
	
	@GET
	@Path("/corrigirantigo")
	public void corrigirAntigo() {
		corrigirRegistrosAntigos();
	}

	@POST
	@Path("/correcao-registros-antigos")
	public void corrigirAntigoPost() {
		corrigirRegistrosAntigos();
	}

	private void corrigirRegistrosAntigos() {
		pacientesService.corrigirAntigo();
	}
	
	
	@GET
	@Path("/alterarIdade/{nomepaciente}") 
	public void alterarAIdade(@PathParam("nomepaciente") String nomepaciente) {
		atualizarIdadePaciente(nomepaciente);
	}

	@PATCH
	@Path("/{nomepaciente}/idade/atualizacao") 
	public void alterarAIdadePatch(@PathParam("nomepaciente") String nomepaciente) {
		atualizarIdadePaciente(nomepaciente);
	}

	private void atualizarIdadePaciente(String nomepaciente) {
		pacientesService.alterarAIdade(nomepaciente);
	}
	
	@GET
	@Path("/alterarIdadeTriagem/{unidade}") 
	public void alterarAIdadeNaTriagem(@PathParam("unidade") String unidade) {
		atualizarIdadeNaTriagem(unidade);
	}

	@PATCH
	@Path("/triagem/{unidade}/idade/atualizacao") 
	public void alterarAIdadeNaTriagemPatch(@PathParam("unidade") String unidade) {
		atualizarIdadeNaTriagem(unidade);
	}

	private void atualizarIdadeNaTriagem(String unidade) {
		pacientesService.alterarAIdadeNaTriagem(unidade);
	}
	

	@GET
	@Path("/cpf/{cpf}")
	public Response xlistarCPF(@PathParam("cpf") String cpf) {
		PacientesDto dto = new PacientesDto();
		List<Pacientes> pacientes = pacientesService.xlocalizarCPF(cpf, dto);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/controle/{controle}")
	public Response localizarIdPeloControle(@PathParam("controle") String controle) {
		PacientesDto dto = new PacientesDto();
		List<Pacientes> pacientes = pacientesService.xlocalizarControle(controle, dto);
		return Response.ok(pacientes).build();
	}
	
	
	@POST
	public Response salvarPacientes(PacientesDto dto) {
		Pacientes pacientes = pacientesService.salvarPaciente(dto, unidadeDaSessao());
		return Response.ok(pacientes).status(201).build();
	}

	@PUT
	@Path("{id}")
	public Response atualizarPaciente(@PathParam("id") int id, PacientesDto dto) {
		pacientesService.atualizarPaciente(id, dto, unidadeDaSessao());
		return Response.status(204).build();
	}

	@DELETE
	@Path("{id}")
	public Response removerPaciente(@PathParam("id") Long id, PacientesDto dto) {
		pacientesService.removePaciente(id);
		return Response.status(204).build();
	}
	
	//--------------------- NIR --------------------------
	@GET
	@Path("/porid/{id}/unidade/{unidade}")
	public Response filtragemParaNirPorId(@PathParam("id") int id,@PathParam("unidade") String unidade) {
		List<PacientesNirDto> pacientes = pacientesService.filtragemParaNirPorId(id, unidade);
		return Response.ok(pacientes).build();
	}
	@GET
	@Path("/nomenir/{nomenir}/unidade/{unidade}/tipo/{tipo}")
	public Response filtragemParaNir(@PathParam("nomenir") String nomenir,@PathParam("unidade") String unidade,@PathParam("tipo") String tipo) {
		List<PacientesNirDto> pacientes = pacientesService.filtragemParaNir(nomenir, unidade, tipo);
		return Response.ok(pacientes).build();
	}
	@GET
	@Path("/nomenirpainel/{unidade}")
	public Response filtragemParaNir(@PathParam("unidade") String unidade) {
		List<PacientesNirDto> pacientes = pacientesService.filtragemParaNirPainel(unidade);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/nomenirrestaurarinternacao/{nomenirrestaurarinternacao}/unidade/{unidade}/tipo/{tipo}")
	public Response filtragemParaNirResturarInternacao(@PathParam("nomenirrestaurarinternacao") String nomenirrestaurarinternacao,@PathParam("unidade") String unidade,@PathParam("tipo") String tipo) {
		List<PacientesNirDto> pacientes = pacientesService.filtragemParaNirResturarInternacao(nomenirrestaurarinternacao, unidade, tipo);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/nomenir2/{nomenir2}/unidade/{unidade}/local/{local}")
	public Response filtragemParaNir2(@PathParam("nomenir2") String nomenir2,@PathParam("unidade") String unidade, @PathParam("local") String local) {
		List<PacientesNirDto> pacientes = pacientesService.filtragemParaNir2(nomenir2, unidade, local);
		return Response.ok(pacientes).build();
	}
	
	
	
	@GET
	@Path("/internadonomenir/{nomenir}/unidade/{unidade}")
	public Response filtragemParaNirInternado(@PathParam("nomenir") String nomenir,@PathParam("unidade") String unidade) {
		List<PacientesNirDto> pacientes = pacientesService.filtragemParaNirInternado(nomenir, unidade);
		if (pacientes == null) {
			pacientes = Collections.emptyList();
		}
		return Response.ok(pacientes).build();
	}

	
	@GET
	@Path("/internadonomenirtodos/{nomenir}/unidade/{unidade}")
	public Response filtragemParaNirInternadoTodos(@PathParam("nomenir") String nomenir,@PathParam("unidade") String unidade) {
		List<PacientesNirDto> pacientes = pacientesService.filtragemParaNirInternadoTodos(nomenir, unidade);
		return Response.ok(pacientes).build();
	}

	
	
	
	@GET
	@Path("/alterarprontuario/{idpaciente}/pront/{pront}") 
	public void alterarProntuario(@PathParam("idpaciente") int idpaciente, @PathParam("pront") String pront) {
		pacientesService.alterarProntuario(idpaciente, pront);
	}

	@PATCH
	@Path("/{idpaciente}/prontuario/{pront}") 
	public Response atualizarProntuario(@PathParam("idpaciente") int idpaciente, @PathParam("pront") String pront) {
		pacientesService.alterarProntuario(idpaciente, pront);
		return Response.status(204).build();
	}
	
	@GET
	@Path("/resultados/{nome}/local/{local}/unidade/{unidade}")
	public Response listaResultadoExamesLaboratorio(@PathParam("nome") String nome, @PathParam("local") String local, @PathParam("unidade") String unidade) {
		List<PacienteResultadoExameDto> pacientes = pacientesService.listaResultadoExamesLaboratorio(nome, local, unidade);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("/unidademunicipio/{unidade}/datainicial/{d1}/datafinal/{d2}")
	public Response filtarPorPacienteMunicipio(
	        @PathParam("unidade") String unidade, 
	        @PathParam("d1") String d1, 
	        @PathParam("d2") String d2) {
	    
	    List<MunicipioCountDTO> resultado = pacientesService.filtarPorPacienteMunicipio(unidade, d1, d2);
	    return Response.ok(resultado).build();
	}
	
	//------------------------------------ Correção de bam --------------------------------------
	@GET
	@Path("correcaobam/{bam}/unidade/{unidade}")
	public Response corrigirbam(@PathParam("bam") String bam, @PathParam("unidade") String unidade) {
		List<PacientesNirDto> pacientes = pacientesService.corrigirbam(bam, unidade);
		return Response.ok(pacientes).build();
	}
	@GET
	@Path("correcaobam2/{id}/bam/{bam}/unidade/{unidade}")
	public Response corrigirbam2(@PathParam("id") int id, @PathParam("bam") String bam, @PathParam("unidade") String unidade) {
		List<PacientesBamDto> pacientes = pacientesService.corrigirbam2(id, bam, unidade);
		return Response.ok(pacientes).build();
	}
	
	@GET
	@Path("opcao1/bamdestino/{bamdestino}/idp1/{idpaciente1}/bamorigem/{bamorigem}/prontuario/{prontuario}/registro/{idregistro}/tratado/{idtratado}/controle/{controle}/unidade/{unidade}")
	public void opcao1(@PathParam("bamdestino") int bamdestino, @PathParam("idpaciente1") int idpaciente1,
			@PathParam("bamorigem") int bamorigem, @PathParam("prontuario") String prontuario, @PathParam("idregistro") int idregistro, @PathParam("idtratado") int idtratado, @PathParam("controle") String controle, @PathParam("unidade") String unidade) {
		 pacientesService.opcao1( bamdestino,  idpaciente1,  bamorigem,  prontuario,  idregistro,  idtratado,  controle,  unidade);
		
	}

	@POST
	@Path("correcao-bam/opcao1/bamdestino/{bamdestino}/idp1/{idpaciente1}/bamorigem/{bamorigem}/prontuario/{prontuario}/registro/{idregistro}/tratado/{idtratado}/controle/{controle}/unidade/{unidade}")
	public Response corrigirBamOpcao1(@PathParam("bamdestino") int bamdestino, @PathParam("idpaciente1") int idpaciente1,
			@PathParam("bamorigem") int bamorigem, @PathParam("prontuario") String prontuario, @PathParam("idregistro") int idregistro, @PathParam("idtratado") int idtratado, @PathParam("controle") String controle, @PathParam("unidade") String unidade) {
		 pacientesService.opcao1( bamdestino,  idpaciente1,  bamorigem,  prontuario,  idregistro,  idtratado,  controle,  unidade);
		 return Response.status(204).build();
	}
	
	@GET
	@Path("opcao2/bamorigem/{bamorigem}")
	public void opcao2(@PathParam("bamorigem") int bamorigem) {
		 pacientesService.opcao2(bamorigem);
		
	}

	@POST
	@Path("correcao-bam/opcao2/bamorigem/{bamorigem}")
	public Response corrigirBamOpcao2(@PathParam("bamorigem") int bamorigem) {
		 pacientesService.opcao2(bamorigem);
		 return Response.status(204).build();
	}
	
	@GET
	@Path("opcao3/bamorigem/{bamorigem}")
	public void opcao3(@PathParam("bamorigem") int bamorigem) {
		 pacientesService.opcao3(bamorigem);
		
	}

	@POST
	@Path("correcao-bam/opcao3/bamorigem/{bamorigem}")
	public Response corrigirBamOpcao3(@PathParam("bamorigem") int bamorigem) {
		 pacientesService.opcao3(bamorigem);
		 return Response.status(204).build();
	}
	
	@GET
	@Path("opcao4/bamorigem/{bamorigem}")
	public void opcao4(@PathParam("bamorigem") int bamorigem) {
		 pacientesService.opcao4(bamorigem);
		
	}

	@POST
	@Path("correcao-bam/opcao4/bamorigem/{bamorigem}")
	public Response corrigirBamOpcao4(@PathParam("bamorigem") int bamorigem) {
		 pacientesService.opcao4(bamorigem);
		 return Response.status(204).build();
	}

	@GET
	@Path("opcao5/bamultimo/{bamultimo}/idpac/{idpac}/unidade/{unidade}")
	public void opcao5(@PathParam("bamultimo") int bamultimo, @PathParam("idpac") int idpac, @PathParam("unidade") String unidade) {
		 pacientesService.opcao5( bamultimo,  idpac,  unidade);
		
	}

	@POST
	@Path("correcao-bam/opcao5/bamultimo/{bamultimo}/idpac/{idpac}/unidade/{unidade}")
	public Response corrigirBamOpcao5(@PathParam("bamultimo") int bamultimo, @PathParam("idpac") int idpac, @PathParam("unidade") String unidade) {
		 pacientesService.opcao5( bamultimo,  idpac,  unidade);
		 return Response.status(204).build();
	}
	
	@GET
	@Path("opcao7/bamorigem/{bamorigem}")
	public void opcao7(@PathParam("bamorigem") int bamorigem) {
		 pacientesService.opcao7(bamorigem);
		
	}

	@POST
	@Path("correcao-bam/opcao7/bamorigem/{bamorigem}")
	public Response corrigirBamOpcao7(@PathParam("bamorigem") int bamorigem) {
		 pacientesService.opcao7(bamorigem);
		 return Response.status(204).build();
	}

	// Devolver paciente a fila da triagem
	@POST
	@Path("correcao-bam/opcao8/bamorigem/{bamorigem}/unidade/{unidade}")
	public Response corrigirBamOpcao8(@PathParam("bamorigem") int bamorigem, @PathParam("unidade") String unidade) {
		 pacientesService.opcao8(bamorigem, unidade);
		 return Response.status(204).build();
	}

	// Devolver paciente a fila do medico
	@POST
	@Path("correcao-bam/opcao9/bamorigem/{bamorigem}/unidade/{unidade}")
	public Response corrigirBamOpcao9(@PathParam("bamorigem") int bamorigem, @PathParam("unidade") String unidade) {
		 pacientesService.opcao9(bamorigem, unidade);
		 return Response.status(204).build();
	}

	// Devolver exames de laboratorio a coleta
	@POST
	@Path("correcao-bam/opcao10/bamorigem/{bamorigem}/unidade/{unidade}")
	public Response corrigirBamOpcao10(@PathParam("bamorigem") int bamorigem, @PathParam("unidade") String unidade) {
		 int alterados = pacientesService.opcao10(bamorigem, unidade);
		 return Response.ok(java.util.Map.of("exames", alterados)).build();
	}
	
}

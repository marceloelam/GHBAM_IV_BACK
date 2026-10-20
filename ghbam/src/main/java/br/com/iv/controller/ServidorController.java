package br.com.iv.controller;
import java.util.Base64;
//YHWH é minha sabedoria. A YHWH consagro esse sistema
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
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.ws.rs.DELETE;

import org.jboss.logging.Logger;

import br.com.iv.dto.ServidorDto;
import br.com.iv.entity.Servidor;
import br.com.iv.repository.ServidorUnidadeRepository;
import br.com.iv.service.ServidorService;
import br.com.iv.security.LoginRateLimitService;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.util.UsuariosConectadosUtil;

@Path("servidor")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ServidorController {

	private static final Logger LOG = Logger.getLogger(ServidorController.class);

	@Inject
	ServidorService servidorService;

	@Inject
	LoginRateLimitService loginRateLimitService;

	@Inject
	SecurityPermissionService securityPermissionService;

	@Inject
	ServidorUnidadeRepository servidorUnidadeRepository;

	@Inject
	br.com.iv.service.ServidorMatriculaService servidorMatriculaService;

	@Inject
	br.com.iv.service.IdentificacaoFacialService identificacaoFacialService;

	@PATCH
	@Path("/{idservidor}/matricula")
	public Response alterarMatricula(@PathParam("idservidor") int idservidor,
			AlterarMatriculaRequest request, @Context ContainerRequestContext context) {
		AuthenticatedUser usuario = (AuthenticatedUser) context.getProperty("ghbam.user");
		if (usuario == null) usuario = securityPermissionService.resolve(context);
		return Response.ok(servidorMatriculaService.alterar(idservidor,
				request == null ? null : request.matriculaAnterior,
				request == null ? null : request.novaMatricula,
				request == null ? null : request.motivo, usuario)).build();
	}

	public static class AlterarMatriculaRequest {
		public String matriculaAnterior;
		public String novaMatricula;
		public String motivo;
	}

	@GET
	public Response listarServidor() {
		List<Servidor> servidor = servidorService.listarTodosOsServidores();
		return Response.ok(servidor).build();
	}
	
	@GET
	@Path("/especialistas/{unidade}")
	public Response listarEspecialistas( @PathParam("unidade") String unidade) {
		List<Servidor> servidor = servidorService.listarTodosOsEspecialistas(unidade);
		return Response.ok(servidor).build();
	}
	
	@GET
	@Path("/nome/{xservidor}/unidade/{unidade}")
	public Response xlistarSrvidor(@PathParam("xservidor") String xservidor, @PathParam("unidade") String unidade) {
		List<Servidor> servidor = servidorService.filtarPorServidor(xservidor, unidade);
		return Response.ok(servidor).build();
	}
	
	@GET
	@Path("/matricula/{matricula}/pw/{pwd}") 
	public void alterarSenha(@PathParam("matricula") String matricula, @PathParam("pwd") String pwd) {
		servidorService.alterarSenha(matricula, pwd);
		loginRateLimitService.registrarSucessoMatricula(matricula);
	}

	@PATCH
	@Path("/{matricula}/senha/{pwd}") 
	public Response atualizarSenha(@PathParam("matricula") String matricula, @PathParam("pwd") String pwd) {
		servidorService.alterarSenha(matricula, pwd);
		loginRateLimitService.registrarSucessoMatricula(matricula);
		return Response.status(204).build();
	}

	@PATCH
	@Path("/{matricula}/senha")
	public Response atualizarSenhaNoCorpo(@PathParam("matricula") String matricula, Map<String, String> dados) {
		String senha = dados == null ? null : dados.get("senha");
		if (matricula == null || matricula.isBlank() || senha == null || !senha.matches("^[1-9][0-9]{5}$")) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(Map.of("erro", "A senha deve conter seis digitos e nao pode comecar com zero.")).build();
		}
		servidorService.alterarSenha(matricula, senha);
		loginRateLimitService.registrarSucessoMatricula(matricula);
		return Response.noContent().build();
	}
	
	@GET
	@Path("/idser/{id}/ativo/{stat}") 
	public void alterarStatus(@PathParam("id") int id, @PathParam("stat") boolean stat) {
		servidorService.alterarStatus(id, stat); 
	}

	@PATCH
	@Path("/{id}/ativo/{stat}") 
	public Response atualizarStatus(@PathParam("id") int id, @PathParam("stat") boolean stat) {
		servidorService.alterarStatus(id, stat); 
		return Response.status(204).build();
	}
	
	// Unidades adicionais vinculadas ao servidor (servidor_unidade, V72), ate 3
	// no total incluindo a principal (servidor.unidade). Usado pelo cadastro
	// (Cad_servidores.jsx) pra permitir trabalhar em mais de uma unidade sem
	// precisar de um segundo cadastro.
	@GET
	@Path("/{idservidor}/unidades")
	public Response listarUnidadesVinculadas(@PathParam("idservidor") int idservidor) {
		List<String> unidades = servidorUnidadeRepository.listarSiglasPorServidor(idservidor);
		return Response.ok(java.util.Map.of("unidades", unidades)).build();
	}

	@PUT
	@Path("/{idservidor}/unidades")
	public Response definirUnidadesVinculadas(@PathParam("idservidor") int idservidor, UnidadesVinculadasRequest request) {
		List<String> unidades = request == null || request.unidades == null ? List.of() : request.unidades;
		if (unidades.size() > ServidorUnidadeRepository.LIMITE_UNIDADES_POR_SERVIDOR) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(java.util.Map.of("mensagem", "Maximo de " + ServidorUnidadeRepository.LIMITE_UNIDADES_POR_SERVIDOR + " unidades por servidor."))
					.build();
		}
		String divergente = servidorService.primeiraUnidadeDeMunicipioDivergente(unidades);
		if (divergente != null) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(java.util.Map.of("mensagem", "A unidade " + divergente + " e de outro municipio; nao pode ser vinculada a este servidor."))
					.build();
		}
		servidorUnidadeRepository.substituirVinculos(idservidor, unidades);
		return Response.ok(java.util.Map.of("unidades", servidorUnidadeRepository.listarSiglasPorServidor(idservidor))).build();
	}

	public static class UnidadesVinculadasRequest {
		public List<String> unidades;
	}

	// Define a unidade PRINCIPAL (servidor.unidade) - acao separada e
	// deliberada da edicao normal, porque re-escopa RBAC/login inteiro. A
	// principal antiga vira vinculo adicional automaticamente.
	@PUT
	@Path("/{idservidor}/unidade-principal")
	public Response definirUnidadePrincipal(@PathParam("idservidor") int idservidor, UnidadePrincipalRequest request) {
		String novaUnidade = request == null ? null : request.unidade;
		if (novaUnidade == null || novaUnidade.isBlank()) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(java.util.Map.of("mensagem", "Unidade nao informada."))
					.build();
		}
		Servidor atualizado;
		try {
			atualizado = servidorService.definirUnidadePrincipal(idservidor, novaUnidade);
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(java.util.Map.of("mensagem", e.getMessage()))
					.build();
		}
		if (atualizado == null) {
			return Response.status(Response.Status.NOT_FOUND)
					.entity(java.util.Map.of("mensagem", "Servidor nao encontrado."))
					.build();
		}
		return Response.ok(java.util.Map.of(
				"unidade", atualizado.getUnidade(),
				"unidades", servidorUnidadeRepository.listarSiglasPorServidor(idservidor)
		)).build();
	}

	public static class UnidadePrincipalRequest {
		public String unidade;
	}

	@GET
	@Path("/unidade/{yunidade}")
	public Response xlistarUnidades(@PathParam("yunidade") String yunidade) {
		ServidorDto dto = new ServidorDto();
		List<Servidor> servidor = servidorService.filtarPorUnidadeServidor(yunidade, dto);
		return Response.ok(servidor).build();
	}
	
	@GET
	@Path("/medico/{yunidade}")
	public Response xlistarMedicoUnidades(@PathParam("yunidade") String yunidade) {
		ServidorDto dto = new ServidorDto();
		List<Servidor> servidor = servidorService.filtarPorUnidadeMedico(yunidade, dto);
		return Response.ok(servidor).build();
	}

	@GET
	@Path("/medicos")
	public Response listarMedicos() {
		List<Servidor> medicos = servidorService.listarMedicos();
		return Response.ok(medicos).build();
	}

	@GET
	@Path("/certificado/{matricula}")
	public Response obterCertificado(@PathParam("matricula") String matricula) {
		Map<String, String> resultado = servidorService.obterCertificadoPorMatricula(matricula);
		if (resultado == null) return Response.noContent().build();
		return Response.ok(resultado).build();
	}
	
	@GET
	@Path("/idservidor/{idservidor}")
	public Response unicoServidorPorId(@PathParam("idservidor") int idservidor) {
		Servidor servidor = servidorService.servidorId(idservidor);
		return Response.ok(servidor).build();
	}
	
	// ... dentro do método do resource:
	@GET
	@Path("/matricula/{matricula}")
	public Response unicoServidor(
		@PathParam("matricula") String matricula
	) {
		ServidorDto dto = new ServidorDto();

		Servidor servidor = servidorService.servidorMatricula(matricula, dto);
		
		if (servidor == null) {
			return Response.status(Response.Status.NOT_FOUND)
				.entity("{\"erro\":\"Servidor com matrícula " + matricula + " não encontrado\"}")
				.build();
		}
		   
		String unidade = servidor.getUnidade();
		String usuario = servidor.getNome();
		String nome = usuario.length() > 15 ? usuario.substring(0, 15) : usuario;
		String cargo = servidor.getCargo();
		String especialidade = servidor.getEspecialidade();
		String foto = servidor.getFoto();
		String matriculaStr = servidor.getMatricula() != null ? servidor.getMatricula().toString() : matricula;

		UsuariosConectadosUtil.adicionarUsuario(unidade, nome, matriculaStr, cargo, especialidade, foto);

		return Response.ok(servidor).build();
	}

	@POST
	@Path("/validar-credencial")
	public Response validarCredencial(CredencialDTO credencial, @Context ContainerRequestContext requestContext) {
		if (credencial == null || credencial.matricula == null || credencial.senha == null
				|| credencial.matricula.isBlank() || credencial.senha.isBlank()) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("{\"erro\":\"Matrícula e senha são obrigatórias.\"}")
					.build();
		}

		// Reconfirmacao de senha vale so para o proprio usuario logado; impede
		// usar este endpoint para testar senha de outra matricula.
		AuthenticatedUser logado = (AuthenticatedUser) requestContext.getProperty("ghbam.user");
		if (logado == null || logado.matricula().isBlank()
				|| !logado.matricula().trim().equalsIgnoreCase(credencial.matricula.trim())) {
			return Response.status(Response.Status.FORBIDDEN)
					.entity("{\"erro\":\"A matrícula informada não é a do usuário logado.\"}")
					.build();
		}

		long bloqueadoPor = loginRateLimitService.segundosBloqueadoMatricula(credencial.matricula);
		if (bloqueadoPor > 0) {
			return Response.status(429)
					.entity("{\"erro\":\"Muitas tentativas. Aguarde antes de tentar novamente.\"}")
					.build();
		}

		AuthenticatedUser usuario = securityPermissionService.authenticate(credencial.matricula, credencial.senha);
		if (usuario == null) {
			loginRateLimitService.registrarFalhaMatricula(credencial.matricula);
			return Response.status(Response.Status.UNAUTHORIZED)
					.entity("{\"erro\":\"Credencial inválida.\"}")
					.build();
		}

		loginRateLimitService.registrarSucessoMatricula(credencial.matricula);
		return Response.noContent().build();
	}
	
	

	@GET
	@Path("/nome/{nome}/unidade/{unidade}")
	public Response unicoServidor(@PathParam("nome") String nome, @PathParam("unidade") String unidade) {
		ServidorDto dto = new ServidorDto();
		Servidor servidor = servidorService.findByNomeDoMedico(nome,unidade, dto);
		return Response.ok(servidor).build();
	}
	
	
	
	@POST
	public Response salvarSetores(ServidorDto dto) {
		try {
			Servidor servidor = servidorService.salvarServidor(dto);
			return Response.ok(servidor).status(201).build();
		} catch (IllegalStateException e) {
			return Response.status(Response.Status.CONFLICT)
				.entity("{\"erro\":\"" + e.getMessage() + "\"}")
				.build();
		}
	}
	
	@PUT
	@Path("/atualizar/{id}")
	public Response atualizarSetor(@PathParam("id") int id, ServidorDto dto) {
		try {
			if (dto == null) {
				return Response.status(400).entity("Corpo da requisição não recebido ou malformado").build();
			}
			servidorService.atualizarServidor(id, dto);
			if (dto.getPwd() != null && !dto.getPwd().isBlank() && dto.getMatricula() != null) {
				loginRateLimitService.registrarSucessoMatricula(dto.getMatricula());
			}
			return Response.status(204).build();
		} catch (Exception e) {
			String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			LOG.errorf("Erro ao atualizar servidor id=%d: %s", id, msg);
			return Response.status(500).entity("Erro ao atualizar servidor: " + msg).build();
		}
	}

	@DELETE
	@Path("/remover/{id}")
	public Response removerSetor(@PathParam("id") Long id, ServidorDto dto) {
		servidorService.removeServidor(id);
		return Response.status(204).build();
	}
	
	
	@GET
    @Path("/{id}/foto")
    @Produces("image/jpeg")
    public Response getFoto(@PathParam("id") Long id) {
		// Buscar base64 do campo foto no banco via service
		String fotoBase64 = servidorService.getFotoBase64(id);

		if (fotoBase64 == null || fotoBase64.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Remover prefixo data URI se existir (data:image/jpeg;base64,)
		String base64Limpo = fotoBase64.trim();
		int comma = base64Limpo.indexOf(',');
		if (comma >= 0 && comma < base64Limpo.length() - 1) {
			base64Limpo = base64Limpo.substring(comma + 1);
		}

		try {
			byte[] fotoBytes = Base64.getDecoder().decode(base64Limpo);
			return Response.ok(fotoBytes).build();
		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
				.entity("{\"erro\":\"Foto com formato inválido\"}")
				.build();
		}
	}

	/**
	 * Retorna lista de servidores ativos com suas fotos em base64
	 * Para ser usado pelo frontend no reconhecimento facial
	 */
	@GET
	@Path("/fotos-ativas")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getFotosServidoresAtivos() {
		List<ServidorService.ServidorFotoInfo> fotosInfo = servidorService.listarFotosServidoresAtivos();
		// Converter para DTO do controller e normalizar fotos
		List<ServidorFotoDTO> fotos = fotosInfo.stream()
			.map(info -> {
				String foto = info.getFoto();
				// Garantir que a foto tenha o prefixo correto e não duplicado
				if (foto != null && !foto.isEmpty()) {
					// Remover qualquer prefixo existente
					int comma = foto.indexOf(',');
					if (comma >= 0 && comma < foto.length() - 1) {
						foto = foto.substring(comma + 1).trim();
					}
					// Adicionar prefixo padronizado
					foto = "data:image/jpeg;base64," + foto;
				}
				return new ServidorFotoDTO(info.getId(), info.getNome(), info.getMatricula(), foto, info.getCargo(), info.getEspecialidade(), info.getUnidade());
			})
			.collect(java.util.stream.Collectors.toList());
		return Response.ok(fotos).build();
	}

	@POST
	@Path("/identificar")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response identificarServidor(ImagemDTO dto) {
		if (dto == null || dto.imagem == null || dto.imagem.isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST)
				.entity("{\"erro\":\"Imagem não informada\"}")
				.build();
		}

		// Identificacao 1:N no CompreFace da VPS (servico Recognition).
		br.com.iv.service.IdentificacaoFacialService.ResultadoIdentificacao resultado = identificacaoFacialService.identificar(dto.imagem);
		if (resultado.erro() != null) {
			return Response.status(Response.Status.SERVICE_UNAVAILABLE)
				.entity(Map.of("erro", resultado.erro()))
				.build();
		}

		Map<String, Object> corpo = new java.util.HashMap<>();
		corpo.put("identificado", resultado.identificado());
		corpo.put("rostoDetectado", resultado.rostoDetectado());
		corpo.put("similaridade", resultado.similaridade());
		Servidor servidor = resultado.servidor();
		if (servidor != null) {
			ServidorDTO info = new ServidorDTO(servidor.getNome(), servidor.getMatricula(), Long.valueOf(servidor.getIdservidor()), servidor.getCargo(), servidor.getEspecialidade(), servidor.getUnidade());
			String foto = servidor.getFoto();
			if (foto != null && !foto.isBlank()) {
				int virgula = foto.indexOf(',');
				info.foto = "data:image/jpeg;base64," + (virgula >= 0 ? foto.substring(virgula + 1).trim() : foto.trim());
			}
			corpo.put("servidor", info);
		}
		return Response.ok(corpo).build();
	}

	@GET
	@Path("/identificacao-facial/status")
	@Produces(MediaType.APPLICATION_JSON)
	public Response statusIdentificacaoFacial() {
		return Response.ok(identificacaoFacialService.getStatus()).build();
	}

	@POST
	@Path("/identificacao-facial/sincronizar")
	@Produces(MediaType.APPLICATION_JSON)
	public Response sincronizarIdentificacaoFacial() {
		if (!identificacaoFacialService.configurado()) {
			return Response.status(Response.Status.SERVICE_UNAVAILABLE)
				.entity(Map.of("erro", "Identificacao facial nao configurada no servidor."))
				.build();
		}
		boolean iniciada = identificacaoFacialService.iniciarSincronizacao();
		return Response.ok(Map.of("iniciada", iniciada, "status", identificacaoFacialService.getStatus())).build();
	}

	public static class ImagemDTO {
		public String imagem;
		public String imagemFace;
		public FaceBox faceBox;
	}

	public static class CredencialDTO {
		public String matricula;
		public String senha;
	}

	public static class FaceBox {
		public Integer x;
		public Integer y;
		public Integer width;
		public Integer height;
	}

	public static class ServidorFotoDTO {
		public Long id;
		public String nome;
		public String matricula;
		public String foto;
		public String cargo;
		public String especialidade;
		public String unidade;
		
		public ServidorFotoDTO(Long id, String nome, String matricula, String foto, String cargo, String especialidade, String unidade) {
			this.id = id;
			this.nome = nome;
			this.matricula = matricula;
			this.foto = foto;
			this.cargo = cargo;
			this.especialidade = especialidade;
			this.unidade = unidade;
		}
	}

	public static class ServidorDTO {
		public String nome;
		public String matricula;
		public Long id;
		public String cargo;
		public String unidade;
		public String especialidade;
		public String foto;

		public ServidorDTO(String nome, String matricula, Long id, String cargo, String especialidade, String unidade) {
			this.nome = nome;
			this.matricula = matricula;
			this.id = id;
			this.cargo = cargo;
			this.especialidade = especialidade;
			this.unidade = unidade;
		}
	}
}

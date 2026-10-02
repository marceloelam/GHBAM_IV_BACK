package br.com.iv.controller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
	    List<Bam> bam = bamService.filtarMapaGrafico(d1, d2, unidade);

	    try {
	        // Identificar se é Windows ou Linux
	        String osName = System.getProperty("os.name").toLowerCase();
	        String baseDir;

	        if (osName.contains("win")) {
	            baseDir = "C:\\nginx\\html\\ghbam";
	        } else {
	            baseDir = "/var/www/html";
	        }

	        System.out.println("Sistema operacional detectado: " + osName);
	        System.out.println("Usando diretório: " + baseDir);

	        File directory = new File(baseDir);

	        // DEBUG: Verificar se o diretório existe e permissões
	        System.out.println("Diretório existe: " + directory.exists());
	        System.out.println("Caminho absoluto: " + directory.getAbsolutePath());
	        System.out.println("Pode ler: " + directory.canRead());
	        System.out.println("Pode escrever: " + directory.canWrite());
	        System.out.println("Pode executar: " + directory.canExecute());

	        if (!directory.exists()) {
	            System.out.println("❌ Diretório não existe: " + baseDir);
	            System.out.println("Tentando criar diretório...");
	            boolean criado = directory.mkdirs();
	            if (criado) {
	                System.out.println("✅ Diretório criado com sucesso!");
	                if (!osName.contains("win")) {
	                    try {
	                        Process process = Runtime.getRuntime().exec(new String[]{"chmod", "777", baseDir});
	                        int exitCode = process.waitFor();
	                        if (exitCode == 0) {
	                            System.out.println("✅ Permissões concedidas ao diretório");
	                        } else {
	                            System.out.println("❌ Falha ao definir permissões. Código: " + exitCode);
	                        }
	                    } catch (Exception e) {
	                        System.out.println("❌ Erro ao alterar permissões: " + e.getMessage());
	                    }
	                }
	            } else {
	                System.out.println("❌ Falha ao criar diretório. Usando fallback...");
	                baseDir = System.getProperty("user.dir") + File.separator + "webapp";
	                directory = new File(baseDir);
	                if (directory.mkdirs()) {
	                    System.out.println("📁 Usando diretório alternativo: " + baseDir);
	                }
	            }
	        }

	        if (!directory.canWrite()) {
	            System.out.println("⚠️  AVISO: Sem permissão de escrita no diretório!");
	            if (!osName.contains("win")) {
	                try {
	                    Process process = Runtime.getRuntime().exec(new String[]{"chmod", "777", baseDir});
	                    int exitCode = process.waitFor();
	                    if (exitCode == 0) {
	                        System.out.println("✅ Permissões corrigidas");
	                    } else {
	                        System.out.println("❌ Não foi possível corrigir permissões");
	                    }
	                } catch (Exception e) {
	                    System.out.println("❌ Erro ao corrigir permissões: " + e.getMessage());
	                }
	            }
	        }

	        // Caminho do arquivo
	        String arquivo = baseDir + File.separator + "marcas.xml";
	        File arquivoFile = new File(arquivo);

	        System.out.println("📄 Arquivo será criado em: " + arquivoFile.getAbsolutePath());

	        if (arquivoFile.exists()) {
	            System.out.println("📄 Arquivo anterior existe, apagando...");
	            boolean deletado = arquivoFile.delete();
	            if (deletado) {
	                System.out.println("✅ Arquivo anterior apagado");
	            } else {
	                System.out.println("❌ Não foi possível apagar o arquivo existente");
	                try {
	                    Thread.sleep(100);
	                    deletado = arquivoFile.delete();
	                    if (deletado) {
	                        System.out.println("✅ Arquivo apagado na segunda tentativa");
	                    }
	                } catch (InterruptedException ie) {
	                    Thread.currentThread().interrupt();
	                }
	            }
	        }

	        // Criar o arquivo XML
	        System.out.println("🛠️  Criando novo XML...");
	        try (FileOutputStream fos = new FileOutputStream(arquivoFile);
	             OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
	             BufferedWriter out = new BufferedWriter(osw)) {

	            out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
	            out.write("<markers>\n");
	            for (Bam baml : bam) {
	                out.write("<marker name=\"" + baml.getPaciente().getNome() + "\" address=\""
	                        + baml.getPaciente().getEndereco() + ", " + baml.getPaciente().getNumero() + " - "
	                        + baml.getPaciente().getBairro() + ", " + baml.getPaciente().getMunicipio() + ", "
	                        + baml.getPaciente().getEstado() + "\" lat=\"" + baml.getPaciente().getLatitude()
	                        + "\" lng=\"" + baml.getPaciente().getLongitude() + "\" type=\""
	                        + baml.getRegistro().getSetor() + "\" />\n");
	            }
	            out.write("</markers>");
	            out.flush();

	            System.out.println("✅ Conteúdo XML escrito com sucesso");
	        }

	        if (!osName.contains("win")) {
	            try {
	                Process process = Runtime.getRuntime().exec(new String[]{"chmod", "777", arquivo});
	                int exitCode = process.waitFor();
	                if (exitCode == 0) {
	                    System.out.println("✅ Permissões 777 definidas para o arquivo");
	                } else {
	                    System.out.println("❌ Falha ao definir permissões do arquivo");
	                }
	            } catch (Exception e) {
	                System.out.println("❌ Erro ao definir permissões: " + e.getMessage());
	            }
	        }

	        if (arquivoFile.exists()) {
	            System.out.println("🎉 XML criado com sucesso!");
	            System.out.println("📍 Local: " + arquivoFile.getAbsolutePath());
	            System.out.println("📏 Tamanho: " + arquivoFile.length() + " bytes");
	            System.out.println("👁️  Pode ler: " + arquivoFile.canRead());
	            System.out.println("✏️  Pode escrever: " + arquivoFile.canWrite());

	            if (!osName.contains("win")) {
	                try {
	                    Process process = Runtime.getRuntime().exec(new String[]{"ls", "-l", arquivo});
	                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
	                    String line;
	                    while ((line = reader.readLine()) != null) {
	                        System.out.println("🔍 Permissões do arquivo: " + line);
	                    }
	                    process.waitFor();
	                } catch (Exception e) {
	                    System.out.println("❌ Não foi possível verificar permissões detalhadas");
	                }
	            }
	        } else {
	            System.out.println("❌ ERRO: Arquivo não foi criado!");
	            return Response.serverError().entity("Erro: arquivo não foi criado!").build();
	        }

	        // ✅ Sucesso → retorna 200
	        return Response.ok("Arquivo marcas.xml gerado com sucesso!").build();

	    } catch (Exception e) {
	        e.printStackTrace();
	        return Response.serverError()
	                       .entity("💥 Erro crítico ao gerar XML: " + e.getMessage())
	                       .build();
	    }
	}

	// Método auxiliar para escape de XML (opcional, mas recomendado)
	private String escapeXml(String input) {
	    if (input == null) return "";
	    return input.replace("&", "&amp;")
	                .replace("<", "&lt;")
	                .replace(">", "&gt;")
	                .replace("\"", "&quot;")
	                .replace("'", "&apos;");
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

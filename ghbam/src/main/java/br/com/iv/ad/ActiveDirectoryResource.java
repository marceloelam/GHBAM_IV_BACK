package br.com.iv.ad;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@Path("/ad")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ActiveDirectoryResource {

    @Inject
    ActiveDirectoryService adService;

    public static class Credenciais {
        public String usuario;
        public String senha;
    }

    @POST
    @Path("/verificar")
    public Response verificarUsuario(Credenciais credenciais) {
        if (credenciais == null || credenciais.usuario == null || credenciais.senha == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"status\":\"Credenciais ausentes\"}")
                    .build();
        }

        String usuario = credenciais.usuario.trim();
        String senha = credenciais.senha;

        boolean valido = adService.autenticar(usuario, senha);

        if (valido) {
            // Buscar atributos do usuário no AD
            Map<String, String> atributos = adService.buscarAtributos(usuario, senha);
            // Criar resposta com os principais atributos
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("status", "Usuario válido no AD");
            resposta.put("usuario", usuario);
            resposta.put("cn", atributos.getOrDefault("cn", ""));
            resposta.put("mail", atributos.getOrDefault("mail", ""));
            resposta.put("memberOf", atributos.getOrDefault("memberOf", ""));
            resposta.put("description", atributos.getOrDefault("description", ""));
            resposta.put("department", atributos.getOrDefault("department", ""));
            resposta.put("dn", atributos.getOrDefault("distinguishedName", ""));
            resposta.put("userAccountControl", atributos.getOrDefault("userAccountControl", ""));
            /*
             O valor total do userAccountControl é a soma dos valores das flags aplicadas. Aqui estão os mais usados:

					Valor Decimal	Valor Hex	Nome da Flag	Descrição
					512	0x200	NORMAL_ACCOUNT	Conta de usuário padrão (ativa).
					2	0x2	ACCOUNTDISABLE	A conta está desabilitada.
					65536	0x10000	DONT_EXPIRE_PASSWORD	A senha nunca expira.
					32	0x20	PASSWD_NOTREQD	Não é necessária senha (raro e perigoso).
					64	0x40	PASSWD_CANT_CHANGE	O usuário não pode alterar a senha.
					128	0x80	ENCRYPTED_TEXT_PWD_ALLOWED	Senha armazenada com criptografia reversível (inseguro, necessário para CHAP).
					4096	0x1000	WORKSTATION_TRUST_ACCOUNT	Conta de computador que pertence ao domínio.
					8192	0x2000	SERVER_TRUST_ACCOUNT	Conta de Controlador de Domínio.
					66048	0x10200	NORMAL_ACCOUNT + DONT_EXPIRE_PASSWORD	Conta normal com senha que nunca expira.
					544	0x220	NORMAL_ACCOUNT + PASSWD_NOTREQD + PASSWD_CANT_CHANGE	Conta normal sem senha necessária e que não pode ser alterada.
					8388608	0x800000	PASSWORD_EXPIRED	Senha expirada (o usuário deve alterá-la no próximo logon).
					1048576	0x100000	TRUSTED_TO_AUTH_FOR_DELEGATION	Conta confiável para delegação.
					
					Exemplos Práticos
						Usuário padrão ativo: 512
						Usuário desabilitado: 514 (512 + 2)
						Usuário com senha que nunca expira: 66048 (512 + 65536)
						Conta de computador: 4096
             */
            
            
            return Response.ok(resposta).build();
        }

        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"status\":\"Falha na autenticação\"}")
                .build();
    }

    public static class VerificacaoUsuario {
        public String usuario;
    }

    @POST
    @Path("/verificar-status")
    public Response verificarStatusUsuario(VerificacaoUsuario verificacao) {
        if (verificacao == null || verificacao.usuario == null || verificacao.usuario.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"Usuário não informado\"}")
                    .build();
        }

        String usuario = verificacao.usuario.trim();
        
        Map<String, Object> resultado = adService.verificarStatusUsuario(usuario);
        
        // Criar resposta simplificada
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("usuario", usuario);
        resposta.put("existe", resultado.get("existe"));
        resposta.put("ativo", resultado.get("ativo"));
        resposta.put("userAccountControl", resultado.get("userAccountControl"));
        
        if (!resultado.get("userAccountControl").toString().isEmpty()) {
            try {
                int uac = Integer.parseInt(resultado.get("userAccountControl").toString());
                resposta.put("descricao", interpretarUserAccountControl(uac));
            } catch (NumberFormatException e) {
                resposta.put("descricao", "Valor inválido");
            }
        }
        
        return Response.ok(resposta).build();
    }
    
    private String interpretarUserAccountControl(int uac) {
        List<String> flags = new ArrayList<>();
        
        if ((uac & 2) != 0) flags.add("DESABILITADA");
        else flags.add("ATIVA");
        
        if ((uac & 65536) != 0) flags.add("SENHA_NAO_EXPIRA");
        if ((uac & 32) != 0) flags.add("SENHA_NAO_REQUERIDA");
        if ((uac & 64) != 0) flags.add("SENHA_NAO_PODE_MUDAR");
        if ((uac & 8388608) != 0) flags.add("SENHA_EXPIRADA");
        
        return String.join(", ", flags);
    }

    @GET
    @Path("/usuariosPorOu") //http://localhost:8080/ad/usuariosPorOu?ou=OU=Vila Geny,OU=UBS,OU=SMS,OU=Prefeitura,DC=itg,DC=rio
    public Response listarUsuariosPorOu(@QueryParam("ou") String ou) {
        if (ou == null || ou.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"erro\":\"OU não informado\"}")
                    .build();
        }
        List<Map<String, String>> usuarios = adService.listarUsuariosPorOU(ou);
        return Response.ok(usuarios).build();
    }

    @GET
    @Path("/organizationalUnits") //http://localhost:8080/ad/organizationalUnits?base=dc=itg,dc=rio
    public Response listarOrganizationalUnits(@QueryParam("base") String base) {
        // Se base não informado, usa o domínio padrão
        String baseDN = (base == null || base.trim().isEmpty()) ? adService.buildBaseDN(adService.ldapDomain) : base.trim();
        List<String> ous = adService.listarOUs(baseDN);
        return Response.ok(ous).build();
    }

    @PUT
    @Path("/atualizarUsuarioAdServidor") //http://localhost:8080/ad/atualizarUsuarioAdServidor?base=OU=Vila Geny,OU=UBS,OU=SMS,OU=Prefeitura,DC=itg,DC=rio
    public Response atualizarUsuarioAdServidor(@QueryParam("base") String base) {
        String baseDN = (base == null || base.trim().isEmpty()) ? adService.buildBaseDN(adService.ldapDomain) : base.trim();
        List<Map<String, String>> usuarios = adService.listarUsuariosPorOU(baseDN);
        int atualizados = 0;
        int pulados = 0;
        for (Map<String, String> usuario : usuarios) {
            String cn = usuario.get("cn");
            String usuarioAd = usuario.get("usuario");
            if (cn != null && usuarioAd != null) {
                int count = adService.atualizarUsuarioAdServidorPorNome(cn.toUpperCase(), usuarioAd);
                if (count > 0) {
                    atualizados += count;
                } else {
                    pulados++;
                }
            } else {
                pulados++;
            }
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("atualizados", atualizados);
        resp.put("nao_encontrados", pulados);
        return Response.ok(resp).build();
    }

    @PUT
    @Path("/atualizarUsuarioAdServidorPorDescription")
    public Response atualizarUsuarioAdServidorPorDescription(@QueryParam("base") String base) {
        String baseDN = (base == null || base.trim().isEmpty()) ? adService.buildBaseDN(adService.ldapDomain) : base.trim();
        List<Map<String, String>> usuarios = adService.listarUsuariosPorOU(baseDN);
        int atualizados = 0;
        int pulados = 0;
        for (Map<String, String> usuario : usuarios) {
            String description = usuario.get("description");
            String usuarioAd = usuario.get("usuario");
            if (description != null && usuarioAd != null) {
                int count = adService.atualizarUsuarioAdServidorPorNome2(description.toUpperCase(), usuarioAd);
                if (count > 0) {
                    atualizados += count;
                } else {
                    pulados++;
                }
            } else {
                pulados++;
            }
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("atualizados", atualizados);
        resp.put("nao_encontrados", pulados);
        return Response.ok(resp).build();
    }
}

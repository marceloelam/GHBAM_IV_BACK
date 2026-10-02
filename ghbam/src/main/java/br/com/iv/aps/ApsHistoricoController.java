package br.com.iv.aps;

import static br.com.iv.aps.ApsDb.p;
import static br.com.iv.aps.ApsHttp.executar;
import static br.com.iv.aps.ApsHttp.pode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Atendimentos da Atencao Primaria (G-PEC) no Historico Completo do Paciente.
 * Fica sob "historicopaciente" de proposito: mesmo publico (RoutePermissionCatalog) e mesma
 * auditoria de leitura clinica do Prontuario Completo. Mostra TODAS as unidades - o historico
 * e longitudinal, diferente das telas do G-PEC, que sao por unidade logada. So leitura.
 */
@Path("historicopaciente/aps")
@Produces(MediaType.APPLICATION_JSON)
public class ApsHistoricoController {

    /** Mesmo publico da entrada "historicopaciente" do RoutePermissionCatalog. */
    private static final String[] PUBLICO_HISTORICO = { "krecepcao", "ktriagem", "kmedico", "kenfermagem", "kfaturamento",
            "presumopaciente", "pcomoestaopaciente", "pprontuariocompleto", "penfermariaseleitos", "pnir", "pccih", "kadm" };

    @Inject
    SecurityPermissionService sps;
    @Inject
    ApsDb db;
    @Inject
    ApsReferenciaService referencias;

    @Context
    ContainerRequestContext ctx;

    @GET
    @Path("/paciente/{idpaciente}")
    public Response porPaciente(@PathParam("idpaciente") Integer idPaciente) {
        AuthenticatedUser user = ApsHttp.usuario(ctx, sps);
        if (!pode(user, PUBLICO_HISTORICO)) {
            return ApsHttp.proibido("Historico do paciente");
        }
        return executar(() -> historico(idPaciente));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> historico(Integer idPaciente) {
        if (idPaciente == null) {
            throw new IllegalArgumentException("Paciente nao informado.");
        }
        List<Map<String, Object>> atendimentos = db.list("""
                SELECT a.id, a.tipo, a.unidade, a.profissional, a.cbo, a.inicio, a.fim, a.registro_tardio,
                       a.data_referencia, a.soap, a.dados
                FROM aps_atendimento a
                JOIN aps_cidadao c ON c.id = a.id_cidadao
                WHERE c.id_paciente = :p AND a.status = 'FINALIZADO'
                ORDER BY a.inicio DESC
                LIMIT 300
                """, p("p", idPaciente));
        List<Map<String, Object>> outAtend = new ArrayList<>();
        for (Map<String, Object> a : atendimentos) {
            Map<String, Object> dados = a.get("dados") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.get("id"));
            item.put("tipo", a.get("tipo"));
            item.put("unidade", a.get("unidade"));
            item.put("profissional", a.get("profissional"));
            item.put("cbo", a.get("cbo"));
            item.put("inicio", a.get("inicio"));
            item.put("dataReferencia", a.get("data_referencia"));
            item.put("registroTardio", a.get("registro_tardio"));
            item.put("soap", a.get("soap"));
            item.put("problemas", dados.getOrDefault("problemasAvaliados", List.of()));
            item.put("medicoes", dados.getOrDefault("medicoes", Map.of()));
            item.put("medicamentos", dados.getOrDefault("medicamentos", List.of()));
            item.put("procedimentos", dados.getOrDefault("procedimentos", List.of()));
            item.put("condutas", descricoes("condutaencaminhamento", dados.get("condutas")));
            outAtend.add(item);
        }
        List<Map<String, Object>> vacinas = db.list("""
                SELECT v.id, v.unidade, v.tipo_registro, v.data_aplicacao, v.imunobiologico, v.dose, v.estrategia,
                       v.lote, v.fabricante, v.profissional, v.cbo, v.criado_em
                FROM aps_vacinacao v
                JOIN aps_cidadao c ON c.id = v.id_cidadao
                WHERE c.id_paciente = :p AND NOT v.excluido AND v.tipo_registro IN ('APLICACAO', 'TRANSCRICAO')
                ORDER BY v.data_aplicacao DESC NULLS LAST
                LIMIT 300
                """, p("p", idPaciente));
        for (Map<String, Object> v : vacinas) {
            v.put("imunobiologicoNome", referencias.descricao("imunobiologico", v.get("imunobiologico")));
            v.put("doseNome", referencias.descricao("dose", v.get("dose")));
            v.put("estrategiaNome", referencias.descricao("estrategiavacinacao", v.get("estrategia")));
        }
        return p("atendimentos", outAtend, "vacinas", vacinas);
    }

    private List<String> descricoes(String enumeracao, Object codigos) {
        List<String> out = new ArrayList<>();
        if (codigos instanceof List<?> l) {
            for (Object c : l) {
                String d = referencias.descricao(enumeracao, c);
                if (d != null) {
                    out.add(d);
                }
            }
        }
        return out;
    }
}

package br.com.iv.security;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import br.com.iv.cdss.entity.DispositivoInvasivo;
import br.com.iv.cdss.entity.PassagemPlantao;
import br.com.iv.entity.AnamneseInternacao;
import br.com.iv.entity.AssinaturaClinica;
import br.com.iv.entity.Bam;
import br.com.iv.entity.CirurgiaAgendamento;
import br.com.iv.entity.GedDocumento;
import br.com.iv.entity.Internacao;
import br.com.iv.entity.LeitoReserva;
import br.com.iv.entity.NspEvento;
import br.com.iv.entity.ResumoBalancoHidrico;
import br.com.iv.odontologia.OdontologiaAtendimento;
import br.com.iv.entity.AnotacaoEnfermagem;
import br.com.iv.entity.AtendimentoMedico;
import br.com.iv.entity.AtendimentoMultidisciplinar;
import br.com.iv.entity.BalancoHidricoEntrada;
import br.com.iv.entity.BalancoHidricoSaida;
import br.com.iv.entity.ConsultaPediatrica;
import br.com.iv.entity.EvolucaoPaciente;
import br.com.iv.entity.EvolucaoPacienteEnf;
import br.com.iv.entity.LaudoAutorizacao;
import br.com.iv.entity.ListaAnestesica;
import br.com.iv.entity.NotificacaoSinan;
import br.com.iv.entity.ParecerEspecialista;
import br.com.iv.entity.PedidoMedico2;
import br.com.iv.entity.PharmaciaDispensacao;
import br.com.iv.entity.Prescricao;
import br.com.iv.entity.PrescricaoContinuacao;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.entity.ProtocoloExecucao;
import br.com.iv.entity.ResumoDeAlta;
import br.com.iv.entity.SumarioObito;
import br.com.iv.entity.VacinaPediatrica;
import br.com.iv.entity.Violencia;

/**
 * Rotas que recebem o id do PROPRIO registro (ex.: DELETE /prescricaocontinuacao/55,
 * PUT /evolucaopaciente/inativar/123): o numero nao e paciente nem BAM. Aqui fica qual entidade
 * aquele numero identifica, para o log buscar o registro e dizer de qual paciente ele e.
 *
 * So aceita o id logo depois da rota ("rota/{id}") ou depois de um verbo conhecido
 * ("rota/inativar/{id}"), para nao confundir numero de unidade, setor, pagina etc. com id do registro.
 */
final class AuditRegistroPaciente {

    private AuditRegistroPaciente() {
    }

    record Alvo(Class<?> entidade, String id) {
    }

    private static final Map<String, Class<?>> ROTAS = new LinkedHashMap<>();
    static {
        ROTAS.put("pharmacia/dispensacao", PharmaciaDispensacao.class);
        ROTAS.put("api/violencias", Violencia.class);
        ROTAS.put("anotacaoenfermagem", AnotacaoEnfermagem.class);
        ROTAS.put("atendimentomultidisciplinar", AtendimentoMultidisciplinar.class);
        ROTAS.put("backend_controle_entrada", BalancoHidricoEntrada.class);
        ROTAS.put("backend_controle_saida", BalancoHidricoSaida.class);
        ROTAS.put("consultapediatrica", ConsultaPediatrica.class);
        ROTAS.put("evolucaopaciente", EvolucaoPaciente.class);
        ROTAS.put("evolucaopacienteenf", EvolucaoPacienteEnf.class);
        ROTAS.put("listaanestesica", ListaAnestesica.class);
        ROTAS.put("notificacoes-sinan", NotificacaoSinan.class);
        ROTAS.put("parecerespecialista", ParecerEspecialista.class);
        ROTAS.put("pedidomedico2", PedidoMedico2.class);
        ROTAS.put("prescricao", Prescricao.class);
        ROTAS.put("prescricaocontinuacao", PrescricaoContinuacao.class);
        ROTAS.put("prontuario-clinico", ProntuarioClinicoItem.class);
        ROTAS.put("protocolos-execucao", ProtocoloExecucao.class);
        ROTAS.put("sumario-obito", SumarioObito.class);
        ROTAS.put("vacinapediatrica", VacinaPediatrica.class);
        ROTAS.put("atendimentomedico", AtendimentoMedico.class);
        ROTAS.put("anamneseinternacao", AnamneseInternacao.class);
        ROTAS.put("resumodealta", ResumoDeAlta.class);
        ROTAS.put("laudoautorizacao", LaudoAutorizacao.class);
        // Fase 1 (17/11/2026): gravacoes por id que escapavam do escopo por unidade.
        // Prefixo mais longo ANTES do curto: localizar() para no primeiro prefixo que casa.
        ROTAS.put("centro-cirurgico/execucao", CirurgiaAgendamento.class);
        ROTAS.put("centro-cirurgico/documentacao", CirurgiaAgendamento.class);
        ROTAS.put("centro-cirurgico", CirurgiaAgendamento.class);
        ROTAS.put("cdss/plantao/passagem", PassagemPlantao.class);
        ROTAS.put("cdss/plantao/dispositivo", DispositivoInvasivo.class);
        ROTAS.put("assinaturas-clinicas", AssinaturaClinica.class);
        ROTAS.put("api/assinaturas-clinicas", AssinaturaClinica.class);
        ROTAS.put("api/odontologia", OdontologiaAtendimento.class);
        ROTAS.put("backend_resumo_balanco", ResumoBalancoHidrico.class);
        ROTAS.put("ged/documentos", GedDocumento.class);
        // So "alterar": em internacao/{bam}/restauracao o numero e BAM, nao id da internacao.
        ROTAS.put("internacao/alterar", Internacao.class);
        ROTAS.put("leitoreserva", LeitoReserva.class);
        ROTAS.put("nsp/eventos", NspEvento.class);
        ROTAS.put("bam/atualizar", Bam.class);
    }

    private static final Set<String> VERBOS = Set.of(
            "inativar", "cancelar", "remove", "remover", "deletar", "delete", "excluir",
            "alterar", "atualizar", "editar", "assinar", "finalizar", "aprazamento-checagem");

    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    static Alvo localizar(String path) {
        String limpo = String.valueOf(path == null ? "" : path).toLowerCase(Locale.ROOT).replaceAll("^/+", "");
        String[] seg = limpo.split("/");
        for (Map.Entry<String, Class<?>> rota : ROTAS.entrySet()) {
            String[] prefixo = rota.getKey().split("/");
            if (seg.length <= prefixo.length) continue;
            boolean casa = true;
            for (int i = 0; i < prefixo.length && casa; i++) casa = prefixo[i].equals(seg[i]);
            if (!casa) continue;
            int p = prefixo.length;
            String id = ehId(seg[p]) ? seg[p]
                    : (VERBOS.contains(seg[p]) && seg.length > p + 1 && ehId(seg[p + 1]) ? seg[p + 1] : null);
            return id == null ? null : new Alvo(rota.getValue(), id);
        }
        return null;
    }

    static Alvo deTexto(String valor) {
        int i = valor == null ? -1 : valor.indexOf('#');
        if (i <= 0) return null;
        for (Class<?> c : ROTAS.values()) {
            if (c.getSimpleName().equals(valor.substring(0, i))) return new Alvo(c, valor.substring(i + 1));
        }
        return null;
    }

    static String emTexto(Alvo alvo) {
        return alvo.entidade().getSimpleName() + "#" + alvo.id();
    }

    private static boolean ehId(String s) {
        return s.matches("\\d{1,12}") || s.matches(UUID);
    }
}

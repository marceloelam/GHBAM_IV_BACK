package br.com.iv.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import br.com.iv.dto.AfaturarDto;
import br.com.iv.entity.Afaturar;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.ProcedimentosSus;
import br.com.iv.entity.Servidor;
import br.com.iv.entity.Tratamento;
import br.com.iv.repository.ProcedimentosSusRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

/**
 * Corrige a pendencia "Atendimento sem procedimento real de faturamento" da
 * conformidade gerando a mesma producao que o AtMedico grava ao abrir o BAM.
 *
 * A chave de origem e a do AtMedico (ATEND_MED_030106006 + BAM): se o medico
 * abrir o BAM depois, ou se a correcao rodar duas vezes, AfaturarService devolve
 * a linha existente em vez de duplicar a producao SUS.
 *
 * Nao fatura consulta sem evidencia: o tratamento nasce vazio na triagem, entao
 * "ter tratamento" nao prova que o medico atendeu. Sem registro medico no
 * tratamento o BAM volta como SEM_EVIDENCIA e a pendencia continua para
 * conferencia humana.
 *
 * O profissional e o medico do tratamento, nunca quem clicou em corrigir. Sem
 * medico identificado a linha e gravada sem CBO/CNS e o motor de criticas do
 * faturamento aponta o que falta, como no centro cirurgico.
 */
@ApplicationScoped
public class ConformidadeFaturamentoService {

    private static final Logger LOG = Logger.getLogger(ConformidadeFaturamentoService.class);

    static final String PROCEDIMENTO = "0301060061";
    static final String DESCRICAO = "ATENDIMENTO DE URGENCIA EM ATENCAO ESPECIALIZADA";
    static final String ORIGEM_MODULO = "ATEND_MED_030106006";
    private static final String LOCAL = "CONSULTORIO MEDICO";
    private static final int LIMITE_LOTE = 50;

    @Inject
    AfaturarService afaturarService;

    @Inject
    ProcedimentosSusRepository procedimentosSusRepository;

    @Inject
    JornadaPaService jornadaPaService;

    public Map<String, Object> corrigirLote(List<Integer> bams, String unidadeLogada, AuthenticatedUser user) {
        validarPermissao(user);
        List<Map<String, Object>> itens = new ArrayList<>();
        int gerados = 0;
        List<Integer> lista = bams == null ? List.of() : bams.stream().distinct().limit(LIMITE_LOTE).toList();
        for (Integer idbam : lista) {
            int id = idbam == null ? 0 : idbam;
            Map<String, Object> resultado;
            try {
                resultado = corrigir(id, unidadeLogada, user);
            } catch (RuntimeException e) {
                LOG.warnf(e, "Faturamento automatico da conformidade falhou para o BAM %s", id);
                resultado = resultado(id, "ERRO",
                        "Nao foi possivel gravar a producao. Tente novamente ou lance pelo faturamento.");
            }
            if ("GERADO".equals(resultado.get("status"))) gerados++;
            itens.add(resultado);
        }
        if (gerados > 0) jornadaPaService.limparCacheConformidade();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", itens.size());
        response.put("gerados", gerados);
        response.put("itens", itens);
        return response;
    }

    Map<String, Object> corrigir(int idbam, String unidadeLogada, AuthenticatedUser user) {
        Bam bam = idbam > 0 ? Bam.findById(idbam) : null;
        if (bam == null) {
            return resultado(idbam, "NAO_ENCONTRADO", "BAM nao encontrado.");
        }
        String unidade = !texto(unidadeLogada).isEmpty() ? texto(unidadeLogada)
                : user == null ? "" : texto(user.unidade());
        if (!unidade.isEmpty() && !unidade.equalsIgnoreCase(texto(bam.getUnidade()))) {
            return resultado(idbam, "OUTRA_UNIDADE", "BAM pertence a outra unidade; nao foi alterado.");
        }
        if (Afaturar.count("bam", idbam) > 0) {
            return resultado(idbam, "JA_FATURADO", "O BAM ja possui procedimento no faturamento.");
        }
        Tratamento tratamento = bam.getTratamento();
        if (!temEvidenciaAtendimentoMedico(tratamento)) {
            return resultado(idbam, "SEM_EVIDENCIA",
                    "Sem registro medico no tratamento. Confira se houve consulta antes de faturar.");
        }

        Servidor medico = localizarMedico(tratamento, bam.getUnidade());
        Afaturar producao = afaturarService.salvarFatura(montar(bam, tratamento, medico), user);

        Map<String, Object> item = resultado(idbam, "GERADO", medico == null
                ? "Producao gerada sem medico identificado: complete CBO/CNS no faturamento."
                : "Producao gerada para " + texto(medico.getNome()) + ".");
        item.put("idafaturar", producao == null ? null : producao.getIdafaturar());
        return item;
    }

    /** Algum campo que so o medico preenche, ou o BAM marcado como tratado. */
    static boolean temEvidenciaAtendimentoMedico(Tratamento t) {
        if (t == null) return false;
        return t.getTratado() > 0
                || preenchido(t.getMedicoecrm())
                || preenchido(t.getDiagnostico_provisorio())
                || preenchido(t.getPrescricao_inicial())
                || preenchido(t.getEvolucao_prescricao())
                || preenchido(t.getTipo_alta());
    }

    /** O construtor do Tratamento grava "-" em tipo_alta; placeholder nao e registro medico. */
    private static boolean preenchido(String valor) {
        String limpo = texto(valor);
        return !limpo.isEmpty() && !"-".equals(limpo);
    }

    AfaturarDto montar(Bam bam, Tratamento tratamento, Servidor medico) {
        AfaturarDto dto = new AfaturarDto();
        dto.setProcedimento(PROCEDIMENTO);
        dto.setDescricao(DESCRICAO);
        dto.setOrigemModulo(ORIGEM_MODULO);
        dto.setOrigemId(bam.getIdbam());
        dto.setOrigeminfo("BPA");
        dto.setLocal(LOCAL);
        dto.setLocalsolicitante(LOCAL);
        dto.setUnidade(texto(bam.getUnidade(), 25));
        dto.setCarateratend(2);
        dto.setCid10("0");

        dto.setBam(bam.getIdbam());
        dto.setIdregistro(bam.getRegistro() == null ? 0 : bam.getRegistro().getIdregistro());
        dto.setIdtratamento(tratamento.getIdtratamento());

        // Data do atendimento, nao a da correcao: a competencia do BPA sai dela.
        String data = bam.getData() == null ? "" : new SimpleDateFormat("yyyy-MM-dd").format(bam.getData());
        dto.setData(data);
        dto.setDtatendimento(data);

        aplicarPaciente(dto, bam.getPaciente());
        aplicarMedico(dto, medico);

        String competencia = data.length() >= 7 ? data.substring(0, 7).replace("-", "") : "";
        ProcedimentosSus sigtap = procedimentosSusRepository.filtrarPorProcedimentoNaCompetencia(PROCEDIMENTO, competencia);
        if (sigtap != null) {
            dto.setPa_sa(sigtap.getPa_sa());
            dto.setPa_sp(sigtap.getPa_sp());
            dto.setPa_sh(sigtap.getPa_sh());
            dto.setPa_tpreg(texto(sigtap.getPa_tpreg()));
            dto.setPa_eletiva(texto(sigtap.getPa_eletiva(), 1));
        }
        return dto;
    }

    private void aplicarPaciente(AfaturarDto dto, Pacientes paciente) {
        if (paciente == null) return;
        dto.setIdpaciente(paciente.getIdpaciente());
        dto.setNomepaciente(texto(paciente.getNome(), 30));
        dto.setCnsdopciente(texto(paciente.getCns(), 16));
        dto.setSexo(texto(paciente.getSexo(), 1));
        dto.setDtnascimento(texto(paciente.getNascimento(), 12));
        dto.setIdade(paciente.getIdade());
        dto.setEndereco(texto(paciente.getEndereco(), 30));
        dto.setNumerocasa(texto(paciente.getNumero(), 5));
        dto.setBairro(texto(paciente.getBairro(), 30));
        dto.setCep(somenteDigitosInt(paciente.getCep()));
        dto.setTelefone(somenteDigitosLong(paciente.getTelefone()));
    }

    private void aplicarMedico(AfaturarDto dto, Servidor medico) {
        if (medico == null) return;
        dto.setNomeservidor(texto(medico.getNome(), 55));
        dto.setMatriculaservidor(texto(medico.getMatricula(), 10));
        dto.setCbo(texto(medico.getCbo(), 55));
        dto.setCnsdoprofissional(texto(medico.getCns(), 16));
    }

    /**
     * O tratamento guarda o medico como texto livre ("NOME - CRM 123" ou so o
     * nome). Procura primeiro pelo CRM, depois pelo nome na unidade; ambiguidade
     * vira "sem medico" em vez de atribuir a producao a outra pessoa.
     */
    Servidor localizarMedico(Tratamento tratamento, String unidade) {
        String bruto = texto(tratamento == null ? null : tratamento.getMedicoecrm());
        if (bruto.isEmpty()) return null;

        String crm = bruto.replaceAll("(?is).*CRM[^0-9]*([0-9]{3,}).*", "$1");
        if (!crm.equals(bruto)) {
            List<Servidor> porCrm = Servidor.list("crm = ?1", crm);
            if (porCrm.size() == 1) return porCrm.get(0);
        }

        String nome = bruto.split("(?i)\\s*(-|\\(|CRM)")[0].trim();
        if (nome.isEmpty()) return null;
        List<Servidor> porNome = Servidor.list("upper(nome) = ?1 and unidade = ?2", nome.toUpperCase(), texto(unidade));
        return porNome.size() == 1 ? porNome.get(0) : null;
    }

    private void validarPermissao(AuthenticatedUser user) {
        if (user == null
                || !(user.hasPermission("kfaturamento") || user.hasPermission("kmedico") || user.hasPermission("kadm"))) {
            throw new ForbiddenException("Gerar producao SUS exige perfil de faturamento, medico ou administracao.");
        }
    }

    private static Map<String, Object> resultado(int idbam, String status, String mensagem) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idbam", idbam);
        item.put("status", status);
        item.put("mensagem", mensagem);
        return item;
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String texto(String valor, int limite) {
        String limpo = texto(valor);
        return limpo.length() <= limite ? limpo : limpo.substring(0, limite).trim();
    }

    private static int somenteDigitosInt(String valor) {
        String digitos = texto(valor).replaceAll("[^0-9]", "");
        try {
            return digitos.isEmpty() ? 0 : Integer.parseInt(digitos);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long somenteDigitosLong(String valor) {
        String digitos = texto(valor).replaceAll("[^0-9]", "");
        try {
            return digitos.isEmpty() ? 0L : Long.parseLong(digitos);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}

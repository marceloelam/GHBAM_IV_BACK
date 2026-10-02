package br.com.iv.resumoclinico.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Pacientes;
import br.com.iv.repository.BamRepository;
import br.com.iv.repository.PacientesRepository;
import br.com.iv.resumoclinico.dto.*;
import br.com.iv.service.RagPacienteService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ResumoClinicoDataService {
    private static final Logger LOG = Logger.getLogger(ResumoClinicoDataService.class);
    private static final Pattern FONTE = Pattern.compile("(?m)^\\s*(?:FONTE|ORIGEM)\\s*[:=-]\\s*([^\\n]+)");
    private static final Pattern SECAO = Pattern.compile(
        "(?m)^([\\p{Lu}0-9 ÁÉÍÓÚÇÃÕ_-]{3,})(?: \\(BAM (\\d+)\\))?:\\s*$");
    private static final Pattern DATA_FONTE = Pattern.compile(
        "(?:\\[|Data:\\s*|Entrada:\\s*)(\\d{2}[-/]\\d{2}[-/]\\d{4}|\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern PROFISSIONAL_FONTE = Pattern.compile(
        "(?i)(?:Profissional|Prescritor|Enfermagem|Profissional do BAM):\\s*([^\\n.]+)");
    @Inject PacientesRepository pacientes;
    @Inject BamRepository bams;
    @Inject RagPacienteService ragPaciente;
    @ConfigProperty(name = "ghbam.ia.max-context-chars", defaultValue = "60000") int maxChars;

    public List<PacienteResumoDto> buscarPacientes(String termo) {
        if (termo == null || termo.trim().length() < 2) return List.of();
        String t = termo.trim();
        LinkedHashSet<Pacientes> encontrados = new LinkedHashSet<>();
        if (t.matches("\\d+")) {
            encontrados.addAll(pacientes.localizarPeloBam(Integer.parseInt(t)));
            encontrados.addAll(pacientes.findByProntuario(t));
        }
        encontrados.addAll(pacientes.buscarPorNomeInteligente(t, 30));
        return encontrados.stream().limit(30).map(this::pacienteDto).toList();
    }

    public List<AtendimentoResumoDto> atendimentos(int pacienteId) {
        return bams.filtrarPorIdDoPciente(pacienteId).stream().limit(100).map(b -> new AtendimentoResumoDto(
            b.getIdbam(), String.valueOf(b.getData()), b.getHora(), b.getUnidade(), b.isEmatendimento(),
            b.isInternar(), b.isDarAlta(), b.getRegistro() == null ? "" : b.getRegistro().getQueixaprincipal()
        )).toList();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ContextoClinico carregar(int pacienteId, int bam, TipoResumoClinico tipo, PeriodoResumoClinico periodo) {
        Pacientes p = pacientes.findById((long) pacienteId);
        if (p == null) throw new IllegalArgumentException("Paciente não encontrado.");
        Bam atendimento = bams.findById((long) bam);
        if (atendimento == null || atendimento.getPaciente() == null || atendimento.getPaciente().getIdpaciente() != pacienteId)
            throw new IllegalArgumentException("O BAM não pertence ao paciente selecionado.");
        String dados;
        try {
            dados = ragPaciente.buscarDadosPaciente(pacienteId, bam);
        } catch (RuntimeException e) {
            // O prontuário legado reúne muitas tabelas opcionais. A ausência ou
            // inconsistência de uma categoria não deve ocultar os dados básicos
            // reais do atendimento nem impedir o fallback local.
            LOG.warnf("Contexto clínico parcial para pacienteId=%d bam=%d causa=%s",
                pacienteId, bam, e.getClass().getSimpleName());
            dados = contextoBasico(p, atendimento);
        }
        dados = deduplicarELimitar(dados);
        return new ContextoClinico(
            p.getIdpaciente(), p.getNome(), p.getProntuario(),
            atendimento.getIdbam(), atendimento.getUnidade(), String.valueOf(atendimento.getData()), atendimento.getHora(),
            tipo, periodo, dados, alertas(dados), pendencias(dados), fontes(dados, atendimento));
    }

    private String contextoBasico(Pacientes p, Bam b) {
        StringBuilder c = new StringBuilder();
        c.append("IDENTIFICAÇÃO:\n")
            .append("Paciente: ").append(seguro(p.getNome())).append('\n')
            .append("Prontuário: ").append(seguro(p.getProntuario())).append('\n')
            .append("Nascimento: ").append(seguro(p.getNascimento())).append('\n')
            .append("Idade registrada: ").append(p.getIdade()).append('\n')
            .append("Sexo: ").append(seguro(p.getSexo())).append("\n\n")
            .append("ATENDIMENTO:\n")
            .append("BAM: ").append(b.getIdbam()).append('\n')
            .append("Entrada: ").append(b.getData()).append(' ').append(seguro(b.getHora())).append('\n')
            .append("Unidade: ").append(seguro(b.getUnidade())).append('\n');
        if (b.getRegistro() != null) {
            c.append("Queixa principal: ").append(seguro(b.getRegistro().getQueixaprincipal())).append('\n')
                .append("Observação de entrada: ").append(seguro(b.getRegistro().getObsentrada())).append('\n');
        }
        if (b.getAvaliacao() != null) {
            var a = b.getAvaliacao();
            c.append("\nTRIAGEM REGISTRADA:\n")
                .append("PA: ").append(a.getPasist()).append('/').append(a.getPadiast()).append('\n')
                .append("FC: ").append(a.getFc()).append('\n')
                .append("FR: ").append(a.getFr()).append('\n')
                .append("Temperatura: ").append(a.getTax()).append('\n')
                .append("SpO2: ").append(a.getSpo()).append('\n')
                .append("Risco registrado: ").append(seguro(a.getRisco())).append('\n')
                .append("Alergia sinalizada: ").append(a.isAlergiaSouN() ? "SIM" : "NÃO").append('\n')
                .append("Anamnese: ").append(seguro(a.getAnamnese())).append('\n');
        }
        return c.toString();
    }

    private String seguro(Object valor) {
        return valor == null ? "" : String.valueOf(valor).replace("\u0000", "").trim();
    }

    public String hash(String contexto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.valueOf(contexto).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { return ""; }
    }

    private PacienteResumoDto pacienteDto(Pacientes p) {
        List<Bam> lista = bams.filtrarPorIdDoPciente(p.getIdpaciente());
        Bam b = lista.isEmpty() ? null : lista.get(0);
        return new PacienteResumoDto(p.getIdpaciente(), p.getNome(), p.getProntuario(), p.getNascimento(),
            p.getIdade(), p.getSexo(), b == null ? null : b.getIdbam(), b == null ? "" : b.getUnidade(),
            b == null ? "" : String.valueOf(b.getData()));
    }

    private String deduplicarELimitar(String dados) {
        if (dados == null) return "";
        LinkedHashSet<String> linhas = new LinkedHashSet<>();
        for (String linha : dados.replace("\u0000", "").split("\\R")) {
            if (!linha.isBlank()) linhas.add(linha.stripTrailing());
        }
        String texto = String.join("\n", linhas);
        return texto.length() <= maxChars ? texto : texto.substring(0, maxChars)
            + "\n[Contexto limitado pelo sistema; registros mais recentes e dados críticos foram priorizados pela fonte.]";
    }

    private List<String> alertas(String dados) {
        return linhasCom(dados, List.of("alerg", "alerta", "risco", "crític", "critic"));
    }

    private List<String> pendencias(String dados) {
        return linhasCom(dados, List.of("pendente", "pendência", "pendencia", "aguardando"));
    }

    private List<String> linhasCom(String dados, List<String> termos) {
        if (dados == null) return List.of();
        List<String> saida = new ArrayList<>();
        for (String linha : dados.split("\\R")) {
            String n = linha.toLowerCase(Locale.ROOT);
            if (termos.stream().anyMatch(n::contains) && saida.size() < 20) saida.add(linha.trim());
        }
        return saida.stream().distinct().toList();
    }

    private List<ResumoFonteDto> fontes(String dados, Bam atendimento) {
        List<ResumoFonteDto> fontes = new ArrayList<>();
        Matcher m = FONTE.matcher(String.valueOf(dados));
        while (m.find() && fontes.size() < 100) {
            fontes.add(new ResumoFonteDto(
                m.group(1).trim(), String.valueOf(atendimento.getIdbam()),
                "Prontuário GHBAM IV", formatarDataFonte(String.valueOf(atendimento.getData())), null));
        }

        if (dados != null && !dados.isBlank()) {
            Matcher secoes = SECAO.matcher(dados);
            List<int[]> limites = new ArrayList<>();
            List<String> nomes = new ArrayList<>();
            List<String> bamsSecao = new ArrayList<>();
            while (secoes.find()) {
                nomes.add(secoes.group(1).trim());
                bamsSecao.add(secoes.group(2));
                limites.add(new int[] { secoes.start(), secoes.end() });
            }
            for (int i = 0; i < nomes.size() && fontes.size() < 100; i++) {
                String nome = nomes.get(i);
                String origem = origemSecao(nome);
                if (origem == null) continue;
                int inicio = limites.get(i)[1];
                int fim = i + 1 < limites.size() ? limites.get(i + 1)[0] : dados.length();
                String bloco = dados.substring(inicio, fim);
                fontes.add(new ResumoFonteDto(
                    rotuloSecao(nome),
                    bamsSecao.get(i) == null ? String.valueOf(atendimento.getIdbam()) : bamsSecao.get(i),
                    origem,
                    extrairData(bloco, atendimento),
                    extrairProfissional(bloco)));
            }
        }

        if (fontes.isEmpty() && dados != null && !dados.isBlank()) {
            fontes.add(new ResumoFonteDto(
                "Prontuário consolidado", String.valueOf(atendimento.getIdbam()),
                "Prontuário GHBAM IV", formatarDataFonte(String.valueOf(atendimento.getData())), null));
        }
        return fontes.stream().distinct().toList();
    }

    private String origemSecao(String secao) {
        return switch (secao) {
            case "IDENTIFICACAO" -> "Cadastro do paciente";
            case "PERIODO DO ATENDIMENTO", "DIAGNOSTICO PROVISORIO" -> "Atendimento / BAM";
            case "TRIAGEM", "ANAMNESE", "COMENTARIO DA AVALIACAO", "EXAME FISICO" -> "Avaliação / Triagem";
            case "PRESCRICOES" -> "Prescrição";
            case "EVOLUCOES MEDICAS" -> "Evolução médica";
            case "EVOLUCAO DE ENFERMAGEM" -> "Evolução de enfermagem";
            case "SINAIS VITAIS" -> "Sinais vitais";
            case "EXAMES LABORATORIAIS" -> "Laboratório";
            case "EVOLUCOES DE RECEITUARIO" -> "Receituário";
            case "REGISTRO CLINICO" -> "Prontuário clínico";
            default -> null;
        };
    }

    private String rotuloSecao(String secao) {
        String texto = secao.toLowerCase(Locale.ROOT).replace('_', ' ');
        return texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
    }

    private String extrairData(String bloco, Bam atendimento) {
        Matcher data = DATA_FONTE.matcher(bloco);
        return data.find()
            ? formatarDataFonte(data.group(1))
            : formatarDataFonte(String.valueOf(atendimento.getData()));
    }

    private String extrairProfissional(String bloco) {
        Matcher profissional = PROFISSIONAL_FONTE.matcher(bloco);
        return profissional.find() ? profissional.group(1).trim() : null;
    }

    private String formatarDataFonte(String valor) {
        if (valor == null) return null;
        Matcher iso = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})").matcher(valor.trim());
        if (iso.find()) return iso.group(3) + "-" + iso.group(2) + "-" + iso.group(1);
        return valor.trim().replace('/', '-');
    }
}

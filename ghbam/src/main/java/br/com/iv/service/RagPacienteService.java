package br.com.iv.service;

import br.com.iv.entity.Avaliacao;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Bulario;
import br.com.iv.entity.EvolucaoPaciente;
import br.com.iv.entity.EvolucaoPacienteEnf;
import br.com.iv.entity.EvolucaoPacienteReceituario;
import br.com.iv.entity.Pacientes;
import br.com.iv.entity.PedidoResultadoLaboratorio;
import br.com.iv.entity.Prescricao;
import br.com.iv.entity.PrescricaoContinuacao;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.entity.Servidor;
import br.com.iv.repository.AvaliacaoRepository;
import br.com.iv.repository.BamRepository;
import br.com.iv.repository.BularioRepository;
import br.com.iv.repository.EvolucaoPacienteEnfRepository;
import br.com.iv.repository.EvolucaoPacienteReceituarioRepository;
import br.com.iv.repository.EvolucaoPacienteRepository;
import br.com.iv.repository.PacientesRepository;
import br.com.iv.repository.PedidoResultadoLaboratorioRepository;
import br.com.iv.repository.PrescricaoContinuacaoRepository;
import br.com.iv.repository.PrescricaoRepository;
import br.com.iv.repository.ProntuarioClinicoItemRepository;
import br.com.iv.repository.ServidorRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class RagPacienteService {

    private static final int MAX_RESULTS = 100;
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @Inject
    BamRepository bamRepository;

    @Inject
    BularioRepository bularioRepository;

    @Inject
    AvaliacaoRepository avaliacaoRepository;

    @Inject
    PrescricaoRepository prescricaoRepository;

    @Inject
    EvolucaoPacienteRepository evolucaoPacienteRepository;

    @Inject
    EvolucaoPacienteEnfRepository evolucaoPacienteEnfRepository;

    @Inject
    ProntuarioClinicoItemRepository prontuarioClinicoItemRepository;

    @Inject
    PedidoResultadoLaboratorioRepository pedidoResultadoLaboratorioRepository;

    @Inject
    PrescricaoContinuacaoRepository prescricaoContinuacaoRepository;

    @Inject
    EvolucaoPacienteReceituarioRepository evolucaoPacienteReceituarioRepository;

    @Inject
    PacientesRepository pacientesRepository;

    @Inject
    ServidorRepository servidorRepository;

    public String buscarDadosPaciente(int idpaciente) {
        return buscarDadosPaciente(idpaciente, null);
    }

    public String buscarDadosPaciente(int idpaciente, Integer bamEspecifico) {
        return buscarDadosPaciente(idpaciente, bamEspecifico, null);
    }

    // Overload com unidade: usado pelo RAG Prontuario para que o "ultimo BAM"
    // (quando a pergunta nao cita um numero de BAM especifico) seja o mais
    // recente NA UNIDADE do profissional que esta perguntando, nao o mais
    // recente entre todas as unidades do paciente. unidade == null preserva o
    // comportamento antigo (sem filtro), usado pelos outros callers
    // (ResumoClinicoDataService, RagService) que ainda nao pedem esse filtro.
    public String buscarDadosPaciente(int idpaciente, Integer bamEspecifico, String unidade) {
        StringBuilder sb = new StringBuilder();

        List<Bam> bams;
        if (bamEspecifico != null && bamEspecifico > 0) {
            bams = bamRepository.filtrarPorIdDoPciente(idpaciente).stream()
                .filter(bam -> bam.getIdbam() == bamEspecifico)
                .collect(Collectors.toList());
        } else if (unidade != null && !unidade.isBlank()) {
            Bam ultimoBamDaUnidade = bamRepository.filtrarPorIdDoPcienteUltimoBam(idpaciente, unidade);
            bams = ultimoBamDaUnidade != null ? List.of(ultimoBamDaUnidade) : List.of();
        } else {
            bams = bamRepository.filtrarPorIdDoPciente(idpaciente).stream()
                .limit(1)
                .collect(Collectors.toList());
        }
        if (bams.isEmpty()) return "";

        sb.append("--- DADOS DO PACIENTE ---\n\n");
        Map<String, String> cacheExplicacaoMedicamento = new HashMap<>();

        // DADOS CADASTRAIS
        Pacientes pac = pacientesRepository.filtrarPorUnicoPacienteId(idpaciente);
        if (pac != null) {
            sb.append("IDENTIFICACAO:\n");
            sb.append("  Nome: ").append(pac.getNome() != null ? pac.getNome() : "N/A").append("\n");
            sb.append("  Idade: ").append(pac.getIdade()).append(" anos\n");
            sb.append("  Sexo: ").append(pac.getSexo() != null ? pac.getSexo() : "N/A").append("\n");
            sb.append("  Nascimento: ").append(pac.getNascimento() != null ? pac.getNascimento() : "N/A").append("\n");
            sb.append("  Mae: ").append(pac.getMae() != null ? pac.getMae() : "N/A").append("\n");
            sb.append("  Prontuario: ").append(pac.getProntuario() != null ? pac.getProntuario() : "N/A").append("\n");
            sb.append("  CPF: ").append(pac.getCpf() != null ? pac.getCpf() : "N/A").append("\n");
            if (pac.getPacienteespecial() != null && !pac.getPacienteespecial().isBlank())
                sb.append("  Classificacao: ").append(pac.getPacienteespecial()).append("\n");
            sb.append("\n");
        }

        // DIAGNOSTICO / TRIAGEM do BAM selecionado. Sem BAM explicito, usa o ultimo BAM do paciente.
        Bam primeiroBam = bams.get(0);
        sb.append("PERIODO DO ATENDIMENTO:\n");
        sb.append("  BAM: ").append(primeiroBam.getIdbam()).append("\n");
        sb.append("  Entrada: ").append(formatarData(primeiroBam.getData()));
        if (temTexto(primeiroBam.getHora())) {
            sb.append(" ").append(primeiroBam.getHora().trim());
        }
        sb.append("\n");
        sb.append("  Encerramento/alta: ")
            .append(temTexto(primeiroBam.getDataDaAlta()) ? formatarData(primeiroBam.getDataDaAlta()) : "sem data de encerramento localizada")
            .append("\n\n");

        if (primeiroBam.getTratamento() != null) {
            String diag = primeiroBam.getTratamento().getDiagnostico_provisorio();
            if (diag != null && !diag.isBlank() && !diag.equals(".")) {
                sb.append("DIAGNOSTICO PROVISORIO:\n").append(diag.trim()).append("\n\n");
            }
        }
        String[] cores = {"SEM", "VERMELHO", "LARANJA", "AMARELO", "VERDE", "AZUL"};
        int corIdx = primeiroBam.getCor();
        sb.append("TRIAGEM:\n");
        String riscoBam = corIdx >= 0 && corIdx < cores.length ? cores[corIdx] : "N/A";
        sb.append("  Classificacao de risco do BAM selecionado: ").append(riscoBam).append("\n");
        sb.append("  Prioridade: ").append(primeiroBam.getPrioridade()).append("\n");
        sb.append("  Setor: ").append(primeiroBam.getRegistro() != null ? primeiroBam.getRegistro().getSetor() : "N/A").append("\n");
        sb.append("\n");

        sb.append("PROFISSIONAIS ENVOLVIDOS:\n");
        adicionarProfissional(sb, "Profissional do BAM", primeiroBam.getServidor());
        sb.append("\n");

        Avaliacao ultimaAvaliacao = avaliacaoRepository.ultimaPorBam(primeiroBam.getIdbam());
        if (ultimaAvaliacao != null) {
            if (ultimaAvaliacao.getAnamnese() != null && !ultimaAvaliacao.getAnamnese().isBlank() && !ultimaAvaliacao.getAnamnese().equals(".")) {
                sb.append("ANAMNESE:\n").append(ultimaAvaliacao.getAnamnese()).append("\n\n");
            }
            if (ultimaAvaliacao.getComentario() != null && !ultimaAvaliacao.getComentario().isBlank() && !ultimaAvaliacao.getComentario().equals(".")) {
                sb.append("COMENTARIO DA AVALIACAO:\n").append(ultimaAvaliacao.getComentario()).append("\n\n");
            }
            sb.append("EXAME FISICO:\n");
            sb.append("  Risco registrado na avaliacao deste BAM: ").append(ultimaAvaliacao.getRisco() != null ? ultimaAvaliacao.getRisco() : "N/A").append("\n");
            sb.append("  Glasgow: ").append(ultimaAvaliacao.getGlasgow()).append("\n");
            sb.append("  Nivel de dor: ").append(ultimaAvaliacao.getNiveldor()).append("/10\n");
            sb.append("  PA: ").append((int)ultimaAvaliacao.getPasist()).append("/").append((int)ultimaAvaliacao.getPadiast()).append(" mmHg\n");
            sb.append("  FC: ").append((int)ultimaAvaliacao.getFc()).append(" bpm\n");
            sb.append("  FR: ").append((int)ultimaAvaliacao.getFr()).append(" irpm\n");
            sb.append("  Temperatura: ").append(ultimaAvaliacao.getTax()).append(" °C\n");
            sb.append("  SpO2: ").append((int)ultimaAvaliacao.getSpo()).append("%\n");
            sb.append("  Peso: ").append(ultimaAvaliacao.getPeso()).append(" kg\n");
            if (ultimaAvaliacao.isEupneico()) sb.append("  Eupneico - respiracao normal\n");
            if (ultimaAvaliacao.isDispneico()) sb.append("  Dispneico - dificuldade respiratoria\n");
            if (ultimaAvaliacao.isTaquipneico()) sb.append("  Taquipneico - respiracao acelerada\n");
            if (ultimaAvaliacao.isAcianotico()) sb.append("  Acianotico - sem cianose\n");
            if (ultimaAvaliacao.isCianotico()) sb.append("  Cianotico - com cianose\n");
            if (ultimaAvaliacao.isHidratado()) sb.append("  Hidratado\n");
            if (ultimaAvaliacao.isDesidratado()) sb.append("  Desidratado\n");
            if (ultimaAvaliacao.isNormocorado()) sb.append("  Normocorado - coloracao normal\n");
            if (ultimaAvaliacao.isHipocorado()) sb.append("  Hipocorado (+/4+) - palido\n");
            if (ultimaAvaliacao.isSudoreico()) sb.append("  Sudoreico - suando\n");
            if (ultimaAvaliacao.isAnicterico()) sb.append("  Anicterico - sem ictericia\n");
            if (ultimaAvaliacao.isIcterico()) sb.append("  Icterico - com ictericia\n");
            if (ultimaAvaliacao.isPulsocheio()) sb.append("  Pulso cheio\n");
            if (ultimaAvaliacao.isPulsofino()) sb.append("  Pulso fino\n");
            if (ultimaAvaliacao.isEtilismo()) sb.append("  Etilismo\n");
            if (ultimaAvaliacao.isAcordado()) sb.append("  Acordado\n");
            if (ultimaAvaliacao.isOrientado()) sb.append("  Orientado\n");
            if (ultimaAvaliacao.isDesorientado()) sb.append("  Desorientado\n");
            if (ultimaAvaliacao.isTorporoso()) sb.append("  Torporoso\n");
            if (ultimaAvaliacao.isComatoso()) sb.append("  Comatoso\n");
            if (ultimaAvaliacao.isIsocorico()) sb.append("  Pupilas isocoricas\n");
            if (ultimaAvaliacao.isAnisocorico()) sb.append("  Pupilas anisocoricas\n");
            if (ultimaAvaliacao.isMidriatico()) sb.append("  Pupilas midriaticas\n");
            if (ultimaAvaliacao.isMiotico()) sb.append("  Pupilas mioticas\n");
            if (ultimaAvaliacao.isRigideznuca()) sb.append("  Rigidez de nuca\n");
            if (ultimaAvaliacao.isAlergiaSouN()) sb.append("  Alergia: Sim\n");
            if (ultimaAvaliacao.getCabeca() != null && !ultimaAvaliacao.getCabeca().isBlank()) sb.append("  Cabeca: ").append(ultimaAvaliacao.getCabeca()).append("\n");
            sb.append("\n");
        }

        for (int i = 0; i < bams.size(); i++) {
            Bam bam = bams.get(i);
            int idbam = bam.getIdbam();

            List<Prescricao> prescricoes = prescricaoRepository.filtarPoBam2(idbam);
            List<Prescricao> recentesPresc = prescricoes.stream().limit(MAX_RESULTS).collect(Collectors.toList());
            if (!recentesPresc.isEmpty()) {
                sb.append("PRESCRICOES (BAM ").append(idbam).append("):\n");
                for (Prescricao p : recentesPresc) {
                    String conteudo = p.getPrescricaopuro() != null ? p.getPrescricaopuro() : p.getPrescricao();
                    if (conteudo != null && !conteudo.isBlank()) {
                        adicionarProfissional(sb, "Prescritor", p.getMedicoq(), p.getMediccrm());
                        sb.append("- ").append(conteudo.trim()).append("\n");
                        adicionarExplicacaoMedicamento(sb, p, conteudo, cacheExplicacaoMedicamento);
                    }
                }
                sb.append("\n");
            }

            List<EvolucaoPaciente> evolucoes = evolucaoPacienteRepository.filtarPoBam2(idbam);
            List<EvolucaoPaciente> recentesEvo = evolucoes.stream().limit(MAX_RESULTS).collect(Collectors.toList());
            if (!recentesEvo.isEmpty()) {
                sb.append("EVOLUCOES MEDICAS (BAM ").append(idbam).append("):\n");
                for (EvolucaoPaciente e : recentesEvo) {
                    if (e.getRelato() != null && !e.getRelato().isBlank()) {
                        sb.append("[").append(formatarData(e.getData())).append("] ");
                        adicionarProfissionalInline(sb, e.getMedico(), e.getCrm());
                        sb.append(e.getRelato().trim()).append("\n");
                    }
                }
                sb.append("\n");
            }

            EvolucaoPacienteEnf ultimaEvoEnf = evolucaoPacienteEnfRepository.filtarUltimaPoBam(idbam);
            if (ultimaEvoEnf != null && ultimaEvoEnf.getRelato() != null && !ultimaEvoEnf.getRelato().isBlank()) {
                sb.append("EVOLUCAO DE ENFERMAGEM (BAM ").append(idbam).append("):\n");
                sb.append("[").append(formatarData(ultimaEvoEnf.getData())).append("] ");
                if (temTexto(ultimaEvoEnf.getEnfermeiro())) {
                    sb.append("Enfermagem: ").append(ultimaEvoEnf.getEnfermeiro().trim()).append(". ");
                }
                adicionarProfissionalInline(sb, ultimaEvoEnf.getMedico(), ultimaEvoEnf.getCrm());
                sb.append(ultimaEvoEnf.getRelato().trim()).append("\n\n");
            }

            PrescricaoContinuacao ultimosVitais = prescricaoContinuacaoRepository.filtarUltimaPoBam(idbam);
            if (ultimosVitais != null) {
                sb.append("SINAIS VITAIS (BAM ").append(idbam).append("):\n");
                sb.append("Data: ").append(formatarData(ultimosVitais.getData())).append(" Hora: ").append(ultimosVitais.getHora()).append("\n");
                if (ultimosVitais.getPasist() != 0 || ultimosVitais.getPadiast() != 0)
                    sb.append("PA: ").append((int)ultimosVitais.getPasist()).append("/").append((int)ultimosVitais.getPadiast()).append(" mmHg\n");
                if (ultimosVitais.getFc() != 0)
                    sb.append("FC: ").append((int)ultimosVitais.getFc()).append(" bpm\n");
                if (ultimosVitais.getFr() != 0)
                    sb.append("FR: ").append((int)ultimosVitais.getFr()).append(" irpm\n");
                if (ultimosVitais.getTax() != 0)
                    sb.append("Temperatura: ").append(ultimosVitais.getTax()).append(" °C\n");
                if (ultimosVitais.getSpo() != 0)
                    sb.append("SpO2: ").append((int)ultimosVitais.getSpo()).append("%\n");
                if (ultimosVitais.getPeso() != 0)
                    sb.append("Peso: ").append(ultimosVitais.getPeso()).append(" kg\n");
                sb.append("\n");
            }

            List<PedidoResultadoLaboratorio> labResults = pedidoResultadoLaboratorioRepository.filtrarBamPedidoResultadoLaboratorio(idbam);
            List<PedidoResultadoLaboratorio> recentesLab = labResults.stream().limit(MAX_RESULTS).collect(Collectors.toList());
            if (!recentesLab.isEmpty()) {
                sb.append("EXAMES LABORATORIAIS (BAM ").append(idbam).append("):\n");
                for (PedidoResultadoLaboratorio lab : recentesLab) {
                    sb.append("[").append(formatarData(lab.getDt())).append("] ").append(lab.getDescricao());
                    if (lab.getGrupopedidos() != null && !lab.getGrupopedidos().isBlank()) {
                        sb.append(": ").append(lab.getGrupopedidos().trim());
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }

            List<EvolucaoPacienteReceituario> receituarioEvolucoes = evolucaoPacienteReceituarioRepository.filtarPoBam(idbam);
            List<EvolucaoPacienteReceituario> recentesReceita = receituarioEvolucoes.stream().limit(MAX_RESULTS).collect(Collectors.toList());
            if (!recentesReceita.isEmpty()) {
                sb.append("EVOLUCOES DE RECEITUARIO (BAM ").append(idbam).append("):\n");
                for (EvolucaoPacienteReceituario evo : recentesReceita) {
                    if (evo.getRelato() != null && !evo.getRelato().isBlank()) {
                        sb.append("[").append(formatarData(evo.getData())).append(" ").append(evo.getHoraev()).append("] ");
                        if (temTexto(evo.getMedicocrm())) {
                            sb.append("Profissional: ").append(evo.getMedicocrm().trim()).append(". ");
                        }
                        sb.append(evo.getRelato().trim()).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        List<ProntuarioClinicoItem> itensClinicos = prontuarioClinicoItemRepository.listarPorPaciente(idpaciente, null);
        List<ProntuarioClinicoItem> recentesItens = itensClinicos.stream().limit(MAX_RESULTS).collect(Collectors.toList());
        if (!recentesItens.isEmpty()) {
            sb.append("REGISTRO CLINICO:\n");
            for (ProntuarioClinicoItem item : recentesItens) {
                if (item.getDescricao() != null && !item.getDescricao().isBlank()) {
                    sb.append("[").append(item.getTipo()).append("] ").append(item.getDescricao().trim()).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private void adicionarProfissional(StringBuilder sb, String rotulo, Servidor servidor) {
        if (servidor == null) {
            return;
        }
        adicionarProfissional(sb, rotulo, servidor.getNome(), crmDoServidor(servidor));
    }

    private void adicionarProfissional(StringBuilder sb, String rotulo, String nome, String crm) {
        String crmResolvido = resolverCrmDoMedico(nome, crm);
        if (!temTexto(nome) && !temTexto(crmResolvido)) {
            return;
        }
        sb.append("  ").append(rotulo).append(": ");
        if (temTexto(nome)) {
            sb.append(nome.trim());
        } else {
            sb.append("nome nao informado");
        }
        if (temTexto(crmResolvido)) {
            sb.append(" - CRM: ").append(crmResolvido.trim());
        }
        sb.append("\n");
    }

    private void adicionarProfissionalInline(StringBuilder sb, String nome, String crm) {
        String crmResolvido = resolverCrmDoMedico(nome, crm);
        if (!temTexto(nome) && !temTexto(crmResolvido)) {
            return;
        }
        sb.append("Profissional: ");
        if (temTexto(nome)) {
            sb.append(nome.trim());
        } else {
            sb.append("nome nao informado");
        }
        if (temTexto(crmResolvido)) {
            sb.append(" - CRM: ").append(crmResolvido.trim());
        }
        sb.append(". ");
    }

    private void adicionarExplicacaoMedicamento(
        StringBuilder sb,
        Prescricao prescricao,
        String conteudo,
        Map<String, String> cache
    ) {
        String chave = chaveMedicamento(prescricao, conteudo);
        if (!temTexto(chave)) {
            return;
        }
        String explicacao = cache.computeIfAbsent(chave, this::montarExplicacaoMedicamento);
        if (temTexto(explicacao)) {
            sb.append("  Entenda este medicamento: ").append(explicacao).append("\n");
        }
    }

    private String chaveMedicamento(Prescricao prescricao, String conteudo) {
        LinkedHashSet<String> candidatos = new LinkedHashSet<>();
        if (prescricao != null) {
            adicionarCandidatoMedicamento(candidatos, prescricao.getMedicacao());
            adicionarCandidatoMedicamento(candidatos, prescricao.getMedicamentopedido());
        }
        adicionarCandidatoMedicamento(candidatos, conteudo);
        return candidatos.stream().findFirst().orElse("");
    }

    private void adicionarCandidatoMedicamento(LinkedHashSet<String> candidatos, String texto) {
        String candidato = extrairNomeMedicamento(texto);
        if (temTexto(candidato)) {
            candidatos.add(candidato);
        }
    }

    private String extrairNomeMedicamento(String texto) {
        if (!temTexto(texto)) {
            return "";
        }
        String valor = texto.trim()
            .replaceAll("(?i)\\b(uso|tomar|administrar|aplicar|prescrever|medicamento|medicacao|se necessario|se dor|se febre)\\b", " ")
            .replaceAll("\\d+[,.]?\\d*\\s*(mg|mcg|g|ml|ui|amp|ampola|comprimido|comp|gota|gotas|cp|fr|frasco|dose|doses)\\b", " ")
            .replaceAll("(?i)\\b(vo|iv|ev|im|sc|sl|inalatorio|oral|endovenoso|intramuscular|subcutaneo)\\b", " ")
            .replaceAll("[^\\p{L}\\s\\-]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (!temTexto(valor)) {
            return "";
        }
        String[] partes = valor.split("\\s+");
        int limite = Math.min(partes.length, 4);
        List<String> palavras = new ArrayList<>();
        for (int i = 0; i < limite; i++) {
            String palavra = partes[i].trim();
            if (palavra.length() >= 3) {
                palavras.add(palavra);
            }
        }
        return String.join(" ", palavras).trim();
    }

    private String montarExplicacaoMedicamento(String medicamento) {
        Bulario bula = localizarBula(medicamento);
        if (bula == null) {
            return "medicamento prescrito no atendimento. Nao foi encontrada explicacao no bulario interno; finalidade, dose, riscos e cuidados devem ser conferidos na prescricao original e com o profissional responsavel.";
        }

        List<String> partes = new ArrayList<>();
        if (temTexto(bula.getResumo())) {
            partes.add(resumirCampo(bula.getResumo(), 220));
        }
        if (temTexto(bula.getIndicacoes())) {
            partes.add("Costuma ser usado para: " + resumirCampo(bula.getIndicacoes(), 180));
        }
        if (temTexto(bula.getEfeitosAdversos())) {
            partes.add("Possiveis efeitos indesejados: " + resumirCampo(bula.getEfeitosAdversos(), 160));
        }
        if (temTexto(bula.getPrecaucoes())) {
            partes.add("Cuidados: " + resumirCampo(bula.getPrecaucoes(), 160));
        }
        if (partes.isEmpty()) {
            partes.add("medicamento localizado no bulario do sistema, mas sem resumo suficiente para explicar em linguagem simples.");
        }
        partes.add("Nao alterar dose, horario ou suspender sem orientacao profissional.");
        return String.join(" ", partes);
    }

    private Bulario localizarBula(String medicamento) {
        if (!temTexto(medicamento)) {
            return null;
        }
        // Sem bula compatível devolve null; antes caía em resultados.get(0), que podia
        // ser a bula de outro remédio que só citava este nas interações.
        return br.com.iv.cdss.service.BulaCasamento
            .escolher(medicamento, bularioRepository.filtarBulario(medicamento))
            .orElse(null);
    }

    private String resumirCampo(String texto, int limite) {
        if (!temTexto(texto)) {
            return "";
        }
        String limpo = texto.replaceAll("\\s+", " ").trim();
        if (limpo.length() <= limite) {
            return limpo;
        }
        int corte = limpo.lastIndexOf(' ', limite);
        if (corte < 80) {
            corte = limite;
        }
        return limpo.substring(0, corte).trim() + "...";
    }

    private String resolverCrmDoMedico(String nome, String crm) {
        if (temTexto(crm)) {
            return crm.trim();
        }
        Servidor servidor = localizarServidorPorNome(nome);
        return crmDoServidor(servidor);
    }

    private String crmDoServidor(Servidor servidor) {
        if (servidor == null) {
            return "";
        }
        if (temTexto(servidor.getCrm())) {
            return servidor.getCrm().trim();
        }
        if (ehMedico(servidor) && temTexto(servidor.getCorem())) {
            return servidor.getCorem().trim();
        }
        return "";
    }

    private Servidor localizarServidorPorNome(String nome) {
        if (!temTexto(nome)) {
            return null;
        }
        String nomeBusca = nome.trim().toUpperCase();
        Servidor servidor = servidorRepository.find("upper(nome) = ?1", nomeBusca).firstResult();
        if (servidor != null) {
            return servidor;
        }
        return servidorRepository.find("upper(nome) like ?1", nomeBusca + "%").firstResult();
    }

    private boolean ehMedico(Servidor servidor) {
        if (servidor == null) {
            return false;
        }
        if (Boolean.TRUE.equals(servidor.getMedico())) {
            return true;
        }
        String cargo = normalizarSemAcento(servidor.getCargo());
        return cargo.contains("MEDICO");
    }

    private String normalizarSemAcento(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase();
    }

    private boolean temTexto(String texto) {
        return texto != null && !texto.isBlank() && !texto.trim().equals(".");
    }

    private String formatarData(Date data) {
        if (data == null) {
            return "N/A";
        }
        return new java.text.SimpleDateFormat("dd-MM-yyyy").format(data);
    }

    private String formatarData(LocalDate data) {
        if (data == null) {
            return "N/A";
        }
        return data.format(DATA_BR);
    }

    private String formatarData(String data) {
        if (!temTexto(data)) {
            return "N/A";
        }
        String valor = data.trim();
        String somenteData = valor.length() >= 10 ? valor.substring(0, 10) : valor;
        for (DateTimeFormatter formatter : List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
        )) {
            try {
                return LocalDate.parse(somenteData, formatter).format(DATA_BR);
            } catch (DateTimeParseException ignored) {
                // Tenta o proximo formato conhecido.
            }
        }
        return valor.replace("/", "-");
    }
}


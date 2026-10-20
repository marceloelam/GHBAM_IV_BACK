package br.com.iv.cdss.service;

import java.io.Serializable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;

import br.com.iv.cdss.dto.CdssAlertDto;
import br.com.iv.cdss.dto.CdssAvaliacaoPrescricaoRequest;
import br.com.iv.cdss.dto.CdssAvaliacaoResponse;
import br.com.iv.cdss.dto.CdssBularioMedicamentoDto;
import br.com.iv.cdss.dto.CdssOverrideRequest;
import br.com.iv.cdss.dto.News2Request;
import br.com.iv.cdss.dto.News2Response;
import br.com.iv.cdss.entity.CdssAlertaOverride;
import br.com.iv.cdss.repository.CdssAlertaOverrideRepository;
import br.com.iv.entity.Avaliacao;
import br.com.iv.entity.Bam;
import br.com.iv.entity.Bulario;
import br.com.iv.entity.PedidoResultadoLaboratorio;
import br.com.iv.entity.PedidoResultadoLaboratorioItem;
import br.com.iv.entity.PrescricaoItemEstruturado;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.repository.BamRepository;
import br.com.iv.repository.BularioRepository;
import br.com.iv.repository.PrescricaoItemEstruturadoRepository;
import br.com.iv.repository.ProntuarioClinicoItemRepository;
import br.com.iv.service.PedidoResultadoLaboratorioService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CdssService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    CdssRulesEngine rulesEngine;

    @Inject
    CdssBaseFarmaceuticaRegras regrasBaseFarmaceutica;

    @Inject
    PrescricaoItemEstruturadoRepository prescricaoRepository;

    @Inject
    ProntuarioClinicoItemRepository prontuarioRepository;

    @Inject
    CdssAlertaOverrideRepository overrideRepository;

    @Inject
    BularioRepository bularioRepository;

    @Inject
    BamRepository bamRepository;

    @Inject
    PedidoResultadoLaboratorioService resultadoLaboratorioService;

    public CdssAvaliacaoResponse avaliarPrescricao(CdssAvaliacaoPrescricaoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dados da prescricao sao obrigatorios.");
        }
        enriquecerComContextoClinico(request);
        CdssAvaliacaoResponse response = rulesEngine.avaliarPrescricao(request);
        if (regrasBaseFarmaceutica != null && request.getMedicamento() != null && !request.getMedicamento().isBlank()) {
            List<CdssAlertDto> alertas = new ArrayList<>(response.getAlertas());
            if (regrasBaseFarmaceutica.possuiDoseMaxima(request.getMedicamento())) {
                // A base farmaceutica ja tem limite de dose: a pendencia do bulario deixa de valer.
                alertas.removeIf(a -> "CDSS_BULARIO_REGRA_PENDENTE".equals(a.getCodigo()));
            }
            alertas.addAll(regrasBaseFarmaceutica.avaliar(request, alertas));
            alertas.sort(Comparator.comparingInt(a -> ordemSeveridade(a.getSeveridade())));
            response.setAlertas(alertas);
            response.setPossuiAlertaCritico(alertas.stream().anyMatch(a -> "CRITICAL".equalsIgnoreCase(a.getSeveridade())));
            response.setBloqueante(alertas.stream().anyMatch(CdssAlertDto::isBloqueante));
        }
        return response;
    }

    private int ordemSeveridade(String severidade) {
        return switch (safe(severidade).toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 0;
            case "MAJOR" -> 1;
            default -> 2;
        };
    }

    public News2Response calcularNews2(News2Request request) {
        return rulesEngine.calcularNews2(request);
    }

    public List<CdssAlertaOverride> listarOverridesPorBam(int idbam) {
        return overrideRepository.listarPorBam(idbam);
    }

    @Transactional
    public CdssAlertaOverride registrarOverride(CdssOverrideRequest request) {
        validarOverride(request);
        CdssAlertaOverride override = new CdssAlertaOverride();
        override.setIdpaciente(request.getIdpaciente());
        override.setIdbam(request.getIdbam());
        override.setUnidade(safe(request.getUnidade()));
        override.setAlertaCodigo(safe(request.getAlertaCodigo()));
        override.setAlertaSeveridade(safe(request.getAlertaSeveridade()).toUpperCase());
        override.setJustificativa(safeText(request.getJustificativa()));
        override.setModuloOrigem(safe(request.getModuloOrigem()));
        override.setUsuarioNome(safe(request.getUsuarioNome()));
        override.setUsuarioMatricula(safe(request.getUsuarioMatricula()));
        override.persist();
        return override;
    }

    private void enriquecerComContextoClinico(CdssAvaliacaoPrescricaoRequest request) {
        if ((request.getMedicamentosAtuais() == null || request.getMedicamentosAtuais().isEmpty())
                && request.getIdbam() != null && request.getIdbam() > 0) {
            List<String> medicamentos = prescricaoRepository.listarAtivosPorBam(request.getIdbam()).stream()
                    .map(PrescricaoItemEstruturado::getMedicamento)
                    .filter(v -> v != null && !v.isBlank())
                    .toList();
            request.setMedicamentosAtuais(medicamentos);
        }
        if (request.getIdpaciente() != null && request.getIdpaciente() > 0) {
            // Une o que veio da tela (texto de alergia da triagem daquele atendimento)
            // com as alergias registradas no prontuario do paciente. Antes o prontuario
            // so era lido quando a tela nao mandava nada, entao qualquer texto na
            // triagem escondia a alergia registrada em atendimento anterior.
            List<String> alergias = new ArrayList<>(request.getAlergias() == null ? List.of() : request.getAlergias());
            Set<String> chaves = new LinkedHashSet<>();
            for (String informada : alergias) {
                String chave = chaveAlergia(informada);
                if (!chave.isBlank()) {
                    chaves.add(chave);
                }
            }
            for (ProntuarioClinicoItem item : prontuarioRepository.listarPorPacienteTipo(request.getIdpaciente(), "ALERGIA", "TODAS")) {
                if (!"ATIVO".equalsIgnoreCase(item.getStatus())) {
                    continue;
                }
                String texto = textoAlergia(item);
                if (texto == null || texto.isBlank()) {
                    continue;
                }
                String chave = chaveAlergia(texto);
                if (!chave.isBlank() && chaves.add(chave)) {
                    alergias.add(texto);
                }
            }
            request.setAlergias(alergias);
        }
        if (request.getBulario() == null && request.getMedicamento() != null && !request.getMedicamento().isBlank()) {
            localizarBula(request.getMedicamento()).stream()
                    .findFirst()
                    .map(this::toCdssBulario)
                    .ifPresent(request::setBulario);
        }
        if ((request.getPesoKg() == null || request.getPesoKg() <= 0)
                && request.getIdbam() != null && request.getIdbam() > 0) {
            Double pesoTriagem = pesoTriagem(request.getIdbam());
            if (pesoTriagem != null && pesoTriagem > 0) {
                request.setPesoKg(pesoTriagem);
            }
        }
        if ((request.getEgfr() == null || request.getEgfr() <= 0)
                && request.getIdbam() != null && request.getIdbam() > 0
                && request.getIdadeAnos() != null && request.getIdadeAnos() >= 18) {
            Double tfg = tfgDaUltimaCreatinina(request.getIdbam(), request.getIdadeAnos());
            if (tfg != null) {
                request.setEgfr(tfg);
            }
        }
    }

    /**
     * TFG estimada (CKD-EPI 2021, sem raca) pela creatinina mais recente dos laudos
     * deste BAM. So estima com creatinina em mg/dL e sexo reconhecivel; na duvida
     * devolve null e a regra renal simplesmente nao dispara.
     */
    private Double tfgDaUltimaCreatinina(int idbam, int idade) {
        if (resultadoLaboratorioService == null || bamRepository == null) return null;
        try {
            Bam bam = bamRepository.filtrarPorUnicoBam(idbam);
            Boolean feminino = feminino(bam == null || bam.getPaciente() == null ? null : bam.getPaciente().getSexo());
            if (feminino == null) return null;
            List<Integer> ids = resultadoLaboratorioService.filtrarBamPedidoResultadoLaboratorio(idbam).stream()
                    .map(PedidoResultadoLaboratorio::getIdPedidoResultado)
                    .toList();
            if (ids.isEmpty()) return null;
            for (PedidoResultadoLaboratorioItem item : PedidoResultadoLaboratorioItem
                    .<PedidoResultadoLaboratorioItem>list("idpedidoresultado in ?1 order by idpedidoresultado desc, ordem", ids)) {
                boolean creatinina = "2160-0".equals(safe(item.getCodigoLoinc()))
                        || (safe(item.getCodigoLoinc()).isBlank() && "creatinina".equals(chaveAlergia(item.getNomeExame())));
                if (!creatinina) continue;
                String unidade = chaveAlergia(item.getUnidadeMedida());
                if (!unidade.isBlank() && !"mg dl".equals(unidade)) return null; // umol/L etc.: nao arrisca
                Double scr = numero(item.getValor());
                return scr == null ? null : ckdEpi2021(scr, idade, feminino);
            }
        } catch (RuntimeException e) {
            System.err.println("[CDSS] Nao foi possivel estimar TFG do BAM " + idbam + ": " + e.getMessage());
        }
        return null;
    }

    static Double ckdEpi2021(double creatininaMgDl, int idade, boolean feminino) {
        if (creatininaMgDl <= 0 || idade < 18) return null;
        double kappa = feminino ? 0.7 : 0.9;
        double alfa = feminino ? -0.241 : -0.302;
        double razao = creatininaMgDl / kappa;
        double tfg = 142d * Math.pow(Math.min(razao, 1d), alfa) * Math.pow(Math.max(razao, 1d), -1.200)
                * Math.pow(0.9938, idade) * (feminino ? 1.012 : 1d);
        return Math.round(tfg * 10d) / 10d;
    }

    static Boolean feminino(String sexo) {
        String s = safeStatic(sexo).toUpperCase(Locale.ROOT);
        if (s.startsWith("F")) return Boolean.TRUE;
        if (s.startsWith("M")) return Boolean.FALSE;
        return null;
    }

    private static Double numero(String valor) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:[.,]\\d+)?").matcher(safeStatic(valor));
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String safeStatic(String value) {
        return value == null ? "" : value.trim();
    }

    private Double pesoTriagem(int idbam) {
        Bam bam = bamRepository.filtrarPorUnicoBam(idbam);
        if (bam == null) return null;
        Avaliacao avaliacao = bam.getAvaliacao();
        if (avaliacao == null) return null;
        if (avaliacao.getPeso2() > 0) return avaliacao.getPeso2();
        if (avaliacao.getPeso() > 0) return avaliacao.getPeso();
        return null;
    }

    private List<Bulario> localizarBula(String medicamento) {
        String termo = safe(medicamento);
        if (termo.isBlank()) return List.of();
        String semDose = termo.replaceAll("(?i)\\b\\d+[,.]?\\d*\\s*(mg|mcg|g|ml|ui|amp|ampola|comprimido|comp|cp|gota|gotas)\\b", " ").trim();
        List<Bulario> porNome = bularioRepository.filtarBulario(semDose.isBlank() ? termo : semDose);
        if (!porNome.isEmpty()) return porNome;
        String primeiroTermo = semDose.split("\\s+")[0];
        return primeiroTermo.isBlank() ? List.of() : bularioRepository.filtarBulario(primeiroTermo);
    }

    private CdssBularioMedicamentoDto toCdssBulario(Bulario bula) {
        CdssBularioMedicamentoDto dto = new CdssBularioMedicamentoDto();
        dto.setId(bula.getId());
        dto.setNome(bula.getNome());
        dto.setPrincipioAtivo(bula.getPrincipioAtivo());
        dto.setPosologia(bula.getPosologia());
        dto.setContraindicacoes(bula.getContraindicacoes());
        dto.setInteracoes(bula.getInteracoes());
        dto.setPrecaucoes(bula.getPrecaucoes());
        dto.setFonte(bula.getFonte());
        return dto;
    }

    private String textoAlergia(ProntuarioClinicoItem item) {
        if (item == null) return "";
        if (item.getTitulo() != null && !item.getTitulo().isBlank()) return item.getTitulo();
        return item.getDescricao();
    }

    private void validarOverride(CdssOverrideRequest request) {
        if (request == null || safe(request.getAlertaCodigo()).isBlank()) {
            throw new IllegalArgumentException("Codigo do alerta e obrigatorio para override.");
        }
        if (safeText(request.getJustificativa()).length() < 15) {
            throw new IllegalArgumentException("Justificativa clinica deve ter pelo menos 15 caracteres.");
        }
        if (safe(request.getUsuarioMatricula()).isBlank()) {
            throw new IllegalArgumentException("Matricula do usuario e obrigatoria para auditoria do override.");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String chaveAlergia(String valor) {
        String base = valor == null ? "" : valor.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}

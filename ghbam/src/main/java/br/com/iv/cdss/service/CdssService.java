package br.com.iv.cdss.service;

import java.io.Serializable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;

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
import br.com.iv.entity.PrescricaoItemEstruturado;
import br.com.iv.entity.ProntuarioClinicoItem;
import br.com.iv.repository.BamRepository;
import br.com.iv.repository.BularioRepository;
import br.com.iv.repository.PrescricaoItemEstruturadoRepository;
import br.com.iv.repository.ProntuarioClinicoItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CdssService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    CdssRulesEngine rulesEngine;

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

    public CdssAvaliacaoResponse avaliarPrescricao(CdssAvaliacaoPrescricaoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dados da prescricao sao obrigatorios.");
        }
        enriquecerComContextoClinico(request);
        return rulesEngine.avaliarPrescricao(request);
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

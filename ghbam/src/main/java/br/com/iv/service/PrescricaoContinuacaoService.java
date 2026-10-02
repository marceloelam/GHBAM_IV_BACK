package br.com.iv.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.PrescricaoContinuacaoDto;
import br.com.iv.entity.PrescricaoContinuacao;
import br.com.iv.repository.PrescricaoContinuacaoRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PrescricaoContinuacaoService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    PrescricaoContinuacaoRepository prescricaoContinuacaoRepository;

    @Inject
    AssinaturaClinicaService assinaturaClinicaService;

    @Transactional
    public List<PrescricaoContinuacao> filtarPorBam(int bam) {
        return prescricaoContinuacaoRepository.filtarPoBam(bam);
    }

    @Transactional
    public List<PrescricaoContinuacao> filtarPorBam2(int bam, String data) {
        return prescricaoContinuacaoRepository.filtarPoBam2(bam, data);
    }

    @Transactional
    public PrescricaoContinuacao filtarUltimaPorBam(int bam) {
        return prescricaoContinuacaoRepository.filtarUltimaPoBam(bam);
    }

    @Transactional
    public PrescricaoContinuacao salvarEvolucao(PrescricaoContinuacaoDto dto, AuthenticatedUser user) {
        PrescricaoContinuacao prescricaoContinuacao = new PrescricaoContinuacao();
        preencher(prescricaoContinuacao, dto);
        if (user != null && !user.matricula().isBlank()) {
            prescricaoContinuacao.setCriadopor(user.matricula());
        }
        prescricaoContinuacao.persist();
        return prescricaoContinuacao;
    }

    @Transactional
    public void prescricaoContinuacaoAltera(String id, PrescricaoContinuacaoDto dto) {
        PrescricaoContinuacao prescricaoContinuacao = prescricaoContinuacaoRepository.findByUuid(id);
        if (prescricaoContinuacao == null) {
            throw new IllegalArgumentException("Continuacao da prescricao nao localizada para ID: " + id);
        }

        validarDocumentoAberto(prescricaoContinuacao);
        preencher(prescricaoContinuacao, dto);
        prescricaoContinuacao.persist();
    }

    @Transactional
    public void removePaciente(int id, AuthenticatedUser user) {
        Optional<PrescricaoContinuacao> prescricaoContinuacaoOP = PrescricaoContinuacao.findByIdOptional(id);
        if (prescricaoContinuacaoOP.isEmpty()) {
            throw new IllegalArgumentException("Continuacao da prescricao nao localizada.");
        }

        PrescricaoContinuacao prescricaoContinuacao = prescricaoContinuacaoOP.get();
        validarDocumentoAberto(prescricaoContinuacao);
        validarPermissaoExclusao(prescricaoContinuacao, user);
        prescricaoContinuacao.delete();
    }

    private void validarPermissaoExclusao(PrescricaoContinuacao prescricaoContinuacao, AuthenticatedUser user) {
        String criador = prescricaoContinuacao.getCriadopor();
        if (criador == null || criador.isBlank()) {
            throw new SecurityException("Registro sem identificacao do profissional que o realizou; exclusao nao permitida.");
        }
        if (user == null || user.matricula().isBlank()
                || !criador.trim().equalsIgnoreCase(user.matricula().trim())) {
            throw new SecurityException("Somente o profissional que realizou o registro pode exclui-lo.");
        }

        LocalDateTime criado = dataHoraRegistro(prescricaoContinuacao);
        if (criado == null) {
            throw new SecurityException("Nao foi possivel validar a data do registro; exclusao nao permitida.");
        }
        if (LocalDateTime.now().isAfter(criado.plusHours(12))) {
            throw new SecurityException("Exclusao permitida somente ate 12 horas apos o registro.");
        }
    }

    private LocalDateTime dataHoraRegistro(PrescricaoContinuacao prescricaoContinuacao) {
        String data = prescricaoContinuacao.getData() == null ? "" : prescricaoContinuacao.getData().trim();
        String hora = prescricaoContinuacao.getHora() == null ? "" : prescricaoContinuacao.getHora().trim();
        if (data.isEmpty() || hora.isEmpty()) {
            return null;
        }
        String dataHora = data + "T" + hora;
        if (hora.length() == 5) {
            dataHora += ":00";
        }
        try {
            return LocalDateTime.parse(dataHora);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void preencher(PrescricaoContinuacao prescricaoContinuacao, PrescricaoContinuacaoDto dto) {
        prescricaoContinuacao.setObservacao(dto.getObservacao());
        prescricaoContinuacao.setObservacao2(dto.getObservacao2());
        prescricaoContinuacao.setPadiast(dto.getPadiast());
        prescricaoContinuacao.setPasist(dto.getPasist());
        prescricaoContinuacao.setFc(dto.getFc());
        prescricaoContinuacao.setTax(dto.getTax());
        prescricaoContinuacao.setFr(dto.getFr());
        prescricaoContinuacao.setSpo(dto.getSpo());
        prescricaoContinuacao.setPeso(dto.getPeso());
        prescricaoContinuacao.setPeso2(dto.getPeso2());
        prescricaoContinuacao.setBam(dto.getBam());
        prescricaoContinuacao.setData(dto.getData());
        prescricaoContinuacao.setHora(dto.getHora());
        prescricaoContinuacao.setLocalsolicitante(dto.getLocalsolicitante());
        prescricaoContinuacao.setEnfermeiro(dto.getEnfermeiro());
        prescricaoContinuacao.setTecnico(dto.getTecnico());
        prescricaoContinuacao.setFeito(dto.isFeito());
        prescricaoContinuacao.setChecado(dto.isChecado());
        prescricaoContinuacao.setIdprescricao(dto.getIdprescricao());
        prescricaoContinuacao.setUuid(dto.getUuid());
    }

    private void validarDocumentoAberto(PrescricaoContinuacao prescricaoContinuacao) {
        if (assinaturaClinicaService.bloqueadoPorBam(
                AssinaturaClinicaService.PRESCRICAO_ESTRUTURADA_BAM,
                prescricaoContinuacao.getBam())) {
            throw new IllegalStateException("Prescricao assinada nao pode ser alterada. Registre um adendo.");
        }
        if (prescricaoContinuacao.getIdprescricao() > 0
                && assinaturaClinicaService.bloqueadoPorDocumento(
                        AssinaturaClinicaService.PRESCRICAO_MEDICA,
                        prescricaoContinuacao.getIdprescricao())) {
            throw new IllegalStateException("Prescricao assinada nao pode ser alterada. Registre um adendo.");
        }
        if (assinaturaClinicaService.bloqueadoPorDocumento(
                "PRESCRICAO_CONTINUACAO",
                prescricaoContinuacao.getIdprescricaocontinuacao())) {
            throw new IllegalStateException("Continuacao da prescricao assinada nao pode ser alterada. Registre um adendo.");
        }
    }
}

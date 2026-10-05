package br.com.iv.service;

import br.com.iv.entity.*;
import br.com.iv.dto.PharmaciaConfirmacaoRequest;
import br.com.iv.pharmacia.validacao.dto.ItemDispensacaoDto;
import br.com.iv.security.SecurityPermissionService;
import br.com.iv.security.LoginRateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@ApplicationScoped
public class PharmaciaDispensacaoControleService {
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject SecurityPermissionService security;
    @Inject LoginRateLimitService rate;

    public String hash(PharmaciaConfirmacaoRequest req) {
        try {
            ObjectNode json = mapper.valueToTree(req); json.remove("duplaChecagemSenha"); json.remove("chaveConfirmacao");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(json)));
        } catch (Exception e) { throw new IllegalArgumentException("Nao foi possivel identificar a confirmacao."); }
    }
    public List<PharmaciaDispensacao> recuperar(PharmaciaConfirmacaoRequest req, String hash) {
        PharmaciaOperacaoService.exigirTexto(req.chaveConfirmacao, 80, "Chave de confirmacao");
        PharmaciaDispensacao d = req.itens.get(0);
        PharmaciaConfirmacaoChave c = em.createQuery("from PharmaciaConfirmacaoChave where unidade = :u and chave = :c", PharmaciaConfirmacaoChave.class)
                .setParameter("u", d.getUnidadeSigla()).setParameter("c", req.chaveConfirmacao).getResultStream().findFirst().orElse(null);
        if (c == null) return null;
        if (!hash.equals(c.hash) || !d.getPacienteId().equals(c.paciente) || !d.getUsuarioMatricula().equals(c.matricula)) throw new IllegalArgumentException("Chave de confirmacao ja utilizada para outra solicitacao.");
        try { return mapper.readValue(c.respostaJson, mapper.getTypeFactory().constructCollectionType(List.class, PharmaciaDispensacao.class)); }
        catch (Exception e) { throw new IllegalStateException("Registro de confirmacao indisponivel."); }
    }
    public void registrar(PharmaciaConfirmacaoRequest req, String hash, List<PharmaciaDispensacao> lista) {
        em.flush(); PharmaciaDispensacao d = lista.get(0); PharmaciaConfirmacaoChave c = new PharmaciaConfirmacaoChave();
        c.chave = req.chaveConfirmacao; c.hash = hash; c.unidade = d.getUnidadeSigla(); c.paciente = d.getPacienteId(); c.matricula = d.getUsuarioMatricula(); c.criadoEm = LocalDateTime.now();
        try { c.respostaJson = mapper.writeValueAsString(lista); } catch (Exception e) { throw new IllegalStateException("Falha ao registrar confirmacao."); }
        em.persist(c);
    }

    static void validarConferencia(PharmaciaDispensacao d, PharmaciaProduto p) {
        if (!Boolean.TRUE.equals(d.getConferida())) throw new IllegalArgumentException("Confirme paciente, principio ativo, concentracao, apresentacao, dose, via e frequencia.");
        PharmaciaOperacaoService.exigirTexto(d.getConferenciaJustificativa(), 1000, "Registro da conferencia da prescricao");
        if (d.getPrescricaoItemId() == null) {
            PharmaciaOperacaoService.exigirTexto(d.getReceitaReferencia(), 120, "Referencia da prescricao externa/legada");
            LocalDate hoje = LocalDate.now();
            if (d.getReceitaData() == null || d.getReceitaValidade() == null || d.getReceitaData().isAfter(hoje) || d.getReceitaValidade().isBefore(hoje) || d.getReceitaValidade().isBefore(d.getReceitaData())) throw new IllegalArgumentException("Confira emissao e validade da prescricao externa.");
        }
        if ((Boolean.TRUE.equals(p.getAntimicrobiano()) || Boolean.TRUE.equals(p.getExigeReceita()) || (p.getListaControlada() != null && !p.getListaControlada().isBlank())) && !Boolean.TRUE.equals(d.getReceitaRetida())) throw new IllegalArgumentException("Registre a retencao/arquivo da prescricao exigida para o produto.");
    }

    public void conferir(PharmaciaDispensacao d, PharmaciaProduto p, ItemDispensacaoDto item) {
        validarConferencia(d, p);
        if (d.getBamId() != null) {
            Bam b = em.find(Bam.class, d.getBamId());
            if (b == null || b.getPaciente() == null || b.getPaciente().getIdpaciente() != d.getPacienteId().intValue() || !d.getUnidadeSigla().equals(b.getUnidade())) throw new IllegalArgumentException("BAM nao corresponde ao paciente e unidade.");
        }
        if (d.getPrescricaoItemId() != null) {
            PrescricaoItemEstruturado rx = em.find(PrescricaoItemEstruturado.class, d.getPrescricaoItemId(), LockModeType.PESSIMISTIC_WRITE);
            if (rx == null || !"ATIVO".equalsIgnoreCase(rx.getStatus()) || !Objects.equals(rx.getIdpaciente(), d.getPacienteId().intValue()) || !d.getUnidadeSigla().equals(rx.getUnidade()) || !Objects.equals(d.getBamId(), rx.getIdbam()) || !"MEDICAMENTO".equalsIgnoreCase(rx.getTipoItem())) throw new IllegalArgumentException("Prescricao suspensa ou sem vinculo com paciente, BAM e unidade.");
            if (rx.getIdprescricao() != null) {
                Prescricao original = em.find(Prescricao.class, rx.getIdprescricao(), LockModeType.PESSIMISTIC_WRITE);
                if (original == null || original.isInativo()) throw new IllegalArgumentException("Prescricao original cancelada ou retificada.");
            }
            if (rx.getDose() == null || rx.getDose().isBlank() || rx.getVia() == null || rx.getVia().isBlank() || rx.getFrequencia() == null || rx.getFrequencia().isBlank()) throw new IllegalArgumentException("Prescricao estruturada sem dose, via ou frequencia.");
            item.setDose(rx.getDose()); item.setFrequencia(rx.getFrequencia()); item.setVia(rx.getVia());
            d.setReceitaReferencia("Prescricao item #" + rx.getIditem());
            // A correspondencia entre texto prescrito e produto selecionado e explicitamente conferida pelo profissional.
            // Nao assumir equivalencia de concentracao/forma por semelhanca de nome.
        }
    }

    public void autenticarSegundo(PharmaciaConfirmacaoRequest req, String primeiro) {
        String matricula = PharmaciaOperacaoService.exigirTexto(req.duplaChecagemMatricula, 40, "Matricula do conferente");
        if (matricula.equals(primeiro) || req.duplaChecagemSenha == null || req.duplaChecagemSenha.isBlank()) throw new IllegalArgumentException("Segundo profissional deve confirmar com sua propria credencial.");
        if (rate.segundosBloqueadoMatricula(matricula) > 0 || rate.segundosBloqueadoIp("farmacia:" + primeiro) > 0) throw new IllegalArgumentException("Credencial temporariamente bloqueada. Aguarde antes de tentar novamente.");
        br.com.iv.security.AuthenticatedUser segundo = security.authenticate(matricula, req.duplaChecagemSenha);
        req.duplaChecagemSenha = null;
        if (segundo == null) { rate.registrarFalhaMatricula(matricula); rate.registrarFalhaIp("farmacia:" + primeiro); throw new IllegalArgumentException("Credencial do segundo profissional invalida."); }
        if (!segundo.hasAnyPermission(List.of("kfarmaciacentral", "kfarmhmsfx", "kremedios", "kadm"))) throw new IllegalArgumentException("Segundo profissional sem acesso a farmacia.");
        rate.registrarSucessoMatricula(matricula); rate.registrarSucessoIp("farmacia:" + primeiro);
    }
    public void registrarCiclo(List<PharmaciaDispensacao> lista) {
        for (PharmaciaDispensacao d : lista) if (d.getPrescricaoItemId() != null) {
            PrescricaoItemCicloEvento e = new PrescricaoItemCicloEvento(); e.setIditem(d.getPrescricaoItemId()); e.setIdbam(d.getBamId()); e.setIdpaciente(d.getPacienteId().intValue());
            e.setUnidade(d.getUnidadeSigla()); e.setTipoEvento("DISPENSACAO"); e.setStatusEvento("REALIZADO"); e.setQuantidade(java.math.BigDecimal.valueOf(d.getQuantidade()));
            e.setResponsavelMatricula(d.getUsuarioMatricula()); e.setDataHoraRealizada(d.getDataHora()); e.setObservacao("Dispensacao #" + d.getId() + ", lote #" + d.getLoteId());
            // Sem doseAdministrada: entregar nao equivale a administrar.
            em.persist(e);
        }
    }
}

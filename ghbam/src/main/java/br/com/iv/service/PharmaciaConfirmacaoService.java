package br.com.iv.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.iv.dto.PharmaciaConfirmacaoRequest;
import br.com.iv.entity.*;
import br.com.iv.pharmacia.validacao.dto.*;
import br.com.iv.pharmacia.validacao.entity.PharmaciaValidacao;
import br.com.iv.pharmacia.validacao.service.ValidacaoFarmaceuticaService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PharmaciaConfirmacaoService {
    @Inject EntityManager em;
    @Inject PharmaciaDispensacaoControleService controle;
    @Inject PharmaciaDispensacaoService dispensacoes;
    @Inject ValidacaoFarmaceuticaService validacao;
    @Inject ObjectMapper mapper;
    @Inject br.com.iv.security.SecurityPermissionService security;

    static String texto(String s) { return s == null ? "" : s.trim(); }

    static void exigirDecisao(ValidacaoFarmaceuticaResponse resultado, String justificativa, String dupla, String matricula) {
        if ((resultado.isExigeJustificativa() || resultado.isPossuiCritico()) && texto(justificativa).isEmpty()) {
            throw new IllegalArgumentException("Justificativa clinica obrigatoria para alertas criticos.");
        }
        if (resultado.isExigeDuplaChecagem() && (texto(dupla).isEmpty() || texto(dupla).equals(texto(matricula)))) {
            throw new IllegalArgumentException("Dupla checagem exige a matricula de outro profissional.");
        }
    }

    @Transactional
    public List<PharmaciaDispensacao> confirmar(PharmaciaConfirmacaoRequest req) {
        if (req == null || req.itens == null || req.itens.isEmpty() || req.itens.size() > 100) throw new IllegalArgumentException("Informe entre 1 e 100 itens.");
        req.itens.forEach(PharmaciaDispensacaoService::validar);
        PharmaciaDispensacao primeiro = req.itens.get(0);
        if (req.itens.stream().anyMatch(d -> !d.getPacienteId().equals(primeiro.getPacienteId()) || !d.getUnidadeSigla().equals(primeiro.getUnidadeSigla()))) {
            throw new IllegalArgumentException("Itens de pacientes ou unidades diferentes.");
        }
        String hash = controle.hash(req);
        int pacienteId = Math.toIntExact(primeiro.getPacienteId());
        Pacientes paciente = em.find(Pacientes.class, pacienteId, LockModeType.PESSIMISTIC_WRITE);
        if (paciente == null) throw new IllegalArgumentException("Paciente nao encontrado.");
        List<PharmaciaDispensacao> anterior = controle.recuperar(req, hash);
        if (anterior != null) return anterior;
        ValidacaoDispensacaoRequest contexto = req.contexto == null ? new ValidacaoDispensacaoRequest() : req.contexto;
        contexto.setIdpaciente(pacienteId); contexto.setPacienteNome(paciente.getNome()); contexto.setUnidade(primeiro.getUnidadeSigla());
        contexto.setSexo(paciente.getSexo());
        try { contexto.setIdadeAnos(Period.between(LocalDate.parse(paciente.getNascimento()), LocalDate.now()).getYears()); }
        catch (RuntimeException e) { contexto.setIdadeAnos(null); }
        List<ProntuarioClinicoItem> alergias = em.createQuery(
            "from ProntuarioClinicoItem where idpaciente = :paciente and tipo = 'ALERGIA' and (status is null or upper(status) <> 'INATIVO')", ProntuarioClinicoItem.class)
            .setParameter("paciente", pacienteId).getResultList();
        contexto.setAlergias(alergias.stream().map(a -> texto(a.getTitulo()).isEmpty() ? a.getDescricao() : a.getTitulo()).filter(Objects::nonNull).toList());
        List<ItemDispensacaoDto> informados = contexto.getItens();
        List<ItemDispensacaoDto> canonicos = new ArrayList<>();
        req.itens.stream().map(d -> d.getLote().getId()).distinct().sorted().forEach(id -> em.find(PharmaciaLote.class, id, LockModeType.PESSIMISTIC_WRITE));
        for (PharmaciaDispensacao d : req.itens) {
            PharmaciaLote lote = em.find(PharmaciaLote.class, d.getLote().getId(), LockModeType.PESSIMISTIC_WRITE);
            PharmaciaMovimentacaoService.validarLoteDispensavel(lote);
            d.setPacienteNome(paciente.getNome()); d.setPacienteCpf(paciente.getCpf());
            ItemDispensacaoDto item = new ItemDispensacaoDto();
            item.setLoteId(lote.getId()); item.setProdutoId(lote.getProduto().getId());
            item.setProdutoNome(lote.getProduto().getNomeComercial()); item.setPrincipioAtivo(lote.getProduto().getPrincipioAtivo());
            item.setQuantidade(d.getQuantidade()); item.setDuracao(d.getDuracao());
            informados.stream().filter(i -> lote.getId().equals(i.getLoteId())).findFirst().ifPresent(i -> {
                item.setDose(i.getDose()); item.setFrequencia(i.getFrequencia()); item.setVia(i.getVia());
            });
            item.setConcentracao(lote.getProduto().getConcentracao()); item.setFormaFarmaceutica(lote.getProduto().getFormaFarmaceutica());
            controle.conferir(d, lote.getProduto(), item);
            canonicos.add(item);
        }
        contexto.setIdbam(primeiro.getBamId());
        List<String> emUso = new ArrayList<>(contexto.getMedicamentosEmUso());
        if (primeiro.getBamId() != null) {
            List<PrescricaoItemEstruturado> ativos = em.createQuery("from PrescricaoItemEstruturado where idpaciente = :p and idbam = :b and upper(status) = 'ATIVO' and upper(tipoItem) = 'MEDICAMENTO'", PrescricaoItemEstruturado.class)
                .setParameter("p", pacienteId).setParameter("b", primeiro.getBamId()).getResultList();
            ativos.stream().filter(rx -> req.itens.stream().noneMatch(d -> Objects.equals(d.getPrescricaoItemId(), rx.getIditem()))).map(PrescricaoItemEstruturado::getMedicamento).filter(Objects::nonNull).forEach(emUso::add);
        }
        List<PharmaciaDispensacao> emCurso = em.createQuery("from PharmaciaDispensacao where pacienteId = :p and ativo = true", PharmaciaDispensacao.class).setParameter("p", primeiro.getPacienteId()).getResultList();
        emCurso.stream().filter(d -> d.getDuracao() != null && d.getDataHora() != null && d.getDataHora().plusDays(d.getDuracao()).isAfter(java.time.LocalDateTime.now())).map(PharmaciaDispensacao::getProdutoNome).filter(Objects::nonNull).forEach(emUso::add);
        contexto.setMedicamentosEmUso(emUso.stream().distinct().toList());
        contexto.setItens(canonicos);
        ValidacaoFarmaceuticaResponse resultado = validacao.validar(contexto);
        exigirDecisao(resultado, req.justificativa, req.duplaChecagemMatricula, primeiro.getUsuarioMatricula());
        boolean altaVigilancia = req.itens.stream().anyMatch(d -> Boolean.TRUE.equals(em.find(PharmaciaLote.class, d.getLoteId()).getProduto().getAltaVigilancia()));
        if (resultado.isExigeDuplaChecagem() || altaVigilancia) controle.autenticarSegundo(req, primeiro.getUsuarioMatricula());
        List<PharmaciaDispensacao> saved = dispensacoes.saveBatch(req.itens);
        PharmaciaValidacao registro = new PharmaciaValidacao();
        registro.setIdpaciente(pacienteId); registro.setPacienteNome(paciente.getNome()); registro.setIdbam(contexto.getIdbam());
        registro.setUnidadeSigla(primeiro.getUnidadeSigla()); registro.setUsuarioMatricula(primeiro.getUsuarioMatricula());
        registro.setVersaoBase(resultado.getVersaoBase()); registro.setTotalAlertas(resultado.getAlertas().size());
        registro.setAlertasCriticos((int) resultado.getAlertas().stream().filter(a -> "CRITICAL".equals(a.getSeveridade())).count());
        registro.setJustificativa(texto(req.justificativa)); registro.setDuplaChecagemMatricula(texto(req.duplaChecagemMatricula));
        registro.setDesfecho(texto(req.justificativa).isEmpty() ? "DISPENSADO" : "DISPENSADO_COM_JUSTIFICATIVA");
        try {
            registro.setItensJson(mapper.writeValueAsString(canonicos)); registro.setAlertasJson(mapper.writeValueAsString(resultado.getAlertas()));
        } catch (Exception e) { throw new IllegalStateException("Nao foi possivel registrar a validacao farmaceutica.", e); }
        em.persist(registro);
        controle.registrarCiclo(saved);
        controle.registrar(req, hash, saved);
        return saved;
    }
}

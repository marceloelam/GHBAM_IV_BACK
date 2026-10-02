package br.com.iv.service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import br.com.iv.dto.ListaAnestesicaDto;
import br.com.iv.entity.ListaAnestesica;
import br.com.iv.repository.ListaAnestesicaRepository;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ListaAnestesicaService implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    EntityManager entityManager;

    @Inject
    ListaAnestesicaRepository listaAnestesicaRepository;

    @Inject
    AssinaturaClinicaService assinaturaClinicaService;

    public List<ListaAnestesica> listarPorBam(int bam) {
        return listaAnestesicaRepository.listarPorBam(bam);
    }

    public List<ListaAnestesica> listarPorBamEData(int bam, String data) {
        return listaAnestesicaRepository.listarPorBamEData(bam, data);
    }

    public Optional<ListaAnestesica> buscarPorId(int id) {
        return ListaAnestesica.findByIdOptional(id);
    }

    @Transactional
    public ListaAnestesica salvar(ListaAnestesicaDto dto, AuthenticatedUser usuario) {
        ListaAnestesica listaAnestesica = new ListaAnestesica();
        
        // Dados principais
        listaAnestesica.setBam(dto.getBam());
        listaAnestesica.setData(dto.getData());
        
        // Dados do paciente
        listaAnestesica.setNomePaciente(dto.getNomePaciente());
        listaAnestesica.setProntuario(dto.getProntuario());
        listaAnestesica.setDataNascimento(dto.getDataNascimento());
        listaAnestesica.setNomeMae(dto.getNomeMae());
        listaAnestesica.setEnfLeito(dto.getEnfLeito());
        
        // Dados da anestesia
        listaAnestesica.setNumeroSala(dto.getNumeroSala());
        listaAnestesica.setAsa(dto.getAsa());
        listaAnestesica.setEfeitoPreMedicacao(dto.getEfeitoPreMedicacao());
        
        // Agentes anestésicos
        listaAnestesica.setO2(dto.getO2());
        listaAnestesica.setN2o(dto.getN2o());
        listaAnestesica.setPropofol(dto.getPropofol());
        listaAnestesica.setRemifentanil(dto.getRemifentanil());
        listaAnestesica.setCristaloides(dto.getCristaloides());
        listaAnestesica.setHemoderivados(dto.getHemoderivados());
        
        // Monitorização
        listaAnestesica.setPvc(dto.getPvc());
        listaAnestesica.setPa(dto.getPa());
        listaAnestesica.setBis(dto.getBis());
        listaAnestesica.setResp(dto.getResp());
        listaAnestesica.setTemp(dto.getTemp());
        listaAnestesica.setSpo2(dto.getSpo2());
        listaAnestesica.setEtco2(dto.getEtco2());
        
        // Procedimento - Intubação
        listaAnestesica.setIntubacao(dto.getIntubacao());
        listaAnestesica.setIntubacaoDificil(dto.isIntubacaoDificil());
        listaAnestesica.setIntubacaoOral(dto.isIntubacaoOral());
        listaAnestesica.setIntubacaoNasal(dto.isIntubacaoNasal());
        listaAnestesica.setComBalonete(dto.isComBalonete());
        listaAnestesica.setCanulaNumero(dto.getCanulaNumero());
        
        // Procedimento - Ventilação
        listaAnestesica.setVentilacao(dto.getVentilacao());
        listaAnestesica.setVentilacaoEspontanea(dto.isVentilacaoEspontanea());
        listaAnestesica.setVentilacaoControlada(dto.isVentilacaoControlada());
        listaAnestesica.setVentilacaoManual(dto.isVentilacaoManual());
        listaAnestesica.setVentilacaoMecanica(dto.isVentilacaoMecanica());
        
        // Ventilador
        listaAnestesica.setVentiladorModo(dto.getVentiladorModo());
        listaAnestesica.setVentiladorPcv(dto.isVentiladorPcv());
        listaAnestesica.setVentiladorVcv(dto.isVentiladorVcv());
        listaAnestesica.setVc(dto.getVc());
        listaAnestesica.setFreq(dto.getFreq());
        listaAnestesica.setPres(dto.getPres());
        listaAnestesica.setPeep(dto.getPeep());
        listaAnestesica.setRelacaoIE(dto.getRelacaoIE());
        listaAnestesica.setComReabsorcao(dto.isComReabsorcao());
        listaAnestesica.setSemReabsorcao(dto.isSemReabsorcao());
        listaAnestesica.setCircComValv(dto.isCircComValv());
        
        // Outros dados
        listaAnestesica.setIntercorrencias(dto.isIntercorrencias());
        listaAnestesica.setDescricaoIntercorrencias(dto.getDescricaoIntercorrencias());
        listaAnestesica.setCirurgioes(dto.getCirurgioes());
        listaAnestesica.setAnestesistas(identificacaoProfissional(usuario));
        listaAnestesica.setProcedimento(dto.getProcedimento());
        listaAnestesica.setHoraInicio(dto.getHoraInicio());
        listaAnestesica.setHoraFim(dto.getHoraFim());
        listaAnestesica.setHoraHemoderivado(dto.getHoraHemoderivado());
        listaAnestesica.setHoraAntibiotico(dto.getHoraAntibiotico());
        
        listaAnestesica.setSupraglotico(dto.getSupraglotico());
        listaAnestesica.setMMacial(dto.isMMacial());
        listaAnestesica.setMLaringea(dto.isMLaringea());
        
        listaAnestesica.setObservacoes(dto.getObservacoes());
        
        entityManager.persist(listaAnestesica);
        entityManager.flush();
        return listaAnestesica;
    }

    @Transactional
    public ListaAnestesica atualizar(int id, ListaAnestesicaDto dto, AuthenticatedUser usuario) {
        Optional<ListaAnestesica> listaAnestesicaOp = ListaAnestesica.findByIdOptional(id);
        if (listaAnestesicaOp.isEmpty()) {
            throw new RuntimeException("Lista Anestésica não encontrada com ID: " + id);
        }

        ListaAnestesica listaAnestesica = listaAnestesicaOp.get();
        bloquearSeAssinado("FICHA_ANESTESICA", id);
        
        // Dados principais
        listaAnestesica.setBam(dto.getBam());
        listaAnestesica.setData(dto.getData());
        
        // Dados do paciente
        listaAnestesica.setNomePaciente(dto.getNomePaciente());
        listaAnestesica.setProntuario(dto.getProntuario());
        listaAnestesica.setDataNascimento(dto.getDataNascimento());
        listaAnestesica.setNomeMae(dto.getNomeMae());
        listaAnestesica.setEnfLeito(dto.getEnfLeito());
        
        // Dados da anestesia
        listaAnestesica.setNumeroSala(dto.getNumeroSala());
        listaAnestesica.setAsa(dto.getAsa());
        listaAnestesica.setEfeitoPreMedicacao(dto.getEfeitoPreMedicacao());
        
        // Agentes anestésicos
        listaAnestesica.setO2(dto.getO2());
        listaAnestesica.setN2o(dto.getN2o());
        listaAnestesica.setPropofol(dto.getPropofol());
        listaAnestesica.setRemifentanil(dto.getRemifentanil());
        listaAnestesica.setCristaloides(dto.getCristaloides());
        listaAnestesica.setHemoderivados(dto.getHemoderivados());
        
        // Monitorização
        listaAnestesica.setPvc(dto.getPvc());
        listaAnestesica.setPa(dto.getPa());
        listaAnestesica.setBis(dto.getBis());
        listaAnestesica.setResp(dto.getResp());
        listaAnestesica.setTemp(dto.getTemp());
        listaAnestesica.setSpo2(dto.getSpo2());
        listaAnestesica.setEtco2(dto.getEtco2());
        
        // Procedimento - Intubação
        listaAnestesica.setIntubacao(dto.getIntubacao());
        listaAnestesica.setIntubacaoDificil(dto.isIntubacaoDificil());
        listaAnestesica.setIntubacaoOral(dto.isIntubacaoOral());
        listaAnestesica.setIntubacaoNasal(dto.isIntubacaoNasal());
        listaAnestesica.setComBalonete(dto.isComBalonete());
        listaAnestesica.setCanulaNumero(dto.getCanulaNumero());
        
        // Procedimento - Ventilação
        listaAnestesica.setVentilacao(dto.getVentilacao());
        listaAnestesica.setVentilacaoEspontanea(dto.isVentilacaoEspontanea());
        listaAnestesica.setVentilacaoControlada(dto.isVentilacaoControlada());
        listaAnestesica.setVentilacaoManual(dto.isVentilacaoManual());
        listaAnestesica.setVentilacaoMecanica(dto.isVentilacaoMecanica());
        
        // Ventilador
        listaAnestesica.setVentiladorModo(dto.getVentiladorModo());
        listaAnestesica.setVentiladorPcv(dto.isVentiladorPcv());
        listaAnestesica.setVentiladorVcv(dto.isVentiladorVcv());
        listaAnestesica.setVc(dto.getVc());
        listaAnestesica.setFreq(dto.getFreq());
        listaAnestesica.setPres(dto.getPres());
        listaAnestesica.setPeep(dto.getPeep());
        listaAnestesica.setRelacaoIE(dto.getRelacaoIE());
        listaAnestesica.setComReabsorcao(dto.isComReabsorcao());
        listaAnestesica.setSemReabsorcao(dto.isSemReabsorcao());
        listaAnestesica.setCircComValv(dto.isCircComValv());
        
        // Outros dados
        listaAnestesica.setIntercorrencias(dto.isIntercorrencias());
        listaAnestesica.setDescricaoIntercorrencias(dto.getDescricaoIntercorrencias());
        listaAnestesica.setCirurgioes(dto.getCirurgioes());
        listaAnestesica.setAnestesistas(identificacaoProfissional(usuario));
        listaAnestesica.setProcedimento(dto.getProcedimento());
        listaAnestesica.setHoraInicio(dto.getHoraInicio());
        listaAnestesica.setHoraFim(dto.getHoraFim());
        listaAnestesica.setHoraHemoderivado(dto.getHoraHemoderivado());
        listaAnestesica.setHoraAntibiotico(dto.getHoraAntibiotico());
        
        listaAnestesica.setSupraglotico(dto.getSupraglotico());
        listaAnestesica.setMMacial(dto.isMMacial());
        listaAnestesica.setMLaringea(dto.isMLaringea());
        
        listaAnestesica.setObservacoes(dto.getObservacoes());
        
        entityManager.merge(listaAnestesica);
        entityManager.flush();
        return listaAnestesica;
    }

    @Transactional
    public void remover(int id, AuthenticatedUser usuario) {
        Optional<ListaAnestesica> listaAnestesicaOp = ListaAnestesica.findByIdOptional(id);
        if (listaAnestesicaOp.isEmpty()) {
            throw new RuntimeException("Lista Anestésica não encontrada com ID: " + id);
        }

        ListaAnestesica listaAnestesica = listaAnestesicaOp.get();
        bloquearSeAssinado("FICHA_ANESTESICA", id);
        listaAnestesica.delete();
    }

    private String identificacaoProfissional(AuthenticatedUser usuario) {
        String nome = usuario == null || usuario.nome() == null ? "" : usuario.nome().trim();
        String crm = usuario == null || usuario.crm() == null ? "" : usuario.crm().trim();
        return crm.isBlank() ? nome : nome + " - CRM: " + crm;
    }

    private void bloquearSeAssinado(String tipoDocumento, Integer iddocumento) {
        if (assinaturaClinicaService.bloqueadoPorDocumento(tipoDocumento, iddocumento)) {
            throw new WebApplicationException(
                    "Documento ja assinado. Registre um adendo em vez de alterar o conteudo original.",
                    Response.Status.CONFLICT);
        }
    }
}

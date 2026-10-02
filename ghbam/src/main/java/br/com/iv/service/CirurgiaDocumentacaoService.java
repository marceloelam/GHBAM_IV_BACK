package br.com.iv.service;

import java.time.LocalDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;

import br.com.iv.entity.CirurgiaAgendamento;
import br.com.iv.entity.CirurgiaDescricao;
import br.com.iv.entity.CirurgiaFichaAnestesica;
import br.com.iv.entity.CirurgiaAgendamento;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class CirurgiaDocumentacaoService {

    @Inject
    FluxoCirurgicoAssistencial fluxoCirurgicoAssistencial;

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private void garantirCirurgia(int idcirurgia) {
        if (CirurgiaAgendamento.findById((long) idcirurgia) == null) {
            throw new IllegalArgumentException("Cirurgia nao encontrada.");
        }
    }

    public CirurgiaDescricao obterDescricao(int idcirurgia) {
        return CirurgiaDescricao.find("idcirurgia", idcirurgia).firstResult();
    }

    @Transactional
    public CirurgiaDescricao salvarDescricao(int idcirurgia, CirurgiaDescricao dados, AuthenticatedUser user) {
        garantirCirurgia(idcirurgia);
        CirurgiaDescricao d = CirurgiaDescricao.find("idcirurgia", idcirurgia).firstResult();
        if (d == null) {
            d = new CirurgiaDescricao();
            d.setIdcirurgia(idcirurgia);
        }
        if (d.isAssinado()) {
            throw new IllegalArgumentException("Descricao cirurgica assinada nao pode ser alterada.");
        }
        d.setAchados(dados.getAchados());
        d.setDescricao(dados.getDescricao());
        d.setIntercorrencias(dados.getIntercorrencias());
        d.setSangramentoMl(dados.getSangramentoMl());
        d.setHemocomponentes(texto(dados.getHemocomponentes()));
        d.setMaterialAnatomiaPatologica(dados.isMaterialAnatomiaPatologica());
        d.setMaterialAnatomiaDescricao(texto(dados.getMaterialAnatomiaDescricao()));
        if (d.isMaterialAnatomiaPatologica() && d.getMaterialAnatomiaDescricao().isBlank()) {
            throw new IllegalArgumentException("Descreva o material enviado para anatomia patologica.");
        }
        d.setDrenos(texto(dados.getDrenos()));
        d.setCondicaoSaida(texto(dados.getCondicaoSaida()));
        d.setDestinoPos(texto(dados.getDestinoPos()));
        d.setAtualizadoEm(LocalDateTime.now());
        d.setUsuarioRegistro(user == null ? null : user.matricula());
        d.persist();
        return d;
    }

    @Transactional
    public CirurgiaDescricao assinarDescricao(int idcirurgia, AuthenticatedUser user) {
        CirurgiaDescricao d = CirurgiaDescricao.find("idcirurgia", idcirurgia).firstResult();
        if (d == null) {
            throw new IllegalArgumentException("Descricao cirurgica ainda nao foi preenchida.");
        }
        if (d.isAssinado()) {
            throw new IllegalArgumentException("Descricao cirurgica ja assinada.");
        }
        if (texto(d.getDescricao()).isBlank()) {
            throw new IllegalArgumentException("Preencha a descricao cirurgica antes de assinar.");
        }
        d.setAssinado(true);
        d.setAssinadoEm(LocalDateTime.now());
        d.setAssinadoPor(user == null ? null : user.nome());
        fluxoCirurgicoAssistencial.enfileirarDocumentoRnds(CirurgiaAgendamento.findById((long) idcirurgia), user);
        return d;
    }

    public CirurgiaFichaAnestesica obterFicha(int idcirurgia) {
        return CirurgiaFichaAnestesica.find("idcirurgia", idcirurgia).firstResult();
    }

    @Transactional
    public CirurgiaFichaAnestesica salvarFicha(int idcirurgia, CirurgiaFichaAnestesica dados, AuthenticatedUser user) {
        garantirCirurgia(idcirurgia);
        CirurgiaFichaAnestesica f = CirurgiaFichaAnestesica.find("idcirurgia", idcirurgia).firstResult();
        if (f == null) {
            f = new CirurgiaFichaAnestesica();
            f.setIdcirurgia(idcirurgia);
        }
        f.setIdanestesista(dados.getIdanestesista());
        f.setNomeanestesista(texto(dados.getNomeanestesista()).isBlank() && user != null
                ? user.nome()
                : texto(dados.getNomeanestesista()));
        f.setTecnica(texto(dados.getTecnica()));
        f.setAsa(texto(dados.getAsa()));
        f.setMallampati(texto(dados.getMallampati()));
        f.setJejumHoras(dados.getJejumHoras());
        f.setViaAerea(texto(dados.getViaAerea()));
        f.setAcessoVenoso(texto(dados.getAcessoVenoso()));
        f.setMonitorizacao(texto(dados.getMonitorizacao()));
        f.setDrogasJson(dados.getDrogasJson());
        f.setSinaisJson(dados.getSinaisJson());
        f.setIntercorrencias(dados.getIntercorrencias());
        if (dados.getAldrete() != null && (dados.getAldrete() < 0 || dados.getAldrete() > 10)) {
            throw new IllegalArgumentException("Indice de Aldrete deve estar entre 0 e 10.");
        }
        f.setAldrete(dados.getAldrete());
        f.setEvolucaoRpa(dados.getEvolucaoRpa());
        f.setAtualizadoEm(LocalDateTime.now());
        f.setUsuarioRegistro(user == null ? null : user.matricula());
        f.persist();
        return f;
    }
}

package br.com.iv.service;

import java.time.LocalDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import br.com.iv.entity.FaturamentoAuditoria;
import br.com.iv.security.AuthenticatedUser;

// Trilha de auditoria estruturada do modulo Faturamento SUS, no molde de
// IntegracaoSusAuditoria (mesmo domínio SUS ja tem esse precedente no projeto),
// com os campos extras pedidos pela secao 38 do escopo (entidade/identidade/
// valor anterior/valor novo/motivo).
@ApplicationScoped
public class FaturamentoAuditService {

    @Transactional
    public void registrar(String unidade, String acao, String status, String entidade, String identidade,
            String valorAnterior, String valorNovo, String motivo, String detalhes, AuthenticatedUser user) {
        FaturamentoAuditoria auditoria = new FaturamentoAuditoria();
        auditoria.setUnidade(unidade);
        auditoria.setAcao(acao);
        auditoria.setStatus(status);
        auditoria.setEntidade(entidade);
        auditoria.setIdentidade(identidade);
        auditoria.setValorAnterior(valorAnterior);
        auditoria.setValorNovo(valorNovo);
        auditoria.setMotivo(motivo);
        auditoria.setDetalhes(detalhes);
        auditoria.setUsuario(user == null ? "" : user.nome());
        auditoria.setMatricula(user == null ? "" : user.matricula());
        auditoria.setCriadoEm(LocalDateTime.now());
        auditoria.persist();
    }
}

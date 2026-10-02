package br.com.iv.service;

import java.time.LocalDateTime;
import java.util.List;

import br.com.iv.entity.LgpdSolicitacaoExclusao;
import br.com.iv.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

// Toda solicitacao de exclusao de dados de paciente e negada automaticamente:
// o prontuario eletronico tem guarda permanente (Resolucao CFM 1.821/2007) e a
// LGPD (art. 16, II) autoriza a retencao nesse caso. O texto abaixo e o que
// deve ser informado ao solicitante, junto com o que ainda e direito dele
// (acesso/copia e correcao de dado cadastral - ja atendido em
// GET /pacientes/exportar/{idpaciente}).
@ApplicationScoped
public class LgpdSolicitacaoExclusaoService {

    public static final String BASE_LEGAL =
            "Pedido de exclusao negado. O prontuario eletronico deste paciente esta sob guarda "
            + "permanente por forca da Resolucao CFM no 1.821/2007. A Lei Geral de Protecao de "
            + "Dados (Lei no 13.709/2018), art. 16, inciso II, autoriza o controlador a manter os "
            + "dados pessoais quando necessario para cumprimento de obrigacao legal ou regulatoria, "
            + "o que se aplica integralmente a este caso. O paciente mantem o direito de solicitar, "
            + "a qualquer momento: (i) acesso e copia dos proprios dados (LGPD art. 18, I e V); e "
            + "(ii) correcao de dado cadastral incorreto ou desatualizado (art. 18, III), sem "
            + "prejuizo da integridade do registro clinico ja lancado, que nao pode ser alterado "
            + "retroativamente.";

    @Transactional
    public LgpdSolicitacaoExclusao registrarPedido(int idpaciente, AuthenticatedUser atendente,
            String nomeSolicitanteExterno, String detalhePedido) {
        LgpdSolicitacaoExclusao solicitacao = new LgpdSolicitacaoExclusao();
        solicitacao.setIdpaciente(idpaciente);
        solicitacao.setNomeSolicitanteExterno(nomeSolicitanteExterno);
        solicitacao.setDetalhePedido(detalhePedido);
        solicitacao.setStatus("NEGADA");
        solicitacao.setBaseLegal(BASE_LEGAL);
        solicitacao.setMatriculaAtendente(atendente.matricula());
        solicitacao.setNomeAtendente(atendente.nome());
        solicitacao.setCriadoEm(LocalDateTime.now());
        solicitacao.persist();
        return solicitacao;
    }

    public List<LgpdSolicitacaoExclusao> historicoPorPaciente(int idpaciente) {
        return LgpdSolicitacaoExclusao.list("idpaciente = ?1 order by criadoEm desc", idpaciente);
    }
}

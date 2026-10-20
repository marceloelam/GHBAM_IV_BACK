package br.com.iv.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import br.com.iv.dto.LogDto;
import br.com.iv.entity.Servidor;
import br.com.iv.entity.Servidor2;
import br.com.iv.repository.ServidorRepository;
import br.com.iv.repository.Servidor2Repository;
import br.com.iv.security.AuthenticatedUser;
import br.com.iv.security.SecurityPermissionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ServidorMatriculaService {
    @Inject EntityManager entityManager;
    @Inject ServidorRepository servidorRepository;
    @Inject Servidor2Repository servidor2Repository;
    @Inject SecurityPermissionService securityPermissionService;
    @Inject SessaoAtivaService sessaoAtivaService;
    @Inject LogService logService;

    @Transactional
    public Map<String, Object> alterar(int id, String anterior, String nova, String motivo, AuthenticatedUser usuario) {
        if (usuario == null || usuario.matricula().isBlank()) {
            throw erro(401, "Sessao nao autenticada.");
        }
        if (!usuario.hasPermission("kadm")) {
            throw erro(403, "Somente administradores podem alterar matriculas.");
        }
        nova = nova == null ? "" : nova.trim();
        motivo = motivo == null ? "" : motivo.trim();
        if (!nova.matches("[A-Za-z0-9._-]{1,15}")) {
            throw erro(400, "Informe uma matricula de ate 15 caracteres: letras, numeros, ponto, traco ou sublinhado.");
        }
        if (motivo.length() < 5 || motivo.length() > 200 || motivo.chars().anyMatch(Character::isISOControl)) {
            throw erro(400, "Informe o motivo da alteracao, entre 5 e 200 caracteres, em uma linha.");
        }
        if (id <= 0 || anterior == null || anterior.isBlank()) {
            throw erro(400, "Selecione um servidor cadastrado.");
        }
        // Servidor2 nao tem restricao UNIQUE na entidade. Serializa com os
        // cadastros legados tambem, evitando conflito entre checagem e gravacao.
        entityManager.createNativeQuery("LOCK TABLE servidor, servidor2 IN EXCLUSIVE MODE").executeUpdate();
        Servidor servidor = entityManager.find(Servidor.class, id);
        if (servidor == null) throw erro(404, "Servidor nao encontrado.");
        // A autenticacao pode ter carregado a propria conta antes do bloqueio.
        entityManager.refresh(servidor);
        boolean superusuario = usuario.hasPermission("ksuperusuario") || usuario.hasPermission("superusuario");
        if (!superusuario && (usuario.unidade() == null || usuario.unidade().isBlank()
                || servidor.getUnidade() == null || !usuario.unidade().trim().equalsIgnoreCase(servidor.getUnidade().trim()))) {
            throw erro(403, "O servidor nao pertence a unidade logada.");
        }
        if (!anterior.equals(servidor.getMatricula())) {
            throw erro(409, "A matricula foi alterada por outro usuario. Busque o cadastro novamente.");
        }
        if (nova.equals(anterior)) throw erro(400, "A nova matricula deve ser diferente da atual.");
        if (servidorRepository.count("matricula", nova) > 0 || servidor2Repository.count("matricula", nova) > 0) {
            throw erro(409, "A nova matricula ja esta cadastrada.");
        }
        if (servidorRepository.count("matricula", anterior) != 1) {
            throw erro(409, "A matricula atual possui cadastros duplicados. Regularize-os antes da troca.");
        }
        List<Servidor2> complementares = servidor2Repository.list("matricula", anterior);
        if (complementares.size() > 1) {
            throw erro(409, "A matricula atual possui permissoes duplicadas. Regularize-as antes da troca.");
        }
        servidor.setMatricula(nova);
        complementares.forEach(registro -> registro.setMatricula(servidor.getMatricula()));
        // IDs, permissoes e registros historicos permanecem nos registros atuais.
        sessaoAtivaService.encerrarSessaoPorMatricula(anterior);
        LogDto log = new LogDto();
        log.setData(LocalDate.now());
        log.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        log.setUnidade(servidor.getUnidade());
        log.setEvento("ALTERACAO DE MATRICULA - servidor ID " + id + ": " + anterior + " -> " + nova
                + "; operador " + usuario.matricula() + "; motivo: " + motivo);
        logService.salvarLog(log);
        entityManager.flush();
        securityPermissionService.invalidateUserCache(anterior);
        securityPermissionService.invalidateUserCache(nova);
        return Map.of("idservidor", id, "matriculaAnterior", anterior, "matricula", nova,
                "cadastroComplementarAtualizado", !complementares.isEmpty());
    }

    private static WebApplicationException erro(int status, String mensagem) {
        return new WebApplicationException(Response.status(status).entity(Map.of("mensagem", mensagem)).build());
    }

    static void validarMatriculaCadastro(String atual, String enviada) {
        if (enviada != null && !enviada.equals(atual)) {
            throw erro(409, "A matricula do cadastro mudou. Busque o servidor novamente. Para trocar a matricula, use a acao Alterar matricula.");
        }
    }
}

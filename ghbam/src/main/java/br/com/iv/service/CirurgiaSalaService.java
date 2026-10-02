package br.com.iv.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import br.com.iv.entity.CirurgiaAgendamento;
import br.com.iv.entity.CirurgiaSala;
import br.com.iv.repository.CirurgiaSalaRepository;
import br.com.iv.security.AuthenticatedUser;

@ApplicationScoped
public class CirurgiaSalaService {

    @Inject
    CirurgiaSalaRepository salaRepository;

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    public List<CirurgiaSala> listar(String unidade, boolean somenteAtivas) {
        return salaRepository.listarPorUnidade(unidade, somenteAtivas);
    }

    @Transactional
    public CirurgiaSala criar(CirurgiaSala dados, AuthenticatedUser user) {
        if (dados == null || texto(dados.getNome()).isBlank()) {
            throw new IllegalArgumentException("Nome da sala e obrigatorio.");
        }
        String unidade = texto(dados.getUnidade());
        if (unidade.isBlank() && user != null) {
            unidade = texto(user.unidade());
        }
        if (unidade.isBlank() || "TODAS".equalsIgnoreCase(unidade)) {
            throw new IllegalArgumentException("Unidade e obrigatoria para cadastrar sala cirurgica.");
        }
        if (salaRepository.nomeDuplicado(unidade, texto(dados.getNome()), null)) {
            throw new IllegalArgumentException("Ja existe uma sala com esse nome nesta unidade.");
        }
        CirurgiaSala s = new CirurgiaSala();
        s.setNome(texto(dados.getNome()));
        s.setUnidade(unidade);
        s.setCnes(texto(dados.getCnes()));
        s.setEspecialidades(texto(dados.getEspecialidades()));
        s.setHoraInicioExpediente(texto(dados.getHoraInicioExpediente()).isBlank() ? "07:00" : texto(dados.getHoraInicioExpediente()));
        s.setHoraFimExpediente(texto(dados.getHoraFimExpediente()).isBlank() ? "19:00" : texto(dados.getHoraFimExpediente()));
        s.setTempoLimpezaMin(dados.getTempoLimpezaMin() <= 0 ? 30 : dados.getTempoLimpezaMin());
        s.setAtiva(true);
        s.setObservacao(texto(dados.getObservacao()));
        s.setUsuarioCriador(user == null ? null : user.matricula());
        s.persist();
        return s;
    }

    @Transactional
    public CirurgiaSala atualizar(int idsala, CirurgiaSala dados, AuthenticatedUser user) {
        CirurgiaSala s = salaRepository.findById((long) idsala);
        if (s == null) {
            throw new IllegalArgumentException("Sala cirurgica nao encontrada.");
        }
        if (texto(dados.getNome()).length() > 0) {
            if (salaRepository.nomeDuplicado(s.getUnidade(), texto(dados.getNome()), idsala)) {
                throw new IllegalArgumentException("Ja existe uma sala com esse nome nesta unidade.");
            }
            s.setNome(texto(dados.getNome()));
        }
        if (dados.getCnes() != null) {
            s.setCnes(texto(dados.getCnes()));
        }
        if (dados.getEspecialidades() != null) {
            s.setEspecialidades(texto(dados.getEspecialidades()));
        }
        if (dados.getHoraInicioExpediente() != null) {
            s.setHoraInicioExpediente(texto(dados.getHoraInicioExpediente()));
        }
        if (dados.getHoraFimExpediente() != null) {
            s.setHoraFimExpediente(texto(dados.getHoraFimExpediente()));
        }
        if (dados.getTempoLimpezaMin() > 0) {
            s.setTempoLimpezaMin(dados.getTempoLimpezaMin());
        }
        if (dados.getObservacao() != null) {
            s.setObservacao(texto(dados.getObservacao()));
        }
        s.setAtiva(dados.isAtiva());
        return s;
    }

    @Transactional
    public void inativar(int idsala) {
        CirurgiaSala s = salaRepository.findById((long) idsala);
        if (s == null) {
            throw new IllegalArgumentException("Sala cirurgica nao encontrada.");
        }
        long agendadas = CirurgiaAgendamento.count(
                "idsala = ?1 and status in ('AGENDADA', 'CONFIRMADA', 'PACIENTE_EM_SALA', 'EM_ANDAMENTO')", idsala);
        if (agendadas > 0) {
            throw new IllegalArgumentException("Sala possui " + agendadas + " cirurgia(s) em aberto. Remaneje antes de inativar.");
        }
        s.setAtiva(false);
    }
}

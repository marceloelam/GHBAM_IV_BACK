package br.com.iv.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import br.com.iv.entity.ChatMensagem;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ChatMensagemRepository implements PanacheRepository<ChatMensagem> {

    public List<ChatMensagem> listarSala(String unidade, String sala, int limite) {
        return find(
                "lower(unidade) = lower(?1) and tipo = 'room' and sala = ?2 and criadoEm >= ?3 order by criadoEm desc",
                unidade,
                sala,
                LocalDate.now().atStartOfDay()
        ).page(0, limite).list();
    }

    public List<ChatMensagem> listarPrivado(String unidade, String atual, String destinatario, int limite) {
        return find(
                "lower(unidade) = lower(?1) and tipo = 'private' and ((remetenteMatricula = ?2 and destinatarioMatricula = ?3) or (remetenteMatricula = ?3 and destinatarioMatricula = ?2)) and criadoEm >= ?4 order by criadoEm desc",
                unidade,
                atual,
                destinatario,
                LocalDate.now().atStartOfDay()
        ).page(0, limite).list();
    }

    public List<ChatMensagem> listarPrivadasRecebidas(String unidade, String matricula, int limite) {
        return find(
                "lower(unidade) = lower(?1) and tipo = 'private' and destinatarioMatricula = ?2 and criadoEm >= ?3 order by criadoEm desc",
                unidade,
                matricula,
                LocalDate.now().atStartOfDay()
        ).page(0, limite).list();
    }

    public List<ChatMensagem> listarPrivadasRecebidas(String matricula, int limite) {
        return find(
                "tipo = 'private' and destinatarioMatricula = ?1 and criadoEm >= ?2 order by criadoEm desc",
                matricula,
                LocalDate.now().atStartOfDay()
        ).page(0, limite).list();
    }

    public int marcarVisualizada(int id, String matricula) {
        return update("visualizadaEm = ?1 where idchatmensagem = ?2 and destinatarioMatricula = ?3",
                LocalDateTime.now(), id, matricula);
    }
}
